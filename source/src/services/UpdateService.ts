import { NativeEventEmitter, NativeModules, Platform } from 'react-native';
import { Native, isSupported } from '../native/BlockerNative';
import { APP_VERSION } from '../data/appVersion';

export interface ReleaseAsset {
  name: string;
  downloadUrl: string;
  sizeBytes: number;
}

export interface ReleaseInfo {
  version: string;
  tagName: string;
  name: string;
  body: string;
  publishedAt: string;
  downloadUrl: string;
  apkName: string;
  sizeBytes: number;
  htmlUrl: string;
}

export interface UpdateState {
  checking: boolean;
  hasUpdate: boolean;
  release: ReleaseInfo | null;
  downloading: boolean;
  downloadProgress: number; // 0.0 to 1.0
  downloadedBytes: number;
  totalBytes: number;
  isDownloaded: boolean;
  error: string | null;
  lastCheckedAt: number;
  dismissed: boolean;
}

const GITHUB_API_URL = 'https://api.github.com/repos/shell-ninja/Blocker-App/releases/latest';
const CHECK_COOLDOWN_MS = 24 * 60 * 60 * 1000; // 24 hours between background auto-checks

export function compareSemVer(v1: string, v2: string): number {
  const parse = (v: string) =>
    v.replace(/^[vV]/, '')
      .split(/[-+]/)[0] // drop prerelease or build tags
      .split('.')
      .map(x => parseInt(x, 10) || 0);

  const p1 = parse(v1);
  const p2 = parse(v2);
  const maxLen = Math.max(p1.length, p2.length);

  for (let i = 0; i < maxLen; i++) {
    const num1 = p1[i] ?? 0;
    const num2 = p2[i] ?? 0;
    if (num1 > num2) return 1;
    if (num1 < num2) return -1;
  }
  return 0;
}

class UpdateManager {
  private state: UpdateState = {
    checking: false,
    hasUpdate: false,
    release: null,
    downloading: false,
    downloadProgress: 0,
    downloadedBytes: 0,
    totalBytes: 0,
    isDownloaded: false,
    error: null,
    lastCheckedAt: 0,
    dismissed: false,
  };

  private listeners: Set<(state: UpdateState) => void> = new Set();
  private progressSub: any = null;

  constructor() {
    this.setupProgressListener();
  }

  private setupProgressListener() {
    if (!isSupported || !NativeModules.BlockerNative) return;
    try {
      const emitter = new NativeEventEmitter(NativeModules.BlockerNative);
      this.progressSub = emitter.addListener('apkDownloadProgress', (data: any) => {
        if (!data) return;
        const progress = Math.min(1.0, Math.max(0.0, Number(data.progress) || 0));
        const downloadedBytes = Number(data.downloadedBytes) || 0;
        const totalBytes = Number(data.totalBytes) || 0;
        const isDone = progress >= 0.999 || (totalBytes > 0 && downloadedBytes >= totalBytes);

        this.setState({
          downloadProgress: progress,
          downloadedBytes,
          totalBytes,
          downloading: !isDone,
          isDownloaded: isDone,
        });
      });
    } catch {
      // Event emitter fallback
    }
  }

  subscribe(listener: (state: UpdateState) => void) {
    this.listeners.add(listener);
    listener(this.state);
    return () => {
      this.listeners.delete(listener);
    };
  }

  getState(): UpdateState {
    return this.state;
  }

  private setState(partial: Partial<UpdateState>) {
    this.state = { ...this.state, ...partial };
    for (const l of this.listeners) {
      try {
        l(this.state);
      } catch {}
    }
  }

  dismissBanner() {
    this.setState({ dismissed: true });
  }

  undismissBanner() {
    this.setState({ dismissed: false });
  }

  async checkOnLaunch() {
    // Automatically clean up downloaded APKs if app was updated to latest
    this.cleanupOldApks().catch(() => {});

    let lastChecked = this.state.lastCheckedAt;
    if (isSupported && Native.getUpdateCheckTimestamp) {
      try {
        lastChecked = await Native.getUpdateCheckTimestamp();
      } catch {}
    }
    const now = Date.now();
    // Only check once every 24 hours on launch
    if (now - lastChecked < CHECK_COOLDOWN_MS) {
      return;
    }
    await this.checkForUpdate(false);
  }

