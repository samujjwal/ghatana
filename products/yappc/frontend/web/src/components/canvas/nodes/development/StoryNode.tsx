/**
 * Story Node Component
 *
 * Canvas node representing a user story in the development phase.
 * Displays story details, status, tasks, and assignee for visual sprint planning.
 *
 * @doc.type component
 * @doc.purpose Story visualization in development canvas
 * @doc.layer product
 * @doc.pattern ReactFlow Custom Node
 */

import React, { memo, useCallback, useState } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { cn } from '../../utils/cn';
import {
  Layers,
  Bug,
  Wrench,
  Lightbulb,
  TrendingUp,
  CheckSquare,
  Square,
  Clock,
  User,
  MessageSquare,
  Paperclip,
  GitPullRequest,
  AlertTriangle,
  ChevronDown,
  ChevronUp,
  MoreHorizontal,
  Edit2,
  Trash2,
  ArrowRight,
  PlayCircle,
  PauseCircle,
  CheckCircle2,
  XCircle,
  Flag,
} from 'lucide-react';

// =============================================================================
// Types
// =============================================================================

export type StoryType = 'feature' | 'bug' | 'tech_debt' | 'spike' | 'improvement';
export type StoryStatus = 'draft' | 'ready' | 'in_progress' | 'in_review' | 'testing' | 'done' | 'blocked';
export type StoryPriority = 'critical' | 'high' | 'medium' | 'low';

export interface StoryTask {
  readonly id: string;
  readonly title: string;
  readonly completed: boolean;
  readonly assigneeId?: string;
}

export interface StoryAssignee {
  readonly id: string;
  readonly name: string;
  readonly avatarUrl?: string;
}

export interface StoryNodeData {
  readonly label: string;
  readonly key: string;
  readonly description?: string;
  readonly type: StoryType;
  readonly status: StoryStatus;
  readonly priority: StoryPriority;
  readonly points?: number;
  readonly assignee?: StoryAssignee;
  readonly tasks?: readonly StoryTask[];
  readonly labels?: readonly string[];
  readonly commentCount?: number;
  readonly attachmentCount?: number;
  readonly pullRequestCount?: number;
  readonly blockedReason?: string;
  readonly epicName?: string;
  readonly epicColor?: string;
  readonly dueDate?: string;
  readonly nodeId?: string;
  // Callbacks
  readonly onEdit?: (nodeId: string) => void;
  readonly onDelete?: (nodeId: string) => void;
  readonly onStatusChange?: (nodeId: string, status: StoryStatus) => void;
  readonly onAssign?: (nodeId: string) => void;
  readonly onOpenDetail?: (nodeId: string) => void;
  readonly onAddTask?: (nodeId: string) => void;
  readonly onToggleTask?: (nodeId: string, taskId: string) => void;
}

export interface StoryNodeProps extends NodeProps<StoryNodeData> {}

// =============================================================================
// Constants
// =============================================================================

const TYPE_CONFIG: Record<StoryType, { label: string; icon: typeof Layers; color: string; bgColor: string }> = {
  feature: {
    label: 'Feature',
    icon: Layers,
    color: 'text-violet-700',
    bgColor: 'bg-violet-50 border-violet-200',
  },
  bug: {
    label: 'Bug',
    icon: Bug,
    color: 'text-red-700',
    bgColor: 'bg-red-50 border-red-200',
  },
  tech_debt: {
    label: 'Tech Debt',
    icon: Wrench,
    color: 'text-orange-700',
    bgColor: 'bg-orange-50 border-orange-200',
  },
  spike: {
    label: 'Spike',
    icon: Lightbulb,
    color: 'text-amber-700',
    bgColor: 'bg-amber-50 border-amber-200',
  },
  improvement: {
    label: 'Improvement',
    icon: TrendingUp,
    color: 'text-blue-700',
    bgColor: 'bg-blue-50 border-blue-200',
  },
};

