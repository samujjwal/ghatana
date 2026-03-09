/**
 * FlashIt Mobile - Database Sync Service
 *
 * Handles synchronization between local SQLite database and backend API.
 * Implements conflict resolution with last-write-wins strategy.
 *
 * @doc.type service
 * @doc.purpose Database synchronization with backend
 * @doc.layer product
 * @doc.pattern SyncService
 */

import { database, SyncQueueRecord } from './database';
import { momentRepository, Moment } from './momentRepository';
import { sphereRepository, Sphere } from './sphereRepository';
import { networkMonitor } from '../services/networkMonitor';
import AsyncStorage from '@react-native-async-storage/async-storage';

const STORAGE_KEYS = {
  LAST_SYNC: '@sync_lastSyncTimestamp',
  SYNC_CONFIG: '@sync_config',
};

/**
 * Sync configuration.
 */
export interface SyncConfig {
  apiBaseUrl: string;
  authToken: string;
  syncIntervalMs: number;
  maxBatchSize: number;
  conflictResolution: 'local-wins' | 'server-wins' | 'last-write-wins';
}

/**
 * Sync result.
 */
export interface SyncResult {
  success: boolean;
  syncedUp: number;
  syncedDown: number;
  conflicts: number;
  errors: string[];
  duration: number;
}

/**
 * Sync queue item with parsed data.
 */
interface SyncItem {
  id: string;
  operationType: 'create' | 'update' | 'delete';
  entityType: 'moment' | 'sphere' | 'media';
  entityId: string;
  payload: any;
  priority: number;
  retryCount: number;
  maxRetries: number;
}

/**
 * Database Sync Service.
 */
class DatabaseSyncService {
  private config: SyncConfig | null = null;
  private isSyncing: boolean = false;
  private syncTimer: ReturnType<typeof setInterval> | null = null;
  private listeners: Set<(result: SyncResult) => void> = new Set();

  /**
   * Initialize the sync service.
   */
  async init(config: Partial<SyncConfig>): Promise<void> {
    this.config = {
      apiBaseUrl: config.apiBaseUrl || process.env.EXPO_PUBLIC_API_URL || '',
      authToken: config.authToken || '',
      syncIntervalMs: config.syncIntervalMs || 5 * 60 * 1000, // 5 minutes
      maxBatchSize: config.maxBatchSize || 50,
      conflictResolution: config.conflictResolution || 'last-write-wins',
    };

    // Store config
    await AsyncStorage.setItem(STORAGE_KEYS.SYNC_CONFIG, JSON.stringify(this.config));

    // Subscribe to network changes
    networkMonitor.subscribe((state) => {
      if (state.isConnected && !this.isSyncing) {
        this.sync();
      }
    });

    console.log('[Sync] Initialized');
  }

  /**
   * Update auth token.
   */
  setAuthToken(token: string): void {
    if (this.config) {
      this.config.authToken = token;
    }
  }

  /**
   * Start automatic sync.
   */
  startAutoSync(): void {
    if (this.syncTimer) return;

    this.syncTimer = setInterval(() => {
      if (networkMonitor.isOnline() && !this.isSyncing) {
        this.sync();
      }
    }, this.config?.syncIntervalMs || 5 * 60 * 1000);

    // Initial sync
    if (networkMonitor.isOnline()) {
      this.sync();
    }

    console.log('[Sync] Auto-sync started');
  }

  /**
   * Stop automatic sync.
   */
  stopAutoSync(): void {
    if (this.syncTimer) {
      clearInterval(this.syncTimer);
      this.syncTimer = null;
    }
    console.log('[Sync] Auto-sync stopped');
  }

