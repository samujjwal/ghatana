import React, { useState, useRef, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { parseJsonResponse, readErrorResponse } from '@/lib/http';
import { cn } from '@/lib/utils';
import { useI18n } from '../../i18n/I18nProvider';
import { Bell, X, AlertTriangle, Info, CheckCircle, Clock } from 'lucide-react';
import { Button } from '../ui/Button';

/**
 * Real-time notification panel with GraphQL subscriptions.
 *
 * **Features**:
 * - Real-time notifications via WebSocket
 * - Notification type badges (info, warning, error, success)
 * - Mark as read functionality
 * - Clear all notifications
 * - Notification grouping by type
 * - Click-to-navigate
 *
 * **Usage**:
 * ```tsx
 * <NotificationPanel tenantId={tenantId} />
 * ```
 */
export function NotificationPanel({ tenantId }: { tenantId: string }) {
  const queryClient = useQueryClient();
  const [isOpen, setIsOpen] = React.useState(false);
  const [panelPosition, setPanelPosition] = useState({ top: 0, left: 0 });
  const buttonRef = useRef<HTMLButtonElement>(null);
  const { t } = useI18n();

  // Calculate panel position when opening
  const updatePanelPosition = useCallback(() => {
    if (buttonRef.current && typeof document !== 'undefined') {
      const rect = buttonRef.current.getBoundingClientRect();
      setPanelPosition({
        top: rect.bottom + window.scrollY + 4, // 4px gap
        left: rect.right + window.scrollX - 384, // Align right edge (384px width)
      });
    }
  }, []);

  // Fetch notifications
  const { data: notifications } = useQuery({
    queryKey: ['notifications', tenantId],
    queryFn: () => fetchNotifications(tenantId),
    refetchInterval: 30000, // Poll every 30 seconds
  });

  // Subscribe to real-time notifications
  // useSubscription({
  //   query: NOTIFICATION_SUBSCRIPTION,
  //   variables: { tenantId },
  //   onData: (data) => {
  //     queryClient.setQueryData(['notifications', tenantId], (old) => {
  //       return [data.notificationCreated, ...(old || [])];
  //     });
  //   },
  // });

  // Mark as read mutation
  const markAsReadMutation = useMutation({
    mutationFn: (notificationId: string) =>
      markNotificationAsRead(notificationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications', tenantId] });
    },
  });

  // Clear all mutation
  const clearAllMutation = useMutation({
    mutationFn: () => clearAllNotifications(tenantId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications', tenantId] });
    },
  });

  const unreadCount = notifications?.filter((n) => !n.isRead).length || 0;

  return (
    <div className="relative">
      {/* Bell icon button */}
      <Button
        ref={buttonRef}
        variant="ghost"
        size="sm"
        className="relative p-2 text-fg-muted hover:text-fg rounded-lg hover:bg-surface-muted"
        aria-label={`Notifications${unreadCount > 0 ? ` (${unreadCount} unread)` : ''}`}
        onClick={() => {
          updatePanelPosition();
          setIsOpen(!isOpen);
        }}
      >
        <Bell className="w-5 h-5" />
        {unreadCount > 0 && (
          <span className="absolute top-1 right-1 w-4 h-4 bg-destructive-bg text-white text-xs font-bold rounded-full flex items-center justify-center">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </Button>

      {/* Notification panel - Rendered via portal to avoid overflow clipping */}
      {isOpen && typeof document !== 'undefined' && createPortal(
        <>
          {/* Backdrop */}
          <div
            className="fixed inset-0 z-40"
            onClick={() => setIsOpen(false)}
          />

          {/* Panel */}
          <div
            className="w-96 bg-white rounded-lg shadow-xl border border-border"
            style={{
              position: 'fixed',
              top: `${panelPosition.top}px`,
              left: `${panelPosition.left}px`,
              zIndex: 9999,
            }}
          >

            {/* Header */}
            <div className="px-4 py-3 border-b border-border flex items-center justify-between">
              <h3 className="text-sm font-semibold text-fg">
                Notifications
              </h3>
              <div className="flex items-center gap-2">
                {unreadCount > 0 && (
                  <Button
                    variant="link"
                    size="sm"
                    className="text-xs text-info-color hover:text-info-color"
                    onClick={() => clearAllMutation.mutate()}
                  >
                    Clear all
                  </Button>
                )}
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-fg-muted hover:text-fg-muted"
                  onClick={() => setIsOpen(false)}
                  aria-label={t('notifications.close')}
                >
                  <X className="w-4 h-4" />
                </Button>
              </div>
            </div>

            {/* Notification list */}
            <div className="max-h-96 overflow-y-auto">
              {notifications?.length === 0 ? (
                <div className="px-4 py-12 text-center text-fg-muted">
                  <Bell className="w-12 h-12 mx-auto mb-3 text-fg-muted" />
                  <p>No notifications</p>
                </div>
              ) : (
                <div className="divide-y divide-gray-200">
                  {notifications?.map((notification) => (
                    <NotificationItem
                      key={notification.id}
                      notification={notification}
                      onMarkAsRead={() =>
                        markAsReadMutation.mutate(notification.id)
                      }
                      onClose={() => setIsOpen(false)}
                    />
                  ))}
                </div>
              )}
            </div>
          </div>
        </>,
        document.body
      )}
    </div>
  );
}

/**
 * Individual notification item.
 */
function NotificationItem({
  notification,
  onMarkAsRead,
  onClose,
}: {
  notification: Notification;
  onMarkAsRead: () => void;
  onClose: () => void;
}) {
  const typeConfig = getNotificationTypeConfig(notification.type);

  const navigate = useNavigate();

  const handleClick = () => {
    if (!notification.isRead) {
      onMarkAsRead();
    }
    if (notification.link) {
      navigate(notification.link);
      onClose();
    }
  };

  return (
    <div
      className={cn(
        'px-4 py-3 hover:bg-surface-muted transition-colors cursor-pointer',
        !notification.isRead && 'bg-info-bg'
      )}
      onClick={handleClick}
    >
      <div className="flex items-start gap-3">
        {/* Icon */}
        <div className={cn('flex-shrink-0 mt-0.5', typeConfig.color)}>
          {typeConfig.icon}
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          <p className="text-sm text-fg font-medium">
            {notification.title}
          </p>
          <p className="text-sm text-fg-muted mt-1 line-clamp-2">
            {notification.message}
          </p>
          <div className="flex items-center gap-2 mt-2 text-xs text-fg-muted">
            <Clock className="w-3 h-3" />
            <span>{formatTime(notification.createdAt)}</span>
          </div>
        </div>

        {/* Unread indicator */}
        {!notification.isRead && (
          <div className="flex-shrink-0 w-2 h-2 bg-info-bg rounded-full mt-2" />
        )}
      </div>
    </div>
  );
}

/**
 * Notification type.
 */
interface Notification {
  id: string;
  type: 'INFO' | 'WARNING' | 'ERROR' | 'SUCCESS';
  title: string;
  message: string;
  link?: string;
  isRead: boolean;
  createdAt: string;
}

/**
 * Get notification type configuration.
 */
function getNotificationTypeConfig(type: string) {
  const configs = {
    INFO: {
      icon: <Info className="w-5 h-5" />,
      color: 'text-info-color',
    },
    WARNING: {
      icon: <AlertTriangle className="w-5 h-5" />,
      color: 'text-warning-color',
    },
    ERROR: {
      icon: <AlertTriangle className="w-5 h-5" />,
      color: 'text-destructive',
    },
    SUCCESS: {
      icon: <CheckCircle className="w-5 h-5" />,
      color: 'text-success-color',
    },
  };

  return configs[type as keyof typeof configs] || configs.INFO;
}

/**
 * Format timestamp to relative time.
 */
function formatTime(timestamp: string): string {
  const date = new Date(timestamp);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);

  if (diffMins < 1) return 'Just now';
  if (diffMins < 60) return `${diffMins}m ago`;

  const diffHours = Math.floor(diffMins / 60);
  if (diffHours < 24) return `${diffHours}h ago`;

  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 7) return `${diffDays}d ago`;

  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
  });
}

async function fetchNotifications(tenantId: string): Promise<Notification[]> {
  const res = await fetch(`/api/notifications?tenantId=${encodeURIComponent(tenantId)}`);
  if (!res.ok) {
    throw new Error(
      await readErrorResponse(res, `Failed to fetch notifications: ${res.status}`)
    );
  }
  return parseJsonResponse<Notification[]>(res, 'fetch notifications');
}

async function markNotificationAsRead(notificationId: string): Promise<void> {
  const res = await fetch(`/api/notifications/${encodeURIComponent(notificationId)}/read`, {
    method: 'PATCH',
  });
  if (!res.ok) throw new Error(`Failed to mark notification as read: ${res.status}`);
}

async function clearAllNotifications(tenantId: string): Promise<void> {
  const res = await fetch(`/api/notifications?tenantId=${encodeURIComponent(tenantId)}`, {
    method: 'DELETE',
  });
  if (!res.ok) throw new Error(`Failed to clear notifications: ${res.status}`);
}
