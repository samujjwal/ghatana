// ============================================================================
// ThreadNode - Canvas node for visualizing discussion threads
//
// Features:
// - Parent message context
// - Reply count and participants
// - Resolution status
// - Timeline of replies
// - Participant avatars
// ============================================================================

import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { Node, NodeProps } from '@xyflow/react';
import {
  MessageCircle,
  CheckCircle2,
  Circle,
  Clock,
  Users,
  Reply,
  CornerDownRight,
  AlertCircle,
  Lock,
  ArrowRight,
} from 'lucide-react';
import { cn } from '../../utils/cn';
import { Button } from '../../../ui/Button';

interface ThreadUser {
  id: string;
  name: string;
  avatarUrl?: string;
  email?: string;
}

interface ThreadMessage {
  id: string;
  content: string;
  createdAt: string;
  author: ThreadUser;
}

interface ThreadRecord {
  id: string;
  createdAt: string;
  isResolved: boolean;
  messageCount: number;
  participantCount: number;
  lastReplyAt?: string;
  resolvedAt?: string;
  resolvedBy?: ThreadUser;
  participants: ThreadUser[];
  parentMessage: ThreadMessage;
  messages?: ThreadMessage[];
}

export interface ThreadNodeData extends Record<string, unknown> {
  thread: ThreadRecord;
  onOpenThread?: () => void;
  onResolve?: () => void;
  onReopen?: () => void;
}

type ThreadCanvasNode = Node<ThreadNodeData, 'thread'>;

