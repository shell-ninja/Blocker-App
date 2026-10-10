import React, { useEffect, useRef, useState } from 'react';
import { Animated, AppState, Easing, Platform, Pressable, StatusBar, Text, UIManager, View } from 'react-native';
import { isSupported } from './src/native/BlockerNative';
import { useProtection } from './src/hooks/useProtection';
import { init, refresh } from './src/services/ProtectionManager';
import { UpdateService } from './src/services/UpdateService';
import { AlertHost, AmbientBackground, Header, Icons, useTheme } from './src/ui';
import HomeScreen from './src/screens/HomeScreen';
import AppBlockerScreen from './src/screens/AppBlockerScreen';
import FocusScreen from './src/screens/FocusScreen';
import BlocklistScreen from './src/screens/BlocklistScreen';
import SettingsScreen from './src/screens/SettingsScreen';
import OnboardingPermissions from './src/screens/OnboardingPermissions';

if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

const TABS = [
  { id: 'home', label: 'Home', icon: Icons.home, C: HomeScreen },
  { id: 'focus', label: 'Focus', icon: Icons.focus, C: FocusScreen },
  { id: 'apps', label: 'Apps', icon: Icons.apps, C: AppBlockerScreen },
  { id: 'web', label: 'Web', icon: Icons.web, C: BlocklistScreen },
  { id: 'settings', label: 'Settings', icon: Icons.settings, C: SettingsScreen }
] as const;

export default function App() {
  const t = useTheme();
  const p = useProtection();
  const [tab, setTab] = useState<(typeof TABS)[number]['id']>('home');
  const top = StatusBar.currentHeight ?? 0;
  const fade = useRef(new Animated.Value(1)).current;
  const translateY = useRef(new Animated.Value(0)).current;

  const changeTab = (id: (typeof TABS)[number]['id']) => {
    if (id === tab) return;
    Animated.parallel([
      Animated.timing(fade, { toValue: 0, duration: 90, easing: Easing.out(Easing.cubic), useNativeDriver: true }),
      Animated.timing(translateY, { toValue: -6, duration: 90, easing: Easing.out(Easing.cubic), useNativeDriver: true }),
    ]).start(() => {
      setTab(id);
      translateY.setValue(10);
      Animated.parallel([
        Animated.timing(fade, { toValue: 1, duration: 200, easing: Easing.out(Easing.cubic), useNativeDriver: true }),
        Animated.timing(translateY, { toValue: 0, duration: 200, easing: Easing.out(Easing.cubic), useNativeDriver: true }),
      ]).start();
    });
  };

  useEffect(() => {
    if (!isSupported) return;
    init().catch(() => {});
    UpdateService.checkOnLaunch().catch(() => {});
    const i = setInterval(() => refresh().catch(() => {}), 5000);
    const sub = AppState.addEventListener('change', s => s === 'active' && refresh().catch(() => {}));
    return () => {
      clearInterval(i);
      sub.remove();
    };
  }, []);

  const shell = { flex: 1, backgroundColor: t.bg, paddingTop: top } as const;

  if (!isSupported) {
    return (
      <View style={[shell, { alignItems: 'center', justifyContent: 'center', padding: 32 }]}>
        <Text style={{ color: t.text, fontSize: 18, fontWeight: '600' }}>Android only</Text>
        <Text style={{ color: t.sub, textAlign: 'center', marginTop: 8 }}>
          The iOS module (Screen Time / Network Extension) is not part of this build.
        </Text>
      </View>
    );
  }
  if (!p.ready || !p.perms) return <View style={shell} />;

  // while focus mode runs, the only screen is the focus screen (Blocker's own settings are locked too)
  if (p.focus.active) {
    return (
      <View style={shell}>
        <StatusBar barStyle="light-content" backgroundColor={t.bg} />
        <AmbientBackground />
        <Header />
        <FocusScreen />
        <AlertHost />
      </View>
    );
  }

  const pm = p.perms;
  const permsOk = pm.accessibility && pm.deviceAdmin && pm.usageAccess && pm.overlay;
  if (!p.state.onboarded || !permsOk) {
    return (
      <View style={shell}>
        <StatusBar barStyle="light-content" backgroundColor={t.bg} />
        <AmbientBackground />
        <Header />
        <OnboardingPermissions onDone={() => {}} />
        <AlertHost />
      </View>
    );
  }

  const Active = TABS.find(x => x.id === tab)!.C;
  return (
    <View style={shell}>
      <StatusBar barStyle="light-content" backgroundColor={t.bg} />
      <AmbientBackground />
      <Header />
      <Animated.View style={{ flex: 1, opacity: fade, transform: [{ translateY }] }}><Active /></Animated.View>
      <View style={{
        flexDirection: 'row', backgroundColor: t.bgAlt, borderTopColor: t.border, borderTopWidth: 1, paddingTop: 6, paddingBottom: 8
      }}>
        {TABS.map(x => {
          const active = tab === x.id;
          const Ico = x.icon;
          return (
            <Pressable
              key={x.id}
              onPress={() => changeTab(x.id)}
              style={({ pressed }) => [{ flex: 1, alignItems: 'center', gap: 3, paddingVertical: 6, opacity: pressed ? 0.6 : 1 }]}>
              <View style={{ alignItems: 'center', justifyContent: 'center' }}>
                {/* Layered vibrant neon glow behind the active icon */}
                {active && (
                  <View style={{
                    position: 'absolute', width: 50, height: 34, borderRadius: 17,
                    backgroundColor: t.accent, opacity: 0.32,
                    shadowColor: t.accent, shadowOpacity: 1,
                    shadowRadius: 16, shadowOffset: { width: 0, height: 0 }, elevation: 6
                  }} />
                )}
                <View style={{
                  width: 44, height: 28, borderRadius: 14, alignItems: 'center', justifyContent: 'center',
                  backgroundColor: active ? t.accentSoft : 'transparent',
                  borderWidth: active ? 1 : 0, borderColor: t.accent,
                  shadowColor: active ? t.accent : 'transparent', shadowOpacity: active ? 1 : 0,
                  shadowRadius: active ? 12 : 0, shadowOffset: { width: 0, height: 0 }, elevation: active ? 8 : 0
                }}>
                  <Ico size={18} color={active ? '#FFFFFF' : t.sub} strokeWidth={active ? 2.6 : 2} />
                </View>
              </View>
              <Text style={{
                color: active ? t.accent : t.sub,
                fontWeight: active ? '700' : '600',
                fontSize: 11,
                textShadowColor: active ? t.accentSoft : 'transparent',
                textShadowOffset: { width: 0, height: 0 },
                textShadowRadius: active ? 8 : 0
              }}>
                {x.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
      <AlertHost />
    </View>
  );
}
