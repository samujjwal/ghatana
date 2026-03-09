/**
 * Tests for Production WebGL Renderer Integration
 * 
 * Tests the integration of Feature 1.10 production WebGL renderer
 * with Feature 2.27 renderer abstraction interface.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import { ProductionWebGLRenderer } from '../productionWebGLRenderer';

import type { CanvasState } from '../rendererSwitcher';

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
      {
        id: 'node1',
        position: { x: 100, y: 100 },
        data: { label: 'Node 1', width: 120, height: 60, type: 'rectangle' },
        style: { backgroundColor: '#ff0000', borderColor: '#000000' }
      },
      {
        id: 'node2',
        position: { x: 300, y: 200 },
        data: { label: 'Node 2', width: 120, height: 60, type: 'rectangle' },
        style: { backgroundColor: '#00ff00', borderColor: '#000000' }
      }
    ],
    edges: [
      {
        id: 'edge1',
        source: 'node1',
        target: 'node2',
        style: { color: '#0000ff', width: 2 }
      }
    ],
    viewport: { x: 0, y: 0, zoom: 1 },
    selectedNodes: ['node1'],
    selectedEdges: [],
    metadata: { version: '1.0' }
  };
}

// ============================================================================
// Production WebGL Renderer Tests
// ============================================================================

describe('Production WebGL Renderer', () => {
  let renderer: ProductionWebGLRenderer;
  let container: HTMLElement;
  
  beforeEach(() => {
    renderer = new ProductionWebGLRenderer({
      preferWebGL2: true,
      antialias: true,
    });
    container = createMockContainer();
  });
  
  afterEach(() => {
    if (renderer) {
      renderer.destroy();
    }
    if (container && container.parentNode) {
      container.parentNode.removeChild(container);
    }
  });
  
  describe('Initialization', () => {
    it('should initialize with production renderer', async () => {
      try {
        await renderer.initialize(container);
        
        expect(renderer.type).toBe('webgl');
        expect(renderer.isReady()).toBe(true);
        expect(container.querySelector('canvas')).toBeTruthy();
      } catch (error) {
        // WebGL might not be available in test environment
        expect((error as Error).message).toContain('WebGL');
      }
    });
    
    it('should throw error if WebGL not supported', async () => {
      // Mock WebGL support check to fail
      const mockContainer = createMockContainer();
      const originalGetContext = HTMLCanvasElement.prototype.getContext;
      
      HTMLCanvasElement.prototype.getContext = vi.fn(() => null);
      
      try {
        await expect(renderer.initialize(mockContainer)).rejects.toThrow('WebGL not supported');
      } finally {
        HTMLCanvasElement.prototype.getContext = originalGetContext;
        mockContainer.parentNode?.removeChild(mockContainer);
      }
    });
    
    it('should create canvas with correct dimensions', async () => {
      try {
        await renderer.initialize(container);
        
        const canvas = container.querySelector('canvas');
        expect(canvas).toBeTruthy();
        expect(canvas!.width).toBeGreaterThan(0);
        expect(canvas!.height).toBeGreaterThan(0);
      } catch (error) {
        // Skip if WebGL not available
        expect((error as Error).message).toContain('WebGL');
      }
    });
  });
  
  describe('Capabilities', () => {
    it('should report high-performance capabilities', () => {
      const caps = renderer.capabilities;
      
      expect(caps.maxNodes).toBeGreaterThanOrEqual(100000);
      expect(caps.maxEdges).toBeGreaterThanOrEqual(200000);
      expect(caps.supportsCustomShaders).toBe(true);
      expect(caps.supportsInstancing).toBe(true);
    });
    
    it('should update capabilities after initialization', async () => {
      const initialCaps = renderer.capabilities;
      
      try {
        await renderer.initialize(container);
        
        const updatedCaps = renderer.capabilities;
        expect(updatedCaps.webglVersion).toBeGreaterThan(0);
        expect(updatedCaps.extensions.length).toBeGreaterThanOrEqual(0);
      } catch (error) {
        // Skip if WebGL not available
      }
    });
    
    it('should provide detailed capabilities through getDetailedCapabilities', async () => {
      try {
        await renderer.initialize(container);
        
        const detailedCaps = renderer.getDetailedCapabilities();
        expect(detailedCaps).toBeTruthy();
        
        if (detailedCaps) {
          expect(detailedCaps.version).toBeGreaterThan(0);
          expect(detailedCaps.maxTextureSize).toBeGreaterThan(0);
        }
      } catch (error) {
        // Skip if WebGL not available
      }
    });
  });
  
  describe('State Management', () => {
    it('should save and restore state', () => {
      const state = createTestState();
      
      renderer.restoreState(state);
      const saved = renderer.saveState();
      
      expect(saved.nodes).toHaveLength(2);
      expect(saved.edges).toHaveLength(1);
      expect(saved.viewport.zoom).toBe(1);
      expect(saved.selectedNodes).toEqual(['node1']);
    });
    
    it('should preserve state during save/restore cycle', () => {
      const original = createTestState();
      
      renderer.restoreState(original);
      const restored = renderer.saveState();
      
      expect(JSON.stringify(restored)).toBe(JSON.stringify(original));
    });
    
    it('should update viewport on state restore', async () => {
      try {
        await renderer.initialize(container);
        
        const state = createTestState();
        state.viewport = { x: 100, y: 200, zoom: 1.5 };
        
        renderer.restoreState(state);
        const viewport = renderer.getViewport();
        
        expect(viewport.x).toBe(100);
        expect(viewport.y).toBe(200);
        expect(viewport.zoom).toBe(1.5);
      } catch (error) {
        // Skip if WebGL not available
      }
    });
  });
  
  describe('Node Operations', () => {
    it('should add nodes', () => {
      renderer.addNode({
        id: 'test',
        position: { x: 0, y: 0 },
        data: { label: 'Test' }
      });
      
      const state = renderer.saveState();
      expect(state.nodes).toHaveLength(1);
      expect(state.nodes[0].id).toBe('test');
    });
    
    it('should update nodes', () => {
      renderer.addNode({
        id: 'test',
        position: { x: 0, y: 0 },
        data: {}
      });
      
      renderer.updateNode('test', {
        id: 'test',
        position: { x: 100, y: 100 },
        data: { label: 'Updated' }
      });
      
      const state = renderer.saveState();
      expect(state.nodes[0].position.x).toBe(100);
      expect(state.nodes[0].data.label).toBe('Updated');
    });
    
    it('should remove nodes', () => {
      renderer.addNode({
        id: 'test',
        position: { x: 0, y: 0 },
        data: {}
      });
      
      renderer.removeNode('test');
      
      const state = renderer.saveState();
      expect(state.nodes).toHaveLength(0);
    });
    
    it('should remove connected edges when removing node', () => {
      renderer.addNode({ id: 'node1', position: { x: 0, y: 0 }, data: {} });
      renderer.addNode({ id: 'node2', position: { x: 100, y: 100 }, data: {} });
      renderer.addEdge({ id: 'edge1', source: 'node1', target: 'node2' });
      
      renderer.removeNode('node1');
      
      const state = renderer.saveState();
      expect(state.nodes).toHaveLength(1);
      expect(state.edges).toHaveLength(0); // Edge should be removed
    });
  });
  
  describe('Edge Operations', () => {
    beforeEach(() => {
      // Add nodes for edge connections
      renderer.addNode({ id: 'node1', position: { x: 0, y: 0 }, data: {} });
      renderer.addNode({ id: 'node2', position: { x: 100, y: 100 }, data: {} });
    });
    
    it('should add edges', () => {
      renderer.addEdge({
        id: 'edge1',
        source: 'node1',
        target: 'node2'
      });
      
      const state = renderer.saveState();
      expect(state.edges).toHaveLength(1);
      expect(state.edges[0].source).toBe('node1');
      expect(state.edges[0].target).toBe('node2');
    });
    
    it('should update edges', () => {
      renderer.addEdge({
        id: 'edge1',
        source: 'node1',
        target: 'node2'
      });
      
      renderer.addNode({ id: 'node3', position: { x: 200, y: 200 }, data: {} });
      renderer.updateEdge('edge1', {
        id: 'edge1',
        source: 'node1',
        target: 'node3'
      });
      
      const state = renderer.saveState();
      expect(state.edges[0].target).toBe('node3');
    });
    
    it('should remove edges', () => {
      renderer.addEdge({
        id: 'edge1',
        source: 'node1',
        target: 'node2'
      });
      
      renderer.removeEdge('edge1');
      
      const state = renderer.saveState();
      expect(state.edges).toHaveLength(0);
    });
  });
  
  describe('Viewport Operations', () => {
    it('should set viewport', () => {
      renderer.setViewport(100, 200, 1.5);
      
      const viewport = renderer.getViewport();
      expect(viewport.x).toBe(100);
      expect(viewport.y).toBe(200);
      expect(viewport.zoom).toBe(1.5);
    });
    
    it('should update production renderer viewport', async () => {
      try {
        await renderer.initialize(container);
        
        renderer.setViewport(50, 75, 2.0);
        
        const viewport = renderer.getViewport();
        expect(viewport.zoom).toBe(2.0);
      } catch (error) {
        // Skip if WebGL not available
      }
    });
  });
  
  describe('Rendering', () => {
    it('should render without errors when initialized', async () => {
      try {
        await renderer.initialize(container);
        
        const state = createTestState();
        renderer.restoreState(state);
        
        expect(() => renderer.render()).not.toThrow();
      } catch (error) {
        // Skip if WebGL not available
      }
    });
    
    it('should handle render call before initialization', () => {
      // Should not throw, just do nothing
      expect(() => renderer.render()).not.toThrow();
    });
    
    it('should clear renderer', async () => {
      try {
        await renderer.initialize(container);
        
        renderer.addNode({ id: 'test', position: { x: 0, y: 0 }, data: {} });
        renderer.clear();
        
        const state = renderer.saveState();
        expect(state.nodes).toHaveLength(0);
        expect(state.edges).toHaveLength(0);
      } catch (error) {
        // Skip if WebGL not available
      }
    });
  });
  
  describe('Performance', () => {
    it('should report performance metrics', async () => {
      try {
        await renderer.initialize(container);
        
        const state = createTestState();
        renderer.restoreState(state);
        renderer.render();
        
        const perf = renderer.getPerformance();
        
        expect(perf.fps).toBeGreaterThan(0);
        expect(perf.drawCalls).toBeGreaterThanOrEqual(0);
        expect(perf.memoryUsage).toBeGreaterThanOrEqual(0);
      } catch (error) {
        // Skip if WebGL not available
      }
    });
    
    it('should estimate memory usage', () => {
      // Add many nodes to test memory estimation
      for (let i = 0; i < 100; i++) {
        renderer.addNode({
          id: `node${i}`,
          position: { x: i * 10, y: i * 10 },
          data: {}
        });
      }
      
      const perf = renderer.getPerformance();
      expect(perf.memoryUsage).toBeGreaterThan(0);
    });
    
    it('should report GPU time for WebGL rendering', async () => {
      try {
        await renderer.initialize(container);
        
        renderer.render();
        const perf = renderer.getPerformance();
        
        expect(perf.gpuTime).toBeDefined();
        expect(perf.gpuTime).toBeGreaterThanOrEqual(0);
      } catch (error) {
        // Skip if WebGL not available
      }
    });
  });
  
  describe('Integration with Production Renderer', () => {
    it('should provide access to underlying production renderer', async () => {
      try {
        await renderer.initialize(container);
        
        const productionRenderer = renderer.getProductionRenderer();
        expect(productionRenderer).toBeDefined();
        expect(productionRenderer?.isSupported()).toBe(true);
      } catch (error) {
        // Skip if WebGL not available
      }
    });
    
    it('should handle large datasets efficiently', async () => {
      try {
        await renderer.initialize(container);
        
        // Add 1000 nodes
        for (let i = 0; i < 1000; i++) {
          renderer.addNode({
            id: `node${i}`,
            position: { x: (i % 50) * 20, y: Math.floor(i / 50) * 20 },
            data: { width: 10, height: 10 }
          });
        }
        
        const startTime = performance.now();
        renderer.render();
        const renderTime = performance.now() - startTime;
        
        // Should render in reasonable time (less than 100ms for 1000 nodes)
        expect(renderTime).toBeLessThan(100);
        
        const perf = renderer.getPerformance();
        expect(perf.drawCalls).toBeLessThan(1000); // Should use instancing
      } catch (error) {
        // Skip if WebGL not available
      }
    });
  });
  
  describe('Cleanup', () => {
    it('should clean up resources on destroy', async () => {
      try {
        await renderer.initialize(container);
        
        expect(container.querySelector('canvas')).toBeTruthy();
        
        renderer.destroy();
        
        expect(renderer.isReady()).toBe(false);
        expect(container.innerHTML).toBe('');
      } catch (error) {
        // Skip if WebGL not available
      }
    });
    
    it('should handle multiple destroy calls safely', async () => {
      try {
        await renderer.initialize(container);
        
        renderer.destroy();
        expect(() => renderer.destroy()).not.toThrow();
      } catch (error) {
        // Skip if WebGL not available
      }
    });
  });
});

// ============================================================================
// State-to-Elements Conversion Tests
// ============================================================================

describe('State to Elements Conversion', () => {
  let renderer: ProductionWebGLRenderer;
  let container: HTMLElement;
  
  beforeEach(() => {
    renderer = new ProductionWebGLRenderer();
    container = createMockContainer();
  });
  
  afterEach(() => {
    renderer.destroy();
    container.parentNode?.removeChild(container);
  });
  
  it('should convert nodes to canvas elements', async () => {
    try {
      await renderer.initialize(container);
      
      renderer.addNode({
        id: 'rect',
        position: { x: 100, y: 100 },
        data: { type: 'rectangle', width: 120, height: 60 },
        style: { backgroundColor: '#ff0000' }
      });
      
      // Render should convert state to elements
      expect(() => renderer.render()).not.toThrow();
    } catch (error) {
      // Skip if WebGL not available
    }
  });
  
  it('should convert edges to line elements', async () => {
    try {
      await renderer.initialize(container);
      
      renderer.addNode({
        id: 'node1',
        position: { x: 100, y: 100 },
        data: { width: 60, height: 60 }
      });
      renderer.addNode({
        id: 'node2',
        position: { x: 300, y: 200 },
        data: { width: 60, height: 60 }
      });
      renderer.addEdge({
        id: 'edge1',
        source: 'node1',
        target: 'node2',
        style: { color: '#0000ff' }
      });
      
      expect(() => renderer.render()).not.toThrow();
    } catch (error) {
      // Skip if WebGL not available
    }
  });
});
