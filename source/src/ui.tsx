import React, { useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import {
  Animated,
  Easing,
  Image,
  LayoutAnimation,
  Modal,
  Pressable,
  StyleProp,
  StyleSheet,
  Switch,
  Text,
  TextStyle,
  View,
  ViewStyle,
  useColorScheme,
} from 'react-native';
import Svg, { Circle, Defs, Pattern, Rect } from 'react-native-svg';
import {
  AlertTriangle,
  Ban,
  Check,
  CheckCircle2,
  ChevronRight,
  Circle as CircleIcon,
  Clock,
  Loader2,
  LucideIcon,
  Plus,
  Shield,
  ShieldCheck,
  ShieldOff,
  X,
  XCircle,
} from 'lucide-react-native';
import type { LockKey } from './native/BlockerNative';
import { useNow, useProtection } from './hooks/useProtection';
import { cancel, confirm } from './services/ProtectionManager';
import { APP_ICON_DATA_URI } from './data/appIcon';
import { BG_PATTERN_DATA_URI } from './data/bgPattern';

/**
 * Obsidian Bento Cyber-Security Theme Tokens
 * Deep obsidian-midnight (#070913), dark slate-navy squircle cards (#0E1322 to #13192B),
 * luminous emerald-mint (#10B981 / #00E676), electric purple/violet (#7C3AED / #9333EA),
 * crisp white headings (#FFFFFF), and cool muted slate-gray (#8B97B0).
 */
export const DARK = {
  bg: '#070913',
  bgAlt: '#0E1322',
  card: '#0E1322',
  cardSurface: '#121727',
  cardBorder: '#1E2640',
  cardBorderGlow: 'rgba(124, 58, 237, 0.35)',
  glass: 'rgba(255, 255, 255, 0.04)',
  glassBorder: 'rgba(255, 255, 255, 0.08)',
  text: '#FFFFFF',
  sub: '#8B97B0',
  border: '#1E2640',
  accent: '#7C3AED',
  accentGlow: '#9333EA',
  accentSoft: 'rgba(124, 58, 237, 0.16)',
  accentWell: '#18122B',
  accentWellBorder: 'rgba(124, 58, 237, 0.40)',
  ok: '#10B981',
  okGlow: '#00E676',
  okSoft: 'rgba(16, 185, 129, 0.14)',
  okBorder: 'rgba(16, 185, 129, 0.45)',
  okWell: '#07241A',
  okWellBorder: 'rgba(16, 185, 129, 0.35)',
  warn: '#F59E0B',
  warnSoft: 'rgba(245, 158, 11, 0.15)',
  warnBorder: 'rgba(245, 158, 11, 0.35)',
  danger: '#EF4444',
  dangerSoft: 'rgba(239, 68, 68, 0.15)',
  dangerBorder: 'rgba(239, 68, 68, 0.35)',
};

export const LIGHT: typeof DARK = {
  bg: '#070913',
  bgAlt: '#0E1322',
  card: '#0E1322',
  cardSurface: '#121727',
  cardBorder: '#1E2640',
  cardBorderGlow: 'rgba(124, 58, 237, 0.35)',
  glass: 'rgba(255, 255, 255, 0.04)',
  glassBorder: 'rgba(255, 255, 255, 0.08)',
  text: '#FFFFFF',
  sub: '#8B97B0',
  border: '#1E2640',
  accent: '#7C3AED',
  accentGlow: '#9333EA',
  accentSoft: 'rgba(124, 58, 237, 0.16)',
  accentWell: '#18122B',
  accentWellBorder: 'rgba(124, 58, 237, 0.40)',
  ok: '#10B981',
  okGlow: '#00E676',
  okSoft: 'rgba(16, 185, 129, 0.14)',
  okBorder: 'rgba(16, 185, 129, 0.45)',
  okWell: '#07241A',
  okWellBorder: 'rgba(16, 185, 129, 0.35)',
  warn: '#F59E0B',
  warnSoft: 'rgba(245, 158, 11, 0.15)',
  warnBorder: 'rgba(245, 158, 11, 0.35)',
  danger: '#EF4444',
  dangerSoft: 'rgba(239, 68, 68, 0.15)',
  dangerBorder: 'rgba(239, 68, 68, 0.35)',
};

export type Theme = typeof DARK;
export const useTheme = (): Theme => DARK;
export const errMsg = (e: unknown) => (e as Error)?.message ?? String(e);
export { Icons } from './icons';
export type { IconName } from './icons';

/**
 * Tactile spring press wrapper for physics-based scale response.
 * Scales to 0.96x on press with a crisp spring rebound on release.
 */
export function PressableScale({
  children,
  onPress,
  disabled,
  style,
  contentStyle,
  activeScale = 0.96,
  hitSlop,
}: {
  children: React.ReactNode;
  onPress?: () => void;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
  contentStyle?: StyleProp<ViewStyle>;
  activeScale?: number;
  hitSlop?: number | { top?: number; left?: number; bottom?: number; right?: number };
}) {
  const scale = useRef(new Animated.Value(1)).current;

  const handlePressIn = () => {
    Animated.spring(scale, {
      toValue: activeScale,
      useNativeDriver: true,
      speed: 30,
      bounciness: 4,
    }).start();
  };

  const handlePressOut = () => {
    Animated.spring(scale, {
      toValue: 1,
      useNativeDriver: true,
      speed: 25,
      bounciness: 6,
    }).start();
  };

  return (
    <Pressable
      disabled={disabled}
      onPress={onPress}
      onPressIn={handlePressIn}
      onPressOut={handlePressOut}
      hitSlop={hitSlop}
      style={style}>
      <Animated.View style={[{ transform: [{ scale }] }, contentStyle]}>
        {children}
      </Animated.View>
    </Pressable>
  );
}

/**
 * Deep obsidian canvas layered with a subtle, low-opacity violet/indigo dot-matrix grid pattern.
 * Hardware-accelerated with zero CPU overhead.
 */
export const DotMatrixBackground = React.memo(function DotMatrixBackground() {
  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      <Svg width="100%" height="100%" style={StyleSheet.absoluteFill}>
        <Defs>
          <Pattern id="dotGrid" width="24" height="24" patternUnits="userSpaceOnUse">
            <Circle cx="12" cy="12" r="1.15" fill="#7C3AED" opacity="0.22" />
          </Pattern>
        </Defs>
        <Rect width="100%" height="100%" fill="url(#dotGrid)" />
      </Svg>
    </View>
  );
});

