/**
 * Messaging module — use @yappc/messaging directly.
 *
 * The canonical messaging implementation lives in `@yappc/messaging`.
 * This re-export exists for internal AI package compatibility only.
 */
export * from '@yappc/messaging';

export type {
  UseChatBackendConfig,
  ChatMessage,
  ChatReaction,
  TypingIndicator,
  ReadReceipt,
  ChatSendPayload,
  ChatTypingPayload,
  ChatReadPayload,
  ChatReactionPayload,
  ChatState,
} from './chat/index';
export { ChatPanel } from './chat/index';
export type { ChatPanelProps } from './chat/index';
export { ChatMessage as ChatMessageComponent } from './chat/index';
export type { ChatMessageProps } from './chat/index';

// Notifications
export { useNotificationBackend } from './notifications/index';
export type {
  UseNotificationBackendConfig,
  Notification,
  NotificationType,
  NotificationPriority,
  NotificationSendPayload,
  NotificationReadPayload,
  NotificationDismissPayload,
  NotificationState,
} from './notifications/index';
export { NotificationBell } from './notifications/index';
export type { NotificationBellProps } from './notifications/index';
export { NotificationPanel } from './notifications/index';
export type { NotificationPanelProps } from './notifications/index';
export { NotificationItem } from './notifications/index';
export type { NotificationItemProps } from './notifications/index';
