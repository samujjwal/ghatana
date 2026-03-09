/**
 * Chat Backend Integration Hook
 * 
 * Integrates chat functionality with backend ChatHandler via WebSocket.
 * Handles message sending, typing indicators, read receipts, and reactions.
 * 
 * @module chat/hooks
 * @doc.type integration
 * @doc.purpose Real-time team chat with backend
 */

import { useEffect, useCallback, useState, useRef } from 'react';
import { WebSocketClient } from '@ghatana/yappc-realtime';

/**
 * Chat message structure
 */
export interface ChatMessage {
  id: string;
  channelId: string;
  userId: string;
  userName: string;
  userAvatar?: string;
  content: string;
  timestamp: number;
  edited?: boolean;
  editedAt?: number;
  reactions?: ChatReaction[];
  threadId?: string;
  replyTo?: string;
}

/**
 * Chat reaction
 */
export interface ChatReaction {
  emoji: string;
  userId: string;
  userName: string;
  timestamp: number;
}

/**
 * Typing indicator
 */
export interface TypingIndicator {
  userId: string;
  userName: string;
  channelId: string;
  timestamp: number;
}

/**
 * Read receipt
 */
export interface ReadReceipt {
  userId: string;
  userName: string;
  messageId: string;
  timestamp: number;
}

/**
 * Chat send payload
 */
export interface ChatSendPayload {
  channelId: string;
  content: string;
  threadId?: string;
  replyTo?: string;
}

/**
 * Typing payload
 */
export interface ChatTypingPayload {
  channelId: string;
  isTyping: boolean;
}

/**
 * Read receipt payload
 */
export interface ChatReadPayload {
  channelId: string;
  messageId: string;
}

/**
 * Reaction payload
 */
export interface ChatReactionPayload {
  messageId: string;
  emoji: string;
  action: 'add' | 'remove';
}

/**
 * Chat state
 */
export interface ChatState {
  messages: Map<string, ChatMessage[]>; // channelId -> messages
  typingUsers: Map<string, TypingIndicator[]>; // channelId -> typing users
  readReceipts: Map<string, ReadReceipt[]>; // messageId -> receipts
  isConnected: boolean;
}

/**
 * Hook configuration
 */
export interface UseChatBackendConfig {
  /** WebSocket client instance */
  wsClient: WebSocketClient;
  
  /** Current user ID */
  userId: string;
  
  /** Current user name */
  userName: string;
  
  /** User avatar URL */
  userAvatar?: string;
  
  /** Callback when message received */
  onMessageReceived?: (message: ChatMessage) => void;
  
  /** Callback when typing indicator received */
  onTypingUpdate?: (indicator: TypingIndicator) => void;
  
  /** Callback when read receipt received */
  onReadReceipt?: (receipt: ReadReceipt) => void;
  
  /** Callback when reaction received */
  onReaction?: (messageId: string, reaction: ChatReaction) => void;
  
  /** Enable debug logging */
  debug?: boolean;
}

/**
 * Chat Backend Integration Hook
 * 
 * Connects chat UI to backend ChatHandler via WebSocket.
 * Handles all chat operations with proper error handling and state management.
 * 
 * Features:
 * - Send/receive messages in real-time
 * - Typing indicators with auto-clear (5s timeout)
 * - Read receipts tracking
 * - Emoji reactions
 * - Thread support
 * - Message replies
 * - Automatic cleanup
 * 
 * @example
 * ```tsx
 * const chat = useChatBackend({
 *   wsClient,
 *   userId: user.id,
 *   userName: user.name,
 *   onMessageReceived: (msg) => {
 *     console.log('New message:', msg);
 *   },
 * });
 * 
 * // Send message
 * chat.sendMessage('channel-123', 'Hello team!');
 * 
 * // Send typing indicator
 * chat.sendTyping('channel-123', true);
 * 
 * // Mark as read
 * chat.markAsRead('channel-123', 'message-456');
 * 
 * // Add reaction
 * chat.addReaction('message-456', '👍');
 * ```
 */