  /**
   * Perform a full sync.
   */
  async sync(): Promise<SyncResult> {
    if (this.isSyncing) {
      return {
        success: false,
        syncedUp: 0,
        syncedDown: 0,
        conflicts: 0,
        errors: ['Sync already in progress'],
        duration: 0,
      };
    }

    if (!networkMonitor.isOnline()) {
      return {
        success: false,
        syncedUp: 0,
        syncedDown: 0,
        conflicts: 0,
        errors: ['No network connection'],
        duration: 0,
      };
    }

    this.isSyncing = true;
    const startTime = Date.now();
    const result: SyncResult = {
      success: true,
      syncedUp: 0,
      syncedDown: 0,
      conflicts: 0,
      errors: [],
      duration: 0,
    };

    try {
      // 1. Push local changes to server
      const pushResult = await this.pushChanges();
      result.syncedUp = pushResult.synced;
      result.errors.push(...pushResult.errors);

      // 2. Pull changes from server
      const pullResult = await this.pullChanges();
      result.syncedDown = pullResult.synced;
      result.conflicts = pullResult.conflicts;
      result.errors.push(...pullResult.errors);

      // 3. Update last sync timestamp
      await AsyncStorage.setItem(STORAGE_KEYS.LAST_SYNC, new Date().toISOString());

      result.success = result.errors.length === 0;
    } catch (error) {
      result.success = false;
      result.errors.push(error instanceof Error ? error.message : 'Unknown error');
    } finally {
      this.isSyncing = false;
      result.duration = Date.now() - startTime;

      // Notify listeners
      this.notifyListeners(result);

      console.log('[Sync] Completed:', result);
    }

    return result;
  }

  /**
   * Push local changes to server.
   */
  private async pushChanges(): Promise<{ synced: number; errors: string[] }> {
    const queue = await this.getSyncQueue();
    let synced = 0;
    const errors: string[] = [];

    // Sort by priority (higher first) and created time
    queue.sort((a, b) => b.priority - a.priority);

    // Process in batches
    const batchSize = this.config?.maxBatchSize || 50;
    const batches = this.chunkArray(queue, batchSize);

    for (const batch of batches) {
      const results = await Promise.allSettled(
        batch.map((item) => this.processSyncItem(item))
      );

      for (let i = 0; i < results.length; i++) {
        const result = results[i];
        const item = batch[i];

        if (result.status === 'fulfilled') {
          if (result.value.success) {
            await this.removeSyncItem(item.id);
            synced++;
          } else {
            await this.handleSyncItemError(item, result.value.error || 'Unknown error');
            errors.push(`Failed to sync ${item.entityType} ${item.entityId}: ${result.value.error}`);
          }
        } else {
          await this.handleSyncItemError(item, result.reason?.message || 'Unknown error');
          errors.push(`Failed to sync ${item.entityType} ${item.entityId}: ${result.reason?.message}`);
        }
      }
    }

    return { synced, errors };
  }

  /**
   * Pull changes from server.
   */
  private async pullChanges(): Promise<{ synced: number; conflicts: number; errors: string[] }> {
    if (!this.config) {
      return { synced: 0, conflicts: 0, errors: ['Sync not configured'] };
    }

    const lastSync = await AsyncStorage.getItem(STORAGE_KEYS.LAST_SYNC);
    let synced = 0;
    let conflicts = 0;
    const errors: string[] = [];

    try {
      // Fetch spheres
      const spheresResponse = await this.fetchFromServer('/api/spheres', lastSync);
      if (spheresResponse.success && spheresResponse.data) {
        for (const serverSphere of spheresResponse.data) {
          const conflict = await this.checkConflict('sphere', serverSphere.id);
          if (conflict) {
            await this.resolveConflict('sphere', serverSphere);
            conflicts++;
          } else {
            await sphereRepository.importFromServer(serverSphere);
          }
          synced++;
        }
      }

      // Fetch moments
      const momentsResponse = await this.fetchFromServer('/api/moments', lastSync);
      if (momentsResponse.success && momentsResponse.data) {
        for (const serverMoment of momentsResponse.data) {
          const conflict = await this.checkConflict('moment', serverMoment.id);
          if (conflict) {
            await this.resolveConflict('moment', serverMoment);
            conflicts++;
          } else {
            await momentRepository.importFromServer(serverMoment);
          }
          synced++;
        }
      }
    } catch (error) {
      errors.push(error instanceof Error ? error.message : 'Failed to pull changes');
    }

    return { synced, conflicts, errors };
  }

