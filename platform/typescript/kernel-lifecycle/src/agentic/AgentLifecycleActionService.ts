/**
 * AgentLifecycleActionService - governed entrypoint for agent-proposed lifecycle work.
 *
 * @doc.type service
 * @doc.purpose Validate and execute agent lifecycle action requests through Kernel planner/executor boundaries
 * @doc.layer platform
 * @doc.pattern Service
 */

import {
  AgentLifecycleActionRequestSchema,
  AgentLifecycleActionResultSchema,
  type AgentLifecycleActionRequest,
  type AgentLifecycleActionResult,
  type AgentLifecycleApprovalDecision,
  type AgentLifecycleDecision,
  type AgentLifecycleHealthStatus,
  type AgentLifecycleRollbackReadiness,
  type KernelLifecycleProviderContext,
  type LifecycleProviderWriteOptions,
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

export interface AgentLifecycleActionServiceOptions {
  readonly planner: AgentLifecycleActionPlanner | ProductLifecyclePlanner;
  readonly executor: AgentLifecycleActionExecutor | ProductLifecycleExecutor;
  readonly outputDirectory: string;
  readonly providerContext?: KernelLifecycleProviderContext;
  readonly checks?: AgentLifecycleActionChecks;
  readonly now?: () => string;
}

export class AgentLifecycleActionService {
  private readonly planner: AgentLifecycleActionPlanner;
  private readonly executor: AgentLifecycleActionExecutor;
  private readonly outputDirectory: string;
  private readonly providerContext: KernelLifecycleProviderContext | undefined;
  private readonly checks: AgentLifecycleActionChecks;
  private readonly now: () => string;

  constructor(options: AgentLifecycleActionServiceOptions) {
    this.planner = options.planner;
    this.executor = options.executor;
    this.outputDirectory = options.outputDirectory;
    this.providerContext = options.providerContext;
    this.checks = options.checks ?? {};
    this.now = options.now ?? (() => new Date().toISOString());
  }

  async handle(requestInput: unknown): Promise<AgentLifecycleActionResult> {
    const request = AgentLifecycleActionRequestSchema.parse(requestInput);
    const policyDecision = await this.evaluatePolicy(request);
    if (policyDecision === 'denied') {
      return this.result(request, {
        policyDecision,
        masteryDecision: 'denied',
        approvalDecision: 'not-required',
        lifecycleRunRef: request.proposedPlanRef,
        healthStatus: 'unknown',
        rollbackReadiness: 'not-ready',
      });
    }

    const masteryDecision = await this.evaluateMastery(request);
    if (masteryDecision === 'denied') {
      return this.result(request, {
        policyDecision,
        masteryDecision,
        approvalDecision: 'not-required',
        lifecycleRunRef: request.proposedPlanRef,
        healthStatus: 'unknown',
        rollbackReadiness: 'not-ready',
      });
    }

    const plan = await this.planner.plan(request.productUnitId, request.lifecyclePhase, {
      correlationId: request.correlationId,
      providerMode: this.providerContext?.mode ?? 'bootstrap',
    });
    await this.recordProvenance(request, plan);

    const approvalDecision = await this.evaluateApproval(request, plan);
    if (approvalDecision === 'pending' || approvalDecision === 'rejected') {
      return this.result(request, {
        policyDecision,
        masteryDecision,
        approvalDecision,
        lifecycleRunRef: `lifecycle-plan:${plan.runId}`,
        healthStatus: 'unknown',
        rollbackReadiness: 'ready',
      });
    }

    const lifecycleResult = await this.executor.executePlan(plan, {
      dryRun: false,
      outputDirectory: this.outputDirectory,
      ...(this.providerContext !== undefined ? { providerContext: this.providerContext } : {}),
    });
    const verificationPassed = await this.evaluateVerification(request, lifecycleResult);
    return this.result(request, {
      policyDecision,
      masteryDecision,
      approvalDecision,
      lifecycleRunRef: `lifecycle-run:${lifecycleResult.runId}`,
      healthStatus: this.healthStatus(lifecycleResult, verificationPassed),
      rollbackReadiness: verificationPassed ? 'ready' : 'not-ready',
      evidenceRefs: [
        ...request.evidenceRefs,
        ...(lifecycleResult.manifestRefs?.lifecycleResult !== undefined
          ? [lifecycleResult.manifestRefs.lifecycleResult]
          : []),
      ],
    });
  }

  private async evaluatePolicy(request: AgentLifecycleActionRequest): Promise<AgentLifecycleDecision> {
    return this.checks.policy ? this.checks.policy(request) : 'allowed';
  }

  private async evaluateMastery(request: AgentLifecycleActionRequest): Promise<AgentLifecycleDecision> {
    return this.checks.mastery ? this.checks.mastery(request) : 'allowed';
  }

  private async evaluateApproval(
    request: AgentLifecycleActionRequest,
    plan: ProductLifecyclePlan
  ): Promise<AgentLifecycleApprovalDecision> {
    if (this.checks.approval) {
      return this.checks.approval(request, plan);
    }
    return request.requiredApprovals.some((approval) => approval.required) ? 'pending' : 'not-required';
  }

  private async evaluateVerification(
    request: AgentLifecycleActionRequest,
    result: ProductLifecycleResult
  ): Promise<boolean> {
    if (this.checks.verification) {
      return this.checks.verification(request, result);
    }
    return result.status === 'succeeded';
  }

  private async recordProvenance(
    request: AgentLifecycleActionRequest,
    plan: ProductLifecyclePlan
  ): Promise<void> {
    if (!this.providerContext?.provenance) {
      return;
    }
    const options: LifecycleProviderWriteOptions = {
      required: false,
      correlationId: request.correlationId,
    };
    await this.providerContext.provenance.recordProvenance(
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
      request,
    });
  }
}
