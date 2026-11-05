import React, { useEffect, useState } from 'react';
import { AppState, Pressable, ScrollView, Text, View } from 'react-native';
import { LucideIcon } from 'lucide-react-native';
import { PermissionKind, Native } from '../native/BlockerNative';
import { useProtection } from '../hooks/useProtection';
import {
  getPopupLanguage,
  markOnboarded,
  refresh,
  setPopupLanguage,
  setProtection,
  setShield,
} from '../services/ProtectionManager';
import {
  Btn,
  CyberSwitch,
  GlowCard,
  Icons,
  PressableScale,
  StatusPillBadge,
  Sub,
  Title,
  errMsg,
  showAlert,
  useTheme,
} from '../ui';

const STEPS: { kind: PermissionKind; title: string; icon: LucideIcon; why: string; how: string }[] = [
  {
    kind: 'accessibility',
    title: 'Accessibility service',
    icon: Icons.eye,
    why: 'Reads the browser address bar and detects blocked apps.',
    how: 'Installed apps → Blocker → turn on.',
  },
  {
    kind: 'deviceAdmin',
    title: 'Device admin',
    icon: Icons.shieldOn,
    why: 'Prevents Blocker from being uninstalled.',
    how: 'Tap Activate this device admin app.',
  },
  {
    kind: 'usageAccess',
    title: 'Usage access',
    icon: Icons.apps,
    why: 'Lets Blocker see which app is in the foreground.',
    how: 'Select Blocker and allow usage access.',
  },
  {
    kind: 'overlay',
    title: 'Display over other apps',
    icon: Icons.layers,
    why: 'Shows the blocking screen on top of blocked content.',
    how: 'Allow display over other apps for Blocker.',
  },
];

