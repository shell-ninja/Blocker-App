import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Animated,
  BackHandler,
  Easing,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import {
  AlertTriangle,
  BellOff,
  BookOpen,
  CheckCircle2,
  Coffee,
  FastForward,
  Flame,
  Lock,
  Pause,
  Play,
  RotateCcw,
  ShieldAlert,
  Sparkles,
  Wifi,
  WifiOff,
} from 'lucide-react-native';
import { useNow, useProtection } from '../hooks/useProtection';
import {
  endStudyEarly,
  isWifiOn,
  openWifiSettings,
  refresh,
  setWifiEnabled,
  skipStudyBreak,
  startStudy,
} from '../services/ProtectionManager';
import {
  Badge,
  Btn,
  Card,
  Icons,
  Sub,
  Title,
  errMsg,
  showAlert,
  useTheme,
} from '../ui';

const FOCUS_PRESETS = [15, 25, 30, 45, 50, 60];
const BREAK_PRESETS = [3, 5, 10, 15];
const ROUNDS_PRESETS = [1, 2, 3, 4, 6];

const QUOTES = [
  {
    quote:
      'Take advantage of five before five: your youth before your old age, your health before your sickness, your wealth before your poverty, your free time before your busyness, and your life before your death.',
    source: 'Hadith — Al-Hakim',
  },
  {
    quote:
      'And that there is not for man except that [good] for which he strives, and that his effort is going to be seen.',
    source: 'Surah An-Najm (53:39-40)',
  },
  {
    quote:
      'You have power over your mind - not outside events. Realize this, and you will find strength.',
    source: 'Marcus Aurelius',
  },
  {
    quote:
      'The secret of getting ahead is getting started. The secret of getting started is breaking your complex overwhelming tasks into small manageable tasks.',
    source: 'Mark Twain',
  },
  {
    quote:
      'Indeed, with hardship comes ease. So when you have finished, still labor hard.',
    source: 'Surah Ash-Sharh (94:6-7)',
  },
];

