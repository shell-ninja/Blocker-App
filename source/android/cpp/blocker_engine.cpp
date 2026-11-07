#include <cstdint>
#include <cstddef>
#include <cstdlib>
#include <cstring>
#include <cstdio>
#include <vector>
#include <string>
#include <string_view>
#include <queue>
#include <algorithm>
#include <mutex>
#include <utility>
#include <ctime>

#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <poll.h>

#include "blocker_engine.h"

#if __has_include(<jni.h>)
#include <jni.h>
#else
// Safe fallback declarations for host linters/IDEs without NDK configuration
#ifndef JNIEXPORT
#define JNIEXPORT
#endif
#ifndef JNICALL
#define JNICALL
#endif
#ifndef JNI_FALSE
#define JNI_FALSE 0
#endif
#ifndef JNI_TRUE
#define JNI_TRUE 1
#endif
#ifndef JNI_ABORT
#define JNI_ABORT 2
#endif

typedef uint8_t jboolean;
typedef int8_t jbyte;
typedef uint16_t jchar;
typedef int16_t jshort;
typedef int32_t jint;
typedef int64_t jlong;
typedef float jfloat;
typedef double jdouble;
typedef jint jsize;

typedef void* jobject;
typedef jobject jclass;
typedef jobject jstring;
typedef jobject jarray;
typedef jobject jobjectArray;
typedef jobject jcharArray;
typedef jobject jbyteArray;

struct _JNIEnv {
    const char* GetStringUTFChars(jstring, jboolean*) { return ""; }
    void ReleaseStringUTFChars(jstring, const char*) {}
    jsize GetArrayLength(jarray) { return 0; }
    jobject GetObjectArrayElement(jobjectArray, jsize) { return nullptr; }
    void DeleteLocalRef(jobject) {}
    void* GetPrimitiveArrayCritical(jarray, jboolean*) { return nullptr; }
    void ReleasePrimitiveArrayCritical(jarray, void*, jint) {}
    jstring NewStringUTF(const char*) { return nullptr; }
    void* GetDirectBufferAddress(jobject) { return nullptr; }
};
typedef _JNIEnv JNIEnv;
#endif

#if __has_include(<android/log.h>)
#include <android/log.h>
#else
#define ANDROID_LOG_UNKNOWN 0
#define ANDROID_LOG_DEFAULT 1
#define ANDROID_LOG_VERBOSE 2
#define ANDROID_LOG_DEBUG   3
#define ANDROID_LOG_INFO    4
#define ANDROID_LOG_WARN    5
#define ANDROID_LOG_ERROR   6
#define ANDROID_LOG_FATAL   7
#define ANDROID_LOG_SILENT  8
inline int __android_log_print(int, const char*, const char*, ...) { return 0; }
#endif

#ifndef MAP_POPULATE
#define MAP_POPULATE 0
#endif

#define TAG "BlockerEngineNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static std::recursive_mutex g_engine_mutex;
static void* g_mmap_addr = nullptr;
static size_t g_mmap_size = 0;
static const BlocklistHeader* g_header = nullptr;
static const uint64_t* g_domain_hashes = nullptr;
static const FlatAcNode* g_ac_nodes = nullptr;
static const FlatAcEdge* g_ac_edges = nullptr;
static const char* g_str_pool = nullptr;
static uint32_t g_root_transitions[256];

// FNV-1a 64-bit constant primes
static const uint64_t FNV_OFFSET_BASIS = 14695981039346656037ULL;
static const uint64_t FNV_PRIME = 1099511628211ULL;

// Helper: POSIX CLOCK_MONOTONIC timestamp in milliseconds
static inline uint64_t get_monotonic_ms() noexcept {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000ULL + (uint64_t)ts.tv_nsec / 1000000ULL;
}

// Rate-limiting state for zero-queue HUD and cooldowns
struct EngineRateLimiter {
    uint64_t last_hud_ms = 0;
    uint8_t last_hud_reason = 0;
    uint64_t last_tamper_ms = 0;
};
static EngineRateLimiter g_rate_limiter;
static std::mutex g_rate_mutex;

static uint16_t compute_ip_checksum(const uint8_t* buf, size_t len) {
    uint32_t sum = 0;
    for (size_t i = 0; i < len; i += 2) {
        uint16_t word = (uint16_t)((buf[i] << 8) | (i + 1 < len ? buf[i + 1] : 0));
        sum += word;
    }
    while (sum >> 16) {
        sum = (sum & 0xFFFF) + (sum >> 16);
    }
    return (uint16_t)(~sum);
}

struct BuildNode {
    int fail = 0;
    uint32_t match_str_offset = 0;
    uint16_t match_len = 0;
    std::vector<std::pair<uint8_t, int>> children;
};

// -----------------------------------------------------------------------------
// Fast C++20 Zero-Allocation String Matching Helpers
// -----------------------------------------------------------------------------
static inline char to_lower_ascii(char c) noexcept {
    return (c >= 'A' && c <= 'Z') ? static_cast<char>(c + 32) : c;
}

static inline bool contains_ci(std::string_view haystack, std::string_view needle) noexcept {
    if (needle.empty()) return true;
    if (haystack.size() < needle.size()) return false;
    const size_t max_i = haystack.size() - needle.size();
    for (size_t i = 0; i <= max_i; ++i) {
        bool match = true;
        for (size_t j = 0; j < needle.size(); ++j) {
            if (to_lower_ascii(haystack[i + j]) != to_lower_ascii(needle[j])) {
                match = false;
                break;
            }
        }
        if (match) return true;
    }
    return false;
}

static inline bool equals_ci(std::string_view a, std::string_view b) noexcept {
    if (a.size() != b.size()) return false;
    for (size_t i = 0; i < a.size(); ++i) {
        if (to_lower_ascii(a[i]) != to_lower_ascii(b[i])) return false;
    }
    return true;
}

static inline bool is_alnum_ascii(char c) noexcept {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
}

// Packed 64-bit verdict helper
static inline uint64_t pack_verdict(
    uint8_t action,
    uint8_t reason,
    uint16_t click_idx,
    uint8_t show_hud,
    uint8_t show_shield
) noexcept {
    return ((uint64_t)action) |
           (((uint64_t)reason) << 8) |
           (((uint64_t)click_idx) << 16) |
           (((uint64_t)show_hud) << 32) |
           (((uint64_t)show_shield) << 40);
}

