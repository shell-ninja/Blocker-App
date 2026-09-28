import React, { useEffect } from 'react';
import { AppState, ScrollView, Text, View } from 'react-native';
import { LucideIcon } from 'lucide-react-native';
import { PermissionKind, Native } from '../native/BlockerNative';
import { useProtection } from '../hooks/useProtection';
import { markOnboarded, refresh } from '../services/ProtectionManager';
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
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
              <Title icon={s.icon} tone={ok ? 'ok' : 'accent'}>{i + 1}. {s.title}</Title>
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
        label={all ? 'Continue' : 'Grant all permissions to continue'}
        disabled={!all}
        icon={Icons.play}
        onPress={() => {
          markOnboarded();
          onDone();
        }}
      />
    </ScrollView>
  );
}
