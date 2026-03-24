/**
 * Selection management types for the useSelection hook.
 * Provides comprehensive type definitions for managing multi-item selection
 * with keyboard shortcuts, bulk operations, and undo/redo capabilities.
 */

/**
 * Represents a selectable item in a selection context.
 * All items must have a unique `id` property.
 */
export interface SelectionItem {
  /** Unique identifier for the item */
  id: string;
  /** Additional properties can be attached to the item */
  [key: string]: unknown;
}

/**
 * Represents a range of items selected by start and end indices.
 * Used for range-based selection operations (e.g., Shift+Click).
 */
export interface SelectionRange {
  /** Start index of the selection range (inclusive) */
  start: number;
  /** End index of the selection range (inclusive) */
  end: number;
}

/**
 * Represents a single selection action that can be undone/redone.
 * Each action is timestamped and includes the type and affected items.
 */
export interface SelectionAction {
  /** Type of selection action performed */
  type:
    | 'select'
    | 'deselect'
    | 'toggle'
    | 'selectAll'
    | 'deselectAll'
    | 'selectRange';
  /** IDs of items affected by this action (if applicable) */
  items?: string[];
  /** Range information for range-based selections */
  range?: SelectionRange;
  /** Timestamp when the action was performed (milliseconds since epoch) */
  timestamp: number;
}

/**
 * Configuration options for the useSelection hook.
 * Controls selection behavior, keyboard shortcuts, and undo/redo functionality.
 */
export interface UseSelectionOptions<T extends SelectionItem> {
  /** Array of items available for selection */
  items: T[];
  /** Initial selected IDs (for controlled mode) */
  selectedIds?: string[];
  /** Callback fired when selection changes */
  onSelectionChange?: (selectedIds: string[], selectedItems: T[]) => void;
  /** Maximum number of items that can be selected simultaneously */
  maxSelections?: number;
  /** Whether to enable keyboard shortcuts (Ctrl+A, Escape, Shift+Click, etc.) */
  enableKeyboardShortcuts?: boolean;
  /** Whether to enable undo/redo functionality */
  enableUndo?: boolean;
  /** Whether to select items when they receive focus */
  selectOnFocus?: boolean;
}

/**
 * Return type for the useSelection hook.
 * Provides all selection state and methods for managing multi-item selection.
 */
export interface UseSelectionReturn<T extends SelectionItem> {
  // ===== Selection State =====

  /** Array of currently selected item IDs */
  selectedIds: string[];

  /** Array of currently selected items (objects, not just IDs) */
  selectedItems: T[];

  /** Number of currently selected items */
  selectedCount: number;

  /** True if all items in the collection are selected */
  isAllSelected: boolean;

  /** True if some (but not all) items are selected */
  isPartiallySelected: boolean;

  // ===== Selection Actions =====

  /** Select a single item by ID */
  selectItem: (id: string) => void;

  /** Deselect a single item by ID */
  deselectItem: (id: string) => void;

  /** Toggle selection state of a single item */
  toggleItem: (id: string) => void;

  /** Select all items in the collection */
  selectAll: () => void;

  /** Deselect all items (clear selection) */
  deselectAll: () => void;

  /** Select all items within a range (inclusive) */
  selectRange: (startId: string, endId: string) => void;

  /** Select multiple items by their IDs */
  selectMultiple: (ids: string[]) => void;

  // ===== Keyboard Selection =====

  /** Handle keyboard events for selection shortcuts (Ctrl+A, Escape, etc.) */
  handleKeyDown: (event: React.KeyboardEvent, currentId?: string) => boolean;

  // ===== Bulk Operations =====

  /** Check if a specific item is currently selected */
  isSelected: (id: string) => boolean;

  /** Get summary statistics about the current selection */
  getSelectionSummary: () => {
    /** Total number of items available */
    total: number;
    /** Number of selected items */
    selected: number;
    /** Percentage of selected items (0-100) */
    percentage: number;
  };

  // ===== Undo/Redo =====

  /** True if there are actions that can be undone */
  canUndo: boolean;

  /** True if there are actions that can be redone */
  canRedo: boolean;

  /** Undo the last selection action */
  undo: () => void;

  /** Redo the last undone selection action */
  redo: () => void;

  // ===== Helpers =====

  /** Extract a specific property from all selected items */
  getSelectedData: <K extends keyof T>(key: K) => T[K][];

  /** Export current selection as a JSON string for serialization */
  exportSelection: () => string;

  /** Import selection state from a JSON string */
  importSelection: (data: string) => void;
}
