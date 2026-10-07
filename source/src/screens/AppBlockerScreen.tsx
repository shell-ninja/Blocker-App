import React, { useEffect, useMemo, useState } from 'react';
import { Image, Modal, Pressable, ScrollView, SectionList, Switch, Text, TextInput, View } from 'react-native';
import { InstalledApp, Native } from '../native/BlockerNative';
import { useProtection } from '../hooks/useProtection';
import { addApp, exemptApp, removeApp, setAppUsageLimit, unexemptApp } from '../services/ProtectionManager';
import { Badge, Btn, Card, Icons, IconToggle, LockBar, Sub, Title, errMsg, showAlert, useTheme } from '../ui';

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

function fmtMinutes(m: number): string {
  if (m <= 0) return '0m';
  const h = Math.floor(m / 60);
  const min = m % 60;
  if (h > 0 && min > 0) return `${h}h ${min}m`;
  if (h > 0) return `${h}h`;
  return `${min}m`;
}

interface LimitModalProps {
  app: InstalledApp | null;
  currentLimit: number;
  todayUsage: number;
  hasUsageAccess: boolean;
  onClose: () => void;
  onSave: (minutes: number) => Promise<void>;
}

function AppLimitModal({ app, currentLimit, todayUsage, hasUsageAccess, onClose, onSave }: LimitModalProps) {
  const t = useTheme();
  const [selectedMinutes, setSelectedMinutes] = useState<number>(currentLimit);
  const [customHours, setCustomHours] = useState<string>(
    currentLimit > 0 ? String(Math.floor(currentLimit / 60)) : '1'
  );
  const [customMins, setCustomMins] = useState<string>(
    currentLimit > 0 ? String(currentLimit % 60) : '0'
  );
  const [isCustom, setIsCustom] = useState<boolean>(
    currentLimit > 0 && ![0, 15, 30, 45, 60, 90, 120, 180, 240].includes(currentLimit)
  );
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setSelectedMinutes(currentLimit);
    setIsCustom(currentLimit > 0 && ![0, 15, 30, 45, 60, 90, 120, 180, 240].includes(currentLimit));
    if (currentLimit > 0) {
      setCustomHours(String(Math.floor(currentLimit / 60)));
      setCustomMins(String(currentLimit % 60));
    }
  }, [currentLimit, app]);

  if (!app) return null;

  const presets = [
    { label: 'Off', val: 0 },
    { label: '15m', val: 15 },
    { label: '30m', val: 30 },
    { label: '45m', val: 45 },
    { label: '1h', val: 60 },
    { label: '1.5h', val: 90 },
    { label: '2h', val: 120 },
    { label: '3h', val: 180 },
    { label: '4h', val: 240 },
  ];

  const handleSave = async () => {
    try {
      setSaving(true);
      let minsToSave = selectedMinutes;
      if (isCustom) {
        const h = parseInt(customHours.trim() || '0', 10);
        const m = parseInt(customMins.trim() || '0', 10);
        minsToSave = Math.max(0, h * 60 + m);
      }
      await onSave(minsToSave);
      onClose();
    } catch (e) {
      showAlert('Blocker', errMsg(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal visible transparent animationType="fade" onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.7)', justifyContent: 'center', padding: 20 }}>
        <View style={{
          backgroundColor: t.bgAlt, borderColor: t.cardBorder, borderWidth: 1, borderRadius: 24, padding: 20,
          maxHeight: '90%'
        }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 16 }}>
            <AppIcon pkg={app.packageName} />
            <View style={{ flex: 1 }}>
              <Text style={{ color: t.text, fontSize: 18, fontWeight: '700' }} numberOfLines={1}>{app.label}</Text>
              <Text style={{ color: t.sub, fontSize: 12 }} numberOfLines={1}>{app.packageName}</Text>
            </View>
          </View>

          <ScrollView showsVerticalScrollIndicator={false}>
            {/* Daily Usage Progress */}
            <Card style={{ padding: 14, marginBottom: 16 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                <Text style={{ color: t.sub, fontSize: 13 }}>Today's Usage</Text>
                <Text style={{ color: t.text, fontWeight: '700', fontSize: 14 }}>{fmtMinutes(todayUsage)}</Text>
              </View>
              {selectedMinutes > 0 && (
                <View style={{ marginTop: 8 }}>
                  <View style={{
                    height: 6, borderRadius: 3, backgroundColor: t.glass, overflow: 'hidden', flexDirection: 'row'
                  }}>
                    <View style={{
                      height: '100%',
                      width: `${Math.min(100, Math.round((todayUsage / selectedMinutes) * 100))}%`,
                      backgroundColor: todayUsage >= selectedMinutes ? t.danger : t.accent
                    }} />
                  </View>
                  <Text style={{ color: t.sub, fontSize: 11, marginTop: 4 }}>
                    {todayUsage >= selectedMinutes
                      ? 'Limit reached today — app is blocked'
                      : `${fmtMinutes(Math.max(0, selectedMinutes - todayUsage))} remaining today`}
                  </Text>
                </View>
              )}
            </Card>

            {!hasUsageAccess && (
              <View style={{
                backgroundColor: t.warnSoft, borderColor: t.warn, borderWidth: 1, borderRadius: 14,
                padding: 12, marginBottom: 16, flexDirection: 'row', alignItems: 'center', gap: 10
              }}>
                <Icons.clock size={20} color={t.warn} />
                <View style={{ flex: 1 }}>
                  <Text style={{ color: t.text, fontWeight: '700', fontSize: 13 }}>Usage Access Required</Text>
                  <Text style={{ color: t.sub, fontSize: 12, marginTop: 2 }}>
                    Blocker needs Usage Access to accurately track daily screen time.
                  </Text>
                  <Pressable
                    onPress={() => Native.openPermissionSettings('usageAccess')}
                    style={{ marginTop: 8, alignSelf: 'flex-start' }}>
                    <Text style={{ color: t.warn, fontWeight: '700', fontSize: 13 }}>Enable in Settings →</Text>
                  </Pressable>
                </View>
              </View>
            )}

            <Text style={{ color: t.text, fontWeight: '700', fontSize: 14, marginBottom: 10 }}>
              Set Daily Usage Limit
            </Text>
            <Text style={{ color: t.sub, fontSize: 12, marginBottom: 12, lineHeight: 17 }}>
              Once you reach this time limit in a single day, {app.label} will be automatically blocked until midnight.
            </Text>

            {/* Presets Grid */}
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 14 }}>
              {presets.map(p => {
                const active = !isCustom && selectedMinutes === p.val;
                return (
                  <Pressable
                    key={p.val}
                    onPress={() => {
                      setIsCustom(false);
                      setSelectedMinutes(p.val);
                    }}
                    style={({ pressed }) => [{
                      backgroundColor: active ? t.accent : t.card,
                      borderColor: active ? t.accent : t.cardBorder,
                      borderWidth: 1, borderRadius: 12, paddingVertical: 8, paddingHorizontal: 14,
                      opacity: pressed ? 0.8 : 1
                    }]}>
                    <Text style={{ color: active ? '#fff' : t.text, fontWeight: '600', fontSize: 13 }}>
                      {p.label}
                    </Text>
                  </Pressable>
                );
              })}
              <Pressable
                onPress={() => setIsCustom(true)}
                style={({ pressed }) => [{
                  backgroundColor: isCustom ? t.accent : t.card,
                  borderColor: isCustom ? t.accent : t.cardBorder,
                  borderWidth: 1, borderRadius: 12, paddingVertical: 8, paddingHorizontal: 14,
                  opacity: pressed ? 0.8 : 1
                }]}>
                <Text style={{ color: isCustom ? '#fff' : t.text, fontWeight: '600', fontSize: 13 }}>
                  Custom
                </Text>
              </Pressable>
            </View>

            {/* Custom Input */}
            {isCustom && (
              <View style={{
                backgroundColor: t.card, borderColor: t.cardBorder, borderWidth: 1, borderRadius: 14,
                padding: 12, marginBottom: 14
              }}>
                <Text style={{ color: t.sub, fontSize: 12, marginBottom: 8 }}>Enter custom daily allowance:</Text>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                  <View style={{ flex: 1, flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                    <TextInput
                      value={customHours}
                      onChangeText={setCustomHours}
                      keyboardType="numeric"
                      style={{
                        backgroundColor: t.glass, borderColor: t.border, borderWidth: 1, borderRadius: 10,
                        color: t.text, paddingHorizontal: 12, paddingVertical: 8, textAlign: 'center', width: 55, fontWeight: '700'
                      }}
                    />
                    <Text style={{ color: t.sub, fontSize: 13 }}>hours</Text>
                  </View>
                  <View style={{ flex: 1, flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                    <TextInput
                      value={customMins}
                      onChangeText={setCustomMins}
                      keyboardType="numeric"
                      style={{
                        backgroundColor: t.glass, borderColor: t.border, borderWidth: 1, borderRadius: 10,
                        color: t.text, paddingHorizontal: 12, paddingVertical: 8, textAlign: 'center', width: 55, fontWeight: '700'
                      }}
                    />
                    <Text style={{ color: t.sub, fontSize: 13 }}>mins</Text>
                  </View>
                </View>
              </View>
            )}
          </ScrollView>

          <View style={{ flexDirection: 'row', gap: 10, marginTop: 16 }}>
            <View style={{ flex: 1 }}>
              <Btn label="Cancel" kind="ghost" onPress={onClose} disabled={saving} />
            </View>
            <View style={{ flex: 1 }}>
              <Btn label={saving ? "Saving…" : "Save Limit"} kind="primary" onPress={handleSave} disabled={saving} />
            </View>
          </View>
        </View>
      </View>
    </Modal>
  );
}

export default function AppBlockerScreen() {
  const t = useTheme();
  const p = useProtection();
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [q, setQ] = useState('');
  const [showSystem, setShowSystem] = useState(false);
  const [loading, setLoading] = useState(true);
  const [targetApp, setTargetApp] = useState<InstalledApp | null>(null);

  useEffect(() => {
    setLoading(true);
    Native.getInstalledApps(showSystem).then(setApps).catch(e => showAlert('Blocker', errMsg(e))).finally(() => setLoading(false));
  }, [showSystem]);

  const blocked = useMemo(() => new Set(p.state.blockedApps), [p.state.blockedApps]);
  const exempt = useMemo(() => new Set(p.state.exemptApps), [p.state.exemptApps]);
  const pendingRemoval = useMemo(() => new Set((p.state.queue.remove_apps ?? []).map(a => a.v)), [p.state.queue]);
  const pendingExempt = useMemo(() => new Set((p.state.queue.exempt_apps ?? []).map(a => a.v)), [p.state.queue]);
  const pendingLimits = useMemo(() => new Set((p.state.queue.app_limits ?? []).map(a => a.v)), [p.state.queue]);

  const appLimits = useMemo(() => p.appLimits ?? {}, [p.appLimits]);
  const appUsage = useMemo(() => p.appUsageToday ?? {}, [p.appUsageToday]);

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

  const saveAppLimit = async (minutes: number) => {
    if (!targetApp) return;
    const r = await setAppUsageLimit(targetApp.packageName, minutes, targetApp.label);
    if (r !== 'applied') {
      showAlert(
        r === 'restarted' ? 'Timer restarted' : 'Change requested',
        `Increasing or removing the limit for ${targetApp.label} requires the ${p.delayDays}-day delay timer.`
      );
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
          Tap the timer button on any app to set a daily usage limit (e.g. 3h a day). Once reached, the app is blocked.
        </Text>
        <LockBar lockKey="remove_apps" />
        <LockBar lockKey="exempt_apps" />
        <LockBar lockKey="app_limits" />
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
          const limit = appLimits[item.packageName] ?? 0;
          const used = appUsage[item.packageName] ?? 0;
          const isPendingLimit = pendingLimits.has(item.packageName);

          return (
            <View style={{
              flexDirection: 'row', alignItems: 'center', paddingVertical: 8, gap: 10,
              borderBottomColor: t.glass, borderBottomWidth: 1
            }}>
              <AppIcon pkg={item.packageName} />
              <View style={{ flex: 1 }}>
                <Text style={{ color: t.text, fontWeight: '600' }} numberOfLines={1}>{item.label}</Text>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 2 }}>
                  <Text style={{ color: t.sub, fontSize: 11 }} numberOfLines={1}>
                    {used > 0 ? `${fmtMinutes(used)} today` : item.packageName}
                  </Text>
                </View>
                {/* Daily limit badge button */}
                <Pressable
                  onPress={() => setTargetApp(item)}
                  style={({ pressed }) => [{
                    marginTop: 4, alignSelf: 'flex-start', flexDirection: 'row', alignItems: 'center', gap: 5,
                    backgroundColor: limit > 0 ? t.accentSoft : t.glass,
                    borderColor: limit > 0 ? t.accent : t.border,
                    borderWidth: 1, borderRadius: 8, paddingHorizontal: 7, paddingVertical: 2,
                    opacity: pressed ? 0.7 : 1
                  }]}>
                  <Icons.clock size={11} color={limit > 0 ? t.accent : t.sub} />
                  <Text style={{ color: limit > 0 ? t.accent : t.sub, fontSize: 11, fontWeight: '600' }}>
                    {limit > 0 ? `${fmtMinutes(limit)}/day` : 'Set timer'}
                  </Text>
                  {isPendingLimit && (
                    <Text style={{ color: t.warn, fontSize: 10, fontWeight: '700' }}>• pending</Text>
                  )}
                </Pressable>
              </View>

              {pendingRemoval.has(item.packageName) && <Badge label="Unblock pending" tone="warn" icon={false} />}
              <IconToggle Icon={isExempt ? Icons.eyeOff : Icons.eye} active={isExempt} onPress={() => toggleExempt(item)} />
              <Switch value={blocked.has(item.packageName)} onValueChange={v => toggleBlock(item, v)} trackColor={{ true: t.danger }} />
            </View>
          );
        }}
      />

      <AppLimitModal
        app={targetApp}
        currentLimit={targetApp ? (appLimits[targetApp.packageName] ?? 0) : 0}
        todayUsage={targetApp ? (appUsage[targetApp.packageName] ?? 0) : 0}
        hasUsageAccess={p.perms?.usageAccess ?? false}
        onClose={() => setTargetApp(null)}
        onSave={saveAppLimit}
      />
    </View>
  );
}
