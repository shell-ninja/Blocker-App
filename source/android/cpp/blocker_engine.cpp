#include "blocker_engine.h"
#include <jni.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <android/log.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <poll.h>
#include <vector>
#include <string>
#include <queue>
#include <algorithm>

#define TAG "BlockerEngineNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

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

uint64_t engine_hash_fnv1a(const char* str, size_t len) {
    uint64_t hash = FNV_OFFSET_BASIS;
    for (size_t i = 0; i < len; ++i) {
        hash ^= (uint64_t)((unsigned char)str[i]);
        hash *= FNV_PRIME;
    }
    return hash;
}

bool engine_init(const char* file_path) {
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
    return g_header != nullptr;
}

// Branchless binary search over sorted 64-bit domain hashes
bool engine_match_domain_hash(uint64_t hash) {
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
    if (!g_header || g_header->ac_node_count == 0 || len == 0) return 0;

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
            // Whole-word boundary check
            bool left_boundary = (i + 1 == matched->match_len) ||
                !((text[i - matched->match_len] >= 'a' && text[i - matched->match_len] <= 'z') ||
                  (text[i - matched->match_len] >= '0' && text[i - matched->match_len] <= '9'));

            bool right_boundary = (i + 1 == len) ||
                !((text[i + 1] >= 'a' && text[i + 1] <= 'z') ||
                  (text[i + 1] >= '0' && text[i + 1] <= '9'));

            if (left_boundary && right_boundary) {
                if (out_rule && max_out_len > 0) {
                    const char* src = g_str_pool + matched->match_str_offset;
                    size_t cpy = matched->match_len < (max_out_len - 1) ? matched->match_len : (max_out_len - 1);
                    memcpy(out_rule, src, cpy);
                    out_rule[cpy] = '\0';
                }
                return (int)matched->match_len;
            }
        }
    }
    return 0;
}

