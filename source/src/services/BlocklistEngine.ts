export type EntryKind = 'domain' | 'keyword';
export interface Entry {
  kind: EntryKind;
  value: string;
}
export interface Match {
  blocked: boolean;
  reason?: EntryKind | 'tld' | 'whitelisted';
  rule?: string;
}

const DOMAIN_RE = /^(https?:\/\/)?(www\.)?[a-z0-9-]+(\.[a-z0-9-]+)+\/?$/;

/** Classifies user input: a bare domain/URL becomes a domain rule, anything else a keyword. */
export function normalizeEntry(raw: string): Entry | null {
  const t = raw.trim().toLowerCase().replace(/\s+/g, ' ');
  if (!t) return null;
  if (DOMAIN_RE.test(t)) {
    return { kind: 'domain', value: t.replace(/^https?:\/\//, '').replace(/^www\./, '').replace(/\/$/, '') };
  }
  return { kind: 'keyword', value: t };
}

export function hostOf(input: string): string | null {
  const h = input
    .trim()
    .toLowerCase()
    .replace(/^[a-z][a-z0-9+.-]*:\/\//, '')
    .split(/[/?#]/)[0]
    .replace(/:\d+$/, '')
    .replace(/^www\./, '');
  return h.includes('.') && !/\s/.test(h) ? h : null;
}

/** Whole-word scan with whitelist phrases shielding any keyword they contain (mirrors the Android service). */
function wholeWordHit(text: string, keywords: Set<string>, whitelist: string[]): string | null {
  const safe: [number, number][] = [];
  for (const w of whitelist) {
    if (!w) continue;
    let from = 0;
    for (;;) {
      const i = text.indexOf(w, from);
      if (i < 0) break;
      safe.push([i, i + w.length]);
      from = i + 1;
    }
  }

  const isWord = (c: string) => /[a-z0-9]/i.test(c);

  // 1. Check multi-word / delimited keywords first (e.g. "live cam", "adult chat")
  for (const kw of keywords) {
    if (!kw.includes(' ') && !kw.includes('-') && !kw.includes('_')) continue;
    let from = 0;
    while (from < text.length) {
      const idx = text.indexOf(kw, from);
      if (idx < 0) break;
      const endIdx = idx + kw.length;
      const startBoundary = idx === 0 || !isWord(text[idx - 1]);
      const endBoundary = endIdx === text.length || !isWord(text[endIdx]);
      if (startBoundary && endBoundary) {
        const isSafe = safe.some(([a, b]) => !(endIdx <= a || idx >= b));
        if (!isSafe) return kw;
      }
      from = idx + 1;
    }
  }

  // 2. Check single-word keywords with token scanning
  let i = 0;
  while (i < text.length) {
    if (!isWord(text[i])) {
      i++;
      continue;
    }
    let j = i;
    while (j < text.length && isWord(text[j])) j++;
    const token = text.slice(i, j);
    if (keywords.has(token) && !safe.some(([a, b]) => a <= i && j <= b)) return token;
    i = j;
  }
  return null;
}

export class BlocklistEngine {
  private constructor(
    private domains: Set<string>,
    private keywords: Set<string>,
    private tlds: string[],
    private whitelist: string[],
    private packages: Set<string>
  ) {}

  static from(cfg: {
    domains: string[]; keywords: string[]; tlds?: string[]; whitelist?: string[]; packages?: string[];
  }): BlocklistEngine {
    return new BlocklistEngine(
      new Set(cfg.domains.map(d => d.toLowerCase())),
      new Set(cfg.keywords.map(k => k.toLowerCase())),
      (cfg.tlds ?? []).map(t => t.toLowerCase()),
      (cfg.whitelist ?? []).map(w => w.toLowerCase()),
      new Set(cfg.packages ?? [])
    );
  }

  /** Checks a URL/host with substring matching (as the real domain list intends), or free text word-by-word. */
  matchUrl(input: string): Match {
    const text = input.trim().toLowerCase();
    const host = hostOf(text);
    if (host) {
      let d = host;
      for (;;) {
        if (this.domains.has(d)) return { blocked: true, reason: 'domain', rule: d };
        const i = d.indexOf('.');
        if (i < 0) break;
        d = d.slice(i + 1);
      }
      const tld = this.tlds.find(t => host.endsWith(t));
      if (tld) return { blocked: true, reason: 'tld', rule: tld };
      const norm = host.replace(/[-_]/g, '');
      const kw = [...this.keywords].find(k => norm.includes(k));
      if (kw) return { blocked: true, reason: 'keyword', rule: kw };
    }
    const hit = wholeWordHit(text, this.keywords, this.whitelist);
    return hit ? { blocked: true, reason: 'keyword', rule: hit } : { blocked: false };
  }

  matchPackage(pkg: string): boolean {
    return this.packages.has(pkg);
  }
}