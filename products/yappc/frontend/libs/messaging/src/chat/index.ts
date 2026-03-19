/**
 * @ghatana/yappc-chat
 * 
 * Real-time chat library for YAPPC.
 * Provides chat components and hooks for team messaging.
 * 
 * @module chat
 */

// Hooks
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
