import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Animated,
  LayoutAnimation,
  Pressable,
  ScrollView,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';
import { BookOpen, ChevronDown, Trash2 } from 'lucide-react-native';
import { InstalledApp, Native, Schedule } from '../native/BlockerNative';
import { useNow, useProtection } from '../hooks/useProtection';
import {
  MAX_FOCUS_APPS,
  SchedulePatch,
  addFocusApp,
  addSchedule,
  deleteSchedule,
  endFocusEarly,
  refresh,
  removeFocusApp,
  setFocusRemaining,
  setGranularFocusToggle,
  startFocus,
  updateSchedule,
} from '../services/ProtectionManager';
import { AppIcon } from './AppBlockerScreen';
import StudyScreen from './StudyScreen';
import {
  AnimatedToggleRow,
  Btn,
  CyberSwitch,
  Fab,
  GlowCard,
  Icons,
  LockBar,
  PressableScale,
  StatusPillBadge,
  Sub,
  Title,
  errMsg,
  fmt,
  showAlert,
  useTheme,
} from '../ui';

const PRESETS = [30, 60, 120, 240, 480, 720, 1440];
const label = (m: number) => (m < 60 ? `${m} min` : `${m / 60} h`);

/** 12-hour clock text, e.g. 0 -> "12:00 AM", 810 -> "1:30 PM". */
function clock12(m: number) {
  const h24 = Math.floor(m / 60) % 24;
  const min = m % 60;
  const ampm = h24 < 12 ? 'AM' : 'PM';
  const h12 = h24 % 12 === 0 ? 12 : h24 % 12;
  return `${h12}:${String(min).padStart(2, '0')} ${ampm}`;
}

/** Rounds up to the next whole hour, e.g. 14:37 -> 15:00. */
function nextRoundedHour(): number {
  const d = new Date();
  return ((d.getHours() + 1) % 24) * 60;
}

function Chips({
  values,
  selected,
  onPick,
}: {
  values: number[];
  selected?: number;
  onPick: (m: number) => void;
}) {
  const t = useTheme();
  return (
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 12 }}>
      {values.map(m => {
        const sel = m === selected;
        return (
          <PressableScale
            key={m}
            onPress={() => onPick(m)}
            style={{
              paddingVertical: 8,
              paddingHorizontal: 15,
              borderRadius: 999,
              borderWidth: 1,
              borderColor: sel ? t.accentWellBorder : t.border,
              backgroundColor: sel ? t.accentWell : 'transparent',
              shadowColor: sel ? t.accent : 'transparent',
              shadowOpacity: sel ? 0.6 : 0,
              shadowRadius: 8,
              shadowOffset: { width: 0, height: 0 },
              elevation: sel ? 3 : 0,
            }}>
            <Text
              style={{
                color: sel ? '#FFFFFF' : t.sub,
                fontWeight: '700',
                fontSize: 12.5,
              }}>
              {label(m)}
            </Text>
          </PressableScale>
        );
      })}
    </View>
  );
}

function formatDuration(startMin: number, endMin: number): string {
  const diff = endMin >= startMin ? endMin - startMin : 1440 - startMin + endMin;
  if (diff === 0 || diff === 1440) return '24h';
  const h = Math.floor(diff / 60);
  const m = diff % 60;
  if (h === 0) return `${m}m`;
  if (m === 0) return `${h}h`;
  return `${h}h ${m}m`;
}

/** A button showing a 12-hour time that opens Android's system clock picker. */
function TimeButton({ minute, onChange }: { minute: number; onChange: (m: number) => void }) {
  const t = useTheme();
  return (
    <PressableScale
      onPress={() => Native.pickTime(minute).then(onChange).catch(() => {})}
      style={{
        backgroundColor: t.bg,
        borderColor: t.cardBorder,
        borderWidth: 1,
        borderRadius: 12,
        paddingVertical: 10,
        paddingHorizontal: 14,
        minWidth: 110,
        alignItems: 'center',
        flexDirection: 'row',
        gap: 8,
        justifyContent: 'center',
        marginTop: 6,
      }}>
      <Icons.clock size={15} color={t.accent} strokeWidth={2.3} />
      <Text style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 13.5, letterSpacing: -0.2 }}>
        {clock12(minute)}
      </Text>
    </PressableScale>
  );
}

