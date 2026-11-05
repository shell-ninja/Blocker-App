import React, { useEffect } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { Check, CheckCircle2, Lock, LucideIcon, Shield, ShieldCheck, X } from 'lucide-react-native';
import { Native } from '../native/BlockerNative';
import { useProtection } from '../hooks/useProtection';
import { refresh, setProtection, setShield } from '../services/ProtectionManager';
import {
  BentoStatCard,
  Btn,
  CyberSwitch,
  GlowCard,
  Icons,
  LockBar,
  PressableScale,
  RollingCounter,
  Row,
  StatusPillBadge,
  Sub,
  Title,
  errMsg,
  showAlert,
  useTheme,
} from '../ui';
import UpdateBanner from './UpdateBanner';

export default function HomeScreen() {
  const t = useTheme();
  const p = useProtection();

  useEffect(() => {
    const i = setInterval(() => refresh().catch(() => {}), 3000);
    return () => clearInterval(i);
  }, []);

  const onToggle = async (on: boolean) => {
    try {
      const r = await setProtection(on);
      if (r !== 'applied') {
        showAlert(
          'Protection locked',
          `Protection stays on until the ${p.delayDays}-day timer completes and you confirm.`
        );
      }
    } catch (e) {
      showAlert('Blocker', errMsg(e));
    }
  };

  const perms = p.perms;
  const health: [string, boolean | undefined][] = [
    ['Accessibility service', perms?.accessibility],
    ['Device admin (uninstall guard)', perms?.deviceAdmin],
    ['Usage access', perms?.usageAccess],
    ['Display over other apps', perms?.overlay],
  ];
  const healthy = health.every(([, ok]) => ok);

  const stats: [string, number, LucideIcon][] = [
    ['Sites redirected', p.stats?.sitesBlockedToday ?? 0, Icons.web],
    ['Apps blocked', p.stats?.appsBlockedToday ?? 0, Icons.apps],
    ['Screen closes', p.stats?.screenBlocksToday ?? 0, Icons.eyeOff],
    ['Bypass attempts', p.stats?.tamperAttemptsToday ?? 0, Icons.lock],
  ];

  const recMasterOn = p.active;
  const recShieldOn = p.shield;
  const recBatteryOn = !!p.perms?.battery;
  const recommendedScore = (recMasterOn ? 1 : 0) + (recShieldOn ? 1 : 0) + (recBatteryOn ? 1 : 0);
  const allRecommendedActive = recommendedScore === 3;

  return (
    <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 28 }} showsVerticalScrollIndicator={false}>
      {/* GitHub Releases Update Banner */}
      <UpdateBanner />

      {/* Recommended Setup Guide - only shown when recommended protections need attention */}
      {!allRecommendedActive && (
        <GlowCard tone="accent" style={{ borderColor: t.accentWellBorder, borderWidth: 1 }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6, gap: 8 }}>
            <View style={{ flex: 1, paddingRight: 6 }}>
              <Title icon={Icons.guide} tone="accent">Setup Guide</Title>
            </View>
            <StatusPillBadge label={`${recommendedScore}/3 Active`} tone="warn" />
          </View>
          <Sub>Essential protections for full device security:</Sub>

          <View style={{ marginTop: 14, gap: 10 }}>
            {/* 1. Master Protection */}
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
              <View style={{ flex: 1, paddingRight: 8 }}>
                <Text style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 13.5 }}>Master Protection</Text>
                <Text style={{ color: t.sub, fontSize: 11.5, marginTop: 1 }}>Blocklists, keywords & apps</Text>
              </View>
              {recMasterOn ? (
                <StatusPillBadge label="Enabled" tone="ok" />
              ) : (
                <Btn label="Turn On" kind="primary" onPress={() => onToggle(true)} />
              )}
            </View>

            {/* 2. Anti-Tamper & Uninstall Shield */}
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
              <View style={{ flex: 1, paddingRight: 8 }}>
                <Text style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 13.5 }}>Uninstall Guard</Text>
                <Text style={{ color: t.sub, fontSize: 11.5, marginTop: 1 }}>Locks settings & device admin</Text>
              </View>
              {recShieldOn ? (
                <StatusPillBadge label="Enabled" tone="ok" />
              ) : (
                <Btn
                  label="Turn On"
                  kind="primary"
                  onPress={async () => {
                    try {
                      await setShield(true);
                      refresh();
                    } catch (e) {
                      showAlert('Shield', errMsg(e));
                    }
                  }}
                />
              )}
            </View>

            {/* 3. Background Unrestricted Battery */}
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
              <View style={{ flex: 1, paddingRight: 8 }}>
                <Text style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 13.5 }}>Background Service</Text>
                <Text style={{ color: t.sub, fontSize: 11.5, marginTop: 1 }}>Prevents system task killing</Text>
              </View>
              {recBatteryOn ? (
                <StatusPillBadge label="Unrestricted" tone="ok" />
              ) : (
                <Btn
                  label="Allow"
                  kind="ghost"
                  onPress={async () => {
                    try {
                      await Native.openPermissionSettings('battery');
                    } catch (e) {
                      showAlert('Battery Settings', errMsg(e));
                    }
                  }}
                />
              )}
            </View>
          </View>

          <View style={{ marginTop: 16 }}>
            <Btn
              label="Enable All Recommended"
              icon={Icons.shieldOn}
              kind="primary"
              onPress={async () => {
                try {
                  if (!recMasterOn) await setProtection(true);
                  if (!recShieldOn) await setShield(true);
                  if (!recBatteryOn) await Native.openPermissionSettings('battery');
                  refresh();
                } catch (e) {
                  showAlert('Setup', errMsg(e));
                }
              }}
            />
          </View>
        </GlowCard>
      )}

      {/* Primary Hero Bento Card: "Protection Active" / "Protection Paused" */}
      <GlowCard active={p.active} tone={p.active ? 'ok' : 'default'} style={{ padding: 18 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 14, flex: 1, paddingRight: 10 }}>
            {/* Luminous circular/squircle icon well */}
            <View
              style={{
                width: 44,
                height: 44,
                borderRadius: 22,
                backgroundColor: p.active ? t.okWell : '#161A2B',
                borderColor: p.active ? t.okBorder : t.cardBorder,
                borderWidth: 1.5,
                alignItems: 'center',
                justifyContent: 'center',
                shadowColor: p.active ? t.ok : 'transparent',
                shadowOpacity: p.active ? 0.6 : 0,
                shadowRadius: 10,
                shadowOffset: { width: 0, height: 0 },
                elevation: p.active ? 4 : 0,
              }}>
              {p.active ? (
                <Check size={22} color={t.okGlow} strokeWidth={2.8} />
              ) : (
                <Icons.shieldOff size={20} color={t.sub} strokeWidth={2.2} />
              )}
            </View>

            <View style={{ flex: 1 }}>
              <Text
                style={{
                  color: '#FFFFFF',
                  fontSize: 19,
                  fontWeight: '800',
                  letterSpacing: -0.3,
                }}>
                {p.active ? 'Protection Active' : 'Protection Paused'}
              </Text>
              <Text
                style={{
                  color: t.sub,
                  fontSize: 12.5,
                  marginTop: 3,
                  lineHeight: 17,
                }}>
                {p.active
                  ? 'Domain blocklists, keywords & limits enforced'
                  : 'Distraction protection is paused'}
              </Text>
            </View>
          </View>

          {/* Spring-animated CyberSwitch */}
          <CyberSwitch value={p.active} onValueChange={onToggle} />
        </View>

        <LockBar lockKey="protection" />
      </GlowCard>

      {/* Secondary Hero Bento Card: "Anti-Tamper Fortress" */}
      <GlowCard style={{ padding: 18, borderColor: t.cardBorder, borderWidth: 1 }}>
        <View style={{ flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 8 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, flex: 1, paddingRight: 8 }}>
            <Lock size={20} color={t.okGlow} strokeWidth={2.5} />
            <Text style={{ color: '#FFFFFF', fontSize: 17, fontWeight: '800', letterSpacing: -0.2 }}>
              Anti-Tamper{'\n'}Fortress
            </Text>
          </View>
          <StatusPillBadge label="Airtight Lock" tone="ok" breathing={p.shield} />
        </View>

        <Text style={{ color: t.sub, fontSize: 13, lineHeight: 19, marginTop: 4 }}>
          Device Administrator, launcher shield, and watchdog active. Uninstallation is strictly locked.
        </Text>

        <View style={{ marginTop: 14 }}>
          <Btn
            label="Anti-Uninstall Shield Active"
            icon={ShieldCheck}
            kind="secondary"
            onPress={() => {
              showAlert(
                'Anti-Uninstall Fortress Active',
                '1. Device Administrator: Android OS blocks immediate uninstallation while protection is active.\n\n2. Launcher Shield: Press-and-hold / drag-to-uninstall is intercepted in 0ms.\n\n3. Settings Ejection: Any attempt to reach App Info or Deactivation in Settings is kicked to Home immediately.\n\n4. Delay Weakening: If protection is ever turned off, the full delay timer must run down first.'
              );
            }}
          />
        </View>
      </GlowCard>

      {/* 2x2 Bento Stat Cards Grid */}
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 10, marginBottom: 12 }}>
        {stats.map(([label, n, Ico]) => (
          <BentoStatCard
            key={label}
            label={label}
            value={n}
            icon={Ico}
            onPress={() => {
              if (label.includes('Sites')) showAlert('Sites Redirected', `Blocker redirected ${n} dangerous or distracted website attempts today.`);
              else if (label.includes('Apps')) showAlert('Apps Blocked', `Blocker blocked ${n} restricted app launch attempts today.`);
              else if (label.includes('Screen')) showAlert('Screen Closes', `Screen text scanning detected and closed ${n} inappropriate text screens today.`);
              else showAlert('Bypass Attempts', `Anti-tamper fortress intercepted ${n} uninstallation or deactivation bypass attempts today.`);
            }}
          />
        ))}
      </View>

      {/* System Health Bento Card */}
      <GlowCard style={{ padding: 18, borderColor: t.cardBorder, borderWidth: 1 }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <Shield size={19} color="#A78BFA" strokeWidth={2.4} />
            <Text style={{ color: '#FFFFFF', fontSize: 17, fontWeight: '800', letterSpacing: -0.2 }}>
              System Health
            </Text>
          </View>
          <StatusPillBadge label={healthy ? 'Healthy' : 'Attention'} tone={healthy ? 'ok' : 'warn'} />
        </View>

        <View style={{ marginTop: 4 }}>
          {health.map(([label, ok]) => (
            <Row
              key={label}
              label={label}
              right={<StatusPillBadge label={ok ? 'On' : 'Off'} tone={ok ? 'ok' : 'danger'} size="sm" />}
            />
          ))}
        </View>

        {perms?.adminLost && (
          <View
            style={{
              marginTop: 12,
              padding: 12,
              borderRadius: 12,
              backgroundColor: t.dangerSoft,
              borderColor: t.dangerBorder,
              borderWidth: 1,
            }}>
            <Text style={{ color: t.danger, fontSize: 12.5, fontWeight: '600' }}>
              Device admin was deactivated. Re-enable it in Settings to restore airtight protection.
            </Text>
          </View>
        )}
      </GlowCard>
    </ScrollView>
  );
}
