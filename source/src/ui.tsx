import React, { useEffect, useRef, useSyncExternalStore } from 'react';
import { Animated, Easing, Image, LayoutAnimation, Modal, Pressable, StyleProp, StyleSheet, Switch, Text, View, ViewStyle, useColorScheme } from 'react-native';
import {
  AlertTriangle, Ban, Check, CheckCircle2, ChevronRight, Circle, Clock, Loader2, LucideIcon, Plus, ShieldOff, XCircle
} from 'lucide-react-native';
import type { LockKey } from './native/BlockerNative';
import { useNow, useProtection } from './hooks/useProtection';
import { cancel, confirm } from './services/ProtectionManager';
import { APP_ICON_DATA_URI } from './data/appIcon';
import { BG_PATTERN_DATA_URI } from './data/bgPattern';

// Purple-on-dark, glassmorphism-inspired. Cards use a translucent purple tint over the base
// background (true backdrop blur needs a native blur library, which isn't wired into this build).
const DARK = {
  bg: '#0C0A16', bgAlt: '#120E22', card: 'rgba(139,92,246,0.08)', cardBorder: 'rgba(199,175,255,0.16)',
  glass: 'rgba(255,255,255,0.04)', text: '#F3F0FF', sub: '#9C93B8', border: 'rgba(199,175,255,0.14)',
  accent: '#8B5CF6', accentSoft: 'rgba(139,92,246,0.18)', danger: '#F5455C', dangerSoft: 'rgba(245,69,92,0.16)',
  ok: '#2ED88A', okSoft: 'rgba(46,216,138,0.16)', warn: '#F5A623', warnSoft: 'rgba(245,166,35,0.16)'
};
const LIGHT: typeof DARK = {
  bg: '#F5F3FC', bgAlt: '#EDE9FB', card: 'rgba(139,92,246,0.06)', cardBorder: 'rgba(124,58,237,0.16)',
  glass: 'rgba(255,255,255,0.5)', text: '#181321', sub: '#655E78', border: 'rgba(124,58,237,0.14)',
  accent: '#7C3AED', accentSoft: 'rgba(124,58,237,0.12)', danger: '#DC2A42', dangerSoft: 'rgba(220,42,66,0.1)',
  ok: '#149A63', okSoft: 'rgba(20,154,99,0.12)', warn: '#C77700', warnSoft: 'rgba(199,119,0,0.12)'
};
export type Theme = typeof DARK;
export const useTheme = (): Theme => (useColorScheme() === 'light' ? LIGHT : DARK);
export const errMsg = (e: unknown) => (e as Error)?.message ?? String(e);
export { Icons } from './icons';
export type { IconName } from './icons';

/**
 * Meaningful cybersecurity and lock background texture with very low opacity.
 * Symbolizes lock, blockage, and technical security without any AI aesthetics.
 * Zero CPU / animation overhead — completely hardware-accelerated static rendering.
 */
export function AppBackground() {
  const t = useTheme();
  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      <Image
        source={{ uri: BG_PATTERN_DATA_URI }}
        style={[
          StyleSheet.absoluteFillObject,
          {
            width: '100%',
            height: '100%',
            opacity: 0.16,
          },
        ]}
        resizeMode="cover"
      />
    </View>
  );
}

export const AmbientBackground = AppBackground;

/** App-wide top bar: uncropped icon + "Blocker" in the theme accent color. */
export function Header() {
  const t = useTheme();
  return (
    <View style={{
      flexDirection: 'row', alignItems: 'center', gap: 10, paddingHorizontal: 16, paddingTop: 10, paddingBottom: 6
    }}>
      <Image source={{ uri: APP_ICON_DATA_URI }} style={{ width: 30, height: 30 }} resizeMode="contain" />
      <Text style={{ color: t.accent, fontSize: 20, fontWeight: '800', letterSpacing: 0.3 }}>Blocker</Text>
    </View>
  );
}

/** Floating action button, bottom-right, for adding a new item (e.g. a focus schedule). */
export function Fab({ onPress, icon: Icon = Plus }: { onPress: () => void; icon?: LucideIcon }) {
  const t = useTheme();
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [{
        position: 'absolute', right: 20, bottom: 20, width: 56, height: 56, borderRadius: 28,
        backgroundColor: t.accent, alignItems: 'center', justifyContent: 'center',
        shadowColor: t.accent, shadowOpacity: 0.6, shadowRadius: 10, shadowOffset: { width: 0, height: 4 }, elevation: 8,
        transform: [{ scale: pressed ? 0.92 : 1 }], opacity: pressed ? 0.9 : 1
      }]}>
      <Icon size={26} color="#fff" strokeWidth={2.5} />
    </Pressable>
  );
}

