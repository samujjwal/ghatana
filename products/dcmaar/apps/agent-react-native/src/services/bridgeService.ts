/**
 * BridgeService - Unified Native Module Coordination
 *
 * Coordinates all native bridge modules and synchronizes with Jotai state management.
 * Handles event emission from native modules and state updates.
 *
 * Architecture:
 * - AccessibilityBridge: Monitors app focus and usage
 * - PermissionsBridge: Manages permission requests
 * - DeviceAdminBridge: Device administration operations
 * - BackgroundSyncBridge: Background sync coordination
 *
 * @see ../native/AccessibilityServiceModule.ts
 * @see ../native/PermissionsModule.ts
 * @see ../native/DeviceAdminModule.ts
 * @see ../native/BackgroundSyncModule.ts
 */

import { NativeEventEmitter, NativeModules } from 'react-native';
import { useEffect, useRef } from 'react';

// Import native modules
const AccessibilityModule = NativeModules.AccessibilityServiceModule;
const PermissionsModule = NativeModules.PermissionsModule || {};
const DeviceAdminModule = NativeModules.DeviceAdminModule || {};
const BackgroundSyncModule = NativeModules.BackgroundSyncModule || {};

/**
 * Bridge Service Interface
 * Defines contract for all bridge operations
 */
export interface IBridgeService {
  initialize(): Promise<void>;
  startMonitoring(): Promise<void>;
  stopMonitoring(): Promise<void>;
  requestPermission(permission: string): Promise<boolean>;
  checkPermissionStatus(): Promise<PermissionStatus>;
  startSync(syncType: string): Promise<void>;
  stopSync(): Promise<void>;
  cleanup(): Promise<void>;
}

/**
 * Permission Status from Native Bridge
 */
export interface PermissionStatus {
  accessibilityService: boolean;
  usageStats: boolean;
  deviceAdmin: boolean;
  permission?: string;
  status?: 'granted' | 'denied' | 'pending';
}

/**
 * App Focus Event from Native Bridge
 */
export interface AppFocusEvent {
  packageName: string;
  appName: string;
  timestamp: number;
  duration?: number;
  category?: string;
}

/**
 * Sync Operation from Native Bridge
 */
export interface SyncOperation {
  id: string;
  type: 'upload_apps' | 'download_policies' | 'sync_permissions';
  status: 'pending' | 'in_progress' | 'completed' | 'failed';
  itemsTotal: number;
  itemsSynced: number;
  startedAt: number;
  completedAt?: number;
  error?: string;
}

/**
 * BridgeService Implementation
 * Central coordinator for all native module interactions
 */
export class BridgeService implements IBridgeService {
  private eventEmitter: NativeEventEmitter | null = null;
  private isInitialized = false;
  private listeners: Map<string, Function[]> = new Map();
  private syncOperations: Map<string, SyncOperation> = new Map();

  /**
   * Initialize all native bridges
   * Must be called before other operations
   */
  async initialize(): Promise<void> {
    if (this.isInitialized) {
      console.log('[BridgeService] Already initialized');
      return;
    }

    try {
      console.log('[BridgeService] Initializing native bridges...');

      // Create event emitter for accessibility module
      if (AccessibilityModule) {
        this.eventEmitter = new NativeEventEmitter(AccessibilityModule);
        this.setupAccessibilityListeners();
      }

      // Initialize each bridge module
      if (AccessibilityModule?.initialize) {
        await AccessibilityModule.initialize({
          enableAppMonitoring: true,
          detectAnomalies: true,
          enableLogging: true,
        });
        console.log('[BridgeService] AccessibilityBridge initialized');
      }

      if (PermissionsModule?.initialize) {
        await PermissionsModule.initialize();
        console.log('[BridgeService] PermissionsBridge initialized');
      }

      if (DeviceAdminModule?.initialize) {
        await DeviceAdminModule.initialize();
        console.log('[BridgeService] DeviceAdminBridge initialized');
      }

      if (BackgroundSyncModule?.initialize) {
        await BackgroundSyncModule.initialize();
        console.log('[BridgeService] BackgroundSyncBridge initialized');
      }

      this.isInitialized = true;
      console.log('[BridgeService] All bridges initialized successfully');
    } catch (error) {
      console.error('[BridgeService] Initialization failed:', error);
      throw error;
    }
  }