export default function OnboardingPermissions({ onDone }: { onDone: () => void }) {
  const t = useTheme();
  const { perms } = useProtection();
  const [step, setStep] = useState<'language' | 'permissions' | 'defaults'>('language');
  const [selectedLang, setSelectedLang] = useState<'en' | 'bn'>('en');
  const [enableProtection, setEnableProtection] = useState(true);
  const [enableShield, setEnableShield] = useState(true);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    getPopupLanguage()
      .then(l => {
        if (l === 'bn' || l === 'en') setSelectedLang(l);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    const i = setInterval(() => refresh().catch(() => {}), 1500);
    const sub = AppState.addEventListener('change', s => s === 'active' && refresh().catch(() => {}));
    return () => {
      clearInterval(i);
      sub.remove();
    };
  }, []);

  const handleSelectLanguage = async (lang: 'en' | 'bn') => {
    setSelectedLang(lang);
    try {
      await setPopupLanguage(lang);
    } catch {}
  };

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

  // Step 0: Language Selection
  if (step === 'language') {
    return (
      <ScrollView contentContainerStyle={{ padding: 20, paddingBottom: 40 }} showsVerticalScrollIndicator={false}>
        <Title size={24} icon={Icons.shieldOn}>Popup Message Language</Title>
        <Text style={{ color: t.sub, marginTop: 8, marginBottom: 20, lineHeight: 21, fontSize: 13.5 }}>
          Choose your preferred language for the blocker warning popups, Quranic reminders, and Hadiths.
          {'\n'}(The main application settings and navigation remain in English.)
        </Text>

        {/* Option 1: English */}
        <PressableScale onPress={() => handleSelectLanguage('en')} style={{ marginBottom: 14 }}>
          <GlowCard
            tone={selectedLang === 'en' ? 'accent' : 'default'}
            style={{
              padding: 18,
              borderColor: selectedLang === 'en' ? t.accentWellBorder : t.cardBorder,
              borderWidth: 1.5,
              backgroundColor: selectedLang === 'en' ? t.accentWell : t.card,
            }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                <View
                  style={{
                    width: 22,
                    height: 22,
                    borderRadius: 11,
                    borderWidth: 2,
                    borderColor: selectedLang === 'en' ? t.accent : t.sub,
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}>
                  {selectedLang === 'en' && (
                    <View style={{ width: 12, height: 12, borderRadius: 6, backgroundColor: t.accent }} />
                  )}
                </View>
                <Title size={18}>English</Title>
              </View>
              {selectedLang === 'en' && <StatusPillBadge label="Selected" tone="ok" size="sm" />}
            </View>

            <Sub>Displays verses & Hadiths in English:</Sub>
            <View
              style={{
                marginTop: 10,
                padding: 12,
                borderRadius: 12,
                backgroundColor: 'rgba(0,0,0,0.3)',
                borderLeftWidth: 3,
                borderLeftColor: selectedLang === 'en' ? t.accent : t.border,
              }}>
              <Text style={{ color: '#F3F0FF', fontWeight: '700', fontSize: 13, marginBottom: 3 }}>
                🛡️ Fear Allah
              </Text>
              <Text style={{ color: t.sub, fontSize: 12, fontStyle: 'italic', lineHeight: 18 }}>
                "Does he not know that Allah sees?" (Quran 96:14)
              </Text>
            </View>
          </GlowCard>
        </PressableScale>

        {/* Option 2: Bangla */}
        <PressableScale onPress={() => handleSelectLanguage('bn')} style={{ marginBottom: 20 }}>
          <GlowCard
            tone={selectedLang === 'bn' ? 'accent' : 'default'}
            style={{
              padding: 18,
              borderColor: selectedLang === 'bn' ? t.accentWellBorder : t.cardBorder,
              borderWidth: 1.5,
              backgroundColor: selectedLang === 'bn' ? t.accentWell : t.card,
            }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                <View
                  style={{
                    width: 22,
                    height: 22,
                    borderRadius: 11,
                    borderWidth: 2,
                    borderColor: selectedLang === 'bn' ? t.accent : t.sub,
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}>
                  {selectedLang === 'bn' && (
                    <View style={{ width: 12, height: 12, borderRadius: 6, backgroundColor: t.accent }} />
                  )}
                </View>
                <Title size={18}>বাংলা (Bangla)</Title>
              </View>
              {selectedLang === 'bn' && <StatusPillBadge label="Selected" tone="ok" size="sm" />}
            </View>

            <Sub>Displays verses & Hadiths in Bengali:</Sub>
            <View
              style={{
                marginTop: 10,
                padding: 12,
                borderRadius: 12,
                backgroundColor: 'rgba(0,0,0,0.3)',
                borderLeftWidth: 3,
                borderLeftColor: selectedLang === 'bn' ? t.accent : t.border,
              }}>
              <Text style={{ color: '#F3F0FF', fontWeight: '700', fontSize: 13, marginBottom: 3 }}>
                🛡️ আল্লাহকে ভয় করুন
              </Text>
              <Text style={{ color: t.sub, fontSize: 12, fontStyle: 'italic', lineHeight: 18 }}>
                "তিনি কি জানেন না যে, নিশ্চয়ই আল্লাহ সবকিছু দেখেন?" (সূরা আল-আলাক: ১৪)
              </Text>
            </View>
          </GlowCard>
        </PressableScale>

        <Btn
          label="Continue to Permissions"
          icon={Icons.chevronRight}
          kind="primary"
          onPress={() => setStep('permissions')}
        />
      </ScrollView>
    );
  }

  // Step 1: Permissions
  if (step === 'permissions') {
    return (
      <ScrollView contentContainerStyle={{ padding: 20, paddingBottom: 40 }} showsVerticalScrollIndicator={false}>
        <Title size={24} icon={Icons.shieldOn}>Required Permissions</Title>
        <Text style={{ color: t.sub, marginTop: 8, marginBottom: 18, lineHeight: 21, fontSize: 13.5 }}>
          To enforce airtight uninstallation protection and instant content blocking, Blocker requires standard Android permissions.
        </Text>

        {STEPS.map(s => {
          const ok = granted(s.kind);
          const Ico = s.icon;
          return (
            <GlowCard key={s.kind} style={{ padding: 18, marginBottom: 12 }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, flex: 1, paddingRight: 8 }}>
                  <Ico size={19} color={ok ? t.okGlow : t.accent} strokeWidth={2.4} />
                  <Text style={{ color: '#FFFFFF', fontSize: 16, fontWeight: '700' }} numberOfLines={1}>
                    {s.title}
                  </Text>
                </View>
                <StatusPillBadge label={ok ? 'Granted' : 'Missing'} tone={ok ? 'ok' : 'warn'} size="sm" />
              </View>
              <Sub>{s.why}</Sub>
              {!ok && (
                <View style={{ marginTop: 12 }}>
                  <Btn label={`Grant ${s.title}`} kind="primary" onPress={() => grant(s.kind)} />
                </View>
              )}
            </GlowCard>
          );
        })}

        {/* Unrestricted Battery */}
        <GlowCard style={{ padding: 18, marginTop: 4, marginBottom: 18 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <View style={{ flex: 1, paddingRight: 8 }}>
              <Title icon={Icons.clock} size={16}>Unrestricted Background</Title>
              <Sub>Recommended: prevents Android from killing the background watchdog.</Sub>
            </View>
            {perms?.battery ? (
              <StatusPillBadge label="Active" tone="ok" size="sm" />
            ) : (
              <Btn label="Allow" kind="ghost" onPress={allowBackground} />
            )}
          </View>
        </GlowCard>

        <Btn
          label={all ? 'Continue to Default Setup' : `Next: Grant ${next?.title ?? 'Permission'}`}
          icon={Icons.chevronRight}
          kind={all ? 'primary' : 'ghost'}
          onPress={() => {
            if (all) setStep('defaults');
            else if (next) grant(next.kind);
          }}
        />
      </ScrollView>
    );
  }

  // Step 2: Defaults Confirmation
  return (
    <ScrollView contentContainerStyle={{ padding: 20, paddingBottom: 40 }} showsVerticalScrollIndicator={false}>
      <Title size={24} icon={Icons.shieldOn}>Finalize Security</Title>
      <Text style={{ color: t.sub, marginTop: 8, marginBottom: 20, lineHeight: 21, fontSize: 13.5 }}>
        Enable Blocker's foundational protection guards. You can customize additional blocklists and timers after onboarding.
      </Text>

      <GlowCard style={{ padding: 18, marginBottom: 12 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <View style={{ flex: 1, paddingRight: 10 }}>
            <Title icon={Icons.shieldOn}>Master Protection</Title>
            <Sub>Enforces adult domain blocklists, distraction keywords & browser filtering.</Sub>
          </View>
          <CyberSwitch value={enableProtection} onValueChange={setEnableProtection} />
        </View>
      </GlowCard>

      <GlowCard style={{ padding: 18, marginBottom: 22 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
          <View style={{ flex: 1, paddingRight: 10 }}>
            <Title icon={Icons.lock}>Anti-Tamper & Uninstall Guard</Title>
            <Sub>Protects Settings, launcher shortcuts, and device admin deactivation.</Sub>
          </View>
          <CyberSwitch value={enableShield} onValueChange={setEnableShield} />
        </View>
      </GlowCard>

      <Btn
        label={loading ? 'Activating Security…' : 'Enter Blocker'}
        icon={Icons.shieldOn}
        kind="primary"
        disabled={loading}
        onPress={finishSetup}
      />
    </ScrollView>
  );
}
