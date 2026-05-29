/**
 * GateProvider - interface for executing governance gates and compliance checks.
 *
 * @doc.type interface
 * @doc.purpose Gate provider interface for governance gate execution
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

import { z } from "zod";
import type { KernelProvider } from "./KernelProvider.js";

/**
 * Gate evaluation request.
 */
export interface GateEvaluationRequest {
  readonly gateId: string;
  readonly productUnitId: string;
  readonly phase: string;
  readonly context: Record<string, unknown>;
}

export const GateEvaluationRequestSchema = z
  .object({
    gateId: z.string().trim().min(1),
    productUnitId: z.string().trim().min(1),
    phase: z.string().trim().min(1),
    context: z.record(z.string(), z.unknown()),
  })
  .strict();

/**
 * Gate evaluation result.
 */
export interface GateEvaluationResult {
  readonly gateId: string;
  readonly passed: boolean;
  readonly reason: string;
  readonly evidence: readonly string[];
  readonly evaluatedAt: string;
  readonly duration: number;
}

export const GateEvaluationResultSchema = z
  .object({
    gateId: z.string().trim().min(1),
    passed: z.boolean(),
    reason: z.string().trim().min(1),
    evidence: z.array(z.string().trim().min(1)),
    evaluatedAt: z.string().datetime({ offset: true }),
    duration: z.number().nonnegative(),
  })
  .strict();

/**
 * Gate provider for executing governance gates and compliance checks.
 */
export interface GateProvider extends KernelProvider {
  /**
   * Evaluates a gate.
   */
  evaluateGate(request: GateEvaluationRequest): Promise<GateEvaluationResult>;

  /**
   * Gets gate configuration.
   */
  getGateConfig(gateId: string): Promise<Record<string, unknown> | null>;

  /**
   * Lists available gates.
   */
  listGates(): Promise<readonly string[]>;
}

export const GateProviderSchema = z.custom<GateProvider>(
  (value) => {
    if (typeof value !== "object" || value === null) {
      return false;
    }
    const provider = value as Record<string, unknown>;
    return (
      typeof provider.evaluateGate === "function" &&
      typeof provider.getGateConfig === "function" &&
      typeof provider.listGates === "function"
    );
  },
  "GateProvider requires gate evaluation functions"
);

export function validateGateEvaluationRequest(
  value: unknown
): value is GateEvaluationRequest {
  return GateEvaluationRequestSchema.safeParse(value).success;
}

export function validateGateEvaluationResult(
  value: unknown
): value is GateEvaluationResult {
  return GateEvaluationResultSchema.safeParse(value).success;
}

export function validateGateProvider(value: unknown): value is GateProvider {
  return GateProviderSchema.safeParse(value).success;
}
