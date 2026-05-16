/**
 * KernelLifecycleApiHandlers - framework-light HTTP handlers for Kernel lifecycle APIs.
 *
 * @doc.type module
 * @doc.purpose Exposes product-neutral Kernel lifecycle service operations to Studio and gateways
 * @doc.layer kernel-lifecycle
 * @doc.pattern Adapter
 */

import { z } from 'zod';
import type {
  ApprovalDecision,
  ApprovalRequest,
  ProductUnitScope,
} from '@ghatana/kernel-product-contracts';
import type {
  ProductLifecycleManifestType,
  ProductLifecyclePhase,
  ProductLifecyclePlan,
  ProductLifecycleResult,
} from '../domain/ProductLifecyclePhase.js';
import {
  KernelLifecycleError,
} from '../service/KernelLifecycleErrors.js';
import type {
  KernelLifecycleService,
  LifecycleRunSummary,
} from '../service/KernelLifecycleService.js';

export const PRODUCT_LIFECYCLE_PHASES = [
  'create',
  'bootstrap',
  'dev',
  'validate',
  'test',
  'build',
  'package',
  'release',
  'deploy',
  'verify',
  'promote',
  'rollback',
  'operate',
  'retire',
] as const satisfies readonly ProductLifecyclePhase[];

export const LIFECYCLE_RUN_STATUSES = [
  'healthy',
  'degraded',
  'blocked',
  'failed',
  'skipped',
  'planned',
  'running',
  'pending approval',
  'requires verification',
  'obsolete',
  'quarantined',
  'unknown',
] as const;

export const FAILURE_REASON_CODES = [
  'adapter-failed',
  'gate-failed',
  'artifact-missing',
  'manifest-write-failed',
  'approval-required',
  'policy-denied',
  'provider-unavailable',
  'run-not-found',
  'manifest-not-found',
  'execution-failed',
  'scope-headers-required',
  'authorization-failed',
  'not-ready',
  'lifecycle-not-enabled',
] as const;

const ParamsSchema = z.object({
  productUnitId: z.string().trim().min(1).optional(),
  runId: z.string().trim().min(1).optional(),
  approvalId: z.string().trim().min(1).optional(),
});

const QuerySchema = z.record(z.string(), z.union([z.string(), z.number(), z.boolean()])).default({});

const PlanBodySchema = z.object({
  phase: z.enum(PRODUCT_LIFECYCLE_PHASES),
  surfaceSelector: z.array(z.string().trim().min(1)).optional(),
  environment: z.string().trim().min(1).optional(),
  sourceRef: z.string().trim().min(1).optional(),
  outputDir: z.string().trim().min(1).optional(),
  providerMode: z.enum(['bootstrap', 'platform']).optional(),
  correlationId: z.string().trim().min(1).optional(),
});

const ExecuteBodySchema = PlanBodySchema.extend({
  dryRun: z.boolean().default(false),
});

const ApprovalRequestBodySchema = z.object({
  approvalId: z.string().trim().min(1),
  productUnitId: z.string().trim().min(1),
  runId: z.string().trim().min(1).optional(),
  correlationId: z.string().trim().min(1).optional(),
  requestedBy: z.string().trim().min(1),
  requestedAt: z.string().datetime({ offset: true }).optional(),
  reason: z.string().trim().min(1),
  environment: z.string().trim().min(1).optional(),
  action: z.string().trim().min(1).optional(),
  riskLevel: z.enum(['low', 'medium', 'high', 'critical']).optional(),
  evidenceRefs: z.array(z.string().trim().min(1)).optional(),
  requiredApprovers: z.array(z.string().trim().min(1)).min(1),
  expiresAt: z.string().datetime({ offset: true }),
});

const ApprovalDecisionBodySchema = z.object({
  approvalId: z.string().trim().min(1),
  approved: z.boolean(),
  approvedBy: z.string().trim().min(1),
  reason: z.string().trim().min(1),
  decidedAt: z.string().datetime({ offset: true }),
  evidenceRefs: z.array(z.string().trim().min(1)).optional(),
});

