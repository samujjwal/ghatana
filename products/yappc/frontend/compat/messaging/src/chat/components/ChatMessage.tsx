/**
 * Chat Message Component
 * 
 * Displays individual chat message with reactions, replies, and timestamps.
 * Supports editing, threading, and read receipts.
 * 
 * @module chat/components
 */

import React, { useState, useCallback } from 'react';
import { formatDistanceToNow } from 'date-fns';
import { MoreVertical, Reply, Smile, Check, CheckCheck } from 'lucide-react';
import type { ChatMessage as ChatMessageType, ChatReaction } from '../hooks/useChatBackend';

export interface ChatMessageProps {
  message: ChatMessageType;
  currentUserId: string;
  isOwn: boolean;
  showAvatar?: boolean;
  onReply?: (messageId: string) => void;
  onReact?: (messageId: string, emoji: string) => void;
  onEdit?: (messageId: string, newContent: string) => void;
  onDelete?: (messageId: string) => void;
  readBy?: string[];
}

/**
 * Chat Message Component
 * 
 * Renders a single chat message with full functionality.
 */
export const ChatMessage: React.FC<ChatMessageProps> = ({
  message,
  currentUserId,
  isOwn,
  showAvatar = true,
  onReply,
  onReact,
  onEdit,
  onDelete,
  readBy = [],
}) => {
  const [showActions, setShowActions] = useState(false);
  const [showReactions, setShowReactions] = useState(false);

  const handleReact = useCallback((emoji: string) => {
    onReact?.(message.id, emoji);
    setShowReactions(false);
  }, [message.id, onReact]);

  const handleReply = useCallback(() => {
    onReply?.(message.id);
  }, [message.id, onReply]);

  // Group reactions by emoji
  const groupedReactions = message.reactions?.reduce((acc, reaction) => {
    if (!acc[reaction.emoji]) {
      acc[reaction.emoji] = [];
    }
    acc[reaction.emoji].push(reaction);
    return acc;
  }, {} as Record<string, ChatReaction[]>) || {};

  const hasReactions = Object.keys(groupedReactions).length > 0;

  return (
    <div
      className={`group flex gap-3 px-4 py-2 hover:bg-zinc-900/50 ${isOwn ? 'flex-row-reverse' : ''}`}
      onMouseEnter={() => setShowActions(true)}
      onMouseLeave={() => setShowActions(false)}
    >
      {/* Avatar */}
      {showAvatar && (
        <div className="flex-shrink-0">
          {message.userAvatar ? (
            <img
              src={message.userAvatar}
              alt={message.userName}
              className="w-8 h-8 rounded-full"
            />
          ) : (
            <div className="w-8 h-8 rounded-full bg-violet-500 flex items-center justify-center text-white text-sm font-medium">
              {message.userName.charAt(0).toUpperCase()}
            </div>
          )}
        </div>
      )}

      {/* Message Content */}
      <div className={`flex-1 min-w-0 ${isOwn ? 'text-right' : ''}`}>
        {/* Header */}
        <div className={`flex items-baseline gap-2 mb-1 ${isOwn ? 'justify-end' : ''}`}>
          <span className="font-medium text-sm text-zinc-200">
            {message.userName}
          </span>
          <span className="text-xs text-zinc-500">
            {formatDistanceToNow(message.timestamp, { addSuffix: true })}
          </span>
          {message.edited && (
            <span className="text-xs text-zinc-500">(edited)</span>
          )}
        </div>

        {/* Message Body */}
        <div
          className={`inline-block px-3 py-2 rounded-lg ${
            isOwn
              ? 'bg-violet-600 text-white'
              : 'bg-zinc-800 text-zinc-100'
          }`}
        >
          <p className="text-sm whitespace-pre-wrap break-words">
            {message.content}
          </p>
        </div>

        {/* Reactions */}
        {hasReactions && (
          <div className={`flex flex-wrap gap-1 mt-1 ${isOwn ? 'justify-end' : ''}`}>
            {Object.entries(groupedReactions).map(([emoji, reactions]) => (
              <button
                key={emoji}
                onClick={() => handleReact(emoji)}
                className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs ${
                  reactions.some((r) => r.userId === currentUserId)
                    ? 'bg-violet-500/20 text-violet-400 border border-violet-500/30'
                    : 'bg-zinc-800 text-zinc-400 hover:bg-zinc-700'
                }`}
                title={reactions.map((r) => r.userName).join(', ')}
              >
                <span>{emoji}</span>
                <span>{reactions.length}</span>
              </button>
            ))}
          </div>
        )}

        {/* Read Receipts */}
        {isOwn && readBy.length > 0 && (
          <div className="flex items-center gap-1 mt-1 justify-end text-xs text-zinc-500">
            {readBy.length === 1 ? <Check className="w-3 h-3" /> : <CheckCheck className="w-3 h-3" />}
            <span>Read by {readBy.length}</span>
          </div>
        )}
      </div>

      {/* Actions */}
      {showActions && (
        <div className={`flex items-start gap-1 opacity-0 group-hover:opacity-100 transition-opacity ${isOwn ? 'order-first' : ''}`}>
          <button
            onClick={handleReply}
            className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-white"
            title="Reply"
          >
            <Reply className="w-4 h-4" />
          </button>
          <button
            onClick={() => setShowReactions(!showReactions)}
            className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-white"
            title="React"
          >
            <Smile className="w-4 h-4" />
          </button>
          {isOwn && (
            <button
              className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-white"
              title="More"
            >
              <MoreVertical className="w-4 h-4" />
            </button>
          )}
        </div>
      )}

      {/* Reaction Picker */}
      {showReactions && (
        <div className="absolute mt-8 bg-zinc-800 rounded-lg shadow-lg border border-zinc-700 p-2 flex gap-1 z-10">
          {['👍', '❤️', '😂', '😮', '😢', '🎉', '🚀', '👀'].map((emoji) => (
            <button
              key={emoji}
              onClick={() => handleReact(emoji)}
              className="w-8 h-8 flex items-center justify-center rounded hover:bg-zinc-700 text-lg"
            >
              {emoji}
            </button>
          ))}
        </div>
      )}
    </div>
  );
};

export default ChatMessage;
