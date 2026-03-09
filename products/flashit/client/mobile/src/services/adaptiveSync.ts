/**
 * Adaptive Sync Service for Flashit Mobile
 * Intelligently adjusts sync behavior based on conditions
 *
 * @doc.type service
 * @doc.purpose Smart sync management based on battery, network, and activity
 * @doc.layer product
 * @doc.pattern SyncService
 */

import * as Network from 'expo-network';
import * as Battery from 'expo-battery';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { AppState, AppStateStatus } from 'react-native';
import { getBatteryOptimizer, type PowerSettings } from './batteryOptimizer';
import { getDataUsageTracker } from './dataUsageTracker';

// ============================================================================
// Types & Interfaces
// ============================================================================

export type SyncPriority = 'critical' | 'high' | 'normal' | 'low' | 'background';
export type SyncStatus = 'idle' | 'syncing' | 'paused' | 'error' | 'offline';

export interface SyncItem {
  id: string;
  type: string;
  priority: SyncPriority;
  payload: unknown;
  createdAt: Date;
  attempts: number;
  lastAttempt?: Date;
  error?: string;
}

export interface SyncConditions {
  isOnline: boolean;
  isWifi: boolean;
  batteryLevel: number;
  isCharging: boolean;
  isLowPowerMode: boolean;
  appState: AppStateStatus;
}

export interface SyncConfig {
  // Intervals (ms)
  normalInterval: number;
  backgroundInterval: number;
  offlineRetryInterval: number;
  
  // Thresholds
  minBatteryLevel: number;
  maxRetries: number;
  batchSize: number;
  
  // Conditions
  requireWifi: boolean;
  allowBackgroundSync: boolean;
  pauseOnLowBattery: boolean;
}

export interface SyncStats {
  totalSynced: number;
  totalFailed: number;
  pendingItems: number;
  lastSyncTime: Date | null;
  averageSyncDuration: number;
  successRate: number;
}

export interface AdaptiveSyncEvents {
  onSyncStart: () => void;
  onSyncComplete: (synced: number, failed: number) => void;
  onSyncError: (error: Error) => void;
  onConditionsChange: (conditions: SyncConditions) => void;
  onStatusChange: (status: SyncStatus) => void;
}

// ============================================================================
// Constants
// ============================================================================

const STORAGE_KEY = '@ghatana/flashit-sync_queue';
const STATS_KEY = '@ghatana/flashit-sync_stats';

const DEFAULT_CONFIG: SyncConfig = {
  normalInterval: 30000,
  backgroundInterval: 120000,
  offlineRetryInterval: 60000,
  minBatteryLevel: 0.15,
  maxRetries: 5,
  batchSize: 10,
  requireWifi: false,
  allowBackgroundSync: true,
  pauseOnLowBattery: true,
};

const PRIORITY_ORDER: SyncPriority[] = ['critical', 'high', 'normal', 'low', 'background'];

// ============================================================================
// Adaptive Sync Service
// ============================================================================

/**
 * AdaptiveSyncService manages intelligent sync operations
 */
class AdaptiveSyncService {
  private static instance: AdaptiveSyncService | null = null;
  
  private config: SyncConfig;
  private queue: SyncItem[] = [];
  private status: SyncStatus = 'idle';
  private conditions: SyncConditions;
  private stats: SyncStats;
  
  private syncTimer: NodeJS.Timeout | null = null;
  private appStateSubscription: ReturnType<typeof AppState.addEventListener> | null = null;
  private batteryUnsubscribe: (() => void) | null = null;
  
  private syncHandler: ((items: SyncItem[]) => Promise<Map<string, boolean>>) | null = null;
  private eventListeners: Partial<AdaptiveSyncEvents> = {};
  private initialized: boolean = false;
  private isSyncing: boolean = false;

  private constructor(config: Partial<SyncConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
    this.conditions = {
      isOnline: true,
      isWifi: false,
      batteryLevel: 1,
      isCharging: false,
      isLowPowerMode: false,
      appState: 'active',
    };
    this.stats = {
      totalSynced: 0,
      totalFailed: 0,
      pendingItems: 0,
      lastSyncTime: null,
      averageSyncDuration: 0,
      successRate: 1,
    };
  }

