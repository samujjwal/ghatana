/**
 * AgentLifecycleActionService - governed entrypoint for agent-proposed lifecycle work.
 *
 * @doc.type service
 * @doc.purpose Validate and execute agent lifecycle action requests through Kernel planner/executor boundaries
 * @doc.layer platform
 * @doc.pattern Service
 */

import {
  AgentLifecycleActionResultSchema,
  type AgentLifecycleActionRequest,
  type AgentLifecycleActionResult,
  type AgentLifecycleActionFailure,
  type AgentLifecycleApprovalDecision,
  type AgentLifecycleDecision,
  type AgentLifecycleHealthStatus,
  type AgentLifecycleRollbackReadiness,
  type AgentLifecycleRequiredNextAction,
  type KernelLifecycleProviderContext,
  type LifecycleProviderWriteOptions,
  parseAgentLifecycleActionRequest,
} from '@ghatana/kernel-product-contracts';
import type {
  ProductLifecyclePlan,
  ProductLifecycleResult,
} from '../domain/ProductLifecyclePhase.js';
import type {
  ProductLifecycleExecutionOptions,
  ProductLifecycleExecutor,
} from '../execution/ProductLifecycleExecutor.js';
import type {
  ProductLifecyclePlanOptions,
  ProductLifecyclePlanner,
} from '../planning/ProductLifecyclePlanner.js';

export interface AgentLifecycleActionPlanner {
  plan(
    productId: string,
    phase: AgentLifecycleActionRequest['lifecyclePhase'],
    options?: ProductLifecyclePlanOptions
  ): Promise<ProductLifecyclePlan>;
}

export interface AgentLifecycleActionExecutor {
  executePlan(
    plan: ProductLifecyclePlan,
    options: ProductLifecycleExecutionOptions
  ): Promise<ProductLifecycleResult>;
}

// Provider-backed governance interfaces
export interface AgentPolicyProvider {
  evaluatePolicy(request: AgentLifecycleActionRequest): Promise<AgentLifecycleDecision>;
}

export interface AgentMasteryProvider {
  evaluateMastery(request: AgentLifecycleActionRequest): Promise<AgentLifecycleDecision>;
}

export interface AgentApprovalProvider {
  resolveApproval(
    request: AgentLifecycleActionRequest,
    plan: ProductLifecyclePlan
  ): Promise<AgentLifecycleApprovalDecision>;
}

export interface AgentVerificationProvider {
  verifyResult(
    request: AgentLifecycleActionRequest,
    result: ProductLifecycleResult
  ): Promise<boolean>;
}

// Legacy function-only checks for test adapters
export interface AgentLifecycleActionChecks {
  readonly policy?: (request: AgentLifecycleActionRequest) => Promise<AgentLifecycleDecision> | AgentLifecycleDecision;
  readonly mastery?: (request: AgentLifecycleActionRequest) => Promise<AgentLifecycleDecision> | AgentLifecycleDecision;
  readonly approval?: (
    request: AgentLifecycleActionRequest,
    plan: ProductLifecyclePlan
  ) => Promise<AgentLifecycleApprovalDecision> | AgentLifecycleApprovalDecision;
  readonly verification?: (
    request: AgentLifecycleActionRequest,
    result: ProductLifecycleResult
  ) => Promise<boolean> | boolean;
}

export type AgentGovernanceMode = 'test' | 'bootstrap' | 'platform';

export interface AgentLifecycleGovernanceProviders {
  readonly policyProvider?: AgentPolicyProvider;
  readonly masteryProvider?: AgentMasteryProvider;
  readonly approvalProvider?: AgentApprovalProvider;
  readonly verificationProvider?: AgentVerificationProvider;
}

export interface AgentLifecycleActionServiceOptions {
  readonly planner: AgentLifecycleActionPlanner | ProductLifecyclePlanner;
  readonly executor: AgentLifecycleActionExecutor | ProductLifecycleExecutor;
  readonly outputDirectory: string;
  readonly providerContext?: KernelLifecycleProviderContext;
  readonly governanceMode?: AgentGovernanceMode;
  readonly governanceProviders?: AgentLifecycleGovernanceProviders;
  readonly checks?: AgentLifecycleActionChecks; // Legacy test adapters only
  readonly now?: () => string;
  readonly allowBootstrapDevDefaults?: boolean;
}

