/**
 * KanbanBoard Component Types
 *
 * @module DevSecOps/KanbanBoard/types
 */

import type { Item, ItemStatus } from '@ghatana/yappc-types/devsecops';

/**
 * Kanban column configuration
 */
export interface KanbanColumn {
  /** Unique column identifier */
  id: string;

  /** Column title */
  title: string;

  /** Item status this column represents */
  status: ItemStatus;

  /** Column color */
  color?: string;

  /** Maximum work-in-progress limit */
  wipLimit?: number;

  /** Column order */
  order: number;
}

/**
 * Drag event data
 */
export interface DragEventData {
  /** Item being dragged */
  item: Item;

  /** Source column ID */
  sourceColumnId: string;

  /** Target column ID */
  targetColumnId: string;

  /** Source status */
  sourceStatus: ItemStatus;

  /** Target status */
  targetStatus: ItemStatus;
}

/**
 * Props for the KanbanBoard component
 */
export interface KanbanBoardProps {
  /** Items to display in the board */
  items: Item[];

  /** Custom column configuration (optional) */
  columns?: KanbanColumn[];

  /** Callback when item is moved between columns */
  onItemMove?: (data: DragEventData) => void;

  /** Callback when item is clicked */
  onItemClick?: (item: Item) => void;

  /** Callback when workflow is clicked */
  onWorkflowClick?: (workflowId: string) => void;

  /** Whether drag-and-drop is enabled */
  dragEnabled?: boolean;

  /** Show WIP limits on columns */
  showWipLimits?: boolean;

  /** Loading state */
  loading?: boolean;
}
