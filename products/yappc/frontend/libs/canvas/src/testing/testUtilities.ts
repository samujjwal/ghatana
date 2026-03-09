/**
 * @module testUtilities
 * @description Shared test utilities for canvas testing including React Flow mocks,
 * Playwright helpers, test fixtures, and reusable test helpers
 *
 * Core Responsibilities:
 * - Provide React Flow mocks for unit/component testing
 * - Playwright helpers for E2E testing (getStoryUrl, waitForCanvasReady)
 * - Shared test fixtures for common canvas scenarios
 * - Helper functions for test setup and teardown
 *
 * Key Features:
 * - Mock implementations for React Flow hooks and components
 * - Storybook integration helpers
 * - Canvas readiness detection for E2E tests
 * - Fixture generators for nodes, edges, and layouts
 * - Snapshot utilities for visual regression testing
 *
 * Example Usage:
 * ```typescript
 * // React Flow mocks in unit tests
 * import { mockReactFlowHooks, createMockNode } from '@ghatana/yappc-canvas/testing';
 *
 * mockReactFlowHooks();
 * const node = createMockNode({ type: 'custom', data: { label: 'Test' } });
 *
 * // Playwright helpers in E2E tests
 * import { getStoryUrl, waitForCanvasReady } from '@ghatana/yappc-canvas/testing';
 *
 * await page.goto(getStoryUrl('Canvas', 'Default'));
 * await waitForCanvasReady(page);
 * ```
 */

import type { Page } from '@playwright/test';

/**
 * React Flow node mock
 */
export interface MockNode {
  id: string;
  type?: string;
  position: { x: number; y: number };
  data: Record<string, unknown>;
  style?: Record<string, unknown>;
  className?: string;
  draggable?: boolean;
  selectable?: boolean;
  connectable?: boolean;
  deletable?: boolean;
}

/**
 * React Flow edge mock
 */
export interface MockEdge {
  id: string;
  source: string;
  target: string;
  type?: string;
  data?: Record<string, unknown>;
  style?: Record<string, unknown>;
  animated?: boolean;
  label?: string;
  labelStyle?: Record<string, unknown>;
}

/**
 * Canvas fixture configuration
 */
export interface CanvasFixtureConfig {
  /**
   * Number of nodes to generate
   */
  nodeCount?: number;
  /**
   * Number of edges to generate
   */
  edgeCount?: number;
  /**
   * Layout type
   */
  layout?: 'grid' | 'tree' | 'force' | 'random';
  /**
   * Custom node types
   */
  nodeTypes?: string[];
  /**
   * Custom edge types
   */
  edgeTypes?: string[];
  /**
   * Seed for deterministic generation
   */
  seed?: number;
}

/**
 * Canvas readiness check configuration
 */
export interface CanvasReadinessConfig {
  /**
   * Timeout in milliseconds
   */
  timeout?: number;
  /**
   * Selector for canvas container
   */
  selector?: string;
  /**
   * Minimum number of nodes expected
   */
  minNodes?: number;
  /**
   * Wait for React Flow to be initialized
   */
  waitForReactFlow?: boolean;
}

/**
 * Storybook configuration
 */
export interface StoryConfig {
  /**
   * Base URL for Storybook
   */
  baseUrl?: string;
  /**
   * Storybook parameters
   */
  parameters?: Record<string, unknown>;
  /**
   * Viewport configuration
   */
  viewport?: {
    width: number;
    height: number;
  };
}

/**
 * React Flow hook mock state
 */
interface ReactFlowMockState {
  nodes: MockNode[];
  edges: MockEdge[];
  viewport: { x: number; y: number; zoom: number };
  selectedNodes: string[];
  selectedEdges: string[];
}

/**
 * Global mock state
 */
let globalMockState: ReactFlowMockState = {
  nodes: [],
  edges: [],
  viewport: { x: 0, y: 0, zoom: 1 },
  selectedNodes: [],
  selectedEdges: [],
};

/**
 * Test Utilities Manager
 * Centralized utilities for canvas testing
 */
export class TestUtilitiesManager {
  private config: StoryConfig;
  private mockState: ReactFlowMockState;

  /**
   *
   */
  constructor(config: StoryConfig = {}) {
    this.config = {
      baseUrl: process.env.STORYBOOK_URL || 'http://localhost:6006',
      ...config,
    };

    this.mockState = { ...globalMockState };
  }