export const AppBackground = React.memo(function AppBackground() {
  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      <View style={[StyleSheet.absoluteFill, { backgroundColor: DARK.bg }]} />
      <DotMatrixBackground />
    </View>
  );
});

export const AmbientBackground = AppBackground;

/**
 * Luminous Status Pill Badge
 * Supports breathing glow animation on active states (e.g. 'Fortress Live', 'Airtight Lock', 'Healthy').
 */
export function StatusPillBadge({
  label,
  tone = 'ok',
  icon: showIcon = true,
  breathing = false,
  customIcon,
  size = 'md',
}: {
  label: string;
  tone?: 'ok' | 'warn' | 'danger' | 'accent' | 'idle';
  icon?: boolean;
  breathing?: boolean;
  customIcon?: LucideIcon;
  size?: 'sm' | 'md' | 'lg';
}) {
  const t = useTheme();
  const pulseAnim = useRef(new Animated.Value(1)).current;

  useEffect(() => {
    if (!breathing) {
      pulseAnim.setValue(1);
      return;
    }
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(pulseAnim, {
          toValue: 0.65,
          duration: 1200,
          easing: Easing.inOut(Easing.ease),
          useNativeDriver: true,
        }),
        Animated.timing(pulseAnim, {
          toValue: 1,
          duration: 1200,
          easing: Easing.inOut(Easing.ease),
          useNativeDriver: true,
        }),
      ])
    );
    loop.start();
    return () => loop.stop();
  }, [breathing]);

  const color =
    tone === 'ok'
      ? t.ok
      : tone === 'accent'
      ? t.accent
      : tone === 'warn'
      ? t.warn
      : tone === 'danger'
      ? t.danger
      : t.sub;

  const bg =
    tone === 'ok'
      ? t.okSoft
      : tone === 'accent'
      ? t.accentSoft
      : tone === 'warn'
      ? t.warnSoft
      : tone === 'danger'
      ? t.dangerSoft
      : 'rgba(255, 255, 255, 0.04)';

  const border =
    tone === 'ok'
      ? t.okBorder
      : tone === 'accent'
      ? t.accentWellBorder
      : tone === 'warn'
      ? t.warnBorder
      : tone === 'danger'
      ? t.dangerBorder
      : t.cardBorder;

  const IconComp = customIcon
    ? customIcon
    : tone === 'ok'
    ? Check
    : tone === 'warn'
    ? AlertTriangle
    : tone === 'danger'
    ? X
    : CircleIcon;

  const padV = size === 'sm' ? 3 : size === 'lg' ? 6 : 4.5;
  const padH = size === 'sm' ? 8 : size === 'lg' ? 14 : 10;
  const fSize = size === 'sm' ? 11 : size === 'lg' ? 13 : 12;

  return (
    <Animated.View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: 5,
        backgroundColor: bg,
        borderColor: border,
        borderWidth: 1,
        borderRadius: 999,
        paddingHorizontal: padH,
        paddingVertical: padV,
        opacity: breathing ? pulseAnim : 1,
        shadowColor: color,
        shadowOpacity: breathing ? 0.4 : 0,
        shadowRadius: 8,
        shadowOffset: { width: 0, height: 0 },
        elevation: breathing ? 3 : 0,
        flexShrink: 0,
      }}>
      {showIcon && <IconComp size={size === 'sm' ? 11 : 12} color={color} strokeWidth={2.8} />}
      <Text
        style={{
          color,
          fontSize: fSize,
          fontWeight: '700',
          letterSpacing: 0.1,
        }}>
        {label}
      </Text>
    </Animated.View>
  );
}

