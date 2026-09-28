import {
  GranularFocusKey, GranularFocusToggles,
  LOCK_KEYS, LockKey, Native, PermissionStatus, Schedule, SettingLock, Stats
} from '../native/BlockerNative';
import { CATEGORIES } from '../data/defaults';
import { USER_DEFAULT_WHITELIST } from '../data/userLists';
import { BlocklistEngine, normalizeEntry } from './BlocklistEngine';

export type ActionType =
  | 'protection_off' | 'shield_off' | 'delay' | 'rm_app' | 'rm_domain' | 'rm_keyword' | 'cat_off'
  | 'add_whitelist' | 'exempt_app'
  | 'focus_end' | 'focus_shorten' | 'focus_add_app' | 'schedule_update' | 'schedule_delete'
  | 'granular_focus_disable';
export interface SchedulePatch {
  label: string;
  startMin: number;
  endMin: number;
  enabled: boolean;
}
export interface Action {
  t: ActionType;
  v?: string;
  days?: number;
  n?: number;
  schedule?: SchedulePatch;
  label: string;
}
export interface PersistState {
  categories: Record<string, boolean>;
  customDomains: string[];
  customKeywords: string[];
  whitelist: string[];
  blockedApps: string[];
  exemptApps: string[];
  granularFocus?: GranularFocusToggles;
  queue: Partial<Record<LockKey, Action[]>>;
  onboarded: boolean;
}
export interface FocusState {
  active: boolean;
  until: number;
  remainingMs: number;
  apps: string[];
}
export const DEFAULT_GRANULAR: GranularFocusToggles = {
  block_fb_reels: false,
  block_insta_reels: false,
  block_insta_search: false,
  block_yt_shorts: false
};

export interface Snapshot {
  ready: boolean;
  state: PersistState;
  active: boolean;
  shield: boolean;
  delayDays: number;
  focus: FocusState;
  granularFocus: GranularFocusToggles;
  schedules: Schedule[];
  locks: Partial<Record<LockKey, SettingLock>>;
  perms: PermissionStatus | null;
  stats: Stats | null;
}
export type Outcome = 'applied' | 'queued' | 'restarted';

const EMPTY: PersistState = {
  categories: {}, customDomains: [], customKeywords: [], whitelist: [], blockedApps: [], exemptApps: [],
  granularFocus: DEFAULT_GRANULAR,
  queue: {}, onboarded: false
};

let snap: Snapshot = {
  ready: false, state: EMPTY, active: false, shield: true, delayDays: 1,
  focus: { active: false, until: 0, remainingMs: 0, apps: [] },
  granularFocus: DEFAULT_GRANULAR,
  schedules: [], locks: {}, perms: null, stats: null
};
const listeners = new Set<() => void>();
const emit = (patch: Partial<Snapshot>) => {
  snap = { ...snap, ...patch };
  listeners.forEach(l => l());
};
export const subscribe = (l: () => void) => {
  listeners.add(l);
  return () => {
    listeners.delete(l);
  };
};
export const getSnapshot = () => snap;

function persist(state: PersistState) {
  Native.saveState(JSON.stringify(state)).catch(() => {});
  emit({ state });
}
const catOn = (s: PersistState, id: string, def: boolean) => s.categories[id] ?? def;

export function effectiveLists(s: PersistState = snap.state) {
  const domains = new Set(s.customDomains);
  const keywords = new Set(s.customKeywords);
  const tlds = new Set<string>();
  for (const c of CATEGORIES) {
    if (!catOn(s, c.id, c.defaultOn)) continue;
    c.domains.forEach(d => domains.add(d));
    c.keywords.forEach(k => keywords.add(k));
    (c.tlds ?? []).forEach(t => tlds.add(t));
  }
  // Built-in protective phrases (e.g. "sex education") always apply; the UI only ever shows the
  // user's own additions in s.whitelist, so these never appear as removable entries there.
  const whitelist = new Set([...USER_DEFAULT_WHITELIST, ...s.whitelist]);
  return { domains: [...domains], keywords: [...keywords], tlds: [...tlds], whitelist: [...whitelist] };
}
export const buildEngine = (s: PersistState = snap.state) =>
  BlocklistEngine.from({ ...effectiveLists(s), packages: s.blockedApps });

async function sync(s: PersistState = snap.state) {
  const e = effectiveLists(s);
  await Native.setBlocklist(e.domains, e.keywords, e.tlds, e.whitelist);
  await Native.setBlockedApps(s.blockedApps);
  await Native.setExemptApps(s.exemptApps);
}

// ---------- lifecycle ----------

