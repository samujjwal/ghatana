/**
 * WarRoomChat Component
 *
 * @description Real-time chat interface for incident war rooms with
 * message threading, mentions, and quick actions.
 *
 * @doc.type component
 * @doc.purpose War room communication
 * @doc.layer presentation
 * @doc.phase 4
 */

import React, { useState, useRef, useEffect, useCallback } from 'react';
import { cn } from '@ghatana/ui';

// ============================================================================
// Types
// ============================================================================

export type MessageType = 'message' | 'action' | 'status_update' | 'system';

export interface ChatUser {
  id: string;
  name: string;
  avatar?: string;
  role?: 'commander' | 'responder' | 'observer';
  isOnline?: boolean;
}

export interface ChatMessage {
  id: string;
  type: MessageType;
  content: string;
  author: ChatUser;
  timestamp: string;
  mentions?: string[];
  reactions?: { emoji: string; count: number; users: string[] }[];
  threadCount?: number;
  isEdited?: boolean;
}

export interface WarRoomChatProps {
  messages: ChatMessage[];
  currentUser: ChatUser;
  participants: ChatUser[];
  onSendMessage: (content: string, mentions?: string[]) => void;
  onReaction?: (messageId: string, emoji: string) => void;
  onOpenThread?: (messageId: string) => void;
  isConnected?: boolean;
  className?: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

const formatMessageTime = (timestamp: string): string => {
  const date = new Date(timestamp);
  return date.toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });
};

const getMessageTypeConfig = (type: MessageType) => {
  const configs: Record<MessageType, { bg: string; border: string }> = {
    message: { bg: 'transparent', border: 'transparent' },
    action: { bg: 'rgba(245, 158, 11, 0.1)', border: '#F59E0B' },
    status_update: { bg: 'rgba(59, 130, 246, 0.1)', border: '#3B82F6' },
    system: { bg: 'rgba(107, 114, 128, 0.1)', border: 'transparent' },
  };
  return configs[type];
};

const getRoleConfig = (role?: string) => {
  const configs: Record<string, { label: string; color: string }> = {
    commander: { label: 'IC', color: '#EF4444' },
    responder: { label: 'RSP', color: '#3B82F6' },
    observer: { label: 'OBS', color: '#6B7280' },
  };
  return role ? configs[role] : null;
};

// ============================================================================
// Chat Message Sub-component
// ============================================================================

interface ChatMessageItemProps {
  message: ChatMessage;
  showAuthor: boolean;
  currentUserId: string;
  onReaction?: (emoji: string) => void;
  onOpenThread?: () => void;
}

