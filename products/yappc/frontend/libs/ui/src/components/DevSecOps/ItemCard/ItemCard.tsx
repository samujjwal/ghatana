/**
 * ItemCard Component
 *
 * A draggable card component for displaying canvas items with priority,
 * status, and progress information.
 *
 * @module DevSecOps/ItemCard
 */

import { Avatar, AvatarGroup, Box, Card, CardContent, Chip, LinearProgress, Typography } from '@ghatana/ui';
import { GitBranch as AccountTreeIcon } from 'lucide-react';

import type { ItemCardProps } from './types';
import type React from 'react';

/**
 * ItemCard - Canvas item card
 *
 * Displays item information in a card format with drag-and-drop support,
 * priority indicators, and progress tracking.
 *
 * @param props - ItemCard component props
 * @returns Rendered ItemCard component
 *
 * @example
 * ```tsx
 * <ItemCard
 *   item={{
 *     id: '123',
 *     title: 'User Authentication',
 *     priority: 'high',
 *     owners: ['John Doe'],
 *     progress: 75
 *   }}
 *   onSelect={handleSelect}
 * />
 * ```
 */
export const ItemCard: React.FC<ItemCardProps> = ({
  item,
  selected = false,
  onSelect,
  onWorkflowClick,
  draggable = false,
}) => {
  const priorityColors = {
    low: 'info',
    medium: 'warning',
    high: 'error',
    critical: 'error',
  } as const;

  const statusColors = {
    'not-started': 'default',
    'in-progress': 'primary',
    'in-review': 'warning',
    'completed': 'success',
    'blocked': 'error',
    'archived': 'default',
  } as const;

  const statusLabels = {
    'not-started': 'Not Started',
    'in-progress': 'In Progress',
    'in-review': 'In Review',
    'completed': 'Completed',
    'blocked': 'Blocked',
    'archived': 'Archived',
  } as const;

  const priorityColor = priorityColors[item.priority || 'medium'];
  const statusColor = item.status ? statusColors[item.status] : 'default';
  const statusLabel = item.status ? statusLabels[item.status] : 'Unknown';
  // Clamp progress to 0-100 range
  const progress = Math.max(0, Math.min(100, item.progress || 0));
  const displayTitle = item.title || 'Untitled';

  return (
    <Card
      data-testid="item-card"
      data-item-id={item.id}
      draggable={draggable}
      onClick={() => onSelect?.(item.id)}
      className={`min-w-[240px] min-h-[160px] cursor-pointer rounded transition-all hover:shadow-lg hover:-translate-y-[3px] hover:bg-blue-500/[0.01] ${selected ? 'border-2 shadow-md bg-blue-500/[0.03]' : 'border'}`}
      style={{
        borderStyle: 'solid',
        borderWidth: selected ? 2 : 1,
        borderColor: selected ? '#2563eb' : '#e5e7eb',
        borderLeftWidth: 5,
        borderLeftColor: priorityColor === 'error' ? '#ef4444' : priorityColor === 'warning' ? '#f59e0b' : '#3b82f6',
        borderTopWidth: 3,
        borderTopColor: '#2563eb',
        transitionDuration: 'var(--ds-duration-base)',
        transitionTimingFunction: 'var(--ds-ease-in-out)',
      }}
    >
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="flex-start" mb={1}>
          <Box display="flex" gap={0.5}>
            <Chip
              label={item.priority || 'medium'}
              size="sm"
              color={priorityColor}
              className="capitalize"
            />
            <Chip
              label={statusLabel}
              size="sm"
              color={statusColor}
            />
          </Box>
          <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
            #{item.id}
          </Typography>
        </Box>

        <Typography as="h6" className="mt-2 mb-4" noWrap title={displayTitle}>
          {displayTitle}
        </Typography>

        {item.description && (
          <Typography
            as="p" className="text-sm"
            color="text.secondary"
            className="mb-4"
            noWrap
            title={item.description}
          >
            {item.description}
          </Typography>
        )}

        {item.owners && item.owners.length > 0 && (
          <Box display="flex" alignItems="center" gap={1} mb={1}>
            {item.owners.length === 1 ? (
              <>
                <Avatar className="text-xs w-[24px] h-[24px]">
                  {item.owners[0].name ? item.owners[0].name.split(' ').map((n: string) => n[0]).join('').toUpperCase() : '?'}
                </Avatar>
                <Typography as="span" className="text-xs text-gray-500" noWrap>
                  {item.owners[0].name || item.owners[0].id}
                </Typography>
              </>
            ) : (
              <AvatarGroup max={3} className="[&>*]:w-6 [&>*]:h-6 [&>*]:text-xs">
                {item.owners.map((owner) => (
                  <Avatar key={owner.id} alt={owner.name}>
                    {owner.name ? owner.name.split(' ').map((n: string) => n[0]).join('').toUpperCase() : '?'}
                  </Avatar>
                ))}
              </AvatarGroup>
            )}
          </Box>
        )}

        {(item.tags?.length > 0 || item.workflowId) && (
          <Box display="flex" flexWrap="wrap" gap={0.5} mb={1}>
            {item.tags?.map((tag) => (
              <Chip
                key={tag}
                label={tag}
                size="sm"
                variant="outlined"
              />
            ))}
            {item.workflowId && (
              <Chip
                icon={<AccountTreeIcon style={{ fontSize: 16, borderRadius: 'var(--ds-radius-full)' }} />}
                label="Workflow"
                size="sm"
                variant="outlined"
                tone="primary"
                onClick={(e) => {
                  e.stopPropagation();
                  if (item.workflowId) {
                    onWorkflowClick?.(item.workflowId);
                  }
                }}
              />
            )}
          </Box>
        )}

        <LinearProgress
          variant="determinate"
          value={progress}
          role="progressbar"
          aria-valuenow={progress}
          aria-valuemin={0}
          aria-valuemax={100}
          className="mb-1 h-[6px]" />
        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
          {progress}% Complete
        </Typography>

        {item.dueDate && (
          <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mt-2 block">
            Due: {item.dueDate}
          </Typography>
        )}

        {item.artifacts && item.artifacts.length > 0 && (
          <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mt-2 block">
            {item.artifacts.length} artifact{item.artifacts.length !== 1 ? 's' : ''}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
};
