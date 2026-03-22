import { atom } from 'jotai';
import type { PhysicsEntity, PhysicsConfig, EntitySelection } from '../types';
import { DEFAULT_PHYSICS_CONFIG } from '../entities/defaults';

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
export const simulationEntitiesAtom = atom<PhysicsEntity[]>([]);

/**
 * @doc.type atom
 * @doc.purpose Atom for physics configuration
 * @doc.layer core
 * @doc.pattern State
 */
export const simulationPhysicsConfigAtom = atom<PhysicsConfig>(DEFAULT_PHYSICS_CONFIG);

/**
 * @doc.type atom
 * @doc.purpose Atom for entity selection state
 * @doc.layer core
 * @doc.pattern State
 */
export const simulationSelectionAtom = atom<EntitySelection>({
    selectedId: null,
    multiSelect: [],
    isLocked: false,
});

/**
 * @doc.type atom
 * @doc.purpose Atom for simulation history (undo/redo)
 * @doc.layer core
 * @doc.pattern State
 */
export const simulationHistoryAtom = atom<SimulationHistoryEntry[]>([
    { entities: [], selectedEntityId: null, timestamp: Date.now() },
]);

/**
 * @doc.type atom
 * @doc.purpose Current history index for undo/redo
 * @doc.layer core
 * @doc.pattern State
 */
export const simulationHistoryIndexAtom = atom<number>(0);

/**
 * @doc.type atom
 * @doc.purpose Preview mode toggle
 * @doc.layer core
 * @doc.pattern State
 */
export const simulationPreviewModeAtom = atom<boolean>(false);

// Derived atoms

/**
 * @doc.type atom
 * @doc.purpose Derived atom for selected entity
 * @doc.layer core
 * @doc.pattern Derived
 */
export const selectedEntityAtom = atom((get) => {
    const entities = get(simulationEntitiesAtom);
    const selection = get(simulationSelectionAtom);
    return entities.find((e) => e.id === selection.selectedId) ?? null;
});

/**
 * @doc.type atom
 * @doc.purpose Derived atom for checking if undo is available
 * @doc.layer core
 * @doc.pattern Derived
 */
export const canUndoAtom = atom((get) => {
    const index = get(simulationHistoryIndexAtom);
    return index > 0;
});

/**
 * @doc.type atom
 * @doc.purpose Derived atom for checking if redo is available
 * @doc.layer core
 * @doc.pattern Derived
 */
export const canRedoAtom = atom((get) => {
    const history = get(simulationHistoryAtom);
    const index = get(simulationHistoryIndexAtom);
    return index < history.length - 1;
});

/**
 * @doc.type atom
 * @doc.purpose Derived atom for history status display
 * @doc.layer core
 * @doc.pattern Derived
 */
export const historyStatusAtom = atom((get) => {
    const history = get(simulationHistoryAtom);
    const index = get(simulationHistoryIndexAtom);
    return { current: index + 1, total: history.length };
});

// Action atoms

/**
 * @doc.type atom
 * @doc.purpose Write atom for adding history entry
 * @doc.layer core
 * @doc.pattern Action
 */
export const addHistoryEntryAtom = atom(
    null,
    (get, set, entry: { entities: PhysicsEntity[]; selectedEntityId: string | null }) => {
        const history = get(simulationHistoryAtom);
        const index = get(simulationHistoryIndexAtom);

        const newEntry: SimulationHistoryEntry = {
            entities: entry.entities,
            selectedEntityId: entry.selectedEntityId,
            timestamp: Date.now(),
        };

        // Truncate history if we're not at the end
        const newHistory = [...history.slice(0, index + 1), newEntry];
        set(simulationHistoryAtom, newHistory);
        set(simulationHistoryIndexAtom, newHistory.length - 1);
    }
);

/**
 * @doc.type atom
 * @doc.purpose Write atom for undo operation
 * @doc.layer core
 * @doc.pattern Action
 */
