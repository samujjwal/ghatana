/**
 * Chat Panel Component
 * 
 * Complete chat interface with message list, input, and typing indicators.
 * Integrates with useChatBackend hook for real-time messaging.
 * 
 * @module chat/components
 */

import React, { useState, useCallback, useRef, useEffect } from 'react';
import { Send, Loader2 } from 'lucide-react';
import { ChatMessage } from './ChatMessage';
import type { ChatMessage as ChatMessageType, TypingIndicator } from '../hooks/useChatBackend';

export interface ChatPanelProps {
  channelId: string;
  channelName: string;
  currentUserId: string;
  messages: ChatMessageType[];
  typingUsers: TypingIndicator[];
  isConnected: boolean;
  onSendMessage: (content: string) => void;
  onTyping: (isTyping: boolean) => void;
  onReact: (messageId: string, emoji: string) => void;
  onMarkAsRead: (messageId: string) => void;
}

/**
 * Chat Panel Component
 * 
 * Full-featured chat interface with real-time messaging.
 * 
 * @example
 * ```tsx
 * <ChatPanel
 *   channelId="channel-123"
 *   channelName="General"
 *   currentUserId={user.id}
 *   messages={messages}
 *   typingUsers={typingUsers}
 *   isConnected={isConnected}
 *   onSendMessage={(content) => chat.sendMessage(channelId, content)}
 *   onTyping={(isTyping) => chat.sendTyping(channelId, isTyping)}
 *   onReact={(msgId, emoji) => chat.addReaction(msgId, emoji)}
 *   onMarkAsRead={(msgId) => chat.markAsRead(channelId, msgId)}
 * />
 * ```
 */
export const ChatPanel: React.FC<ChatPanelProps> = ({
  channelId,
  channelName,
  currentUserId,
  messages,
  typingUsers,
  isConnected,
  onSendMessage,
  onTyping,
  onReact,
  onMarkAsRead,
}) => {
  const [inputValue, setInputValue] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const typingTimeoutRef = useRef<ReturnType<typeof setTimeout>>();

  // Auto-scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Mark last message as read when visible
  useEffect(() => {
    if (messages.length > 0) {
      const lastMessage = messages[messages.length - 1];
      if (lastMessage.userId !== currentUserId) {
        onMarkAsRead(lastMessage.id);
      }
    }
  }, [messages, currentUserId, onMarkAsRead]);

  // Handle input change with typing indicator
  const handleInputChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const value = e.target.value;
    setInputValue(value);

    // Send typing indicator
    if (value.length > 0 && !isTyping) {
      setIsTyping(true);
      onTyping(true);
    }

    // Clear existing timeout
    if (typingTimeoutRef.current) {
      clearTimeout(typingTimeoutRef.current);
    }

    // Stop typing after 3 seconds of inactivity
    typingTimeoutRef.current = setTimeout(() => {
      setIsTyping(false);
      onTyping(false);
    }, 3000);
  }, [isTyping, onTyping]);

  // Handle send message
  const handleSend = useCallback(() => {
    const trimmed = inputValue.trim();
    if (!trimmed || !isConnected) return;

    onSendMessage(trimmed);
    setInputValue('');
    setIsTyping(false);
    onTyping(false);

    if (typingTimeoutRef.current) {
      clearTimeout(typingTimeoutRef.current);
    }
  }, [inputValue, isConnected, onSendMessage, onTyping]);

  // Handle Enter key
  const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }, [handleSend]);

  return (
    <div className="flex flex-col h-full bg-zinc-950">
      {/* Header */}
      <div className="h-14 border-b border-zinc-800 flex items-center justify-between px-4 bg-zinc-900/50">
        <div>
          <h3 className="font-medium text-white">{channelName}</h3>
          <p className="text-xs text-zinc-500">
            {isConnected ? (
              <span className="flex items-center gap-1">
                <span className="w-2 h-2 rounded-full bg-emerald-500" />
                Connected
              </span>
            ) : (
              <span className="flex items-center gap-1">
                <Loader2 className="w-3 h-3 animate-spin" />
                Connecting...
              </span>
            )}
          </p>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto">
        {messages.length === 0 ? (
          <div className="flex items-center justify-center h-full text-zinc-500">
            <p>No messages yet. Start the conversation!</p>
          </div>
        ) : (
          <div className="py-4">
            {messages.map((message, index) => {
              const isOwn = message.userId === currentUserId;
              const showAvatar = index === 0 || messages[index - 1].userId !== message.userId;

              return (
                <ChatMessage
                  key={message.id}
                  message={message}
                  currentUserId={currentUserId}
                  isOwn={isOwn}
                  showAvatar={showAvatar}
                  onReact={onReact}
                />
              );
            })}
            <div ref={messagesEndRef} />
          </div>
        )}

        {/* Typing Indicators */}
        {typingUsers.length > 0 && (
          <div className="px-4 py-2 text-sm text-zinc-500">
            {typingUsers.length === 1 ? (
              <span>{typingUsers[0].userName} is typing...</span>
            ) : typingUsers.length === 2 ? (
              <span>{typingUsers[0].userName} and {typingUsers[1].userName} are typing...</span>
            ) : (
              <span>{typingUsers.length} people are typing...</span>
            )}
          </div>
        )}
      </div>

      {/* Input */}
      <div className="border-t border-zinc-800 p-4">
        <div className="flex gap-2">
          <textarea
            value={inputValue}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            placeholder={isConnected ? `Message ${channelName}` : 'Connecting...'}
            disabled={!isConnected}
            className="flex-1 bg-zinc-900 text-white rounded-lg px-4 py-2 resize-none focus:outline-none focus:ring-2 focus:ring-violet-500 disabled:opacity-50 disabled:cursor-not-allowed"
            rows={1}
            style={{
              minHeight: '40px',
              maxHeight: '120px',
            }}
          />
          <button
            onClick={handleSend}
            disabled={!inputValue.trim() || !isConnected}
            className="px-4 py-2 bg-violet-600 text-white rounded-lg hover:bg-violet-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            title="Send message (Enter)"
          >
            <Send className="w-5 h-5" />
          </button>
        </div>
        <p className="text-xs text-zinc-500 mt-2">
          Press Enter to send, Shift+Enter for new line
        </p>
      </div>
    </div>
  );
};

export default ChatPanel;
