/**
 * TutorPutor Mobile - Background Sync Service
 *
 * Handles synchronization of offline data when connectivity is restored.
 * Uses NetInfo for network state monitoring.
 *
 * @doc.type module
 * @doc.purpose Background sync for React Native
 * @doc.layer product
 * @doc.pattern Service
 */

import NetInfo, { NetInfoState } from '@react-native-community/netinfo';
import { database, MutationRecord } from '../storage/SQLiteStorage';
import { syncStorage, CacheKeys } from '../storage/MMKVStorage';

/**
 * Sync result for a single mutation.
 */
export interface MutationSyncResult {
  mutationId: string;
  success: boolean;
  error?: string;
}

/**
 * Overall sync result.
 */
export interface SyncResult {
  success: boolean;
  syncedCount: number;
  failedCount: number;
  errors: MutationSyncResult[];
  completedAt: string;
}

/**
 * Sync handler function type.
 */
export type SyncHandler = (
  type: string,
  payload: unknown
) => Promise<{ success: boolean; error?: string }>;

/**
 * Sync service configuration.
 */
export interface SyncServiceConfig {
  /** API base URL */
  apiBaseUrl: string;
  /** Authorization token getter */
  getAuthToken: () => Promise<string | null>;
  /** Maximum concurrent sync operations */
  maxConcurrent: number;
  /** Retry delay base in ms */
  retryBaseDelayMs: number;
  /** Callback when sync starts */
  onSyncStart?: () => void;
  /** Callback when sync completes */
  onSyncComplete?: (result: SyncResult) => void;
  /** Callback when network state changes */
  onNetworkChange?: (isConnected: boolean) => void;
}

/**
 * Background sync service for offline mutations.
 */
class BackgroundSyncService {
  private config: SyncServiceConfig | null = null;
  private unsubscribe: (() => void) | null = null;
  private isSyncing = false;
  private lastSyncResult: SyncResult | null = null;
  private handlers: Map<string, SyncHandler> = new Map();

  /**
   * Initialize the sync service.
   */
  async init(config: SyncServiceConfig): Promise<void> {
    this.config = config;

    // Set up network listener
    this.unsubscribe = NetInfo.addEventListener(this.handleNetworkChange);

    // Register default handlers
    this.registerDefaultHandlers();

    // Check current network state
    const state = await NetInfo.fetch();
    if (state.isConnected) {
      // Sync on startup if connected
      await this.sync();
    }
  }

  /**
   * Stop the sync service.
   */
  stop(): void {
    if (this.unsubscribe) {
      this.unsubscribe();
      this.unsubscribe = null;
    }
  }

  /**
   * Register a handler for a mutation type.
   */
  registerHandler(type: string, handler: SyncHandler): void {
    this.handlers.set(type, handler);
  }

  /**
   * Handle network state changes.
   */
  private handleNetworkChange = async (state: NetInfoState): Promise<void> => {
    const isConnected = state.isConnected ?? false;
    
    this.config?.onNetworkChange?.(isConnected);

    if (isConnected && !this.isSyncing) {
      // Trigger sync when coming back online
      await this.sync();
    }
  };

  /**
   * Trigger a manual sync.
   */
  async sync(): Promise<SyncResult> {
    if (!this.config) {
      throw new Error('Sync service not initialized');
    }

    if (this.isSyncing) {
      return this.lastSyncResult ?? {
        success: false,
        syncedCount: 0,
        failedCount: 0,
        errors: [{ mutationId: '', success: false, error: 'Sync in progress' }],
        completedAt: new Date().toISOString(),
      };
    }

    // Check network
    const netState = await NetInfo.fetch();
    if (!netState.isConnected) {
      return {
        success: false,
        syncedCount: 0,
        failedCount: 0,
        errors: [{ mutationId: '', success: false, error: 'Device is offline' }],
        completedAt: new Date().toISOString(),
      };
    }

    this.isSyncing = true;
    this.config.onSyncStart?.();

    try {
      // Get pending mutations
      const mutations = await database.getPendingMutations();

      if (mutations.length === 0) {
        this.lastSyncResult = {
          success: true,
          syncedCount: 0,
          failedCount: 0,
          errors: [],
          completedAt: new Date().toISOString(),
        };
        return this.lastSyncResult;
      }

      // Process mutations
      const results = await this.processMutations(mutations);

      // Update last sync time
      syncStorage.set(CacheKeys.lastSync(), Date.now());
      
      // Update pending count
      const remainingCount = await database.getMutationCount();
      syncStorage.set(CacheKeys.pendingCount(), remainingCount);

      this.lastSyncResult = results;
      this.config.onSyncComplete?.(results);

      return results;
    } finally {
      this.isSyncing = false;
    }
  }

