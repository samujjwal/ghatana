/**
 * @ghatana/yappc-notifications
 * 
 * Real-time notification library for YAPPC.
 * Provides notification components and hooks for system notifications.
 * 
 * @module notifications
 */

// Hooks
export { useNotificationBackend } from './hooks/useNotificationBackend';
export type {
  UseNotificationBackendConfig,
  Notification,
  NotificationType,
  NotificationPriority,
  NotificationSendPayload,
  NotificationReadPayload,
  NotificationDismissPayload,
  NotificationState,
} from './hooks/useNotificationBackend';

// Components
export { NotificationBell } from './components/NotificationBell';
export type { NotificationBellProps } from './components/NotificationBell';

export { NotificationPanel } from './components/NotificationPanel';
export type { NotificationPanelProps } from './components/NotificationPanel';

export { NotificationItem } from './components/NotificationItem';
export type { NotificationItemProps } from './components/NotificationItem';
