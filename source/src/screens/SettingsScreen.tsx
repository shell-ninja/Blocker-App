import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Image, Linking, Pressable, ScrollView, Switch, Text, View } from 'react-native';
import { ArrowDownToLine, Code, ExternalLink, RotateCw, Trash2 } from 'lucide-react-native';
import { LOCK_KEYS, LockKey, Native } from '../native/BlockerNative';
import { useNow, useProtection } from '../hooks/useProtection';
import { useAppUpdate } from '../hooks/useAppUpdate';
import { setDelay, setProtection, setShield } from '../services/ProtectionManager';
import { Badge, Btn, Card, Icons, LockBar, Row, Sub, Title, errMsg, showAlert, useTheme } from '../ui';
import { APP_VERSION, BUILD_NUMBER } from '../data/appVersion';
import { SHELL_NINJA_LOGO_URI } from '../data/shellNinjaLogo';
import { compareSemVer, UpdateService } from '../services/UpdateService';
import UpdateModal from './UpdateModal';

const DAYS = [1, 2, 3, 7, 14, 30];
const NAMES: Record<LockKey, string> = {
  protection: 'Master protection',
  shield: 'Uninstall shield',
  delay_duration: 'Delay duration',
  remove_apps: 'App unblocks',
  remove_blocklist: 'Blocklist removals',
  whitelist: 'Whitelist additions',
  exempt_apps: 'Scan exemptions',
  focus: 'Focus mode',
  granular_focus: 'Distraction shield changes',
  schedule: 'Daily schedules',
  app_limits: 'App usage timers'
};

