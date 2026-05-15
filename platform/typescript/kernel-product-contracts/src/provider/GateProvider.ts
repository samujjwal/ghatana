/**
 * GateProvider - interface for executing governance gates and compliance checks.
 *
 * @doc.type interface
 * @doc.purpose Gate provider interface for governance gate execution
 * @doc.layer kernel-product-contracts
 * @doc.pattern Interface
 */

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
