/**
 * Mobile Notifications Route
 * 
 * Mobile-optimized notifications and alerts management.
 * Real-time notifications with filtering and management capabilities.
 */

import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { logger } from '../../utils/Logger';

export interface Notification {
  id: string;
  title: string;
  message: string;
  type: 'info' | 'warning' | 'error' | 'success';
  timestamp: string;
  read: boolean;
  actionUrl?: string;
  actionText?: string;
  metadata?: Record<string, unknown>;
}

export interface NotificationFilter {
  type: string;
  readStatus: 'all' | 'read' | 'unread';
  dateRange?: {
    start: string;
    end: string;
  };
}

/**
 * Mobile Notifications Component
 */
export default function Component() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [filter, setFilter] = useState<NotificationFilter>({
    type: 'all',
    readStatus: 'all'
  });
  const [showFilters, setShowFilters] = useState(false);

  const NOTIFS_KEY = ['notifications', filter.type, filter.readStatus] as const;

  // Fetch notifications from API
  const { data: notifications = [], isLoading } = useQuery<Notification[]>({
    queryKey: NOTIFS_KEY,
    queryFn: async () => {
      const params = new URLSearchParams();
      if (filter.type !== 'all') params.set('type', filter.type);
      if (filter.readStatus !== 'all') params.set('read', filter.readStatus === 'read' ? 'true' : 'false');
      const res = await fetch(`/api/notifications?${params.toString()}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json() as Notification[];
      logger.info('Notifications loaded', 'mobile-notifications', { count: data.length });
      return data;
    },
  });

  // Mark as read
  const markAsReadMutation = useMutation({
    mutationFn: async (notificationId: string) => {
      const res = await fetch(`/api/notifications/${notificationId}/read`, { method: 'PATCH' });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
    },
    onSuccess: (_data, notificationId) => {
      logger.info('Notification marked as read', 'mobile-notifications', { notificationId });
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
    onError: (error, notificationId) => {
      logger.error('Failed to mark notification as read', 'mobile-notifications', {
        notificationId,
        error: error instanceof Error ? error.message : String(error),
      });
    },
  });
  const markAsRead = (notificationId: string) => markAsReadMutation.mutate(notificationId);

  // Delete notification
  const deleteMutation = useMutation({
    mutationFn: async (notificationId: string) => {
      const res = await fetch(`/api/notifications/${notificationId}`, { method: 'DELETE' });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
    },
    onSuccess: (_data, notificationId) => {
      logger.info('Notification deleted', 'mobile-notifications', { notificationId });
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
    onError: (error, notificationId) => {
      logger.error('Failed to delete notification', 'mobile-notifications', {
        notificationId,
        error: error instanceof Error ? error.message : String(error),
      });
    },
  });
  const deleteNotification = (notificationId: string) => deleteMutation.mutate(notificationId);

  // Mark all as read
  const markAllAsReadMutation = useMutation({
    mutationFn: async () => {
      const res = await fetch('/api/notifications/read-all', { method: 'PATCH' });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
    },
    onSuccess: () => {
      logger.info('All notifications marked as read', 'mobile-notifications');
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
    onError: (error) => {
      logger.error('Failed to mark all notifications as read', 'mobile-notifications', {
        error: error instanceof Error ? error.message : String(error),
      });
    },
  });
  const markAllAsRead = () => markAllAsReadMutation.mutate();

  // Clear all notifications
  const clearAllMutation = useMutation({
    mutationFn: async () => {
      const res = await fetch('/api/notifications/clear-all', { method: 'DELETE' });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
    },
    onSuccess: () => {
      logger.info('All notifications cleared', 'mobile-notifications');
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
    onError: (error) => {
      logger.error('Failed to clear all notifications', 'mobile-notifications', {
        error: error instanceof Error ? error.message : String(error),
      });
    },
  });
  const clearAllNotifications = () => clearAllMutation.mutate();

  // Filter notifications — server already filters by type/readStatus; apply client-side search as no API param
  const filteredNotifications = notifications.filter(notification => {
    if (filter.type !== 'all' && notification.type !== filter.type) return false;
    if (filter.readStatus === 'read' && !notification.read) return false;
    if (filter.readStatus === 'unread' && notification.read) return false;
    return true;
  });

  // Get notification icon
  const getNotificationIcon = (type: Notification['type']) => {
    switch (type) {
      case 'success': return '✅';
      case 'warning': return '⚠️';
      case 'error': return '❌';
      case 'info': return 'ℹ️';
      default: return '📢';
    }
  };

  // Format timestamp
  const formatTimestamp = (timestamp: string) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / (1000 * 60));
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString();
  };

  const unreadCount = notifications.filter(n => !n.read).length;

  return (
    <div className="min-h-screen bg-bg-default">
      {/* Header */}
      <div className="bg-bg-paper border-b border-divider px-4 py-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="relative">
              <span className="text-2xl">🔔</span>
              {unreadCount > 0 && (
                <span className="absolute -top-1 -right-1 bg-error-color text-white text-xs rounded-full w-5 h-5 flex items-center justify-center">
                  {unreadCount}
                </span>
              )}
            </div>
            <h1 className="text-xl font-semibold text-text-primary">Notifications</h1>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setShowFilters(!showFilters)}
              className="p-2 rounded-lg hover:bg-bg-surface transition-colors"
              aria-label="Filter notifications"
            >
              <span className="text-xl">🔍</span>
            </button>
            <button
              className="p-2 rounded-lg hover:bg-bg-surface transition-colors"
              aria-label="Notification settings"
            >
              <span className="text-xl">⚙️</span>
            </button>
          </div>
        </div>
      </div>

      {/* Filters */}
      {showFilters && (
        <div className="bg-bg-surface border-b border-divider px-4 py-3">
          <div className="space-y-3">
            <div>
              <label className="block text-sm font-medium text-text-primary mb-2">Type</label>
              <select
                value={filter.type}
                onChange={(e) => setFilter(prev => ({ ...prev, type: e.target.value }))}
                className="w-full p-2 rounded-lg border border-divider bg-bg-paper text-text-primary"
              >
                <option value="all">All Types</option>
                <option value="info">Info</option>
                <option value="success">Success</option>
                <option value="warning">Warning</option>
                <option value="error">Error</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-text-primary mb-2">Status</label>
              <select
                value={filter.readStatus}
                onChange={(e) => setFilter(prev => ({ ...prev, readStatus: e.target.value as unknown }))}
                className="w-full p-2 rounded-lg border border-divider bg-bg-paper text-text-primary"
              >
                <option value="all">All</option>
                <option value="unread">Unread</option>
                <option value="read">Read</option>
              </select>
            </div>
          </div>
        </div>
      )}

      {/* Actions */}
      {notifications.length > 0 && (
        <div className="bg-bg-surface border-b border-divider px-4 py-3">
          <div className="flex items-center justify-between">
            <span className="text-sm text-text-secondary">
              {unreadCount} unread
            </span>
            <div className="flex items-center gap-2">
              {unreadCount > 0 && (
                <button
                  onClick={markAllAsRead}
                  className="text-sm text-primary-600 hover:text-primary-700 transition-colors"
                >
                  Mark all as read
                </button>
              )}
              <button
                onClick={clearAllNotifications}
                className="text-sm text-error-color hover:text-error-color/80 transition-colors"
              >
                Clear all
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Notifications List */}
      <div className="px-4 py-2">
        {isLoading ? (
          <div className="flex items-center justify-center py-8">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
          </div>
        ) : filteredNotifications.length === 0 ? (
          <div className="text-center py-8">
            <span className="text-4xl">🔔</span>
            <p className="text-text-secondary mt-4">
              {notifications.length === 0 ? 'No notifications yet' : 'No notifications match your filters'}
            </p>
          </div>
        ) : (
          <div className="space-y-2">
            {filteredNotifications.map((notification) => (
              <div
                key={notification.id}
                className={`bg-bg-paper rounded-lg border p-4 transition-all ${notification.read ? 'border-divider opacity-75' : 'border-primary-200 bg-primary-50'
                  }`}
              >
                <div className="flex items-start gap-3">
                  <span className="text-xl">{getNotificationIcon(notification.type)}</span>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between">
                      <div className="flex-1">
                        <h3 className={`font-medium text-text-primary ${notification.read ? 'font-normal' : 'font-semibold'
                          }`}>
                          {notification.title}
                        </h3>
                        <p className="text-sm text-text-secondary mt-1">
                          {notification.message}
                        </p>
                        <p className="text-xs text-text-tertiary mt-2">
                          {formatTimestamp(notification.timestamp)}
                        </p>
                      </div>
                      <div className="flex items-center gap-1 ml-2">
                        {!notification.read && (
                          <button
                            onClick={() => markAsRead(notification.id)}
                            className="p-1 rounded hover:bg-bg-surface transition-colors"
                            aria-label="Mark as read"
                          >
                            <span className="text-sm">✓</span>
                          </button>
                        )}
                        <button
                          onClick={() => deleteNotification(notification.id)}
                          className="p-1 rounded hover:bg-bg-surface transition-colors"
                          aria-label="Delete notification"
                        >
                          <span className="text-sm">✕</span>
                        </button>
                      </div>
                    </div>
                    {notification.actionUrl && (
                      <button
                        onClick={() => {
                          void markAsRead(notification.id);
                          navigate(notification.actionUrl!);
                        }}
                        className="mt-3 text-sm text-primary-600 hover:text-primary-700 transition-colors"
                      >
                        {notification.actionText}
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export const ErrorBoundary = () => {
  return (
    <div className="min-h-screen bg-bg-default flex items-center justify-center px-4">
      <div className="text-center">
        <span className="text-4xl">🔔</span>
        <h2 className="text-xl font-semibold text-text-primary mb-2 mt-4">
          Notifications Error
        </h2>
        <p className="text-text-secondary">
          Unable to load notifications. Please try again later.
        </p>
      </div>
    </div>
  );
};