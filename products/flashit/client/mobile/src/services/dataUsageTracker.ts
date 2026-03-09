/**
 * Data Usage Tracker Service for Flashit Mobile
 * Tracks and monitors network data consumption
 *
 * @doc.type service
 * @doc.purpose Track and display data usage metrics
 * @doc.layer product
 * @doc.pattern TrackingService
 */

import * as Network from 'expo-network';
import AsyncStorage from '@react-native-async-storage/async-storage';

// ============================================================================
// Types & Interfaces
// ============================================================================

export type NetworkType = 'wifi' | 'cellular' | 'unknown';
export type DataCategory = 'upload' | 'download' | 'sync' | 'media' | 'other';

export interface DataUsageEntry {
  id: string;
  bytes: number;
  category: DataCategory;
  networkType: NetworkType;
  timestamp: Date;
  description?: string;
}

export interface DataUsageSummary {
  totalBytes: number;
  uploadBytes: number;
  downloadBytes: number;
  wifiBytes: number;
  cellularBytes: number;
  byCategory: Record<DataCategory, number>;
  period: {
    start: Date;
    end: Date;
  };
}

export interface DataUsageSettings {
  wifiOnly: boolean;
  dailyLimitMB: number;
  monthlyLimitMB: number;
  warnAtPercent: number;
  trackingEnabled: boolean;
}

export interface DataUsageAlert {
  id: string;
  type: 'daily_limit' | 'monthly_limit' | 'cellular_usage';
  message: string;
  usageBytes: number;
  limitBytes: number;
  timestamp: Date;
  dismissed: boolean;
}

export interface DataUsageStats {
  today: DataUsageSummary;
  thisWeek: DataUsageSummary;
  thisMonth: DataUsageSummary;
  allTime: DataUsageSummary;
}

// ============================================================================
// Constants
// ============================================================================

const STORAGE_KEY = '@ghatana/flashit-data_usage';
const SETTINGS_KEY = '@ghatana/flashit-data_usage_settings';
const ALERTS_KEY = '@ghatana/flashit-data_usage_alerts';

const DEFAULT_SETTINGS: DataUsageSettings = {
  wifiOnly: false,
  dailyLimitMB: 100,
  monthlyLimitMB: 2000,
  warnAtPercent: 80,
  trackingEnabled: true,
};

// ============================================================================
// Data Usage Tracker Service
// ============================================================================

/**
 * DataUsageTrackerService tracks network data consumption
 */
class DataUsageTrackerService {
  private static instance: DataUsageTrackerService | null = null;
  
  private entries: DataUsageEntry[] = [];
  private settings: DataUsageSettings = DEFAULT_SETTINGS;
  private alerts: DataUsageAlert[] = [];
  private listeners: Set<(stats: DataUsageStats) => void> = new Set();
  private currentNetworkType: NetworkType = 'unknown';
  private networkSubscription: (() => void) | null = null;
  private initialized: boolean = false;

  private constructor() {}

  /**
   * Get singleton instance
   */
  static getInstance(): DataUsageTrackerService {
    if (!this.instance) {
      this.instance = new DataUsageTrackerService();
    }
    return this.instance;
  }

  /**
   * Initialize the tracker
   */
  async initialize(): Promise<void> {
    if (this.initialized) return;

    // Load persisted data
    await Promise.all([
      this.loadEntries(),
      this.loadSettings(),
      this.loadAlerts(),
    ]);

    // Get current network type
    await this.updateNetworkType();

    this.initialized = true;
  }

  /**
   * Update current network type
   */
  private async updateNetworkType(): Promise<void> {
    try {
      const networkState = await Network.getNetworkStateAsync();
      if (networkState.type === Network.NetworkStateType.WIFI) {
        this.currentNetworkType = 'wifi';
      } else if (networkState.type === Network.NetworkStateType.CELLULAR) {
        this.currentNetworkType = 'cellular';
      } else {
        this.currentNetworkType = 'unknown';
      }
    } catch {
      this.currentNetworkType = 'unknown';
    }
  }

  /**
   * Record data usage
   */
  async recordUsage(
    bytes: number,
    category: DataCategory,
    description?: string
  ): Promise<void> {
    if (!this.settings.trackingEnabled) return;

    await this.updateNetworkType();

    const entry: DataUsageEntry = {
      id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      bytes,
      category,
      networkType: this.currentNetworkType,
      timestamp: new Date(),
      description,
    };

    this.entries.push(entry);
    await this.saveEntries();

    // Check limits
    await this.checkLimits();

    // Notify listeners
    this.notifyListeners();
  }

  /**
   * Record upload
   */
  async recordUpload(bytes: number, description?: string): Promise<void> {
    await this.recordUsage(bytes, 'upload', description);
  }

  /**
   * Record download
   */
  async recordDownload(bytes: number, description?: string): Promise<void> {
    await this.recordUsage(bytes, 'download', description);
  }

  /**
   * Record sync
   */
  async recordSync(bytes: number, description?: string): Promise<void> {
    await this.recordUsage(bytes, 'sync', description);
  }

