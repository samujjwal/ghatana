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
  // Platform hybrid atoms
  hybridCanvasStateAtom,
  renderingModeAtom,
  activeLayerAtom,

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

  // Interaction mode & sketch/diagram
  canvasInteractionModeAtom,
  sketchToolAtom,
  sketchColorAtom,
  sketchStrokeWidthAtom,
  diagramTypeAtom,
  diagramContentAtom,
  diagramZoomAtom,
  showDiagramEditorAtom,

  // Command registry
  commandRegistryAtom,
  sortedCommandsAtom,
  registerCommandsAtom,
  unregisterCommandsAtom,

  // Workspace UI
  activePersonaAtom,
  isAIModalOpenAtom,
  isProjectSwitcherOpenAtom,
  isInspectorOpenAtom,
  isCommandPaletteOpenAtom,
  isSearchOpenAtom,

  // Accessibility
  prefersReducedMotionAtom,
  prefersDarkModeAtom,
  canvasAnnouncementAtom,

  // Alignment and constants
  alignmentGuidesAtom,
  PHASE_ZONE_CENTERS,
  MAX_HISTORY_SIZE,

  // Lifecycle & tasks
  lifecyclePhaseAtom,
  phaseProgressAtom,
  canvasTasksAtom,
  tasksByPhaseAtom,
  blockedTasksAtom,
  nextBestTaskAtom,

  // AI & validation
  aiSuggestionsAtom,
  validationIssuesAtom,
  validationScoreAtom,

  // Collaboration presence
  collaboratorsAtom,
  onlineCollaboratorsAtom,

  // Project metadata
  canvasProjectMetadataAtom,

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
  CanvasInteractionMode,
  SketchTool,
  DiagramType,
  CanvasCommandAction,
  LifecyclePhase,
  PhaseProgress,
  TaskStatus,
  TaskPriority,
  CanvasTask,
  AISuggestion,
  ValidationIssue,
  Collaborator,
  CanvasProjectMetadata,
} from '../types/canvas-document';

export { cameraAtom } from './canvas-atoms';
