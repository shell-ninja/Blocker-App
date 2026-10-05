import React, { useEffect, useState } from 'react';
import { AppState, ScrollView, Switch, Text, View } from 'react-native';
import { LucideIcon } from 'lucide-react-native';
import { PermissionKind, Native } from '../native/BlockerNative';
import { useProtection } from '../hooks/useProtection';
import { markOnboarded, refresh, setProtection, setShield } from '../services/ProtectionManager';
import { Badge, Btn, Card, Icons, Sub, Title, errMsg, showAlert, useTheme } from '../ui';

const STEPS: { kind: PermissionKind; title: string; icon: LucideIcon; why: string; how: string }[] = [
  {
    kind: 'accessibility',
    title: 'Accessibility service',
    icon: Icons.eye,
    why: 'Reads the browser address bar and detects blocked apps.',
    how: 'Installed apps → Blocker → turn on.'
  },
  {
    kind: 'deviceAdmin',
    title: 'Device admin',
    icon: Icons.shieldOn,
    why: 'Prevents Blocker from being uninstalled.',
    how: 'Tap Activate this device admin app.'
  },
  {
    kind: 'usageAccess',
    title: 'Usage access',
    icon: Icons.apps,
    why: 'Lets Blocker see which app is in the foreground.',
    how: 'Select Blocker and allow usage access.'
  },
  {
    kind: 'overlay',
    title: 'Display over other apps',
    icon: Icons.sparkles,
    why: 'Shows the blocking screen on top of blocked content.',
    how: 'Allow display over other apps for Blocker.'
  }
];

export default function OnboardingPermissions({ onDone }: { onDone: () => void }) {
  const t = useTheme();
  const { perms } = useProtection();
  const [step, setStep] = useState<'permissions' | 'defaults'>('permissions');
  const [enableProtection, setEnableProtection] = useState(true);
  const [enableShield, setEnableShield] = useState(true);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const i = setInterval(() => refresh().catch(() => {}), 1500);
    const sub = AppState.addEventListener('change', s => s === 'active' && refresh().catch(() => {}));
    return () => {
      clearInterval(i);
      sub.remove();
    };
  }, []);

  const granted = (k: PermissionKind) => !!perms?.[k];
  const all = STEPS.every(s => granted(s.kind));
  const next = STEPS.find(s => !granted(s.kind));

  const grant = async (kind: PermissionKind) => {
    try {
      const direct = await Native.openPermissionSettings(kind);
      if (!direct && kind === 'deviceAdmin') {
        showAlert('Opened Security settings', 'Look for \u201cDevice admin apps\u201d and turn on Blocker there.');
      }
    } catch (e) {
      showAlert('Couldn\u2019t open that screen', errMsg(e));
    }
  };

  const allowBackground = async () => {
    try {
      await Native.openPermissionSettings('battery');
    } catch (e) {
      showAlert('Couldn\u2019t open settings', errMsg(e));
    }
  };

  const finishSetup = async () => {
    setLoading(true);
    try {
      if (enableProtection) await setProtection(true);
      if (enableShield) await setShield(true);
      markOnboarded();
      onDone();
    } catch (e) {
      showAlert('Setup error', errMsg(e));
    } finally {
      setLoading(false);
    }
  };

  // Step 2: Guide user on which buttons to enable by default
  if (step === 'defaults') {
    const batteryOk = !!perms?.battery;
    return (
      <ScrollView contentContainerStyle={{ padding: 20 }}>
        <Title size={26} icon={Icons.shieldOn}>Recommended Defaults</Title>
        <Text style={{ color: t.sub, marginTop: 6, marginBottom: 20, lineHeight: 20 }}>
          Permissions granted! Enable these recommended buttons by default to ensure full protection:
        </Text>

        {/* 1. Master Protection */}
        <Card style={{ borderColor: enableProtection ? t.accent : t.cardBorder, borderWidth: 1.5 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <View style={{ flex: 1, paddingRight: 10 }}>
              <Title icon={Icons.shieldOn} tone={enableProtection ? 'ok' : 'accent'}>1. Master Protection</Title>
              <Sub>Recommended: ON. Blocks adult websites, search keywords, and blacklisted apps.</Sub>
            </View>
            <Switch value={enableProtection} onValueChange={setEnableProtection} trackColor={{ true: t.ok }} />
          </View>
        </Card>

        {/* 2. Anti-Tamper & Uninstall Shield */}
        <Card style={{ borderColor: enableShield ? t.accent : t.cardBorder, borderWidth: 1.5 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <View style={{ flex: 1, paddingRight: 10 }}>
              <Title icon={Icons.lock} tone={enableShield ? 'ok' : 'accent'}>2. Uninstall & Settings Shield</Title>
              <Sub>Recommended: ON. Locks Developer options, Device Admin, and Settings to prevent bypass.</Sub>
            </View>
            <Switch value={enableShield} onValueChange={setEnableShield} trackColor={{ true: t.ok }} />
          </View>
        </Card>

        {/* 3. Background Unrestricted Battery */}
        <Card style={{ borderColor: batteryOk ? t.ok : t.border, borderWidth: 1.5 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <View style={{ flex: 1, paddingRight: 10 }}>
              <Title icon={Icons.clock} tone={batteryOk ? 'ok' : 'accent'}>3. Unrestricted Background</Title>
              <Sub>Recommended: Unrestricted. Prevents Android from killing Blocker in the background.</Sub>
            </View>
            {batteryOk ? (
              <Badge label="Unrestricted" tone="ok" />
            ) : (
              <Btn label="Allow" kind="primary" onPress={allowBackground} />
            )}
          </View>
        </Card>

        <View style={{ marginTop: 14 }}>
          <Btn
            label={loading ? 'Applying...' : 'Enable Recommended Defaults & Start'}
            disabled={loading}
            icon={Icons.play}
            onPress={finishSetup}
          />
        </View>
      </ScrollView>
    );
  }

  // Step 1: Required system permissions
  return (
    <ScrollView contentContainerStyle={{ padding: 20 }}>
      <Title size={26} icon={Icons.shieldOn}>Set up Blocker</Title>
      <Text style={{ color: t.sub, marginTop: 6, marginBottom: 20, lineHeight: 20 }}>
        Grant these four permissions. Status updates live when you return to the app.
      </Text>

      {STEPS.map((s, i) => {
        const ok = granted(s.kind);
        const current = next?.kind === s.kind;
        return (
          <Card key={s.kind} style={current ? { borderColor: t.accent, borderWidth: 1.5 } : undefined}>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
              <View style={{ flex: 1, paddingRight: 6 }}>
                <Title icon={s.icon} tone={ok ? 'ok' : 'accent'}>{i + 1}. {s.title}</Title>
              </View>
              <Badge label={ok ? 'Granted' : 'Missing'} tone={ok ? 'ok' : 'danger'} />
            </View>
            <Sub>{s.why}</Sub>
            {!ok && <Text style={{ color: t.sub, fontSize: 12, marginTop: 6 }}>{s.how}</Text>}
            {!ok && (
              <View style={{ marginTop: 12 }}>
                <Btn label="Grant" kind={current ? 'primary' : 'ghost'} icon={s.icon} onPress={() => grant(s.kind)} />
              </View>
            )}
          </Card>
        );
      })}

      <Btn
        label={all ? 'Next: Recommended Defaults' : 'Grant all permissions to continue'}
        disabled={!all}
        icon={Icons.chevronRight}
        onPress={() => setStep('defaults')}
      />
    </ScrollView>
  );
}
