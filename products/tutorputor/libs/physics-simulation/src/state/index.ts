/**
 * @doc.type module
 * @doc.purpose State management exports (Jotai atoms)
 * @doc.layer core
 * @doc.pattern Barrel
 */

export {
    // Core state atoms
    simulationEntitiesAtom,
    simulationPhysicsConfigAtom,
    simulationSelectionAtom,
    simulationHistoryAtom,
    simulationHistoryIndexAtom,
    simulationPreviewModeAtom,
    // Derived atoms
    selectedEntityAtom,
    canUndoAtom,
    canRedoAtom,
    historyStatusAtom,
    // Action atoms
    addHistoryEntryAtom,
    undoAtom,
    redoAtom,
    addEntityAtom,
    updateEntityAtom,
    deleteEntityAtom,
    clearAllEntitiesAtom,
    loadEntitiesAtom,
    // Types
    type SimulationHistoryEntry,
} from './atoms';