  /**
   * Get singleton instance
   */
  static getInstance(config?: Partial<SyncConfig>): AdaptiveSyncService {
    if (!this.instance) {
      this.instance = new AdaptiveSyncService(config);
    }
    return this.instance;
  }

  /**
   * Initialize the sync service
   */
  async initialize(): Promise<void> {
    if (this.initialized) return;

    // Load persisted data
    await this.loadQueue();
    await this.loadStats();

    // Update conditions
    await this.updateConditions();

    // Subscribe to app state changes
    this.appStateSubscription = AppState.addEventListener(
      'change',
      this.handleAppStateChange.bind(this)
    );

    // Subscribe to battery changes
    const batteryOptimizer = getBatteryOptimizer();
    this.batteryUnsubscribe = batteryOptimizer.subscribe((settings) => {
      this.handlePowerSettingsChange(settings);
    });

    // Start sync timer
    this.startSyncTimer();

    this.initialized = true;
  }

  /**
   * Set the sync handler function
   */
  setSyncHandler(
    handler: (items: SyncItem[]) => Promise<Map<string, boolean>>
  ): void {
    this.syncHandler = handler;
  }

  /**
   * Set event listeners
   */
  setEventListeners(listeners: Partial<AdaptiveSyncEvents>): void {
    this.eventListeners = { ...this.eventListeners, ...listeners };
  }

  /**
   * Add item to sync queue
   */
  async enqueue(
    type: string,
    payload: unknown,
    priority: SyncPriority = 'normal'
  ): Promise<string> {
    const item: SyncItem = {
      id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      type,
      priority,
      payload,
      createdAt: new Date(),
      attempts: 0,
    };

    this.queue.push(item);
    await this.saveQueue();

    // Trigger immediate sync for critical items
    if (priority === 'critical') {
      this.triggerSync();
    }

    return item.id;
  }

  /**
   * Remove item from queue
   */
  async dequeue(id: string): Promise<void> {
    this.queue = this.queue.filter((item) => item.id !== id);
    await this.saveQueue();
  }

  /**
   * Get current sync status
   */
  getStatus(): SyncStatus {
    return this.status;
  }

  /**
   * Get current conditions
   */
  getConditions(): SyncConditions {
    return { ...this.conditions };
  }

  /**
   * Get sync statistics
   */
  getStats(): SyncStats {
    return {
      ...this.stats,
      pendingItems: this.queue.length,
    };
  }

  /**
   * Get pending queue
   */
  getQueue(): SyncItem[] {
    return [...this.queue];
  }

  /**
   * Force sync now
   */
  async forceSync(): Promise<void> {
    if (this.isSyncing) return;
    await this.performSync(true);
  }

  /**
   * Pause syncing
   */
  pause(): void {
    this.setStatus('paused');
    this.stopSyncTimer();
  }

  /**
   * Resume syncing
   */
  resume(): void {
    this.setStatus('idle');
    this.startSyncTimer();
  }

  /**
   * Update configuration
   */
  updateConfig(config: Partial<SyncConfig>): void {
    this.config = { ...this.config, ...config };
    this.restartSyncTimer();
  }

  /**
   * Update sync conditions
   */
  private async updateConditions(): Promise<void> {
    try {
      const [networkState, batteryLevel, batteryState, lowPowerMode] =
        await Promise.all([
          Network.getNetworkStateAsync(),
          Battery.getBatteryLevelAsync(),
          Battery.getBatteryStateAsync(),
          Battery.isLowPowerModeEnabledAsync(),
        ]);

      const newConditions: SyncConditions = {
        isOnline: networkState.isInternetReachable ?? false,
        isWifi: networkState.type === Network.NetworkStateType.WIFI,
        batteryLevel,
        isCharging:
          batteryState === Battery.BatteryState.CHARGING ||
          batteryState === Battery.BatteryState.FULL,
        isLowPowerMode: lowPowerMode,
        appState: this.conditions.appState,
      };

      const conditionsChanged =
        JSON.stringify(this.conditions) !== JSON.stringify(newConditions);

      this.conditions = newConditions;

      if (conditionsChanged) {
        this.eventListeners.onConditionsChange?.(this.conditions);
        this.adjustSyncBehavior();
      }
    } catch (error) {
      console.warn('Failed to update sync conditions:', error);
    }
  }