/** One row of the schedule accordion: collapsed shows the summary, expanded shows the editor. */
function ScheduleRow({
  s,
  apps,
  initialOpen,
}: {
  s: Schedule;
  apps: InstalledApp[];
  initialOpen?: boolean;
}) {
  const t = useTheme();
  const p = useProtection();
  const isSaved = (p.state.savedSchedules ?? []).includes(s.id);
  const [open, setOpen] = useState(initialOpen || !isSaved);
  const rotate = useRef(new Animated.Value(initialOpen || !isSaved ? 1 : 0)).current;
  const rotateInterp = rotate.interpolate({ inputRange: [0, 1], outputRange: ['0deg', '180deg'] });
  const [label_, setLabel] = useState(s.label);
  const [start, setStart] = useState(s.startMin);
  const [end, setEnd] = useState(s.endMin);
  const [allowedApps, setAllowedApps] = useState<string[]>(s.allowedApps ?? []);
  const [showAppPicker, setShowAppPicker] = useState(false);
  const [appQuery, setAppQuery] = useState('');

  const delayText = p.delayDays === 1 ? '24 hours' : `${p.delayDays} days`;
  const appsDirty =
    JSON.stringify([...(allowedApps ?? [])].sort()) !== JSON.stringify([...(s.allowedApps ?? [])].sort());
  const dirty = !isSaved || label_ !== s.label || start !== s.startMin || end !== s.endMin || appsDirty;

  const prevScheduleJson = useRef(
    JSON.stringify({
      id: s.id,
      label: s.label,
      start: s.startMin,
      end: s.endMin,
      apps: (s.allowedApps ?? []).slice().sort(),
    })
  );

  useEffect(() => {
    const nextJson = JSON.stringify({
      id: s.id,
      label: s.label,
      start: s.startMin,
      end: s.endMin,
      apps: (s.allowedApps ?? []).slice().sort(),
    });
    if (nextJson !== prevScheduleJson.current) {
      prevScheduleJson.current = nextJson;
      if (!dirty) {
        setLabel(s.label);
        setStart(s.startMin);
        setEnd(s.endMin);
        setAllowedApps(s.allowedApps ?? []);
      }
    }
  }, [s.id, s.label, s.startMin, s.endMin, JSON.stringify(s.allowedApps ?? []), dirty]);

  const guard = (fn: () => Promise<unknown>) => fn().catch(e => showAlert('Blocker', errMsg(e)));

  const patch: SchedulePatch = {
    label: label_.trim() || 'Schedule',
    startMin: start,
    endMin: end,
    enabled: s.enabled,
    allowedApps,
  };

  const save = () =>
    guard(async () => {
      const wasSaved = isSaved;
      const r = await updateSchedule(s.id, patch);
      if (r !== 'applied') {
        showAlert(
          'Change requested',
          `That change applies only after ${delayText} and confirming, since protection is active.`
        );
      } else if (!wasSaved) {
        showAlert('Schedule saved', `"${patch.label}" is saved and active. Future changes will be protected.`);
      } else {
        showAlert('Schedule saved', `"${patch.label}" has been updated.`);
      }
    });

  const toggleEnabled = (enabled: boolean) => {
    guard(async () => {
      const r = await updateSchedule(s.id, { ...patch, enabled });
      if (r !== 'applied') {
        showAlert('Change requested', `Turning this off applies only after ${delayText} and confirming.`);
      }
    });
  };

  const remove = () =>
    showAlert('Delete schedule?', `Remove "${s.label}"?`, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: () =>
          guard(async () => {
            const r = await deleteSchedule(s.id);
            if (r !== 'applied') {
              showAlert('Change requested', `Deleting this applies only after ${delayText} and confirming.`);
            }
          }),
      },
    ]);

  const toggleOpen = () => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    const next = !open;
    setOpen(next);
    Animated.timing(rotate, { toValue: next ? 1 : 0, duration: 200, useNativeDriver: true }).start();
  };

  const filteredApps = useMemo(() => {
    const q = appQuery.trim().toLowerCase();
    const matched = apps.filter(
      a => !q || a.label.toLowerCase().includes(q) || a.packageName.toLowerCase().includes(q)
    );
    return matched
      .sort((a, b) => {
        const aAllowed = allowedApps.includes(a.packageName) ? 1 : 0;
        const bAllowed = allowedApps.includes(b.packageName) ? 1 : 0;
        if (aAllowed !== bAllowed) return bAllowed - aAllowed;
        return a.label.localeCompare(b.label);
      })
      .slice(0, 80);
  }, [apps, appQuery, allowedApps]);

  const toggleApp = (pkg: string) => {
    setAllowedApps(prev => (prev.includes(pkg) ? prev.filter(p => p !== pkg) : [...prev, pkg]));
  };

  return (
    <View style={{ borderTopWidth: 1, borderTopColor: t.cardBorder, paddingVertical: 12 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
        <Pressable
          onPress={toggleOpen}
          style={({ pressed }) => [{
            flex: 1,
            flexDirection: 'row',
            alignItems: 'center',
            gap: 10,
            opacity: pressed ? 0.7 : 1,
          }]}>
          <View style={{ flex: 1 }}>
            <Text style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 14 }} numberOfLines={1}>
              {label_ || s.label}
            </Text>
            <Text style={{ color: t.sub, fontSize: 12.5, marginTop: 2 }}>
              {clock12(start)} – {clock12(end)} ({formatDuration(start, end)})
              {allowedApps.length > 0 ? ` • ${allowedApps.length} apps allowed` : ''}
            </Text>
          </View>
          <Animated.View style={{ transform: [{ rotate: rotateInterp }] }}>
            <ChevronDown size={18} color={t.sub} />
          </Animated.View>
          {!isSaved && <StatusPillBadge label="New" tone="warn" size="sm" />}
          {s.activeNow && <StatusPillBadge label="Active" tone="ok" size="sm" breathing />}
        </Pressable>
        <CyberSwitch value={s.enabled} onValueChange={toggleEnabled} />
      </View>

      {open && (
        <View style={{ marginTop: 14, gap: 12 }}>
          <TextInput
            value={label_}
            onChangeText={setLabel}
            placeholder="Schedule name"
            placeholderTextColor={t.sub}
            style={{
              backgroundColor: t.bg,
              color: '#FFFFFF',
              borderColor: t.cardBorder,
              borderWidth: 1,
              borderRadius: 12,
              paddingHorizontal: 14,
              paddingVertical: 10,
              fontSize: 13.5,
            }}
          />
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <View>
              <Sub>Starts</Sub>
              <TimeButton minute={start} onChange={setStart} />
            </View>
            <View style={{ alignItems: 'center', justifyContent: 'center', paddingHorizontal: 6 }}>
              <Text style={{ color: '#10B981', fontSize: 12.5, fontWeight: '700', marginBottom: 2 }}>
                {formatDuration(start, end)}
              </Text>
              <Text style={{ color: t.sub, fontSize: 10.5, letterSpacing: 0.2 }}>duration</Text>
            </View>
            <View>
              <Sub>Ends</Sub>
              <TimeButton minute={end} onChange={setEnd} />
            </View>
          </View>

          {/* Per-schedule unblocked apps customization */}
          <View
            style={{
              marginTop: 6,
              padding: 14,
              backgroundColor: t.bg,
              borderRadius: 14,
              borderWidth: 1,
              borderColor: t.cardBorder,
            }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
              <View style={{ flex: 1, paddingRight: 8 }}>
                <Text style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 13 }}>Allowed Apps</Text>
                <Text style={{ color: t.sub, fontSize: 11.5, marginTop: 2 }}>
                  {allowedApps.length === 0
                    ? 'Default essential apps allowed'
                    : `${allowedApps.length} custom app${allowedApps.length === 1 ? '' : 's'} unblocked`}
                </Text>
              </View>
              <Btn
                label={showAppPicker ? 'Done' : 'Customize'}
                kind={showAppPicker ? 'primary' : 'ghost'}
                onPress={() => setShowAppPicker(!showAppPicker)}
              />
            </View>

            {showAppPicker && (
              <View style={{ marginTop: 12 }}>
                <TextInput
                  value={appQuery}
                  onChangeText={setAppQuery}
                  placeholder="Search apps to allow..."
                  placeholderTextColor={t.sub}
                  style={{
                    backgroundColor: t.card,
                    color: '#FFFFFF',
                    borderColor: t.cardBorder,
                    borderWidth: 1,
                    borderRadius: 10,
                    paddingHorizontal: 12,
                    paddingVertical: 8,
                    fontSize: 13,
                    marginBottom: 10,
                  }}
                />
                <ScrollView style={{ maxHeight: 200 }} nestedScrollEnabled>
                  {apps.length === 0 && (
                    <Text style={{ color: t.sub, fontSize: 12, paddingVertical: 8 }}>Loading installed apps...</Text>
                  )}
                  {apps.length > 0 && filteredApps.length === 0 && (
                    <Text style={{ color: t.sub, fontSize: 12, paddingVertical: 8 }}>No matching apps found.</Text>
                  )}
                  {filteredApps.map(a => {
                    const isAllowed = allowedApps.includes(a.packageName);
                    return (
                      <Pressable
                        key={a.packageName}
                        onPress={() => toggleApp(a.packageName)}
                        style={({ pressed }) => [{
                          flexDirection: 'row',
                          alignItems: 'center',
                          paddingVertical: 8,
                          gap: 10,
                          borderBottomWidth: 1,
                          borderBottomColor: t.cardBorder,
                          opacity: pressed ? 0.7 : 1,
                        }]}>
                        <AppIcon pkg={a.packageName} />
                        <Text style={{ color: '#FFFFFF', flex: 1, fontSize: 13 }} numberOfLines={1}>
                          {a.label}
                        </Text>
                        <View pointerEvents="none">
                          <Switch
                            value={isAllowed}
                            trackColor={{ true: t.accent, false: t.border }}
                            thumbColor="#FFFFFF"
                          />
                        </View>
                      </Pressable>
                    );
                  })}
                </ScrollView>
              </View>
            )}
          </View>

          <View style={{ flexDirection: 'row', gap: 10, marginTop: 4 }}>
            {dirty && (
              <View style={{ flex: 1 }}>
                <Btn label={isSaved ? 'Save' : 'Save schedule'} kind="primary" icon={Icons.clock} onPress={save} />
              </View>
            )}
            <View style={dirty ? undefined : { flex: 1 }}>
              <Btn label="Delete" kind="danger" icon={Trash2} onPress={remove} />
            </View>
          </View>
        </View>
      )}
    </View>
  );
}

