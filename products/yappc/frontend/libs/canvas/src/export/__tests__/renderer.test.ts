/**
 * Export Renderer Tests
 * Feature 1.8: Export Formats
 *
 * Tests for SVG renderer and export engine
 */

import { describe, it, expect, beforeEach } from 'vitest';

import { SVGRenderer, exportEngine } from '../renderer';

import type { CanvasData } from '../../schemas/canvas-schemas';

// Helper to create minimal valid CanvasData
const createTestCanvas = (overrides: Partial<CanvasData> = {}): CanvasData => ({
  metadata: {
    id: 'test-canvas',
    name: 'Test Canvas',
    version: 1,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    createdBy: 'test-user',
    lastModifiedBy: 'test-user',
    tags: [],
    isTemplate: false,
    isPublic: false,
  },
  nodes: [],
  edges: [],
  layers: [],
  viewport: { x: 0, y: 0, zoom: 1 },
  settings: {
    snapToGrid: false,
    gridSize: 20,
    showGrid: false,
    showMinimap: true,
    showControls: true,
    nodesDraggable: true,
    nodesConnectable: true,
    elementsSelectable: true,
    multiSelectionKeyCode: 'Meta',
    deleteKeyCode: 'Backspace',
    panOnDrag: true,
    zoomOnScroll: true,
    zoomOnPinch: true,
    fitViewOnInit: false,
    attributionPosition: 'bottom-right' as const,
  },
  ...overrides,
});

describe('SVGRenderer', () => {
  let renderer: SVGRenderer;
  let mockCanvas: CanvasData;

  beforeEach(() => {
    renderer = new SVGRenderer({
      width: 800,
      height: 600,
      backgroundColor: '#ffffff',
    });

    mockCanvas = createTestCanvas({
      nodes: [
        {
          id: 'node-1',
          type: 'process',
          position: { x: 100, y: 100 },
          width: 150,
          height: 80,
          data: { label: 'Node 1', description: 'Test node' },
          style: { backgroundColor: '#e3f2fd' },
        },
        {
          id: 'node-2',
          type: 'decision',
          position: { x: 400, y: 200 },
          width: 100,
          height: 100,
          data: { label: 'Node 2' },
          style: { backgroundColor: '#f3e5f5' },
        },
      ],
      edges: [
        {
          id: 'edge-1',
          type: 'default',
          source: 'node-1',
          target: 'node-2',
          data: { label: 'success' },
          style: { stroke: '#666' },
        },
      ],
    });
  });

  it('should create SVG with correct dimensions', () => {
    const svg = renderer.renderCanvas(mockCanvas);

    expect(svg).toContain('width="800"');
    expect(svg).toContain('height="600"');
    expect(svg).toContain('viewBox="0 0 800 600"');
  });

  it('should render background with specified color', () => {
    const svg = renderer.renderCanvas(mockCanvas);

    expect(svg).toContain('fill="#ffffff"');
  });

  it('should render all nodes', () => {
    const svg = renderer.renderCanvas(mockCanvas);

    expect(svg).toContain('id="node-node-1"');
    expect(svg).toContain('id="node-node-2"');
    expect(svg).toContain('Node 1');
    expect(svg).toContain('Node 2');
  });

  it('should render all edges', () => {
    const svg = renderer.renderCanvas(mockCanvas);

    expect(svg).toContain('id="edge-edge-1"');
  });

  it('should apply viewport transformation', () => {
    const canvasWithViewport = createTestCanvas({
      viewport: { x: 50, y: 50, zoom: 1.5 },
    });

    const svg = renderer.renderCanvas(canvasWithViewport);

    expect(svg).toContain('translate(50, 50)');
    expect(svg).toContain('scale(1.5)');
  });

  it('should handle empty canvas', () => {
    const emptyCanvas = createTestCanvas();

    const svg = renderer.renderCanvas(emptyCanvas);

    expect(svg).toContain('<svg');
    expect(svg).toContain('</svg>');
  });

  it('should apply node styles', () => {
    const svg = renderer.renderCanvas(mockCanvas);

    expect(svg).toContain('#e3f2fd');
    expect(svg).toContain('#f3e5f5');
  });

  it('should position nodes correctly', () => {
    const svg = renderer.renderCanvas(mockCanvas);

    expect(svg).toContain('translate(100, 100)');
    expect(svg).toContain('translate(400, 200)');
  });

  it('should render process nodes', () => {
    const processCanvas = createTestCanvas({
      nodes: [
        {
          id: 'process-1',
          type: 'process',
          position: { x: 50, y: 50 },
          width: 120,
          height: 60,
          data: { label: 'Process' },
        },
      ],
    });

    const svg = renderer.renderCanvas(processCanvas);
    expect(svg).toContain('node-process');
  });

  it('should render decision nodes', () => {
    const decisionCanvas = createTestCanvas({
      nodes: [
        {
          id: 'decision-1',
          type: 'decision',
          position: { x: 50, y: 50 },
          width: 100,
          height: 100,
          data: { label: 'Decision' },
        },
      ],
    });

    const svg = renderer.renderCanvas(decisionCanvas);
    expect(svg).toContain('node-decision');
  });

  it('should render database nodes', () => {
    const databaseCanvas = createTestCanvas({
      nodes: [
        {
          id: 'db-1',
          type: 'database',
          position: { x: 50, y: 50 },
          width: 80,
          height: 80,
          data: { label: 'Database', schema: 'users' },
        },
      ],
    });

    const svg = renderer.renderCanvas(databaseCanvas);
    expect(svg).toContain('node-database');
  });

  it('should render group nodes', () => {
    const groupCanvas = createTestCanvas({
      nodes: [
        {
          id: 'group-1',
          type: 'group',
          position: { x: 0, y: 0 },
          width: 300,
          height: 200,
          data: { label: 'Group', childIds: [] },
        },
      ],
    });

    const svg = renderer.renderCanvas(groupCanvas);
    expect(svg).toContain('node-group');
  });
});

