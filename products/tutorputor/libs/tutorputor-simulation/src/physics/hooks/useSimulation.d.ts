import type { PhysicsEntity, PhysicsConfig, EntityType } from '../types';
export interface UseSimulationResult {
    entities: PhysicsEntity[];
    selectedEntity: PhysicsEntity | null;
    physicsConfig: PhysicsConfig;
    isPreviewMode: boolean;
    canUndo: boolean;
    canRedo: boolean;
    historyStatus: {
        current: number;
        total: number;
    };
    addEntity: (type: EntityType, x: number, y: number) => void;
    updateEntity: (id: string, changes: Partial<PhysicsEntity>) => void;
    deleteEntity: (id: string) => void;
    selectEntity: (id: string | null) => void;
    clearAll: () => void;
    undo: () => void;
    redo: () => void;
    setPreviewMode: (enabled: boolean) => void;
    setPhysicsConfig: (config: Partial<PhysicsConfig>) => void;
    exportManifest: (filename?: string) => void;
    importManifest: (file: File) => Promise<{
        success: boolean;
        error?: string;
    }>;
    loadManifest: (manifest: unknown) => void;
}
/**
 * Main hook for simulation state management
 * @doc.type hook
 * @doc.purpose Centralized simulation state and actions
 * @doc.layer core
 * @doc.pattern Hook
 */
export declare function useSimulation(): UseSimulationResult;
/**
 * Hook for keyboard shortcuts (undo/redo)
 * @doc.type hook
 * @doc.purpose Keyboard shortcut handling
 * @doc.layer core
 * @doc.pattern Hook
 */
export declare function useSimulationKeyboardShortcuts(): void;
//# sourceMappingURL=useSimulation.d.ts.map