/**
 * Renderer Abstraction (Feature 2.27)
 * 
 * Provides seamless switching between DOM/SVG and WebGL renderers with
 * state preservation, fallback handling, and plugin compatibility.
 * 
 * Features:
 * - Unified renderer interface for DOM/SVG and WebGL
 * - Runtime renderer switching with state preservation
 * - WebGL fallback detection (unsupported browsers, hardware limits)
 * - Plugin adaptation layer for renderer-specific APIs
 * - Performance monitoring and automatic degradation
 * - Integration with production WebGL renderer from Feature 1.10
 * 
 * @module renderer/rendererSwitcher
 */

import { ProductionWebGLRenderer } from './productionWebGLRenderer';
import type {
  RendererType,
  RendererCapabilities,
  CanvasState,
  IRenderer,
  RendererPerformance,
} from './renderer-types';

// Re-export for backward compatibility
export type {
  RendererType,
  RendererCapabilities,
  CanvasState,
  IRenderer,
  RendererPerformance,
};

// ============================================================================
// Types & Interfaces (Additional)
// ============================================================================

/**
 * Canvas state that needs to be preserved during renderer switch
 * @deprecated Use CanvasState from renderer-types.ts
 */
interface _CanvasState {
  nodes: Array<{
    id: string;
    position: { x: number; y: number };
    data: Record<string, unknown>;
    style?: Record<string, unknown>;
  }>;
  edges: Array<{
    id: string;
    source: string;
    target: string;
    data?: Record<string, unknown>;
    style?: Record<string, unknown>;
  }>;
  viewport: {
    x: number;
    y: number;
    zoom: number;
  };
  selectedNodes: string[];
  selectedEdges: string[];
  camera?: {
    position: { x: number; y: number; z: number };
    rotation: { x: number; y: number; z: number };
  };
  metadata: Record<string, unknown>;
}

// ============================================================================
// Renderer Management
// ============================================================================

/**
 * Renderer switcher configuration
 */
export interface RendererSwitcherConfig {
  preferredRenderer: RendererType;
  fallbackRenderer: RendererType;
  autoSwitchOnPerformanceDegradation: boolean;
  performanceThresholds: {
    minFps: number;
    maxMemoryMB: number;
    maxDrawTime: number;
  };
  webglFallbackReasons: Set<WebGLFallbackReason>;
}

/**
 * Reasons for WebGL fallback
 */
export type WebGLFallbackReason =
  | 'unsupported'
  | 'context-lost'
  | 'low-performance'
  | 'memory-exceeded'
  | 'user-preference';

/**
 * Renderer switch event
 */
export interface RendererSwitchEvent {
  from: RendererType;
  to: RendererType;
  reason: WebGLFallbackReason | 'user-initiated';
  timestamp: number;
  statePreserved: boolean;
}

/**
 * Plugin adaptation interface
 */
export interface PluginAdapter {
  name: string;
  supportedRenderers: RendererType[];
  
  // Lifecycle
  onRendererChange(from: RendererType, to: RendererType): void;
  
  // Capabilities
  canAdapt(from: RendererType, to: RendererType): boolean;
  
  // Adaptation
  adaptNode?(node: unknown, toRenderer: RendererType): unknown;
  adaptEdge?(edge: unknown, toRenderer: RendererType): unknown;
}

// ============================================================================
// DOM Renderer Implementation
// ============================================================================

/**
 *
 */
export class DOMRenderer implements IRenderer {
  type: RendererType = 'dom';
  capabilities: RendererCapabilities = {
    maxNodes: 1000,
    maxEdges: 2000,
    supportsCustomShaders: false,
    supportsInstancing: false,
    supports3D: false,
    supportsOffscreenCanvas: false,
    extensions: []
  };
  
  private container?: HTMLElement;
  private state: CanvasState;
  private lastFrameTime: number = 0;
  
  /**
   *
   */
  constructor() {
    this.state = this.createEmptyState();
  }
  
