import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Linking,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  AlertCircle,
  ArrowDownToLine,
  CheckCircle2,
  ExternalLink,
  Info,
  Package,
  RotateCw,
  Trash2,
  X,
} from 'lucide-react-native';
import { useAppUpdate } from '../hooks/useAppUpdate';
import { APP_VERSION } from '../data/appVersion';
import { compareSemVer } from '../services/UpdateService';
import {
  Btn,
  GlowCard,
  PressableScale,
  StatusPillBadge,
  errMsg,
  showAlert,
  useTheme,
} from '../ui';

interface UpdateModalProps {
  visible: boolean;
  onClose: () => void;
}

export default function UpdateModal({ visible, onClose }: UpdateModalProps) {
  const t = useTheme();
  const update = useAppUpdate();
  const [canInstall, setCanInstall] = useState<boolean>(true);

  useEffect(() => {
    if (visible) {
      update.checkCanInstall().then(setCanInstall).catch(() => setCanInstall(true));
    }
  }, [visible]);

  if (!visible || !update.release) return null;

  const rel = update.release;
  const isNewer = update.hasUpdate;
  const isAhead = rel ? compareSemVer(APP_VERSION, rel.version) > 0 : false;
  const sizeMb = rel.sizeBytes > 0 ? (rel.sizeBytes / (1024 * 1024)).toFixed(1) : null;
  const downloadedMb = (update.downloadedBytes / (1024 * 1024)).toFixed(1);
  const totalMb = update.totalBytes > 0 ? (update.totalBytes / (1024 * 1024)).toFixed(1) : sizeMb;
  const progressPercent = Math.round(update.downloadProgress * 100);

  const handleUpdate = async () => {
    try {
      if (!canInstall) {
        showAlert(
          'Permission Required',
          'Android requires permission to install apps from Blocker. Tap "Allow from this source" in the next screen, then return here to install.',
          [
            { text: 'Cancel', style: 'cancel' },
            {
              text: 'Open Settings',
              onPress: async () => {
                await update.openInstallSettings();
                setTimeout(() => {
                  update.checkCanInstall().then(setCanInstall).catch(() => {});
                }, 1000);
              },
            },
          ]
        );
        return;
      }

      if (update.isDownloaded) {
        await update.installDownloaded();
      } else {
        await update.startDownloadAndInstall();
      }
    } catch (e) {
      showAlert('Update Failed', errMsg(e));
    }
  };

  const handleClearCache = async () => {
    try {
      const res = await update.clearUpdateCache();
      const freedMb = (res.freedBytes / (1024 * 1024)).toFixed(1);
      showAlert(
        'Cache Cleared',
        res.deletedCount > 0
          ? `Removed ${res.deletedCount} update file(s) and freed ${freedMb} MB. You can now download afresh.`
          : 'Update download cache is already clean.'
      );
    } catch (e) {
      showAlert('Clear Failed', errMsg(e));
    }
  };

  // Simple Markdown cleaner and segmenter for GitHub release bodies
  const parseMarkdownSections = (markdown: string) => {
    const lines = markdown.split('\n');
    const sections: { title?: string; items: string[]; type: 'header' | 'item' | 'text' }[] = [];
    let currentHeader = '';
    let currentItems: string[] = [];

    const flush = () => {
      if (currentHeader || currentItems.length > 0) {
        sections.push({
          title: currentHeader,
          items: [...currentItems],
          type: currentHeader ? 'header' : 'text',
        });
        currentHeader = '';
        currentItems = [];
      }
    };

    for (const rawLine of lines) {
      const line = rawLine.trim();
      if (!line) continue;

      if (line.startsWith('#')) {
        flush();
        currentHeader = line.replace(/^#+\s*/, '').replace(/[*_]/g, '');
      } else if (line.startsWith('- ') || line.startsWith('* ')) {
        const itemText = line.replace(/^[-*]\s*/, '').replace(/\*\*(.*?)\*\*/g, '$1');
        currentItems.push(itemText);
      } else {
        if (currentItems.length > 0) {
          flush();
        }
        sections.push({
          items: [line.replace(/\*\*(.*?)\*\*/g, '$1')],
          type: 'text',
        });
      }
    }
    flush();
    return sections;
  };

  const sections = parseMarkdownSections(rel.body);

  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <View style={[styles.container, { backgroundColor: t.bg, borderColor: t.cardBorder }]}>
          {/* Header Bar */}
          <View style={[styles.header, { borderBottomColor: t.cardBorder }]}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, flex: 1 }}>
              <View
                style={[
                  styles.iconBox,
                  { backgroundColor: t.accentWell, borderColor: t.accentWellBorder },
                ]}>
                <ArrowDownToLine size={18} color="#A78BFA" strokeWidth={2.4} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={[styles.title, { color: '#FFFFFF' }]}>
                  {isNewer ? 'New Update Available' : isAhead ? 'Public Release Notes' : 'Release Notes'}
                </Text>
                <Text style={{ color: t.sub, fontSize: 12, marginTop: 2 }}>
                  {rel.tagName} • Installed: v{APP_VERSION}
                </Text>
              </View>
            </View>

            <PressableScale onPress={onClose} hitSlop={12} style={styles.closeBtn}>
              <X size={20} color={t.sub} />
            </PressableScale>
          </View>

          {/* Body Content */}
          <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
            {/* Version Meta Card */}
            <GlowCard style={{ padding: 16 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                  <Package size={17} color={t.accent} />
                  <Text style={{ color: '#FFFFFF', fontWeight: '700', fontSize: 15 }}>
                    {rel.name || rel.tagName}
                  </Text>
                </View>
                <StatusPillBadge
                  label={isNewer ? 'Update Available' : isAhead ? 'Public Release' : 'Current Version'}
                  tone={isNewer ? 'warn' : isAhead ? 'idle' : 'ok'}
                  size="sm"
                />
              </View>

              <View style={{ flexDirection: 'row', gap: 16, marginTop: 10 }}>
                {sizeMb && (
                  <Text style={{ color: t.sub, fontSize: 12 }}>
                    Size: <Text style={{ color: '#FFFFFF', fontWeight: '600' }}>{sizeMb} MB</Text>
                  </Text>
                )}
                {rel.publishedAt && (
                  <Text style={{ color: t.sub, fontSize: 12 }}>
                    Released:{' '}
                    <Text style={{ color: '#FFFFFF', fontWeight: '600' }}>
                      {rel.publishedAt.split('T')[0]}
                    </Text>
                  </Text>
                )}
              </View>
            </GlowCard>

            {/* Safety & Settings Persistence Notice */}
            <View
              style={[
                styles.noticeBox,
                { backgroundColor: t.accentWell, borderColor: t.accentWellBorder },
              ]}>
              <Info size={16} color="#A78BFA" />
              <Text style={{ color: '#FFFFFF', fontSize: 12.5, flex: 1, lineHeight: 18 }}>
                Updating preserves all your active protections, app timers, custom blocklists, and settings.
              </Text>
            </View>

            {/* Permission Banner if needed */}
            {!canInstall && (
              <PressableScale
                onPress={update.openInstallSettings}
                style={[
                  styles.noticeBox,
                  { backgroundColor: t.dangerSoft, borderColor: t.dangerBorder },
                ]}>
                <AlertCircle size={16} color={t.danger} />
                <View style={{ flex: 1 }}>
                  <Text style={{ color: t.danger, fontWeight: '700', fontSize: 12.5 }}>
                    Install Unknown Apps Permission Needed
                  </Text>
                  <Text style={{ color: t.sub, fontSize: 11.5, marginTop: 2 }}>
                    Tap here to allow Blocker to install APK updates directly.
                  </Text>
                </View>
              </PressableScale>
            )}

            {/* Error Notice if any */}
            {update.error && (
              <View
                style={[
                  styles.noticeBox,
                  { backgroundColor: t.dangerSoft, borderColor: t.dangerBorder },
                ]}>
                <AlertCircle size={16} color={t.danger} />
                <View style={{ flex: 1 }}>
                  <Text style={{ color: t.danger, fontWeight: '700', fontSize: 12.5 }}>Update Notice</Text>
                  <Text style={{ color: t.sub, fontSize: 11.5, marginTop: 2 }}>{update.error}</Text>
                </View>
              </View>
            )}

            {/* Release Notes Changelog */}
            <Text style={[styles.sectionHeading, { color: '#FFFFFF' }]}>What's New & Bug Fixes</Text>

            <GlowCard style={{ padding: 18 }}>
              {sections.length === 0 ? (
                <Text style={{ color: t.sub, fontSize: 13, fontStyle: 'italic' }}>
                  No changelog description provided.
                </Text>
              ) : (
                sections.map((sec, idx) => (
                  <View key={idx} style={{ marginBottom: 14 }}>
                    {sec.title && (
                      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginBottom: 8 }}>
                        <View style={{ width: 4, height: 14, backgroundColor: t.accent, borderRadius: 2 }} />
                        <Text style={{ color: '#A78BFA', fontWeight: '700', fontSize: 13.5 }}>
                          {sec.title}
                        </Text>
                      </View>
                    )}
                    {sec.items.map((item, itemIdx) => (
                      <View key={itemIdx} style={{ flexDirection: 'row', gap: 8, marginTop: 4 }}>
                        <Text style={{ color: t.accent, fontSize: 14, lineHeight: 19 }}>•</Text>
                        <Text style={{ color: t.sub, fontSize: 12.5, lineHeight: 18, flex: 1 }}>
                          {item}
                        </Text>
                      </View>
                    ))}
                  </View>
                ))
              )}
            </GlowCard>
          </ScrollView>

          {/* Footer Actions & Download Progress */}
          <View style={[styles.footer, { borderTopColor: t.cardBorder, backgroundColor: t.bgAlt }]}>
            {update.downloading ? (
              <View style={{ width: '100%', gap: 10 }}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Text style={{ color: '#FFFFFF', fontWeight: '600', fontSize: 13 }}>
                    Downloading update... {progressPercent}%
                  </Text>
                  <Text style={{ color: t.sub, fontSize: 12 }}>
                    {downloadedMb} / {totalMb} MB
                  </Text>
                </View>

                {/* Progress Bar with neon glow */}
                <View style={[styles.progressTrack, { backgroundColor: t.cardBorder }]}>
                  <View
                    style={[
                      styles.progressBar,
                      {
                        width: `${Math.max(4, progressPercent)}%`,
                        backgroundColor: t.accent,
                        shadowColor: t.accent,
                        shadowOpacity: 0.8,
                        shadowRadius: 8,
                      },
                    ]}
                  />
                </View>
              </View>
            ) : update.isDownloaded ? (
              <View style={{ width: '100%', gap: 10 }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                  <CheckCircle2 size={16} color={t.ok} />
                  <Text style={{ color: t.ok, fontSize: 13, fontWeight: '600' }}>
                    Download complete. Ready to install!
                  </Text>
                </View>

                <Btn
                  label="Install Update Now"
                  kind="emerald"
                  icon={Package}
                  onPress={handleUpdate}
                />

                <Btn
                  label="Clear Downloaded APK Cache"
                  kind="ghost"
                  icon={Trash2}
                  onPress={handleClearCache}
                />
              </View>
            ) : (
              <View style={{ width: '100%', gap: 10 }}>
                {isNewer ? (
                  <Btn
                    label="Download & Install Update"
                    kind="primary"
                    icon={ArrowDownToLine}
                    onPress={handleUpdate}
                  />
                ) : (
                  <View
                    style={{
                      alignItems: 'center',
                      gap: 4,
                      paddingVertical: 10,
                      backgroundColor: t.card,
                      borderRadius: 14,
                      borderWidth: 1,
                      borderColor: t.cardBorder,
                    }}>
                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                      <CheckCircle2 size={16} color={t.ok} />
                      <Text style={{ color: t.ok, fontWeight: '700', fontSize: 13.5 }}>
                        {isAhead ? "You're on a newer dev build!" : "You're on the latest version!"}
                      </Text>
                    </View>
                    <Text style={{ color: t.sub, fontSize: 11.5 }}>
                      {isAhead
                        ? `Installed v${APP_VERSION} is ahead of GitHub ${rel.tagName}.`
                        : 'No updates are currently required.'}
                    </Text>
                  </View>
                )}

                <Btn
                  label={update.error ? 'Clear Download Cache & Retry' : 'Clear Downloaded Cache'}
                  kind="ghost"
                  icon={Trash2}
                  onPress={handleClearCache}
                />

                <PressableScale
                  onPress={() => Linking.openURL(rel.htmlUrl).catch(() => {})}
                  style={[styles.secondaryBtn, { borderColor: t.cardBorder }]}>
                  <Text style={{ color: t.sub, fontSize: 12.5, fontWeight: '600' }}>View on GitHub Releases</Text>
                  <ExternalLink size={13} color={t.sub} />
                </PressableScale>
              </View>
            )}
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(3, 4, 10, 0.8)',
    justifyContent: 'flex-end',
  },
  container: {
    maxHeight: '90%',
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    borderWidth: 1,
    borderBottomWidth: 0,
    overflow: 'hidden',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
  },
  iconBox: {
    width: 38,
    height: 38,
    borderRadius: 12,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    fontSize: 16.5,
    fontWeight: '800',
    letterSpacing: -0.2,
  },
  closeBtn: {
    padding: 6,
    borderRadius: 999,
  },
  scrollContent: {
    padding: 20,
    gap: 14,
  },
  noticeBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    padding: 14,
    borderRadius: 14,
    borderWidth: 1,
  },
  sectionHeading: {
    fontSize: 14,
    fontWeight: '700',
    marginTop: 4,
  },
  footer: {
    padding: 18,
    borderTopWidth: 1,
  },
  secondaryBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingVertical: 10,
    borderRadius: 12,
    borderWidth: 1,
  },
  progressTrack: {
    height: 8,
    borderRadius: 4,
    overflow: 'hidden',
  },
  progressBar: {
    height: '100%',
    borderRadius: 4,
  },
});