  /**
   * Handle app state changes
   */
  private handleAppStateChange(state: AppStateStatus): void {
    this.conditions.appState = state;
    this.eventListeners.onConditionsChange?.(this.conditions);

    if (state === 'active') {
      this.updateConditions();
      this.startSyncTimer();
    } else if (state === 'background') {
      this.adjustForBackground();
    }
  }

  /**
   * Handle power settings changes
   */
  private handlePowerSettingsChange(settings: PowerSettings): void {
    if (!settings.enableBackgroundSync && this.conditions.appState !== 'active') {
      this.pause();
    } else if (this.status === 'paused') {
      this.resume();
    }
  }

  /**
   * Adjust sync behavior based on conditions
   */
  private adjustSyncBehavior(): void {
    // Offline
    if (!this.conditions.isOnline) {
      this.setStatus('offline');
      return;
    }

    // Require WiFi check
    if (this.config.requireWifi && !this.conditions.isWifi) {
      this.setStatus('paused');
      return;
    }

    // Low battery check
    if (
      this.config.pauseOnLowBattery &&
      this.conditions.batteryLevel < this.config.minBatteryLevel &&
      !this.conditions.isCharging
    ) {
      this.setStatus('paused');
      return;
    }

    // Resume if conditions are good
    if (this.status === 'paused' || this.status === 'offline') {
      this.setStatus('idle');
    }
  }

  /**
   * Adjust for background mode
   */
  private adjustForBackground(): void {
    if (!this.config.allowBackgroundSync) {
      this.pause();
    } else {
      // Use longer interval in background
      this.restartSyncTimer();
    }
  }

  /**
   * Start sync timer
   */
  private startSyncTimer(): void {
    this.stopSyncTimer();

    const interval =
      this.conditions.appState === 'active'
        ? this.config.normalInterval
        : this.config.backgroundInterval;

    this.syncTimer = setInterval(() => {
      this.triggerSync();
    }, interval);
  }

  /**
   * Stop sync timer
   */
  private stopSyncTimer(): void {
    if (this.syncTimer) {
      clearInterval(this.syncTimer);
      this.syncTimer = null;
    }
  }

  /**
   * Restart sync timer
   */
  private restartSyncTimer(): void {
    this.stopSyncTimer();
    this.startSyncTimer();
  }

  /**
   * Trigger a sync operation
   */
  private async triggerSync(): Promise<void> {
    if (this.isSyncing) return;
    if (this.status === 'paused' || this.status === 'offline') return;
    if (this.queue.length === 0) return;

    await this.performSync();
  }

  /**
   * Perform sync operation
   */
  private async performSync(force: boolean = false): Promise<void> {
    if (!this.syncHandler) {
      console.warn('No sync handler configured');
      return;
    }

    // Check conditions unless forced
    if (!force) {
      await this.updateConditions();
      if (!this.canSync()) return;
    }

    this.isSyncing = true;
    this.setStatus('syncing');
    this.eventListeners.onSyncStart?.();

    const startTime = Date.now();
    let synced = 0;
    let failed = 0;

    try {
      // Get items to sync (sorted by priority)
      const itemsToSync = this.getItemsToSync();

      if (itemsToSync.length === 0) {
        this.setStatus('idle');
        this.isSyncing = false;
        return;
      }

      // Track data usage
      const dataTracker = getDataUsageTracker();

      // Perform sync
      const results = await this.syncHandler(itemsToSync);

      // Process results
      for (const item of itemsToSync) {
        const success = results.get(item.id) ?? false;

        if (success) {
          await this.dequeue(item.id);
          synced++;
        } else {
          item.attempts++;
          item.lastAttempt = new Date();
          item.error = 'Sync failed';

          if (item.attempts >= this.config.maxRetries) {
            await this.dequeue(item.id);
            failed++;
          }
        }
      }

      // Update stats
      const duration = Date.now() - startTime;
      this.updateStats(synced, failed, duration);

      // Track sync data
      await dataTracker.recordSync(1024 * synced, 'Sync operation');

      this.eventListeners.onSyncComplete?.(synced, failed);
    } catch (error) {
      this.setStatus('error');
      this.eventListeners.onSyncError?.(error as Error);
    } finally {
      this.isSyncing = false;
      if (this.status !== 'error') {
        this.setStatus('idle');
      }
    }
  }

