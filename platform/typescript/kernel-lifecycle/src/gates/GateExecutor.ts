import type {
  GateProvider,
  KernelLifecycleProviderContext,
} from '@ghatana/kernel-product-contracts';
import type {
  ProductArtifact,
  ProductGatePlan,
  ProductGateResult,
  ProductLifecyclePhase,
} from '../domain/ProductLifecyclePhase.js';

export interface GateExecutorOptions {
  readonly providerContext?: KernelLifecycleProviderContext;
}

export interface GateExecutionRequest {
  readonly productId: string;
  readonly runId: string;
  readonly phase: ProductLifecyclePhase;
  readonly gates: readonly ProductGatePlan[];
  readonly artifacts: readonly ProductArtifact[];
  readonly environment?: string;
  readonly productUnit?: unknown;
  readonly deployment?: unknown;
  readonly providerMode?: string;
}

export interface GateExecutionResult {
  readonly gates: readonly ProductGateResult[];
  readonly failedRequiredGate?: ProductGateResult;
}

export class GateExecutor {
  private readonly providerContext: KernelLifecycleProviderContext | undefined;

  constructor(options: GateExecutorOptions = {}) {
    this.providerContext = options.providerContext;
  }

  async execute(request: GateExecutionRequest): Promise<GateExecutionResult> {
    const gates: ProductGateResult[] = [];

    for (const gate of request.gates) {
      const result = await this.executeGate(gate, request);
      gates.push(result);
      if (gate.required && result.status === 'failed') {
        return {
          gates,
          failedRequiredGate: result,
        };
      }
    }

    return { gates };
  }

  private async executeGate(
    gate: ProductGatePlan,
    request: GateExecutionRequest,
  ): Promise<ProductGateResult> {
    const provider = this.resolveProvider(gate);
    if (provider === undefined) {
      return this.missingProviderResult(gate);
    }

    const startedAt = Date.now();
    try {
      const evaluation = await provider.evaluateGate({
        gateId: gate.gateId,
        productUnitId: request.productId,
        phase: request.phase,
        context: this.buildGateContext(request),
      });

      return {
        gateId: gate.gateId,
        gateName: gate.gateName,
        status: evaluation.passed ? 'passed' : 'failed',
        checkedAt: evaluation.evaluatedAt,
        details: evaluation.reason,
        evidenceRefs: evaluation.evidence,
        durationMs: evaluation.duration,
        providerId: provider.providerId,
      };
    } catch (error) {
      return {
        gateId: gate.gateId,
        gateName: gate.gateName,
        status: gate.required ? 'failed' : 'skipped',
        checkedAt: new Date().toISOString(),
        details: `Gate provider ${provider.providerId} failed: ${stringifyError(error)}`,
        evidenceRefs: [],
        durationMs: Date.now() - startedAt,
        providerId: provider.providerId,
      };
    }
  }

  private resolveProvider(gate: ProductGatePlan): GateProvider | undefined {
    const providers = this.providerContext?.gates;
    if (providers === undefined) {
      return undefined;
    }
    if (gate.providerId !== undefined && providers[gate.providerId] !== undefined) {
      return providers[gate.providerId];
    }
    return providers[gate.gateId];
  }

  private missingProviderResult(gate: ProductGatePlan): ProductGateResult {
    return {
      gateId: gate.gateId,
      gateName: gate.gateName,
      status: gate.required ? 'failed' : 'skipped',
      checkedAt: new Date().toISOString(),
      details: gate.required
        ? `Required gate provider missing: ${gate.providerId ?? gate.gateId}`
        : `Optional gate provider missing: ${gate.providerId ?? gate.gateId}`,
      evidenceRefs: [],
    };
  }

  private buildGateContext(request: GateExecutionRequest): Record<string, unknown> {
    return {
      runId: request.runId,
      phase: request.phase,
      artifacts: request.artifacts,
      ...(request.environment !== undefined ? { environment: request.environment } : {}),
      ...(request.productUnit !== undefined ? { productUnit: request.productUnit } : {}),
      ...(request.deployment !== undefined ? { deployment: request.deployment } : {}),
      ...(request.providerMode !== undefined ? { providerMode: request.providerMode } : {}),
    };
  }
}

function stringifyError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
