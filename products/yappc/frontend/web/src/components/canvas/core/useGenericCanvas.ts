/**
 * Generic Canvas State Management Hook
 * Provides unified state management for all canvas implementations
 */

import { useState, useCallback, useMemo, useEffect, useRef } from 'react';

import type {
  BaseItem,
  CanvasCapabilities,
  ViewModeDefinition,
  CanvasAPI,
  CanvasState,
  CanvasEvent,
  CanvasEventHandler,
  CanvasActionContext,
  TemplateDefinition,
  FilterDefinition,
  SortDefinition,
  CanvasPlugin,
} from './types';

/**
 *
 */
export interface UseGenericCanvasOptions<TItem extends BaseItem> {
  items: TItem[];
  onItemsChange: (items: TItem[]) => void;
  capabilities: CanvasCapabilities;
  viewModes: ViewModeDefinition<TItem>[];
  defaultViewMode?: string;
  templates?: TemplateDefinition<TItem>[];
  filters?: FilterDefinition<TItem>[];
  sorts?: SortDefinition<TItem>[];
  plugins?: CanvasPlugin<TItem>[];
  persistenceKey?: string;
  initialState?: Partial<CanvasState<TItem>>;
  onStateChange?: (state: CanvasState<TItem>) => void;
  onSelectionChange?: (selectedIds: string[]) => void;
  onViewModeChange?: (mode: string) => void;
}

/**
 *
 */
export interface UseGenericCanvasResult<TItem extends BaseItem> {
  canvasState: CanvasState<TItem>;
  selectedItems: string[];
  currentViewMode: string;
  filteredItems: TItem[];
  canvasAPI: CanvasAPI<TItem>;
  actions: {
    createItem: (item: Omit<TItem, 'id'>) => void;
    updateItem: (id: string, updates: Partial<TItem>) => void;
    deleteItem: (id: string) => void;
    selectItem: (id: string, multi?: boolean) => void;
    clearSelection: () => void;
    setViewMode: (mode: string) => void;
    loadTemplate: (templateId: string) => void;
    exportState: () => CanvasState<TItem>;
    importState: (state: CanvasState<TItem>) => void;
  };
}

