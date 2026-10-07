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
  Sparkles,
  X,
} from 'lucide-react-native';
import { useAppUpdate } from '../hooks/useAppUpdate';
import { APP_VERSION } from '../data/appVersion';
import { compareSemVer } from '../services/UpdateService';
import { Badge, Btn, Card, errMsg, showAlert, useTheme } from '../ui';

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
          <View style={[styles.header, { borderBottomColor: t.border }]}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, flex: 1 }}>
              <View style={[styles.iconBox, { backgroundColor: t.accentSoft, borderColor: t.accent }]}>
                <Sparkles size={18} color={t.accent} strokeWidth={2.5} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={[styles.title, { color: t.text }]}>
                  {isNewer ? 'New Update Available' : isAhead ? 'Public Release Notes' : 'Release Notes'}
                </Text>
                <Text style={{ color: t.sub, fontSize: 12 }}>
                  {rel.tagName} • Installed: v{APP_VERSION}
                </Text>
              </View>
            </View>

            <Pressable onPress={onClose} hitSlop={12} style={styles.closeBtn}>
              <X size={20} color={t.sub} />
            </Pressable>
          </View>

          {/* Body Content */}
          <ScrollView contentContainerStyle={styles.scrollContent}>
            {/* Version Meta Card */}
            <View style={[styles.metaCard, { backgroundColor: t.card, borderColor: t.cardBorder }]}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                  <Package size={16} color={t.accent} />
                  <Text style={{ color: t.text, fontWeight: '700', fontSize: 15 }}>
                    {rel.name || rel.tagName}
                  </Text>
                </View>
                <Badge
                  label={isNewer ? 'Update Available' : isAhead ? 'Public Release' : 'Current Version'}
                  tone={isNewer ? 'warn' : isAhead ? 'idle' : 'ok'}
                />
              </View>

              <View style={{ flexDirection: 'row', gap: 16, marginTop: 8 }}>
                {sizeMb && (
                  <Text style={{ color: t.sub, fontSize: 12 }}>
                    Size: <Text style={{ color: t.text, fontWeight: '600' }}>{sizeMb} MB</Text>
                  </Text>
                )}
                {rel.publishedAt && (
                  <Text style={{ color: t.sub, fontSize: 12 }}>
                    Released: <Text style={{ color: t.text, fontWeight: '600' }}>{rel.publishedAt.split('T')[0]}</Text>
                  </Text>
                )}
              </View>
            </View>

            {/* Safety & Settings Persistence Notice */}
            <View style={[styles.noticeBox, { backgroundColor: t.accentSoft, borderColor: t.border }]}>
              <Info size={15} color={t.accent} />
              <Text style={{ color: t.text, fontSize: 12, flex: 1, lineHeight: 17 }}>
                Updating preserves all your active protections, app timers, custom blocklists, and settings.
              </Text>
            </View>

            {/* Permission Banner if needed */}
            {!canInstall && (
              <Pressable
                onPress={update.openInstallSettings}
                style={[styles.noticeBox, { backgroundColor: 'rgba(239, 68, 68, 0.12)', borderColor: '#EF4444' }]}>
                <AlertCircle size={16} color="#EF4444" />
                <View style={{ flex: 1 }}>
                  <Text style={{ color: '#EF4444', fontWeight: '700', fontSize: 12.5 }}>
                    Install Unknown Apps Permission Needed
                  </Text>
                  <Text style={{ color: t.sub, fontSize: 11.5, marginTop: 2 }}>
                    Tap here to allow Blocker to install APK updates directly.
                  </Text>
                </View>
              </Pressable>
            )}

            {/* Release Notes Changelog */}
            <Text style={[styles.sectionHeading, { color: t.text }]}>What's New & Bug Fixes</Text>

            <View style={[styles.notesCard, { backgroundColor: t.card, borderColor: t.cardBorder }]}>
              {sections.length === 0 ? (
                <Text style={{ color: t.sub, fontSize: 13, fontStyle: 'italic' }}>
                  No changelog description provided.
                </Text>
              ) : (
                sections.map((sec, idx) => (
                  <View key={idx} style={{ marginBottom: 12 }}>
                    {sec.title && (
                      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                        <View style={{ width: 4, height: 14, backgroundColor: t.accent, borderRadius: 2 }} />
                        <Text style={{ color: t.accent, fontWeight: '700', fontSize: 13.5 }}>
                          {sec.title}
                        </Text>
                      </View>
                    )}
                    {sec.items.map((item, itemIdx) => (
                      <View key={itemIdx} style={{ flexDirection: 'row', gap: 8, marginTop: 4 }}>
                        <Text style={{ color: t.accent, fontSize: 14, lineHeight: 19 }}>•</Text>
                        <Text style={{ color: t.text, fontSize: 12.5, lineHeight: 18, flex: 1 }}>
                          {item}
                        </Text>
                      </View>
                    ))}
                  </View>
                ))
              )}
            </View>
          </ScrollView>

          {/* Footer Actions & Download Progress */}
          <View style={[styles.footer, { borderTopColor: t.border, backgroundColor: t.bgAlt }]}>
            {update.downloading ? (
              <View style={{ width: '100%', gap: 8 }}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Text style={{ color: t.text, fontWeight: '600', fontSize: 13 }}>
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

                <Pressable
                  onPress={handleUpdate}
                  style={[styles.primaryBtn, { backgroundColor: t.ok }]}>
                  <Package size={17} color="#FFFFFF" strokeWidth={2.4} />
                  <Text style={styles.primaryBtnText}>Install Update Now</Text>
                </Pressable>
              </View>
            ) : (
              <View style={{ width: '100%', gap: 10 }}>
                {isNewer ? (
                  <Pressable
                    onPress={handleUpdate}
                    style={[styles.primaryBtn, { backgroundColor: t.accent }]}>
                    <ArrowDownToLine size={18} color="#FFFFFF" strokeWidth={2.4} />
                    <Text style={styles.primaryBtnText}>Download & Install Update</Text>
                  </Pressable>
                ) : (
                  <View style={{ alignItems: 'center', gap: 4, paddingVertical: 6, backgroundColor: t.card, borderRadius: 12, borderWidth: 1, borderColor: t.cardBorder }}>
                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                      <CheckCircle2 size={16} color={t.ok} />
                      <Text style={{ color: t.ok, fontWeight: '700', fontSize: 13.5 }}>
                        {isAhead ? "You're on a newer dev build!" : "You're on the latest version!"}
                      </Text>
                    </View>
                    <Text style={{ color: t.sub, fontSize: 11.5 }}>
                      {isAhead ? `Installed v${APP_VERSION} is ahead of GitHub ${rel.tagName}.` : 'No updates are currently required.'}
                    </Text>
                  </View>
                )}

                <Pressable
                  onPress={() => Linking.openURL(rel.htmlUrl).catch(() => {})}
                  style={[styles.secondaryBtn, { borderColor: t.border }]}>
                  <Text style={{ color: t.sub, fontSize: 12.5, fontWeight: '600' }}>View on GitHub Releases</Text>
                  <ExternalLink size={13} color={t.sub} />
                </Pressable>
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
    backgroundColor: 'rgba(0, 0, 0, 0.75)',
    justifyContent: 'flex-end',
  },
  container: {
    maxHeight: '88%',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
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
    width: 36,
    height: 36,
    borderRadius: 12,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    fontSize: 16.5,
    fontWeight: '800',
  },
  closeBtn: {
    padding: 6,
    borderRadius: 999,
  },
  scrollContent: {
    padding: 20,
    gap: 14,
  },
  metaCard: {
    padding: 14,
    borderRadius: 14,
    borderWidth: 1,
  },
  noticeBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    padding: 12,
    borderRadius: 12,
    borderWidth: 1,
  },
  sectionHeading: {
    fontSize: 14,
    fontWeight: '700',
    marginTop: 4,
  },
  notesCard: {
    padding: 16,
    borderRadius: 16,
    borderWidth: 1,
  },
  footer: {
    padding: 18,
    borderTopWidth: 1,
  },
  primaryBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingVertical: 14,
    borderRadius: 14,
  },
  primaryBtnText: {
    color: '#FFFFFF',
    fontWeight: '700',
    fontSize: 14.5,
  },
  secondaryBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingVertical: 9,
    borderRadius: 10,
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