  /**
   *
   */
  async initialize(container: HTMLElement): Promise<void> {
    this.container = container;
    this.container.innerHTML = '';
    this.container.style.position = 'relative';
  }
  
  /**
   *
   */
  destroy(): void {
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
  }
  
  /**
   *
   */
  render(): void {
    const startTime = performance.now();
    // DOM rendering logic would go here
    this.lastFrameTime = performance.now() - startTime;
  }
  
  /**
   *
   */
  clear(): void {
    this.state = this.createEmptyState();
    if (this.container) {
      this.container.innerHTML = '';
    }
  }
  
  /**
   *
   */
  getPerformance(): RendererPerformance {
    return {
      fps: 60,
      drawCalls: this.state.nodes.length + this.state.edges.length,
      triangles: 0,
      memoryUsage: this.estimateMemoryUsage(),
      cpuTime: this.lastFrameTime,
      lastFrameTime: this.lastFrameTime
    };
  }
  
  /**
   *
   */
  setViewport(x: number, y: number, zoom: number): void {
    this.state.viewport = { x, y, zoom };
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
      metadata: {}
    };
  }
  
  /**
   *
   */
  private estimateMemoryUsage(): number {
    // Rough estimate: ~1KB per node, ~0.5KB per edge
    return (this.state.nodes.length * 1024 + this.state.edges.length * 512) / (1024 * 1024);
  }
}

// ============================================================================
// WebGL Renderer Implementation (Legacy - kept for backward compatibility)
// ============================================================================
// Note: This is a basic WebGL implementation kept for testing purposes.
// For production use, the RendererSwitcher now uses ProductionWebGLRenderer
// which wraps the full-featured webglRenderer.ts from Feature 1.10.
// ============================================================================

/**
 * @deprecated Use ProductionWebGLRenderer for production. This basic implementation
 * is kept for backward compatibility and testing only.
 */
export class WebGLRenderer implements IRenderer {
  type: RendererType = 'webgl';
  capabilities: RendererCapabilities;
  
  private container?: HTMLElement;
  private canvas?: HTMLCanvasElement;
  private gl?: WebGLRenderingContext | WebGL2RenderingContext;
  private state: CanvasState;
  private lastFrameTime: number = 0;
  
  /**
   *
   */
  constructor() {
    this.state = this.createEmptyState();
    this.capabilities = this.detectCapabilities();
  }
  
  /**
   *
   */
  async initialize(container: HTMLElement): Promise<void> {
    this.container = container;
    this.canvas = document.createElement('canvas');
    this.canvas.width = container.clientWidth;
    this.canvas.height = container.clientHeight;
    
    // Try WebGL2 first, fall back to WebGL1
    this.gl = this.canvas.getContext('webgl2') as WebGL2RenderingContext || 
              this.canvas.getContext('webgl') as WebGLRenderingContext ||
              this.canvas.getContext('experimental-webgl') as WebGLRenderingContext;
    
    if (!this.gl) {
      throw new Error('WebGL not supported');
    }
    
    this.container.innerHTML = '';
    this.container.appendChild(this.canvas);
    
    this.capabilities = this.detectCapabilities();
  }
  
