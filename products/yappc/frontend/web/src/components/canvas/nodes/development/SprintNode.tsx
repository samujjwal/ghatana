/**
 * Sprint Node Component
 *
 * Canvas node representing a sprint in the development phase.
 * Shows sprint details, progress, burndown preview, and contained stories.
 *
 * @doc.type component
 * @doc.purpose Sprint visualization in development canvas
 * @doc.layer product
 * @doc.pattern ReactFlow Custom Node
 */

import React, { memo, useCallback, useState } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { cn } from '../../utils/cn';
import {
  Calendar,
  Target,
  Users,
  TrendingUp,
  Clock,
  ChevronDown,
  ChevronUp,
  Play,
  CheckCircle2,
  XCircle,
  PauseCircle,
  MoreHorizontal,
  Edit2,
  Trash2,
  BarChart3,
  AlertTriangle,
  Layers,
} from 'lucide-react';

// =============================================================================
// Types
// =============================================================================

export type SprintStatus = 'planning' | 'active' | 'completed' | 'cancelled';

export interface SprintMember {
  readonly id: string;
  readonly name: string;
  readonly avatarUrl?: string;
  readonly allocatedPoints: number;
  readonly completedPoints: number;
}

export interface SprintBurndown {
  readonly date: string;
  readonly planned: number;
  readonly actual: number;
}

export interface SprintNodeData {
  readonly label: string;
  readonly number: number;
  readonly goal?: string;
  readonly status: SprintStatus;
  readonly startDate: string;
  readonly endDate: string;
  readonly plannedPoints: number;
  readonly completedPoints: number;
  readonly storyCount: number;
  readonly completedStoryCount: number;
  readonly members?: readonly SprintMember[];
  readonly burndownData?: readonly SprintBurndown[];
  readonly daysRemaining?: number;
  readonly velocity?: number;
  readonly nodeId?: string;
  // Callbacks
  readonly onEdit?: (nodeId: string) => void;
  readonly onDelete?: (nodeId: string) => void;
  readonly onStart?: (nodeId: string) => void;
  readonly onComplete?: (nodeId: string) => void;
  readonly onOpenBoard?: (nodeId: string) => void;
  readonly onOpenBurndown?: (nodeId: string) => void;
  readonly onAddStory?: (nodeId: string) => void;
}

export interface SprintNodeProps extends NodeProps<SprintNodeData> {}

// =============================================================================
// Constants
// =============================================================================

const STATUS_CONFIG: Record<SprintStatus, { label: string; icon: typeof Play; color: string; bgColor: string }> = {
  planning: {
    label: 'Planning',
    icon: PauseCircle,
    color: 'text-gray-600',
    bgColor: 'bg-gray-100 border-gray-200',
  },
  active: {
    label: 'Active',
    icon: Play,
    color: 'text-emerald-600',
    bgColor: 'bg-emerald-50 border-emerald-200',
  },
  completed: {
    label: 'Completed',
    icon: CheckCircle2,
    color: 'text-blue-600',
    bgColor: 'bg-blue-50 border-blue-200',
  },
  cancelled: {
    label: 'Cancelled',
    icon: XCircle,
    color: 'text-red-600',
    bgColor: 'bg-red-50 border-red-200',
  },
};

// =============================================================================
// Sub-Components
// =============================================================================

interface MiniBurndownProps {
  data: readonly SprintBurndown[];
  width?: number;
  height?: number;
}

