import { describe, expect, it } from 'vitest';

import {
  CANVAS_PERFORMANCE_BUDGETS,
  estimateCanvasMemoryMb,
  evaluateCanvasPerformanceBudget,
  evaluatePageBuilderPerformanceBudget,
  evaluateRoutePerformanceBudget,
  routeLatencyBudgetMs,
  shouldUseCanvasViewportCulling,
} from '../canvasPerformanceBudgets';

describe('canvas performance budgets', () => {
  it('requires viewport culling before the 500-node large canvas budget', () => {
    expect(shouldUseCanvasViewportCulling(49)).toBe(false);
    expect(shouldUseCanvasViewportCulling(50)).toBe(true);
    expect(shouldUseCanvasViewportCulling(CANVAS_PERFORMANCE_BUDGETS.largeCanvasNodeCount)).toBe(true);
  });

  it('keeps a 500-node canvas within budget when visible nodes are culled', () => {
    const result = evaluateCanvasPerformanceBudget({
      nodeCount: 500,
      visibleNodeCount: 120,
      edgeCount: 499,
      renderTimeMs: 42,
      interactionLatencyMs: 28,
    });

    expect(result).toEqual({
      withinBudget: true,
      violations: [],
    });
  });

  it('reports actionable large-canvas budget violations', () => {
    const result = evaluateCanvasPerformanceBudget({
      nodeCount: 500,
      visibleNodeCount: 240,
      edgeCount: 750,
      renderTimeMs: 96,
      interactionLatencyMs: 75,
      estimatedMemoryMb: 80,
    });

    expect(result.withinBudget).toBe(false);
    expect(result.violations.map((violation) => violation.metric)).toEqual([
      'visibleNodeCount',
      'renderTimeMs',
      'interactionLatencyMs',
      'estimatedMemoryMb',
    ]);
  });

  it('estimates memory deterministically for canvas and page-builder documents', () => {
    expect(estimateCanvasMemoryMb({ nodeCount: 500, edgeCount: 499 })).toBeGreaterThan(1);
    expect(estimateCanvasMemoryMb({ nodeCount: 0, edgeCount: 0, builderComponentCount: 250 })).toBeGreaterThan(0);
  });

  it('checks large page-builder document validation and memory budgets', () => {
    expect(
      evaluatePageBuilderPerformanceBudget({
        componentCount: 250,
        validationTimeMs: 90,
      })
    ).toEqual({
      withinBudget: true,
      violations: [],
    });

    const result = evaluatePageBuilderPerformanceBudget({
      componentCount: 300,
      validationTimeMs: 180,
      estimatedMemoryMb: 96,
    });

    expect(result.withinBudget).toBe(false);
    expect(result.violations.map((violation) => violation.metric)).toEqual([
      'validationTimeMs',
      'estimatedMemoryMb',
    ]);
  });

  it('checks key route latency budgets for phase cockpit, admin, and kernel-health routes', () => {
    expect(routeLatencyBudgetMs('phase-cockpit')).toBe(
      CANVAS_PERFORMANCE_BUDGETS.maxPhaseCockpitRouteLatencyMs
    );

    expect(
      evaluateRoutePerformanceBudget({
        routeId: '/p/:projectId/validate',
        kind: 'phase-cockpit',
        latencyMs: 600,
      })
    ).toEqual({
      withinBudget: true,
      violations: [],
    });

    const adminResult = evaluateRoutePerformanceBudget({
      routeId: '/admin/observability',
      kind: 'admin',
      latencyMs: CANVAS_PERFORMANCE_BUDGETS.maxAdminRouteLatencyMs + 1,
    });
    const kernelResult = evaluateRoutePerformanceBudget({
      routeId: '/kernel-health/:productUnitId',
      kind: 'kernel-health',
      latencyMs: CANVAS_PERFORMANCE_BUDGETS.maxKernelHealthRouteLatencyMs + 1,
    });

    expect(adminResult.withinBudget).toBe(false);
    expect(adminResult.violations[0]?.metric).toBe('/admin/observability.latencyMs');
    expect(kernelResult.withinBudget).toBe(false);
    expect(kernelResult.violations[0]?.metric).toBe('/kernel-health/:productUnitId.latencyMs');
  });

  it('keeps shape route canvas with representative project graph inside performance budgets', () => {
    const shapeCanvas = representativeShapeRouteCanvas();
    const visibleNodeCount = shapeCanvas.nodes.filter((node) => node.visible).length;
    const result = evaluateCanvasPerformanceBudget({
      nodeCount: shapeCanvas.nodes.length,
      visibleNodeCount,
      edgeCount: shapeCanvas.edges.length,
      renderTimeMs: 54,
      interactionLatencyMs: 32,
    });

    expect(shapeCanvas.projectId).toBe('project-shape-route-fixture');
    expect(shapeCanvas.nodes.length).toBeGreaterThanOrEqual(500);
    expect(visibleNodeCount).toBeLessThanOrEqual(
      CANVAS_PERFORMANCE_BUDGETS.maxVisibleNodesForLargeCanvas
    );
    expect(result).toEqual({
      withinBudget: true,
      violations: [],
    });
  });
});

interface ShapeRouteCanvasNode {
  readonly id: string;
  readonly kind: 'surface' | 'module' | 'integration';
  readonly visible: boolean;
}

interface ShapeRouteCanvasEdge {
  readonly id: string;
  readonly sourceId: string;
  readonly targetId: string;
}

interface ShapeRouteCanvasFixture {
  readonly projectId: string;
  readonly nodes: ShapeRouteCanvasNode[];
  readonly edges: ShapeRouteCanvasEdge[];
}

function representativeShapeRouteCanvas(): ShapeRouteCanvasFixture {
  const nodes = Array.from({ length: 520 }, (_, index): ShapeRouteCanvasNode => ({
    id: `shape-node-${index}`,
    kind: index % 9 === 0 ? 'surface' : index % 5 === 0 ? 'integration' : 'module',
    visible: index < 144,
  }));
  const edges = Array.from({ length: 780 }, (_, index): ShapeRouteCanvasEdge => ({
    id: `shape-edge-${index}`,
    sourceId: nodes[index % nodes.length]?.id ?? 'shape-node-0',
    targetId: nodes[(index + 7) % nodes.length]?.id ?? 'shape-node-1',
  }));

  return {
    projectId: 'project-shape-route-fixture',
    nodes,
    edges,
  };
}
