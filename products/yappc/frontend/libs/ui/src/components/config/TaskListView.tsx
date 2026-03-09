/**
 * TaskListView Component
 *
 * Displays tasks for the currently selected domain with filtering and details.
 *
 * @module ui/components/config
 * @doc.type component
 * @doc.purpose Task list display with domain filtering
 * @doc.layer ui
 */

import React, { Suspense } from 'react';

import { Box, Card, CardContent, Typography, Stack, Chip, Skeleton } from '@ghatana/ui';
import { useAtom } from 'jotai';

import { useDomainById } from '../../hooks/useConfig';
import { selectedDomainIdAtom } from '../../state/configAtoms';

// ============================================================================
// Types
// ============================================================================

interface TaskListViewProps {
  /** Additional CSS class name */
  className?: string;
}

interface TaskListContentProps {
  /** Domain ID to fetch tasks for */
  domainId: string;
}

interface TaskCardProps {
  /** Task data */
  task: {
    id: string;
    name: string;
    description?: string;
    category?: string;
    estimatedDuration?: string;
    tags?: string[];
    tools?: Array<{ name: string; purpose?: string }>;
    dependencies?: string[];
  };
}

// ============================================================================
// Skeleton Component
// ============================================================================

const TaskListSkeleton: React.FC = () => (
  <Stack spacing={2}>
    <Stack direction="row" spacing={2} alignItems="center">
      <Skeleton variant="rectangular" width={32} height={32} />
      <Box className="flex-1">
        <Skeleton variant="ghost" width="33%" height={24} />
        <Skeleton variant="ghost" width="66%" height={20} />
      </Box>
    </Stack>

    {[1, 2, 3].map((i) => (
      <Card key={i} variant="outlined">
        <CardContent>
          <Stack direction="row" justifyContent="space-between" alignItems="flex-start" mb={1}>
            <Box className="flex-1">
              <Skeleton variant="ghost" width="50%" height={24} />
              <Skeleton variant="ghost" width="75%" height={20} />
            </Box>
            <Stack direction="row" spacing={1}>
              <Skeleton variant="rectangular" width={48} height={24} className="rounded" />
              <Skeleton variant="rectangular" width={64} height={24} className="rounded" />
            </Stack>
          </Stack>
          <Stack direction="row" spacing={0.5} mt={2}>
            {[1, 2, 3].map((j) => (
              <Skeleton key={j} variant="rectangular" width={48} height={24} className="rounded" />
            ))}
          </Stack>
        </CardContent>
      </Card>
    ))}
  </Stack>
);

// ============================================================================
// Task Card Component (exported for reuse)
// ============================================================================

export const TaskCard: React.FC<TaskCardProps> = ({ task }) => (
  <Card variant="outlined" className="hover:border-gray-200 dark:hover:border-gray-700">
    <CardContent>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" mb={1}>
        <Box className="flex-1">
          <Typography as="p" className="text-lg font-medium" fontWeight={500}>
            {task.name}
          </Typography>
          {task.description && (
            <Typography as="p" className="text-sm" color="text.secondary" className="mt-1">
              {task.description}
            </Typography>
          )}
        </Box>
        <Stack direction="row" spacing={1} className="ml-4">
          {task.category && (
            <Chip label={task.category} size="sm" variant="outlined" />
          )}
          {task.estimatedDuration && (
            <Chip label={task.estimatedDuration} size="sm" tone="primary" variant="outlined" />
          )}
        </Stack>
      </Stack>

      {/* Tags */}
      {task.tags && task.tags.length > 0 && (
        <Stack direction="row" spacing={0.5} flexWrap="wrap" gap={0.5} mb={1.5}>
          {task.tags.map((tag) => (
            <Chip
              key={tag}
              label={tag}
              size="sm"
              className="bg-indigo-100 dark:bg-indigo-900/30 text-white"
            />
          ))}
        </Stack>
      )}

      {/* Tools */}
      {task.tools && task.tools.length > 0 && (
        <Box className="mt-3">
          <Typography as="span" className="text-xs text-gray-500" fontWeight={500} color="text.secondary" gutterBottom>
            Tools
          </Typography>
          <Stack direction="row" spacing={0.5} flexWrap="wrap" gap={0.5}>
            {task.tools.map((tool) => (
              <Chip
                key={tool.name}
                label={tool.name}
                size="sm"
                title={tool.purpose}
                className="bg-green-100 dark:bg-green-900/30 text-white cursor-help"
              />
            ))}
          </Stack>
        </Box>
      )}

      {/* Dependencies */}
      {task.dependencies && task.dependencies.length > 0 && (
        <Box className="mt-3">
          <Typography as="span" className="text-xs text-gray-500" fontWeight={500} color="text.secondary" gutterBottom>
            Dependencies
          </Typography>
          <Stack direction="row" spacing={0.5} flexWrap="wrap" gap={0.5}>
            {task.dependencies.map((dep) => (
              <Chip
                key={dep}
                label={dep}
                size="sm"
                className="bg-amber-100 dark:bg-amber-900/30 text-gray-900"
              />
            ))}
          </Stack>
        </Box>
      )}
    </CardContent>
  </Card>
);

// ============================================================================
// Task List Content Component
// ============================================================================

const TaskListContent: React.FC<TaskListContentProps> = ({ domainId }) => {
  const domain = useDomainById(domainId);

  if (!domain) {
    return (
      <Box className="text-center py-8 text-gray-500 dark:text-gray-400">
        <Typography as="p">Domain not found</Typography>
      </Box>
    );
  }

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Typography component="span" className="text-[2rem]">
          {domain.icon || '📋'}
        </Typography>
        <Box>
          <Typography as="h6" fontWeight={600}>
            {domain.name}
          </Typography>
          {domain.description && (
            <Typography as="p" className="text-sm" color="text.secondary">
              {domain.description}
            </Typography>
          )}
        </Box>
      </Stack>

      <Box className="text-center py-8 text-gray-500 dark:text-gray-400">
        <Typography as="p">
          Tasks are loaded from the domain configuration.
        </Typography>
        <Typography as="p" className="text-sm" className="mt-2">
          Select a domain to see associated tasks.
        </Typography>
      </Box>
    </Stack>
  );
};

// ============================================================================
// Main Component
// ============================================================================

/**
 * Task list view component that displays tasks for the selected domain
 *
 * @example
 * ```tsx
 * <TaskListView className="my-4" />
 * ```
 */
export const TaskListView: React.FC<TaskListViewProps> = ({ className = '' }) => {
  const [selectedDomainId] = useAtom(selectedDomainIdAtom);

  if (!selectedDomainId) {
    return (
      <Box className={className} className="text-center py-16 text-gray-500 dark:text-gray-400">
        <Typography className="mb-2 text-[2.5rem]">📋</Typography>
        <Typography as="p">Select a domain to view tasks</Typography>
      </Box>
    );
  }

  return (
    <Box className={className}>
      <Suspense fallback={<TaskListSkeleton />}>
        <TaskListContent domainId={selectedDomainId} />
      </Suspense>
    </Box>
  );
};

export default TaskListView;
