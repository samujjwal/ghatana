/**
 * Rendering Optimizations Module
 * 
 * Exports virtual viewport, LOD (Level of Detail) system, and WebGL renderer
 * for high-performance rendering.
 * 
 * @module rendering
 */

export {
  createVirtualViewport,
  createVisibilityChecker,
  VirtualViewportUtils,
  type ViewportBounds,
  type VirtualViewportConfig,
  type VisibilityResult,
  type ViewportStats,
} from './virtualViewport';

export {
  createLODSystem,
  LODLevel,
  DEFAULT_LOD_CONFIG,
  PERFORMANCE_LOD_CONFIG,
  QUALITY_LOD_CONFIG,
  GlyphRenderers,
  ProgressiveRendering,
  LODTransitions,
  createLODPerformanceMonitor,
  type LODConfig,
  type LODRenderInstruction,
  type ElementTypeLODConfig,
  type LODSystemInstance,
  type LODPerformanceMetrics,
  type LODPerformanceMonitor,
} from './lodSystem';

export {
  createWebGLRenderer,
  DEFAULT_WEBGL_CONFIG,
  WebGLRendererUtils,
  type WebGLRendererConfig,
  type WebGLCapabilities,
  type WebGLRenderStats,
  type WebGLRendererInstance,
} from './webglRenderer';
