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

const NO_BOOTSTRAP_GATES: readonly string[] = Object.freeze([]);

export interface FileBootstrapGateProviderOptions {
  readonly providerId?: string;
  readonly version?: string;
  readonly capabilities?: readonly string[];
  readonly supportedGates?: readonly string[];
}

export class FileBootstrapGateProvider implements GateProvider {
  readonly providerId: string;
  readonly version: string;
  readonly backingStore = "file" as const;
  readonly capabilities: readonly string[];
  private readonly supportedGates: Set<string>;
  private readonly explicitSupportedGates: boolean;

  constructor(options: FileBootstrapGateProviderOptions = {}) {
    this.providerId = options.providerId ?? "file-bootstrap-gates";
    this.version = options.version ?? "1.0.0";
    this.capabilities = options.capabilities ?? ["gates", "bootstrap-mode", "file-backed"];
    this.supportedGates = new Set(options.supportedGates);
    this.explicitSupportedGates = options.supportedGates !== undefined;
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

    // Fail closed unless a gate is explicitly listed as supported.
    if (!this.supportedGates.has(gateId)) {
      return {
        gateId,
        passed: false,
        reason: `Gate ${gateId} is NOT_READY in bootstrap mode - no concrete provider implementation found`,
        evidence: [],
        evaluatedAt: new Date().toISOString(),
        duration: Date.now() - startedAt,
      };
    }

    // Synthetic success is allowed only for explicitly configured bootstrap pilot gates.
    return {
      gateId,
      passed: true,
      reason: `Bootstrap gate ${gateId} passed (synthetic - replace with concrete provider)`,
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
      policy: this.explicitSupportedGates ? "explicit-bootstrap-allowlist" : "not-ready",
    };
  }

  async listGates(): Promise<readonly string[]> {
    return Array.from(NO_BOOTSTRAP_GATES);
  }
}
