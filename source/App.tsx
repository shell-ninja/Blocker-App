import React, { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Animated,
  AppState,
  BackHandler,
  Easing,
  Platform,
  Pressable,
  StatusBar,
  Text,
  UIManager,
  View,
} from 'react-native';
import { ShieldCheck } from 'lucide-react-native';
import { isSupported } from './src/native/BlockerNative';
import { useProtection } from './src/hooks/useProtection';
import { init, refresh } from './src/services/ProtectionManager';
import { UpdateService } from './src/services/UpdateService';
import { AlertHost, AmbientBackground, Header, Icons, PressableScale, useTheme } from './src/ui';
import HomeScreen from './src/screens/HomeScreen';
import AppBlockerScreen from './src/screens/AppBlockerScreen';
import FocusScreen from './src/screens/FocusScreen';
import BlocklistScreen from './src/screens/BlocklistScreen';
import SettingsScreen from './src/screens/SettingsScreen';
import OnboardingPermissions from './src/screens/OnboardingPermissions';

if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

class RootErrorBoundary extends React.Component<
  { children: React.ReactNode },
  { hasError: boolean; error: string }
> {
  state = { hasError: false, error: '' };
  static getDerivedStateFromError(error: any) {
    return { hasError: true, error: String(error?.message || error) };
  }
  componentDidCatch(error: any, info: any) {
    console.warn('RootErrorBoundary caught render error:', error, info);
  }
  render() {
    if (this.state.hasError) {
      return (
        <View
          style={{
            flex: 1,
            backgroundColor: '#070913',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 24,
          }}>
          <View
            style={{
              width: 54,
              height: 54,
              borderRadius: 18,
              backgroundColor: '#16122C',
              borderColor: '#7C3AED',
              borderWidth: 1.5,
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: 16,
            }}>
            <ShieldCheck size={28} color="#A78BFA" strokeWidth={2.4} />
          </View>
          <Text
            style={{
              color: '#FFFFFF',
              fontSize: 19,
              fontWeight: '800',
              marginBottom: 8,
              letterSpacing: -0.3,
            }}>
            Blocker Recovery
          </Text>
          <Text
            style={{
              color: '#8B97B0',
              fontSize: 12.5,
              textAlign: 'center',
              marginBottom: 20,
              lineHeight: 18,
            }}>
            {this.state.error}
          </Text>
          <PressableScale
            onPress={() => {
              this.setState({ hasError: false, error: '' });
              refresh().catch(() => {});
            }}
            contentStyle={{
              backgroundColor: '#7C3AED',
              paddingVertical: 12,
              paddingHorizontal: 28,
              borderRadius: 14,
            }}>
            <Text style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 14 }}>
              Reload Interface
            </Text>
          </PressableScale>
        </View>
      );
    }
    return this.props.children;
  }
}

const TABS = [
  { id: 'home', label: 'Home', icon: Icons.home, C: HomeScreen },
  { id: 'focus', label: 'Focus', icon: Icons.focus, C: FocusScreen },
  { id: 'apps', label: 'Apps', icon: Icons.apps, C: AppBlockerScreen },
  { id: 'web', label: 'Web', icon: Icons.web, C: BlocklistScreen },
  { id: 'settings', label: 'Settings', icon: Icons.settings, C: SettingsScreen },
] as const;

