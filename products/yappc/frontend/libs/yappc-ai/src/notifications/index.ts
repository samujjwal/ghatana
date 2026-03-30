/**
 * @yappc/ai notifications — real-time notification functionality
 */
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

export { NotificationBell } from './components/NotificationBell';
export type { NotificationBellProps } from './components/NotificationBell';

export { NotificationPanel } from './components/NotificationPanel';
export type { NotificationPanelProps } from './components/NotificationPanel';

export { NotificationItem } from './components/NotificationItem';
export type { NotificationItemProps } from './components/NotificationItem';
