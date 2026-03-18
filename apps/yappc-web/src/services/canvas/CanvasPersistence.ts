/**
 * Canvas Persistence Module
 * 
 * Standalone module for canvas persistence: save/load, version history,
 * auto-save, snapshot management. Uses command pattern for undo/redo.
 * 
 * @doc.type module
 * @doc.purpose Canvas persistence and versioning
 * @doc.layer product
 * @doc.pattern Service Module + Command Pattern
 */

import React from 'react';
import { logger } from '../../utils/Logger';
import type { CanvasState } from '../../components/canvas/workspace/canvasAtoms';

// Re-export for convenience
export type { CanvasState };

/**
 * Canvas snapshot with version metadata
 */
export interface CanvasSnapshot {
    id: string;
    projectId: string;
    canvasId: string;
    version: number;
    timestamp: number;
    data: CanvasState;
    checksum: string;
    label?: string;
    description?: string;
    author?: string;
    tags?: string[];
}

/**
 * Version comparison result
 */
export interface VersionDiff {
    added: {
        nodes: number;
        edges: number;
    };
    removed: {
        nodes: number;
        edges: number;
    };
    modified: {
        nodes: number;
        edges: number;
    };
    unchanged: {
        nodes: number;
        edges: number;
    };
}

/**
 * Auto-save configuration
 */
export interface AutoSaveConfig {
    enabled: boolean;
    interval: number; // milliseconds
    maxSnapshots: number;
    onSuccess?: (snapshot: CanvasSnapshot) => void;
    onError?: (error: Error) => void;
}

/**
 * Command interface for undo/redo
 */
export interface Command {
    id: string;
    type: string;
    timestamp: number;
    execute(): Promise<void>;
    undo(): Promise<void>;
    redo?(): Promise<void>;
}

/**
 * Command stack for history
 */
export interface CommandStack {
    past: Command[];
    future: Command[];
    current: Command | null;
    maxSize: number;
}

/**
 * Persistence configuration
 */
export interface PersistenceConfig {
    storage: 'localStorage' | 'indexedDB' | 'api';
    apiEndpoint?: string;
    autoSave?: AutoSaveConfig;
    maxHistory?: number;
    compression?: boolean;
}

/**
 * Canvas Persistence Service
 * 
 * Manages canvas state persistence with version history, auto-save,
 * and undo/redo capabilities using command pattern.
 */
export class CanvasPersistence {
    private config: PersistenceConfig;
    private commandStack: CommandStack;
    private snapshots: Map<string, CanvasSnapshot> = new Map();
    private autoSaveTimer: NodeJS.Timeout | null = null;
    private isDirty: boolean = false;
    private versionCounter: number = 1;
    private listeners: Set<() => void> = new Set();

    constructor(config: PersistenceConfig) {
        this.config = {
            maxHistory: 50,
            compression: false,
            ...config,
        };

        this.commandStack = {
            past: [],
            future: [],
            current: null,
            maxSize: this.config.maxHistory || 50,
        };
    }

    /**
     * Subscribe to state changes (history stack updates)
     */
    public subscribe(listener: () => void): () => void {
        this.listeners.add(listener);
        return () => {
            this.listeners.delete(listener);
        };
    }

    private notifyListeners(): void {
        this.listeners.forEach(listener => listener());
    }

    /**
     * Initialize auto-save
     */
    public startAutoSave(getState: () => CanvasState, projectId: string, canvasId: string): void {
        if (!this.config.autoSave?.enabled) return;

        this.stopAutoSave();

        this.autoSaveTimer = setInterval(async () => {
            if (this.isDirty) {
                try {
                    const state = getState();
                    const snapshot = await this.save(projectId, canvasId, state, {
                        label: 'Auto-save',
                        description: 'Automatic snapshot',
                    });

                    this.isDirty = false;
                    this.config.autoSave?.onSuccess?.(snapshot);
                } catch (error) {
                    this.config.autoSave?.onError?.(error as Error);
                }
            }
        }, this.config.autoSave.interval);
    }