// In-place zero-allocation URL parsing and multi-stage matching
int engine_match_url(const char* url, size_t len, char* out_rule, size_t max_out_len) {
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
// Offline Binary Compiler (invoked by React Native NativeModule on list updates)
// -----------------------------------------------------------------------------
struct BuildNode {
    int fail = 0;
    uint32_t match_str_offset = 0;
    uint16_t match_len = 0;
    std::vector<std::pair<uint8_t, int>> children;
};

extern "C" JNIEXPORT jboolean JNICALL
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
extern "C" {

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

    char stack_buf[1024];
    size_t scan_len = (size_t)len < sizeof(stack_buf) ? (size_t)len : sizeof(stack_buf);
    for (size_t i = 0; i < scan_len; ++i) {
        stack_buf[i] = (char)chars[i];
    }
    env->ReleasePrimitiveArrayCritical(jchars, chars, JNI_ABORT);

    return engine_match_url(stack_buf, scan_len, nullptr, 0);
}

JNIEXPORT jint JNICALL
Java_com_blocker_NativeBlockerBridge_nativeMatchText(JNIEnv* env, jclass, jcharArray jchars, jint len) {
    if (len <= 0) return 0;
    jchar* chars = (jchar*)env->GetPrimitiveArrayCritical(jchars, nullptr);
    if (!chars) return 0;

    char stack_buf[2048];
    size_t scan_len = (size_t)len < sizeof(stack_buf) ? (size_t)len : sizeof(stack_buf);
    for (size_t i = 0; i < scan_len; ++i) {
        stack_buf[i] = (char)chars[i];
    }
    env->ReleasePrimitiveArrayCritical(jchars, chars, JNI_ABORT);

    return engine_match_text(stack_buf, scan_len, nullptr, 0);
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

// -----------------------------------------------------------------------------
// Battery-Friendly Local DNS Sinkhole Loop
// -----------------------------------------------------------------------------
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

    // Cache of in-flight DNS query headers for upstream response translation:
    // map query ID to (client_ip, client_port)
    struct PendingQuery {
        uint16_t id;
        uint32_t client_ip;
        uint16_t client_port;
    };
    PendingQuery pending[64];
    size_t pending_idx = 0;
    memset(pending, 0, sizeof(pending));

    while (true) {
        int ret = poll(fds, 3, 2000); // 2s timeout
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

                            // Parse QNAME
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
                                    // Synthesize RFC 1035 A-Record Response with 0.0.0.0 in < 0.1ms
                                    uint8_t resp_buf[512];
                                    size_t q_end = ptr + 4; // Skip QTYPE and QCLASS
                                    if (q_end <= dns_len && ip_ihl + 8 + q_end + 16 <= sizeof(resp_buf)) {
                                        // Copy IP & UDP headers
                                        memcpy(resp_buf, pkt_buf, ip_ihl + 8);

                                        // Swap IP src and dst
                                        *(uint32_t*)(resp_buf + 12) = *(uint32_t*)(pkt_buf + 16);
                                        *(uint32_t*)(resp_buf + 16) = src_ip;

                                        // Swap UDP ports
                                        *(uint16_t*)(resp_buf + ip_ihl) = htons(53);
                                        *(uint16_t*)(resp_buf + ip_ihl + 2) = src_port;

                                        // Build DNS Response
                                        uint8_t* r_dns = resp_buf + ip_ihl + 8;
                                        memcpy(r_dns, dns_ptr, q_end);
                                        r_dns[2] = 0x81; // Response, recursion desired
                                        r_dns[3] = 0x80; // No error
                                        r_dns[6] = 0x00; r_dns[7] = 0x01; // Answer count = 1
                                        r_dns[8] = 0x00; r_dns[9] = 0x00; // NS count = 0
                                        r_dns[10] = 0x00; r_dns[11] = 0x00; // AR count = 0

                                        // Append Answer
                                        uint8_t* ans = r_dns + q_end;
                                        ans[0] = 0xC0; ans[1] = 0x0C; // Pointer to QNAME
                                        ans[2] = 0x00; ans[3] = 0x01; // Type A
                                        ans[4] = 0x00; ans[5] = 0x01; // Class IN
                                        ans[6] = 0x00; ans[7] = 0x00; ans[8] = 0x00; ans[9] = 0x3C; // TTL 60s
                                        ans[10] = 0x00; ans[11] = 0x04; // RDLength = 4
                                        ans[12] = 0x00; ans[13] = 0x00; ans[14] = 0x00; ans[15] = 0x00; // 0.0.0.0

                                        size_t total_payload = 8 + q_end + 16;
                                        *(uint16_t*)(resp_buf + ip_ihl + 4) = htons((uint16_t)total_payload);
                                        *(uint16_t*)(resp_buf + ip_ihl + 6) = 0; // Checksum 0

                                        size_t total_ip_len = ip_ihl + total_payload;
                                        *(uint16_t*)(resp_buf + 2) = htons((uint16_t)total_ip_len);
                                        *(uint16_t*)(resp_buf + 10) = 0;
                                        *(uint16_t*)(resp_buf + 10) = htons(compute_ip_checksum(resp_buf, ip_ihl));

                                        write(tun_fd, resp_buf, total_ip_len);
                                    }
                                } else {
                                    // Forward query to upstream DNS
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
                        out_pkt[0] = 0x45; // IPv4, IHL 5
                        out_pkt[8] = 64;   // TTL
                        out_pkt[9] = 17;   // UDP
                        *(uint16_t*)(out_pkt + 2) = htons((uint16_t)total_len);
                        inet_pton(AF_INET, "10.255.255.1", out_pkt + 12);
                        *(uint32_t*)(out_pkt + 16) = pending[p].client_ip;
                        *(uint16_t*)(out_pkt + 10) = htons(compute_ip_checksum(out_pkt, ip_ihl));

                        // UDP Header
                        *(uint16_t*)(out_pkt + ip_ihl) = htons(53);
                        *(uint16_t*)(out_pkt + ip_ihl + 2) = pending[p].client_port;
                        *(uint16_t*)(out_pkt + ip_ihl + 4) = htons((uint16_t)total_payload);
                        *(uint16_t*)(out_pkt + ip_ihl + 6) = 0;

                        memcpy(out_pkt + ip_ihl + 8, up_resp, up_len);
                        write(tun_fd, out_pkt, total_len);

                        pending[p].client_ip = 0; // Clear slot
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
