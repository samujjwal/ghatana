// ============================================================================
// SessionNode - Canvas node for visualizing collaboration sessions
//
// Features:
// - Session type icons and status
// - Participant list with roles
// - Duration tracking
// - Resource linkage
// - Join/Leave actions
// - Real-time status
// ============================================================================

import { memo, useState, useEffect } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps } from '@xyflow/react';
import {
  Users,
  Code2,
  GitCompare,
  Layout,
  FileText,
  Monitor,
  Video,
  Play,
  Pause,
  Square,
  Calendar,
  Clock,
  Crown,
  Eye,
  LogIn,
  LogOut,
  UserMinus,
  Circle,
} from 'lucide-react';
import { cn } from '../../utils/cn';
import type {
  CollaborationSession,
  CollaborationSessionType,
  CollaborationSessionStatus,
  SessionParticipant,
  SessionParticipantRole,
} from '@ghatana/yappc-api';

export interface SessionNodeData {
  session: CollaborationSession;
  currentUserId?: string;
  onJoin?: () => void;
  onLeave?: () => void;
  onStart?: () => void;
  onPause?: () => void;
  onEnd?: () => void;
  onKick?: (userId: string) => void;
}

const sessionTypeConfig: Record<
  CollaborationSessionType,
  { icon: typeof Users; label: string; color: string; bgColor: string }
> = {
  PAIR_PROGRAMMING: {
    icon: Code2,
    label: 'Pair Programming',
    color: 'text-purple-400',
    bgColor: 'bg-purple-500/20',
  },
  CODE_REVIEW: {
    icon: GitCompare,
    label: 'Code Review',
    color: 'text-green-400',
    bgColor: 'bg-green-500/20',
  },
  WHITEBOARD: {
    icon: Layout,
    label: 'Whiteboard',
    color: 'text-blue-400',
    bgColor: 'bg-blue-500/20',
  },
  DOCUMENT_EDITING: {
    icon: FileText,
    label: 'Document Editing',
    color: 'text-yellow-400',
    bgColor: 'bg-yellow-500/20',
  },
  SCREEN_SHARE: {
    icon: Monitor,
    label: 'Screen Share',
    color: 'text-cyan-400',
    bgColor: 'bg-cyan-500/20',
  },
  MEETING: {
    icon: Video,
    label: 'Meeting',
    color: 'text-orange-400',
    bgColor: 'bg-orange-500/20',
  },
};

const statusConfig: Record<
  CollaborationSessionStatus,
  { color: string; bgColor: string; label: string; icon: typeof Circle }
> = {
  SCHEDULED: {
    color: 'text-blue-400',
    bgColor: 'bg-blue-500/20',
    label: 'Scheduled',
    icon: Calendar,
  },
  ACTIVE: {
    color: 'text-green-400',
    bgColor: 'bg-green-500/20',
    label: 'Active',
    icon: Play,
  },
  PAUSED: {
    color: 'text-yellow-400',
    bgColor: 'bg-yellow-500/20',
    label: 'Paused',
    icon: Pause,
  },
  COMPLETED: {
    color: 'text-slate-400',
    bgColor: 'bg-slate-500/20',
    label: 'Completed',
    icon: Square,
  },
  CANCELLED: {
    color: 'text-red-400',
    bgColor: 'bg-red-500/20',
    label: 'Cancelled',
    icon: Square,
  },
};

const roleIcons: Record<SessionParticipantRole, typeof Crown> = {
  HOST: Crown,
  PARTICIPANT: Users,
  OBSERVER: Eye,
};

const roleColors: Record<SessionParticipantRole, string> = {
  HOST: 'text-yellow-400',
  PARTICIPANT: 'text-blue-400',
  OBSERVER: 'text-slate-400',
};

