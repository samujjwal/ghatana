/**
 * Tests for WebGL Renderer
 * 
 * Comprehensive test suite covering:
 * - WebGL context initialization
 * - Capability detection
 * - Rendering pipeline
 * - Performance statistics
 * - Error handling and fallbacks
 * 
 * @module rendering/__tests__/webglRenderer.test
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import {
  createWebGLRenderer,
  DEFAULT_WEBGL_CONFIG,
  WebGLRendererUtils,
  type WebGLRendererConfig,
  type WebGLCapabilities,
} from '../webglRenderer';

import type { CanvasElement } from '../../types/canvas-document';

// Mock HTMLCanvasElement
function createMockCanvas(): HTMLCanvasElement {
  const canvas = document.createElement('canvas');
  canvas.width = 800;
  canvas.height = 600;
  return canvas;
}

// Test element helper
function createTestElement(
  id: string,
  x: number,
  y: number,
  width: number = 100,
  height: number = 100
): CanvasElement {
  return {
    id,
    type: 'node',
    transform: {
      position: { x, y },
      scale: 1,
      rotation: 0,
    },
    bounds: { x, y, width, height },
    visible: true,
    locked: false,
    selected: false,
    zIndex: 0,
    metadata: {},
    version: '1.0.0',
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

describe('WebGL Renderer', () => {
  let canvas: HTMLCanvasElement;

  beforeEach(() => {
    canvas = createMockCanvas();
  });

  afterEach(() => {
    // Cleanup
  });

  describe('Initialization', () => {
    it('should create renderer with default config', () => {
      const renderer = createWebGLRenderer(canvas);
      
      expect(renderer).toBeDefined();
      expect(renderer.config.preferWebGL2).toBe(true);
      expect(renderer.config.antialias).toBe(true);
    });

    it('should create renderer with custom config', () => {
      const config: Partial<WebGLRendererConfig> = {
        preferWebGL2: false,
        antialias: false,
        maxBatchSize: 500,
      };

      const renderer = createWebGLRenderer(canvas, config);

      expect(renderer.config.preferWebGL2).toBe(false);
      expect(renderer.config.antialias).toBe(false);
      expect(renderer.config.maxBatchSize).toBe(500);
    });

    it('should check WebGL support', () => {
      const renderer = createWebGLRenderer(canvas);
      const supported = renderer.isSupported();

      // Support check should return boolean
      expect(typeof supported).toBe('boolean');
    });
  });

  describe('Capabilities', () => {
    it('should detect WebGL capabilities after initialization', () => {
      const renderer = createWebGLRenderer(canvas);
      
      if (renderer.isSupported()) {
        const initialized = renderer.initialize();
        
        if (initialized) {
          const capabilities = renderer.getCapabilities();
          
          expect(capabilities).toBeDefined();
          if (capabilities) {
            expect(capabilities.version).toMatch(/^[12]$/);
            expect(capabilities.maxTextureSize).toBeGreaterThan(0);
            expect(capabilities.extensions).toBeInstanceOf(Set);
          }
        }
      }
    });

    it('should return null capabilities before initialization', () => {
      const renderer = createWebGLRenderer(canvas);
      const capabilities = renderer.getCapabilities();

      expect(capabilities).toBeNull();
    });
  });

  describe('Viewport', () => {
    it('should set viewport bounds', () => {
      const renderer = createWebGLRenderer(canvas);
      
      if (renderer.isSupported() && renderer.initialize()) {
        renderer.setViewport({
          x: 100,
          y: 200,
          width: 1920,
          height: 1080,
          zoom: 1.5,
        });

        // Viewport should be set (checked via rendering)
        const stats = renderer.getStats();
        expect(stats).toBeDefined();
      }
    });
  });

  describe('Rendering', () => {
    it('should render empty element array', () => {
      const renderer = createWebGLRenderer(canvas);
      
      if (renderer.isSupported() && renderer.initialize()) {
        renderer.render([]);

        const stats = renderer.getStats();
        expect(stats.drawCalls).toBe(0);
        expect(stats.vertices).toBe(0);
      }
    });

    it('should render single element', () => {
      const renderer = createWebGLRenderer(canvas);
      
      if (renderer.isSupported() && renderer.initialize()) {
        const element = createTestElement('el1', 100, 100);
        renderer.render([element]);

        const stats = renderer.getStats();
        expect(stats.drawCalls).toBeGreaterThan(0);
        expect(stats.vertices).toBeGreaterThan(0);
      }
    });

    it('should render multiple elements', () => {
      const renderer = createWebGLRenderer(canvas);
      
      if (renderer.isSupported() && renderer.initialize()) {
        const elements = [
          createTestElement('el1', 0, 0),
          createTestElement('el2', 100, 100),
          createTestElement('el3', 200, 200),
        ];

        renderer.render(elements);

        const stats = renderer.getStats();
        expect(stats.drawCalls).toBe(3);
        expect(stats.vertices).toBe(12); // 4 vertices per rect * 3
        expect(stats.triangles).toBe(6); // 2 triangles per rect * 3
      }
    });

    it('should clear canvas', () => {
      const renderer = createWebGLRenderer(canvas);
      
      if (renderer.isSupported() && renderer.initialize()) {
        const element = createTestElement('el1', 100, 100);
        renderer.render([element]);
        
        // Clear should succeed
        renderer.clear();
        
        const stats = renderer.getStats();
        expect(stats).toBeDefined();
      }
    });
  });

  describe('Performance Statistics', () => {
    it('should track draw calls', () => {
      const renderer = createWebGLRenderer(canvas);
      
      if (renderer.isSupported() && renderer.initialize()) {
        const elements = Array.from({ length: 10 }, (_, i) =>
          createTestElement(`el${i}`, i * 100, 0)
        );

        renderer.render(elements);

        const stats = renderer.getStats();
        expect(stats.drawCalls).toBe(10);
      }
    });

    it('should track vertices and triangles', () => {
      const renderer = createWebGLRenderer(canvas);
      
      if (renderer.isSupported() && renderer.initialize()) {
        const element = createTestElement('el1', 0, 0);
        renderer.render([element]);

        const stats = renderer.getStats();
        expect(stats.vertices).toBe(4); // Rectangle has 4 vertices
        expect(stats.triangles).toBe(2); // Rectangle has 2 triangles
      }
    });

    it('should measure frame time', () => {
      const renderer = createWebGLRenderer(canvas);
      
      if (renderer.isSupported() && renderer.initialize()) {
        const element = createTestElement('el1', 0, 0);
        renderer.render([element]);

        const stats = renderer.getStats();
        expect(stats.frameTime).toBeGreaterThanOrEqual(0);
        expect(stats.fps).toBeGreaterThan(0);
      }
    });

    it('should calculate FPS', () => {
      const renderer = createWebGLRenderer(canvas);
      
      if (renderer.isSupported() && renderer.initialize()) {
        // Render multiple frames
        const element = createTestElement('el1', 0, 0);
        
        for (let i = 0; i < 5; i++) {
          renderer.render([element]);
        }

        const stats = renderer.getStats();
        expect(stats.fps).toBeGreaterThan(0);
        expect(stats.fps).toBeLessThanOrEqual(1000); // Sanity check
      }
    });
  });

  describe('Resource Management', () => {
    it('should dispose resources', () => {
      const renderer = createWebGLRenderer(canvas);
      
      if (renderer.isSupported() && renderer.initialize()) {
        const element = createTestElement('el1', 0, 0);
        renderer.render([element]);

        // Dispose should not throw
        expect(() => renderer.dispose()).not.toThrow();
      }
    });

    it('should handle operations after dispose gracefully', () => {
      const renderer = createWebGLRenderer(canvas);
      
      if (renderer.isSupported() && renderer.initialize()) {
        renderer.dispose();

        // Operations after dispose should not crash
        expect(() => {
          const element = createTestElement('el1', 0, 0);
          renderer.render([element]);
        }).not.toThrow();
      }
    });
  });

  describe('Context Access', () => {
    it('should return null context before initialization', () => {
      const renderer = createWebGLRenderer(canvas);
      const ctx = renderer.getContext();

      expect(ctx).toBeNull();
    });

    it('should return WebGL context after initialization', () => {
      const renderer = createWebGLRenderer(canvas);
      
      if (renderer.isSupported() && renderer.initialize()) {
        const ctx = renderer.getContext();
        
        if (ctx) {
          expect(ctx).toBeDefined();
          // Check it's actually a WebGL context
          expect(typeof ctx.drawArrays).toBe('function');
        }
      }
    });
  });
});

describe('WebGL Renderer Utils', () => {
  describe('Support Detection', () => {
    it('should check WebGL 2 support', () => {
      const supported = WebGLRendererUtils.isWebGL2Supported();
      expect(typeof supported).toBe('boolean');
    });

    it('should check WebGL 1 support', () => {
      const supported = WebGLRendererUtils.isWebGLSupported();
      expect(typeof supported).toBe('boolean');
    });
  });

  describe('Renderer Recommendation', () => {
    it('should recommend canvas2d for small scenes', () => {
      const recommendation = WebGLRendererUtils.getRecommendedRenderer(100);
      expect(recommendation).toBe('canvas2d');
    });

    it('should recommend WebGL for medium scenes', () => {
      const recommendation = WebGLRendererUtils.getRecommendedRenderer(500);
      expect(recommendation).toBe('webgl');
    });

    it('should recommend WebGL for large scenes', () => {
      const recommendation = WebGLRendererUtils.getRecommendedRenderer(5000);
      expect(recommendation).toBe('webgl');
    });
  });

  describe('Performance Estimation', () => {
    it('should estimate no gain for tiny scenes', () => {
      const gain = WebGLRendererUtils.estimatePerformanceGain(50);
      expect(gain).toBe(1);
    });

    it('should estimate 2x gain for small scenes', () => {
      const gain = WebGLRendererUtils.estimatePerformanceGain(200);
      expect(gain).toBe(2);
    });

    it('should estimate 5x gain for medium scenes', () => {
      const gain = WebGLRendererUtils.estimatePerformanceGain(600);
      expect(gain).toBe(5);
    });

    it('should estimate 10x gain for large scenes', () => {
      const gain = WebGLRendererUtils.estimatePerformanceGain(2000);
      expect(gain).toBe(10);
    });

    it('should estimate 50x gain for very large scenes', () => {
      const gain = WebGLRendererUtils.estimatePerformanceGain(10000);
      expect(gain).toBe(50);
    });
  });
});

describe('Integration Tests', () => {
  it('should handle complete render pipeline', () => {
    const canvas = createMockCanvas();
    const renderer = createWebGLRenderer(canvas);

    if (!renderer.isSupported()) {
      console.log('WebGL not supported, skipping integration test');
      return;
    }

    // Initialize
    const initialized = renderer.initialize();
    expect(initialized).toBe(true);

    // Set viewport
    renderer.setViewport({
      x: 0,
      y: 0,
      width: 1920,
      height: 1080,
      zoom: 1.0,
    });

    // Create scene
    const elements: CanvasElement[] = [];
    for (let i = 0; i < 100; i++) {
      const x = (i % 10) * 100;
      const y = Math.floor(i / 10) * 100;
      elements.push(createTestElement(`el-${i}`, x, y));
    }

    // Render multiple frames
    for (let frame = 0; frame < 3; frame++) {
      renderer.render(elements);
    }

    // Check stats
    const stats = renderer.getStats();
    expect(stats.drawCalls).toBe(100);
    expect(stats.fps).toBeGreaterThan(0);

    // Cleanup
    renderer.dispose();
  });

  it('should handle viewport changes', () => {
    const canvas = createMockCanvas();
    const renderer = createWebGLRenderer(canvas);

    if (!renderer.isSupported() || !renderer.initialize()) {
      return;
    }

    const element = createTestElement('el1', 500, 500);

    // Render at different zoom levels
    const zooms = [0.5, 1.0, 2.0];
    
    for (const zoom of zooms) {
      renderer.setViewport({
        x: 0,
        y: 0,
        width: 1920,
        height: 1080,
        zoom,
      });

      renderer.render([element]);

      const stats = renderer.getStats();
      expect(stats.drawCalls).toBeGreaterThan(0);
    }

    renderer.dispose();
  });
});
