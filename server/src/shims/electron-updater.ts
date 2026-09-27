/**
 * electron-updater shim: auto-update is a desktop-only concern.
 * Provides inert stand-ins so any transitively imported updater code loads.
 */
import { EventEmitter } from 'node:events';

export type UpdateInfo = { version: string; releaseDate?: string; releaseNotes?: string | null };
export type ProgressInfo = { total: number; delta: number; transferred: number; percent: number; bytesPerSecond: number };
export type UpdateDownloadedEvent = UpdateInfo;

class AutoUpdaterShim extends EventEmitter {
  autoDownload = false;
  autoInstallOnAppQuit = false;
  allowPrerelease = false;
  channel: string | null = null;
  logger: unknown = null;

  async checkForUpdates(): Promise<{ updateInfo: UpdateInfo } | null> { return null; }
  async downloadUpdate(): Promise<string[]> { return []; }
  quitAndInstall(): void {}
  setFeedURL(_options: unknown): void {}
}

export const autoUpdater = new AutoUpdaterShim();