function formatStudyTime(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

function ChipSelector<T extends number>({
  values,
  selected,
  onPick,
  formatLabel,
}: {
  values: T[];
  selected: T;
  onPick: (val: T) => void;
  formatLabel: (val: T) => string;
}) {
  const t = useTheme();
  return (
    <View style={styles.chipRow}>
      {values.map(val => {
        const isSelected = val === selected;
        return (
          <Pressable
            key={String(val)}
            onPress={() => onPick(val)}
            style={({ pressed }) => [
              styles.chip,
              {
                borderColor: isSelected ? t.accent : t.border,
                backgroundColor: isSelected ? t.accentSoft : 'transparent',
                transform: [{ scale: pressed ? 0.95 : 1 }],
              },
            ]}>
            <Text
              style={[
                styles.chipText,
                {
                  color: isSelected ? t.accent : t.text,
                  fontWeight: isSelected ? '800' : '600',
                },
              ]}>
              {formatLabel(val)}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export default function StudyScreen() {
  const t = useTheme();
  const p = useProtection();

  // Setup state
  const [focusMinutes, setFocusMinutes] = useState(25);
  const [breakMinutes, setBreakMinutes] = useState(5);
  const [totalRounds, setTotalRounds] = useState(4);
  const [customFocus, setCustomFocus] = useState('');

  // Emergency stop state
  const [showEmergencyModal, setShowEmergencyModal] = useState(false);
  const [emergencyHoldProgress, setEmergencyHoldProgress] = useState(0);
  const holdTimerRef = useRef<NodeJS.Timeout | null>(null);

  // Active state
  const active = p.study?.active;
  const isBreak = p.study?.isBreak;
  const currentRound = p.study?.currentRound || 1;
  const roundsCount = p.study?.totalRounds || totalRounds;

  const now = useNow(active ? 1000 : 0);

  // Poll faster while study mode is active to detect round transitions
  useEffect(() => {
    if (!active) return;
    const interval = setInterval(() => {
      refresh().catch(() => {});
    }, 1500);
    return () => clearInterval(interval);
  }, [active]);

  const endsAt = useMemo(() => {
    return Date.now() + (p.study?.remainingMs || 0);
  }, [p.study?.remainingMs, active]);

  const liveRemaining = Math.max(0, endsAt - now);

  useEffect(() => {
    if (active && liveRemaining === 0) {
      refresh().catch(() => {});
    }
  }, [liveRemaining, active]);

  // Back button protection while active
  useEffect(() => {
    if (!active) return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => true);
    return () => sub.remove();
  }, [active]);

  // Wi-Fi detection
  const [wifiOn, setWifiOn] = useState(false);
  const checkWifi = () => {
    isWifiOn().then(setWifiOn).catch(() => {});
  };
  useEffect(() => {
    checkWifi();
    const interval = setInterval(checkWifi, 2500);
    return () => clearInterval(interval);
  }, []);

  const handleToggleWifi = async () => {
    try {
      const targetState = !wifiOn;
      const res = await setWifiEnabled(targetState);
      setWifiOn(res);
      if (!targetState && res) {
        // If system blocked direct toggle, launch system panel
        openWifiSettings().catch(() => {});
      }
    } catch (_) {
      openWifiSettings().catch(() => {});
    }
  };

  // Quote selector
  const quote = useMemo(() => {
    const idx = (currentRound - 1) % QUOTES.length;
    return QUOTES[idx];
  }, [currentRound]);

  // Animated pulse for active study indicator
  const pulseAnim = useRef(new Animated.Value(1)).current;
  useEffect(() => {
    if (!active) return;
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(pulseAnim, {
          toValue: 1.04,
          duration: 1800,
          easing: Easing.inOut(Easing.ease),
          useNativeDriver: true,
        }),
        Animated.timing(pulseAnim, {
          toValue: 1,
          duration: 1800,
          easing: Easing.inOut(Easing.ease),
          useNativeDriver: true,
        }),
      ])
    );
    loop.start();
    return () => loop.stop();
  }, [active]);

  const chosenFocus = customFocus.trim()
    ? Math.max(5, Math.min(180, parseInt(customFocus, 10) || 25))
    : focusMinutes;

  const handleStartStudy = () => {
    const wifiNotice = wifiOn ? '\n\nNote: Wi-Fi is currently ON. Blocker will attempt to switch it off automatically. For maximum focus, you can also turn it off manually beforehand.' : '';
    showAlert(
      'Activate Strict Study Mode?',
      `Your phone will be completely locked down for ${totalRounds} rounds of ${chosenFocus} min focus${
        totalRounds > 1 ? ` (${breakMinutes}m breaks in between)` : ''
      }.\n\nAll apps, notification shade, internet toggles, and settings will be blocked until the session ends.\n\nYou can turn off the screen at any time; waking the device returns directly to your study timer.${wifiNotice}\n\nReady to commit?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Lock In & Start',
          style: 'destructive',
          onPress: () => {
            startStudy(chosenFocus, breakMinutes, totalRounds)
              .then(() => checkWifi())
              .catch(err => {
                showAlert('Error', errMsg(err));
              });
          },
        },
      ]
    );
  };

  const handleSkipBreak = () => {
    showAlert(
      'Skip Break?',
      'Start the next focus round immediately?',
      [
        { text: 'Keep Resting', style: 'cancel' },
        {
          text: 'Start Focus',
          style: 'default',
          onPress: () => {
            skipStudyBreak().catch(err => showAlert('Error', errMsg(err)));
          },
        },
      ]
    );
  };

  const startHoldEmergency = () => {
    setEmergencyHoldProgress(0);
    const start = Date.now();
    const duration = 4000; // 4 seconds hold
    holdTimerRef.current = setInterval(() => {
      const elapsed = Date.now() - start;
      const prog = Math.min(1, elapsed / duration);
      setEmergencyHoldProgress(prog);
      if (prog >= 1) {
        if (holdTimerRef.current) clearInterval(holdTimerRef.current);
        endStudyEarly()
          .then(() => {
            setShowEmergencyModal(false);
            setEmergencyHoldProgress(0);
            showAlert('Session Ended', 'Study session was ended early.');
          })
          .catch(err => showAlert('Error', errMsg(err)));
      }
    }, 50);
  };

  const stopHoldEmergency = () => {
    if (holdTimerRef.current) {
      clearInterval(holdTimerRef.current);
      holdTimerRef.current = null;
    }
    setEmergencyHoldProgress(0);
  };

  // -------------------------------------------------------------
  // ACTIVE STUDY / BREAK VIEW
  // -------------------------------------------------------------
  if (active) {
    const currentTotalMs =
      (isBreak ? (p.study?.breakMinutes || 5) : (p.study?.focusMinutes || 25)) * 60_000;
    const progressPercent = Math.min(
      100,
      Math.max(0, ((currentTotalMs - liveRemaining) / currentTotalMs) * 100)
    );

    const themeColor = isBreak ? t.ok : t.accent;
    const themeBg = isBreak ? t.okSoft : t.accentSoft;

    return (
      <View style={{ flex: 1 }}>
        <ScrollView
          contentContainerStyle={styles.activeContainer}
          showsVerticalScrollIndicator={false}>
          {/* Header Status Card */}
          <Card>
            <View style={styles.phaseHeader}>
              <View
                style={[
                  styles.phaseIconContainer,
                  { backgroundColor: themeBg, borderColor: themeColor },
                ]}>
                {isBreak ? (
                  <Coffee size={22} color={themeColor} strokeWidth={2.4} />
                ) : (
                  <Flame size={22} color={themeColor} strokeWidth={2.4} />
                )}
              </View>
              <View style={{ flex: 1 }}>
                <Text style={[styles.phaseTitle, { color: themeColor }]}>
                  {isBreak ? 'Short Break' : `Focus Round ${currentRound} of ${roundsCount}`}
                </Text>
                <Text style={[styles.phaseSub, { color: t.sub }]}>
                  {isBreak
                    ? 'Rest your eyes, hydrate, and stretch.'
                    : 'Aggressive phone lockout is active.'}
                </Text>
              </View>
            </View>

            {/* Glowing Main Timer Dial */}
            <Animated.View
              style={[
                styles.timerDial,
                {
                  borderColor: themeColor,
                  backgroundColor: themeBg,
                  transform: [{ scale: pulseAnim }],
                  shadowColor: themeColor,
                },
              ]}>
              <Text style={[styles.timerDigits, { color: t.text }]}>
                {formatStudyTime(liveRemaining)}
              </Text>
              <View style={styles.timerSubBadge}>
                <Lock size={12} color={themeColor} strokeWidth={2.5} />
                <Text style={[styles.timerSubText, { color: themeColor }]}>
                  {isBreak ? 'Break Time' : 'Phone Locked Down'}
                </Text>
              </View>
            </Animated.View>

            {/* Progress Bar */}
            <View style={styles.progressContainer}>
              <View style={[styles.progressTrack, { backgroundColor: t.glass }]}>
                <View
                  style={[
                    styles.progressBar,
                    { width: `${progressPercent}%`, backgroundColor: themeColor },
                  ]}
                />
              </View>
              <Text style={[styles.progressLabel, { color: t.sub }]}>
                {Math.round(progressPercent)}% elapsed
              </Text>
            </View>

            {/* Rounds Indicators */}
            <View style={styles.roundsTracker}>
              {Array.from({ length: roundsCount }).map((_, index) => {
                const roundNum = index + 1;
                const isCompleted = roundNum < currentRound;
                const isCurrent = roundNum === currentRound;

                return (
                  <View
                    key={roundNum}
                    style={[
                      styles.roundPill,
                      {
                        backgroundColor: isCompleted
                          ? t.okSoft
                          : isCurrent
                          ? themeBg
                          : 'transparent',
                        borderColor: isCompleted
                          ? t.ok
                          : isCurrent
                          ? themeColor
                          : t.border,
                      },
                    ]}>
                    {isCompleted ? (
                      <CheckCircle2 size={13} color={t.ok} strokeWidth={2.5} />
                    ) : (
                      <Text
                        style={[
                          styles.roundPillText,
                          {
                            color: isCurrent ? themeColor : t.sub,
                            fontWeight: isCurrent ? '800' : '600',
                          },
                        ]}>
                        {roundNum}
                      </Text>
                    )}
                  </View>
                );
              })}
            </View>
          </Card>

          {/* Break Action */}
          {isBreak && (
            <Card>
              <Title icon={FastForward}>Ready to continue?</Title>
              <Sub>You can skip the rest of your break and start the next round now.</Sub>
              <View style={{ height: 10 }} />
              <Btn
                label={`Start Round ${currentRound + 1}`}
                icon={Play}
                onPress={handleSkipBreak}
              />
            </Card>
          )}

          {/* Active Protections Summary */}
          <Card>
            <Title icon={ShieldAlert}>Active Lockouts</Title>
            <View style={styles.lockoutGrid}>
              <View style={styles.lockoutItem}>
                {wifiOn ? <Wifi size={16} color={t.warn} strokeWidth={2.4} /> : <WifiOff size={16} color={t.ok} strokeWidth={2.4} />}
                <Text style={[styles.lockoutText, { color: t.text, flex: 1 }]}>
                  {wifiOn ? 'Wi-Fi is ON — network settings blocked' : 'Wi-Fi is OFF — device is completely offline'}
                </Text>
                {wifiOn && (
                  <Pressable
                    onPress={handleToggleWifi}
                    style={({ pressed }) => [{
                      paddingVertical: 4,
                      paddingHorizontal: 9,
                      backgroundColor: t.dangerSoft,
                      borderRadius: 8,
                      borderWidth: 1,
                      borderColor: t.danger,
                      opacity: pressed ? 0.7 : 1
                    }]}>
                    <Text style={{ color: t.danger, fontSize: 11, fontWeight: '800' }}>Turn Off</Text>
                  </Pressable>
                )}
              </View>
              <View style={styles.lockoutItem}>
                <BellOff size={16} color={t.danger} strokeWidth={2.4} />
                <Text style={[styles.lockoutText, { color: t.text }]}>
                  Notifications shade & alerts silenced
                </Text>
              </View>
              <View style={styles.lockoutItem}>
                <Lock size={16} color={t.danger} strokeWidth={2.4} />
                <Text style={[styles.lockoutText, { color: t.text }]}>
                  All launcher apps locked out
                </Text>
              </View>
            </View>
            <Text style={[styles.lockoutTip, { color: t.sub }]}>
              Tip: You can lock the screen using the power button anytime. Unlocking will bring you straight back to this timer.
            </Text>
          </Card>

          {/* Motivational Quote */}
          <Card>
            <View style={styles.quoteCard}>
              <Sparkles size={16} color={t.accent} strokeWidth={2.4} />
              <Text style={[styles.quoteText, { color: t.text }]}>
                "{quote.quote}"
              </Text>
              <Text style={[styles.quoteSource, { color: t.accent }]}>
                — {quote.source}
              </Text>
            </View>
          </Card>

          {/* Emergency Stop Button */}
          <View style={styles.emergencyContainer}>
            <Pressable
              onPress={() => setShowEmergencyModal(true)}
              style={({ pressed }) => [
                styles.emergencyBtn,
                { opacity: pressed ? 0.6 : 1 },
              ]}>
              <AlertTriangle size={14} color={t.sub} strokeWidth={2} />
              <Text style={[styles.emergencyBtnText, { color: t.sub }]}>
                Emergency Exit
              </Text>
            </Pressable>
          </View>
        </ScrollView>

        {/* Emergency Unlock Modal */}
        <Modal
          visible={showEmergencyModal}
          transparent
          animationType="fade"
          onRequestClose={() => setShowEmergencyModal(false)}>
          <View style={styles.modalOverlay}>
            <View
              style={[
                styles.modalContent,
                { backgroundColor: t.bgAlt, borderColor: t.cardBorder },
              ]}>
              <View style={styles.modalHeader}>
                <AlertTriangle size={26} color={t.danger} strokeWidth={2.5} />
                <Text style={[styles.modalTitle, { color: t.text }]}>
                  Emergency Exit
                </Text>
              </View>
              <Text style={[styles.modalMessage, { color: t.sub }]}>
                Study mode is designed to protect your focus against temptation. Giving up early will cancel all remaining Pomodoro rounds.
              </Text>
              <Text
                style={[
                  styles.modalMessage,
                  { color: t.warn, fontWeight: '700', marginTop: 8 },
                ]}>
                Press and hold the button below for 4 seconds to force unlock.
              </Text>

              {/* Hold-to-exit button */}
              <View style={{ marginTop: 18 }}>
                <Pressable
                  onPressIn={startHoldEmergency}
                  onPressOut={stopHoldEmergency}
                  style={[
                    styles.holdBtn,
                    {
                      borderColor: t.danger,
                      backgroundColor: t.dangerSoft,
                    },
                  ]}>
                  {/* Progress bar fill inside button */}
                  <View
                    style={[
                      styles.holdBtnFill,
                      {
                        width: `${emergencyHoldProgress * 100}%`,
                        backgroundColor: t.danger,
                      },
                    ]}
                  />
                  <Text style={[styles.holdBtnText, { color: '#FFF' }]}>
                    {emergencyHoldProgress > 0
                      ? `Holding... ${Math.round(emergencyHoldProgress * 100)}%`
                      : 'Hold 4s to Emergency Exit'}
                  </Text>
                </Pressable>
              </View>

              <Pressable
                onPress={() => {
                  stopHoldEmergency();
                  setShowEmergencyModal(false);
                }}
                style={styles.modalCancelBtn}>
                <Text style={{ color: t.sub, fontWeight: '700' }}>
                  Keep Studying
                </Text>
              </Pressable>
            </View>
          </View>
        </Modal>
      </View>
    );
  }

  // -------------------------------------------------------------
  // IDLE SETUP VIEW
  // -------------------------------------------------------------
  const totalStudyTimeMinutes = chosenFocus * totalRounds;
  const totalBreakTimeMinutes = breakMinutes * Math.max(0, totalRounds - 1);
  const totalSessionMinutes = totalStudyTimeMinutes + totalBreakTimeMinutes;

  const totalHour = Math.floor(totalSessionMinutes / 60);
  const totalMin = totalSessionMinutes % 60;
  const sessionLengthText =
    totalHour > 0
      ? `${totalHour}h ${totalMin > 0 ? `${totalMin}m` : ''}`
      : `${totalMin}m`;

  return (
    <ScrollView
      contentContainerStyle={styles.setupContainer}
      showsVerticalScrollIndicator={false}
      keyboardShouldPersistTaps="handled">
      {/* Hero Card */}
      <Card>
        <View style={styles.heroRow}>
          <View
            style={[
              styles.heroIconBox,
              { backgroundColor: t.accentSoft, borderColor: t.accent },
            ]}>
            <BookOpen size={26} color={t.accent} strokeWidth={2.4} />
          </View>
          <View style={{ flex: 1 }}>
            <Title size={20} icon={Sparkles}>
              Study Mode
            </Title>
            <Sub>
              Strict Pomodoro phone lockout. When active, all apps, internet toggles, and notification shades are blocked until finished.
            </Sub>
          </View>
        </View>

        {/* Feature Badges */}
        <View style={styles.badgeRow}>
          <View
            style={[
              styles.badgeItem,
              { backgroundColor: t.glass, borderColor: t.border },
            ]}>
            <Lock size={12} color={t.accent} strokeWidth={2.4} />
            <Text style={[styles.badgeText, { color: t.text }]}>Full Lockout</Text>
          </View>
          <View
            style={[
              styles.badgeItem,
              { backgroundColor: t.glass, borderColor: t.border },
            ]}>
            <BellOff size={12} color={t.accent} strokeWidth={2.4} />
            <Text style={[styles.badgeText, { color: t.text }]}>No Notifications</Text>
          </View>
          <View
            style={[
              styles.badgeItem,
              { backgroundColor: t.glass, borderColor: t.border },
            ]}>
            <WifiOff size={12} color={t.accent} strokeWidth={2.4} />
            <Text style={[styles.badgeText, { color: t.text }]}>Internet Shield</Text>
          </View>
        </View>
      </Card>

      {/* Network Shield Card */}
      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, flex: 1 }}>
            <View
              style={{
                width: 38,
                height: 38,
                borderRadius: 10,
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: wifiOn ? t.warnSoft : t.okSoft,
                borderWidth: 1,
                borderColor: wifiOn ? t.warn : t.ok,
              }}>
              {wifiOn ? (
                <Wifi size={18} color={t.warn} strokeWidth={2.4} />
              ) : (
                <WifiOff size={18} color={t.ok} strokeWidth={2.4} />
              )}
            </View>
            <View style={{ flex: 1 }}>
              <Text style={{ color: t.text, fontSize: 15, fontWeight: '700' }}>
                {wifiOn ? 'Wi-Fi is currently ON' : 'Wi-Fi is currently OFF'}
              </Text>
              <Text style={{ color: t.sub, fontSize: 12, marginTop: 2 }}>
                {wifiOn
                  ? 'Blocker will attempt to turn off Wi-Fi on start. You can also turn it off now.'
                  : 'Distraction-free: device is offline and ready for deep work.'}
              </Text>
            </View>
          </View>
          <Pressable
            onPress={handleToggleWifi}
            style={({ pressed }) => [{
              paddingVertical: 7,
              paddingHorizontal: 12,
              backgroundColor: wifiOn ? t.dangerSoft : t.accentSoft,
              borderRadius: 10,
              borderWidth: 1,
              borderColor: wifiOn ? t.danger : t.accent,
              marginLeft: 10,
              opacity: pressed ? 0.7 : 1,
            }]}>
            <Text
              style={{
                color: wifiOn ? t.danger : t.accent,
                fontSize: 12,
                fontWeight: '800',
              }}>
              {wifiOn ? 'Turn Off' : 'Turn On'}
            </Text>
          </Pressable>
        </View>
      </Card>

      {/* Focus Duration */}
      <Card>
        <Title icon={Flame}>Focus Duration</Title>
        <Sub>How long each study session lasts before a rest break.</Sub>
        <ChipSelector
          values={FOCUS_PRESETS}
          selected={customFocus.trim() ? 0 : focusMinutes}
          onPick={m => {
            setCustomFocus('');
            setFocusMinutes(m);
          }}
          formatLabel={m => `${m} min`}
        />
        <TextInput
          value={customFocus}
          onChangeText={setCustomFocus}
          keyboardType="number-pad"
          placeholder="Or custom focus minutes (5 to 180)"
          placeholderTextColor={t.sub}
          style={[
            styles.customInput,
            {
              backgroundColor: t.card,
              borderColor: t.cardBorder,
              color: t.text,
            },
          ]}
        />
      </Card>

      {/* Break Duration */}
      <Card>
        <Title icon={Coffee}>Break Duration</Title>
        <Sub>Short rest period between focus rounds to recharge your brain.</Sub>
        <ChipSelector
          values={BREAK_PRESETS}
          selected={breakMinutes}
          onPick={setBreakMinutes}
          formatLabel={m => `${m} min`}
        />
      </Card>

      {/* Total Rounds */}
      <Card>
        <Title icon={RotateCcw}>Session Rounds</Title>
        <Sub>Number of focus blocks to complete in this study cycle.</Sub>
        <ChipSelector
          values={ROUNDS_PRESETS}
          selected={totalRounds}
          onPick={setTotalRounds}
          formatLabel={r => (r === 1 ? '1 round' : `${r} rounds`)}
        />
      </Card>

      {/* Session Plan Summary & Launch */}
      <Card>
        <Title icon={CheckCircle2}>Session Plan</Title>
        <View style={styles.planSummary}>
          <Text style={[styles.planTitle, { color: t.accent }]}>
            {totalRounds} × {chosenFocus} min focus
            {totalRounds > 1 ? ` + ${totalRounds - 1} × ${breakMinutes} min break` : ''}
          </Text>
          <Text style={[styles.planSub, { color: t.sub }]}>
            Total study session: {sessionLengthText}
          </Text>
        </View>
        <View style={{ height: 12 }} />
        <Btn
          label={`Lock Phone & Study (${sessionLengthText})`}
          icon={Flame}
          onPress={handleStartStudy}
        />
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  setupContainer: {
    padding: 16,
    paddingBottom: 40,
  },
  activeContainer: {
    padding: 16,
    paddingBottom: 40,
  },
  heroRow: {
    flexDirection: 'row',
    gap: 12,
    alignItems: 'flex-start',
  },
  heroIconBox: {
    width: 48,
    height: 48,
    borderRadius: 24,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 2,
  },
  badgeRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 14,
  },
  badgeItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingVertical: 5,
    paddingHorizontal: 10,
    borderRadius: 999,
    borderWidth: 1,
  },
  badgeText: {
    fontSize: 12,
    fontWeight: '700',
  },
  chipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 10,
  },
  chip: {
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 999,
    borderWidth: 1,
  },
  chipText: {
    fontSize: 13,
  },
  customInput: {
    marginTop: 12,
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 10,
    fontSize: 14,
  },
  planSummary: {
    paddingVertical: 4,
  },
  planTitle: {
    fontSize: 16,
    fontWeight: '800',
  },
  planSub: {
    fontSize: 13,
    marginTop: 3,
  },
  phaseHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    marginBottom: 16,
  },
  phaseIconContainer: {
    width: 44,
    height: 44,
    borderRadius: 22,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  phaseTitle: {
    fontSize: 18,
    fontWeight: '800',
  },
  phaseSub: {
    fontSize: 12,
    marginTop: 2,
  },
  timerDial: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 28,
    borderRadius: 24,
    borderWidth: 1.5,
    marginVertical: 10,
    shadowOpacity: 0.6,
    shadowRadius: 20,
    shadowOffset: { width: 0, height: 0 },
    elevation: 8,
  },
  timerDigits: {
    fontSize: 54,
    fontWeight: '900',
    fontVariant: ['tabular-nums'],
    letterSpacing: -1,
  },
  timerSubBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 6,
  },
  timerSubText: {
    fontSize: 13,
    fontWeight: '700',
  },
  progressContainer: {
    marginTop: 12,
    marginBottom: 4,
  },
  progressTrack: {
    height: 8,
    borderRadius: 4,
    overflow: 'hidden',
    width: '100%',
  },
  progressBar: {
    height: '100%',
    borderRadius: 4,
  },
  progressLabel: {
    fontSize: 11,
    fontWeight: '600',
    textAlign: 'right',
    marginTop: 4,
  },
  roundsTracker: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 8,
    marginTop: 14,
  },
  roundPill: {
    width: 30,
    height: 30,
    borderRadius: 15,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  roundPillText: {
    fontSize: 12,
  },
  lockoutGrid: {
    gap: 10,
    marginTop: 6,
  },
  lockoutItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  lockoutText: {
    fontSize: 13,
    fontWeight: '600',
  },
  lockoutTip: {
    fontSize: 12,
    marginTop: 12,
    lineHeight: 17,
  },
  quoteCard: {
    gap: 8,
    paddingVertical: 4,
  },
  quoteText: {
    fontSize: 13,
    fontStyle: 'italic',
    lineHeight: 19,
  },
  quoteSource: {
    fontSize: 12,
    fontWeight: '700',
  },
  emergencyContainer: {
    alignItems: 'center',
    marginTop: 16,
  },
  emergencyBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingVertical: 8,
    paddingHorizontal: 16,
  },
  emergencyBtnText: {
    fontSize: 12,
    fontWeight: '600',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.75)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  modalContent: {
    width: '100%',
    maxWidth: 340,
    borderRadius: 20,
    borderWidth: 1,
    padding: 20,
  },
  modalHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginBottom: 10,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '800',
  },
  modalMessage: {
    fontSize: 13,
    lineHeight: 19,
  },
  holdBtn: {
    height: 48,
    borderRadius: 14,
    borderWidth: 1.5,
    overflow: 'hidden',
    justifyContent: 'center',
    alignItems: 'center',
  },
  holdBtnFill: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
  },
  holdBtnText: {
    fontSize: 14,
    fontWeight: '800',
  },
  modalCancelBtn: {
    alignItems: 'center',
    paddingVertical: 14,
    marginTop: 6,
  },
});