export const Badge = StatusPillBadge;

/**
 * Top App Header matching reference UI:
 * - Rounded squircle purple icon well with glowing shield
 * - Bold white "Blocker" title with subtle electric violet highlight
 * - "Fortress Live" luminous emerald status pill on the right
 */
export function Header({ subtitle }: { subtitle?: string }) {
  const p = useProtection();
  const isLive = p.active || p.shield;

  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        paddingHorizontal: 18,
        paddingTop: 12,
        paddingBottom: 10,
      }}>
      {/* Left: App Logo + Name */}
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <View
          style={{
            width: 42,
            height: 42,
            borderRadius: 13,
            backgroundColor: '#16122C',
            borderColor: '#7C3AED',
            borderWidth: 1.5,
            alignItems: 'center',
            justifyContent: 'center',
            shadowColor: '#7C3AED',
            shadowOpacity: 0.5,
            shadowRadius: 12,
            shadowOffset: { width: 0, height: 0 },
            elevation: 6,
            overflow: 'hidden',
          }}>
          <Image
            source={{ uri: APP_ICON_DATA_URI }}
            style={{ width: 34, height: 34, borderRadius: 8 }}
            resizeMode="contain"
          />
        </View>
        <View>
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <Text
              style={{
                color: '#FFFFFF',
                fontSize: 24,
                fontWeight: '800',
                letterSpacing: -0.5,
              }}>
              Blocker
            </Text>
            <View
              style={{
                width: 6,
                height: 6,
                borderRadius: 3,
                backgroundColor: isLive ? '#10B981' : '#8B97B0',
                marginLeft: 4,
                marginBottom: 10,
              }}
            />
          </View>
          {subtitle && (
            <Text style={{ color: '#8B97B0', fontSize: 11.5, marginTop: -2, fontWeight: '500' }}>
              {subtitle}
            </Text>
          )}
        </View>
      </View>

      {/* Right: Live Status Pill */}
      <StatusPillBadge
        label={isLive ? 'Fortress Live' : 'Protection Paused'}
        tone={isLive ? 'ok' : 'idle'}
        breathing={isLive}
        customIcon={isLive ? Check : CircleIcon}
      />
    </View>
  );
}