  async cleanupOldApks(): Promise<number> {
    if (isSupported && Native.cleanupOldUpdateApks) {
      try {
        return await Native.cleanupOldUpdateApks(APP_VERSION);
      } catch {}
    }
    return 0;
  }

  async checkForUpdate(manual: boolean = false): Promise<ReleaseInfo | null> {
    if (this.state.checking) return this.state.release;

    this.setState({ checking: true, error: null });

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 12000);

      const res = await fetch(GITHUB_API_URL, {
        headers: {
          Accept: 'application/vnd.github.v3+json',
          'User-Agent': 'Blocker-App',
        },
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (!res.ok) {
        throw new Error(`GitHub API error (${res.status})`);
      }

      const data = await res.json();
      const tagName: string = data.tag_name || '';
      const version = tagName.replace(/^[vV]/, '');

      // Determine if currently running app is a debug build
      let isCurrentDebug = false;
      if (isSupported && Native.isDebugBuild) {
        try {
          isCurrentDebug = await Native.isDebugBuild();
        } catch {}
      }

      // Pick release APK (Release builds must NEVER update to a debug build)
      let chosenAsset: any = null;
      if (Array.isArray(data.assets) && data.assets.length > 0) {
        if (!isCurrentDebug) {
          // Release user: strictly require non-debug release APK
          chosenAsset = data.assets.find(
            (a: any) =>
              typeof a.name === 'string' &&
              a.name.toLowerCase().endsWith('.apk') &&
              !a.name.toLowerCase().includes('debug')
          );
        } else {
          // Debug user: prefer release APK, fallback to debug APK
          chosenAsset =
            data.assets.find(
              (a: any) =>
                typeof a.name === 'string' &&
                a.name.toLowerCase().endsWith('.apk') &&
                !a.name.toLowerCase().includes('debug')
            ) ||
            data.assets.find(
              (a: any) => typeof a.name === 'string' && a.name.toLowerCase().endsWith('.apk')
            );
        }
      }

      if (!chosenAsset || !chosenAsset.browser_download_url) {
        throw new Error(
          !isCurrentDebug
            ? 'No official release APK attached to this update.'
            : 'No APK asset attached to the latest release.'
        );
      }

      const downloadUrl: string = chosenAsset.browser_download_url;
      try {
        // Use regex parsing because React Native's URL polyfill does not implement URL.hostname
        const match = downloadUrl.match(/^https:\/\/([a-zA-Z0-9.-]+)(?::\d+)?(?:\/|$|\?|#)/i);
        if (!match) {
          throw new Error('Only secure HTTPS downloads are permitted.');
        }
        const host = match[1].toLowerCase();
        const isGithub =
          host === 'github.com' ||
          host.endsWith('.github.com') ||
          host.endsWith('.githubusercontent.com');
        if (!isGithub) {
          throw new Error(`Untrusted update host: ${host}`);
        }
      } catch (err: any) {
        throw new Error(`Insecure update asset URL: ${err.message}`);
      }

      const release: ReleaseInfo = {
        version,
        tagName,
        name: data.name || tagName,
        body: data.body || 'No release notes provided.',
        publishedAt: data.published_at || '',
        downloadUrl,
        apkName: chosenAsset.name,
        sizeBytes: Number(chosenAsset.size) || 0,
        htmlUrl: data.html_url || `https://github.com/shell-ninja/Blocker-App/releases/tag/${tagName}`,
      };

      const hasUpdate = compareSemVer(version, APP_VERSION) > 0;
      const now = Date.now();

      this.setState({
        checking: false,
        hasUpdate,
        release,
        lastCheckedAt: now,
        dismissed: false,
      });

      // If app is already up to date, ensure any leftover update APKs are removed
      if (!hasUpdate) {
        this.cleanupOldApks().catch(() => {});
      }

      // Persist check timestamp
      if (isSupported && Native.setUpdateCheckTimestamp) {
        Native.setUpdateCheckTimestamp(now).catch(() => {});
      }

      // Show system notification only when a NEWER version is detected and not yet notified
      if (hasUpdate && isSupported) {
        let lastNotified: string | null = null;
        if (Native.getLastNotifiedVersion) {
          try {
            lastNotified = await Native.getLastNotifiedVersion();
          } catch {}
        }

        if (lastNotified !== version) {
          if (Native.setLastNotifiedVersion) {
            Native.setLastNotifiedVersion(version).catch(() => {});
          }
          Native.showUpdateNotification(
            `🚀 Blocker Update Available (${tagName})`,
            `Blocker ${tagName} is now available with new features and fixes. Tap to update.`,
            version
          ).catch(() => {});
        }
      }

      return hasUpdate ? release : null;
    } catch (e: any) {
      const msg = e?.name === 'AbortError' ? 'Network timeout checking for updates.' : (e?.message || 'Check failed');
      this.setState({ checking: false, error: msg });
      if (manual) throw e;
      return null;
    }
  }

  async downloadAndInstall(): Promise<boolean> {
    if (!this.state.release) {
      throw new Error('No update available to download');
    }
    if (!isSupported) {
      throw new Error('In-app updates are only supported on Android');
    }

    if (this.state.release.apkName?.toLowerCase().includes('debug')) {
      let isCurrentDebug = false;
      if (Native.isDebugBuild) {
        try {
          isCurrentDebug = await Native.isDebugBuild();
        } catch {}
      }
      if (!isCurrentDebug) {
        throw new Error('Installing debug builds over the release version is prohibited.');
      }
    }

    const { downloadUrl, version } = this.state.release;

    this.setState({
      downloading: true,
      downloadProgress: 0.01,
      isDownloaded: false,
      error: null,
    });

    try {
      await Native.downloadAndInstallApk(downloadUrl, version);
      this.setState({ downloading: false, isDownloaded: true });
      return true;
    } catch (e: any) {
      const msg = e?.message || 'Download and installation failed';
      this.setState({ downloading: false, error: msg });
      throw e;
    }
  }

  async installDownloaded(): Promise<boolean> {
    if (!this.state.release) return false;
    if (!isSupported) return false;
    try {
      await Native.installDownloadedApk(this.state.release.version);
      return true;
    } catch (e: any) {
      this.setState({ error: e?.message || 'Failed to trigger installer' });
      throw e;
    }
  }

  async checkCanInstall(): Promise<boolean> {
    if (!isSupported) return true;
    try {
      return await Native.checkCanInstallPackages();
    } catch {
      return true;
    }
  }

  async openInstallSettings(): Promise<boolean> {
    if (!isSupported) return false;
    try {
      return await Native.openInstallPermissionSettings();
    } catch {
      return false;
    }
  }

  async clearUpdateCache(): Promise<{ deletedCount: number; freedBytes: number }> {
    if (!isSupported || !Native.clearAllUpdateApks) {
      this.setState({
        isDownloaded: false,
        downloadProgress: 0,
        downloadedBytes: 0,
        error: null,
      });
      return { deletedCount: 0, freedBytes: 0 };
    }
    try {
      const res = await Native.clearAllUpdateApks();
      this.setState({
        isDownloaded: false,
        downloadProgress: 0,
        downloadedBytes: 0,
        error: null,
      });
      return res;
    } catch (e: any) {
      this.setState({ error: e?.message || 'Failed to clear update cache' });
      throw e;
    }
  }

  async getCacheStats(): Promise<{ fileCount: number; totalBytes: number }> {
    if (!isSupported || !Native.getUpdateCacheStats) {
      return { fileCount: 0, totalBytes: 0 };
    }
    try {
      return await Native.getUpdateCacheStats();
    } catch {
      return { fileCount: 0, totalBytes: 0 };
    }
  }
}

export const UpdateService = new UpdateManager();