  /**
   * Record media transfer
   */
  async recordMedia(bytes: number, description?: string): Promise<void> {
    await this.recordUsage(bytes, 'media', description);
  }

  /**
   * Get usage summary for a period
   */
  getUsageSummary(start: Date, end: Date): DataUsageSummary {
    const filteredEntries = this.entries.filter(
      (e) => e.timestamp >= start && e.timestamp <= end
    );

    const byCategory: Record<DataCategory, number> = {
      upload: 0,
      download: 0,
      sync: 0,
      media: 0,
      other: 0,
    };

    let totalBytes = 0;
    let uploadBytes = 0;
    let downloadBytes = 0;
    let wifiBytes = 0;
    let cellularBytes = 0;

    for (const entry of filteredEntries) {
      totalBytes += entry.bytes;
      byCategory[entry.category] += entry.bytes;

      if (entry.category === 'upload') {
        uploadBytes += entry.bytes;
      } else if (entry.category === 'download') {
        downloadBytes += entry.bytes;
      }

      if (entry.networkType === 'wifi') {
        wifiBytes += entry.bytes;
      } else if (entry.networkType === 'cellular') {
        cellularBytes += entry.bytes;
      }
    }

    return {
      totalBytes,
      uploadBytes,
      downloadBytes,
      wifiBytes,
      cellularBytes,
      byCategory,
      period: { start, end },
    };
  }

  /**
   * Get all usage stats
   */
  getStats(): DataUsageStats {
    const now = new Date();

    // Today
    const todayStart = new Date(now);
    todayStart.setHours(0, 0, 0, 0);

    // This week
    const weekStart = new Date(now);
    weekStart.setDate(now.getDate() - now.getDay());
    weekStart.setHours(0, 0, 0, 0);

    // This month
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);

    // All time
    const allTimeStart = new Date(0);

