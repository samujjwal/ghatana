/**
 * Canvas Backend Service - Persistence Layer
 * 
 * Handles saving/loading canvas data to/from backend
 * 
 * @doc.type service
 * @doc.purpose Canvas data persistence
 * @doc.layer services
 * @doc.pattern Service
 */

import type { HierarchicalNode } from '../lib/canvas/HierarchyManager';
import type { Connection } from '../lib/canvas/NodeManipulation';
import type { DrawingStroke } from '../lib/canvas/DrawingManager';
import { logger } from '../utils/Logger';

export interface CanvasData {
    id: string;
    projectId: string;
    nodes: HierarchicalNode[];
    connections: Connection[];
    drawings: DrawingStroke[];
    viewport: { x: number; y: number; zoom: number };
    version: number;
    updatedAt: string;
}

export interface SaveCanvasRequest {
    projectId: string;
    data: Omit<CanvasData, 'id' | 'projectId' | 'version' | 'updatedAt'>;
}

export interface LoadCanvasResponse {
    data: CanvasData | null;
    error?: string;
}

export class CanvasBackendService {
    private baseUrl: string;
    private saveTimeout: NodeJS.Timeout | null = null;
    private saveDebounceMs = 1000;

    constructor(baseUrl: string = '/api') {
        this.baseUrl = baseUrl;
    }

    /**
     * Load canvas data for a project
     */
    async loadCanvas(projectId: string, authToken?: string): Promise<LoadCanvasResponse> {
        const headers: Record<string, string> = {};
        if (authToken) headers['Authorization'] = `Bearer ${authToken}`;

        try {
            // Backend is the source of truth — always try it first
            const response = await fetch(`${this.baseUrl}/projects/${projectId}/canvas`, { headers });

            if (response.status === 404) {
                logger.info('No canvas found on backend, checking localStorage', 'canvas-backend', { projectId });
                const localData = this.loadFromLocalStorage(projectId);
                return { data: localData };
            }

            if (!response.ok) {
                throw new Error(`Failed to load canvas: ${response.statusText}`);
            }

            const data = await response.json() as CanvasData;
            logger.info('Loaded from backend', 'canvas-backend', { projectId });

            // Write-through cache so offline fallback is fresh
            this.saveToLocalStorage(projectId, data);

            return { data };
        } catch (error) {
            logger.error('Backend load failed, falling back to localStorage', 'canvas-backend', {
                projectId,
                error: error instanceof Error ? error.message : String(error),
            });

            const localData = this.loadFromLocalStorage(projectId);
            if (localData) {
                logger.info('Loaded from localStorage (offline fallback)', 'canvas-backend', { projectId });
                return { data: localData };
            }

            return {
                data: null,
                error: error instanceof Error ? error.message : 'Unknown error',
            };
        }
    }

    /**
     * Save canvas data (debounced)
     */
    saveCanvas(projectId: string, data: SaveCanvasRequest['data'], authToken?: string): void {
        if (this.saveTimeout) {
            clearTimeout(this.saveTimeout);
        }
        this.saveTimeout = setTimeout(() => {
            this.performSave(projectId, data, authToken);
        }, this.saveDebounceMs);
    }

    /**
     * Save immediately (no debounce)
     */
    async saveImmediately(projectId: string, data: SaveCanvasRequest['data'], authToken?: string): Promise<void> {
        if (this.saveTimeout) {
            clearTimeout(this.saveTimeout);
            this.saveTimeout = null;
        }
        await this.performSave(projectId, data, authToken);
    }

    /**
     * Perform the actual save — backend first, localStorage as write-through cache
     */
    private async performSave(projectId: string, data: SaveCanvasRequest['data'], authToken?: string): Promise<void> {
        const headers: Record<string, string> = { 'Content-Type': 'application/json' };
        if (authToken) headers['Authorization'] = `Bearer ${authToken}`;

        try {
            logger.info('Saving canvas to backend', 'canvas-backend', { projectId });

            const response = await fetch(`${this.baseUrl}/projects/${projectId}/canvas`, {
                method: 'PUT',
                headers,
                body: JSON.stringify(data),
            });

            if (!response.ok) {
                throw new Error(`Failed to save canvas: ${response.statusText}`);
            }

            logger.info('Saved to backend successfully', 'canvas-backend');

            // Update localStorage cache after a successful backend write
            this.saveToLocalStorage(projectId, {
                id: projectId,
                projectId,
                ...data,
                version: Date.now(),
                updatedAt: new Date().toISOString(),
            });
        } catch (error) {
            logger.error('Backend save failed — writing to localStorage', 'canvas-backend', {
                error: error instanceof Error ? error.message : String(error),
            });
            // Still write to localStorage so the user doesn't lose work
            this.saveToLocalStorage(projectId, {
                id: projectId,
                projectId,
                ...data,
                version: Date.now(),
                updatedAt: new Date().toISOString(),
            });
        }
    }

    /**
     * Export canvas as JSON
     */
    exportCanvas(projectId: string, data: SaveCanvasRequest['data']): void {
        const json = JSON.stringify(data, null, 2);
        const blob = new Blob([json], { type: 'application/json' });
        const url = URL.createObjectURL(blob);

        const link = document.createElement('a');
        link.href = url;
        link.download = `canvas-${projectId}-${Date.now()}.json`;
        link.click();

        URL.revokeObjectURL(url);
    }

    /**
     * Import canvas from JSON
     */
    async importCanvas(file: File): Promise<SaveCanvasRequest['data']> {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();

            reader.onload = (e) => {
                try {
                    const data = JSON.parse(e.target?.result as string);
                    resolve(data);
                } catch (error) {
                    reject(new Error('Invalid JSON file'));
                }
            };

            reader.onerror = () => reject(new Error('Failed to read file'));
            reader.readAsText(file);
        });
    }

    /**
     * Save to localStorage
     */
    private saveToLocalStorage(projectId: string, data: CanvasData): void {
        try {
            const key = `canvas_${projectId}`;
            localStorage.setItem(key, JSON.stringify(data));
        } catch (error) {
            logger.warn('localStorage save failed', 'canvas-backend', { error: error instanceof Error ? error.message : String(error) });
        }
    }

    /**
     * Load from localStorage
     */
    private loadFromLocalStorage(projectId: string): CanvasData | null {
        try {
            const key = `canvas_${projectId}`;
            const json = localStorage.getItem(key);
            return json ? JSON.parse(json) : null;
        } catch (error) {
            logger.warn('localStorage load failed', 'canvas-backend', { error: error instanceof Error ? error.message : String(error) });
            return null;
        }
    }

    /**
     * Clear local cache
     */
    clearLocalCache(projectId: string): void {
        try {
            const key = `canvas_${projectId}`;
            localStorage.removeItem(key);
        } catch (error) {
            logger.warn('localStorage clear failed', 'canvas-backend', { error: error instanceof Error ? error.message : String(error) });
        }
    }
}