export function useChatBackend(config: UseChatBackendConfig) {
  const {
    wsClient,
    userId,
    userName,
    userAvatar,
    onMessageReceived,
    onTypingUpdate,
    onReadReceipt,
    onReaction,
    debug = false,
  } = config;

  // Chat state
  const [state, setState] = useState<ChatState>({
    messages: new Map(),
    typingUsers: new Map(),
    readReceipts: new Map(),
    isConnected: false,
  });

  // Typing timeout refs
  const typingTimeouts = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

  /**
   * Debug logging
   */
  const log = useCallback(
    (...args: unknown[]) => {
      if (debug) {
        console.log('[ChatBackend]', ...args);
      }
    },
    [debug]
  );

  /**
   * Send chat message
   */
  const sendMessage = useCallback(
    (channelId: string, content: string, options?: { threadId?: string; replyTo?: string }) => {
      if (!wsClient.isConnected()) {
        log('Cannot send message - WebSocket not connected');
        return;
      }

      const payload: ChatSendPayload = {
        channelId,
        content,
        threadId: options?.threadId,
        replyTo: options?.replyTo,
      };

      wsClient.send('chat.send', payload);
      log('Sent message:', payload);
    },
    [wsClient, log]
  );

  /**
   * Send typing indicator
   */
  const sendTyping = useCallback(
    (channelId: string, isTyping: boolean) => {
      if (!wsClient.isConnected()) {
        return;
      }

      const payload: ChatTypingPayload = {
        channelId,
        isTyping,
      };

      wsClient.send('chat.typing', payload);
      log('Sent typing indicator:', payload);
    },
    [wsClient, log]
  );

  /**
   * Mark message as read
   */
  const markAsRead = useCallback(
    (channelId: string, messageId: string) => {
      if (!wsClient.isConnected()) {
        return;
      }

      const payload: ChatReadPayload = {
        channelId,
        messageId,
      };

      wsClient.send('chat.read', payload);
      log('Marked as read:', payload);
    },
    [wsClient, log]
  );

  /**
   * Add reaction to message
   */
  const addReaction = useCallback(
    (messageId: string, emoji: string) => {
      if (!wsClient.isConnected()) {
        return;
      }

      const payload: ChatReactionPayload = {
        messageId,
        emoji,
        action: 'add',
      };

      wsClient.send('chat.reaction', payload);
      log('Added reaction:', payload);
    },
    [wsClient, log]
  );

  /**
   * Remove reaction from message
   */
  const removeReaction = useCallback(
    (messageId: string, emoji: string) => {
      if (!wsClient.isConnected()) {
        return;
      }

      const payload: ChatReactionPayload = {
        messageId,
        emoji,
        action: 'remove',
      };

      wsClient.send('chat.reaction', payload);
      log('Removed reaction:', payload);
    },
    [wsClient, log]
  );

  /**
   * Handle incoming messages
   */
  useEffect(() => {
    const unsubscribe = wsClient.on<ChatMessage>('chat.send', (message) => {
      log('Received message:', message);

      // Add to state
      setState((prev) => {
        const newMessages = new Map(prev.messages);
        const channelMessages = newMessages.get(message.channelId) || [];
        newMessages.set(message.channelId, [...channelMessages, message]);
        return { ...prev, messages: newMessages };
      });

      // Notify callback
      onMessageReceived?.(message);
    });

    return unsubscribe;
  }, [wsClient, onMessageReceived, log]);

  /**
   * Handle typing indicators
   */
  useEffect(() => {
    const unsubscribe = wsClient.on<TypingIndicator>('chat.typing', (indicator) => {
      // Ignore own typing
      if (indicator.userId === userId) {
        return;
      }

      log('Received typing indicator:', indicator);

      // Update state
      setState((prev) => {
        const newTypingUsers = new Map(prev.typingUsers);
        const channelTyping = newTypingUsers.get(indicator.channelId) || [];
        
        // Remove existing indicator for this user
        const filtered = channelTyping.filter((t) => t.userId !== indicator.userId);
        
        // Add new indicator
        newTypingUsers.set(indicator.channelId, [...filtered, indicator]);
        
        return { ...prev, typingUsers: newTypingUsers };
      });

      // Auto-clear typing indicator after 5 seconds
      const timeoutKey = `${indicator.channelId}-${indicator.userId}`;
      const existingTimeout = typingTimeouts.current.get(timeoutKey);
      if (existingTimeout) {
        clearTimeout(existingTimeout);
      }

      const timeout = setTimeout(() => {
        setState((prev) => {
          const newTypingUsers = new Map(prev.typingUsers);
          const channelTyping = newTypingUsers.get(indicator.channelId) || [];
          const filtered = channelTyping.filter((t) => t.userId !== indicator.userId);
          newTypingUsers.set(indicator.channelId, filtered);
          return { ...prev, typingUsers: newTypingUsers };
        });
        typingTimeouts.current.delete(timeoutKey);
      }, 5000);

      typingTimeouts.current.set(timeoutKey, timeout);

      // Notify callback
      onTypingUpdate?.(indicator);
    });

    return unsubscribe;
  }, [wsClient, userId, onTypingUpdate, log]);

  /**
   * Handle read receipts
   */
  useEffect(() => {
    const unsubscribe = wsClient.on<ReadReceipt>('chat.read', (receipt) => {
      // Ignore own receipts
      if (receipt.userId === userId) {
        return;
      }

      log('Received read receipt:', receipt);

      // Update state
      setState((prev) => {
        const newReceipts = new Map(prev.readReceipts);
        const messageReceipts = newReceipts.get(receipt.messageId) || [];
        newReceipts.set(receipt.messageId, [...messageReceipts, receipt]);
        return { ...prev, readReceipts: newReceipts };
      });

      // Notify callback
      onReadReceipt?.(receipt);
    });

    return unsubscribe;
  }, [wsClient, userId, onReadReceipt, log]);

  /**
   * Handle reactions
   */
  useEffect(() => {
    const unsubscribe = wsClient.on<{ messageId: string; reaction: ChatReaction }>('chat.reaction', (payload) => {
      log('Received reaction:', payload);

      // Update message reactions in state
      setState((prev) => {
        const newMessages = new Map(prev.messages);
        
        // Find and update the message
        for (const [channelId, messages] of newMessages.entries()) {
          const messageIndex = messages.findIndex((m) => m.id === payload.messageId);
          if (messageIndex !== -1) {
            const updatedMessages = [...messages];
            const message = { ...updatedMessages[messageIndex] };
            message.reactions = message.reactions || [];
            
            // Check if reaction already exists
            const existingIndex = message.reactions.findIndex(
              (r) => r.emoji === payload.reaction.emoji && r.userId === payload.reaction.userId
            );
            
            if (existingIndex !== -1) {
              // Remove reaction
              message.reactions = message.reactions.filter((_, i) => i !== existingIndex);
            } else {
              // Add reaction
              message.reactions = [...message.reactions, payload.reaction];
            }
            
            updatedMessages[messageIndex] = message;
            newMessages.set(channelId, updatedMessages);
            break;
          }
        }
        
        return { ...prev, messages: newMessages };
      });

      // Notify callback
      onReaction?.(payload.messageId, payload.reaction);
    });

    return unsubscribe;
  }, [wsClient, onReaction, log]);

  /**
   * Handle connection state changes
   */
  useEffect(() => {
    const unsubscribe = wsClient.onStateChange((connectionState) => {
      const isConnected = connectionState === 'connected';
      setState((prev) => ({ ...prev, isConnected }));
      log('Connection state changed:', connectionState);
    });

    return unsubscribe;
  }, [wsClient, log]);

  /**
   * Cleanup on unmount
   */
  useEffect(() => {
    return () => {
      // Clear all typing timeouts
      typingTimeouts.current.forEach((timeout) => clearTimeout(timeout));
      typingTimeouts.current.clear();
    };
  }, []);

  return {
    // State
    state,
    isConnected: state.isConnected,
    
    // Actions
    sendMessage,
    sendTyping,
    markAsRead,
    addReaction,
    removeReaction,
    
    // Helpers
    getMessages: (channelId: string) => state.messages.get(channelId) || [],
    getTypingUsers: (channelId: string) => state.typingUsers.get(channelId) || [],
    getReadReceipts: (messageId: string) => state.readReceipts.get(messageId) || [],
  };
}

export default useChatBackend;
