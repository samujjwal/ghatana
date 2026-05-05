// @ts-nocheck
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
import { useParams, useNavigate } from 'react-router';
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
} from 'yappc-api';

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
        isActive ? 'bg-info-bg text-info-color' : 'hover:bg-surface-muted text-fg'
      )}
    >
      <ChannelIcon type={channel.type} className="w-4 h-4 flex-shrink-0" />
      <span className="flex-1 text-sm truncate">{channel.name}</span>
      {channel.unreadCount > 0 && (
        <span className="px-1.5 py-0.5 bg-primary rounded text-xs text-primary-foreground">
          {channel.unreadCount}
        </span>
      )}
      {channel.isMuted && <BellOff className="w-3 h-3 text-fg-muted" />}
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
          <div className="w-8 h-8 rounded-full bg-muted flex items-center justify-center">
            <span className="text-xs text-fg">
              {message.sender?.name?.charAt(0).toUpperCase() ?? '?'}
            </span>
          </div>
        )}
      </div>

      {/* Content */}
      <div className={cn('flex-1 max-w-xl', isOwn && 'text-right')}>
        <div className="flex items-baseline gap-2">
          <span className="text-sm font-medium text-fg">{message.sender?.name}</span>
          <span className="text-xs text-fg-muted">{formatTime(message.createdAt)}</span>
          {message.isPinned && <Pin className="w-3 h-3 text-warning-color" />}
          {message.isEdited && (
            <span className="text-xs text-fg-muted">(edited)</span>
          )}
        </div>
        <div
          className={cn(
            'mt-1 px-4 py-2 rounded-lg inline-block text-left',
            isOwn ? 'bg-primary text-primary-foreground' : 'bg-surface-muted text-fg'
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
                    ? 'bg-info-bg text-info-color'
                    : 'bg-surface-muted text-fg-muted hover:bg-muted'
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
          <button className="flex items-center gap-2 mt-2 text-xs text-info-color hover:opacity-80">
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
            'flex items-center gap-1 bg-surface border border-border rounded-lg p-1 shadow-lg',
            isOwn ? 'order-first' : ''
          )}
        >
          {/* Reactions picker */}
          <div className="relative">
            <button
              onClick={() => setShowReactions(!showReactions)}
              className="p-1.5 hover:bg-surface-muted rounded"
            >
              <Smile className="w-4 h-4 text-fg-muted" />
            </button>
            {showReactions && (
              <div className="absolute bottom-full left-0 mb-1 flex gap-1 bg-surface border border-border rounded-lg p-1 shadow-lg">
                {reactions.map(({ emoji, icon: Icon }) => (
                  <button
                    key={emoji}
                    onClick={() => {
                      onReact(emoji);
                      setShowReactions(false);
                    }}
                    className="p-1.5 hover:bg-surface-muted rounded"
                  >
                    <Icon className="w-4 h-4 text-fg-muted" />
                  </button>
                ))}
              </div>
            )}
          </div>
          <button onClick={onReply} className="p-1.5 hover:bg-surface-muted rounded">
            <Reply className="w-4 h-4 text-fg-muted" />
          </button>
          <button onClick={onPin} className="p-1.5 hover:bg-surface-muted rounded">
            <Pin className="w-4 h-4 text-fg-muted" />
          </button>
          {isOwn && (
            <>
              <button onClick={onEdit} className="p-1.5 hover:bg-surface-muted rounded">
                <Edit2 className="w-4 h-4 text-fg-muted" />
              </button>
              <button onClick={onDelete} className="p-1.5 hover:bg-surface-muted rounded">
                <Trash2 className="w-4 h-4 text-destructive" />
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
    <div className="flex items-center gap-2 px-4 py-2 text-sm text-muted-foreground">
      <div className="flex gap-1">
        <span className="w-2 h-2 bg-muted-foreground rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
        <span className="w-2 h-2 bg-muted-foreground rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
        <span className="w-2 h-2 bg-muted-foreground rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
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
    <div className="flex h-screen bg-background">
      {/* Sidebar - Channel List */}
      <aside className="w-64 flex-shrink-0 border-r border-border flex flex-col">
        {/* Team Header */}
        <div className="p-4 border-b border-border">
          <h2 className="text-lg font-semibold text-fg">Team Chat</h2>
        </div>

        {/* Search */}
        <div className="p-3 border-b border-border">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-fg-muted" />
            <input
              type="text"
              placeholder="Search channels..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-3 py-1.5 bg-surface border border-border rounded-lg text-sm text-fg placeholder:text-fg-muted focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
        </div>

        {/* Channels List */}
        <div className="flex-1 overflow-y-auto p-3 space-y-1">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-medium text-fg-muted uppercase tracking-wider">
              Channels
            </span>
            <button
              onClick={() => navigate(`/teams/${teamId}/channels/new`)}
              className="p-1 hover:bg-surface-muted rounded"
            >
              <Plus className="w-4 h-4 text-fg-muted" />
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
            <span className="text-xs font-medium text-fg-muted uppercase tracking-wider">
              Direct Messages
            </span>
            <button className="p-1 hover:bg-surface-muted rounded">
              <Plus className="w-4 h-4 text-fg-muted" />
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
            <header className="flex items-center justify-between px-6 py-3 border-b border-border bg-surface">
              <div className="flex items-center gap-3">
                <ChannelIcon type={channel.type} className="w-5 h-5 text-fg-muted" />
                <div>
                  <h3 className="text-lg font-semibold text-fg">{channel.name}</h3>
                  {channel.topic && (
                    <p className="text-sm text-fg-muted">{channel.topic}</p>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setShowSearch(!showSearch)}
                  className={cn(
                    'p-2 rounded-lg transition-colors',
                    showSearch ? 'bg-primary text-primary-foreground' : 'hover:bg-surface-muted text-fg-muted'
                  )}
                >
                  <Search className="w-5 h-5" />
                </button>
                <button className="p-2 hover:bg-surface-muted rounded-lg text-fg-muted">
                  <Users className="w-5 h-5" />
                </button>
                <button className="p-2 hover:bg-surface-muted rounded-lg text-fg-muted">
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
              <div className="px-6 py-2 bg-surface border-t border-border flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Reply className="w-4 h-4 text-fg-muted" />
                  <span className="text-sm text-fg-muted">
                    Replying to <span className="text-fg">{replyingTo.sender?.name}</span>
                  </span>
                </div>
                <button
                  onClick={() => setReplyingTo(null)}
                  className="p-1 hover:bg-surface-muted rounded"
                >
                  <X className="w-4 h-4 text-fg-muted" />
                </button>
              </div>
            )}

            {/* Message Input */}
            <div className="px-6 py-4 border-t border-border bg-surface">
              <div className="flex items-end gap-3">
                <button className="p-2 hover:bg-surface-muted rounded-lg text-fg-muted">
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
                    className="w-full px-4 py-3 bg-surface-muted border border-border rounded-lg text-fg placeholder:text-fg-muted resize-none focus:outline-none focus:ring-2 focus:ring-primary"
                  />
                </div>
                <button className="p-2 hover:bg-surface-muted rounded-lg text-fg-muted">
                  <Smile className="w-5 h-5" />
                </button>
                <button className="p-2 hover:bg-surface-muted rounded-lg text-fg-muted">
                  <AtSign className="w-5 h-5" />
                </button>
                <button
                  onClick={handleSendMessage}
                  disabled={!messageText.trim() || sending}
                  className={cn(
                    'p-2 rounded-lg transition-colors',
                    messageText.trim()
                      ? 'bg-primary hover:opacity-90 text-primary-foreground'
                      : 'bg-muted text-fg-muted cursor-not-allowed'
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
              <MessageSquare className="w-16 h-16 text-fg-muted mx-auto mb-4" />
              <h3 className="text-lg font-medium text-fg-muted">Select a channel</h3>
              <p className="text-sm text-fg-muted mt-1">
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