export interface KernelLifecycleApiRequest {
  readonly params?: Record<string, string | undefined>;
  readonly query?: Record<string, string | number | boolean | undefined>;
  readonly body?: unknown;
  readonly headers?: Record<string, string | string[] | undefined>;
}

export interface KernelLifecycleApiResponse {
  readonly statusCode: number;
  readonly headers: Record<string, string>;
  readonly body: unknown;
}

export interface KernelLifecycleActor {
  readonly actorId: string;
  readonly tenantId?: string;
  readonly workspaceId?: string;
  readonly projectId?: string;
  readonly roles: readonly string[];
  readonly capabilities: readonly string[];
}

export interface KernelLifecycleAuthContext {
  readonly productUnitId?: string;
  readonly runId?: string;
  readonly phase?: ProductLifecyclePhase;
  readonly correlationId?: string;
}

export interface KernelLifecycleAuthorizer {
  authenticate(request: KernelLifecycleApiRequest): Promise<KernelLifecycleActor | null>;
  authorizeProductUnitRead(actor: KernelLifecycleActor, context: KernelLifecycleAuthContext): Promise<boolean>;
  authorizeLifecyclePlan(actor: KernelLifecycleActor, context: KernelLifecycleAuthContext): Promise<boolean>;
  authorizeLifecycleExecute(actor: KernelLifecycleActor, context: KernelLifecycleAuthContext): Promise<boolean>;
  authorizeManifestRead(actor: KernelLifecycleActor, context: KernelLifecycleAuthContext): Promise<boolean>;
  authorizeApprovalRequest(actor: KernelLifecycleActor, context: KernelLifecycleAuthContext): Promise<boolean>;
  authorizeApprovalDecision(actor: KernelLifecycleActor, context: KernelLifecycleAuthContext): Promise<boolean>;
}

export interface KernelLifecycleApiHandlersOptions {
  readonly service: KernelLifecycleService;
  readonly requireScopeHeaders?: boolean;
  readonly allowUnscopedLocalDevelopment?: boolean;
  readonly authorizer?: KernelLifecycleAuthorizer;
  readonly requireAuthentication?: boolean;
}

export interface KernelLifecycleRouteMetadata {
  readonly routeId: string;
  readonly method: 'GET' | 'POST';
  readonly path: string;
  readonly handler: keyof KernelLifecycleApiHandlers;
}

export class KernelLifecycleApiHandlers {
  readonly routeMetadata: readonly KernelLifecycleRouteMetadata[] = [
    { routeId: 'kernel.productUnits.list', method: 'GET', path: '/api/kernel/product-units', handler: 'listProductUnits' },
    { routeId: 'kernel.productUnits.get', method: 'GET', path: '/api/kernel/product-units/:productUnitId', handler: 'getProductUnit' },
    { routeId: 'kernel.lifecycle.plan', method: 'POST', path: '/api/kernel/product-units/:productUnitId/lifecycle/plans', handler: 'createLifecyclePlan' },
    { routeId: 'kernel.lifecycle.execute', method: 'POST', path: '/api/kernel/product-units/:productUnitId/lifecycle/execute', handler: 'executeLifecyclePhase' },
    { routeId: 'kernel.lifecycle.runs.list', method: 'GET', path: '/api/kernel/product-units/:productUnitId/lifecycle/runs', handler: 'listLifecycleRuns' },
    { routeId: 'kernel.lifecycle.runs.get', method: 'GET', path: '/api/kernel/product-units/:productUnitId/lifecycle/runs/:runId', handler: 'getLifecycleRun' },
    { routeId: 'kernel.lifecycle.manifest.gates', method: 'GET', path: '/api/kernel/product-units/:productUnitId/lifecycle/runs/:runId/gate-result-manifest', handler: 'getGateResultManifest' },
    { routeId: 'kernel.lifecycle.manifest.artifacts', method: 'GET', path: '/api/kernel/product-units/:productUnitId/lifecycle/runs/:runId/artifact-manifest', handler: 'getArtifactManifest' },
    { routeId: 'kernel.lifecycle.manifest.deployment', method: 'GET', path: '/api/kernel/product-units/:productUnitId/lifecycle/runs/:runId/deployment-manifest', handler: 'getDeploymentManifest' },
    { routeId: 'kernel.lifecycle.manifest.verify', method: 'GET', path: '/api/kernel/product-units/:productUnitId/lifecycle/runs/:runId/verify-health-report', handler: 'getVerifyHealthReport' },
    { routeId: 'kernel.approvals.list', method: 'GET', path: '/api/kernel/approvals', handler: 'listPendingApprovals' },
    { routeId: 'kernel.approvals.request', method: 'POST', path: '/api/kernel/approvals', handler: 'requestApproval' },
    { routeId: 'kernel.approvals.decide', method: 'POST', path: '/api/kernel/approvals/:approvalId/decisions', handler: 'submitApprovalDecision' },
  ];