  /**
   * Get pending sync queue items.
   */
  private async getSyncQueue(): Promise<SyncItem[]> {
    const rows = await database.query<any>(
      'SELECT * FROM sync_queue WHERE retry_count < max_retries ORDER BY priority DESC, created_at'
    );

    return rows.map((row) => ({
      id: row.id,
      operationType: row.operation_type,
      entityType: row.entity_type,
      entityId: row.entity_id,
      payload: JSON.parse(row.payload_json),
      priority: row.priority,
      retryCount: row.retry_count,
      maxRetries: row.max_retries,
    }));
  }

  /**
   * Process a single sync queue item.
   */
  private async processSyncItem(item: SyncItem): Promise<{ success: boolean; error?: string }> {
    if (!this.config) {
      return { success: false, error: 'Sync not configured' };
    }

    const endpoint = this.getEndpoint(item.entityType, item.operationType, item.entityId);
    const method = this.getMethod(item.operationType);

    try {
      const response = await fetch(`${this.config.apiBaseUrl}${endpoint}`, {
        method,
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${this.config.authToken}`,
        },
        body: method !== 'DELETE' ? JSON.stringify(item.payload) : undefined,
      });

      if (!response.ok) {
        const errorText = await response.text();
        return { success: false, error: `HTTP ${response.status}: ${errorText}` };
      }

      // Update local record with server ID if applicable
      if (item.operationType === 'create') {
        const serverData = await response.json();
        if (item.entityType === 'moment') {
          await momentRepository.markSynced(item.entityId, serverData.id, serverData.version);
        } else if (item.entityType === 'sphere') {
          await sphereRepository.markSynced(item.entityId, serverData.id);
        }
      }

      return { success: true };
    } catch (error) {
      return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
    }
  }

  /**
   * Get API endpoint for entity operation.
   */
  private getEndpoint(entityType: string, operation: string, entityId: string): string {
    const base = entityType === 'moment' ? '/api/moments' : '/api/spheres';
    
    switch (operation) {
      case 'create':
        return base;
      case 'update':
      case 'delete':
        return `${base}/${entityId}`;
      default:
        return base;
    }
  }

  /**
   * Get HTTP method for operation.
   */
  private getMethod(operation: string): string {
    switch (operation) {
      case 'create':
        return 'POST';
      case 'update':
        return 'PUT';
      case 'delete':
        return 'DELETE';
      default:
        return 'POST';
    }
  }

  /**
   * Remove sync item from queue.
   */
  private async removeSyncItem(id: string): Promise<void> {
    await database.execute('DELETE FROM sync_queue WHERE id = ?', [id]);
  }

  /**
   * Handle sync item error.
   */
  private async handleSyncItemError(item: SyncItem, error: string): Promise<void> {
    const nextRetryAt = new Date(Date.now() + this.calculateBackoff(item.retryCount));

    await database.execute(
      `UPDATE sync_queue SET 
        retry_count = retry_count + 1,
        last_error = ?,
        next_retry_at = ?,
        updated_at = datetime('now')
      WHERE id = ?`,
      [error, nextRetryAt.toISOString(), item.id]
    );

    // Mark entity sync as failed if max retries exceeded
    if (item.retryCount + 1 >= item.maxRetries) {
      if (item.entityType === 'moment') {
        await momentRepository.markSyncError(item.entityId, error);
      }
    }
  }

  /**
   * Calculate exponential backoff delay.
   */
  private calculateBackoff(retryCount: number): number {
    const baseDelay = 1000; // 1 second
    const maxDelay = 30 * 60 * 1000; // 30 minutes
    const delay = Math.min(baseDelay * Math.pow(2, retryCount), maxDelay);
    const jitter = Math.random() * 0.3 * delay; // 30% jitter
    return delay + jitter;
  }

  /**
   * Fetch data from server.
   */
  private async fetchFromServer(
    endpoint: string,
    since?: string | null
  ): Promise<{ success: boolean; data?: any[] }> {
    if (!this.config) {
      return { success: false };
    }

    try {
      const url = new URL(`${this.config.apiBaseUrl}${endpoint}`);
      if (since) {
        url.searchParams.set('since', since);
      }

      const response = await fetch(url.toString(), {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${this.config.authToken}`,
        },
      });

      if (!response.ok) {
        return { success: false };
      }

      const data = await response.json();
      return { success: true, data: Array.isArray(data) ? data : data.items || [] };
    } catch (error) {
      console.error('[Sync] Failed to fetch from server:', error);
      return { success: false };
    }
  }

  /**
   * Check if there's a conflict with local data.
   */
  private async checkConflict(entityType: string, serverId: string): Promise<boolean> {
    const table = entityType === 'moment' ? 'moments' : 'spheres';
    const row = await database.queryOne(
      `SELECT * FROM ${table} WHERE server_id = ? AND sync_status = 'pending'`,
      [serverId]
    );
    return row !== null;
  }

  /**
   * Resolve conflict using configured strategy.
   */
  private async resolveConflict(entityType: string, serverData: any): Promise<void> {
    const strategy = this.config?.conflictResolution || 'last-write-wins';

    switch (strategy) {
      case 'server-wins':
        if (entityType === 'moment') {
          await momentRepository.importFromServer(serverData);
        } else {
          await sphereRepository.importFromServer(serverData);
        }
        break;

      case 'local-wins':
        // Keep local version, do nothing
        break;

      case 'last-write-wins':
      default:
        // Compare timestamps
        const table = entityType === 'moment' ? 'moments' : 'spheres';
        const local = await database.queryOne<{ updated_at: string }>(
          `SELECT updated_at FROM ${table} WHERE server_id = ?`,
          [serverData.id]
        );

        if (local) {
          const localTime = new Date(local.updated_at).getTime();
          const serverTime = new Date(serverData.updatedAt).getTime();

          if (serverTime > localTime) {
            if (entityType === 'moment') {
              await momentRepository.importFromServer(serverData);
            } else {
              await sphereRepository.importFromServer(serverData);
            }
          }
        }
        break;
    }
  }

  /**
   * Get sync status.
   */
  async getStatus(): Promise<{
    lastSync: string | null;
    pendingCount: number;
    isSyncing: boolean;
    errorCount: number;
  }> {
    const lastSync = await AsyncStorage.getItem(STORAGE_KEYS.LAST_SYNC);
    const pending = await database.query<{ count: number }>(
      'SELECT COUNT(*) as count FROM sync_queue WHERE retry_count < max_retries'
    );
    const errors = await database.query<{ count: number }>(
      'SELECT COUNT(*) as count FROM sync_queue WHERE retry_count >= max_retries'
    );

    return {
      lastSync,
      pendingCount: pending[0]?.count || 0,
      isSyncing: this.isSyncing,
      errorCount: errors[0]?.count || 0,
    };
  }

  /**
   * Clear failed sync items.
   */
  async clearFailedItems(): Promise<void> {
    await database.execute('DELETE FROM sync_queue WHERE retry_count >= max_retries');
  }

  /**
   * Force retry failed items.
   */
  async retryFailedItems(): Promise<void> {
    await database.execute(
      `UPDATE sync_queue SET retry_count = 0, last_error = NULL WHERE retry_count >= max_retries`
    );
  }

  /**
   * Subscribe to sync events.
   */
  subscribe(listener: (result: SyncResult) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notifyListeners(result: SyncResult): void {
    this.listeners.forEach((listener) => listener(result));
  }

  /**
   * Chunk array into smaller arrays.
   */
  private chunkArray<T>(array: T[], size: number): T[][] {
    const chunks: T[][] = [];
    for (let i = 0; i < array.length; i += size) {
      chunks.push(array.slice(i, i + size));
    }
    return chunks;
  }
}

// Export singleton instance
export const databaseSyncService = new DatabaseSyncService();
export default databaseSyncService;
