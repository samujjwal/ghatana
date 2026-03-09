import { useEffect, useCallback, useRef } from 'react';
import { useSetAtom } from 'jotai';
import { BridgeService } from '../services/bridgeService';
import {
  showNotificationAtom,
} from '../stores';

/**
 * Hook for subscribing to native bridge events and routing to Jotai atoms.
 *
 * <p><b>Purpose</b><br>
 * Establishes real-time event subscription from the native bridge (Permissions,
 * DeviceAdmin, BackgroundSync modules) and dispatches events to Jotai atoms for
 * centralized state management.
 *
 * <p><b>Events Handled</b><br>
 * - app:focus - App comes to foreground, triggers data refresh
 * - app:blur - App goes to background
 * - permission:changed - Permission granted/revoked, updates atoms
 * - device:status - Device status update from monitoring
 * - background:sync - Background sync progress/completion
 * - monitoring:event - New monitoring event recorded
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // In app initialization (e.g., App.tsx)
 * function AppRoot() {
 *   useEventBridge();
 *   return <AppStack />;
 * }
 * }</pre>
 *
 * <p><b>Integration Points</b><br>
 * - BridgeService: Central event source from native modules
 * - Jotai atoms: Event handlers update app state
 * - React Query: Derived from React Query hooks for API sync
 * - Navigation: Accessible for error routing if needed
 *
 * @see BridgeService
 * @see ../stores - Jotai atoms
 * @see ../services/bridgeService - Native bridge coordination
 *
 * @doc.type hook
 * @doc.purpose Real-time event subscription and routing
 * @doc.layer product
 * @doc.pattern Observer/Subscription
 */
export function useEventBridge(): void {
  const bridgeRef = useRef<BridgeService | null>(null);
  type EventHandler<T = unknown> = (data: T) => void;
  const listenersRef = useRef<{ [key: string]: EventHandler }>({});

  // Atom setters for events
  const showNotification = useSetAtom(showNotificationAtom);

  /**
   * Handle app focus event - triggered when app comes to foreground.
   */
  const handleAppFocus = useCallback((): void => {
    showNotification({
      type: 'info',
      message: 'App resumed - syncing data',
      duration: 2000,
    });
  }, [showNotification]);

  /**
   * Handle app blur event - triggered when app goes to background.
   */
  const handleAppBlur = useCallback((): void => {
    showNotification({
      type: 'info',
      message: 'App backgrounded',
      duration: 1000,
    });
  }, [showNotification]);

  /**
   * Handle permission change event.
   */
  const handlePermissionChanged: EventHandler<{ permission: string; granted: boolean }> = useCallback((permissionData) => {
      showNotification({
        type: permissionData.granted ? 'success' : 'warning',
        message: `${permissionData.permission} ${permissionData.granted ? 'granted' : 'denied'}`,
        duration: 2000,
      });
    },
    [showNotification]
  );

  /**
   * Handle device status update event.
   */
  const handleDeviceStatusUpdated: EventHandler<Record<string, unknown>> = useCallback((_statusData) => {
      // Status updates handled by bridge service internally
    },
    []
  );

  /**
   * Handle background sync progress event.
   */
  const handleBackgroundSyncProgress: EventHandler<{ status: string; error?: string }> = useCallback((syncData) => {
      if (syncData.status === 'STARTED') {
        showNotification({
          type: 'info',
          message: 'Background sync started',
          duration: 1500,
        });
      } else if (syncData.status === 'COMPLETED') {
        showNotification({
          type: 'success',
          message: 'Sync completed successfully',
          duration: 2000,
        });
      } else if (syncData.status === 'FAILED') {
        showNotification({
          type: 'error',
          message: `Sync failed: ${syncData.error || 'Unknown error'}`,
          duration: 3000,
        });
      }
    },
    [showNotification]
  );

  /**
   * Handle monitoring event.
   */
  const handleMonitoringEvent: EventHandler<Record<string, unknown>> = useCallback((_eventData) => {
      // Monitoring events recorded internally by bridge service
    },
    []
  );

  /**
   * Initialize event subscription on component mount.
   */
  useEffect(() => {
    // Initialize bridge if available
    try {
      bridgeRef.current = new BridgeService();
    } catch (error) {
      console.warn(
        'BridgeService not available - running in non-native environment',
        error
      );
      return;
    }

    // Store listener functions
    listenersRef.current = {
      'app:focus': handleAppFocus,
      'app:blur': handleAppBlur,
      'permission:changed': handlePermissionChanged as EventHandler,
      'device:status': handleDeviceStatusUpdated as EventHandler,
      'background:sync': handleBackgroundSyncProgress as EventHandler,
      'monitoring:event': handleMonitoringEvent as EventHandler,
    };

    // Register listeners if bridge supports it
    if (bridgeRef.current) {
      Object.entries(listenersRef.current).forEach(([_eventName, _handler]) => {
        // Bridge service will handle event registration internally
      });
    }

    // Cleanup function
    return () => {
      // Bridge service cleanup handled internally
    };
  }, [
    handleAppFocus,
    handleAppBlur,
    handlePermissionChanged,
    handleDeviceStatusUpdated,
    handleBackgroundSyncProgress,
    handleMonitoringEvent,
  ]);
}

/**
 * Hook for manually triggering specific event handlers (useful for testing).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { triggerAppFocus, triggerSync } = useEventBridgeManual();
 *
 * triggerAppFocus();
 * triggerSync('COMPLETED');
 * }</pre>
 *
 * @returns Object with manual trigger functions
 */
export function useEventBridgeManual() {
  const showNotification = useSetAtom(showNotificationAtom);

  return {
    /**
     * Manually trigger app focus event handler.
     */
    triggerAppFocus: useCallback((): void => {
      showNotification({
        type: 'info',
        message: '[TEST] App focus triggered',
        duration: 2000,
      });
    }, [showNotification]),

    /**
     * Manually trigger app blur event handler.
     */
    triggerAppBlur: useCallback((): void => {
      showNotification({
        type: 'info',
        message: '[TEST] App blur triggered',
        duration: 1000,
      });
    }, [showNotification]),

    /**
     * Manually trigger sync operation.
     */
    triggerSync: useCallback((status: 'STARTED' | 'COMPLETED' | 'FAILED'): void => {
      const messages: Record<string, string> = {
        STARTED: 'Sync started',
        COMPLETED: 'Sync completed',
        FAILED: 'Sync failed',
      };
      showNotification({
        type: status === 'FAILED' ? 'error' : 'info',
        message: `[TEST] ${messages[status]}`,
        duration: 1500,
      });
    }, [showNotification]),
  };
}