export class AgentLifecycleActionService {
  private readonly planner: AgentLifecycleActionPlanner;
  private readonly executor: AgentLifecycleActionExecutor;
  private readonly outputDirectory: string;
  private readonly providerContext: KernelLifecycleProviderContext | undefined;
  private readonly governanceMode: AgentGovernanceMode;
  private readonly governanceProviders: AgentLifecycleGovernanceProviders;
  private readonly checks: AgentLifecycleActionChecks;
  private readonly now: () => string;
  private readonly allowBootstrapDevDefaults: boolean;

  constructor(options: AgentLifecycleActionServiceOptions) {
    this.planner = options.planner;
    this.executor = options.executor;
    this.outputDirectory = options.outputDirectory;
    this.providerContext = options.providerContext;
    this.governanceMode = options.governanceMode ?? 'bootstrap';
    this.governanceProviders = options.governanceProviders ?? {};
    this.checks = options.checks ?? {};
    this.now = options.now ?? (() => new Date().toISOString());
    this.allowBootstrapDevDefaults = options.allowBootstrapDevDefaults ?? false;
    this.validateGovernanceProviders();
  }

  private validateGovernanceProviders(): void {
    if (this.governanceMode === 'platform') {
      const missingProviders: string[] = [];
      if (!this.governanceProviders.policyProvider) missingProviders.push('policy-provider-missing');
      if (!this.governanceProviders.masteryProvider) missingProviders.push('mastery-provider-missing');
      if (!this.governanceProviders.approvalProvider) missingProviders.push('approval-provider-missing');
      if (!this.governanceProviders.verificationProvider) missingProviders.push('verification-provider-missing');
      if (!this.providerContext?.provenance) missingProviders.push('provenance-provider-missing');
      if (!this.providerContext?.runtimeTruth) missingProviders.push('runtime-truth-required');
      if (!this.providerContext?.memory) missingProviders.push('memory-provider-missing');
      if (missingProviders.length > 0) {
        throw new Error(
          `Platform governance mode requires all governance providers. Missing: ${missingProviders.join(', ')}`,
        );
      }
    }
  }