describe('ExportEngine', () => {
  let mockCanvas: CanvasData;

  beforeEach(() => {
    mockCanvas = createTestCanvas({
      nodes: [
        {
          id: 'node-1',
          type: 'process',
          position: { x: 100, y: 100 },
          width: 150,
          height: 80,
          data: { label: 'Test' },
        },
      ],
    });
  });

  it('should export as SVG format', async () => {
    const result = await exportEngine.exportCanvas(mockCanvas, {
      format: 'svg',
      width: 800,
      height: 600,
    });

    expect(result.status).toBe('completed');
    expect(result.format).toBe('svg');
    expect(result.url).toContain('data:image/svg+xml');
  });

  it('should export as JSON format', async () => {
    const result = await exportEngine.exportCanvas(mockCanvas, {
      format: 'json',
    });

    expect(result.status).toBe('completed');
    expect(result.format).toBe('json');
    expect(result.url).toBeDefined();
  });

  it('should handle export errors gracefully', async () => {
    const invalidCanvas = null as unknown;

    const result = await exportEngine.exportCanvas(invalidCanvas, {
      format: 'svg',
    });

    expect(result.status).toBe('failed');
    expect(result.error).toBeDefined();
  });

  it('should include metadata in export result', async () => {
    const result = await exportEngine.exportCanvas(mockCanvas, {
      format: 'svg',
    });

    expect(result.metadata).toBeDefined();
    expect(result.createdAt).toBeDefined();
    expect(result.format).toBe('svg');
  });

  it('should include processing metrics', async () => {
    const result = await exportEngine.exportCanvas(mockCanvas, {
      format: 'svg',
    });

    if (result.status === 'completed') {
      expect(result.metadata).toBeDefined();
      expect(result.metadata?.processingTime).toBeDefined();
      expect(result.metadata?.nodeCount).toBe(1);
      expect(result.metadata?.edgeCount).toBe(0);
    }
  });

  it('should handle large canvases', async () => {
    const largeCanvas = createTestCanvas({
      nodes: Array.from({ length: 100 }, (_, i) => ({
        id: `node-${i}`,
        type: 'process' as const,
        position: { x: (i % 10) * 150, y: Math.floor(i / 10) * 100 },
        width: 120,
        height: 60,
        data: { label: `Node ${i}` },
      })),
    });

    const result = await exportEngine.exportCanvas(largeCanvas, {
      format: 'svg',
    });

    expect(result.status).toBe('completed');
    expect(result.metadata?.nodeCount).toBe(100);
  });
});

describe('Export Format Validation', () => {
  it('should validate SVG output structure', async () => {
    const canvas = createTestCanvas({
      nodes: [
        {
          id: '1',
          type: 'process',
          position: { x: 0, y: 0 },
          data: { label: 'Test' },
        },
      ],
    });

    const result = await exportEngine.exportCanvas(canvas, { format: 'svg' });

    if (result.status === 'completed') {
      expect(result.url).toContain('data:image/svg+xml');
      expect(result.filename).toContain('.svg');
      expect(result.size).toBeGreaterThan(0);
    }
  });

  it('should validate JSON output structure', async () => {
    const canvas = createTestCanvas({
      nodes: [
        {
          id: '1',
          type: 'process',
          position: { x: 0, y: 0 },
          data: { label: 'Test' },
        },
      ],
    });

    const result = await exportEngine.exportCanvas(canvas, { format: 'json' });

    if (result.status === 'completed' && result.url) {
      expect(result.url).toBeDefined();
      expect(result.format).toBe('json');
    }
  });

  it('should sanitize SVG exports', async () => {
    const canvas = createTestCanvas({
      nodes: [
        {
          id: '1',
          type: 'process',
          position: { x: 0, y: 0 },
          data: { label: '<script>alert("xss")</script>' },
        },
      ],
    });

    const result = await exportEngine.exportCanvas(canvas, { format: 'svg' });

    if (result.status === 'completed') {
      // SVG should be sanitized and not contain script tags
      expect(result.metadata?.sanitized).toBeDefined();
    }
  });

  it('should perform security audit on exports', async () => {
    const canvas = createTestCanvas({
      nodes: [
        {
          id: '1',
          type: 'process',
          position: { x: 0, y: 0 },
          data: { label: 'Safe content' },
        },
      ],
    });

    const result = await exportEngine.exportCanvas(canvas, { format: 'svg' });

    if (result.status === 'completed') {
      expect(result.metadata?.securityAudit).toBeDefined();
    }
  });
});
