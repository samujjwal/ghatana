/**
 * Canvas Context API - Composable CanvasFlow Component
 *
 * Provides a flexible, type-safe API for embedding canvas functionality.
 * Supports both controlled and uncontrolled modes with normalized callbacks.
 *
 * Features:
 * - Controlled/uncontrolled state management
 * - Normalized event callbacks with consistent payloads
 * - Type-safe generics for custom data schemas
 * - Context-based state sharing
 * - Performance optimized re-renders
 *
 * @module api/canvasContext
 */

import { useAtom, useAtomValue, useSetAtom, Provider as JotaiProvider } from 'jotai';
import {
  createContext,
  useContext,
  useCallback,
  useMemo,
  useRef,
  useEffect,
  type ReactNode,
} from 'react';
import type { JSX } from 'react';

import {
  canvasDocumentAtom,
  canvasSelectionAtom,
  canvasViewportAtom,
  canvasUIStateAtom,
  updateElementAtom,
  addElementAtom,
  removeElementAtom,
} from '../state';

import { Canvas } from '../components';

import type {
  CanvasDocument,
  CanvasElement,
  CanvasSelection,
  CanvasViewport,
  CanvasUIState,
  Point,
} from '../types/canvas-document';

/**
 * Normalized event payload types for consistent callback signatures
 */

/**
 *
 */
export interface CanvasChangeEvent<T = CanvasDocument> {
  readonly document: T;
  readonly changes: readonly CanvasChange[];
  readonly timestamp: number;
}

/**
 *
 */
export interface CanvasChange {
  readonly type: 'element-added' | 'element-removed' | 'element-updated' | 'viewport-changed' | 'selection-changed';
  readonly elementId?: string;
  readonly oldValue?: unknown;
  readonly newValue?: unknown;
}

/**
 *
 */
export interface CanvasElementEvent {
  readonly element: CanvasElement;
  readonly position: Point;
  readonly timestamp: number;
}

/**
 *
 */
export interface CanvasSelectionEvent {
  readonly selectedIds: readonly string[];
  readonly previousIds: readonly string[];
  readonly timestamp: number;
}

/**
 *
 */
export interface CanvasViewportEvent {
  readonly viewport: CanvasViewport;
  readonly previousViewport: CanvasViewport;
  readonly timestamp: number;
}

/**
 *
 */
export interface CanvasInteractionEvent {
  readonly type: 'drag' | 'pan' | 'zoom' | 'select';
  readonly position: Point;
  readonly delta?: Point;
  readonly timestamp: number;
}

/**
 * Canvas API methods exposed through context
 */
export interface CanvasAPI {
  // Element manipulation
  readonly addElement: (element: Partial<CanvasElement>) => string;
  readonly removeElement: (elementId: string) => void;
  readonly updateElement: (elementId: string, updates: Partial<CanvasElement>) => void;
  readonly getElement: (elementId: string) => CanvasElement | undefined;
  readonly getAllElements: () => readonly CanvasElement[];

  // Selection
  readonly selectElement: (elementId: string, append?: boolean) => void;
  readonly selectElements: (elementIds: readonly string[]) => void;
  readonly clearSelection: () => void;
  readonly getSelection: () => readonly string[];

  // Viewport
  readonly setViewport: (viewport: Partial<CanvasViewport>) => void;
  readonly panViewport: (delta: Point) => void;
  readonly zoomViewport: (delta: number, center?: Point) => void;
  readonly fitToScreen: () => void;
  readonly getViewport: () => CanvasViewport;

  // State queries
  readonly getDocument: () => CanvasDocument;
  readonly getUIState: () => CanvasUIState;

  // Utilities
  readonly screenToCanvas: (screenPoint: Point) => Point;
  readonly canvasToScreen: (canvasPoint: Point) => Point;

  // Events & persistence
  readonly on?: (eventName: string, handler: (payload: unknown) => void) => () => void;
  readonly off?: (eventName: string, handler: (payload: unknown) => void) => void;
  readonly emit?: (eventName: string, payload: unknown) => void;
  readonly saveDocument?: (document: CanvasDocument) => Promise<void>;
}

/**
 * Canvas context value including state and API methods
 */
export interface CanvasContextValue {
  readonly document: CanvasDocument;
  readonly selection: CanvasSelection;
  readonly viewport: CanvasViewport;
  readonly uiState: CanvasUIState;
  readonly api: CanvasAPI;
  readonly isControlled: boolean;
}

/**
 * Canvas provider props with controlled/uncontrolled modes
 */
