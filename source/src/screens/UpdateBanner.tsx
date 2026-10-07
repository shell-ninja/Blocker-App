import React, { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { ArrowRight, Sparkles, X } from 'lucide-react-native';
import { useAppUpdate } from '../hooks/useAppUpdate';
import { useTheme } from '../ui';
import UpdateModal from './UpdateModal';

export default function UpdateBanner() {
  const t = useTheme();
  const update = useAppUpdate();
  const [modalVisible, setModalVisible] = useState(false);

  if (!update.hasUpdate || update.dismissed || !update.release) {
    return (
      <UpdateModal
        visible={modalVisible}
        onClose={() => setModalVisible(false)}
      />
    );
  }

  const rel = update.release;

  return (
    <>
      <Pressable
        onPress={() => setModalVisible(true)}
        style={({ pressed }) => [
          styles.banner,
          {
            backgroundColor: t.card,
            borderColor: t.accent,
            shadowColor: t.accent,
            opacity: pressed ? 0.85 : 1,
          },
        ]}>
        <View style={[styles.iconWrap, { backgroundColor: t.accentSoft, borderColor: t.accent }]}>
          <Sparkles size={16} color={t.accent} strokeWidth={2.4} />
        </View>

        <View style={{ flex: 1, paddingRight: 4 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
            <Text style={[styles.title, { color: t.text }]}>Update Available</Text>
            <View style={[styles.versionPill, { backgroundColor: t.accent }]}>
              <Text style={styles.versionText}>{rel.tagName}</Text>
            </View>
          </View>
          <Text style={[styles.subtitle, { color: t.sub }]} numberOfLines={1}>
            Tap to view release notes and install update
          </Text>
        </View>

        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
          <View style={[styles.actionBtn, { backgroundColor: t.accentSoft }]}>
            <ArrowRight size={14} color={t.accent} strokeWidth={2.5} />
          </View>
          <Pressable
            hitSlop={8}
            onPress={(e) => {
              e.stopPropagation();
              update.dismissBanner();
            }}
            style={styles.closeBtn}>
            <X size={16} color={t.sub} />
          </Pressable>
        </View>
      </Pressable>

      <UpdateModal
        visible={modalVisible}
        onClose={() => setModalVisible(false)}
      />
    </>
  );
}

const styles = StyleSheet.create({
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    padding: 12,
    borderRadius: 16,
    borderWidth: 1.5,
    marginBottom: 14,
    shadowOpacity: 0.35,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 2 },
    elevation: 4,
  },
  iconWrap: {
    width: 34,
    height: 34,
    borderRadius: 10,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    fontSize: 13.5,
    fontWeight: '700',
  },
  versionPill: {
    paddingHorizontal: 6,
    paddingVertical: 1,
    borderRadius: 6,
  },
  versionText: {
    color: '#FFFFFF',
    fontSize: 10.5,
    fontWeight: '800',
  },
  subtitle: {
    fontSize: 11.5,
    marginTop: 2,
  },
  actionBtn: {
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeBtn: {
    padding: 4,
  },
});