  private readonly service: KernelLifecycleService;
  private readonly requireScopeHeaders: boolean;
  private readonly allowUnscopedLocalDevelopment: boolean;
  private readonly authorizer: KernelLifecycleAuthorizer | undefined;
  private readonly requireAuthentication: boolean;

  constructor(options: KernelLifecycleApiHandlersOptions) {
    this.service = options.service;
    this.requireScopeHeaders = options.requireScopeHeaders ?? true;
    this.allowUnscopedLocalDevelopment = options.allowUnscopedLocalDevelopment ?? false;
    this.authorizer = options.authorizer;
    this.requireAuthentication = options.requireAuthentication ?? false;
  }

  async listProductUnits(request: KernelLifecycleApiRequest): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      await this.enforceAuth(request, context, 'authorizeProductUnitRead', {});
      const productUnits = await this.service.listProductUnits({
        correlationId: context.correlationId,
        ...(context.scope === undefined ? {} : { scope: context.scope }),
      });
      return this.ok(productUnits, context.correlationId);
    });
  }

  async getProductUnit(request: KernelLifecycleApiRequest): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const productUnitId = requireParam(context.params, 'productUnitId');
      await this.enforceAuth(request, context, 'authorizeProductUnitRead', { productUnitId });
      const productUnit = await this.service.getProductUnit(productUnitId, {
        correlationId: context.correlationId,
        ...(context.scope === undefined ? {} : { scope: context.scope }),
      });
      return this.ok(productUnit, context.correlationId);
    });
  }

  async createLifecyclePlan(request: KernelLifecycleApiRequest): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const productUnitId = requireParam(context.params, 'productUnitId');
      const body = PlanBodySchema.parse(request.body ?? {});
      const correlationId = body.correlationId ?? context.correlationId;
      await this.enforceAuth(request, context, 'authorizeLifecyclePlan', { productUnitId, correlationId });
      const plan = await this.service.createLifecyclePlan(productUnitId, body.phase, {
        correlationId,
        ...(context.scope === undefined ? {} : { scope: context.scope }),
        ...(body.surfaceSelector === undefined ? {} : { surfaceSelector: body.surfaceSelector }),
        ...(body.environment === undefined ? {} : { environment: body.environment }),
        ...(body.sourceRef === undefined ? {} : { sourceRef: body.sourceRef }),
        ...(body.outputDir === undefined ? {} : { outputDir: body.outputDir }),
        ...(body.providerMode === undefined ? {} : { providerMode: body.providerMode }),
      });
      return this.created(toLifecyclePlanResponse(plan), correlationId);
    });
  }

  async executeLifecyclePhase(request: KernelLifecycleApiRequest): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const productUnitId = requireParam(context.params, 'productUnitId');
      const body = ExecuteBodySchema.parse(request.body ?? {});
      const correlationId = body.correlationId ?? context.correlationId;
      await this.enforceAuth(request, context, 'authorizeLifecycleExecute', { productUnitId, correlationId });
      const result = await this.service.runLifecyclePhase(productUnitId, body.phase, {
        dryRun: body.dryRun,
        correlationId,
        ...(context.scope === undefined ? {} : { scope: context.scope }),
        ...(body.surfaceSelector === undefined ? {} : { surfaceSelector: body.surfaceSelector }),
        ...(body.environment === undefined ? {} : { environment: body.environment }),
        ...(body.sourceRef === undefined ? {} : { sourceRef: body.sourceRef }),
        ...(body.outputDir === undefined ? {} : { outputDir: body.outputDir }),
        ...(body.providerMode === undefined ? {} : { providerMode: body.providerMode }),
      });
      return this.ok(toLifecycleRunResponse(result), correlationId);
    });
  }

  async listLifecycleRuns(request: KernelLifecycleApiRequest): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const productUnitId = requireParam(context.params, 'productUnitId');
      await this.enforceAuth(request, context, 'authorizeProductUnitRead', { productUnitId });
      const phase = optionalPhase(context.query.phase);
      const correlationIdQuery = optionalString(context.query.correlationId);
      const runs = await this.service.listLifecycleRuns(productUnitId, {
        ...(phase === undefined ? {} : { phase }),
        ...(correlationIdQuery === undefined ? {} : { correlationId: correlationIdQuery }),
      });
      return this.ok(runs.map(toLifecycleSummaryResponse), context.correlationId);
    });
  }

  async getLifecycleRun(request: KernelLifecycleApiRequest): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const productUnitId = requireParam(context.params, 'productUnitId');
      const runId = requireParam(context.params, 'runId');
      await this.enforceAuth(request, context, 'authorizeProductUnitRead', { productUnitId, runId });
      const run = await this.service.getLifecycleRun(productUnitId, runId);
      return this.ok(toLifecycleSummaryResponse(run), context.correlationId);
    });
  }

  async getGateResultManifest(request: KernelLifecycleApiRequest): Promise<KernelLifecycleApiResponse> {
    return this.getManifest(request, 'gate-result-manifest');
  }

  async getArtifactManifest(request: KernelLifecycleApiRequest): Promise<KernelLifecycleApiResponse> {
    return this.getManifest(request, 'artifact-manifest');
  }

  async getDeploymentManifest(request: KernelLifecycleApiRequest): Promise<KernelLifecycleApiResponse> {
    return this.getManifest(request, 'deployment-manifest');
  }

  async getVerifyHealthReport(request: KernelLifecycleApiRequest): Promise<KernelLifecycleApiResponse> {
    return this.getManifest(request, 'verify-health-report');
  }

  async requestApproval(request: KernelLifecycleApiRequest): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const parsed = ApprovalRequestBodySchema.parse(request.body ?? {});
      await this.enforceAuth(request, context, 'authorizeApprovalRequest', { productUnitId: parsed.productUnitId });
      const approvalRequest: ApprovalRequest = toApprovalRequest(parsed, context.correlationId);
      const result = await this.service.requestApproval(approvalRequest);
      return this.created(result, context.correlationId);
    });
  }

  async listPendingApprovals(request: KernelLifecycleApiRequest): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const productUnitId = optionalString(context.query.productUnitId);
      const runId = optionalString(context.query.runId);
      await this.enforceAuth(request, context, 'authorizeProductUnitRead', {
        ...(productUnitId === undefined ? {} : { productUnitId }),
      });
      const approvals = await this.service.listPendingApprovals({
        ...(context.scope === undefined ? {} : { scope: context.scope }),
        ...(productUnitId === undefined ? {} : { productUnitId }),
        ...(runId === undefined ? {} : { runId }),
        correlationId: context.correlationId,
      });
      return this.ok(approvals, context.correlationId);
    });
  }

  async submitApprovalDecision(request: KernelLifecycleApiRequest): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const approvalId = requireParam(context.params, 'approvalId');
      await this.enforceAuth(request, context, 'authorizeApprovalDecision', {});
      const parsedDecision = ApprovalDecisionBodySchema.parse(request.body ?? {});
      const decision: ApprovalDecision = toApprovalDecision(parsedDecision);
      if (decision.approvalId !== approvalId) {
        throw new KernelLifecycleError({
          reasonCode: 'invalid-approval-decision',
          message: 'approvalId path parameter must match decision.approvalId',
          correlationId: context.correlationId,
        });
      }
      const result = await this.service.submitApprovalDecision(approvalId, decision);
      return this.ok(result, context.correlationId);
    });
  }

  private async getManifest(
    request: KernelLifecycleApiRequest,
    manifestType: ProductLifecycleManifestType,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const productUnitId = requireParam(context.params, 'productUnitId');
      const runId = requireParam(context.params, 'runId');
      await this.enforceAuth(request, context, 'authorizeManifestRead', { productUnitId, runId });
      const phase = optionalPhase(context.query.phase);
      const manifest = await this.service.getManifest(productUnitId, runId, manifestType, phase);
      return this.ok(manifest, context.correlationId);
    });
  }

  private async enforceAuth(
    request: KernelLifecycleApiRequest,
    context: HandlerContext,
    check: keyof Omit<KernelLifecycleAuthorizer, 'authenticate'>,
    authContext: KernelLifecycleAuthContext,
  ): Promise<void> {
    if (this.authorizer === undefined) {
      if (this.requireAuthentication && !this.allowUnscopedLocalDevelopment) {
        throw new KernelLifecycleError({
          reasonCode: 'authentication-required',
          message: 'Kernel lifecycle API requires an authorizer when requireAuthentication is true',
          correlationId: context.correlationId,
        });
      }
      return;
    }

    const actor = await this.authorizer.authenticate(request);
    if (actor === null) {
      throw new KernelLifecycleError({
        reasonCode: 'authentication-required',
        message: 'Authentication required',
        correlationId: context.correlationId,
      });
    }

    const allowed = await this.authorizer[check](actor, {
      ...authContext,
      ...(authContext.correlationId === undefined ? { correlationId: context.correlationId } : {}),
    });
    if (!allowed) {
      throw new KernelLifecycleError({
        reasonCode: 'authorization-failed',
        message: `Authorization denied for operation: ${check}`,
        correlationId: context.correlationId,
      });
    }
  }

  private async handle(
    request: KernelLifecycleApiRequest,
    operation: (context: HandlerContext) => Promise<KernelLifecycleApiResponse>,
  ): Promise<KernelLifecycleApiResponse> {
    try {
      const context = this.contextFromRequest(request);
      return await operation(context);
    } catch (error: unknown) {
      return this.errorResponse(error, correlationIdFromHeaders(request.headers));
    }
  }

  private contextFromRequest(request: KernelLifecycleApiRequest): HandlerContext {
    const headers = normalizeHeaders(request.headers);
    const correlationId = headers['x-correlation-id'] ?? `kernel-api-${Date.now()}`;
    const scope = this.scopeFromHeaders(headers, correlationId);
    return {
      params: ParamsSchema.parse(request.params ?? {}),
      query: QuerySchema.parse(request.query ?? {}) as Record<string, string | number | boolean>,
      correlationId,
      ...(scope === undefined ? {} : { scope }),
    };
  }

  private scopeFromHeaders(
    headers: Record<string, string>,
    correlationId: string,
  ): ProductUnitScope | undefined {
    const tenantId = headers['x-ghatana-tenant-id'];
    const workspaceId = headers['x-ghatana-workspace-id'];
    const projectId = headers['x-ghatana-project-id'];
    if (tenantId !== undefined && workspaceId !== undefined && projectId !== undefined) {
      return { tenantId, workspaceId, projectId };
    }
    if (this.allowUnscopedLocalDevelopment || !this.requireScopeHeaders) {
      return undefined;
    }
    throw new KernelLifecycleError({
      reasonCode: 'scope-headers-required',
      message: 'Kernel lifecycle API requires tenant, workspace, and project headers',
      correlationId,
      safeDetails: {
        missingHeaders: [
          ...(tenantId === undefined ? ['X-Ghatana-Tenant-Id'] : []),
          ...(workspaceId === undefined ? ['X-Ghatana-Workspace-Id'] : []),
          ...(projectId === undefined ? ['X-Ghatana-Project-Id'] : []),
        ],
      },
    });
  }

  private ok(body: unknown, correlationId: string): KernelLifecycleApiResponse {
    return { statusCode: 200, headers: responseHeaders(correlationId), body };
  }

  private created(body: unknown, correlationId: string): KernelLifecycleApiResponse {
    return { statusCode: 201, headers: responseHeaders(correlationId), body };
  }

  private errorResponse(error: unknown, fallbackCorrelationId?: string): KernelLifecycleApiResponse {
    if (error instanceof z.ZodError) {
      const correlationId = fallbackCorrelationId ?? `kernel-api-${Date.now()}`;
      return {
        statusCode: 400,
        headers: responseHeaders(correlationId),
        body: {
          reasonCode: 'invalid-request',
          message: 'Kernel lifecycle API request validation failed',
          correlationId,
          safeDetails: { issues: error.issues.map((issue) => ({ path: issue.path, message: issue.message })) },
        },
      };
    }
    const normalized = error instanceof KernelLifecycleError
      ? error
      : new KernelLifecycleError({
          reasonCode: 'internal-error',
          message: error instanceof Error ? error.message : String(error),
          ...(fallbackCorrelationId === undefined ? {} : { correlationId: fallbackCorrelationId }),
        });
    const statusCode = statusCodeForReason(normalized.reasonCode);
    return {
      statusCode,
      headers: responseHeaders(normalized.correlationId ?? fallbackCorrelationId ?? `kernel-api-${Date.now()}`),
      body: normalized.toSafeResponse(),
    };
  }
}

