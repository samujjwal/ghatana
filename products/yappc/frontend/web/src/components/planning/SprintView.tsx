/**
 * Sprint View Component
 *
 * Displays items in a specific sprint with capacity metrics and progress.
 * Used for active sprint management and daily standups.
 *
 * @doc.type component
 * @doc.purpose Sprint iteration view with capacity tracking
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { ReactNode, useMemo, useCallback } from 'react';
import {
  Zap as SprintIcon,
  Target as GoalIcon,
  Users as CapacityIcon,
  CheckCircle2 as DoneIcon,
  Clock as InProgressIcon,
  AlertCircle as BlockedIcon,
  CalendarDays as CalendarIcon,
  BarChart3 as MetricsIcon,
} from 'lucide-react';
import {
  Typography,
  Button,
  Box,
  Card,
  CardContent,
  Chip,
  Progress,
} from '@ghatana/design-system';
import { useQuery, useMutation, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { parseJsonResponse, readErrorResponse } from '@/lib/http';
import type { BacklogItem, ItemStatus } from './BacklogBoard';

// ============================================================================
// Types
// ============================================================================

export interface Sprint {
  id: string;
  projectId: string;
  name: string;
  goal: string | null;
  status: 'PLANNING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
  startDate: string | null;
  endDate: string | null;
  capacity: number | null;
  itemCount?: number;
  createdAt: string;
  updatedAt: string;
  items?: BacklogItem[];
}

export interface SprintViewProps {
  projectId: string;
  sprintId: string;
  className?: string;
}

interface SprintResponse {
  sprint: Sprint & { items: BacklogItem[] };
}

interface UpdateSprintResponse {
  sprint: Sprint;
}

// ============================================================================
// Helpers
// ============================================================================

function formatDate(dateString: string | null): string {
  if (!dateString) return '—';
  return new Date(dateString).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

function getStatusColor(status: ItemStatus): string {
  const map: Record<ItemStatus, string> = {
    NOT_STARTED: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    IN_PROGRESS: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
    BLOCKED: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
    IN_REVIEW: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
    COMPLETED: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
    ARCHIVED: 'bg-gray-200 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
  };
  return map[status] ?? 'bg-gray-100 text-gray-700';
}

function getSprintStatusBadgeColor(status: Sprint['status']): string {
  const map: Record<Sprint['status'], string> = {
    PLANNING: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
    ACTIVE: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
    COMPLETED: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
    CANCELLED: 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400',
  };
  return map[status] ?? 'bg-gray-100 text-gray-700';
}

// ============================================================================
// Sprint Metrics
// ============================================================================

interface SprintMetrics {
  total: number;
  notStarted: number;
  inProgress: number;
  blocked: number;
  completed: number;
  completedPoints: number;
  totalPoints: number;
  progressPercent: number;
}

function computeMetrics(items: BacklogItem[]): SprintMetrics {
  const total = items.length;
  const notStarted = items.filter((i) => i.status === 'NOT_STARTED').length;
  const inProgress = items.filter((i) => i.status === 'IN_PROGRESS').length;
  const blocked = items.filter((i) => i.status === 'BLOCKED').length;
  const completed = items.filter((i) => i.status === 'COMPLETED').length;

  const totalPoints = items.reduce((sum, i) => sum + (i.storyPoints ?? 0), 0);
  const completedPoints = items
    .filter((i) => i.status === 'COMPLETED')
    .reduce((sum, i) => sum + (i.storyPoints ?? 0), 0);

  const progressPercent = total > 0 ? Math.round((completed / total) * 100) : 0;

  return { total, notStarted, inProgress, blocked, completed, completedPoints, totalPoints, progressPercent };
}

// ============================================================================
// Sprint View Component
// ============================================================================

async function fetchSprint(projectId: string, sprintId: string): Promise<Sprint & { items: BacklogItem[] }> {
  const response = await fetch(`/api/v1/planning/${projectId}/sprints/${sprintId}`);
  if (!response.ok) {
    const message = await readErrorResponse(response, 'Failed to load sprint');
    throw new Error(message);
  }
  const data = await parseJsonResponse<SprintResponse>(response, 'SprintView');
  return data.sprint;
}

/**
 * SprintView — Detailed view of a sprint with items, metrics, and capacity tracking.
 */
