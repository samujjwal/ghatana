/**
 * Canvas Persistence Service
 * Handles localStorage with project/canvas namespacing and conflict resolution
 */

import type { CanvasState } from '../components/canvas/workspace/canvasAtoms';
import { logger } from '../utils/Logger';

/**
 *
 */
export interface CanvasSnapshot {
  id: string;
  projectId: string;
  canvasId: string;
  version: string;
  timestamp: string;
  data: CanvasState;
  checksum?: string;
}

/**
 *
 */
export class CanvasPersistenceService {
  private static readonly STORAGE_PREFIX = 'yappc-canvas';
  private static readonly VERSION = '1.0.0';

  /**
   * Defensive normalizer for persisted CanvasState shapes.
   * Ensures arrays/optional fields have sensible defaults so callers
   * won't throw when reading .length or iterating.
   */
  private static normalizeCanvasState(data?: CanvasState): CanvasState {
    const safe: CanvasState = {
      elements: (data && Array.isArray(data.elements)
        ? data.elements
        : []) as CanvasState['elements'],
      connections: (data && Array.isArray(data.connections)
        ? data.connections
        : []) as CanvasState['connections'],
      selectedElements: (data && Array.isArray(data.selectedElements)
        ? data.selectedElements
        : []) as CanvasState['selectedElements'],
      viewportPosition: data?.viewportPosition ??
        data?.viewport ?? {
        x: data?.viewport?.x ?? 0,
        y: data?.viewport?.y ?? 0,
      },
      zoomLevel: data?.zoomLevel ?? data?.viewport?.zoom ?? 1,
      viewport: data?.viewport ?? {
        x: data?.viewportPosition?.x ?? 0,
        y: data?.viewportPosition?.y ?? 0,
        zoom: data?.zoomLevel ?? 1,
      },
      metadata: data?.metadata ?? {},
      draggedElement: data?.draggedElement,
      isReadOnly: data?.isReadOnly ?? false,
      layers: data?.layers ?? [],
      history: data?.history ?? undefined,
    };

    // Copy any other fields that consumers may rely on
    return { ...data, ...safe } as CanvasState;
  }

  /**
   * Generate storage key for a canvas
   */
  private static getStorageKey(projectId: string, canvasId: string): string {
    return `${this.STORAGE_PREFIX}:${projectId}:${canvasId}`;
  }

  /**
   * Generate a simple checksum for conflict detection
   */
  private static generateChecksum(data: CanvasState): string {
    const elementsCount = Array.isArray(data?.elements)
      ? data.elements.length
      : 0;
    const connectionsCount = Array.isArray(data?.connections)
      ? data.connections.length
      : 0;
    const selectedCount = Array.isArray(data?.selectedElements)
      ? data.selectedElements.length
      : 0;

    const str = JSON.stringify({
      elements: elementsCount,
      connections: connectionsCount,
      selected: selectedCount,
      lastModified: Date.now(),
    });
    return btoa(str).substring(0, 16);
  }