  async handle(requestInput: unknown): Promise<AgentLifecycleActionResult> {
    const request = parseAgentLifecycleActionRequest(requestInput);
    await this.recordRuntimeTruth(request, 'agent-action-received', request.evidenceRefs);
    const policyDecision = await this.evaluatePolicy(request);
    if (policyDecision === 'denied') {
      await this.recordRuntimeTruth(request, 'policy-denied', request.evidenceRefs);
      return this.result(request, {
        policyDecision,
        masteryDecision: 'denied',
        approvalDecision: 'not-required',
        lifecycleRunRef: request.proposedPlanRef,
        healthStatus: 'unknown',
        rollbackReadiness: 'not-ready',
        failure: {
          reasonCode: 'policy-denied',
          message: 'Agent lifecycle action was denied by policy',
          evidenceRefs: request.evidenceRefs,
        },
        requiredNextAction: 'none',
      });
    }

    const masteryDecision = await this.evaluateMastery(request);
    if (masteryDecision === 'denied') {
      await this.recordRuntimeTruth(request, 'mastery-denied', request.evidenceRefs);
      return this.result(request, {
        policyDecision,
        masteryDecision,
        approvalDecision: 'not-required',
        lifecycleRunRef: request.proposedPlanRef,
        healthStatus: 'unknown',
        rollbackReadiness: 'not-ready',
        failure: {
          reasonCode: 'mastery-denied',
          message: 'Agent lifecycle action was denied by mastery evaluation',
          evidenceRefs: request.evidenceRefs,
        },
        requiredNextAction: 'request-approval',
      });
    }

    if (policyDecision === 'requires-approval' || masteryDecision === 'requires-approval') {
      await this.recordRuntimeTruth(request, 'approval-pending', request.evidenceRefs);
      return this.result(request, {
        policyDecision,
        masteryDecision,
        approvalDecision: 'pending',
        lifecycleRunRef: request.proposedPlanRef,
        healthStatus: 'unknown',
        rollbackReadiness: 'not-ready',
        failure: {
          reasonCode: 'approval-required',
          message: 'Agent lifecycle action requires approval before planning or execution',
          evidenceRefs: request.evidenceRefs,
        },
        requiredNextAction: 'request-approval',
      });
    }

    const lifecyclePhase = this.phaseForRequestedAction(request);
    const plan = await this.planner.plan(request.productUnitId, lifecyclePhase, {
      correlationId: request.correlationId,
      providerMode: this.providerContext?.mode ?? 'bootstrap',
    });
    await this.recordProvenance(request, plan);

    if (request.requestedAction === 'create-lifecycle-plan' || request.requestedAction === 'prepare-rollback') {
      await this.recordRuntimeTruth(request, 'lifecycle-plan-created', [`lifecycle-plan:${plan.runId}`]);
      return this.result(request, {
        policyDecision,
        masteryDecision,
        approvalDecision: 'not-required',
        lifecycleRunRef: `lifecycle-plan:${plan.runId}`,
        healthStatus: 'unknown',
        rollbackReadiness: request.requestedAction === 'prepare-rollback' ? 'ready' : 'not-required',
        evidenceRefs: [...request.evidenceRefs, `lifecycle-plan:${plan.runId}`],
        requiredNextAction: request.requestedAction === 'prepare-rollback' ? 'none' : 'run-verification',
      });
    }

    if (request.requestedAction === 'request-approval') {
      await this.recordRuntimeTruth(request, 'approval-pending', [`lifecycle-plan:${plan.runId}`]);
      return this.result(request, {
        policyDecision,
        masteryDecision,
        approvalDecision: 'pending',
        lifecycleRunRef: `lifecycle-plan:${plan.runId}`,
        healthStatus: 'unknown',
        rollbackReadiness: 'not-ready',
        evidenceRefs: [...request.evidenceRefs, `lifecycle-plan:${plan.runId}`],
        requiredNextAction: 'request-approval',
      });
    }

    const approvalDecision = await this.evaluateApproval(request, plan);
    if (approvalDecision === 'pending' || approvalDecision === 'rejected') {
      await this.recordRuntimeTruth(request, 'approval-pending', [`lifecycle-plan:${plan.runId}`]);
      return this.result(request, {
        policyDecision,
        masteryDecision,
        approvalDecision,
        lifecycleRunRef: `lifecycle-plan:${plan.runId}`,
        healthStatus: 'unknown',
        rollbackReadiness: 'ready',
        requiredNextAction: approvalDecision === 'pending' ? 'request-approval' : 'inspect-failure',
        ...(approvalDecision === 'rejected'
          ? {
              failure: {
                reasonCode: 'approval-rejected',
                message: 'Agent lifecycle action approval was rejected',
                evidenceRefs: request.evidenceRefs,
              },
            }
          : {}),
      });
    }

    const lifecycleResult = await this.executor.executePlan(plan, {
      dryRun: false,
      outputDirectory: this.outputDirectory,
      ...(this.providerContext !== undefined ? { providerContext: this.providerContext } : {}),
    });
    const verificationPassed = await this.evaluateVerification(request, lifecycleResult);
    if (!verificationPassed) {
      await this.recordRuntimeTruth(request, 'verification-failed', this.resultEvidenceRefs(request, lifecycleResult));
    } else {
      await this.recordRuntimeTruth(request, 'lifecycle-executed', this.resultEvidenceRefs(request, lifecycleResult));
    }
    await this.recordMemory(request, lifecycleResult, verificationPassed);
    return this.result(request, {
      policyDecision,
      masteryDecision,
      approvalDecision,
      lifecycleRunRef: `lifecycle-run:${lifecycleResult.runId}`,
      healthStatus: this.healthStatus(lifecycleResult, verificationPassed),
      rollbackReadiness: verificationPassed ? 'ready' : 'not-ready',
      evidenceRefs: this.resultEvidenceRefs(request, lifecycleResult),
      ...(verificationPassed ? { requiredNextAction: 'none' } : { requiredNextAction: 'run-verification' }),
      ...(!verificationPassed
        ? {
            failure: {
              reasonCode: 'verification-failed',
              message: 'Lifecycle execution completed but required verification failed',
              evidenceRefs: this.resultEvidenceRefs(request, lifecycleResult),
            },
          }
        : {}),
    });
  }

