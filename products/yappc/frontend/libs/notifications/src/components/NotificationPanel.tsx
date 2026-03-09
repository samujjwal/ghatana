/**
 * Notification Panel Component
 * 
 * Dropdown panel displaying notification list with filtering and actions.
 * 
 * @module notifications/components
 */

import React, { useState, useCallback } from 'react';
import { CheckCheck, Trash2, Filter } from 'lucide-react';
import { NotificationItem } from './NotificationItem';
import type { Notification, NotificationType } from '../hooks/useNotificationBackend';

export interface NotificationPanelProps {
  notifications: Notification[];
  unreadCount: number;
  onRead: (notificationId: string) => void;
  onDismiss: (notificationId: string) => void;
  onMarkAllAsRead: () => void;
  onClearAll: () => void;
  onAction?: (notification: Notification) => void;
}

type FilterType = 'all' | NotificationType;

/**
 * Notification Panel Component
 * 
 * Complete notification interface with filtering and bulk actions.
 * 
 * @example
 * ```tsx
 * <NotificationPanel
 *   notifications={notifications}
 *   unreadCount={unreadCount}
 *   onRead={(id) => notif.markAsRead(id)}
 *   onDismiss={(id) => notif.dismiss(id)}
 *   onMarkAllAsRead={() => notif.markAllAsRead()}
 *   onClearAll={() => notif.clearAll()}
 *   onAction={(notif) => navigate(notif.actionUrl)}
 * />
 * ```
 */
export const NotificationPanel: React.FC<NotificationPanelProps> = ({
  notifications,
  unreadCount,
  onRead,
  onDismiss,
  onMarkAllAsRead,
  onClearAll,
  onAction,
}) => {
  const [filter, setFilter] = useState<FilterType>('all');
  const [showFilters, setShowFilters] = useState(false);

  // Filter notifications
  const filteredNotifications = notifications.filter((n) => {
    if (filter === 'all') return true;
    return n.type === filter;
  });

  // Group by read status
  const unreadNotifications = filteredNotifications.filter((n) => !n.read);
  const readNotifications = filteredNotifications.filter((n) => n.read);

  const handleFilterChange = useCallback((newFilter: FilterType) => {
    setFilter(newFilter);
    setShowFilters(false);
  }, []);

  return (
    <div className="w-96 max-h-[600px] flex flex-col bg-zinc-900 border border-zinc-800 rounded-lg shadow-xl">
      {/* Header */}
      <div className="p-4 border-b border-zinc-800">
        <div className="flex items-center justify-between mb-3">
          <h3 className="font-semibold text-white">
            Notifications
            {unreadCount > 0 && (
              <span className="ml-2 px-2 py-0.5 rounded-full text-xs bg-violet-500 text-white">
                {unreadCount}
              </span>
            )}
          </h3>

          {/* Filter button */}
          <button
            onClick={() => setShowFilters(!showFilters)}
            className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
            title="Filter"
          >
            <Filter className="w-4 h-4" />
          </button>
        </div>

        {/* Filter options */}
        {showFilters && (
          <div className="flex flex-wrap gap-1 mb-3">
            {(['all', 'info', 'success', 'warning', 'error', 'mention', 'assignment', 'approval', 'system'] as FilterType[]).map((f) => (
              <button
                key={f}
                onClick={() => handleFilterChange(f)}
                className={`
                  px-2 py-1 rounded text-xs font-medium transition-colors
                  ${filter === f 
                    ? 'bg-violet-500 text-white' 
                    : 'bg-zinc-800 text-zinc-400 hover:bg-zinc-700 hover:text-white'
                  }
                `}
              >
                {f.charAt(0).toUpperCase() + f.slice(1)}
              </button>
            ))}
          </div>
        )}

        {/* Actions */}
        {notifications.length > 0 && (
          <div className="flex gap-2">
            <button
              onClick={onMarkAllAsRead}
              disabled={unreadCount === 0}
              className="flex items-center gap-1 px-3 py-1.5 rounded text-xs font-medium bg-zinc-800 text-zinc-400 hover:bg-zinc-700 hover:text-white disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              <CheckCheck className="w-3 h-3" />
              Mark all read
            </button>
            <button
              onClick={onClearAll}
              className="flex items-center gap-1 px-3 py-1.5 rounded text-xs font-medium bg-zinc-800 text-zinc-400 hover:bg-zinc-700 hover:text-white transition-colors"
            >
              <Trash2 className="w-3 h-3" />
              Clear all
            </button>
          </div>
        )}
      </div>

      {/* Notification list */}
      <div className="flex-1 overflow-y-auto">
        {filteredNotifications.length === 0 ? (
          <div className="flex items-center justify-center h-48 text-zinc-500">
            <p className="text-sm">
              {filter === 'all' ? 'No notifications' : `No ${filter} notifications`}
            </p>
          </div>
        ) : (
          <div className="p-2 space-y-2">
            {/* Unread notifications */}
            {unreadNotifications.length > 0 && (
              <>
                <div className="px-2 py-1 text-xs font-medium text-zinc-500">
                  Unread ({unreadNotifications.length})
                </div>
                {unreadNotifications.map((notification) => (
                  <NotificationItem
                    key={notification.id}
                    notification={notification}
                    onRead={onRead}
                    onDismiss={onDismiss}
                    onAction={onAction}
                  />
                ))}
              </>
            )}

            {/* Read notifications */}
            {readNotifications.length > 0 && (
              <>
                {unreadNotifications.length > 0 && (
                  <div className="px-2 py-1 text-xs font-medium text-zinc-500 mt-4">
                    Read ({readNotifications.length})
                  </div>
                )}
                {readNotifications.map((notification) => (
                  <NotificationItem
                    key={notification.id}
                    notification={notification}
                    onRead={onRead}
                    onDismiss={onDismiss}
                    onAction={onAction}
                  />
                ))}
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default NotificationPanel;
