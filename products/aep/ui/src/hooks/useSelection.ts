/**
 * useSelection — Bulk selection hook for tables and lists.
 *
 * Designed for cross-product reuse. Can be extracted to @ghatana/ui-hooks
 * after validation in AEP and Data Cloud.
 *
 * @doc.type hook
 * @doc.purpose Manage bulk selection state for tables and lists
 * @doc.layer frontend
 */

import { useState, useCallback, useMemo } from 'react';

/**
 * Selection hook options
 */
interface UseSelectionOptions<T> {
  items: T[];
  keyFn: (item: T) => string;
  /**
   * Optional: Initial selected IDs
   */
  initialSelectedIds?: Set<string>;
  /**
   * Optional: Whether to allow multiple selection (default: true)
   */
  allowMultiple?: boolean;
}

/**
 * Selection hook return value
 */
interface UseSelectionReturn<T> {
  selectedIds: Set<string>;
  selectedItems: T[];
  selectedCount: number;
  isAllSelected: boolean;
  isIndeterminate: boolean;
  toggle: (id: string) => void;
  toggleAll: () => void;
  select: (id: string) => void;
  deselect: (id: string) => void;
  selectAll: () => void;
  deselectAll: () => void;
  setSelectedIds: (ids: Set<string>) => void;
}

/**
 * useSelection hook
 *
 * Manages bulk selection state for tables and lists. Provides methods to
 * toggle individual items, select/deselect all, and get selected items.
 * Supports single and multiple selection modes.
 */
export function useSelection<T>({
  items,
  keyFn,
  initialSelectedIds = new Set(),
  allowMultiple = true,
}: UseSelectionOptions<T>): UseSelectionReturn<T> {
  const [selectedIds, setSelectedIds] = useState<Set<string>>(initialSelectedIds);

  /**
   * Toggle selection for a single item
   */
  const toggle = useCallback((id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (allowMultiple) {
        if (next.has(id)) {
          next.delete(id);
        } else {
          next.add(id);
        }
      } else {
        // Single selection mode
        if (next.has(id)) {
          next.clear();
        } else {
          next.clear();
          next.add(id);
        }
      }
      return next;
    });
  }, [allowMultiple]);

  /**
   * Toggle all items (select/deselect all)
   */
  const toggleAll = useCallback(() => {
    setSelectedIds((prev) => {
      const allIds = new Set(items.map(keyFn));
      if (prev.size === allIds.size && [...prev].every((id) => allIds.has(id))) {
        // All selected, deselect all
        return new Set();
      }
      // Select all
      return allIds;
    });
  }, [items, keyFn]);

  /**
   * Select a specific item
   */
  const select = useCallback((id: string) => {
    setSelectedIds((prev) => {
      if (allowMultiple) {
        const next = new Set(prev);
        next.add(id);
        return next;
      }
      // Single selection mode
      return new Set([id]);
    });
  }, [allowMultiple]);

  /**
   * Deselect a specific item
   */
  const deselect = useCallback((id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      next.delete(id);
      return next;
    });
  }, []);

  /**
   * Select all items
   */
  const selectAll = useCallback(() => {
    setSelectedIds(new Set(items.map(keyFn)));
  }, [items, keyFn]);

  /**
   * Deselect all items
   */
  const deselectAll = useCallback(() => {
    setSelectedIds(new Set());
  }, []);

  /**
   * Get selected items
   */
  const selectedItems = useMemo(() => {
    return items.filter((item) => selectedIds.has(keyFn(item)));
  }, [items, selectedIds, keyFn]);

  /**
   * Check if all items are selected
   */
  const isAllSelected = useMemo(() => {
    if (items.length === 0) return false;
    const allIds = new Set(items.map(keyFn));
    return selectedIds.size === allIds.size && [...selectedIds].every((id) => allIds.has(id));
  }, [items, selectedIds, keyFn]);

  /**
   * Check if selection is indeterminate (some but not all selected)
   */
  const isIndeterminate = useMemo(() => {
    if (items.length === 0) return false;
    return selectedIds.size > 0 && !isAllSelected;
  }, [selectedIds.size, items.length, isAllSelected]);

  return {
    selectedIds,
    selectedItems,
    selectedCount: selectedIds.size,
    isAllSelected,
    isIndeterminate,
    toggle,
    toggleAll,
    select,
    deselect,
    selectAll,
    deselectAll,
    setSelectedIds,
  };
}

/**
 * Hook for range selection (Shift+Click behavior)
 */
export function useRangeSelection<T>({
  items,
  keyFn,
  allowMultiple = true,
}: {
  items: T[];
  keyFn: (item: T) => string;
  allowMultiple?: boolean;
}) {
  const selection = useSelection({ items, keyFn, allowMultiple });
  const [lastSelectedIndex, setLastSelectedIndex] = useState<number | null>(null);

  const selectRange = useCallback((currentIndex: number) => {
    if (lastSelectedIndex === null) {
      selection.select(keyFn(items[currentIndex]));
      setLastSelectedIndex(currentIndex);
      return;
    }

    if (!allowMultiple) {
      selection.select(keyFn(items[currentIndex]));
      setLastSelectedIndex(currentIndex);
      return;
    }

    const start = Math.min(lastSelectedIndex, currentIndex);
    const end = Math.max(lastSelectedIndex, currentIndex);
    const rangeIds = new Set();

    for (let i = start; i <= end; i++) {
      rangeIds.add(keyFn(items[i]));
    }

    selection.setSelectedIds(rangeIds as Set<string>);
    setLastSelectedIndex(currentIndex);
  }, [items, keyFn, allowMultiple, selection, lastSelectedIndex]);

  const handleItemClick = useCallback((index: number, isShiftKey: boolean) => {
    if (isShiftKey) {
      selectRange(index);
    } else {
      selection.toggle(keyFn(items[index]));
      setLastSelectedIndex(index);
    }
  }, [selection, selectRange, items, keyFn]);

  return {
    ...selection,
    handleItemClick,
    lastSelectedIndex,
  };
}
