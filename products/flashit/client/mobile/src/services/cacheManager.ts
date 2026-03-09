/**
 * FlashIt Mobile - Cache Manager Service
 *
 * Manages app cache including media files, database, and temporary files.
 *
 * @doc.type service
 * @doc.purpose Cache and storage management
 * @doc.layer product
 * @doc.pattern Cache Manager
 */

import * as FileSystem from 'expo-file-system';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { database } from '../database';

/**
 * Cache statistics.
 */
export interface CacheStats {
  totalSize: number;
  mediaCache: number;
  tempFiles: number;
  databaseSize: number;
  offlineQueue: number;
  appData: number;
  formattedTotal: string;
}

/**
 * Storage usage by category.
 */
export interface StorageBreakdown {
  category: string;
  size: number;
  percentage: number;
  formattedSize: string;
}

/**
 * Cache Manager Service.
 */
class CacheManagerService {
  private readonly cacheDirs = {
    media: `${FileSystem.cacheDirectory}media/`,
    temp: `${FileSystem.cacheDirectory}temp/`,
    thumbnails: `${FileSystem.cacheDirectory}thumbnails/`,
    compressed: `${FileSystem.cacheDirectory}compressed/`,
  };

  /**
   * Initialize cache directories.
   */
  async init(): Promise<void> {
    for (const dir of Object.values(this.cacheDirs)) {
      const info = await FileSystem.getInfoAsync(dir);
      if (!info.exists) {
        await FileSystem.makeDirectoryAsync(dir, { intermediates: true });
      }
    }
    console.log('[CacheManager] Initialized');
  }

  /**
   * Get cache statistics.
   */
  async getStats(): Promise<CacheStats> {
    const [mediaCache, tempFiles, thumbnails, compressed, databaseSize, offlineQueue] =
      await Promise.all([
        this.getDirectorySize(this.cacheDirs.media),
        this.getDirectorySize(this.cacheDirs.temp),
        this.getDirectorySize(this.cacheDirs.thumbnails),
        this.getDirectorySize(this.cacheDirs.compressed),
        database.getDatabaseSize(),
        this.getAsyncStorageSize(),
      ]);

    const totalSize =
      mediaCache + tempFiles + thumbnails + compressed + databaseSize + offlineQueue;

    return {
      totalSize,
      mediaCache: mediaCache + thumbnails,
      tempFiles: tempFiles + compressed,
      databaseSize,
      offlineQueue,
      appData: databaseSize + offlineQueue,
      formattedTotal: this.formatSize(totalSize),
    };
  }

  /**
   * Get storage breakdown by category.
   */
  async getStorageBreakdown(): Promise<StorageBreakdown[]> {
    const stats = await this.getStats();
    const total = stats.totalSize || 1; // Avoid division by zero

    return [
      {
        category: 'Media Cache',
        size: stats.mediaCache,
        percentage: Math.round((stats.mediaCache / total) * 100),
        formattedSize: this.formatSize(stats.mediaCache),
      },
      {
        category: 'Temporary Files',
        size: stats.tempFiles,
        percentage: Math.round((stats.tempFiles / total) * 100),
        formattedSize: this.formatSize(stats.tempFiles),
      },
      {
        category: 'Database',
        size: stats.databaseSize,
        percentage: Math.round((stats.databaseSize / total) * 100),
        formattedSize: this.formatSize(stats.databaseSize),
      },
      {
        category: 'Offline Queue',
        size: stats.offlineQueue,
        percentage: Math.round((stats.offlineQueue / total) * 100),
        formattedSize: this.formatSize(stats.offlineQueue),
      },
    ].sort((a, b) => b.size - a.size);
  }

  /**
   * Get size of a directory.
   */
  private async getDirectorySize(dirPath: string): Promise<number> {
    try {
      const info = await FileSystem.getInfoAsync(dirPath);
      if (!info.exists) return 0;

      const files = await FileSystem.readDirectoryAsync(dirPath);
      let totalSize = 0;

      for (const file of files) {
        const filePath = `${dirPath}${file}`;
        const fileInfo = await FileSystem.getInfoAsync(filePath);
        if (fileInfo.exists && !fileInfo.isDirectory) {
          totalSize += (fileInfo as any).size || 0;
        } else if (fileInfo.isDirectory) {
          totalSize += await this.getDirectorySize(`${filePath}/`);
        }
      }

      return totalSize;
    } catch (error) {
      console.error(`[CacheManager] Error reading directory ${dirPath}:`, error);
      return 0;
    }
  }

  /**
   * Estimate AsyncStorage size.
   */
  private async getAsyncStorageSize(): Promise<number> {
    try {
      const keys = await AsyncStorage.getAllKeys();
      let totalSize = 0;

      for (const key of keys) {
        const value = await AsyncStorage.getItem(key);
        if (value) {
          totalSize += key.length + value.length;
        }
      }

      return totalSize * 2; // Approximate (characters → bytes)
    } catch (error) {
      return 0;
    }
  }

  /**
   * Format size in human-readable format.
   */
  formatSize(bytes: number): string {
    if (bytes === 0) return '0 B';

    const units = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    const size = bytes / Math.pow(1024, i);

    return `${size.toFixed(i > 0 ? 1 : 0)} ${units[i]}`;
  }