// -----------------------------------------------------------------------------
// Exported C Engine API
// -----------------------------------------------------------------------------
extern "C" {

uint64_t engine_hash_fnv1a(const char* str, size_t len) {
    uint64_t hash = FNV_OFFSET_BASIS;
    for (size_t i = 0; i < len; ++i) {
        hash ^= (uint64_t)((unsigned char)str[i]);
        hash *= FNV_PRIME;
    }
    return hash;
}

bool engine_init(const char* file_path) {
    std::lock_guard<std::recursive_mutex> lock(g_engine_mutex);
    engine_close();

    int fd = open(file_path, O_RDONLY);
    if (fd < 0) {
        LOGE("Failed to open binary blocklist: %s", file_path);
        return false;
    }

    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size < (off_t)sizeof(BlocklistHeader)) {
        close(fd);
        LOGE("Invalid blocklist file size");
        return false;
    }

    g_mmap_size = (size_t)st.st_size;
    g_mmap_addr = mmap(nullptr, g_mmap_size, PROT_READ, MAP_SHARED | MAP_POPULATE, fd, 0);
    close(fd);

    if (g_mmap_addr == MAP_FAILED) {
        g_mmap_addr = nullptr;
        g_mmap_size = 0;
        LOGE("mmap failed on blocklist");
        return false;
    }

    g_header = (const BlocklistHeader*)g_mmap_addr;
    if (g_header->magic != BLOCKER_MAGIC || g_header->version != BLOCKER_VERSION) {
        LOGE("Magic or version mismatch in blocklist.bin");
        engine_close();
        return false;
    }

    const uint8_t* base = (const uint8_t*)g_mmap_addr;
    g_domain_hashes = (const uint64_t*)(base + g_header->domain_offset);
    g_ac_nodes = (const FlatAcNode*)(base + g_header->ac_node_offset);
    g_ac_edges = (const FlatAcEdge*)(base + g_header->ac_edge_offset);
    g_str_pool = (const char*)(base + g_header->str_pool_offset);

    // Pre-bake root transitions for O(1) entry
    memset(g_root_transitions, 0, sizeof(g_root_transitions));
    if (g_header->ac_node_count > 0) {
        const FlatAcNode* root = &g_ac_nodes[0];
        for (uint16_t e = 0; e < root->edge_count; ++e) {
            uint32_t val = g_ac_edges[root->edge_offset + e].data;
            uint8_t ch = (uint8_t)(val >> 24);
            uint32_t target = val & 0x00FFFFFF;
            g_root_transitions[ch] = target;
        }
    }

    LOGI("Blocklist mapped: %u domains, %u AC nodes, size: %zu bytes",
         g_header->domain_count, g_header->ac_node_count, g_mmap_size);
    return true;
}

void engine_close(void) {
    std::lock_guard<std::recursive_mutex> lock(g_engine_mutex);
    if (g_mmap_addr != nullptr && g_mmap_size > 0) {
        munmap(g_mmap_addr, g_mmap_size);
        g_mmap_addr = nullptr;
        g_mmap_size = 0;
        g_header = nullptr;
        g_domain_hashes = nullptr;
        g_ac_nodes = nullptr;
        g_ac_edges = nullptr;
        g_str_pool = nullptr;
    }
}

bool engine_is_loaded(void) {
    std::lock_guard<std::recursive_mutex> lock(g_engine_mutex);
    return g_header != nullptr;
}

// Branchless binary search over sorted 64-bit domain hashes
bool engine_match_domain_hash(uint64_t hash) {
    std::lock_guard<std::recursive_mutex> lock(g_engine_mutex);
    if (!g_header || g_header->domain_count == 0) return false;

    int low = 0;
    int high = (int)g_header->domain_count - 1;
    while (low <= high) {
        int mid = low + ((high - low) >> 1);
        uint64_t mid_val = g_domain_hashes[mid];
        if (mid_val == hash) return true;
        if (mid_val < hash) low = mid + 1;
        else high = mid - 1;
    }
    return false;
}

// Match text using Flat Aho-Corasick Automaton in O(M)
int engine_match_text(const char* text, size_t len, char* out_rule, size_t max_out_len) {
    std::lock_guard<std::recursive_mutex> lock(g_engine_mutex);
    if (!g_header || g_header->ac_node_count == 0 || len == 0 || !g_str_pool) return 0;

    uint32_t curr_state = 0;
    for (size_t i = 0; i < len; ++i) {
        uint8_t ch = (uint8_t)text[i];
        if (ch >= 'A' && ch <= 'Z') ch = (uint8_t)(ch | 0x20); // In-place lowercase

        while (true) {
            uint32_t next_state = 0;
            if (curr_state == 0) {
                next_state = g_root_transitions[ch];
            } else {
                const FlatAcNode* n = &g_ac_nodes[curr_state];
                for (uint16_t e = 0; e < n->edge_count; ++e) {
                    uint32_t edge_val = g_ac_edges[n->edge_offset + e].data;
                    if ((uint8_t)(edge_val >> 24) == ch) {
                        next_state = edge_val & 0x00FFFFFF;
                        break;
                    }
                }
            }

            if (next_state != 0) {
                curr_state = next_state;
                break;
            }
            if (curr_state == 0) break;
            curr_state = g_ac_nodes[curr_state].fail_idx;
        }

        const FlatAcNode* matched = &g_ac_nodes[curr_state];
        if (matched->match_str_offset != 0) {
            size_t mlen = (size_t)matched->match_len;
            if (mlen > 0 && (i + 1) >= mlen) {
                // Whole-word boundary check
                bool left_boundary = (i + 1 == mlen) ||
                    (i >= mlen && !is_alnum_ascii(text[i - mlen]));

                bool right_boundary = (i + 1 == len) ||
                    !is_alnum_ascii(text[i + 1]);

                if (left_boundary && right_boundary) {
                    if (out_rule && max_out_len > 0) {
                        if (matched->match_str_offset < g_header->str_pool_size) {
                            const char* src = g_str_pool + matched->match_str_offset;
                            size_t cpy = mlen < (max_out_len - 1) ? mlen : (max_out_len - 1);
                            memcpy(out_rule, src, cpy);
                            out_rule[cpy] = '\0';
                        }
                    }
                    return (int)mlen;
                }
            }
        }
    }
    return 0;
}

// In-place zero-allocation URL parsing and multi-stage matching
int engine_match_url(const char* url, size_t len, char* out_rule, size_t max_out_len) {
    std::lock_guard<std::recursive_mutex> lock(g_engine_mutex);
    if (!g_header || len == 0) return 0;

    // 1. Strip protocol in-place
    size_t start = 0;
    if (len >= 8 && memcmp(url, "https://", 8) == 0) start = 8;
    else if (len >= 7 && memcmp(url, "http://", 7) == 0) start = 7;

    // 2. Locate hostname boundaries
    size_t host_start = start;
    if (len - host_start >= 4 && memcmp(url + host_start, "www.", 4) == 0) host_start += 4;

    size_t host_end = host_start;
    while (host_end < len && url[host_end] != '/' && url[host_end] != '?' &&
           url[host_end] != '#' && url[host_end] != ':') {
        host_end++;
    }

    size_t host_len = host_end - host_start;
    if (host_len > 0 && host_len < 256) {
        char host_buf[256];
        for (size_t i = 0; i < host_len; ++i) {
            char c = url[host_start + i];
            if (c >= 'A' && c <= 'Z') c |= 0x20;
            host_buf[i] = c;
        }

        // Subdomain decomposition: test sub.domain.com -> domain.com -> com
        size_t sub_offset = 0;
        while (sub_offset < host_len) {
            uint64_t h = engine_hash_fnv1a(host_buf + sub_offset, host_len - sub_offset);
            if (engine_match_domain_hash(h)) {
                if (out_rule && max_out_len > 0) {
                    size_t cpy = (host_len - sub_offset) < (max_out_len - 1) ? (host_len - sub_offset) : (max_out_len - 1);
                    memcpy(out_rule, host_buf + sub_offset, cpy);
                    out_rule[cpy] = '\0';
                }
                return 1; // Domain rule hit
            }
            // Move to next dot
            const char* next_dot = (const char*)memchr(host_buf + sub_offset, '.', host_len - sub_offset);
            if (!next_dot) break;
            sub_offset = (size_t)(next_dot - host_buf) + 1;
        }
    }

    // 3. Keyword scan over full URL
    return engine_match_text(url, len, out_rule, max_out_len);
}

