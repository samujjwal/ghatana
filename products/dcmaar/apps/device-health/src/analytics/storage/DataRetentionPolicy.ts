/**
 * @fileoverview Data Retention Policy for DCMAAR Extension
 *
 * Manages automated cleanup of old metrics data with configurable
 * retention periods and important event preservation.
 *
 * @module analytics/storage
 * @since 2.0.0
 */

import browser from 'webextension-polyfill';
import type { ProcessedMetrics } from '../AnalyticsPipeline';

// Type definitions for stored data
interface StoredMetricEntry extends Partial<ProcessedMetrics> {
  timestamp: number;
  _compressed?: boolean;
  _originalSize?: number;
  _compressedSize?: number;
}

interface StorageData {
  [key: string]: StoredMetricEntry | StoredMetricEntry[] | any;
}

export type RetentionPeriod = '7d' | '30d' | '90d' | '1y';

export interface RetentionConfig {
  defaultPeriod: RetentionPeriod;
  maxEntries: number;
  compressionThreshold: number; // days after which to compress
  preserveAlerts: boolean;
  preserveBaselines: boolean; // keep baseline measurements for comparison
  cleanupInterval: number; // hours between cleanup runs
}

export interface RetentionMetadata {
  lastCleanup: number;
  totalCleaned: number;
  compressedEntries: number;
  preservedEntries: number;
}

const DEFAULT_CONFIG: RetentionConfig = {
  defaultPeriod: '30d',
  maxEntries: 1000,
  compressionThreshold: 30,
  preserveAlerts: true,
  preserveBaselines: true,
  cleanupInterval: 24, // daily cleanup
};

const RETENTION_PERIODS: Record<RetentionPeriod, number> = {
  '7d': 7 * 24 * 60 * 60 * 1000,     // 7 days in ms
  '30d': 30 * 24 * 60 * 60 * 1000,   // 30 days in ms
  '90d': 90 * 24 * 60 * 60 * 1000,   // 90 days in ms
  '1y': 365 * 24 * 60 * 60 * 1000,   // 1 year in ms
};

const STORAGE_KEYS = {
  config: 'dcmaar:retention-config:v1',
  metadata: 'dcmaar:retention-metadata:v1',
  lastCleanup: 'dcmaar:last-cleanup:v1',
};

export class DataRetentionPolicy {
  private config: RetentionConfig;
  private metadata: RetentionMetadata;

  constructor(config?: Partial<RetentionConfig>) {
    this.config = { ...DEFAULT_CONFIG, ...config };
    this.metadata = {
      lastCleanup: 0,
      totalCleaned: 0,
      compressedEntries: 0,
      preservedEntries: 0,
    };
    
    this.loadConfiguration();
    this.scheduleCleanup();
  }

  /**
   * Load configuration from storage
   */
  private async loadConfiguration(): Promise<void> {
    try {
      const stored = await browser.storage.local.get([
        STORAGE_KEYS.config,
        STORAGE_KEYS.metadata,
      ]);

      if (stored[STORAGE_KEYS.config]) {
        this.config = { ...this.config, ...(stored[STORAGE_KEYS.config] as Partial<RetentionConfig>) };
      }

      if (stored[STORAGE_KEYS.metadata]) {
        this.metadata = { ...this.metadata, ...(stored[STORAGE_KEYS.metadata] as Partial<RetentionMetadata>) };
      }
    } catch (error) {
      console.warn('Failed to load retention configuration:', error);
    }
  }

  /**
   * Save configuration to storage
   */
  private async saveConfiguration(): Promise<void> {
    try {
      await browser.storage.local.set({
        [STORAGE_KEYS.config]: this.config,
        [STORAGE_KEYS.metadata]: this.metadata,
      });
    } catch (error) {
      console.warn('Failed to save retention configuration:', error);
    }
  }

  /**
   * Schedule automatic cleanup
   */
  private scheduleCleanup(): void {
    const cleanupInterval = this.config.cleanupInterval * 60 * 60 * 1000; // convert hours to ms
    
    // Check if cleanup is needed
    const shouldRunCleanup = () => {
      const now = Date.now();
      const timeSinceLastCleanup = now - this.metadata.lastCleanup;
      return timeSinceLastCleanup >= cleanupInterval;
    };

    // Run cleanup if needed
    const runCleanupIfNeeded = () => {
      if (shouldRunCleanup()) {
        this.runCleanup().catch(error => {
          console.error('Scheduled cleanup failed:', error);
        });
      }
    };

    // Run initial check
    runCleanupIfNeeded();

    // Schedule periodic checks
    setInterval(runCleanupIfNeeded, Math.min(cleanupInterval, 60 * 60 * 1000)); // max 1 hour interval
  }

