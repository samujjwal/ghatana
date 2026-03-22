import { useEffect, useCallback } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import {
    simulationEntitiesAtom,
    simulationPhysicsConfigAtom,
    simulationSelectionAtom,
    simulationPreviewModeAtom,
    selectedEntityAtom,
    canUndoAtom,
    canRedoAtom,
    historyStatusAtom,
    undoAtom,
    redoAtom,
    addEntityAtom,
    updateEntityAtom,
    deleteEntityAtom,
    clearAllEntitiesAtom,
    loadEntitiesAtom,
} from '../state/atoms';
import type { PhysicsEntity, PhysicsConfig, EntityType } from '../types';
import { createEntity } from '../entities';
import { createManifest, downloadManifest, readManifestFromFile } from '../serialization';/**
 * @doc.type interface
 * @doc.purpose Return type for useSimulation hook
 * @doc.layer core
 * @doc.pattern Hook
 */
export interface UseSimulationResult {
    // State
    entities: PhysicsEntity[];
    selectedEntity: PhysicsEntity | null;
    physicsConfig: PhysicsConfig;
    isPreviewMode: boolean;
    canUndo: boolean;
    canRedo: boolean;
    historyStatus: { current: number; total: number };

    // Actions
    addEntity: (type: EntityType, x: number, y: number) => void;
    updateEntity: (id: string, changes: Partial<PhysicsEntity>) => void;
    deleteEntity: (id: string) => void;
    selectEntity: (id: string | null) => void;
    clearAll: () => void;
    undo: () => void;
    redo: () => void;
    setPreviewMode: (enabled: boolean) => void;
    setPhysicsConfig: (config: Partial<PhysicsConfig>) => void;

    // Import/Export
    exportManifest: (filename?: string) => void;
    importManifest: (file: File) => Promise<{ success: boolean; error?: string }>;
    loadManifest: (manifest: any) => void;
}

/**
 * Main hook for simulation state management
 * @doc.type hook
 * @doc.purpose Centralized simulation state and actions
 * @doc.layer core
 * @doc.pattern Hook
 */
export function useSimulation(): UseSimulationResult {
    const entities = useAtomValue(simulationEntitiesAtom);
    const selectedEntity = useAtomValue(selectedEntityAtom);
    const [physicsConfig, setPhysicsConfigAtom] = useAtom(simulationPhysicsConfigAtom);
    const [, setSelection] = useAtom(simulationSelectionAtom);
    const [isPreviewMode, setPreviewModeAtom] = useAtom(simulationPreviewModeAtom);
    const canUndo = useAtomValue(canUndoAtom);
    const canRedo = useAtomValue(canRedoAtom);
    const historyStatus = useAtomValue(historyStatusAtom);

    const dispatchUndo = useSetAtom(undoAtom);
    const dispatchRedo = useSetAtom(redoAtom);
    const dispatchAddEntity = useSetAtom(addEntityAtom);
    const dispatchUpdateEntity = useSetAtom(updateEntityAtom);
    const dispatchDeleteEntity = useSetAtom(deleteEntityAtom);
    const dispatchClearAll = useSetAtom(clearAllEntitiesAtom);
    const dispatchLoadEntities = useSetAtom(loadEntitiesAtom);

    // Add entity action
    const addEntity = useCallback(
        (type: EntityType, x: number, y: number) => {
            const entity = createEntity(type, x, y);
            dispatchAddEntity(entity);
        },
        [dispatchAddEntity]
    );

    // Update entity action
    const updateEntity = useCallback(
        (id: string, changes: Partial<PhysicsEntity>) => {
            dispatchUpdateEntity({ id, changes });
        },
        [dispatchUpdateEntity]
    );

    // Delete entity action
    const deleteEntity = useCallback(
        (id: string) => {
            dispatchDeleteEntity(id);
        },
        [dispatchDeleteEntity]
    );

    // Select entity action
    const selectEntity = useCallback(
        (id: string | null) => {
            setSelection((prev) => ({ ...prev, selectedId: id }));
        },
        [setSelection]
    );

    // Clear all action
    const clearAll = useCallback(() => {
        if (window.confirm('Are you sure you want to clear all entities?')) {
            dispatchClearAll();
        }
    }, [dispatchClearAll]);

    // Undo action
    const undo = useCallback(() => {
        dispatchUndo();
    }, [dispatchUndo]);

    // Redo action
    const redo = useCallback(() => {
        dispatchRedo();
    }, [dispatchRedo]);

    // Toggle preview mode
    const setPreviewMode = useCallback(
        (enabled: boolean) => {
            setPreviewModeAtom(enabled);
        },
        [setPreviewModeAtom]
    );

    // Update physics config
    const setPhysicsConfig = useCallback(
        (config: Partial<PhysicsConfig>) => {
            setPhysicsConfigAtom((prev) => ({ ...prev, ...config }));
        },
        [setPhysicsConfigAtom]
    );

    // Export manifest
    const exportManifest = useCallback(
        (filename?: string) => {
            const manifest = createManifest(entities, physicsConfig);
            downloadManifest(manifest, filename);
        },
        [entities, physicsConfig]
    );

    // Import manifest
    const importManifest = useCallback(
        async (file: File): Promise<{ success: boolean; error?: string }> => {
            const result = await readManifestFromFile(file);
            if (result.success && result.manifest) {
                dispatchLoadEntities({
                    entities: result.manifest.entities,
                    physics: result.manifest.physics,
                });
                return { success: true };
            }
            return { success: false, error: result.error };
        },
        [dispatchLoadEntities]
    );

    // Load manifest from object
    const loadManifest = useCallback(
        (manifest: any) => {
            dispatchLoadEntities({
                entities: manifest.entities,
                physics: manifest.physics,
            });
        },
        [dispatchLoadEntities]
    );

    return {
        // State
        entities,
        selectedEntity,
        physicsConfig,
        isPreviewMode,
        canUndo,
        canRedo,
        historyStatus,
        // Actions
        addEntity,
        updateEntity,
        deleteEntity,
        selectEntity,
        clearAll,
        undo,
        redo,
        setPreviewMode,
        setPhysicsConfig,
        // Import/Export
        exportManifest,
        importManifest,
        loadManifest,
    };
}

/**
 * Hook for keyboard shortcuts (undo/redo)
 * @doc.type hook
 * @doc.purpose Keyboard shortcut handling
 * @doc.layer core
 * @doc.pattern Hook
 */
export function useSimulationKeyboardShortcuts(): void {
    const undo = useSetAtom(undoAtom);
    const redo = useSetAtom(redoAtom);
    const canUndo = useAtomValue(canUndoAtom);
    const canRedo = useAtomValue(canRedoAtom);

    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            const isMod = e.metaKey || e.ctrlKey;

            // Undo: Cmd/Ctrl + Z
            if (isMod && e.key === 'z' && !e.shiftKey && canUndo) {
                e.preventDefault();
                undo();
            }

            // Redo: Cmd/Ctrl + Y or Cmd/Ctrl + Shift + Z
            if (isMod && (e.key === 'y' || (e.key === 'z' && e.shiftKey)) && canRedo) {
                e.preventDefault();
                redo();
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [undo, redo, canUndo, canRedo]);
}
