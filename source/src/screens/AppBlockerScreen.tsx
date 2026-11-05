import React, { useEffect, useMemo, useState } from 'react';
import {
  Image,
  Modal,
  Pressable,
  ScrollView,
  SectionList,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';
import { InstalledApp, Native } from '../native/BlockerNative';
import { useProtection } from '../hooks/useProtection';
import {
  addApp,
  exemptApp,
  removeApp,
  setAppUsageLimit,
  unexemptApp,
} from '../services/ProtectionManager';
import {
  Btn,
  CyberSwitch,
  GlowCard,
  Icons,
  IconToggle,
  LockBar,
  PressableScale,
  StatusPillBadge,
  Sub,
  Title,
  errMsg,
  showAlert,
  useTheme,
} from '../ui';

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

  return uri ? (
    <Image source={{ uri }} style={{ width: 42, height: 42, borderRadius: 12 }} />
  ) : (
    <View
      style={{
        width: 42,
        height: 42,
        borderRadius: 12,
        backgroundColor: t.bgAlt,
        borderColor: t.cardBorder,
        borderWidth: 1,
      }}
    />
  );
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

function AppLimitModal({
  app,
  currentLimit,
  todayUsage,
  hasUsageAccess,
  onClose,
  onSave,
}: LimitModalProps) {
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
      <View
        style={{
          flex: 1,
          backgroundColor: 'rgba(3, 4, 10, 0.78)',
          justifyContent: 'center',
          padding: 20,
        }}>
        <View
          style={{
            backgroundColor: t.bgAlt,
            borderColor: t.cardBorderGlow,
            borderWidth: 1.5,
            borderRadius: 24,
            padding: 22,
            maxHeight: '90%',
            shadowColor: '#000',
            shadowOpacity: 0.6,
            shadowRadius: 24,
            shadowOffset: { width: 0, height: 10 },
            elevation: 14,
          }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 14, marginBottom: 18 }}>
            <AppIcon pkg={app.packageName} />
            <View style={{ flex: 1 }}>
              <Text style={{ color: '#FFFFFF', fontSize: 18, fontWeight: '800' }} numberOfLines={1}>
                {app.label}
              </Text>
              <Text style={{ color: t.sub, fontSize: 12, marginTop: 2 }} numberOfLines={1}>
                {app.packageName}
              </Text>
            </View>
          </View>

          <ScrollView showsVerticalScrollIndicator={false}>
            {/* Daily Usage Progress Bento Card */}
            <GlowCard style={{ padding: 14, marginBottom: 16 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                <Text style={{ color: t.sub, fontSize: 13 }}>Today's Usage</Text>
                <Text style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 14 }}>
                  {fmtMinutes(todayUsage)}
                </Text>
              </View>
              {selectedMinutes > 0 && (
                <View style={{ marginTop: 10 }}>
                  <View
                    style={{
                      height: 7,
                      borderRadius: 4,
                      backgroundColor: 'rgba(255, 255, 255, 0.08)',
                      overflow: 'hidden',
                      flexDirection: 'row',
                    }}>
                    <View
                      style={{
                        height: '100%',
                        width: `${Math.min(100, Math.round((todayUsage / selectedMinutes) * 100))}%`,
                        backgroundColor: todayUsage >= selectedMinutes ? t.danger : t.accent,
                      }}
                    />
                  </View>
                  <Text style={{ color: t.sub, fontSize: 11.5, marginTop: 6 }}>
                    {todayUsage >= selectedMinutes
                      ? 'Limit reached today — app is blocked'
                      : `${fmtMinutes(Math.max(0, selectedMinutes - todayUsage))} remaining today`}
                  </Text>
                </View>
              )}
            </GlowCard>

            {!hasUsageAccess && (
              <View
                style={{
                  backgroundColor: t.warnSoft,
                  borderColor: t.warnBorder,
                  borderWidth: 1,
                  borderRadius: 14,
                  padding: 14,
                  marginBottom: 16,
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: 12,
                }}>
                <Icons.clock size={20} color={t.warn} />
                <View style={{ flex: 1 }}>
                  <Text style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 13 }}>
                    Usage Access Required
                  </Text>
                  <Text style={{ color: t.sub, fontSize: 12, marginTop: 2 }}>
                    Blocker needs Usage Access to accurately track daily screen time.
                  </Text>
                  <PressableScale
                    onPress={() => Native.openPermissionSettings('usageAccess')}
                    style={{ marginTop: 8, alignSelf: 'flex-start' }}>
                    <Text style={{ color: t.warn, fontWeight: '700', fontSize: 13 }}>
                      Enable in Settings →
                    </Text>
                  </PressableScale>
                </View>
              </View>
            )}

            <Text style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 14, marginBottom: 6 }}>
              Set Daily Usage Limit
            </Text>
            <Text style={{ color: t.sub, fontSize: 12, marginBottom: 14 }}>
              {app.label} will be blocked once this daily allowance is reached.
            </Text>

            {/* Presets Grid */}
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 14 }}>
              {presets.map(p => {
                const active = !isCustom && selectedMinutes === p.val;
                return (
                  <PressableScale
                    key={p.val}
                    onPress={() => {
                      setIsCustom(false);
                      setSelectedMinutes(p.val);
                    }}
                    style={{
                      backgroundColor: active ? t.accentWell : t.card,
                      borderColor: active ? t.accentWellBorder : t.cardBorder,
                      borderWidth: 1,
                      borderRadius: 12,
                      paddingVertical: 8,
                      paddingHorizontal: 14,
                    }}>
                    <Text
                      style={{
                        color: active ? '#FFFFFF' : t.sub,
                        fontWeight: active ? '700' : '600',
                        fontSize: 13,
                      }}>
                      {p.label}
                    </Text>
                  </PressableScale>
                );
              })}
              <PressableScale
                onPress={() => setIsCustom(true)}
                style={{
                  backgroundColor: isCustom ? t.accentWell : t.card,
                  borderColor: isCustom ? t.accentWellBorder : t.cardBorder,
                  borderWidth: 1,
                  borderRadius: 12,
                  paddingVertical: 8,
                  paddingHorizontal: 14,
                }}>
                <Text
                  style={{
                    color: isCustom ? '#FFFFFF' : t.sub,
                    fontWeight: isCustom ? '700' : '600',
                    fontSize: 13,
                  }}>
                  Custom
                </Text>
              </PressableScale>
            </View>

            {/* Custom Input */}
            {isCustom && (
              <GlowCard style={{ padding: 14, marginBottom: 14 }}>
                <Text style={{ color: t.sub, fontSize: 12, marginBottom: 10 }}>
                  Enter custom daily allowance:
                </Text>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 14 }}>
                  <View style={{ flex: 1, flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                    <TextInput
                      value={customHours}
                      onChangeText={setCustomHours}
                      keyboardType="numeric"
                      style={{
                        backgroundColor: t.bg,
                        borderColor: t.cardBorder,
                        borderWidth: 1,
                        borderRadius: 10,
                        color: '#FFFFFF',
                        paddingHorizontal: 12,
                        paddingVertical: 8,
                        textAlign: 'center',
                        width: 55,
                        fontWeight: '700',
                        fontSize: 14,
                      }}
                    />
                    <Text style={{ color: t.sub, fontSize: 13 }}>hours</Text>
                  </View>
                  <View style={{ flex: 1, flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                    <TextInput
                      value={customMins}
                      onChangeText={setCustomMins}
                      keyboardType="numeric"
                      style={{
                        backgroundColor: t.bg,
                        borderColor: t.cardBorder,
                        borderWidth: 1,
                        borderRadius: 10,
                        color: '#FFFFFF',
                        paddingHorizontal: 12,
                        paddingVertical: 8,
                        textAlign: 'center',
                        width: 55,
                        fontWeight: '700',
                        fontSize: 14,
                      }}
                    />
                    <Text style={{ color: t.sub, fontSize: 13 }}>mins</Text>
                  </View>
                </View>
              </GlowCard>
            )}
          </ScrollView>

          <View style={{ flexDirection: 'row', gap: 10, marginTop: 16 }}>
            <View style={{ flex: 1 }}>
              <Btn label="Cancel" kind="ghost" onPress={onClose} disabled={saving} />
            </View>
            <View style={{ flex: 1 }}>
              <Btn
                label={saving ? 'Saving…' : 'Save Limit'}
                kind="primary"
                onPress={handleSave}
                disabled={saving}
              />
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
    Native.getInstalledApps(showSystem)
      .then(setApps)
      .catch(e => showAlert('Blocker', errMsg(e)))
      .finally(() => setLoading(false));
  }, [showSystem]);

  const blocked = useMemo(() => new Set(p.state.blockedApps), [p.state.blockedApps]);
  const exempt = useMemo(() => new Set(p.state.exemptApps), [p.state.exemptApps]);
  const pendingRemoval = useMemo(
    () => new Set((p.state.queue.remove_apps ?? []).map(a => a.v)),
    [p.state.queue]
  );
  const pendingExempt = useMemo(
    () => new Set((p.state.queue.exempt_apps ?? []).map(a => a.v)),
    [p.state.queue]
  );
  const pendingLimits = useMemo(
    () => new Set((p.state.queue.app_limits ?? []).map(a => a.v)),
    [p.state.queue]
  );

  const appLimits = useMemo(() => p.appLimits ?? {}, [p.appLimits]);
  const appUsage = useMemo(() => p.appUsageToday ?? {}, [p.appUsageToday]);

  const sections = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const groups = new Map<string, InstalledApp[]>();
    for (const a of apps) {
      if (needle && !a.label.toLowerCase().includes(needle) && !a.packageName.toLowerCase().includes(needle))
        continue;
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
        showAlert(
          r === 'restarted' ? 'Timer restarted' : 'Unblock requested',
          `${a.label} stays blocked until the ${p.delayDays}-day timer completes. Confirm it in Settings or below.`
        );
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
        showAlert(
          'Change requested',
          `${a.label} keeps being screen-scanned until the ${p.delayDays}-day timer completes. Confirm it in Settings.`
        );
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

        {/* Bento Search Bar */}
        <View
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            backgroundColor: t.card,
            borderColor: t.cardBorder,
            borderWidth: 1,
            borderRadius: 14,
            paddingHorizontal: 14,
            marginTop: 12,
          }}>
          <Icons.search size={16} color={t.sub} />
          <TextInput
            value={q}
            onChangeText={setQ}
            placeholder="Search apps"
            placeholderTextColor={t.sub}
            style={{ flex: 1, color: '#FFFFFF', paddingHorizontal: 10, paddingVertical: 10, fontSize: 13.5 }}
          />
        </View>

        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 12 }}>
          <Text style={{ color: t.sub, fontSize: 13 }}>Show system apps</Text>
          <CyberSwitch value={showSystem} onValueChange={setShowSystem} />
        </View>

        <Text style={{ color: t.sub, fontSize: 12, marginTop: 10, lineHeight: 16 }}>
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
        initialNumToRender={12}
        maxToRenderPerBatch={12}
        windowSize={5}
        removeClippedSubviews={true}
        ListEmptyComponent={
          <Text style={{ color: t.sub, textAlign: 'center', marginTop: 40, fontSize: 13.5 }}>
            {loading ? 'Loading apps…' : 'No apps found'}
          </Text>
        }
        renderSectionHeader={({ section }) => (
          <Text
            style={{
              color: t.sub,
              fontSize: 12,
              fontWeight: '700',
              textTransform: 'uppercase',
              letterSpacing: 0.5,
              paddingTop: 14,
              paddingBottom: 6,
              backgroundColor: t.bg,
            }}>
            {section.title}
          </Text>
        )}
        renderItem={({ item }) => {
          const isExempt = exempt.has(item.packageName) || pendingExempt.has(item.packageName);
          const limit = appLimits[item.packageName] ?? 0;
          const used = appUsage[item.packageName] ?? 0;
          const isPendingLimit = pendingLimits.has(item.packageName);

          return (
            <View
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                paddingVertical: 9,
                gap: 12,
                borderBottomColor: t.cardBorder,
                borderBottomWidth: 1,
              }}>
              <AppIcon pkg={item.packageName} />
              <View style={{ flex: 1 }}>
                <Text style={{ color: '#FFFFFF', fontWeight: '600', fontSize: 13.5 }} numberOfLines={1}>
                  {item.label}
                </Text>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 2 }}>
                  <Text style={{ color: t.sub, fontSize: 11.5 }} numberOfLines={1}>
                    {used > 0 ? `${fmtMinutes(used)} today` : item.packageName}
                  </Text>
                </View>

                {/* Daily limit badge button */}
                <PressableScale
                  onPress={() => setTargetApp(item)}
                  style={{
                    marginTop: 5,
                    alignSelf: 'flex-start',
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: 5,
                    backgroundColor: limit > 0 ? t.accentWell : 'rgba(255, 255, 255, 0.04)',
                    borderColor: limit > 0 ? t.accentWellBorder : t.border,
                    borderWidth: 1,
                    borderRadius: 8,
                    paddingHorizontal: 8,
                    paddingVertical: 3,
                  }}>
                  <Icons.clock size={11} color={limit > 0 ? '#A78BFA' : t.sub} />
                  <Text
                    style={{
                      color: limit > 0 ? '#A78BFA' : t.sub,
                      fontSize: 11,
                      fontWeight: '700',
                    }}>
                    {limit > 0 ? `${fmtMinutes(limit)}/day` : 'Set timer'}
                  </Text>
                  {isPendingLimit && (
                    <Text style={{ color: t.warn, fontSize: 10, fontWeight: '700' }}>• pending</Text>
                  )}
                </PressableScale>
              </View>

              {pendingRemoval.has(item.packageName) && (
                <StatusPillBadge label="Unblock pending" tone="warn" size="sm" icon={false} />
              )}
              <IconToggle
                Icon={isExempt ? Icons.eyeOff : Icons.eye}
                active={isExempt}
                onPress={() => toggleExempt(item)}
              />
              <Switch
                value={blocked.has(item.packageName)}
                onValueChange={v => toggleBlock(item, v)}
                trackColor={{ true: t.danger, false: t.border }}
                thumbColor="#FFFFFF"
              />
            </View>
          );
        }}
      />

      <AppLimitModal
        app={targetApp}
        currentLimit={targetApp ? appLimits[targetApp.packageName] ?? 0 : 0}
        todayUsage={targetApp ? appUsage[targetApp.packageName] ?? 0 : 0}
        hasUsageAccess={p.perms?.usageAccess ?? false}
        onClose={() => setTargetApp(null)}
        onSave={saveAppLimit}
      />
    </View>
  );
}
