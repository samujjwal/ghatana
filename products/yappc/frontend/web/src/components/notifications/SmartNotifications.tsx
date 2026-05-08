/**
 * Smart Notifications Component
 *
 * Displays AI-prioritized and consolidated notifications.
 * Provides contextual notification delivery with smart grouping.
 *
 * @doc.type component
 * @doc.purpose AI-powered notification display
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { ReactNode, useState, useCallback } from 'react';
import {
  Bell as BellIcon,
  X as CloseIcon,
  CheckCircle as SuccessIcon,
  Check as CheckIcon,
  AlertTriangle as WarningIcon,
  AlertCircle as ErrorIcon,
  Info as InfoIcon,
  Settings as SettingsIcon,
  ChevronDown as ExpandIcon,
} from 'lucide-react';
import { Typography, Button, Box, Card, CardContent, CardActions, Chip, Badge } from '@ghatana/design-system';
import { useSmartNotifications, useNotificationToast } from '../../hooks/useSmartNotifications';
import type { Notification, ConsolidatedNotification, NotificationType } from '../../services/ai/NotificationService';

const NativeInput = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  (props, ref) => React.createElement('input', { ...props, ref })
);

NativeInput.displayName = 'NativeInput';

// ============================================================================
// Types
// ============================================================================

export interface SmartNotificationsProps {
  position?: 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left';
  maxVisible?: number;
  showPreferences?: boolean;
  className?: string;
}

// ============================================================================
// Notification Icon Component
// ============================================================================

interface NotificationIconProps {
  type: NotificationType;
}

function NotificationIcon({ type }: NotificationIconProps): ReactNode {
  const icons = {
    success: <SuccessIcon className="w-5 h-5" />,
    warning: <WarningIcon className="w-5 h-5" />,
    error: <ErrorIcon className="w-5 h-5" />,
    info: <InfoIcon className="w-5 h-5" />,
    task: <CheckIcon className="w-5 h-5" />,
    mention: <BellIcon className="w-5 h-5" />,
    system: <SettingsIcon className="w-5 h-5" />,
  };

  return icons[type] || icons.info;
}

// ============================================================================
// Notification Item Component
// ============================================================================

interface NotificationItemProps {
  notification: Notification;
  onDismiss: () => void;
  onMarkRead: () => void;
  onAction?: (actionId: string) => void;
}

function NotificationItem({ notification, onDismiss, onMarkRead, onAction }: NotificationItemProps): ReactNode {
  const getTypeColor = (type: NotificationType): string => {
    switch (type) {
      case 'success':
        return 'border-success-border dark:border-success-border bg-success-bg dark:bg-success-bg/20';
      case 'warning':
        return 'border-warning-border dark:border-warning-border bg-warning-bg dark:bg-warning-bg/20';
      case 'error':
        return 'border-destructive-border dark:border-destructive-border bg-destructive-bg dark:bg-destructive-bg/20';
      case 'task':
        return 'border-info-border dark:border-info-border bg-info-bg dark:bg-info-bg/20';
      case 'mention':
        return 'border-info-border dark:border-info-border bg-info-bg dark:bg-info-bg/20';
      default:
        return 'border-border dark:border-border bg-surface-muted dark:bg-surface';
    }
  };

  return (
    <Card className={`mb-2 ${getTypeColor(notification.type)} ${!notification.read ? 'border-l-4' : ''}`}>
      <CardContent className="p-3">
        <div className="flex items-start gap-3">
          <div className={`mt-0.5 ${notification.type === 'error' ? 'text-destructive dark:text-destructive' : notification.type === 'warning' ? 'text-warning-color dark:text-warning-color' : notification.type === 'success' ? 'text-success-color dark:text-success-color' : 'text-info-color dark:text-info-color'}`}>
            <NotificationIcon type={notification.type} />
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-start justify-between mb-1">
              <Typography className="font-medium text-sm">{notification.title}</Typography>
              <Button
                size="sm"
                variant="text"
                onClick={onDismiss}
                className="text-fg-muted hover:text-fg-muted"
              >
                <CloseIcon className="w-4 h-4" />
              </Button>
            </div>
            <Typography className="text-sm text-fg dark:text-fg-muted mb-2">
              {notification.message}
            </Typography>
            {notification.actions && notification.actions.length > 0 && (
              <div className="flex gap-2">
                {notification.actions.map(action => (
                  <Button
                    key={action.id}
                    size="sm"
                    variant={action.primary ? 'contained' : 'outlined'}
                    onClick={() => onAction?.(action.id)}
                  >
                    {action.label}
                  </Button>
                ))}
              </div>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

// ============================================================================
// Consolidated Notification Component
// ============================================================================

interface ConsolidatedNotificationItemProps {
  consolidated: ConsolidatedNotification;
  onDismiss: () => void;
  onExpand: () => void;
  isExpanded: boolean;
}

function ConsolidatedNotificationItem({
  consolidated,
  onDismiss,
  onExpand,
  isExpanded,
}: ConsolidatedNotificationItemProps): ReactNode {
  const getTypeColor = (type: NotificationType): string => {
    switch (type) {
      case 'success':
        return 'border-success-border dark:border-success-border bg-success-bg dark:bg-success-bg/20';
      case 'warning':
        return 'border-warning-border dark:border-warning-border bg-warning-bg dark:bg-warning-bg/20';
      case 'error':
        return 'border-destructive-border dark:border-destructive-border bg-destructive-bg dark:bg-destructive-bg/20';
      case 'task':
        return 'border-info-border dark:border-info-border bg-info-bg dark:bg-info-bg/20';
      case 'mention':
        return 'border-info-border dark:border-info-border bg-info-bg dark:bg-info-bg/20';
      default:
        return 'border-border dark:border-border bg-surface-muted dark:bg-surface';
    }
  };

  return (
    <Card className={`mb-2 ${getTypeColor(consolidated.type)}`}>
      <CardContent className="p-3">
        <div className="flex items-start gap-3">
          <div className="mt-0.5">
            <NotificationIcon type={consolidated.type} />
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-start justify-between mb-1">
              <Typography className="font-medium text-sm">{consolidated.title}</Typography>
              <div className="flex items-center gap-1">
                <span className="absolute -top-1 -right-1 bg-primary text-white text-xs rounded-full w-5 h-5 flex items-center justify-center">
                  {consolidated.count}
                </span>
                <Button
                  size="sm"
                  variant="text"
                  onClick={onDismiss}
                  className="text-fg-muted hover:text-fg-muted"
                >
                  <CloseIcon className="w-4 h-4" />
                </Button>
              </div>
            </div>
            <Button
              size="sm"
              variant="text"
              onClick={onExpand}
              className="text-xs text-fg-muted"
            >
              {isExpanded ? 'Show less' : 'Show all'}
            </Button>
            {isExpanded && (
              <div className="mt-2 space-y-2">
                {consolidated.notifications.map(notification => (
                  <div key={notification.id} className="text-xs text-fg-muted dark:text-fg-muted p-2 bg-white dark:bg-surface rounded">
                    {notification.title}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

// ============================================================================
// Smart Notifications Component
// ============================================================================

/**
 * Smart Notifications Component
 */
