/**
 * useSelection - Generic selection management hook
 *
 * @doc.type hook
 * @doc.purpose Manage selection state for lists and tables
 * @doc.layer platform
 * @doc.pattern Selection Hook
 */

import { useState, useCallback, useMemo } from 'react';

/**
 * Selection item interface
 */
export interface SelectionItem {
  id: string;
  [key: string]: unknown;
}

/**
 * Selection options
 */
export interface UseSelectionOptions<T extends SelectionItem> {
  items: T[];
  keyFn?: (item: T) => string;
  initialSelection?: Set<string>;
}

/**
 * Selection return type
 */
export interface UseSelectionReturn<T extends SelectionItem> {
  selectedIds: Set<string>;
  selectedItems: T[];
  isAllSelected: boolean;
  isIndeterminate: boolean;
  toggleSelection: (id: string) => void;
  toggleAll: () => void;
  clearSelection: () => void;
  selectIds: (ids: string[]) => void;
}

/**
 * Use selection hook
 */
export function useSelection<T extends SelectionItem>(
  options: UseSelectionOptions<T>,
): UseSelectionReturn<T> {
  const { items, keyFn = (item) => item.id, initialSelection = new Set<string>() } = options;

  const [selectedIds, setSelectedIds] = useState<Set<string>>(initialSelection);

  const selectedItems = useMemo(
    () => items.filter((item) => selectedIds.has(keyFn(item))),
    [items, selectedIds, keyFn],
  );

  const isAllSelected = useMemo(() => {
    if (items.length === 0) return false;
    return items.every((item) => selectedIds.has(keyFn(item)));
  }, [items, selectedIds, keyFn]);

  const isIndeterminate = useMemo(() => {
    if (items.length === 0) return false;
    const selectedCount = items.filter((item) => selectedIds.has(keyFn(item))).length;
    return selectedCount > 0 && selectedCount < items.length;
  }, [items, selectedIds, keyFn]);

  const toggleSelection = useCallback((id: string): void => {
    setSelectedIds((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(id)) {
        newSet.delete(id);
      } else {
        newSet.add(id);
      }
      return newSet;
    });
  }, []);

  const toggleAll = useCallback((): void => {
    setSelectedIds(() => {
      if (isAllSelected) {
        return new Set<string>();
      }
      return new Set(items.map(keyFn));
    });
  }, [isAllSelected, items, keyFn]);

  const clearSelection = useCallback((): void => {
    setSelectedIds(new Set<string>());
  }, []);

  const selectIds = useCallback((ids: string[]): void => {
    setSelectedIds(new Set(ids));
  }, []);

  return {
    selectedIds,
    selectedItems,
    isAllSelected,
    isIndeterminate,
    toggleSelection,
    toggleAll,
    clearSelection,
    selectIds,
  };
}
