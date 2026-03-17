// ============================================================================
// NotificationNode - Canvas node for visualizing notifications
//
// Features:
// - Notification type icons and colors
// - Priority indicators
// - Read/unread status
// - Actor information
// - Action link
// - Timestamp display
// ============================================================================

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps } from '@xyflow/react';
import {
  Bell,
  AtSign,
  Reply,
  MessageSquare,
  Hash,
  UserPlus,
  ClipboardList,
  MessageCircle,
  GitPullRequest,
  AlertTriangle,
  CheckCircle2,
  Circle,
  Clock,
  ExternalLink,
  Archive,
  Eye,
  EyeOff,
} from 'lucide-react';
import { cn } from '../../utils/cn';
import type { Notification, NotificationType, NotificationPriority, NotificationStatus } from '@ghatana/yappc-api';

export interface NotificationNodeData {
  notification: Notification;
  onMarkRead?: () => void;
  onArchive?: () => void;
  onNavigate?: () => void;
}

const notificationTypeConfig: Record<
  NotificationType,
  { icon: typeof Bell; label: string; color: string; bgColor: string }
> = {
  MENTION: {
    icon: AtSign,
    label: 'Mention',
    color: 'text-blue-400',
    bgColor: 'bg-blue-500/20',
  },
  REPLY: {
    icon: Reply,
    label: 'Reply',
    color: 'text-green-400',
    bgColor: 'bg-green-500/20',
  },
  DIRECT_MESSAGE: {
    icon: MessageSquare,
    label: 'Direct Message',
    color: 'text-purple-400',
    bgColor: 'bg-purple-500/20',
  },
  CHANNEL_ACTIVITY: {
    icon: Hash,
    label: 'Channel Activity',
    color: 'text-cyan-400',
    bgColor: 'bg-cyan-500/20',
  },
  TEAM_INVITE: {
    icon: UserPlus,
    label: 'Team Invite',
    color: 'text-yellow-400',
    bgColor: 'bg-yellow-500/20',
  },
  ASSIGNMENT: {
    icon: ClipboardList,
    label: 'Assignment',
    color: 'text-orange-400',
    bgColor: 'bg-orange-500/20',
  },
  COMMENT: {
    icon: MessageCircle,
    label: 'Comment',
    color: 'text-teal-400',
    bgColor: 'bg-teal-500/20',
  },
  REVIEW_REQUEST: {
    icon: GitPullRequest,
    label: 'Review Request',
    color: 'text-pink-400',
    bgColor: 'bg-pink-500/20',
  },
  SYSTEM: {
    icon: AlertTriangle,
    label: 'System',
    color: 'text-gray-400',
    bgColor: 'bg-gray-500/20',
  },
};

const priorityConfig: Record<
  NotificationPriority,
  { color: string; bgColor: string; label: string }
> = {
  LOW: { color: 'text-slate-400', bgColor: 'bg-slate-500/20', label: 'Low' },
  NORMAL: { color: 'text-blue-400', bgColor: 'bg-blue-500/20', label: 'Normal' },
  HIGH: { color: 'text-orange-400', bgColor: 'bg-orange-500/20', label: 'High' },
  URGENT: { color: 'text-red-400', bgColor: 'bg-red-500/20', label: 'Urgent' },
};

const statusConfig: Record<NotificationStatus, { color: string; bgColor: string }> = {
  UNREAD: { color: 'text-blue-400', bgColor: 'bg-blue-500/20' },
  READ: { color: 'text-slate-400', bgColor: 'bg-slate-500/20' },
  ARCHIVED: { color: 'text-slate-500', bgColor: 'bg-slate-600/20' },
};

