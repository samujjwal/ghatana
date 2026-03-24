/**
 * Tests for Virtual Viewport and LOD System
 * 
 * Comprehensive test suite covering:
 * - Virtual viewport visibility culling
 * - Spatial indexing and quad-tree performance
 * - LOD level switching and rendering instructions
 * - Performance benchmarks for large scenes
 * 
 * @module rendering/__tests__/virtualization.test
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  createLODSystem,
  LODLevel,
  DEFAULT_LOD_CONFIG,
  PERFORMANCE_LOD_CONFIG,
  QUALITY_LOD_CONFIG,
  GlyphRenderers,
  ProgressiveRendering,
  LODTransitions,
  createLODPerformanceMonitor,
} from '../lodSystem';
import {
  createVirtualViewport,
  createVisibilityChecker,
  VirtualViewportUtils,
  type ViewportBounds,
} from '../virtualViewport';

import type { CanvasElement } from '../../types/canvas-document';

// Test helpers
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

function createTestViewport(
  x: number = 0,
  y: number = 0,
  width: number = 1920,
  height: number = 1080,
  zoom: number = 1
): ViewportBounds {
  return { x, y, width, height, zoom };
}

describe('Virtual Viewport System', () => {
  describe('Basic Visibility', () => {
    it('should identify visible elements within viewport', () => {
      const viewport = createVirtualViewport();
      viewport.updateViewport(createTestViewport(0, 0, 1000, 1000));

      const elements = [
        createTestElement('visible1', 100, 100),
        createTestElement('visible2', 500, 500),
        createTestElement('offscreen', 2000, 2000),
      ];

      const visible = viewport.getVisibleElements(elements);

      expect(visible).toHaveLength(2);
      expect(visible.map(el => el.id)).toContain('visible1');
      expect(visible.map(el => el.id)).toContain('visible2');
      expect(visible.map(el => el.id)).not.toContain('offscreen');
    });

    it('should handle margin for prefetching', () => {
      const viewport = createVirtualViewport({ margin: 200 });
      viewport.updateViewport(createTestViewport(0, 0, 1000, 1000));

      // Element just outside viewport but within margin
      const elements = [createTestElement('marginal', 1100, 500)];

      const visible = viewport.getVisibleElements(elements);

      expect(visible).toHaveLength(1);
    });

    it('should check individual element visibility', () => {
      const viewport = createVirtualViewport();
      viewport.updateViewport(createTestViewport(0, 0, 1000, 1000));

      const visibleEl = createTestElement('visible', 500, 500);
      const offscreenEl = createTestElement('offscreen', 2000, 2000);

      expect(viewport.isElementVisible(visibleEl)).toBe(true);
      expect(viewport.isElementVisible(offscreenEl)).toBe(false);
    });
  });

  describe('Spatial Indexing', () => {
    it('should use spatial index for large element sets', () => {
      const viewport = createVirtualViewport({ useSpatialIndex: true });
      viewport.updateViewport(createTestViewport(0, 0, 1000, 1000));

      // Create 1000 elements
      const elements: CanvasElement[] = [];
      for (let i = 0; i < 1000; i++) {
        const x = (i % 50) * 200;
        const y = Math.floor(i / 50) * 200;
        elements.push(createTestElement(`el-${i}`, x, y));
      }

      const startTime = performance.now();
      const visible = viewport.getVisibleElements(elements);
      const queryTime = performance.now() - startTime;

      // Should be fast with spatial index
      expect(queryTime).toBeLessThan(10); // <10ms for 1000 elements
      expect(visible.length).toBeGreaterThan(0);
      expect(visible.length).toBeLessThan(elements.length);
    });

    it('should invalidate index when elements change', () => {
      const viewport = createVirtualViewport({ useSpatialIndex: true });
      viewport.updateViewport(createTestViewport(0, 0, 1000, 1000));

      const elements = [createTestElement('el1', 500, 500)];
      viewport.getVisibleElements(elements);

      // Invalidate and query again
      viewport.invalidateIndex();
      const visible = viewport.getVisibleElements(elements);

      expect(visible).toHaveLength(1);
    });
  });

  describe('Performance Statistics', () => {
    it('should track visibility statistics', () => {
      const viewport = createVirtualViewport();
      viewport.updateViewport(createTestViewport(0, 0, 1000, 1000));

      const elements = [
        createTestElement('v1', 100, 100),
        createTestElement('v2', 500, 500),
        createTestElement('off1', 2000, 2000),
        createTestElement('off2', 3000, 3000),
      ];

      viewport.getVisibleElements(elements);
      const stats = viewport.getStats();

      expect(stats.totalElements).toBe(4);
      expect(stats.visibleElements).toBe(2);
      expect(stats.culledElements).toBe(2);
      expect(stats.indexQueryTime).toBeGreaterThanOrEqual(0);
    });
  });

  describe('Visibility Checker', () => {
    it('should provide simple visibility check', () => {
      const checker = createVisibilityChecker();
      const viewport = createTestViewport(0, 0, 1000, 1000);

      const visibleEl = createTestElement('visible', 500, 500);
      const offscreenEl = createTestElement('offscreen', 2000, 2000);

      expect(checker.isVisible(visibleEl, viewport)).toBe(true);
      expect(checker.isVisible(offscreenEl, viewport)).toBe(false);
    });

    it('should count visible elements', () => {
      const checker = createVisibilityChecker();
      const viewport = createTestViewport(0, 0, 1000, 1000);

      const elements = [
        createTestElement('v1', 100, 100),
        createTestElement('v2', 500, 500),
        createTestElement('off', 2000, 2000),
      ];

      const count = checker.getVisibleCount(elements, viewport);
      expect(count).toBe(2);
    });
  });

  describe('Throttling', () => {
    it('should throttle viewport updates', async () => {
      const viewport = createVirtualViewport({ updateThrottle: 50 });

      viewport.updateViewport(createTestViewport(0, 0, 1000, 1000));
      
      // Create elements to update stats
      const elements = [createTestElement('el1', 500, 500)];
      viewport.getVisibleElements(elements);
      
      // Wait for throttle
      await new Promise(resolve => setTimeout(resolve, 60));

      viewport.updateViewport(createTestViewport(200, 200, 1000, 1000));
      viewport.getVisibleElements(elements);

      // Verify stats updated
      const stats = viewport.getStats();
      expect(stats.lastUpdateTime).toBeGreaterThan(0);
    });
  });

  describe('Max Visible Limit', () => {
    it('should respect max visible nodes limit', () => {
      const viewport = createVirtualViewport({ maxVisibleNodes: 10 });
      viewport.updateViewport(createTestViewport(0, 0, 10000, 10000));

      // Create 50 elements all in viewport
      const elements: CanvasElement[] = [];
      for (let i = 0; i < 50; i++) {
        elements.push(createTestElement(`el-${i}`, i * 100, 100));
      }

      const visible = viewport.getVisibleElements(elements);

      expect(visible.length).toBeLessThanOrEqual(10);
    });
  });
});

describe('LOD System', () => {
  describe('LOD Level Determination', () => {
    it('should determine LOD levels based on zoom', () => {
      const lod = createLODSystem();

      expect(lod.getLODLevel(1.0)).toBe(LODLevel.FULL);
      expect(lod.getLODLevel(0.5)).toBe(LODLevel.MEDIUM);
      expect(lod.getLODLevel(0.3)).toBe(LODLevel.LOW);
      expect(lod.getLODLevel(0.08)).toBe(LODLevel.GLYPH); // Below glyph threshold (0.1)
      expect(lod.getLODLevel(0.03)).toBe(LODLevel.HIDDEN);
    });

    it('should use custom LOD thresholds', () => {
      const lod = createLODSystem({
        fullDetailThreshold: 1.0,
        mediumDetailThreshold: 0.6,
        lowDetailThreshold: 0.3,
        glyphThreshold: 0.15,
        hideThreshold: 0.08,
      });

      expect(lod.getLODLevel(0.9)).toBe(LODLevel.MEDIUM);
      expect(lod.getLODLevel(0.5)).toBe(LODLevel.LOW);
    });
  });

  describe('LOD Instructions', () => {
    it('should generate render instructions for elements', () => {
      const lod = createLODSystem();
      const elements = [
        createTestElement('el1', 0, 0),
        createTestElement('el2', 100, 100),
      ];

      const instructions = lod.getLODInstructions(elements, 0.5);

      expect(instructions).toHaveLength(2);
      expect(instructions[0].elementId).toBe('el1');
      expect(instructions[0].level).toBe(LODLevel.MEDIUM);
      expect(instructions[0].showLabels).toBe(true);
      expect(instructions[0].showEffects).toBe(false);
      expect(instructions[0].showContent).toBe(true);
    });

    it('should handle glyph rendering at low zoom', () => {
      const lod = createLODSystem();
      const element = createTestElement('el1', 0, 0, 200, 200);

      // Use zoom below glyph threshold (0.1) to get glyph
      const instructions = lod.getLODInstructions([element], 0.08);

      expect(instructions[0].level).toBe(LODLevel.GLYPH);
      expect(instructions[0].glyphSize).toBeDefined();
      expect(instructions[0].showContent).toBe(false);
    });
  });

  describe('Quality Settings', () => {
    it('should apply performance LOD config', () => {
      const lod = createLODSystem(PERFORMANCE_LOD_CONFIG);

      // Performance mode should be more aggressive
      expect(lod.getLODLevel(0.9)).toBe(LODLevel.MEDIUM);
    });

    it('should apply quality LOD config', () => {
      const lod = createLODSystem(QUALITY_LOD_CONFIG);

      // Quality mode should maintain detail longer
      // With QUALITY_LOD_CONFIG fullDetailThreshold = 0.5
      expect(lod.getLODLevel(0.6)).toBe(LODLevel.FULL);
    });
  });

  describe('Element Filtering', () => {
    it('should filter elements by minimum LOD level', () => {
      const lod = createLODSystem();
      const elements = [
        createTestElement('el1', 0, 0),
        createTestElement('el2', 100, 100),
        createTestElement('el3', 200, 200),
      ];

      const filtered = lod.filterByLOD(elements, 0.15, LODLevel.GLYPH);

      // At zoom 0.15, all should be glyph or higher
      expect(filtered.length).toBeGreaterThan(0);
    });

    it('should check if element should render', () => {
      const lod = createLODSystem();
      const element = createTestElement('el1', 0, 0);

      expect(lod.shouldRenderElement(element, 0.5)).toBe(true);
      expect(lod.shouldRenderElement(element, 0.01)).toBe(false); // Below hide threshold
    });
  });

  describe('Quality Multiplier', () => {
    it('should calculate quality multiplier', () => {
      const lod = createLODSystem();

      expect(lod.getQualityMultiplier(1.0)).toBe(1.0);
      expect(lod.getQualityMultiplier(0.5)).toBe(0.7);
      expect(lod.getQualityMultiplier(0.3)).toBe(0.4);
      expect(lod.getQualityMultiplier(0.15)).toBe(0.2);
    });
  });
});

describe('Glyph Renderers', () => {
  const element = createTestElement('el1', 0, 0, 100, 100);

  it('should render rectangle glyph', () => {
    const svg = GlyphRenderers.rectangle(element);
    expect(svg).toContain('<rect');
    expect(svg).toContain('width="100"');
    expect(svg).toContain('height="100"');
  });

  it('should render circle glyph', () => {
    const svg = GlyphRenderers.circle(element);
    expect(svg).toContain('<circle');
    expect(svg).toContain('r="50"'); // radius = min(100, 100) / 2
  });

  it('should render icon glyph', () => {
    const svg = GlyphRenderers.icon(element);
    expect(svg).toContain('<rect');
    expect(svg).toContain('rx="4"'); // rounded corners
  });

  it('should render label glyph', () => {
    const svg = GlyphRenderers.label(element, 'Test');
    expect(svg).toContain('<text');
    expect(svg).toContain('Test');
  });
});

describe('Progressive Rendering', () => {
  it('should create rendering batches', () => {
    const elements: CanvasElement[] = [];
    for (let i = 0; i < 150; i++) {
      elements.push(createTestElement(`el-${i}`, i * 100, 0));
    }

    const batches = ProgressiveRendering.createBatches(elements, 0.5, 50);

    expect(batches.length).toBe(3); // 150 elements / 50 batch size
    expect(batches[0].length).toBe(50);
    expect(batches[2].length).toBe(50);
  });

  it('should calculate optimal batch size', () => {
    const batchSize = ProgressiveRendering.calculateOptimalBatchSize(1000, 16);

    expect(batchSize).toBeGreaterThan(10);
    expect(batchSize).toBeLessThanOrEqual(100);
  });

  it('should create render schedule', () => {
    const batches = [[], [], []]; // 3 empty batches
    const schedule = ProgressiveRendering.createRenderSchedule(batches, 16);

    expect(schedule).toEqual([0, 16, 32]);
  });
});

describe('LOD Transitions', () => {
  it('should calculate transition opacity', () => {
    const opacity = LODTransitions.calculateTransitionOpacity(
      0.755,
      LODLevel.FULL,
      LODLevel.MEDIUM,
      0.75
    );

    expect(opacity).toBeGreaterThan(0);
    expect(opacity).toBeLessThanOrEqual(1.0);
  });

  it('should detect transition zones', () => {
    const thresholds = [0.75, 0.5, 0.25];

    expect(LODTransitions.isInTransitionZone(0.76, thresholds)).toBe(true);
    expect(LODTransitions.isInTransitionZone(0.9, thresholds)).toBe(false);
  });

  it('should calculate blend factor', () => {
    const blend = LODTransitions.getBlendFactor(0.5, 0.25, 0.75);

    expect(blend).toBe(0.5); // Exactly halfway
  });
});

describe('LOD Performance Monitor', () => {
  it('should track frame metrics', () => {
    const monitor = createLODPerformanceMonitor();
    const lod = createLODSystem();

    const elements = [
      createTestElement('el1', 0, 0),
      createTestElement('el2', 100, 100),
    ];

    const instructions = lod.getLODInstructions(elements, 0.5);
    monitor.recordFrame(10, instructions);

    const metrics = monitor.getMetrics();

    expect(metrics.avgRenderTime).toBe(10);
    expect(metrics.renderedCount).toBe(2);
    expect(metrics.culledCount).toBe(0);
    expect(metrics.fps).toBeGreaterThan(0);
  });

  it('should calculate LOD distribution', () => {
    const monitor = createLODPerformanceMonitor();
    const lod = createLODSystem();

    const elements = [
      createTestElement('el1', 0, 0),
      createTestElement('el2', 100, 100),
      createTestElement('el3', 200, 200),
    ];

    const instructions = lod.getLODInstructions(elements, 0.5);
    monitor.recordFrame(10, instructions);

    const metrics = monitor.getMetrics();

    expect(metrics.lodDistribution[LODLevel.MEDIUM]).toBeGreaterThan(0);
  });

  it('should reset metrics', () => {
    const monitor = createLODPerformanceMonitor();
    monitor.recordFrame(10, []);
    monitor.reset();

    const metrics = monitor.getMetrics();
    expect(metrics.avgRenderTime).toBe(0);
  });
});

describe('Performance Benchmarks', () => {
  it('should handle 1000 elements efficiently', () => {
    const viewport = createVirtualViewport({ useSpatialIndex: true });
    const lod = createLODSystem();

    viewport.updateViewport(createTestViewport(0, 0, 2000, 2000));

    // Create 1000 elements
    const elements: CanvasElement[] = [];
    for (let i = 0; i < 1000; i++) {
      const x = (i % 50) * 100;
      const y = Math.floor(i / 50) * 100;
      elements.push(createTestElement(`el-${i}`, x, y));
    }

    // Measure viewport culling
    const cullStart = performance.now();
    const visible = viewport.getVisibleElements(elements);
    const cullTime = performance.now() - cullStart;

    // Measure LOD processing
    const lodStart = performance.now();
    const instructions = lod.getLODInstructions(visible, 0.5);
    const lodTime = performance.now() - lodStart;

    // Should be fast enough for 60fps (16.67ms budget)
    expect(cullTime + lodTime).toBeLessThan(5); // <5ms total
    expect(visible.length).toBeLessThan(elements.length); // Some culled
    expect(instructions.length).toBe(visible.length);
  });

  it('should maintain performance with 5000 elements', () => {
    const viewport = createVirtualViewport({
      useSpatialIndex: true,
      maxVisibleNodes: 500,
    });

    viewport.updateViewport(createTestViewport(0, 0, 2000, 2000));

    // Create 5000 elements spread across large area
    const elements: CanvasElement[] = [];
    for (let i = 0; i < 5000; i++) {
      const x = (i % 100) * 100;
      const y = Math.floor(i / 100) * 100;
      elements.push(createTestElement(`el-${i}`, x, y));
    }

    const startTime = performance.now();
    const visible = viewport.getVisibleElements(elements);
    const queryTime = performance.now() - startTime;

    // Should still be fast with spatial index
    expect(queryTime).toBeLessThan(10); // <10ms for 5000 elements
    expect(visible.length).toBeLessThanOrEqual(500); // Respects max limit
  });
});

describe('Integration Tests', () => {
  it('should combine viewport culling with LOD', () => {
    const viewport = createVirtualViewport({ margin: 100 });
    const lod = createLODSystem();

    viewport.updateViewport(createTestViewport(0, 0, 1000, 1000, 0.5));

    const elements = [
      createTestElement('visible-full', 500, 500),
      createTestElement('offscreen', 2000, 2000),
      createTestElement('marginal', 1050, 500),
    ];

    // First: cull by viewport
    const visible = viewport.getVisibleElements(elements);
    
    // Then: apply LOD
    const instructions = lod.getLODInstructions(visible, 0.5);

    expect(visible.length).toBe(2); // visible-full and marginal
    expect(instructions.length).toBe(2);
    expect(instructions[0].level).toBe(LODLevel.MEDIUM); // zoom = 0.5
  });

  it('should provide complete rendering pipeline', () => {
    const viewport = createVirtualViewport();
    const lod = createLODSystem();
    const monitor = createLODPerformanceMonitor();

    viewport.updateViewport(createTestViewport(0, 0, 1000, 1000, 0.7));

    const elements: CanvasElement[] = [];
    for (let i = 0; i < 100; i++) {
      elements.push(createTestElement(`el-${i}`, i * 50, i * 50));
    }

    // Simulate render pipeline
    const startTime = performance.now();
    
    const visible = viewport.getVisibleElements(elements);
    const instructions = lod.getLODInstructions(visible, 0.7);
    
    const renderTime = performance.now() - startTime;
    monitor.recordFrame(renderTime, instructions);

    const stats = viewport.getStats();
    const metrics = monitor.getMetrics();

    expect(stats.visibleElements).toBeGreaterThan(0);
    expect(metrics.renderedCount).toBe(stats.visibleElements);
    expect(metrics.avgRenderTime).toBeGreaterThan(0);
  });
});