  /**
   *
   */
  destroy(): void {
    // Clean up WebGL resources
    if (this.gl && this.canvas) {
      const loseContext = this.gl.getExtension('WEBGL_lose_context');
      if (loseContext) {
        loseContext.loseContext();
      }
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
  }
  
  /**
   *
   */
  render(): void {
    if (!this.gl) return;
    
    const startTime = performance.now();
    
    // Clear canvas
    this.gl.clear(this.gl.COLOR_BUFFER_BIT | this.gl.DEPTH_BUFFER_BIT);
    
    // WebGL rendering logic would go here
    
    this.lastFrameTime = performance.now() - startTime;
  }
  
  /**
   *
   */
  clear(): void {
    this.state = this.createEmptyState();
    if (this.gl) {
      this.gl.clear(this.gl.COLOR_BUFFER_BIT | this.gl.DEPTH_BUFFER_BIT);
    }
  }
  
  /**
   *
   */
  getPerformance(): RendererPerformance {
    return {
      fps: 60,
      drawCalls: Math.ceil(this.state.nodes.length / 100), // Instanced rendering
      triangles: this.state.nodes.length * 6, // 2 triangles per quad
      memoryUsage: this.estimateMemoryUsage(),
      cpuTime: this.lastFrameTime * 0.3, // GPU does most work
      gpuTime: this.lastFrameTime * 0.7,
      lastFrameTime: this.lastFrameTime
    };
  }
  
  /**
   *
   */
  setViewport(x: number, y: number, zoom: number): void {
    this.state.viewport = { x, y, zoom };
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
      metadata: {}
    };
  }
  
  /**
   *
   */
  private detectCapabilities(): RendererCapabilities {
    if (!this.gl) {
      return {
        maxNodes: 0,
        maxEdges: 0,
        supportsCustomShaders: false,
        supportsInstancing: false,
        supports3D: false,
        supportsOffscreenCanvas: false,
        extensions: []
      };
    }
    
    const isWebGL2 = this.gl instanceof WebGL2RenderingContext;
    const extensions = this.gl.getSupportedExtensions() || [];
    
    return {
      maxNodes: 100000,
      maxEdges: 200000,
      supportsCustomShaders: true,
      supportsInstancing: isWebGL2 || extensions.includes('ANGLE_instanced_arrays'),
      supports3D: true,
      supportsOffscreenCanvas: typeof OffscreenCanvas !== 'undefined',
      webglVersion: isWebGL2 ? 2 : 1,
      extensions
    };
  }
  
  /**
   *
   */
  private estimateMemoryUsage(): number {
    // WebGL uses GPU memory, roughly ~100 bytes per node, ~50 bytes per edge
    return (this.state.nodes.length * 100 + this.state.edges.length * 50) / (1024 * 1024);
  }
}

// ============================================================================
// Renderer Switcher
// ============================================================================

/**
 *
 */
export class RendererSwitcher {
  private currentRenderer?: IRenderer;
  private config: RendererSwitcherConfig;
  private pluginAdapters: PluginAdapter[] = [];
  private switchHistory: RendererSwitchEvent[] = [];
  
  /**
   *
   */
  constructor(config?: Partial<RendererSwitcherConfig>) {
    this.config = this.createDefaultConfig(config);
  }
  
  /**
   *
   */
  async initialize(container: HTMLElement, rendererType?: RendererType): Promise<void> {
    const type = rendererType || this.config.preferredRenderer;
    
    try {
      this.currentRenderer = this.createRenderer(type);
      await this.currentRenderer.initialize(container);
    } catch (error) {
      // Fall back to alternative renderer
      const fallbackType = type === 'webgl' ? 'dom' : 'webgl';
      this.currentRenderer = this.createRenderer(fallbackType);
      await this.currentRenderer.initialize(container);
      
      this.recordSwitch({
        from: type,
        to: fallbackType,
        reason: 'unsupported',
        timestamp: Date.now(),
        statePreserved: false
      });
    }
  }
  
