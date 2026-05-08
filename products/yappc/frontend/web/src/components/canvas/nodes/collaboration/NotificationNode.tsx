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
import type { Node, NodeProps } from '@xyflow/react';
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
import { Button } from '../../../ui/Button';

type NotificationType =
  | 'MENTION'
  | 'REPLY'
  | 'DIRECT_MESSAGE'
  | 'CHANNEL_ACTIVITY'
  | 'TEAM_INVITE'
  | 'ASSIGNMENT'
  | 'COMMENT'
  | 'REVIEW_REQUEST'
  | 'SYSTEM';

type NotificationPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
type NotificationStatus = 'UNREAD' | 'READ' | 'ARCHIVED';

interface NotificationActor {
  [key: string]: unknown;
  name: string;
  email?: string;
  avatarUrl?: string;
}

interface NotificationActionLink {
  label: string;
  url?: string;
}

interface Notification {
  [key: string]: unknown;
  type: NotificationType;
  priority: NotificationPriority;
  status: NotificationStatus;
  title: string;
  body: string;
  actor?: NotificationActor;
  actionLink?: NotificationActionLink;
  actionUrl?: string;
  sourceType?: string;
  createdAt: string;
  readAt?: string;
}

export interface NotificationNodeData {
  [key: string]: unknown;
  notification: Notification;
  onMarkRead?: () => void;
  onArchive?: () => void;
  onNavigate?: () => void;
}

type NotificationCanvasNode = Node<NotificationNodeData, 'notification'>;

const notificationTypeConfig: Record<
  NotificationType,
  { icon: typeof Bell; label: string; color: string; bgColor: string }
> = {
  MENTION: {
    icon: AtSign,
    label: 'Mention',
    color: 'text-info-color',
    bgColor: 'bg-info-bg/20',
  },
  REPLY: {
    icon: Reply,
    label: 'Reply',
    color: 'text-success-color',
    bgColor: 'bg-success-bg0/20',
  },
  DIRECT_MESSAGE: {
    icon: MessageSquare,
    label: 'Direct Message',
    color: 'text-info-color',
    bgColor: 'bg-info-bg/20',
  },
  CHANNEL_ACTIVITY: {
    icon: Hash,
    label: 'Channel Activity',
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
  },
  TEAM_INVITE: {
    icon: UserPlus,
    label: 'Team Invite',
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg0/20',
  },
  ASSIGNMENT: {
    icon: ClipboardList,
    label: 'Assignment',
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg0/20',
  },
  COMMENT: {
    icon: MessageCircle,
    label: 'Comment',
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
  },
  REVIEW_REQUEST: {
    icon: GitPullRequest,
    label: 'Review Request',
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
  },
  SYSTEM: {
    icon: AlertTriangle,
    label: 'System',
    color: 'text-fg-muted',
    bgColor: 'bg-surface-muted0/20',
  },
};

const priorityConfig: Record<
  NotificationPriority,
  { color: string; bgColor: string; label: string }
> = {
  LOW: { color: 'text-fg-muted', bgColor: 'bg-surface-muted0/20', label: 'Low' },
  NORMAL: { color: 'text-info-color', bgColor: 'bg-info-bg/20', label: 'Normal' },
  HIGH: { color: 'text-warning-color', bgColor: 'bg-warning-bg0/20', label: 'High' },
  URGENT: { color: 'text-destructive', bgColor: 'bg-destructive-bg0/20', label: 'Urgent' },
};

const statusConfig: Record<NotificationStatus, { color: string; bgColor: string }> = {
  UNREAD: { color: 'text-info-color', bgColor: 'bg-info-bg/20' },
  READ: { color: 'text-fg-muted', bgColor: 'bg-surface-muted0/20' },
  ARCHIVED: { color: 'text-fg-muted', bgColor: 'bg-muted/20' },
};

function NotificationNode({ data }: NodeProps<NotificationCanvasNode>) {
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
        'bg-surface rounded-lg border shadow-xl min-w-[300px] max-w-[360px]',
        isUnread ? 'border-info-border/50' : 'border-border',
        isArchived && 'opacity-60'
      )}
    >
      {/* Input Handle */}
      <Handle
        type="target"
        position={Position.Left}
        className="w-3 h-3 bg-info-bg border-2 border-border"
      />

      {/* Header */}
      <div className="p-4 border-b border-border">
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
                <Circle className="w-2 h-2 fill-blue-400 text-info-color" />
              )}
            </div>
            <h3 className="text-white font-semibold mt-2 line-clamp-2">
              {notification.title}
            </h3>
          </div>
        </div>
      </div>

      {/* Body */}
      <div className="p-4 border-b border-border">
        <p className="text-fg text-sm leading-relaxed">
          {notification.body}
        </p>
      </div>

      {/* Actor */}
      {notification.actor && (
        <div className="p-4 border-b border-border">
          <span className="text-fg-muted text-xs font-medium uppercase tracking-wide mb-2 block">
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
              <p className="text-fg-muted text-xs truncate">
                {notification.actor.email}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Source Info */}
      {notification.sourceType && (
        <div className="px-4 py-3 border-b border-border">
          <div className="flex items-center justify-between text-sm">
            <span className="text-fg-muted">Source</span>
            <span className="text-fg font-medium capitalize">
              {notification.sourceType.replace(/_/g, ' ').toLowerCase()}
            </span>
          </div>
        </div>
      )}

      {/* Actions */}
      <div className="p-4 border-b border-border">
        <div className="flex items-center gap-2">
          {notification.actionUrl && onNavigate && (
            <Button variant="ghost" size="sm"
              onClick={onNavigate}
              className="flex-1 flex items-center justify-center gap-2 px-3 py-2 bg-info-bg text-white rounded-lg hover:bg-info-color transition-colors text-sm font-medium"
            >
              <ExternalLink className="w-4 h-4" />
              View
            </Button>
          )}
          {isUnread && onMarkRead && (
            <Button variant="ghost" size="sm"
              onClick={onMarkRead}
              className="flex items-center justify-center gap-2 px-3 py-2 bg-surface-muted text-fg rounded-lg hover:bg-muted transition-colors text-sm font-medium"
              title="Mark as read"
            >
              <Eye className="w-4 h-4" />
            </Button>
          )}
          {!isArchived && onArchive && (
            <Button variant="ghost" size="sm"
              onClick={onArchive}
              className="flex items-center justify-center gap-2 px-3 py-2 bg-surface-muted text-fg rounded-lg hover:bg-muted transition-colors text-sm font-medium"
              title="Archive"
            >
              <Archive className="w-4 h-4" />
            </Button>
          )}
        </div>
      </div>

      {/* Footer */}
      <div className="px-4 py-3 bg-surface-muted rounded-b-lg">
        <div className="flex items-center justify-between text-xs text-fg-muted">
          <div className="flex items-center gap-1">
            <Clock className="w-3 h-3" />
            <span>{formatTime(notification.createdAt)}</span>
          </div>
          <div className="flex items-center gap-2">
            {isUnread ? (
              <span className="flex items-center gap-1 text-info-color">
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
          <div className="mt-1 text-xs text-fg-muted">
            Read {formatTime(notification.readAt)}
          </div>
        )}
      </div>

      {/* Output Handle */}
      <Handle
        type="source"
        position={Position.Right}
        className="w-3 h-3 bg-success-bg0 border-2 border-border"
      />
    </div>
  );
}

export default memo(NotificationNode);
