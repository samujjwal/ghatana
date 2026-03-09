/**
 * Sprint Board Component
 *
 * @description Kanban-style sprint board with drag-and-drop story management,
 * WIP limits, swimlanes, and real-time collaboration.
 *
 * @doc.type component
 * @doc.purpose Sprint task management
 * @doc.layer presentation
 * @doc.phase development
 */

import React, {
  useState,
  useCallback,
  useMemo,
  useRef,
  useEffect,
} from 'react';
import {
  DndContext,
  DragOverlay,
  closestCorners,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  DragStartEvent,
  DragEndEvent,
  DragOverEvent,
  UniqueIdentifier,
} from '@dnd-kit/core';
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
  useSortable,
  arrayMove,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Plus,
  MoreHorizontal,
  AlertCircle,
  Clock,
  User,
  Tag,
  MessageSquare,
  Paperclip,
  CheckSquare,
  GitPullRequest,
  Flag,
  Calendar,
  Filter,
  Search,
  Settings,
  RefreshCw,
  Maximize2,
  ChevronDown,
  Circle,
  CheckCircle2,
  XCircle,
  PauseCircle,
  PlayCircle,
} from 'lucide-react';

import { cn } from '@ghatana/ui';
import { Button } from '@ghatana/ui';
import { Input } from '@ghatana/ui';
import { Badge } from '@ghatana/ui';
import { Avatar } from '@ghatana/ui';
import { AvatarFallback, AvatarImage } from '@ghatana/yappc-ui';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
} from '@ghatana/yappc-ui';
import { Tooltip } from '@ghatana/ui';
import { TooltipContent, TooltipTrigger } from '@ghatana/yappc-ui';
import { Dialog, DialogContent, DialogTitle } from '@ghatana/ui';
import { DialogHeader, DialogTrigger } from '@ghatana/yappc-ui';
import { ScrollArea } from '@ghatana/yappc-ui';
import { Progress } from '@ghatana/ui';

import {
  activeSprintAtom,
  sprintBoardAtom,
  selectedStoryAtom,
  sprintStoriesAtom,
} from '@ghatana/yappc-canvas';

// =============================================================================
// Types
// =============================================================================

export type StoryStatus =
  | 'backlog'
  | 'todo'
  | 'in_progress'
  | 'in_review'
  | 'done'
  | 'blocked';

export type StoryPriority = 'critical' | 'high' | 'medium' | 'low';
export type StoryType = 'feature' | 'bug' | 'tech_debt' | 'spike' | 'chore';

export interface Story {
  id: string;
  title: string;
  description?: string;
  status: StoryStatus;
  priority: StoryPriority;
  type: StoryType;
  points?: number;
  assignee?: {
    id: string;
    name: string;
    avatar?: string;
  };
  labels: string[];
  dueDate?: string;
  comments: number;
  attachments: number;
  subtasks: {
    total: number;
    completed: number;
  };
  pullRequest?: {
    id: string;
    status: 'open' | 'merged' | 'closed';
  };
  blocked?: {
    reason: string;
    blockedBy?: string;
  };
  createdAt: string;
  updatedAt: string;
}

export interface BoardColumn {
  id: StoryStatus;
  title: string;
  wipLimit?: number;
  stories: Story[];
}

export interface Sprint {
  id: string;
  name: string;
  goal?: string;
  startDate: string;
  endDate: string;
  status: 'planning' | 'active' | 'completed';
  velocity?: number;
  commitment: number;
  completed: number;
}

interface SprintBoardProps {
  sprintId: string;
  sprint?: Sprint;
  columns?: BoardColumn[];
  onStoryMove?: (storyId: string, newStatus: StoryStatus, newIndex: number) => void;
  onStoryClick?: (story: Story) => void;
  onStoryCreate?: (status: StoryStatus) => void;
  onRefresh?: () => void;
  isLoading?: boolean;
  className?: string;
}

// =============================================================================
// Subcomponents
// =============================================================================

const statusIcons: Record<StoryStatus, React.ComponentType<{ className?: string }>> = {
  backlog: Circle,
  todo: Circle,
  in_progress: PlayCircle,
  in_review: PauseCircle,
  done: CheckCircle2,
  blocked: XCircle,
};