export async function init() {
  const raw = await Native.loadState();
  const state: PersistState = raw ? { ...EMPTY, ...JSON.parse(raw) } : EMPTY;
  emit({ state });
  try {
    await sync(state);
  } catch {
    // native side still holds entries pending a locked removal; keep native state
  }
  await refresh();
  emit({ ready: true });
}

export async function refresh() {
  const [perms, stats, delayDays, locks, schedules, granularFocus] = await Promise.all([
    Native.getPermissionStatus(), Native.getStats(), Native.getDelayDays(), Native.getSettingLocks(LOCK_KEYS),
    Native.getSchedules(), Native.getGranularFocusToggles().catch(() => DEFAULT_GRANULAR)
  ]);
  // a pending focus change is pointless once focus mode has ended by itself
  if (!stats.focusActive && (locks.focus?.pending || locks.focus?.open)) {
    await Native.cancelSettingChange('focus').catch(() => {});
    delete locks.focus;
  }
  // drop queued actions whose timer no longer exists (cancelled or applied elsewhere)
  const queue = { ...snap.state.queue };
  let dirty = false;
  for (const k of LOCK_KEYS) {
    const l = locks[k];
    if (queue[k]?.length && !l?.pending && !l?.open) {
      delete queue[k];
      dirty = true;
    }
  }
  if (dirty) persist({ ...snap.state, queue });
  emit({
    perms, stats, delayDays, locks, schedules, granularFocus, active: stats.active, shield: stats.shield,
    focus: { active: stats.focusActive, until: stats.focusUntil, remainingMs: stats.focusRemainingMs, apps: stats.focusApps }
  });
}

export function markOnboarded() {
  persist({ ...snap.state, onboarded: true });
}

// ---------- delay queue ----------

async function queueAction(key: LockKey, a: Action): Promise<Outcome> {
  const cur = snap.state.queue[key] ?? [];
  if (cur.some(x => x.t === a.t && x.v === a.v && x.days === a.days && x.n === a.n)) return 'queued';
  // a new change added to a running timer restarts it, so nothing can piggy-back on an old countdown
  const restarted = cur.length > 0;
  persist({ ...snap.state, queue: { ...snap.state.queue, [key]: [...cur, a] } });
  if (restarted) await Native.cancelSettingChange(key);
  await Native.requestSettingChange(key);
  await refresh();
  return restarted ? 'restarted' : 'queued';
}

function reduce(s: PersistState, a: Action): PersistState {
  switch (a.t) {
    case 'rm_app': return { ...s, blockedApps: s.blockedApps.filter(p => p !== a.v) };
    case 'rm_domain': return { ...s, customDomains: s.customDomains.filter(d => d !== a.v) };
    case 'rm_keyword': return { ...s, customKeywords: s.customKeywords.filter(k => k !== a.v) };
    case 'cat_off': return { ...s, categories: { ...s.categories, [a.v!]: false } };
    case 'add_whitelist': return { ...s, whitelist: [...new Set([...s.whitelist, a.v!])] };
    case 'exempt_app': return { ...s, exemptApps: [...new Set([...s.exemptApps, a.v!])] };
    default: return s;
  }
}

/** Removal-type change: immediate while protection is off, otherwise queued behind the delay timer. */
async function removal(key: LockKey, a: Action): Promise<Outcome> {
  if (!snap.active) {
    const s = reduce(snap.state, a);
    await sync(s);
    persist(s);
    return 'applied';
  }
  return queueAction(key, a);
}

export async function confirm(key: LockKey) {
  if (!snap.locks[key]?.open) await Native.confirmSettingChange(key);
  let s = snap.state;
  let focusApps = snap.focus.apps;
  for (const a of s.queue[key] ?? []) {
    if (a.t === 'protection_off') await Native.setProtectionActive(false);
    else if (a.t === 'shield_off') await Native.setShieldEnabled(false);
    else if (a.t === 'delay') await Native.setDelayDays(a.days!);
    else if (a.t === 'focus_end') await Native.endFocus();
    else if (a.t === 'focus_shorten') await Native.shortenFocus(a.n!);
    else if (a.t === 'schedule_update') {
      const p = a.schedule!;
      await Native.updateSchedule(a.v!, p.label, p.startMin, p.endMin, p.enabled).catch(() => {});
    } else if (a.t === 'schedule_delete') {
      await Native.deleteSchedule(a.v!).catch(() => {});
    } else if (a.t === 'focus_add_app') {
      focusApps = [...new Set([...focusApps, a.v!])];
      await Native.setFocusApps(focusApps);
    } else if (a.t === 'granular_focus_disable') {
      await Native.setGranularFocusToggle(a.v as GranularFocusKey, false).catch(() => {});
    } else s = reduce(s, a);
  }
  if (key === 'remove_apps') await sync(s);
  if (key === 'remove_blocklist' || key === 'whitelist') await sync(s);
  if (key === 'exempt_apps') await sync(s);
  const queue = { ...s.queue };
  delete queue[key];
  persist({ ...s, queue });
  await Native.cancelSettingChange(key);
  await refresh();
}

