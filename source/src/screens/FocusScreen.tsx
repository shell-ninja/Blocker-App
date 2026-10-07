import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Animated, LayoutAnimation, Pressable, ScrollView, Switch, Text, TextInput, View } from 'react-native';
import { ChevronDown, Trash2 } from 'lucide-react-native';
import { InstalledApp, Native, Schedule } from '../native/BlockerNative';
import { useNow, useProtection } from '../hooks/useProtection';
import {
  MAX_FOCUS_APPS, SchedulePatch, addFocusApp, addSchedule, deleteSchedule, endFocusEarly, refresh,
  removeFocusApp, setFocusRemaining, setGranularFocusToggle, startFocus, updateSchedule
} from '../services/ProtectionManager';
import { AppIcon } from './AppBlockerScreen';
import { AnimatedToggleRow, Badge, Btn, Card, Fab, Icons, LockBar, Sub, Title, errMsg, fmt, showAlert, useTheme } from '../ui';

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

function Chips({ values, selected, onPick }: { values: number[]; selected?: number; onPick: (m: number) => void }) {
  const t = useTheme();
  return (
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 10 }}>
      {values.map(m => {
        const sel = m === selected;
        return (
          <Pressable
            key={m}
            onPress={() => onPick(m)}
            style={{
              paddingVertical: 8, paddingHorizontal: 14, borderRadius: 999, borderWidth: 1,
              borderColor: sel ? t.accent : t.border, backgroundColor: sel ? t.accentSoft : 'transparent',
              shadowColor: sel ? t.accent : 'transparent', shadowOpacity: sel ? 0.85 : 0,
              shadowRadius: sel ? 8 : 0, shadowOffset: { width: 0, height: 0 }, elevation: sel ? 4 : 0
            }}>
            <Text style={{
              color: sel ? t.accent : t.text, fontWeight: '700',
              textShadowColor: sel ? t.accentSoft : 'transparent', textShadowOffset: { width: 0, height: 0 }, textShadowRadius: sel ? 6 : 0
            }}>{label(m)}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

/** A button showing a 12-hour time that opens Android's own clock-dial picker when tapped. */
function TimeButton({ minute, onChange }: { minute: number; onChange: (m: number) => void }) {
  const t = useTheme();
  return (
    <Pressable
      onPress={() => Native.pickTime(minute).then(onChange).catch(() => {})}
      style={{
        backgroundColor: t.card, borderColor: t.cardBorder, borderWidth: 1, borderRadius: 12,
        paddingVertical: 10, paddingHorizontal: 16, minWidth: 96, alignItems: 'center'
      }}>
      <Text style={{ color: t.text, fontWeight: '700', fontSize: 15 }}>{clock12(minute)}</Text>
    </Pressable>
  );
}

/** One row of the accordion: collapsed shows the summary, expanded shows the editor. */
function ScheduleRow({ s, initialOpen }: { s: Schedule; initialOpen?: boolean }) {
  const t = useTheme();
  const p = useProtection();
  const isSaved = (p.state.savedSchedules ?? []).includes(s.id);
  const [open, setOpen] = useState(initialOpen || !isSaved);
  const rotate = useRef(new Animated.Value(initialOpen || !isSaved ? 1 : 0)).current;
  const rotateInterp = rotate.interpolate({ inputRange: [0, 1], outputRange: ['0deg', '180deg'] });
  const [label_, setLabel] = useState(s.label);
  const [start, setStart] = useState(s.startMin);
  const [end, setEnd] = useState(s.endMin);
  useEffect(() => {
    setLabel(s.label);
    setStart(s.startMin);
    setEnd(s.endMin);
  }, [s.label, s.startMin, s.endMin]);

  const delayText = p.delayDays === 1 ? '24 hours' : `${p.delayDays} days`;
  const dirty = !isSaved || label_ !== s.label || start !== s.startMin || end !== s.endMin;
  const guard = (fn: () => Promise<unknown>) => fn().catch(e => showAlert('Blocker', errMsg(e)));

  const patch: SchedulePatch = { label: label_.trim() || 'Schedule', startMin: start, endMin: end, enabled: s.enabled };

  const save = () => guard(async () => {
    const wasSaved = isSaved;
    const r = await updateSchedule(s.id, patch);
    if (r !== 'applied') {
      showAlert('Change requested', `That change applies only after ${delayText} and confirming, since the schedule is currently active.`);
    } else if (!wasSaved) {
      showAlert('Schedule saved', `"${patch.label}" is saved and active. Future changes will be protected.`);
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

  const remove = () => showAlert('Delete schedule?', `Remove "${s.label}"?`, [
    { text: 'Cancel', style: 'cancel' },
    {
      text: 'Delete', style: 'destructive', onPress: () => guard(async () => {
        const r = await deleteSchedule(s.id);
        if (r !== 'applied') {
          showAlert('Change requested', `Deleting this applies only after ${delayText} and confirming.`);
        }
      })
    }
  ]);

  const toggleOpen = () => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    const next = !open;
    setOpen(next);
    Animated.timing(rotate, { toValue: next ? 1 : 0, duration: 200, useNativeDriver: true }).start();
  };

  return (
    <View style={{ borderTopWidth: 1, borderTopColor: t.cardBorder, paddingVertical: 10 }}>
      <Pressable onPress={toggleOpen} style={({ pressed }) => [{ flexDirection: 'row', alignItems: 'center', gap: 10, opacity: pressed ? 0.7 : 1 }]}>
        <View style={{ flex: 1 }}>
          <Text style={{ color: t.text, fontWeight: '700' }} numberOfLines={1}>{s.label}</Text>
          <Text style={{ color: t.sub, fontSize: 13, marginTop: 1 }}>{clock12(s.startMin)} – {clock12(s.endMin)}</Text>
        </View>
        <Animated.View style={{ transform: [{ rotate: rotateInterp }] }}>
          <ChevronDown size={18} color={t.sub} />
        </Animated.View>
        {!isSaved && <Badge label="New" tone="warn" />}
        {s.activeNow && <Badge label="Active" tone="ok" />}
        <Switch value={s.enabled} onValueChange={toggleEnabled} trackColor={{ true: t.accent }} />
      </Pressable>

      {open && (
        <View style={{ marginTop: 12, gap: 12 }}>
          <TextInput
            value={label_}
            onChangeText={setLabel}
            placeholder="Schedule name"
            placeholderTextColor={t.sub}
            style={{
              backgroundColor: t.card, color: t.text, borderColor: t.cardBorder, borderWidth: 1, borderRadius: 12,
              paddingHorizontal: 14, paddingVertical: 10
            }}
          />
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <View>
              <Sub>Starts</Sub>
              <TimeButton minute={start} onChange={setStart} />
            </View>
            <Icons.clock size={16} color={t.sub} />
            <View>
              <Sub>Ends</Sub>
              <TimeButton minute={end} onChange={setEnd} />
            </View>
          </View>
          <View style={{ flexDirection: 'row', gap: 8 }}>
            {dirty && <View style={{ flex: 1 }}><Btn label={isSaved ? "Save" : "Save schedule"} icon={Icons.clock} onPress={save} /></View>}
            <View style={dirty ? undefined : { flex: 1 }}>
              <Btn label="Delete" kind="danger" icon={Trash2} onPress={remove} />
            </View>
          </View>
        </View>
      )}
    </View>
  );
}

function SchedulesCard() {
  const t = useTheme();
  const p = useProtection();
  const delayText = p.delayDays === 1 ? '24 hours' : `${p.delayDays} days`;
  const [newestId, setNewestId] = useState<string | null>(null);

  const addNew = () => {
    const start = nextRoundedHour();
    const end = (start + 60) % 1440;
    addSchedule(`Schedule ${p.schedules.length + 1}`, start, end, true)
      .then(id => setNewestId(id))
      .catch(e => showAlert('Blocker', errMsg(e)));
  };

  return (
    <Card style={{ paddingBottom: p.schedules.length ? 4 : 16 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center' }}>
        <View style={{ flex: 1, paddingRight: 12 }}>
          <Title icon={Icons.clock}>Daily schedules</Title>
          <Sub>Focus mode turns on by itself during each active schedule below.</Sub>
        </View>
        <Btn label="Add" icon={Icons.plus} onPress={addNew} />
      </View>
      {p.schedules.length === 0 && <Sub>No schedules yet — tap Add, or the + button, to create one.</Sub>}
      {p.schedules.map(s => <ScheduleRow key={s.id} s={s} initialOpen={s.id === newestId} />)}
      <Sub>First-time schedule setup is saved immediately. Once saved, editing, shrinking or turning one off waits for {delayText}.</Sub>
      <LockBar lockKey="schedule" />
    </Card>
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
    const chosen = sel.map(pkg => byPkg.get(pkg) ?? {
      packageName: pkg, label: `${pkg} (not installed)`, isSystem: false, category: 'Other', blocked: false, scanExempt: false
    });
    const others = apps.filter(a =>
      !sel.includes(a.packageName) && (showSystem || !a.isSystem) &&
      (!needle || a.label.toLowerCase().includes(needle)));
    return [...chosen, ...others].slice(0, 80);
  }, [apps, sel, q, showSystem, byPkg]);

  const toggle = async (a: InstalledApp, on: boolean) => {
    try {
      if (!on) return await removeFocusApp(a.packageName);
      const r = await addFocusApp(a.packageName, a.label);
      if (r !== 'applied') {
        showAlert('Change requested',
          `${a.label} is allowed during focus only after the ${p.delayDays === 1 ? '24-hour' : `${p.delayDays}-day`} timer completes.`);
      }
    } catch (e) {
      showAlert('Blocker', errMsg(e));
    }
  };

  return (
    <Card>
      <Title icon={Icons.apps}>Essential apps ({sel.length}/{MAX_FOCUS_APPS})</Title>
      <Sub>Only these apps (plus calls and your keyboard) work during focus mode.</Sub>
      <TextInput
        value={q}
        onChangeText={setQ}
        placeholder="Search apps"
        placeholderTextColor={t.sub}
        style={{ marginTop: 10, backgroundColor: t.bg, color: t.text, borderColor: t.cardBorder, borderWidth: 1, borderRadius: 12, paddingHorizontal: 14, paddingVertical: 10 }}
      />
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 10 }}>
        <Text style={{ color: t.sub }}>Show system apps</Text>
        <Switch value={showSystem} onValueChange={setShowSystem} trackColor={{ true: t.accent }} />
      </View>
      {rows.map(a => {
        const on = sel.includes(a.packageName) || pendingAdd.has(a.packageName);
        return (
          <View key={a.packageName} style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 8, gap: 12 }}>
            <AppIcon pkg={a.packageName} />
            <Text style={{ color: t.text, flex: 1 }} numberOfLines={1}>{a.label}</Text>
            {pendingAdd.has(a.packageName) && <Badge label="Pending" tone="warn" />}
            <Switch value={on} onValueChange={v => toggle(a as InstalledApp, v)} trackColor={{ true: t.accent }} />
          </View>
        );
      })}
    </Card>
  );
}

function GranularFocusCard() {
  const p = useProtection();
  const gf = p.granularFocus || {
    block_fb_reels: false,
    block_insta_reels: false,
    block_insta_search: false,
    block_yt_shorts: false
  };

  const onToggle = async (key: 'block_fb_reels' | 'block_insta_reels' | 'block_insta_search' | 'block_yt_shorts', on: boolean) => {
    try {
      const r = await setGranularFocusToggle(key, on);
      if (r !== 'applied') {
        const delayText = p.delayDays === 1 ? '24-hour' : `${p.delayDays}-day`;
        showAlert('Change requested',
          `This shield will be disabled only after the ${delayText} timer completes and you confirm.`);
      }
    } catch (e) {
      showAlert('Blocker', errMsg(e));
    }
  };

  return (
    <Card>
      <Title icon={Icons.shieldOn}>Focus Distraction Shields</Title>
      <Sub>Intercept and exit addictive short-form loops without closing allowed parent apps.</Sub>
      <View style={{ marginTop: 10 }}>
        <AnimatedToggleRow
          icon={Icons.play}
          label="Block YouTube Shorts"
          sub="Instantly exit Shorts player feed back to regular YouTube"
          value={gf.block_yt_shorts}
          onValueChange={v => onToggle('block_yt_shorts', v)}
        />
        <AnimatedToggleRow
          icon={Icons.film}
          label="Block Instagram Reels"
          sub="Instantly exit Reels tab & player back to main feed"
          value={gf.block_insta_reels}
          onValueChange={v => onToggle('block_insta_reels', v)}
        />
        <AnimatedToggleRow
          icon={Icons.search}
          label="Block Instagram Explore / Search"
          sub="Instantly exit Search & Explore feed to prevent scrolling"
          value={gf.block_insta_search}
          onValueChange={v => onToggle('block_insta_search', v)}
        />
        <AnimatedToggleRow
          icon={Icons.apps}
          label="Block Facebook Reels"
          sub="Instantly exit Reels viewer back to main Facebook feed"
          value={gf.block_fb_reels}
          onValueChange={v => onToggle('block_fb_reels', v)}
        />
        <LockBar lockKey="granular_focus" />
      </View>
    </Card>
  );
}

export default function FocusScreen() {
  const t = useTheme();
  const p = useProtection();
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [minutes, setMinutes] = useState(60);
  const [custom, setCustom] = useState('');

  useEffect(() => {
    Native.getInstalledApps(true).then(setApps).catch(() => {});
  }, []);

  const active = p.focus.active;
  const now = useNow(1000);
  // While focus is running, poll a bit faster than the app-wide 5s cadence so a schedule
  // starting/ending, or a change made elsewhere, shows up here within about 2 seconds.
  useEffect(() => {
    if (!active) return;
    const i = setInterval(() => refresh().catch(() => {}), 2000);
    return () => clearInterval(i);
  }, [active]);
  // p.focus.remainingMs only refreshes on each poll; derive a fixed end time from it and tick the
  // display every second in between, so the countdown moves smoothly instead of jumping every few
  // seconds, and immediately reflects any change we just made (refresh() runs right after it).
  const [endsAt, setEndsAt] = useState(() => Date.now() + p.focus.remainingMs);
  useEffect(() => {
    setEndsAt(Date.now() + p.focus.remainingMs);
  }, [p.focus.remainingMs]);
  const liveRemaining = Math.max(0, endsAt - now);
  useEffect(() => {
    if (liveRemaining === 0 && active) refresh().catch(() => {});
  }, [liveRemaining, active]);

  const byPkg = useMemo(() => new Map(apps.map(a => [a.packageName, a])), [apps]);
  const delayText = p.delayDays === 1 ? '24 hours' : `${p.delayDays} days`;
  const guard = (fn: () => Promise<unknown>) => fn().catch(e => showAlert('Blocker', errMsg(e)));
  const chosenMinutes = custom.trim() ? Math.max(1, Math.min(10080, parseInt(custom, 10) || 0)) : minutes;
  const timerRunning = p.focus.until > Date.now();
  const activeSchedule = p.schedules.find(s => s.activeNow);

  const endNow = () => guard(async () => {
    if (timerRunning) {
      await endFocusEarly();
    } else if (activeSchedule) {
      const r = await updateSchedule(activeSchedule.id, {
        label: activeSchedule.label, startMin: activeSchedule.startMin, endMin: activeSchedule.endMin, enabled: false
      });
      if (r === 'applied') return;
    }
    showAlert('Change requested', `Focus ends early only after the ${delayText} timer completes and you confirm.`);
  });

  if (active) {
    return (
      <ScrollView contentContainerStyle={{ padding: 16 }}>
        <Card>
          <Title size={22} icon={Icons.focus}>Focus mode</Title>
          <Text style={{ color: t.accent, fontSize: 40, fontWeight: '800', marginVertical: 8, fontVariant: ['tabular-nums'] }}>
            {fmt(liveRemaining)}
          </Text>
          <Sub>
            {timerRunning ? 'Running from your timer' : `Running from "${activeSchedule?.label ?? 'a schedule'}"`}.
            Settings and every app except your essential apps are locked until it ends.
          </Sub>
          <LockBar lockKey="focus" />
        </Card>

        <Card>
          <Title icon={Icons.play}>Open an essential app</Title>
          {p.focus.apps.map(pkg => {
            const a = byPkg.get(pkg);
            return (
              <View key={pkg} style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 8, gap: 12 }}>
                <AppIcon pkg={pkg} />
                <Text style={{ color: t.text, flex: 1 }} numberOfLines={1}>{a?.label ?? pkg}</Text>
                <Btn label="Open" disabled={!a} onPress={() => guard(() => Native.launchApp(pkg))} />
              </View>
            );
          })}
        </Card>

        {timerRunning && (
          <Card>
            <Title icon={Icons.clock}>Change the timer</Title>
            <Sub>Making it longer applies now. Shortening waits for the {delayText} timer.</Sub>
            <Chips
              values={PRESETS}
              onPick={m => guard(async () => {
                const r = await setFocusRemaining(m);
                if (r !== 'applied') {
                  showAlert('Change requested', `The shorter timer applies only after ${delayText}, if focus is still running.`);
                }
              })}
            />
          </Card>
        )}

        <Card>
          <Btn label="End focus now" kind="danger" icon={Icons.unlock} onPress={endNow} />
        </Card>

        <SchedulesCard />
        <GranularFocusCard />
        <EssentialApps apps={apps} />
      </ScrollView>
    );
  }

  return (
    <View style={{ flex: 1 }}>
      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 90 }} keyboardShouldPersistTaps="handled">
        <Card>
          <Title size={22} icon={Icons.focus}>Focus mode</Title>
          <Sub>Locks all settings and every app except your essential apps for the time you choose.</Sub>
          <Chips values={PRESETS} selected={custom.trim() ? undefined : minutes} onPick={m => { setCustom(''); setMinutes(m); }} />
          <TextInput
            value={custom}
            onChangeText={setCustom}
            keyboardType="number-pad"
            placeholder="Or custom minutes (1 to 10080)"
            placeholderTextColor={t.sub}
            style={{ marginTop: 12, backgroundColor: t.card, color: t.text, borderColor: t.cardBorder, borderWidth: 1, borderRadius: 12, paddingHorizontal: 14, paddingVertical: 10 }}
          />
          <View style={{ height: 12 }} />
          <Btn
            label={`Start focus for ${label(chosenMinutes)}`}
            icon={Icons.play}
            onPress={() => showAlert(
              'Start focus mode?',
              `Everything except your ${p.focus.apps.length} essential apps will be locked for ${label(chosenMinutes)}. Ending it early takes ${delayText}.`,
              [
                { text: 'Cancel', style: 'cancel' },
                { text: 'Start', style: 'destructive', onPress: () => guard(() => startFocus(chosenMinutes)) }
              ]
            )}
          />
        </Card>

        <SchedulesCard />
        <GranularFocusCard />
        <EssentialApps apps={apps} />
      </ScrollView>
      <Fab
        onPress={() => {
          const start = nextRoundedHour();
          const end = (start + 60) % 1440;
          addSchedule(`Schedule ${p.schedules.length + 1}`, start, end, true).catch(e => showAlert('Blocker', errMsg(e)));
        }}
      />
    </View>
  );
}
