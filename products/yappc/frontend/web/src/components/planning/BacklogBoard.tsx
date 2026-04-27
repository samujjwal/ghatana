/**
 * Backlog Board Component
 *
 * Kanban-style backlog view for sprint planning. Displays work items organized
 * by status with drag-drop between backlog and sprint.
 *
 * @doc.type component
 * @doc.purpose Sprint backlog kanban board
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { ReactNode, useState, useCallback } from 'react';
import {
  ListTodo as BacklogIcon,
  ArrowRight as MoveIcon,
  AlertCircle as BlockedIcon,
  CheckCircle2 as DoneIcon,
  Clock as InProgressIcon,
  Tag as TagIcon,
  Flame as CriticalIcon,
  ChevronDown as ExpandIcon,
} from 'lucide-react';
import {
  Typography,
  Button,
  Box,
  Card,
  CardContent,
  Chip,
} from '@ghatana/design-system';
import {
  useQuery,
  useMutation,
  useQueryClient,
  type QueryClient,
} from '@tanstack/react-query';
import { parseJsonResponse, readErrorResponse } from '@/lib/http';

// ============================================================================
// Types
// ============================================================================

export type ItemType =
  | 'FEATURE'
  | 'STORY'
  | 'TASK'
  | 'BUG'
  | 'EPIC'
  | 'SPIKE'
  | 'SECURITY_ISSUE'
  | 'TECH_DEBT';

export type ItemStatus =
  | 'NOT_STARTED'
  | 'IN_PROGRESS'
  | 'BLOCKED'
  | 'IN_REVIEW'
  | 'COMPLETED'
  | 'ARCHIVED';

export type ItemPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface BacklogItem {
  id: string;
  title: string;
  description?: string | null;
  type: ItemType;
  status: ItemStatus;
  priority: ItemPriority;
  storyPoints?: number | null;
  phaseId: string;
  sprintId: string | null;
  dueDate?: string | null;
  owners?: Array<{ user: { id: string; name: string; email: string } }>;
}

export interface BacklogBoardProps {
  projectId: string;
  activeSprints?: Array<{ id: string; name: string }>;
  onMoveToSprint?: (itemId: string, sprintId: string) => void;
  className?: string;
}

interface BacklogResponse {
  items: BacklogItem[];
}

interface MoveItemResponse {
  item: BacklogItem;
}

// ============================================================================
// Column config
// ============================================================================

const STATUS_COLUMNS: Array<{ status: ItemStatus; label: string; icon: ReactNode }> = [
  {
    status: 'NOT_STARTED',
    label: 'Not Started',
    icon: <BacklogIcon className="h-4 w-4" aria-hidden="true" />,
  },
  {
    status: 'IN_PROGRESS',
    label: 'In Progress',
    icon: <InProgressIcon className="h-4 w-4" aria-hidden="true" />,
  },
  {
    status: 'BLOCKED',
    label: 'Blocked',
    icon: <BlockedIcon className="h-4 w-4 text-red-500" aria-hidden="true" />,
  },
  {
    status: 'IN_REVIEW',
    label: 'In Review',
    icon: <MoveIcon className="h-4 w-4" aria-hidden="true" />,
  },
  {
    status: 'COMPLETED',
    label: 'Completed',
    icon: <DoneIcon className="h-4 w-4 text-green-500" aria-hidden="true" />,
  },
];

// ============================================================================
// Helpers
// ============================================================================

function getTypeLabel(type: ItemType): string {
  const labels: Record<ItemType, string> = {
    FEATURE: 'Feature',
    STORY: 'Story',
    TASK: 'Task',
    BUG: 'Bug',
    EPIC: 'Epic',
    SPIKE: 'Spike',
    SECURITY_ISSUE: 'Security',
    TECH_DEBT: 'Tech Debt',
  };
  return labels[type] ?? type;
}

function getTypeColor(type: ItemType): string {
  const colors: Record<ItemType, string> = {
    FEATURE: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-200',
    STORY: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-200',
    TASK: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200',
    BUG: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-200',
    EPIC: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-200',
    SPIKE: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-200',
    SECURITY_ISSUE: 'bg-rose-100 text-rose-800 dark:bg-rose-900/30 dark:text-rose-200',
    TECH_DEBT: 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-200',
  };
  return colors[type] ?? 'bg-gray-100 text-gray-800';
}

function getPriorityColor(priority: ItemPriority): string {
  const colors: Record<ItemPriority, string> = {
    LOW: 'text-gray-500 dark:text-gray-400',
    MEDIUM: 'text-blue-500 dark:text-blue-400',
    HIGH: 'text-orange-500 dark:text-orange-400',
    CRITICAL: 'text-red-600 dark:text-red-400',
  };
  return colors[priority] ?? 'text-gray-500';
}

// ============================================================================
// Item Card
// ============================================================================

interface ItemCardProps {
  item: BacklogItem;
  activeSprints: Array<{ id: string; name: string }>;
  onMoveToSprint: (itemId: string, sprintId: string | null) => void;
  isMutating: boolean;
}

function ItemCard({
  item,
  activeSprints,
  onMoveToSprint,
  isMutating,
}: ItemCardProps): ReactNode {
  const [expanded, setExpanded] = useState(false);

  const handleToggle = useCallback(() => {
    setExpanded((prev) => !prev);
  }, []);

  return (
    <Card className="mb-2 hover:shadow-md transition-shadow">
      <CardContent className="p-3">
        <div className="flex items-start gap-2">
          {/* Priority indicator */}
          {item.priority === 'CRITICAL' && (
            <CriticalIcon
              className={`h-4 w-4 flex-shrink-0 mt-0.5 ${getPriorityColor(item.priority)}`}
              aria-label="Critical priority"
            />
          )}
          {item.priority === 'HIGH' && (
            <TagIcon
              className={`h-4 w-4 flex-shrink-0 mt-0.5 ${getPriorityColor(item.priority)}`}
              aria-label="High priority"
            />
          )}

          {/* Content */}
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-1 mb-1 flex-wrap">
              <span className={`text-xs font-medium px-1.5 py-0.5 rounded ${getTypeColor(item.type)}`}>
                {getTypeLabel(item.type)}
              </span>
              {item.storyPoints != null && (
                <span className="text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 px-1.5 py-0.5 rounded font-mono">
                  {item.storyPoints}pt
                </span>
              )}
            </div>

            <Typography variant="body2" className="font-medium text-sm line-clamp-2">
              {item.title}
            </Typography>

            {expanded && item.description && (
              <Typography variant="caption" className="text-gray-500 mt-1 text-xs">
                {item.description}
              </Typography>
            )}
          </div>

          {/* Expand toggle */}
          {item.description && (
            <button
              onClick={handleToggle}
              aria-label={expanded ? 'Collapse' : 'Expand'}
              className="flex-shrink-0 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
            >
              <ExpandIcon className={`h-4 w-4 transition-transform ${expanded ? 'rotate-180' : ''}`} />
            </button>
          )}
        </div>

        {/* Sprint actions */}
        {activeSprints.length > 0 && item.sprintId == null && (
          <div className="mt-2 flex items-center gap-1 flex-wrap">
            {activeSprints.map((sprint) => (
              <Button
                key={sprint.id}
                size="sm"
                variant="outline"
                onClick={() => onMoveToSprint(item.id, sprint.id)}
                disabled={isMutating}
                className="text-xs h-6 px-2"
              >
                <MoveIcon className="h-3 w-3 mr-1" aria-hidden="true" />
                {sprint.name}
              </Button>
            ))}
          </div>
        )}

        {item.sprintId != null && (
          <Button
            size="sm"
            variant="ghost"
            onClick={() => onMoveToSprint(item.id, null)}
            disabled={isMutating}
            className="mt-2 text-xs h-6 px-2 text-gray-500"
          >
            ← Move to Backlog
          </Button>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================================================
// Backlog Board Component
// ============================================================================

async function fetchBacklogItems(projectId: string): Promise<BacklogItem[]> {
  const response = await fetch(`/api/v1/planning/${projectId}/backlog`);
  if (!response.ok) {
    const message = await readErrorResponse(response, 'Failed to load backlog');
    throw new Error(message);
  }
  const data = await parseJsonResponse<BacklogResponse>(response, 'BacklogBoard');
  return data.items;
}

/**
 * BacklogBoard — Kanban-style view of all project backlog items.
 */
export function BacklogBoard({
  projectId,
  activeSprints = [],
  onMoveToSprint,
  className = '',
}: BacklogBoardProps): ReactNode {
  const queryClient: QueryClient = useQueryClient();
  const [filterType, setFilterType] = useState<ItemType | 'ALL'>('ALL');

  const {
    data: items = [],
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ['planning', 'backlog', projectId],
    queryFn: () => fetchBacklogItems(projectId),
    staleTime: 30_000,
  });

  const moveMutation = useMutation({
    mutationFn: async ({
      itemId,
      sprintId,
    }: {
      itemId: string;
      sprintId: string | null;
    }) => {
      const response = await fetch(`/api/v1/planning/${projectId}/items/move`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ itemId, sprintId }),
      });
      if (!response.ok) {
        const message = await readErrorResponse(response, 'Failed to move item');
        throw new Error(message);
      }
      return parseJsonResponse<MoveItemResponse>(response, 'BacklogBoard.move');
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['planning', 'backlog', projectId] });
      void queryClient.invalidateQueries({ queryKey: ['planning', 'sprints', projectId] });
    },
  });

  const handleMoveToSprint = useCallback(
    (itemId: string, sprintId: string | null) => {
      moveMutation.mutate({ itemId, sprintId });
      onMoveToSprint?.(itemId, sprintId ?? '');
    },
    [moveMutation, onMoveToSprint]
  );

  const visibleItems = filterType === 'ALL' ? items : items.filter((i) => i.type === filterType);

  // Group by status
  const itemsByStatus = STATUS_COLUMNS.reduce<Record<ItemStatus, BacklogItem[]>>(
    (acc, col) => {
      acc[col.status] = visibleItems.filter((item) => item.status === col.status);
      return acc;
    },
    {} as Record<ItemStatus, BacklogItem[]>
  );

  const typeOptions: Array<{ value: ItemType | 'ALL'; label: string }> = [
    { value: 'ALL', label: 'All Types' },
    { value: 'STORY', label: 'Stories' },
    { value: 'TASK', label: 'Tasks' },
    { value: 'BUG', label: 'Bugs' },
    { value: 'FEATURE', label: 'Features' },
    { value: 'EPIC', label: 'Epics' },
    { value: 'TECH_DEBT', label: 'Tech Debt' },
    { value: 'SECURITY_ISSUE', label: 'Security' },
  ];

  if (isLoading) {
    return (
      <Box className={`p-6 ${className}`}>
        <div
          className="flex items-center justify-center gap-2 text-gray-500"
          role="status"
          aria-label="Loading backlog"
        >
          <BacklogIcon className="h-5 w-5 animate-pulse" aria-hidden="true" />
          <Typography variant="body2">Loading backlog…</Typography>
        </div>
      </Box>
    );
  }

  if (isError) {
    return (
      <Box className={`p-6 ${className}`}>
        <div
          className="flex items-center gap-2 text-red-600 dark:text-red-400"
          role="alert"
        >
          <BlockedIcon className="h-5 w-5" aria-hidden="true" />
          <Typography variant="body2">
            {error instanceof Error ? error.message : 'Failed to load backlog items'}
          </Typography>
        </div>
      </Box>
    );
  }

  return (
    <Box className={`flex flex-col gap-4 ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-2">
        <div className="flex items-center gap-2">
          <BacklogIcon className="h-5 w-5 text-gray-500" aria-hidden="true" />
          <Typography variant="h6" className="font-semibold">
            Backlog Board
          </Typography>
          <Chip label={`${visibleItems.length} items`} size="small" />
        </div>

        {/* Type filter */}
        <div className="flex items-center gap-1 flex-wrap">
          {typeOptions.map(({ value, label }) => (
            <button
              key={value}
              onClick={() => setFilterType(value)}
              aria-pressed={filterType === value}
              className={`text-xs px-2 py-1 rounded-full border transition-colors ${
                filterType === value
                  ? 'bg-blue-600 text-white border-blue-600'
                  : 'bg-transparent text-gray-600 dark:text-gray-400 border-gray-300 dark:border-gray-600 hover:border-blue-400'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {/* Kanban columns */}
      {visibleItems.length === 0 ? (
        <Card>
          <CardContent className="p-8 text-center">
            <BacklogIcon className="h-10 w-10 text-gray-300 mx-auto mb-3" aria-hidden="true" />
            <Typography variant="body1" className="text-gray-500">
              No backlog items found
            </Typography>
            <Typography variant="body2" className="text-gray-400 mt-1">
              Items will appear here once they are added to a phase
            </Typography>
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
          {STATUS_COLUMNS.map((col) => {
            const colItems = itemsByStatus[col.status] ?? [];
            return (
              <div key={col.status} className="flex flex-col gap-2">
                {/* Column header */}
                <div className="flex items-center gap-2 px-1">
                  {col.icon}
                  <Typography variant="caption" className="font-semibold text-gray-700 dark:text-gray-300">
                    {col.label}
                  </Typography>
                  <span className="ml-auto text-xs text-gray-400 bg-gray-100 dark:bg-gray-700 rounded-full px-1.5">
                    {colItems.length}
                  </span>
                </div>

                {/* Items */}
                <div className="flex flex-col min-h-16">
                  {colItems.length === 0 ? (
                    <div className="border-2 border-dashed border-gray-200 dark:border-gray-700 rounded-lg p-3 text-center">
                      <Typography variant="caption" className="text-gray-400 text-xs">
                        Empty
                      </Typography>
                    </div>
                  ) : (
                    colItems.map((item) => (
                      <ItemCard
                        key={item.id}
                        item={item}
                        activeSprints={activeSprints}
                        onMoveToSprint={handleMoveToSprint}
                        isMutating={moveMutation.isPending}
                      />
                    ))
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </Box>
  );
}