export default function SettingsScreen() {
  const t = useTheme();
  const p = useProtection();
  const now = useNow();
  const update = useAppUpdate();
  const [modalVisible, setModalVisible] = useState(false);
  const isAhead = update.release ? compareSemVer(APP_VERSION, update.release.version) > 0 : false;
  const [cacheStats, setCacheStats] = useState<{ fileCount: number; totalBytes: number }>({ fileCount: 0, totalBytes: 0 });

  useEffect(() => {
    update.getCacheStats().then(setCacheStats).catch(() => {});
  }, [modalVisible, update.isDownloaded]);

  const handleClearCache = async () => {
    try {
      const res = await update.clearUpdateCache();
      setCacheStats({ fileCount: 0, totalBytes: 0 });
      const freedMb = (res.freedBytes / (1024 * 1024)).toFixed(1);
      showAlert(
        'Cache Cleared',
        res.deletedCount > 0
          ? `Removed ${res.deletedCount} update file(s) and freed ${freedMb} MB.`
          : 'Update cache is already empty.'
      );
    } catch (e) {
      showAlert('Clear Failed', errMsg(e));
    }
  };

  const checkUpdatesManual = async () => {
    try {
      const rel = await update.checkForUpdate(true);
      const state = UpdateService.getState();
      if (state.hasUpdate) {
        setModalVisible(true);
      } else {
        const isAhead = rel ? compareSemVer(APP_VERSION, rel.version) > 0 : false;
        showAlert(
          'Blocker is Up to Date',
          isAhead
            ? `You are running v${APP_VERSION} (build ${BUILD_NUMBER}), which is ahead of GitHub's latest release (${rel?.tagName}).`
            : `You are on the latest version (v${APP_VERSION}).`
        );
      }
    } catch (e) {
      showAlert('Check Failed', errMsg(e));
    }
  };

  const run = async (fn: () => Promise<string>, what: string) => {
    try {
      const r = await fn();
      if (r !== 'applied') {
        showAlert(r === 'restarted' ? 'Timer restarted' : 'Change requested',
          `${what} changes after the ${p.delayDays}-day timer completes and you confirm.`);
      }
    } catch (e) {
      showAlert('Blocker', errMsg(e));
    }
  };

  const grantDeviceAdmin = async () => {
    try {
      const direct = await Native.openPermissionSettings('deviceAdmin');
      if (!direct) {
        showAlert(
          'Opened Security settings',
          'Your phone didn\u2019t support the direct screen, so Security settings opened instead. ' +
          'Look for \u201cDevice admin apps\u201d and turn on Blocker there.'
        );
      }
    } catch (e) {
      showAlert('Couldn\u2019t open settings', `${errMsg(e)}\n\nOpen Settings \u2192 Security \u2192 Device admin apps \u2192 Blocker manually.`);
    }
  };

  const allowBackground = async () => {
    try {
      const direct = await Native.openPermissionSettings('battery');
      if (!direct) {
        showAlert(
          'Opened battery settings',
          'Find Blocker in the list and choose \u201cDon\u2019t optimize\u201d (or \u201cUnrestricted\u201d) so it can keep running in the background.'
        );
      }
    } catch (e) {
      showAlert('Couldn\u2019t open settings', `${errMsg(e)}\n\nOpen Settings \u2192 Apps \u2192 Blocker \u2192 Battery and choose Unrestricted.`);
    }
  };

  return (
    <ScrollView contentContainerStyle={{ padding: 16 }}>
      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <View style={{ flex: 1, paddingRight: 12 }}>
            <Title icon={Icons.shieldOn}>Protection</Title>
            <Sub>Master switch for all blocking.</Sub>
          </View>
          <Switch value={p.active} onValueChange={v => run(() => setProtection(v), 'Protection')} trackColor={{ true: t.ok }} />
        </View>
        <LockBar lockKey="protection" />
      </Card>

      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <View style={{ flex: 1, paddingRight: 12 }}>
            <Title icon={Icons.lock}>Uninstall & settings shield</Title>
            <Sub>Blocks Developer options, Device admin, Special app access and Blocker's app settings pages.</Sub>
          </View>
          <Switch value={p.shield} onValueChange={v => run(() => setShield(v), 'The shield')} trackColor={{ true: t.ok }} />
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 12 }}>
          <Text style={{ color: t.text }}>Device admin</Text>
          {p.perms?.deviceAdmin ? <Badge label="Enabled" tone="ok" /> : <Btn label="Enable" icon={Icons.shieldOn} onPress={grantDeviceAdmin} />}
        </View>
        {p.perms && !p.perms.deviceAdmin && (
          <Sub>If the button doesn't open the right screen, go to Settings → Security → Device admin apps → Blocker.</Sub>
        )}
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 12 }}>
          <Text style={{ color: t.text }}>USB & Wireless debugging lock</Text>
          <Badge
            label={!p.shield ? 'Off' : p.perms?.secureSettings ? 'Instant lock' : 'On'}
            tone={p.shield ? 'ok' : 'idle'}
          />
        </View>
        <Sub>
          {p.perms?.secureSettings
            ? 'USB and Wireless debugging are switched off the moment anything turns them on.'
            : 'Developer options are blocked, and if USB or Wireless debugging is ever found on, Blocker switches it off through Settings by itself. ' +
              'Optional instant lock (needs a computer once): adb shell pm grant com.blocker android.permission.WRITE_SECURE_SETTINGS'}
        </Sub>
        <LockBar lockKey="shield" />
      </Card>

      <Card>
        <Title icon={Icons.clock}>Background activity</Title>
        <Sub>
          Battery optimization can put Blocker to sleep. Setting it to unrestricted keeps blocking running in the background.
        </Sub>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 12 }}>
          <Text style={{ color: t.text }}>Battery optimization</Text>
          {p.perms?.battery
            ? <Badge label="Unrestricted" tone="ok" />
            : <Btn label="Allow" icon={Icons.shieldOn} onPress={allowBackground} />}
        </View>
      </Card>

      <Card>
        <Title icon={Icons.clock}>Delay timer</Title>
        <Sub>How long a change waits before it can be confirmed. Currently {p.delayDays} day(s).</Sub>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 12 }}>
          {DAYS.map(d => {
            const sel = d === p.delayDays;
            return (
              <Pressable
                key={d}
                onPress={() => run(() => setDelay(d), 'The delay')}
                style={{
                  paddingVertical: 8, paddingHorizontal: 14, borderRadius: 999, borderWidth: 1,
                  borderColor: sel ? t.accent : t.border, backgroundColor: sel ? t.accentSoft : 'transparent',
                  shadowColor: sel ? t.accent : 'transparent', shadowOpacity: sel ? 0.85 : 0,
                  shadowRadius: sel ? 8 : 0, shadowOffset: { width: 0, height: 0 }, elevation: sel ? 4 : 0
                }}>
                <Text style={{
                  color: sel ? t.accent : t.text, fontWeight: '700',
                  textShadowColor: sel ? t.accentSoft : 'transparent', textShadowOffset: { width: 0, height: 0 }, textShadowRadius: sel ? 6 : 0
                }}>{d === 1 ? '24h' : `${d}d`}</Text>
              </Pressable>
            );
          })}
        </View>
        <Sub>Lengthening applies immediately; shortening is protected by the active delay.</Sub>
        <LockBar lockKey="delay_duration" />
      </Card>

      <Card>
        <Title icon={Icons.eye}>Screen Monitoring</Title>
        <Sub>
          Scans text locally on-device. Blocked words trigger an instant safe exit. Completely private, offline, and zero telemetry.
        </Sub>
      </Card>

      <Card>
        <Title icon={Icons.list}>Lock status</Title>
        {LOCK_KEYS.map(k => {
          const l = p.locks[k];
          const queued = p.state.queue[k]?.length ?? 0;
          const ready = !!l && (l.open || (l.pending && now >= l.unlockAt));
          const tone = ready && queued ? 'ok' : l?.pending ? 'warn' : 'idle';
          const label = ready && queued ? 'Ready' : l?.pending ? 'Locked' : 'Idle';
          return <Row key={k} label={NAMES[k]} right={<Badge label={label} tone={tone} />} />;
        })}
        <LockBar lockKey="remove_apps" />
        <LockBar lockKey="remove_blocklist" />
        <LockBar lockKey="whitelist" />
        <LockBar lockKey="exempt_apps" />
      </Card>

      <Card style={update.hasUpdate ? { borderColor: t.accent, borderWidth: 1.5 } : undefined}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <Title icon={Icons.download}>App Updates</Title>
          <Badge
            label={
              update.hasUpdate
                ? `v${update.release?.version} Available`
                : isAhead
                ? `v${APP_VERSION} (Dev)`
                : `v${APP_VERSION} (Latest)`
            }
            tone={update.hasUpdate ? 'warn' : 'ok'}
          />
        </View>
        <Sub>
          Check GitHub releases for updates, changelogs, bug fixes, and install directly.
        </Sub>

        <View style={{ marginTop: 12, gap: 10 }}>
          <Row
            label="Installed Version"
            right={<Text style={{ color: t.text, fontWeight: '700', fontSize: 13 }}>v{APP_VERSION} (build {BUILD_NUMBER})</Text>}
          />
          {update.release && (
            <Row
              label="Latest on GitHub"
              right={
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                  <Text
                    style={{
                      color: update.hasUpdate ? t.accent : t.text,
                      fontWeight: '700',
                      fontSize: 13
                    }}>
                    {update.release.tagName}
                  </Text>
                  {update.hasUpdate ? (
                    <Badge label="Update" tone="warn" icon={false} />
                  ) : isAhead ? (
                    <Badge label="Public" tone="idle" icon={false} />
                  ) : (
                    <Badge label="Up to date" tone="ok" icon={false} />
                  )}
                </View>
              }
            />
          )}

          {cacheStats.fileCount > 0 && (
            <Row
              label="Download Cache"
              right={
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                  <Text style={{ color: t.sub, fontSize: 12 }}>
                    {(cacheStats.totalBytes / (1024 * 1024)).toFixed(1)} MB ({cacheStats.fileCount} file{cacheStats.fileCount > 1 ? 's' : ''})
                  </Text>
                  <Pressable
                    onPress={handleClearCache}
                    style={{
                      paddingVertical: 3,
                      paddingHorizontal: 8,
                      borderRadius: 6,
                      backgroundColor: 'rgba(239, 68, 68, 0.12)',
                      borderWidth: 1,
                      borderColor: 'rgba(239, 68, 68, 0.3)',
                    }}>
                    <Text style={{ color: t.warn, fontSize: 11, fontWeight: '700' }}>Clear</Text>
                  </Pressable>
                </View>
              }
            />
          )}

          <View style={{ flexDirection: 'row', gap: 8, marginTop: 4 }}>
            {update.hasUpdate ? (
              <>
                <Btn
                  style={{ flex: 1 }}
                  label="View & Install"
                  kind="primary"
                  icon={Icons.download}
                  onPress={() => setModalVisible(true)}
                />
                <Btn
                  style={{ flex: 1 }}
                  label={update.checking ? 'Checking...' : 'Check Again'}
                  kind="ghost"
                  icon={RotateCw}
                  disabled={update.checking}
                  onPress={checkUpdatesManual}
                />
              </>
            ) : (
              <>
                <Btn
                  style={{ flex: 1 }}
                  label={update.checking ? 'Checking...' : 'Check Updates'}
                  kind="primary"
                  icon={update.checking ? RotateCw : Icons.clock}
                  disabled={update.checking}
                  onPress={checkUpdatesManual}
                />
                {update.release && (
                  <Btn
                    style={{ flex: 1 }}
                    label="Release Notes"
                    kind="ghost"
                    icon={Icons.list}
                    onPress={() => setModalVisible(true)}
                  />
                )}
              </>
            )}
          </View>
        </View>
      </Card>

      <UpdateModal
        visible={modalVisible}
        onClose={() => setModalVisible(false)}
      />

      <View style={{ alignItems: 'center', marginTop: 14, marginBottom: 28, gap: 12 }}>
        <Pressable
          onPress={() => Linking.openURL('https://github.com/shell-ninja/Blocker-App').catch(() => {})}
          style={({ pressed }) => [{
            flexDirection: 'row',
            alignItems: 'center',
            gap: 8,
            backgroundColor: t.card,
            borderColor: t.cardBorder,
            borderWidth: 1,
            borderRadius: 14,
            paddingVertical: 10,
            paddingHorizontal: 18,
            shadowColor: t.accent,
            shadowOpacity: 0.35,
            shadowRadius: 10,
            shadowOffset: { width: 0, height: 2 },
            opacity: pressed ? 0.75 : 1,
            transform: [{ scale: pressed ? 0.97 : 1 }]
          }]}>
          <Code size={16} color={t.accent} strokeWidth={2.5} />
          <Text style={{ color: t.text, fontWeight: '700', fontSize: 13.5 }}>Source Code</Text>
          <ExternalLink size={13} color={t.sub} strokeWidth={2.2} />
        </Pressable>

        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 5 }}>
          <Text style={{ color: t.sub, fontSize: 13 }}>Developed By</Text>
          <Pressable
            onPress={() => Linking.openURL('https://github.com/shell-ninja').catch(() => {})}
            hitSlop={8}
            style={({ pressed }) => [{
              flexDirection: 'row',
              alignItems: 'center',
              gap: 5,
              opacity: pressed ? 0.7 : 1
            }]}>
            <Image
              source={{ uri: SHELL_NINJA_LOGO_URI }}
              style={{ width: 18, height: 18, borderRadius: 4 }}
              resizeMode="contain"
            />
            <Text style={{
              color: t.accent,
              fontSize: 13,
              fontWeight: '700',
              textDecorationLine: 'underline',
              textShadowColor: t.accentSoft,
              textShadowOffset: { width: 0, height: 0 },
              textShadowRadius: 8
            }}>
              Shell Ninja
            </Text>
          </Pressable>
        </View>

        <Text style={{ color: t.sub, fontSize: 11.5, textAlign: 'center', opacity: 0.75 }}>
          Blocker v-{APP_VERSION}
        </Text>
      </View>
    </ScrollView>
  );
}