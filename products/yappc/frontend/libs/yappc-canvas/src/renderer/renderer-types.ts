/**
 * Renderer Types
 * 
 * Core type definitions for canvas renderer abstraction.
 * Extracted to prevent circular dependencies.
 * 
 * @doc.type types
 * @doc.purpose Renderer type definitions
 * @doc.layer canvas/renderer
 */

import type { CanvasElement } from '../types/canvas-document';

/**
 * Renderer type
 */
export type RendererType = 'dom' | 'webgl';

/**
 * Renderer capabilities
 */
export interface RendererCapabilities {
  maxElements: number;
  supportsFilters: boolean;
  supportsBlending: boolean;
  supportsClipping: boolean;
  gpuAccelerated: boolean;
  maxTextureSize?: number;
}

/**
 * Canvas state
 */
export interface CanvasState {
  elements: CanvasElement[];
  viewport: {
    x: number;
    y: number;
    zoom: number;
  };
  selection: string[];
  version: number;
}

/**
 * Renderer performance metrics
 */
export interface RendererPerformance {
  fps: number;
  renderTime: number;
  elementCount: number;
  memoryUsage?: number;
}

/**
 * Renderer interface
 */
export interface IRenderer {
  type: RendererType;
  capabilities: RendererCapabilities;
  
  initialize(container: HTMLElement): Promise<void>;
  render(state: CanvasState): Promise<void>;
  destroy(): void;
  
  getPerformance(): RendererPerformance;
  supportsFeature(feature: string): boolean;
}
