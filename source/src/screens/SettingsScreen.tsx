import React, { useEffect, useState } from 'react';
import { Alert, Image, Linking, ScrollView, Switch, Text, View } from 'react-native';
import { Code, ExternalLink, RotateCw } from 'lucide-react-native';
import { LOCK_KEYS, LockKey, Native } from '../native/BlockerNative';
import { useNow, useProtection } from '../hooks/useProtection';
import { useAppUpdate } from '../hooks/useAppUpdate';
import {
  deactivateDeviceOwner,
  getPopupLanguage,
  setDelay,
  setPopupLanguage,
  setPreventPowerOff,
  setProtection,
  setShield,
} from '../services/ProtectionManager';
import {
  Btn,
  CyberSwitch,
  GlowCard,
  Icons,
  LockBar,
  PressableScale,
  Row,
  StatusPillBadge,
  Sub,
  Title,
  errMsg,
  showAlert,
  useTheme,
} from '../ui';
import { APP_VERSION, BUILD_NUMBER } from '../data/appVersion';
import { SHELL_NINJA_LOGO_URI } from '../data/shellNinjaLogo';
import { APP_ICON_DATA_URI } from '../data/appIcon';
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
  app_limits: 'App usage timers',
  device_owner: 'Device Owner deactivation',
  power_menu: 'Power Menu shield',
};

