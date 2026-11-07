#ifndef BLOCKER_ENGINE_H
#define BLOCKER_ENGINE_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#define BLOCKER_MAGIC 0x424C4F43 // 'BLOC'
#define BLOCKER_VERSION 1

#define SCREEN_EVAL_MAGIC 0x424C4B52 // 'BLKR'

#pragma pack(push, 1)
struct BlocklistHeader {
    uint32_t magic;
    uint32_t version;
    uint32_t domain_count;
    uint32_t domain_offset;
    uint32_t ac_node_count;
    uint32_t ac_node_offset;
    uint32_t ac_edge_count;
    uint32_t ac_edge_offset;
    uint32_t str_pool_offset;
    uint32_t str_pool_size;
    uint32_t checksum;
};

// 16-byte cache-aligned flat node
struct FlatAcNode {
    uint32_t fail_idx;
    uint32_t match_str_offset; // 0 if non-terminal
    uint32_t edge_offset;      // Offset into edge array
    uint16_t edge_count;
    uint16_t match_len;
};

// 4-byte packed edge (ch in upper 8 bits, target node in lower 24 bits)
struct FlatAcEdge {
    uint32_t data; // (ch << 24) | (target_node_idx & 0x00FFFFFF)
};

// Flat buffer layout for Screen Evaluation JNI
struct ScreenEvalHeader {
    uint32_t magic;         // 0x424C4B52 ('BLKR')
    uint16_t node_count;    // Number of serialized nodes (capped at 180)
    uint16_t event_type;    // AccessibilityEvent type
    uint32_t screen_width;  // Pixels
    uint32_t screen_height; // Pixels
    uint32_t config_flags;  // Bitmask
    uint16_t pkg_len;
    uint16_t cls_len;
    uint16_t self_pkg_len;
    uint16_t self_lbl_len;
};

struct SerializedNodeHeader {
    uint16_t left;   // 0..1000 permille
    uint16_t top;    // 0..1000 permille
    uint16_t right;  // 0..1000 permille
    uint16_t bottom; // 0..1000 permille
    uint32_t flags;
    uint16_t text_len;
    uint16_t cd_len;
    uint16_t id_len;
    uint16_t cls_len;
};
#pragma pack(pop)

// Node Flags
#define NODE_FLAG_SELECTED            (1 << 0)
#define NODE_FLAG_CHECKED             (1 << 1)
#define NODE_FLAG_CLICKABLE           (1 << 2)
#define NODE_FLAG_SCROLLABLE          (1 << 3)
#define NODE_FLAG_FOCUSED             (1 << 4)
#define NODE_FLAG_VISIBLE             (1 << 5)
#define NODE_FLAG_STATE_DESC_SELECTED (1 << 6)

// Config Flags
#define CONFIG_FLAG_SHIELD_ACTIVE      (1 << 0)
#define CONFIG_FLAG_BLOCK_FB_REELS     (1 << 1)
#define CONFIG_FLAG_BLOCK_INSTA_REELS  (1 << 2)
#define CONFIG_FLAG_BLOCK_INSTA_SEARCH (1 << 3)
#define CONFIG_FLAG_BLOCK_YT_SHORTS    (1 << 4)
#define CONFIG_FLAG_ONBOARDING_ARMED   (1 << 5)
#define CONFIG_FLAG_BLOCK_DATE_TIME    (1 << 6)
#define CONFIG_FLAG_BLOCK_SCHED_POWER  (1 << 7)
#define CONFIG_FLAG_ADMIN_ACTIVE       (1 << 8)

// Action Codes
#define ACTION_ALLOW            0
#define ACTION_BLOCK_APP        1
#define ACTION_BLOCK_REELS      2
#define ACTION_TAMPER_DETECTED  3
#define ACTION_CLICK_NODE       4

// Reason IDs
#define REASON_NONE                  0
#define REASON_APP_INFO_TAMPER       1
#define REASON_UNINSTALL_TAMPER      2
#define REASON_DEV_OPTIONS_TAMPER    3
#define REASON_DEVICE_ADMIN_TAMPER   4
#define REASON_SPECIAL_ACCESS_TAMPER 5
#define REASON_FB_REELS              6
#define REASON_INSTA_REELS           7
#define REASON_YT_SHORTS             8
#define REASON_DATE_TIME_TAMPER      9
#define REASON_RECENTS_POPUP_TAMPER  10

bool engine_init(const char* file_path);
void engine_close(void);
bool engine_is_loaded(void);

// Matching APIs - Zero heap allocations
bool engine_match_domain_hash(uint64_t hash);
uint64_t engine_hash_fnv1a(const char* str, size_t len);
int engine_match_url(const char* url, size_t len, char* out_rule, size_t max_out_len);
int engine_match_text(const char* text, size_t len, char* out_rule, size_t max_out_len);

// Screen Evaluation Engine (C++20 Zero-Copy Flat Buffer Evaluation)
uint64_t engine_evaluate_screen(const uint8_t* buffer, size_t buffer_len);

// Rate Limiting API (Monotonic Debouncing)
bool engine_check_hud_rate_limit(uint8_t reason_id);

// DNS Sinkhole Engine
void dns_sinkhole_poll_loop(int tun_fd, const char* upstream_dns_ip, int stop_pipe_fd);

#ifdef __cplusplus
}
#endif

#endif // BLOCKER_ENGINE_H