  /**
   * Clear all cache.
   */
  async clearAll(): Promise<{ success: boolean; cleared: number }> {
    let cleared = 0;

    try {
      for (const [name, dir] of Object.entries(this.cacheDirs)) {
        const size = await this.getDirectorySize(dir);
        await FileSystem.deleteAsync(dir, { idempotent: true });
        await FileSystem.makeDirectoryAsync(dir, { intermediates: true });
        cleared += size;
        console.log(`[CacheManager] Cleared ${name}: ${this.formatSize(size)}`);
      }

      return { success: true, cleared };
    } catch (error) {
      console.error('[CacheManager] Clear error:', error);
      return { success: false, cleared };
    }
  }

  /**
   * Clear media cache only.
   */
  async clearMediaCache(): Promise<{ success: boolean; cleared: number }> {
    let cleared = 0;

    try {
      for (const dir of [this.cacheDirs.media, this.cacheDirs.thumbnails]) {
        const size = await this.getDirectorySize(dir);
        await FileSystem.deleteAsync(dir, { idempotent: true });
        await FileSystem.makeDirectoryAsync(dir, { intermediates: true });
        cleared += size;
      }

      return { success: true, cleared };
    } catch (error) {
      console.error('[CacheManager] Clear media cache error:', error);
      return { success: false, cleared };
    }
  }

  /**
   * Clear temporary files only.
   */
  async clearTempFiles(): Promise<{ success: boolean; cleared: number }> {
    let cleared = 0;

    try {
      for (const dir of [this.cacheDirs.temp, this.cacheDirs.compressed]) {
        const size = await this.getDirectorySize(dir);
        await FileSystem.deleteAsync(dir, { idempotent: true });
        await FileSystem.makeDirectoryAsync(dir, { intermediates: true });
        cleared += size;
      }

      return { success: true, cleared };
    } catch (error) {
      console.error('[CacheManager] Clear temp files error:', error);
      return { success: false, cleared };
    }
  }

  /**
   * Clear old files (older than specified days).
   */
  async clearOldFiles(daysOld: number = 7): Promise<{ success: boolean; cleared: number }> {
    let cleared = 0;
    const cutoff = Date.now() - daysOld * 24 * 60 * 60 * 1000;

    try {
      for (const dir of Object.values(this.cacheDirs)) {
        cleared += await this.clearOldFilesInDirectory(dir, cutoff);
      }

      return { success: true, cleared };
    } catch (error) {
      console.error('[CacheManager] Clear old files error:', error);
      return { success: false, cleared };
    }
  }

  /**
   * Clear old files in a specific directory.
   */
  private async clearOldFilesInDirectory(
    dirPath: string,
    cutoff: number
  ): Promise<number> {
    let cleared = 0;

    try {
      const info = await FileSystem.getInfoAsync(dirPath);
      if (!info.exists) return 0;

      const files = await FileSystem.readDirectoryAsync(dirPath);

      for (const file of files) {
        const filePath = `${dirPath}${file}`;
        const fileInfo = await FileSystem.getInfoAsync(filePath);

        if (fileInfo.exists && !fileInfo.isDirectory) {
          // Check modification time
          const modTime = (fileInfo as any).modificationTime || 0;
          if (modTime * 1000 < cutoff) {
            const size = (fileInfo as any).size || 0;
            await FileSystem.deleteAsync(filePath, { idempotent: true });
            cleared += size;
          }
        }
      }

      return cleared;
    } catch (error) {
      return 0;
    }
  }

  /**
   * Clear offline queue (failed uploads).
   */
  async clearOfflineQueue(): Promise<{ success: boolean; count: number }> {
    try {
      // Clear from AsyncStorage
      const keys = await AsyncStorage.getAllKeys();
      const queueKeys = keys.filter((k) => k.startsWith('@offline_'));
      await AsyncStorage.multiRemove(queueKeys);

      // Clear from database
      await database.execute('DELETE FROM sync_queue');

      return { success: true, count: queueKeys.length };
    } catch (error) {
      console.error('[CacheManager] Clear offline queue error:', error);
      return { success: false, count: 0 };
    }
  }

  /**
   * Get cache directory path.
   */
  getCacheDir(type: 'media' | 'temp' | 'thumbnails' | 'compressed'): string {
    return this.cacheDirs[type];
  }

  /**
   * Create a temp file path.
   */
  createTempPath(extension: string): string {
    const filename = `temp_${Date.now()}_${Math.random().toString(36).substring(7)}`;
    return `${this.cacheDirs.temp}${filename}.${extension}`;
  }

  /**
   * Check if cache exceeds threshold and needs cleaning.
   */
  async needsCleaning(thresholdMB: number = 500): Promise<boolean> {
    const stats = await this.getStats();
    const thresholdBytes = thresholdMB * 1024 * 1024;
    return stats.totalSize > thresholdBytes;
  }

  /**
   * Auto-clean cache if needed.
   */
  async autoClean(thresholdMB: number = 500): Promise<void> {
    const needsCleaning = await this.needsCleaning(thresholdMB);

    if (needsCleaning) {
      console.log('[CacheManager] Auto-cleaning cache...');

      // First, clear old files
      await this.clearOldFiles(7);

      // If still over threshold, clear temp files
      if (await this.needsCleaning(thresholdMB)) {
        await this.clearTempFiles();
      }

      // Last resort: clear media cache
      if (await this.needsCleaning(thresholdMB)) {
        await this.clearMediaCache();
      }

      console.log('[CacheManager] Auto-clean completed');
    }
  }
}

// Export singleton instance
export const cacheManager = new CacheManagerService();
export default cacheManager;
