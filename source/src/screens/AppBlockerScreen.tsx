import React, { useEffect, useMemo, useState } from 'react';
import { Image, SectionList, Switch, Text, TextInput, View } from 'react-native';
import { InstalledApp, Native } from '../native/BlockerNative';
import { useProtection } from '../hooks/useProtection';
import { addApp, exemptApp, removeApp, unexemptApp } from '../services/ProtectionManager';
import { Badge, Icons, IconToggle, LockBar, Title, errMsg, showAlert, useTheme } from '../ui';

const iconCache = new Map<string, string>();

export function AppIcon({ pkg }: { pkg: string }) {
  const t = useTheme();
  const [uri, setUri] = useState<string | undefined>(iconCache.get(pkg));
  useEffect(() => {
    if (uri) return;
    let live = true;
    Native.getAppIcon(pkg, 96)
      .then(u => {
        iconCache.set(pkg, u);
        if (live) setUri(u);
      })
      .catch(() => {});
    return () => {
      live = false;
    };
  }, [pkg, uri]);
  return uri
    ? <Image source={{ uri }} style={{ width: 40, height: 40, borderRadius: 12 }} />
    : <View style={{ width: 40, height: 40, borderRadius: 12, backgroundColor: t.glass }} />;
}

export default function AppBlockerScreen() {
  const t = useTheme();
  const p = useProtection();
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [q, setQ] = useState('');
  const [showSystem, setShowSystem] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    Native.getInstalledApps(showSystem).then(setApps).catch(e => showAlert('Blocker', errMsg(e))).finally(() => setLoading(false));
  }, [showSystem]);

  const blocked = useMemo(() => new Set(p.state.blockedApps), [p.state.blockedApps]);
  const exempt = useMemo(() => new Set(p.state.exemptApps), [p.state.exemptApps]);
  const pendingRemoval = useMemo(() => new Set((p.state.queue.remove_apps ?? []).map(a => a.v)), [p.state.queue]);
  const pendingExempt = useMemo(() => new Set((p.state.queue.exempt_apps ?? []).map(a => a.v)), [p.state.queue]);

  const sections = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const groups = new Map<string, InstalledApp[]>();
    for (const a of apps) {
      if (needle && !a.label.toLowerCase().includes(needle) && !a.packageName.toLowerCase().includes(needle)) continue;
      groups.set(a.category, [...(groups.get(a.category) ?? []), a]);
    }
    const order = (c: string) => (c === 'Browsers' ? 0 : c === 'Other' ? 2 : 1);
    return [...groups.entries()]
      .sort((a, b) => order(a[0]) - order(b[0]) || a[0].localeCompare(b[0]))
      .map(([title, data]) => ({ title, data }));
  }, [apps, q]);

  const toggleBlock = async (a: InstalledApp, on: boolean) => {
    try {
      if (on) return await addApp(a.packageName);
      const r = await removeApp(a.packageName, a.label);
      if (r !== 'applied') {
        showAlert(r === 'restarted' ? 'Timer restarted' : 'Unblock requested',
          `${a.label} stays blocked until the ${p.delayDays}-day timer completes. Confirm it in Settings or below.`);
      }
    } catch (e) {
      showAlert('Blocker', errMsg(e));
    }
  };

  const toggleExempt = async (a: InstalledApp) => {
    try {
      if (exempt.has(a.packageName)) return await unexemptApp(a.packageName);
      const r = await exemptApp(a.packageName, a.label);
      if (r !== 'applied') {
        showAlert('Change requested',
          `${a.label} keeps being screen-scanned until the ${p.delayDays}-day timer completes. Confirm it in Settings.`);
      }
    } catch (e) {
      showAlert('Blocker', errMsg(e));
    }
  };

  return (
    <View style={{ flex: 1 }}>
      <View style={{ padding: 16, paddingBottom: 8 }}>
        <Title icon={Icons.apps}>Apps</Title>
        <View style={{ flexDirection: 'row', alignItems: 'center', backgroundColor: t.card, borderColor: t.cardBorder, borderWidth: 1, borderRadius: 14, paddingHorizontal: 12, marginTop: 12 }}>
          <Icons.search size={16} color={t.sub} />
          <TextInput
            value={q}
            onChangeText={setQ}
            placeholder="Search apps"
            placeholderTextColor={t.sub}
            style={{ flex: 1, color: t.text, paddingHorizontal: 8, paddingVertical: 10 }}
          />
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 10 }}>
          <Text style={{ color: t.sub }}>Show system apps</Text>
          <Switch value={showSystem} onValueChange={setShowSystem} trackColor={{ true: t.accent }} />
        </View>
        <Text style={{ color: t.sub, fontSize: 12, marginTop: 8 }}>
          The eye icon stops on-screen keyword scanning for that one app (block list and browser checks still apply).
        </Text>
        <LockBar lockKey="remove_apps" />
        <LockBar lockKey="exempt_apps" />
      </View>
      <SectionList
        sections={sections}
        keyExtractor={a => a.packageName}
        contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 24 }}
        ListEmptyComponent={<Text style={{ color: t.sub, textAlign: 'center', marginTop: 40 }}>{loading ? 'Loading apps…' : 'No apps found'}</Text>}
        renderSectionHeader={({ section }) => (
          <Text style={{ color: t.sub, fontSize: 12, fontWeight: '700', textTransform: 'uppercase', paddingTop: 14, paddingBottom: 6, backgroundColor: t.bg }}>
            {section.title}
          </Text>
        )}
        renderItem={({ item }) => {
          const isExempt = exempt.has(item.packageName) || pendingExempt.has(item.packageName);
          return (
            <View style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 8, gap: 10 }}>
              <AppIcon pkg={item.packageName} />
              <View style={{ flex: 1 }}>
                <Text style={{ color: t.text, fontWeight: '600' }} numberOfLines={1}>{item.label}</Text>
                <Text style={{ color: t.sub, fontSize: 12 }} numberOfLines={1}>{item.packageName}</Text>
              </View>
              {pendingRemoval.has(item.packageName) && <Badge label="Unblock pending" tone="warn" icon={false} />}
              <IconToggle Icon={isExempt ? Icons.eyeOff : Icons.eye} active={isExempt} onPress={() => toggleExempt(item)} />
              <Switch value={blocked.has(item.packageName)} onValueChange={v => toggleBlock(item, v)} trackColor={{ true: t.danger }} />
            </View>
          );
        }}
      />
    </View>
  );
}
