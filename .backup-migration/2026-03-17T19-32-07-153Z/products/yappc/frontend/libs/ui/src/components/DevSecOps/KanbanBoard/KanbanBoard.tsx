/**
 * KanbanBoard Component
 *
 * A drag-and-drop Kanban board for visualizing and managing items
 * across different status columns.
 *
 * @module DevSecOps/KanbanBoard
 */

import {
  DndContext,
  DragOverlay,
  closestCorners,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core';
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
  useSortable
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { Box, Surface as Paper, Stack, Typography, Chip, LinearProgress } from '@ghatana/ui';
import { useMemo, useState } from 'react';


import { ItemCard } from '../ItemCard';

import type { KanbanBoardProps, KanbanColumn } from './types';
import type { Item, ItemStatus } from '@ghatana/yappc-types/devsecops';

/**
 * Default column configuration
 */
const DEFAULT_COLUMNS: KanbanColumn[] = [
  {
    id: 'not-started',
    title: 'Not Started',
    status: 'not-started',
    color: 'var(--color-grey-400, #9CA3AF)',
    order: 1,
  },
  {
    id: 'in-progress',
    title: 'In Progress',
    status: 'in-progress',
    color: 'var(--color-blue-500, #3B82F6)',
    wipLimit: 5,
    order: 2,
  },
  {
    id: 'in-review',
    title: 'In Review',
    status: 'in-review',
    color: 'var(--color-warning-main, #F59E0B)',
    wipLimit: 3,
    order: 3,
  },
  {
    id: 'completed',
    title: 'Completed',
    status: 'completed',
    color: 'var(--color-success-main, #10B981)',
    order: 4,
  },
  {
    id: 'blocked',
    title: 'Blocked',
    status: 'blocked',
    color: 'var(--color-error-main, #EF4444)',
    order: 5,
  },
];

/**
 * Sortable Item Card wrapper for drag-and-drop
 */
interface SortableItemProps {
  item: Item;
  onItemClick?: (item: Item) => void;
  onWorkflowClick?: (workflowId: string) => void;
}

/**
 *
 */
function SortableItem({ item, onItemClick, onWorkflowClick }: SortableItemProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: item.id,
  });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  return (
    <div ref={setNodeRef} style={style} {...attributes} {...listeners}>
      <ItemCard item={item} onSelect={() => onItemClick?.(item)} onWorkflowClick={onWorkflowClick} />
    </div>
  );
}

/**
 * Kanban Column Component
 */
interface KanbanColumnProps {
  column: KanbanColumn;
  items: Item[];
  showWipLimit?: boolean;
  onItemClick?: (item: Item) => void;
  onWorkflowClick?: (workflowId: string) => void;
}

/**
 *
 */
function KanbanColumnComponent({ column, items, showWipLimit, onItemClick, onWorkflowClick }: KanbanColumnProps) {
  const isOverLimit = column.wipLimit ? items.length > column.wipLimit : false;

  return (
    <Paper
      role="region"
      aria-label={`${column.title} column`}
      variant="raised"
      className="min-w-[300px] max-w-[350px] bg-white dark:bg-gray-900 border-t-[3px] flex flex-col h-full"
      style={{ borderColor: column.color || '#3b82f6' }}
    >
      {/* Column Header */}
      <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Typography as="h6" fontWeight={600}>
            {column.title}
          </Typography>
          <Chip
            label={items.length}
            size="sm"
            color={isOverLimit ? 'error' : 'default'}
          />
        </Box>

        {showWipLimit && column.wipLimit && (
          <Box className="mt-2">
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={0.5}>
              <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                WIP Limit
              </Typography>
              <Typography as="span" className="text-xs text-gray-500" color={isOverLimit ? 'error.main' : 'text.secondary'}>
                {items.length} / {column.wipLimit}
              </Typography>
            </Box>
            <LinearProgress
              variant="determinate"
              value={(items.length / column.wipLimit) * 100}
              color={isOverLimit ? 'error' : 'primary'}
              className="rounded-lg h-[4px]"
            />
          </Box>
        )}
      </Box>

      {/* Column Items */}
      <Box
        className="p-4 grow overflow-y-auto min-h-[400px] bg-gray-50 dark:bg-gray-800"
      >
        <SortableContext items={items.map((i) => i.id)} strategy={verticalListSortingStrategy}>
          <Stack spacing={2}>
            {items.map((item) => (
              <SortableItem key={item.id} item={item} onItemClick={onItemClick} onWorkflowClick={onWorkflowClick} />
            ))}
          </Stack>
        </SortableContext>

        {items.length === 0 && (
          <Box
            className="flex items-center justify-center h-full min-h-[200px]"
          >
            <Typography as="p" className="text-sm" color="text.secondary">
              No items
            </Typography>
          </Box>
        )}
      </Box>
    </Paper>
  );
}