  /**
   * Run data cleanup process
   */
  async runCleanup(): Promise<RetentionMetadata> {
    console.log('Starting data retention cleanup...');
    
    const startTime = Date.now();
    const retentionCutoff = startTime - RETENTION_PERIODS[this.config.defaultPeriod];
    
    let cleaned = 0;
    let compressed = 0;
    let preserved = 0;

    try {
      // Get all stored metrics data
      const allData = await browser.storage.local.get() as StorageData;
      const metricsEntries: Array<{ key: string; data: StoredMetricEntry; timestamp: number }> = [];

      // Find all metrics entries
      for (const [key, value] of Object.entries(allData)) {
        if (key.startsWith('dcmaar:pageUsage:metricsHistory') || 
            key.startsWith('dcmaar_extension_events')) {
          
          if (Array.isArray(value)) {
            // Handle array of metrics
            value.forEach((entry: any, index: number) => {
              if (entry && typeof entry.timestamp === 'number') {
                metricsEntries.push({
                  key: `${key}[${index}]`,
                  data: entry as StoredMetricEntry,
                  timestamp: entry.timestamp,
                });
              }
            });
          } else if (value && typeof (value as any).timestamp === 'number') {
            // Handle single metric entry
            const entry = value as StoredMetricEntry;
            metricsEntries.push({
              key,
              data: entry,
              timestamp: entry.timestamp,
            });
          }
        }
      }

      console.log(`Found ${metricsEntries.length} metrics entries to analyze`);

      // Sort by timestamp (oldest first)
      metricsEntries.sort((a, b) => a.timestamp - b.timestamp);

      // Process entries for cleanup
      const entriesToDelete: string[] = [];
      const entriesToCompress: Array<{ key: string; data: any }> = [];
      const compressionCutoff = startTime - (this.config.compressionThreshold * 24 * 60 * 60 * 1000);

      for (const entry of metricsEntries) {
        const age = startTime - entry.timestamp;
        const isOld = entry.timestamp < retentionCutoff;
        const shouldCompress = entry.timestamp < compressionCutoff && entry.timestamp >= retentionCutoff;

        // Check if entry should be preserved
        const shouldPreserve = this.shouldPreserveEntry(entry.data);

        if (isOld && !shouldPreserve) {
          // Delete old entries
          entriesToDelete.push(entry.key);
          cleaned++;
        } else if (shouldCompress && !entry.data._compressed) {
          // Compress old but retained entries
          entriesToCompress.push({
            key: entry.key,
            data: this.compressEntry(entry.data),
          });
          compressed++;
        } else if (shouldPreserve) {
          preserved++;
        }
      }

      // Check entry count limits
      const totalRetained = metricsEntries.length - cleaned;
      if (totalRetained > this.config.maxEntries) {
        const excessEntries = totalRetained - this.config.maxEntries;
        console.log(`Removing ${excessEntries} entries to stay within max limit`);
        
        // Remove oldest non-preserved entries
        for (let i = 0; i < metricsEntries.length && entriesToDelete.length - cleaned < excessEntries; i++) {
          const entry = metricsEntries[i];
          if (!this.shouldPreserveEntry(entry.data) && !entriesToDelete.includes(entry.key)) {
            entriesToDelete.push(entry.key);
            cleaned++;
          }
        }
      }

      // Execute deletions
      if (entriesToDelete.length > 0) {
        console.log(`Deleting ${entriesToDelete.length} old entries`);
        
        // Note: We can't actually delete array indices, so we'll need to rebuild arrays
        const keysToRemove = entriesToDelete.filter(key => !key.includes('['));
        if (keysToRemove.length > 0) {
          await browser.storage.local.remove(keysToRemove);
        }
      }

      // Execute compressions
      if (entriesToCompress.length > 0) {
        console.log(`Compressing ${entriesToCompress.length} entries`);
        
        const updatedData: Record<string, any> = {};
        for (const { key, data } of entriesToCompress) {
          if (!key.includes('[')) {
            updatedData[key] = data;
          }
        }
        
        if (Object.keys(updatedData).length > 0) {
          await browser.storage.local.set(updatedData);
        }
      }

      // Update metadata
      this.metadata = {
        lastCleanup: startTime,
        totalCleaned: this.metadata.totalCleaned + cleaned,
        compressedEntries: this.metadata.compressedEntries + compressed,
        preservedEntries: preserved,
      };

      await this.saveConfiguration();

      console.log(`Cleanup completed: ${cleaned} deleted, ${compressed} compressed, ${preserved} preserved`);
      
      return this.metadata;

    } catch (error) {
      console.error('Data cleanup failed:', error);
      throw error;
    }
  }

