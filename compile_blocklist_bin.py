#!/usr/bin/env python3
"""
Compiles blocklists (domains, keywords) into a flat binary format (blocklist.bin)
compatible with the C++ mmap engine (blocker_engine.cpp).
"""
import struct
import sys
import os
import re
from pathlib import Path
from collections import deque

FNV_OFFSET_BASIS = 14695981039346656037
FNV_PRIME = 1099511628211

def hash_fnv1a(s: str) -> int:
    h = FNV_OFFSET_BASIS
    for b in s.encode('utf-8'):
        h ^= b
        h = (h * FNV_PRIME) & 0xFFFFFFFFFFFFFFFF
    return h

class BuildNode:
    def __init__(self):
        self.fail = 0
        self.match_str_offset = 0
        self.match_len = 0
        self.children = {} # ch -> target_idx

def compile_binary(domains, keywords, out_path):
    # 1. Domain Hashes (Sorted uint64 array)
    domain_hashes = sorted(list(set(hash_fnv1a(d.lower().strip()) for d in domains if d.strip())))

    # 2. Build Aho-Corasick Trie in memory
    trie = [BuildNode()] # root at 0
    str_pool = bytearray(b"\x00") # offset 0 is empty string

    for kw in keywords:
        kw = kw.lower().strip()
        if not kw:
            continue
        kw_bytes = kw.encode('utf-8')
        curr = 0
        for b in kw_bytes:
            if b not in trie[curr].children:
                next_idx = len(trie)
                trie.append(BuildNode())
                trie[curr].children[b] = next_idx
            curr = trie[curr].children[b]
        
        trie[curr].match_str_offset = len(str_pool)
        trie[curr].match_len = len(kw_bytes)
        str_pool.extend(kw_bytes + b"\x00")

    # 3. Compute Failure Links (BFS)
    q = deque()
    for ch, target in trie[0].children.items():
        trie[target].fail = 0
        q.append(target)

    while q:
        u = q.popleft()
        for ch, v in trie[u].children.items():
            f = trie[u].fail
            while f > 0 and ch not in trie[f].children:
                f = trie[f].fail
            if f == 0 and ch not in trie[0].children:
                trie[v].fail = 0
            else:
                trie[v].fail = trie[f].children.get(ch, 0)

            # Propagate matches
            if trie[v].match_str_offset == 0 and trie[trie[v].fail].match_str_offset != 0:
                trie[v].match_str_offset = trie[trie[v].fail].match_str_offset
                trie[v].match_len = trie[trie[v].fail].match_len

            q.append(v)

    # 4. Flatten Nodes and Edges
    # FlatAcNode: fail_idx (4), match_str_offset (4), edge_offset (4), edge_count (2), match_len (2) = 16 bytes
    # FlatAcEdge: uint32 (ch << 24 | target & 0x00FFFFFF) = 4 bytes
    flat_nodes = []
    flat_edges = []

    for i, node in enumerate(trie):
        edge_offset = len(flat_edges)
        edge_count = len(node.children)
        for ch, target in sorted(node.children.items()):
            edge_val = ((ch & 0xFF) << 24) | (target & 0x00FFFFFF)
            flat_edges.append(edge_val)

        flat_nodes.append((node.fail, node.match_str_offset, edge_offset, edge_count, node.match_len))

    # 5. Write Binary File
    # Header format:
    # magic (4), version (4), domain_count (4), domain_offset (4),
    # ac_node_count (4), ac_node_offset (4), ac_edge_count (4), ac_edge_offset (4),
    # str_pool_offset (4), str_pool_size (4), checksum (4) = 44 bytes
    header_size = 44
    domain_offset = header_size
    domain_size = len(domain_hashes) * 8

    ac_node_offset = domain_offset + domain_size
    ac_node_size = len(flat_nodes) * 16

    ac_edge_offset = ac_node_offset + ac_node_size
    ac_edge_size = len(flat_edges) * 4

    str_pool_offset = ac_edge_offset + ac_edge_size
    str_pool_size = len(str_pool)

    header = struct.pack(
        "<IIIIIIIIIII",
        0x424C4F43, # 'BLOC'
        1,          # version
        len(domain_hashes),
        domain_offset,
        len(flat_nodes),
        ac_node_offset,
        len(flat_edges),
        ac_edge_offset,
        str_pool_offset,
        str_pool_size,
        0           # checksum
    )

    out_p = Path(out_path)
    out_p.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "wb") as f:
        f.write(header)
        for h in domain_hashes:
            f.write(struct.pack("<Q", h))
        for fn in flat_nodes:
            f.write(struct.pack("<IIIHH", fn[0], fn[1], fn[2], fn[3], fn[4]))
        for fe in flat_edges:
            f.write(struct.pack("<I", fe))
        f.write(bytes(str_pool))

    print(f"Compiled {len(domain_hashes)} domains and {len(keywords)} keywords into {out_path} ({os.path.getsize(out_path)} bytes)")

if __name__ == "__main__":
    from import_lists import parse_structured, parse_simple, RETIRED_KEYWORDS
    here = Path(__file__).resolve().parent
    d1, k1, _ = parse_structured(here / "blocklists" / "block-list.md")
    d2, k2 = parse_simple(here / "blocklists" / "my_list.txt")
    domains = sorted(set(d1 + d2))
    keywords = sorted(set(k1 + k2) - RETIRED_KEYWORDS)

    out_asset = here / "source" / "android" / "assets" / "blocklist.bin"
    compile_binary(domains, keywords, out_asset)
