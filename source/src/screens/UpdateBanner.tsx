import React, { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { ArrowDownToLine, ArrowRight, X } from 'lucide-react-native';
import { useAppUpdate } from '../hooks/useAppUpdate';
import { PressableScale, StatusPillBadge, useTheme } from '../ui';
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
      <PressableScale
        onPress={() => setModalVisible(true)}
        style={{ marginBottom: 14 }}>
        <View
          style={[
            styles.banner,
            {
              backgroundColor: t.bgAlt,
              borderColor: t.accentWellBorder,
              shadowColor: t.accent,
            },
          ]}>
          <View style={[styles.iconWrap, { backgroundColor: t.accentWell, borderColor: t.accent }]}>
            <ArrowDownToLine size={16} color="#A78BFA" strokeWidth={2.4} />
          </View>

          <View style={{ flex: 1, paddingRight: 4 }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
              <Text style={[styles.title, { color: '#FFFFFF' }]}>Update Available</Text>
              <StatusPillBadge label={rel.tagName} tone="accent" size="sm" icon={false} />
            </View>
            <Text style={[styles.subtitle, { color: t.sub }]} numberOfLines={1}>
              Tap to view release notes and install update
            </Text>
          </View>

          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
            <View style={[styles.actionBtn, { backgroundColor: t.accentWell, borderColor: t.accentWellBorder }]}>
              <ArrowRight size={14} color="#A78BFA" strokeWidth={2.5} />
            </View>
            <Pressable
              hitSlop={8}
              onPress={e => {
                e.stopPropagation();
                update.dismissBanner();
              }}
              style={styles.closeBtn}>
              <X size={16} color={t.sub} />
            </Pressable>
          </View>
        </View>
      </PressableScale>

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
    padding: 14,
    borderRadius: 18,
    borderWidth: 1,
    shadowOpacity: 0.35,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 2 },
    elevation: 4,
  },
  iconWrap: {
    width: 38,
    height: 38,
    borderRadius: 12,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    fontSize: 14,
    fontWeight: '800',
    letterSpacing: -0.2,
  },
  subtitle: {
    fontSize: 12,
    marginTop: 2,
  },
  actionBtn: {
    width: 30,
    height: 30,
    borderRadius: 10,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeBtn: {
    padding: 4,
    marginLeft: 2,
  },
});
