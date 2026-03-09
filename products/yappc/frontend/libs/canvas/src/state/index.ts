/**
 * Canvas State Management
 *
 * Unified state system using Jotai atoms for the Canvas library.
 * Provides type-safe, performant state management with atomic updates.
 *
 * @module canvas/state
 */

// Core atoms for canvas state
export {
  // Document state
  canvasDocumentAtom,
  updateDocumentAtom,

  // Element management
  addElementAtom,
  updateElementAtom,
  removeElementAtom,
  batchUpdateElementsAtom,

  // Selection state
  canvasSelectionAtom,
  updateSelectionAtom,
  clearSelectionAtom,

  // Viewport state
  canvasViewportAtom,
  updateViewportAtom,
  panViewportAtom,
  zoomViewportAtom,

  // History state
  canvasHistoryAtom,
  addHistoryEntryAtom,
  undoAtom,
  redoAtom,
  clearHistoryAtom,

  // UI state
  canvasUIStateAtom,
  updateUIStateAtom,
  resetUIStateAtom,

  // Performance tracking
  canvasPerformanceAtom,
  updatePerformanceAtom,

  // Collaboration state
  canvasCollaborationAtom,

  // Derived computed atoms
  canvasElementsArrayAtom,
  selectedElementsAtom,
  canvasCapabilitiesAtom,
  hasUnsavedChangesAtom,
  canRedoAtom,
  canUndoAtom,
  boundingBoxAtom,
} from './atoms';

// Batch update utilities
export {
  useBatchUpdates,
  useDebouncedAutosave,
  batchAtomUpdates,
  useWorkerOffload,
  batchUpdateStateAtom,
  DEFAULT_BATCH_CONFIG,
  type BatchConfig,
  type WorkerConfig,
} from './batchUpdates';

// Types for state management
export type {
  CanvasDocument,
  CanvasElement,
  CanvasSelection,
  CanvasViewport,
  CanvasHistoryEntry,
  CanvasUIState,
  CanvasPerformanceMetrics,
} from '../types/canvas-document';
