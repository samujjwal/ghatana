/**
 * @module mocks
 * @description Mock implementations for testing canvas components.
 */

import { vi } from 'vitest';

import type { Node, Edge, ReactFlowInstance, XYPosition } from '@xyflow/react';

/**
 * Mock React Flow instance for testing
 */
export function createMockReactFlowInstance(
  overrides?: Partial<ReactFlowInstance>
): ReactFlowInstance {
  const defaultInstance = {
    // Viewport methods
    getZoom: vi.fn(() => 1),
    setViewport: vi.fn(),
    getViewport: vi.fn(() => ({ x: 0, y: 0, zoom: 1 })),
    fitView: vi.fn(),
    zoomIn: vi.fn(),
    zoomOut: vi.fn(),
    zoomTo: vi.fn(),
    setCenter: vi.fn(),
    fitBounds: vi.fn(),

    // Node methods
    getNodes: vi.fn(() => []),
    setNodes: vi.fn(),
    getNode: vi.fn(),
    addNodes: vi.fn(),

    // Edge methods
    getEdges: vi.fn(() => []),
    setEdges: vi.fn(),
    getEdge: vi.fn(),
    addEdges: vi.fn(),

    // Selection methods
    getIntersectingNodes: vi.fn(() => []),
    isNodeIntersecting: vi.fn(() => false),

    // Coordinate conversion
    screenToFlowPosition: vi.fn((pos: XYPosition) => pos),
    flowToScreenPosition: vi.fn((pos: XYPosition) => pos),
    project: vi.fn((pos: XYPosition) => pos),

    // Other methods
    deleteElements: vi.fn(),
    toObject: vi.fn(() => ({
      nodes: [],
      edges: [],
      viewport: { x: 0, y: 0, zoom: 1 },
    })),

    viewportInitialized: true,
  };

  return { ...defaultInstance, ...overrides } as unknown as ReactFlowInstance;
}

/**
 * Mock canvas node for testing
 */
export function createMockNode(
  overrides?: Partial<Node>
): Node {
  return {
    id: 'mock-node-1',
    type: 'default',
    position: { x: 0, y: 0 },
    data: { label: 'Mock Node' },
    ...overrides,
  };
}

/**
 * Mock canvas edge for testing
 */
export function createMockEdge(
  overrides?: Partial<Edge>
): Edge {
  return {
    id: 'mock-edge-1',
    source: 'node-1',
    target: 'node-2',
    ...overrides,
  };
}

/**
 * Mock multiple nodes for testing
 */
export function createMockNodes(count: number, baseId = 'node'): Node[] {
  return Array.from({ length: count }, (_, i) => createMockNode({
    id: `${baseId}-${i + 1}`,
    position: { x: i * 100, y: i * 100 },
    data: { label: `Node ${i + 1}` },
  }));
}

/**
 * Mock multiple edges for testing
 */
export function createMockEdges(
  nodeIds: string[],
  baseId = 'edge'
): Edge[] {
  const edges: Edge[] = [];

  for (let i = 0; i < nodeIds.length - 1; i++) {
    edges.push(createMockEdge({
      id: `${baseId}-${i + 1}`,
      source: nodeIds[i],
      target: nodeIds[i + 1],
    }));
  }

  return edges;
}

/**
 * Mock canvas state for testing
 */
export interface MockCanvasState {
  nodes: Node[];
  edges: Edge[];
  viewport: { x: number; y: number; zoom: number };
  selectedNodes: string[];
  selectedEdges: string[];
}

/**
 * Create mock canvas state
 */
export function createMockCanvasState(
  overrides?: Partial<MockCanvasState>
): MockCanvasState {
  return {
    nodes: [],
    edges: [],
    viewport: { x: 0, y: 0, zoom: 1 },
    selectedNodes: [],
    selectedEdges: [],
    ...overrides,
  };
}

/**
 * Mock drag event for testing
 */
export function createMockDragEvent(
  overrides?: Partial<DragEvent>
): DragEvent {
  const dataTransfer = {
    getData: vi.fn(() => ''),
    setData: vi.fn(),
    effectAllowed: 'all' as DataTransfer['effectAllowed'],
    dropEffect: 'move' as DataTransfer['dropEffect'],
    files: [] as unknown as FileList,
    items: [] as unknown as DataTransferItemList,
    types: [],
    clearData: vi.fn(),
    setDragImage: vi.fn(),
  };

  return {
    dataTransfer,
    clientX: 0,
    clientY: 0,
    preventDefault: vi.fn(),
    stopPropagation: vi.fn(),
    ...overrides,
  } as unknown as DragEvent;
}

/**
 * Mock pointer event for testing
 */
export function createMockPointerEvent(
  overrides?: Partial<PointerEvent>
): PointerEvent {
  return {
    clientX: 0,
    clientY: 0,
    button: 0,
    buttons: 1,
    pointerId: 1,
    pointerType: 'mouse',
    isPrimary: true,
    preventDefault: vi.fn(),
    stopPropagation: vi.fn(),
    ...overrides,
  } as unknown as PointerEvent;
}

/**
 * Mock intersection observer for testing
 */
export function createMockIntersectionObserver(): typeof IntersectionObserver {
  return class MockIntersectionObserver implements IntersectionObserver {
    readonly root: Element | null = null;
    readonly rootMargin: string = '';
    readonly thresholds: ReadonlyArray<number> = [];

    /**
     *
     */
    constructor(
      public callback: IntersectionObserverCallback,
      public options?: IntersectionObserverInit
    ) { }

    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
    takeRecords = vi.fn(() => []);
  };
}

/**
 * Mock resize observer for testing
 */
export function createMockResizeObserver(): typeof ResizeObserver {
  return class MockResizeObserver implements ResizeObserver {
    /**
     *
     */
    constructor(public callback: ResizeObserverCallback) { }

    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
  };
}

/**
 * Mock local storage for testing
 */
export function createMockLocalStorage(): Storage {
  let store: Record<string, string> = {};

  return {
    getItem: vi.fn((key: string) => store[key] || null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value;
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key];
    }),
    clear: vi.fn(() => {
      store = {};
    }),
    key: vi.fn((index: number) => {
      const keys = Object.keys(store);
      return keys[index] || null;
    }),
    get length() {
      return Object.keys(store).length;
    },
  };
}

/**
 * Setup global mocks for testing
 */
export function setupGlobalMocks(): void {
  // Mock IntersectionObserver
  global.IntersectionObserver = createMockIntersectionObserver();

  // Mock ResizeObserver
  global.ResizeObserver = createMockResizeObserver();

  // Mock localStorage
  Object.defineProperty(window, 'localStorage', {
    value: createMockLocalStorage(),
    writable: true,
  });

  // Mock sessionStorage
  Object.defineProperty(window, 'sessionStorage', {
    value: createMockLocalStorage(),
    writable: true,
  });

  // Mock matchMedia
  Object.defineProperty(window, 'matchMedia', {
    value: vi.fn((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
    writable: true,
  });
}

/**
 * Cleanup global mocks after testing
 */
export function cleanupGlobalMocks(): void {
  vi.clearAllMocks();
}