/** Drop-in themed replacement for React Native's Alert.alert (same signature), so every
 *  confirmation in the app matches the purple/dark UI instead of the OS's plain default dialog.
 *  Mount <AlertHost/> once near the root; calling showAlert() anywhere then shows the themed modal. */
export type AlertButton = { text: string; onPress?: () => void; style?: 'default' | 'cancel' | 'destructive' };
interface AlertConfig { title: string; message?: string; buttons: AlertButton[] }

let alertState: AlertConfig | null = null;
const alertListeners = new Set<() => void>();
const emitAlert = () => alertListeners.forEach(l => l());

export function showAlert(title: string, message?: string, buttons: AlertButton[] = [{ text: 'OK' }]) {
  alertState = { title, message, buttons };
  emitAlert();
}
function dismissAlert() {
  alertState = null;
  emitAlert();
}

export function AlertHost() {
  const t = useTheme();
  const cfg = useSyncExternalStore(
    (cb: () => void) => {
      alertListeners.add(cb);
      return () => alertListeners.delete(cb);
    },
    () => alertState
  );
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const transY = useRef(new Animated.Value(18)).current;

  useEffect(() => {
    if (cfg) {
      fadeAnim.setValue(0);
      transY.setValue(18);
      Animated.parallel([
        Animated.timing(fadeAnim, { toValue: 1, duration: 180, useNativeDriver: true }),
        Animated.timing(transY, { toValue: 0, duration: 180, useNativeDriver: true }),
      ]).start();
    }
  }, [cfg]);

  if (!cfg) return null;
  const stacked = cfg.buttons.length > 2;
  return (
    <Modal transparent visible animationType="fade" statusBarTranslucent onRequestClose={dismissAlert}>
      <View style={{ flex: 1, backgroundColor: 'rgba(6,4,14,0.68)', alignItems: 'center', justifyContent: 'center', padding: 24 }}>
        <Animated.View style={{
          width: '100%', maxWidth: 380, backgroundColor: t.bgAlt, borderColor: t.cardBorder, borderWidth: 1,
          borderRadius: 24, padding: 20, shadowColor: '#000', shadowOpacity: 0.4, shadowRadius: 20,
          shadowOffset: { width: 0, height: 8 }, elevation: 12,
          opacity: fadeAnim,
          transform: [{ translateY: transY }]
        }}>
          <Text style={{ color: t.text, fontSize: 18, fontWeight: '800' }}>{cfg.title}</Text>
          {!!cfg.message && <Text style={{ color: t.sub, fontSize: 14, marginTop: 8, lineHeight: 20 }}>{cfg.message}</Text>}
          <View style={{ flexDirection: stacked ? 'column' : 'row', gap: 8, marginTop: 18 }}>
            {cfg.buttons.map((b, i) => (
              <View key={i} style={stacked ? undefined : { flex: 1 }}>
                <Btn
                  label={b.text}
                  kind={b.style === 'destructive' ? 'danger' : b.style === 'cancel' ? 'ghost' : 'primary'}
                  onPress={() => {
                    dismissAlert();
                    b.onPress?.();
                  }}
                />
              </View>
            ))}
          </View>
        </Animated.View>
      </View>
    </Modal>
  );
}

export function fmt(ms: number) {
  const s = Math.ceil(ms / 1000);
  const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
  const p = (n: number) => String(n).padStart(2, '0');
  return d > 0 ? `${d}d ${p(h)}h ${p(m)}m` : `${p(h)}:${p(m)}:${p(sec)}`;
}

export function Card({ children, style }: { children: React.ReactNode; style?: StyleProp<ViewStyle> }) {
  const t = useTheme();
  return (
    <View
      style={[
        {
          backgroundColor: t.card, borderColor: t.cardBorder, borderWidth: 1, borderRadius: 20, padding: 16,
          marginBottom: 12, shadowColor: '#000', shadowOpacity: 0.18, shadowRadius: 10, shadowOffset: { width: 0, height: 4 },
          overflow: 'hidden'
        },
        style
      ]}>
      {children}
    </View>
  );
}

export function Title({
  children, size = 17, icon: Icon, tone = 'accent'
}: { children: React.ReactNode; size?: number; icon?: LucideIcon; tone?: 'accent' | 'ok' | 'danger' | 'warn' }) {
  const t = useTheme();
  const color = tone === 'ok' ? t.ok : tone === 'danger' ? t.danger : tone === 'warn' ? t.warn : t.accent;
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, flexShrink: 1 }}>
      {Icon && <Icon size={size + 2} color={color} strokeWidth={2.25} />}
      <Text style={{ color: t.text, fontSize: size, fontWeight: '700', flexShrink: 1 }}>{children}</Text>
    </View>
  );
}
export function Sub({ children }: { children: React.ReactNode }) {
  const t = useTheme();
  return <Text style={{ color: t.sub, fontSize: 13, marginTop: 2, lineHeight: 18 }}>{children}</Text>;
}