const MiniBurndown: React.FC<MiniBurndownProps> = memo(({ data, width = 100, height = 40 }) => {
  if (data.length === 0) return null;

  const maxValue = Math.max(...data.map((d) => Math.max(d.planned, d.actual)));
  const points = data.map((d, i) => {
    const x = (i / (data.length - 1)) * width;
    const y = height - (d.actual / maxValue) * height;
    return `${x},${y}`;
  }).join(' ');

  const idealPoints = data.map((d, i) => {
    const x = (i / (data.length - 1)) * width;
    const y = height - (d.planned / maxValue) * height;
    return `${x},${y}`;
  }).join(' ');

  return (
    <svg width={width} height={height} className="overflow-visible">
      {/* Ideal line (dashed) */}
      <polyline
        points={idealPoints}
        fill="none"
        stroke="#9CA3AF"
        strokeWidth="1"
        strokeDasharray="3,3"
      />
      {/* Actual line */}
      <polyline
        points={points}
        fill="none"
        stroke="#8B5CF6"
        strokeWidth="2"
      />
    </svg>
  );
});

MiniBurndown.displayName = 'MiniBurndown';

interface TeamAvatarsProps {
  members: readonly SprintMember[];
  maxDisplay?: number;
}

const TeamAvatars: React.FC<TeamAvatarsProps> = memo(({ members, maxDisplay = 4 }) => {
  const displayed = members.slice(0, maxDisplay);
  const remaining = members.length - maxDisplay;

  return (
    <div className="flex -space-x-2">
      {displayed.map((member) => (
        <div
          key={member.id}
          className="w-6 h-6 rounded-full border-2 border-white overflow-hidden"
          title={`${member.name}: ${member.completedPoints}/${member.allocatedPoints} pts`}
        >
          {member.avatarUrl ? (
            <img src={member.avatarUrl} alt={member.name} className="w-full h-full object-cover" />
          ) : (
            <div className="w-full h-full bg-gradient-to-br from-violet-400 to-purple-500 flex items-center justify-center">
              <span className="text-[10px] font-medium text-white">
                {member.name.charAt(0).toUpperCase()}
              </span>
            </div>
          )}
        </div>
      ))}
      {remaining > 0 && (
        <div className="w-6 h-6 rounded-full border-2 border-white bg-gray-200 flex items-center justify-center">
          <span className="text-[9px] font-medium text-gray-600">+{remaining}</span>
        </div>
      )}
    </div>
  );
});

TeamAvatars.displayName = 'TeamAvatars';

// =============================================================================
// Main Component
// =============================================================================

