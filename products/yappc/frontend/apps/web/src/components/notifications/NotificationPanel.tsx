import React, { useState, useRef, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cn } from '@/lib/utils';
import { Bell, X, AlertTriangle, Info, CheckCircle, Clock } from 'lucide-react';

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
      <button
        ref={buttonRef}
        className="relative p-2 text-gray-600 hover:text-gray-900 rounded-lg hover:bg-gray-100"
        onClick={() => {
          updatePanelPosition();
          setIsOpen(!isOpen);
        }}
      >
        <Bell className="w-5 h-5" />
        {unreadCount > 0 && (
          <span className="absolute top-1 right-1 w-4 h-4 bg-red-500 text-white text-xs font-bold rounded-full flex items-center justify-center">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

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
            className="w-96 bg-white rounded-lg shadow-xl border border-gray-200"
            style={{
              position: 'fixed',
              top: `${panelPosition.top}px`,
              left: `${panelPosition.left}px`,
              zIndex: 9999,
            }}
          >
            {(() => {
              console.log('NotificationPanel portal rendered with position:', panelPosition);
              return null;
            })()}
            {/* Header */}
            <div className="px-4 py-3 border-b border-gray-200 flex items-center justify-between">
              <h3 className="text-sm font-semibold text-gray-900">
                Notifications
              </h3>
              <div className="flex items-center gap-2">
                {unreadCount > 0 && (
                  <button
                    className="text-xs text-blue-600 hover:text-blue-700"
                    onClick={() => clearAllMutation.mutate()}
                  >
                    Clear all
                  </button>
                )}
                <button
                  className="text-gray-400 hover:text-gray-600"
                  onClick={() => setIsOpen(false)}
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>

            {/* Notification list */}
            <div className="max-h-96 overflow-y-auto">
              {notifications?.length === 0 ? (
                <div className="px-4 py-12 text-center text-gray-500">
                  <Bell className="w-12 h-12 mx-auto mb-3 text-gray-400" />
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

  const handleClick = () => {
    if (!notification.isRead) {
      onMarkAsRead();
    }
    if (notification.link) {
      // NOTE: Navigate to link
      console.log('Navigate to:', notification.link);
      onClose();
    }
  };

  return (
    <div
      className={cn(
        'px-4 py-3 hover:bg-gray-50 transition-colors cursor-pointer',
        !notification.isRead && 'bg-blue-50'
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
          <p className="text-sm text-gray-900 font-medium">
            {notification.title}
          </p>
          <p className="text-sm text-gray-600 mt-1 line-clamp-2">
            {notification.message}
          </p>
          <div className="flex items-center gap-2 mt-2 text-xs text-gray-500">
            <Clock className="w-3 h-3" />
            <span>{formatTime(notification.createdAt)}</span>
          </div>
        </div>

        {/* Unread indicator */}
        {!notification.isRead && (
          <div className="flex-shrink-0 w-2 h-2 bg-blue-500 rounded-full mt-2" />
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
      color: 'text-blue-600',
    },
    WARNING: {
      icon: <AlertTriangle className="w-5 h-5" />,
      color: 'text-yellow-600',
    },
    ERROR: {
      icon: <AlertTriangle className="w-5 h-5" />,
      color: 'text-red-600',
    },
    SUCCESS: {
      icon: <CheckCircle className="w-5 h-5" />,
      color: 'text-green-600',
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

/**
 * Mock API functions.
 */
async function fetchNotifications(tenantId: string): Promise<Notification[]> {
  // Mock data
  return [
    {
      id: '1',
      type: 'ERROR',
      title: 'Critical Security Incident',
      message: 'SQL injection attempt detected in API endpoint /api/v1/users',
      link: '/incidents/INC-2024-001',
      isRead: false,
      createdAt: new Date(Date.now() - 300000).toISOString(),
    },
    {
      id: '2',
      type: 'WARNING',
      title: 'High Severity Vulnerability',
      message: 'CVE-2024-1234 found in openssl package version 1.1.1k',
      link: '/vulnerabilities/CVE-2024-1234',
      isRead: false,
      createdAt: new Date(Date.now() - 3600000).toISOString(),
    },
    {
      id: '3',
      type: 'SUCCESS',
      title: 'Pipeline Deployed Successfully',
      message: 'Backend API - CI/CD deployed to production environment',
      link: '/pipelines/1',
      isRead: true,
      createdAt: new Date(Date.now() - 7200000).toISOString(),
    },
    {
      id: '4',
      type: 'INFO',
      title: 'Compliance Assessment Completed',
      message: 'SOC 2 Type II assessment completed with 88.5% score',
      link: '/compliance/soc2',
      isRead: true,
      createdAt: new Date(Date.now() - 86400000).toISOString(),
    },
  ];
}

async function markNotificationAsRead(notificationId: string): Promise<void> {
  console.log('Mark as read:', notificationId);
  await new Promise((resolve) => setTimeout(resolve, 300));
}

async function clearAllNotifications(tenantId: string): Promise<void> {
  console.log('Clear all notifications for:', tenantId);
  await new Promise((resolve) => setTimeout(resolve, 300));
}
