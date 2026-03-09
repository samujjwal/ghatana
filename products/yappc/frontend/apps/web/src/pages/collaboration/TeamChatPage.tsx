/**
 * TeamChatPage
 *
 * @description Real-time team chat page with channels, threads, and direct messages.
 * Supports rich messaging, mentions, reactions, and file sharing.
 *
 * @route /teams/:teamId/chat
 * @route /teams/:teamId/channels/:channelId
 * @doc.phase 5
 * @doc.type page
 */

import React, { useState, useCallback, useRef, useEffect, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAtom, useAtomValue } from 'jotai';
import {
  Hash,
  Lock,
  MessageSquare,
  Users,
  Search,
  Send,
  Paperclip,
  Smile,
  AtSign,
  Reply,
  Pin,
  MoreHorizontal,
  Settings,
  ChevronRight,
  Plus,
  Bell,
  BellOff,
  Bookmark,
  Edit2,
  Trash2,
  Check,
  X,
  ThumbsUp,
  Heart,
  Laugh,
  PartyPopper,
} from 'lucide-react';
import { cn } from '../../utils/cn';
import {
  useChannels,
  useChannel,
  useMessages,
  useSendMessage,
  useUpdateMessage,
  useAddReaction,
  useRemoveReaction,
  usePinMessage,
  useCreateThread,
  useSetTyping,
  useChannelPresence,
  useMessageCreatedSubscription,
  useTypingIndicatorSubscription,
  type Channel,
  type Message,
  type Reaction,
  type Thread,
  type UserPresence,
} from '@ghatana/yappc-api';

// ============================================================================
// Types
// ============================================================================

interface ChatMessage extends Message {
  isEditing?: boolean;
}

// ============================================================================
// Sub-components
// ============================================================================

const ChannelIcon: React.FC<{ type: string; className?: string }> = ({ type, className }) => {
  const icons: Record<string, typeof Hash> = {
    PUBLIC: Hash,
    PRIVATE: Lock,
    DIRECT_MESSAGE: MessageSquare,
    GROUP_DM: Users,
  };
  const Icon = icons[type] ?? Hash;
  return <Icon className={className} />;
};

const ChannelListItem: React.FC<{
  channel: Channel;
  isActive: boolean;
  onClick: () => void;
}> = ({ channel, isActive, onClick }) => {
  return (
    <button
      onClick={onClick}
      className={cn(
        'w-full flex items-center gap-2 px-3 py-1.5 rounded-lg text-left transition-colors',
        isActive ? 'bg-blue-600/20 text-blue-400' : 'hover:bg-slate-700 text-slate-300'
      )}
    >
      <ChannelIcon type={channel.type} className="w-4 h-4 flex-shrink-0" />
      <span className="flex-1 text-sm truncate">{channel.name}</span>
      {channel.unreadCount > 0 && (
        <span className="px-1.5 py-0.5 bg-blue-600 rounded text-xs text-white">
          {channel.unreadCount}
        </span>
      )}
      {channel.isMuted && <BellOff className="w-3 h-3 text-slate-500" />}
    </button>
  );
};