const SprintNodeComponent: React.FC<SprintNodeProps> = ({
  data,
  selected,
  dragging,
}) => {
  const [expanded, setExpanded] = useState(false);
  const [showMenu, setShowMenu] = useState(false);

  const statusConfig = STATUS_CONFIG[data.status];
  const StatusIcon = statusConfig.icon;

  const nodeId = data.nodeId || `sprint-${data.number}`;

  const progress = data.plannedPoints > 0
    ? Math.round((data.completedPoints / data.plannedPoints) * 100)
    : 0;

  const storyProgress = data.storyCount > 0
    ? Math.round((data.completedStoryCount / data.storyCount) * 100)
    : 0;

  const handleToggleExpand = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    setExpanded((prev) => !prev);
  }, []);

  const handleEdit = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      setShowMenu(false);
      data.onEdit?.(nodeId);
    },
    [data, nodeId]
  );

  const handleDelete = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      setShowMenu(false);
      data.onDelete?.(nodeId);
    },
    [data, nodeId]
  );

  const handleOpenBoard = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      data.onOpenBoard?.(nodeId);
    },
    [data, nodeId]
  );

  const handleStart = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      data.onStart?.(nodeId);
    },
    [data, nodeId]
  );

  const handleComplete = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      data.onComplete?.(nodeId);
    },
    [data, nodeId]
  );

  return (
    <div
      className={cn(
        'sprint-node w-80 rounded-xl border shadow-sm transition-all duration-200',
        'bg-white',
        statusConfig.bgColor,
        selected && 'ring-2 ring-violet-500 ring-offset-2',
        dragging && 'opacity-75 shadow-lg scale-[1.02]'
      )}
      onDoubleClick={handleOpenBoard}
    >
      {/* Handles for connections */}
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-violet-500 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-violet-500 !border-2 !border-white"
      />

      {/* Header */}
      <div className="p-4 border-b border-gray-100">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-3 flex-1 min-w-0">
            <div className={cn('p-2 rounded-lg', statusConfig.bgColor)}>
              <Target className={cn('w-5 h-5', statusConfig.color)} />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-xs font-mono text-gray-500">
                  Sprint {data.number}
                </span>
                <div className={cn('flex items-center gap-1 px-2 py-0.5 rounded-full', statusConfig.bgColor)}>
                  <StatusIcon className={cn('w-3 h-3', statusConfig.color)} />
                  <span className={cn('text-[10px] font-medium', statusConfig.color)}>
                    {statusConfig.label}
                  </span>
                </div>
              </div>
              <h4 className="font-semibold text-gray-900 truncate">{data.label}</h4>
            </div>
          </div>

          {/* Menu */}
          <div className="relative">
            <button
              onClick={(e) => {
                e.stopPropagation();
                setShowMenu(!showMenu);
              }}
              className="p-1.5 rounded hover:bg-gray-100 text-gray-400 hover:text-gray-600"
            >
              <MoreHorizontal className="w-4 h-4" />
            </button>

            {showMenu && (
              <>
                <div
                  className="fixed inset-0 z-10"
                  onClick={(e) => {
                    e.stopPropagation();
                    setShowMenu(false);
                  }}
                />
                <div className="absolute right-0 top-full mt-1 w-40 bg-white rounded-lg shadow-lg border border-gray-200 py-1 z-20">
                  <button
                    onClick={handleEdit}
                    className="flex items-center gap-2 w-full px-3 py-2 text-sm text-gray-700 hover:bg-gray-50"
                  >
                    <Edit2 className="w-4 h-4" />
                    Edit Sprint
                  </button>
                  {data.status === 'planning' && (
                    <button
                      onClick={handleStart}
                      className="flex items-center gap-2 w-full px-3 py-2 text-sm text-emerald-600 hover:bg-emerald-50"
                    >
                      <Play className="w-4 h-4" />
                      Start Sprint
                    </button>
                  )}
                  {data.status === 'active' && (
                    <button
                      onClick={handleComplete}
                      className="flex items-center gap-2 w-full px-3 py-2 text-sm text-blue-600 hover:bg-blue-50"
                    >
                      <CheckCircle2 className="w-4 h-4" />
                      Complete Sprint
                    </button>
                  )}
                  <button
                    onClick={handleDelete}
                    className="flex items-center gap-2 w-full px-3 py-2 text-sm text-red-600 hover:bg-red-50"
                  >
                    <Trash2 className="w-4 h-4" />
                    Delete
                  </button>
                </div>
              </>
            )}
          </div>
        </div>

        {/* Goal */}
        {data.goal && (
          <p className="mt-2 text-sm text-gray-600 line-clamp-2">{data.goal}</p>
        )}

        {/* Date range */}
        <div className="mt-3 flex items-center gap-2 text-xs text-gray-500">
          <Calendar className="w-3.5 h-3.5" />
          <span>{data.startDate}</span>
          <span>→</span>
          <span>{data.endDate}</span>
          {data.daysRemaining !== undefined && data.status === 'active' && (
            <>
              <span className="text-gray-300">|</span>
              <Clock className="w-3.5 h-3.5" />
              <span className={cn(
                data.daysRemaining <= 2 ? 'text-red-500 font-medium' : ''
              )}>
                {data.daysRemaining} days left
              </span>
            </>
          )}
        </div>
      </div>

      {/* Progress section */}
      <div className="p-4 space-y-3">
        {/* Points progress */}
        <div>
          <div className="flex items-center justify-between text-xs mb-1.5">
            <span className="text-gray-500">Points Progress</span>
            <span className="font-medium text-gray-700">
              {data.completedPoints} / {data.plannedPoints} pts ({progress}%)
            </span>
          </div>
          <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
            <div
              className={cn(
                'h-full rounded-full transition-all',
                progress >= 100 ? 'bg-emerald-500' : 'bg-violet-500'
              )}
              style={{ width: `${Math.min(progress, 100)}%` }}
            />
          </div>
        </div>

        {/* Stories progress */}
        <div>
          <div className="flex items-center justify-between text-xs mb-1.5">
            <span className="text-gray-500">Stories</span>
            <span className="font-medium text-gray-700">
              {data.completedStoryCount} / {data.storyCount} ({storyProgress}%)
            </span>
          </div>
          <div className="h-2 bg-gray-200 rounded-full overflow-hidden">
            <div
              className="h-full bg-blue-500 rounded-full transition-all"
              style={{ width: `${Math.min(storyProgress, 100)}%` }}
            />
          </div>
        </div>

        {/* Mini burndown */}
        {data.burndownData && data.burndownData.length > 1 && (
          <div className="mt-3 pt-3 border-t border-gray-100">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs text-gray-500">Burndown</span>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  data.onOpenBurndown?.(nodeId);
                }}
                className="text-xs text-violet-600 hover:text-violet-700"
              >
                View Chart
              </button>
            </div>
            <MiniBurndown data={data.burndownData} />
          </div>
        )}
      </div>

      {/* Expandable content */}
      {expanded && (
        <div className="px-4 pb-4 space-y-3 border-t border-gray-100 pt-3">
          {/* Velocity */}
          {data.velocity !== undefined && (
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-xs text-gray-500">
                <TrendingUp className="w-3.5 h-3.5" />
                <span>Velocity</span>
              </div>
              <span className="text-sm font-medium text-gray-700">
                {data.velocity} pts/sprint
              </span>
            </div>
          )}

          {/* Team members */}
          {data.members && data.members.length > 0 && (
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-xs text-gray-500">
                <Users className="w-3.5 h-3.5" />
                <span>Team ({data.members.length})</span>
              </div>
              <TeamAvatars members={data.members} />
            </div>
          )}

          {/* Quick actions */}
          <div className="flex gap-2 pt-2">
            <button
              onClick={handleOpenBoard}
              className={cn(
                'flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-medium',
                'bg-violet-100 text-violet-700 hover:bg-violet-200 transition-colors'
              )}
            >
              <Layers className="w-3.5 h-3.5" />
              Open Board
            </button>
            <button
              onClick={(e) => {
                e.stopPropagation();
                data.onAddStory?.(nodeId);
              }}
              className={cn(
                'flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-medium',
                'bg-gray-100 text-gray-700 hover:bg-gray-200 transition-colors'
              )}
            >
              <Layers className="w-3.5 h-3.5" />
              Add Story
            </button>
          </div>
        </div>
      )}

      {/* Footer */}
      <div className="px-4 py-3 border-t border-gray-100 flex items-center justify-between">
        <div className="flex items-center gap-3 text-gray-400">
          <div className="flex items-center gap-1.5" title="Total stories">
            <Layers className="w-3.5 h-3.5" />
            <span className="text-xs">{data.storyCount}</span>
          </div>
          {data.velocity !== undefined && (
            <div className="flex items-center gap-1.5" title="Velocity">
              <TrendingUp className="w-3.5 h-3.5" />
              <span className="text-xs">{data.velocity}</span>
            </div>
          )}
          {data.members && (
            <div className="flex items-center gap-1.5" title="Team members">
              <Users className="w-3.5 h-3.5" />
              <span className="text-xs">{data.members.length}</span>
            </div>
          )}
        </div>

        <button
          onClick={handleToggleExpand}
          className="p-1 rounded hover:bg-gray-100 text-gray-400 hover:text-gray-600"
        >
          {expanded ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </button>
      </div>
    </div>
  );
};

/**
 * Memoized Sprint Node
 *
 * Performance optimized with React.memo to prevent unnecessary re-renders
 * during canvas operations like panning and zooming.
 */
export const SprintNode = memo(SprintNodeComponent);
SprintNode.displayName = 'SprintNode';

export default SprintNode;