  /**
   * Generate Storybook URL for a story
   */
  getStoryUrl(
    component: string,
    story: string,
    params?: Record<string, unknown>
  ): string {
    const baseUrl = this.config.baseUrl!;
    // Convert to kebab-case and remove spaces
    const componentPath = component.toLowerCase().replace(/\s+/g, '-');
    const storyPath = `iframe.html?id=${componentPath}--${story.toLowerCase().replace(/\s+/g, '-')}`;

    if (!params || Object.keys(params).length === 0) {
      return `${baseUrl}/${storyPath}`;
    }

    // Add query parameters
    const queryParams = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
      queryParams.set(key, JSON.stringify(value));
    }

    return `${baseUrl}/${storyPath}&${queryParams.toString()}`;
  }

  /**
   * Wait for canvas to be ready in Playwright tests
   */
  async waitForCanvasReady(
    page: Page,
    config: CanvasReadinessConfig = {}
  ): Promise<void> {
    const {
      timeout = 10000,
      selector = '[data-testid="canvas"]',
      minNodes = 0,
      waitForReactFlow = true,
    } = config;

    // Wait for canvas container
    await page.waitForSelector(selector, { timeout, state: 'visible' });

    // Wait for React Flow initialization
    if (waitForReactFlow) {
      await page.waitForFunction(
        () => {
          return (window as unknown as { ReactFlow?: unknown }).ReactFlow !== undefined;
        },
        { timeout }
      );
    }

    // Wait for minimum number of nodes
    if (minNodes > 0) {
      await page.waitForFunction(
        (count) => {
          const nodes = document.querySelectorAll('[data-testid^="node-"]');
          return nodes.length >= count;
        },
        minNodes,
        { timeout }
      );
    }

    // Wait for layout to settle (animations complete)
    await page.waitForTimeout(500);
  }

  /**
   * Create mock node
   */
  createMockNode(partial?: Partial<MockNode>): MockNode {
    const id = partial?.id || `node-${Date.now()}-${Math.random()}`;

    return {
      id,
      type: partial?.type || 'default',
      position: partial?.position || { x: 0, y: 0 },
      data: partial?.data || {},
      style: partial?.style,
      className: partial?.className,
      draggable: partial?.draggable ?? true,
      selectable: partial?.selectable ?? true,
      connectable: partial?.connectable ?? true,
      deletable: partial?.deletable ?? true,
    };
  }

  /**
   * Create mock edge
   */
  createMockEdge(partial?: Partial<MockEdge>): MockEdge {
    const id = partial?.id || `edge-${Date.now()}-${Math.random()}`;

    return {
      id,
      source: partial?.source || 'node-1',
      target: partial?.target || 'node-2',
      type: partial?.type || 'default',
      data: partial?.data,
      style: partial?.style,
      animated: partial?.animated ?? false,
      label: partial?.label,
      labelStyle: partial?.labelStyle,
    };
  }

  /**
   * Create canvas fixture
   */
  createCanvasFixture(config: CanvasFixtureConfig = {}): {
    nodes: MockNode[];
    edges: MockEdge[];
  } {
    const {
      nodeCount = 5,
      edgeCount = 4,
      layout = 'grid',
      nodeTypes = ['default'],
      edgeTypes = ['default'],
      seed,
    } = config;

    // Use seed for deterministic generation
    const random = seed !== undefined ? this.seededRandom(seed) : Math.random;

    // Generate nodes
    const nodes: MockNode[] = [];
    for (let i = 0; i < nodeCount; i++) {
      const position = this.calculatePosition(i, nodeCount, layout, random);
      const type = nodeTypes[Math.floor(random() * nodeTypes.length)];

      nodes.push(
        this.createMockNode({
          id: `node-${i + 1}`,
          type,
          position,
          data: {
            label: `Node ${i + 1}`,
          },
        })
      );
    }

    // Generate edges
    const edges: MockEdge[] = [];
    for (let i = 0; i < Math.min(edgeCount, nodeCount - 1); i++) {
      const source = `node-${i + 1}`;
      const target = `node-${i + 2}`;
      const type = edgeTypes[Math.floor(random() * edgeTypes.length)];

      edges.push(
        this.createMockEdge({
          id: `edge-${i + 1}`,
          source,
          target,
          type,
        })
      );
    }

    return { nodes, edges };
  }

  /**
   * Calculate position based on layout
   */
  private calculatePosition(
    index: number,
    total: number,
    layout: 'grid' | 'tree' | 'force' | 'random',
    random: () => number
  ): { x: number; y: number } {
    switch (layout) {
      case 'grid': {
        const cols = Math.ceil(Math.sqrt(total));
        const row = Math.floor(index / cols);
        const col = index % cols;
        return {
          x: col * 200,
          y: row * 100,
        };
      }

      case 'tree': {
        const level = Math.floor(Math.log2(index + 1));
        const posInLevel = index - (Math.pow(2, level) - 1);
        const levelWidth = Math.pow(2, level);
        return {
          x: (posInLevel * 400) / levelWidth,
          y: level * 150,
        };
      }

      case 'force': {
        const angle = (index / total) * 2 * Math.PI;
        const radius = 200;
        return {
          x: Math.cos(angle) * radius + 400,
          y: Math.sin(angle) * radius + 300,
        };
      }

      case 'random':
      default:
        return {
          x: random() * 800,
          y: random() * 600,
        };
    }
  }

  /**
   * Seeded random number generator
   */
  private seededRandom(seed: number): () => number {
    let state = seed;
    return () => {
      state = (state * 9301 + 49297) % 233280;
      return state / 233280;
    };
  }

  /**
   * Mock React Flow hooks
   */
  mockReactFlowHooks(): void {
    this.mockState = { ...globalMockState };

    // Mock useReactFlow
    (globalThis as unknown as { useReactFlow?: () => unknown }).useReactFlow = () => ({
      getNodes: () => [...this.mockState.nodes],
      getEdges: () => [...this.mockState.edges],
      setNodes: (nodes: MockNode[]) => {
        this.mockState.nodes = [...nodes];
      },
      setEdges: (edges: MockEdge[]) => {
        this.mockState.edges = [...edges];
      },
      getNode: (id: string) => this.mockState.nodes.find((n) => n.id === id),
      getEdge: (id: string) => this.mockState.edges.find((e) => e.id === id),
      addNodes: (nodes: MockNode[]) => {
        this.mockState.nodes.push(...nodes);
      },
      addEdges: (edges: MockEdge[]) => {
        this.mockState.edges.push(...edges);
      },
      deleteElements: ({
        nodes,
        edges,
      }: {
        nodes?: { id: string }[];
        edges?: { id: string }[];
      }) => {
        if (nodes) {
          const nodeIds = new Set(nodes.map((n) => n.id));
          this.mockState.nodes = this.mockState.nodes.filter(
            (n) => !nodeIds.has(n.id)
          );
        }
        if (edges) {
          const edgeIds = new Set(edges.map((e) => e.id));
          this.mockState.edges = this.mockState.edges.filter(
            (e) => !edgeIds.has(e.id)
          );
        }
      },
      fitView: () => {},
      zoomIn: () => {
        this.mockState.viewport.zoom *= 1.2;
      },
      zoomOut: () => {
        this.mockState.viewport.zoom /= 1.2;
      },
      setViewport: (viewport: { x: number; y: number; zoom: number }) => {
        this.mockState.viewport = { ...viewport };
      },
      getViewport: () => ({ ...this.mockState.viewport }),
      project: (position: { x: number; y: number }) => position,
      screenToFlowPosition: (position: { x: number; y: number }) => position,
    });

    // Mock useNodes
    (globalThis as unknown as { useNodes?: () => MockNode[] }).useNodes = () => [
      ...this.mockState.nodes,
    ];

    // Mock useEdges
    (globalThis as unknown as { useEdges?: () => MockEdge[] }).useEdges = () => [
      ...this.mockState.edges,
    ];

    // Mock useViewport
    (globalThis as unknown as { useViewport?: () => { x: number; y: number; zoom: number } }).useViewport = () => ({
      ...this.mockState.viewport,
    });
  }

  /**
   * Reset mock state
   */
  resetMocks(): void {
    this.mockState = {
      nodes: [],
      edges: [],
      viewport: { x: 0, y: 0, zoom: 1 },
      selectedNodes: [],
      selectedEdges: [],
    };
    globalMockState = { ...this.mockState };
  }

  /**
   * Get current mock state
   */
  getMockState(): ReactFlowMockState {
    return { ...this.mockState };
  }

  /**
   * Set mock state
   */
  setMockState(state: Partial<ReactFlowMockState>): void {
    this.mockState = {
      ...this.mockState,
      ...state,
    };
    globalMockState = { ...this.mockState };
  }

  /**
   * Wait for selector in Playwright
   */
  async waitForSelector(
    page: Page,
    selector: string,
    options?: { timeout?: number; state?: 'attached' | 'visible' | 'hidden' }
  ): Promise<void> {
    await page.waitForSelector(selector, {
      timeout: options?.timeout || 5000,
      state: options?.state || 'visible',
    });
  }

  /**
   * Take screenshot with consistent viewport
   */
  async takeScreenshot(
    page: Page,
    name: string,
    options?: {
      fullPage?: boolean;
      clip?: { x: number; y: number; width: number; height: number };
    }
  ): Promise<void> {
    await page.screenshot({
      path: `screenshots/${name}.png`,
      fullPage: options?.fullPage ?? false,
      clip: options?.clip,
    });
  }

  /**
   * Get element bounding box
   */
  async getElementBounds(
    page: Page,
    selector: string
  ): Promise<{ x: number; y: number; width: number; height: number } | null> {
    const element = await page.$(selector);
    if (!element) return null;

    return element.boundingBox();
  }

  /**
   * Simulate drag and drop
   */
  async dragAndDrop(
    page: Page,
    sourceSelector: string,
    targetSelector: string,
    options?: {
      sourcePosition?: { x: number; y: number };
      targetPosition?: { x: number; y: number };
    }
  ): Promise<void> {
    const source = await page.$(sourceSelector);
    const target = await page.$(targetSelector);

    if (!source || !target) {
      throw new Error('Source or target element not found');
    }

    const sourceBounds = await source.boundingBox();
    const targetBounds = await target.boundingBox();

    if (!sourceBounds || !targetBounds) {
      throw new Error('Could not get element bounds');
    }

    const sourceX = sourceBounds.x + (options?.sourcePosition?.x ?? sourceBounds.width / 2);
    const sourceY = sourceBounds.y + (options?.sourcePosition?.y ?? sourceBounds.height / 2);
    const targetX = targetBounds.x + (options?.targetPosition?.x ?? targetBounds.width / 2);
    const targetY = targetBounds.y + (options?.targetPosition?.y ?? targetBounds.height / 2);

    await page.mouse.move(sourceX, sourceY);
    await page.mouse.down();
    await page.mouse.move(targetX, targetY, { steps: 10 });
    await page.mouse.up();
  }

  /**
   * Wait for network idle
   */
  async waitForNetworkIdle(
    page: Page,
    options?: { timeout?: number; idleTime?: number }
  ): Promise<void> {
    await page.waitForLoadState('networkidle', {
      timeout: options?.timeout || 10000,
    });

    // Additional wait for stability
    await page.waitForTimeout(options?.idleTime || 500);
  }

  /**
   * Get configuration
   */
  getConfig(): StoryConfig {
    return { ...this.config };
  }

  /**
   * Update configuration
   */
  updateConfig(config: Partial<StoryConfig>): void {
    this.config = {
      ...this.config,
      ...config,
    };
  }
}