const MessageBubble: React.FC<{
  message: ChatMessage;
  isOwn: boolean;
  onReply: () => void;
  onEdit: () => void;
  onDelete: () => void;
  onPin: () => void;
  onReact: (emoji: string) => void;
}> = ({ message, isOwn, onReply, onEdit, onDelete, onPin, onReact }) => {
  const [showActions, setShowActions] = useState(false);
  const [showReactions, setShowReactions] = useState(false);

  const reactions = [
    { emoji: '👍', icon: ThumbsUp },
    { emoji: '❤️', icon: Heart },
    { emoji: '😂', icon: Laugh },
    { emoji: '🎉', icon: PartyPopper },
  ];

  const formatTime = (timestamp: string) => {
    const date = new Date(timestamp);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  return (
    <div
      className={cn('group flex gap-3', isOwn && 'flex-row-reverse')}
      onMouseEnter={() => setShowActions(true)}
      onMouseLeave={() => {
        setShowActions(false);
        setShowReactions(false);
      }}
    >
      {/* Avatar */}
      <div className="flex-shrink-0">
        {message.sender?.avatarUrl ? (
          <img
            src={message.sender.avatarUrl}
            alt={message.sender.name}
            className="w-8 h-8 rounded-full"
          />
        ) : (
          <div className="w-8 h-8 rounded-full bg-slate-600 flex items-center justify-center">
            <span className="text-xs text-white">
              {message.sender?.name?.charAt(0).toUpperCase() ?? '?'}
            </span>
          </div>
        )}
      </div>

      {/* Content */}
      <div className={cn('flex-1 max-w-xl', isOwn && 'text-right')}>
        <div className="flex items-baseline gap-2">
          <span className="text-sm font-medium text-white">{message.sender?.name}</span>
          <span className="text-xs text-slate-500">{formatTime(message.createdAt)}</span>
          {message.isPinned && <Pin className="w-3 h-3 text-yellow-400" />}
          {message.isEdited && (
            <span className="text-xs text-slate-500">(edited)</span>
          )}
        </div>
        <div
          className={cn(
            'mt-1 px-4 py-2 rounded-lg inline-block text-left',
            isOwn ? 'bg-blue-600 text-white' : 'bg-slate-700 text-slate-200'
          )}
        >
          <p className="text-sm whitespace-pre-wrap">{message.content}</p>
        </div>

        {/* Reactions */}
        {message.reactions && message.reactions.length > 0 && (
          <div className="flex gap-1 mt-1 flex-wrap">
            {message.reactions.map((reaction, idx) => (
              <button
                key={idx}
                onClick={() => onReact(reaction.emoji)}
                className={cn(
                  'flex items-center gap-1 px-2 py-0.5 rounded-full text-xs',
                  reaction.hasReacted
                    ? 'bg-blue-600/30 text-blue-400'
                    : 'bg-slate-700 text-slate-400 hover:bg-slate-600'
                )}
              >
                <span>{reaction.emoji}</span>
                <span>{reaction.count}</span>
              </button>
            ))}
          </div>
        )}

        {/* Thread indicator */}
        {message.threadId && message.replyCount > 0 && (
          <button className="flex items-center gap-2 mt-2 text-xs text-blue-400 hover:text-blue-300">
            <Reply className="w-3 h-3" />
            {message.replyCount} replies
            <ChevronRight className="w-3 h-3" />
          </button>
        )}
      </div>

      {/* Actions */}
      {showActions && (
        <div
          className={cn(
            'flex items-center gap-1 bg-slate-800 border border-slate-700 rounded-lg p-1 shadow-lg',
            isOwn ? 'order-first' : ''
          )}
        >
          {/* Reactions picker */}
          <div className="relative">
            <button
              onClick={() => setShowReactions(!showReactions)}
              className="p-1.5 hover:bg-slate-700 rounded"
            >
              <Smile className="w-4 h-4 text-slate-400" />
            </button>
            {showReactions && (
              <div className="absolute bottom-full left-0 mb-1 flex gap-1 bg-slate-800 border border-slate-700 rounded-lg p-1 shadow-lg">
                {reactions.map(({ emoji, icon: Icon }) => (
                  <button
                    key={emoji}
                    onClick={() => {
                      onReact(emoji);
                      setShowReactions(false);
                    }}
                    className="p-1.5 hover:bg-slate-700 rounded"
                  >
                    <Icon className="w-4 h-4 text-slate-400" />
                  </button>
                ))}
              </div>
            )}
          </div>
          <button onClick={onReply} className="p-1.5 hover:bg-slate-700 rounded">
            <Reply className="w-4 h-4 text-slate-400" />
          </button>
          <button onClick={onPin} className="p-1.5 hover:bg-slate-700 rounded">
            <Pin className="w-4 h-4 text-slate-400" />
          </button>
          {isOwn && (
            <>
              <button onClick={onEdit} className="p-1.5 hover:bg-slate-700 rounded">
                <Edit2 className="w-4 h-4 text-slate-400" />
              </button>
              <button onClick={onDelete} className="p-1.5 hover:bg-slate-700 rounded">
                <Trash2 className="w-4 h-4 text-red-400" />
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
};

const TypingIndicator: React.FC<{ users: string[] }> = ({ users }) => {
  if (users.length === 0) return null;

  const text =
    users.length === 1
      ? `${users[0]} is typing...`
      : users.length === 2
      ? `${users[0]} and ${users[1]} are typing...`
      : `${users[0]} and ${users.length - 1} others are typing...`;

  return (
    <div className="flex items-center gap-2 px-4 py-2 text-sm text-slate-400">
      <div className="flex gap-1">
        <span className="w-2 h-2 bg-slate-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
        <span className="w-2 h-2 bg-slate-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
        <span className="w-2 h-2 bg-slate-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
      </div>
      <span>{text}</span>
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

const TeamChatPage: React.FC = () => {
  const { teamId, channelId } = useParams<{ teamId: string; channelId?: string }>();
  const navigate = useNavigate();

  // State
  const [messageText, setMessageText] = useState('');
  const [replyingTo, setReplyingTo] = useState<Message | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [showSearch, setShowSearch] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // Current user (would come from auth context in production)
  const currentUserId = 'current-user-id';

  // Data queries
  const { data: channelsData } = useChannels(teamId ?? '');
  const { data: channelData } = useChannel(channelId ?? '');
  const { data: messagesData, fetchMore } = useMessages(channelId ?? '', { limit: 50 });
  const { data: presenceData } = useChannelPresence(channelId ?? '');

  // Mutations
  const [sendMessage, { loading: sending }] = useSendMessage();
  const [updateMessage] = useUpdateMessage();
  const [addReaction] = useAddReaction();
  const [removeReaction] = useRemoveReaction();
  const [pinMessage] = usePinMessage();
  const [setTyping] = useSetTyping();

  // Subscriptions
  useMessageCreatedSubscription(channelId ?? '', {
    onData: () => {
      // Scroll to bottom on new message
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    },
  });

  const { data: typingData } = useTypingIndicatorSubscription(channelId ?? '');

  const channels = channelsData?.channels ?? [];
  const channel = channelData?.channel;
  const messages = messagesData?.messages ?? [];
  const typingUsers = typingData?.typingIndicator?.users?.map((u) => u.name) ?? [];

  // Auto-scroll on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  // Typing indicator
  useEffect(() => {
    if (!channelId || !messageText) return;

    const timeout = setTimeout(() => {
      setTyping({ channelId, isTyping: true });
    }, 500);

    return () => {
      clearTimeout(timeout);
      setTyping({ channelId, isTyping: false });
    };
  }, [channelId, messageText, setTyping]);

  // Handlers
  const handleSendMessage = useCallback(async () => {
    if (!messageText.trim() || !channelId) return;

    await sendMessage({
      channelId,
      content: messageText.trim(),
      threadId: replyingTo?.threadId,
    });

    setMessageText('');
    setReplyingTo(null);
    inputRef.current?.focus();
  }, [channelId, messageText, replyingTo, sendMessage]);

  const handleReaction = useCallback(
    async (messageId: string, emoji: string, hasReacted: boolean) => {
      if (hasReacted) {
        await removeReaction({ messageId, emoji });
      } else {
        await addReaction({ messageId, emoji });
      }
    },
    [addReaction, removeReaction]
  );

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const handleSelectChannel = (id: string) => {
    navigate(`/teams/${teamId}/channels/${id}`);
  };

  // Filter channels by search
  const filteredChannels = useMemo(() => {
    if (!searchQuery) return channels;
    return channels.filter((c) =>
      c.name.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [channels, searchQuery]);

  return (
    <div className="flex h-screen bg-slate-900">
      {/* Sidebar - Channel List */}
      <aside className="w-64 flex-shrink-0 border-r border-slate-700 flex flex-col">
        {/* Team Header */}
        <div className="p-4 border-b border-slate-700">
          <h2 className="text-lg font-semibold text-white">Team Chat</h2>
        </div>

        {/* Search */}
        <div className="p-3 border-b border-slate-700">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
            <input
              type="text"
              placeholder="Search channels..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-3 py-1.5 bg-slate-800 border border-slate-700 rounded-lg text-sm text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>

        {/* Channels List */}
        <div className="flex-1 overflow-y-auto p-3 space-y-1">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-medium text-slate-400 uppercase tracking-wider">
              Channels
            </span>
            <button
              onClick={() => navigate(`/teams/${teamId}/channels/new`)}
              className="p-1 hover:bg-slate-700 rounded"
            >
              <Plus className="w-4 h-4 text-slate-400" />
            </button>
          </div>
          {filteredChannels
            .filter((c) => c.type === 'PUBLIC' || c.type === 'PRIVATE')
            .map((channel) => (
              <ChannelListItem
                key={channel.id}
                channel={channel}
                isActive={channel.id === channelId}
                onClick={() => handleSelectChannel(channel.id)}
              />
            ))}

          <div className="flex items-center justify-between mt-4 mb-2">
            <span className="text-xs font-medium text-slate-400 uppercase tracking-wider">
              Direct Messages
            </span>
            <button className="p-1 hover:bg-slate-700 rounded">
              <Plus className="w-4 h-4 text-slate-400" />
            </button>
          </div>
          {filteredChannels
            .filter((c) => c.type === 'DIRECT_MESSAGE' || c.type === 'GROUP_DM')
            .map((channel) => (
              <ChannelListItem
                key={channel.id}
                channel={channel}
                isActive={channel.id === channelId}
                onClick={() => handleSelectChannel(channel.id)}
              />
            ))}
        </div>
      </aside>

      {/* Main Chat Area */}
      <main className="flex-1 flex flex-col min-w-0">
        {channel ? (
          <>
            {/* Channel Header */}
            <header className="flex items-center justify-between px-6 py-3 border-b border-slate-700 bg-slate-800">
              <div className="flex items-center gap-3">
                <ChannelIcon type={channel.type} className="w-5 h-5 text-slate-400" />
                <div>
                  <h3 className="text-lg font-semibold text-white">{channel.name}</h3>
                  {channel.topic && (
                    <p className="text-sm text-slate-400">{channel.topic}</p>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setShowSearch(!showSearch)}
                  className={cn(
                    'p-2 rounded-lg transition-colors',
                    showSearch ? 'bg-blue-600 text-white' : 'hover:bg-slate-700 text-slate-400'
                  )}
                >
                  <Search className="w-5 h-5" />
                </button>
                <button className="p-2 hover:bg-slate-700 rounded-lg text-slate-400">
                  <Users className="w-5 h-5" />
                </button>
                <button className="p-2 hover:bg-slate-700 rounded-lg text-slate-400">
                  <Settings className="w-5 h-5" />
                </button>
              </div>
            </header>

            {/* Messages Area */}
            <div className="flex-1 overflow-y-auto px-6 py-4 space-y-4">
              {messages.map((message) => (
                <MessageBubble
                  key={message.id}
                  message={message}
                  isOwn={message.sender?.id === currentUserId}
                  onReply={() => setReplyingTo(message)}
                  onEdit={() => {}}
                  onDelete={() => {}}
                  onPin={() => pinMessage({ messageId: message.id, pinned: !message.isPinned })}
                  onReact={(emoji) =>
                    handleReaction(
                      message.id,
                      emoji,
                      message.reactions?.some((r) => r.emoji === emoji && r.hasReacted) ?? false
                    )
                  }
                />
              ))}
              <div ref={messagesEndRef} />
            </div>

            {/* Typing Indicator */}
            <TypingIndicator users={typingUsers.filter((u) => u !== 'You')} />

            {/* Reply Context */}
            {replyingTo && (
              <div className="px-6 py-2 bg-slate-800 border-t border-slate-700 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Reply className="w-4 h-4 text-slate-400" />
                  <span className="text-sm text-slate-400">
                    Replying to <span className="text-white">{replyingTo.sender?.name}</span>
                  </span>
                </div>
                <button
                  onClick={() => setReplyingTo(null)}
                  className="p-1 hover:bg-slate-700 rounded"
                >
                  <X className="w-4 h-4 text-slate-400" />
                </button>
              </div>
            )}

            {/* Message Input */}
            <div className="px-6 py-4 border-t border-slate-700 bg-slate-800">
              <div className="flex items-end gap-3">
                <button className="p-2 hover:bg-slate-700 rounded-lg text-slate-400">
                  <Paperclip className="w-5 h-5" />
                </button>
                <div className="flex-1 relative">
                  <textarea
                    ref={inputRef}
                    value={messageText}
                    onChange={(e) => setMessageText(e.target.value)}
                    onKeyDown={handleKeyDown}
                    placeholder={`Message #${channel.name}`}
                    rows={1}
                    className="w-full px-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-white placeholder-slate-400 resize-none focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <button className="p-2 hover:bg-slate-700 rounded-lg text-slate-400">
                  <Smile className="w-5 h-5" />
                </button>
                <button className="p-2 hover:bg-slate-700 rounded-lg text-slate-400">
                  <AtSign className="w-5 h-5" />
                </button>
                <button
                  onClick={handleSendMessage}
                  disabled={!messageText.trim() || sending}
                  className={cn(
                    'p-2 rounded-lg transition-colors',
                    messageText.trim()
                      ? 'bg-blue-600 hover:bg-blue-500 text-white'
                      : 'bg-slate-700 text-slate-500 cursor-not-allowed'
                  )}
                >
                  <Send className="w-5 h-5" />
                </button>
              </div>
            </div>
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center">
            <div className="text-center">
              <MessageSquare className="w-16 h-16 text-slate-600 mx-auto mb-4" />
              <h3 className="text-lg font-medium text-slate-400">Select a channel</h3>
              <p className="text-sm text-slate-500 mt-1">
                Choose a channel from the sidebar to start chatting
              </p>
            </div>
          </div>
        )}
      </main>
    </div>
  );
};

export default TeamChatPage;