/**
 * 3D Bento Squircle Card Surface
 * Dark slate-navy surface (#0E1322 to #13192B, 18-20dp corner radius)
 * Crisp 1dp inner/border highlights.
 * Active cards support emerald breathing pulse.
 */
export function GlowCard({
  children,
  style,
  active = false,
  tone = 'default',
  onPress,
}: {
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  active?: boolean;
  tone?: 'default' | 'ok' | 'accent' | 'danger';
  onPress?: () => void;
}) {
  const t = useTheme();

  const borderColor =
    active || tone === 'ok'
      ? t.okBorder
      : tone === 'accent'
      ? t.accentWellBorder
      : tone === 'danger'
      ? t.dangerBorder
      : t.cardBorder;

  const bg =
    active || tone === 'ok'
      ? '#0E1726'
      : tone === 'accent'
      ? '#131126'
      : t.card;

  const cardContent = (
    <View
      style={[
        styles.bentoCard,
        {
          backgroundColor: bg,
          borderColor,
          borderWidth: 1,
        },
        active && {
          shadowColor: t.ok,
          shadowOpacity: 0.25,
          shadowRadius: 12,
          shadowOffset: { width: 0, height: 0 },
          elevation: 5,
        },
        style,
      ]}>
      {children}
    </View>
  );

  if (onPress) {
    return (
      <PressableScale onPress={onPress}>
        {cardContent}
      </PressableScale>
    );
  }

  return cardContent;
}

export const Card = GlowCard;

/**
 * Live Rolling Counter for Numbers
 * Smooth animated transition whenever metrics change.
 */
export function RollingCounter({
  value,
  style,
}: {
  value: number;
  style?: StyleProp<TextStyle>;
}) {
  const [displayVal, setDisplayVal] = useState(value);
  const animVal = useRef(new Animated.Value(value)).current;

  useEffect(() => {
    Animated.timing(animVal, {
      toValue: value,
      duration: 350,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: false,
    }).start();

    const listener = animVal.addListener(({ value: v }) => {
      setDisplayVal(Math.round(v));
    });

    return () => animVal.removeListener(listener);
  }, [value]);

  return (
    <Text style={style}>
      {displayVal}
    </Text>
  );
}

/**
 * 2x2 Bento Stat Card Component
 * Crisp tabular white number, purple squircle icon well in top right,
 * and cool muted slate-gray label.
 */
