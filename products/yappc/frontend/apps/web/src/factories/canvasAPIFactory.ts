/**
 * Canvas API Factory
 * Creates a mocked Canvas API instance for Page Builder integration
 * Provides persistent storage via localStorage and event bus
 */

import { logger } from '../utils/Logger';
import type { CanvasAPI, CanvasElement, CanvasDocument } from '@ghatana/canvas';

const STORAGE_KEY = 'page-builder-canvas-state';

/**
 * Event listener type
 */
type EventListener = (data?: unknown) => void;

/**
 * Create a mocked Canvas API instance with localStorage persistence
 */
export function createCanvasAPIFactory(): CanvasAPI {
  // Initialize state from localStorage or use defaults
  const state: CanvasDocument = (() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        return JSON.parse(stored);
      }
    } catch (err) {
      logger.warn('Failed to load Canvas state from localStorage', 'canvas-api-factory', {
        error: err instanceof Error ? err.message : String(err)
      });

      return {
        id: `canvas-${Date.now()}`,
        version: '1.0.0',
        title: 'Page Builder Canvas',
        viewport: {
          center: { x: 0, y: 0 },
          zoom: 1.0,
        },
        elements: {},
        elementOrder: [],
        metadata: {},
        capabilities: {
          canEdit: true,
          canZoom: true,
          canPan: true,
          canSelect: true,
          canUndo: true,
          canRedo: true,
          canExport: true,
          canImport: true,
          canCollaborate: false,
          canPersist: true,
          allowedElementTypes: ['node', 'edge', 'group'],
        },
        createdAt: new Date(),
        updatedAt: new Date(),
      };
    }) ();

    let selectedIds: string[] = [];
    const listeners: Map<string, Set<EventListener>> = new Map();

    /**
     * Persist state to localStorage
     */
    const persistState = () => {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
      } catch (err) {
        logger.error('Failed to persist Canvas state', 'canvas-api-factory', {
          error: err instanceof Error ? err.message : String(err)
        });
      };

      /**
       * Emit event to listeners
       */
      const emit = (event: string, data?: unknown): void => {
        const eventListeners = listeners.get(event);
        if (eventListeners) {
          eventListeners.forEach((listener) => {
            try {
              listener(data);
            } catch (err) {
              logger.error(`Error in listener for event "${event}"`, 'canvas-api-factory', {
                event,
                error: err instanceof Error ? err.message : String(err)
              });
            });
        }
      };

      /**
       * Register event listener
       */
      const on = (event: string, listener: EventListener): void => {
        if (!listeners.has(event)) {
          listeners.set(event, new Set());
        }
        listeners.get(event)!.add(listener);
      };

      /**
       * Unregister event listener
       */
      const off = (event: string, listener: EventListener): void => {
        const eventListeners = listeners.get(event);
        if (eventListeners) {
          eventListeners.delete(listener);
        }
      };

      /**
       * Create Canvas API instance
       */
      const api: CanvasAPI = {
        // Element manipulation
        addElement: (element: Partial<CanvasElement>): string => {
          const id = element.id || `element-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;

          const newElement: CanvasElement = {
            id,
            type: element.type || 'node',
            transform: element.transform || { position: { x: 0, y: 0 }, scale: 1, rotation: 0 },
            bounds: element.bounds || { x: 0, y: 0, width: 100, height: 100 },
            visible: element.visible !== false,
            locked: element.locked || false,
            selected: false,
            zIndex: element.zIndex || 0,
            metadata: element.metadata || {},
            version: '1.0.0',
            createdAt: new Date(),
            updatedAt: new Date(),
          };

          state.elements[id] = newElement;
          state.elementOrder.push(id);
          state.updatedAt = new Date();

          persistState();
          emit('element-added', newElement);

          return id;
        },

        removeElement: (elementId: string): void => {
          if (state.elements[elementId]) {
            delete state.elements[elementId];
            state.elementOrder = state.elementOrder.filter((id) => id !== elementId);
            state.updatedAt = new Date();

            persistState();
            emit('element-removed', { id: elementId });
          }
        },

        updateElement: (elementId: string, updates: Partial<CanvasElement>): void => {
          if (state.elements[elementId]) {
            state.elements[elementId] = {
              ...state.elements[elementId],
              ...updates,
              updatedAt: new Date(),
            };
            state.updatedAt = new Date();

            persistState();
            emit('element-updated', state.elements[elementId]);
          }
        },

        getElement: (elementId: string): CanvasElement | undefined => {
          return state.elements[elementId];
        },

        getAllElements: (): readonly CanvasElement[] => {
          return state.elementOrder.map((id) => state.elements[id]).filter(Boolean);
        },

        // Selection
        selectElement: (elementId: string, append = false): void => {
          if (!append) {
            selectedIds = [];
          }
          if (!selectedIds.includes(elementId)) {
            selectedIds.push(elementId);
          }

          // Update element selected state
          Object.values(state.elements).forEach((el) => {
            el.selected = selectedIds.includes(el.id);
          });

          persistState();
          emit('selection-changed', { selectedIds });
        },

        selectElements: (elementIds: readonly string[]): void => {
          selectedIds = Array.from(elementIds);

          // Update element selected state
          Object.values(state.elements).forEach((el) => {
            el.selected = selectedIds.includes(el.id);
          });

          persistState();
          emit('selection-changed', { selectedIds });
        },

        clearSelection: (): void => {
          selectedIds = [];

          // Update element selected state
          Object.values(state.elements).forEach((el) => {
            el.selected = false;
          });

          persistState();
          emit('selection-changed', { selectedIds: [] });
        },

        getSelection: (): readonly string[] => {
          return selectedIds;
        },

        // Viewport
        setViewport: (viewport: Partial<unknown>): void => {
          state.viewport = {
            ...state.viewport,
            ...viewport,
          };
          state.updatedAt = new Date();

          persistState();
          emit('viewport-changed', state.viewport);
        },

        panViewport: (delta: unknown): void => {
          state.viewport.center = {
            x: state.viewport.center.x + delta.x,
            y: state.viewport.center.y + delta.y,
          };
          state.updatedAt = new Date();

          persistState();
          emit('viewport-changed', state.viewport);
        },

        zoomViewport: (delta: number, center?: unknown): void => {
          state.viewport.zoom = Math.max(0.1, Math.min(5.0, state.viewport.zoom + delta));
          if (center) {
            state.viewport.center = center;
          }
          state.updatedAt = new Date();

          persistState();
          emit('viewport-changed', state.viewport);
        },

        fitToScreen: (): void => {
          // Calculate bounds of all elements
          const elements = api.getAllElements();
          if (elements.length === 0) return;

          const bounds = elements.reduce(
            (acc, el) => ({
              minX: Math.min(acc.minX, el.bounds.x),
              minY: Math.min(acc.minY, el.bounds.y),
              maxX: Math.max(acc.maxX, el.bounds.x + el.bounds.width),
              maxY: Math.max(acc.maxY, el.bounds.y + el.bounds.height),
            }),
            { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity }
          );

          const centerX = (bounds.minX + bounds.maxX) / 2;
          const centerY = (bounds.minY + bounds.maxY) / 2;
          const width = bounds.maxX - bounds.minX;
          const height = bounds.maxY - bounds.minY;

          const viewportWidth = 1200;
          const viewportHeight = 800;
          const scaleX = viewportWidth / width;
          const scaleY = viewportHeight / height;
          const newZoom = Math.min(scaleX, scaleY, 1.0) * 0.9;

          state.viewport = {
            center: { x: centerX, y: centerY },
            zoom: newZoom,
          };
          state.updatedAt = new Date();

          persistState();
          emit('viewport-changed', state.viewport);
        },

        getViewport: () => state.viewport,

        // State queries
        getDocument: () => state,
        getUIState: () => ({
          isDragging: false,
          isResizing: false,
          isSelecting: false,
          hoveredElementId: null,
        }),

        // Coordinate transforms
        screenToCanvas: (screenPoint: unknown) => {
          const viewportWidth = 1200;
          const viewportHeight = 800;
          return {
            x: (screenPoint.x - viewportWidth / 2) / state.viewport.zoom + state.viewport.center.x,
            y: (screenPoint.y - viewportHeight / 2) / state.viewport.zoom + state.viewport.center.y,
          };
        },

        canvasToScreen: (canvasPoint: unknown) => {
          const viewportWidth = 1200;
          const viewportHeight = 800;
          return {
            x: (canvasPoint.x - state.viewport.center.x) * state.viewport.zoom + viewportWidth / 2,
            y: (canvasPoint.y - state.viewport.center.y) * state.viewport.zoom + viewportHeight / 2,
          };
        },

        // Event bus
        on,
        off,

        // Document persistence
        saveDocument: async (): Promise<void> => {
          persistState();
        },

        loadDocument: async (): Promise<CanvasDocument> => {
          return state;
        },
      };

      return api;
    }

    /**
     * Clear all Canvas API state from localStorage
     */
    export function clearCanvasState(): void {
      try {
        localStorage.removeItem(STORAGE_KEY);
      } catch (err) {
        logger.error('Failed to clear Canvas state', 'canvas-api-factory', {
          error: err instanceof Error ? err.message : String(err)
        });
      }

      /**
       * Export Canvas state to JSON
       */
      export function exportCanvasState(): string {
        try {
          const stored = localStorage.getItem(STORAGE_KEY);
          return stored || '{}';
        } catch (err) {
          logger.error('Failed to export Canvas state', 'canvas-api-factory', {
            error: err instanceof Error ? err.message : String(err)
          });
        }
      }

      /**
       * Import Canvas state from JSON
       */
      export function importCanvasState(data: string): void {
        try {
          const parsed = JSON.parse(data);
          localStorage.setItem(STORAGE_KEY, JSON.stringify(parsed));
        } catch (err) {
          logger.error('Failed to import Canvas state', 'canvas-api-factory', {
            error: err instanceof Error ? err.message : String(err)
          });
        }
