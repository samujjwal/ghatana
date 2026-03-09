// ============================================================================
// ChannelNode - Canvas node for visualizing chat channels
//
// Features:
// - Channel type indicators (public/private/DM)
// - Member preview with presence
// - Unread message count
// - Topic display
// - Recent messages preview
// - Archive status
// ============================================================================

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps } from '@xyflow/react';
import {
  Hash,
  Lock,
  MessageSquare,
  Users,
  Pin,
  Archive,
  Volume2,
  VolumeX,
  Bell,
  BellOff,
  Clock,
  Circle,
} from 'lucide-react';
import { cn } from '../../utils/cn';
import type { Channel, ChannelMember, ChannelType, Message, PresenceStatus } from '@ghatana/yappc-api';

export interface ChannelNodeData {
  channel: Channel & {
    members?: (ChannelMember & { presence?: PresenceStatus })[];
    recentMessages?: Message[];
  };
  isMuted?: boolean;
  onOpenChannel?: () => void;
  onMute?: () => void;
  onUnmute?: () => void;
}

const channelTypeConfig: Record<
  ChannelType,
  { icon: typeof Hash; label: string; color: string; bgColor: string }
> = {
  PUBLIC: {
    icon: Hash,
    label: 'Public',
    color: 'text-green-400',
    bgColor: 'bg-green-500/20',
  },
  PRIVATE: {
    icon: Lock,
    label: 'Private',
    color: 'text-yellow-400',
    bgColor: 'bg-yellow-500/20',
  },
  DIRECT_MESSAGE: {
    icon: MessageSquare,
    label: 'Direct',
    color: 'text-blue-400',
    bgColor: 'bg-blue-500/20',
  },
  GROUP_DM: {
    icon: Users,
    label: 'Group DM',
    color: 'text-purple-400',
    bgColor: 'bg-purple-500/20',
  },
};

const presenceColors: Record<PresenceStatus, string> = {
  ONLINE: 'bg-green-500',
  AWAY: 'bg-yellow-500',
  BUSY: 'bg-red-500',
  OFFLINE: 'bg-slate-500',
  INVISIBLE: 'bg-slate-500',
};

