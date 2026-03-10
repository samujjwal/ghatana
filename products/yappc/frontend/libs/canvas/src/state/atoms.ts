/**
 * Canvas State Management - Unified Atoms
 *
 * YAPPC-specific canvas state management layered on top of the platform
 * canvas library (`@ghatana/canvas`). Product-specific atoms
 * (collaboration, performance, UI state) are defined here; core atoms
 * (viewport, selection, elements, nodes, edges) are re-exported from
 * the platform hybrid canvas state.
 *
 * **Canonical core atoms come from `@ghatana/canvas` (platform/typescript/canvas).**
 * Do NOT redefine viewport/selection/element atoms here — import them.
 *
 * @module canvas/state/atoms
 * @doc.type module
 * @doc.purpose YAPPC canvas state layered on platform canvas atoms
 * @doc.layer product
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import { StateManager } from '@ghatana/yappc-ui';

// Re-export platform canonical canvas atoms so consumers don't need two imports
export {
  hybridCanvasStateAtom,
  renderingModeAtom,
  activeLayerAtom,
  cameraAtom,
  selectionAtom,
  toolAtom,
  readOnlyAtom,
  gridAtom,
  elementsAtom,
  nodesAtom,
  edgesAtom,
} from '@ghatana/canvas/hybrid/state';

import type {
  CanvasDocument,
  CanvasElement,
  CanvasSelection,
  CanvasViewport,
  CanvasHistoryEntry,
  CanvasUIState,
  CanvasPerformanceMetrics,
} from '../types/canvas-document';

// Core document state - registered with StateManager
export const canvasDocumentAtom = StateManager.createPersistentAtom<CanvasDocument>(
  'canvas:document',
  {
  id: 'default',
  version: '1.0.0',
  title: 'Untitled Canvas',
  viewport: {
    center: { x: 0, y: 0 },
    zoom: 1.0,
  },
  elements: {},
  elementOrder: [],
  metadata: {},
  capabilities: {
    canEdit: true,
    canZoom: true,
    canPan: true,
    canSelect: true,
    canUndo: true,
    canRedo: true,
    canExport: true,
    canImport: true,
    canCollaborate: false,
    canPersist: true,
    allowedElementTypes: ['node', 'edge', 'group'],
  },
  createdAt: new Date(),
  updatedAt: new Date(),
  },
  {
    description: 'Canvas document state with persistence',
    storage: 'local',
  }
);

// Selection state - registered with StateManager
export const canvasSelectionAtom = StateManager.createAtom<CanvasSelection>(
  'canvas:selection',
  { selectedIds: [] },
  'Canvas element selection state'
);

// Viewport state - registered with StateManager
export const canvasViewportAtom = StateManager.createAtom<CanvasViewport>(
  'canvas:viewport',
  {
    center: { x: 0, y: 0 },
    zoom: 1.0,
    bounds: { x: 0, y: 0, width: 1200, height: 800 },
  },
  'Canvas viewport state (center, zoom, bounds)'
);

// History state for undo/redo - registered with StateManager
export const canvasHistoryAtom = StateManager.createPersistentAtom(
  'canvas:history',
  {
    entries: [] as CanvasHistoryEntry[],
    currentIndex: -1,
    maxEntries: 50,
  },
  {
    description: 'Canvas action history for undo/redo',
    storage: 'local',
  }
);

// UI interaction state - registered with StateManager
export const canvasUIStateAtom = StateManager.createAtom<CanvasUIState>(
  'canvas:uiState',
  {
    isDragging: false,
    isSelecting: false,
    isPanning: false,
    isLoading: false,
    mode: 'select',
  },
  'Canvas UI interaction state'
);

// Performance tracking - registered with StateManager
export const canvasPerformanceAtom = StateManager.createAtom<CanvasPerformanceMetrics>(
  'canvas:performance',
  {
    renderTime: 0,
    lastUpdate: new Date(),
    fps: 60,
  },
  'Canvas performance metrics'
);

// Collaboration state - registered with StateManager
export const canvasCollaborationAtom = StateManager.createAtom(
  'canvas:collaboration',
  {
    isEnabled: false,
    connectedUsers: [],
    events: [],
  },
  'Canvas collaboration state'
);

// Derived atoms for computed values

// Get all elements as array (sorted by element order)
export const canvasElementsArrayAtom = atom<CanvasElement[]>((get) => {
  const document = get(canvasDocumentAtom);
  return document.elementOrder
    .map((id) => document.elements[id])
    .filter(Boolean);
});

// Get selected elements
export const selectedElementsAtom = atom<CanvasElement[]>((get) => {
  const document = get(canvasDocumentAtom);
  const selection = get(canvasSelectionAtom);
  return selection.selectedIds
    .map((id) => document.elements[id])
    .filter(Boolean);
});

// Get document capabilities
export const canvasCapabilitiesAtom = atom<unknown>(
  (get) => get(canvasDocumentAtom).capabilities
);

// Check if document has unsaved changes
export const hasUnsavedChangesAtom = atom<boolean>((get) => {
  const document = get(canvasDocumentAtom);
  const history = get(canvasHistoryAtom);
  return (
    history.entries.length > 0 &&
    document.updatedAt >
      (history.entries[history.currentIndex]?.timestamp || document.createdAt)
  );
});

// Action atoms for state mutations

// Update document properties
export const updateDocumentAtom = atom(
  null,
  (get, set, update: Partial<CanvasDocument>) => {
    const current = get(canvasDocumentAtom);
    const updated = {
      ...current,
      ...update,
      updatedAt: new Date(),
    };
    set(canvasDocumentAtom, updated);
  }
);

// Add or update element
export const updateElementAtom = atom(
  null,
  (
    get,
    set,
    elementUpdate: { id: string; changes: Partial<CanvasElement> }
  ) => {
    const document = get(canvasDocumentAtom);
    const element = document.elements[elementUpdate.id];

    if (!element) return;

    const updatedElement = {
      ...element,
      ...elementUpdate.changes,
      updatedAt: new Date(),
    };

    const updatedDocument = {
      ...document,
      elements: {
        ...document.elements,
        [elementUpdate.id]: updatedElement,
      },
      updatedAt: new Date(),
    };

    set(canvasDocumentAtom, updatedDocument);
  }
);

// Add new element
export const addElementAtom = atom(null, (get, set, element: CanvasElement) => {
  const document = get(canvasDocumentAtom);

  const updatedDocument = {
    ...document,
    elements: {
      ...document.elements,
      [element.id]: element,
    },
    elementOrder: [...document.elementOrder, element.id],
    updatedAt: new Date(),
  };

  set(canvasDocumentAtom, updatedDocument);
});

// Remove element
export const removeElementAtom = atom(null, (get, set, elementId: string) => {
  const document = get(canvasDocumentAtom);

  const { [elementId]: removed, ...remainingElements } = document.elements;

  const updatedDocument = {
    ...document,
    elements: remainingElements,
    elementOrder: document.elementOrder.filter((id) => id !== elementId),
    updatedAt: new Date(),
  };

  set(canvasDocumentAtom, updatedDocument);

  // Also clear selection if this element was selected
  const selection = get(canvasSelectionAtom);
  if (selection.selectedIds.includes(elementId)) {
    set(canvasSelectionAtom, {
      ...selection,
      selectedIds: selection.selectedIds.filter((id) => id !== elementId),
    });
  }
});

// Selection management
export const updateSelectionAtom = atom(
  null,
  (get, set, update: Partial<CanvasSelection>) => {
    const current = get(canvasSelectionAtom);
    set(canvasSelectionAtom, { ...current, ...update });
  }
);

// Viewport management
export const updateViewportAtom = atom(
  null,
  (get, set, update: Partial<CanvasViewport>) => {
    const current = get(canvasViewportAtom);
    set(canvasViewportAtom, { ...current, ...update });
  }
);

// History management
export const addHistoryEntryAtom = atom(
  null,
  (get, set, entry: Omit<CanvasHistoryEntry, 'id' | 'timestamp'>) => {
    const history = get(canvasHistoryAtom);
    const newEntry: CanvasHistoryEntry = {
      ...entry,
      id: crypto.randomUUID(),
      timestamp: new Date(),
    };

    // Remove any entries after current index (when adding after undo)
    const entries = history.entries.slice(0, history.currentIndex + 1);
    entries.push(newEntry);

    // Keep only max entries
    const trimmedEntries = entries.slice(-history.maxEntries);

    set(canvasHistoryAtom, {
      ...history,
      entries: trimmedEntries,
      currentIndex: trimmedEntries.length - 1,
    });
  }
);

// Undo operation
export const undoAtom = atom(null, (get, set) => {
  const history = get(canvasHistoryAtom);

  if (history.currentIndex >= 0) {
    const entry = history.entries[history.currentIndex];

    // Apply the before state
    if (entry.beforeState) {
      set(updateDocumentAtom, entry.beforeState);
    }

    // Update history index
    set(canvasHistoryAtom, {
      ...history,
      currentIndex: history.currentIndex - 1,
    });
  }
});

// Redo operation
export const redoAtom = atom(null, (get, set) => {
  const history = get(canvasHistoryAtom);

  if (history.currentIndex < history.entries.length - 1) {
    const nextIndex = history.currentIndex + 1;
    const entry = history.entries[nextIndex];

    // Apply the after state
    if (entry.afterState) {
      set(updateDocumentAtom, entry.afterState);
    }

    // Update history index
    set(canvasHistoryAtom, {
      ...history,
      currentIndex: nextIndex,
    });
  }
});

// Reset UI state
export const resetUIStateAtom = atom(null, (_get, set) => {
  set(canvasUIStateAtom, {
    isDragging: false,
    isSelecting: false,
    isPanning: false,
    isLoading: false,
    mode: 'select',
  });
});

// Batch update multiple elements (for performance)
export const batchUpdateElementsAtom = atom(
  null,
  (
    get,
    set,
    updates: Array<{ id: string; changes: Partial<CanvasElement> }>
  ) => {
    const document = get(canvasDocumentAtom);

    const updatedElements = { ...document.elements };
    updates.forEach(({ id, changes }) => {
      const element = updatedElements[id];
      if (element) {
        updatedElements[id] = {
          ...element,
          ...changes,
          updatedAt: new Date(),
        };
      }
    });

    const updatedDocument = {
      ...document,
      elements: updatedElements,
      updatedAt: new Date(),
    };

    set(canvasDocumentAtom, updatedDocument);
  }
);

// Additional selection actions
export const clearSelectionAtom = atom(null, (_get, set) => {
  set(canvasSelectionAtom, {
    selectedIds: [],
  });
});

// Additional viewport actions
export const panViewportAtom = atom(
  null,
  (get, set, delta: { x: number; y: number }) => {
    const viewport = get(canvasViewportAtom);
    set(canvasViewportAtom, {
      ...viewport,
      center: {
        x: viewport.center.x + delta.x,
        y: viewport.center.y + delta.y,
      },
    });
  }
);

export const zoomViewportAtom = atom(
  null,
  (get, set, zoomDelta: number, center?: { x: number; y: number }) => {
    const viewport = get(canvasViewportAtom);
    const newZoom = Math.max(0.1, Math.min(5.0, viewport.zoom + zoomDelta));

    let newCenter = viewport.center;

    // Zoom to center point if provided
    if (center) {
      const zoomRatio = newZoom / viewport.zoom;
      newCenter = {
        x: center.x - (center.x - viewport.center.x) * zoomRatio,
        y: center.y - (center.y - viewport.center.y) * zoomRatio,
      };
    }

    set(canvasViewportAtom, {
      ...viewport,
      zoom: newZoom,
      center: newCenter,
    });
  }
);

// Additional history actions
export const clearHistoryAtom = atom(null, (get, set) => {
  const history = get(canvasHistoryAtom);
  set(canvasHistoryAtom, {
    ...history,
    entries: [],
    currentIndex: -1,
  });
});

// UI state actions
export const updateUIStateAtom = atom(
  null,
  (get, set, update: Partial<CanvasUIState>) => {
    const current = get(canvasUIStateAtom);
    set(canvasUIStateAtom, {
      ...current,
      ...update,
    });
  }
);

// Performance tracking actions
export const updatePerformanceAtom = atom(
  null,
  (get, set, metrics: Partial<CanvasPerformanceMetrics>) => {
    const current = get(canvasPerformanceAtom);
    set(canvasPerformanceAtom, {
      ...current,
      ...metrics,
    });
  }
);

// Derived atoms for capabilities
export const canUndoAtom = atom((get) => {
  const history = get(canvasHistoryAtom);
  return history.currentIndex >= 0;
});

export const canRedoAtom = atom((get) => {
  const history = get(canvasHistoryAtom);
  return history.currentIndex < history.entries.length - 1;
});

// Bounding box calculation for all selected elements
export const boundingBoxAtom = atom((get) => {
  const selectedElements = get(selectedElementsAtom);

  if (selectedElements.length === 0) {
    return null;
  }

  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;

  selectedElements.forEach((element) => {
    if (element.type === 'node') {
      const node = element as CanvasElement & { bounds: { x: number; y: number; width: number; height: number } };
      minX = Math.min(minX, node.bounds.x);
      minY = Math.min(minY, node.bounds.y);
      maxX = Math.max(maxX, node.bounds.x + node.bounds.width);
      maxY = Math.max(maxY, node.bounds.y + node.bounds.height);
    }
  });

  return {
    x: minX,
    y: minY,
    width: maxX - minX,
    height: maxY - minY,
  };
});

// ============================================================================
// Extended atoms — merged from workspace/canvasAtoms.ts & unifiedCanvasAtom.ts
// These provide a single source of truth for ALL canvas state concerns.
// ============================================================================

// --- Interaction Mode & Sketch/Diagram (from legacy canvasAtoms) ---

export type CanvasInteractionMode = 'navigate' | 'sketch' | 'code' | 'diagram';
export const canvasInteractionModeAtom = atom<CanvasInteractionMode>('navigate');

export type SketchTool = 'pen' | 'rect' | 'ellipse' | 'eraser';
export const sketchToolAtom = atom<SketchTool>('pen');
export const sketchColorAtom = atom<string>('#000000');
export const sketchStrokeWidthAtom = atom<number>(2);

export type DiagramType = 'mermaid' | 'excalidraw' | 'flowchart' | 'sequence' | 'class';
export const diagramTypeAtom = atom<DiagramType>('mermaid');
export const diagramContentAtom = atom<string>('graph TD\n  A[Start] --> B[End]');
export const diagramZoomAtom = atom<number>(1);
export const showDiagramEditorAtom = atom<boolean>(false);

// --- Command Registry (from legacy canvasAtoms) ---

export interface CanvasCommandAction {
  id: string;
  label: string;
  shortcut?: string;
  group?: string;
  icon?: unknown;
  onExecute: () => void;
}

export const commandRegistryAtom = atom<Map<string, CanvasCommandAction>>(new Map());

export const sortedCommandsAtom = atom<CanvasCommandAction[]>((get) =>
  [...get(commandRegistryAtom).values()].sort((a, b) => {
    const ga = a.group ?? '';
    const gb = b.group ?? '';
    return ga !== gb ? ga.localeCompare(gb) : a.label.localeCompare(b.label);
  }),
);

export const registerCommandsAtom = atom(
  null,
  (get, set, commands: CanvasCommandAction[]) => {
    const prev = get(commandRegistryAtom);
    const next = new Map(prev);
    for (const cmd of commands) next.set(cmd.id, cmd);
    set(commandRegistryAtom, next);
  }
);

export const unregisterCommandsAtom = atom(
  null,
  (get, set, ids: string[]) => {
    const prev = get(commandRegistryAtom);
    const next = new Map(prev);
    for (const id of ids) next.delete(id);
    set(commandRegistryAtom, next);
  }
);

// --- Workspace UI atoms (from legacy canvasAtoms) ---

export const activePersonaAtom = atom<string | null>(null);
export const isAIModalOpenAtom = atom(false);
export const isProjectSwitcherOpenAtom = atom(false);
export const isInspectorOpenAtom = atom(false);
export const isCommandPaletteOpenAtom = atom(false);
export const isSearchOpenAtom = atom(false);

// --- Accessibility atoms (from legacy canvasAtoms) ---

export const prefersReducedMotionAtom = atom<boolean>(
  typeof globalThis.window !== 'undefined'
    ? globalThis.window.matchMedia('(prefers-reduced-motion: reduce)').matches
    : false
);

export const prefersDarkModeAtom = atom<boolean>(
  typeof globalThis.window !== 'undefined'
    ? globalThis.document.documentElement.classList.contains('dark') ||
      globalThis.window.matchMedia('(prefers-color-scheme: dark)').matches
    : false
);

export const canvasAnnouncementAtom = atom<string>('');

// --- Alignment guides (from legacy canvasAtoms) ---

export const alignmentGuidesAtom = atom<{ vertical: number | null; horizontal: number | null }>(
  { vertical: null, horizontal: null }
);

// --- Phase zone centers (from legacy canvasAtoms) ---

export const PHASE_ZONE_CENTERS: Record<string, { x: number; y: number }> = {
  INTENT: { x: 400, y: 300 },
  SHAPE: { x: 1250, y: 300 },
  VALIDATE: { x: 2200, y: 300 },
  GENERATE: { x: 3100, y: 300 },
  RUN: { x: 3900, y: 300 },
  OBSERVE: { x: 4550, y: 300 },
  IMPROVE: { x: 5300, y: 300 },
};

export const MAX_HISTORY_SIZE = 100;

// --- Lifecycle & Task atoms (from unifiedCanvasAtom) ---

export type LifecyclePhase = 'design' | 'backlog' | 'build' | 'test' | 'deploy' | 'monitor';

export interface PhaseProgress {
  phase: LifecyclePhase;
  percentage: number;
  status: 'not-started' | 'in-progress' | 'completed' | 'blocked';
}

export const lifecyclePhaseAtom = atom<LifecyclePhase>('design');
export const phaseProgressAtom = atom<PhaseProgress[]>([]);

export type TaskStatus = 'todo' | 'in-progress' | 'done' | 'blocked';
export type TaskPriority = 'low' | 'medium' | 'high' | 'critical';

export interface CanvasTask {
  id: string;
  title: string;
  status: TaskStatus;
  priority: TaskPriority;
  phase: LifecyclePhase;
  assignee?: string;
}

export const canvasTasksAtom = atom<CanvasTask[]>([]);

export const tasksByPhaseAtom = atom((get) => {
  const tasks = get(canvasTasksAtom);
  const phase = get(lifecyclePhaseAtom);
  return tasks.filter((t) => t.phase === phase);
});

export const blockedTasksAtom = atom((get) => {
  return get(canvasTasksAtom).filter((t) => t.status === 'blocked');
});

export const nextBestTaskAtom = atom((get) => {
  const tasks = get(tasksByPhaseAtom);
  const priorityOrder: TaskPriority[] = ['critical', 'high', 'medium', 'low'];
  const todoTasks = tasks.filter((t) => t.status === 'todo');
  for (const priority of priorityOrder) {
    const match = todoTasks.find((t) => t.priority === priority);
    if (match) return match;
  }
  return null;
});

// --- AI suggestion atoms (from unifiedCanvasAtom) ---

export interface AISuggestion {
  id: string;
  type: 'node' | 'connection' | 'layout' | 'content';
  confidence: number;
  description: string;
  data: Record<string, unknown>;
}

export interface ValidationIssue {
  id: string;
  severity: 'error' | 'warning' | 'info';
  message: string;
  elementId?: string;
}

export const aiSuggestionsAtom = atom<AISuggestion[]>([]);
export const validationIssuesAtom = atom<ValidationIssue[]>([]);
export const validationScoreAtom = atom<number>((get) => {
  const issues = get(validationIssuesAtom);
  const errorCount = issues.filter((i) => i.severity === 'error').length;
  const warningCount = issues.filter((i) => i.severity === 'warning').length;
  return Math.max(0, 100 - errorCount * 10 - warningCount * 3);
});

// --- Collaboration extended state (from unifiedCanvasAtom) ---

export interface Collaborator {
  id: string;
  name: string;
  avatar?: string;
  color: string;
  cursor?: { x: number; y: number };
  isOnline: boolean;
}

export const collaboratorsAtom = atom<Collaborator[]>([]);
export const onlineCollaboratorsAtom = atom((get) =>
  get(collaboratorsAtom).filter((c) => c.isOnline)
);

// --- Project metadata atom (from unifiedCanvasAtom) ---

export interface CanvasProjectMetadata {
  id: string;
  name: string;
  description?: string;
  framework?: string;
  buildTool?: string;
}

export const canvasProjectMetadataAtom = atom<CanvasProjectMetadata | null>(null);
