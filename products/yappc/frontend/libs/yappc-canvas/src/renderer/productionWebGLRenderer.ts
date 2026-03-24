/**
 * Production WebGL Renderer Adapter
 * 
 * Integrates the production-grade WebGL renderer from Feature 1.10
 * with the renderer abstraction interface from Feature 2.27.
 * 
 * This adapter bridges the high-performance rendering engine
 * with the unified renderer interface for seamless switching.
 * 
 * @module renderer/productionWebGLRenderer
 */

import {
  createWebGLRenderer,
  type WebGLRendererConfig,
  type WebGLCapabilities as ProductionCapabilities,
  DEFAULT_WEBGL_CONFIG,
} from '../rendering/webglRenderer';

import type {
  IRenderer,
  RendererType,
  RendererCapabilities,
  CanvasState,
  RendererPerformance,
} from './renderer-types';
import type { CanvasElement } from '../types/canvas-document';

/**
 * Production WebGL Renderer with full Feature 1.10 capabilities
 * 
 * Wraps the production webglRenderer.ts implementation with the
 * IRenderer interface for use in renderer switching.
 */
export class ProductionWebGLRenderer implements IRenderer {
  type: RendererType = 'webgl';
  capabilities: RendererCapabilities;
  
  private container?: HTMLElement;
  private canvas?: HTMLCanvasElement;
  private renderer?: ReturnType<typeof createWebGLRenderer>;
  private state: CanvasState;
  private config: WebGLRendererConfig;
  
  /**
   *
   */
  constructor(config?: Partial<WebGLRendererConfig>) {
    this.config = { ...DEFAULT_WEBGL_CONFIG, ...config };
    this.state = this.createEmptyState();
    this.capabilities = this.createInitialCapabilities();
  }
  
  /**
   *
   */
  async initialize(container: HTMLElement): Promise<void> {
    this.container = container;
    
    // Create canvas element
    this.canvas = document.createElement('canvas');
    this.canvas.width = container.clientWidth || 800;
    this.canvas.height = container.clientHeight || 600;
    this.canvas.style.display = 'block';
    this.canvas.style.width = '100%';
    this.canvas.style.height = '100%';
    
    // Clear container and add canvas
    this.container.innerHTML = '';
    this.container.appendChild(this.canvas);
    
    // Initialize production WebGL renderer
    this.renderer = createWebGLRenderer(this.canvas, this.config);
    
    if (!this.renderer.isSupported()) {
      throw new Error('WebGL not supported in this environment');
    }
    
    this.renderer.initialize();
    
    // Update capabilities with detected features
    this.updateCapabilities();
    
    // Set initial viewport
    this.renderer.setViewport({
      x: this.state.viewport.x,
      y: this.state.viewport.y,
      width: this.canvas.width,
      height: this.canvas.height,
      zoom: this.state.viewport.zoom,
    });
  }
  
  /**
   *
   */
  destroy(): void {
    if (this.renderer) {
      this.renderer.dispose();
      this.renderer = undefined;
    }
    
    if (this.canvas) {
      this.canvas.remove();
      this.canvas = undefined;
    }
    
    if (this.container) {
      this.container.innerHTML = '';
    }
  }
  
  /**
   *
   */
  saveState(): CanvasState {
    return JSON.parse(JSON.stringify(this.state));
  }
  
  /**
   *
   */
  restoreState(state: CanvasState): void {
    this.state = JSON.parse(JSON.stringify(state));
    
    // Update renderer viewport if initialized
    if (this.renderer && this.canvas) {
      this.renderer.setViewport({
        x: state.viewport.x,
        y: state.viewport.y,
        width: this.canvas.width,
        height: this.canvas.height,
        zoom: state.viewport.zoom,
      });
    }
  }
  
  /**
   *
   */
  render(): void {
    if (!this.renderer) {
      return;
    }
    
    // Convert state to CanvasElement format
    const elements = this.stateToElements();
    
    // Render using production WebGL renderer
    this.renderer.render(elements);
  }
  
  /**
   *
   */
  clear(): void {
    this.state = this.createEmptyState();
    
    if (this.renderer) {
      this.renderer.clear();
    }
  }
  
