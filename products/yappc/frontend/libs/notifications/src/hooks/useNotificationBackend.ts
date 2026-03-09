/**
 * Notification Backend Integration Hook
 * 
 * Integrates notification functionality with backend NotificationHandler via WebSocket.
 * Handles real-time notification delivery, read status, and dismissal.
 * 
 * @module notifications/hooks
 * @doc.type integration
 * @doc.purpose Real-time notification system with backend
 */

import { useEffect, useCallback, useState } from 'react';
import { WebSocketClient } from '@ghatana/yappc-realtime';

/**
 * Notification types
 */
export type NotificationType = 
  | 'info' 
  | 'success' 
  | 'warning' 
  | 'error' 
  | 'mention' 
  | 'assignment' 
  | 'approval' 
  | 'system';

/**
 * Notification priority
 */
export type NotificationPriority = 'low' | 'normal' | 'high' | 'urgent';

/**
 * Notification structure
 */
export interface Notification {
  id: string;
  userId: string;
  type: NotificationType;
  priority: NotificationPriority;
  title: string;
  message: string;
  timestamp: number;
  read: boolean;
  dismissed: boolean;
  actionUrl?: string;
  actionLabel?: string;
  metadata?: Record<string, unknown>;
  expiresAt?: number;
}

/**
 * Notification send payload
 */
export interface NotificationSendPayload {
  type: NotificationType;
  priority: NotificationPriority;
  title: string;
  message: string;
  actionUrl?: string;
  actionLabel?: string;
  metadata?: Record<string, unknown>;
  expiresAt?: number;
}

/**
 * Notification read payload
 */
export interface NotificationReadPayload {
  notificationId: string;
}

/**
 * Notification dismiss payload
 */
export interface NotificationDismissPayload {
  notificationId: string;
}

/**
 * Notification state
 */
export interface NotificationState {
  notifications: Notification[];
  unreadCount: number;
  isConnected: boolean;
}

/**
 * Hook configuration
 */
export interface UseNotificationBackendConfig {
  /** WebSocket client instance */
  wsClient: WebSocketClient;
  
  /** Current user ID */
  userId: string;
  
  /** Maximum notifications to keep in memory */
  maxNotifications?: number;
  
  /** Auto-dismiss after milliseconds (0 = never) */
  autoDismissAfter?: number;
  
  /** Callback when notification received */
  onNotificationReceived?: (notification: Notification) => void;
  
  /** Callback when notification read */
  onNotificationRead?: (notificationId: string) => void;
  
  /** Callback when notification dismissed */
  onNotificationDismissed?: (notificationId: string) => void;
  
  /** Enable debug logging */
  debug?: boolean;
}

/**
 * Notification Backend Integration Hook
 * 
 * Connects notification UI to backend NotificationHandler via WebSocket.
 * Handles all notification operations with proper state management.
 * 
 * Features:
 * - Real-time notification delivery
 * - Read/unread tracking
 * - Dismiss notifications
 * - Auto-dismiss expired notifications
 * - Priority-based sorting
 * - Unread count tracking
 * - Maximum notification limit
 * 
 * @example
 * ```tsx
 * const notifications = useNotificationBackend({
 *   wsClient,
 *   userId: user.id,
 *   maxNotifications: 100,
 *   autoDismissAfter: 30000, // 30 seconds
 *   onNotificationReceived: (notif) => {
 *     // Show toast or play sound
 *     if (notif.priority === 'urgent') {
 *       playSound();
 *     }
 *   },
 * });
 * 
 * // Mark as read
 * notifications.markAsRead(notificationId);
 * 
 * // Dismiss
 * notifications.dismiss(notificationId);
 * 
 * // Mark all as read
 * notifications.markAllAsRead();
 * 
 * // Clear all
 * notifications.clearAll();
 * ```
 */
