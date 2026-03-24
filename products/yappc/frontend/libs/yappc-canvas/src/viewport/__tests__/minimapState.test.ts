/**
 * Minimap State Tests
 * Feature 2.9: Minimap & Viewport Controls
 *
 * Tests for minimap viewport synchronization and zoom controls
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  calculateCanvasBounds,
  worldToMinimapCoordinates,
  minimapToWorldCoordinates,
  calculateMinimapViewport,
  zoomToSelection,
  applyKeyboardZoom,
  interpolateZoom,
  handleMinimapClick,
  isPointInMinimapViewport,
  createMinimapConfig,
  createZoomConfig,
  type MinimapNode,
  type MinimapConfig,
  type ZoomConfig,
} from './minimapState';

import type { Viewport } from './infiniteSpace';

describe('Canvas Bounds Calculation', () => {
  it('should calculate bounds for single node', () => {
    const nodes: MinimapNode[] = [
      { id: '1', x: 100, y: 100, width: 50, height: 50 },
    ];

    const bounds = calculateCanvasBounds(nodes, 10);

    expect(bounds).toEqual({
      minX: 90,
      minY: 90,
      maxX: 160,
      maxY: 160,
      width: 70,
      height: 70,
    });
  });

  it('should calculate bounds for multiple nodes', () => {
    const nodes: MinimapNode[] = [
      { id: '1', x: 0, y: 0, width: 100, height: 100 },
      { id: '2', x: 200, y: 200, width: 100, height: 100 },
    ];

    const bounds = calculateCanvasBounds(nodes, 50);

    expect(bounds).toEqual({
      minX: -50,
      minY: -50,
      maxX: 350,
      maxY: 350,
      width: 400,
      height: 400,
    });
  });

  it('should handle empty node array', () => {
    const bounds = calculateCanvasBounds([], 25);

    expect(bounds).toEqual({
      minX: -25,
      minY: -25,
      maxX: 25,
      maxY: 25,
      width: 50,
      height: 50,
    });
  });

  it('should apply custom padding', () => {
    const nodes: MinimapNode[] = [
      { id: '1', x: 0, y: 0, width: 100, height: 100 },
    ];

    const bounds = calculateCanvasBounds(nodes, 100);

    expect(bounds.minX).toBe(-100);
    expect(bounds.minY).toBe(-100);
    expect(bounds.maxX).toBe(200);
    expect(bounds.maxY).toBe(200);
  });
});

describe('Coordinate Transformations', () => {
  let config: MinimapConfig;
  let canvasBounds: ReturnType<typeof calculateCanvasBounds>;

  beforeEach(() => {
    config = createMinimapConfig({ width: 200, height: 150, padding: 10 });
    canvasBounds = {
      minX: 0,
      minY: 0,
      maxX: 1000,
      maxY: 1000,
      width: 1000,
      height: 1000,
    };
  });

  it('should convert world to minimap coordinates', () => {
    const worldPoint = { x: 500, y: 500 };

    const minimapPoint = worldToMinimapCoordinates(
      worldPoint,
      canvasBounds,
      config
    );

    // Should be roughly centered in minimap (considering aspect ratio)
    expect(minimapPoint.x).toBeGreaterThan(50);
    expect(minimapPoint.x).toBeLessThan(150);
    expect(minimapPoint.y).toBeGreaterThan(25);
    expect(minimapPoint.y).toBeLessThan(125);
  });

  it('should convert minimap to world coordinates', () => {
    const minimapPoint = { x: 100, y: 75 };

    const worldPoint = minimapToWorldCoordinates(
      minimapPoint,
      canvasBounds,
      config
    );

    // Should be roughly centered in canvas
    expect(worldPoint.x).toBeGreaterThan(400);
    expect(worldPoint.x).toBeLessThan(600);
    expect(worldPoint.y).toBeGreaterThan(400);
    expect(worldPoint.y).toBeLessThan(600);
  });

  it('should maintain bidirectional transformation accuracy', () => {
    const originalWorld = { x: 250, y: 750 };

    const minimap = worldToMinimapCoordinates(
      originalWorld,
      canvasBounds,
      config
    );
    const backToWorld = minimapToWorldCoordinates(
      minimap,
      canvasBounds,
      config
    );

    expect(backToWorld.x).toBeCloseTo(originalWorld.x, 0);
    expect(backToWorld.y).toBeCloseTo(originalWorld.y, 0);
  });

  it('should handle edge coordinates', () => {
    const topLeft = { x: 0, y: 0 };
    const bottomRight = { x: 1000, y: 1000 };

    const minimapTL = worldToMinimapCoordinates(topLeft, canvasBounds, config);
    const minimapBR = worldToMinimapCoordinates(
      bottomRight,
      canvasBounds,
      config
    );

    // Top-left should be near minimap padding
    expect(minimapTL.x).toBeGreaterThanOrEqual(config.padding);
    expect(minimapTL.y).toBeGreaterThanOrEqual(config.padding);

    // Bottom-right should be near minimap dimensions minus padding
    expect(minimapBR.x).toBeLessThanOrEqual(config.width - config.padding);
    expect(minimapBR.y).toBeLessThanOrEqual(config.height - config.padding);
  });
});

describe('Minimap Viewport Calculation', () => {
  let config: MinimapConfig;
  let canvasBounds: ReturnType<typeof calculateCanvasBounds>;

  beforeEach(() => {
    config = createMinimapConfig();
    canvasBounds = {
      minX: 0,
      minY: 0,
      maxX: 1000,
      maxY: 1000,
      width: 1000,
      height: 1000,
    };
  });

  it('should calculate minimap viewport for centered view', () => {
    const viewport: Viewport = {
      center: { x: 500, y: 500 },
      zoom: 1,
      width: 800,
      height: 600,
    };

    const minimapViewport = calculateMinimapViewport(
      viewport,
      canvasBounds,
      config
    );

    expect(minimapViewport.zoom).toBe(1);
    expect(minimapViewport.width).toBeGreaterThan(0);
    expect(minimapViewport.height).toBeGreaterThan(0);
  });

  it('should reflect zoom level in viewport size', () => {
    const viewport1: Viewport = {
      center: { x: 500, y: 500 },
      zoom: 1,
      width: 800,
      height: 600,
    };

    const viewport2: Viewport = {
      ...viewport1,
      zoom: 2,
    };

    const minimap1 = calculateMinimapViewport(viewport1, canvasBounds, config);
    const minimap2 = calculateMinimapViewport(viewport2, canvasBounds, config);

    // Higher zoom = smaller viewport indicator
    expect(minimap2.width).toBeLessThan(minimap1.width);
    expect(minimap2.height).toBeLessThan(minimap1.height);
  });

  it('should update position when viewport pans', () => {
    const viewport1: Viewport = {
      center: { x: 300, y: 300 },
      zoom: 1,
      width: 800,
      height: 600,
    };

    const viewport2: Viewport = {
      ...viewport1,
      center: { x: 700, y: 700 },
    };

    const minimap1 = calculateMinimapViewport(viewport1, canvasBounds, config);
    const minimap2 = calculateMinimapViewport(viewport2, canvasBounds, config);

    expect(minimap2.x).toBeGreaterThan(minimap1.x);
    expect(minimap2.y).toBeGreaterThan(minimap1.y);
  });
});

describe('Zoom to Selection', () => {
  let zoomConfig: ZoomConfig;

  beforeEach(() => {
    zoomConfig = createZoomConfig();
  });

  it('should fit single node in viewport', () => {
    const nodes: MinimapNode[] = [
      { id: '1', x: 100, y: 100, width: 200, height: 200 },
    ];

    const viewport = zoomToSelection(
      nodes,
      { width: 800, height: 600 },
      50,
      zoomConfig
    );

    expect(viewport.center.x).toBeCloseTo(200, 0);
    expect(viewport.center.y).toBeCloseTo(200, 0);
    expect(viewport.zoom).toBeGreaterThan(0);
    expect(viewport.zoom).toBeLessThanOrEqual(zoomConfig.max);
  });

  it('should fit multiple nodes in viewport', () => {
    const nodes: MinimapNode[] = [
      { id: '1', x: 0, y: 0, width: 100, height: 100 },
      { id: '2', x: 500, y: 500, width: 100, height: 100 },
    ];

    const viewport = zoomToSelection(
      nodes,
      { width: 800, height: 600 },
      50,
      zoomConfig
    );

    // Should center on middle of selection
    expect(viewport.center.x).toBeCloseTo(300, 0);
    expect(viewport.center.y).toBeCloseTo(300, 0);
  });

  it('should respect zoom limits', () => {
    const tinyNode: MinimapNode[] = [
      { id: '1', x: 0, y: 0, width: 1, height: 1 },
    ];

    const viewport = zoomToSelection(
      tinyNode,
      { width: 800, height: 600 },
      0,
      zoomConfig
    );

    expect(viewport.zoom).toBeLessThanOrEqual(zoomConfig.max);
  });

  it('should handle empty selection', () => {
    const viewport = zoomToSelection(
      [],
      { width: 800, height: 600 },
      50,
      zoomConfig
    );

    expect(viewport.center).toEqual({ x: 0, y: 0 });
    expect(viewport.zoom).toBe(1);
  });

  it('should apply padding correctly', () => {
    const nodes: MinimapNode[] = [
      { id: '1', x: 100, y: 100, width: 300, height: 300 },
    ];

    const viewportNoPadding = zoomToSelection(
      nodes,
      { width: 800, height: 600 },
      0,
      zoomConfig
    );

    const viewportWithPadding = zoomToSelection(
      nodes,
      { width: 800, height: 600 },
      100,
      zoomConfig
    );

    // Padding should result in lower zoom (more zoomed out) or same if already at max
    expect(viewportWithPadding.zoom).toBeLessThanOrEqual(
      viewportNoPadding.zoom
    );
  });
});

describe('Keyboard Zoom Controls', () => {
  let zoomConfig: ZoomConfig;

  beforeEach(() => {
    zoomConfig = createZoomConfig({ keyboardStep: 0.1 });
  });

  it('should zoom in with keyboard', () => {
    const currentZoom = 1.0;
    const newZoom = applyKeyboardZoom(currentZoom, 'in', zoomConfig);

    expect(newZoom).toBe(1.1);
  });

  it('should zoom out with keyboard', () => {
    const currentZoom = 1.0;
    const newZoom = applyKeyboardZoom(currentZoom, 'out', zoomConfig);

    expect(newZoom).toBe(0.9);
  });

  it('should respect minimum zoom', () => {
    const currentZoom = 0.1;
    const newZoom = applyKeyboardZoom(currentZoom, 'out', zoomConfig);

    expect(newZoom).toBe(zoomConfig.min);
  });

  it('should respect maximum zoom', () => {
    const currentZoom = 2.0;
    const newZoom = applyKeyboardZoom(currentZoom, 'in', zoomConfig);

    expect(newZoom).toBe(zoomConfig.max);
  });

  it('should handle custom keyboard step', () => {
    const customConfig = createZoomConfig({ keyboardStep: 0.25 });
    const currentZoom = 1.0;
    const newZoom = applyKeyboardZoom(currentZoom, 'in', customConfig);

    expect(newZoom).toBe(1.25);
  });
});

describe('Zoom Interpolation', () => {
  it('should interpolate at progress 0', () => {
    const zoom = interpolateZoom(1.0, 2.0, 0);
    expect(zoom).toBe(1.0);
  });

  it('should interpolate at progress 1', () => {
    const zoom = interpolateZoom(1.0, 2.0, 1);
    expect(zoom).toBe(2.0);
  });

  it('should interpolate at progress 0.5', () => {
    const zoom = interpolateZoom(1.0, 2.0, 0.5);
    expect(zoom).toBeGreaterThan(1.0);
    expect(zoom).toBeLessThan(2.0);
  });

  it('should use ease-out curve', () => {
    const zoom25 = interpolateZoom(1.0, 2.0, 0.25);
    const zoom50 = interpolateZoom(1.0, 2.0, 0.5);
    const zoom75 = interpolateZoom(1.0, 2.0, 0.75);

    // Ease-out means faster at start, slower at end
    const delta1 = zoom25 - 1.0;
    const delta2 = zoom50 - zoom25;
    const delta3 = zoom75 - zoom50;
    const delta4 = 2.0 - zoom75;

    expect(delta1).toBeGreaterThan(delta4);
    expect(delta2).toBeGreaterThan(delta3);
  });

  it('should clamp progress to valid range', () => {
    const zoomNegative = interpolateZoom(1.0, 2.0, -0.5);
    const zoomOver = interpolateZoom(1.0, 2.0, 1.5);

    expect(zoomNegative).toBe(1.0);
    expect(zoomOver).toBe(2.0);
  });
});

describe('Minimap Interaction', () => {
  let config: MinimapConfig;
  let canvasBounds: ReturnType<typeof calculateCanvasBounds>;

  beforeEach(() => {
    config = createMinimapConfig();
    canvasBounds = {
      minX: 0,
      minY: 0,
      maxX: 1000,
      maxY: 1000,
      width: 1000,
      height: 1000,
    };
  });

  it('should handle minimap click to pan', () => {
    const viewport: Viewport = {
      center: { x: 300, y: 300 },
      zoom: 1,
      width: 800,
      height: 600,
    };

    const clickPoint = { x: 150, y: 100 };
    const newCenter = handleMinimapClick(
      clickPoint,
      canvasBounds,
      config,
      viewport
    );

    expect(newCenter.x).toBeGreaterThan(0);
    expect(newCenter.y).toBeGreaterThan(0);
  });

  it('should detect point inside viewport indicator', () => {
    const minimapViewport = {
      x: 50,
      y: 50,
      width: 100,
      height: 75,
      zoom: 1,
    };

    expect(isPointInMinimapViewport({ x: 75, y: 75 }, minimapViewport)).toBe(
      true
    );
    expect(isPointInMinimapViewport({ x: 25, y: 25 }, minimapViewport)).toBe(
      false
    );
    expect(isPointInMinimapViewport({ x: 175, y: 75 }, minimapViewport)).toBe(
      false
    );
  });

  it('should detect point on viewport edge', () => {
    const minimapViewport = {
      x: 50,
      y: 50,
      width: 100,
      height: 75,
      zoom: 1,
    };

    expect(isPointInMinimapViewport({ x: 50, y: 50 }, minimapViewport)).toBe(
      true
    );
    expect(isPointInMinimapViewport({ x: 150, y: 125 }, minimapViewport)).toBe(
      true
    );
  });
});

describe('Configuration Builders', () => {
  it('should create default minimap config', () => {
    const config = createMinimapConfig();

    expect(config.width).toBe(200);
    expect(config.height).toBe(150);
    expect(config.padding).toBe(10);
    expect(config.backgroundColor).toBeDefined();
  });

  it('should override minimap config', () => {
    const config = createMinimapConfig({
      width: 300,
      backgroundColor: '#000000',
    });

    expect(config.width).toBe(300);
    expect(config.height).toBe(150); // default
    expect(config.backgroundColor).toBe('#000000');
  });

  it('should create default zoom config', () => {
    const config = createZoomConfig();

    expect(config.min).toBe(0.1);
    expect(config.max).toBe(2.0);
    expect(config.step).toBe(0.1);
    expect(config.keyboardStep).toBe(0.1);
  });

  it('should override zoom config', () => {
    const config = createZoomConfig({
      min: 0.5,
      max: 4.0,
    });

    expect(config.min).toBe(0.5);
    expect(config.max).toBe(4.0);
    expect(config.step).toBe(0.1); // default
  });
});