function SchedulesCard({
  apps,
  onAddNew,
  newestId,
}: {
  apps: InstalledApp[];
  onAddNew: () => void;
  newestId: string | null;
}) {
  const t = useTheme();
  const p = useProtection();
  const delayText = p.delayDays === 1 ? '24 hours' : `${p.delayDays} days`;

  return (
    <GlowCard style={{ padding: 18, paddingBottom: p.schedules.length ? 6 : 18 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <View style={{ flex: 1, paddingRight: 12 }}>
          <Title icon={Icons.clock}>Daily schedules</Title>
          <Sub>Focus mode turns on by itself during each active schedule below.</Sub>
        </View>
        <Btn label="Add" icon={Icons.plus} kind="ghost" onPress={onAddNew} />
      </View>
      {p.schedules.length === 0 && (
        <Text style={{ color: t.sub, fontSize: 13, marginTop: 12 }}>
          No schedules yet — tap Add, or the + button, to create one.
        </Text>
      )}
      <View style={{ marginTop: 8 }}>
        {p.schedules.map(s => (
          <ScheduleRow key={s.id} s={s} apps={apps} initialOpen={s.id === newestId} />
        ))}
      </View>
      <Text style={{ color: t.sub, fontSize: 11.5, marginTop: 10, lineHeight: 16 }}>
        First-time schedule setup is saved immediately. Once saved, editing, shrinking or turning one off waits for {delayText}.
      </Text>
      <LockBar lockKey="schedule" />
    </GlowCard>
  );
}

function EssentialApps({ apps }: { apps: InstalledApp[] }) {
  const t = useTheme();
  const p = useProtection();
  const [q, setQ] = useState('');
  const [showSystem, setShowSystem] = useState(false);
  const sel = p.focus.apps;
  const pendingAdd = useMemo(
    () => new Set((p.state.queue.focus ?? []).filter(a => a.t === 'focus_add_app').map(a => a.v)),
    [p.state.queue]
  );
  const byPkg = useMemo(() => new Map(apps.map(a => [a.packageName, a])), [apps]);

  const rows = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const chosen = sel.map(
      pkg =>
        byPkg.get(pkg) ?? {
          packageName: pkg,
          label: `${pkg} (not installed)`,
          isSystem: false,
          category: 'Other',
          blocked: false,
          scanExempt: false,
        }
    );
    const others = apps.filter(
      a =>
        !sel.includes(a.packageName) &&
        (showSystem || !a.isSystem) &&
        (!needle || a.label.toLowerCase().includes(needle))
    );
    return [...chosen, ...others].slice(0, 80);
  }, [apps, sel, q, showSystem, byPkg]);

  const toggle = async (a: InstalledApp, on: boolean) => {
    try {
      if (!on) return await removeFocusApp(a.packageName);
      const r = await addFocusApp(a.packageName, a.label);
      if (r !== 'applied') {
        showAlert(
          'Change requested',
          `${a.label} is allowed during focus only after the ${
            p.delayDays === 1 ? '24-hour' : `${p.delayDays}-day`
          } timer completes.`
        );
      }
    } catch (e) {
      showAlert('Blocker', errMsg(e));
    }
  };

  return (
    <GlowCard style={{ padding: 18 }}>
      <Title icon={Icons.apps}>Essential apps ({sel.length}/{MAX_FOCUS_APPS})</Title>
      <Sub>Only these apps (plus calls and your keyboard) work during focus mode.</Sub>
      <TextInput
        value={q}
        onChangeText={setQ}
        placeholder="Search apps"
        placeholderTextColor={t.sub}
        style={{
          marginTop: 12,
          backgroundColor: t.bg,
          color: '#FFFFFF',
          borderColor: t.cardBorder,
          borderWidth: 1,
          borderRadius: 12,
          paddingHorizontal: 14,
          paddingVertical: 10,
          fontSize: 13.5,
        }}
      />
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 12 }}>
        <Text style={{ color: t.sub, fontSize: 13 }}>Show system apps</Text>
        <CyberSwitch value={showSystem} onValueChange={setShowSystem} />
      </View>
      <View style={{ marginTop: 8 }}>
        {rows.map(a => {
          const on = sel.includes(a.packageName) || pendingAdd.has(a.packageName);
          return (
            <View
              key={a.packageName}
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                paddingVertical: 8,
                gap: 12,
                borderBottomWidth: 1,
                borderBottomColor: t.cardBorder,
              }}>
              <AppIcon pkg={a.packageName} />
              <Text style={{ color: '#FFFFFF', flex: 1, fontWeight: '600', fontSize: 13.5 }} numberOfLines={1}>
                {a.label}
              </Text>
              {pendingAdd.has(a.packageName) && <StatusPillBadge label="Pending" tone="warn" size="sm" />}
              <CyberSwitch value={on} onValueChange={v => toggle(a as InstalledApp, v)} />
            </View>
          );
        })}
      </View>
    </GlowCard>
  );
}