export function BentoStatCard({
  value,
  label,
  icon: Icon,
  onPress,
}: {
  value: number;
  label: string;
  icon: LucideIcon;
  onPress?: () => void;
}) {
  const t = useTheme();

  return (
    <PressableScale
      onPress={onPress}
      style={{
        flexGrow: 1,
        flexBasis: '47%',
        backgroundColor: t.card,
        borderColor: t.cardBorder,
        borderWidth: 1,
        borderRadius: 18,
        padding: 16,
        shadowColor: '#000',
        shadowOpacity: 0.25,
        shadowRadius: 8,
        shadowOffset: { width: 0, height: 2 },
        elevation: 3,
      }}>
      <View style={{ flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between' }}>
        <RollingCounter
          value={value}
          style={{
            color: '#FFFFFF',
            fontSize: 28,
            fontWeight: '800',
            letterSpacing: -0.5,
            fontVariant: ['tabular-nums'],
          }}
        />
        <View
          style={{
            width: 34,
            height: 34,
            borderRadius: 11,
            backgroundColor: t.accentWell,
            borderColor: t.accentWellBorder,
            borderWidth: 1,
            alignItems: 'center',
            justifyContent: 'center',
            shadowColor: t.accent,
            shadowOpacity: 0.35,
            shadowRadius: 6,
            shadowOffset: { width: 0, height: 0 },
            elevation: 2,
          }}>
          <Icon size={16} color="#A78BFA" strokeWidth={2.4} />
        </View>
      </View>
      <Text
        style={{
          color: t.sub,
          fontSize: 12.5,
          marginTop: 10,
          fontWeight: '600',
          letterSpacing: 0.1,
        }}
        numberOfLines={1}>
        {label}
      </Text>
    </PressableScale>
  );
}

/**
 * Custom Spring-Animated Cyber Switch
 * Smooth spring transition, emerald green track when active, crisp white thumb.
 */
export function CyberSwitch({
  value,
  onValueChange,
  disabled,
}: {
  value: boolean;
  onValueChange?: (val: boolean) => void;
  disabled?: boolean;
}) {
  const t = useTheme();
  const transX = useRef(new Animated.Value(value ? 22 : 2)).current;

  useEffect(() => {
    Animated.spring(transX, {
      toValue: value ? 22 : 2,
      useNativeDriver: true,
      speed: 26,
      bounciness: 5,
    }).start();
  }, [value]);

  const toggle = () => {
    if (disabled || !onValueChange) return;
    onValueChange(!value);
  };

  return (
    <Pressable
      disabled={disabled}
      onPress={toggle}
      hitSlop={8}
      style={{
        width: 50,
        height: 28,
        borderRadius: 14,
        backgroundColor: value ? t.ok : '#1E2640',
        borderColor: value ? t.okGlow : 'rgba(255, 255, 255, 0.08)',
        borderWidth: 1,
        justifyContent: 'center',
        paddingHorizontal: 2,
        opacity: disabled ? 0.45 : 1,
        shadowColor: value ? t.ok : 'transparent',
        shadowOpacity: value ? 0.6 : 0,
        shadowRadius: 8,
        shadowOffset: { width: 0, height: 0 },
        elevation: value ? 4 : 0,
      }}>
      <Animated.View
        style={{
          width: 22,
          height: 22,
          borderRadius: 11,
          backgroundColor: '#FFFFFF',
          shadowColor: '#000',
          shadowOpacity: 0.35,
          shadowRadius: 3,
          shadowOffset: { width: 0, height: 1 },
          elevation: 3,
          transform: [{ translateX: transX }],
        }}
      />
    </Pressable>
  );
}

/**
 * Reusable Action Button with Tactile Physics and Bento Styling
 */
export function Btn({
  label,
  onPress,
  kind = 'primary',
  disabled,
  icon: Icon,
  style,
  textStyle,
}: {
  label: string;
  onPress: () => void;
  kind?: 'primary' | 'ghost' | 'danger' | 'secondary' | 'emerald' | 'outline';
  disabled?: boolean;
  icon?: LucideIcon;
  style?: StyleProp<ViewStyle>;
  textStyle?: StyleProp<TextStyle>;
}) {
  const t = useTheme();

  const bg =
    kind === 'primary'
      ? t.accent
      : kind === 'emerald'
      ? t.ok
      : kind === 'danger'
      ? t.danger
      : kind === 'secondary'
      ? '#0E1322'
      : kind === 'outline'
      ? 'transparent'
      : t.glass;

  const fg =
    kind === 'ghost'
      ? t.text
      : kind === 'secondary'
      ? '#FFFFFF'
      : kind === 'outline'
      ? t.accent
      : '#FFFFFF';

  const borderColor =
    kind === 'secondary'
      ? 'rgba(255, 255, 255, 0.12)'
      : kind === 'ghost'
      ? t.border
      : kind === 'outline'
      ? t.accent
      : 'transparent';

  const borderWidth = kind === 'secondary' || kind === 'ghost' || kind === 'outline' ? 1 : 0;

  return (
    <PressableScale
      disabled={disabled}
      onPress={onPress}
      style={style}>
      <View
        style={[
          {
            backgroundColor: bg,
            opacity: disabled ? 0.45 : 1,
            borderRadius: 14,
            paddingVertical: 11,
            paddingHorizontal: 16,
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            borderWidth,
            borderColor,
            shadowColor: kind === 'primary' ? t.accent : kind === 'emerald' ? t.ok : 'transparent',
            shadowOpacity: kind === 'primary' || kind === 'emerald' ? 0.4 : 0,
            shadowRadius: 10,
            shadowOffset: { width: 0, height: 2 },
            elevation: kind === 'primary' || kind === 'emerald' ? 4 : 0,
          },
        ]}>
        {Icon && <Icon size={16} color={fg} strokeWidth={2.4} />}
        <Text
          style={[
            {
              color: fg,
              fontWeight: '700',
              fontSize: 13.5,
              letterSpacing: -0.1,
            },
            textStyle,
          ]}
          numberOfLines={1}
          ellipsizeMode="tail">
          {label}
        </Text>
      </View>
    </PressableScale>
  );
}

export function Title({
  children,
  size = 17,
  icon: Icon,
  tone = 'accent',
}: {
  children: React.ReactNode;
  size?: number;
  icon?: LucideIcon;
  tone?: 'accent' | 'ok' | 'danger' | 'warn';
}) {
  const t = useTheme();
  const color = tone === 'ok' ? t.ok : tone === 'danger' ? t.danger : tone === 'warn' ? t.warn : t.accent;
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, flexShrink: 1 }}>
      {Icon && <Icon size={size + 2} color={color} strokeWidth={2.3} />}
      <Text style={{ color: '#FFFFFF', fontSize: size, fontWeight: '700', flexShrink: 1 }}>{children}</Text>
    </View>
  );
}

