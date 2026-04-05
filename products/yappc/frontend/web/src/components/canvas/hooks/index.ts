/**
 * Canvas Hooks
 * 
 * Central export for all canvas-related hooks.
 * 
 * @doc.type module
 * @doc.purpose Canvas hook exports
 * @doc.layer product
 */

// Unified keyboard shortcuts (replaces useCanvasKeyboard, useCanvasNavigation)
export { useCanvasShortcuts, type CanvasShortcutsConfig } from './useCanvasShortcuts';

// CRUD / mutation handlers
export { useCanvasHandlers, type UseCanvasHandlersConfig } from './useCanvasHandlers';

// Drag and drop
export { useCanvasDragDrop, type UseCanvasDragDropConfig } from './useCanvasDragDrop';

// Zoom / camera / navigation
export { useCanvasZoom, type UseCanvasZoomConfig } from './useCanvasZoom';

// Accessibility (node traversal, announcements)
export { useCanvasAccessibility } from './useCanvasAccessibility';

// Performance metrics (real FPS)
export { usePerformanceMetrics, type PerformanceMetrics } from './usePerformanceMetrics';

// Batched code associations (replaces per-node useCodeAssociations)
export { useCodeAssociationsBatch, type UseCodeAssociationsBatchOptions } from './useCodeAssociationsBatch';

// Node position persistence (localStorage, survives server re-sync)
export { useNodePositions } from './useNodePositions';

// Phase navigation
export {
    usePhaseNavigation,
    PHASE_ZONE_POSITIONS,
    PHASE_ORDER,
    type PhaseNavigationState,
    type UsePhaseNavigationResult,
} from './usePhaseNavigation';

// Computed view (combinatorial filtering)
export {
    useComputedView,
    userRoleAtom,
    userIdAtom,
    viewModeAtom,
    myTasksFilterAtom,
    myTasksAtom,
    type UserRole,
    type ViewMode,
    type ViewModeConfig,
    type ComputedViewContext,
    type ComputedViewResult,
} from './useComputedView';

// Deprecated hooks (useCanvasKeyboard, useCanvasHistory, useCanvasSelection,
// useCanvasPersistence, useCanvasNavigation) have been removed.
// Use useCanvasShortcuts, Jotai canvasHistoryAtom, selectedNodesAtom,
// service-layer persistence, and useCanvasZoom respectively.