export function SmartNotifications({
  position = 'top-right',
  maxVisible = 5,
  showPreferences = false,
  className = '',
}: SmartNotificationsProps): ReactNode {
  const { consolidated, markAsRead, removeNotification, markAllAsRead, clearExpired, unreadCount, preferences, updatePreferences } = useSmartNotifications();
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [showPanel, setShowPanel] = useState(false);

  const getPositionStyles = (): string => {
    switch (position) {
      case 'top-right':
        return 'fixed top-4 right-4';
      case 'top-left':
        return 'fixed top-4 left-4';
      case 'bottom-right':
        return 'fixed bottom-4 right-4';
      case 'bottom-left':
        return 'fixed bottom-4 left-4';
      default:
        return 'fixed top-4 right-4';
    }
  };

  const handleDismiss = useCallback((id: string) => {
    removeNotification(id);
  }, [removeNotification]);

  const handleExpand = useCallback((id: string) => {
    setExpandedId(expandedId === id ? null : id);
  }, [expandedId]);

  const visibleNotifications = consolidated.slice(0, maxVisible);

  return (
    <div className={`${getPositionStyles()} z-50 ${className}`}>
      {/* Notification Bell */}
      <div className="relative">
        <Button
          size="sm"
          variant="text"
          onClick={() => setShowPanel(!showPanel)}
          className="relative"
        >
          <BellIcon className="w-6 h-6" />
          {unreadCount > 0 && (
            <span className="absolute -top-1 -right-1 bg-destructive-bg text-white text-xs rounded-full w-5 h-5 flex items-center justify-center">
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </Button>
      </div>

      {/* Notification Panel */}
      {showPanel && (
        <Card className="w-80 max-h-96 overflow-y-auto shadow-2xl">
          <CardContent className="p-4">
            <div className="flex items-center justify-between mb-4">
              <Typography className="font-semibold">Notifications</Typography>
              <div className="flex items-center gap-2">
                {unreadCount > 0 && (
                  <Button
                    size="sm"
                    variant="text"
                    onClick={markAllAsRead}
                    className="text-xs"
                  >
                    Mark all read
                  </Button>
                )}
                <Button
                  size="sm"
                  variant="text"
                  onClick={() => setShowPanel(false)}
                >
                  <CloseIcon className="w-4 h-4" />
                </Button>
              </div>
            </div>

            {visibleNotifications.length === 0 ? (
              <Typography className="text-sm text-fg-muted text-center py-4">
                No notifications
              </Typography>
            ) : (
              <>
                {visibleNotifications.map(consolidated => (
                  <ConsolidatedNotificationItem
                    key={consolidated.id}
                    consolidated={consolidated}
                    onDismiss={() => handleDismiss(consolidated.id)}
                    onExpand={() => handleExpand(consolidated.id)}
                    isExpanded={expandedId === consolidated.id}
                  />
                ))}
                {consolidated.length > maxVisible && (
                  <Typography className="text-xs text-fg-muted text-center py-2">
                    +{consolidated.length - maxVisible} more
                  </Typography>
                )}
              </>
            )}

            {/* Preferences */}
            {showPreferences && (
              <div className="mt-4 pt-4 border-t border-border dark:border-border">
                <Typography className="text-sm font-medium mb-2">Preferences</Typography>
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <Typography className="text-xs">Consolidate notifications</Typography>
                    <NativeInput
                      type="checkbox"
                      checked={preferences.enableConsolidation}
                      onChange={(e) => updatePreferences({ enableConsolidation: e.target.checked })}
                    />
                  </div>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