export function Sub({ children }: { children: React.ReactNode }) {
  const t = useTheme();
  return <Text style={{ color: t.sub, fontSize: 13, marginTop: 3, lineHeight: 18.5 }}>{children}</Text>;
}

export function Row({
  label,
  sub,
  right,
  icon: Icon,
}: {
  label: string;
  sub?: string;
  right?: React.ReactNode;
  icon?: LucideIcon;
}) {
  const t = useTheme();
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 10, gap: 12 }}>
      {Icon && (
        <View
          style={{
            width: 32,
            height: 32,
            borderRadius: 10,
            backgroundColor: t.accentWell,
            borderColor: t.accentWellBorder,
            borderWidth: 1,
            alignItems: 'center',
            justifyContent: 'center',
          }}>
          <Icon size={16} color="#A78BFA" strokeWidth={2.25} />
        </View>
      )}
      <View style={{ flex: 1 }}>
        <Text style={{ color: '#FFFFFF', fontWeight: '600', fontSize: 13.5 }} numberOfLines={1}>
          {label}
        </Text>
        {sub && <Text style={{ color: t.sub, fontSize: 12, marginTop: 1.5 }} numberOfLines={1}>{sub}</Text>}
      </View>
      {right}
      {!right && <ChevronRight size={16} color={t.sub} />}
    </View>
  );
}

/**
 * Drop-in themed replacement for React Native's Alert.alert.
 * Obsidian Bento style with slide & fade animations.
 */
