/**
 * kernelLifecycleClient - typed Studio client for Kernel lifecycle truth.
 *
 * @doc.type module
 * @doc.purpose Validated Studio API boundary for ProductUnit lifecycle operations
 * @doc.layer platform
 * @doc.pattern Client
 */

import { ApiClient } from '@ghatana/api';
import type { ApiError } from '@ghatana/api';
import type { ArtifactManifest } from '@ghatana/kernel-artifacts';
import type { DeploymentManifest } from '@ghatana/kernel-deployment';
import {
  ProductUnitIntent,
  ProductUnitIntentApplicationResult,
  ProductUnitIntentApplicationResultSchema,
  ProductUnitIntentSchema,
  ProductUnit,
  ProductUnitSchema,
  parseLifecycleRunSummary,
  type LifecycleRunSummary,
  type LifecycleGateSummary,
  type LifecycleArtifactSummary,
  type LifecycleDeploymentSummary,
  type LifecycleHealthSummary,
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
] as const;

const LIFECYCLE_RUN_STATUSES = [
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

const FAILURE_REASON_CODES = [
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

const ProductUnitListSchema = z.array(ProductUnitSchema);

export const ProductInteractionPreflightSchema = z
  .object({
    contractId: z.string().trim().min(1),
    providerProductId: z.string().trim().min(1),
    consumerProductId: z.string().trim().min(1),
    mode: z.enum([
      'request-response',
      'event-publish',
      'event-subscribe',
      'shared-evidence',
      'provider-capability',
    ]),
    required: z.boolean(),
    status: z.enum(['pending', 'blocked', 'skipped']),
    reasonCode: z.string().trim().min(1).optional(),
    evidenceRequired: z.boolean(),
    evidenceRefs: z.array(z.string().trim().min(1)).optional(),
  })
  .passthrough();

export const LifecyclePlanSchema = z
  .object({
    runId: z.string().trim().min(1),
    correlationId: z.string().trim().min(1),
    productUnitId: z.string().trim().min(1),
    phase: z.enum(PRODUCT_LIFECYCLE_PHASES),
    status: z.enum(LIFECYCLE_RUN_STATUSES),
    createdAt: z.string().datetime({ offset: true }).optional(),
    steps: z.array(z.unknown()).optional(),
    interactionPreflights: z.array(ProductInteractionPreflightSchema).optional(),
    blockingReasons: z.array(z.string().trim().min(1)).optional(),
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

export const KernelLifecycleApiErrorSchema = z
  .object({
    reasonCode: z.string().trim().min(1),
    message: z.string().trim().min(1),
    correlationId: z.string().trim().min(1).optional(),
    statusCode: z.number().int().positive().optional(),
    safeDetails: z.record(z.string(), z.unknown()).optional(),
    details: z.record(z.string(), z.unknown()).optional(),
  })
  .passthrough();

export type KernelLifecycleApiError = z.infer<typeof KernelLifecycleApiErrorSchema>;

export type StudioCapabilityLifecycleStatus = 'unconfigured' | 'loading' | 'ready' | 'degraded';
export type StudioCapabilityProviderMode = 'bootstrap' | 'platform';
export type StudioManifestLoadStatus =
  | 'loaded'
  | 'missing'
  | 'corrupt'
  | 'unauthorized'
  | 'unavailable';

export interface StudioCapabilityManifestLoadState {
  readonly gateResultManifest: { readonly status: StudioManifestLoadStatus };
  readonly artifactManifest: { readonly status: StudioManifestLoadStatus };
  readonly deploymentManifest: { readonly status: StudioManifestLoadStatus };
  readonly verifyHealthReport: { readonly status: StudioManifestLoadStatus };
}

export interface StudioCapabilityState {
  readonly runtimeConfigured: boolean;
  readonly lifecycleConfigured: boolean;
  readonly lifecycleExecutionAllowed: boolean;
  readonly dataCloudEvidenceReady: boolean;
}

export interface StudioCapabilityStateInput {
  readonly runtimeConfigured: boolean;
  readonly lifecycleStatus: StudioCapabilityLifecycleStatus;
  readonly selectedProviderMode: StudioCapabilityProviderMode;
  readonly productUnit?: {
    readonly lifecycleExecutionAllowed?: boolean;
    readonly metadata?: {
      readonly lifecycleExecutionAllowed?: boolean;
    };
  };
  readonly selectedRun?: {
    readonly manifestRefs?: Record<string, string>;
    readonly approvalRefs?: readonly string[];
    readonly eventsRef?: string;
    readonly healthSnapshotRef?: string;
  };
  readonly manifestLoadState?: StudioCapabilityManifestLoadState;
}

function hasLoadedManifestEvidence(manifestLoadState: StudioCapabilityManifestLoadState | undefined): boolean {
  if (manifestLoadState === undefined) {
    return false;
  }

  return (
    manifestLoadState.gateResultManifest.status === 'loaded'
    || manifestLoadState.artifactManifest.status === 'loaded'
    || manifestLoadState.deploymentManifest.status === 'loaded'
    || manifestLoadState.verifyHealthReport.status === 'loaded'
  );
}

function hasRunEvidence(selectedRun: StudioCapabilityStateInput['selectedRun']): boolean {
  if (selectedRun === undefined) {
    return false;
  }

  return (
    selectedRun.eventsRef !== undefined
    || selectedRun.healthSnapshotRef !== undefined
    || Object.keys(selectedRun.manifestRefs ?? {}).length > 0
    || (selectedRun.approvalRefs?.length ?? 0) > 0
  );
}

export function getStudioCapabilityState(input: StudioCapabilityStateInput): StudioCapabilityState {
  const lifecycleConfigured = input.lifecycleStatus !== 'unconfigured' && input.productUnit !== undefined;
  const lifecycleExecutionAllowed =
    input.productUnit?.lifecycleExecutionAllowed
    ?? input.productUnit?.metadata?.lifecycleExecutionAllowed
    ?? false;
  const providerEvidenceReady =
    input.selectedProviderMode === 'bootstrap'
      ? true
      : hasLoadedManifestEvidence(input.manifestLoadState) || hasRunEvidence(input.selectedRun);

  return {
    runtimeConfigured: input.runtimeConfigured,
    lifecycleConfigured,
    lifecycleExecutionAllowed,
    dataCloudEvidenceReady:
      input.runtimeConfigured
      && lifecycleConfigured
      && lifecycleExecutionAllowed
      && providerEvidenceReady,
  };
}

/**
 * Authentication context required for lifecycle mutations.
 */
export interface KernelLifecycleClientAuthContext {
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
  readonly authToken: string;
}

/**
 * Base class for typed Kernel lifecycle client errors.
 */
export class KernelLifecycleClientError extends Error {
  constructor(
    message: string,
    public readonly code: string,
    public readonly statusCode: number,
    public readonly correlationId?: string,
    public readonly details?: Record<string, unknown>,
  ) {
    super(message);
    this.name = 'KernelLifecycleClientError';
  }
}

/**
 * Authentication error: missing or invalid auth token (401).
 */
export class KernelLifecycleAuthError extends KernelLifecycleClientError {
  constructor(
    message: string,
    correlationId?: string,
    details?: Record<string, unknown>,
  ) {
    super(message, 'AUTHENTICATION_REQUIRED', 401, correlationId, details);
    this.name = 'KernelLifecycleAuthError';
  }
}

/**
 * Authorization error: insufficient permissions or scope mismatch (403).
 */
export class KernelLifecycleScopeError extends KernelLifecycleClientError {
  constructor(
    message: string,
    correlationId?: string,
    details?: Record<string, unknown>,
  ) {
    super(message, 'SCOPE_MISMATCH', 403, correlationId, details);
    this.name = 'KernelLifecycleScopeError';
  }
}

/**
 * Provider mode error: platform mode requested without Data Cloud providers.
 */
export class KernelLifecycleProviderModeError extends KernelLifecycleClientError {
  constructor(
    message: string,
    correlationId?: string,
    details?: Record<string, unknown>,
  ) {
    super(message, 'PROVIDER_MODE_UNAVAILABLE', 503, correlationId, details);
    this.name = 'KernelLifecycleProviderModeError';
  }
}

/**
 * Maps HTTP API errors to typed KernelLifecycleClientError subclasses.
 */
export function mapKernelLifecycleClientError(
  error: unknown,
  correlationId?: string,
): KernelLifecycleClientError {
  if (error instanceof KernelLifecycleClientError) {
    return error;
  }

  const apiErrorPayload = extractKernelLifecycleApiErrorPayload(error);
  if (apiErrorPayload !== undefined) {
    const apiError = KernelLifecycleApiErrorSchema.safeParse(apiErrorPayload);
    if (apiError.success) {
      const {
        statusCode,
        reasonCode,
        correlationId: responseCorrelationId,
        message,
        safeDetails,
        details,
      } = apiError.data;
      const errorDetails = safeDetails ?? details;
      const effectiveCorrelationId = correlationId ?? responseCorrelationId;
      const normalizedReasonCode = normalizeKernelLifecycleReasonCode(reasonCode);
      const effectiveStatusCode = statusCode ?? 500;

      if (statusCode === 401 || normalizedReasonCode === 'AUTHENTICATION_REQUIRED' || normalizedReasonCode === 'UNAUTHENTICATED') {
        return new KernelLifecycleAuthError(message, effectiveCorrelationId, errorDetails);
      }
      if (statusCode === 403 || normalizedReasonCode === 'SCOPE_MISMATCH' || normalizedReasonCode === 'FORBIDDEN') {
        return new KernelLifecycleScopeError(message, effectiveCorrelationId, errorDetails);
      }
      if (statusCode === 503 || normalizedReasonCode === 'PROVIDER_MODE_UNAVAILABLE' || normalizedReasonCode === 'PROVIDER_UNAVAILABLE') {
        return new KernelLifecycleProviderModeError(message, effectiveCorrelationId, errorDetails);
      }

      return new KernelLifecycleClientError(
        message,
        normalizedReasonCode,
        effectiveStatusCode,
        effectiveCorrelationId,
        errorDetails,
      );
    }
  }

  return new KernelLifecycleClientError(
    error instanceof Error ? error.message : 'Unknown Kernel lifecycle client error',
    'UNKNOWN_ERROR',
    500,
    correlationId,
  );
}

function normalizeKernelLifecycleReasonCode(reasonCode: string): string {
  return reasonCode
    .trim()
    .replace(/[^a-zA-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .toUpperCase();
}

export type LifecyclePlan = z.infer<typeof LifecyclePlanSchema>;
export type ProductInteractionPreflight = z.infer<typeof ProductInteractionPreflightSchema>;
export type LifecycleRun = z.infer<typeof LifecycleRunSchema>;
export type GateResultManifest = z.infer<typeof GateResultManifestSchema>;
export type VerifyHealthReport = z.infer<typeof VerifyHealthReportSchema>;
export type ApprovalRequest = z.infer<typeof ApprovalRequestSchema>;
export type ApprovalDecision = z.infer<typeof ApprovalDecisionSchema>;

// Re-export UI-facing summary types so Studio sections only import from this client module.
export type {
  LifecycleRunSummary,
  LifecycleGateSummary,
  LifecycleArtifactSummary,
  LifecycleDeploymentSummary,
  LifecycleHealthSummary,
} from '@ghatana/kernel-product-contracts';
export type ProductLifecyclePhase = (typeof PRODUCT_LIFECYCLE_PHASES)[number];

export interface KernelLifecycleClientOptions {
  readonly baseUrl?: string;
  readonly apiClient?: ApiClient;
  readonly tenantId?: string;
  readonly workspaceId?: string;
  readonly projectId?: string;
  readonly correlationIdFactory?: () => string;
  readonly authToken?: string;
}

export interface LifecyclePlanOptions {
  readonly environment?: string;
  readonly dryRun?: boolean;
  readonly providerMode?: 'bootstrap' | 'platform';
  readonly surfaceSelector?: string;
  readonly sourceRef?: string;
  readonly correlationId?: string;
}

export interface ExecuteLifecyclePhaseOptions extends LifecyclePlanOptions {
  readonly dryRun?: boolean;
}

export interface LifecycleRunQuery {
  readonly correlationId?: string;
}

export interface DeploymentManifestQuery {
  readonly environment?: string;
}

export interface PendingApprovalQuery {
  readonly productUnitId?: string;
  readonly runId?: string;
}

export interface ProductUnitIntentMutationOptions {
  readonly providerMode?: 'bootstrap' | 'platform';
  readonly evidenceRefs?: readonly string[];
  readonly correlationId?: string;
}

export interface KernelLifecycleClient {
  listProductUnits(): Promise<readonly ProductUnit[]>;
  getProductUnit(productUnitId: string): Promise<ProductUnit>;
  createLifecyclePlan(
    productUnitId: string,
    phase: ProductLifecyclePhase,
    options?: LifecyclePlanOptions,
  ): Promise<LifecyclePlan>;
  executeLifecyclePhase(
    productUnitId: string,
    phase: ProductLifecyclePhase,
    options?: ExecuteLifecyclePhaseOptions,
  ): Promise<LifecycleRun>;
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
  /**
   * Returns a UI-facing LifecycleRunSummary built entirely from public lifecycle truth.
   * Does not parse stdout, private logs, or product implementation files.
   */
  getLifecycleRunSummary(productUnitId: string, runId: string): Promise<LifecycleRunSummary>;
  listPendingApprovals(query?: PendingApprovalQuery): Promise<readonly ApprovalRequest[]>;
  requestApproval(actionRequest: ApprovalRequest): Promise<ProductApprovalGate>;
  submitApprovalDecision(
    approvalId: string,
    decision: ApprovalDecision,
  ): Promise<ProductApprovalGate>;
  previewProductUnitIntent?(
    intent: ProductUnitIntent,
    options?: ProductUnitIntentMutationOptions,
  ): Promise<ProductUnitIntentApplicationResult>;
  applyProductUnitIntent?(
    intent: ProductUnitIntent,
    options?: ProductUnitIntentMutationOptions,
  ): Promise<ProductUnitIntentApplicationResult>;
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
  private readonly authToken?: string;

  constructor(options: KernelLifecycleClientOptions) {
    this.apiClient = options.apiClient ?? new ApiClient({ baseUrl: options.baseUrl });
    this.tenantId = options.tenantId;
    this.workspaceId = options.workspaceId;
    this.projectId = options.projectId;
    this.correlationIdFactory =
      options.correlationIdFactory ?? (() => `studio-${Date.now()}`);
    this.authToken = options.authToken;
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
          ...(options.providerMode !== undefined ? { providerMode: options.providerMode } : {}),
          ...(options.surfaceSelector !== undefined ? { surfaceSelector: options.surfaceSelector } : {}),
          ...(options.sourceRef !== undefined ? { sourceRef: options.sourceRef } : {}),
        },
        schema: LifecyclePlanSchema,
      },
    );
    return response.data;
  }

  async executeLifecyclePhase(
    productUnitId: string,
    phase: ProductLifecyclePhase,
    options: ExecuteLifecyclePhaseOptions = {},
  ): Promise<LifecycleRun> {
    assertPhase(phase);
    const correlationId = options.correlationId ?? this.correlationIdFactory();
    const response = await this.apiClient.post<LifecycleRun>(
      `/api/kernel/product-units/${encodePathSegment(assertIdentifier(productUnitId, 'productUnitId'))}/lifecycle/execute`,
      {
        headers: this.buildHeaders(correlationId),
        body: {
          phase,
          correlationId,
          dryRun: options.dryRun ?? false,
          ...(options.environment !== undefined ? { environment: options.environment } : {}),
          ...(options.providerMode !== undefined ? { providerMode: options.providerMode } : {}),
          ...(options.surfaceSelector !== undefined ? { surfaceSelector: options.surfaceSelector } : {}),
          ...(options.sourceRef !== undefined ? { sourceRef: options.sourceRef } : {}),
        },
        schema: LifecycleRunSchema,
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

  async getLifecycleRunSummary(
    productUnitId: string,
    runId: string,
  ): Promise<LifecycleRunSummary> {
    // Fetch run record first; this is the single source of truth for the run.
    const run = await this.getLifecycleRun(productUnitId, runId);

    // Attempt to fetch gate result manifest if a ref is available.
    let gates: LifecycleGateSummary[] | undefined;
    if (run.manifestRefs?.['gateResultManifest'] !== undefined) {
      try {
        const gateManifest = await this.getGateResultManifest(productUnitId, runId);
        gates = gateManifest.gates.map((g) => ({
          gateId: g.gateId,
          passed: g.status === 'passed',
          required: g.required ?? true,
        }));
      } catch {
        // Non-fatal: gate manifest may not be available yet.
      }
    }

    // Attempt to fetch artifact manifest if a ref is available.
    let artifacts: LifecycleArtifactSummary[] | undefined;
    if (run.manifestRefs?.['artifactManifest'] !== undefined) {
      try {
        const artifactManifest = await this.getArtifactManifest(productUnitId, runId);
        artifacts = artifactManifest.artifacts.map((a) => ({
          artifactType: a.metadata.type,
          packaging: a.metadata.packaging,
          paths: [a.path],
          required: a.expected,
          produced: a.found,
          digest: a.fingerprint?.hash,
        }));
      } catch {
        // Non-fatal: artifact manifest may not be available yet.
      }
    }

    // Attempt to fetch deployment summary if a ref is available.
    let deployment: LifecycleDeploymentSummary | undefined;
    if (run.manifestRefs?.['deploymentManifest'] !== undefined) {
      try {
        const deploymentManifest = await this.getDeploymentManifest(productUnitId, runId);
        deployment = {
          environment: deploymentManifest.environment,
          adapter: deploymentManifest.target,
          succeeded: deploymentManifest.overallStatus === 'deployed',
          deployedAt: deploymentManifest.deployedAt,
        };
      } catch {
        // Non-fatal: deployment manifest may not be available yet.
      }
    }

    // Attempt to fetch health summary from the verify health report.
    let health: LifecycleHealthSummary | undefined;
    if (run.healthSnapshotRef !== undefined || run.manifestRefs?.['verifyHealthReport'] !== undefined) {
      try {
        const healthReport = await this.getVerifyHealthReport(productUnitId, runId);
        health = {
          status: healthReport.status === 'healthy'
            ? 'healthy'
            : healthReport.status === 'degraded'
              ? 'degraded'
              : healthReport.status === 'failed'
                ? 'unhealthy'
                : 'unknown',
          checkedAt: healthReport.checkedAt,
        };
      } catch {
        // Non-fatal: health report may not be available yet.
      }
    }

    const failedRequiredGateCount =
      gates === undefined
        ? undefined
        : gates.filter((g) => g.required && !g.passed).length;

    return parseLifecycleRunSummary({
      runId: run.runId,
      correlationId: run.correlationId,
      productUnitId: run.productUnitId,
      phase: run.phase ?? 'unknown',
      status: run.status,
      gates,
      artifacts,
      deployment,
      health,
      failedRequiredGateCount,
    });
  }

  async listPendingApprovals(query: PendingApprovalQuery = {}): Promise<readonly ApprovalRequest[]> {
    const response = await this.apiClient.get<readonly ApprovalRequest[]>(
      '/api/kernel/approvals',
      {
        headers: this.buildHeaders(),
        query: {
          ...(query.productUnitId === undefined ? {} : { productUnitId: query.productUnitId }),
          ...(query.runId === undefined ? {} : { runId: query.runId }),
        },
        schema: z.array(ApprovalRequestSchema),
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

  async previewProductUnitIntent(
    intent: ProductUnitIntent,
    options: ProductUnitIntentMutationOptions = {},
  ): Promise<ProductUnitIntentApplicationResult> {
    const parsedIntent = ProductUnitIntentSchema.parse(intent);
    const correlationId = options.correlationId ?? this.correlationIdFactory();
    const response = await this.apiClient.post<ProductUnitIntentApplicationResult>(
      '/api/v1/kernel/lifecycle/product-unit-intents',
      {
        headers: this.buildHeaders(correlationId),
        body: {
          intent: parsedIntent,
          requestedAction: 'preview',
          correlationId,
          ...(options.providerMode === undefined ? {} : { providerMode: options.providerMode }),
          ...(options.evidenceRefs === undefined ? {} : { evidenceRefs: options.evidenceRefs }),
        },
        schema: ProductUnitIntentApplicationResultSchema,
      },
    );
    return response.data;
  }

  async applyProductUnitIntent(
    intent: ProductUnitIntent,
    options: ProductUnitIntentMutationOptions = {},
  ): Promise<ProductUnitIntentApplicationResult> {
    const parsedIntent = ProductUnitIntentSchema.parse(intent);
    const correlationId = options.correlationId ?? this.correlationIdFactory();
    const response = await this.apiClient.post<ProductUnitIntentApplicationResult>(
      '/api/v1/kernel/lifecycle/product-unit-intents',
      {
        headers: this.buildHeaders(correlationId),
        body: {
          intent: parsedIntent,
          requestedAction: 'apply',
          correlationId,
          ...(options.providerMode === undefined ? {} : { providerMode: options.providerMode }),
          ...(options.evidenceRefs === undefined ? {} : { evidenceRefs: options.evidenceRefs }),
        },
        schema: ProductUnitIntentApplicationResultSchema,
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
      'X-Correlation-ID': correlationId ?? this.correlationIdFactory(),
      ...(this.tenantId !== undefined ? { 'X-Ghatana-Tenant-Id': this.tenantId } : {}),
      ...(this.workspaceId !== undefined
        ? { 'X-Ghatana-Workspace-Id': this.workspaceId }
        : {}),
      ...(this.projectId !== undefined ? { 'X-Ghatana-Project-Id': this.projectId } : {}),
      ...(this.authToken !== undefined ? { Authorization: `Bearer ${this.authToken}` } : {}),
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

function extractKernelLifecycleApiErrorPayload(
  error: unknown,
): unknown | undefined {
  if (isKernelLifecycleApiError(error)) {
    return error.response?.data;
  }
  return error;
}

function isKernelLifecycleApiError(error: unknown): error is ApiError {
  return typeof error === 'object' && error !== null && 'request' in error;
}
