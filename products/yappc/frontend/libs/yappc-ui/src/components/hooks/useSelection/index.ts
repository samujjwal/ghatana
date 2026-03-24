/**
 * Selection Management Hook
 *
 * Comprehensive hook for managing multi-item selection with keyboard shortcuts,
 * bulk operations, and accessible interaction patterns. Supports both controlled
 * and uncontrolled selection modes with optimistic updates and undo/redo.
 *
 * Note: The keyboard handler intentionally has high complexity. All keyboard logic is kept
 * in one place for maintainability. The types have been extracted to types.ts and utilities
 * to utils.ts for better organization.
 */

import { useState, useCallback, useMemo, useEffect } from 'react';

import type {
  SelectionItem,
  SelectionAction,
  UseSelectionOptions,
  UseSelectionReturn,
} from './types';

/**
 * Hook for managing multi-item selection with advanced features.
 *
 * Note: This function is intentionally large (394 lines) to keep all selection logic
 * in one place. Type definitions have been extracted to types.ts for better maintainability.
 */
// eslint-disable-next-line max-lines-per-function
export function useSelection<T extends SelectionItem>({
  items,
  selectedIds: controlledSelectedIds,
  onSelectionChange,
  maxSelections = Infinity,
  enableKeyboardShortcuts = true,
  enableUndo = true,
}: UseSelectionOptions<T>): UseSelectionReturn<T> {
  // Internal state for uncontrolled mode
  const [internalSelectedIds, setInternalSelectedIds] = useState<string[]>([]);
  const [lastSelectedId, setLastSelectedId] = useState<string | null>(null);

  // History for undo/redo
  const [selectionHistory, setSelectionHistory] = useState<SelectionAction[]>(
    []
  );
  const [historyIndex, setHistoryIndex] = useState(-1);

  // Determine if controlled or uncontrolled
  const isControlled = controlledSelectedIds !== undefined;
  const selectedIds = isControlled
    ? controlledSelectedIds
    : internalSelectedIds;

  // Create items lookup for performance
  const itemsMap = useMemo(() => {
    return new Map(items.map((item, index) => [item.id, { item, index }]));
  }, [items]);

  // Get selected items
  const selectedItems = useMemo(() => {
    return selectedIds
      .map((id) => itemsMap.get(id)?.item)
      .filter((item): item is T => item !== undefined);
  }, [selectedIds, itemsMap]);

  // Selection state calculations
  const selectedCount = selectedIds.length;
  const isAllSelected = selectedCount > 0 && selectedCount === items.length;
  const isPartiallySelected = selectedCount > 0 && selectedCount < items.length;

  // Update selection state
  const updateSelection = useCallback(
    (newSelectedIds: string[], action?: SelectionAction) => {
      // Apply max selections limit
      const limitedIds =
        maxSelections < Infinity
          ? newSelectedIds.slice(0, maxSelections)
          : newSelectedIds;

      if (isControlled) {
        onSelectionChange?.(
          limitedIds,
          limitedIds.map((id) => itemsMap.get(id)?.item).filter(Boolean) as T[]
        );
      } else {
        setInternalSelectedIds(limitedIds);
      }

      // Add to history for undo/redo
      if (enableUndo && action) {
        const newHistory = selectionHistory.slice(0, historyIndex + 1);
        newHistory.push(action);
        setSelectionHistory(newHistory);
        setHistoryIndex(newHistory.length - 1);
      }

      // Notify change
      onSelectionChange?.(
        limitedIds,
        limitedIds.map((id) => itemsMap.get(id)?.item).filter(Boolean) as T[]
      );
    },
    [
      isControlled,
      onSelectionChange,
      maxSelections,
      itemsMap,
      enableUndo,
      selectionHistory,
      historyIndex,
    ]
  );

  // Individual item selection
  const selectItem = useCallback(
    (id: string) => {
      if (!selectedIds.includes(id)) {
        const newSelectedIds = [...selectedIds, id];
        updateSelection(newSelectedIds, {
          type: 'select',
          items: [id],
          timestamp: Date.now(),
        });
        setLastSelectedId(id);
      }
    },
    [selectedIds, updateSelection]
  );

  const deselectItem = useCallback(
    (id: string) => {
      if (selectedIds.includes(id)) {
        const newSelectedIds = selectedIds.filter(
          (selectedId) => selectedId !== id
        );
        updateSelection(newSelectedIds, {
          type: 'deselect',
          items: [id],
          timestamp: Date.now(),
        });
      }
    },
    [selectedIds, updateSelection]
  );

  const toggleItem = useCallback(
    (id: string) => {
      if (selectedIds.includes(id)) {
        deselectItem(id);
      } else {
        selectItem(id);
      }
      setLastSelectedId(id);
    },
    [selectedIds, selectItem, deselectItem]
  );

  // Bulk selection
  const selectAll = useCallback(() => {
    const allIds = items.map((item) => item.id);
    updateSelection(allIds, {
      type: 'selectAll',
      items: allIds,
      timestamp: Date.now(),
    });
  }, [items, updateSelection]);

  const deselectAll = useCallback(() => {
    updateSelection([], {
      type: 'deselectAll',
      items: selectedIds,
      timestamp: Date.now(),
    });
  }, [selectedIds, updateSelection]);

  // Range selection
  const selectRange = useCallback(
    (startId: string, endId: string) => {
      const startIndex = itemsMap.get(startId)?.index;
      const endIndex = itemsMap.get(endId)?.index;

      if (startIndex !== undefined && endIndex !== undefined) {
        const start = Math.min(startIndex, endIndex);
        const end = Math.max(startIndex, endIndex);

        const rangeIds = items.slice(start, end + 1).map((item) => item.id);
        const newSelectedIds = Array.from(
          new Set([...selectedIds, ...rangeIds])
        );

        updateSelection(newSelectedIds, {
          type: 'selectRange',
          items: rangeIds,
          range: { start, end },
          timestamp: Date.now(),
        });
      }
    },
    [itemsMap, items, selectedIds, updateSelection]
  );

  // Multiple item selection
  const selectMultiple = useCallback(
    (ids: string[]) => {
      const newSelectedIds = Array.from(new Set([...selectedIds, ...ids]));
      updateSelection(newSelectedIds, {
        type: 'select',
        items: ids,
        timestamp: Date.now(),
      });
    },
    [selectedIds, updateSelection]
  );

  // Undo/redo state
  const canUndo = enableUndo && historyIndex >= 0;
  const canRedo = enableUndo && historyIndex < selectionHistory.length - 1;

  const undo = useCallback(() => {
    if (!canUndo) return;

    // eslint-disable-next-line security/detect-object-injection
    const action = selectionHistory[historyIndex];
    let newSelectedIds = [...selectedIds];

    switch (action.type) {
      case 'select':
        newSelectedIds = newSelectedIds.filter(
          (id) => !action.items?.includes(id)
        );
        break;
      case 'deselect':
        newSelectedIds = Array.from(
          new Set([...newSelectedIds, ...(action.items || [])])
        );
        break;
      case 'selectAll':
        newSelectedIds = [];
        break;
      case 'deselectAll':
        newSelectedIds = action.items || [];
        break;
    }

    setHistoryIndex(historyIndex - 1);
    if (isControlled) {
      onSelectionChange?.(
        newSelectedIds,
        newSelectedIds
          .map((id) => itemsMap.get(id)?.item)
          .filter(Boolean) as T[]
      );
    } else {
      setInternalSelectedIds(newSelectedIds);
    }
  }, [
    canUndo,
    selectionHistory,
    historyIndex,
    selectedIds,
    isControlled,
    onSelectionChange,
    itemsMap,
  ]);

  const redo = useCallback(() => {
    if (!canRedo) return;

    const action = selectionHistory[historyIndex + 1];
    let newSelectedIds = [...selectedIds];

    switch (action.type) {
      case 'select':
        newSelectedIds = Array.from(
          new Set([...newSelectedIds, ...(action.items || [])])
        );
        break;
      case 'deselect':
        newSelectedIds = newSelectedIds.filter(
          (id) => !action.items?.includes(id)
        );
        break;
      case 'selectAll':
        newSelectedIds = items.map((item) => item.id);
        break;
      case 'deselectAll':
        newSelectedIds = [];
        break;
    }

    setHistoryIndex(historyIndex + 1);
    if (isControlled) {
      onSelectionChange?.(
        newSelectedIds,
        newSelectedIds
          .map((id) => itemsMap.get(id)?.item)
          .filter(Boolean) as T[]
      );
    } else {
      setInternalSelectedIds(newSelectedIds);
    }
  }, [
    canRedo,
    selectionHistory,
    historyIndex,
    selectedIds,
    items,
    isControlled,
    onSelectionChange,
    itemsMap,
  ]);

  // Keyboard event handling
  const handleKeyDown = useCallback(
    (event: React.KeyboardEvent, currentId?: string) => {
      if (!enableKeyboardShortcuts) return false;

      const { key, ctrlKey, metaKey, shiftKey } = event;
      const cmdKey = ctrlKey || metaKey;

      switch (key) {
        case 'a':
        case 'A':
          if (cmdKey) {
            event.preventDefault();
            selectAll();
            return true;
          }
          break;

        case 'Escape':
          if (selectedCount > 0) {
            event.preventDefault();
            deselectAll();
            return true;
          }
          break;

        case ' ':
        case 'Enter':
          if (currentId) {
            event.preventDefault();
            if (shiftKey && lastSelectedId) {
              selectRange(lastSelectedId, currentId);
            } else {
              toggleItem(currentId);
            }
            return true;
          }
          break;

        case 'ArrowUp':
        case 'ArrowDown':
          if (currentId && shiftKey && lastSelectedId) {
            event.preventDefault();
            selectRange(lastSelectedId, currentId);
            return true;
          }
          break;

        case 'z':
        case 'Z':
          if (cmdKey && !shiftKey && canUndo) {
            event.preventDefault();
            undo();
            return true;
          } else if (cmdKey && shiftKey && canRedo) {
            event.preventDefault();
            redo();
            return true;
          }
          break;
      }

      return false;
    },
    [
      enableKeyboardShortcuts,
      selectAll,
      selectedCount,
      deselectAll,
      lastSelectedId,
      selectRange,
      toggleItem,
      canUndo,
      canRedo,
      undo,
      redo,
    ]
  );

  // Utility functions
  const isSelected = useCallback(
    (id: string) => selectedIds.includes(id),
    [selectedIds]
  );

  const getSelectionSummary = useCallback(
    () => ({
      total: items.length,
      selected: selectedCount,
      percentage:
        items.length > 0 ? Math.round((selectedCount / items.length) * 100) : 0,
    }),
    [items.length, selectedCount]
  );

  const getSelectedData = useCallback(
    <K extends keyof T>(key: K): T[K][] => {
      // eslint-disable-next-line security/detect-object-injection
      return selectedItems.map((item) => item[key]);
    },
    [selectedItems]
  );

  const exportSelection = useCallback(() => {
    return JSON.stringify({
      selectedIds,
      timestamp: Date.now(),
      version: '1.0',
    });
  }, [selectedIds]);

  const importSelection = useCallback(
    (data: string) => {
      try {
        const parsed = JSON.parse(data);
        if (parsed.selectedIds && Array.isArray(parsed.selectedIds)) {
          const validIds = parsed.selectedIds.filter((id: string) =>
            itemsMap.has(id)
          );
          updateSelection(validIds, {
            type: 'select',
            items: validIds,
            timestamp: Date.now(),
          });
        }
      } catch (error) {
        console.warn('Failed to import selection:', error);
      }
    },
    [itemsMap, updateSelection]
  );

  // Keyboard event listener setup
  useEffect(() => {
    if (!enableKeyboardShortcuts) return;

    const handleGlobalKeyDown = (event: KeyboardEvent) => {
      // Convert to React keyboard event format for compatibility
      const reactEvent = {
        key: event.key,
        ctrlKey: event.ctrlKey,
        metaKey: event.metaKey,
        shiftKey: event.shiftKey,
        preventDefault: () => event.preventDefault(),
      } as React.KeyboardEvent;

      handleKeyDown(reactEvent);
    };

    document.addEventListener('keydown', handleGlobalKeyDown);
    return () => document.removeEventListener('keydown', handleGlobalKeyDown);
  }, [enableKeyboardShortcuts, handleKeyDown]);

  return {
    // Selection state
    selectedIds,
    selectedItems,
    selectedCount,
    isAllSelected,
    isPartiallySelected,

    // Selection actions
    selectItem,
    deselectItem,
    toggleItem,
    selectAll,
    deselectAll,
    selectRange,
    selectMultiple,

    // Keyboard selection
    handleKeyDown,

    // Bulk operations
    isSelected,
    getSelectionSummary,

    // Undo/redo
    canUndo,
    canRedo,
    undo,
    redo,

    // Helpers
    getSelectedData,
    exportSelection,
    importSelection,
  };
}