  /**
   * Process mutations with concurrency control.
   */
  private async processMutations(mutations: MutationRecord[]): Promise<SyncResult> {
    const errors: MutationSyncResult[] = [];
    let syncedCount = 0;
    let failedCount = 0;

    // Process in batches based on maxConcurrent
    const batchSize = this.config?.maxConcurrent ?? 3;
    
    for (let i = 0; i < mutations.length; i += batchSize) {
      const batch = mutations.slice(i, i + batchSize);
      const results = await Promise.all(
        batch.map((mutation) => this.processSingleMutation(mutation))
      );

      for (const result of results) {
        if (result.success) {
          syncedCount++;
        } else {
          failedCount++;
          errors.push(result);
        }
      }
    }

    return {
      success: failedCount === 0,
      syncedCount,
      failedCount,
      errors,
      completedAt: new Date().toISOString(),
    };
  }

  /**
   * Process a single mutation.
   */
  private async processSingleMutation(
    mutation: MutationRecord
  ): Promise<MutationSyncResult> {
    const handler = this.handlers.get(mutation.type);

    if (!handler) {
      return {
        mutationId: mutation.id,
        success: false,
        error: `No handler for mutation type: ${mutation.type}`,
      };
    }

    try {
      const payload = JSON.parse(mutation.payloadJson);
      const result = await handler(mutation.type, payload);

      if (result.success) {
        await database.removeMutation(mutation.id);
        return { mutationId: mutation.id, success: true };
      } else {
        // Check if we should retry
        if (mutation.retryCount < mutation.maxRetries) {
          await database.markMutationFailed(mutation.id, result.error ?? 'Unknown error');
        } else {
          // Max retries exceeded, keep in queue but stop retrying
          await database.markMutationFailed(mutation.id, 'Max retries exceeded');
        }
        return {
          mutationId: mutation.id,
          success: false,
          error: result.error,
        };
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      await database.markMutationFailed(mutation.id, errorMessage);
      return {
        mutationId: mutation.id,
        success: false,
        error: errorMessage,
      };
    }
  }

  /**
   * Register default handlers for TutorPutor mutations.
   */
  private registerDefaultHandlers(): void {
    if (!this.config) return;

    // Complete lesson handler
    this.registerHandler('COMPLETE_LESSON', async (_, payload) => {
      return this.makeApiCall('/api/progress/complete-lesson', 'POST', payload);
    });

    // Submit quiz handler
    this.registerHandler('SUBMIT_QUIZ', async (_, payload) => {
      return this.makeApiCall('/api/quizzes/submit', 'POST', payload);
    });

    // Update progress handler
    this.registerHandler('UPDATE_PROGRESS', async (_, payload) => {
      return this.makeApiCall('/api/progress', 'PATCH', payload);
    });

    // Add bookmark handler
    this.registerHandler('ADD_BOOKMARK', async (_, payload) => {
      return this.makeApiCall('/api/bookmarks', 'POST', payload);
    });

    // Add note handler
    this.registerHandler('ADD_NOTE', async (_, payload) => {
      return this.makeApiCall('/api/notes', 'POST', payload);
    });
  }

  /**
   * Make an API call with authentication.
   */
  private async makeApiCall(
    endpoint: string,
    method: string,
    payload: unknown
  ): Promise<{ success: boolean; error?: string }> {
    if (!this.config) {
      return { success: false, error: 'Service not configured' };
    }

    try {
      const token = await this.config.getAuthToken();
      
      const response = await fetch(`${this.config.apiBaseUrl}${endpoint}`, {
        method,
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(payload),
      });

      if (response.ok) {
        return { success: true };
      } else {
        const errorBody = await response.text();
        return {
          success: false,
          error: `HTTP ${response.status}: ${errorBody}`,
        };
      }
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Network error',
      };
    }
  }

  /**
   * Get the current sync status.
   */
  isSyncInProgress(): boolean {
    return this.isSyncing;
  }

  /**
   * Get the last sync result.
   */
  getLastSyncResult(): SyncResult | null {
    return this.lastSyncResult;
  }

  /**
   * Get the pending mutation count.
   */
  async getPendingCount(): Promise<number> {
    return database.getMutationCount();
  }
}

/**
 * Singleton instance of the sync service.
 */
export const syncService = new BackgroundSyncService();
