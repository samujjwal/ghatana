/**
 * yappc-chat — Chat library
 *
 * Provides real-time chat hooks and components.
 *
 * @module chat
 */
export { useChatBackend } from './hooks/useChatBackend';
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