const priorityColors: Record<StoryPriority, string> = {
  critical: 'text-red-500 bg-red-500/10',
  high: 'text-orange-500 bg-orange-500/10',
  medium: 'text-yellow-500 bg-yellow-500/10',
  low: 'text-green-500 bg-green-500/10',
};

const typeIcons: Record<StoryType, { icon: React.ComponentType<{ className?: string }>; color: string }> = {
  feature: { icon: Flag, color: 'text-violet-500' },
  bug: { icon: AlertCircle, color: 'text-red-500' },
  tech_debt: { icon: Settings, color: 'text-amber-500' },
  spike: { icon: Search, color: 'text-blue-500' },
  chore: { icon: CheckSquare, color: 'text-zinc-500' },
};

const StoryCard = React.memo(({
  story,
  isDragging,
  onClick,
}: {
  story: Story;
  isDragging?: boolean;
  onClick?: () => void;
}) => {
  const TypeIcon = typeIcons[story.type].icon;
  const PriorityBadge = () => (
    <Badge variant="outline" className={cn('text-xs', priorityColors[story.priority])}>
      {story.priority}
    </Badge>
  );

  return (
    <div
      onClick={onClick}
      className={cn(
        'p-3 bg-zinc-800 rounded-lg border border-zinc-700 cursor-pointer',
        'hover:border-zinc-600 transition-all',
        isDragging && 'opacity-50 ring-2 ring-violet-500',
        story.blocked && 'border-l-4 border-l-red-500'
      )}
    >
      {/* Header */}
      <div className="flex items-start justify-between gap-2 mb-2">
        <div className="flex items-center gap-2">
          <TypeIcon className={cn('w-4 h-4', typeIcons[story.type].color)} />
          <span className="text-xs text-zinc-500">{story.id}</span>
        </div>
        <PriorityBadge />
      </div>

      {/* Title */}
      <h4 className="text-sm font-medium text-zinc-100 mb-2 line-clamp-2">
        {story.title}
      </h4>

      {/* Blocked indicator */}
      {story.blocked && (
        <div className="flex items-center gap-2 p-2 mb-2 rounded bg-red-500/10 text-xs text-red-400">
          <XCircle className="w-3.5 h-3.5" />
          <span className="line-clamp-1">{story.blocked.reason}</span>
        </div>
      )}

      {/* Labels */}
      {story.labels.length > 0 && (
        <div className="flex flex-wrap gap-1 mb-2">
          {story.labels.slice(0, 3).map((label) => (
            <Badge key={label} variant="secondary" className="text-xs px-1.5 py-0">
              {label}
            </Badge>
          ))}
          {story.labels.length > 3 && (
            <Badge variant="secondary" className="text-xs px-1.5 py-0">
              +{story.labels.length - 3}
            </Badge>
          )}
        </div>
      )}

      {/* Footer */}
      <div className="flex items-center justify-between mt-3 pt-2 border-t border-zinc-700">
        <div className="flex items-center gap-2">
          {/* Points */}
          {story.points !== undefined && (
            <Badge variant="outline" className="text-xs px-1.5 py-0">
              {story.points} pts
            </Badge>
          )}

          {/* PR status */}
          {story.pullRequest && (
            <Tooltip>
              <TooltipTrigger>
                <GitPullRequest
                  className={cn(
                    'w-3.5 h-3.5',
                    story.pullRequest.status === 'merged' && 'text-violet-500',
                    story.pullRequest.status === 'open' && 'text-green-500',
                    story.pullRequest.status === 'closed' && 'text-red-500'
                  )}
                />
              </TooltipTrigger>
              <TooltipContent>PR {story.pullRequest.status}</TooltipContent>
            </Tooltip>
          )}

          {/* Subtasks */}
          {story.subtasks.total > 0 && (
            <div className="flex items-center gap-1 text-xs text-zinc-500">
              <CheckSquare className="w-3.5 h-3.5" />
              {story.subtasks.completed}/{story.subtasks.total}
            </div>
          )}

          {/* Comments */}
          {story.comments > 0 && (
            <div className="flex items-center gap-1 text-xs text-zinc-500">
              <MessageSquare className="w-3 h-3" />
              {story.comments}
            </div>
          )}

          {/* Attachments */}
          {story.attachments > 0 && (
            <div className="flex items-center gap-1 text-xs text-zinc-500">
              <Paperclip className="w-3 h-3" />
              {story.attachments}
            </div>
          )}
        </div>

        {/* Assignee */}
        {story.assignee ? (
          <Tooltip>
            <TooltipTrigger>
              <Avatar className="h-6 w-6">
                <AvatarImage src={story.assignee.avatar} />
                <AvatarFallback className="text-xs">
                  {story.assignee.name.split(' ').map((n) => n[0]).join('')}
                </AvatarFallback>
              </Avatar>
            </TooltipTrigger>
            <TooltipContent>{story.assignee.name}</TooltipContent>
          </Tooltip>
        ) : (
          <div className="h-6 w-6 rounded-full border-2 border-dashed border-zinc-600 flex items-center justify-center">
            <User className="w-3 h-3 text-zinc-500" />
          </div>
        )}
      </div>
    </div>
  );
});