  /**
   *
   */
  getPerformance(): RendererPerformance {
    const memoryUsage = this.estimateMemoryUsage();
    
    if (!this.renderer) {
      return {
        ...this.createEmptyPerformance(),
        memoryUsage, // Still report memory based on state even if not initialized
      };
    }
    
    const stats = this.renderer.getStats();
    
    return {
      fps: stats.fps,
      drawCalls: stats.drawCalls,
      triangles: stats.triangles,
      memoryUsage,
      cpuTime: stats.frameTime * 0.3, // Approximate CPU time
      gpuTime: stats.frameTime * 0.7, // Approximate GPU time
      lastFrameTime: stats.frameTime,
    };
  }
  
  /**
   *
   */
  setViewport(x: number, y: number, zoom: number): void {
    this.state.viewport = { x, y, zoom };
    
    if (this.renderer && this.canvas) {
      this.renderer.setViewport({
        x,
        y,
        width: this.canvas.width,
        height: this.canvas.height,
        zoom,
      });
    }
  }
  
  /**
   *
   */
  getViewport() {
    return this.state.viewport;
  }
  
  /**
   *
   */
  addNode(node: CanvasState['nodes'][0]): void {
    this.state.nodes.push(node);
  }
  
  /**
   *
   */
  updateNode(id: string, updates: Partial<CanvasState['nodes'][0]>): void {
    const node = this.state.nodes.find(n => n.id === id);
    if (node) {
      Object.assign(node, updates);
    }
  }
  
  /**
   *
   */
  removeNode(id: string): void {
    this.state.nodes = this.state.nodes.filter(n => n.id !== id);
    // Also remove edges connected to this node
    this.state.edges = this.state.edges.filter(
      e => e.source !== id && e.target !== id
    );
  }
  
  /**
   *
   */
  addEdge(edge: CanvasState['edges'][0]): void {
    this.state.edges.push(edge);
  }
  
  /**
   *
   */
  updateEdge(id: string, updates: Partial<CanvasState['edges'][0]>): void {
    const edge = this.state.edges.find(e => e.id === id);
    if (edge) {
      Object.assign(edge, updates);
    }
  }
  
  /**
   *
   */
  removeEdge(id: string): void {
    this.state.edges = this.state.edges.filter(e => e.id !== id);
  }
  
  // ============================================================================
  // Helper Methods
  // ============================================================================
  
  /**
   *
   */
  private createEmptyState(): CanvasState {
    return {
      nodes: [],
      edges: [],
      viewport: { x: 0, y: 0, zoom: 1 },
      selectedNodes: [],
      selectedEdges: [],
      metadata: {},
    };
  }
  
  /**
   *
   */
  private createInitialCapabilities(): RendererCapabilities {
    return {
      maxNodes: 100000,
      maxEdges: 200000,
      supportsCustomShaders: true,
      supportsInstancing: true,
      supports3D: false, // 2D canvas for now
      supportsOffscreenCanvas: typeof OffscreenCanvas !== 'undefined',
      webglVersion: 2, // Will be updated after initialization
      extensions: [],
    };
  }
  
  /**
   *
   */
  private updateCapabilities(): void {
    if (!this.renderer) {
      return;
    }
    
    const caps = this.renderer.getCapabilities();

    if (!caps) {
      this.capabilities = {
        maxNodes: 100000,
        maxEdges: 200000,
        supportsCustomShaders: true,
        supportsInstancing: false,
        supports3D: false,
        supportsOffscreenCanvas: typeof OffscreenCanvas !== 'undefined',
        webglVersion: 1,
        extensions: [],
      };
      return;
    }

    this.capabilities = {
      maxNodes: 100000, // Production renderer can handle very large scenes
      maxEdges: 200000,
      supportsCustomShaders: true,
      supportsInstancing: Boolean(caps.supportsInstancedArrays),
      supports3D: false, // 2D canvas
      supportsOffscreenCanvas: typeof OffscreenCanvas !== 'undefined',
      webglVersion: caps.version || 1,
      extensions: Array.from(caps.extensions ?? []),
    };
  }
  