  /**
   * Setup listeners for accessibility events
   */
  private setupAccessibilityListeners(): void {
    if (!this.eventEmitter) return;

    // Listen for app focus changes
    this.eventEmitter.addListener('appFocused', (event: AppFocusEvent) => {
      console.log('[BridgeService] App focused:', event.packageName);
      this.handleAppFocusEvent(event);
    });

    // Listen for app closed/backgrounded
    this.eventEmitter.addListener('appClosed', (event: { packageName: string; duration: number }) => {
      console.log('[BridgeService] App closed:', event.packageName);
      this.handleAppClosedEvent(event);
    });

    // Listen for usage updates
    this.eventEmitter.addListener('usageUpdated', (usage) => {
      console.log('[BridgeService] Usage updated');
      this.handleUsageUpdate(usage);
    });

    // Listen for anomalies
    this.eventEmitter.addListener('anomalyDetected', (anomaly) => {
      console.log('[BridgeService] Anomaly detected:', anomaly);
      this.handleAnomalyDetected(anomaly);
    });

    // Listen for permission requests
    this.eventEmitter.addListener('permissionRequested', (request) => {
      console.log('[BridgeService] Permission requested:', request);
      this.handlePermissionRequest(request);
    });

    // Listen for sync events
    this.eventEmitter.addListener('syncProgress', (progress: SyncOperation) => {
      console.log('[BridgeService] Sync progress:', progress);
      this.handleSyncProgress(progress);
    });

    // Listen for connectivity changes
    this.eventEmitter.addListener('connectivityChanged', (status: { isOnline: boolean }) => {
      console.log('[BridgeService] Connectivity changed:', status.isOnline);
      this.handleConnectivityChange(status.isOnline);
    });
  }

  /**
   * Start monitoring app usage and accessibility events
   */
  async startMonitoring(): Promise<void> {
    if (!this.isInitialized) {
      throw new Error('[BridgeService] Must call initialize() first');
    }

    try {
      console.log('[BridgeService] Starting monitoring...');

      if (AccessibilityModule?.startMonitoring) {
        await AccessibilityModule.startMonitoring();
      }

      if (BackgroundSyncModule?.startSync) {
        await BackgroundSyncModule.startSync();
      }

      console.log('[BridgeService] Monitoring started');
    } catch (error) {
      console.error('[BridgeService] Failed to start monitoring:', error);
      throw error;
    }
  }

  /**
   * Stop monitoring
   */
  async stopMonitoring(): Promise<void> {
    try {
      console.log('[BridgeService] Stopping monitoring...');

      if (AccessibilityModule?.stopMonitoring) {
        await AccessibilityModule.stopMonitoring();
      }

      if (BackgroundSyncModule?.stopSync) {
        await BackgroundSyncModule.stopSync();
      }

      console.log('[BridgeService] Monitoring stopped');
    } catch (error) {
      console.error('[BridgeService] Failed to stop monitoring:', error);
      throw error;
    }
  }

  /**
   * Request a permission from the user
   */
  async requestPermission(permission: string): Promise<boolean> {
    try {
      console.log('[BridgeService] Requesting permission:', permission);

      if (PermissionsModule?.requestPermission) {
        const result = await PermissionsModule.requestPermission(permission);
        console.log('[BridgeService] Permission result:', result);
        return result.granted === true;
      }

      return false;
    } catch (error) {
      console.error('[BridgeService] Permission request failed:', error);
      throw error;
    }
  }

  /**
   * Check permission status
   */
  async checkPermissionStatus(): Promise<PermissionStatus> {
    try {
      console.log('[BridgeService] Checking permission status...');

      const status: PermissionStatus = {
        accessibilityService: false,
        usageStats: false,
        deviceAdmin: false,
      };

      if (AccessibilityModule?.checkPermissionStatus) {
        const accessibilityStatus = await AccessibilityModule.checkPermissionStatus();
        status.accessibilityService = accessibilityStatus.granted === true;
      }

      if (PermissionsModule?.checkPermissionStatus) {
        const permStatus = await PermissionsModule.checkPermissionStatus();
        status.usageStats = permStatus.usageStats?.granted === true;
      }

      if (DeviceAdminModule?.checkPermissionStatus) {
        const adminStatus = await DeviceAdminModule.checkPermissionStatus();
        status.deviceAdmin = adminStatus.granted === true;
      }

      console.log('[BridgeService] Permission status:', status);
      return status;
    } catch (error) {
      console.error('[BridgeService] Failed to check permission status:', error);
      throw error;
    }
  }

  /**
   * Start a sync operation
   */
  async startSync(syncType: string): Promise<void> {
    try {
      const syncId = `sync_${Date.now()}`;
      const operation: SyncOperation = {
        id: syncId,
        type: syncType as any,
        status: 'pending',
        itemsTotal: 0,
        itemsSynced: 0,
        startedAt: Date.now(),
      };

      this.syncOperations.set(syncId, operation);
      console.log('[BridgeService] Starting sync:', syncId);

      if (BackgroundSyncModule?.startSync) {
        await BackgroundSyncModule.startSync(syncType);
      }
    } catch (error) {
      console.error('[BridgeService] Failed to start sync:', error);
      throw error;
    }
  }