export type AlertButton = { text: string; onPress?: () => void; style?: 'default' | 'cancel' | 'destructive' };
interface AlertConfig {
  title: string;
  message?: string;
  buttons: AlertButton[];
}

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
  const scaleAnim = useRef(new Animated.Value(0.92)).current;

  useEffect(() => {
    if (cfg) {
      fadeAnim.setValue(0);
      scaleAnim.setValue(0.92);
      Animated.parallel([
        Animated.timing(fadeAnim, { toValue: 1, duration: 180, useNativeDriver: true }),
        Animated.spring(scaleAnim, { toValue: 1, useNativeDriver: true, speed: 28, bounciness: 5 }),
      ]).start();
    }
  }, [cfg]);

  if (!cfg) return null;
  const stacked = cfg.buttons.length > 2;

  return (
    <Modal transparent visible animationType="fade" statusBarTranslucent onRequestClose={dismissAlert}>
      <View
        style={{
          flex: 1,
          backgroundColor: 'rgba(3, 4, 10, 0.78)',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 24,
        }}>
        <Animated.View
          style={{
            width: '100%',
            maxWidth: 390,
            backgroundColor: t.bgAlt,
            borderColor: t.cardBorderGlow,
            borderWidth: 1.5,
            borderRadius: 24,
            padding: 22,
            shadowColor: '#000',
            shadowOpacity: 0.6,
            shadowRadius: 24,
            shadowOffset: { width: 0, height: 10 },
            elevation: 14,
            opacity: fadeAnim,
            transform: [{ scale: scaleAnim }],
          }}>
          <Text style={{ color: '#FFFFFF', fontSize: 18, fontWeight: '800', letterSpacing: -0.2 }}>
            {cfg.title}
          </Text>
          {!!cfg.message && (
            <Text style={{ color: t.sub, fontSize: 13.5, marginTop: 10, lineHeight: 21 }}>
              {cfg.message}
            </Text>
          )}
          <View style={{ flexDirection: stacked ? 'column' : 'row', gap: 10, marginTop: 22 }}>
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
  const d = Math.floor(s / 86400),
    h = Math.floor((s % 86400) / 3600),
    m = Math.floor((s % 3600) / 60),
    sec = s % 60;
  const p = (n: number) => String(n).padStart(2, '0');
  return d > 0 ? `${d}d ${p(h)}h ${p(m)}m` : `${p(h)}:${p(m)}:${p(sec)}`;
}

/**
 * Floating Action Button with spring tactile response.
 */
export function Fab({ onPress, icon: Icon = Plus }: { onPress: () => void; icon?: LucideIcon }) {
  const t = useTheme();
  return (
    <PressableScale
      onPress={onPress}
      style={{
        position: 'absolute',
        right: 20,
        bottom: 20,
      }}>
      <View
        style={{
          width: 56,
          height: 56,
          borderRadius: 28,
          backgroundColor: t.accent,
          alignItems: 'center',
          justifyContent: 'center',
          shadowColor: t.accent,
          shadowOpacity: 0.65,
          shadowRadius: 14,
          shadowOffset: { width: 0, height: 6 },
          elevation: 10,
        }}>
        <Icon size={26} color="#fff" strokeWidth={2.6} />
      </View>
    </PressableScale>
  );
}

/**
 * Round icon-only toggle button with luminous accent halo.
 */
export function IconToggle({
  Icon,
  active,
  onPress,
  tone = 'accent',
}: {
  Icon: LucideIcon;
  active: boolean;
  onPress: () => void;
  tone?: 'accent' | 'danger';
}) {
  const t = useTheme();
  const color = tone === 'danger' ? t.danger : t.accent;
  const soft = tone === 'danger' ? t.dangerSoft : t.accentSoft;

  return (
    <PressableScale onPress={onPress} hitSlop={8}>
      <View
        style={{
          width: 36,
          height: 36,
          borderRadius: 18,
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: active ? soft : t.glass,
          borderWidth: 1,
          borderColor: active ? color : t.border,
          shadowColor: active ? color : 'transparent',
          shadowOpacity: active ? 0.7 : 0,
          shadowRadius: 10,
          shadowOffset: { width: 0, height: 0 },
          elevation: active ? 5 : 0,
        }}>
        <Icon size={17} color={active ? color : t.sub} strokeWidth={2.3} />
      </View>
    </PressableScale>
  );
}