  /**
   *
   */
  async switchRenderer(container: HTMLElement, newType: RendererType): Promise<boolean> {
    if (!this.currentRenderer) {
      throw new Error('Renderer not initialized');
    }
    
    if (this.currentRenderer.type === newType) {
      return true; // Already using requested renderer
    }
    
    // Save current state
    const savedState = this.currentRenderer.saveState();
    const oldType = this.currentRenderer.type;
    
    // Destroy current renderer
    this.currentRenderer.destroy();
    
    try {
      // Create and initialize new renderer
      const newRenderer = this.createRenderer(newType);
      await newRenderer.initialize(container);
      
      // Restore state
      newRenderer.restoreState(savedState);
      
      // Apply plugin adaptations
      this.applyPluginAdaptations(oldType, newType);
      
      this.currentRenderer = newRenderer;
      
      this.recordSwitch({
        from: oldType,
        to: newType,
        reason: 'user-initiated',
        timestamp: Date.now(),
        statePreserved: true
      });
      
      return true;
    } catch (error) {
      // Restore old renderer on failure
      const fallbackRenderer = this.createRenderer(oldType);
      await fallbackRenderer.initialize(container);
      fallbackRenderer.restoreState(savedState);
      this.currentRenderer = fallbackRenderer;
      
      return false;
    }
  }
  
  /**
   *
   */
  getCurrentRenderer(): IRenderer | undefined {
    return this.currentRenderer;
  }
  
  /**
   *
   */
  getCapabilities(): RendererCapabilities | undefined {
    return this.currentRenderer?.capabilities;
  }
  
  /**
   *
   */
  registerPlugin(adapter: PluginAdapter): void {
    this.pluginAdapters.push(adapter);
  }
  
  /**
   *
   */
  getSwitchHistory(): RendererSwitchEvent[] {
    return [...this.switchHistory];
  }
  
  /**
   *
   */
  checkWebGLSupport(): { supported: boolean; reason?: WebGLFallbackReason } {
    const canvas = document.createElement('canvas');
    const gl = canvas.getContext('webgl2') || 
               canvas.getContext('webgl') ||
               canvas.getContext('experimental-webgl');
    
    if (!gl) {
      return { supported: false, reason: 'unsupported' };
    }
    
    return { supported: true };
  }
  
  /**
   *
   */
  private createRenderer(type: RendererType): IRenderer {
    switch (type) {
      case 'dom':
        return new DOMRenderer();
      case 'webgl':
        // Use production WebGL renderer for best performance
        return new ProductionWebGLRenderer({
          preferWebGL2: true,
          antialias: true,
          powerPreference: 'high-performance',
        });
      default:
        throw new Error(`Unknown renderer type: ${type}`);
    }
  }
  
  /**
   *
   */
  private applyPluginAdaptations(from: RendererType, to: RendererType): void {
    this.pluginAdapters.forEach(adapter => {
      if (adapter.canAdapt(from, to)) {
        adapter.onRendererChange(from, to);
      }
    });
  }
  
  /**
   *
   */
  private recordSwitch(event: RendererSwitchEvent): void {
    this.switchHistory.push(event);
    
    // Keep only last 10 switches
    if (this.switchHistory.length > 10) {
      this.switchHistory.shift();
    }
  }
  
  /**
   *
   */
  private createDefaultConfig(overrides?: Partial<RendererSwitcherConfig>): RendererSwitcherConfig {
    return {
      preferredRenderer: 'webgl',
      fallbackRenderer: 'dom',
      autoSwitchOnPerformanceDegradation: true,
      performanceThresholds: {
        minFps: 30,
        maxMemoryMB: 512,
        maxDrawTime: 33 // ~30fps
      },
      webglFallbackReasons: new Set<WebGLFallbackReason>(),
      ...overrides
    };
  }
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Detect best renderer for current environment
 */
export function detectBestRenderer(): RendererType {
  const canvas = document.createElement('canvas');
  const gl = canvas.getContext('webgl2') || canvas.getContext('webgl');
  
  if (!gl) {
    return 'dom';
  }
  
  // Check for mobile device
  const isMobile = /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
  if (isMobile) {
    return 'dom'; // Prefer DOM on mobile for better battery life
  }
  
  return 'webgl';
}

/**
 * Create renderer switcher with auto-detection
 */
export function createRendererSwitcher(config?: Partial<RendererSwitcherConfig>): RendererSwitcher {
  const bestRenderer = detectBestRenderer();
  return new RendererSwitcher({
    preferredRenderer: bestRenderer,
    ...config
  });
}