    /**
     * Stop auto-save
     */
    public stopAutoSave(): void {
        if (this.autoSaveTimer) {
            clearInterval(this.autoSaveTimer);
            this.autoSaveTimer = null;
        }
    }

    /**
     * Mark state as dirty (needs save)
     */
    public markDirty(): void {
        this.isDirty = true;
    }

    /**
     * Migrate legacy localStorage state
     */
    public async migrateLegacyState(): Promise<CanvasState | null> {
        if (typeof window === 'undefined') return null;

        const legacyRaw = localStorage.getItem('canvas-state');
        if (!legacyRaw) return null;

        try {
            const legacy = JSON.parse(legacyRaw);
            const legacyElements = Array.isArray(legacy.elements)
                ? (legacy.elements as unknown[]).map(
                    (element: unknown, index: number) => ({
                        id: element.id ?? `legacy-element-${index}`,
                        kind: element.kind ?? 'node',
                        type: element.type ?? 'component',
                        position: element.position ?? { x: 0, y: 0 },
                        size: element.size,
                        data: element.data ?? {
                            label: element.id ?? `Element ${index + 1}`,
                        },
                        style: element.style,
                        selected: element.selected ?? false,
                    })
                )
                : [];

            const legacyShapes = Array.isArray(legacy.sketches)
                ? (legacy.sketches as unknown[]).map(
                    (shape: unknown, index: number) => ({
                        id: shape.id ?? `legacy-shape-${index}`,
                        kind: 'shape',
                        type: shape.type ?? 'stroke',
                        position: shape.position ?? { x: 0, y: 0 },
                        data: shape.data ?? shape,
                        style: shape.style,
                    })
                )
                : [];

            const legacyConnections = Array.isArray(legacy.connections)
                ? (legacy.connections as unknown[])
                    .filter(
                        (connection: unknown) =>
                            connection?.source && connection?.target
                    )
                    .map((connection: unknown, index: number) => ({
                        id: connection.id ?? `legacy-connection-${index}`,
                        source: connection.source,
                        target: connection.target,
                        sourceHandle: connection.sourceHandle,
                        targetHandle: connection.targetHandle,
                        type: connection.type ?? 'default',
                        animated: connection.animated ?? false,
                        data: connection.data,
                        style: connection.style,
                    }))
                : [];

            const loadedState: CanvasState = {
                elements: [...legacyElements, ...legacyShapes],
                connections: legacyConnections,
                selectedElements: Array.isArray(legacy.selectedElements)
                    ? legacy.selectedElements
                    : [],
                viewportPosition: legacy.viewportPosition ??
                    legacy.viewport ?? { x: 0, y: 0 },
                viewport: legacy.viewport ?? {
                    x: legacy.viewportPosition?.x ?? 0,
                    y: legacy.viewportPosition?.y ?? 0,
                    zoom: legacy.zoomLevel ?? 1,
                },
                zoomLevel: legacy.zoomLevel ?? legacy.viewport?.zoom ?? 1,
                metadata: legacy.metadata ?? {},
                draggedElement: legacy.draggedElement,
                isReadOnly: legacy.isReadOnly ?? false,
                layers: legacy.layers ?? [],
                history: legacy.history,
            };

            localStorage.removeItem('canvas-state');
            return loadedState;
        } catch (e) {
            logger.warn('Failed to parse legacy canvas-state', 'canvas-persistence', {
                error: e instanceof Error ? e.message : String(e)
            });
            return null;
        }
    }