  /**
   *
   */
  private createEmptyPerformance(): RendererPerformance {
    return {
      fps: 0,
      drawCalls: 0,
      triangles: 0,
      memoryUsage: 0,
      cpuTime: 0,
      gpuTime: 0,
      lastFrameTime: 0,
    };
  }
  
  /**
   *
   */
  private estimateMemoryUsage(): number {
    // Estimate based on vertex data
    // ~100 bytes per node (position, color, size, etc.)
    // ~50 bytes per edge (endpoints, color, width)
    const nodeMemory = this.state.nodes.length * 100;
    const edgeMemory = this.state.edges.length * 50;
    
    const totalBytes = nodeMemory + edgeMemory;
    return totalBytes / (1024 * 1024); // Convert to MB
  }
  
  /**
   * Convert renderer state to CanvasElement format
   * 
   * Maps the CanvasState format (used by renderer switcher) to
   * the CanvasElement format (used by production renderer).
   */
  private stateToElements(): CanvasElement[] {
    const elements: CanvasElement[] = [];

    this.state.nodes.forEach(node => {
      const width = (node.data.width as number) || 120;
      const height = (node.data.height as number) || 60;
      const style = node.style ?? {};
      const elementType = typeof node.data.type === 'string' ? node.data.type : 'node';
      const timestamp = new Date();

      elements.push({
        id: node.id,
        type: 'node',
        transform: {
          position: { x: node.position.x, y: node.position.y },
          scale: typeof style.scale === 'number' ? style.scale : 1,
          rotation: typeof style.rotation === 'number' ? style.rotation : 0,
        },
        bounds: {
          x: node.position.x,
          y: node.position.y,
          width,
          height,
        },
        visible: style.visible !== false,
        locked: Boolean(style.locked),
        selected: false,
        zIndex: typeof style.zIndex === 'number' ? style.zIndex : 0,
        metadata: {
          custom: {
            elementType,
            style,
            data: node.data,
          },
        },
        version: '1.0.0',
        createdAt: timestamp,
        updatedAt: timestamp,
      });
    });

    this.state.edges.forEach(edge => {
      const sourceNode = this.state.nodes.find(n => n.id === edge.source);
      const targetNode = this.state.nodes.find(n => n.id === edge.target);

      if (!sourceNode || !targetNode) {
        return;
      }

      const sourceWidth = (sourceNode.data.width as number) || 120;
      const sourceHeight = (sourceNode.data.height as number) || 60;
      const style = edge.style ?? {};
      const startX = sourceNode.position.x + sourceWidth / 2;
      const startY = sourceNode.position.y + sourceHeight / 2;
      const width = Math.abs(targetNode.position.x - sourceNode.position.x);
      const height = Math.abs(targetNode.position.y - sourceNode.position.y);
      const timestamp = new Date();

      elements.push({
        id: edge.id,
        type: 'edge',
        transform: {
          position: { x: startX, y: startY },
          scale: 1,
          rotation: 0,
        },
        bounds: {
          x: startX,
          y: startY,
          width,
          height,
        },
        visible: style.visible !== false,
        locked: false,
        selected: false,
        zIndex: typeof style.zIndex === 'number' ? style.zIndex : 0,
        metadata: {
          custom: {
            style,
            data: edge.data ?? {},
            source: edge.source,
            target: edge.target,
          },
        },
        version: '1.0.0',
        createdAt: timestamp,
        updatedAt: timestamp,
      });
    });

    return elements;
  }
  
  /**
   * Get the underlying production renderer instance
   * 
   * Allows access to advanced features not exposed through IRenderer interface
   */
  getProductionRenderer(): ReturnType<typeof createWebGLRenderer> | undefined {
    return this.renderer;
  }
  
  /**
   * Check if renderer is initialized and ready
   */
  isReady(): boolean {
    return this.renderer !== undefined && this.canvas !== undefined;
  }
  
  /**
   * Get detailed WebGL capabilities
   */
  getDetailedCapabilities(): ProductionCapabilities | null {
    if (!this.renderer) {
      return null;
    }
    
    return this.renderer.getCapabilities();
  }
}