export const undoAtom = atom(null, (get, set) => {
    const index = get(simulationHistoryIndexAtom);
    if (index > 0) {
        const newIndex = index - 1;
        const history = get(simulationHistoryAtom);
        const prevState = history[newIndex];

        set(simulationEntitiesAtom, prevState.entities);
        set(simulationSelectionAtom, (prev) => ({
            ...prev,
            selectedId: prevState.selectedEntityId,
        }));
        set(simulationHistoryIndexAtom, newIndex);
    }
});

/**
 * @doc.type atom
 * @doc.purpose Write atom for redo operation
 * @doc.layer core
 * @doc.pattern Action
 */
export const redoAtom = atom(null, (get, set) => {
    const history = get(simulationHistoryAtom);
    const index = get(simulationHistoryIndexAtom);

    if (index < history.length - 1) {
        const newIndex = index + 1;
        const nextState = history[newIndex];

        set(simulationEntitiesAtom, nextState.entities);
        set(simulationSelectionAtom, (prev) => ({
            ...prev,
            selectedId: nextState.selectedEntityId,
        }));
        set(simulationHistoryIndexAtom, newIndex);
    }
});

/**
 * @doc.type atom
 * @doc.purpose Write atom for adding entity
 * @doc.layer core
 * @doc.pattern Action
 */
export const addEntityAtom = atom(null, (get, set, entity: PhysicsEntity) => {
    const entities = get(simulationEntitiesAtom);
    const newEntities = [...entities, entity];
    set(simulationEntitiesAtom, newEntities);
    set(simulationSelectionAtom, (prev) => ({ ...prev, selectedId: entity.id }));
    set(addHistoryEntryAtom, { entities: newEntities, selectedEntityId: entity.id });
});

/**
 * @doc.type atom
 * @doc.purpose Write atom for updating entity
 * @doc.layer core
 * @doc.pattern Action
 */
export const updateEntityAtom = atom(
    null,
    (get, set, update: { id: string; changes: Partial<PhysicsEntity> }) => {
        const entities = get(simulationEntitiesAtom);
        const selection = get(simulationSelectionAtom);
        const newEntities = entities.map((e) =>
            e.id === update.id ? { ...e, ...update.changes } : e
        );
        set(simulationEntitiesAtom, newEntities);
        set(addHistoryEntryAtom, { entities: newEntities, selectedEntityId: selection.selectedId });
    }
);

/**
 * @doc.type atom
 * @doc.purpose Write atom for deleting entity
 * @doc.layer core
 * @doc.pattern Action
 */
export const deleteEntityAtom = atom(null, (get, set, id: string) => {
    const entities = get(simulationEntitiesAtom);
    const newEntities = entities.filter((e) => e.id !== id);
    set(simulationEntitiesAtom, newEntities);
    set(simulationSelectionAtom, (prev) => ({
        ...prev,
        selectedId: prev.selectedId === id ? null : prev.selectedId,
    }));
    set(addHistoryEntryAtom, { entities: newEntities, selectedEntityId: null });
});

/**
 * @doc.type atom
 * @doc.purpose Write atom for clearing all entities
 * @doc.layer core
 * @doc.pattern Action
 */
export const clearAllEntitiesAtom = atom(null, (_get, set) => {
    set(simulationEntitiesAtom, []);
    set(simulationSelectionAtom, { selectedId: null, multiSelect: [], isLocked: false });
    set(addHistoryEntryAtom, { entities: [], selectedEntityId: null });
});

/**
 * @doc.type atom
 * @doc.purpose Write atom for loading entities from manifest
 * @doc.layer core
 * @doc.pattern Action
 */
export const loadEntitiesAtom = atom(
    null,
    (_get, set, data: { entities: PhysicsEntity[]; physics?: PhysicsConfig }) => {
        set(simulationEntitiesAtom, data.entities);
        if (data.physics) {
            set(simulationPhysicsConfigAtom, data.physics);
        }
        set(simulationSelectionAtom, { selectedId: null, multiSelect: [], isLocked: false });
        set(addHistoryEntryAtom, { entities: data.entities, selectedEntityId: null });
    }
);
