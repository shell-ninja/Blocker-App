import React, { useEffect } from 'react';
import { ScrollView, Switch, Text, View } from 'react-native';
import { Native } from '../native/BlockerNative';
import { useProtection } from '../hooks/useProtection';
import { refresh, setProtection, setShield } from '../services/ProtectionManager';
import { Badge, Btn, Card, Icons, LockBar, Row, Sub, Title, errMsg, showAlert, useTheme } from '../ui';
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
        showAlert('Protection locked', `Protection stays on until the ${p.delayDays}-day timer completes and you confirm.`);
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
    ['Display over other apps', perms?.overlay]
  ];
  const healthy = health.every(([, ok]) => ok);
  const stats: [string, number][] = [
    ['Sites redirected', p.stats?.sitesBlockedToday ?? 0],
    ['Apps blocked', p.stats?.appsBlockedToday ?? 0],
    ['Screen closes', p.stats?.screenBlocksToday ?? 0],
    ['Bypass attempts', p.stats?.tamperAttemptsToday ?? 0]
  ];

  const recMasterOn = p.active;
  const recShieldOn = p.shield;
  const recBatteryOn = !!p.perms?.battery;
  const recommendedScore = (recMasterOn ? 1 : 0) + (recShieldOn ? 1 : 0) + (recBatteryOn ? 1 : 0);
  const allRecommendedActive = recommendedScore === 3;

  return (
    <ScrollView contentContainerStyle={{ padding: 16 }}>
      {/* GitHub Releases Update Banner */}
      <UpdateBanner />

      {/* Recommended Setup Guide - only shown when recommended protections need attention */}
      {!allRecommendedActive && (
        <Card style={{ borderColor: t.accent, borderWidth: 1.5 }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6, gap: 8 }}>
            <View style={{ flex: 1, paddingRight: 6 }}>
              <Title icon={Icons.guide} tone="accent">Setup Guide</Title>
            </View>
            <Badge label={`${recommendedScore}/3 Active`} tone="warn" />
          </View>
          <Sub>Essential protections for full security:</Sub>

          <View style={{ marginTop: 12, gap: 10 }}>
            {/* 1. Master Protection */}
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
              <View style={{ flex: 1, paddingRight: 8 }}>
                <Text style={{ color: t.text, fontWeight: '700', fontSize: 13 }}>Master Protection</Text>
                <Text style={{ color: t.sub, fontSize: 11 }}>Blocklists, keywords & apps</Text>
              </View>
              {recMasterOn ? (
                <Badge label="Enabled" tone="ok" />
              ) : (
                <Btn label="Turn On" kind="primary" onPress={() => onToggle(true)} />
              )}
            </View>

            {/* 2. Anti-Tamper & Uninstall Shield */}
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
              <View style={{ flex: 1, paddingRight: 8 }}>
                <Text style={{ color: t.text, fontWeight: '700', fontSize: 13 }}>Uninstall Guard</Text>
                <Text style={{ color: t.sub, fontSize: 11 }}>Locks settings & device admin</Text>
              </View>
              {recShieldOn ? (
                <Badge label="Enabled" tone="ok" />
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
                <Text style={{ color: t.text, fontWeight: '700', fontSize: 13 }}>Background Service</Text>
                <Text style={{ color: t.sub, fontSize: 11 }}>Prevents system task killing</Text>
              </View>
              {recBatteryOn ? (
                <Badge label="Unrestricted" tone="ok" />
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

          <View style={{ marginTop: 14 }}>
            <Btn
              label="Enable All Recommended"
              icon={Icons.shieldOn}
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
        </Card>
      )}

      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <View style={{ flex: 1, paddingRight: 12 }}>
            <Title size={21} icon={p.active ? Icons.shieldOn : Icons.shieldOff} tone={p.active ? 'ok' : 'accent'}>
              {p.active ? 'Protection Active' : 'Protection Paused'}
            </Title>
            <Sub>{p.active ? 'Blocklists and daily limits enforced' : 'Protection is turned off'}</Sub>
          </View>
          <Switch value={p.active} onValueChange={onToggle} trackColor={{ true: t.ok }} />
        </View>
        <LockBar lockKey="protection" />
      </Card>

      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
        {stats.map(([label, n]) => (
          <View key={label} style={{
            flexGrow: 1, flexBasis: '30%', backgroundColor: t.card, borderColor: t.cardBorder, borderWidth: 1,
            borderRadius: 18, padding: 14
          }}>
            <Text style={{ color: t.accent, fontSize: 26, fontWeight: '800', letterSpacing: -0.5 }}>{n}</Text>
            <Text style={{ color: t.sub, fontSize: 11, marginTop: 3 }}>{label}</Text>
          </View>
        ))}
      </View>

      <Card>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
          <Title icon={Icons.shield}>System Health</Title>
          <Badge label={healthy ? 'Healthy' : 'Attention'} tone={healthy ? 'ok' : 'warn'} />
        </View>
        {health.map(([label, ok]) => (
          <Row key={label} label={label} right={<Badge label={ok ? 'On' : 'Off'} tone={ok ? 'ok' : 'danger'} />} />
        ))}
        {perms?.adminLost && (
          <Text style={{ color: t.danger, marginTop: 8 }}>Device admin was deactivated. Re-enable it in Settings.</Text>
        )}
      </Card>
    </ScrollView>
  );
}
