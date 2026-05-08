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
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import { cn } from '../../utils/cn';
import { Button } from '../../../ui/Button';
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

export interface StoryNodeData extends Record<string, unknown> {
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

type StoryCanvasNode = Node<StoryNodeData>;

export interface StoryNodeProps extends NodeProps<StoryCanvasNode> {}

// =============================================================================
// Constants
// =============================================================================

const TYPE_CONFIG: Record<StoryType, { label: string; icon: typeof Layers; color: string; bgColor: string }> = {
  feature: {
    label: 'Feature',
    icon: Layers,
    color: 'text-info-color',
    bgColor: 'bg-info-bg border-info-border',
  },
  bug: {
    label: 'Bug',
    icon: Bug,
    color: 'text-destructive',
    bgColor: 'bg-destructive-bg border-destructive-border',
  },
  tech_debt: {
    label: 'Tech Debt',
    icon: Wrench,
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg border-warning-border',
  },
  spike: {
    label: 'Spike',
    icon: Lightbulb,
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg border-warning-border',
  },
  improvement: {
    label: 'Improvement',
    icon: TrendingUp,
    color: 'text-success-color',
    bgColor: 'bg-success-bg border-success-border',
  },
};

const STATUS_CONFIG: Record<StoryStatus, { label: string; icon: typeof PlayCircle; color: string; bgColor: string }> = {
  draft: {
    label: 'Draft',
    icon: Square,
    color: 'text-muted-foreground',
    bgColor: 'bg-muted',
  },
  ready: {
    label: 'Ready',
    icon: PlayCircle,
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
  },
  in_progress: {
    label: 'In Progress',
    icon: PlayCircle,
    color: 'text-warning-color',
    bgColor: 'bg-warning-bg',
  },
  in_review: {
    label: 'In Review',
    icon: GitPullRequest,
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
  },
  testing: {
    label: 'Testing',
    icon: CheckSquare,
    color: 'text-info-color',
    bgColor: 'bg-info-bg',
  },
  done: {
    label: 'Done',
    icon: CheckCircle2,
    color: 'text-success-color',
    bgColor: 'bg-success-bg',
  },
  blocked: {
    label: 'Blocked',
    icon: XCircle,
    color: 'text-destructive',
    bgColor: 'bg-destructive-bg',
  },
};

const PRIORITY_CONFIG: Record<StoryPriority, { label: string; color: string; iconColor: string }> = {
  critical: { label: 'Critical', color: 'text-danger-color', iconColor: 'text-danger-color' },
  high: { label: 'High', color: 'text-warning-color', iconColor: 'text-warning-color' },
  medium: { label: 'Medium', color: 'text-warning-color', iconColor: 'text-warning-color' },
  low: { label: 'Low', color: 'text-muted-foreground', iconColor: 'text-muted-foreground' },
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
        <span className="text-muted-foreground font-medium">Tasks</span>
        <span className="text-muted-foreground">
          {completedCount}/{tasks.length}
        </span>
      </div>
      <div className="space-y-1">
        {tasks.slice(0, 3).map((task) => (
          <Button variant="ghost" size="sm"
            key={task.id}
            onClick={(e) => {
              e.stopPropagation();
              onToggle?.(task.id);
            }}
            className={cn(
              'flex w-full items-center justify-start gap-2 text-left p-1.5 rounded-md',
              'hover:bg-muted/20 transition-colors',
              'text-xs'
            )}
          >
            {task.completed ? (
              <CheckSquare className="w-3.5 h-3.5 text-success-color flex-shrink-0" />
            ) : (
              <Square className="w-3.5 h-3.5 text-muted-foreground flex-shrink-0" />
            )}
            <span
              className={cn(
                'truncate',
                task.completed ? 'text-muted-foreground line-through' : 'text-foreground'
              )}
            >
              {task.title}
            </span>
          </Button>
        ))}
        {tasks.length > 3 && (
          <div className="text-xs text-muted-foreground pl-6">
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
        'bg-surface',
        typeConfig.bgColor,
        selected && 'ring-2 ring-info-border ring-offset-2',
        dragging && 'opacity-75 shadow-lg scale-[1.02]'
      )}
      onDoubleClick={handleOpenDetail}
    >
      {/* Handles for connections */}
      <Handle
        type="target"
        position={Position.Left}
        className="!w-3 !h-3 !bg-info-color !border-2 !border-background"
      />
      <Handle
        type="source"
        position={Position.Right}
        className="!w-3 !h-3 !bg-info-color !border-2 !border-background"
      />
      <Handle
        type="target"
        position={Position.Top}
        id="top"
        className="!w-3 !h-3 !bg-info-color !border-2 !border-background"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        id="bottom"
        className="!w-3 !h-3 !bg-info-color !border-2 !border-background"
      />

      {/* Header */}
      <div className="p-3 border-b border-border">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-2 flex-1 min-w-0">
            <div className={cn('p-1.5 rounded-lg', typeConfig.bgColor)}>
              <TypeIcon className={cn('w-4 h-4', typeConfig.color)} />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-xs font-mono text-muted-foreground">{data.key}</span>
                {data.priority === 'critical' && (
                  <Flag className={cn('w-3 h-3', priorityConfig.iconColor)} />
                )}
              </div>
              <h4 className="font-semibold text-sm text-fg truncate">
                {data.label}
              </h4>
            </div>
          </div>

          <div className="flex items-center gap-1">
            {/* Points badge */}
            {data.points !== undefined && (
              <span className="px-2 py-0.5 bg-muted text-fg text-xs font-medium rounded-full">
                {data.points} pts
              </span>
            )}

            {/* Menu */}
            <div className="relative">
              <Button variant="ghost" size="sm"
                onClick={(e) => {
                  e.stopPropagation();
                  setShowMenu(!showMenu);
                }}
                className="p-1 rounded hover:bg-muted text-muted-foreground hover:text-fg"
              >
                <MoreHorizontal className="w-4 h-4" />
              </Button>

              {showMenu && (
                <>
                  <div
                    className="fixed inset-0 z-10"
                    onClick={(e) => {
                      e.stopPropagation();
                      setShowMenu(false);
                    }}
                  />
                  <div className="absolute right-0 top-full mt-1 w-36 bg-surface rounded-lg shadow-lg border border-border py-1 z-20">
                    <Button variant="ghost" size="sm"
                      onClick={handleEdit}
                      className="flex w-full items-center justify-start gap-2 px-3 py-2 text-sm text-fg hover:bg-muted/40"
                    >
                      <Edit2 className="w-4 h-4" />
                      Edit
                    </Button>
                    <Button variant="ghost" size="sm"
                      onClick={handleDelete}
                      className="flex w-full items-center justify-start gap-2 px-3 py-2 text-sm text-destructive hover:bg-destructive-bg"
                    >
                      <Trash2 className="w-4 h-4" />
                      Delete
                    </Button>
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
            <span className="text-xs text-muted-foreground truncate">{data.epicName}</span>
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
              <span className="text-xs text-fg-muted">{data.assignee.name}</span>
            </div>
          ) : (
            <Button variant="ghost" size="sm"
              onClick={(e) => {
                e.stopPropagation();
                data.onAssign?.(nodeId);
              }}
              className="flex items-center gap-1 px-2 py-1 rounded-full bg-muted hover:bg-muted/80 text-muted-foreground text-xs"
            >
              <User className="w-3 h-3" />
              <span>Assign</span>
            </Button>
          )}
        </div>

        {/* Blocked warning */}
        {data.status === 'blocked' && data.blockedReason && (
          <div className="mt-2 p-2 bg-destructive-bg border border-destructive-border rounded-lg">
            <div className="flex items-start gap-2">
              <AlertTriangle className="w-4 h-4 text-destructive flex-shrink-0 mt-0.5" />
              <span className="text-xs text-destructive">{data.blockedReason}</span>
            </div>
          </div>
        )}
      </div>

      {/* Expandable content */}
      {expanded && (
        <div className="p-3 space-y-3 border-b border-border">
          {/* Description */}
          {data.description && (
            <p className="text-xs text-fg-muted line-clamp-2">{data.description}</p>
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
                  className="px-2 py-0.5 bg-muted text-muted-foreground text-[10px] rounded-full"
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
        <div className="flex items-center gap-3 text-muted-foreground">
          {/* Task progress */}
          {totalTasks > 0 && (
            <div className="flex items-center gap-1.5">
              <CheckSquare className="w-3.5 h-3.5" />
              <span className="text-xs">
                {completedTasks}/{totalTasks}
              </span>
              <div className="w-12 h-1.5 bg-muted rounded-full overflow-hidden">
                <div
                  className="h-full bg-success-color rounded-full transition-all"
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
        <Button variant="ghost" size="sm"
          onClick={handleToggleExpand}
          className="p-1 rounded hover:bg-muted text-muted-foreground hover:text-fg"
        >
          {expanded ? (
            <ChevronUp className="w-4 h-4" />
          ) : (
            <ChevronDown className="w-4 h-4" />
          )}
        </Button>
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
