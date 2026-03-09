/**
 * Hook Migration Utilities
 * Tools for migrating existing canvas hooks to use the generic useGenericCanvas
 */

import React from 'react';

import { useGenericCanvas } from '../../components/canvas/core/useGenericCanvas';

import type {
  BaseItem,
  ViewModeDefinition,
} from '../../components/canvas/core/types';

// Legacy hook interfaces for migration
/**
 *
 */
export interface LegacyCanvasSceneHook<T> {
  items: T[];
  selectedItems: string[];
  addItem: (item: T) => void;
  updateItem: (id: string, updates: Partial<T>) => void;
  deleteItem: (id: string) => void;
  selectItem: (id: string) => void;
  clearSelection: () => void;
}

/**
 *
 */
export interface LegacyFlowDiagramHook<T> {
  nodes: T[];
  edges: unknown[];
  selectedNodeId: string | null;
  onNodeAdd: (node: T) => void;
  onNodeUpdate: (id: string, updates: Partial<T>) => void;
  onNodeDelete: (id: string) => void;
  onNodeSelect: (id: string) => void;
}

/**
 * Migration adapter for useCanvasScene hook
 * Provides backward compatibility while transitioning to useGenericCanvas
 */
export function useCanvasSceneMigrated<T extends BaseItem>(
  initialItems: T[] = [],
  viewModes: ViewModeDefinition<T>[] = []
): LegacyCanvasSceneHook<T> {
  const [items, setItems] = React.useState<T[]>(initialItems);

  const canvasResult = useGenericCanvas<T>({
    items,
    onItemsChange: setItems,
    capabilities: {
      dragDrop: true,
      selection: true,
      keyboard: true,
      persistence: true,
      undo: true,
    },
    viewModes,
  });

  // Adapt the new API to match the legacy interface
  return {
    items: canvasResult.filteredItems,
    selectedItems: canvasResult.selectedItems,
    addItem: (item: T) => canvasResult.actions.createItem(item),
    updateItem: (id: string, updates: Partial<T>) =>
      canvasResult.actions.updateItem(id, updates),
    deleteItem: (id: string) => canvasResult.actions.deleteItem(id),
    selectItem: (id: string) => canvasResult.actions.selectItem(id),
    clearSelection: () => canvasResult.actions.clearSelection(),
  };
}

/**
 * Migration adapter for useFlowDiagram hook
 * Provides backward compatibility for flow diagram specific operations
 */
export function useFlowDiagramMigrated<T extends BaseItem>(
  initialNodes: T[] = [],
  initialEdges: unknown[] = []
): LegacyFlowDiagramHook<T> {
  const [nodes, setNodes] = React.useState<T[]>(initialNodes);
  const [edges] = React.useState<unknown[]>(initialEdges);

  const canvasResult = useGenericCanvas<T>({
    items: nodes,
    onItemsChange: setNodes,
    viewModes: [],
    capabilities: {
      dragDrop: true,
      selection: true,
      keyboard: true,
    },
  });

  const selectedItems = canvasResult.selectedItems;

  return {
    nodes: canvasResult.filteredItems,
    edges, // For now, maintain edges separately
    selectedNodeId: selectedItems.length > 0 ? selectedItems[0] : null,
    onNodeAdd: (node: T) => canvasResult.actions.createItem(node),
    onNodeUpdate: (id: string, updates: Partial<T>) =>
      canvasResult.actions.updateItem(id, updates),
    onNodeDelete: (id: string) => canvasResult.actions.deleteItem(id),
    onNodeSelect: (id: string) => canvasResult.actions.selectItem(id),
  };
}

/**
 * Migration helper for keyboard shortcuts
 */
export function useKeyboardShortcutsMigration(
  shortcuts: Record<string, () => void>
) {
  React.useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const key = `${event.ctrlKey ? 'Ctrl+' : ''}${event.shiftKey ? 'Shift+' : ''}${event.key}`;
      const handler = shortcuts[key];
      if (handler) {
        event.preventDefault();
        handler();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [shortcuts]);
}

/**
 * Migration strategies for different canvas types
 */
export class CanvasHookMigration {
  /**
   * Strategy 1: Gradual migration with backward compatibility
   * Keep legacy hooks working while introducing new generic hooks
   */
  static gradualMigration<T extends BaseItem>(
    legacyHook: unknown,
    newGenericCanvasConfig: unknown
  ) {
    const isNewImplementation = React.useRef(false);

    // Feature flag or environment variable to control migration
    const useNewImplementation =
      process.env.NODE_ENV === 'development' || isNewImplementation.current;

    if (useNewImplementation) {
      return useGenericCanvas<T>(newGenericCanvasConfig);
    } else {
      return legacyHook;
    }
  }

  /**
   * Strategy 2: Progressive enhancement
   * Add new features to existing implementations
   */
  static addPersistence(existingHook: unknown, key: string = 'canvas-state') {
    const enhanced = { ...existingHook };

    // Add save method
    enhanced.saveState = () => {
      localStorage.setItem(key, JSON.stringify(enhanced));
    };

    // Add load method
    enhanced.loadState = () => {
      const savedState = localStorage.getItem(key);
      if (savedState) {
        return JSON.parse(savedState);
      }
      return null;
    };

    return enhanced;
  }

  /**
   * Strategy 3: Migration configuration mapper
   * Map legacy configuration to generic configuration format
   */
  static mapLegacyConfig(legacyConfig: unknown) {
    return {
      capabilities: {
        dragDrop: legacyConfig.enableDragDrop ?? true,
        selection: legacyConfig.enableSelection ?? true,
        keyboard: legacyConfig.enableKeyboard ?? true,
        persistence: legacyConfig.enablePersistence ?? false,
        undo: legacyConfig.enableUndo ?? false,
      },
      viewModes: legacyConfig.viewModes || [],
      plugins: legacyConfig.plugins || [],
    };
  }
}

// Export migration utilities
export const MigrationStrategies = CanvasHookMigration;
