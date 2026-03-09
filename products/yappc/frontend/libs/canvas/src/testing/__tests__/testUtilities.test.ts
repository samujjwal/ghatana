/**
 * @jest-environment node
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';

import {
  TestUtilitiesManager,
  createMockNode,
  createMockEdge,
  createCanvasFixture,
  getStoryUrl,
  mockReactFlowHooks,
  resetMocks,
  type MockNode,
  type MockEdge,
} from '../testUtilities';

describe('TestUtilitiesManager', () => {
  let manager: TestUtilitiesManager;

  beforeEach(() => {
    manager = new TestUtilitiesManager();
    resetMocks();
  });

  describe('Initialization', () => {
    it('should initialize with default configuration', () => {
      const config = manager.getConfig();
      expect(config.baseUrl).toBeDefined();
    });

    it('should accept custom configuration', () => {
      const customManager = new TestUtilitiesManager({
        baseUrl: 'http://custom:8080',
        viewport: { width: 1920, height: 1080 },
      });

      const config = customManager.getConfig();
      expect(config.baseUrl).toBe('http://custom:8080');
      expect(config.viewport?.width).toBe(1920);
    });

    it('should use STORYBOOK_URL from environment if available', () => {
      const originalEnv = process.env.STORYBOOK_URL;
      process.env.STORYBOOK_URL = 'http://env:9000';

      const envManager = new TestUtilitiesManager();
      const config = envManager.getConfig();
      expect(config.baseUrl).toBe('http://env:9000');

      // Restore
      if (originalEnv) {
        process.env.STORYBOOK_URL = originalEnv;
      } else {
        delete process.env.STORYBOOK_URL;
      }
    });
  });

  describe('Storybook URL Generation', () => {
    it('should generate basic story URL', () => {
      const url = manager.getStoryUrl('Canvas', 'Default');
      expect(url).toContain('canvas--default');
      expect(url).toContain('iframe.html');
    });

    it('should handle component names with spaces', () => {
      const url = manager.getStoryUrl('Canvas Scene', 'With Nodes');
      expect(url).toContain('canvas-scene--with-nodes');
    });

    it('should append query parameters', () => {
      const url = manager.getStoryUrl('Canvas', 'Default', {
        theme: 'dark',
        width: 1920,
      });

      expect(url).toContain('theme');
      expect(url).toContain('dark');
      expect(url).toContain('width');
    });

    it('should handle empty parameters', () => {
      const url = manager.getStoryUrl('Canvas', 'Default', {});
      // Storybook URLs always contain ?id= for story path
      expect(url).toContain('?id=');
      expect(url).not.toContain('&'); // No additional query params
    });

    it('should encode complex parameter values', () => {
      const url = manager.getStoryUrl('Canvas', 'Default', {
        config: { nested: { value: true } },
      });

      expect(url).toContain('config');
    });
  });

  describe('Mock Node Creation', () => {
    it('should create node with defaults', () => {
      const node = manager.createMockNode();

      expect(node.id).toBeDefined();
      expect(node.type).toBe('default');
      expect(node.position).toEqual({ x: 0, y: 0 });
      expect(node.draggable).toBe(true);
      expect(node.selectable).toBe(true);
    });

    it('should accept partial node configuration', () => {
      const node = manager.createMockNode({
        id: 'custom-id',
        type: 'custom',
        position: { x: 100, y: 200 },
        data: { label: 'Test Node' },
      });

      expect(node.id).toBe('custom-id');
      expect(node.type).toBe('custom');
      expect(node.position).toEqual({ x: 100, y: 200 });
      expect(node.data.label).toBe('Test Node');
    });

    it('should generate unique IDs', () => {
      const node1 = manager.createMockNode();
      const node2 = manager.createMockNode();

      expect(node1.id).not.toBe(node2.id);
    });

    it('should support custom styling', () => {
      const node = manager.createMockNode({
        style: { backgroundColor: 'red', border: '2px solid blue' },
        className: 'custom-node',
      });

      expect(node.style).toEqual({
        backgroundColor: 'red',
        border: '2px solid blue',
      });
      expect(node.className).toBe('custom-node');
    });

    it('should support node interaction flags', () => {
      const node = manager.createMockNode({
        draggable: false,
        selectable: false,
        connectable: false,
        deletable: false,
      });

      expect(node.draggable).toBe(false);
      expect(node.selectable).toBe(false);
      expect(node.connectable).toBe(false);
      expect(node.deletable).toBe(false);
    });
  });

  describe('Mock Edge Creation', () => {
    it('should create edge with defaults', () => {
      const edge = manager.createMockEdge();

      expect(edge.id).toBeDefined();
      expect(edge.type).toBe('default');
      expect(edge.source).toBe('node-1');
      expect(edge.target).toBe('node-2');
      expect(edge.animated).toBe(false);
    });

    it('should accept partial edge configuration', () => {
      const edge = manager.createMockEdge({
        id: 'custom-edge',
        source: 'node-a',
        target: 'node-b',
        type: 'smoothstep',
        animated: true,
        label: 'Connection',
      });

      expect(edge.id).toBe('custom-edge');
      expect(edge.source).toBe('node-a');
      expect(edge.target).toBe('node-b');
      expect(edge.type).toBe('smoothstep');
      expect(edge.animated).toBe(true);
      expect(edge.label).toBe('Connection');
    });

    it('should generate unique IDs', () => {
      const edge1 = manager.createMockEdge();
      const edge2 = manager.createMockEdge();

      expect(edge1.id).not.toBe(edge2.id);
    });

    it('should support custom styling', () => {
      const edge = manager.createMockEdge({
        style: { stroke: 'red', strokeWidth: 3 },
        labelStyle: { fill: 'blue', fontSize: 14 },
      });

      expect(edge.style).toEqual({ stroke: 'red', strokeWidth: 3 });
      expect(edge.labelStyle).toEqual({ fill: 'blue', fontSize: 14 });
    });

    it('should support edge data', () => {
      const edge = manager.createMockEdge({
        data: { weight: 0.8, metadata: { type: 'relation' } },
      });

      expect(edge.data).toEqual({
        weight: 0.8,
        metadata: { type: 'relation' },
      });
    });
  });

  describe('Canvas Fixture Generation', () => {
    it('should generate fixture with default settings', () => {
      const fixture = manager.createCanvasFixture();

      expect(fixture.nodes).toHaveLength(5);
      expect(fixture.edges).toHaveLength(4);
    });

    it('should generate custom number of nodes', () => {
      const fixture = manager.createCanvasFixture({ nodeCount: 10 });

      expect(fixture.nodes).toHaveLength(10);
    });

    it('should generate custom number of edges', () => {
      const fixture = manager.createCanvasFixture({
        nodeCount: 8,
        edgeCount: 6,
      });

      expect(fixture.edges).toHaveLength(6);
    });

    it('should not generate more edges than possible', () => {
      const fixture = manager.createCanvasFixture({
        nodeCount: 3,
        edgeCount: 10, // More than nodeCount - 1
      });

      expect(fixture.edges.length).toBeLessThanOrEqual(2); // Max is nodeCount - 1
    });

    it('should use custom node types', () => {
      const fixture = manager.createCanvasFixture({
        nodeCount: 5,
        nodeTypes: ['custom', 'special'],
      });

      fixture.nodes.forEach((node) => {
        expect(['custom', 'special']).toContain(node.type);
      });
    });

    it('should use custom edge types', () => {
      const fixture = manager.createCanvasFixture({
        nodeCount: 5,
        edgeCount: 3,
        edgeTypes: ['smoothstep', 'straight'],
      });

      fixture.edges.forEach((edge) => {
        expect(['smoothstep', 'straight']).toContain(edge.type);
      });
    });

    it('should generate grid layout', () => {
      const fixture = manager.createCanvasFixture({
        nodeCount: 9,
        layout: 'grid',
      });

      // Grid layout should have regular spacing
      const xPositions = fixture.nodes.map((n) => n.position.x);
      const uniqueX = new Set(xPositions);
      expect(uniqueX.size).toBeGreaterThan(1); // Multiple columns
    });

    it('should generate tree layout', () => {
      const fixture = manager.createCanvasFixture({
        nodeCount: 7,
        layout: 'tree',
      });

      // Tree layout should have increasing Y positions
      const yPositions = fixture.nodes.map((n) => n.position.y);
      const uniqueY = new Set(yPositions);
      expect(uniqueY.size).toBeGreaterThan(1); // Multiple levels
    });

    it('should generate force layout', () => {
      const fixture = manager.createCanvasFixture({
        nodeCount: 8,
        layout: 'force',
      });

      // Force layout should position nodes in circle
      fixture.nodes.forEach((node) => {
        expect(node.position.x).toBeGreaterThanOrEqual(0);
        expect(node.position.y).toBeGreaterThanOrEqual(0);
      });
    });

    it('should generate random layout', () => {
      const fixture = manager.createCanvasFixture({
        nodeCount: 6,
        layout: 'random',
      });

      // Random layout should have varied positions
      const positions = fixture.nodes.map((n) => `${n.position.x},${n.position.y}`);
      const unique = new Set(positions);
      expect(unique.size).toBe(fixture.nodes.length);
    });

    it('should support deterministic generation with seed', () => {
      const fixture1 = manager.createCanvasFixture({
        nodeCount: 5,
        layout: 'random',
        seed: 12345,
      });
      const fixture2 = manager.createCanvasFixture({
        nodeCount: 5,
        layout: 'random',
        seed: 12345,
      });

      // Same seed should produce identical fixtures
      expect(fixture1.nodes).toEqual(fixture2.nodes);
      expect(fixture1.edges).toEqual(fixture2.edges);
    });

    it('should generate edges connecting sequential nodes', () => {
      const fixture = manager.createCanvasFixture({
        nodeCount: 5,
        edgeCount: 4,
      });

      expect(fixture.edges[0].source).toBe('node-1');
      expect(fixture.edges[0].target).toBe('node-2');
      expect(fixture.edges[1].source).toBe('node-2');
      expect(fixture.edges[1].target).toBe('node-3');
    });
  });

  describe('React Flow Hook Mocking', () => {
    beforeEach(() => {
      manager.mockReactFlowHooks();
    });

    it('should mock useReactFlow hook', () => {
      const useReactFlow = (globalThis as unknown as { useReactFlow: () => unknown }).useReactFlow;
      expect(useReactFlow).toBeDefined();

      const api = useReactFlow() as { getNodes: () => MockNode[] };
      expect(api.getNodes).toBeDefined();
    });

    it('should mock useNodes hook', () => {
      const useNodes = (globalThis as unknown as { useNodes: () => MockNode[] }).useNodes;
      expect(useNodes).toBeDefined();

      const nodes = useNodes();
      expect(Array.isArray(nodes)).toBe(true);
    });

    it('should mock useEdges hook', () => {
      const useEdges = (globalThis as unknown as { useEdges: () => MockEdge[] }).useEdges;
      expect(useEdges).toBeDefined();

      const edges = useEdges();
      expect(Array.isArray(edges)).toBe(true);
    });

    it('should mock useViewport hook', () => {
      const useViewport = (globalThis as unknown as { useViewport: () => { x: number; y: number; zoom: number } }).useViewport;
      expect(useViewport).toBeDefined();

      const viewport = useViewport();
      expect(viewport.x).toBeDefined();
      expect(viewport.y).toBeDefined();
      expect(viewport.zoom).toBeDefined();
    });

    it('should allow setting nodes', () => {
      const useReactFlow = (globalThis as unknown as { useReactFlow: () => unknown }).useReactFlow;
      const api = useReactFlow() as {
        setNodes: (nodes: MockNode[]) => void;
        getNodes: () => MockNode[];
      };

      const nodes = [createMockNode({ id: 'test-1' })];
      api.setNodes(nodes);

      const retrieved = api.getNodes();
      expect(retrieved).toHaveLength(1);
      expect(retrieved[0].id).toBe('test-1');
    });

    it('should allow adding nodes', () => {
      const useReactFlow = (globalThis as unknown as { useReactFlow: () => unknown }).useReactFlow;
      const api = useReactFlow() as {
        addNodes: (nodes: MockNode[]) => void;
        getNodes: () => MockNode[];
      };

      api.addNodes([createMockNode({ id: 'test-1' })]);
      api.addNodes([createMockNode({ id: 'test-2' })]);

      const nodes = api.getNodes();
      expect(nodes).toHaveLength(2);
    });

    it('should allow deleting elements', () => {
      const useReactFlow = (globalThis as unknown as { useReactFlow: () => unknown }).useReactFlow;
      const api = useReactFlow() as {
        setNodes: (nodes: MockNode[]) => void;
        setEdges: (edges: MockEdge[]) => void;
        deleteElements: (params: {
          nodes?: { id: string }[];
          edges?: { id: string }[];
        }) => void;
        getNodes: () => MockNode[];
        getEdges: () => MockEdge[];
      };

      api.setNodes([
        createMockNode({ id: 'node-1' }),
        createMockNode({ id: 'node-2' }),
      ]);
      api.setEdges([createMockEdge({ id: 'edge-1' })]);

      api.deleteElements({ nodes: [{ id: 'node-1' }] });

      expect(api.getNodes()).toHaveLength(1);
      expect(api.getNodes()[0].id).toBe('node-2');
    });

    it('should support viewport manipulation', () => {
      const useReactFlow = (globalThis as unknown as { useReactFlow: () => unknown }).useReactFlow;
      const api = useReactFlow() as {
        zoomIn: () => void;
        zoomOut: () => void;
        getViewport: () => { x: number; y: number; zoom: number };
      };

      const initialZoom = api.getViewport().zoom;

      api.zoomIn();
      const zoomedInZoom = api.getViewport().zoom;
      expect(zoomedInZoom).toBeGreaterThan(initialZoom);

      api.zoomOut();
      const zoomedOutZoom = api.getViewport().zoom;
      expect(zoomedOutZoom).toBeLessThan(zoomedInZoom);
    });
  });

  describe('Mock State Management', () => {
    it('should get current mock state', () => {
      const state = manager.getMockState();

      expect(state.nodes).toBeDefined();
      expect(state.edges).toBeDefined();
      expect(state.viewport).toBeDefined();
    });

    it('should set mock state', () => {
      const nodes = [createMockNode({ id: 'test' })];
      manager.setMockState({ nodes });

      const state = manager.getMockState();
      expect(state.nodes).toHaveLength(1);
      expect(state.nodes[0].id).toBe('test');
    });

    it('should reset mock state', () => {
      manager.setMockState({
        nodes: [createMockNode({ id: 'test' })],
      });

      manager.resetMocks();

      const state = manager.getMockState();
      expect(state.nodes).toHaveLength(0);
      expect(state.edges).toHaveLength(0);
    });
  });

  describe('Configuration Management', () => {
    it('should update configuration', () => {
      manager.updateConfig({ baseUrl: 'http://updated:3000' });

      const config = manager.getConfig();
      expect(config.baseUrl).toBe('http://updated:3000');
    });

    it('should merge configuration updates', () => {
      manager.updateConfig({ viewport: { width: 1920, height: 1080 } });
      manager.updateConfig({ parameters: { theme: 'dark' } });

      const config = manager.getConfig();
      expect(config.viewport?.width).toBe(1920);
      expect(config.parameters?.theme).toBe('dark');
    });
  });
});

describe('Standalone Helper Functions', () => {
  describe('createMockNode', () => {
    it('should create node', () => {
      const node = createMockNode({ id: 'test' });
      expect(node.id).toBe('test');
    });
  });

  describe('createMockEdge', () => {
    it('should create edge', () => {
      const edge = createMockEdge({ id: 'test' });
      expect(edge.id).toBe('test');
    });
  });

  describe('createCanvasFixture', () => {
    it('should create fixture', () => {
      const fixture = createCanvasFixture({ nodeCount: 3 });
      expect(fixture.nodes).toHaveLength(3);
    });
  });

  describe('getStoryUrl', () => {
    it('should generate URL', () => {
      const url = getStoryUrl('Test', 'Story');
      expect(url).toContain('test--story');
    });
  });

  describe('mockReactFlowHooks', () => {
    it('should setup mocks', () => {
      mockReactFlowHooks();
      const useNodes = (globalThis as unknown as { useNodes: () => MockNode[] }).useNodes;
      expect(useNodes).toBeDefined();
    });
  });

  describe('resetMocks', () => {
    beforeEach(() => {
      mockReactFlowHooks();
    });

    it('should reset global state', () => {
      const useReactFlow = (globalThis as unknown as { useReactFlow: () => unknown }).useReactFlow;
      const api = useReactFlow() as {
        addNodes: (nodes: MockNode[]) => void;
        getNodes: () => MockNode[];
      };

      api.addNodes([createMockNode({ id: 'test' })]);

      resetMocks();

      // After reset, new instance should have empty state
      const manager = new TestUtilitiesManager();
      const state = manager.getMockState();
      expect(state.nodes).toHaveLength(0);
    });
  });
});
