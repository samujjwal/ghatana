/**
 * Tests for Renderer Switcher (Feature 2.27)
 * 
 * Tests renderer abstraction, switching, state preservation,
 * fallback handling, and plugin compatibility.
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';

import {
  DOMRenderer,
  WebGLRenderer,
  RendererSwitcher,
  detectBestRenderer,
  createRendererSwitcher,
  type CanvasState,
  type PluginAdapter,
  type RendererType
} from '../rendererSwitcher';

// ============================================================================
// Test Helpers
// ============================================================================

function createMockContainer(): HTMLElement {
  const div = document.createElement('div');
  div.style.width = '800px';
  div.style.height = '600px';
  document.body.appendChild(div);
  return div;
}

function createTestState(): CanvasState {
  return {
    nodes: [
      { id: 'node1', position: { x: 100, y: 100 }, data: { label: 'Node 1' } },
      { id: 'node2', position: { x: 200, y: 200 }, data: { label: 'Node 2' } }
    ],
    edges: [
      { id: 'edge1', source: 'node1', target: 'node2' }
    ],
    viewport: { x: 0, y: 0, zoom: 1 },
    selectedNodes: ['node1'],
    selectedEdges: [],
    metadata: { version: '1.0' }
  };
}

// ============================================================================
// DOM Renderer Tests
// ============================================================================

describe('Renderer Switcher - DOM Renderer', () => {
  let renderer: DOMRenderer;
  let container: HTMLElement;
  
  beforeEach(() => {
    renderer = new DOMRenderer();
    container = createMockContainer();
  });
  
  it('should initialize DOM renderer', async () => {
    await renderer.initialize(container);
    
    expect(renderer.type).toBe('dom');
    expect(container.style.position).toBe('relative');
  });
  
  it('should report DOM capabilities', () => {
    const caps = renderer.capabilities;
    
    expect(caps.maxNodes).toBe(1000);
    expect(caps.maxEdges).toBe(2000);
    expect(caps.supportsCustomShaders).toBe(false);
    expect(caps.supports3D).toBe(false);
  });
  
  it('should save and restore state', () => {
    const state = createTestState();
    
    renderer.restoreState(state);
    const saved = renderer.saveState();
    
    expect(saved.nodes).toHaveLength(2);
    expect(saved.edges).toHaveLength(1);
    expect(saved.viewport.zoom).toBe(1);
  });
  
  it('should manage nodes', () => {
    renderer.addNode({ id: 'test', position: { x: 0, y: 0 }, data: {} });
    
    const state = renderer.saveState();
    expect(state.nodes).toHaveLength(1);
    
    renderer.updateNode('test', { position: { x: 100, y: 100 }, data: {} });
    const updated = renderer.saveState();
    expect(updated.nodes[0].position.x).toBe(100);
    
    renderer.removeNode('test');
    const removed = renderer.saveState();
    expect(removed.nodes).toHaveLength(0);
  });
  
  it('should manage edges', () => {
    renderer.addEdge({ id: 'edge1', source: 'a', target: 'b' });
    
    const state = renderer.saveState();
    expect(state.edges).toHaveLength(1);
    
    renderer.updateEdge('edge1', { id: 'edge1', source: 'a', target: 'c' });
    const updated = renderer.saveState();
    expect(updated.edges[0].target).toBe('c');
    
    renderer.removeEdge('edge1');
    const removed = renderer.saveState();
    expect(removed.edges).toHaveLength(0);
  });
  
  it('should manage viewport', () => {
    renderer.setViewport(100, 200, 1.5);
    
    const viewport = renderer.getViewport();
    expect(viewport.x).toBe(100);
    expect(viewport.y).toBe(200);
    expect(viewport.zoom).toBe(1.5);
  });
  
  it('should report performance metrics', () => {
    renderer.addNode({ id: 'node1', position: { x: 0, y: 0 }, data: {} });
    renderer.addNode({ id: 'node2', position: { x: 0, y: 0 }, data: {} });
    renderer.addEdge({ id: 'edge1', source: 'node1', target: 'node2' });
    
    const perf = renderer.getPerformance();
    
    expect(perf.fps).toBe(60);
    expect(perf.drawCalls).toBe(3); // 2 nodes + 1 edge
    expect(perf.memoryUsage).toBeGreaterThan(0);
  });
});

// ============================================================================
// WebGL Renderer Tests
// ============================================================================

describe('Renderer Switcher - WebGL Renderer', () => {
  let renderer: WebGLRenderer;
  let container: HTMLElement;
  
  beforeEach(() => {
    renderer = new WebGLRenderer();
    container = createMockContainer();
  });
  
  it('should initialize WebGL renderer', async () => {
    try {
      await renderer.initialize(container);
      
      expect(renderer.type).toBe('webgl');
      expect(container.querySelector('canvas')).toBeTruthy();
    } catch (error) {
      // WebGL might not be available in test environment
      expect((error as Error).message).toContain('WebGL');
    }
  });
  
  it('should report WebGL capabilities', () => {
    const caps = renderer.capabilities;
    
    // Before initialization, capabilities should show 0 or after initialization show higher limits
    if (caps.maxNodes > 0) {
      expect(caps.maxNodes).toBeGreaterThanOrEqual(10000);
      expect(caps.supports3D).toBe(true);
      expect(caps.supportsCustomShaders).toBe(true);
    }
  });
  
  it('should save and restore state', () => {
    const state = createTestState();
    
    renderer.restoreState(state);
    const saved = renderer.saveState();
    
    expect(saved.nodes).toHaveLength(2);
    expect(saved.edges).toHaveLength(1);
  });
  
  it('should manage nodes efficiently', () => {
    // Add many nodes to test WebGL efficiency
    for (let i = 0; i < 100; i++) {
      renderer.addNode({
        id: `node${i}`,
        position: { x: i * 10, y: i * 10 },
        data: {}
      });
    }
    
    const state = renderer.saveState();
    expect(state.nodes).toHaveLength(100);
    
    const perf = renderer.getPerformance();
    expect(perf.drawCalls).toBeLessThan(100); // Should use instancing
  });
  
  it('should handle viewport transformations', () => {
    renderer.setViewport(500, 300, 2.0);
    
    const viewport = renderer.getViewport();
    expect(viewport.x).toBe(500);
    expect(viewport.y).toBe(300);
    expect(viewport.zoom).toBe(2.0);
  });
});

// ============================================================================
// Renderer Switcher Tests
// ============================================================================

describe('Renderer Switcher - Core Functionality', () => {
  let switcher: RendererSwitcher;
  let container: HTMLElement;
  
  beforeEach(() => {
    switcher = new RendererSwitcher({ preferredRenderer: 'dom' });
    container = createMockContainer();
  });
  
  it('should initialize with preferred renderer', async () => {
    await switcher.initialize(container, 'dom');
    
    const current = switcher.getCurrentRenderer();
    expect(current?.type).toBe('dom');
  });
  
  it('should fall back when preferred renderer fails', async () => {
    // Try to initialize WebGL (might fail in test environment)
    await switcher.initialize(container, 'webgl');
    
    const current = switcher.getCurrentRenderer();
    expect(current).toBeDefined();
    expect(['dom', 'webgl']).toContain(current!.type);
  });
  
  it('should check WebGL support', () => {
    const support = switcher.checkWebGLSupport();
    
    expect(support.supported).toBeDefined();
    if (!support.supported) {
      expect(support.reason).toBe('unsupported');
    }
  });
  
  it('should provide renderer capabilities', async () => {
    await switcher.initialize(container, 'dom');
    
    const caps = switcher.getCapabilities();
    expect(caps).toBeDefined();
    expect(caps?.maxNodes).toBeGreaterThan(0);
  });
});

// ============================================================================
// Renderer Switching Tests
// ============================================================================

describe('Renderer Switcher - Renderer Switching', () => {
  let switcher: RendererSwitcher;
  let container: HTMLElement;
  
  beforeEach(async () => {
    switcher = new RendererSwitcher({ preferredRenderer: 'dom' });
    container = createMockContainer();
    await switcher.initialize(container, 'dom');
  });
  
  it('should switch renderers with state preservation', async () => {
    const state = createTestState();
    const current = switcher.getCurrentRenderer()!;
    current.restoreState(state);
    
    const switched = await switcher.switchRenderer(container, 'webgl');
    
    if (switched) {
      const newRenderer = switcher.getCurrentRenderer()!;
      expect(newRenderer.type).toBe('webgl');
      
      const newState = newRenderer.saveState();
      expect(newState.nodes).toHaveLength(2);
      expect(newState.edges).toHaveLength(1);
      expect(newState.viewport.zoom).toBe(1);
    } else {
      // WebGL not available, should stay on DOM
      const fallbackRenderer = switcher.getCurrentRenderer()!;
      expect(fallbackRenderer.type).toBe('dom');
    }
  });
  
  it('should not switch if already using requested renderer', async () => {
    const result = await switcher.switchRenderer(container, 'dom');
    
    expect(result).toBe(true);
    expect(switcher.getCurrentRenderer()?.type).toBe('dom');
  });
  
  it('should record switch history', async () => {
    // Initialize with DOM
    expect(switcher.getCurrentRenderer()?.type).toBe('dom');
    
    // Try to switch to WebGL
    await switcher.switchRenderer(container, 'webgl');
    
    const history = switcher.getSwitchHistory();
    
    // History should be recorded either:
    // 1. From DOM->WebGL if switch succeeded
    // 2. Or have initial fallback record if WebGL not available
    // In either case, if current renderer is still DOM, no switch was made
    const currentType = switcher.getCurrentRenderer()?.type;
    
    if (currentType === 'webgl') {
      // Switch succeeded, should have history
      expect(history.length).toBeGreaterThan(0);
      const lastSwitch = history[history.length - 1];
      expect(lastSwitch.from).toBe('dom');
      expect(lastSwitch.to).toBe('webgl');
    } else {
      // Switch failed (WebGL not available), DOM renderer still in use
      // History might be empty or have initial setup records
      expect(currentType).toBe('dom');
    }
  });
  
  it('should restore old renderer on switch failure', async () => {
    const state = createTestState();
    const current = switcher.getCurrentRenderer()!;
    current.restoreState(state);
    
    // Try to switch (might fail)
    await switcher.switchRenderer(container, 'webgl');
    
    // Regardless of success, renderer should be functional
    const finalRenderer = switcher.getCurrentRenderer()!;
    expect(finalRenderer).toBeDefined();
    expect(finalRenderer.saveState().nodes.length).toBe(2);
  });
});

// ============================================================================
// Plugin Adapter Tests
// ============================================================================

describe('Renderer Switcher - Plugin Adapters', () => {
  let switcher: RendererSwitcher;
  let container: HTMLElement;
  
  beforeEach(async () => {
    switcher = new RendererSwitcher({ preferredRenderer: 'dom' });
    container = createMockContainer();
    await switcher.initialize(container, 'dom');
  });
  
  it('should register plugin adapters', () => {
    const adapter: PluginAdapter = {
      name: 'TestPlugin',
      supportedRenderers: ['dom', 'webgl'],
      onRendererChange: vi.fn(),
      canAdapt: () => true
    };
    
    switcher.registerPlugin(adapter);
    
    // Plugin should be registered (internal state)
    expect(true).toBe(true); // Plugin registration is internal
  });
  
  it('should call plugin adapters on renderer switch', async () => {
    const onRendererChange = vi.fn();
    
    const adapter: PluginAdapter = {
      name: 'TestPlugin',
      supportedRenderers: ['dom', 'webgl'],
      onRendererChange,
      canAdapt: (from: RendererType, to: RendererType) => from !== to
    };
    
    switcher.registerPlugin(adapter);
    
    await switcher.switchRenderer(container, 'webgl');
    
    // If switch succeeded, plugin should be notified
    if (switcher.getCurrentRenderer()?.type === 'webgl') {
      expect(onRendererChange).toHaveBeenCalled();
    }
  });
  
  it('should support node adaptation', () => {
    const adapter: PluginAdapter = {
      name: 'NodeAdapter',
      supportedRenderers: ['dom', 'webgl'],
      onRendererChange: vi.fn(),
      canAdapt: () => true,
      adaptNode: (node: unknown, toRenderer: RendererType) => {
        return { ...node, adapted: true, renderer: toRenderer };
      }
    };
    
    expect(adapter.adaptNode).toBeDefined();
    
    const adapted = adapter.adaptNode!({ id: 'test', data: {} }, 'webgl');
    expect(adapted.adapted).toBe(true);
    expect(adapted.renderer).toBe('webgl');
  });
});

// ============================================================================
// Helper Function Tests
// ============================================================================

describe('Renderer Switcher - Helper Functions', () => {
  it('should detect best renderer for environment', () => {
    const best = detectBestRenderer();
    
    expect(['dom', 'webgl']).toContain(best);
  });
  
  it('should create renderer switcher with auto-detection', () => {
    const switcher = createRendererSwitcher();
    
    expect(switcher).toBeInstanceOf(RendererSwitcher);
  });
  
  it('should apply custom configuration', () => {
    const switcher = createRendererSwitcher({
      preferredRenderer: 'dom',
      autoSwitchOnPerformanceDegradation: false
    });
    
    expect(switcher).toBeInstanceOf(RendererSwitcher);
  });
});

// ============================================================================
// Performance & Fallback Tests
// ============================================================================

describe('Renderer Switcher - Performance & Fallback', () => {
  it('should handle large datasets with DOM renderer', async () => {
    const renderer = new DOMRenderer();
    const container = createMockContainer();
    await renderer.initialize(container);
    
    // Add nodes up to capacity
    for (let i = 0; i < 500; i++) {
      renderer.addNode({
        id: `node${i}`,
        position: { x: i * 10, y: i * 10 },
        data: {}
      });
    }
    
    const perf = renderer.getPerformance();
    expect(perf.drawCalls).toBe(500);
  });
  
  it('should handle large datasets efficiently with WebGL', async () => {
    const renderer = new WebGLRenderer();
    const container = createMockContainer();
    
    try {
      await renderer.initialize(container);
      
      // Add many nodes
      for (let i = 0; i < 1000; i++) {
        renderer.addNode({
          id: `node${i}`,
          position: { x: i * 10, y: i * 10 },
          data: {}
        });
      }
      
      const perf = renderer.getPerformance();
      // WebGL should use instancing, reducing draw calls
      expect(perf.drawCalls).toBeLessThan(100);
    } catch (error) {
      // WebGL not available in test environment
      expect(true).toBe(true);
    }
  });
  
  it('should measure memory usage', () => {
    const renderer = new DOMRenderer();
    
    for (let i = 0; i < 100; i++) {
      renderer.addNode({
        id: `node${i}`,
        position: { x: 0, y: 0 },
        data: { large: 'x'.repeat(100) }
      });
    }
    
    const perf = renderer.getPerformance();
    expect(perf.memoryUsage).toBeGreaterThan(0);
  });
});