StoryCard.displayName = 'StoryCard';

const SortableStoryCard = ({
  story,
  onClick,
}: {
  story: Story;
  onClick?: () => void;
}) => {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: story.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  return (
    <div ref={setNodeRef} style={style} {...attributes} {...listeners}>
      <StoryCard story={story} isDragging={isDragging} onClick={onClick} />
    </div>
  );
};

const BoardColumnComponent = React.memo(({
  column,
  onStoryClick,
  onAddStory,
}: {
  column: BoardColumn;
  onStoryClick?: (story: Story) => void;
  onAddStory?: () => void;
}) => {
  const StatusIcon = statusIcons[column.id];
  const isOverLimit = column.wipLimit ? column.stories.length > column.wipLimit : false;
  const atLimit = column.wipLimit ? column.stories.length === column.wipLimit : false;

  return (
    <div className="flex flex-col w-[300px] min-w-[300px] bg-zinc-900 rounded-lg">
      {/* Column header */}
      <div className="flex items-center justify-between p-3 border-b border-zinc-800">
        <div className="flex items-center gap-2">
          <StatusIcon
            className={cn(
              'w-4 h-4',
              column.id === 'done' && 'text-green-500',
              column.id === 'blocked' && 'text-red-500',
              column.id === 'in_progress' && 'text-blue-500',
              column.id === 'in_review' && 'text-amber-500'
            )}
          />
          <span className="font-medium text-sm text-zinc-200">{column.title}</span>
          <Badge
            variant={isOverLimit ? 'destructive' : atLimit ? 'secondary' : 'outline'}
            className="text-xs"
          >
            {column.stories.length}
            {column.wipLimit && `/${column.wipLimit}`}
          </Badge>
        </div>

        <div className="flex items-center gap-1">
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="h-7 w-7"
                onClick={onAddStory}
              >
                <Plus className="w-4 h-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Add story</TooltipContent>
          </Tooltip>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" className="h-7 w-7">
                <MoreHorizontal className="w-4 h-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem>Set WIP limit</DropdownMenuItem>
              <DropdownMenuItem>Collapse column</DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem className="text-red-400">
                Archive all done
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {/* WIP limit warning */}
      {isOverLimit && (
        <div className="px-3 py-2 bg-red-500/10 border-b border-red-500/20">
          <p className="text-xs text-red-400 flex items-center gap-1">
            <AlertCircle className="w-3.5 h-3.5" />
            WIP limit exceeded
          </p>
        </div>
      )}

      {/* Stories */}
      <ScrollArea className="flex-1 p-2">
        <SortableContext
          items={column.stories.map((s) => s.id)}
          strategy={verticalListSortingStrategy}
        >
          <div className="space-y-2">
            {column.stories.map((story) => (
              <SortableStoryCard
                key={story.id}
                story={story}
                onClick={() => onStoryClick?.(story)}
              />
            ))}
          </div>
        </SortableContext>

        {column.stories.length === 0 && (
          <div className="flex flex-col items-center justify-center py-8 text-center">
            <p className="text-sm text-zinc-500">No stories</p>
            <Button
              variant="ghost"
              size="sm"
              className="mt-2"
              onClick={onAddStory}
            >
              <Plus className="w-4 h-4 mr-1" />
              Add story
            </Button>
          </div>
        )}
      </ScrollArea>
    </div>
  );
});