  private async evaluatePolicy(request: AgentLifecycleActionRequest): Promise<AgentLifecycleDecision> {
    if (this.governanceProviders.policyProvider) {
      return this.governanceProviders.policyProvider.evaluatePolicy(request);
    }
    if (this.checks.policy) {
      return this.checks.policy(request);
    }
    if (this.allowBootstrapDevDefaults && request.requestedAction === 'create-lifecycle-plan' && request.riskLevel === 'low') {
      return 'allowed';
    }
    if (this.governanceMode === 'platform') {
      throw new Error('Policy provider missing in platform governance mode');
    }
    return 'denied';
  }

  private async evaluateMastery(request: AgentLifecycleActionRequest): Promise<AgentLifecycleDecision> {
    if (this.governanceProviders.masteryProvider) {
      return this.governanceProviders.masteryProvider.evaluateMastery(request);
    }
    if (this.checks.mastery) {
      return this.checks.mastery(request);
    }
    if (request.riskLevel === 'critical') {
      return 'denied';
    }
    return 'requires-approval';
  }

  private async evaluateApproval(
    request: AgentLifecycleActionRequest,
    plan: ProductLifecyclePlan
  ): Promise<AgentLifecycleApprovalDecision> {
    if (this.governanceProviders.approvalProvider) {
      return this.governanceProviders.approvalProvider.resolveApproval(request, plan);
    }
    if (this.checks.approval) {
      return this.checks.approval(request, plan);
    }
    return request.requiredApprovals.some((approval: { required: boolean }) => approval.required) ? 'pending' : 'not-required';
  }

  private phaseForRequestedAction(request: AgentLifecycleActionRequest): AgentLifecycleActionRequest['lifecyclePhase'] {
    if (request.requestedAction === 'verify-lifecycle-health') {
      return 'verify';
    }
    if (request.requestedAction === 'prepare-rollback') {
      return 'rollback';
    }
    return request.lifecyclePhase;
  }

  private async evaluateVerification(
    request: AgentLifecycleActionRequest,
    result: ProductLifecycleResult
  ): Promise<boolean> {
    if (this.governanceProviders.verificationProvider) {
      return this.governanceProviders.verificationProvider.verifyResult(request, result);
    }
    if (this.checks.verification) {
      return this.checks.verification(request, result);
    }
    if (this.governanceMode === 'platform') {
      throw new Error('Verification provider missing in platform governance mode');
    }
    return result.status === 'succeeded';
  }

  private async recordProvenance(
    request: AgentLifecycleActionRequest,
    plan: ProductLifecyclePlan
  ): Promise<void> {
    if (!this.providerContext?.provenance) {
      if (this.providerContext?.mode === 'platform') {
        throw new Error('Kernel platform mode requires provenance provider for agent lifecycle actions');
      }
      return;
    }
    const options: LifecycleProviderWriteOptions = {
      required: this.providerContext.mode === 'platform',
      correlationId: request.correlationId,
    };
    const result = await this.providerContext.provenance.recordProvenance(
      {
        provenanceId: `agent-lifecycle:${request.requestId}`,
        productUnitId: request.productUnitId,
        runId: plan.runId,
        source: request.requestedByAgent,
        evidenceRefs: request.evidenceRefs,
        recordedAt: this.now(),
      },
      options
    );
    if (!result.success && options.required) {
      throw new Error(result.error ?? 'Required provenance write failed for agent lifecycle action');
    }
  }

