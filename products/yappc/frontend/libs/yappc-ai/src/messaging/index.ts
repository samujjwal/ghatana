/**
 * Messaging module — use yappc-chat directly.
 *
 * @deprecated Use yappc-chat for chat functionality. This re-export module is no longer functional.
 */
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
export { ChatMessageComponent } from './chat/index';
export type { ChatMessageProps } from './chat/index';

// Notifications
// NOTE: Consolidated to yappc-ai/notifications - re-exporting from canonical location
export { useNotificationBackend } from '../notifications/index';
export type {
  UseNotificationBackendConfig,
  Notification,
  NotificationType,
  NotificationPriority,
  NotificationSendPayload,
  NotificationReadPayload,
  NotificationDismissPayload,
  NotificationState,
} from '../notifications/index';
export { NotificationBell } from '../notifications/index';
export type { NotificationBellProps } from '../notifications/index';
export { NotificationPanel } from '../notifications/index';
export type { NotificationPanelProps } from '../notifications/index';
export { NotificationItem } from '../notifications/index';
export type { NotificationItemProps } from '../notifications/index';