function GranularFocusCard() {
  const p = useProtection();
  const gf = p.granularFocus || {
    block_fb_reels: false,
    block_insta_reels: false,
    block_insta_search: false,
    block_yt_shorts: false,
  };

  const onToggle = async (
    key: 'block_fb_reels' | 'block_insta_reels' | 'block_insta_search' | 'block_yt_shorts',
    on: boolean
  ) => {
    try {
      const r = await setGranularFocusToggle(key, on);
      if (r !== 'applied') {
        const delayText = p.delayDays === 1 ? '24-hour' : `${p.delayDays}-day`;
        showAlert(
          'Change requested',
          `This shield will be disabled only after the ${delayText} timer completes and you confirm.`
        );
      }
    } catch (e) {
      showAlert('Blocker', errMsg(e));
    }
  };

  return (
    <GlowCard style={{ padding: 18 }}>
      <Title icon={Icons.shieldOn}>Distraction Shields</Title>
      <Sub>Exit short-form video feeds & adult content while keeping parent apps usable.</Sub>
      <View style={{ marginTop: 12 }}>
        <AnimatedToggleRow
          icon={Icons.play}
          label="Block YouTube Shorts"
          sub="Exit Shorts back to standard feed"
          value={gf.block_yt_shorts}
          onValueChange={v => onToggle('block_yt_shorts', v)}
        />
        <AnimatedToggleRow
          icon={Icons.film}
          label="Block Instagram Reels"
          sub="Exit Reels back to main feed"
          value={gf.block_insta_reels}
          onValueChange={v => onToggle('block_insta_reels', v)}
        />
        <AnimatedToggleRow
          icon={Icons.search}
          label="Block Instagram Explore"
          sub="Exit Explore & search feed"
          value={gf.block_insta_search}
          onValueChange={v => onToggle('block_insta_search', v)}
        />
        <AnimatedToggleRow
          icon={Icons.apps}
          label="Block Facebook Reels & Videos"
          sub="Exit Reels, Watch & video posts (Stories remain accessible)"
          value={gf.block_fb_reels}
          onValueChange={v => onToggle('block_fb_reels', v)}
        />
        <AnimatedToggleRow
          icon={Icons.shield}
          label="Reddit Mature Content Lockdown"
          sub="Permanently enforces 'Show mature content' OFF"
          value={true}
          badge="Enforced"
        />
        <LockBar lockKey="granular_focus" />
      </View>
    </GlowCard>
  );
}