// -----------------------------------------------------------------------------
// Rate Limiter API (Debounce repeated hits)
// -----------------------------------------------------------------------------
bool engine_check_hud_rate_limit(uint8_t reason_id) {
    std::lock_guard<std::mutex> lock(g_rate_mutex);
    uint64_t now = get_monotonic_ms();
    if (g_rate_limiter.last_hud_reason == reason_id && (now - g_rate_limiter.last_hud_ms) < 800ULL) {
        return false; // Suppress / debounce
    }
    g_rate_limiter.last_hud_reason = reason_id;
    g_rate_limiter.last_hud_ms = now;
    return true; // Allow HUD
}

// -----------------------------------------------------------------------------
// High-Speed C++20 Screen Evaluation & Anti-Tamper Engine
// -----------------------------------------------------------------------------
struct InternalNodeView {
    uint16_t left = 0;
    uint16_t top = 0;
    uint16_t right = 0;
    uint16_t bottom = 0;
    uint32_t flags = 0;
    std::string_view text;
    std::string_view cd;
    std::string_view id;
    std::string_view cls;
    int original_index = 0;
};

uint64_t engine_evaluate_screen(const uint8_t* buffer, size_t buffer_len) {
    if (!buffer || buffer_len < sizeof(ScreenEvalHeader)) {
        return pack_verdict(ACTION_ALLOW, REASON_NONE, 0xFFFF, 0, 0);
    }

    const auto* hdr = reinterpret_cast<const ScreenEvalHeader*>(buffer);
    if (hdr->magic != SCREEN_EVAL_MAGIC) {
        return pack_verdict(ACTION_ALLOW, REASON_NONE, 0xFFFF, 0, 0);
    }

    size_t offset = sizeof(ScreenEvalHeader);
    size_t str_bytes = (size_t)hdr->pkg_len + hdr->cls_len + hdr->self_pkg_len + hdr->self_lbl_len;
    if (offset + str_bytes > buffer_len) {
        return pack_verdict(ACTION_ALLOW, REASON_NONE, 0xFFFF, 0, 0);
    }

    const char* str_base = reinterpret_cast<const char*>(buffer + offset);
    std::string_view pkg(str_base, hdr->pkg_len);
    str_base += hdr->pkg_len;
    std::string_view cls(str_base, hdr->cls_len);
    str_base += hdr->cls_len;
    std::string_view self_pkg(str_base, hdr->self_pkg_len);
    str_base += hdr->self_pkg_len;
    std::string_view self_lbl(str_base, hdr->self_lbl_len);
    offset += str_bytes;

    constexpr size_t MAX_NODES = 180;
    InternalNodeView nodes[MAX_NODES];
    size_t node_count = 0;

    const size_t total_nodes = std::min((size_t)hdr->node_count, MAX_NODES);
    for (size_t i = 0; i < total_nodes; ++i) {
        if (offset + sizeof(SerializedNodeHeader) > buffer_len) break;
        const auto* nh = reinterpret_cast<const SerializedNodeHeader*>(buffer + offset);
        offset += sizeof(SerializedNodeHeader);

        size_t node_str_bytes = (size_t)nh->text_len + nh->cd_len + nh->id_len + nh->cls_len;
        if (offset + node_str_bytes > buffer_len) break;

        const char* ns_base = reinterpret_cast<const char*>(buffer + offset);
        nodes[node_count].left = nh->left;
        nodes[node_count].top = nh->top;
        nodes[node_count].right = nh->right;
        nodes[node_count].bottom = nh->bottom;
        nodes[node_count].flags = nh->flags;

        nodes[node_count].text = std::string_view(ns_base, nh->text_len);
        ns_base += nh->text_len;
        nodes[node_count].cd = std::string_view(ns_base, nh->cd_len);
        ns_base += nh->cd_len;
        nodes[node_count].id = std::string_view(ns_base, nh->id_len);
        ns_base += nh->id_len;
        nodes[node_count].cls = std::string_view(ns_base, nh->cls_len);

        nodes[node_count].original_index = (int)i;
        offset += node_str_bytes;
        node_count++;
    }

    const bool is_armed = (hdr->config_flags & CONFIG_FLAG_SHIELD_ACTIVE) &&
                          (hdr->config_flags & CONFIG_FLAG_ONBOARDING_ARMED);
    const bool block_fb_reels = (hdr->config_flags & CONFIG_FLAG_BLOCK_FB_REELS) != 0;
    const bool block_date_time = (hdr->config_flags & CONFIG_FLAG_BLOCK_DATE_TIME) != 0;

    // -------------------------------------------------------------------------
    // 1. Anti-Tamper & Surgical Scoping (Active Only When Armed)
    // -------------------------------------------------------------------------
    if (is_armed) {
        // A. Recents 3-Dot / App Info Popup & Task Context Menus
        // NOTE: Launchers (home screens) are never evaluated here; Kotlin handles them via guardLauncherInstant.
        const bool is_recents_task_menu =
            (contains_ci(cls, "TaskMenuView") || contains_ci(cls, "TaskMenu") ||
             contains_ci(cls, "RecentsAppMenu") || contains_ci(cls, "OverviewActionsView")) &&
            !contains_ci(cls, "Notification") && !contains_ci(cls, "Shade") &&
            !contains_ci(cls, "Status") && !contains_ci(cls, "Volume");

        if (is_recents_task_menu) {
            bool has_self_token = false;
            for (size_t i = 0; i < node_count; ++i) {
                // Must not be a notification or status bar node
                std::string_view id = nodes[i].id;
                if (contains_ci(id, "notification") || contains_ci(id, "status_bar") ||
                    contains_ci(id, "shade") || contains_ci(id, "qs_")) continue;

                if ((!self_lbl.empty() && (contains_ci(nodes[i].text, self_lbl) || contains_ci(nodes[i].cd, self_lbl))) ||
                    (!self_pkg.empty() && (contains_ci(nodes[i].text, self_pkg) || contains_ci(nodes[i].cd, self_pkg)))) {
                    has_self_token = true;
                    break;
                }
            }

            if (has_self_token) {
                for (size_t i = 0; i < node_count; ++i) {
                    std::string_view t = nodes[i].text;
                    std::string_view c = nodes[i].cd;
                    if (contains_ci(t, "app info") || contains_ci(c, "app info") ||
                        contains_ci(t, "application info") || contains_ci(c, "application info") ||
                        contains_ci(t, "app details") || contains_ci(c, "app details") ||
                        contains_ci(t, "split screen") || contains_ci(c, "split screen") ||
                        contains_ci(t, "pause app") || contains_ci(c, "pause app") ||
                        contains_ci(t, "floating window") || contains_ci(c, "floating window") ||
                        contains_ci(t, "uninstall") || contains_ci(c, "uninstall")) {
                        return pack_verdict(ACTION_TAMPER_DETECTED, REASON_RECENTS_POPUP_TAMPER, 0xFFFF, 0, 1);
                    }
                }
            }
        }

        // B. PackageInstaller: SURGICAL SCOPING (Allow other apps to be uninstalled freely!)
        const bool is_installer =
            contains_ci(pkg, "packageinstaller") || contains_ci(cls, "packageinstaller") ||
            contains_ci(cls, "UninstallerActivity") || contains_ci(cls, "DeleteStagedFileOnResult") ||
            contains_ci(cls, "UninstallAppProgress");

        if (is_installer) {
            bool targets_self = false;
            bool is_uninstall_prompt = false;

            for (size_t i = 0; i < node_count; ++i) {
                std::string_view t = nodes[i].text;
                std::string_view c = nodes[i].cd;

                if ((!self_lbl.empty() && (contains_ci(t, self_lbl) || contains_ci(c, self_lbl))) ||
                    (!self_pkg.empty() && (contains_ci(t, self_pkg) || contains_ci(c, self_pkg))) ||
                    contains_ci(t, "blocker") || contains_ci(c, "blocker")) {
                    targets_self = true;
                }

                if (contains_ci(t, "uninstall") || contains_ci(c, "uninstall") ||
                    contains_ci(t, "do you want to uninstall") || contains_ci(c, "do you want to uninstall") ||
                    contains_ci(t, "delete") || contains_ci(c, "delete") ||
                    contains_ci(t, "remove") || contains_ci(c, "remove")) {
                    is_uninstall_prompt = true;
                }
            }

            if (targets_self && is_uninstall_prompt) {
                return pack_verdict(ACTION_TAMPER_DETECTED, REASON_UNINSTALL_TAMPER, 0xFFFF, 0, 1);
            }

            // CRITICAL: If not targeting Blocker, return ALLOW immediately so user can uninstall any other app!
            return pack_verdict(ACTION_ALLOW, REASON_NONE, 0xFFFF, 0, 0);
        }

        // C. Settings / SpaActivity / OEM Security Centers
        const bool is_settings =
            contains_ci(pkg, "settings") || contains_ci(pkg, "securitycenter") ||
            contains_ci(pkg, "safecenter") || contains_ci(pkg, "permissioncontroller") ||
            contains_ci(pkg, "permissionmanager") || contains_ci(pkg, "phonemaster") ||
            contains_ci(pkg, "phonemanager") || contains_ci(pkg, "storagemanager") ||
            contains_ci(pkg, "freezer") || contains_ci(pkg, "lool");

        if (is_settings) {
            // Check for Developer Options & Debugging
            bool has_dev_opts =
                contains_ci(cls, "DevelopmentSettings") ||
                contains_ci(cls, "DevelopmentSettingsDashboardActivity");

            if (!has_dev_opts) {
                for (size_t i = 0; i < node_count; ++i) {
                    std::string_view t = nodes[i].text;
                    if (contains_ci(t, "developer options") || contains_ci(t, "development settings") ||
                        contains_ci(t, "wireless debugging") || contains_ci(t, "usb debugging")) {
                        has_dev_opts = true;
                        break;
                    }
                }
            }
            if (has_dev_opts) {
                return pack_verdict(ACTION_TAMPER_DETECTED, REASON_DEV_OPTIONS_TAMPER, 0xFFFF, 0, 1);
            }

            // Check for Date & Time Clock Tampering
            if (block_date_time) {
                bool has_date_time =
                    contains_ci(cls, "DateTimeSettings") || contains_ci(cls, "ZonePicker");
                if (!has_date_time) {
                    for (size_t i = 0; i < node_count; ++i) {
                        std::string_view t = nodes[i].text;
                        if (contains_ci(t, "date & time") || contains_ci(t, "automatic date & time") ||
                            contains_ci(t, "set time automatically") || contains_ci(t, "use network-provided time")) {
                            has_date_time = true;
                            break;
                        }
                    }
                }
                if (has_date_time) {
                    return pack_verdict(ACTION_TAMPER_DETECTED, REASON_DATE_TIME_TAMPER, 0xFFFF, 0, 1);
                }
            }

            // Check for Device Admin Screens
            bool is_admin_screen =
                contains_ci(cls, "DeviceAdminAdd") || contains_ci(cls, "DeviceAdminSettings") ||
                contains_ci(cls, "DeviceAdmin");

            if (is_admin_screen) {
                bool mentions_self = false;
                bool is_deactivation_attempt = false;
                for (size_t i = 0; i < node_count; ++i) {
                    std::string_view t = nodes[i].text;
                    std::string_view c = nodes[i].cd;
                    if ((!self_lbl.empty() && (contains_ci(t, self_lbl) || contains_ci(c, self_lbl))) ||
                        (!self_pkg.empty() && (contains_ci(t, self_pkg) || contains_ci(c, self_pkg)))) {
                        mentions_self = true;
                    }
                    if (contains_ci(t, "deactivate") || contains_ci(c, "deactivate") ||
                        contains_ci(t, "remove device admin") || contains_ci(c, "remove device admin")) {
                        is_deactivation_attempt = true;
                    }
                }
                if (mentions_self && is_deactivation_attempt) {
                    return pack_verdict(ACTION_TAMPER_DETECTED, REASON_DEVICE_ADMIN_TAMPER, 0xFFFF, 0, 1);
                }
                // Legitimate admin activation or list screen: allow user to turn ON device admin
                return pack_verdict(ACTION_ALLOW, REASON_NONE, 0xFFFF, 0, 0);
            }

            // Scoped Check: Does this Settings / App Info screen target Blocker?
            bool targets_self_in_settings = false;
            for (size_t i = 0; i < node_count; ++i) {
                if ((!self_lbl.empty() && (contains_ci(nodes[i].text, self_lbl) || contains_ci(nodes[i].cd, self_lbl))) ||
                    (!self_pkg.empty() && (contains_ci(nodes[i].text, self_pkg) || contains_ci(nodes[i].cd, self_pkg)))) {
                    targets_self_in_settings = true;
                    break;
                }
            }

            if (targets_self_in_settings) {
                for (size_t i = 0; i < node_count; ++i) {
                    std::string_view t = nodes[i].text;
                    std::string_view c = nodes[i].cd;
                    if (contains_ci(t, "force stop") || contains_ci(c, "force stop") ||
                        contains_ci(t, "uninstall") || contains_ci(c, "uninstall") ||
                        contains_ci(t, "clear storage") || contains_ci(c, "clear storage") ||
                        contains_ci(t, "clear data") || contains_ci(c, "clear data") ||
                        contains_ci(t, "storage & cache") || contains_ci(c, "storage & cache") ||
                        contains_ci(t, "manage space") || contains_ci(c, "manage space") ||
                        contains_ci(t, "disable") || contains_ci(c, "disable")) {
                        return pack_verdict(ACTION_TAMPER_DETECTED, REASON_APP_INFO_TAMPER, 0xFFFF, 0, 1);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 2. Facebook Reels Page & Full-Screen Player Blocker (Litho-Aware)
    // -------------------------------------------------------------------------
    if (block_fb_reels && (pkg == "com.facebook.katana" || pkg == "com.facebook.lite")) {
        // Fast Story Check: Never block Facebook Stories!
        bool is_story = false;
        for (size_t i = 0; i < node_count; ++i) {
            std::string_view t = nodes[i].text;
            std::string_view c = nodes[i].cd;
            std::string_view id = nodes[i].id;
            if (contains_ci(id, "story") || contains_ci(t, "add to story") || contains_ci(c, "add to story") ||
                contains_ci(t, "reply to") || contains_ci(c, "reply to") ||
                contains_ci(id, "stories_tray") || contains_ci(id, "story_viewer")) {
                is_story = true;
                break;
            }
        }
        if (is_story) {
            return pack_verdict(ACTION_ALLOW, REASON_NONE, 0xFFFF, 0, 0);
        }

        // ---------------------------------------------------------------------
        // STATE A: Main Navigation Bar Inspector & Newsfeed Safe-Guard
        // ---------------------------------------------------------------------
        // Scan top (top < 220) and bottom (bottom > 880) for Facebook's main tab bar (4 to 6 horizontal tab nodes)
        int nav_tabs_top = 0;
        int nav_tabs_bottom = 0;
        bool home_tab_selected = false;
        bool reels_or_video_tab_selected = false;
        uint16_t home_tab_idx = 0xFFFF;

        for (size_t i = 0; i < node_count; ++i) {
            const auto& n = nodes[i];
            bool is_top_band = (n.top < 220 && n.bottom < 300);
            bool is_bottom_band = (n.top > 800 && n.bottom > 880);

            if (is_top_band || is_bottom_band) {
                std::string_view t = n.text;
                std::string_view c = n.cd;

                bool is_tab_node = contains_ci(c, "tab") || contains_ci(t, "home") || contains_ci(t, "feed") ||
                    contains_ci(t, "friends") || contains_ci(t, "watch") || contains_ci(t, "video") ||
                    contains_ci(t, "marketplace") || contains_ci(t, "notifications") || contains_ci(t, "menu") ||
                    contains_ci(t, "reels") || contains_ci(c, "home") || contains_ci(c, "feed");

                if (is_tab_node) {
                    if (is_top_band) nav_tabs_top++;
                    if (is_bottom_band) nav_tabs_bottom++;

                    bool is_home = contains_ci(c, "home, tab") || contains_ci(c, "feed, tab") ||
                        contains_ci(c, "tab home") || contains_ci(c, "tab feed") ||
                        (t == "Home" || t == "Feed" || t == "ফিড" || t == "হোম");

                    bool is_reels_or_video = contains_ci(c, "reels, tab") || contains_ci(c, "reels tab") ||
                        contains_ci(c, "tab reels") || contains_ci(c, "video, tab") || contains_ci(c, "watch, tab") ||
                        contains_ci(c, "tab video") || contains_ci(c, "tab watch") ||
                        (t == "Reels" || t == "Video" || t == "Watch" || t == "রিলস" || t == "ভিডিও");

                    bool is_selected = (n.flags & (NODE_FLAG_SELECTED | NODE_FLAG_CHECKED | NODE_FLAG_STATE_DESC_SELECTED)) != 0 ||
                        contains_ci(c, "selected") || contains_ci(c, "active");

                    if (is_home) {
                        if (home_tab_idx == 0xFFFF) home_tab_idx = static_cast<uint16_t>(n.original_index);
                        if (is_selected) home_tab_selected = true;
                    }
                    if (is_reels_or_video && is_selected) {
                        reels_or_video_tab_selected = true;
                    }
                }
            }
        }

        bool main_tab_bar_visible = (nav_tabs_top >= 3 || nav_tabs_bottom >= 3);

        // STATE A SAFE-GUARD:
        // If Main Tab Bar is Visible AND Home / Feed Tab is Selected:
        // The user is on their normal Newsfeed! Immediately return ALLOW!
        if (main_tab_bar_visible && home_tab_selected) {
            return pack_verdict(ACTION_ALLOW, REASON_NONE, 0xFFFF, 0, 0);
        }

        // ---------------------------------------------------------------------
        // STATE B: Dedicated Facebook Reels Tab or Video/Watch Tab
        // ---------------------------------------------------------------------
        if (main_tab_bar_visible && reels_or_video_tab_selected && !home_tab_selected) {
            uint8_t show_hud = engine_check_hud_rate_limit(REASON_FB_REELS) ? 1 : 0;
            return pack_verdict(ACTION_BLOCK_REELS, REASON_FB_REELS, home_tab_idx, show_hud, 0);
        }

        // ---------------------------------------------------------------------
        // STATE C: Clicked Video / Reel Player (Main Tab Bar is NOT visible)
        // ---------------------------------------------------------------------
        bool player_activity = contains_ci(cls, "FbShorts") || contains_ci(cls, "ReelViewer") ||
            contains_ci(cls, "TahoeActivity") || contains_ci(cls, "VideoHome") ||
            contains_ci(cls, "FullScreenVideo") || contains_ci(cls, "InspirationViewer") ||
            contains_ci(cls, "ClipsViewer");

        if (!main_tab_bar_visible || player_activity) {
            // Signature C1: Dedicated Player Activity Class
            if (player_activity) {
                uint8_t show_hud = engine_check_hud_rate_limit(REASON_FB_REELS) ? 1 : 0;
                return pack_verdict(ACTION_BLOCK_REELS, REASON_FB_REELS, 0xFFFF, show_hud, 0);
            }

            // Signature C2: True Vertical Right-Edge Reels Action Rail
            int like_x = -1, like_y = -1;
            int comment_x = -1, comment_y = -1;
            int share_x = -1, share_y = -1;
            bool has_story_reply = false;

            for (size_t i = 0; i < node_count; ++i) {
                const auto& n = nodes[i];
                std::string_view t = n.text;
                std::string_view c = n.cd;

                if (n.top > 850 && (contains_ci(t, "reply to") || contains_ci(c, "reply to") ||
                    contains_ci(t, "send message") || contains_ci(c, "send message"))) {
                    has_story_reply = true;
                }

                int cx = (n.left + n.right) / 2;
                int cy = (n.top + n.bottom) / 2;

                if (cx >= 800) {
                    if (contains_ci(t, "like") || contains_ci(c, "like") ||
                        contains_ci(t, "react") || contains_ci(c, "react")) {
                        like_x = cx; like_y = cy;
                    } else if (contains_ci(t, "comment") || contains_ci(c, "comment")) {
                        comment_x = cx; comment_y = cy;
                    } else if (contains_ci(t, "share") || contains_ci(c, "share") ||
                               contains_ci(t, "send") || contains_ci(c, "send")) {
                        share_x = cx; share_y = cy;
                    }
                }
            }

            if (!has_story_reply && like_x > 0 && comment_x > 0 && share_x > 0) {
                int min_x = std::min({like_x, comment_x, share_x});
                int max_x = std::max({like_x, comment_x, share_x});
                if ((max_x - min_x) <= 80) { // Same vertical column (<= 8% screen width)
                    if (like_y < comment_y && comment_y < share_y) {
                        if ((comment_y - like_y) >= 40 && (share_y - comment_y) >= 40) {
                            uint8_t show_hud = engine_check_hud_rate_limit(REASON_FB_REELS) ? 1 : 0;
                            return pack_verdict(ACTION_BLOCK_REELS, REASON_FB_REELS, 0xFFFF, show_hud, 0);
                        }
                    }
                }
            }

            // Signature C3: Video Player Overlay Controls
            bool has_player_controls = false;
            for (size_t i = 0; i < node_count; ++i) {
                const auto& n = nodes[i];
                std::string_view t = n.text;
                std::string_view c = n.cd;

                // Top overlay bar with exact "Reels" or "Watch"
                if (n.top < 160 && (t == "Reels" || c == "Reels" || t == "Watch" || c == "Watch")) {
                    has_player_controls = true;
                    break;
                }
                // Transport controls
                if (contains_ci(c, "seek bar") || contains_ci(c, "pause video") ||
                    contains_ci(t, "pause video") || contains_ci(c, "enter full screen") ||
                    contains_ci(c, "exit full screen") || contains_ci(t, "original audio") ||
                    contains_ci(c, "original audio")) {
                    has_player_controls = true;
                    break;
                }
            }

            if (has_player_controls) {
                uint8_t show_hud = engine_check_hud_rate_limit(REASON_FB_REELS) ? 1 : 0;
                return pack_verdict(ACTION_BLOCK_REELS, REASON_FB_REELS, 0xFFFF, show_hud, 0);
            }
        }
    }

    return pack_verdict(ACTION_ALLOW, REASON_NONE, 0xFFFF, 0, 0);
}

// -----------------------------------------------------------------------------
// Offline Binary Compiler (invoked by React Native NativeModule on list updates)
// -----------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_blocker_NativeBlockerBridge_compileBinary(
    JNIEnv* env, jclass,
    jobjectArray jdomains, jobjectArray jkeywords, jstring jout_path) {

    const char* out_path = env->GetStringUTFChars(jout_path, nullptr);
    if (!out_path) return JNI_FALSE;

    // 1. Process Domains: Sort FNV-1a hashes
    int domain_count = env->GetArrayLength(jdomains);
    std::vector<uint64_t> domain_hashes;
    domain_hashes.reserve(domain_count);

    for (int i = 0; i < domain_count; ++i) {
        jstring str = (jstring)env->GetObjectArrayElement(jdomains, i);
        if (!str) continue;
        const char* s = env->GetStringUTFChars(str, nullptr);
        size_t slen = strlen(s);
        std::string lower(s, slen);
        for (char& c : lower) { if (c >= 'A' && c <= 'Z') c |= 0x20; }
        domain_hashes.push_back(engine_hash_fnv1a(lower.c_str(), lower.size()));
        env->ReleaseStringUTFChars(str, s);
        env->DeleteLocalRef(str);
    }
    std::sort(domain_hashes.begin(), domain_hashes.end());
    domain_hashes.erase(std::unique(domain_hashes.begin(), domain_hashes.end()), domain_hashes.end());

    // 2. Build Aho-Corasick Trie in memory
    std::vector<BuildNode> trie(1); // Root at 0
    std::string str_pool = "\0";    // Offset 0 is empty

    int kw_count = env->GetArrayLength(jkeywords);
    for (int i = 0; i < kw_count; ++i) {
        jstring str = (jstring)env->GetObjectArrayElement(jkeywords, i);
        if (!str) continue;
        const char* s = env->GetStringUTFChars(str, nullptr);
        size_t slen = strlen(s);
        std::string kw(s, slen);
        for (char& c : kw) { if (c >= 'A' && c <= 'Z') c |= 0x20; }
        env->ReleaseStringUTFChars(str, s);
        env->DeleteLocalRef(str);

        if (kw.empty()) continue;

        int curr = 0;
        for (char ch_c : kw) {
            uint8_t ch = (uint8_t)ch_c;
            int next = -1;
            for (auto& edge : trie[curr].children) {
                if (edge.first == ch) { next = edge.second; break; }
            }
            if (next == -1) {
                next = (int)trie.size();
                trie.emplace_back();
                trie[curr].children.emplace_back(ch, next);
            }
            curr = next;
        }
        trie[curr].match_str_offset = (uint32_t)str_pool.size();
        trie[curr].match_len = (uint16_t)kw.size();
        str_pool.append(kw);
        str_pool.push_back('\0');
    }

    // 3. Compute Aho-Corasick Failure Links (BFS)
    std::queue<int> q;
    for (auto& edge : trie[0].children) {
        trie[edge.second].fail = 0;
        q.push(edge.second);
    }
    while (!q.empty()) {
        int u = q.front();
        q.pop();

        for (auto& edge : trie[u].children) {
            uint8_t ch = edge.first;
            int v = edge.second;

            int f = trie[u].fail;
            while (f > 0) {
                bool found = false;
                for (auto& fe : trie[f].children) {
                    if (fe.first == ch) { f = fe.second; found = true; break; }
                }
                if (found) break;
                f = trie[f].fail;
            }
            if (f == 0) {
                bool found = false;
                for (auto& fe : trie[0].children) {
                    if (fe.first == ch) { trie[v].fail = fe.second; found = true; break; }
                }
                if (!found) trie[v].fail = 0;
            } else {
                trie[v].fail = f;
            }

            // Propagate match outputs
            if (trie[v].match_str_offset == 0 && trie[trie[v].fail].match_str_offset != 0) {
                trie[v].match_str_offset = trie[trie[v].fail].match_str_offset;
                trie[v].match_len = trie[trie[v].fail].match_len;
            }
            q.push(v);
        }
    }

    // 4. Flatten Nodes and Edges into Contiguous Offsets
    std::vector<FlatAcNode> flat_nodes(trie.size());
    std::vector<FlatAcEdge> flat_edges;

    for (size_t i = 0; i < trie.size(); ++i) {
        flat_nodes[i].fail_idx = (uint32_t)trie[i].fail;
        flat_nodes[i].match_str_offset = trie[i].match_str_offset;
        flat_nodes[i].match_len = trie[i].match_len;
        flat_nodes[i].edge_offset = (uint32_t)flat_edges.size();
        flat_nodes[i].edge_count = (uint16_t)trie[i].children.size();

        for (auto& edge : trie[i].children) {
            FlatAcEdge fe;
            fe.data = ((uint32_t)edge.first << 24) | ((uint32_t)edge.second & 0x00FFFFFF);
            flat_edges.push_back(fe);
        }
    }

    // 5. Serialize into Flat Binary File
    char tmp_path[512];
    snprintf(tmp_path, sizeof(tmp_path), "%s.tmp", out_path);
    FILE* fp = fopen(tmp_path, "wb");
    if (!fp) {
        env->ReleaseStringUTFChars(jout_path, out_path);
        return JNI_FALSE;
    }

    BlocklistHeader hdr;
    memset(&hdr, 0, sizeof(hdr));
    hdr.magic = BLOCKER_MAGIC;
    hdr.version = BLOCKER_VERSION;
    hdr.domain_count = (uint32_t)domain_hashes.size();
    hdr.ac_node_count = (uint32_t)flat_nodes.size();
    hdr.ac_edge_count = (uint32_t)flat_edges.size();
    hdr.str_pool_size = (uint32_t)str_pool.size();

    uint32_t offset = (uint32_t)sizeof(BlocklistHeader);
    hdr.domain_offset = offset;
    offset += (uint32_t)(domain_hashes.size() * sizeof(uint64_t));

    hdr.ac_node_offset = offset;
    offset += (uint32_t)(flat_nodes.size() * sizeof(FlatAcNode));

    hdr.ac_edge_offset = offset;
    offset += (uint32_t)(flat_edges.size() * sizeof(FlatAcEdge));

    hdr.str_pool_offset = offset;
    offset += (uint32_t)str_pool.size();

    hdr.checksum = 0;

    fwrite(&hdr, sizeof(hdr), 1, fp);
    if (!domain_hashes.empty()) {
        fwrite(domain_hashes.data(), sizeof(uint64_t), domain_hashes.size(), fp);
    }
    fwrite(flat_nodes.data(), sizeof(FlatAcNode), flat_nodes.size(), fp);
    if (!flat_edges.empty()) {
        fwrite(flat_edges.data(), sizeof(FlatAcEdge), flat_edges.size(), fp);
    }
    fwrite(str_pool.data(), 1, str_pool.size(), fp);

    fflush(fp);
    fsync(fileno(fp));
    fclose(fp);

    rename(tmp_path, out_path);
    env->ReleaseStringUTFChars(jout_path, out_path);
    return JNI_TRUE;
}

// -----------------------------------------------------------------------------
// JNI Method Bindings
// -----------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_blocker_NativeBlockerBridge_nativeInit(JNIEnv* env, jclass, jstring jpath) {
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    bool ok = engine_init(path);
    env->ReleaseStringUTFChars(jpath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_blocker_NativeBlockerBridge_nativeClose(JNIEnv*, jclass) {
    engine_close();
}

JNIEXPORT jboolean JNICALL
Java_com_blocker_NativeBlockerBridge_nativeIsLoaded(JNIEnv*, jclass) {
    return engine_is_loaded() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_blocker_NativeBlockerBridge_nativeMatchUrl(JNIEnv* env, jclass, jcharArray jchars, jint len) {
    if (len <= 0) return 0;
    jchar* chars = (jchar*)env->GetPrimitiveArrayCritical(jchars, nullptr);
    if (!chars) return 0;

    char stack_buf[2048];
    size_t scan_len = (size_t)len < sizeof(stack_buf) ? (size_t)len : sizeof(stack_buf);
    for (size_t i = 0; i < scan_len; ++i) {
        stack_buf[i] = (char)chars[i];
    }
    env->ReleasePrimitiveArrayCritical(jchars, chars, JNI_ABORT);

    return engine_match_url(stack_buf, scan_len, nullptr, 0);
}

JNIEXPORT jstring JNICALL
Java_com_blocker_NativeBlockerBridge_nativeMatchUrlRule(JNIEnv* env, jclass, jcharArray jchars, jint len) {
    if (len <= 0) return nullptr;
    jchar* chars = (jchar*)env->GetPrimitiveArrayCritical(jchars, nullptr);
    if (!chars) return nullptr;

    char stack_buf[2048];
    size_t scan_len = (size_t)len < sizeof(stack_buf) ? (size_t)len : sizeof(stack_buf);
    for (size_t i = 0; i < scan_len; ++i) {
        stack_buf[i] = (char)chars[i];
    }
    env->ReleasePrimitiveArrayCritical(jchars, chars, JNI_ABORT);

    char out_rule[256];
    out_rule[0] = '\0';
    int match_len = engine_match_url(stack_buf, scan_len, out_rule, sizeof(out_rule));
    if (match_len > 0 && out_rule[0] != '\0') {
        return env->NewStringUTF(out_rule);
    }
    return nullptr;
}

JNIEXPORT jint JNICALL
Java_com_blocker_NativeBlockerBridge_nativeMatchText(JNIEnv* env, jclass, jcharArray jchars, jint len) {
    if (len <= 0) return 0;
    jchar* chars = (jchar*)env->GetPrimitiveArrayCritical(jchars, nullptr);
    if (!chars) return 0;

    char stack_buf[4096];
    size_t scan_len = (size_t)len < sizeof(stack_buf) ? (size_t)len : sizeof(stack_buf);
    for (size_t i = 0; i < scan_len; ++i) {
        stack_buf[i] = (char)chars[i];
    }
    env->ReleasePrimitiveArrayCritical(jchars, chars, JNI_ABORT);

    return engine_match_text(stack_buf, scan_len, nullptr, 0);
}

JNIEXPORT jstring JNICALL
Java_com_blocker_NativeBlockerBridge_nativeMatchTextRule(JNIEnv* env, jclass, jcharArray jchars, jint len) {
    if (len <= 0) return nullptr;
    jchar* chars = (jchar*)env->GetPrimitiveArrayCritical(jchars, nullptr);
    if (!chars) return nullptr;

    char stack_buf[4096];
    size_t scan_len = (size_t)len < sizeof(stack_buf) ? (size_t)len : sizeof(stack_buf);
    for (size_t i = 0; i < scan_len; ++i) {
        stack_buf[i] = (char)chars[i];
    }
    env->ReleasePrimitiveArrayCritical(jchars, chars, JNI_ABORT);

    char out_rule[128];
    out_rule[0] = '\0';
    int match_len = engine_match_text(stack_buf, scan_len, out_rule, sizeof(out_rule));
    if (match_len > 0 && out_rule[0] != '\0') {
        return env->NewStringUTF(out_rule);
    }
    return nullptr;
}

JNIEXPORT jlong JNICALL
Java_com_blocker_NativeBlockerBridge_nativeHashChars(JNIEnv* env, jclass, jcharArray jchars, jint len) {
    if (len <= 0) return 0L;
    jchar* chars = (jchar*)env->GetPrimitiveArrayCritical(jchars, nullptr);
    if (!chars) return 0L;

    uint64_t hash = FNV_OFFSET_BASIS;
    for (jint i = 0; i < len; ++i) {
        hash ^= (uint64_t)((char)chars[i]);
        hash *= FNV_PRIME;
    }
    env->ReleasePrimitiveArrayCritical(jchars, chars, JNI_ABORT);
    return (jlong)hash;
}

JNIEXPORT jlong JNICALL
Java_com_blocker_NativeBlockerBridge_nativeEvaluateScreen(
    JNIEnv* env, jclass, jobject jbuffer, jint len) {
    if (!jbuffer || len < (jint)sizeof(ScreenEvalHeader)) {
        return (jlong)pack_verdict(ACTION_ALLOW, REASON_NONE, 0xFFFF, 0, 0);
    }
    const uint8_t* buf = (const uint8_t*)env->GetDirectBufferAddress(jbuffer);
    if (!buf) {
        return (jlong)pack_verdict(ACTION_ALLOW, REASON_NONE, 0xFFFF, 0, 0);
    }
    return (jlong)engine_evaluate_screen(buf, (size_t)len);
}

JNIEXPORT jboolean JNICALL
Java_com_blocker_NativeBlockerBridge_nativeCheckHudRateLimit(
    JNIEnv*, jclass, jint reason_id) {
    return engine_check_hud_rate_limit((uint8_t)reason_id) ? JNI_TRUE : JNI_FALSE;
}

// -----------------------------------------------------------------------------
// Battery-Friendly Local DNS Sinkhole Loop
// -----------------------------------------------------------------------------
void dns_sinkhole_poll_loop(int tun_fd, const char* upstream_dns_ip, int stop_pipe_fd) {
    int upstream_sock = socket(AF_INET, SOCK_DGRAM | SOCK_NONBLOCK, 0);
    struct sockaddr_in up_addr;
    memset(&up_addr, 0, sizeof(up_addr));
    up_addr.sin_family = AF_INET;
    up_addr.sin_port = htons(53);
    inet_pton(AF_INET, upstream_dns_ip, &up_addr.sin_addr);

    uint8_t pkt_buf[4096];
    struct pollfd fds[3];
    fds[0].fd = tun_fd;
    fds[0].events = POLLIN;
    fds[1].fd = stop_pipe_fd;
    fds[1].events = POLLIN;
    fds[2].fd = upstream_sock;
    fds[2].events = POLLIN;

    struct PendingQuery {
        uint16_t id;
        uint32_t client_ip;
        uint16_t client_port;
    };
    PendingQuery pending[64];
    size_t pending_idx = 0;
    memset(pending, 0, sizeof(pending));

    while (true) {
        int ret = poll(fds, 3, 2000);
        if (ret < 0) continue;
        if (fds[1].revents & POLLIN) break; // Stop signal

        // Handle incoming DNS request from virtual TUN interface
        if (fds[0].revents & POLLIN) {
            ssize_t nread = read(tun_fd, pkt_buf, sizeof(pkt_buf));
            if (nread > 28) {
                uint8_t ip_ver = (pkt_buf[0] >> 4) & 0x0F;
                uint8_t ip_proto = pkt_buf[9];
                if (ip_ver == 4 && ip_proto == 17) {
                    uint8_t ip_ihl = (pkt_buf[0] & 0x0F) * 4;
                    uint8_t* udp_ptr = pkt_buf + ip_ihl;
                    uint16_t dst_port = (uint16_t)((udp_ptr[2] << 8) | udp_ptr[3]);

                    if (dst_port == 53) {
                        uint8_t* dns_ptr = udp_ptr + 8;
                        size_t dns_len = (size_t)nread - (ip_ihl + 8);
                        if (dns_len >= 12) {
                            uint16_t qid = (uint16_t)((dns_ptr[0] << 8) | dns_ptr[1]);
                            uint32_t src_ip = *(uint32_t*)(pkt_buf + 12);
                            uint16_t src_port = *(uint16_t*)udp_ptr;

                            char qname[256];
                            size_t qname_idx = 0;
                            size_t ptr = 12;
                            bool valid_qname = true;

                            while (ptr < dns_len) {
                                uint8_t label_len = dns_ptr[ptr++];
                                if (label_len == 0) break;
                                if ((label_len & 0xC0) != 0 || ptr + label_len > dns_len || qname_idx + label_len + 1 >= sizeof(qname)) {
                                    valid_qname = false;
                                    break;
                                }
                                if (qname_idx > 0) qname[qname_idx++] = '.';
                                for (size_t l = 0; l < label_len; ++l) {
                                    char c = (char)dns_ptr[ptr++];
                                    if (c >= 'A' && c <= 'Z') c |= 0x20;
                                    qname[qname_idx++] = c;
                                }
                            }

                            if (valid_qname && qname_idx > 0) {
                                qname[qname_idx] = '\0';
                                char matched_rule[128];
                                if (engine_match_url(qname, qname_idx, matched_rule, sizeof(matched_rule))) {
                                    uint8_t resp_buf[512];
                                    size_t q_end = ptr + 4;
                                    if (q_end <= dns_len && ip_ihl + 8 + q_end + 16 <= sizeof(resp_buf)) {
                                        memcpy(resp_buf, pkt_buf, ip_ihl + 8);

                                        *(uint32_t*)(resp_buf + 12) = *(uint32_t*)(pkt_buf + 16);
                                        *(uint32_t*)(resp_buf + 16) = src_ip;

                                        *(uint16_t*)(resp_buf + ip_ihl) = htons(53);
                                        *(uint16_t*)(resp_buf + ip_ihl + 2) = src_port;

                                        uint8_t* r_dns = resp_buf + ip_ihl + 8;
                                        memcpy(r_dns, dns_ptr, q_end);
                                        r_dns[2] = 0x81;
                                        r_dns[3] = 0x80;
                                        r_dns[6] = 0x00; r_dns[7] = 0x01;
                                        r_dns[8] = 0x00; r_dns[9] = 0x00;
                                        r_dns[10] = 0x00; r_dns[11] = 0x00;

                                        uint8_t* ans = r_dns + q_end;
                                        ans[0] = 0xC0; ans[1] = 0x0C;
                                        ans[2] = 0x00; ans[3] = 0x01;
                                        ans[4] = 0x00; ans[5] = 0x01;
                                        ans[6] = 0x00; ans[7] = 0x00; ans[8] = 0x00; ans[9] = 0x3C;
                                        ans[10] = 0x00; ans[11] = 0x04;
                                        ans[12] = 0x00; ans[13] = 0x00; ans[14] = 0x00; ans[15] = 0x00;

                                        size_t total_payload = 8 + q_end + 16;
                                        *(uint16_t*)(resp_buf + ip_ihl + 4) = htons((uint16_t)total_payload);
                                        *(uint16_t*)(resp_buf + ip_ihl + 6) = 0;

                                        size_t total_ip_len = ip_ihl + total_payload;
                                        *(uint16_t*)(resp_buf + 2) = htons((uint16_t)total_ip_len);
                                        *(uint16_t*)(resp_buf + 10) = 0;
                                        *(uint16_t*)(resp_buf + 10) = htons(compute_ip_checksum(resp_buf, ip_ihl));

                                        write(tun_fd, resp_buf, total_ip_len);
                                    }
                                } else {
                                    pending[pending_idx].id = qid;
                                    pending[pending_idx].client_ip = src_ip;
                                    pending[pending_idx].client_port = src_port;
                                    pending_idx = (pending_idx + 1) % 64;

                                    sendto(upstream_sock, dns_ptr, dns_len, 0, (struct sockaddr*)&up_addr, sizeof(up_addr));
                                }
                            }
                        }
                    }
                }
            }
        }

        // Handle response from upstream DNS resolver
        if (fds[2].revents & POLLIN) {
            uint8_t up_resp[4096];
            ssize_t up_len = recv(upstream_sock, up_resp, sizeof(up_resp), 0);
            if (up_len >= 12) {
                uint16_t resp_id = (uint16_t)((up_resp[0] << 8) | up_resp[1]);
                for (size_t p = 0; p < 64; ++p) {
                    if (pending[p].id == resp_id && pending[p].client_ip != 0) {
                        uint8_t out_pkt[4200];
                        size_t ip_ihl = 20;
                        size_t total_payload = 8 + up_len;
                        size_t total_len = ip_ihl + total_payload;

                        memset(out_pkt, 0, ip_ihl);
                        out_pkt[0] = 0x45;
                        out_pkt[8] = 64;
                        out_pkt[9] = 17;
                        *(uint16_t*)(out_pkt + 2) = htons((uint16_t)total_len);
                        inet_pton(AF_INET, "10.255.255.1", out_pkt + 12);
                        *(uint32_t*)(out_pkt + 16) = pending[p].client_ip;
                        *(uint16_t*)(out_pkt + 10) = htons(compute_ip_checksum(out_pkt, ip_ihl));

                        *(uint16_t*)(out_pkt + ip_ihl) = htons(53);
                        *(uint16_t*)(out_pkt + ip_ihl + 2) = pending[p].client_port;
                        *(uint16_t*)(out_pkt + ip_ihl + 4) = htons((uint16_t)total_payload);
                        *(uint16_t*)(out_pkt + ip_ihl + 6) = 0;

                        memcpy(out_pkt + ip_ihl + 8, up_resp, up_len);
                        write(tun_fd, out_pkt, total_len);

                        pending[p].client_ip = 0;
                        break;
                    }
                }
            }
        }
    }
    close(upstream_sock);
}

JNIEXPORT void JNICALL
Java_com_blocker_BlockerVpnService_nativeStartDnsSinkhole(
    JNIEnv* env, jclass, jint tun_fd, jstring j_upstream_dns, jint stop_pipe_fd) {
    const char* upstream_ip = env->GetStringUTFChars(j_upstream_dns, nullptr);
    dns_sinkhole_poll_loop(tun_fd, upstream_ip ? upstream_ip : "1.1.1.1", stop_pipe_fd);
    if (upstream_ip) {
        env->ReleaseStringUTFChars(j_upstream_dns, upstream_ip);
    }
}

} // extern "C"
