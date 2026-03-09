/**
 * Renderer Module
 * 
 * Provides renderer abstraction with seamless switching between
 * DOM and WebGL renderers, with state preservation and fallback handling.
 * 
 * @module renderer
 */

// Main renderer abstraction
export {
  RendererSwitcher,
  DOMRenderer,
  WebGLRenderer, // Legacy basic implementation
  createRendererSwitcher,
  detectBestRenderer,
  type IRenderer,
  type RendererType,
  type RendererCapabilities,
  type CanvasState,
  type RendererPerformance,
  type RendererSwitcherConfig,
  type WebGLFallbackReason,
  type RendererSwitchEvent,
  type PluginAdapter,
} from './rendererSwitcher';

// Production WebGL renderer with full Feature 1.10 capabilities
export {
  ProductionWebGLRenderer,
} from './productionWebGLRenderer';