const STATUS_CONFIG: Record<StoryStatus, { label: string; icon: typeof PlayCircle; color: string; bgColor: string }> = {
  draft: {
    label: 'Draft',
    icon: Square,
    color: 'text-gray-500',
    bgColor: 'bg-gray-100',
  },
  ready: {
    label: 'Ready',
    icon: PlayCircle,
    color: 'text-blue-600',
    bgColor: 'bg-blue-100',
  },
  in_progress: {
    label: 'In Progress',
    icon: PlayCircle,
    color: 'text-yellow-600',
    bgColor: 'bg-yellow-100',
  },
  in_review: {
    label: 'In Review',
    icon: GitPullRequest,
    color: 'text-purple-600',
    bgColor: 'bg-purple-100',
  },
  testing: {
    label: 'Testing',
    icon: CheckSquare,
    color: 'text-cyan-600',
    bgColor: 'bg-cyan-100',
  },
  done: {
    label: 'Done',
    icon: CheckCircle2,
    color: 'text-emerald-600',
    bgColor: 'bg-emerald-100',
  },
  blocked: {
    label: 'Blocked',
    icon: XCircle,
    color: 'text-red-600',
    bgColor: 'bg-red-100',
  },
};

const PRIORITY_CONFIG: Record<StoryPriority, { label: string; color: string; iconColor: string }> = {
  critical: { label: 'Critical', color: 'text-red-600', iconColor: 'text-red-500' },
  high: { label: 'High', color: 'text-orange-600', iconColor: 'text-orange-500' },
  medium: { label: 'Medium', color: 'text-yellow-600', iconColor: 'text-yellow-500' },
  low: { label: 'Low', color: 'text-gray-500', iconColor: 'text-gray-400' },
};

// =============================================================================
// Sub-Components
// =============================================================================

interface TaskListProps {
  tasks: readonly StoryTask[];
  onToggle?: (taskId: string) => void;
}

const TaskList: React.FC<TaskListProps> = memo(({ tasks, onToggle }) => {
  const completedCount = tasks.filter((t) => t.completed).length;

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-xs">
        <span className="text-gray-500 font-medium">Tasks</span>
        <span className="text-gray-400">
          {completedCount}/{tasks.length}
        </span>
      </div>
      <div className="space-y-1">
        {tasks.slice(0, 3).map((task) => (
          <button
            key={task.id}
            onClick={(e) => {
              e.stopPropagation();
              onToggle?.(task.id);
            }}
            className={cn(
              'flex items-center gap-2 w-full text-left p-1.5 rounded-md',
              'hover:bg-gray-50 transition-colors',
              'text-xs'
            )}
          >
            {task.completed ? (
              <CheckSquare className="w-3.5 h-3.5 text-emerald-500 flex-shrink-0" />
            ) : (
              <Square className="w-3.5 h-3.5 text-gray-400 flex-shrink-0" />
            )}
            <span
              className={cn(
                'truncate',
                task.completed ? 'text-gray-400 line-through' : 'text-gray-700'
              )}
            >
              {task.title}
            </span>
          </button>
        ))}
        {tasks.length > 3 && (
          <div className="text-xs text-gray-400 pl-6">
            +{tasks.length - 3} more tasks
          </div>
        )}
      </div>
    </div>
  );
});

TaskList.displayName = 'TaskList';

// =============================================================================
// Main Component
// =============================================================================