export default function SettingsScreen() {
  const t = useTheme();
  const p = useProtection();
  const now = useNow();
  const update = useAppUpdate();
  const [modalVisible, setModalVisible] = useState(false);
  const isAhead = update.release ? compareSemVer(APP_VERSION, update.release.version) > 0 : false;
  const [cacheStats, setCacheStats] = useState<{ fileCount: number; totalBytes: number }>({
    fileCount: 0,
    totalBytes: 0,
  });
  const [popupLang, setPopupLang] = useState<'en' | 'bn'>('en');

  useEffect(() => {
    getPopupLanguage().then(setPopupLang).catch(() => {});
  }, []);

  const handleSetPopupLanguage = async (lang: 'en' | 'bn') => {
    setPopupLang(lang);
    try {
      await setPopupLanguage(lang);
    } catch (e) {
      showAlert('Error', errMsg(e));
    }
  };

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
        showAlert(
          r === 'restarted' ? 'Timer restarted' : 'Change requested',
          `${what} changes after the ${p.delayDays}-day timer completes and you confirm.`
        );
      }
    } catch (e) {
      showAlert('Blocker', errMsg(e));
    }
  };

  const handleDeactivateDeviceOwner = () => {
    const hasActiveLock = Object.values(p.locks).some(l => l && (l.pending || (l.remainingMs ?? 0) > 0));
    const isLocked = p.active || p.shield || hasActiveLock || p.focus.active || p.study.active;
    if (!isLocked) {
      Alert.alert(
        'Deactivate Device Owner?',
        'This will clear Device Owner status and deactivate hardware-level protections.',
        [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Deactivate',
            style: 'destructive',
            onPress: () => run(() => deactivateDeviceOwner(), 'Device Owner'),
          },
        ]
      );
    } else {
      run(() => deactivateDeviceOwner(), 'Device Owner');
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
      showAlert(
        'Couldn\u2019t open settings',
        `${errMsg(e)}\n\nOpen Settings \u2192 Security \u2192 Device admin apps \u2192 Blocker manually.`
      );
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
      showAlert(
        'Couldn\u2019t open settings',
        `${errMsg(e)}\n\nOpen Settings \u2192 Apps \u2192 Blocker \u2192 Battery and choose Unrestricted.`
      );
    }
  };

  return (
    <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 28 }} showsVerticalScrollIndicator={false}>
      {/* Master Protection */}
      <GlowCard style={{ padding: 18 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <View style={{ flex: 1, paddingRight: 12 }}>
            <Title icon={Icons.shieldOn}>Protection</Title>
            <Sub>Master switch for all blocking.</Sub>
          </View>
          <CyberSwitch value={p.active} onValueChange={v => run(() => setProtection(v), 'Protection')} />
        </View>
        <LockBar lockKey="protection" />
      </GlowCard>

      {/* Uninstall & Settings Shield */}
      <GlowCard style={{ padding: 18 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <View style={{ flex: 1, paddingRight: 12 }}>
            <Title icon={Icons.lock}>Uninstall & settings shield</Title>
            <Sub>Blocks Developer options, Device admin, Special app access and Blocker's app settings pages.</Sub>
          </View>
          <CyberSwitch value={p.shield} onValueChange={v => run(() => setShield(v), 'The shield')} />
        </View>

        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 14 }}>
          <Text style={{ color: '#FFFFFF', fontWeight: '600', fontSize: 13.5 }}>Device admin</Text>
          {p.perms?.deviceAdmin ? (
            <StatusPillBadge label="Enabled" tone="ok" size="sm" />
          ) : (
            <Btn label="Enable" icon={Icons.shieldOn} kind="primary" onPress={grantDeviceAdmin} />
          )}
        </View>
        {p.perms && !p.perms.deviceAdmin && (
          <Sub>If the button doesn't open the right screen, go to Settings → Security → Device admin apps → Blocker.</Sub>
        )}

        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 14 }}>
          <Text style={{ color: '#FFFFFF', fontWeight: '600', fontSize: 13.5 }}>Device Owner</Text>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <StatusPillBadge
              label={p.perms?.isDeviceOwner ? 'Active' : 'Inactive'}
              tone={p.perms?.isDeviceOwner ? 'ok' : 'idle'}
              size="sm"
            />
            {p.perms?.isDeviceOwner && (
              <Btn
                label="Deactivate"
                kind="ghost"
                icon={Icons.shieldOff}
                onPress={handleDeactivateDeviceOwner}
              />
            )}
          </View>
        </View>
        <Sub>
          {p.perms?.isDeviceOwner
            ? 'Hardware-level device ownership is active and tamper-proof.'
            : 'For advanced hardware-level anti-tamper protection, set Device Owner via ADB: adb shell dpm set-device-owner com.blocker/.BlockerDeviceAdminReceiver'}
        </Sub>
        <LockBar lockKey="device_owner" />

        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 14 }}>
          <Text style={{ color: '#FFFFFF', fontWeight: '600', fontSize: 13.5 }}>USB & Wireless debugging lock</Text>
          <StatusPillBadge
            label={!p.shield ? 'Off' : p.perms?.secureSettings ? 'Instant lock' : 'On'}
            tone={p.shield ? 'ok' : 'idle'}
            size="sm"
          />
        </View>
        <Sub>
          {p.perms?.secureSettings
            ? 'USB and Wireless debugging are switched off the moment anything turns them on.'
            : 'Developer options are blocked, and if USB or Wireless debugging is ever found on, Blocker switches it off through Settings by itself. ' +
              'Optional instant lock (needs a computer once): adb shell pm grant com.blocker android.permission.WRITE_SECURE_SETTINGS'}
        </Sub>
        <LockBar lockKey="shield" />

        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 16 }}>
          <View style={{ flex: 1, paddingRight: 12 }}>
            <Text style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 13.5 }}>Prevent Power off & Restart</Text>
            <Sub>Dismisses power and restart dialogs to prevent impulse shutdowns. Disabling requires a 2-hour delay.</Sub>
          </View>
          <CyberSwitch
            value={p.stats?.preventPowerOff ?? false}
            onValueChange={v => run(() => setPreventPowerOff(v), 'Prevent Power off')}
          />
        </View>
        <LockBar lockKey="power_menu" />
      </GlowCard>

      {/* Anti-Tamper & Uninstall Fortress */}
      <GlowCard style={{ padding: 18 }}>
        <Title icon={Icons.shield}>Anti-Tamper & Uninstall Fortress</Title>
        <Sub>
          Blocker employs an airtight multi-layer defense preventing uninstallation, deactivation, or circumvention without completing the full delay timer.
        </Sub>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 14 }}>
          <View style={{ flex: 1, paddingRight: 10 }}>
            <Text style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 13.5 }}>Protection Enforcement</Text>
            <Sub>Active 24/7 (Hardware-Enforced)</Sub>
          </View>
          <StatusPillBadge label="100% Locked" tone="ok" size="sm" breathing />
        </View>
        <View style={{ marginTop: 12, gap: 8 }}>
          <Text style={{ color: t.sub, fontSize: 13, lineHeight: 19 }}>
            • <Text style={{ color: '#FFFFFF', fontWeight: '600' }}>Device Administrator:</Text> Android OS blocks immediate uninstallation while protection is engaged.{'\n'}
            • <Text style={{ color: '#FFFFFF', fontWeight: '600' }}>Zero-ms Launcher Shield:</Text> Press-and-hold / drag-to-uninstall is intercepted in 0ms across all launchers.{'\n'}
            • <Text style={{ color: '#FFFFFF', fontWeight: '600' }}>Settings Ejection:</Text> Any attempt to reach App Info, Clear Data, or Admin Deactivation is kicked to Home.{'\n'}
            • <Text style={{ color: '#FFFFFF', fontWeight: '600' }}>Package Installer Watchdog:</Text> Foreground service kicks sideload and uninstaller dialogs within 200ms.{'\n'}
            • <Text style={{ color: '#FFFFFF', fontWeight: '600' }}>Delayed Weakening:</Text> Turning off protection requires waiting out the full delay timer.
          </Text>
        </View>
      </GlowCard>

      {/* Background Activity */}
      <GlowCard style={{ padding: 18 }}>
        <Title icon={Icons.clock}>Background activity</Title>
        <Sub>
          Battery optimization can put Blocker to sleep. Setting it to unrestricted keeps blocking running in the background.
        </Sub>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 14 }}>
          <Text style={{ color: '#FFFFFF', fontWeight: '600', fontSize: 13.5 }}>Battery optimization</Text>
          {p.perms?.battery ? (
            <StatusPillBadge label="Unrestricted" tone="ok" size="sm" />
          ) : (
            <Btn label="Allow" icon={Icons.shieldOn} kind="primary" onPress={allowBackground} />
          )}
        </View>
      </GlowCard>

      {/* Delay Timer */}
      <GlowCard style={{ padding: 18 }}>
        <Title icon={Icons.clock}>Delay timer</Title>
        <Sub>How long a change waits before it can be confirmed. Currently {p.delayDays} day(s).</Sub>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 14 }}>
          {DAYS.map(d => {
            const sel = d === p.delayDays;
            return (
              <PressableScale
                key={d}
                onPress={() => run(() => setDelay(d), 'The delay')}
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
                    fontSize: 13,
                  }}>
                  {d === 1 ? '24h' : `${d}d`}
                </Text>
              </PressableScale>
            );
          })}
        </View>
        <Sub>Lengthening applies immediately; shortening is protected by the active delay.</Sub>
        <LockBar lockKey="delay_duration" />
      </GlowCard>

      {/* Popup Language Selection */}
      <GlowCard style={{ padding: 18 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
          <Title icon={Icons.layers}>Popup Messages Language</Title>
          <StatusPillBadge label={popupLang === 'bn' ? 'বাংলা' : 'English'} tone="ok" size="sm" />
        </View>
        <Sub>
          Choose the language for blocker warning overlays, Quranic verses, and Hadiths (app UI remains in English).
        </Sub>
        <View style={{ flexDirection: 'row', gap: 10, marginTop: 14 }}>
          <PressableScale
            onPress={() => handleSetPopupLanguage('en')}
            style={{
              flex: 1,
              paddingVertical: 12,
              paddingHorizontal: 14,
              borderRadius: 14,
              borderWidth: 1.5,
              borderColor: popupLang === 'en' ? t.accentWellBorder : t.border,
              backgroundColor: popupLang === 'en' ? t.accentWell : t.bg,
              alignItems: 'center',
            }}>
            <Text
              style={{
                color: popupLang === 'en' ? '#FFFFFF' : t.text,
                fontWeight: '700',
                fontSize: 14,
              }}>
              English
            </Text>
            <Text style={{ color: t.sub, fontSize: 11.5, marginTop: 3 }}>
              Fear Allah
            </Text>
          </PressableScale>

          <PressableScale
            onPress={() => handleSetPopupLanguage('bn')}
            style={{
              flex: 1,
              paddingVertical: 12,
              paddingHorizontal: 14,
              borderRadius: 14,
              borderWidth: 1.5,
              borderColor: popupLang === 'bn' ? t.accentWellBorder : t.border,
              backgroundColor: popupLang === 'bn' ? t.accentWell : t.bg,
              alignItems: 'center',
            }}>
            <Text
              style={{
                color: popupLang === 'bn' ? '#FFFFFF' : t.text,
                fontWeight: '700',
                fontSize: 14,
              }}>
              বাংলা (Bangla)
            </Text>
            <Text style={{ color: t.sub, fontSize: 11.5, marginTop: 3 }}>
              আল্লাহকে ভয় করুন
            </Text>
          </PressableScale>
        </View>
      </GlowCard>

      {/* Screen Monitoring */}
      <GlowCard style={{ padding: 18 }}>
        <Title icon={Icons.eye}>Screen Monitoring</Title>
        <Sub>
          Scans text locally on-device. Blocked words trigger an instant safe exit. Completely private, offline, and zero telemetry.
        </Sub>
      </GlowCard>

      {/* Lock Status */}
      <GlowCard style={{ padding: 18 }}>
        <Title icon={Icons.list}>Lock status</Title>
        <View style={{ marginTop: 8 }}>
          {LOCK_KEYS.map(k => {
            const l = p.locks[k];
            const queued = p.state.queue[k]?.length ?? 0;
            const ready = !!l && (l.open || (l.pending && now >= l.unlockAt));
            const tone = ready && queued ? 'ok' : l?.pending ? 'warn' : 'idle';
            const label = ready && queued ? 'Ready' : l?.pending ? 'Locked' : 'Idle';
            return <Row key={k} label={NAMES[k]} right={<StatusPillBadge label={label} tone={tone} size="sm" />} />;
          })}
        </View>
        <LockBar lockKey="remove_apps" />
        <LockBar lockKey="remove_blocklist" />
        <LockBar lockKey="whitelist" />
        <LockBar lockKey="exempt_apps" />
        <LockBar lockKey="device_owner" />
        <LockBar lockKey="power_menu" />
      </GlowCard>

      {/* App Updates Bento Card */}
      <GlowCard
        style={{ padding: 18, borderColor: update.hasUpdate ? t.accentWellBorder : t.cardBorder, borderWidth: 1 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <Title icon={Icons.download}>App Updates</Title>
          <StatusPillBadge
            label={
              update.hasUpdate
                ? `v${update.release?.version} Available`
                : isAhead
                ? `v${APP_VERSION} (Dev)`
                : `v${APP_VERSION} (Latest)`
            }
            tone={update.hasUpdate ? 'warn' : 'ok'}
            size="sm"
          />
        </View>
        <Sub>Check GitHub releases for updates, changelogs, bug fixes, and install directly.</Sub>

        <View style={{ marginTop: 12, gap: 10 }}>
          <Row
            label="Installed Version"
            right={
              <Text style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 13 }}>
                v{APP_VERSION} (build {BUILD_NUMBER})
              </Text>
            }
          />
          {update.release && (
            <Row
              label="Latest on GitHub"
              right={
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                  <Text
                    style={{
                      color: update.hasUpdate ? t.accent : '#FFFFFF',
                      fontWeight: '700',
                      fontSize: 13,
                    }}>
                    {update.release.tagName}
                  </Text>
                  {update.hasUpdate ? (
                    <StatusPillBadge label="Update" tone="warn" icon={false} size="sm" />
                  ) : isAhead ? (
                    <StatusPillBadge label="Public" tone="idle" icon={false} size="sm" />
                  ) : (
                    <StatusPillBadge label="Up to date" tone="ok" icon={false} size="sm" />
                  )}
                </View>
              }
            />
          )}

          <Row
            label="Download Cache"
            right={
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                <Text style={{ color: t.sub, fontSize: 12 }}>
                  {cacheStats.fileCount > 0
                    ? `${(cacheStats.totalBytes / (1024 * 1024)).toFixed(1)} MB (${cacheStats.fileCount} file${
                        cacheStats.fileCount > 1 ? 's' : ''
                      })`
                    : 'Clean (0 MB)'}
                </Text>
                <PressableScale
                  onPress={handleClearCache}
                  style={{
                    paddingVertical: 4,
                    paddingHorizontal: 9,
                    borderRadius: 8,
                    backgroundColor: cacheStats.fileCount > 0 ? t.dangerSoft : 'rgba(255, 255, 255, 0.05)',
                    borderWidth: 1,
                    borderColor: cacheStats.fileCount > 0 ? t.dangerBorder : t.border,
                  }}>
                  <Text
                    style={{
                      color: cacheStats.fileCount > 0 ? t.danger : t.sub,
                      fontSize: 11,
                      fontWeight: '700',
                    }}>
                    Clear
                  </Text>
                </PressableScale>
              </View>
            }
          />

          <View style={{ flexDirection: 'row', gap: 8, marginTop: 6 }}>
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
      </GlowCard>

      <UpdateModal visible={modalVisible} onClose={() => setModalVisible(false)} />

      {/* Footer Branding */}
      <View style={{ alignItems: 'center', marginTop: 18, marginBottom: 28, gap: 12 }}>
        <PressableScale
          onPress={() => Linking.openURL('https://github.com/shell-ninja/Blocker-App').catch(() => {})}
          contentStyle={{
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'center',
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
          }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <Code size={16} color={t.accent} strokeWidth={2.5} />
            <Text style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 13.5 }}>Source Code</Text>
            <ExternalLink size={13} color={t.sub} strokeWidth={2.2} />
          </View>
        </PressableScale>

        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: 4 }}>
          <Text style={{ color: t.sub, fontSize: 13, lineHeight: 18 }}>Developed By</Text>
          <PressableScale
            onPress={() => Linking.openURL('https://github.com/shell-ninja').catch(() => {})}
            hitSlop={8}
            contentStyle={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
            <Image
              source={{ uri: SHELL_NINJA_LOGO_URI }}
              style={{ width: 18, height: 18, borderRadius: 4 }}
              resizeMode="contain"
            />
            <Text
              style={{
                color: '#A78BFA',
                fontSize: 13,
                lineHeight: 18,
                fontWeight: '700',
                textDecorationLine: 'underline',
              }}>
              Shell Ninja
            </Text>
          </PressableScale>
        </View>

        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, opacity: 0.85 }}>
          <Image
            source={{ uri: APP_ICON_DATA_URI }}
            style={{ width: 16, height: 16, borderRadius: 4 }}
            resizeMode="contain"
          />
          <Text style={{ color: t.sub, fontSize: 11.5, textAlign: 'center' }}>
            Blocker v-{APP_VERSION}
          </Text>
        </View>
      </View>
    </ScrollView>
  );
}
