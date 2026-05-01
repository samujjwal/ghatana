/**
 * @jest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';

import {
  createMockNode,
  createMockEdge,
  createMockNodes,
  createMockEdges,
  createMockCanvasState,
  createMockDragEvent,
  createMockPointerEvent,
  createMockIntersectionObserver,
  createMockResizeObserver,
  createMockLocalStorage,
  setupGlobalMocks,
  cleanupGlobalMocks,
} from '../mocks';

describe('Test Utilities - Mocks', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Node Mocks', () => {
    it('should create mock node with defaults', () => {
      const node = createMockNode();

      expect(node).toMatchObject({
        id: 'mock-node-1',
        type: 'default',
        position: { x: 0, y: 0 },
        data: { label: 'Mock Node' },
      });
    });

    it('should create mock node with overrides', () => {
      const node = createMockNode({
        id: 'custom-1',
        position: { x: 100, y: 200 },
        data: { label: 'Custom' },
      });

      expect(node.id).toBe('custom-1');
      expect(node.position).toEqual({ x: 100, y: 200 });
      expect(node.data.label).toBe('Custom');
    });

    it('should create multiple mock nodes', () => {
      const nodes = createMockNodes(5);

      expect(nodes).toHaveLength(5);
      expect(nodes[0].id).toBe('node-1');
      expect(nodes[4].id).toBe('node-5');
    });

    it('should create nodes with custom base ID', () => {
      const nodes = createMockNodes(3, 'test');

      expect(nodes[0].id).toBe('test-1');
      expect(nodes[2].id).toBe('test-3');
    });

    it('should position nodes incrementally', () => {
      const nodes = createMockNodes(3);

      expect(nodes[0].position).toEqual({ x: 0, y: 0 });
      expect(nodes[1].position).toEqual({ x: 100, y: 100 });
      expect(nodes[2].position).toEqual({ x: 200, y: 200 });
    });
  });

  describe('Edge Mocks', () => {
    it('should create mock edge with defaults', () => {
      const edge = createMockEdge();

      expect(edge).toMatchObject({
        id: 'mock-edge-1',
        source: 'node-1',
        target: 'node-2',
      });
    });

    it('should create mock edge with overrides', () => {
      const edge = createMockEdge({
        id: 'custom-edge',
        source: 'start',
        target: 'end',
      });

      expect(edge.id).toBe('custom-edge');
      expect(edge.source).toBe('start');
      expect(edge.target).toBe('end');
    });

    it('should create edges from node IDs', () => {
      const nodeIds = ['node-1', 'node-2', 'node-3'];
      const edges = createMockEdges(nodeIds);

      expect(edges).toHaveLength(2);
      expect(edges[0]).toMatchObject({
        source: 'node-1',
        target: 'node-2',
      });
      expect(edges[1]).toMatchObject({
        source: 'node-2',
        target: 'node-3',
      });
    });

    it('should create edges with custom base ID', () => {
      const nodeIds = ['a', 'b', 'c'];
      const edges = createMockEdges(nodeIds, 'connection');

      expect(edges[0].id).toBe('connection-1');
      expect(edges[1].id).toBe('connection-2');
    });

    it('should handle single node gracefully', () => {
      const edges = createMockEdges(['node-1']);

      expect(edges).toHaveLength(0);
    });
  });

  describe('Canvas State Mock', () => {
    it('should create empty canvas state', () => {
      const state = createMockCanvasState();

      expect(state).toEqual({
        nodes: [],
        edges: [],
        viewport: { x: 0, y: 0, zoom: 1 },
        selectedNodes: [],
        selectedEdges: [],
      });
    });

    it('should create canvas state with overrides', () => {
      const nodes = createMockNodes(2);
      const state = createMockCanvasState({
        nodes,
        viewport: { x: 100, y: 200, zoom: 1.5 },
      });

      expect(state.nodes).toHaveLength(2);
      expect(state.viewport.zoom).toBe(1.5);
    });

    it('should handle selected elements', () => {
      const state = createMockCanvasState({
        selectedNodes: ['node-1', 'node-2'],
        selectedEdges: ['edge-1'],
      });

      expect(state.selectedNodes).toEqual(['node-1', 'node-2']);
      expect(state.selectedEdges).toEqual(['edge-1']);
    });
  });

  describe('Event Mocks', () => {
    it('should create mock drag event', () => {
      const event = createMockDragEvent();

      expect(event.dataTransfer).toBeDefined();
      expect(event.clientX).toBe(0);
      expect(event.clientY).toBe(0);
      expect(event.preventDefault).toBeDefined();
    });

    it('should create drag event with overrides', () => {
      const event = createMockDragEvent({
        clientX: 100,
        clientY: 200,
      });

      expect(event.clientX).toBe(100);
      expect(event.clientY).toBe(200);
    });

    it('should create mock pointer event', () => {
      const event = createMockPointerEvent();

      expect(event.clientX).toBe(0);
      expect(event.clientY).toBe(0);
      expect(event.button).toBe(0);
      expect(event.pointerType).toBe('mouse');
    });

    it('should create pointer event with overrides', () => {
      const event = createMockPointerEvent({
        clientX: 50,
        clientY: 75,
        button: 1,
      });

      expect(event.clientX).toBe(50);
      expect(event.clientY).toBe(75);
      expect(event.button).toBe(1);
    });
  });

  describe('Observer Mocks', () => {
    it('should create mock intersection observer', () => {
      const MockObserver = createMockIntersectionObserver();
      const callback = vi.fn();
      const observer = new MockObserver(callback);

      expect(observer.observe).toBeDefined();
      expect(observer.unobserve).toBeDefined();
      expect(observer.disconnect).toBeDefined();
    });

    it('should call intersection observer methods', () => {
      const MockObserver = createMockIntersectionObserver();
      const callback = vi.fn();
      const observer = new MockObserver(callback);
      const element = document.createElement('div');

      observer.observe(element);
      expect(observer.observe).toHaveBeenCalledWith(element);

      observer.disconnect();
      expect(observer.disconnect).toHaveBeenCalled();
    });

    it('should create mock resize observer', () => {
      const MockObserver = createMockResizeObserver();
      const callback = vi.fn();
      const observer = new MockObserver(callback);

      expect(observer.observe).toBeDefined();
      expect(observer.unobserve).toBeDefined();
      expect(observer.disconnect).toBeDefined();
    });

    it('should call resize observer methods', () => {
      const MockObserver = createMockResizeObserver();
      const callback = vi.fn();
      const observer = new MockObserver(callback);
      const element = document.createElement('div');

      observer.observe(element);
      expect(observer.observe).toHaveBeenCalledWith(element);

      observer.disconnect();
      expect(observer.disconnect).toHaveBeenCalled();
    });
  });

  describe('Storage Mocks', () => {
    it('should create mock localStorage', () => {
      const storage = createMockLocalStorage();

      expect(storage.getItem).toBeDefined();
      expect(storage.setItem).toBeDefined();
      expect(storage.removeItem).toBeDefined();
      expect(storage.clear).toBeDefined();
    });

    it('should store and retrieve items', () => {
      const storage = createMockLocalStorage();

      storage.setItem('key1', 'value1');
      expect(storage.getItem('key1')).toBe('value1');
    });

    it('should return null for missing items', () => {
      const storage = createMockLocalStorage();

      expect(storage.getItem('missing')).toBeNull();
    });

    it('should remove items', () => {
      const storage = createMockLocalStorage();

      storage.setItem('key1', 'value1');
      storage.removeItem('key1');
      expect(storage.getItem('key1')).toBeNull();
    });

    it('should clear all items', () => {
      const storage = createMockLocalStorage();

      storage.setItem('key1', 'value1');
      storage.setItem('key2', 'value2');
      storage.clear();

      expect(storage.length).toBe(0);
      expect(storage.getItem('key1')).toBeNull();
    });

    it('should get key by index', () => {
      const storage = createMockLocalStorage();

      storage.setItem('key1', 'value1');
      const key = storage.key(0);

      expect(key).toBe('key1');
    });

    it('should track length', () => {
      const storage = createMockLocalStorage();

      expect(storage.length).toBe(0);

      storage.setItem('key1', 'value1');
      expect(storage.length).toBe(1);

      storage.setItem('key2', 'value2');
      expect(storage.length).toBe(2);

      storage.removeItem('key1');
      expect(storage.length).toBe(1);
    });
  });

  describe('Global Mocks Setup', () => {
    it('should setup global mocks', () => {
      setupGlobalMocks();

      expect(global.IntersectionObserver).toBeDefined();
      expect(global.ResizeObserver).toBeDefined();
      expect(window.localStorage).toBeDefined();
      expect(window.sessionStorage).toBeDefined();
      expect(window.matchMedia).toBeDefined();
    });

    it('should cleanup global mocks', () => {
      setupGlobalMocks();
      cleanupGlobalMocks();

      // Verify mocks are cleared but still defined
      expect(global.IntersectionObserver).toBeDefined();
      expect(global.ResizeObserver).toBeDefined();
    });

    it('should mock matchMedia', () => {
      setupGlobalMocks();

      const media = window.matchMedia('(prefers-color-scheme: dark)');

      expect(media).toBeDefined();
      expect(media.matches).toBe(false);
      expect(media.media).toBe('(prefers-color-scheme: dark)');
    });
  });
});