  private async recordRuntimeTruth(
    request: AgentLifecycleActionRequest,
    status: string,
    evidenceRefs: readonly string[],
  ): Promise<void> {
    if (!this.providerContext?.runtimeTruth) {
      return;
    }
    const result = await this.providerContext.runtimeTruth.recordRuntimeTruth(
      {
        productUnitId: request.productUnitId,
        runId: request.requestId,
        phase: request.lifecyclePhase,
        status,
        observedAt: this.now(),
        evidenceRefs,
      },
      {
        required: this.providerContext.mode === 'platform',
        correlationId: request.correlationId,
      },
    );
    if (!result.success && this.providerContext.mode === 'platform') {
      throw new Error(result.error ?? 'Required runtime truth write failed for agent lifecycle action');
    }
  }

  private async recordMemory(
    request: AgentLifecycleActionRequest,
    result: ProductLifecycleResult,
    verificationPassed: boolean,
  ): Promise<void> {
    if (!this.providerContext?.memory) {
      return;
    }
    const writeResult = await this.providerContext.memory.recordMemory(
      {
        memoryId: `agent-lifecycle:${request.requestId}:${result.runId}`,
        productUnitId: request.productUnitId,
        runId: result.runId,
        kind: verificationPassed ? 'lifecycle-result-summary' : 'lifecycle-failure-diagnosis',
        contentRef: result.manifestRefs?.lifecycleResult ?? `lifecycle-run:${result.runId}`,
        recordedAt: this.now(),
      },
      {
        required: this.providerContext.mode === 'platform',
        correlationId: request.correlationId,
      },
    );
    if (!writeResult.success && this.providerContext.mode === 'platform') {
      throw new Error(writeResult.error ?? 'Required memory write failed for agent lifecycle action');
    }
  }

  private resultEvidenceRefs(
    request: AgentLifecycleActionRequest,
    lifecycleResult: ProductLifecycleResult,
  ): readonly string[] {
    return [
      ...request.evidenceRefs,
      ...(lifecycleResult.manifestRefs?.lifecycleResult !== undefined
        ? [lifecycleResult.manifestRefs.lifecycleResult]
        : []),
    ];
  }

  private healthStatus(
    lifecycleResult: ProductLifecycleResult,
    verificationPassed: boolean
  ): AgentLifecycleHealthStatus {
    if (!verificationPassed) {
      return 'degraded';
    }
    return lifecycleResult.status === 'succeeded' ? 'healthy' : 'unhealthy';
  }

  private result(
    request: AgentLifecycleActionRequest,
    overrides: {
      readonly policyDecision: AgentLifecycleDecision;
      readonly masteryDecision: AgentLifecycleDecision;
      readonly approvalDecision: AgentLifecycleApprovalDecision;
      readonly lifecycleRunRef: string;
      readonly healthStatus: AgentLifecycleHealthStatus;
      readonly rollbackReadiness: AgentLifecycleRollbackReadiness;
      readonly evidenceRefs?: readonly string[];
      readonly failure?: AgentLifecycleActionFailure;
      readonly requiredNextAction?: AgentLifecycleRequiredNextAction;
    }
  ): AgentLifecycleActionResult {
    return AgentLifecycleActionResultSchema.parse({
      schemaVersion: '1.0.0',
      resultId: `agent-result:${request.requestId}`,
      requestId: request.requestId,
      correlationId: request.correlationId,
      productUnitId: request.productUnitId,
      policyDecision: overrides.policyDecision,
      masteryDecision: overrides.masteryDecision,
      approvalDecision: overrides.approvalDecision,
      lifecycleRunRef: overrides.lifecycleRunRef,
      evidenceRefs: overrides.evidenceRefs ?? request.evidenceRefs,
      healthStatus: overrides.healthStatus,
      rollbackReadiness: overrides.rollbackReadiness,
      evaluatedAt: this.now(),
      ...(overrides.failure === undefined ? {} : { failure: overrides.failure }),
      ...(overrides.requiredNextAction === undefined ? {} : { requiredNextAction: overrides.requiredNextAction }),
      request,
    });
  }
}