interface HandlerContext {
  readonly params: z.infer<typeof ParamsSchema>;
  readonly query: Record<string, string | number | boolean>;
  readonly correlationId: string;
  readonly scope?: ProductUnitScope;
}

function requireParam(params: z.infer<typeof ParamsSchema>, name: 'productUnitId' | 'runId' | 'approvalId'): string {
  const value = params[name];
  if (value === undefined) {
    throw new KernelLifecycleError({
      reasonCode: 'invalid-request',
      message: `Missing required path parameter: ${name}`,
    });
  }
  return value;
}

function optionalPhase(value: string | number | boolean | undefined): ProductLifecyclePhase | undefined {
  if (value === undefined) {
    return undefined;
  }
  const parsed = z.enum(PRODUCT_LIFECYCLE_PHASES).safeParse(String(value));
  if (!parsed.success) {
    throw new KernelLifecycleError({
      reasonCode: 'invalid-request',
      message: `Unsupported lifecycle phase: ${String(value)}`,
    });
  }
  return parsed.data;
}

function optionalString(value: string | number | boolean | undefined): string | undefined {
  return value === undefined ? undefined : String(value);
}

function normalizeHeaders(headers: KernelLifecycleApiRequest['headers']): Record<string, string> {
  const normalized: Record<string, string> = {};
  for (const [key, value] of Object.entries(headers ?? {})) {
    if (value === undefined) {
      continue;
    }
    normalized[key.toLowerCase()] = Array.isArray(value) ? value.join(',') : value;
  }
  return normalized;
}

