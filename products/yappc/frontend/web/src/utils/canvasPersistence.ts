/**
 * Canvas State Persistence Utilities
 * 
 * Provides localStorage-based persistence for canvas state across sessions.
 * Supports automatic saving, loading, and clearing of canvas data.
 * 
 * @doc.type utility
 * @doc.purpose Canvas state persistence
 * @doc.layer product
 * @doc.pattern Utility
 */

import { CanvasMode, AbstractionLevel } from '../types/canvas';
import {
    CanvasPersistenceService,
    type CanvasSnapshot,
} from '../services/persistence';

const COMPAT_STATE_PROJECT_ID = 'yappc-canvas-state';
const COMPAT_STATE_METADATA_KEY = 'compatPersistedState';

interface CanvasStateData {
    mode: CanvasMode;
    level: AbstractionLevel;
    timestamp: number;
    data: unknown;
}

interface CanvasPersistenceOptions {
    storageKey?: string;
    maxAge?: number; // milliseconds
    enableCompression?: boolean;
}

interface CanvasStateCompatEnvelope<T> {
    [COMPAT_STATE_METADATA_KEY]?: T;
}

const DEFAULT_OPTIONS: Required<CanvasPersistenceOptions> = {
    storageKey: 'yappc-canvas-state',
    maxAge: 7 * 24 * 60 * 60 * 1000, // 7 days
    enableCompression: false,
};

function getCompatibilityCanvasId(
    mode: CanvasMode,
    level: AbstractionLevel,
    options: CanvasPersistenceOptions
): string {
    const opts = { ...DEFAULT_OPTIONS, ...options };
    return `${opts.storageKey}:${mode}:${level}`;
}

function toCompatibleCanvasState<T>(
    mode: CanvasMode,
    level: AbstractionLevel,
    data: T
) {
    return {
        elements: [],
        connections: [],
        metadata: {
            mode,
            level,
            [COMPAT_STATE_METADATA_KEY]: data,
        } satisfies CanvasStateCompatEnvelope<T> & {
            mode: CanvasMode;
            level: AbstractionLevel;
        },
    };
}

function fromSnapshotData<T>(snapshot?: CanvasSnapshot): T | null {
    const metadata = snapshot?.data?.metadata as
        | (CanvasStateCompatEnvelope<T> & Record<string, unknown>)
        | undefined;

    if (!metadata || !(COMPAT_STATE_METADATA_KEY in metadata)) {
        return null;
    }

    return metadata[COMPAT_STATE_METADATA_KEY] ?? null;
}

function isExpired(
    snapshot: CanvasSnapshot,
    maxAge: number
): boolean {
    const timestamp = Date.parse(snapshot.timestamp);
    return Number.isFinite(timestamp) && Date.now() - timestamp > maxAge;
}

/**
 * Save canvas state to localStorage
 */
export function saveCanvasState(
    mode: CanvasMode,
    level: AbstractionLevel,
    data: unknown,
    options: CanvasPersistenceOptions = {}
): boolean {
    try {
        const canvasId = getCompatibilityCanvasId(mode, level, options);
        void CanvasPersistenceService.saveCanvas(
            COMPAT_STATE_PROJECT_ID,
            canvasId,
            toCompatibleCanvasState(mode, level, data)
        );
        return true;
    } catch (error) {
        console.error('Failed to save canvas state:', error);
        return false;
    }
}

/**
 * Load canvas state from localStorage
 */
export function loadCanvasState(
    mode: CanvasMode,
    level: AbstractionLevel,
    options: CanvasPersistenceOptions = {}
): unknown | null {
    try {
        const opts = { ...DEFAULT_OPTIONS, ...options };
        const canvasId = getCompatibilityCanvasId(mode, level, options);
        const key = `yappc-canvas:${COMPAT_STATE_PROJECT_ID}:${canvasId}`;
        const stored = localStorage.getItem(key);

        if (!stored) {
            return null;
        }

        const snapshot = JSON.parse(stored) as CanvasSnapshot;

        if (isExpired(snapshot, opts.maxAge)) {
            void CanvasPersistenceService.deleteCanvas(
                COMPAT_STATE_PROJECT_ID,
                canvasId
            );
            return null;
        }

        return fromSnapshotData(snapshot);
    } catch (error) {
        console.error('Failed to load canvas state:', error);
        return null;
    }
}

/**
 * Clear canvas state for specific mode/level
 */
export function clearCanvasState(
    mode: CanvasMode,
    level: AbstractionLevel,
    options: CanvasPersistenceOptions = {}
): boolean {
    try {
        const canvasId = getCompatibilityCanvasId(mode, level, options);
        void CanvasPersistenceService.deleteCanvas(
            COMPAT_STATE_PROJECT_ID,
            canvasId
        );
        return true;
    } catch (error) {
        console.error('Failed to clear canvas state:', error);
        return false;
    }
}

/**
 * Clear all canvas states
 */
export function clearAllCanvasStates(
    options: CanvasPersistenceOptions = {}
): boolean {
    try {
        const opts = { ...DEFAULT_OPTIONS, ...options };
        const keys = Object.keys(localStorage);
        const prefix = `yappc-canvas:${COMPAT_STATE_PROJECT_ID}:${opts.storageKey}:`;

        keys.forEach(key => {
            if (key.startsWith(prefix)) {
                localStorage.removeItem(key);
            }
        });

        return true;
    } catch (error) {
        console.error('Failed to clear all canvas states:', error);
        return false;
    }
}

/**
 * Get all stored canvas states
 */
export function getAllCanvasStates(
    options: CanvasPersistenceOptions = {}
): Map<string, CanvasStateData> {
    const opts = { ...DEFAULT_OPTIONS, ...options };
    const states = new Map<string, CanvasStateData>();
    const prefix = `yappc-canvas:${COMPAT_STATE_PROJECT_ID}:${opts.storageKey}:`;

    try {
        const keys = Object.keys(localStorage);

        keys.forEach(key => {
            if (key.startsWith(prefix)) {
                const stored = localStorage.getItem(key);
                if (stored) {
                    const snapshot = JSON.parse(stored) as CanvasSnapshot;
                    const data = fromSnapshotData(snapshot);
                    if (data !== null) {
                        const [, , , mode, level] = key.split(':');
                        states.set(key, {
                            mode: (mode ?? 'freeform') as CanvasMode,
                            level: (level ?? 'high') as AbstractionLevel,
                            timestamp: Date.parse(snapshot.timestamp),
                            data,
                        });
                    }
                }
            }
        });
    } catch (error) {
        console.error('Failed to get all canvas states:', error);
    }

    return states;
}

/**
 * React hook for canvas state persistence
 */
export function useCanvasPersistence<T>(
    mode: CanvasMode,
    level: AbstractionLevel,
    options: CanvasPersistenceOptions = {}
) {
    const save = (data: T) => saveCanvasState(mode, level, data, options);
    const load = () => loadCanvasState(mode, level, options) as T | null;
    const clear = () => clearCanvasState(mode, level, options);

    return { save, load, clear };
}
