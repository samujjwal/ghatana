/**
 * @yappc/notifications — REMOVED
 *
 * This package has been consolidated into `@yappc/messaging`.
 * All notification functionality is now owned by `@yappc/messaging`.
 *
 * @module notifications
 */
export * from '@yappc/messaging';


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