/**
 * Standalone helper functions for convenience
 */

/**
 * Create mock node (standalone)
 */
export function createMockNode(partial?: Partial<MockNode>): MockNode {
  const manager = new TestUtilitiesManager();
  return manager.createMockNode(partial);
}

/**
 * Create mock edge (standalone)
 */
export function createMockEdge(partial?: Partial<MockEdge>): MockEdge {
  const manager = new TestUtilitiesManager();
  return manager.createMockEdge(partial);
}

/**
 * Create canvas fixture (standalone)
 */
export function createCanvasFixture(
  config?: CanvasFixtureConfig
): { nodes: MockNode[]; edges: MockEdge[] } {
  const manager = new TestUtilitiesManager();
  return manager.createCanvasFixture(config);
}

/**
 * Get Storybook URL (standalone)
 */
export function getStoryUrl(
  component: string,
  story: string,
  params?: Record<string, unknown>
): string {
  const manager = new TestUtilitiesManager();
  return manager.getStoryUrl(component, story, params);
}

/**
 * Wait for canvas ready (standalone)
 */
export async function waitForCanvasReady(
  page: Page,
  config?: CanvasReadinessConfig
): Promise<void> {
  const manager = new TestUtilitiesManager();
  return manager.waitForCanvasReady(page, config);
}

/**
 * Mock React Flow hooks (standalone)
 */
export function mockReactFlowHooks(): void {
  const manager = new TestUtilitiesManager();
  manager.mockReactFlowHooks();
}

/**
 * Reset mocks (standalone)
 */
export function resetMocks(): void {
  globalMockState = {
    nodes: [],
    edges: [],
    viewport: { x: 0, y: 0, zoom: 1 },
    selectedNodes: [],
    selectedEdges: [],
  };
}