export async function cancel(key: LockKey) {
  await Native.cancelSettingChange(key);
  const queue = { ...snap.state.queue };
  delete queue[key];
  persist({ ...snap.state, queue });
  await refresh();
}

// ---------- settings ----------

export async function setProtection(on: boolean): Promise<Outcome> {
  if (on) {
    await sync();
    await Native.setProtectionActive(true);
    await refresh();
    return 'applied';
  }
  if (!snap.active) return 'applied';
  return queueAction('protection', { t: 'protection_off', label: 'Turn off protection' });
}

export async function setShield(on: boolean): Promise<Outcome> {
  if (on || !snap.active) {
    await Native.setShieldEnabled(on);
    await refresh();
    return 'applied';
  }
  return queueAction('shield', { t: 'shield_off', label: 'Turn off uninstall shield' });
}

export async function setDelay(days: number): Promise<Outcome> {
  if (days >= snap.delayDays || !snap.active) {
    await Native.setDelayDays(days);
    await refresh();
    return 'applied';
  }
  return queueAction('delay_duration', { t: 'delay', days, label: `Shorten delay to ${days} day(s)` });
}

// ---------- focus mode (one-off timer) ----------

export const MAX_FOCUS_APPS = 5;

export async function startFocus(minutes: number) {
  await Native.startFocus(minutes);
  await refresh();
}

/** Longer applies now; shorter waits for the delay timer. */
/** Longer applies now; shorter waits for the delay timer. Refreshes first so the extend/shorten
 *  decision (and the extend amount) is based on the real remaining time, not a stale poll from a
 *  few seconds ago \u2014 without this, a borderline tap could pick the wrong path entirely. */
export async function setFocusRemaining(minutes: number): Promise<Outcome> {
  await refresh();
  const remaining = snap.focus.remainingMs;
  const target = minutes * 60_000;
  if (target > remaining) {
    await Native.extendFocus(Math.ceil((target - remaining) / 60_000));
    await refresh();
    return 'applied';
  }
  return queueAction('focus', {
    t: 'focus_shorten', n: minutes, label: `Shorten focus to ${minutes} min (counted from confirmation)`
  });
}

export const endFocusEarly = () => queueAction('focus', { t: 'focus_end', label: 'End focus mode early' });

export async function addFocusApp(pkg: string, label: string): Promise<Outcome> {
  const cur = snap.focus.apps;
  if (cur.includes(pkg)) return 'applied';
  if (cur.length >= MAX_FOCUS_APPS) throw new Error(`Pick at most ${MAX_FOCUS_APPS} essential apps.`);
  if (snap.focus.active) {
    return queueAction('focus', { t: 'focus_add_app', v: pkg, label: `Allow ${label} during focus` });
  }
  await Native.setFocusApps([...cur, pkg]);
  await refresh();
  return 'applied';
}

export async function removeFocusApp(pkg: string) {
  await Native.setFocusApps(snap.focus.apps.filter(p => p !== pkg));
  await refresh();
}

// ---------- focus mode (daily schedules) ----------

const windowLen = (start: number, end: number) => (start <= end ? end - start : 1440 - start + end);

export async function addSchedule(label: string, startMin: number, endMin: number, enabled: boolean) {
  await Native.addSchedule(label, startMin, endMin, enabled);
  await refresh();
}

/** Enabling, widening, or editing an inactive schedule is immediate. Shrinking or disabling one
 *  that's currently running waits for the delay timer. */
/** Enabling, widening, or editing an inactive schedule is immediate. Shrinking or disabling one
 *  that's currently running waits for the delay timer. Refreshes first so "is this active/shrinking"
 *  reflects the real current time rather than a stale poll. */
export async function updateSchedule(id: string, patch: SchedulePatch): Promise<Outcome> {
  await refresh();
  const old = snap.schedules.find(x => x.id === id);
  if (!old) throw new Error('That schedule no longer exists.');
  const shrinking = old.activeNow && (!patch.enabled || windowLen(patch.startMin, patch.endMin) < windowLen(old.startMin, old.endMin));
  if (!shrinking) {
    await Native.updateSchedule(id, patch.label, patch.startMin, patch.endMin, patch.enabled);
    await refresh();
    return 'applied';
  }
  return queueAction('focus', { t: 'schedule_update', v: id, schedule: patch, label: `Update "${old.label}"` });
}

export async function deleteSchedule(id: string): Promise<Outcome> {
  await refresh();
  const old = snap.schedules.find(x => x.id === id);
  if (!old) return 'applied';
  if (!old.activeNow) {
    await Native.deleteSchedule(id);
    await refresh();
    return 'applied';
  }
  return queueAction('focus', { t: 'schedule_delete', v: id, label: `Delete "${old.label}"` });
}