function ChannelNode({ data }: NodeProps<ChannelNodeData>) {
  const { channel, isMuted, onOpenChannel, onMute, onUnmute } = data;

  const typeConfig = channelTypeConfig[channel.type];
  const TypeIcon = typeConfig.icon;

  // Get online members
  const onlineMembers = (channel.members ?? []).filter(
    (m) => m.presence === 'ONLINE' || m.presence === 'BUSY'
  );

  // Get recent messages (up to 3)
  const recentMessages = (channel.recentMessages ?? []).slice(0, 3);

  // Format last message time
  const formatTime = (date: string) => {
    const d = new Date(date);
    const now = new Date();
    const diff = now.getTime() - d.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes}m ago`;
    if (hours < 24) return `${hours}h ago`;
    return `${days}d ago`;
  };

  return (
    <div
      className={cn(
        'bg-slate-800 rounded-lg border shadow-xl min-w-[300px] max-w-[360px]',
        channel.isArchived ? 'border-slate-700 opacity-60' : 'border-slate-600'
      )}
    >
      {/* Input Handle */}
      <Handle
        type="target"
        position={Position.Left}
        className="w-3 h-3 bg-blue-500 border-2 border-slate-800"
      />

      {/* Header */}
      <div className="p-4 border-b border-slate-700">
        <div className="flex items-start gap-3">
          {/* Channel Icon */}
          <div
            className={cn(
              'w-10 h-10 rounded-lg flex items-center justify-center',
              typeConfig.bgColor
            )}
          >
            <TypeIcon className={cn('w-5 h-5', typeConfig.color)} />
          </div>

          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <h3 className="text-white font-semibold truncate">{channel.name}</h3>
              {channel.isArchived && (
                <Archive className="w-4 h-4 text-slate-500" />
              )}
              {channel.unreadCount > 0 && (
                <span className="px-2 py-0.5 bg-blue-500 text-white text-xs font-bold rounded-full">
                  {channel.unreadCount > 99 ? '99+' : channel.unreadCount}
                </span>
              )}
            </div>
            <div className="flex items-center gap-2 mt-1">
              <span
                className={cn(
                  'px-2 py-0.5 text-xs font-medium rounded-full',
                  typeConfig.bgColor,
                  typeConfig.color
                )}
              >
                {typeConfig.label}
              </span>
              <span className="text-slate-500 text-xs flex items-center gap-1">
                <Users className="w-3 h-3" />
                {channel.memberCount}
              </span>
            </div>
          </div>

          {/* Mute Button */}
          {!channel.isArchived && (
            <button
              onClick={isMuted ? onUnmute : onMute}
              className={cn(
                'p-1.5 rounded-lg transition-colors',
                isMuted
                  ? 'text-slate-500 hover:text-slate-400 hover:bg-slate-700'
                  : 'text-slate-400 hover:text-white hover:bg-slate-700'
              )}
              title={isMuted ? 'Unmute Channel' : 'Mute Channel'}
            >
              {isMuted ? (
                <BellOff className="w-4 h-4" />
              ) : (
                <Bell className="w-4 h-4" />
              )}
            </button>
          )}
        </div>

        {/* Topic */}
        {channel.topic && (
          <div className="mt-3 px-3 py-2 bg-slate-700/50 rounded-lg">
            <span className="text-slate-400 text-xs">Topic: </span>
            <span className="text-slate-300 text-xs">{channel.topic}</span>
          </div>
        )}
      </div>

      {/* Description */}
      {channel.description && (
        <div className="px-4 py-3 border-b border-slate-700">
          <p className="text-slate-400 text-sm line-clamp-2">{channel.description}</p>
        </div>
      )}

      {/* Online Members */}
      {channel.members && channel.members.length > 0 && (
        <div className="p-4 border-b border-slate-700">
          <div className="flex items-center justify-between mb-2">
            <span className="text-slate-400 text-xs font-medium uppercase tracking-wide">
              Members Online
            </span>
            <span className="text-green-400 text-xs font-medium">
              {onlineMembers.length}/{channel.memberCount}
            </span>
          </div>

          <div className="flex flex-wrap gap-2">
            {channel.members.slice(0, 8).map((member) => (
              <div
                key={member.id}
                className="relative"
                title={`${member.user.name} (${member.role})`}
              >
                {member.user.avatarUrl ? (
                  <img
                    src={member.user.avatarUrl}
                    alt={member.user.name}
                    className="w-8 h-8 rounded-full object-cover"
                  />
                ) : (
                  <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center">
                    <span className="text-white text-xs font-semibold">
                      {member.user.name.charAt(0).toUpperCase()}
                    </span>
                  </div>
                )}
                <div
                  className={cn(
                    'absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full border-2 border-slate-800',
                    presenceColors[member.presence ?? 'OFFLINE']
                  )}
                />
              </div>
            ))}
            {(channel.members.length > 8) && (
              <div className="w-8 h-8 rounded-full bg-slate-700 flex items-center justify-center">
                <span className="text-slate-400 text-xs font-medium">
                  +{channel.members.length - 8}
                </span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Recent Messages */}
      {recentMessages.length > 0 && (
        <div className="p-4 border-b border-slate-700">
          <span className="text-slate-400 text-xs font-medium uppercase tracking-wide">
            Recent Messages
          </span>
          <div className="mt-2 space-y-2">
            {recentMessages.map((message) => (
              <div key={message.id} className="flex items-start gap-2">
                {message.author.avatarUrl ? (
                  <img
                    src={message.author.avatarUrl}
                    alt={message.author.name}
                    className="w-6 h-6 rounded-full object-cover flex-shrink-0 mt-0.5"
                  />
                ) : (
                  <div className="w-6 h-6 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center flex-shrink-0 mt-0.5">
                    <span className="text-white text-xs font-semibold">
                      {message.author.name.charAt(0).toUpperCase()}
                    </span>
                  </div>
                )}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-slate-300 text-xs font-medium truncate">
                      {message.author.name}
                    </span>
                    <span className="text-slate-500 text-xs">
                      {formatTime(message.createdAt)}
                    </span>
                    {message.isPinned && (
                      <Pin className="w-3 h-3 text-yellow-400" />
                    )}
                  </div>
                  <p className="text-slate-400 text-xs line-clamp-1">{message.content}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Pinned Messages Count */}
      {channel.pinnedMessages && channel.pinnedMessages.length > 0 && (
        <div className="px-4 py-3 border-b border-slate-700">
          <div className="flex items-center gap-2 text-yellow-400">
            <Pin className="w-4 h-4" />
            <span className="text-sm font-medium">
              {channel.pinnedMessages.length} pinned message
              {channel.pinnedMessages.length !== 1 ? 's' : ''}
            </span>
          </div>
        </div>
      )}

      {/* Open Channel Button */}
      {!channel.isArchived && onOpenChannel && (
        <div className="p-4 border-b border-slate-700">
          <button
            onClick={onOpenChannel}
            className="w-full flex items-center justify-center gap-2 px-3 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors text-sm font-medium"
          >
            <MessageSquare className="w-4 h-4" />
            Open Channel
          </button>
        </div>
      )}

      {/* Footer */}
      <div className="px-4 py-3 bg-slate-900/50 rounded-b-lg">
        <div className="flex items-center justify-between text-xs text-slate-500">
          <div className="flex items-center gap-1">
            <Clock className="w-3 h-3" />
            <span>
              {channel.lastMessageAt
                ? `Active ${formatTime(channel.lastMessageAt)}`
                : 'No messages yet'}
            </span>
          </div>
          <div className="flex items-center gap-2">
            {isMuted && (
              <span className="flex items-center gap-1">
                <VolumeX className="w-3 h-3" />
                Muted
              </span>
            )}
            {channel.isArchived && (
              <span className="flex items-center gap-1">
                <Archive className="w-3 h-3" />
                Archived
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Output Handles */}
      <Handle
        type="source"
        position={Position.Right}
        id="messages"
        className="w-3 h-3 bg-green-500 border-2 border-slate-800"
        style={{ top: '40%' }}
      />
      <Handle
        type="source"
        position={Position.Right}
        id="threads"
        className="w-3 h-3 bg-purple-500 border-2 border-slate-800"
        style={{ top: '60%' }}
      />
    </div>
  );
}

export default memo(ChannelNode);
