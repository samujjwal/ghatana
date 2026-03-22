/**
 * Canvas Workspace Components
 * 
 * Task guidance and spatial navigation components for Canvas-First UX.
 * 
 * @doc.type module
 * @doc.purpose Canvas workspace UI components
 * @doc.layer product
 */

/**
 * State Management
 */
export {
    activePersonaAtom,
    isAIModalOpenAtom,
    isProjectSwitcherOpenAtom,
    isInspectorOpenAtom,
    selectedArtifactAtom,
    isSearchOpenAtom,
    selectedNodesAtom,
    quickCreateMenuPositionAtom,
    draggedTemplateAtom,
    copiedNodesAtom,
    nodesAtom,
    edgesAtom,
    suppressGeneratedSyncAtom,
    cameraAtom,
    ghostNodesAtom,
    canvasHistoryAtom,
    historyIndexAtom,
    canUndoAtom,
    canRedoAtom,
    selectedNodeAtom,
    getNodeByIdAtom,
    getEdgeByIdAtom,
    updateNodesAtom,
    updateEdgesAtom,
    updateViewportAtom,
    addNodeAtom,
    removeNodeAtom,
    addEdgeAtom,
    removeEdgeAtom,
    clearSelectionAtom,
    undoAtom,
    redoAtom,
    pushHistoryAtom,
    canvasInteractionModeAtom,
    sketchToolAtom,
    sketchColorAtom,
    sketchStrokeWidthAtom,
    diagramTypeAtom,
    diagramContentAtom,
    diagramZoomAtom,
    showDiagramEditorAtom,
    isCommandPaletteOpenAtom,
    alignmentGuidesAtom,
    prefersReducedMotionAtom,
    prefersDarkModeAtom,
    canvasAnnouncementAtom,
    PHASE_ZONE_CENTERS,
    type CanvasHistoryEntry,
    // New architectural atoms
    cameraAtom,
    cameraZoomAtom,
    commandRegistryAtom,
    sortedCommandsAtom,
    registerCommandsAtom,
    unregisterCommandsAtom,
    nodePositionsAtom,
    setNodePositionAtom,
    codeAssociationsAtom,
    MAX_HISTORY_SIZE,
    visibleViewportAtom,
    visibleNodeIdsAtom,
    nodeByIdAtomFamily,
    isNodeSelectedAtomFamily,
    nodePositionAtomFamily,
    type CanvasCommandAction,
    type NodePosition,
    type CodeLink,
} from './canvasAtoms';

/**
 * Command Pattern (undo/redo infrastructure)
 */
export {
    commandHistoryAtom,
    canUndoCommandAtom,
    canRedoCommandAtom,
    executeCommandAtom,
    undoCommandAtom,
    redoCommandAtom,
    executeBatchAtom,
    AddNodeCommand,
    RemoveNodesCommand,
    MoveNodesCommand,
    AlignNodesCommand,
    UpdateNodeDataCommand,
    AddEdgeCommand,
    RemoveEdgeCommand,
    PasteNodesCommand,
    type CanvasCommand,
    type CommandHistoryState,
} from './canvasCommands';

/**
 * UI Components
 */
export { PhaseProgressPill, type PhaseProgressPillProps, type GateCriterion } from './PhaseProgressPill';
export { NextBestTaskCard, type NextBestTaskProps } from './NextBestTaskCard';
export { PersonaBadge, StatusBadge, PERSONA_ICONS, type PersonaBadgeProps, type StatusBadgeProps } from './PersonaBadge';
export { SpatialZones, useZoneBoundaries, getZonePlacementPosition, type SpatialZonesProps, type PhaseZone } from './SpatialZones';
export { PersonaFilterToolbar, type PersonaFilterProps, type PersonaFilterData } from './PersonaFilterToolbar';
export { AIAssistantModal, type AIAssistantModalProps, type AISuggestion } from './AIAssistantModal';
export { ArtifactPalette, type ArtifactPaletteProps, type ArtifactTemplate, type ArtifactType } from './ArtifactPalette';
export { QuickCreateMenu, type QuickCreateMenuProps } from './QuickCreateMenu';
export { InspectorPanel, type InspectorPanelProps, type InspectorArtifact } from './InspectorPanel';
export { PresenceIndicator, CanvasPresence, type PresenceIndicatorProps, type CanvasPresenceProps, type PresenceUser } from './PresenceIndicator';

/**
 * Zero State & Onboarding
 */
export { GhostNodes, type GhostNodesProps, type GhostNodeTemplate } from './GhostNodes';

/**
 * View Mode Filtering
 */
export { ViewModeSelector, ViewModeSelectorCompact, type ViewModeSelectorProps } from './ViewModeSelector';
