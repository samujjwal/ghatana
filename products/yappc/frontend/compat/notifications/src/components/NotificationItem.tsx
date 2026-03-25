/**
 * Notification Item Component
 * 
 * Displays individual notification with type-specific styling and actions.
 * 
 * @module notifications/components
 */

import React, { useCallback } from 'react';
import { formatDistanceToNow } from 'date-fns';
import { 
  Info, 
  CheckCircle, 
  AlertTriangle, 
  XCircle, 
  AtSign, 
  ClipboardCheck, 
  FileCheck, 
  Bell,
  X,
  ExternalLink,
} from 'lucide-react';
import type { Notification, NotificationType } from '../hooks/useNotificationBackend';

export interface NotificationItemProps {
  notification: Notification;
  onRead?: (notificationId: string) => void;
  onDismiss?: (notificationId: string) => void;
  onAction?: (notification: Notification) => void;
}

/**
 * Get icon for notification type
 */
const getNotificationIcon = (type: NotificationType) => {
  const iconMap = {
    info: Info,
    success: CheckCircle,
    warning: AlertTriangle,
    error: XCircle,
    mention: AtSign,
    assignment: ClipboardCheck,
    approval: FileCheck,
    system: Bell,
  };
  
  return iconMap[type] || Info;
};

/**
 * Get color classes for notification type
 */
const getNotificationColors = (type: NotificationType) => {
  const colorMap = {
    info: {
      bg: 'bg-blue-500/10',
      border: 'border-blue-500/30',
      icon: 'text-blue-400',
      badge: 'bg-blue-500',
    },
    success: {
      bg: 'bg-emerald-500/10',
      border: 'border-emerald-500/30',
      icon: 'text-emerald-400',
      badge: 'bg-emerald-500',
    },
    warning: {
      bg: 'bg-amber-500/10',
      border: 'border-amber-500/30',
      icon: 'text-amber-400',
      badge: 'bg-amber-500',
    },
    error: {
      bg: 'bg-red-500/10',
      border: 'border-red-500/30',
      icon: 'text-red-400',
      badge: 'bg-red-500',
    },
    mention: {
      bg: 'bg-violet-500/10',
      border: 'border-violet-500/30',
      icon: 'text-violet-400',
      badge: 'bg-violet-500',
    },
    assignment: {
      bg: 'bg-cyan-500/10',
      border: 'border-cyan-500/30',
      icon: 'text-cyan-400',
      badge: 'bg-cyan-500',
    },
    approval: {
      bg: 'bg-green-500/10',
      border: 'border-green-500/30',
      icon: 'text-green-400',
      badge: 'bg-green-500',
    },
    system: {
      bg: 'bg-zinc-500/10',
      border: 'border-zinc-500/30',
      icon: 'text-zinc-400',
      badge: 'bg-zinc-500',
    },
  };
  
  return colorMap[type] || colorMap.info;
};

/**
 * Notification Item Component
 * 
 * Renders a single notification with appropriate styling and actions.
 */
export const NotificationItem: React.FC<NotificationItemProps> = ({
  notification,
  onRead,
  onDismiss,
  onAction,
}) => {
  const Icon = getNotificationIcon(notification.type);
  const colors = getNotificationColors(notification.type);

  const handleClick = useCallback(() => {
    if (!notification.read) {
      onRead?.(notification.id);
    }
    if (notification.actionUrl) {
      onAction?.(notification);
    }
  }, [notification, onRead, onAction]);

  const handleDismiss = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    onDismiss?.(notification.id);
  }, [notification.id, onDismiss]);

  return (
    <div
      className={`
        relative group p-4 rounded-lg border transition-all
        ${notification.read ? 'bg-zinc-900/50 border-zinc-800' : `${colors.bg} ${colors.border}`}
        ${notification.actionUrl ? 'cursor-pointer hover:bg-zinc-800/50' : ''}
      `}
      onClick={handleClick}
    >
      {/* Unread indicator */}
      {!notification.read && (
        <div className={`absolute left-2 top-1/2 -translate-y-1/2 w-2 h-2 rounded-full ${colors.badge}`} />
      )}

      <div className="flex gap-3 ml-2">
        {/* Icon */}
        <div className={`flex-shrink-0 ${colors.icon}`}>
          <Icon className="w-5 h-5" />
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          {/* Title */}
          <div className="flex items-start justify-between gap-2 mb-1">
            <h4 className="font-medium text-sm text-white">
              {notification.title}
            </h4>
            <button
              onClick={handleDismiss}
              className="opacity-0 group-hover:opacity-100 p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-white transition-all"
              title="Dismiss"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* Message */}
          <p className="text-sm text-zinc-400 mb-2">
            {notification.message}
          </p>

          {/* Footer */}
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs text-zinc-500">
              {formatDistanceToNow(notification.timestamp, { addSuffix: true })}
            </span>

            {/* Action button */}
            {notification.actionUrl && notification.actionLabel && (
              <button
                className={`
                  flex items-center gap-1 px-2 py-1 rounded text-xs font-medium
                  ${colors.icon} hover:bg-zinc-800 transition-colors
                `}
              >
                {notification.actionLabel}
                <ExternalLink className="w-3 h-3" />
              </button>
            )}
          </div>

          {/* Priority badge */}
          {notification.priority === 'urgent' && (
            <div className="absolute top-2 right-2">
              <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-red-500 text-white">
                Urgent
              </span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default NotificationItem;
