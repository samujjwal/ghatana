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
// Types
export { EntityType, } from "./types";
// Entity utilities
export { DEFAULT_PHYSICS, ENTITY_DEFAULTS, TOOLBOX_ITEMS, DEFAULT_PHYSICS_CONFIG, createEntity, physicsPropertiesSchema, entityAppearanceSchema, physicsEntitySchema, physicsConfigSchema, validateEntity, validatePhysicsConfig, isValidEntityType, } from "./entities";
// Serialization
export { MANIFEST_VERSION, physicsManifestSchema, createManifest, exportManifestToJSON, downloadManifest, parseManifest, migrateManifest, readManifestFromFile, } from "./serialization";
// Rendering
export { KonvaEntityRenderer, } from "./rendering";
// State atoms
export { 
// Core state atoms
simulationEntitiesAtom, simulationPhysicsConfigAtom, simulationSelectionAtom, simulationHistoryAtom, simulationHistoryIndexAtom, simulationPreviewModeAtom, 
// Derived atoms
selectedEntityAtom, canUndoAtom, canRedoAtom, historyStatusAtom, 
// Action atoms
addHistoryEntryAtom, undoAtom, redoAtom, addEntityAtom, updateEntityAtom, deleteEntityAtom, clearAllEntitiesAtom, loadEntitiesAtom, } from "./state";
// Hooks
export { useSimulation, useSimulationKeyboardShortcuts, } from "./hooks";
// Components
export { DraggableToolboxItem, EntityToolbox, PhysicsPropertyPanel, PhysicsConfigPanel, SimulationCanvas, SimulationToolbar, } from "./components";
// Adapters - Unified rendering backend abstraction
export { KONVA_CAPABILITIES, REACT_FLOW_CAPABILITIES, WEBGL_CAPABILITIES, renderingAdapterRegistry, registerRenderingAdapter, getRenderingAdapter, selectBestAdapter, KonvaRenderingAdapter, } from "./adapters";
// Collaboration - Yjs-based real-time collaboration
export { usePhysicsCollaboration, UserCursor, CollaborationCursors, CollaborationStatusBar, } from "./collaboration";
//# sourceMappingURL=index.js.map