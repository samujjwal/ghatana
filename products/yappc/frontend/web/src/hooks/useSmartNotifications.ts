/**
 * Smart Notifications Hook
 *
 * React hook for AI-powered notification management.
 * Provides notification prioritization, consolidation, and delivery.
 *
 * @doc.type hook
 * @doc.purpose AI-powered notification management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  processNotifications,
  getNotificationStats,
  markAsRead,
  markAllAsRead,
  removeNotification,
  clearExpiredNotifications,
  type Notification,
  type NotificationPreferences,
  type NotificationRequest,
  type ConsolidatedNotification,
} from '../services/ai/NotificationService';

// ============================================================================
// Types
// ============================================================================

export interface UseSmartNotificationsOptions {
  enableConsolidation?: boolean;
  quietHours?: {
    start: string;
    end: string;
  };
  enabled?: boolean;
}

export interface UseSmartNotificationsResult {
  // Notification state
  notifications: Notification[];
  consolidated: ConsolidatedNotification[];
  unreadCount: number;
  stats: {
    total: number;
    unread: number;
    byType: Record<string, number>;
    byPriority: Record<string, number>;
  };

  // Actions
  addNotification: (notification: Omit<Notification, 'id' | 'timestamp' | 'read'>) => void;
  markAsRead: (id: string) => void;
  markAllAsRead: () => void;
  removeNotification: (id: string) => void;
  clearExpired: () => void;
  processIncoming: (notifications: Notification[]) => Promise<void>;

  // Preferences
  preferences: NotificationPreferences;
  updatePreferences: (prefs: Partial<NotificationPreferences>) => void;

  // Loading state
  isLoading: boolean;
  error: Error | null;
}

// ============================================================================
// Hook Implementation
// ============================================================================

export function useSmartNotifications(options: UseSmartNotificationsOptions = {}): UseSmartNotificationsResult {
  const {
    enableConsolidation = true,
    quietHours,
    enabled = true,
  } = options;

  const queryClient = useQueryClient();
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [consolidated, setConsolidated] = useState<ConsolidatedNotification[]>([]);
  const [preferences, setPreferences] = useState<NotificationPreferences>({
    enableConsolidation,
    quietHours,
    categories: {
      info: true,
      success: true,
      warning: true,
      error: true,
      task: true,
      mention: true,
      system: true,
    },
    priorities: {
      urgent: true,
      high: true,
      normal: true,
      low: true,
    },
  });

  // Process notifications when they change
  useEffect(() => {
    const process = async () => {
      if (!enabled || notifications.length === 0) return;

      const response = await processNotifications({
        notifications,
        userContext: {
          preferences,
        },
      });

      setConsolidated(response.consolidated);
    };

    process();
  }, [notifications, preferences, enabled]);

  // Update stats
  const stats = getNotificationStats(notifications);
  const unreadCount = stats.unread;

  // Add notification
  const addNotification = useCallback((notification: Omit<Notification, 'id' | 'timestamp' | 'read'>) => {
    const newNotification: Notification = {
      ...notification,
      id: `notif-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      timestamp: Date.now(),
      read: false,
    };

    setNotifications(prev => [newNotification, ...prev]);
  }, []);

  // Mark as read
  const markAsReadCallback = useCallback((id: string) => {
    setNotifications(prev => markAsRead(id, prev));
  }, []);

  // Mark all as read
  const markAllAsReadCallback = useCallback(() => {
    setNotifications(prev => markAllAsRead(prev));
  }, []);

  // Remove notification
  const removeNotificationCallback = useCallback((id: string) => {
    setNotifications(prev => removeNotification(id, prev));
  }, []);

  // Clear expired notifications
  const clearExpiredCallback = useCallback(() => {
    setNotifications(prev => clearExpiredNotifications(prev));
  }, []);

  // Process incoming notifications
  const processIncoming = useCallback(async (incoming: Notification[]) => {
    const response = await processNotifications({
      notifications: incoming,
      userContext: {
        preferences,
      },
    });

    setNotifications(prev => [...response.prioritized, ...prev]);
  }, [preferences]);

  // Update preferences
  const updatePreferences = useCallback((prefs: Partial<NotificationPreferences>) => {
    setPreferences(prev => ({ ...prev, ...prefs }));
  }, []);

  return {
    notifications,
    consolidated,
    unreadCount,
    stats,
    addNotification,
    markAsRead: markAsReadCallback,
    markAllAsRead: markAllAsReadCallback,
    removeNotification: removeNotificationCallback,
    clearExpired: clearExpiredCallback,
    processIncoming,
    preferences,
    updatePreferences,
    isLoading: false,
    error: null,
  };
}

// ============================================================================
// Notification Toast Hook
// ============================================================================

export interface UseNotificationToastResult {
  show: (notification: Omit<Notification, 'id' | 'timestamp' | 'read'>) => void;
  success: (message: string, title?: string) => void;
  error: (message: string, title?: string) => void;
  warning: (message: string, title?: string) => void;
  info: (message: string, title?: string) => void;
}

export function useNotificationToast(): UseNotificationToastResult {
  const { addNotification } = useSmartNotifications();

  const show = useCallback((notification: Omit<Notification, 'id' | 'timestamp' | 'read'>) => {
    addNotification(notification);
  }, [addNotification]);

  const success = useCallback((message: string, title = 'Success') => {
    show({
      type: 'success',
      priority: 'normal',
      title,
      message,
    });
  }, [show]);

  const error = useCallback((message: string, title = 'Error') => {
    show({
      type: 'error',
      priority: 'high',
      title,
      message,
    });
  }, [show]);

  const warning = useCallback((message: string, title = 'Warning') => {
    show({
      type: 'warning',
      priority: 'normal',
      title,
      message,
    });
  }, [show]);

  const info = useCallback((message: string, title = 'Info') => {
    show({
      type: 'info',
      priority: 'low',
      title,
      message,
    });
  }, [show]);

  return {
    show,
    success,
    error,
    warning,
    info,
  };
}
