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
#pragma pack(pop)

bool engine_init(const char* file_path);
void engine_close(void);
bool engine_is_loaded(void);

// Matching APIs - Zero heap allocations
bool engine_match_domain_hash(uint64_t hash);
uint64_t engine_hash_fnv1a(const char* str, size_t len);
int engine_match_url(const char* url, size_t len, char* out_rule, size_t max_out_len);
int engine_match_text(const char* text, size_t len, char* out_rule, size_t max_out_len);

// DNS Sinkhole Engine
void dns_sinkhole_poll_loop(int tun_fd, const char* upstream_dns_ip, int stop_pipe_fd);

#ifdef __cplusplus
}
#endif

#endif // BLOCKER_ENGINE_H