/**
 * Countdown badge + confirm/cancel controls for delayed setting changes.
 */
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

  const remaining = lock.pending
    ? Math.max(0, (lock.unlockAt && lock.unlockAt > 0) ? lock.unlockAt - now : (lock.remainingMs ?? 0))
    : 0;
  const ready =
    (lock.ready ?? false) ||
    (lock.open ?? false) ||
    (lock.pending && remaining === 0 && (lock.remainingMs ?? 0) === 0);

  useEffect(() => {
    if (ready && queued.length > 0) {
      confirm(lockKey).catch(() => {});
    }
  }, [ready, queued.length, lockKey]);

  const color = ready ? t.ok : t.warn;
  const soft = ready ? t.okSoft : t.warnSoft;
  const border = ready ? t.okBorder : t.warnBorder;
  const run = (fn: () => Promise<void>) => fn().catch(e => showAlert('Blocker', errMsg(e)));

  return (
    <View
      style={{
        marginTop: 14,
        padding: 14,
        borderRadius: 16,
        backgroundColor: soft,
        borderColor: border,
        borderWidth: 1,
      }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        {ready ? <CheckCircle2 size={16} color={color} strokeWidth={2.4} /> : <Clock size={16} color={color} strokeWidth={2.4} />}
        <Text style={{ color, fontWeight: '700', fontVariant: ['tabular-nums'], flexShrink: 1, fontSize: 13.5 }}>
          {ready ? 'Timer complete: confirm to apply' : `Locked: ${fmt(remaining)} remaining`}
        </Text>
      </View>
      {queued.map((a, i) => (
        <Text key={i} style={{ color: t.sub, fontSize: 12, marginTop: 4 }}>
          • {a.label}
        </Text>
      ))}
      <View style={{ flexDirection: 'row', gap: 8, marginTop: 12 }}>
        {ready && (
          <View style={{ flex: 1 }}>
            <Btn label="Confirm change" kind="emerald" icon={Check} onPress={() => run(() => confirm(lockKey))} />
          </View>
        )}
        <View style={{ flex: 1 }}>
          <Btn label="Cancel" kind="ghost" icon={Ban} onPress={() => run(() => cancel(lockKey))} />
        </View>
      </View>
    </View>
  );
}

/**
 * Bento Animated Toggle Row with Squircle Icon Well and CyberSwitch
 */
export function AnimatedToggleRow({
  label,
  sub,
  value,
  onValueChange,
  icon: Icon,
  disabled,
  badge,
}: {
  label: string;
  sub?: string;
  value: boolean;
  onValueChange?: (v: boolean) => void;
  icon?: LucideIcon;
  disabled?: boolean;
  badge?: string;
}) {
  const t = useTheme();

  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        paddingVertical: 10,
        gap: 12,
      }}>
      {Icon && (
        <View
          style={{
            width: 34,
            height: 34,
            borderRadius: 11,
            backgroundColor: value ? t.accentWell : t.glass,
            borderWidth: 1,
            borderColor: value ? t.accentWellBorder : t.border,
            shadowColor: value ? t.accent : 'transparent',
            shadowOpacity: value ? 0.6 : 0,
            shadowRadius: 8,
            shadowOffset: { width: 0, height: 0 },
            elevation: value ? 4 : 0,
            alignItems: 'center',
            justifyContent: 'center',
          }}>
          <Icon size={16} color={value ? '#A78BFA' : t.sub} strokeWidth={value ? 2.5 : 2.2} />
        </View>
      )}
      <View style={{ flex: 1, paddingRight: 6 }}>
        <Text style={{ color: '#FFFFFF', fontWeight: '600', fontSize: 13.5 }} numberOfLines={1}>
          {label}
        </Text>
        {sub && <Text style={{ color: t.sub, fontSize: 12, marginTop: 2 }} numberOfLines={1}>{sub}</Text>}
      </View>
      {badge ? (
        <StatusPillBadge label={badge} tone={value ? 'ok' : 'idle'} />
      ) : (
        <CyberSwitch value={value} onValueChange={onValueChange} disabled={disabled} />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  bentoCard: {
    borderRadius: 20,
    padding: 16,
    marginBottom: 12,
    overflow: 'hidden',
  },
});

export { ShieldOff, Loader2 };
