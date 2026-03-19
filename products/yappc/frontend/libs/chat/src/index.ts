/**
 * @yappc/chat — DEPRECATED
 *
 * @deprecated Use `@yappc/messaging` instead.
 * This package is a compatibility shim and will be removed in a future release.
 *
 * Migration: replace `@yappc/chat` imports with `@yappc/messaging` or
 * the scoped `@yappc/messaging/chat` sub-path.
 *
 * @module chat
 */

// Re-export everything from the canonical messaging library
export * from '@yappc/messaging/chat';

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
} from './hooks/useChatBackend';

// Components
export { ChatPanel } from './components/ChatPanel';
export type { ChatPanelProps } from './components/ChatPanel';

export { ChatMessage as ChatMessageComponent } from './components/ChatMessage';
export type { ChatMessageProps } from './components/ChatMessage';
