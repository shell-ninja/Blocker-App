import React, { useEffect } from 'react';
import { ScrollView, Switch, Text, View } from 'react-native';
import { useProtection } from '../hooks/useProtection';
import { refresh, setProtection } from '../services/ProtectionManager';
import { Badge, Card, Icons, LockBar, Row, Sub, Title, errMsg, showAlert, useTheme } from '../ui';

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

  return (
    <ScrollView contentContainerStyle={{ padding: 16 }}>
      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <View style={{ flex: 1, paddingRight: 12 }}>
            <Title size={22} icon={p.active ? Icons.shieldOn : Icons.shieldOff} tone={p.active ? 'ok' : 'accent'}>
              {p.active ? 'Protection active' : 'Protection off'}
            </Title>
            <Sub>{p.active ? 'Sites, keywords and selected apps are blocked.' : 'Nothing is being blocked right now.'}</Sub>
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
            <Text style={{ color: t.accent, fontSize: 26, fontWeight: '800' }}>{n}</Text>
            <Text style={{ color: t.sub, fontSize: 12, marginTop: 2 }}>{label} today</Text>
          </View>
        ))}
      </View>

      <Card>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
          <Title icon={Icons.shield}>System health</Title>
          <Badge label={healthy ? 'All good' : 'Attention'} tone={healthy ? 'ok' : 'warn'} />
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
