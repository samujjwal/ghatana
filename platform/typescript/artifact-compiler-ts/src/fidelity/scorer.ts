/**
 * @fileoverview Fidelity scorer for the artifact compiler pipeline.
 *
 * Wraps the `computeFidelityReport` primitive from `@ghatana/artifact-contracts`
 * with higher-level scoring utilities for pipeline runs, node-level aggregation,
 * and threshold checks used in Studio review flows.
 *
 * @doc.type module
 * @doc.purpose Fidelity scoring utilities for the artifact compiler pipeline
 * @doc.layer platform
 * @doc.pattern Utility
 */

import type { FidelityReport, LossPoint } from "@ghatana/artifact-contracts";
import {
  computeFidelityReport,
  createPerfectFidelityReport,
} from "@ghatana/artifact-contracts";

// ============================================================================
// THRESHOLD HELPERS
// ============================================================================

/**
 * Canonical fidelity thresholds used across Studio review flows.
 */
export const FIDELITY_THRESHOLDS = {
  /** Score at or above this is considered "clean" — no review required. */
  CLEAN: 0.95,
  /** Score at or above this is "review-recommended" but not blocked. */
  REVIEW_RECOMMENDED: 0.75,
  /**
   * Score below this is "blocked" — the Studio requires human review before
   * the compiled output can be promoted.
   */
  BLOCKED: 0.5,
} as const;

/**
 * Fidelity gate result for a given report.
 */
export type FidelityGate = "clean" | "review-recommended" | "blocked";

/**
 * Determine the fidelity gate status for a report.
 */
export function fidelityGate(report: FidelityReport): FidelityGate {
  if (report.score >= FIDELITY_THRESHOLDS.CLEAN) return "clean";
  if (report.score >= FIDELITY_THRESHOLDS.REVIEW_RECOMMENDED) return "review-recommended";
  return "blocked";
}

// ============================================================================
// PIPELINE AGGREGATION
// ============================================================================

/**
 * Aggregate multiple node-level fidelity reports into one pipeline-level report.
 *
 * The pipeline score is the arithmetic mean of all node scores.
 * All loss points from all nodes are collected into the aggregate.
 */
export function aggregateFidelityReports(
  reports: ReadonlyMap<string, FidelityReport>,
  pipelineId: string,
): FidelityReport {
  if (reports.size === 0) {
    return computeFidelityReport([], pipelineId, "pipeline");
  }

  const allLossPoints: LossPoint[] = [];
  let totalScore = 0;

  for (const report of reports.values()) {
    totalScore += report.score;
    allLossPoints.push(...report.lossPoints);
  }

  const meanScore = totalScore / reports.size;

  // Re-compute from collected loss points to get accurate canRoundTrip
  const aggregated = computeFidelityReport(allLossPoints, pipelineId, "pipeline");

  // Override score with mean (the primitive computes score from loss points
  // which can differ from the per-node mean)
  return {
    ...aggregated,
    score: Math.max(0, Math.min(1, meanScore)),
    canRoundTrip: meanScore >= 1 && allLossPoints.length === 0,
  };
}

// ============================================================================
// NODE-LEVEL SCORING
// ============================================================================

/**
 * Score an individual artifact node based on how many of its key properties
 * could be inferred.
 *
 * Scoring rules:
 * - If exportedSymbols is empty → −0.1 (props confidence reduced)
 * - If inferredProps is empty (for component/page/layout) → −0.1
 * - If usesDesignSystem is false (but kind is component) → −0.05
 * - If classificationConfidence < 0.8 → −(1 − confidence) * 0.5
 */
export function scoreArtifactNode(
  nodeId: string,
  kind: string,
  exportedSymbols: readonly string[],
  inferredProps: Readonly<Record<string, string>>,
  usesDesignSystem: boolean,
  classificationConfidence: number,
): FidelityReport {
  const lossPoints: LossPoint[] = [];

  if (exportedSymbols.length === 0) {
    lossPoints.push({
      code: "no-exported-symbols",
      description: "No exported symbols detected — node may be an internal module.",
      severity: "info",
      confidenceImpact: 0.1,
    });
  }

  if (
    (kind === "component" || kind === "page" || kind === "layout") &&
    Object.keys(inferredProps).length === 0
  ) {
    lossPoints.push({
      code: "no-inferred-props",
      description: "No props interface found for component/page/layout node.",
      severity: "info",
      confidenceImpact: 0.1,
    });
  }

  if (kind === "component" && !usesDesignSystem) {
    lossPoints.push({
      code: "no-design-system-usage",
      description: "Component does not appear to use the design system — custom styling may not be portable.",
      severity: "info",
      confidenceImpact: 0.05,
    });
  }

  if (classificationConfidence < 0.8) {
    lossPoints.push({
      code: "low-classification-confidence",
      description: `Classification confidence ${(classificationConfidence * 100).toFixed(0)}% is below 80%.`,
      severity: classificationConfidence < 0.5 ? "critical" : "warning",
      confidenceImpact: (1 - classificationConfidence) * 0.5,
    });
  }

  return computeFidelityReport(lossPoints, nodeId, "node");
}

// ============================================================================
// RE-EXPORTS FOR CONVENIENCE
// ============================================================================

export {
  computeFidelityReport,
  createPerfectFidelityReport,
  type FidelityReport,
  type LossPoint,
} from "@ghatana/artifact-contracts";