    return {
      today: this.getUsageSummary(todayStart, now),
      thisWeek: this.getUsageSummary(weekStart, now),
      thisMonth: this.getUsageSummary(monthStart, now),
      allTime: this.getUsageSummary(allTimeStart, now),
    };
  }

  /**
   * Get settings
   */
  getSettings(): DataUsageSettings {
    return { ...this.settings };
  }

  /**
   * Update settings
   */
  async updateSettings(updates: Partial<DataUsageSettings>): Promise<void> {
    this.settings = { ...this.settings, ...updates };
    await this.saveSettings();
  }

  /**
   * Get alerts
   */
  getAlerts(includeDismissed: boolean = false): DataUsageAlert[] {
    if (includeDismissed) {
      return [...this.alerts];
    }
    return this.alerts.filter((a) => !a.dismissed);
  }

  /**
   * Dismiss an alert
   */
  async dismissAlert(alertId: string): Promise<void> {
    const alert = this.alerts.find((a) => a.id === alertId);
    if (alert) {
      alert.dismissed = true;
      await this.saveAlerts();
    }
  }

  /**
   * Subscribe to stats changes
   */
  subscribe(listener: (stats: DataUsageStats) => void): () => void {
    this.listeners.add(listener);
    listener(this.getStats());
    
    return () => {
      this.listeners.delete(listener);
    };
  }

  /**
   * Check if WiFi only mode blocks current operation
   */
  async shouldBlock(): Promise<boolean> {
    if (!this.settings.wifiOnly) {
      return false;
    }

    await this.updateNetworkType();
    return this.currentNetworkType === 'cellular';
  }

  /**
   * Get current network type
   */
  getCurrentNetworkType(): NetworkType {
    return this.currentNetworkType;
  }

  /**
   * Get human-readable size
   */
  formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B';
    
    const units = ['B', 'KB', 'MB', 'GB'];
    const k = 1024;
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${units[i]}`;
  }

  /**
   * Check usage limits
   */
  private async checkLimits(): Promise<void> {
    const stats = this.getStats();

    // Check daily limit
    const dailyLimitBytes = this.settings.dailyLimitMB * 1024 * 1024;
    const dailyUsagePercent = (stats.today.totalBytes / dailyLimitBytes) * 100;

    if (dailyUsagePercent >= 100) {
      await this.createAlert({
        type: 'daily_limit',
        message: `Daily data limit reached (${this.formatBytes(stats.today.totalBytes)} / ${this.settings.dailyLimitMB}MB)`,
        usageBytes: stats.today.totalBytes,
        limitBytes: dailyLimitBytes,
      });
    } else if (dailyUsagePercent >= this.settings.warnAtPercent) {
      await this.createAlert({
        type: 'daily_limit',
        message: `${Math.round(dailyUsagePercent)}% of daily data limit used`,
        usageBytes: stats.today.totalBytes,
        limitBytes: dailyLimitBytes,
      });
    }

    // Check monthly limit
    const monthlyLimitBytes = this.settings.monthlyLimitMB * 1024 * 1024;
    const monthlyUsagePercent = (stats.thisMonth.totalBytes / monthlyLimitBytes) * 100;

    if (monthlyUsagePercent >= 100) {
      await this.createAlert({
        type: 'monthly_limit',
        message: `Monthly data limit reached (${this.formatBytes(stats.thisMonth.totalBytes)} / ${this.settings.monthlyLimitMB}MB)`,
        usageBytes: stats.thisMonth.totalBytes,
        limitBytes: monthlyLimitBytes,
      });
    } else if (monthlyUsagePercent >= this.settings.warnAtPercent) {
      await this.createAlert({
        type: 'monthly_limit',
        message: `${Math.round(monthlyUsagePercent)}% of monthly data limit used`,
        usageBytes: stats.thisMonth.totalBytes,
        limitBytes: monthlyLimitBytes,
      });
    }

    // Check cellular usage
    if (stats.today.cellularBytes > 50 * 1024 * 1024) { // 50MB cellular
      await this.createAlert({
        type: 'cellular_usage',
        message: `High cellular data usage today: ${this.formatBytes(stats.today.cellularBytes)}`,
        usageBytes: stats.today.cellularBytes,
        limitBytes: 0,
      });
    }
  }

  /**
   * Create an alert
   */
  private async createAlert(params: {
    type: DataUsageAlert['type'];
    message: string;
    usageBytes: number;
    limitBytes: number;
  }): Promise<void> {
    // Check for recent similar alert
    const recentSimilar = this.alerts.find(
      (a) =>
        a.type === params.type &&
        !a.dismissed &&
        new Date().getTime() - a.timestamp.getTime() < 60 * 60 * 1000
    );

    if (recentSimilar) return;

    const alert: DataUsageAlert = {
      id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      ...params,
      timestamp: new Date(),
      dismissed: false,
    };

    this.alerts.push(alert);
    await this.saveAlerts();
  }

  /**
   * Notify listeners
   */
  private notifyListeners(): void {
    const stats = this.getStats();
    for (const listener of this.listeners) {
      try {
        listener(stats);
      } catch (error) {
        console.error('Data usage listener error:', error);
      }
    }
  }

  /**
   * Load entries from storage
   */
  private async loadEntries(): Promise<void> {
    try {
      const stored = await AsyncStorage.getItem(STORAGE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        this.entries = parsed.map((e: DataUsageEntry) => ({
          ...e,
          timestamp: new Date(e.timestamp),
        }));
      }
    } catch (error) {
      console.warn('Failed to load data usage entries:', error);
    }
  }

  /**
   * Save entries to storage
   */
  private async saveEntries(): Promise<void> {
    try {
      // Keep only last 30 days
      const cutoff = new Date();
      cutoff.setDate(cutoff.getDate() - 30);
      this.entries = this.entries.filter((e) => e.timestamp >= cutoff);

      await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(this.entries));
    } catch (error) {
      console.warn('Failed to save data usage entries:', error);
    }
  }

  /**
   * Load settings from storage
   */
  private async loadSettings(): Promise<void> {
    try {
      const stored = await AsyncStorage.getItem(SETTINGS_KEY);
      if (stored) {
        this.settings = { ...DEFAULT_SETTINGS, ...JSON.parse(stored) };
      }
    } catch (error) {
      console.warn('Failed to load data usage settings:', error);
    }
  }

  /**
   * Save settings to storage
   */
  private async saveSettings(): Promise<void> {
    try {
      await AsyncStorage.setItem(SETTINGS_KEY, JSON.stringify(this.settings));
    } catch (error) {
      console.warn('Failed to save data usage settings:', error);
    }
  }

  /**
   * Load alerts from storage
   */
  private async loadAlerts(): Promise<void> {
    try {
      const stored = await AsyncStorage.getItem(ALERTS_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        this.alerts = parsed.map((a: DataUsageAlert) => ({
          ...a,
          timestamp: new Date(a.timestamp),
        }));
      }
    } catch (error) {
      console.warn('Failed to load data usage alerts:', error);
    }
  }

  /**
   * Save alerts to storage
   */
  private async saveAlerts(): Promise<void> {
    try {
      // Keep only last 50 alerts
      this.alerts = this.alerts.slice(-50);
      await AsyncStorage.setItem(ALERTS_KEY, JSON.stringify(this.alerts));
    } catch (error) {
      console.warn('Failed to save data usage alerts:', error);
    }
  }

  /**
   * Clear all data
   */
  async clearAll(): Promise<void> {
    this.entries = [];
    this.alerts = [];
    await Promise.all([
      AsyncStorage.removeItem(STORAGE_KEY),
      AsyncStorage.removeItem(ALERTS_KEY),
    ]);
    this.notifyListeners();
  }

  /**
   * Destroy the instance
   */
  destroy(): void {
    this.listeners.clear();
    DataUsageTrackerService.instance = null;
  }
}

/**
 * Get the data usage tracker instance
 */
export function getDataUsageTracker(): DataUsageTrackerService {
  return DataUsageTrackerService.getInstance();
}

export default DataUsageTrackerService;