/**
 * KanbanBoard - Drag-and-drop board view
 *
 * Displays items in status-based columns with drag-and-drop support.
 *
 * @param props - KanbanBoard component props
 * @returns Rendered KanbanBoard component
 *
 * @example
 * ```tsx
 * <KanbanBoard
 *   items={items}
 *   onItemMove={(data) => updateItemStatus(data.item.id, data.targetStatus)}
 *   onItemClick={(item) => setSelectedItem(item)}
 *   showWipLimits
 * />
 * ```
 */
export function KanbanBoard({
  items,
  columns = DEFAULT_COLUMNS,
  onItemMove,
  onItemClick,
  onWorkflowClick,
  dragEnabled = true,
  showWipLimits = true,
  loading = false,
}: KanbanBoardProps) {
  const [activeId, setActiveId] = useState<string | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 8,
      },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  // Group items by status
  const itemsByStatus = useMemo(() => {
    const grouped: Record<string, Item[]> = {};
    columns.forEach((col) => {
      grouped[col.status] = [];
    });

    items.forEach((item) => {
      if (grouped[item.status]) {
        grouped[item.status].push(item);
      }
    });

    return grouped;
  }, [items, columns]);

  const handleDragStart = (event: DragStartEvent) => {
    setActiveId(event.active.id as string);
  };

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;

    if (!over || active.id === over.id) {
      setActiveId(null);
      return;
    }

    // Find the item being dragged
    const draggedItem = items.find((item) => item.id === active.id);
    if (!draggedItem) {
      setActiveId(null);
      return;
    }

    // Find source and target columns
    const sourceColumn = columns.find((col) => col.status === draggedItem.status);
    const targetColumn = columns.find((col) =>
      itemsByStatus[col.status]?.some((item) => item.id === over.id) ||
      col.id === over.id
    );

    if (!sourceColumn || !targetColumn) {
      setActiveId(null);
      return;
    }

    // If status changed, trigger callback
    if (sourceColumn.status !== targetColumn.status) {
      onItemMove?.({
        item: draggedItem,
        sourceColumnId: sourceColumn.id,
        targetColumnId: targetColumn.id,
        sourceStatus: sourceColumn.status,
        targetStatus: targetColumn.status,
      });
    }

    setActiveId(null);
  };

  const handleDragCancel = () => {
    setActiveId(null);
  };

  if (loading) {
    return (
      <Box className="w-full p-4">
        <LinearProgress />
        <Typography as="p" className="text-sm" color="text.secondary" className="mt-4 text-center">
          Loading board...
        </Typography>
      </Box>
    );
  }

  const activeItem = activeId ? items.find((item) => item.id === activeId) : null;

  return (
    <DndContext
      sensors={dragEnabled ? sensors : []}
      collisionDetection={closestCorners}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
      onDragCancel={handleDragCancel}
    >
      <Box
        className="flex gap-4 overflow-x-auto pb-4 min-h-[600px]"
      >
        {columns
          .sort((a, b) => a.order - b.order)
          .map((column) => (
            <KanbanColumnComponent
              key={column.id}
              column={column}
              items={itemsByStatus[column.status] || []}
              showWipLimit={showWipLimits}
              onItemClick={onItemClick}
              onWorkflowClick={onWorkflowClick}
            />
          ))}
      </Box>

      <DragOverlay>
        {activeItem ? (
          <Box className="rotate-3 cursor-grabbing">
            <ItemCard item={activeItem} />
          </Box>
        ) : null}
      </DragOverlay>
    </DndContext>
  );
}