function ThreadNode({ data }: NodeProps<ThreadCanvasNode>) {
  const { thread, onOpenThread, onResolve, onReopen } = data;

  // Get latest messages (up to 5)
  const latestMessages = (thread.messages ?? []).slice(-5);

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

  // Calculate thread activity
  const getActivityLevel = () => {
    if (!thread.lastReplyAt) return 'dormant';
    const diff = Date.now() - new Date(thread.lastReplyAt).getTime();
    const hours = diff / 3600000;
    if (hours < 1) return 'hot';
    if (hours < 24) return 'active';
    if (hours < 168) return 'warm';
    return 'dormant';
  };

  const activityLevel = getActivityLevel();
  const activityColors = {
    hot: 'from-orange-500 to-red-500',
    active: 'from-green-500 to-blue-500',
    warm: 'from-blue-500 to-purple-500',
    dormant: 'from-slate-500 to-slate-600',
  };

  return (
    <div
      className={cn(
        'bg-surface rounded-lg border shadow-xl min-w-[320px] max-w-[380px]',
        thread.isResolved ? 'border-success-border' : 'border-border'
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
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <div
              className={cn(
                'w-8 h-8 rounded-lg flex items-center justify-center bg-gradient-to-br',
                activityColors[activityLevel]
              )}
            >
              <MessageCircle className="w-4 h-4 text-white" />
            </div>
            <div>
              <span className="text-white font-semibold text-sm">Thread</span>
              <div className="flex items-center gap-2 mt-0.5">
                <span className="text-fg-muted text-xs">
                  {thread.messageCount} replies
                </span>
                <span className="text-fg-muted">•</span>
                <span className="text-fg-muted text-xs flex items-center gap-1">
                  <Users className="w-3 h-3" />
                  {thread.participantCount}
                </span>
              </div>
            </div>
          </div>

          {/* Status Badge */}
          {thread.isResolved ? (
            <span className="flex items-center gap-1 px-2 py-1 bg-success-bg0/20 text-success-color text-xs font-medium rounded-full">
              <CheckCircle2 className="w-3 h-3" />
              Resolved
            </span>
          ) : (
            <span className="flex items-center gap-1 px-2 py-1 bg-info-bg/20 text-info-color text-xs font-medium rounded-full">
              <Circle className="w-3 h-3" />
              Open
            </span>
          )}
        </div>
      </div>

      {/* Parent Message */}
      <div className="p-4 border-b border-border bg-surface-muted">
        <span className="text-fg-muted text-xs font-medium uppercase tracking-wide mb-2 block">
          Original Message
        </span>
        <div className="flex items-start gap-2">
          {thread.parentMessage.author.avatarUrl ? (
            <img
              src={thread.parentMessage.author.avatarUrl}
              alt={thread.parentMessage.author.name}
              className="w-8 h-8 rounded-full object-cover flex-shrink-0"
            />
          ) : (
            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center flex-shrink-0">
              <span className="text-white text-xs font-semibold">
                {thread.parentMessage.author.name.charAt(0).toUpperCase()}
              </span>
            </div>
          )}
          <div className="flex-1 min-w-0">
            <span className="text-white text-sm font-medium">
              {thread.parentMessage.author.name}
            </span>
            <p className="text-fg text-sm mt-1 line-clamp-3">
              {thread.parentMessage.content}
            </p>
          </div>
        </div>
      </div>

      {/* Participants */}
      <div className="p-4 border-b border-border">
        <span className="text-fg-muted text-xs font-medium uppercase tracking-wide mb-2 block">
          Participants
        </span>
        <div className="flex items-center">
          <div className="flex -space-x-2">
            {thread.participants.slice(0, 6).map((participant: ThreadUser, index: number) => (
              <div
                key={participant.id}
                className="relative"
                style={{ zIndex: 6 - index }}
                title={participant.name}
              >
                {participant.avatarUrl ? (
                  <img
                    src={participant.avatarUrl}
                    alt={participant.name}
                    className="w-7 h-7 rounded-full object-cover border-2 border-border"
                  />
                ) : (
                  <div className="w-7 h-7 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center border-2 border-border">
                    <span className="text-white text-xs font-semibold">
                      {participant.name.charAt(0).toUpperCase()}
                    </span>
                  </div>
                )}
              </div>
            ))}
          </div>
          {thread.participantCount > 6 && (
            <span className="ml-2 text-fg-muted text-xs">
              +{thread.participantCount - 6} more
            </span>
          )}
        </div>
      </div>

      {/* Latest Replies */}
      {latestMessages.length > 0 && (
        <div className="p-4 border-b border-border">
          <span className="text-fg-muted text-xs font-medium uppercase tracking-wide mb-2 block">
            Latest Replies
          </span>
          <div className="space-y-3">
            {latestMessages.map((message: ThreadMessage, index: number) => (
              <div key={message.id} className="flex items-start gap-2">
                <CornerDownRight className="w-3 h-3 text-fg-muted mt-1.5 flex-shrink-0" />
                {message.author.avatarUrl ? (
                  <img
                    src={message.author.avatarUrl}
                    alt={message.author.name}
                    className="w-6 h-6 rounded-full object-cover flex-shrink-0"
                  />
                ) : (
                  <div className="w-6 h-6 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center flex-shrink-0">
                    <span className="text-white text-[10px] font-semibold">
                      {message.author.name.charAt(0).toUpperCase()}
                    </span>
                  </div>
                )}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-fg text-xs font-medium">
                      {message.author.name}
                    </span>
                    <span className="text-fg-muted text-xs">
                      {formatTime(message.createdAt)}
                    </span>
                  </div>
                  <p className="text-fg-muted text-xs line-clamp-2 mt-0.5">
                    {message.content}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Resolution Info */}
      {thread.isResolved && thread.resolvedBy && (
        <div className="p-4 border-b border-border bg-success-bg0/5">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-success-color" />
            <span className="text-success-color text-sm">
              Resolved by <span className="font-medium">{thread.resolvedBy.name}</span>
            </span>
          </div>
          {thread.resolvedAt && (
            <span className="text-fg-muted text-xs mt-1 block">
              {formatTime(thread.resolvedAt)}
            </span>
          )}
        </div>
      )}

      {/* Actions */}
      <div className="p-4 border-b border-border">
        <div className="flex items-center gap-2">
          {onOpenThread && (
            <Button variant="ghost" size="sm"
              onClick={onOpenThread}
              className="flex-1 flex items-center justify-center gap-2 px-3 py-2 bg-info-bg text-white rounded-lg hover:bg-info-color transition-colors text-sm font-medium"
            >
              <Reply className="w-4 h-4" />
              View Thread
            </Button>
          )}
          {!thread.isResolved && onResolve && (
            <Button variant="ghost" size="sm"
              onClick={onResolve}
              className="flex items-center justify-center gap-2 px-3 py-2 bg-success-bg0/20 text-success-color rounded-lg hover:bg-success-bg0/30 transition-colors text-sm font-medium"
            >
              <CheckCircle2 className="w-4 h-4" />
            </Button>
          )}
          {thread.isResolved && onReopen && (
            <Button variant="ghost" size="sm"
              onClick={onReopen}
              className="flex items-center justify-center gap-2 px-3 py-2 bg-warning-bg0/20 text-warning-color rounded-lg hover:bg-warning-bg0/30 transition-colors text-sm font-medium"
            >
              <AlertCircle className="w-4 h-4" />
            </Button>
          )}
        </div>
      </div>

      {/* Footer */}
      <div className="px-4 py-3 bg-surface-muted rounded-b-lg">
        <div className="flex items-center justify-between text-xs text-fg-muted">
          <div className="flex items-center gap-1">
            <Clock className="w-3 h-3" />
            <span>Started {formatTime(thread.createdAt)}</span>
          </div>
          <div className="flex items-center gap-1">
            {activityLevel === 'hot' && (
              <span className="flex items-center gap-1 text-warning-color">
                <Circle className="w-2 h-2 fill-orange-400 animate-pulse" />
                Hot
              </span>
            )}
            {activityLevel === 'active' && (
              <span className="flex items-center gap-1 text-success-color">
                <Circle className="w-2 h-2 fill-green-400" />
                Active
              </span>
            )}
            {activityLevel === 'warm' && (
              <span className="flex items-center gap-1 text-info-color">
                <Circle className="w-2 h-2 fill-blue-400" />
                Recent
              </span>
            )}
            {activityLevel === 'dormant' && (
              <span className="flex items-center gap-1 text-fg-muted">
                <Circle className="w-2 h-2 fill-slate-500" />
                Dormant
              </span>
            )}
          </div>
        </div>
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

export default memo(ThreadNode);
