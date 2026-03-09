/**
 * ItemCard Component Types
 *
 * @module DevSecOps/ItemCard/types
 */

import type { Item } from '@ghatana/yappc-types/devsecops';

/**
 * Props for the ItemCard component
 *
 * @property item - Item data to display
 * @property selected - Whether the item is currently selected
 * @property onSelect - Callback when item is selected
 * @property onDragStart - Callback when drag starts
 * @property onDragEnd - Callback when drag ends
 * @property draggable - Whether the card should be draggable
 *
 * @example
 * ```typescript
 * <ItemCard
 *   item={itemData}
 *   selected={selectedId === itemData.id}
 *   onSelect={(id) => setSelectedId(id)}
 *   draggable={true}
 * />
 * ```
 */
export interface ItemCardProps {
  /** Item data */
  item: Item;

  /** Selection state */
  selected?: boolean;

  /** Selection callback */
  onSelect?: (id: string) => void;

  /** Drag start callback */
  onDragStart?: (id: string) => void;

  /** Drag end callback */
  onDragEnd?: () => void;

  /** Workflow click callback */
  onWorkflowClick?: (workflowId: string) => void;

  /** Whether the card is draggable */
  draggable?: boolean;
}

// Re-export Item type for convenience
export type { Item };