  /**
   * Save canvas state to localStorage
   */
  static async saveCanvas(
    projectId: string,
    canvasId: string,
    canvasState: CanvasState
  ): Promise<{ success: boolean; snapshot?: CanvasSnapshot; error?: string }> {
    try {
      const snapshot: CanvasSnapshot = {
        id: `snapshot-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        projectId,
        canvasId,
        version: this.VERSION,
        timestamp: new Date().toISOString(),
        data: canvasState,
        checksum: this.generateChecksum(canvasState),
      };

      const key = this.getStorageKey(projectId, canvasId);
      localStorage.setItem(key, JSON.stringify(snapshot));

      // Also save to history for undo/redo
      this.saveToHistory(projectId, canvasId, snapshot);

      return { success: true, snapshot };
    } catch (error) {
      logger.error('Failed to save canvas', 'persistence', { error: error instanceof Error ? error.message : String(error) });
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error',
      };
    }
  }

  /**
   * Load canvas state from localStorage
   */
  static async loadCanvas(
    projectId: string,
    canvasId: string
  ): Promise<{ success: boolean; snapshot?: CanvasSnapshot; error?: string }> {
    try {
      const key = this.getStorageKey(projectId, canvasId);
      const stored = localStorage.getItem(key);

      if (!stored) {
        return { success: false, error: 'Canvas not found' };
      }

      const snapshot: CanvasSnapshot = JSON.parse(stored);

      // Validate version compatibility
      if (snapshot.version !== this.VERSION) {
        logger.warn('Canvas version mismatch', 'persistence', {
          snapshotVersion: snapshot.version,
          currentVersion: this.VERSION
        });
        // Could run migrations here in the future
      }

      // Defensive normalization: ensure snapshot.data has required shapes
      try {
        snapshot.data = this.normalizeCanvasState(snapshot.data as CanvasState);
      } catch (e) {
        logger.warn('Failed to normalize canvas snapshot data, falling back to empty canvas', 'persistence', {
          error: e instanceof Error ? e.message : String(e)
        });
        snapshot.data = this.normalizeCanvasState(undefined);
      }

      // Debug: expose a compact summary of the loaded snapshot for E2E traces
      try {
        // normalized data guarantees arrays
        logger.debug('loadCanvas summary', 'persistence', {
          projectId,
          canvasId,
          version: snapshot.version,
          elements: snapshot.data.elements.length,
          connections: snapshot.data.connections.length,
          checksum: snapshot.checksum,
        });
      } catch (e) {
        // swallow logging errors
      }

      return { success: true, snapshot };
    } catch (error) {
      logger.error('Failed to load canvas', 'persistence', { error: error instanceof Error ? error.message : String(error) });
      return {
        success: false,
        error:
          error instanceof Error
            ? error.message
            : 'Failed to parse canvas data',
      };
    }
  }

  /**
   * List all saved canvases for a project
   */
  static async listCanvases(projectId: string): Promise<{
    success: boolean;
    canvases?: Array<{ canvasId: string; timestamp: string; elements: number }>;
    error?: string;
  }> {
    try {
      const canvases: Array<{
        canvasId: string;
        timestamp: string;
        elements: number;
      }> = [];
      const prefix = `${this.STORAGE_PREFIX}:${projectId}:`;

      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key?.startsWith(prefix)) {
          const canvasId = key.substring(prefix.length);
          const stored = localStorage.getItem(key);

          if (stored) {
            try {
              const snapshot: CanvasSnapshot = JSON.parse(stored);
              const elementsCount =
                snapshot?.data && Array.isArray(snapshot.data.elements)
                  ? snapshot.data.elements.length
                  : 0;
              canvases.push({
                canvasId,
                timestamp: snapshot.timestamp,
                elements: elementsCount,
              });
            } catch {
              // Skip invalid entries
            }
          }
        }
      }

      return { success: true, canvases };
    } catch (error) {
      return {
        success: false,
        error:
          error instanceof Error ? error.message : 'Failed to list canvases',
      };
    }
  }

  /**
   * Delete a canvas
   */
  static async deleteCanvas(
    projectId: string,
    canvasId: string
  ): Promise<{ success: boolean; error?: string }> {
    try {
      const key = this.getStorageKey(projectId, canvasId);
      localStorage.removeItem(key);

      // Also clear history
      const historyKey = `${key}:history`;
      localStorage.removeItem(historyKey);

      return { success: true };
    } catch (error) {
      return {
        success: false,
        error:
          error instanceof Error ? error.message : 'Failed to delete canvas',
      };
    }
  }

  /**
   * Save to history for undo/redo (keep last 10 snapshots)
   */
  private static saveToHistory(
    projectId: string,
    canvasId: string,
    snapshot: CanvasSnapshot
  ): void {
    try {
      const historyKey = `${this.getStorageKey(projectId, canvasId)}:history`;
      const stored = localStorage.getItem(historyKey);
      let history: CanvasSnapshot[] = stored ? JSON.parse(stored) : [];

      // Add new snapshot
      history.push(snapshot);

      // Keep only last 10 snapshots
      if (history.length > 10) {
        history = history.slice(-10);
      }

      localStorage.setItem(historyKey, JSON.stringify(history));
    } catch (error) {
      logger.warn('Failed to save to history', 'persistence', { error: error instanceof Error ? error.message : String(error) });
    }
  }

  /**
   * Get history snapshots for undo/redo
   */
  static async getHistory(
    projectId: string,
    canvasId: string
  ): Promise<{
    success: boolean;
    history?: CanvasSnapshot[];
    error?: string;
  }> {
    try {
      const historyKey = `${this.getStorageKey(projectId, canvasId)}:history`;
      const stored = localStorage.getItem(historyKey);
      const history: CanvasSnapshot[] = stored ? JSON.parse(stored) : [];

      return { success: true, history };
    } catch (error) {
      return {
        success: false,
        error:
          error instanceof Error ? error.message : 'Failed to load history',
      };
    }
  }

  /**
   * Check for conflicts (compare checksums)
   */
  static async checkForConflicts(
    projectId: string,
    canvasId: string,
    currentChecksum: string
  ): Promise<{ hasConflict: boolean; remoteSnapshot?: CanvasSnapshot }> {
    const result = await this.loadCanvas(projectId, canvasId);

    if (!result.success || !result.snapshot) {
      return { hasConflict: false };
    }

    const hasConflict = result.snapshot.checksum !== currentChecksum;
    return {
      hasConflict,
      remoteSnapshot: hasConflict ? result.snapshot : undefined,
    };
  }

  /**
   * Export canvas as JSON
   */
  static async exportCanvas(
    projectId: string,
    canvasId: string
  ): Promise<{
    success: boolean;
    data?: string;
    filename?: string;
    error?: string;
  }> {
    const result = await this.loadCanvas(projectId, canvasId);

    if (!result.success || !result.snapshot) {
      return { success: false, error: result.error };
    }

    const exportData = {
      ...result.snapshot,
      exportedAt: new Date().toISOString(),
      format: 'yappc-canvas-v1',
    };

    const filename = `canvas-${projectId}-${canvasId}-${new Date().toISOString().split('T')[0]}.json`;

    return {
      success: true,
      data: JSON.stringify(exportData, null, 2),
      filename,
    };
  }

  /**
   * Import canvas from JSON
   */
  static async importCanvas(
    projectId: string,
    canvasId: string,
    jsonData: string
  ): Promise<{ success: boolean; snapshot?: CanvasSnapshot; error?: string }> {
    try {
      const importData = JSON.parse(jsonData);

      // Validate import format
      if (!importData.data || !importData.version) {
        return { success: false, error: 'Invalid canvas format' };
      }

      // Create new snapshot with current timestamp
      const snapshot: CanvasSnapshot = {
        ...importData,
        id: `imported-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
        projectId,
        canvasId,
        timestamp: new Date().toISOString(),
        checksum: this.generateChecksum(importData.data),
      };

      // Save imported canvas
      const saveResult = await this.saveCanvas(
        projectId,
        canvasId,
        importData.data
      );

      if (!saveResult.success) {
        return { success: false, error: saveResult.error };
      }

      return { success: true, snapshot };
    } catch (error) {
      return {
        success: false,
        error:
          error instanceof Error
            ? error.message
            : 'Failed to parse import data',
      };
    }
  }
}