export interface CanvasProviderProps<TDocument extends CanvasDocument = CanvasDocument> {
  /** Child components */
  readonly children: ReactNode;

  /** Controlled mode: external document state */
  readonly document?: TDocument;

  /** Controlled mode: document change callback */
  readonly onDocumentChange?: (event: CanvasChangeEvent<TDocument>) => void;

  /** Controlled mode: external selection */
  readonly selectedIds?: readonly string[];

  /** Selection change callback */
  readonly onSelectionChange?: (event: CanvasSelectionEvent) => void;

  /** Controlled mode: external viewport */
  readonly viewport?: CanvasViewport;

  /** Viewport change callback */
  readonly onViewportChange?: (event: CanvasViewportEvent) => void;

  /** Element interaction callbacks */
  readonly onElementClick?: (event: CanvasElementEvent) => void;
  readonly onElementDoubleClick?: (event: CanvasElementEvent) => void;
  readonly onElementDragStart?: (event: CanvasElementEvent) => void;
  readonly onElementDrag?: (event: CanvasElementEvent) => void;
  readonly onElementDragEnd?: (event: CanvasElementEvent) => void;

  /** Canvas interaction callbacks */
  readonly onCanvasClick?: (event: CanvasInteractionEvent) => void;
  readonly onCanvasPan?: (event: CanvasInteractionEvent) => void;
  readonly onCanvasZoom?: (event: CanvasInteractionEvent) => void;

  /** Initial document for uncontrolled mode */
  readonly initialDocument?: TDocument;

  /** Performance: disable change tracking */
  readonly disableChangeTracking?: boolean;

  /** Debug mode */
  readonly debug?: boolean;
}

// Create context with undefined default (must be used within provider)
const CanvasContext = createContext<CanvasContextValue | undefined>(undefined);

/**
 * CanvasProvider - Context provider for canvas state and API
 *
 * Supports both controlled and uncontrolled modes:
 * - **Controlled**: Pass `document`, `selectedIds`, `viewport` props
 * - **Uncontrolled**: Pass `initialDocument`, state managed internally
 *
 * @example
 * // Controlled mode
 * <CanvasProvider
 *   document={myDocument}
 *   onDocumentChange={(e) => setMyDocument(e.document)}
 * >
 *   <CanvasFlow />
 * </CanvasProvider>
 *
 * @example
 * // Uncontrolled mode
 * <CanvasProvider initialDocument={myDocument}>
 *   <CanvasFlow />
 * </CanvasProvider>
 */
