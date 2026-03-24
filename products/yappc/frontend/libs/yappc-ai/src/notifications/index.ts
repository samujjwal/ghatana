/**
 * @yappc/notifications — DEPRECATED
 *
 * @deprecated Use `@yappc/messaging` instead.
 * This package is a compatibility shim and will be removed in a future release.
 *
 * Migration: replace `@yappc/notifications` imports with `@yappc/messaging` or
 * the scoped `@yappc/messaging/notifications` sub-path.
 *
 * @module notifications
 */

// Re-export everything from the canonical messaging library
export * from '@yappc/ai/messaging/notifications';

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