export function Btn({ label, onPress, kind = 'primary', disabled, icon: Icon }: {
  label: string; onPress: () => void; kind?: 'primary' | 'ghost' | 'danger'; disabled?: boolean; icon?: LucideIcon;
}) {
  const t = useTheme();
  const bg = kind === 'primary' ? t.accent : kind === 'danger' ? t.danger : t.glass;
  const fg = kind === 'ghost' ? t.text : '#fff';
  return (
    <Pressable
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [{
        backgroundColor: bg, opacity: disabled ? 0.4 : pressed ? 0.8 : 1, borderRadius: 14, paddingVertical: 11, paddingHorizontal: 16,
        flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8,
        borderWidth: kind === 'ghost' ? 1 : 0, borderColor: t.border,
        transform: [{ scale: pressed ? 0.98 : 1 }]
      }]}>
      {Icon && <Icon size={16} color={fg} strokeWidth={2.5} />}
      <Text style={{ color: fg, fontWeight: '700' }}>{label}</Text>
    </Pressable>
  );
}

const toneColor = (t: Theme, tone: 'ok' | 'warn' | 'danger' | 'idle') =>
  tone === 'ok' ? t.ok : tone === 'warn' ? t.warn : tone === 'danger' ? t.danger : t.sub;
const toneSoft = (t: Theme, tone: 'ok' | 'warn' | 'danger' | 'idle') =>
  tone === 'ok' ? t.okSoft : tone === 'warn' ? t.warnSoft : tone === 'danger' ? t.dangerSoft : t.glass;
const toneIcon = (tone: 'ok' | 'warn' | 'danger' | 'idle'): LucideIcon =>
  tone === 'ok' ? CheckCircle2 : tone === 'warn' ? AlertTriangle : tone === 'danger' ? XCircle : Circle;

export function Badge({ label, tone, icon = true }: { label: string; tone: 'ok' | 'warn' | 'danger' | 'idle'; icon?: boolean }) {
  const t = useTheme();
  const c = toneColor(t, tone);
  const Ico = toneIcon(tone);
  return (
    <View style={{
      flexDirection: 'row', alignItems: 'center', gap: 5, backgroundColor: toneSoft(t, tone),
      borderColor: c, borderWidth: 1, borderRadius: 999, paddingHorizontal: 10, paddingVertical: 4,
      flexShrink: 0
    }}>
      {icon && <Ico size={12} color={c} strokeWidth={3} />}
      <Text style={{ color: c, fontSize: 12, fontWeight: '700' }}>{label}</Text>
    </View>
  );
}

/** A round icon-only toggle button (e.g. "stop scanning this app"), lit up in `tone` color when active. */
export function IconToggle({
  Icon, active, onPress, tone = 'accent'
}: { Icon: LucideIcon; active: boolean; onPress: () => void; tone?: 'accent' | 'danger' }) {
  const t = useTheme();
  const color = tone === 'danger' ? t.danger : t.accent;
  const soft = tone === 'danger' ? t.dangerSoft : t.accentSoft;
  return (
    <Pressable
      onPress={onPress}
      hitSlop={8}
      style={({ pressed }) => [{
        width: 34, height: 34, borderRadius: 17, alignItems: 'center', justifyContent: 'center',
        backgroundColor: active ? soft : t.glass, borderWidth: 1, borderColor: active ? color : t.border,
        shadowColor: active ? color : 'transparent', shadowOpacity: active ? 0.9 : 0,
        shadowRadius: active ? 10 : 0, shadowOffset: { width: 0, height: 0 }, elevation: active ? 6 : 0,
        opacity: pressed ? 0.7 : 1, transform: [{ scale: pressed ? 0.9 : 1 }]
      }]}>
      <Icon size={17} color={active ? color : t.sub} strokeWidth={2.25} />
    </Pressable>
  );
}

export function Row({ label, sub, right, icon: Icon }: {
  label: string; sub?: string; right?: React.ReactNode; icon?: LucideIcon;
}) {
  const t = useTheme();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 9, gap: 12 }}>
      {Icon && (
        <View style={{
          width: 30, height: 30, borderRadius: 10, backgroundColor: t.accentSoft, alignItems: 'center', justifyContent: 'center'
        }}>
          <Icon size={16} color={t.accent} strokeWidth={2.25} />
        </View>
      )}
      <View style={{ flex: 1 }}>
        <Text style={{ color: t.text, fontWeight: '600' }} numberOfLines={1}>{label}</Text>
        {sub && <Text style={{ color: t.sub, fontSize: 12 }} numberOfLines={1}>{sub}</Text>}
      </View>
      {right}
      {!right && <ChevronRight size={16} color={t.sub} />}
    </View>
  );
}

