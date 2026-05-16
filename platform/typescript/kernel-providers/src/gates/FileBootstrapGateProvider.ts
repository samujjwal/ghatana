/**
 * FileBootstrapGateProvider - bootstrap gate evaluator with explicit evidence refs.
 *
 * @doc.type class
 * @doc.purpose Evaluate lifecycle gates in bootstrap mode with deterministic pass/fail signaling
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import type {
  GateEvaluationRequest,
  GateEvaluationResult,
  GateProvider,
} from "@ghatana/kernel-product-contracts";

export interface FileBootstrapGateProviderOptions {
  readonly providerId?: string;
  readonly version?: string;
  readonly capabilities?: readonly string[];
}

export class FileBootstrapGateProvider implements GateProvider {
  readonly providerId: string;
  readonly version: string;
  readonly backingStore = "file" as const;
  readonly capabilities: readonly string[];

  constructor(options: FileBootstrapGateProviderOptions = {}) {
    this.providerId = options.providerId ?? "file-bootstrap-gates";
    this.version = options.version ?? "1.0.0";
    this.capabilities = options.capabilities ?? ["gates", "bootstrap-mode", "file-backed"];
  }

  async evaluateGate(request: GateEvaluationRequest): Promise<GateEvaluationResult> {
    const startedAt = Date.now();
    const gateId = request.gateId.trim();
    if (gateId.length === 0) {
      return {
        gateId: request.gateId,
        passed: false,
        reason: "Gate evaluation requires a non-empty gateId",
        evidence: [],
        evaluatedAt: new Date().toISOString(),
        duration: Date.now() - startedAt,
      };
    }

    return {
      gateId,
      passed: true,
      reason: `Bootstrap gate ${gateId} passed`,
      evidence: [`bootstrap-gate:${gateId}`],
      evaluatedAt: new Date().toISOString(),
      duration: Date.now() - startedAt,
    };
  }

  async getGateConfig(gateId: string): Promise<Record<string, unknown> | null> {
    const normalizedGateId = gateId.trim();
    if (normalizedGateId.length === 0) {
      return null;
    }

    return {
      gateId: normalizedGateId,
      mode: "bootstrap",
      providerId: this.providerId,
      policy: "pass-with-evidence",
    };
  }

  async listGates(): Promise<readonly string[]> {
    return [];
  }
}