export default function FocusScreen() {
  const t = useTheme();
  const p = useProtection();
  const [subPage, setSubPage] = useState<'focus' | 'study'>('focus');
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [minutes, setMinutes] = useState(60);
  const [custom, setCustom] = useState('');

  useEffect(() => {
    Native.getInstalledApps(true).then(setApps).catch(() => {});
  }, []);

  const active = p.focus.active;
  const studyActive = p.study?.active;
  const effectivePage = studyActive ? 'study' : active ? 'focus' : subPage;
  const now = useNow(active ? 500 : 0);
  const [newestId, setNewestId] = useState<string | null>(null);

  const addNewSchedule = () => {
    const start = nextRoundedHour();
    const end = (start + 60) % 1440;
    addSchedule(`Schedule ${p.schedules.length + 1}`, start, end, true, [])
      .then(id => setNewestId(id))
      .catch(e => showAlert('Blocker', errMsg(e)));
  };

  useEffect(() => {
    if (!active) return;
    const i = setInterval(() => refresh().catch(() => {}), 2000);
    return () => clearInterval(i);
  }, [active]);

  const activeSchedule = p.schedules.find(s => s.activeNow);
  const timerRunning = p.focus.until > Date.now();

  const targetEnd = useMemo(() => {
    if (p.focus.until > Date.now()) {
      return p.focus.until;
    }
    if (activeSchedule) {
      const d = new Date();
      const curSecOfDay = d.getHours() * 3600 + d.getMinutes() * 60 + d.getSeconds();
      const endSec = activeSchedule.endMin * 60;
      let secLeft = endSec - curSecOfDay;
      if (secLeft <= 0) secLeft += 86400;
      return Date.now() + secLeft * 1000 - d.getMilliseconds();
    }
    return p.focus.remainingMs > 0 ? Date.now() + p.focus.remainingMs : 0;
  }, [p.focus.until, activeSchedule?.id, activeSchedule?.endMin, active]);

  const liveRemaining = targetEnd > 0 ? Math.max(0, targetEnd - now) : Math.max(0, p.focus.remainingMs);
  const prevRemainingRef = useRef<number>(liveRemaining);
  useEffect(() => {
    if (prevRemainingRef.current > 0 && liveRemaining === 0 && active) {
      refresh().catch(() => {});
    }
    prevRemainingRef.current = liveRemaining;
  }, [liveRemaining, active]);

  const byPkg = useMemo(() => new Map(apps.map(a => [a.packageName, a])), [apps]);
  const delayText = p.delayDays === 1 ? '24 hours' : `${p.delayDays} days`;
  const guard = (fn: () => Promise<unknown>) => fn().catch(e => showAlert('Blocker', errMsg(e)));
  const chosenMinutes = custom.trim() ? Math.max(1, Math.min(10080, parseInt(custom, 10) || 0)) : minutes;

  const endNow = () =>
    guard(async () => {
      if (timerRunning) {
        await endFocusEarly();
      } else if (activeSchedule) {
        const r = await updateSchedule(activeSchedule.id, {
          label: activeSchedule.label,
          startMin: activeSchedule.startMin,
          endMin: activeSchedule.endMin,
          enabled: false,
        });
        if (r === 'applied') return;
      }
      showAlert('Change requested', `Focus ends early only after the ${delayText} timer completes and you confirm.`);
    });

  const activeFocusApps = useMemo(() => {
    const set = new Set(p.focus.apps);
    if (activeSchedule?.allowedApps) {
      activeSchedule.allowedApps.forEach(pkg => set.add(pkg));
    }
    return Array.from(set);
  }, [p.focus.apps, activeSchedule]);

  return (
    <View style={{ flex: 1 }}>
      {/* Top Segmented Page Switcher */}
      <View
        style={{
          flexDirection: 'row',
          backgroundColor: '#0E1322',
          borderRadius: 16,
          padding: 4,
          marginHorizontal: 16,
          marginTop: 8,
          marginBottom: 6,
          borderWidth: 1,
          borderColor: '#1E2640',
        }}>
        <PressableScale
          onPress={() => !studyActive && setSubPage('focus')}
          style={{ flex: 1 }}>
          <View
            style={{
              paddingVertical: 10,
              borderRadius: 12,
              alignItems: 'center',
              justifyContent: 'center',
              flexDirection: 'row',
              gap: 8,
              backgroundColor: effectivePage === 'focus' ? t.accent : 'transparent',
              opacity: studyActive && effectivePage !== 'focus' ? 0.35 : 1,
              shadowColor: effectivePage === 'focus' ? t.accent : 'transparent',
              shadowOpacity: effectivePage === 'focus' ? 0.6 : 0,
              shadowRadius: 8,
              shadowOffset: { width: 0, height: 0 },
              elevation: effectivePage === 'focus' ? 3 : 0,
            }}>
            <Icons.focus size={16} color={effectivePage === 'focus' ? '#FFF' : t.sub} strokeWidth={2.4} />
            <Text
              style={{
                color: effectivePage === 'focus' ? '#FFF' : t.sub,
                fontWeight: '700',
                fontSize: 13,
                letterSpacing: -0.2,
              }}>
              Focus Mode
            </Text>
          </View>
        </PressableScale>

        <PressableScale
          onPress={() => !active && setSubPage('study')}
          style={{ flex: 1 }}>
          <View
            style={{
              paddingVertical: 10,
              borderRadius: 12,
              alignItems: 'center',
              justifyContent: 'center',
              flexDirection: 'row',
              gap: 8,
              backgroundColor: effectivePage === 'study' ? t.accent : 'transparent',
              opacity: active && effectivePage !== 'study' ? 0.35 : 1,
              shadowColor: effectivePage === 'study' ? t.accent : 'transparent',
              shadowOpacity: effectivePage === 'study' ? 0.6 : 0,
              shadowRadius: 8,
              shadowOffset: { width: 0, height: 0 },
              elevation: effectivePage === 'study' ? 3 : 0,
            }}>
            <BookOpen size={16} color={effectivePage === 'study' ? '#FFF' : t.sub} strokeWidth={2.4} />
            <Text
              style={{
                color: effectivePage === 'study' ? '#FFF' : t.sub,
                fontWeight: '700',
                fontSize: 13,
                letterSpacing: -0.2,
              }}>
              Study Mode
            </Text>
          </View>
        </PressableScale>
      </View>

      {effectivePage === 'study' ? (
        <StudyScreen />
      ) : active ? (
        <ScrollView contentContainerStyle={{ padding: 16, paddingTop: 10, paddingBottom: 28 }} showsVerticalScrollIndicator={false}>
          <GlowCard active tone="ok" style={{ padding: 20 }}>
            <Title size={21} icon={Icons.focus}>Focus mode active</Title>
            <Text
              style={{
                color: '#FFFFFF',
                fontSize: 42,
                fontWeight: '800',
                marginVertical: 10,
                fontVariant: ['tabular-nums'],
                letterSpacing: -1,
              }}>
              {fmt(liveRemaining)}
            </Text>
            <Sub>
              {timerRunning
                ? 'Running from your timer'
                : `Running from "${activeSchedule?.label ?? 'a schedule'}" (${activeSchedule ? formatDuration(activeSchedule.startMin, activeSchedule.endMin) : ''})`}.
              Settings and all non-essential apps are locked until completion.
            </Sub>
            <LockBar lockKey="focus" />
          </GlowCard>

          <GlowCard style={{ padding: 18 }}>
            <Title icon={Icons.play}>Open an essential app</Title>
            <View style={{ marginTop: 8 }}>
              {activeFocusApps.map(pkg => {
                const a = byPkg.get(pkg);
                return (
                  <View
                    key={pkg}
                    style={{
                      flexDirection: 'row',
                      alignItems: 'center',
                      paddingVertical: 9,
                      gap: 12,
                      borderBottomWidth: 1,
                      borderBottomColor: t.cardBorder,
                    }}>
                    <AppIcon pkg={pkg} />
                    <Text style={{ color: '#FFFFFF', flex: 1, fontWeight: '600', fontSize: 13.5 }} numberOfLines={1}>
                      {a?.label ?? pkg}
                    </Text>
                    <Btn label="Open" disabled={!a} kind="ghost" onPress={() => guard(() => Native.launchApp(pkg))} />
                  </View>
                );
              })}
            </View>
          </GlowCard>

          {timerRunning && (
            <GlowCard style={{ padding: 18 }}>
              <Title icon={Icons.clock}>Change the timer</Title>
              <Sub>Making it longer applies immediately. Shortening waits for the {delayText} timer.</Sub>
              <Chips
                values={PRESETS}
                onPick={m =>
                  guard(async () => {
                    const r = await setFocusRemaining(m);
                    if (r !== 'applied') {
                      showAlert(
                        'Change requested',
                        `The shorter timer applies only after ${delayText}, if focus is still running.`
                      );
                    }
                  })
                }
              />
            </GlowCard>
          )}

          <GlowCard style={{ padding: 18 }}>
            <Btn label="End focus now" kind="danger" icon={Icons.unlock} onPress={endNow} />
          </GlowCard>

          <SchedulesCard apps={apps} onAddNew={addNewSchedule} newestId={newestId} />
          <GranularFocusCard />
          <EssentialApps apps={apps} />
        </ScrollView>
      ) : (
        <View style={{ flex: 1 }}>
          <ScrollView
            contentContainerStyle={{ padding: 16, paddingTop: 10, paddingBottom: 90 }}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}>
            <GlowCard style={{ padding: 18 }}>
              <Title size={21} icon={Icons.focus}>Focus mode</Title>
              <Sub>Locks all settings and every app except your essential apps for the time you choose.</Sub>
              <Chips
                values={PRESETS}
                selected={custom.trim() ? undefined : minutes}
                onPick={m => {
                  setCustom('');
                  setMinutes(m);
                }}
              />
              <TextInput
                value={custom}
                onChangeText={setCustom}
                keyboardType="number-pad"
                placeholder="Or custom minutes (1 to 10080)"
                placeholderTextColor={t.sub}
                style={{
                  marginTop: 14,
                  backgroundColor: t.bg,
                  color: '#FFFFFF',
                  borderColor: t.cardBorder,
                  borderWidth: 1,
                  borderRadius: 12,
                  paddingHorizontal: 14,
                  paddingVertical: 10,
                  fontSize: 13.5,
                }}
              />
              <View style={{ height: 14 }} />
              <Btn
                label={`Start focus for ${label(chosenMinutes)}`}
                icon={Icons.play}
                kind="primary"
                onPress={() =>
                  showAlert(
                    'Start focus mode?',
                    `Everything except your ${p.focus.apps.length} essential apps will be locked for ${label(
                      chosenMinutes
                    )}. Ending it early takes ${delayText}.`,
                    [
                      { text: 'Cancel', style: 'cancel' },
                      { text: 'Start', style: 'destructive', onPress: () => guard(() => startFocus(chosenMinutes)) },
                    ]
                  )
                }
              />
            </GlowCard>

            <SchedulesCard apps={apps} onAddNew={addNewSchedule} newestId={newestId} />
            <GranularFocusCard />
            <EssentialApps apps={apps} />
          </ScrollView>
          <Fab onPress={addNewSchedule} />
        </View>
      )}
    </View>
  );
}
