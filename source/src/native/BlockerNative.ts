import { NativeModules, Platform } from 'react-native';

export type PermissionKind = 'accessibility' | 'overlay' | 'usageAccess' | 'deviceAdmin';
export type LockKey =
  | 'protection' | 'shield' | 'delay_duration' | 'remove_apps' | 'remove_blocklist'
  | 'whitelist' | 'exempt_apps' | 'focus' | 'granular_focus' | 'schedule';
export const LOCK_KEYS: LockKey[] = [
  'protection', 'shield', 'delay_duration', 'remove_apps', 'remove_blocklist',
  'whitelist', 'exempt_apps', 'focus', 'granular_focus', 'schedule'
];

export interface PermissionStatus {
  accessibility: boolean;
  overlay: boolean;
  usageAccess: boolean;
  deviceAdmin: boolean;
  adminLost: boolean;
}
export interface InstalledApp {
  packageName: string;
  label: string;
  isSystem: boolean;
  category: string;
  blocked: boolean;
  scanExempt: boolean;
}
export interface SettingLock {
  pending: boolean;
  unlockAt: number;
  remainingMs: number;
  ready: boolean;
  open: boolean;
}
export interface Stats {
  sitesBlockedToday: number;
  appsBlockedToday: number;
  tamperAttemptsToday: number;
  screenBlocksToday: number;
  active: boolean;
  shield: boolean;
  focusActive: boolean;
  focusUntil: number;
  focusRemainingMs: number;
  focusApps: string[];
}
export interface GranularFocusToggles {
  block_fb_reels: boolean;
  block_insta_reels: boolean;
  block_insta_search: boolean;
  block_yt_shorts: boolean;
}
export type GranularFocusKey = keyof GranularFocusToggles;

export interface Schedule {
  id: string;
  label: string;
  startMin: number;
  endMin: number;
  enabled: boolean;
  activeNow: boolean;
}

interface BlockerNativeSpec {
  getPermissionStatus(): Promise<PermissionStatus>;
  /** Resolves true if the direct settings screen opened, false if a fallback screen opened instead. */
  openPermissionSettings(kind: PermissionKind): Promise<boolean>;
  getInstalledApps(includeSystem: boolean): Promise<InstalledApp[]>;
  getAppIcon(pkg: string, size: number): Promise<string>;
  setProtectionActive(active: boolean): Promise<boolean>;
  setShieldEnabled(enabled: boolean): Promise<boolean>;
  setBlocklist(domains: string[], keywords: string[], tlds: string[], whitelist: string[]): Promise<boolean>;
  setBlockedApps(packages: string[]): Promise<boolean>;
  setExemptApps(packages: string[]): Promise<boolean>;
  setDelayDays(days: number): Promise<number>;
  getDelayDays(): Promise<number>;
  requestSettingChange(key: LockKey): Promise<number>;
  getSettingLocks(keys: LockKey[]): Promise<Partial<Record<LockKey, SettingLock>>>;
  confirmSettingChange(key: LockKey): Promise<number>;
  cancelSettingChange(key: LockKey): Promise<boolean>;
  getStats(): Promise<Stats>;
  startFocus(minutes: number): Promise<boolean>;
  extendFocus(minutes: number): Promise<boolean>;
  shortenFocus(minutes: number): Promise<boolean>;
  endFocus(): Promise<boolean>;
  setFocusApps(packages: string[]): Promise<boolean>;
  getSchedules(): Promise<Schedule[]>;
  addSchedule(label: string, startMin: number, endMin: number, enabled: boolean): Promise<string>;
  updateSchedule(id: string, label: string, startMin: number, endMin: number, enabled: boolean): Promise<boolean>;
  deleteSchedule(id: string): Promise<boolean>;
  /** Shows Android's own clock-style AM/PM time picker; resolves minutes since midnight, or rejects on cancel. */
  pickTime(initialMinute: number): Promise<number>;
  launchApp(pkg: string): Promise<boolean>;
  loadState(): Promise<string | null>;
  saveState(json: string): Promise<boolean>;
  setGranularFocusToggle(key: GranularFocusKey, enabled: boolean): Promise<boolean>;
  getGranularFocusToggles(): Promise<GranularFocusToggles>;
}

export const isSupported = Platform.OS === 'android' && !!NativeModules.BlockerNative;
export const Native = NativeModules.BlockerNative as BlockerNativeSpec;