function NotificationNode({ data }: NodeProps<NotificationNodeData>) {
  const { notification, onMarkRead, onArchive, onNavigate } = data;

  const typeConfig = notificationTypeConfig[notification.type];
  const TypeIcon = typeConfig.icon;
  const priorityInfo = priorityConfig[notification.priority];
  const statusInfo = statusConfig[notification.status];

  const isUnread = notification.status === 'UNREAD';
  const isArchived = notification.status === 'ARCHIVED';

  // Format time
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
    if (days < 7) return `${days}d ago`;
    return d.toLocaleDateString();
  };

  return (
    <div
      className={cn(
        'bg-slate-800 rounded-lg border shadow-xl min-w-[300px] max-w-[360px]',
        isUnread ? 'border-blue-500/50' : 'border-slate-600',
        isArchived && 'opacity-60'
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
          {/* Type Icon */}
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
              <span
                className={cn(
                  'px-2 py-0.5 text-xs font-medium rounded-full',
                  typeConfig.bgColor,
                  typeConfig.color
                )}
              >
                {typeConfig.label}
              </span>
              {notification.priority !== 'NORMAL' && (
                <span
                  className={cn(
                    'px-2 py-0.5 text-xs font-medium rounded-full',
                    priorityInfo.bgColor,
                    priorityInfo.color
                  )}
                >
                  {priorityInfo.label}
                </span>
              )}
              {isUnread && (
                <Circle className="w-2 h-2 fill-blue-400 text-blue-400" />
              )}
            </div>
            <h3 className="text-white font-semibold mt-2 line-clamp-2">
              {notification.title}
            </h3>
          </div>
        </div>
      </div>

      {/* Body */}
      <div className="p-4 border-b border-slate-700">
        <p className="text-slate-300 text-sm leading-relaxed">
          {notification.body}
        </p>
      </div>

      {/* Actor */}
      {notification.actor && (
        <div className="p-4 border-b border-slate-700">
          <span className="text-slate-400 text-xs font-medium uppercase tracking-wide mb-2 block">
            From
          </span>
          <div className="flex items-center gap-2">
            {notification.actor.avatarUrl ? (
              <img
                src={notification.actor.avatarUrl}
                alt={notification.actor.name}
                className="w-8 h-8 rounded-full object-cover"
              />
            ) : (
              <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center">
                <span className="text-white text-xs font-semibold">
                  {notification.actor.name.charAt(0).toUpperCase()}
                </span>
              </div>
            )}
            <div className="flex-1 min-w-0">
              <p className="text-white text-sm font-medium truncate">
                {notification.actor.name}
              </p>
              <p className="text-slate-500 text-xs truncate">
                {notification.actor.email}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Source Info */}
      {notification.sourceType && (
        <div className="px-4 py-3 border-b border-slate-700">
          <div className="flex items-center justify-between text-sm">
            <span className="text-slate-400">Source</span>
            <span className="text-slate-300 font-medium capitalize">
              {notification.sourceType.replace(/_/g, ' ').toLowerCase()}
            </span>
          </div>
        </div>
      )}

      {/* Actions */}
      <div className="p-4 border-b border-slate-700">
        <div className="flex items-center gap-2">
          {notification.actionUrl && onNavigate && (
            <button
              onClick={onNavigate}
              className="flex-1 flex items-center justify-center gap-2 px-3 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors text-sm font-medium"
            >
              <ExternalLink className="w-4 h-4" />
              View
            </button>
          )}
          {isUnread && onMarkRead && (
            <button
              onClick={onMarkRead}
              className="flex items-center justify-center gap-2 px-3 py-2 bg-slate-700 text-slate-300 rounded-lg hover:bg-slate-600 transition-colors text-sm font-medium"
              title="Mark as read"
            >
              <Eye className="w-4 h-4" />
            </button>
          )}
          {!isArchived && onArchive && (
            <button
              onClick={onArchive}
              className="flex items-center justify-center gap-2 px-3 py-2 bg-slate-700 text-slate-300 rounded-lg hover:bg-slate-600 transition-colors text-sm font-medium"
              title="Archive"
            >
              <Archive className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>

      {/* Footer */}
      <div className="px-4 py-3 bg-slate-900/50 rounded-b-lg">
        <div className="flex items-center justify-between text-xs text-slate-500">
          <div className="flex items-center gap-1">
            <Clock className="w-3 h-3" />
            <span>{formatTime(notification.createdAt)}</span>
          </div>
          <div className="flex items-center gap-2">
            {isUnread ? (
              <span className="flex items-center gap-1 text-blue-400">
                <Circle className="w-2 h-2 fill-blue-400" />
                Unread
              </span>
            ) : isArchived ? (
              <span className="flex items-center gap-1">
                <Archive className="w-3 h-3" />
                Archived
              </span>
            ) : (
              <span className="flex items-center gap-1">
                <CheckCircle2 className="w-3 h-3" />
                Read
              </span>
            )}
          </div>
        </div>
        {notification.readAt && (
          <div className="mt-1 text-xs text-slate-600">
            Read {formatTime(notification.readAt)}
          </div>
        )}
      </div>

      {/* Output Handle */}
      <Handle
        type="source"
        position={Position.Right}
        className="w-3 h-3 bg-green-500 border-2 border-slate-800"
      />
    </div>
  );
}

export default memo(NotificationNode);
