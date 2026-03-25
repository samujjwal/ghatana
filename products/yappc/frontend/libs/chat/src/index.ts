/**
 * @yappc/chat — REMOVED
 *
 * This package has been consolidated into `@yappc/messaging`.
 * All chat functionality is now owned by `@yappc/messaging`.
 *
 * @module chat
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
} from './hooks/useChatBackend';

// Components
export { ChatPanel } from './components/ChatPanel';
export type { ChatPanelProps } from './components/ChatPanel';

export { ChatMessage as ChatMessageComponent } from './components/ChatMessage';
export type { ChatMessageProps } from './components/ChatMessage';
