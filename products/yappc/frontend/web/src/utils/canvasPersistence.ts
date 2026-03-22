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

const DEFAULT_OPTIONS: Required<CanvasPersistenceOptions> = {
    storageKey: 'yappc-canvas-state',
    maxAge: 7 * 24 * 60 * 60 * 1000, // 7 days
    enableCompression: false,
};

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
        const opts = { ...DEFAULT_OPTIONS, ...options };
        const stateKey = `${opts.storageKey}-${mode}-${level}`;

        const state: CanvasStateData = {
            mode,
            level,
            timestamp: Date.now(),
            data,
        };

        localStorage.setItem(stateKey, JSON.stringify(state));
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
        const stateKey = `${opts.storageKey}-${mode}-${level}`;

        const stored = localStorage.getItem(stateKey);
        if (!stored) {
            return null;
        }

        const state: CanvasStateData = JSON.parse(stored);

        // Check if state is expired
        if (Date.now() - state.timestamp > opts.maxAge) {
            localStorage.removeItem(stateKey);
            return null;
        }

        return state.data;
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
        const opts = { ...DEFAULT_OPTIONS, ...options };
        const stateKey = `${opts.storageKey}-${mode}-${level}`;
        localStorage.removeItem(stateKey);
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

        keys.forEach(key => {
            if (key.startsWith(opts.storageKey)) {
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

    try {
        const keys = Object.keys(localStorage);

        keys.forEach(key => {
            if (key.startsWith(opts.storageKey)) {
                const stored = localStorage.getItem(key);
                if (stored) {
                    const state: CanvasStateData = JSON.parse(stored);
                    states.set(key, state);
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