const StoryNodeComponent: React.FC<StoryNodeProps> = ({
  data,
  selected,
  dragging,
}) => {
  const [expanded, setExpanded] = useState(false);
  const [showMenu, setShowMenu] = useState(false);

  const typeConfig = TYPE_CONFIG[data.type];
  const statusConfig = STATUS_CONFIG[data.status];
  const priorityConfig = PRIORITY_CONFIG[data.priority];

  const TypeIcon = typeConfig.icon;
  const StatusIcon = statusConfig.icon;

  const nodeId = data.nodeId || data.key;

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

  const handleOpenDetail = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      data.onOpenDetail?.(nodeId);
    },
    [data, nodeId]
  );

  const handleToggleTask = useCallback(
    (taskId: string) => {
      data.onToggleTask?.(nodeId, taskId);
    },
    [data, nodeId]
  );

  const completedTasks = data.tasks?.filter((t) => t.completed).length ?? 0;
  const totalTasks = data.tasks?.length ?? 0;
  const taskProgress = totalTasks > 0 ? (completedTasks / totalTasks) * 100 : 0;

  return (
    <div
      className={cn(
        'story-node w-72 rounded-xl border shadow-sm transition-all duration-200',
        'bg-white',
        typeConfig.bgColor,
        selected && 'ring-2 ring-violet-500 ring-offset-2',
        dragging && 'opacity-75 shadow-lg scale-[1.02]'
      )}
      onDoubleClick={handleOpenDetail}
    >
      {/* Handles for connections */}
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-gray-400 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-gray-400 !border-2 !border-white"
      />
      <Handle
        type="target"
        position={Position.Top}
        id="top"
        className="!w-3 !h-3 !bg-gray-400 !border-2 !border-white"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        id="bottom"
        className="!w-3 !h-3 !bg-gray-400 !border-2 !border-white"
      />

      {/* Header */}
      <div className="p-3 border-b border-gray-100">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-2 flex-1 min-w-0">
            <div className={cn('p-1.5 rounded-lg', typeConfig.bgColor)}>
              <TypeIcon className={cn('w-4 h-4', typeConfig.color)} />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-xs font-mono text-gray-500">{data.key}</span>
                {data.priority === 'critical' && (
                  <Flag className={cn('w-3 h-3', priorityConfig.iconColor)} />
                )}
              </div>
              <h4 className="font-semibold text-sm text-gray-900 truncate">
                {data.label}
              </h4>
            </div>
          </div>

          <div className="flex items-center gap-1">
            {/* Points badge */}
            {data.points !== undefined && (
              <span className="px-2 py-0.5 bg-gray-100 text-gray-700 text-xs font-medium rounded-full">
                {data.points} pts
              </span>
            )}

            {/* Menu */}
            <div className="relative">
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setShowMenu(!showMenu);
                }}
                className="p-1 rounded hover:bg-gray-100 text-gray-400 hover:text-gray-600"
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
                  <div className="absolute right-0 top-full mt-1 w-36 bg-white rounded-lg shadow-lg border border-gray-200 py-1 z-20">
                    <button
                      onClick={handleEdit}
                      className="flex items-center gap-2 w-full px-3 py-2 text-sm text-gray-700 hover:bg-gray-50"
                    >
                      <Edit2 className="w-4 h-4" />
                      Edit
                    </button>
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
        </div>

        {/* Epic label */}
        {data.epicName && (
          <div className="mt-2 flex items-center gap-1.5">
            <div
              className="w-2 h-2 rounded-full"
              style={{ backgroundColor: data.epicColor || '#8B5CF6' }}
            />
            <span className="text-xs text-gray-500 truncate">{data.epicName}</span>
          </div>
        )}

        {/* Status */}
        <div className="mt-2 flex items-center justify-between">
          <div className={cn('flex items-center gap-1.5 px-2 py-1 rounded-full', statusConfig.bgColor)}>
            <StatusIcon className={cn('w-3.5 h-3.5', statusConfig.color)} />
            <span className={cn('text-xs font-medium', statusConfig.color)}>
              {statusConfig.label}
            </span>
          </div>

          {/* Assignee */}
          {data.assignee ? (
            <div className="flex items-center gap-1.5">
              {data.assignee.avatarUrl ? (
                <img
                  src={data.assignee.avatarUrl}
                  alt={data.assignee.name}
                  className="w-6 h-6 rounded-full"
                />
              ) : (
                <div className="w-6 h-6 rounded-full bg-gradient-to-br from-violet-400 to-purple-500 flex items-center justify-center">
                  <span className="text-[10px] font-medium text-white">
                    {data.assignee.name.charAt(0).toUpperCase()}
                  </span>
                </div>
              )}
              <span className="text-xs text-gray-600">{data.assignee.name}</span>
            </div>
          ) : (
            <button
              onClick={(e) => {
                e.stopPropagation();
                data.onAssign?.(nodeId);
              }}
              className="flex items-center gap-1 px-2 py-1 rounded-full bg-gray-100 hover:bg-gray-200 text-gray-500 text-xs"
            >
              <User className="w-3 h-3" />
              <span>Assign</span>
            </button>
          )}
        </div>

        {/* Blocked warning */}
        {data.status === 'blocked' && data.blockedReason && (
          <div className="mt-2 p-2 bg-red-50 border border-red-200 rounded-lg">
            <div className="flex items-start gap-2">
              <AlertTriangle className="w-4 h-4 text-red-500 flex-shrink-0 mt-0.5" />
              <span className="text-xs text-red-700">{data.blockedReason}</span>
            </div>
          </div>
        )}
      </div>

      {/* Expandable content */}
      {expanded && (
        <div className="p-3 space-y-3 border-b border-gray-100">
          {/* Description */}
          {data.description && (
            <p className="text-xs text-gray-600 line-clamp-2">{data.description}</p>
          )}

          {/* Tasks */}
          {data.tasks && data.tasks.length > 0 && (
            <TaskList tasks={data.tasks} onToggle={handleToggleTask} />
          )}

          {/* Labels */}
          {data.labels && data.labels.length > 0 && (
            <div className="flex flex-wrap gap-1">
              {data.labels.map((label) => (
                <span
                  key={label}
                  className="px-2 py-0.5 bg-gray-100 text-gray-600 text-[10px] rounded-full"
                >
                  {label}
                </span>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Footer */}
      <div className="p-3 flex items-center justify-between">
        <div className="flex items-center gap-3 text-gray-400">
          {/* Task progress */}
          {totalTasks > 0 && (
            <div className="flex items-center gap-1.5">
              <CheckSquare className="w-3.5 h-3.5" />
              <span className="text-xs">
                {completedTasks}/{totalTasks}
              </span>
              <div className="w-12 h-1.5 bg-gray-200 rounded-full overflow-hidden">
                <div
                  className="h-full bg-emerald-500 rounded-full transition-all"
                  style={{ width: `${taskProgress}%` }}
                />
              </div>
            </div>
          )}

          {/* Comments */}
          {(data.commentCount ?? 0) > 0 && (
            <div className="flex items-center gap-1">
              <MessageSquare className="w-3.5 h-3.5" />
              <span className="text-xs">{data.commentCount}</span>
            </div>
          )}

          {/* Attachments */}
          {(data.attachmentCount ?? 0) > 0 && (
            <div className="flex items-center gap-1">
              <Paperclip className="w-3.5 h-3.5" />
              <span className="text-xs">{data.attachmentCount}</span>
            </div>
          )}

          {/* Pull Requests */}
          {(data.pullRequestCount ?? 0) > 0 && (
            <div className="flex items-center gap-1">
              <GitPullRequest className="w-3.5 h-3.5" />
              <span className="text-xs">{data.pullRequestCount}</span>
            </div>
          )}

          {/* Due date */}
          {data.dueDate && (
            <div className="flex items-center gap-1">
              <Clock className="w-3.5 h-3.5" />
              <span className="text-xs">{data.dueDate}</span>
            </div>
          )}
        </div>

        {/* Expand toggle */}
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
 * Memoized Story Node
 *
 * Performance optimized with React.memo to prevent unnecessary re-renders
 * during canvas operations like panning and zooming.
 */
export const StoryNode = memo(StoryNodeComponent);
StoryNode.displayName = 'StoryNode';

export default StoryNode;
