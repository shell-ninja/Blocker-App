import { useEffect, useState } from 'react';
import { ReleaseInfo, UpdateService, UpdateState } from '../services/UpdateService';

export function useAppUpdate() {
  const [state, setState] = useState<UpdateState>(UpdateService.getState());

  useEffect(() => {
    return UpdateService.subscribe(s => setState(s));
  }, []);

  const checkForUpdate = async (manual = false): Promise<ReleaseInfo | null> => {
    return UpdateService.checkForUpdate(manual);
  };

  const startDownloadAndInstall = async (): Promise<boolean> => {
    return UpdateService.downloadAndInstall();
  };

  const installDownloaded = async (): Promise<boolean> => {
    return UpdateService.installDownloaded();
  };

  const checkCanInstall = async (): Promise<boolean> => {
    return UpdateService.checkCanInstall();
  };

  const openInstallSettings = async (): Promise<boolean> => {
    return UpdateService.openInstallSettings();
  };

  const dismissBanner = () => {
    UpdateService.dismissBanner();
  };

  const cleanupOldApks = async (): Promise<number> => {
    return UpdateService.cleanupOldApks();
  };

  const clearUpdateCache = async (): Promise<{ deletedCount: number; freedBytes: number }> => {
    return UpdateService.clearUpdateCache();
  };

  const getCacheStats = async (): Promise<{ fileCount: number; totalBytes: number }> => {
    return UpdateService.getCacheStats();
  };

  return {
    ...state,
    checkForUpdate,
    startDownloadAndInstall,
    installDownloaded,
    checkCanInstall,
    openInstallSettings,
    dismissBanner,
    cleanupOldApks,
    clearUpdateCache,
    getCacheStats,
  };
}
