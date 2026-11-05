import React, { useMemo, useState } from 'react';
import { Pressable, ScrollView, Switch, Text, TextInput, View } from 'react-native';
import { CATEGORIES } from '../data/defaults';
import { useProtection } from '../hooks/useProtection';
import {
  addEntry,
  addWhitelist,
  buildEngine,
  removeEntry,
  removeWhitelist,
  setCategory,
} from '../services/ProtectionManager';
import {
  Btn,
  CyberSwitch,
  GlowCard,
  Icons,
  LockBar,
  PressableScale,
  StatusPillBadge,
  Sub,
  Title,
  errMsg,
  showAlert,
  useTheme,
} from '../ui';

export default function BlocklistScreen() {
  const t = useTheme();
  const p = useProtection();
  const [input, setInput] = useState('');
  const [wlInput, setWlInput] = useState('');
  const [test, setTest] = useState('');
  const s = p.state;

  const pending = useMemo(
    () => new Set((s.queue.remove_blocklist ?? []).map(a => `${a.t}:${a.v}`)),
    [s.queue]
  );
  const wlPending = useMemo(() => new Set((s.queue.whitelist ?? []).map(a => a.v)), [s.queue]);
  const testResult = useMemo(() => (test.trim() ? buildEngine(s).matchUrl(test) : null), [test, s]);

  const notify = (r: string, what: string) => {
    if (r !== 'applied') {
      showAlert(
        r === 'restarted' ? 'Timer restarted' : 'Change requested',
        `${what} stays in place until the ${p.delayDays}-day timer completes and you confirm.`
      );
    }
  };
  const guard = (fn: () => Promise<void>) => fn().catch(e => showAlert('Blocker', errMsg(e)));

  const add = () =>
    guard(async () => {
      if (await addEntry(input)) setInput('');
    });
  const addWl = () =>
    guard(async () => {
      const text = wlInput.trim();
      if (!text) return;
      const r = await addWhitelist(text);
      setWlInput('');
      if (r !== 'applied') {
        showAlert(
          'Change requested',
          `"${text}" starts protecting matches only after the ${p.delayDays}-day timer completes and you confirm.`
        );
      }
    });

  const custom = [
    ...s.customDomains.map(v => ({ kind: 'domain' as const, v })),
    ...s.customKeywords.map(v => ({ kind: 'keyword' as const, v })),
  ];

  const inputStyle = {
    backgroundColor: t.bg,
    color: '#FFFFFF',
    borderColor: t.cardBorder,
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 10,
    fontSize: 13.5,
  } as const;

  return (
    <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 28 }} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
      <Title icon={Icons.web}>Web & keywords</Title>
      <View style={{ height: 12 }} />
      <LockBar lockKey="remove_blocklist" />

      {CATEGORIES.filter(c => c.domains.length + c.keywords.length > 0).map(c => {
        const on = s.categories[c.id] ?? c.defaultOn;
        const off = pending.has(`cat_off:${c.id}`);
        return (
          <GlowCard key={c.id} style={{ padding: 18 }}>
            <View style={{ flexDirection: 'row', alignItems: 'center' }}>
              <View style={{ flex: 1, paddingRight: 12 }}>
                <Title icon={Icons.shield}>{c.title}</Title>
                <Sub>{c.description}</Sub>
              </View>
              {off && <StatusPillBadge label="Off pending" tone="warn" size="sm" />}
              <CyberSwitch
                value={on}
                onValueChange={v => guard(async () => notify(await setCategory(c.id, v), c.title))}
              />
            </View>
          </GlowCard>
        );
      })}

      <GlowCard style={{ padding: 18 }}>
        <Title icon={Icons.plus}>Add domain or keyword</Title>
        <Sub>example.com blocks the site and subdomains; anything else blocks that whole word wherever it appears.</Sub>
        <View style={{ flexDirection: 'row', gap: 8, marginTop: 12 }}>
          <TextInput
            value={input}
            onChangeText={setInput}
            onSubmitEditing={add}
            autoCapitalize="none"
            autoCorrect={false}
            placeholder="domain or keyword"
            placeholderTextColor={t.sub}
            style={[inputStyle, { flex: 1 }]}
          />
          <Btn label="Add" icon={Icons.plus} kind="primary" onPress={add} disabled={!input.trim()} />
        </View>
        <View style={{ marginTop: 8 }}>
          {custom.map(e => {
            const isPending = pending.has(`${e.kind === 'domain' ? 'rm_domain' : 'rm_keyword'}:${e.v}`);
            return (
              <View
                key={`${e.kind}:${e.v}`}
                style={{
                  flexDirection: 'row',
                  alignItems: 'center',
                  paddingVertical: 9,
                  gap: 10,
                  borderBottomWidth: 1,
                  borderBottomColor: t.cardBorder,
                }}>
                <StatusPillBadge label={e.kind} tone="idle" icon={false} size="sm" />
                <Text style={{ color: '#FFFFFF', flex: 1, fontSize: 13.5, fontWeight: '500' }} numberOfLines={1}>
                  {e.v}
                </Text>
                {isPending ? (
                  <StatusPillBadge label="Removal pending" tone="warn" size="sm" />
                ) : (
                  <PressableScale
                    onPress={() => guard(async () => notify(await removeEntry(e.kind, e.v), e.v))}
                    hitSlop={10}>
                    <Icons.trash size={16} color={t.danger} />
                  </PressableScale>
                )}
              </View>
            );
          })}
        </View>
      </GlowCard>

      <GlowCard style={{ padding: 18 }}>
        <Title icon={Icons.key}>Whitelist</Title>
        <Sub>
          A word or phrase here is never blocked, even if it contains a blocked keyword — e.g. whitelisting
          "sex education" stops "sex" from tripping inside it. Adding one needs the delay timer; removing one is instant.
        </Sub>
        <View style={{ flexDirection: 'row', gap: 8, marginTop: 12 }}>
          <TextInput
            value={wlInput}
            onChangeText={setWlInput}
            onSubmitEditing={addWl}
            autoCapitalize="none"
            autoCorrect={false}
            placeholder="e.g. sex education"
            placeholderTextColor={t.sub}
            style={[inputStyle, { flex: 1 }]}
          />
          <Btn label="Add" icon={Icons.plus} kind="primary" onPress={addWl} disabled={!wlInput.trim()} />
        </View>
        <View style={{ marginTop: 8 }}>
          {s.whitelist.map(w => (
            <View
              key={w}
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                paddingVertical: 9,
                gap: 10,
                borderBottomWidth: 1,
                borderBottomColor: t.cardBorder,
              }}>
              <Icons.key size={15} color={t.accent} />
              <Text style={{ color: '#FFFFFF', flex: 1, fontSize: 13.5, fontWeight: '500' }} numberOfLines={1}>
                {w}
              </Text>
              <PressableScale onPress={() => guard(() => removeWhitelist(w))} hitSlop={10}>
                <Icons.trash size={16} color={t.danger} />
              </PressableScale>
            </View>
          ))}
          {[...wlPending].map(w => (
            <View
              key={`pending-${w}`}
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                paddingVertical: 9,
                gap: 10,
                borderBottomWidth: 1,
                borderBottomColor: t.cardBorder,
              }}>
              <Icons.key size={15} color={t.warn} />
              <Text style={{ color: '#FFFFFF', flex: 1, fontSize: 13.5, fontWeight: '500' }} numberOfLines={1}>
                {w}
              </Text>
              <StatusPillBadge label="Pending" tone="warn" size="sm" />
            </View>
          ))}
        </View>
        <LockBar lockKey="whitelist" />
      </GlowCard>

      <GlowCard style={{ padding: 18 }}>
        <Title icon={Icons.search}>Test a URL or word</Title>
        <TextInput
          value={test}
          onChangeText={setTest}
          autoCapitalize="none"
          autoCorrect={false}
          placeholder="https://example.com/page"
          placeholderTextColor={t.sub}
          style={[inputStyle, { marginTop: 12 }]}
        />
        {testResult && (
          <View style={{ marginTop: 12, flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            <StatusPillBadge
              label={testResult.blocked ? 'Blocked' : 'Allowed'}
              tone={testResult.blocked ? 'danger' : 'ok'}
            />
            {testResult.blocked && (
              <Text style={{ color: t.sub, fontSize: 13, flex: 1 }} numberOfLines={1}>
                {testResult.reason}: {testResult.rule}
              </Text>
            )}
          </View>
        )}
      </GlowCard>
    </ScrollView>
  );
}
