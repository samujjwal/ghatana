import type { PhysicsEntity, PhysicsConfig, EntitySelection } from '../types';
/**
 * @doc.type interface
 * @doc.purpose History state for undo/redo operations
 * @doc.layer core
 * @doc.pattern ValueObject
 */
export interface SimulationHistoryEntry {
    entities: PhysicsEntity[];
    selectedEntityId: string | null;
    timestamp: number;
}
/**
 * @doc.type atom
 * @doc.purpose Root atom for simulation entities
 * @doc.layer core
 * @doc.pattern State
 */
export declare const simulationEntitiesAtom: import("jotai").PrimitiveAtom<PhysicsEntity[]> & {
    init: PhysicsEntity[];
};
/**
 * @doc.type atom
 * @doc.purpose Atom for physics configuration
 * @doc.layer core
 * @doc.pattern State
 */
export declare const simulationPhysicsConfigAtom: import("jotai").PrimitiveAtom<PhysicsConfig> & {
    init: PhysicsConfig;
};
/**
 * @doc.type atom
 * @doc.purpose Atom for entity selection state
 * @doc.layer core
 * @doc.pattern State
 */
export declare const simulationSelectionAtom: import("jotai").PrimitiveAtom<EntitySelection> & {
    init: EntitySelection;
};
/**
 * @doc.type atom
 * @doc.purpose Atom for simulation history (undo/redo)
 * @doc.layer core
 * @doc.pattern State
 */
export declare const simulationHistoryAtom: import("jotai").PrimitiveAtom<SimulationHistoryEntry[]> & {
    init: SimulationHistoryEntry[];
};
/**
 * @doc.type atom
 * @doc.purpose Current history index for undo/redo
 * @doc.layer core
 * @doc.pattern State
 */
export declare const simulationHistoryIndexAtom: import("jotai").PrimitiveAtom<number> & {
    init: number;
};
/**
 * @doc.type atom
 * @doc.purpose Preview mode toggle
 * @doc.layer core
 * @doc.pattern State
 */
export declare const simulationPreviewModeAtom: import("jotai").PrimitiveAtom<boolean> & {
    init: boolean;
};
/**
 * @doc.type atom
 * @doc.purpose Derived atom for selected entity
 * @doc.layer core
 * @doc.pattern Derived
 */
export declare const selectedEntityAtom: import("jotai").Atom<PhysicsEntity | null>;
/**
 * @doc.type atom
 * @doc.purpose Derived atom for checking if undo is available
 * @doc.layer core
 * @doc.pattern Derived
 */
export declare const canUndoAtom: import("jotai").Atom<boolean>;
/**
 * @doc.type atom
 * @doc.purpose Derived atom for checking if redo is available
 * @doc.layer core
 * @doc.pattern Derived
 */
export declare const canRedoAtom: import("jotai").Atom<boolean>;
/**
 * @doc.type atom
 * @doc.purpose Derived atom for history status display
 * @doc.layer core
 * @doc.pattern Derived
 */
export declare const historyStatusAtom: import("jotai").Atom<{
    current: number;
    total: number;
}>;
/**
 * @doc.type atom
 * @doc.purpose Write atom for adding history entry
 * @doc.layer core
 * @doc.pattern Action
 */
export declare const addHistoryEntryAtom: import("jotai").WritableAtom<null, [entry: {
    entities: PhysicsEntity[];
    selectedEntityId: string | null;
}], void> & {
    init: null;
};
/**
 * @doc.type atom
 * @doc.purpose Write atom for undo operation
 * @doc.layer core
 * @doc.pattern Action
 */
export declare const undoAtom: import("jotai").WritableAtom<null, [], void> & {
    init: null;
};
/**
 * @doc.type atom
 * @doc.purpose Write atom for redo operation
 * @doc.layer core
 * @doc.pattern Action
 */
export declare const redoAtom: import("jotai").WritableAtom<null, [], void> & {
    init: null;
};
/**
 * @doc.type atom
 * @doc.purpose Write atom for adding entity
 * @doc.layer core
 * @doc.pattern Action
 */
export declare const addEntityAtom: import("jotai").WritableAtom<null, [entity: PhysicsEntity], void> & {
    init: null;
};
/**
 * @doc.type atom
 * @doc.purpose Write atom for updating entity
 * @doc.layer core
 * @doc.pattern Action
 */
export declare const updateEntityAtom: import("jotai").WritableAtom<null, [update: {
    id: string;
    changes: Partial<PhysicsEntity>;
}], void> & {
    init: null;
};
/**
 * @doc.type atom
 * @doc.purpose Write atom for deleting entity
 * @doc.layer core
 * @doc.pattern Action
 */
export declare const deleteEntityAtom: import("jotai").WritableAtom<null, [id: string], void> & {
    init: null;
};
/**
 * @doc.type atom
 * @doc.purpose Write atom for clearing all entities
 * @doc.layer core
 * @doc.pattern Action
 */
export declare const clearAllEntitiesAtom: import("jotai").WritableAtom<null, [], void> & {
    init: null;
};
/**
 * @doc.type atom
 * @doc.purpose Write atom for loading entities from manifest
 * @doc.layer core
 * @doc.pattern Action
 */
export declare const loadEntitiesAtom: import("jotai").WritableAtom<null, [data: {
    entities: PhysicsEntity[];
    physics?: PhysicsConfig;
}], void> & {
    init: null;
};
//# sourceMappingURL=atoms.d.ts.map