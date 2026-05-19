/**
 * LifecycleSummaries — UI-facing lifecycle summary contracts.
 *
 * These types are the canonical shape that Studio and other UI consumers use
 * to display lifecycle state. They are sourced from public lifecycle truth only.
 * Studio must never parse stdout, private logs, or product implementation files.
 *
 * @doc.type module
 * @doc.purpose UI-facing lifecycle summary contracts for Studio consumption
 * @doc.layer kernel-product-contracts
 * @doc.pattern Contract
 */

import { z } from "zod";

// ─── LifecycleGateSummary ─────────────────────────────────────────────────

/**
 * Summary of a single gate evaluation result, suitable for UI rendering.
 */
export const LifecycleGateSummarySchema = z.object({
  /** Gate identifier. */
  gateId: z.string().min(1),
  /** Human-readable display name. */
  displayName: z.string().optional(),
  /** Whether the gate passed. */
  passed: z.boolean(),
  /** Whether the gate is required (blocking if failed). */
  required: z.boolean(),
  /** ISO 8601 timestamp when the gate was evaluated. */
  evaluatedAt: z.string().optional(),
  /** Structured failure reason if the gate did not pass. */
  failureReason: z.string().optional(),
  /** Evidence references collected during evaluation. */
  evidenceRefs: z.array(z.string()).optional(),
});

export type LifecycleGateSummary = z.infer<typeof LifecycleGateSummarySchema>;

// ─── LifecycleArtifactSummary ─────────────────────────────────────────────

/**
 * Summary of a produced lifecycle artifact, suitable for UI rendering.
 */
export const LifecycleArtifactSummarySchema = z.object({
  /** Artifact type (e.g. "jar", "container-image", "static-web-bundle"). */
  artifactType: z.string().min(1),
  /** Surface that produced this artifact. */
  surfaceId: z.string().optional(),
  /** Packaging format (e.g. "jar", "container", "static-files"). */
  packaging: z.string().optional(),
  /** Declared output paths or references. */
  paths: z.array(z.string()).optional(),
  /** Whether the artifact is required in this phase. */
  required: z.boolean(),
  /** Whether the artifact was successfully produced. */
  produced: z.boolean(),
  /** Digest or content hash if available. */
  digest: z.string().optional(),
});

export type LifecycleArtifactSummary = z.infer<
  typeof LifecycleArtifactSummarySchema
>;

// ─── LifecycleDeploymentSummary ───────────────────────────────────────────

/**
 * Summary of a lifecycle deployment operation, suitable for UI rendering.
 */
export const LifecycleDeploymentSummarySchema = z.object({
  /** Target environment identifier. */
  environment: z.string().min(1),
  /** Adapter used for deployment (e.g. "compose-local", "kubernetes"). */
  adapter: z.string().optional(),
  /** Whether deployment succeeded. */
  succeeded: z.boolean(),
  /** ISO 8601 timestamp when deployment was attempted. */
  deployedAt: z.string().optional(),
  /** Services confirmed running after deployment. */
  confirmedServices: z.array(z.string()).optional(),
  /** Human-readable deployment status description. */
  statusDescription: z.string().optional(),
});

export type LifecycleDeploymentSummary = z.infer<
  typeof LifecycleDeploymentSummarySchema
>;

// ─── LifecycleHealthSummary ───────────────────────────────────────────────

/**
 * Summary of a lifecycle health snapshot, suitable for UI rendering.
 */
export const LifecycleHealthSummarySchema = z.object({
  /** Overall health status. */
  status: z.enum(["healthy", "degraded", "unhealthy", "unknown"]),
  /** ISO 8601 timestamp when the health snapshot was taken. */
  checkedAt: z.string().optional(),
  /** Per-surface health check results. */
  checks: z
    .array(
      z.object({
        /** Surface identifier. */
        surfaceId: z.string().min(1),
        /** Whether the surface is healthy. */
        healthy: z.boolean(),
        /** HTTP status or error detail if available. */
        detail: z.string().optional(),
      }),
    )
    .optional(),
});

export type LifecycleHealthSummary = z.infer<
  typeof LifecycleHealthSummarySchema
>;

// ─── LifecycleRunSummary ──────────────────────────────────────────────────

/**
 * Top-level UI-facing summary of a single lifecycle phase run.
 * Studio and other UI consumers render this as the canonical display shape.
 *
 * Rules:
 *  - Sourced from public lifecycle truth endpoints only.
 *  - Must not embed raw stdout, private logs, or product implementation paths.
 *  - All nested summaries are optional; UI must render gracefully if absent.
 */
export const LifecycleRunSummarySchema = z.object({
  /** Unique run identifier. */
  runId: z.string().min(1),
  /** Correlation identifier for distributed tracing. */
  correlationId: z.string().optional(),
  /** Product unit identifier. */
  productUnitId: z.string().min(1),
  /** Lifecycle phase that was executed. */
  phase: z.string().min(1),
  /** Overall run status. */
  status: z.enum([
    "healthy",
    "degraded",
    "blocked",
    "failed",
    "skipped",
    "planned",
    "running",
    "pending approval",
    "requires verification",
    "obsolete",
    "quarantined",
    "unknown",
  ]),
  /** ISO 8601 timestamp when the run started. */
  startedAt: z.string().optional(),
  /** ISO 8601 timestamp when the run completed. */
  completedAt: z.string().optional(),
  /** Gate evaluation summaries for this run. */
  gates: z.array(LifecycleGateSummarySchema).optional(),
  /** Artifact summaries produced in this run. */
  artifacts: z.array(LifecycleArtifactSummarySchema).optional(),
  /** Deployment summary if a deployment occurred. */
  deployment: LifecycleDeploymentSummarySchema.optional(),
  /** Health snapshot summary at the end of the run. */
  health: LifecycleHealthSummarySchema.optional(),
  /** Count of required gates that failed (0 means all required gates passed). */
  failedRequiredGateCount: z.number().int().min(0).optional(),
  /** Human-readable status description. */
  statusDescription: z.string().optional(),
});

export type LifecycleRunSummary = z.infer<typeof LifecycleRunSummarySchema>;

// ─── Parse helpers ────────────────────────────────────────────────────────

/**
 * Parses and validates a LifecycleRunSummary from an unknown payload.
 * Throws if validation fails.
 */
export function parseLifecycleRunSummary(input: unknown): LifecycleRunSummary {
  return LifecycleRunSummarySchema.parse(input);
}