function MainApp() {
  const t = useTheme();
  const p = useProtection();
  const [tab, setTab] = useState<(typeof TABS)[number]['id']>('home');
  const [initTimeout, setInitTimeout] = useState(false);
  const top = StatusBar.currentHeight ?? 0;
  const fade = useRef(new Animated.Value(1)).current;
  const translateY = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    const timer = setTimeout(() => setInitTimeout(true), 1200);
    return () => clearTimeout(timer);
  }, []);

  const changeTab = (id: (typeof TABS)[number]['id']) => {
    if (id === tab) return;
    Animated.parallel([
      Animated.timing(fade, {
        toValue: 0,
        duration: 90,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }),
      Animated.timing(translateY, {
        toValue: -6,
        duration: 90,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }),
    ]).start(() => {
      setTab(id);
      translateY.setValue(8);
      Animated.parallel([
        Animated.timing(fade, {
          toValue: 1,
          duration: 180,
          easing: Easing.out(Easing.cubic),
          useNativeDriver: true,
        }),
        Animated.timing(translateY, {
          toValue: 0,
          duration: 180,
          easing: Easing.out(Easing.cubic),
          useNativeDriver: true,
        }),
      ]).start();
    });
  };

  useEffect(() => {
    if (!isSupported) return;
    init().catch(() => {});
    UpdateService.checkOnLaunch().catch(() => {});
    const i = setInterval(() => refresh().catch(() => {}), 5000);
    const sub = AppState.addEventListener('change', s => {
      if (s === 'active') {
        refresh().catch(() => {});
        UpdateService.checkOnLaunch().catch(() => {});
      }
    });
    return () => {
      clearInterval(i);
      sub.remove();
    };
  }, []);

  useEffect(() => {
    if (p.focus.active || p.study?.active) {
      const sub = BackHandler.addEventListener('hardwareBackPress', () => true);
      return () => sub.remove();
    }
  }, [p.focus.active, p.study?.active]);

  const shell = { flex: 1, backgroundColor: t.bg, paddingTop: top } as const;

  if (!isSupported) {
    return (
      <View style={[shell, { alignItems: 'center', justifyContent: 'center', padding: 32 }]}>
        <Text style={{ color: t.text, fontSize: 18, fontWeight: '700' }}>Android only</Text>
        <Text style={{ color: t.sub, textAlign: 'center', marginTop: 8 }}>
          The iOS module (Screen Time / Network Extension) is not part of this build.
        </Text>
      </View>
    );
  }

  // Graceful splash while loading initial state, transitions automatically after timeout
  const isReady = p.ready || initTimeout;
  if (!isReady && !p.perms) {
    return (
      <View style={[shell, { alignItems: 'center', justifyContent: 'center' }]}>
        <StatusBar barStyle="light-content" backgroundColor={t.bg} translucent />
        <AmbientBackground />
        <View style={{ alignItems: 'center', gap: 14 }}>
          <View
            style={{
              width: 58,
              height: 58,
              borderRadius: 20,
              backgroundColor: '#16122C',
              borderColor: '#7C3AED',
              borderWidth: 1.5,
              alignItems: 'center',
              justifyContent: 'center',
              shadowColor: '#7C3AED',
              shadowOpacity: 0.6,
              shadowRadius: 16,
              shadowOffset: { width: 0, height: 0 },
              elevation: 8,
            }}>
            <ShieldCheck size={30} color="#A78BFA" strokeWidth={2.4} />
          </View>
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            <Text style={{ color: '#FFFFFF', fontSize: 22, fontWeight: '800', letterSpacing: -0.4 }}>
              Block
            </Text>
            <Text style={{ color: '#A78BFA', fontSize: 22, fontWeight: '800', letterSpacing: -0.4 }}>
              er
            </Text>
          </View>
          <ActivityIndicator size="small" color="#7C3AED" style={{ marginTop: 6 }} />
        </View>
      </View>
    );
  }

  // While focus or study mode runs, lock Blocker's settings to the active session screen
  if (p.focus.active || p.study?.active) {
    return (
      <View style={shell}>
        <StatusBar barStyle="light-content" backgroundColor={t.bg} translucent />
        <AmbientBackground />
        <Header />
        <FocusScreen />
        <AlertHost />
      </View>
    );
  }

  const pm = p.perms ?? {
    accessibility: true,
    deviceAdmin: true,
    usageAccess: true,
    overlay: true,
    adminLost: false,
    battery: true,
  };
  const permsOk = pm.accessibility && pm.deviceAdmin && pm.usageAccess && pm.overlay;
  if (!p.state.onboarded || !permsOk) {
    return (
      <View style={shell}>
        <StatusBar barStyle="light-content" backgroundColor={t.bg} translucent />
        <AmbientBackground />
        <Header />
        <OnboardingPermissions onDone={() => {}} />
        <AlertHost />
      </View>
    );
  }

  const Active = TABS.find(x => x.id === tab)?.C ?? HomeScreen;
  return (
    <View style={shell}>
      <StatusBar barStyle="light-content" backgroundColor={t.bg} translucent />
      <AmbientBackground />
      <Header />
      <Animated.View style={{ flex: 1, opacity: fade, transform: [{ translateY }] }}>
        <Active />
      </Animated.View>

      {/* CyberBottomBar: 5 tabs matching reference UI */}
      <View
        style={{
          flexDirection: 'row',
          backgroundColor: '#070913',
          borderTopColor: '#1E2640',
          borderTopWidth: 1,
          paddingTop: 8,
          paddingBottom: 10,
          paddingHorizontal: 8,
        }}>
        {TABS.map(x => {
          const active = tab === x.id;
          const Ico = x.icon;
          return (
            <PressableScale
              key={x.id}
              activeScale={0.92}
              onPress={() => changeTab(x.id)}
              style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}
              contentStyle={{ alignItems: 'center', justifyContent: 'center', width: '100%' }}>
              <View style={{ width: 52, height: 32, alignItems: 'center', justifyContent: 'center' }}>
                {active && (
                  <View
                    style={{
                      position: 'absolute',
                      width: 50,
                      height: 32,
                      borderRadius: 16,
                      backgroundColor: '#7C3AED',
                      opacity: 0.3,
                      shadowColor: '#7C3AED',
                      shadowOpacity: 0.9,
                      shadowRadius: 10,
                      shadowOffset: { width: 0, height: 0 },
                      elevation: 4,
                    }}
                  />
                )}
                <View
                  style={{
                    width: 44,
                    height: 28,
                    borderRadius: 14,
                    alignItems: 'center',
                    justifyContent: 'center',
                    backgroundColor: active ? '#18122B' : 'transparent',
                    borderWidth: active ? 1 : 0,
                    borderColor: '#7C3AED',
                    shadowColor: active ? '#7C3AED' : 'transparent',
                    shadowOpacity: active ? 0.7 : 0,
                    shadowRadius: 8,
                    shadowOffset: { width: 0, height: 0 },
                    elevation: active ? 3 : 0,
                  }}>
                  <Ico
                    size={18}
                    color={active ? '#FFFFFF' : '#8B97B0'}
                    strokeWidth={active ? 2.6 : 2}
                  />
                </View>
              </View>
              <Text
                style={{
                  color: active ? '#A78BFA' : '#8B97B0',
                  fontWeight: active ? '700' : '500',
                  fontSize: 11,
                  lineHeight: 14,
                  textAlign: 'center',
                  width: '100%',
                  marginTop: 3,
                  letterSpacing: 0.2,
                }}>
                {x.label}
              </Text>
            </PressableScale>
          );
        })}
      </View>
      <AlertHost />
    </View>
  );
}

export default function App() {
  return (
    <RootErrorBoundary>
      <MainApp />
    </RootErrorBoundary>
  );
}