  /**
   * Check if an entry should be preserved despite age
   */
  private shouldPreserveEntry(data: any): boolean {
    // Preserve entries with alerts
    if (this.config.preserveAlerts && data.alerts && data.alerts.length > 0) {
      return true;
    }

    // Preserve baseline measurements (e.g., first measurement of each day)
    if (this.config.preserveBaselines) {
      // Check if this is a baseline entry (implementation depends on data structure)
      const timestamp = data.timestamp;
      if (timestamp) {
        const date = new Date(timestamp);
        const isStartOfDay = date.getHours() === 0 && date.getMinutes() < 30; // first 30 minutes of day
        if (isStartOfDay) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Compress an entry to save space
   */
  private compressEntry(data: any): any {
    const compressed = {
      ...data,
      _compressed: true,
      _originalSize: JSON.stringify(data).length,
    };

    // Remove detailed metrics, keep only summary
    if (data.details) {
      delete compressed.details;
    }

    // Compress alerts to essential info only
    if (data.alerts) {
      compressed.alerts = data.alerts.map((alert: any) => ({
        id: alert.id,
        metric: alert.metric,
        severity: alert.severity,
        value: alert.value,
        timestamp: alert.timestamp,
      }));
    }

    // Round numeric values to reduce precision
    if (data.summary) {
      compressed.summary = Object.fromEntries(
        Object.entries(data.summary).map(([key, value]) => [
          key,
          typeof value === 'number' ? Math.round(value * 100) / 100 : value,
        ])
      );
    }

    compressed._compressedSize = JSON.stringify(compressed).length;
    
    return compressed;
  }

  /**
   * Get current retention configuration
   */
  getConfig(): RetentionConfig {
    return { ...this.config };
  }

  /**
   * Update retention configuration
   */
  async updateConfig(updates: Partial<RetentionConfig>): Promise<void> {
    this.config = { ...this.config, ...updates };
    await this.saveConfiguration();
    
    // Restart cleanup scheduling with new config
    this.scheduleCleanup();
  }

  /**
   * Get retention metadata and statistics
   */
  getMetadata(): RetentionMetadata {
    return { ...this.metadata };
  }

  /**
   * Manually trigger cleanup (for testing or manual maintenance)
   */
  async manualCleanup(): Promise<RetentionMetadata> {
    return this.runCleanup();
  }

  /**
   * Estimate storage usage
   */
  async getStorageStats(): Promise<{
    totalEntries: number;
    totalSize: number; // bytes
    oldestEntry: number; // timestamp
    newestEntry: number; // timestamp
    compressedEntries: number;
  }> {
    try {
      const allData = await browser.storage.local.get() as StorageData;
      let totalEntries = 0;
      let totalSize = 0;
      let oldestEntry = Number.MAX_SAFE_INTEGER;
      let newestEntry = 0;
      let compressedEntries = 0;

      for (const [key, value] of Object.entries(allData)) {
        if (key.startsWith('dcmaar:pageUsage:metricsHistory') || 
            key.startsWith('dcmaar_extension_events')) {
          
          const serialized = JSON.stringify(value);
          totalSize += new Blob([serialized]).size;

          if (Array.isArray(value)) {
            totalEntries += value.length;
            value.forEach((entry: any) => {
              if (entry && entry.timestamp) {
                oldestEntry = Math.min(oldestEntry, entry.timestamp);
                newestEntry = Math.max(newestEntry, entry.timestamp);
                if (entry._compressed) compressedEntries++;
              }
            });
          } else if (value && (value as any).timestamp) {
            const entry = value as StoredMetricEntry;
            totalEntries++;
            oldestEntry = Math.min(oldestEntry, entry.timestamp);
            newestEntry = Math.max(newestEntry, entry.timestamp);
            if (entry._compressed) compressedEntries++;
          }
        }
      }

      return {
        totalEntries,
        totalSize,
        oldestEntry: oldestEntry === Number.MAX_SAFE_INTEGER ? 0 : oldestEntry,
        newestEntry,
        compressedEntries,
      };
    } catch (error) {
      console.error('Failed to calculate storage stats:', error);
      throw error;
    }
  }
}

// Export singleton instance
export const dataRetentionPolicy = new DataRetentionPolicy();

// Export utility functions
export const formatRetentionPeriod = (period: RetentionPeriod): string => {
  const labels: Record<RetentionPeriod, string> = {
    '7d': '7 days',
    '30d': '30 days', 
    '90d': '90 days',
    '1y': '1 year',
  };
  return labels[period];
};

export const formatBytes = (bytes: number): string => {
  const units = ['B', 'KB', 'MB', 'GB'];
  let size = bytes;
  let unitIndex = 0;
  
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex++;
  }
  
  return `${size.toFixed(1)} ${units[unitIndex]}`;
};