function correlationIdFromHeaders(headers: KernelLifecycleApiRequest['headers']): string | undefined {
  return normalizeHeaders(headers)['x-correlation-id'];
}

function responseHeaders(correlationId: string): Record<string, string> {
  return {
    'content-type': 'application/json',
    'x-correlation-id': correlationId,
  };
}

function toApprovalRequest(
  parsed: z.infer<typeof ApprovalRequestBodySchema>,
  fallbackCorrelationId: string,
): ApprovalRequest {
  return {
    approvalId: parsed.approvalId,
    productUnitId: parsed.productUnitId,
    requestedBy: parsed.requestedBy,
    reason: parsed.reason,
    requiredApprovers: parsed.requiredApprovers,
    expiresAt: parsed.expiresAt,
    correlationId: parsed.correlationId ?? fallbackCorrelationId,
    ...(parsed.runId === undefined ? {} : { runId: parsed.runId }),
    ...(parsed.requestedAt === undefined ? {} : { requestedAt: parsed.requestedAt }),
    ...(parsed.environment === undefined ? {} : { environment: parsed.environment }),
    ...(parsed.action === undefined ? {} : { action: parsed.action }),
    ...(parsed.riskLevel === undefined ? {} : { riskLevel: parsed.riskLevel }),
    ...(parsed.evidenceRefs === undefined ? {} : { evidenceRefs: parsed.evidenceRefs }),
  };
}