  /**
   * Stop sync operations
   */
  async stopSync(): Promise<void> {
    try {
      console.log('[BridgeService] Stopping sync...');

      if (BackgroundSyncModule?.stopSync) {
        await BackgroundSyncModule.stopSync();
      }

      this.syncOperations.clear();
    } catch (error) {
      console.error('[BridgeService] Failed to stop sync:', error);
      throw error;
    }
  }

  /**
   * Cleanup and teardown
   */
  async cleanup(): Promise<void> {
    try {
      console.log('[BridgeService] Cleaning up...');

      if (this.eventEmitter) {
        // Remove all event listeners
        [
          'appFocused',
          'appClosed',
          'usageUpdated',
          'anomalyDetected',
          'permissionRequested',
          'syncProgress',
          'connectivityChanged',
        ].forEach((event) => {
          this.eventEmitter?.removeAllListeners(event);
        });
      }

      await this.stopMonitoring();
      this.syncOperations.clear();
      this.listeners.clear();
      this.isInitialized = false;

      console.log('[BridgeService] Cleanup complete');
    } catch (error) {
      console.error('[BridgeService] Cleanup failed:', error);
      throw error;
    }
  }

  // ============================================================
  // Private Event Handlers
  // ============================================================

  private handleAppFocusEvent(event: AppFocusEvent): void {
    // Emit to listeners
    const handlers = this.listeners.get('appFocused') || [];
    handlers.forEach((handler) => handler(event));
  }

  private handleAppClosedEvent(event: unknown): void {
    // Emit to listeners
    const handlers = this.listeners.get('appClosed') || [];
    handlers.forEach((handler) => handler(event));
  }

  private handleUsageUpdate(usage: unknown): void {
    // Emit to listeners
    const handlers = this.listeners.get('usageUpdated') || [];
    handlers.forEach((handler) => handler(usage));
  }

  private handleAnomalyDetected(anomaly: unknown): void {
    console.warn('[BridgeService] Anomaly detected:', anomaly);
    // Emit to listeners
    const handlers = this.listeners.get('anomalyDetected') || [];
    handlers.forEach((handler) => handler(anomaly));
  }

  private handlePermissionRequest(request: unknown): void {
    // Emit to listeners
    const handlers = this.listeners.get('permissionRequested') || [];
    handlers.forEach((handler) => handler(request));
  }

  private handleSyncProgress(progress: SyncOperation): void {
    this.syncOperations.set(progress.id, progress);
    // Emit to listeners
    const handlers = this.listeners.get('syncProgress') || [];
    handlers.forEach((handler) => handler(progress));
  }

  private handleConnectivityChange(isOnline: boolean): void {
    // Emit to listeners
    const handlers = this.listeners.get('connectivityChanged') || [];
    handlers.forEach((handler) => handler({ isOnline }));
  }

  // ============================================================
  // Public Event Registration
  // ============================================================

  /**
   * Register event listener
   */
  on(event: string, handler: Function): void {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, []);
    }
    this.listeners.get(event)!.push(handler);
  }

  /**
   * Unregister event listener
   */
  off(event: string, handler: Function): void {
    const handlers = this.listeners.get(event);
    if (handlers) {
      const index = handlers.indexOf(handler);
      if (index > -1) {
        handlers.splice(index, 1);
      }
    }
  }

  /**
   * Get sync operation status
   */
  getSyncOperation(syncId: string): SyncOperation | undefined {
    return this.syncOperations.get(syncId);
  }

  /**
   * Get all active sync operations
   */
  getActiveSyncOperations(): SyncOperation[] {
    return Array.from(this.syncOperations.values()).filter(
      (op) => op.status === 'in_progress' || op.status === 'pending'
    );
  }
}

// Singleton instance
let bridgeServiceInstance: BridgeService | null = null;

/**
 * Get singleton instance of BridgeService
 */
export function getBridgeService(): BridgeService {
  if (!bridgeServiceInstance) {
    bridgeServiceInstance = new BridgeService();
  }
  return bridgeServiceInstance;
}

/**
 * Hook to initialize and manage bridge service lifecycle
 */
export function useBridgeService(): BridgeService {
  const bridgeRef = useRef<BridgeService | null>(null);

  useEffect(() => {
    const initBridge = async () => {
      const bridge = getBridgeService();
      bridgeRef.current = bridge;

      try {
        await bridge.initialize();
        await bridge.startMonitoring();
      } catch (error) {
        console.error('[useBridgeService] Initialization failed:', error);
      }
    };

    initBridge();

    return () => {
      // Cleanup on unmount
      if (bridgeRef.current) {
        bridgeRef.current.cleanup();
      }
    };
  }, []);

  return bridgeRef.current || getBridgeService();
}