export function useNotificationBackend(config: UseNotificationBackendConfig) {
  const {
    wsClient,
    userId,
    maxNotifications = 100,
    autoDismissAfter = 0,
    onNotificationReceived,
    onNotificationRead,
    onNotificationDismissed,
    debug = false,
  } = config;

  // Notification state
  const [state, setState] = useState<NotificationState>({
    notifications: [],
    unreadCount: 0,
    isConnected: false,
  });

  /**
   * Debug logging
   */
  const log = useCallback(
    (...args: unknown[]) => {
      if (debug) {
        console.log('[NotificationBackend]', ...args);
      }
    },
    [debug]
  );

  /**
   * Sort notifications by priority and timestamp
   */
  const sortNotifications = useCallback((notifications: Notification[]): Notification[] => {
    const priorityOrder: Record<NotificationPriority, number> = {
      urgent: 0,
      high: 1,
      normal: 2,
      low: 3,
    };

    return [...notifications].sort((a, b) => {
      // First by priority
      const priorityDiff = priorityOrder[a.priority] - priorityOrder[b.priority];
      if (priorityDiff !== 0) return priorityDiff;
      
      // Then by timestamp (newest first)
      return b.timestamp - a.timestamp;
    });
  }, []);

  /**
   * Update unread count
   */
  const updateUnreadCount = useCallback((notifications: Notification[]) => {
    return notifications.filter((n) => !n.read && !n.dismissed).length;
  }, []);

  /**
   * Add notification to state
   */
  const addNotification = useCallback((notification: Notification) => {
    setState((prev) => {
      let newNotifications = [notification, ...prev.notifications];
      
      // Limit to max notifications
      if (newNotifications.length > maxNotifications) {
        newNotifications = newNotifications.slice(0, maxNotifications);
      }
      
      // Sort by priority
      newNotifications = sortNotifications(newNotifications);
      
      return {
        ...prev,
        notifications: newNotifications,
        unreadCount: updateUnreadCount(newNotifications),
      };
    });
  }, [maxNotifications, sortNotifications, updateUnreadCount]);

  /**
   * Mark notification as read
   */
  const markAsRead = useCallback(
    (notificationId: string) => {
      if (!wsClient.isConnected()) {
        log('Cannot mark as read - WebSocket not connected');
        return;
      }

      const payload: NotificationReadPayload = {
        notificationId,
      };

      wsClient.send('notification.read', payload);
      log('Marked as read:', notificationId);

      // Optimistically update local state
      setState((prev) => {
        const newNotifications = prev.notifications.map((n) =>
          n.id === notificationId ? { ...n, read: true } : n
        );
        return {
          ...prev,
          notifications: newNotifications,
          unreadCount: updateUnreadCount(newNotifications),
        };
      });
    },
    [wsClient, log, updateUnreadCount]
  );

  /**
   * Dismiss notification
   */
  const dismiss = useCallback(
    (notificationId: string) => {
      if (!wsClient.isConnected()) {
        log('Cannot dismiss - WebSocket not connected');
        return;
      }

      const payload: NotificationDismissPayload = {
        notificationId,
      };

      wsClient.send('notification.dismiss', payload);
      log('Dismissed:', notificationId);

      // Optimistically update local state
      setState((prev) => {
        const newNotifications = prev.notifications.map((n) =>
          n.id === notificationId ? { ...n, dismissed: true } : n
        );
        return {
          ...prev,
          notifications: newNotifications,
          unreadCount: updateUnreadCount(newNotifications),
        };
      });
    },
    [wsClient, log, updateUnreadCount]
  );

  /**
   * Mark all as read
   */
  const markAllAsRead = useCallback(() => {
    setState((prev) => {
      const newNotifications = prev.notifications.map((n) => ({ ...n, read: true }));
      return {
        ...prev,
        notifications: newNotifications,
        unreadCount: 0,
      };
    });

    // Send to backend for each unread notification
    state.notifications
      .filter((n) => !n.read && !n.dismissed)
      .forEach((n) => markAsRead(n.id));
  }, [state.notifications, markAsRead]);

  /**
   * Clear all notifications
   */
  const clearAll = useCallback(() => {
    setState((prev) => ({
      ...prev,
      notifications: [],
      unreadCount: 0,
    }));

    // Dismiss all on backend
    state.notifications.forEach((n) => dismiss(n.id));
  }, [state.notifications, dismiss]);

  /**
   * Handle incoming notifications
   */
  useEffect(() => {
    const unsubscribe = wsClient.on<Notification>('notification.send', (notification) => {
      // Ignore if not for current user
      if (notification.userId !== userId) {
        return;
      }

      log('Received notification:', notification);

      // Add to state
      addNotification(notification);

      // Notify callback
      onNotificationReceived?.(notification);

      // Auto-dismiss if configured
      if (autoDismissAfter > 0) {
        setTimeout(() => {
          dismiss(notification.id);
        }, autoDismissAfter);
      }
    });

    return unsubscribe;
  }, [wsClient, userId, addNotification, onNotificationReceived, autoDismissAfter, dismiss, log]);

  /**
   * Handle read confirmations
   */
  useEffect(() => {
    const unsubscribe = wsClient.on<NotificationReadPayload>('notification.read', (payload) => {
      log('Notification read confirmed:', payload.notificationId);
      onNotificationRead?.(payload.notificationId);
    });

    return unsubscribe;
  }, [wsClient, onNotificationRead, log]);

  /**
   * Handle dismiss confirmations
   */
  useEffect(() => {
    const unsubscribe = wsClient.on<NotificationDismissPayload>('notification.dismiss', (payload) => {
      log('Notification dismiss confirmed:', payload.notificationId);
      onNotificationDismissed?.(payload.notificationId);
    });

    return unsubscribe;
  }, [wsClient, onNotificationDismissed, log]);

  /**
   * Handle connection state changes
   */
  useEffect(() => {
    const unsubscribe = wsClient.onStateChange((connectionState: unknown) => {
      const isConnected = connectionState === 'connected';
      setState((prev) => ({ ...prev, isConnected }));
      log('Connection state changed:', connectionState);
    });

    return unsubscribe;
  }, [wsClient, log]);

  /**
   * Auto-expire notifications
   */
  useEffect(() => {
    const interval = setInterval(() => {
      const now = Date.now();
      setState((prev) => {
        const newNotifications = prev.notifications.filter((n) => {
          // Remove if expired
          if (n.expiresAt && n.expiresAt < now) {
            log('Auto-expired notification:', n.id);
            return false;
          }
          return true;
        });

        if (newNotifications.length !== prev.notifications.length) {
          return {
            ...prev,
            notifications: newNotifications,
            unreadCount: updateUnreadCount(newNotifications),
          };
        }

        return prev;
      });
    }, 10000); // Check every 10 seconds

    return () => clearInterval(interval);
  }, [log, updateUnreadCount]);

  return {
    // State
    state,
    notifications: state.notifications.filter((n) => !n.dismissed),
    unreadCount: state.unreadCount,
    isConnected: state.isConnected,
    
    // Actions
    markAsRead,
    dismiss,
    markAllAsRead,
    clearAll,
    
    // Helpers
    getUnreadNotifications: () => state.notifications.filter((n) => !n.read && !n.dismissed),
    getNotificationsByType: (type: NotificationType) => 
      state.notifications.filter((n) => n.type === type && !n.dismissed),
    getNotificationsByPriority: (priority: NotificationPriority) => 
      state.notifications.filter((n) => n.priority === priority && !n.dismissed),
  };
}

export default useNotificationBackend;