    /**
     * Save canvas state
     */
    public async save(
        projectId: string,
        canvasId: string,
        state: CanvasState,
        metadata?: Partial<CanvasSnapshot>
    ): Promise<CanvasSnapshot> {
        const snapshot: CanvasSnapshot = {
            id: `snapshot-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            projectId,
            canvasId,
            version: this.versionCounter++,
            timestamp: Date.now(),
            data: state,
            checksum: this.generateChecksum(state),
            ...metadata,
        };

        // Store snapshot
        this.snapshots.set(snapshot.id, snapshot);

        // Persist based on storage type
        switch (this.config.storage) {
            case 'localStorage':
                await this.saveToLocalStorage(projectId, canvasId, snapshot);
                break;
            case 'indexedDB':
                await this.saveToIndexedDB(projectId, canvasId, snapshot);
                break;
            case 'api':
                await this.saveToAPI(projectId, canvasId, snapshot);
                break;
        }

        // Manage snapshot history
        await this.pruneSnapshots(projectId, canvasId);

        this.isDirty = false;

        return snapshot;
    }

    /**
     * Load canvas state
     */
    public async load(projectId: string, canvasId: string): Promise<CanvasSnapshot | null> {
        logger.info('Loading canvas', 'canvas-persistence', { projectId, canvasId });

        // First check for AI-generated canvas data with old key format
        const legacyAIKey = `canvas:${projectId}:${canvasId}`;
        const legacyAIData = typeof window !== 'undefined' ? localStorage.getItem(legacyAIKey) : null;

        if (legacyAIData) {
            try {
                const snapshot = JSON.parse(legacyAIData);
                // Migrate to new key format
                const newKey = `yappc-canvas:${projectId}:${canvasId}`;
                localStorage.setItem(newKey, legacyAIData);
                localStorage.removeItem(legacyAIKey);
                logger.info('Migrated AI-generated canvas', 'canvas-persistence', { from: legacyAIKey, to: newKey });
                return snapshot;
            } catch (e) {
                logger.warn('Failed to migrate AI-generated canvas', 'canvas-persistence', {
                    error: e instanceof Error ? e.message : String(e)
                });
            }
        }

        let result: CanvasSnapshot | null = null;
        switch (this.config.storage) {
            case 'localStorage':
                result = await this.loadFromLocalStorage(projectId, canvasId);
                break;
            case 'indexedDB':
                result = await this.loadFromIndexedDB(projectId, canvasId);
                break;
            case 'api':
                result = await this.loadFromAPI(projectId, canvasId);
                break;
            default:
                result = null;
        }

        logger.info('Canvas load result', 'canvas-persistence', {
            result: result ? `Found snapshot with ${result.data?.elements?.length || 0} elements` : 'No snapshot found'
        });
        return result;
    }

    /**
     * Get version history
     */
    public async getHistory(projectId: string, canvasId: string): Promise<CanvasSnapshot[]> {
        const key = `${projectId}:${canvasId}:history`;

        switch (this.config.storage) {
            case 'localStorage': {
                const stored = localStorage.getItem(key);
                return stored ? JSON.parse(stored) : [];
            }
            case 'indexedDB': {
                // IndexedDB implementation
                return [];
            }
            case 'api': {
                // API implementation
                return [];
            }
            default:
                return [];
        }
    }

    /**
     * Create manual snapshot
     */
    public async createSnapshot(
        projectId: string,
        canvasId: string,
        state: CanvasState,
        label: string,
        description?: string
    ): Promise<CanvasSnapshot> {
        return await this.save(projectId, canvasId, state, {
            label,
            description,
            tags: ['manual'],
        });
    }

    /**
     * Restore from snapshot
     */
    public async restore(snapshotId: string): Promise<CanvasState | null> {
        const snapshot = this.snapshots.get(snapshotId);
        return snapshot ? snapshot.data : null;
    }

    /**
     * Compare two versions
     */
    public diffVersions(snapshot1: CanvasSnapshot, snapshot2: CanvasSnapshot): VersionDiff {
        const state1 = snapshot1.data;
        const state2 = snapshot2.data;

        const diff: VersionDiff = {
            added: { nodes: 0, edges: 0 },
            removed: { nodes: 0, edges: 0 },
            modified: { nodes: 0, edges: 0 },
            unchanged: { nodes: 0, edges: 0 },
        };

        // Compare nodes
        const state1NodeIds = new Set(state1.elements.map((e: unknown) => e.id));
        const state2NodeIds = new Set(state2.elements.map((e: unknown) => e.id));

        state2.elements.forEach((node: unknown) => {
            if (!state1NodeIds.has(node.id)) {
                diff.added.nodes++;
            } else {
                const oldNode = state1.elements.find((n: unknown) => n.id === node.id);
                if (JSON.stringify(oldNode) !== JSON.stringify(node)) {
                    diff.modified.nodes++;
                } else {
                    diff.unchanged.nodes++;
                }
            }
        });

        state1.elements.forEach((node: unknown) => {
            if (!state2NodeIds.has(node.id)) {
                diff.removed.nodes++;
            }
        });

        // Similar for edges
        const state1EdgeIds = new Set(state1.connections.map((c: unknown) => c.id));
        const state2EdgeIds = new Set(state2.connections.map((c: unknown) => c.id));

        state2.connections.forEach((edge: unknown) => {
            if (!state1EdgeIds.has(edge.id)) {
                diff.added.edges++;
            } else {
                const oldEdge = state1.connections.find((e: unknown) => e.id === edge.id);
                if (JSON.stringify(oldEdge) !== JSON.stringify(edge)) {
                    diff.modified.edges++;
                } else {
                    diff.unchanged.edges++;
                }
            }
        });

        state1.connections.forEach((edge: unknown) => {
            if (!state2EdgeIds.has(edge.id)) {
                diff.removed.edges++;
            }
        });

        return diff;
    }

    /**
     * Get version history for a canvas
     */
    public async getVersionHistory(projectId: string, canvasId: string): Promise<CanvasSnapshot[]> {
        const key = `canvas:${projectId}:${canvasId}:history`;
        const historyJson = localStorage.getItem(key);
        if (!historyJson) return [];

        const history = JSON.parse(historyJson);
        return history.sort((a: CanvasSnapshot, b: CanvasSnapshot) => b.timestamp - a.timestamp);
    }

    /**
     * Restore canvas from a snapshot
     */
    public async restoreSnapshot(snapshotId: string): Promise<CanvasState | null> {
        const snapshot = this.snapshots.get(snapshotId);
        if (!snapshot) return null;
        return snapshot.data;
    }

    /**
     * Delete a snapshot
     */
    public async deleteSnapshot(snapshotId: string): Promise<void> {
        this.snapshots.delete(snapshotId);
    }

    /**
     * Get command stack for inspection
     */
    public getCommandStack(): CommandStack {
        return { ...this.commandStack };
    }

    /**
     * Get current project ID
     */
    public getCurrentProject(): string | null {
        return localStorage.getItem('currentProjectId');
    }

    /**
     * Get current canvas ID
     */
    public getCurrentCanvas(): string | null {
        return localStorage.getItem('currentCanvasId');
    }

    /**
     * Check if there are unsaved changes
     */
    public hasUnsavedChanges(): boolean {
        return this.isDirty || this.commandStack.past.length > 0;
    }

    /**
     * Execute command (for undo/redo)
     */
    public async executeCommand(command: Command): Promise<void> {
        await command.execute();
        this.addCommand(command);
    }

    /**
     * Add command to history without executing it
     * (Useful when the action has already been performed)
     */
    public addCommand(command: Command): void {
        // Add to history
        this.commandStack.past.push(command);
        this.commandStack.current = command;
        this.commandStack.future = []; // Clear redo stack

        // Limit history size
        if (this.commandStack.past.length > this.commandStack.maxSize) {
            this.commandStack.past.shift();
        }

        this.markDirty();
        this.notifyListeners();
    }

    /**
     * Undo last command
     */
    public async undo(): Promise<boolean> {
        if (this.commandStack.past.length === 0) return false;

        const command = this.commandStack.past.pop()!;
        await command.undo();

        this.commandStack.future.unshift(command);
        this.commandStack.current = this.commandStack.past[this.commandStack.past.length - 1] || null;

        this.markDirty();
        this.notifyListeners();
        return true;
    }

    /**
     * Redo last undone command
     */
    public async redo(): Promise<boolean> {
        if (this.commandStack.future.length === 0) return false;

        const command = this.commandStack.future.shift()!;

        if (command.redo) {
            await command.redo();
        } else {
            await command.execute();
        }

        this.commandStack.past.push(command);
        this.commandStack.current = command;

        this.markDirty();
        this.notifyListeners();
        return true;
    }

    /**
     * Check if undo is available
     */
    public canUndo(): boolean {
        return this.commandStack.past.length > 0;
    }

    /**
     * Check if redo is available
     */
    public canRedo(): boolean {
        return this.commandStack.future.length > 0;
    }

    /**
     * Get command history
     */
    public getCommandHistory(): Command[] {
        return [...this.commandStack.past];
    }

    // ===== Private methods =====

    private generateChecksum(state: CanvasState): string {
        const str = JSON.stringify(state);
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            const char = str.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash; // Convert to 32-bit integer
        }
        return hash.toString(16);
    }

    private async saveToLocalStorage(
        projectId: string,
        canvasId: string,
        snapshot: CanvasSnapshot
    ): Promise<void> {
        const key = `yappc-canvas:${projectId}:${canvasId}`;
        const historyKey = `${key}:history`;

        // Save current snapshot
        localStorage.setItem(key, JSON.stringify(snapshot));

        // Save to history
        const history = await this.getHistory(projectId, canvasId);
        history.unshift(snapshot);
        localStorage.setItem(historyKey, JSON.stringify(history));
    }

    private async loadFromLocalStorage(
        projectId: string,
        canvasId: string
    ): Promise<CanvasSnapshot | null> {
        const key = `yappc-canvas:${projectId}:${canvasId}`;
        const stored = localStorage.getItem(key);
        return stored ? JSON.parse(stored) : null;
    }

    private async saveToIndexedDB(
        projectId: string,
        canvasId: string,
        snapshot: CanvasSnapshot
    ): Promise<void> {
        // IndexedDB implementation
        console.warn('IndexedDB persistence not yet implemented');
    }

    private async loadFromIndexedDB(
        projectId: string,
        canvasId: string
    ): Promise<CanvasSnapshot | null> {
        console.warn('IndexedDB persistence not yet implemented');
        return null;
    }

    private async saveToAPI(
        projectId: string,
        canvasId: string,
        snapshot: CanvasSnapshot
    ): Promise<void> {
        if (!this.config.apiEndpoint) {
            throw new Error('API endpoint not configured');
        }

        const response = await fetch(`${this.config.apiEndpoint}/canvas`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                projectId,
                canvasId,
                data: snapshot.data,
            }),
        });

        if (!response.ok) {
            throw new Error(`Failed to save to API: ${response.statusText}`);
        }
    }

    private async loadFromAPI(
        projectId: string,
        canvasId: string
    ): Promise<CanvasSnapshot | null> {
        if (!this.config.apiEndpoint) {
            throw new Error('API endpoint not configured');
        }

        const response = await fetch(`${this.config.apiEndpoint}/canvas/${projectId}/${canvasId}`);

        if (!response.ok) {
            return null;
        }

        const data = await response.json();
        return data.canvas ? {
            id: data.canvas.id,
            projectId: data.canvas.projectId,
            canvasId: data.canvas.canvasId,
            version: 1,
            timestamp: new Date(data.canvas.updatedAt).getTime(),
            data: data.canvas.data,
            checksum: '',
        } : null;
    }

    private async pruneSnapshots(projectId: string, canvasId: string): Promise<void> {
        const maxSnapshots = this.config.autoSave?.maxSnapshots || 10;
        const history = await this.getHistory(projectId, canvasId);

        if (history.length > maxSnapshots) {
            const pruned = history.slice(0, maxSnapshots);
            const key = `${projectId}:${canvasId}:history`;
            localStorage.setItem(key, JSON.stringify(pruned));
        }
    }

    /**
     * Cleanup
     */
    public destroy(): void {
        this.stopAutoSave();
        this.snapshots.clear();
        this.commandStack.past = [];
        this.commandStack.future = [];
    }
}

/**
 * React hook for using Canvas Persistence
 */
export function useCanvasPersistence(config: PersistenceConfig) {
    const persistenceRef = React.useRef<CanvasPersistence | null>(null);

    if (!persistenceRef.current) {
        persistenceRef.current = new CanvasPersistence(config);
    }

    React.useEffect(() => {
        return () => {
            persistenceRef.current?.destroy();
        };
    }, []);

    return persistenceRef.current;
}
