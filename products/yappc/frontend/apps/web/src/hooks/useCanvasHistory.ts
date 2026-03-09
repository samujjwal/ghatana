/**
 * Canvas History Manager
 * 
 * Manages undo/redo state for canvas operations.
 * 
 * @doc.type hook
 * @doc.purpose Canvas history management
 * @doc.layer product
 * @doc.pattern State Management
 */

import { useState, useCallback } from 'react';

export interface HistoryState<T> {
    past: T[];
    present: T;
    future: T[];
}

export interface CanvasAction {
    type: 'create' | 'update' | 'delete' | 'move' | 'link';
    artifactId: string;
    data: unknown;
    timestamp: Date;
}

export interface UseCanvasHistoryReturn {
    canUndo: boolean;
    canRedo: boolean;
    undo: () => void;
    redo: () => void;
    pushAction: (action: CanvasAction) => void;
    clear: () => void;
    history: CanvasAction[];
}

const MAX_HISTORY_SIZE = 50;

export const useCanvasHistory = (initialState?: CanvasAction[]): UseCanvasHistoryReturn => {
    const [state, setState] = useState<HistoryState<CanvasAction[]>>({
        past: [],
        present: initialState || [],
        future: [],
    });

    const canUndo = state.past.length > 0;
    const canRedo = state.future.length > 0;

    const pushAction = useCallback((action: CanvasAction) => {
        setState((current) => {
            const newPresent = [...current.present, action];

            // Limit history size
            const newPast = [...current.past, current.present].slice(-MAX_HISTORY_SIZE);

            return {
                past: newPast,
                present: newPresent,
                future: [], // Clear future on new action
            };
        });
    }, []);

    const undo = useCallback(() => {
        setState((current) => {
            if (current.past.length === 0) return current;

            const previous = current.past[current.past.length - 1];
            const newPast = current.past.slice(0, -1);

            return {
                past: newPast,
                present: previous,
                future: [current.present, ...current.future],
            };
        });
    }, []);

    const redo = useCallback(() => {
        setState((current) => {
            if (current.future.length === 0) return current;

            const next = current.future[0];
            const newFuture = current.future.slice(1);

            return {
                past: [...current.past, current.present],
                present: next,
                future: newFuture,
            };
        });
    }, []);

    const clear = useCallback(() => {
        setState({
            past: [],
            present: [],
            future: [],
        });
    }, []);

    return {
        canUndo,
        canRedo,
        undo,
        redo,
        pushAction,
        clear,
        history: state.present,
    };
};