BoardColumnComponent.displayName = 'BoardColumnComponent';

const SprintHeader = React.memo(({
  sprint,
  onRefresh,
  isLoading,
}: {
  sprint: Sprint;
  onRefresh?: () => void;
  isLoading?: boolean;
}) => {
  const daysRemaining = useMemo(() => {
    const end = new Date(sprint.endDate);
    const now = new Date();
    const diff = Math.ceil((end.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
    return Math.max(0, diff);
  }, [sprint.endDate]);

  const progress = useMemo(() => {
    if (sprint.commitment === 0) return 0;
    return Math.round((sprint.completed / sprint.commitment) * 100);
  }, [sprint.completed, sprint.commitment]);

  return (
    <div className="flex items-center justify-between p-4 bg-zinc-900 rounded-lg mb-4">
      <div className="flex items-center gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-semibold text-zinc-100">{sprint.name}</h2>
            <Badge
              variant={
                sprint.status === 'active'
                  ? 'default'
                  : sprint.status === 'completed'
                  ? 'secondary'
                  : 'outline'
              }
            >
              {sprint.status}
            </Badge>
          </div>
          {sprint.goal && (
            <p className="text-sm text-zinc-400 mt-1">{sprint.goal}</p>
          )}
        </div>
      </div>

      <div className="flex items-center gap-6">
        {/* Days remaining */}
        <div className="text-center">
          <div className="text-2xl font-bold text-zinc-100">{daysRemaining}</div>
          <div className="text-xs text-zinc-500">days left</div>
        </div>

        {/* Progress */}
        <div className="w-48">
          <div className="flex items-center justify-between mb-1">
            <span className="text-xs text-zinc-500">Progress</span>
            <span className="text-xs text-zinc-400">
              {sprint.completed} / {sprint.commitment} pts
            </span>
          </div>
          <Progress value={progress} className="h-2" />
        </div>

        {/* Velocity */}
        {sprint.velocity !== undefined && (
          <div className="text-center">
            <div className="text-2xl font-bold text-zinc-100">
              {sprint.velocity}
            </div>
            <div className="text-xs text-zinc-500">velocity</div>
          </div>
        )}

        {/* Actions */}
        <div className="flex items-center gap-2">
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="outline"
                size="icon"
                onClick={onRefresh}
                disabled={isLoading}
              >
                <RefreshCw
                  className={cn('w-4 h-4', isLoading && 'animate-spin')}
                />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Refresh</TooltipContent>
          </Tooltip>

          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="outline" size="icon">
                <Filter className="w-4 h-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Filter</TooltipContent>
          </Tooltip>

          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="outline" size="icon">
                <Maximize2 className="w-4 h-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Fullscreen</TooltipContent>
          </Tooltip>
        </div>
      </div>
    </div>
  );
});

SprintHeader.displayName = 'SprintHeader';

// =============================================================================
// Main Component
// =============================================================================

export const SprintBoard: React.FC<SprintBoardProps> = ({
  sprintId,
  sprint: sprintProp,
  columns: columnsProp,
  onStoryMove,
  onStoryClick,
  onStoryCreate,
  onRefresh,
  isLoading = false,
  className,
}) => {
  const activeSprint = useAtomValue(activeSprintAtom);
  const boardState = useAtomValue(sprintBoardAtom);
  const setSelectedStory = useSetAtom(selectedStoryAtom);

  const sprint = sprintProp || activeSprint;
  const [columns, setColumns] = useState<BoardColumn[]>(
    columnsProp || boardState.columns || []
  );
  const [activeId, setActiveId] = useState<UniqueIdentifier | null>(null);
  const [activeStory, setActiveStory] = useState<Story | null>(null);

  // Update columns when props change
  useEffect(() => {
    if (columnsProp) {
      setColumns(columnsProp);
    } else if (boardState.columns) {
      setColumns(boardState.columns);
    }
  }, [columnsProp, boardState.columns]);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 8 },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  // Find story by ID across all columns
  const findStory = useCallback(
    (id: UniqueIdentifier): Story | null => {
      for (const column of columns) {
        const story = column.stories.find((s) => s.id === id);
        if (story) return story;
      }
      return null;
    },
    [columns]
  );

  // Find column containing a story
  const findColumn = useCallback(
    (id: UniqueIdentifier): BoardColumn | null => {
      for (const column of columns) {
        if (column.stories.some((s) => s.id === id)) {
          return column;
        }
      }
      return null;
    },
    [columns]
  );

  const handleDragStart = useCallback(
    (event: DragStartEvent) => {
      const { active } = event;
      setActiveId(active.id);
      const story = findStory(active.id);
      setActiveStory(story);
    },
    [findStory]
  );

  const handleDragOver = useCallback(
    (event: DragOverEvent) => {
      const { active, over } = event;
      if (!over) return;

      const activeColumn = findColumn(active.id);
      const overColumn = columns.find((c) => c.id === over.id) || findColumn(over.id);

      if (!activeColumn || !overColumn || activeColumn.id === overColumn.id) {
        return;
      }

      setColumns((cols) => {
        const activeColumnIndex = cols.findIndex((c) => c.id === activeColumn.id);
        const overColumnIndex = cols.findIndex((c) => c.id === overColumn.id);

        const activeStoryIndex = cols[activeColumnIndex].stories.findIndex(
          (s) => s.id === active.id
        );

        const newColumns = [...cols];
        const [movedStory] = newColumns[activeColumnIndex].stories.splice(
          activeStoryIndex,
          1
        );

        movedStory.status = overColumn.id;
        newColumns[overColumnIndex].stories.push(movedStory);

        return newColumns;
      });
    },
    [columns, findColumn]
  );

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const { active, over } = event;
      setActiveId(null);
      setActiveStory(null);

      if (!over) return;

      const activeColumn = findColumn(active.id);
      if (!activeColumn) return;

      const activeIndex = activeColumn.stories.findIndex((s) => s.id === active.id);
      const overIndex = activeColumn.stories.findIndex((s) => s.id === over.id);

      if (activeIndex !== overIndex) {
        setColumns((cols) => {
          const columnIndex = cols.findIndex((c) => c.id === activeColumn.id);
          const newColumns = [...cols];
          newColumns[columnIndex] = {
            ...newColumns[columnIndex],
            stories: arrayMove(
              newColumns[columnIndex].stories,
              activeIndex,
              overIndex
            ),
          };
          return newColumns;
        });
      }

      // Notify parent
      const story = findStory(active.id);
      if (story && onStoryMove) {
        const targetColumn = findColumn(active.id);
        if (targetColumn) {
          const newIndex = targetColumn.stories.findIndex((s) => s.id === active.id);
          onStoryMove(story.id, targetColumn.id, newIndex);
        }
      }
    },
    [findColumn, findStory, onStoryMove]
  );

  const handleStoryClick = useCallback(
    (story: Story) => {
      setSelectedStory(story);
      onStoryClick?.(story);
    },
    [setSelectedStory, onStoryClick]
  );

  if (!sprint) {
    return (
      <div className={cn('flex items-center justify-center h-full', className)}>
        <div className="text-center">
          <h3 className="text-lg font-medium text-zinc-400">No active sprint</h3>
          <p className="text-sm text-zinc-500 mt-1">
            Start a sprint to see the board
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className={cn('flex flex-col h-full', className)}>
      <SprintHeader
        sprint={sprint}
        onRefresh={onRefresh}
        isLoading={isLoading}
      />

      <DndContext
        sensors={sensors}
        collisionDetection={closestCorners}
        onDragStart={handleDragStart}
        onDragOver={handleDragOver}
        onDragEnd={handleDragEnd}
      >
        <div className="flex-1 overflow-x-auto">
          <div className="flex gap-4 h-full min-w-max p-4">
            {columns.map((column) => (
              <BoardColumnComponent
                key={column.id}
                column={column}
                onStoryClick={handleStoryClick}
                onAddStory={() => onStoryCreate?.(column.id)}
              />
            ))}
          </div>
        </div>

        <DragOverlay>
          {activeStory ? (
            <div className="rotate-3 scale-105">
              <StoryCard story={activeStory} isDragging />
            </div>
          ) : null}
        </DragOverlay>
      </DndContext>
    </div>
  );
};

export default SprintBoard;