  /**
   * Check if sync is possible
   */
  private canSync(): boolean {
    if (!this.conditions.isOnline) return false;
    if (this.config.requireWifi && !this.conditions.isWifi) return false;
    if (
      this.config.pauseOnLowBattery &&
      this.conditions.batteryLevel < this.config.minBatteryLevel &&
      !this.conditions.isCharging
    ) {
      return false;
    }
    return true;
  }

  /**
   * Get items to sync (prioritized)
   */
  private getItemsToSync(): SyncItem[] {
    const sorted = [...this.queue].sort((a, b) => {
      const priorityDiff =
        PRIORITY_ORDER.indexOf(a.priority) - PRIORITY_ORDER.indexOf(b.priority);
      if (priorityDiff !== 0) return priorityDiff;
      return a.createdAt.getTime() - b.createdAt.getTime();
    });

    return sorted.slice(0, this.config.batchSize);
  }

  /**
   * Update statistics
   */
  private updateStats(synced: number, failed: number, duration: number): void {
    this.stats.totalSynced += synced;
    this.stats.totalFailed += failed;
    this.stats.lastSyncTime = new Date();

    // Update average duration (exponential moving average)
    if (this.stats.averageSyncDuration === 0) {
      this.stats.averageSyncDuration = duration;
    } else {
      this.stats.averageSyncDuration =
        this.stats.averageSyncDuration * 0.8 + duration * 0.2;
    }

    // Update success rate
    const total = this.stats.totalSynced + this.stats.totalFailed;
    this.stats.successRate = total > 0 ? this.stats.totalSynced / total : 1;

    this.saveStats();
  }

  /**
   * Set status
   */
  private setStatus(status: SyncStatus): void {
    if (this.status !== status) {
      this.status = status;
      this.eventListeners.onStatusChange?.(status);
    }
  }

  /**
   * Load queue from storage
   */
  private async loadQueue(): Promise<void> {
    try {
      const stored = await AsyncStorage.getItem(STORAGE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        this.queue = parsed.map((item: SyncItem) => ({
          ...item,
          createdAt: new Date(item.createdAt),
          lastAttempt: item.lastAttempt ? new Date(item.lastAttempt) : undefined,
        }));
      }
    } catch (error) {
      console.warn('Failed to load sync queue:', error);
    }
  }

  /**
   * Save queue to storage
   */
  private async saveQueue(): Promise<void> {
    try {
      await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(this.queue));
    } catch (error) {
      console.warn('Failed to save sync queue:', error);
    }
  }

  /**
   * Load stats from storage
   */
  private async loadStats(): Promise<void> {
    try {
      const stored = await AsyncStorage.getItem(STATS_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        this.stats = {
          ...parsed,
          lastSyncTime: parsed.lastSyncTime
            ? new Date(parsed.lastSyncTime)
            : null,
        };
      }
    } catch (error) {
      console.warn('Failed to load sync stats:', error);
    }
  }

  /**
   * Save stats to storage
   */
  private async saveStats(): Promise<void> {
    try {
      await AsyncStorage.setItem(STATS_KEY, JSON.stringify(this.stats));
    } catch (error) {
      console.warn('Failed to save sync stats:', error);
    }
  }

  /**
   * Clear queue
   */
  async clearQueue(): Promise<void> {
    this.queue = [];
    await AsyncStorage.removeItem(STORAGE_KEY);
  }

  /**
   * Destroy the instance
   */
  destroy(): void {
    this.stopSyncTimer();
    this.appStateSubscription?.remove();
    this.batteryUnsubscribe?.();
    AdaptiveSyncService.instance = null;
  }
}

/**
 * Get the adaptive sync service instance
 */
export function getAdaptiveSync(
  config?: Partial<SyncConfig>
): AdaptiveSyncService {
  return AdaptiveSyncService.getInstance(config);
}

export default AdaptiveSyncService;
