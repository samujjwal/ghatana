import { describe, expect, it } from 'vitest';

import {
  CANVAS_PERFORMANCE_BUDGETS,
  estimateCanvasMemoryMb,
  evaluateCanvasPerformanceBudget,
  evaluatePageBuilderPerformanceBudget,
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
});