/** Countdown badge + confirm/cancel controls for a delayed setting change. Renders nothing when idle. */
export function LockBar({ lockKey }: { lockKey: LockKey }) {
  const t = useTheme();
  const p = useProtection();
  const now = useNow();
  const lock = p.locks[lockKey];
  const queued = p.state.queue[lockKey] ?? [];
  const visible = !!lock && (lock.pending || (lock.open && queued.length > 0));
  useEffect(() => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
  }, [visible, lock?.ready]);
  if (!visible) return null;
  const remaining = lock.pending ? Math.max(0, lock.unlockAt - now) : 0;
  const ready = remaining === 0;
  const color = ready ? t.ok : t.warn;
  const run = (fn: () => Promise<void>) => fn().catch(e => showAlert('Blocker', errMsg(e)));
  return (
    <View style={{
      marginTop: 12, padding: 14, borderRadius: 16, backgroundColor: ready ? t.okSoft : t.warnSoft,
      borderColor: color, borderWidth: 1
    }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        {ready ? <CheckCircle2 size={16} color={color} /> : <Clock size={16} color={color} />}
        <Text style={{ color, fontWeight: '700', fontVariant: ['tabular-nums'], flexShrink: 1 }}>
          {ready ? 'Timer complete: confirm to apply' : `Locked: ${fmt(remaining)} remaining`}
        </Text>
      </View>
      {queued.map((a, i) => (
        <Text key={i} style={{ color: t.sub, fontSize: 12, marginTop: 4 }}>• {a.label}</Text>
      ))}
      <View style={{ flexDirection: 'row', gap: 8, marginTop: 10 }}>
        {ready && <View style={{ flex: 1 }}><Btn label="Confirm change" icon={Check} onPress={() => run(() => confirm(lockKey))} /></View>}
        <View style={{ flex: 1 }}><Btn label="Cancel" kind="ghost" icon={Ban} onPress={() => run(() => cancel(lockKey))} /></View>
      </View>
    </View>
  );
}

export function AnimatedToggleRow({
  label, sub, value, onValueChange, icon: Icon, disabled
}: {
  label: string; sub?: string; value: boolean; onValueChange: (v: boolean) => void; icon?: LucideIcon; disabled?: boolean;
}) {
  const t = useTheme();
  const animAlpha = useRef(new Animated.Value(1)).current;
  const animY = useRef(new Animated.Value(0)).current;

  const handleToggle = (nextVal: boolean) => {
    animAlpha.setValue(0.75);
    animY.setValue(nextVal ? -2 : 2);
    Animated.parallel([
      Animated.timing(animAlpha, { toValue: 1, duration: 160, useNativeDriver: true }),
      Animated.timing(animY, { toValue: 0, duration: 160, useNativeDriver: true }),
    ]).start();
    onValueChange(nextVal);
  };

  return (
    <Animated.View style={{
      opacity: animAlpha,
      transform: [{ translateY: animY }],
      flexDirection: 'row', alignItems: 'center', paddingVertical: 10, gap: 12
    }}>
      {Icon && (
        <View style={{
          width: 32, height: 32, borderRadius: 10,
          backgroundColor: value ? t.accentSoft : t.glass,
          borderWidth: value ? 1 : 0, borderColor: t.accent,
          shadowColor: value ? t.accent : 'transparent', shadowOpacity: value ? 0.9 : 0,
          shadowRadius: value ? 10 : 0, shadowOffset: { width: 0, height: 0 }, elevation: value ? 6 : 0,
          alignItems: 'center', justifyContent: 'center'
        }}>
          <Icon size={16} color={value ? '#FFFFFF' : t.sub} strokeWidth={value ? 2.6 : 2.25} />
        </View>
      )}
      <View style={{ flex: 1 }}>
        <Text style={{ color: t.text, fontWeight: '600' }} numberOfLines={1}>{label}</Text>
        {sub && <Text style={{ color: t.sub, fontSize: 12, marginTop: 1 }} numberOfLines={1}>{sub}</Text>}
      </View>
      <Switch
        disabled={disabled}
        value={value}
        onValueChange={handleToggle}
        trackColor={{ true: t.accent, false: t.border }}
      />
    </Animated.View>
  );
}

export { ShieldOff, Loader2 };
