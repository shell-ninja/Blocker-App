import React, { useEffect, useState } from 'react';
import { AppState, Pressable, ScrollView, Switch, Text, View } from 'react-native';
import { LucideIcon } from 'lucide-react-native';
import { PermissionKind, Native } from '../native/BlockerNative';
import { useProtection } from '../hooks/useProtection';
import { getPopupLanguage, markOnboarded, refresh, setPopupLanguage, setProtection, setShield } from '../services/ProtectionManager';
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
    icon: Icons.layers,
    why: 'Shows the blocking screen on top of blocked content.',
    how: 'Allow display over other apps for Blocker.'
  }
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
    getPopupLanguage().then(l => {
      if (l === 'bn' || l === 'en') setSelectedLang(l);
    }).catch(() => {});
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

  // Step 0: Initial Language Selection for Blocker Popups
  if (step === 'language') {
    return (
      <ScrollView contentContainerStyle={{ padding: 20 }}>
        <Title size={26} icon={Icons.shieldOn}>Popup Message Language</Title>
        <Text style={{ color: t.sub, marginTop: 6, marginBottom: 20, lineHeight: 21 }}>
          Choose your preferred language for the blocker warning popups, Quranic reminders, and Hadiths.
          {'\n'}(The main application settings and navigation remain in English.)
        </Text>

        {/* Option 1: English */}
        <Pressable
          onPress={() => handleSelectLanguage('en')}
          style={{ marginBottom: 14 }}>
          <Card
            style={{
              borderColor: selectedLang === 'en' ? t.accent : t.cardBorder,
              borderWidth: selectedLang === 'en' ? 2 : 1,
              backgroundColor: selectedLang === 'en' ? t.accentSoft : t.card
            }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                <View
                  style={{
                    width: 22,
                    height: 22,
                    borderRadius: 11,
                    borderWidth: 2,
                    borderColor: selectedLang === 'en' ? t.accent : t.sub,
                    alignItems: 'center',
                    justifyContent: 'center'
                  }}>
                  {selectedLang === 'en' && (
                    <View style={{ width: 12, height: 12, borderRadius: 6, backgroundColor: t.accent }} />
                  )}
                </View>
                <Title size={18}>English</Title>
              </View>
              {selectedLang === 'en' && <Badge label="Selected" tone="ok" />}
            </View>

            <Sub>Displays verses & Hadiths in English:</Sub>
            <View
              style={{
                marginTop: 10,
                padding: 12,
                borderRadius: 10,
                backgroundColor: 'rgba(0,0,0,0.25)',
                borderLeftWidth: 3,
                borderLeftColor: selectedLang === 'en' ? t.accent : t.border
              }}>
              <Text style={{ color: '#F3F0FF', fontWeight: '700', fontSize: 13, marginBottom: 3 }}>
                🛡️ Fear Allah
              </Text>
              <Text style={{ color: t.sub, fontSize: 12, fontStyle: 'italic', lineHeight: 17 }}>
                “Does he not know that Allah sees?”
                {'\n'}— Surah Al-Alaq (96:14)
              </Text>
              <View style={{ marginTop: 6, alignSelf: 'flex-start' }}>
                <Text style={{ color: t.accent, fontSize: 11, fontWeight: '700' }}>[ Astaghfirullah ]</Text>
              </View>
            </View>
          </Card>
        </Pressable>

        {/* Option 2: বাংলা (Bangla) */}
        <Pressable
          onPress={() => handleSelectLanguage('bn')}
          style={{ marginBottom: 20 }}>
          <Card
            style={{
              borderColor: selectedLang === 'bn' ? t.accent : t.cardBorder,
              borderWidth: selectedLang === 'bn' ? 2 : 1,
              backgroundColor: selectedLang === 'bn' ? t.accentSoft : t.card
            }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                <View
                  style={{
                    width: 22,
                    height: 22,
                    borderRadius: 11,
                    borderWidth: 2,
                    borderColor: selectedLang === 'bn' ? t.accent : t.sub,
                    alignItems: 'center',
                    justifyContent: 'center'
                  }}>
                  {selectedLang === 'bn' && (
                    <View style={{ width: 12, height: 12, borderRadius: 6, backgroundColor: t.accent }} />
                  )}
                </View>
                <Title size={18}>বাংলা (Bangla)</Title>
              </View>
              {selectedLang === 'bn' && <Badge label="নির্বাচিত" tone="ok" />}
            </View>

            <Sub>পপআপে পবিত্র কুরআনের আয়াত ও হাদিস বাংলায় প্রদর্শিত হবে:</Sub>
            <View
              style={{
                marginTop: 10,
                padding: 12,
                borderRadius: 10,
                backgroundColor: 'rgba(0,0,0,0.25)',
                borderLeftWidth: 3,
                borderLeftColor: selectedLang === 'bn' ? t.accent : t.border
              }}>
              <Text style={{ color: '#F3F0FF', fontWeight: '700', fontSize: 13, marginBottom: 3 }}>
                🛡️ আল্লাহকে ভয় করুন
              </Text>
              <Text style={{ color: t.sub, fontSize: 12, lineHeight: 18 }}>
                “সে কি জানে না যে, নিশ্চয়ই আল্লাহ সবকিছু দেখেন?”
                {'\n'}— সূরা আল-আলাক (৯৬:১৪)
              </Text>
              <View style={{ marginTop: 6, alignSelf: 'flex-start' }}>
                <Text style={{ color: t.accent, fontSize: 11, fontWeight: '700' }}>[ আস্তাগফিরুল্লাহ ]</Text>
              </View>
            </View>
          </Card>
        </Pressable>

        <Text style={{ color: t.sub, fontSize: 12, textAlign: 'center', marginBottom: 14 }}>
          💡 You can change this language setting at any time from Settings.
        </Text>

        <Btn
          label={selectedLang === 'bn' ? 'এগিয়ে যান / Continue to Setup' : 'Continue to Setup'}
          icon={Icons.chevronRight}
          onPress={() => setStep('permissions')}
        />
      </ScrollView>
    );
  }

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
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
        <Title size={26} icon={Icons.shieldOn}>Set up Blocker</Title>
        <Pressable
          onPress={() => setStep('language')}
          style={{
            paddingHorizontal: 10,
            paddingVertical: 5,
            borderRadius: 8,
            backgroundColor: t.accentSoft,
            borderWidth: 1,
            borderColor: t.accent
          }}>
          <Text style={{ color: t.accent, fontSize: 12, fontWeight: '700' }}>
            {selectedLang === 'bn' ? 'ভাষা: বাংলা' : 'Lang: English'}
          </Text>
        </Pressable>
      </View>
      <Text style={{ color: t.sub, marginTop: 4, marginBottom: 20, lineHeight: 20 }}>
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
