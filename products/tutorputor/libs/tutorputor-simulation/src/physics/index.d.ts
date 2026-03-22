/**
 * @ghatana/tutorputor-physics-simulation
 *
 * TutorPutor physics simulation library with:
 * - Konva-based rendering for physics entities
 * - Jotai state management for undo/redo
 * - Zod validation for manifests
 * - Drag-and-drop toolbox components
 * - Import/Export serialization
 *
 * @doc.type module
 * @doc.purpose Main library export
 * @doc.layer core
 * @doc.pattern Barrel
 *
 * @example
 * ```tsx
 * import {
 *   useSimulation,
 *   SimulationCanvas,
 *   EntityToolbox,
 *   PhysicsPropertyPanel,
 *   TOOLBOX_ITEMS,
 * } from '@ghatana/physics-simulation';
 *
 * function MySimulation() {
 *   const sim = useSimulation();
 *   return <SimulationCanvas entities={sim.entities} ... />;
 * }
 * ```
 */
export { EntityType, type PhysicsProperties, type EntityAppearance, type PhysicsEntity, type PhysicsConfig, type ToolboxItem, type EntitySelection, } from "./types";
export { DEFAULT_PHYSICS, ENTITY_DEFAULTS, TOOLBOX_ITEMS, DEFAULT_PHYSICS_CONFIG, createEntity, physicsPropertiesSchema, entityAppearanceSchema, physicsEntitySchema, physicsConfigSchema, validateEntity, validatePhysicsConfig, isValidEntityType, } from "./entities";
export { type PhysicsSimulationManifest, MANIFEST_VERSION, physicsManifestSchema, createManifest, exportManifestToJSON, downloadManifest, parseManifest, migrateManifest, readManifestFromFile, } from "./serialization";
export { KonvaEntityRenderer, type KonvaEntityRendererProps, } from "./rendering";
export { simulationEntitiesAtom, simulationPhysicsConfigAtom, simulationSelectionAtom, simulationHistoryAtom, simulationHistoryIndexAtom, simulationPreviewModeAtom, selectedEntityAtom, canUndoAtom, canRedoAtom, historyStatusAtom, addHistoryEntryAtom, undoAtom, redoAtom, addEntityAtom, updateEntityAtom, deleteEntityAtom, clearAllEntitiesAtom, loadEntitiesAtom, type SimulationHistoryEntry, } from "./state";
export { useSimulation, useSimulationKeyboardShortcuts, type UseSimulationResult, } from "./hooks";
export { DraggableToolboxItem, EntityToolbox, PhysicsPropertyPanel, PhysicsConfigPanel, SimulationCanvas, SimulationToolbar, type DraggableToolboxItemProps, type EntityToolboxProps, type EntityDropPayload, type PhysicsPropertyPanelProps, type PhysicsConfigPanelProps, type SimulationCanvasProps, type SimulationToolbarProps, } from "./components";
export { type RenderingBackend, type RenderableElement, type RenderingCapabilities, type IRenderingAdapter, type RenderingAdapterFactory, KONVA_CAPABILITIES, REACT_FLOW_CAPABILITIES, WEBGL_CAPABILITIES, renderingAdapterRegistry, registerRenderingAdapter, getRenderingAdapter, selectBestAdapter, KonvaRenderingAdapter, } from "./adapters";
export { usePhysicsCollaboration, type CollaborationUser, type PhysicsCollaborationState, type UsePhysicsCollaborationOptions, UserCursor, CollaborationCursors, CollaborationStatusBar, type UserCursorProps, type CollaborationCursorsProps, type CollaborationStatusBarProps, } from "./collaboration";
//# sourceMappingURL=index.d.ts.map