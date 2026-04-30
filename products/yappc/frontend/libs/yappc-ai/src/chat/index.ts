/**
 * yappc-ai chat — real-time chat functionality
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

export { ChatPanel } from './components/ChatPanel';
export type { ChatPanelProps } from './components/ChatPanel';

export { ChatMessage as ChatMessageComponent } from './components/ChatMessage';
export type { ChatMessageProps } from './components/ChatMessage';
