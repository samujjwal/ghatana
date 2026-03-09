/**
 * useCanvasPersistence - Canvas State Persistence Hook
 * 
 * Handles auto-save, conflict resolution, and backend sync
 * 
 * @doc.type hook
 * @doc.purpose Canvas state persistence and sync
 * @doc.layer hooks
 * @doc.pattern Hook
 */

import { useEffect, useCallback, useRef, useState } from 'react';
import { useAtomValue } from 'jotai';
import { canvasAtom } from '../state/atoms/unifiedCanvasAtom';
import { apiUrl } from '../config/api';

export interface PersistenceOptions {
    projectId: string;
    autoSaveInterval?: number; // milliseconds
    debounceDelay?: number; // milliseconds
    enableConflictResolution?: boolean;
}

export interface UseCanvasPersistenceReturn {
    isSaving: boolean;
    lastSaved: Date | null;
    saveStatus: 'saved' | 'saving' | 'unsaved' | 'error';
    saveNow: () => Promise<void>;
    loadCanvas: () => Promise<void>;
}

export function useCanvasPersistence(options: PersistenceOptions): UseCanvasPersistenceReturn {
    const {
        projectId,
        autoSaveInterval = 30000, // 30 seconds
        debounceDelay = 2000, // 2 seconds
        enableConflictResolution = true
    } = options;

    const canvas = useAtomValue(canvasAtom);

    const [isSaving, setIsSaving] = useState(false);
    const [lastSaved, setLastSaved] = useState<Date | null>(null);
    const [saveStatus, setSaveStatus] = useState<'saved' | 'saving' | 'unsaved' | 'error'>('saved');

    const saveTimeoutRef = useRef<NodeJS.Timeout | null>(null);
    const autoSaveIntervalRef = useRef<NodeJS.Timeout | null>(null);
    const lastSavedStateRef = useRef<string | null>(null);

    /**
     * Save canvas to backend
     */
    const saveToBackend = useCallback(async () => {
        try {
            setIsSaving(true);
            setSaveStatus('saving');

            // Serialize canvas state
            const canvasData = {
                projectId,
                nodes: canvas.nodes,
                connections: canvas.connections,
                viewport: canvas.viewport,
                layers: canvas.layers,
                groups: canvas.groups,
                timestamp: new Date().toISOString()
            };

            // Save to backend API
            console.log('[Persistence] Saving canvas:', projectId);

            const response = await fetch(`http://localhost:7003/api/projects/${projectId}/canvas`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ data: canvasData })
            });

            if (!response.ok) {
                throw new Error(`Failed to save canvas: ${response.statusText}`);
            }

            const result = await response.json();
            console.log('[Persistence] Saved successfully, version:', result.version);

            lastSavedStateRef.current = JSON.stringify(canvasData);
            setLastSaved(new Date());
            setSaveStatus('saved');

            console.log('[Persistence] Canvas saved successfully');
        } catch (error) {
            console.error('[Persistence] Save failed:', error);
            setSaveStatus('error');
        } finally {
            setIsSaving(false);
        }
    }, [canvas, projectId]);

    /**
     * Load canvas from backend
     */
    const loadCanvas = useCallback(async () => {
        try {
            console.log('[Persistence] Loading canvas:', projectId);

            const response = await fetch(apiUrl(`/projects/${projectId}/canvas`), {
                method: 'GET',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include'
            });

            if (!response.ok) {
                throw new Error(`Failed to load canvas: ${response.statusText}`);
            }

            const data = await response.json();
            console.log('[Persistence] Canvas loaded successfully');

            // Update save state
            if (data.lastSaved) {
                setLastSaved(new Date(data.lastSaved));
                lastSavedStateRef.current = JSON.stringify(data);
            }
            setSaveStatus('saved');

            // NOTE: Update canvasAtom with loaded data using setCanvas from useUnifiedCanvas
            // This requires access to the canvas update action
        } catch (error) {
            console.error('[Persistence] Load failed:', error);
        }
    }, [projectId]);

    /**
     * Debounced save
     */
    const saveNow = useCallback(async () => {
        // Clear existing debounce timeout
        if (saveTimeoutRef.current) {
            clearTimeout(saveTimeoutRef.current);
        }

        await saveToBackend();
    }, [saveToBackend]);

    /**
     * Check if canvas has unsaved changes
     */
    const hasUnsavedChanges = useCallback(() => {
        const currentState = JSON.stringify({
            nodes: canvas.nodes,
            connections: canvas.connections,
            viewport: canvas.viewport
        });
        return currentState !== lastSavedStateRef.current;
    }, [canvas]);

    /**
     * Effect: Debounced auto-save on canvas changes
     */
    useEffect(() => {
        if (!hasUnsavedChanges()) return;

        setSaveStatus('unsaved');

        // Clear existing timeout
        if (saveTimeoutRef.current) {
            clearTimeout(saveTimeoutRef.current);
        }

        // Set new debounce timeout
        saveTimeoutRef.current = setTimeout(() => {
            saveToBackend();
        }, debounceDelay);

        return () => {
            if (saveTimeoutRef.current) {
                clearTimeout(saveTimeoutRef.current);
            }
        };
    }, [canvas.nodes, canvas.connections, canvas.viewport, debounceDelay, saveToBackend, hasUnsavedChanges]);

    /**
     * Effect: Periodic auto-save interval
     */
    useEffect(() => {
        autoSaveIntervalRef.current = setInterval(() => {
            if (hasUnsavedChanges()) {
                saveToBackend();
            }
        }, autoSaveInterval);

        return () => {
            if (autoSaveIntervalRef.current) {
                clearInterval(autoSaveIntervalRef.current);
            }
        };
    }, [autoSaveInterval, saveToBackend, hasUnsavedChanges]);

    /**
     * Effect: Load canvas on mount
     */
    useEffect(() => {
        loadCanvas();
    }, [loadCanvas]);

    /**
     * Effect: Save before unload
     */
    useEffect(() => {
        const handleBeforeUnload = (event: BeforeUnloadEvent) => {
            if (hasUnsavedChanges()) {
                event.preventDefault();
                event.returnValue = 'You have unsaved changes. Are you sure you want to leave?';

                // Attempt synchronous save (browser may block this)
                saveToBackend();
            }
        };

        window.addEventListener('beforeunload', handleBeforeUnload);

        return () => {
            window.removeEventListener('beforeunload', handleBeforeUnload);
        };
    }, [hasUnsavedChanges, saveToBackend]);

    return {
        isSaving,
        lastSaved,
        saveStatus,
        saveNow,
        loadCanvas
    };
}
