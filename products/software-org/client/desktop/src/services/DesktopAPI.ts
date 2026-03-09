/**
 * Desktop API Service
 *
 * Wrapper for Tauri-specific APIs to enable desktop features
 * like native notifications, file system access, and window management.
 *
 * @package @ghatana/software-org-desktop
 */

import { invoke } from '@tauri-apps/api/tauri';
import { sendNotification } from '@tauri-apps/api/notification';
import { save, open } from '@tauri-apps/api/dialog';
import { writeTextFile, readTextFile } from '@tauri-apps/api/fs';
import { appWindow } from '@tauri-apps/api/window';

/**
 * Desktop API Service
 *
 * Provides desktop-specific functionality using Tauri APIs.
 */
export class DesktopAPI {
  /**
   * Check if running in desktop mode
   */
  static isDesktop(): boolean {
    return typeof window !== 'undefined' && '__TAURI__' in window;
  }

  /**
   * Send native notification
   */
  static async notify(title: string, body: string): Promise<void> {
    if (!this.isDesktop()) return;

    await sendNotification({
      title,
      body,
    });
  }

  /**
   * Save data to file
   */
  static async saveToFile(filename: string, data: string): Promise<void> {
    if (!this.isDesktop()) {
      // Fallback to browser download
      const blob = new Blob([data], { type: 'text/plain' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      a.click();
      URL.revokeObjectURL(url);
      return;
    }

    const filePath = await save({
      defaultPath: filename,
      filters: [
        { name: 'JSON', extensions: ['json'] },
        { name: 'CSV', extensions: ['csv'] },
        { name: 'All Files', extensions: ['*'] },
      ],
    });

    if (filePath) {
      await writeTextFile(filePath, data);
    }
  }

  /**
   * Open file dialog and read content
   */
  static async openFile(): Promise<string | null> {
    if (!this.isDesktop()) return null;

    const filePath = await open({
      multiple: false,
      filters: [
        { name: 'JSON', extensions: ['json'] },
        { name: 'All Files', extensions: ['*'] },
      ],
    });

    if (typeof filePath === 'string') {
      return await readTextFile(filePath);
    }

    return null;
  }

  /**
   * Cache data locally (desktop only)
   */
  static async cacheData(key: string, data: any): Promise<void> {
    if (!this.isDesktop()) {
      localStorage.setItem(key, JSON.stringify(data));
      return;
    }

    // Use Tauri's file system for persistent cache
    try {
      await writeTextFile(`cache_${key}.json`, JSON.stringify(data), {
        dir: 13, // App data directory
      });
    } catch (error) {
      console.error('Failed to cache data:', error);
    }
  }

  /**
   * Retrieve cached data
   */
  static async getCachedData(key: string): Promise<any | null> {
    if (!this.isDesktop()) {
      const data = localStorage.getItem(key);
      return data ? JSON.parse(data) : null;
    }

    try {
      const content = await readTextFile(`cache_${key}.json`, {
        dir: 13,
      });
      return JSON.parse(content);
    } catch (error) {
      return null;
    }
  }

  /**
   * Window management
   */
  static async setTitle(title: string): Promise<void> {
    if (!this.isDesktop()) return;
    await appWindow.setTitle(title);
  }

  static async minimize(): Promise<void> {
    if (!this.isDesktop()) return;
    await appWindow.minimize();
  }

  static async maximize(): Promise<void> {
    if (!this.isDesktop()) return;
    await appWindow.maximize();
  }

  static async close(): Promise<void> {
    if (!this.isDesktop()) return;
    await appWindow.close();
  }

  /**
   * Backend invoke (for custom Tauri commands)
   */
  static async invoke<T>(command: string, args?: any): Promise<T> {
    if (!this.isDesktop()) {
      throw new Error('Desktop API not available');
    }

    return await invoke<T>(command, args);
  }
}

/**
 * Desktop shortcuts configuration
 */
export const desktopShortcuts = {
  'Ctrl+N': 'new-item',
  'Ctrl+S': 'save',
  'Ctrl+F': 'search',
  'Ctrl+,': 'settings',
  'Ctrl+Q': 'quit',
  'F5': 'refresh',
};