const ChatMessageItem: React.FC<ChatMessageItemProps> = ({
  message,
  showAuthor,
  currentUserId,
  onReaction,
  onOpenThread,
}) => {
  const [showActions, setShowActions] = useState(false);
  const typeConfig = getMessageTypeConfig(message.type);
  const roleConfig = getRoleConfig(message.author.role);
  const isOwnMessage = message.author.id === currentUserId;

  const quickReactions = ['👍', '👀', '✅', '❓', '🚨'];

  return (
    <div
      className={cn(
        'chat-message',
        `chat-message--${message.type}`,
        isOwnMessage && 'chat-message--own'
      )}
      style={{ backgroundColor: typeConfig.bg }}
      onMouseEnter={() => setShowActions(true)}
      onMouseLeave={() => setShowActions(false)}
    >
      {/* Author Avatar */}
      {showAuthor && (
        <div className="message-avatar">
          {message.author.avatar ? (
            <img src={message.author.avatar} alt={message.author.name} className="avatar-img" />
          ) : (
            <div className="avatar-placeholder">{message.author.name.charAt(0)}</div>
          )}
          {message.author.isOnline && <span className="online-indicator" />}
        </div>
      )}

      {/* Message Content */}
      <div className={cn('message-content', !showAuthor && 'message-content--no-avatar')}>
        {/* Header */}
        {showAuthor && (
          <div className="message-header">
            <span className="author-name">{message.author.name}</span>
            {roleConfig && (
              <span
                className="author-role"
                style={{ backgroundColor: roleConfig.color }}
              >
                {roleConfig.label}
              </span>
            )}
            <span className="message-time">{formatMessageTime(message.timestamp)}</span>
            {message.isEdited && <span className="edited-label">(edited)</span>}
          </div>
        )}

        {/* Content */}
        <div className="message-text">
          {message.type === 'action' && <span className="action-prefix">⚡ Action: </span>}
          {message.type === 'status_update' && <span className="status-prefix">📢 Update: </span>}
          {message.content}
        </div>

        {/* Reactions */}
        {message.reactions && message.reactions.length > 0 && (
          <div className="message-reactions">
            {message.reactions.map((reaction) => (
              <button
                key={reaction.emoji}
                type="button"
                className={cn(
                  'reaction-btn',
                  reaction.users.includes(currentUserId) && 'reaction-btn--active'
                )}
                onClick={() => onReaction?.(reaction.emoji)}
              >
                <span className="reaction-emoji">{reaction.emoji}</span>
                <span className="reaction-count">{reaction.count}</span>
              </button>
            ))}
          </div>
        )}

        {/* Thread */}
        {message.threadCount && message.threadCount > 0 && (
          <button type="button" className="thread-btn" onClick={onOpenThread}>
            💬 {message.threadCount} repl{message.threadCount === 1 ? 'y' : 'ies'}
          </button>
        )}

        {/* Actions (on hover) */}
        {showActions && (
          <div className="message-actions">
            {quickReactions.map((emoji) => (
              <button
                key={emoji}
                type="button"
                className="action-btn"
                onClick={() => onReaction?.(emoji)}
                aria-label={`React with ${emoji}`}
              >
                {emoji}
              </button>
            ))}
            {onOpenThread && (
              <button
                type="button"
                className="action-btn"
                onClick={onOpenThread}
                aria-label="Reply in thread"
              >
                💬
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

// ============================================================================
// Participants List Sub-component
// ============================================================================

interface ParticipantsListProps {
  participants: ChatUser[];
}

const ParticipantsList: React.FC<ParticipantsListProps> = ({ participants }) => {
  const online = participants.filter((p) => p.isOnline);
  const offline = participants.filter((p) => !p.isOnline);

  return (
    <div className="participants-list">
      <div className="participants-header">
        <span className="participants-title">Responders</span>
        <span className="participants-count">{online.length} online</span>
      </div>
      <div className="participants-content">
        {online.map((user) => {
          const roleConfig = getRoleConfig(user.role);
          return (
            <div key={user.id} className="participant">
              <div className="participant-avatar">
                {user.avatar ? (
                  <img src={user.avatar} alt={user.name} />
                ) : (
                  <div className="avatar-placeholder">{user.name.charAt(0)}</div>
                )}
                <span className="online-dot" />
              </div>
              <span className="participant-name">{user.name}</span>
              {roleConfig && (
                <span
                  className="participant-role"
                  style={{ color: roleConfig.color }}
                >
                  {roleConfig.label}
                </span>
              )}
            </div>
          );
        })}
        {offline.length > 0 && (
          <div className="offline-section">
            <span className="offline-label">Offline ({offline.length})</span>
          </div>
        )}
      </div>
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const WarRoomChat: React.FC<WarRoomChatProps> = ({
  messages,
  currentUser,
  participants,
  onSendMessage,
  onReaction,
  onOpenThread,
  isConnected = true,
  className,
}) => {
  const [inputValue, setInputValue] = useState('');
  const [messageType, setMessageType] = useState<'message' | 'action' | 'status_update'>('message');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // Auto-scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Handle send
  const handleSend = useCallback(() => {
    if (!inputValue.trim()) return;

    // Extract mentions
    const mentionRegex = /@(\w+)/g;
    const mentions = [...inputValue.matchAll(mentionRegex)].map((m) => m[1]);

    let content = inputValue.trim();
    if (messageType === 'action') {
      content = `[ACTION] ${content}`;
    } else if (messageType === 'status_update') {
      content = `[STATUS] ${content}`;
    }

    onSendMessage(content, mentions.length > 0 ? mentions : undefined);
    setInputValue('');
    setMessageType('message');
  }, [inputValue, messageType, onSendMessage]);

  // Handle key press
  const handleKeyPress = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  // Group consecutive messages from same author
  const shouldShowAuthor = (index: number): boolean => {
    if (index === 0) return true;
    const current = messages[index];
    const previous = messages[index - 1];
    if (current.author.id !== previous.author.id) return true;
    // Show author if messages are > 5 min apart
    const timeDiff = new Date(current.timestamp).getTime() - new Date(previous.timestamp).getTime();
    return timeDiff > 5 * 60 * 1000;
  };

  return (
    <div className={cn('war-room-chat', className)}>
      {/* Main Chat Area */}
      <div className="chat-main">
        {/* Connection Status */}
        {!isConnected && (
          <div className="connection-banner">
            <span className="connection-icon">⚠️</span>
            <span>Reconnecting to war room...</span>
          </div>
        )}

        {/* Messages */}
        <div className="messages-container">
          {messages.map((message, index) => (
            <ChatMessageItem
              key={message.id}
              message={message}
              showAuthor={shouldShowAuthor(index)}
              currentUserId={currentUser.id}
              onReaction={onReaction ? (emoji) => onReaction(message.id, emoji) : undefined}
              onOpenThread={onOpenThread ? () => onOpenThread(message.id) : undefined}
            />
          ))}
          <div ref={messagesEndRef} />
        </div>

        {/* Input Area */}
        <div className="chat-input-area">
          {/* Message Type Selector */}
          <div className="input-type-selector">
            <button
              type="button"
              className={cn('type-btn', messageType === 'message' && 'type-btn--active')}
              onClick={() => setMessageType('message')}
            >
              💬 Message
            </button>
            <button
              type="button"
              className={cn('type-btn', messageType === 'action' && 'type-btn--active')}
              onClick={() => setMessageType('action')}
            >
              ⚡ Action
            </button>
            <button
              type="button"
              className={cn('type-btn', messageType === 'status_update' && 'type-btn--active')}
              onClick={() => setMessageType('status_update')}
            >
              📢 Update
            </button>
          </div>

          {/* Input */}
          <div className="input-wrapper">
            <textarea
              ref={inputRef}
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyDown={handleKeyPress}
              placeholder={
                messageType === 'action'
                  ? 'Describe the action taken...'
                  : messageType === 'status_update'
                    ? 'Share a status update...'
                    : 'Type a message... (use @name to mention)'
              }
              className="chat-input"
              rows={1}
              disabled={!isConnected}
            />
            <button
              type="button"
              className="send-btn"
              onClick={handleSend}
              disabled={!inputValue.trim() || !isConnected}
              aria-label="Send message"
            >
              ➤
            </button>
          </div>
        </div>
      </div>

      {/* Participants Sidebar */}
      <ParticipantsList participants={participants} />

      <style>{`
        .war-room-chat {
          display: flex;
          height: 100%;
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 12px;
          overflow: hidden;
        }

        .chat-main {
          flex: 1;
          display: flex;
          flex-direction: column;
          min-width: 0;
        }

        .connection-banner {
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 0.5rem;
          padding: 0.5rem;
          background: #FEF3C7;
          color: #92400E;
          font-size: 0.8125rem;
        }

        .messages-container {
          flex: 1;
          overflow-y: auto;
          padding: 1rem;
          display: flex;
          flex-direction: column;
          gap: 0.25rem;
        }

        .chat-message {
          display: flex;
          gap: 0.75rem;
          padding: 0.375rem 0.5rem;
          border-radius: 8px;
          position: relative;
        }

        .chat-message:hover {
          background: #F9FAFB;
        }

        .chat-message--action {
          border-left: 3px solid #F59E0B;
        }

        .chat-message--status_update {
          border-left: 3px solid #3B82F6;
        }

        .chat-message--system {
          justify-content: center;
          text-align: center;
        }

        .message-avatar {
          position: relative;
          flex-shrink: 0;
        }

        .avatar-img {
          width: 32px;
          height: 32px;
          border-radius: 50%;
          object-fit: cover;
        }

        .avatar-placeholder {
          width: 32px;
          height: 32px;
          border-radius: 50%;
          background: #E5E7EB;
          color: #6B7280;
          font-size: 0.75rem;
          font-weight: 600;
          display: flex;
          align-items: center;
          justify-content: center;
        }

        .online-indicator {
          position: absolute;
          bottom: 0;
          right: 0;
          width: 10px;
          height: 10px;
          background: #10B981;
          border: 2px solid #fff;
          border-radius: 50%;
        }

        .message-content {
          flex: 1;
          min-width: 0;
        }

        .message-content--no-avatar {
          margin-left: 40px;
        }

        .message-header {
          display: flex;
          align-items: center;
          gap: 0.375rem;
          margin-bottom: 0.125rem;
        }

        .author-name {
          font-size: 0.8125rem;
          font-weight: 600;
          color: #111827;
        }

        .author-role {
          font-size: 0.625rem;
          font-weight: 700;
          color: #fff;
          padding: 0.0625rem 0.3125rem;
          border-radius: 3px;
        }

        .message-time {
          font-size: 0.6875rem;
          color: #9CA3AF;
        }

        .edited-label {
          font-size: 0.625rem;
          color: #9CA3AF;
          font-style: italic;
        }

        .message-text {
          font-size: 0.875rem;
          color: #374151;
          line-height: 1.5;
          word-break: break-word;
        }

        .action-prefix,
        .status-prefix {
          font-weight: 600;
        }

        .message-reactions {
          display: flex;
          gap: 0.25rem;
          margin-top: 0.375rem;
        }

        .reaction-btn {
          display: flex;
          align-items: center;
          gap: 0.25rem;
          font-size: 0.75rem;
          padding: 0.125rem 0.375rem;
          background: #F3F4F6;
          border: 1px solid #E5E7EB;
          border-radius: 12px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .reaction-btn:hover {
          background: #E5E7EB;
        }

        .reaction-btn--active {
          background: #DBEAFE;
          border-color: #93C5FD;
        }

        .reaction-count {
          color: #6B7280;
          font-size: 0.6875rem;
        }

        .thread-btn {
          font-size: 0.75rem;
          color: #3B82F6;
          background: transparent;
          border: none;
          cursor: pointer;
          padding: 0.25rem 0;
          margin-top: 0.25rem;
        }

        .thread-btn:hover {
          text-decoration: underline;
        }

        .message-actions {
          position: absolute;
          top: -0.5rem;
          right: 0.5rem;
          display: flex;
          gap: 0.125rem;
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 6px;
          padding: 0.125rem;
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
        }

        .action-btn {
          font-size: 0.75rem;
          padding: 0.25rem;
          background: transparent;
          border: none;
          border-radius: 4px;
          cursor: pointer;
        }

        .action-btn:hover {
          background: #F3F4F6;
        }

        .chat-input-area {
          padding: 0.75rem 1rem;
          border-top: 1px solid #E5E7EB;
        }

        .input-type-selector {
          display: flex;
          gap: 0.375rem;
          margin-bottom: 0.5rem;
        }

        .type-btn {
          font-size: 0.75rem;
          padding: 0.25rem 0.5rem;
          background: #F3F4F6;
          border: 1px solid transparent;
          border-radius: 6px;
          color: #6B7280;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .type-btn:hover {
          background: #E5E7EB;
        }

        .type-btn--active {
          background: #EFF6FF;
          border-color: #3B82F6;
          color: #2563EB;
        }

        .input-wrapper {
          display: flex;
          gap: 0.5rem;
          background: #F9FAFB;
          border: 1px solid #E5E7EB;
          border-radius: 8px;
          padding: 0.5rem;
        }

        .chat-input {
          flex: 1;
          background: transparent;
          border: none;
          outline: none;
          font-size: 0.875rem;
          color: #111827;
          resize: none;
          min-height: 1.5rem;
          max-height: 6rem;
        }

        .chat-input::placeholder {
          color: #9CA3AF;
        }

        .send-btn {
          font-size: 1rem;
          padding: 0.25rem 0.5rem;
          background: #3B82F6;
          border: none;
          border-radius: 6px;
          color: #fff;
          cursor: pointer;
          transition: background 0.15s ease;
          align-self: flex-end;
        }

        .send-btn:hover:not(:disabled) {
          background: #2563EB;
        }

        .send-btn:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }

        /* Participants Sidebar */
        .participants-list {
          width: 200px;
          border-left: 1px solid #E5E7EB;
          display: flex;
          flex-direction: column;
        }

        .participants-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: 0.75rem;
          border-bottom: 1px solid #E5E7EB;
        }

        .participants-title {
          font-size: 0.8125rem;
          font-weight: 600;
          color: #111827;
        }

        .participants-count {
          font-size: 0.6875rem;
          color: #10B981;
        }

        .participants-content {
          flex: 1;
          overflow-y: auto;
          padding: 0.5rem;
        }

        .participant {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          padding: 0.375rem;
          border-radius: 6px;
        }

        .participant:hover {
          background: #F9FAFB;
        }

        .participant-avatar {
          position: relative;
        }

        .participant-avatar img,
        .participant-avatar .avatar-placeholder {
          width: 24px;
          height: 24px;
        }

        .online-dot {
          position: absolute;
          bottom: -1px;
          right: -1px;
          width: 8px;
          height: 8px;
          background: #10B981;
          border: 2px solid #fff;
          border-radius: 50%;
        }

        .participant-name {
          flex: 1;
          font-size: 0.8125rem;
          color: #374151;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .participant-role {
          font-size: 0.625rem;
          font-weight: 600;
        }

        .offline-section {
          padding: 0.5rem 0.375rem;
        }

        .offline-label {
          font-size: 0.6875rem;
          color: #9CA3AF;
        }

        @media (prefers-color-scheme: dark) {
          .war-room-chat {
            background: #1F2937;
            border-color: #374151;
          }

          .chat-message:hover {
            background: #111827;
          }

          .avatar-placeholder {
            background: #374151;
            color: #9CA3AF;
          }

          .author-name {
            color: #F9FAFB;
          }

          .message-text {
            color: #E5E7EB;
          }

          .reaction-btn {
            background: #374151;
            border-color: #4B5563;
          }

          .message-actions {
            background: #1F2937;
            border-color: #374151;
          }

          .chat-input-area {
            border-top-color: #374151;
          }

          .type-btn {
            background: #374151;
            color: #9CA3AF;
          }

          .type-btn--active {
            background: rgba(59, 130, 246, 0.2);
            border-color: #3B82F6;
            color: #60A5FA;
          }

          .input-wrapper {
            background: #111827;
            border-color: #374151;
          }

          .chat-input {
            color: #F9FAFB;
          }

          .participants-list {
            border-left-color: #374151;
          }

          .participants-header {
            border-bottom-color: #374151;
          }

          .participants-title {
            color: #F9FAFB;
          }

          .participant:hover {
            background: #111827;
          }

          .participant-name {
            color: #E5E7EB;
          }
        }
      `}</style>
    </div>
  );
};

WarRoomChat.displayName = 'WarRoomChat';

export default WarRoomChat;