export function SprintView({ projectId, sprintId, className = '' }: SprintViewProps): ReactNode {
  const queryClient: QueryClient = useQueryClient();

  const {
    data: sprint,
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ['planning', 'sprints', projectId, sprintId],
    queryFn: () => fetchSprint(projectId, sprintId),
    staleTime: 30_000,
  });

  const startSprintMutation = useMutation({
    mutationFn: async () => {
      const response = await fetch(`/api/v1/planning/${projectId}/sprints/${sprintId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: 'ACTIVE' }),
      });
      if (!response.ok) {
        const message = await readErrorResponse(response, 'Failed to start sprint');
        throw new Error(message);
      }
      return parseJsonResponse<UpdateSprintResponse>(response, 'SprintView.start');
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['planning', 'sprints', projectId] });
    },
  });

  const completeSprintMutation = useMutation({
    mutationFn: async () => {
      const response = await fetch(`/api/v1/planning/${projectId}/sprints/${sprintId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: 'COMPLETED' }),
      });
      if (!response.ok) {
        const message = await readErrorResponse(response, 'Failed to complete sprint');
        throw new Error(message);
      }
      return parseJsonResponse<UpdateSprintResponse>(response, 'SprintView.complete');
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['planning', 'sprints', projectId] });
    },
  });

  const handleStartSprint = useCallback(() => {
    startSprintMutation.mutate();
  }, [startSprintMutation]);

  const handleCompleteSprint = useCallback(() => {
    completeSprintMutation.mutate();
  }, [completeSprintMutation]);

  const metrics = useMemo<SprintMetrics>(
    () => computeMetrics(sprint?.items ?? []),
    [sprint?.items]
  );

  // ---- Loading state ----
  if (isLoading) {
    return (
      <Box className={`p-6 ${className}`}>
        <div
          className="flex items-center justify-center gap-2 text-gray-500"
          role="status"
          aria-label="Loading sprint"
        >
          <SprintIcon className="h-5 w-5 animate-pulse" aria-hidden="true" />
          <Typography variant="body2">Loading sprint…</Typography>
        </div>
      </Box>
    );
  }

  // ---- Error state ----
  if (isError || !sprint) {
    return (
      <Box className={`p-6 ${className}`}>
        <div className="flex items-center gap-2 text-red-600 dark:text-red-400" role="alert">
          <BlockedIcon className="h-5 w-5" aria-hidden="true" />
          <Typography variant="body2">
            {error instanceof Error ? error.message : 'Failed to load sprint'}
          </Typography>
        </div>
      </Box>
    );
  }

  return (
    <Box className={`flex flex-col gap-4 ${className}`}>
      {/* Sprint Header */}
      <div className="flex items-start justify-between flex-wrap gap-2">
        <div className="flex items-center gap-2">
          <SprintIcon className="h-5 w-5 text-blue-500" aria-hidden="true" />
          <Typography variant="h6" className="font-semibold">
            {sprint.name}
          </Typography>
          <span
            className={`text-xs font-medium px-2 py-0.5 rounded-full ${getSprintStatusBadgeColor(sprint.status)}`}
            aria-label={`Sprint status: ${sprint.status}`}
          >
            {sprint.status}
          </span>
        </div>

        {/* Sprint actions */}
        <div className="flex items-center gap-2">
          {sprint.status === 'PLANNING' && (
            <Button
              size="sm"
              variant="default"
              onClick={handleStartSprint}
              disabled={startSprintMutation.isPending}
              aria-label="Start sprint"
            >
              <SprintIcon className="h-4 w-4 mr-1" aria-hidden="true" />
              Start Sprint
            </Button>
          )}
          {sprint.status === 'ACTIVE' && (
            <Button
              size="sm"
              variant="outline"
              onClick={handleCompleteSprint}
              disabled={completeSprintMutation.isPending}
              aria-label="Complete sprint"
            >
              <DoneIcon className="h-4 w-4 mr-1" aria-hidden="true" />
              Complete Sprint
            </Button>
          )}
        </div>
      </div>

      {/* Sprint goal */}
      {sprint.goal && (
        <Card className="bg-blue-50 dark:bg-blue-900/10 border-blue-200 dark:border-blue-800">
          <CardContent className="p-3">
            <div className="flex items-center gap-2">
              <GoalIcon className="h-4 w-4 text-blue-500 flex-shrink-0" aria-hidden="true" />
              <Typography variant="body2" className="text-blue-700 dark:text-blue-300">
                <strong>Sprint Goal:</strong> {sprint.goal}
              </Typography>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Dates & Capacity row */}
      <div className="flex items-center gap-4 flex-wrap text-sm text-gray-500">
        <div className="flex items-center gap-1">
          <CalendarIcon className="h-4 w-4" aria-hidden="true" />
          <span>
            {formatDate(sprint.startDate)} – {formatDate(sprint.endDate)}
          </span>
        </div>
        {sprint.capacity != null && (
          <div className="flex items-center gap-1">
            <CapacityIcon className="h-4 w-4" aria-hidden="true" />
            <span>{sprint.capacity}pt capacity</span>
          </div>
        )}
      </div>

      {/* Metrics row */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <Card>
          <CardContent className="p-3 text-center">
            <Typography variant="h4" className="font-bold text-xl">
              {metrics.total}
            </Typography>
            <Typography variant="caption" className="text-gray-500">
              Total Items
            </Typography>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-3 text-center">
            <Typography
              variant="h4"
              className="font-bold text-xl text-blue-600 dark:text-blue-400"
            >
              {metrics.inProgress}
            </Typography>
            <div className="flex items-center justify-center gap-1">
              <InProgressIcon className="h-3 w-3 text-blue-500" aria-hidden="true" />
              <Typography variant="caption" className="text-gray-500">
                In Progress
              </Typography>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-3 text-center">
            <Typography
              variant="h4"
              className="font-bold text-xl text-red-600 dark:text-red-400"
            >
              {metrics.blocked}
            </Typography>
            <div className="flex items-center justify-center gap-1">
              <BlockedIcon className="h-3 w-3 text-red-500" aria-hidden="true" />
              <Typography variant="caption" className="text-gray-500">
                Blocked
              </Typography>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-3 text-center">
            <Typography
              variant="h4"
              className="font-bold text-xl text-green-600 dark:text-green-400"
            >
              {metrics.completed}
            </Typography>
            <div className="flex items-center justify-center gap-1">
              <DoneIcon className="h-3 w-3 text-green-500" aria-hidden="true" />
              <Typography variant="caption" className="text-gray-500">
                Done
              </Typography>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Progress bar */}
      {metrics.total > 0 && (
        <Card>
          <CardContent className="p-4">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-1">
                <MetricsIcon className="h-4 w-4 text-gray-400" aria-hidden="true" />
                <Typography variant="body2" className="text-gray-600 dark:text-gray-300 font-medium">
                  Sprint Progress
                </Typography>
              </div>
              <Typography variant="body2" className="text-gray-500">
                {metrics.completedPoints}/{metrics.totalPoints > 0 ? metrics.totalPoints : '?'}pt
                &nbsp;({metrics.progressPercent}%)
              </Typography>
            </div>
            <Progress
              value={metrics.progressPercent}
              aria-label={`Sprint progress: ${metrics.progressPercent}%`}
            />
          </CardContent>
        </Card>
      )}

      {/* Items table */}
      <Card>
        <CardContent className="p-0">
          {sprint.items.length === 0 ? (
            <div className="p-8 text-center">
              <SprintIcon className="h-10 w-10 text-gray-300 mx-auto mb-3" aria-hidden="true" />
              <Typography variant="body1" className="text-gray-500">
                No items in this sprint
              </Typography>
              <Typography variant="body2" className="text-gray-400 mt-1">
                Move items from the backlog to add them here
              </Typography>
            </div>
          ) : (
            <div className="divide-y dark:divide-gray-700">
              {sprint.items.map((item) => (
                <div
                  key={item.id}
                  className="flex items-center gap-3 px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-800/40"
                >
                  <span
                    className={`text-xs font-medium px-2 py-0.5 rounded ${getStatusColor(item.status)}`}
                  >
                    {item.status.replace('_', ' ')}
                  </span>
                  <Typography variant="body2" className="flex-1 font-medium text-sm min-w-0 truncate">
                    {item.title}
                  </Typography>
                  <div className="flex items-center gap-2 flex-shrink-0">
                    {item.storyPoints != null && (
                      <Chip
                        label={`${item.storyPoints}pt`}
                        size="small"
                        aria-label={`${item.storyPoints} story points`}
                      />
                    )}
                    <Chip
                      label={item.type}
                      size="small"
                      aria-label={`Item type: ${item.type}`}
                    />
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </Box>
  );
}