function SessionNode({ data }: NodeProps<SessionNodeData>) {
  const {
    session,
    currentUserId,
    onJoin,
    onLeave,
    onStart,
    onPause,
    onEnd,
    onKick,
  } = data;

  const [elapsedTime, setElapsedTime] = useState<string>('');

  const typeConfig = sessionTypeConfig[session.type];
  const TypeIcon = typeConfig.icon;
  const statusInfo = statusConfig[session.status];
  const StatusIcon = statusInfo.icon;

  // Check if current user is in the session
  const currentParticipant = session.participants.find(
    (p) => p.userId === currentUserId && p.isActive
  );
  const isHost = session.hostId === currentUserId;
  const isActive = session.status === 'ACTIVE';
  const isPaused = session.status === 'PAUSED';
  const canJoin = !currentParticipant && (isActive || isPaused);
  const canLeave = currentParticipant && !isHost;

  // Calculate elapsed time
  useEffect(() => {
    if (!session.startedAt || session.endedAt) return;

    const updateElapsed = () => {
      const start = new Date(session.startedAt!).getTime();
      const end = session.endedAt ? new Date(session.endedAt).getTime() : Date.now();
      const diff = end - start;

      const hours = Math.floor(diff / 3600000);
      const minutes = Math.floor((diff % 3600000) / 60000);
      const seconds = Math.floor((diff % 60000) / 1000);

      if (hours > 0) {
        setElapsedTime(`${hours}h ${minutes}m`);
      } else if (minutes > 0) {
        setElapsedTime(`${minutes}m ${seconds}s`);
      } else {
        setElapsedTime(`${seconds}s`);
      }
    };

    updateElapsed();
    const interval = setInterval(updateElapsed, 1000);
    return () => clearInterval(interval);
  }, [session.startedAt, session.endedAt]);

  // Format scheduled time
  const formatScheduledTime = (date: string) => {
    const d = new Date(date);
    return d.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  // Get active participants
  const activeParticipants = session.participants.filter((p) => p.isActive);

  return (
    <div
      className={cn(
        'bg-slate-800 rounded-lg border shadow-xl min-w-[320px] max-w-[380px]',
        isActive ? 'border-green-500/50' : 'border-slate-600'
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
              'w-12 h-12 rounded-xl flex items-center justify-center',
              typeConfig.bgColor,
              isActive && 'animate-pulse'
            )}
          >
            <TypeIcon className={cn('w-6 h-6', typeConfig.color)} />
          </div>

          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span
                className={cn(
                  'px-2 py-0.5 text-xs font-medium rounded-full',
                  typeConfig.bgColor,
                  typeConfig.color
                )}
              >
                {typeConfig.label}
              </span>
              <span
                className={cn(
                  'px-2 py-0.5 text-xs font-medium rounded-full flex items-center gap-1',
                  statusInfo.bgColor,
                  statusInfo.color
                )}
              >
                <StatusIcon className="w-3 h-3" />
                {statusInfo.label}
              </span>
            </div>
            <h3 className="text-white font-semibold mt-2 line-clamp-1">
              {session.name}
            </h3>
            {session.description && (
              <p className="text-slate-400 text-xs mt-1 line-clamp-2">
                {session.description}
              </p>
            )}
          </div>
        </div>

        {/* Duration / Scheduled Time */}
        <div className="mt-3 flex items-center gap-4">
          {session.startedAt && (
            <div className="flex items-center gap-2">
              <Clock className="w-4 h-4 text-slate-400" />
              <span className="text-white font-medium">{elapsedTime}</span>
            </div>
          )}
          {session.scheduledAt && session.status === 'SCHEDULED' && (
            <div className="flex items-center gap-2">
              <Calendar className="w-4 h-4 text-blue-400" />
              <span className="text-blue-400 text-sm">
                {formatScheduledTime(session.scheduledAt)}
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Host Info */}
      <div className="p-4 border-b border-slate-700">
        <span className="text-slate-400 text-xs font-medium uppercase tracking-wide mb-2 block">
          Host
        </span>
        <div className="flex items-center gap-2">
          {session.host.avatarUrl ? (
            <img
              src={session.host.avatarUrl}
              alt={session.host.name}
              className="w-8 h-8 rounded-full object-cover"
            />
          ) : (
            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-yellow-500 to-orange-600 flex items-center justify-center">
              <span className="text-white text-xs font-semibold">
                {session.host.name.charAt(0).toUpperCase()}
              </span>
            </div>
          )}
          <div className="flex-1 min-w-0">
            <p className="text-white text-sm font-medium truncate">{session.host.name}</p>
            <p className="text-slate-500 text-xs truncate">{session.host.email}</p>
          </div>
          <Crown className="w-4 h-4 text-yellow-400" />
        </div>
      </div>

      {/* Participants */}
      <div className="p-4 border-b border-slate-700">
        <div className="flex items-center justify-between mb-2">
          <span className="text-slate-400 text-xs font-medium uppercase tracking-wide">
            Participants
          </span>
          <span className="text-slate-400 text-xs">
            {activeParticipants.length}
            {session.maxParticipants && `/${session.maxParticipants}`}
          </span>
        </div>

        <div className="space-y-2">
          {activeParticipants.slice(0, 5).map((participant) => {
            const RoleIcon = roleIcons[participant.role];
            return (
              <div
                key={participant.id}
                className="flex items-center gap-2 p-2 rounded-lg bg-slate-700/50"
              >
                {participant.user.avatarUrl ? (
                  <img
                    src={participant.user.avatarUrl}
                    alt={participant.user.name}
                    className="w-7 h-7 rounded-full object-cover"
                  />
                ) : (
                  <div className="w-7 h-7 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center">
                    <span className="text-white text-xs font-semibold">
                      {participant.user.name.charAt(0).toUpperCase()}
                    </span>
                  </div>
                )}
                <div className="flex-1 min-w-0">
                  <p className="text-white text-sm truncate">{participant.user.name}</p>
                </div>
                <RoleIcon
                  className={cn('w-4 h-4', roleColors[participant.role])}
                  title={participant.role}
                />
                {isHost && participant.userId !== currentUserId && onKick && (
                  <button
                    onClick={() => onKick(participant.userId)}
                    className="p-1 rounded hover:bg-slate-600 text-slate-400 hover:text-red-400 transition-colors"
                    title="Remove from session"
                  >
                    <UserMinus className="w-3 h-3" />
                  </button>
                )}
              </div>
            );
          })}
          {activeParticipants.length > 5 && (
            <p className="text-slate-500 text-xs text-center">
              +{activeParticipants.length - 5} more participants
            </p>
          )}
        </div>

        {/* Capacity Bar */}
        {session.maxParticipants && (
          <div className="mt-3">
            <div className="h-1.5 bg-slate-700 rounded-full overflow-hidden">
              <div
                className={cn(
                  'h-full rounded-full transition-all',
                  activeParticipants.length >= session.maxParticipants
                    ? 'bg-red-500'
                    : activeParticipants.length >= session.maxParticipants * 0.8
                    ? 'bg-yellow-500'
                    : 'bg-green-500'
                )}
                style={{
                  width: `${Math.min(
                    (activeParticipants.length / session.maxParticipants) * 100,
                    100
                  )}%`,
                }}
              />
            </div>
          </div>
        )}
      </div>

      {/* Resource Info */}
      {session.resourceType && session.resourceId && (
        <div className="px-4 py-3 border-b border-slate-700">
          <div className="flex items-center justify-between text-sm">
            <span className="text-slate-400">Resource</span>
            <span className="text-slate-300 font-medium capitalize">
              {session.resourceType.replace(/_/g, ' ').toLowerCase()}
            </span>
          </div>
        </div>
      )}

      {/* Actions */}
      <div className="p-4 border-b border-slate-700">
        <div className="flex flex-wrap items-center gap-2">
          {/* Join/Leave */}
          {canJoin && onJoin && (
            <button
              onClick={onJoin}
              className="flex-1 flex items-center justify-center gap-2 px-3 py-2 bg-green-500 text-white rounded-lg hover:bg-green-600 transition-colors text-sm font-medium"
            >
              <LogIn className="w-4 h-4" />
              Join Session
            </button>
          )}
          {canLeave && onLeave && (
            <button
              onClick={onLeave}
              className="flex-1 flex items-center justify-center gap-2 px-3 py-2 bg-slate-700 text-slate-300 rounded-lg hover:bg-slate-600 transition-colors text-sm font-medium"
            >
              <LogOut className="w-4 h-4" />
              Leave
            </button>
          )}

          {/* Host Controls */}
          {isHost && (
            <>
              {session.status === 'SCHEDULED' && onStart && (
                <button
                  onClick={onStart}
                  className="flex-1 flex items-center justify-center gap-2 px-3 py-2 bg-green-500 text-white rounded-lg hover:bg-green-600 transition-colors text-sm font-medium"
                >
                  <Play className="w-4 h-4" />
                  Start
                </button>
              )}
              {isActive && onPause && (
                <button
                  onClick={onPause}
                  className="flex items-center justify-center gap-2 px-3 py-2 bg-yellow-500/20 text-yellow-400 rounded-lg hover:bg-yellow-500/30 transition-colors text-sm font-medium"
                >
                  <Pause className="w-4 h-4" />
                </button>
              )}
              {isPaused && onStart && (
                <button
                  onClick={onStart}
                  className="flex items-center justify-center gap-2 px-3 py-2 bg-green-500/20 text-green-400 rounded-lg hover:bg-green-500/30 transition-colors text-sm font-medium"
                >
                  <Play className="w-4 h-4" />
                </button>
              )}
              {(isActive || isPaused) && onEnd && (
                <button
                  onClick={onEnd}
                  className="flex items-center justify-center gap-2 px-3 py-2 bg-red-500/20 text-red-400 rounded-lg hover:bg-red-500/30 transition-colors text-sm font-medium"
                >
                  <Square className="w-4 h-4" />
                </button>
              )}
            </>
          )}
        </div>
      </div>

      {/* Footer */}
      <div className="px-4 py-3 bg-slate-900/50 rounded-b-lg">
        <div className="flex items-center justify-between text-xs text-slate-500">
          <span>Created {new Date(session.createdAt).toLocaleDateString()}</span>
          <div className="flex items-center gap-1">
            {isActive && (
              <span className="flex items-center gap-1 text-green-400">
                <Circle className="w-2 h-2 fill-green-400 animate-pulse" />
                Live
              </span>
            )}
            {isPaused && (
              <span className="flex items-center gap-1 text-yellow-400">
                <Pause className="w-3 h-3" />
                Paused
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Output Handles */}
      <Handle
        type="source"
        position={Position.Right}
        id="participants"
        className="w-3 h-3 bg-green-500 border-2 border-slate-800"
        style={{ top: '40%' }}
      />
      <Handle
        type="source"
        position={Position.Right}
        id="resource"
        className="w-3 h-3 bg-purple-500 border-2 border-slate-800"
        style={{ top: '60%' }}
      />
    </div>
  );
}

export default memo(SessionNode);