function toApprovalDecision(parsed: z.infer<typeof ApprovalDecisionBodySchema>): ApprovalDecision {
  return {
    approvalId: parsed.approvalId,
    approved: parsed.approved,
    approvedBy: parsed.approvedBy,
    reason: parsed.reason,
    decidedAt: parsed.decidedAt,
    ...(parsed.evidenceRefs === undefined ? {} : { evidenceRefs: parsed.evidenceRefs }),
  };
}

function statusCodeForReason(reasonCode: string): number {
  if (reasonCode === 'product-unit-not-found' || reasonCode === 'run-not-found' || reasonCode === 'manifest-not-found') {
    return 404;
  }
  if (reasonCode === 'invalid-request' || reasonCode === 'invalid-approval-decision') {
    return 400;
  }
  if (reasonCode === 'approval-required') {
    return 409;
  }
  if (reasonCode === 'not-ready' || reasonCode === 'lifecycle-not-enabled') {
    return 409;
  }
  if (reasonCode === 'authentication-required') {
    return 401;
  }
  if (reasonCode === 'scope-headers-required' || reasonCode === 'authorization-failed') {
    return 403;
  }
  if (reasonCode === 'provider-unavailable') {
    return 503;
  }
  return 500;
}

function toLifecyclePlanResponse(plan: ProductLifecyclePlan): Record<string, unknown> {
  return {
    ...plan,
    productUnitId: plan.productId,
    status: 'planned',
  };
}

function toLifecycleRunResponse(result: ProductLifecycleResult): Record<string, unknown> {
  return {
    ...result,
    productUnitId: result.productId,
    status: lifecycleRunStatus(result.status),
    ...(result.failure?.reasonCode === undefined ? {} : { failureReasonCode: result.failure.reasonCode }),
  };
}

function toLifecycleSummaryResponse(summary: LifecycleRunSummary): Record<string, unknown> {
  return {
    ...summary,
    status: summary.status === 'planned' ? 'planned' : lifecycleRunStatus(summary.status),
  };
}

function lifecycleRunStatus(
  status: ProductLifecycleResult['status'] | LifecycleRunSummary['status'],
): string {
  if (status === 'succeeded') {
    return 'healthy';
  }
  return status;
}

export function createKernelLifecycleApiHandlers(
  options: KernelLifecycleApiHandlersOptions,
): KernelLifecycleApiHandlers {
  return new KernelLifecycleApiHandlers(options);
}
