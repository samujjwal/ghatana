/**
 * @ghatana/selection-ui — Selection UI platform stub.
 *
 * Stub implementation until the full platform package is built.
 *
 * @doc.type module
 * @doc.purpose Multi-selection state management hook
 * @doc.layer platform
 * @doc.pattern Library
 */

import React from 'react';

// ── Types ──────────────────────────────────────────────────────────────────────

export interface SelectionItem {
  id: string;
  [key: string]: unknown;
}

export interface UseSelectionOptions<T> {
  items: T[];
  keyFn: (item: T) => string;
}

export interface UseSelectionReturn<T = SelectionItem> {
  selectedIds: Set<string>;
  selectedItems: T[];
  isSelected: (id: string) => boolean;
  isAllSelected: boolean;
  isIndeterminate: boolean;
  toggleSelection: (id: string) => void;
  toggleAll: () => void;
  selectAll: () => void;
  selectIds: (ids: string[]) => void;
  clearSelection: () => void;
  selectedCount: number;
}

// ── Hooks ──────────────────────────────────────────────────────────────────────

export function useSelection<T>(options: UseSelectionOptions<T>): UseSelectionReturn<T> {
  const { items, keyFn } = options;
  const [selectedIds, setSelectedIds] = React.useState<Set<string>>(new Set());

  const isSelected = (id: string): boolean => selectedIds.has(id);

  const toggleSelection = (id: string): void => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const selectAll = (): void => {
    setSelectedIds(new Set(items.map(keyFn)));
  };

  const selectIds = (ids: string[]): void => {
    setSelectedIds(new Set(ids));
  };

  const clearSelection = (): void => {
    setSelectedIds(new Set());
  };

  const toggleAll = (): void => {
    if (selectedIds.size === items.length && items.length > 0) {
      clearSelection();
    } else {
      selectAll();
    }
  };

  const selectedItems = items.filter((item) => selectedIds.has(keyFn(item)));
  const isAllSelected = items.length > 0 && selectedIds.size === items.length;
  const isIndeterminate = selectedIds.size > 0 && selectedIds.size < items.length;

  return {
    selectedIds,
    selectedItems,
    isSelected,
    isAllSelected,
    isIndeterminate,
    toggleSelection,
    toggleAll,
    selectAll,
    selectIds,
    clearSelection,
    selectedCount: selectedIds.size,
  };
}
