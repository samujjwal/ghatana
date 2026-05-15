/**
 * kernelLifecycleClient - typed Studio client for Kernel lifecycle truth.
 *
 * @doc.type module
 * @doc.purpose Validated Studio API boundary for ProductUnit lifecycle operations
 * @doc.layer platform
 * @doc.pattern Client
 */

import { ApiClient } from '@ghatana/api';
import type { ArtifactManifest } from '@ghatana/kernel-artifacts';
import type { DeploymentManifest } from '@ghatana/kernel-deployment';
import {
  ApprovalRequest,
  ApprovalDecision,
  ProductLifecyclePhase,
  ProductUnit,
  ProductUnitSchema,
} from '@ghatana/kernel-product-contracts';
import type { ProductApprovalGate } from '@ghatana/kernel-release';
import { z } from 'zod';

const PRODUCT_LIFECYCLE_PHASES = [
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

const LIFECYCLE_RUN_STATUSES = [
  'healthy',
  'degraded',
  'blocked',
  'failed',
  'skipped',
  'running',
  'pending approval',
  'requires verification',
  'obsolete',
  'quarantined',
  'unknown',
] as const;

const FAILURE_REASON_CODES = [
  'adapter-failed',
  'gate-failed',
  'artifact-missing',
  'manifest-write-failed',
  'approval-required',
  'policy-denied',
  'provider-unavailable',
] as const;

const ProductUnitListSchema = z.array(ProductUnitSchema);

export const LifecyclePlanSchema = z
  .object({
    runId: z.string().trim().min(1),
    correlationId: z.string().trim().min(1),
    productUnitId: z.string().trim().min(1),
    phase: z.enum(PRODUCT_LIFECYCLE_PHASES),
    status: z.enum(LIFECYCLE_RUN_STATUSES),
    createdAt: z.string().datetime({ offset: true }).optional(),
    steps: z.array(z.unknown()).optional(),
  })
  .passthrough();

export const LifecycleRunSchema = z
  .object({
    runId: z.string().trim().min(1),
    correlationId: z.string().trim().min(1),
    productUnitId: z.string().trim().min(1),
    status: z.enum(LIFECYCLE_RUN_STATUSES),
    phase: z.enum(PRODUCT_LIFECYCLE_PHASES).optional(),
    failureReasonCode: z.enum(FAILURE_REASON_CODES).optional(),
    manifestRefs: z.record(z.string(), z.string()).optional(),
    approvalRefs: z.array(z.string().trim().min(1)).optional(),
    eventsRef: z.string().trim().min(1).optional(),
    healthSnapshotRef: z.string().trim().min(1).optional(),
  })
  .passthrough();

const LifecycleRunListSchema = z.array(LifecycleRunSchema);

export const GateResultManifestSchema = z
  .object({
    schemaVersion: z.string().regex(/^\d+\.\d+\.\d+$/),
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    gates: z.array(
      z
        .object({
          gateId: z.string().trim().min(1),
          status: z.string().trim().min(1),
          required: z.boolean().optional(),
        })
        .passthrough(),
    ),
  })
  .passthrough();

const ArtifactManifestSchema = z
  .object({
    schemaVersion: z.string().regex(/^\d+\.\d+\.\d+$/),
    runId: z.string().trim().min(1).optional(),
    correlationId: z.string().trim().min(1).optional(),
    productId: z.string().trim().min(1),
    productUnitId: z.string().trim().min(1).optional(),
    providerMode: z.enum(['bootstrap', 'platform']).optional(),
    phase: z.string().trim().min(1),
    timestamp: z.string().datetime({ offset: true }),
    artifacts: z.array(
      z
        .object({
          id: z.string().trim().min(1),
          path: z.string().trim().min(1),
          metadata: z
            .object({
              type: z.string().trim().min(1),
              packaging: z.string().trim().min(1),
              version: z.string().trim().min(1),
              buildNumber: z.string().trim().min(1),
              timestamp: z.string().datetime({ offset: true }),
              sizeBytes: z.number().int().nonnegative(),
            })
            .passthrough(),
          fingerprint: z.object({
            algorithm: z.enum(['sha256', 'sha512']),
            hash: z.string().trim().min(1),
          }),
          expected: z.boolean(),
          found: z.boolean(),
        })
        .passthrough(),
    ),
  })
  .passthrough();

const DeploymentManifestSchema = z
  .object({
    schemaVersion: z.string().regex(/^\d+\.\d+\.\d+$/),
    runId: z.string().trim().min(1).optional(),
    correlationId: z.string().trim().min(1).optional(),
    productId: z.string().trim().min(1),
    productUnitId: z.string().trim().min(1).optional(),
    version: z.string().trim().min(1),
    environment: z.string().trim().min(1),
    environmentSafety: z.string().trim().min(1).optional(),
    deploymentId: z.string().trim().min(1),
    deployedAt: z.string().datetime({ offset: true }),
    target: z.string().trim().min(1).optional(),
    rollbackPlan: z
      .object({
        strategy: z.string().trim().min(1),
        targetVersion: z.string().trim().min(1),
        reason: z.string().trim().min(1),
        steps: z.array(z.string().trim().min(1)),
      })
      .passthrough(),
    surfaces: z.array(z.unknown()),
    services: z
      .record(
        z.string(),
        z.object({
          status: z.string().trim().min(1),
          healthCheckPassed: z.boolean(),
        }),
      )
      .optional(),
    overallStatus: z.string().trim().min(1).optional(),
    verifierResult: z
      .object({
        valid: z.boolean(),
        checkedAt: z.string().datetime({ offset: true }),
        errors: z.array(z.string()),
      })
      .optional(),
  })
  .passthrough();

export const VerifyHealthReportSchema = z
  .object({
    schemaVersion: z.string().regex(/^\d+\.\d+\.\d+$/),
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    status: z.enum(LIFECYCLE_RUN_STATUSES),
    checkedAt: z.string().datetime({ offset: true }).optional(),
  })
  .passthrough();

export const ApprovalRequestSchema = z
  .object({
    approvalId: z.string().trim().min(1),
    productUnitId: z.string().trim().min(1),
    requestedBy: z.string().trim().min(1),
    reason: z.string().trim().min(1),
    requiredApprovers: z.array(z.string().trim().min(1)).min(1),
    expiresAt: z.string().datetime({ offset: true }),
  })
  .strict();

export const ApprovalDecisionSchema = z
  .object({
    approvalId: z.string().trim().min(1),
    approved: z.boolean(),
    approvedBy: z.string().trim().min(1),
    reason: z.string().trim().min(1),
    decidedAt: z.string().datetime({ offset: true }),
  })
  .strict();

const ProductApprovalGateSchema = z
  .object({
    approvalId: z.string().trim().min(1),
    productId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    correlationId: z.string().trim().min(1),
    gateName: z.string().trim().min(1),
    action: z.string().trim().min(1),
    riskLevel: z.string().trim().min(1),
    requestedBy: z.string().trim().min(1),
    requestedAt: z.string().datetime({ offset: true }),
    evidenceRefs: z.array(z.string().trim().min(1)),
    required: z.boolean(),
    approvers: z.array(z.string().trim().min(1)),
    approvals: z.array(z.unknown()),
    status: z.string().trim().min(1),
    requiredApprovals: z.number().int().nonnegative(),
  })
  .passthrough();

export type LifecyclePlan = z.infer<typeof LifecyclePlanSchema>;
export type LifecycleRun = z.infer<typeof LifecycleRunSchema>;
export type GateResultManifest = z.infer<typeof GateResultManifestSchema>;
export type VerifyHealthReport = z.infer<typeof VerifyHealthReportSchema>;

export interface KernelLifecycleClientOptions {
  readonly baseUrl?: string;
  readonly apiClient?: ApiClient;
  readonly tenantId?: string;
  readonly workspaceId?: string;
  readonly projectId?: string;
  readonly correlationIdFactory?: () => string;
}

export interface LifecyclePlanOptions {
  readonly environment?: string;
  readonly dryRun?: boolean;
  readonly correlationId?: string;
}

export interface LifecycleRunQuery {
  readonly correlationId?: string;
}

export interface DeploymentManifestQuery {
  readonly environment?: string;
}

export interface KernelLifecycleClient {
  listProductUnits(): Promise<readonly ProductUnit[]>;
  getProductUnit(productUnitId: string): Promise<ProductUnit>;
  createLifecyclePlan(
    productUnitId: string,
    phase: ProductLifecyclePhase,
    options?: LifecyclePlanOptions,
  ): Promise<LifecyclePlan>;
  getLifecycleRun(productUnitId: string, runId: string): Promise<LifecycleRun>;
  listLifecycleRuns(productUnitId: string): Promise<readonly LifecycleRun[]>;
  getGateResultManifest(productUnitId: string, runId: string): Promise<GateResultManifest>;
  getArtifactManifest(productUnitId: string, runId: string): Promise<ArtifactManifest>;
  getDeploymentManifest(
    productUnitId: string,
    runId: string,
    query?: DeploymentManifestQuery,
  ): Promise<DeploymentManifest>;
  getVerifyHealthReport(productUnitId: string, runId: string): Promise<VerifyHealthReport>;
  requestApproval(actionRequest: ApprovalRequest): Promise<ProductApprovalGate>;
  submitApprovalDecision(
    approvalId: string,
    decision: ApprovalDecision,
  ): Promise<ProductApprovalGate>;
}

export function createKernelLifecycleClient(
  options: KernelLifecycleClientOptions = {},
): KernelLifecycleClient {
  return new DefaultKernelLifecycleClient(options);
}

class DefaultKernelLifecycleClient implements KernelLifecycleClient {
  private readonly apiClient: ApiClient;
  private readonly tenantId?: string;
  private readonly workspaceId?: string;
  private readonly projectId?: string;
  private readonly correlationIdFactory: () => string;

  constructor(options: KernelLifecycleClientOptions) {
    this.apiClient = options.apiClient ?? new ApiClient({ baseUrl: options.baseUrl });
    this.tenantId = options.tenantId;
    this.workspaceId = options.workspaceId;
    this.projectId = options.projectId;
    this.correlationIdFactory =
      options.correlationIdFactory ?? (() => `studio-${Date.now()}`);
  }

  async listProductUnits(): Promise<readonly ProductUnit[]> {
    const response = await this.apiClient.get<readonly ProductUnit[]>(
      '/api/kernel/product-units',
      {
        headers: this.buildHeaders(),
        schema: ProductUnitListSchema,
      },
    );
    return response.data;
  }

  async getProductUnit(productUnitId: string): Promise<ProductUnit> {
    const response = await this.apiClient.get<ProductUnit>(
      `/api/kernel/product-units/${encodePathSegment(assertIdentifier(productUnitId, 'productUnitId'))}`,
      {
        headers: this.buildHeaders(),
        schema: ProductUnitSchema,
      },
    );
    return response.data;
  }

  async createLifecyclePlan(
    productUnitId: string,
    phase: ProductLifecyclePhase,
    options: LifecyclePlanOptions = {},
  ): Promise<LifecyclePlan> {
    assertPhase(phase);
    const correlationId = options.correlationId ?? this.correlationIdFactory();
    const response = await this.apiClient.post<LifecyclePlan>(
      `/api/kernel/product-units/${encodePathSegment(assertIdentifier(productUnitId, 'productUnitId'))}/lifecycle/plans`,
      {
        headers: this.buildHeaders(correlationId),
        body: {
          phase,
          correlationId,
          ...(options.environment !== undefined ? { environment: options.environment } : {}),
          ...(options.dryRun !== undefined ? { dryRun: options.dryRun } : {}),
        },
        schema: LifecyclePlanSchema,
      },
    );
    return response.data;
  }

  async getLifecycleRun(productUnitId: string, runId: string): Promise<LifecycleRun> {
    const response = await this.apiClient.get<LifecycleRun>(
      `${this.runPath(productUnitId, runId)}`,
      {
        headers: this.buildHeaders(),
        schema: LifecycleRunSchema,
      },
    );
    return response.data;
  }

  async listLifecycleRuns(productUnitId: string): Promise<readonly LifecycleRun[]> {
    const response = await this.apiClient.get<readonly LifecycleRun[]>(
      `/api/kernel/product-units/${encodePathSegment(assertIdentifier(productUnitId, 'productUnitId'))}/lifecycle/runs`,
      {
        headers: this.buildHeaders(),
        schema: LifecycleRunListSchema,
      },
    );
    return response.data;
  }

  async getGateResultManifest(
    productUnitId: string,
    runId: string,
  ): Promise<GateResultManifest> {
    const response = await this.apiClient.get<GateResultManifest>(
      `${this.runPath(productUnitId, runId)}/gate-result-manifest`,
      {
        headers: this.buildHeaders(),
        schema: GateResultManifestSchema,
      },
    );
    return response.data;
  }

  async getArtifactManifest(productUnitId: string, runId: string): Promise<ArtifactManifest> {
    const response = await this.apiClient.get<ArtifactManifest>(
      `${this.runPath(productUnitId, runId)}/artifact-manifest`,
      {
        headers: this.buildHeaders(),
        schema: ArtifactManifestSchema,
      },
    );
    return response.data;
  }

  async getDeploymentManifest(
    productUnitId: string,
    runId: string,
    query: DeploymentManifestQuery = {},
  ): Promise<DeploymentManifest> {
    const response = await this.apiClient.get<DeploymentManifest>(
      `${this.runPath(productUnitId, runId)}/deployment-manifest`,
      {
        headers: this.buildHeaders(),
        query: query.environment !== undefined ? { environment: query.environment } : undefined,
        schema: DeploymentManifestSchema,
      },
    );
    return response.data;
  }

  async getVerifyHealthReport(productUnitId: string, runId: string): Promise<VerifyHealthReport> {
    const response = await this.apiClient.get<VerifyHealthReport>(
      `${this.runPath(productUnitId, runId)}/verify-health-report`,
      {
        headers: this.buildHeaders(),
        schema: VerifyHealthReportSchema,
      },
    );
    return response.data;
  }

  async requestApproval(actionRequest: ApprovalRequest): Promise<ProductApprovalGate> {
    const request = ApprovalRequestSchema.parse(actionRequest);
    const response = await this.apiClient.post<ProductApprovalGate>(
      '/api/kernel/approvals',
      {
        headers: this.buildHeaders(request.approvalId),
        body: request,
        schema: ProductApprovalGateSchema,
      },
    );
    return response.data;
  }

  async submitApprovalDecision(
    approvalId: string,
    decision: ApprovalDecision,
  ): Promise<ProductApprovalGate> {
    const safeApprovalId = assertIdentifier(approvalId, 'approvalId');
    const parsedDecision = ApprovalDecisionSchema.parse(decision);
    if (parsedDecision.approvalId !== safeApprovalId) {
      throw new Error('approvalId must match decision.approvalId');
    }
    const response = await this.apiClient.post<ProductApprovalGate>(
      `/api/kernel/approvals/${encodePathSegment(safeApprovalId)}/decisions`,
      {
        headers: this.buildHeaders(safeApprovalId),
        body: parsedDecision,
        schema: ProductApprovalGateSchema,
      },
    );
    return response.data;
  }

  private runPath(productUnitId: string, runId: string): string {
    return `/api/kernel/product-units/${encodePathSegment(
      assertIdentifier(productUnitId, 'productUnitId'),
    )}/lifecycle/runs/${encodePathSegment(assertIdentifier(runId, 'runId'))}`;
  }

  private buildHeaders(correlationId?: string): Record<string, string> {
    return {
      'X-Correlation-Id': correlationId ?? this.correlationIdFactory(),
      ...(this.tenantId !== undefined ? { 'X-Ghatana-Tenant-Id': this.tenantId } : {}),
      ...(this.workspaceId !== undefined
        ? { 'X-Ghatana-Workspace-Id': this.workspaceId }
        : {}),
      ...(this.projectId !== undefined ? { 'X-Ghatana-Project-Id': this.projectId } : {}),
    };
  }
}

function assertIdentifier(value: string, label: string): string {
  if (value.trim().length === 0) {
    throw new Error(`${label} must be a non-empty string`);
  }
  return value;
}

function assertPhase(phase: ProductLifecyclePhase): void {
  if (!PRODUCT_LIFECYCLE_PHASES.includes(phase)) {
    throw new Error(`Unsupported lifecycle phase: ${phase}`);
  }
}

function encodePathSegment(segment: string): string {
  return encodeURIComponent(segment);
}
