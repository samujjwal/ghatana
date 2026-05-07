/**
 * Canvas and page-builder performance budgets.
 *
 * @doc.type module
 * @doc.purpose Deterministic performance budgets for large canvas and builder documents
 * @doc.layer product
 * @doc.pattern Budget Evaluator
 */

export const CANVAS_PERFORMANCE_BUDGETS = {
  largeCanvasNodeCount: 500,
  viewportCullingNodeThreshold: 50,
  maxVisibleNodesForLargeCanvas: 160,
  maxRenderTimeMs: 75,
  maxInteractionLatencyMs: 50,
  maxEstimatedMemoryMb: 64,
  largeBuilderComponentCount: 250,
  maxBuilderValidationTimeMs: 120,
} as const;

export type PerformanceBudgetArea = 'canvas' | 'page-builder';

export interface PerformanceBudgetViolation {
  readonly area: PerformanceBudgetArea;
  readonly metric: string;
  readonly actual: number;
  readonly budget: number;
  readonly message: string;
}

export interface CanvasPerformanceBudgetInput {
  readonly nodeCount: number;
  readonly visibleNodeCount: number;
  readonly edgeCount: number;
  readonly renderTimeMs: number;
  readonly interactionLatencyMs?: number;
  readonly estimatedMemoryMb?: number;
}

export interface PageBuilderPerformanceBudgetInput {
  readonly componentCount: number;
  readonly validationTimeMs: number;
  readonly estimatedMemoryMb?: number;
}

export interface PerformanceBudgetResult {
  readonly withinBudget: boolean;
  readonly violations: PerformanceBudgetViolation[];
}

export function shouldUseCanvasViewportCulling(nodeCount: number): boolean {
  return nodeCount >= CANVAS_PERFORMANCE_BUDGETS.viewportCullingNodeThreshold;
}

export function estimateCanvasMemoryMb(input: {
  readonly nodeCount: number;
  readonly edgeCount: number;
  readonly builderComponentCount?: number;
}): number {
  const nodeBytes = input.nodeCount * 3200;
  const edgeBytes = input.edgeCount * 1200;
  const builderBytes = (input.builderComponentCount ?? 0) * 2400;
  return Math.round(((nodeBytes + edgeBytes + builderBytes) / 1024 / 1024) * 100) / 100;
}

export function evaluateCanvasPerformanceBudget(
  input: CanvasPerformanceBudgetInput
): PerformanceBudgetResult {
  const violations: PerformanceBudgetViolation[] = [];
  const estimatedMemoryMb =
    input.estimatedMemoryMb ??
    estimateCanvasMemoryMb({
      nodeCount: input.nodeCount,
      edgeCount: input.edgeCount,
    });

  if (
    input.nodeCount >= CANVAS_PERFORMANCE_BUDGETS.largeCanvasNodeCount &&
    input.visibleNodeCount > CANVAS_PERFORMANCE_BUDGETS.maxVisibleNodesForLargeCanvas
  ) {
    violations.push({
      area: 'canvas',
      metric: 'visibleNodeCount',
      actual: input.visibleNodeCount,
      budget: CANVAS_PERFORMANCE_BUDGETS.maxVisibleNodesForLargeCanvas,
      message: `Large canvas renders ${input.visibleNodeCount} visible nodes, above budget ${CANVAS_PERFORMANCE_BUDGETS.maxVisibleNodesForLargeCanvas}.`,
    });
  }

  if (input.renderTimeMs > CANVAS_PERFORMANCE_BUDGETS.maxRenderTimeMs) {
    violations.push({
      area: 'canvas',
      metric: 'renderTimeMs',
      actual: input.renderTimeMs,
      budget: CANVAS_PERFORMANCE_BUDGETS.maxRenderTimeMs,
      message: `Canvas render time ${input.renderTimeMs}ms exceeds budget ${CANVAS_PERFORMANCE_BUDGETS.maxRenderTimeMs}ms.`,
    });
  }

  if (
    input.interactionLatencyMs !== undefined &&
    input.interactionLatencyMs > CANVAS_PERFORMANCE_BUDGETS.maxInteractionLatencyMs
  ) {
    violations.push({
      area: 'canvas',
      metric: 'interactionLatencyMs',
      actual: input.interactionLatencyMs,
      budget: CANVAS_PERFORMANCE_BUDGETS.maxInteractionLatencyMs,
      message: `Canvas interaction latency ${input.interactionLatencyMs}ms exceeds budget ${CANVAS_PERFORMANCE_BUDGETS.maxInteractionLatencyMs}ms.`,
    });
  }

  if (estimatedMemoryMb > CANVAS_PERFORMANCE_BUDGETS.maxEstimatedMemoryMb) {
    violations.push({
      area: 'canvas',
      metric: 'estimatedMemoryMb',
      actual: estimatedMemoryMb,
      budget: CANVAS_PERFORMANCE_BUDGETS.maxEstimatedMemoryMb,
      message: `Canvas estimated memory ${estimatedMemoryMb}MB exceeds budget ${CANVAS_PERFORMANCE_BUDGETS.maxEstimatedMemoryMb}MB.`,
    });
  }

  return {
    withinBudget: violations.length === 0,
    violations,
  };
}

export function evaluatePageBuilderPerformanceBudget(
  input: PageBuilderPerformanceBudgetInput
): PerformanceBudgetResult {
  const violations: PerformanceBudgetViolation[] = [];
  const estimatedMemoryMb =
    input.estimatedMemoryMb ??
    estimateCanvasMemoryMb({
      nodeCount: 0,
      edgeCount: 0,
      builderComponentCount: input.componentCount,
    });

  if (
    input.componentCount >= CANVAS_PERFORMANCE_BUDGETS.largeBuilderComponentCount &&
    input.validationTimeMs > CANVAS_PERFORMANCE_BUDGETS.maxBuilderValidationTimeMs
  ) {
    violations.push({
      area: 'page-builder',
      metric: 'validationTimeMs',
      actual: input.validationTimeMs,
      budget: CANVAS_PERFORMANCE_BUDGETS.maxBuilderValidationTimeMs,
      message: `Large page-builder validation ${input.validationTimeMs}ms exceeds budget ${CANVAS_PERFORMANCE_BUDGETS.maxBuilderValidationTimeMs}ms.`,
    });
  }

  if (estimatedMemoryMb > CANVAS_PERFORMANCE_BUDGETS.maxEstimatedMemoryMb) {
    violations.push({
      area: 'page-builder',
      metric: 'estimatedMemoryMb',
      actual: estimatedMemoryMb,
      budget: CANVAS_PERFORMANCE_BUDGETS.maxEstimatedMemoryMb,
      message: `Page-builder estimated memory ${estimatedMemoryMb}MB exceeds budget ${CANVAS_PERFORMANCE_BUDGETS.maxEstimatedMemoryMb}MB.`,
    });
  }

  return {
    withinBudget: violations.length === 0,
    violations,
  };
}