export function CanvasProvider<TDocument extends CanvasDocument = CanvasDocument>({
  children,
  document: controlledDocument,
  onDocumentChange,
  selectedIds: controlledSelectedIds,
  onSelectionChange,
  viewport: controlledViewport,
  onViewportChange,
  onElementClick,
  onElementDoubleClick,
  onElementDragStart,
  onElementDrag,
  onElementDragEnd,
  onCanvasClick,
  onCanvasPan,
  onCanvasZoom,
  initialDocument,
  disableChangeTracking = false,
  debug = false,
}: CanvasProviderProps<TDocument>): JSX.Element {
  // Determine if component is in controlled mode
  const isControlled = controlledDocument !== undefined;

  // Jotai atoms for state management
  const [internalDocument, setInternalDocument] = useAtom(canvasDocumentAtom);
  const [internalSelection, setInternalSelection] = useAtom(canvasSelectionAtom);
  const [internalViewport, setInternalViewport] = useAtom(canvasViewportAtom);
  const internalUIState = useAtomValue(canvasUIStateAtom);
  const updateElement = useSetAtom(updateElementAtom);
  const addElement = useSetAtom(addElementAtom);
  const removeElement = useSetAtom(removeElementAtom);

  // Track previous values for change detection
  const prevDocumentRef = useRef<CanvasDocument | undefined>(undefined);
  const prevSelectionRef = useRef<readonly string[]>([]);
  const prevViewportRef = useRef<CanvasViewport | undefined>(undefined);
  const changeBufferRef = useRef<CanvasChange[]>([]);
  const eventListenersRef = useRef<Map<string, Set<(payload: unknown) => void>>>(new Map());

  const emitEvent = useCallback((eventName: string, payload: unknown) => {
    const listeners = eventListenersRef.current.get(eventName);
    if (!listeners || listeners.size === 0) {
      return;
    }

    listeners.forEach((listener) => {
      try {
        listener(payload);
      } catch (error) {
        console.error(`[CanvasProvider] Error in listener for ${eventName}:`, error);
      }
    });
  }, []);

  const registerEventListener = useCallback(
    (eventName: string, handler: (payload: unknown) => void) => {
      const listeners = eventListenersRef.current.get(eventName) ?? new Set();
      listeners.add(handler);
      eventListenersRef.current.set(eventName, listeners);

      return () => {
        listeners.delete(handler);
        if (listeners.size === 0) {
          eventListenersRef.current.delete(eventName);
        }
      };
    },
    []
  );

  const unregisterEventListener = useCallback(
    (eventName: string, handler: (payload: unknown) => void) => {
      const listeners = eventListenersRef.current.get(eventName);
      if (!listeners) return;

      listeners.delete(handler);
      if (listeners.size === 0) {
        eventListenersRef.current.delete(eventName);
      }
    },
    []
  );

  // Use controlled or internal state
  const document = (isControlled ? controlledDocument : internalDocument) as TDocument;
  const selectedIds = controlledSelectedIds ?? internalSelection.selectedIds;
  const viewport = controlledViewport ?? internalViewport;

  // Initialize internal state on mount if initial document provided
  useEffect(() => {
    if (initialDocument && !isControlled) {
      setInternalDocument(initialDocument);
      if (debug) {
        console.log('[CanvasProvider] Initialized with document:', initialDocument.id);
      }
    }
  }, [initialDocument, isControlled, setInternalDocument, debug]);

  /**
   * Emit document change event with accumulated changes
   */
  const emitDocumentChange = useCallback(
    (newDocument: TDocument) => {
      if (!onDocumentChange || disableChangeTracking) return;

      const changes = [...changeBufferRef.current];
      changeBufferRef.current = [];

      const event: CanvasChangeEvent<TDocument> = {
        document: newDocument,
        changes,
        timestamp: Date.now(),
      };

      onDocumentChange(event);
      emitEvent('document:changed', event);

      if (debug) {
        console.log('[CanvasProvider] Document changed:', changes.length, 'changes');
      }
    },
    [onDocumentChange, disableChangeTracking, debug, emitEvent]
  );

  /**
   * Track document changes
   */
  useEffect(() => {
    if (prevDocumentRef.current && prevDocumentRef.current !== document) {
      emitDocumentChange(document);
    }
    prevDocumentRef.current = document;
  }, [document, emitDocumentChange]);

  /**
   * Emit selection change event
   */
  const emitSelectionChange = useCallback(
    (newSelectedIds: readonly string[]) => {
      if (!onSelectionChange) return;

      const event: CanvasSelectionEvent = {
        selectedIds: newSelectedIds,
        previousIds: prevSelectionRef.current,
        timestamp: Date.now(),
      };

      onSelectionChange(event);
      emitEvent('selection:changed', event);

      if (debug) {
        console.log('[CanvasProvider] Selection changed:', newSelectedIds);
      }
    },
    [onSelectionChange, debug, emitEvent]
  );

  /**
   * Track selection changes
   */
  useEffect(() => {
    if (
      prevSelectionRef.current.length !== selectedIds.length ||
      prevSelectionRef.current.some((id, i) => id !== selectedIds[i])
    ) {
      emitSelectionChange(selectedIds);
      prevSelectionRef.current = selectedIds;
    }
  }, [selectedIds, emitSelectionChange]);

  /**
   * Emit viewport change event
   */
  const emitViewportChange = useCallback(
    (newViewport: CanvasViewport) => {
      if (!onViewportChange) return;

      const event: CanvasViewportEvent = {
        viewport: newViewport,
        previousViewport: prevViewportRef.current ?? viewport,
        timestamp: Date.now(),
      };

      onViewportChange(event);
      emitEvent('viewport:changed', event);

      if (debug) {
        console.log('[CanvasProvider] Viewport changed:', newViewport);
      }
    },
    [onViewportChange, viewport, debug, emitEvent]
  );

  /**
   * Track viewport changes
   */
  useEffect(() => {
    if (prevViewportRef.current && prevViewportRef.current !== viewport) {
      emitViewportChange(viewport);
    }
    prevViewportRef.current = viewport;
  }, [viewport, emitViewportChange]);

  /**
   * API methods implementation
   */
  const api: CanvasAPI = useMemo(
    () => ({
      // Element manipulation
      addElement: (elementData: Partial<CanvasElement>) => {
        const id = elementData.id || `element-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;

        if (!disableChangeTracking) {
          changeBufferRef.current.push({
            type: 'element-added',
            elementId: id,
            newValue: elementData,
            timestamp: Date.now(),
          } as CanvasChange);
        }

        if (isControlled) {
          // In controlled mode, emit change and let parent handle update
          emitDocumentChange({
            ...document,
            elements: {
              ...document.elements,
              [id]: elementData as CanvasElement,
            },
            elementOrder: [...document.elementOrder, id],
          });
        } else {
          // In uncontrolled mode, update internal state
          addElement(elementData as CanvasElement);
        }

        return id;
      },

      removeElement: (elementId: string) => {
        if (!disableChangeTracking) {
          changeBufferRef.current.push({
            type: 'element-removed',
            elementId,
            oldValue: document.elements[elementId],
            timestamp: Date.now(),
          } as CanvasChange);
        }

        if (isControlled) {
          const newElements = { ...document.elements };
          delete newElements[elementId];

          emitDocumentChange({
            ...document,
            elements: newElements,
            elementOrder: document.elementOrder.filter((id) => id !== elementId),
          });
        } else {
          removeElement(elementId);
        }
      },

      updateElement: (elementId: string, updates: Partial<CanvasElement>) => {
        if (!disableChangeTracking) {
          changeBufferRef.current.push({
            type: 'element-updated',
            elementId,
            oldValue: document.elements[elementId],
            newValue: updates,
            timestamp: Date.now(),
          } as CanvasChange);
        }

        if (isControlled) {
          emitDocumentChange({
            ...document,
            elements: {
              ...document.elements,
              [elementId]: {
                ...document.elements[elementId],
                ...updates,
              },
            },
          });
        } else {
          updateElement({ id: elementId, changes: updates });
        }
      },

      getElement: (elementId: string) => {
        return document.elements[elementId];
      },

      getAllElements: () => {
        return document.elementOrder.map((id) => document.elements[id]);
      },

      // Selection
      selectElement: (elementId: string, append = false) => {
        const newSelection = append ? [...selectedIds, elementId] : [elementId];

        if (isControlled) {
          emitSelectionChange(newSelection);
        } else {
          setInternalSelection({ selectedIds: newSelection });
        }
      },

      selectElements: (elementIds: readonly string[]) => {
        if (isControlled) {
          emitSelectionChange(elementIds);
        } else {
          setInternalSelection({ selectedIds: [...elementIds] });
        }
      },

      clearSelection: () => {
        if (isControlled) {
          emitSelectionChange([]);
        } else {
          setInternalSelection({ selectedIds: [] });
        }
      },

      getSelection: () => selectedIds,

      // Viewport
      setViewport: (viewportUpdates: Partial<CanvasViewport>) => {
        const newViewport = { ...viewport, ...viewportUpdates };

        if (isControlled) {
          emitViewportChange(newViewport);
        } else {
          setInternalViewport(newViewport);
        }
      },

      panViewport: (delta: Point) => {
        const newViewport = {
          ...viewport,
          center: {
            x: viewport.center.x + delta.x,
            y: viewport.center.y + delta.y,
          },
        };

        if (isControlled) {
          emitViewportChange(newViewport);
        } else {
          setInternalViewport(newViewport);
        }

        if (onCanvasPan) {
          onCanvasPan({
            type: 'pan',
            position: viewport.center,
            delta,
            timestamp: Date.now(),
          });
        }
      },

      zoomViewport: (delta: number, center?: Point) => {
        const newZoom = Math.max(0.1, Math.min(5.0, viewport.zoom + delta));
        const newViewport = {
          ...viewport,
          zoom: newZoom,
          ...(center && { center }),
        };

        if (isControlled) {
          emitViewportChange(newViewport);
        } else {
          setInternalViewport(newViewport);
        }

        if (onCanvasZoom) {
          onCanvasZoom({
            type: 'zoom',
            position: center || viewport.center,
            timestamp: Date.now(),
          });
        }
      },

      fitToScreen: () => {
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

        const viewportBounds = viewport.bounds || { width: 1200, height: 800, x: 0, y: 0 };
        const scaleX = viewportBounds.width / width;
        const scaleY = viewportBounds.height / height;
        const newZoom = Math.min(scaleX, scaleY, 1.0) * 0.9; // 90% to add padding

        const newViewport = {
          ...viewport,
          center: { x: centerX, y: centerY },
          zoom: newZoom,
        };

        if (isControlled) {
          emitViewportChange(newViewport);
        } else {
          setInternalViewport(newViewport);
        }
      },

      getViewport: () => viewport,

      // State queries
      getDocument: () => document,
      getUIState: () => internalUIState,

      // Coordinate transforms
      screenToCanvas: (screenPoint: Point): Point => {
        const viewportBounds = viewport.bounds || { x: 0, y: 0, width: 1200, height: 800 };
        return {
          x: (screenPoint.x - viewportBounds.width / 2) / viewport.zoom + viewport.center.x,
          y: (screenPoint.y - viewportBounds.height / 2) / viewport.zoom + viewport.center.y,
        };
      },

      canvasToScreen: (canvasPoint: Point): Point => {
        const viewportBounds = viewport.bounds || { x: 0, y: 0, width: 1200, height: 800 };
        return {
          x: (canvasPoint.x - viewport.center.x) * viewport.zoom + viewportBounds.width / 2,
          y: (canvasPoint.y - viewport.center.y) * viewport.zoom + viewportBounds.height / 2,
        };
      },

      on: (eventName, handler) => registerEventListener(eventName, handler),
      off: (eventName, handler) => unregisterEventListener(eventName, handler),
      emit: (eventName, payload) => emitEvent(eventName, payload),
      saveDocument: async (doc) => {
        emitDocumentChange(doc as TDocument);
      },
    }),
    [
      document,
      selectedIds,
      viewport,
      internalUIState,
      isControlled,
      disableChangeTracking,
      emitDocumentChange,
      emitSelectionChange,
      emitViewportChange,
      onCanvasPan,
      onCanvasZoom,
      setInternalDocument,
      setInternalSelection,
      setInternalViewport,
      updateElement,
      addElement,
      removeElement,
      registerEventListener,
      unregisterEventListener,
      emitEvent,
    ]
  );

  // Context value
  const contextValue: CanvasContextValue = useMemo(
    () => ({
      document,
      selection: { selectedIds: [...selectedIds] },
      viewport,
      uiState: internalUIState,
      api,
      isControlled,
    }),
    [document, selectedIds, viewport, internalUIState, api, isControlled]
  );

  return (
    <JotaiProvider>
      <CanvasContext.Provider value={contextValue}>{children}</CanvasContext.Provider>
    </JotaiProvider>
  );
}

/**
 * useCanvas - Hook to access canvas context and API
 *
 * Must be used within a CanvasProvider.
 *
 * @example
 * function MyComponent() {
 *   const { api, document } = useCanvas();
 *   
 *   const handleAddNode = () => {
 *     api.addElement({
 *       type: 'node',
 *       // ...
 *     });
 *   };
 *   
 *   return <button onClick={handleAddNode}>Add Node</button>;
 * }
 */
export function useCanvas(): CanvasContextValue {
  const context = useContext(CanvasContext);

  if (!context) {
    throw new Error('useCanvas must be used within a CanvasProvider');
  }

  return context;
}

/**
 * CanvasFlow props - omits children from provider props
 */
export type CanvasFlowProps<TDocument extends CanvasDocument = CanvasDocument> = Omit<
  CanvasProviderProps<TDocument>,
  'children'
> & {
  /** Canvas width */
  readonly width?: number;
  /** Canvas height */
  readonly height?: number;
  /** Custom CSS class */
  readonly className?: string;
  /** ARIA label */
  readonly ariaLabel?: string;
};

/**
 * CanvasFlow - Main composable canvas component
 *
 * High-level component that combines CanvasProvider with the Canvas component.
 * Provides a simple, ergonomic API for embedding canvas functionality.
 *
 * @example
 * // Simple uncontrolled usage
 * <CanvasFlow
 *   initialDocument={myDocument}
 *   onSelectionChange={(e) => console.log('Selected:', e.selectedIds)}
 * />
 *
 * @example
 * // Fully controlled
 * <CanvasFlow
 *   document={myDocument}
 *   onDocumentChange={(e) => setMyDocument(e.document)}
 *   selectedIds={selectedIds}
 *   onSelectionChange={(e) => setSelectedIds(e.selectedIds)}
 * />
 */
export function CanvasFlow<TDocument extends CanvasDocument = CanvasDocument>(
  props: CanvasFlowProps<TDocument>
): JSX.Element {
  const { width = 1200, height = 800, className, ariaLabel, ...providerProps } = props;

  return (
    <CanvasProvider {...providerProps}>
      <CanvasFlowInner width={width} height={height} className={className} ariaLabel={ariaLabel} />
    </CanvasProvider>
  );
}

/**
 * Internal component that uses the canvas context
 */
function CanvasFlowInner({
  width,
  height,
  className,
  ariaLabel,
}: {
  width: number;
  height: number;
  className?: string;
  ariaLabel?: string;
}): JSX.Element {
  const { document } = useCanvas();

  return <Canvas document={document} width={width} height={height} className={className} ariaLabel={ariaLabel} />;
}