// ---------- blocklist, whitelist & apps ----------

export async function addApp(pkg: string) {
  const s = { ...snap.state, blockedApps: [...new Set([...snap.state.blockedApps, pkg])] };
  await sync(s);
  persist(s);
}
export const removeApp = (pkg: string, label: string) =>
  removal('remove_apps', { t: 'rm_app', v: pkg, label: `Unblock ${label}` });

export async function addEntry(text: string): Promise<boolean> {
  const e = normalizeEntry(text);
  if (!e) return false;
  const s = { ...snap.state };
  if (e.kind === 'domain') s.customDomains = [...new Set([...s.customDomains, e.value])];
  else s.customKeywords = [...new Set([...s.customKeywords, e.value])];
  await sync(s);
  persist(s);
  return true;
}
export const removeEntry = (kind: 'domain' | 'keyword', value: string) =>
  removal('remove_blocklist', { t: kind === 'domain' ? 'rm_domain' : 'rm_keyword', v: value, label: `Remove "${value}"` });

export async function setCategory(id: string, on: boolean): Promise<Outcome> {
  if (on) {
    const s = { ...snap.state, categories: { ...snap.state.categories, [id]: true } };
    await sync(s);
    persist(s);
    return 'applied';
  }
  const title = CATEGORIES.find(c => c.id === id)?.title ?? id;
  return removal('remove_blocklist', { t: 'cat_off', v: id, label: `Disable ${title}` });
}

export const isCategoryOn = (id: string) => {
  const c = CATEGORIES.find(x => x.id === id);
  return catOn(snap.state, id, c?.defaultOn ?? false);
};

/** Whitelisting a word/phrase creates an exception, so adding one is gated while protection is on;
 *  removing one only tightens things, so it's immediate. Only the user's own entries ever show up
 *  here \u2014 built-in protective phrases are applied in the background and never listed. */
export async function addWhitelist(phrase: string): Promise<Outcome> {
  const p = phrase.trim().toLowerCase().replace(/\s+/g, ' ');
  if (!p || snap.state.whitelist.includes(p) || USER_DEFAULT_WHITELIST.includes(p)) return 'applied';
  if (!snap.active) {
    const s = { ...snap.state, whitelist: [...snap.state.whitelist, p] };
    await sync(s);
    persist(s);
    return 'applied';
  }
  return queueAction('whitelist', { t: 'add_whitelist', v: p, label: `Whitelist "${p}"` });
}

export async function removeWhitelist(phrase: string) {
  const s = { ...snap.state, whitelist: snap.state.whitelist.filter(w => w !== phrase) };
  await sync(s);
  persist(s);
}

// ---------- screen-scan exemptions ----------

/** Exempting an app from screen monitoring weakens protection, so it's gated while active;
 *  removing the exemption (resuming monitoring) is immediate. */
export async function exemptApp(pkg: string, label: string): Promise<Outcome> {
  if (snap.state.exemptApps.includes(pkg)) return 'applied';
  if (!snap.active) {
    const s = { ...snap.state, exemptApps: [...snap.state.exemptApps, pkg] };
    await sync(s);
    persist(s);
    return 'applied';
  }
  return queueAction('exempt_apps', { t: 'exempt_app', v: pkg, label: `Stop scanning ${label}` });
}

export async function unexemptApp(pkg: string) {
  const s = { ...snap.state, exemptApps: snap.state.exemptApps.filter(p => p !== pkg) };
  await sync(s);
  persist(s);
}

// ---------- granular focus toggles ----------

const GRANULAR_LABEL: Record<GranularFocusKey, string> = {
  block_fb_reels:    'Disable Facebook Reels block',
  block_insta_reels: 'Disable Instagram Reels block',
  block_insta_search:'Disable Instagram Search block',
  block_yt_shorts:   'Disable YouTube Shorts block'
};

export async function setGranularFocusToggle(key: GranularFocusKey, on: boolean): Promise<Outcome> {
  // Enabling is always immediate (strengthens protection).
  // Disabling is gated behind the delay timer when protection is active.
  if (on || !snap.active) {
    await Native.setGranularFocusToggle(key, on);
    const current = snap.granularFocus ?? DEFAULT_GRANULAR;
    const next = { ...current, [key]: on };
    const s = { ...snap.state, granularFocus: next };
    persist(s);
    emit({ granularFocus: next });
    await refresh();
    return 'applied';
  }
  // Queue a delayed disable
  return queueAction('granular_focus', {
    t: 'granular_focus_disable', v: key, label: GRANULAR_LABEL[key]
  });
}
