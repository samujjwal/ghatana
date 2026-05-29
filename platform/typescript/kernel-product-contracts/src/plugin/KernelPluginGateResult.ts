/**
 * KernelPluginGateResult - represents the result of a plugin gate evaluation.
 *
 * Gate results provide the outcome of plugin-based governance checks,
 * including pass/fail status and supporting evidence.
 *
 * @doc.type interface
 * @doc.purpose Plugin gate evaluation result representation
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

import { z } from "zod";

const GateEvidenceValueSchema = z.union([
  z.string(),
  z.number(),
  z.boolean(),
  z.record(z.string(), z.unknown()),
]);

/**
 * Represents the result of a plugin gate evaluation.
 */
export interface KernelPluginGateResult {
  /**
   * Unique identifier for the gate result.
   */
  readonly id: string;

  /**
   * Plugin identifier that produced this result.
   */
  readonly pluginId: string;

  /**
   * Gate identifier.
   */
  readonly gateId: string;

  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Lifecycle run identifier.
   */
  readonly runId: string;

  /**
   * Whether the gate passed.
   */
  readonly passed: boolean;

  /**
   * Timestamp of the gate evaluation.
   */
  readonly timestamp: string;

  /**
   * Duration of the gate evaluation in milliseconds.
   */
  readonly durationMs: number;

  /**
   * Message describing the result.
   */
  readonly message: string;

  /**
   * Evidence supporting the gate decision.
   */
  readonly evidence: GateEvidence[];

  /**
   * Severity level (if gate failed).
   */
  readonly severity?: "error" | "warning" | "info";

  /**
   * Additional metadata.
   */
  readonly metadata?: Record<string, unknown>;
}

/**
 * Evidence supporting a gate decision.
 */
export interface GateEvidence {
  /**
   * Type of evidence (e.g., "metric", "log", "scan-result").
   */
  readonly type: string;

  /**
   * Evidence identifier or key.
   */
  readonly key: string;

  /**
   * Evidence value or data.
   */
  readonly value: string | number | boolean | Record<string, unknown>;

  /**
   * Description of the evidence.
   */
  readonly description?: string;
}

export const GateEvidenceSchema = z
  .object({
    type: z.string().trim().min(1),
    key: z.string().trim().min(1),
    value: GateEvidenceValueSchema,
    description: z.string().trim().min(1).optional(),
  })
  .strict();

export const KernelPluginGateResultSchema = z
  .object({
    id: z.string().trim().min(1),
    pluginId: z.string().trim().min(1),
    gateId: z.string().trim().min(1),
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    passed: z.boolean(),
    timestamp: z.string().datetime({ offset: true }),
    durationMs: z.number().nonnegative(),
    message: z.string().trim().min(1),
    evidence: z.array(GateEvidenceSchema),
    severity: z.enum(["error", "warning", "info"]).optional(),
    metadata: z.record(z.string(), z.unknown()).optional(),
  })
  .strict();

/**
 * Type guard to check if a value is a KernelPluginGateResult.
 */
export function isKernelPluginGateResult(value: unknown): value is KernelPluginGateResult {
  return KernelPluginGateResultSchema.safeParse(value).success;
}

export function validateGateEvidence(value: unknown): value is GateEvidence {
  return GateEvidenceSchema.safeParse(value).success;
}
