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
  readonly correlationId?: string;
  readonly phase: ProductLifecyclePhase;
  readonly gates: readonly ProductGatePlan[];
  readonly artifacts: readonly ProductArtifact[];
  readonly environment?: string;
  readonly productUnit?: unknown;
  readonly deployment?: unknown;
  readonly providerMode?: string;
  readonly scope?: {
    readonly tenantId?: string;
    readonly workspaceId?: string;
    readonly projectId?: string;
  };
  readonly policyPacks?: readonly string[];
  readonly privacyClassification?: string;
  readonly evidenceRefs?: readonly string[];
}

export interface GateExecutionResult {
  readonly gates: readonly ProductGateResult[];
  readonly failedRequiredGate?: ProductGateResult;
  readonly summary: GateExecutionSummary;
}

export interface GateExecutionSummary {
  readonly passedCount: number;
  readonly failedRequiredCount: number;
  readonly skippedOptionalCount: number;
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
          summary: summarizeGateResults(gates, request.gates),
        };
      }
    }

    return { gates, summary: summarizeGateResults(gates, request.gates) };
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
      const evidenceRefs = evaluation.evidence.filter((ref) => ref.trim().length > 0);
      if (gate.required && evaluation.passed && evidenceRefs.length === 0) {
        return {
          gateId: gate.gateId,
          gateName: gate.gateName,
          status: 'failed',
          checkedAt: evaluation.evaluatedAt,
          details: 'Required gate passed without evidence refs',
          evidenceRefs: [],
          durationMs: evaluation.duration,
          providerId: provider.providerId,
          reasonCode: 'required-gate-missing-evidence',
          ...(request.privacyClassification !== undefined
            ? { privacyClassification: request.privacyClassification }
            : {}),
        };
      }

      return {
        gateId: gate.gateId,
        gateName: gate.gateName,
        status: evaluation.passed ? 'passed' : 'failed',
        checkedAt: evaluation.evaluatedAt,
        details: evaluation.reason,
        evidenceRefs,
        durationMs: evaluation.duration,
        providerId: provider.providerId,
        ...(evaluation.passed ? {} : { reasonCode: 'gate-evaluation-failed' }),
        ...(request.privacyClassification !== undefined
          ? { privacyClassification: request.privacyClassification }
          : {}),
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
        reasonCode: 'gate-provider-failed',
        ...(request.privacyClassification !== undefined
          ? { privacyClassification: request.privacyClassification }
          : {}),
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
      reasonCode: gate.required ? 'required-gate-provider-missing' : 'optional-gate-provider-missing',
    };
  }

  private buildGateContext(request: GateExecutionRequest): Record<string, unknown> {
    return {
      runId: request.runId,
      ...(request.correlationId !== undefined ? { correlationId: request.correlationId } : {}),
      phase: request.phase,
      artifacts: request.artifacts,
      ...(request.scope !== undefined ? { scope: request.scope } : {}),
      ...(request.policyPacks !== undefined ? { policyPacks: request.policyPacks } : {}),
      ...(request.privacyClassification !== undefined
        ? { privacyClassification: request.privacyClassification }
        : {}),
      ...(request.evidenceRefs !== undefined ? { evidenceRefs: request.evidenceRefs } : {}),
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

function summarizeGateResults(
  gates: readonly ProductGateResult[],
  plans: readonly ProductGatePlan[],
): GateExecutionSummary {
  return gates.reduce<GateExecutionSummary>(
    (summary, gate, index) => ({
      passedCount: summary.passedCount + (gate.status === 'passed' ? 1 : 0),
      failedRequiredCount:
        summary.failedRequiredCount +
        (gate.status === 'failed' && plans[index]?.required === true ? 1 : 0),
      skippedOptionalCount: summary.skippedOptionalCount + (gate.status === 'skipped' ? 1 : 0),
    }),
    {
      passedCount: 0,
      failedRequiredCount: 0,
      skippedOptionalCount: 0,
    },
  );
}