export const useGenericCanvas = <TItem extends BaseItem>(
  options: UseGenericCanvasOptions<TItem>
): UseGenericCanvasResult<TItem> => {
  const {
    items,
    onItemsChange,
    capabilities,
    viewModes,
    defaultViewMode,
    templates,
    filters,
    sorts,
    plugins,
    persistenceKey,
    initialState,
    onStateChange,
    onSelectionChange,
    onViewModeChange,
  } = options;

  // Core state
  const [selectedItems, setSelectedItems] = useState<string[]>(
    initialState?.selectedItems || []
  );
  const [currentViewMode, setCurrentViewMode] = useState<string>(
    initialState?.viewMode || defaultViewMode || viewModes[0]?.id || 'default'
  );
  const [activeFilters] = useState<Record<string, unknown>>({});
  const [currentSort] = useState<string>('');
  const [, setUndoStack] = useState<CanvasState<TItem>[]>([]);
  const [, setRedoStack] = useState<CanvasState<TItem>[]>([]);

  // Event listeners
  const eventListeners = useRef<Map<CanvasEvent, CanvasEventHandler<TItem>[]>>(
    new Map()
  );

  // Generate unique IDs for items
  const generateId = useCallback(() => {
    return `item-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }, []);

  // Create canvas state
  const canvasState = useMemo<CanvasState<TItem>>(
    () => ({
      items,
      selectedItems,
      viewMode: currentViewMode,
      metadata: {
        version: '1.0.0',
        createdAt:
          initialState?.metadata?.createdAt || new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
    }),
    [items, selectedItems, currentViewMode, initialState]
  );

  // Filtered and sorted items
  const filteredItems = useMemo(() => {
    let result = [...items];

    // Apply filters
    if (filters) {
      Object.entries(activeFilters).forEach(([filterId, value]) => {
        const filter = filters.find((f) => f.id === filterId);
        if (filter && value !== undefined && value !== '') {
          result = result.filter((item) => filter.predicate(item, value));
        }
      });
    }

    // Apply sort
    if (currentSort && sorts) {
      const sort = sorts.find((s) => s.id === currentSort);
      if (sort) {
        result.sort(sort.compareFn);
      }
    }

    return result;
  }, [items, activeFilters, currentSort, filters, sorts]);

  // Emit event
  const emitEvent = useCallback(
    (event: CanvasEvent, payload: unknown) => {
      const listeners = eventListeners.current.get(event) || [];
      const context: CanvasActionContext<TItem> = {
        items: filteredItems,
        selectedItems,
        viewMode: currentViewMode,
        canvasAPI: {} as CanvasAPI<TItem>, // Will be set below
      };

      listeners.forEach((handler) => {
        try {
          handler(payload, context);
        } catch (error) {
          console.error(`Error in canvas event handler for ${event}:`, error);
        }
      });
    },
    [filteredItems, selectedItems, currentViewMode]
  );

  // Save state to history for undo/redo
  const saveToHistory = useCallback(() => {
    if (!capabilities.undo) return;

    setUndoStack((prev) => [...prev.slice(-19), canvasState]); // Keep last 20 states
    setRedoStack([]); // Clear redo stack on new action
  }, [capabilities.undo, canvasState]);

  // Actions
  const createItem = useCallback(
    (itemData: Omit<TItem, 'id'>) => {
      saveToHistory();

      const newItem = {
        ...itemData,
        id: generateId(),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          ...itemData.metadata,
        },
      } as TItem;

      const updatedItems = [...items, newItem];
      onItemsChange(updatedItems);

      emitEvent('item-created', newItem);
    },
    [items, onItemsChange, generateId, saveToHistory, emitEvent]
  );

  const updateItem = useCallback(
    (id: string, updates: Partial<TItem>) => {
      saveToHistory();

      const updatedItems = items.map((item) =>
        item.id === id
          ? {
              ...item,
              ...updates,
              metadata: {
                ...item.metadata,
                updatedAt: new Date().toISOString(),
              },
            }
          : item
      );

      onItemsChange(updatedItems);

      const updatedItem = updatedItems.find((item) => item.id === id);
      if (updatedItem) {
        emitEvent('item-updated', { id, updates, item: updatedItem });
      }
    },
    [items, onItemsChange, saveToHistory, emitEvent]
  );

  const deleteItem = useCallback(
    (id: string) => {
      saveToHistory();

      const itemToDelete = items.find((item) => item.id === id);
      const updatedItems = items.filter((item) => item.id !== id);
      onItemsChange(updatedItems);

      // Remove from selection if selected
      if (selectedItems.includes(id)) {
        const newSelection = selectedItems.filter(
          (selectedId) => selectedId !== id
        );
        setSelectedItems(newSelection);
        onSelectionChange?.(newSelection);
      }

      if (itemToDelete) {
        emitEvent('item-deleted', itemToDelete);
      }
    },
    [
      items,
      onItemsChange,
      selectedItems,
      onSelectionChange,
      saveToHistory,
      emitEvent,
    ]
  );

  const selectItem = useCallback(
    (id: string, multi = false) => {
      let newSelection: string[];

      if (multi) {
        newSelection = selectedItems.includes(id)
          ? selectedItems.filter((selectedId) => selectedId !== id)
          : [...selectedItems, id];
      } else {
        newSelection =
          selectedItems.includes(id) && selectedItems.length === 1 ? [] : [id];
      }

      setSelectedItems(newSelection);
      onSelectionChange?.(newSelection);
      emitEvent('selection-changed', { selectedItems: newSelection });
    },
    [selectedItems, onSelectionChange, emitEvent]
  );

  const clearSelection = useCallback(() => {
    setSelectedItems([]);
    onSelectionChange?.([]);
    emitEvent('selection-changed', { selectedItems: [] });
  }, [onSelectionChange, emitEvent]);

  const setViewMode = useCallback(
    (mode: string) => {
      setCurrentViewMode(mode);
      onViewModeChange?.(mode);
      emitEvent('view-mode-changed', { viewMode: mode });
    },
    [onViewModeChange, emitEvent]
  );

  const loadTemplate = useCallback(
    (templateId: string) => {
      const template = templates?.find((t) => t.id === templateId);
      if (!template) {
        console.warn(`Template not found: ${templateId}`);
        return;
      }

      saveToHistory();

      const templateItems = template.items.map((item) => ({
        ...item,
        id: generateId(),
        metadata: {
          ...item.metadata,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      })) as TItem[];

      onItemsChange([...items, ...templateItems]);
      emitEvent('template-loaded', { template, items: templateItems });
    },
    [templates, items, onItemsChange, generateId, saveToHistory, emitEvent]
  );

  const exportState = useCallback((): CanvasState<TItem> => {
    return { ...canvasState };
  }, [canvasState]);

  const importState = useCallback(
    (state: CanvasState<TItem>) => {
      onItemsChange(state.items);
      setSelectedItems(state.selectedItems);
      setCurrentViewMode(state.viewMode);
      emitEvent('state-loaded', state);
    },
    [onItemsChange, emitEvent]
  );

  // Note: Undo/redo functions available but not exposed in current API
  // Can be added to actions if needed in the future

  // Create Canvas API
  const canvasAPI = useMemo<CanvasAPI<TItem>>(
    () => ({
      getItems: () => items,
      getItem: (id: string) => items.find((item) => item.id === id),
      createItem,
      updateItem,
      deleteItem,
      getSelectedItems: () => selectedItems,
      selectItem,
      clearSelection,
      getViewMode: () => currentViewMode,
      setViewMode,
      exportState,
      importState,
      subscribe: (event: CanvasEvent, handler: CanvasEventHandler<TItem>) => {
        const listeners = eventListeners.current.get(event) || [];
        listeners.push(handler);
        eventListeners.current.set(event, listeners);

        // Return unsubscribe function
        return () => {
          const currentListeners = eventListeners.current.get(event) || [];
          const updatedListeners = currentListeners.filter(
            (h) => h !== handler
          );
          eventListeners.current.set(event, updatedListeners);
        };
      },
    }),
    [
      items,
      createItem,
      updateItem,
      deleteItem,
      selectedItems,
      selectItem,
      clearSelection,
      currentViewMode,
      setViewMode,
      exportState,
      importState,
    ]
  );

  // Initialize plugins
  useEffect(() => {
    plugins?.forEach((plugin) => {
      plugin.onInit?.(canvasAPI);
    });

    return () => {
      plugins?.forEach((plugin) => {
        plugin.onDestroy?.();
      });
    };
  }, [plugins, canvasAPI]);

  // Persistence
  useEffect(() => {
    if (capabilities.persistence && persistenceKey) {
      try {
        localStorage.setItem(persistenceKey, JSON.stringify(canvasState));
      } catch (error) {
        console.error('Failed to save canvas state:', error);
      }
    }
  }, [capabilities.persistence, persistenceKey, canvasState]);

  // Load persisted state on mount
  useEffect(() => {
    if (capabilities.persistence && persistenceKey && !initialState) {
      try {
        const saved = localStorage.getItem(persistenceKey);
        if (saved) {
          const state = JSON.parse(saved) as CanvasState<TItem>;
          importState(state);
        }
      } catch (error) {
        console.error('Failed to load canvas state:', error);
      }
    }
  }, [capabilities.persistence, persistenceKey, initialState, importState]);

  // Notify of state changes
  useEffect(() => {
    onStateChange?.(canvasState);
  }, [canvasState, onStateChange]);

  return {
    canvasState,
    selectedItems,
    currentViewMode,
    filteredItems,
    canvasAPI,
    actions: {
      createItem,
      updateItem,
      deleteItem,
      selectItem,
      clearSelection,
      setViewMode,
      loadTemplate,
      exportState,
      importState,
    },
  };
};
