/**
 * KernelLifecycleApiHandlers - framework-light HTTP handlers for Kernel lifecycle APIs.
 *
 * @doc.type module
 * @doc.purpose Exposes product-neutral Kernel lifecycle service operations to Studio and gateways
 * @doc.layer kernel-lifecycle
 * @doc.pattern Adapter
 */

import { z } from "zod";
import { randomUUID } from "node:crypto";
import type {
  ApprovalDecision,
  ApprovalRequest,
  ProductUnitIntent,
  ProductUnitScope,
} from "@ghatana/kernel-product-contracts";
import { ProductUnitIntentSchema } from "@ghatana/kernel-product-contracts";
import type {
  ProductLifecycleManifestType,
  ProductLifecyclePhase,
  ProductLifecyclePlan,
  ProductLifecycleResult,
} from "../domain/ProductLifecyclePhase.js";
import { KernelLifecycleError } from "../service/KernelLifecycleErrors.js";
import type {
  KernelLifecycleService,
  LifecycleRunSummary,
} from "../service/KernelLifecycleService.js";

export const PRODUCT_LIFECYCLE_PHASES = [
  "create",
  "bootstrap",
  "dev",
  "validate",
  "test",
  "build",
  "package",
  "release",
  "deploy",
  "verify",
  "promote",
  "rollback",
  "operate",
  "retire",
] as const satisfies readonly ProductLifecyclePhase[];

export const LIFECYCLE_RUN_STATUSES = [
  "healthy",
  "degraded",
  "blocked",
  "failed",
  "skipped",
  "planned",
  "running",
  "pending approval",
  "requires verification",
  "obsolete",
  "quarantined",
  "unknown",
] as const;

export const FAILURE_REASON_CODES = [
  "adapter-failed",
  "gate-failed",
  "artifact-missing",
  "manifest-write-failed",
  "approval-required",
  "policy-denied",
  "provider-unavailable",
  "run-not-found",
  "manifest-not-found",
  "execution-failed",
  "scope-headers-required",
  "authorization-failed",
  "not-ready",
  "lifecycle-not-enabled",
] as const;

const ParamsSchema = z.object({
  productUnitId: z.string().trim().min(1).optional(),
  runId: z.string().trim().min(1).optional(),
  approvalId: z.string().trim().min(1).optional(),
  jobId: z.string().trim().min(1).optional(),
});

const QuerySchema = z
  .record(z.string(), z.union([z.string(), z.number(), z.boolean()]))
  .default({});

const PlanBodySchema = z.object({
  phase: z.enum(PRODUCT_LIFECYCLE_PHASES),
  surfaceSelector: z.array(z.string().trim().min(1)).optional(),
  environment: z.string().trim().min(1).optional(),
  sourceRef: z.string().trim().min(1).optional(),
  outputDir: z.string().trim().min(1).optional(),
  providerMode: z.enum(["bootstrap", "platform"]).optional(),
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
  riskLevel: z.enum(["low", "medium", "high", "critical"]).optional(),
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

const ProductUnitIntentMutationBodySchema = z.object({
  intent: ProductUnitIntentSchema,
  requestedAction: z.enum(["preview", "apply"]).default("preview"),
  providerMode: z.enum(["bootstrap", "platform"]).optional(),
  correlationId: z.string().trim().min(1).optional(),
  evidenceRefs: z.array(z.string().trim().min(1)).optional(),
});

const MAX_SOURCE_ACQUISITION_FILE_SIZE_BYTES = 5 * 1024 * 1024;
const MAX_SOURCE_ACQUISITION_ARCHIVE_BYTES = 50 * 1024 * 1024;
const MAX_SOURCE_ACQUISITION_BASE64_BYTES =
  Math.ceil((MAX_SOURCE_ACQUISITION_ARCHIVE_BYTES * 4) / 3) + 4;
const ALLOWED_REPOSITORY_HOSTS = new Set(["github.com", "gitlab.com"]);

const WorkflowAuditSchema = z.object({
  persistedAt: z.string().datetime({ offset: true }),
  lastModifiedAt: z.string().datetime({ offset: true }),
  persistenceVersion: z.number().int().min(0),
});

const StudioWorkflowStateBodySchema = z
  .object({
    state: z.record(z.string(), z.unknown()),
    audit: WorkflowAuditSchema,
  })
  .strict();

const StudioWorkflowEvidenceBodySchema = z
  .object({
    evidenceId: z.string().trim().min(1),
    createdAt: z.string().datetime({ offset: true }),
    modelId: z.string().trim().min(1).optional(),
    label: z.string().trim().min(1).optional(),
  })
  .passthrough();

const SourceAcquisitionOptionsSchema = z
  .object({
    maxFileSize: z
      .number()
      .int()
      .positive()
      .max(MAX_SOURCE_ACQUISITION_FILE_SIZE_BYTES)
      .optional(),
    allowedExtensions: z
      .array(
        z
          .string()
          .trim()
          .regex(/^\.[a-z0-9][a-z0-9.+-]*$/i),
      )
      .max(64)
      .optional(),
    includeHidden: z.boolean().optional(),
  })
  .strict()
  .default({});

const RepositorySourceAcquisitionBodySchema = z
  .object({
    input: z
      .object({
        kind: z.enum(["github-repository", "gitlab-repository"]),
        repositoryUrl: z.string().url(),
        ref: z.string().trim().min(1).optional(),
      })
      .strict(),
    options: SourceAcquisitionOptionsSchema.optional(),
  })
  .strict();

const ArchiveSourceAcquisitionBodySchema = z
  .object({
    input: z
      .object({
        kind: z.literal("archive-upload"),
        file: z
          .object({
            name: z.string().trim().min(1).max(255),
            size: z
              .number()
              .int()
              .positive()
              .max(MAX_SOURCE_ACQUISITION_ARCHIVE_BYTES),
            type: z.string().trim().max(128).optional(),
            contentBase64: z
              .string()
              .trim()
              .min(1)
              .max(MAX_SOURCE_ACQUISITION_BASE64_BYTES),
          })
          .strict(),
      })
      .strict(),
    options: SourceAcquisitionOptionsSchema.optional(),
  })
  .strict();

const SourceAcquisitionJobUpdateBodySchema = z
  .object({
    status: z.enum(["running", "complete", "failed", "cancelled"]),
    startedAt: z.string().datetime({ offset: true }).optional(),
    completedAt: z.string().datetime({ offset: true }).optional(),
    totalBytes: z.number().int().nonnegative().optional(),
    fileCount: z.number().int().nonnegative().optional(),
    localWorkspacePath: z.string().trim().min(1).optional(),
    errorMessage: z.string().trim().min(1).optional(),
  })
  .strict()
  .superRefine((body, context) => {
    if (
      (body.status === "complete" ||
        body.status === "failed" ||
        body.status === "cancelled") &&
      body.completedAt === undefined
    ) {
      context.addIssue({
        code: "custom",
        path: ["completedAt"],
        message: "completedAt is required for terminal acquisition job states",
      });
    }
    if (
      body.status === "complete" &&
      (body.totalBytes === undefined ||
        body.fileCount === undefined ||
        body.localWorkspacePath === undefined)
    ) {
      context.addIssue({
        code: "custom",
        path: ["status"],
        message:
          "complete acquisition jobs require totalBytes, fileCount, and localWorkspacePath",
      });
    }
    if (body.status === "failed" && body.errorMessage === undefined) {
      context.addIssue({
        code: "custom",
        path: ["errorMessage"],
        message: "failed acquisition jobs require errorMessage",
      });
    }
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
  authenticate(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleActor | null>;
  authorizeProductUnitRead(
    actor: KernelLifecycleActor,
    context: KernelLifecycleAuthContext,
  ): Promise<boolean>;
  authorizeLifecyclePlan(
    actor: KernelLifecycleActor,
    context: KernelLifecycleAuthContext,
  ): Promise<boolean>;
  authorizeLifecycleExecute(
    actor: KernelLifecycleActor,
    context: KernelLifecycleAuthContext,
  ): Promise<boolean>;
  authorizeManifestRead(
    actor: KernelLifecycleActor,
    context: KernelLifecycleAuthContext,
  ): Promise<boolean>;
  authorizeApprovalRequest(
    actor: KernelLifecycleActor,
    context: KernelLifecycleAuthContext,
  ): Promise<boolean>;
  authorizeApprovalDecision(
    actor: KernelLifecycleActor,
    context: KernelLifecycleAuthContext,
  ): Promise<boolean>;
}

export interface StudioWorkflowStoreScope {
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
}

export interface StudioWorkflowStateRecord {
  readonly state: Record<string, unknown>;
  readonly audit: z.infer<typeof WorkflowAuditSchema>;
  readonly scope: StudioWorkflowStoreScope;
  readonly idempotencyKey?: string;
}

export interface StudioWorkflowEvidenceRecord {
  readonly evidence: Record<string, unknown>;
  readonly evidenceId: string;
  readonly scope: StudioWorkflowStoreScope;
  readonly idempotencyKey?: string;
}

export interface StudioSourceAcquisitionJob {
  readonly jobId: string;
  readonly status: "pending" | "running" | "complete" | "failed" | "cancelled";
  readonly descriptor: {
    readonly kind: "github" | "gitlab" | "archive";
    readonly uri: string;
    readonly label: string;
    readonly ref?: string;
  };
  readonly createdAt: string;
  readonly startedAt?: string;
  readonly completedAt?: string;
  readonly totalBytes?: number;
  readonly fileCount?: number;
  readonly localWorkspacePath?: string;
  readonly errorMessage?: string;
  readonly correlationId?: string;
  readonly leasedBy?: string;
  readonly leaseExpiresAt?: string;
  readonly attemptCount?: number;
  readonly scope: StudioWorkflowStoreScope;
}

export interface StudioWorkflowPersistenceStore {
  putWorkflowState(
    record: StudioWorkflowStateRecord,
  ): Promise<StudioWorkflowStateRecord>;
  getWorkflowState(
    scope: StudioWorkflowStoreScope,
  ): Promise<StudioWorkflowStateRecord | null>;
  clearWorkflowState(scope: StudioWorkflowStoreScope): Promise<void>;
  putWorkflowEvidence(
    record: StudioWorkflowEvidenceRecord,
  ): Promise<StudioWorkflowEvidenceRecord>;
  getWorkflowEvidence(
    scope: StudioWorkflowStoreScope,
    evidenceId: string,
  ): Promise<StudioWorkflowEvidenceRecord | null>;
}

export interface StudioSourceAcquisitionJobStore {
  putJob(job: StudioSourceAcquisitionJob): Promise<StudioSourceAcquisitionJob>;
  getJob(
    scope: StudioWorkflowStoreScope,
    jobId: string,
  ): Promise<StudioSourceAcquisitionJob | null>;
  updateJob(
    scope: StudioWorkflowStoreScope,
    jobId: string,
    patch: StudioSourceAcquisitionJobUpdate,
  ): Promise<StudioSourceAcquisitionJob | null>;
}

export interface StudioSourceAcquisitionArchivePayload {
  readonly jobId: string;
  readonly scope: StudioWorkflowStoreScope;
  readonly fileName: string;
  readonly size: number;
  readonly contentBase64: string;
  readonly receivedAt: string;
  readonly contentType?: string;
}

export interface StudioSourceAcquisitionPayloadStore {
  putArchivePayload(
    payload: StudioSourceAcquisitionArchivePayload,
  ): Promise<void>;
  getArchivePayload(
    scope: StudioWorkflowStoreScope,
    jobId: string,
  ): Promise<StudioSourceAcquisitionArchivePayload | null>;
  deleteArchivePayload?(
    scope: StudioWorkflowStoreScope,
    jobId: string,
  ): Promise<void>;
}

export interface StudioSourceAcquisitionJobUpdate {
  readonly status: "running" | "complete" | "failed" | "cancelled";
  readonly startedAt?: string;
  readonly completedAt?: string;
  readonly totalBytes?: number;
  readonly fileCount?: number;
  readonly localWorkspacePath?: string;
  readonly errorMessage?: string;
}

export interface KernelLifecycleApiHandlersOptions {
  readonly service: KernelLifecycleService;
  readonly studioWorkflowStore?: StudioWorkflowPersistenceStore;
  readonly studioSourceAcquisitionJobStore?: StudioSourceAcquisitionJobStore;
  readonly studioSourceAcquisitionPayloadStore?: StudioSourceAcquisitionPayloadStore;
  readonly requireScopeHeaders?: boolean;
  readonly allowUnscopedLocalDevelopment?: boolean;
  readonly authorizer?: KernelLifecycleAuthorizer;
  readonly requireAuthentication?: boolean;
}

export interface KernelLifecycleRouteMetadata {
  readonly routeId: string;
  readonly method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  readonly path: string;
  readonly handler: keyof KernelLifecycleApiHandlers;
}

export class KernelLifecycleApiHandlers {
  readonly routeMetadata: readonly KernelLifecycleRouteMetadata[] = [
    {
      routeId: "kernel.productUnits.list",
      method: "GET",
      path: "/api/kernel/product-units",
      handler: "listProductUnits",
    },
    {
      routeId: "kernel.productUnits.get",
      method: "GET",
      path: "/api/kernel/product-units/:productUnitId",
      handler: "getProductUnit",
    },
    {
      routeId: "kernel.lifecycle.plan",
      method: "POST",
      path: "/api/kernel/product-units/:productUnitId/lifecycle/plans",
      handler: "createLifecyclePlan",
    },
    {
      routeId: "kernel.lifecycle.execute",
      method: "POST",
      path: "/api/kernel/product-units/:productUnitId/lifecycle/execute",
      handler: "executeLifecyclePhase",
    },
    {
      routeId: "kernel.lifecycle.runs.list",
      method: "GET",
      path: "/api/kernel/product-units/:productUnitId/lifecycle/runs",
      handler: "listLifecycleRuns",
    },
    {
      routeId: "kernel.lifecycle.runs.get",
      method: "GET",
      path: "/api/kernel/product-units/:productUnitId/lifecycle/runs/:runId",
      handler: "getLifecycleRun",
    },
    {
      routeId: "kernel.lifecycle.manifest.gates",
      method: "GET",
      path: "/api/kernel/product-units/:productUnitId/lifecycle/runs/:runId/gate-result-manifest",
      handler: "getGateResultManifest",
    },
    {
      routeId: "kernel.lifecycle.manifest.artifacts",
      method: "GET",
      path: "/api/kernel/product-units/:productUnitId/lifecycle/runs/:runId/artifact-manifest",
      handler: "getArtifactManifest",
    },
    {
      routeId: "kernel.lifecycle.manifest.deployment",
      method: "GET",
      path: "/api/kernel/product-units/:productUnitId/lifecycle/runs/:runId/deployment-manifest",
      handler: "getDeploymentManifest",
    },
    {
      routeId: "kernel.lifecycle.manifest.verify",
      method: "GET",
      path: "/api/kernel/product-units/:productUnitId/lifecycle/runs/:runId/verify-health-report",
      handler: "getVerifyHealthReport",
    },
    {
      routeId: "kernel.approvals.list",
      method: "GET",
      path: "/api/kernel/approvals",
      handler: "listPendingApprovals",
    },
    {
      routeId: "kernel.approvals.request",
      method: "POST",
      path: "/api/kernel/approvals",
      handler: "requestApproval",
    },
    {
      routeId: "kernel.approvals.decide",
      method: "POST",
      path: "/api/kernel/approvals/:approvalId/decisions",
      handler: "submitApprovalDecision",
    },
    {
      routeId: "kernel.lifecycle.productUnitIntent.mutate",
      method: "POST",
      path: "/api/v1/kernel/lifecycle/product-unit-intents",
      handler: "mutateProductUnitIntent",
    },
    {
      routeId: "kernel.lifecycle.productUnitIntent.mutate",
      method: "POST",
      path: "/api/kernel/lifecycle/product-unit-intents",
      handler: "mutateProductUnitIntent",
    },
    {
      routeId: "kernel.studio.workflowState.put",
      method: "PUT",
      path: "/api/v1/studio/workflow-state",
      handler: "putStudioWorkflowState",
    },
    {
      routeId: "kernel.studio.workflowState.get",
      method: "GET",
      path: "/api/v1/studio/workflow-state",
      handler: "getStudioWorkflowState",
    },
    {
      routeId: "kernel.studio.workflowState.delete",
      method: "DELETE",
      path: "/api/v1/studio/workflow-state",
      handler: "deleteStudioWorkflowState",
    },
    {
      routeId: "kernel.studio.workflowEvidence.put",
      method: "PUT",
      path: "/api/v1/studio/workflow-evidence",
      handler: "putStudioWorkflowEvidence",
    },
    {
      routeId: "kernel.studio.workflowEvidence.get",
      method: "GET",
      path: "/api/v1/studio/workflow-evidence",
      handler: "getStudioWorkflowEvidence",
    },
    {
      routeId: "kernel.studio.sourceAcquisition.repository",
      method: "POST",
      path: "/api/v1/studio/source-acquisition/repository",
      handler: "createStudioRepositorySourceAcquisition",
    },
    {
      routeId: "kernel.studio.sourceAcquisition.archive",
      method: "POST",
      path: "/api/v1/studio/source-acquisition/archive",
      handler: "createStudioArchiveSourceAcquisition",
    },
    {
      routeId: "kernel.studio.sourceAcquisition.job.get",
      method: "GET",
      path: "/api/v1/studio/source-acquisition/jobs/:jobId",
      handler: "getStudioSourceAcquisitionJob",
    },
    {
      routeId: "kernel.studio.sourceAcquisition.job.patch",
      method: "PATCH",
      path: "/api/v1/studio/source-acquisition/jobs/:jobId",
      handler: "patchStudioSourceAcquisitionJob",
    },
  ];

  private readonly service: KernelLifecycleService;
  private readonly studioWorkflowStore: StudioWorkflowPersistenceStore;
  private readonly studioSourceAcquisitionJobStore: StudioSourceAcquisitionJobStore;
  private readonly studioSourceAcquisitionPayloadStore: StudioSourceAcquisitionPayloadStore;
  private readonly requireScopeHeaders: boolean;
  private readonly allowUnscopedLocalDevelopment: boolean;
  private readonly authorizer: KernelLifecycleAuthorizer | undefined;
  private readonly requireAuthentication: boolean;

  constructor(options: KernelLifecycleApiHandlersOptions) {
    this.service = options.service;
    this.studioWorkflowStore =
      options.studioWorkflowStore ??
      new InMemoryStudioWorkflowPersistenceStore();
    this.studioSourceAcquisitionJobStore =
      options.studioSourceAcquisitionJobStore ??
      new InMemoryStudioSourceAcquisitionJobStore();
    this.studioSourceAcquisitionPayloadStore =
      options.studioSourceAcquisitionPayloadStore ??
      new InMemoryStudioSourceAcquisitionPayloadStore();
    this.requireScopeHeaders = options.requireScopeHeaders ?? true;
    this.allowUnscopedLocalDevelopment =
      options.allowUnscopedLocalDevelopment ?? false;
    this.authorizer = options.authorizer;
    this.requireAuthentication = options.requireAuthentication ?? true;
  }

  async listProductUnits(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      await this.enforceAuth(request, context, "authorizeProductUnitRead", {});
      const productUnits = await this.service.listProductUnits({
        correlationId: context.correlationId,
        ...(context.scope === undefined ? {} : { scope: context.scope }),
      });
      return this.ok(productUnits, context.correlationId);
    });
  }

  async getProductUnit(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const productUnitId = requireParam(context.params, "productUnitId");
      await this.enforceAuth(request, context, "authorizeProductUnitRead", {
        productUnitId,
      });
      const productUnit = await this.service.getProductUnit(productUnitId, {
        correlationId: context.correlationId,
        ...(context.scope === undefined ? {} : { scope: context.scope }),
      });
      return this.ok(productUnit, context.correlationId);
    });
  }

  async createLifecyclePlan(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const productUnitId = requireParam(context.params, "productUnitId");
      const body = PlanBodySchema.parse(request.body ?? {});
      const correlationId = body.correlationId ?? context.correlationId;
      await this.enforceAuth(request, context, "authorizeLifecyclePlan", {
        productUnitId,
        correlationId,
      });
      const plan = await this.service.createLifecyclePlan(
        productUnitId,
        body.phase,
        {
          correlationId,
          ...(context.scope === undefined ? {} : { scope: context.scope }),
          ...(body.surfaceSelector === undefined
            ? {}
            : { surfaceSelector: body.surfaceSelector }),
          ...(body.environment === undefined
            ? {}
            : { environment: body.environment }),
          ...(body.sourceRef === undefined
            ? {}
            : { sourceRef: body.sourceRef }),
          ...(body.outputDir === undefined
            ? {}
            : { outputDir: body.outputDir }),
          ...(body.providerMode === undefined
            ? {}
            : { providerMode: body.providerMode }),
        },
      );
      return this.created(toLifecyclePlanResponse(plan), correlationId);
    });
  }

  async executeLifecyclePhase(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const productUnitId = requireParam(context.params, "productUnitId");
      const body = ExecuteBodySchema.parse(request.body ?? {});
      const correlationId = body.correlationId ?? context.correlationId;
      await this.enforceAuth(request, context, "authorizeLifecycleExecute", {
        productUnitId,
        correlationId,
      });
      const result = await this.service.runLifecyclePhase(
        productUnitId,
        body.phase,
        {
          dryRun: body.dryRun,
          correlationId,
          ...(context.scope === undefined ? {} : { scope: context.scope }),
          ...(body.surfaceSelector === undefined
            ? {}
            : { surfaceSelector: body.surfaceSelector }),
          ...(body.environment === undefined
            ? {}
            : { environment: body.environment }),
          ...(body.sourceRef === undefined
            ? {}
            : { sourceRef: body.sourceRef }),
          ...(body.outputDir === undefined
            ? {}
            : { outputDir: body.outputDir }),
          ...(body.providerMode === undefined
            ? {}
            : { providerMode: body.providerMode }),
        },
      );
      return this.ok(toLifecycleRunResponse(result), correlationId);
    });
  }

  async listLifecycleRuns(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const productUnitId = requireParam(context.params, "productUnitId");
      await this.enforceAuth(request, context, "authorizeProductUnitRead", {
        productUnitId,
      });
      const phase = optionalPhase(context.query.phase);
      const correlationIdQuery = optionalString(context.query.correlationId);
      const runs = await this.service.listLifecycleRuns(productUnitId, {
        ...(phase === undefined ? {} : { phase }),
        ...(correlationIdQuery === undefined
          ? {}
          : { correlationId: correlationIdQuery }),
      });
      return this.ok(
        runs.map(toLifecycleSummaryResponse),
        context.correlationId,
      );
    });
  }

  async getLifecycleRun(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const productUnitId = requireParam(context.params, "productUnitId");
      const runId = requireParam(context.params, "runId");
      await this.enforceAuth(request, context, "authorizeProductUnitRead", {
        productUnitId,
        runId,
      });
      const run = await this.service.getLifecycleRun(productUnitId, runId);
      return this.ok(toLifecycleSummaryResponse(run), context.correlationId);
    });
  }

  async getGateResultManifest(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.getManifest(request, "gate-result-manifest");
  }

  async getArtifactManifest(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.getManifest(request, "artifact-manifest");
  }

  async getDeploymentManifest(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.getManifest(request, "deployment-manifest");
  }

  async getVerifyHealthReport(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.getManifest(request, "verify-health-report");
  }

  async requestApproval(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const parsed = ApprovalRequestBodySchema.parse(request.body ?? {});
      await this.enforceAuth(request, context, "authorizeApprovalRequest", {
        productUnitId: parsed.productUnitId,
      });
      const approvalRequest: ApprovalRequest = toApprovalRequest(
        parsed,
        context.correlationId,
      );
      const result = await this.service.requestApproval(approvalRequest);
      return this.created(result, context.correlationId);
    });
  }

  async listPendingApprovals(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const productUnitId = optionalString(context.query.productUnitId);
      const runId = optionalString(context.query.runId);
      await this.enforceAuth(request, context, "authorizeProductUnitRead", {
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

  async submitApprovalDecision(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const approvalId = requireParam(context.params, "approvalId");
      await this.enforceAuth(request, context, "authorizeApprovalDecision", {});
      const parsedDecision = ApprovalDecisionBodySchema.parse(
        request.body ?? {},
      );
      const decision: ApprovalDecision = toApprovalDecision(parsedDecision);
      if (decision.approvalId !== approvalId) {
        throw new KernelLifecycleError({
          reasonCode: "invalid-approval-decision",
          message: "approvalId path parameter must match decision.approvalId",
          correlationId: context.correlationId,
        });
      }
      const result = await this.service.submitApprovalDecision(
        approvalId,
        decision,
      );
      return this.ok(result, context.correlationId);
    });
  }

  async mutateProductUnitIntent(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const body = ProductUnitIntentMutationBodySchema.parse(
        request.body ?? {},
      );
      const correlationId = body.correlationId ?? context.correlationId;
      await this.enforceAuth(
        request,
        context,
        body.requestedAction === "apply"
          ? "authorizeLifecycleExecute"
          : "authorizeLifecyclePlan",
        {
          productUnitId: body.intent.productUnit.id,
          correlationId,
          phase: "create",
        },
      );
      const result = await this.service.applyProductUnitIntent(
        body.intent as ProductUnitIntent,
        {
          allowWrite: body.requestedAction === "apply",
          ...(body.providerMode === undefined
            ? {}
            : { mode: body.providerMode }),
        },
      );
      return this.ok(result, correlationId);
    });
  }

  async putStudioWorkflowState(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const scope = this.requireScope(context);
      const body = StudioWorkflowStateBodySchema.parse(request.body ?? {});
      assertNoSecrets(body.state, context.correlationId);
      await this.enforceAuth(request, context, "authorizeLifecyclePlan", {
        correlationId: context.correlationId,
      });
      const idempotencyKey = idempotencyKeyFromHeaders(request.headers);
      const record = await this.studioWorkflowStore.putWorkflowState({
        state: body.state,
        audit: body.audit,
        scope,
        ...(idempotencyKey === undefined ? {} : { idempotencyKey }),
      });
      return this.ok(
        { state: record.state, audit: record.audit },
        context.correlationId,
      );
    });
  }

  async getStudioWorkflowState(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const scope = this.requireScope(context);
      await this.enforceAuth(request, context, "authorizeProductUnitRead", {
        correlationId: context.correlationId,
      });
      const record = await this.studioWorkflowStore.getWorkflowState(scope);
      if (record === null) {
        throw new KernelLifecycleError({
          reasonCode: "studio-workflow-state-not-found",
          message: "Studio workflow state was not found for this scope",
          correlationId: context.correlationId,
        });
      }
      return this.ok(
        { state: record.state, audit: record.audit },
        context.correlationId,
      );
    });
  }

  async deleteStudioWorkflowState(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const scope = this.requireScope(context);
      await this.enforceAuth(request, context, "authorizeLifecycleExecute", {
        correlationId: context.correlationId,
      });
      await this.studioWorkflowStore.clearWorkflowState(scope);
      return this.ok({ deleted: true }, context.correlationId);
    });
  }

  async putStudioWorkflowEvidence(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const scope = this.requireScope(context);
      const evidence = StudioWorkflowEvidenceBodySchema.parse(
        request.body ?? {},
      );
      assertNoSecrets(evidence, context.correlationId);
      await this.enforceAuth(request, context, "authorizeLifecyclePlan", {
        correlationId: context.correlationId,
      });
      const idempotencyKey = idempotencyKeyFromHeaders(request.headers);
      const record = await this.studioWorkflowStore.putWorkflowEvidence({
        evidence,
        evidenceId: evidence.evidenceId,
        scope,
        ...(idempotencyKey === undefined ? {} : { idempotencyKey }),
      });
      return this.created(record.evidence, context.correlationId);
    });
  }

  async getStudioWorkflowEvidence(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const scope = this.requireScope(context);
      const evidenceId = optionalString(context.query.evidenceId);
      if (evidenceId === undefined || evidenceId.trim().length === 0) {
        throw new KernelLifecycleError({
          reasonCode: "invalid-request",
          message:
            "Studio workflow evidence lookup requires evidenceId query parameter",
          correlationId: context.correlationId,
        });
      }
      await this.enforceAuth(request, context, "authorizeProductUnitRead", {
        correlationId: context.correlationId,
      });
      const record = await this.studioWorkflowStore.getWorkflowEvidence(
        scope,
        evidenceId,
      );
      if (record === null) {
        throw new KernelLifecycleError({
          reasonCode: "studio-workflow-evidence-not-found",
          message: "Studio workflow evidence was not found for this scope",
          correlationId: context.correlationId,
        });
      }
      return this.ok(record.evidence, context.correlationId);
    });
  }

  async createStudioRepositorySourceAcquisition(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const scope = this.requireScope(context);
      const body = RepositorySourceAcquisitionBodySchema.parse(
        request.body ?? {},
      );
      assertNoSecrets(body, context.correlationId);
      assertSupportedRepositoryUrl(
        body.input.kind,
        body.input.repositoryUrl,
        context.correlationId,
      );
      await this.enforceAuth(request, context, "authorizeLifecyclePlan", {
        correlationId: context.correlationId,
      });
      const descriptorKind =
        body.input.kind === "github-repository" ? "github" : "gitlab";
      const acquisitionJob = await this.studioSourceAcquisitionJobStore.putJob(
        createStudioAcquisitionJob({
          kind: descriptorKind,
          uri: body.input.repositoryUrl,
          label: body.input.repositoryUrl,
          ...(body.input.ref === undefined ? {} : { ref: body.input.ref }),
          correlationId: context.correlationId,
          scope,
        }),
      );
      return this.accepted(
        {
          sources: [],
          errors: [],
          partial: false,
          descriptor: {
            kind: descriptorKind,
            uri: body.input.repositoryUrl,
            label: body.input.repositoryUrl,
            ...(body.input.ref === undefined ? {} : { ref: body.input.ref }),
          },
          acquisitionJob,
        },
        context.correlationId,
      );
    });
  }

  async createStudioArchiveSourceAcquisition(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const scope = this.requireScope(context);
      const body = ArchiveSourceAcquisitionBodySchema.parse(request.body ?? {});
      assertNoSecrets(body.options ?? {}, context.correlationId);
      assertSafeArchiveName(body.input.file.name, context.correlationId);
      assertArchivePayloadSize(
        body.input.file.contentBase64,
        body.input.file.size,
        context.correlationId,
      );
      await this.enforceAuth(request, context, "authorizeLifecyclePlan", {
        correlationId: context.correlationId,
      });
      const uri = `archive://${body.input.file.name}`;
      const acquisitionJob = await this.studioSourceAcquisitionJobStore.putJob(
        createStudioAcquisitionJob({
          kind: "archive",
          uri,
          label: body.input.file.name,
          correlationId: context.correlationId,
          totalBytes: body.input.file.size,
          scope,
        }),
      );
      await this.studioSourceAcquisitionPayloadStore.putArchivePayload({
        jobId: acquisitionJob.jobId,
        scope,
        fileName: body.input.file.name,
        size: body.input.file.size,
        contentBase64: body.input.file.contentBase64,
        receivedAt: acquisitionJob.createdAt,
        ...(body.input.file.type === undefined
          ? {}
          : { contentType: body.input.file.type }),
      });
      return this.accepted(
        {
          sources: [],
          errors: [],
          partial: false,
          descriptor: {
            kind: "archive",
            uri,
            label: body.input.file.name,
          },
          acquisitionJob,
        },
        context.correlationId,
      );
    });
  }

  async getStudioSourceAcquisitionJob(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const scope = this.requireScope(context);
      const jobId = requireParam(context.params, "jobId");
      await this.enforceAuth(request, context, "authorizeProductUnitRead", {
        correlationId: context.correlationId,
      });
      const job = await this.studioSourceAcquisitionJobStore.getJob(
        scope,
        jobId,
      );
      if (job === null) {
        throw new KernelLifecycleError({
          reasonCode: "studio-source-acquisition-job-not-found",
          message: "Studio source acquisition job was not found for this scope",
          correlationId: context.correlationId,
        });
      }
      return this.ok(job, context.correlationId);
    });
  }

  async patchStudioSourceAcquisitionJob(
    request: KernelLifecycleApiRequest,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const scope = this.requireScope(context);
      const jobId = requireParam(context.params, "jobId");
      const body = SourceAcquisitionJobUpdateBodySchema.parse(
        request.body ?? {},
      );
      assertNoSecrets(body, context.correlationId);
      await this.enforceAuth(request, context, "authorizeLifecycleExecute", {
        correlationId: context.correlationId,
      });
      const job = await this.studioSourceAcquisitionJobStore.updateJob(
        scope,
        jobId,
        toSourceAcquisitionJobUpdate(body),
      );
      if (job === null) {
        throw new KernelLifecycleError({
          reasonCode: "studio-source-acquisition-job-not-found",
          message: "Studio source acquisition job was not found for this scope",
          correlationId: context.correlationId,
        });
      }
      return this.ok(job, context.correlationId);
    });
  }

  private async getManifest(
    request: KernelLifecycleApiRequest,
    manifestType: ProductLifecycleManifestType,
  ): Promise<KernelLifecycleApiResponse> {
    return this.handle(request, async (context) => {
      const productUnitId = requireParam(context.params, "productUnitId");
      const runId = requireParam(context.params, "runId");
      await this.enforceAuth(request, context, "authorizeManifestRead", {
        productUnitId,
        runId,
      });
      const phase = optionalPhase(context.query.phase);
      const manifest = await this.service.getManifest(
        productUnitId,
        runId,
        manifestType,
        phase,
      );
      return this.ok(manifest, context.correlationId);
    });
  }

  private async enforceAuth(
    request: KernelLifecycleApiRequest,
    context: HandlerContext,
    check: keyof Omit<KernelLifecycleAuthorizer, "authenticate">,
    authContext: KernelLifecycleAuthContext,
  ): Promise<void> {
    if (this.authorizer === undefined) {
      if (this.requireAuthentication && !this.allowUnscopedLocalDevelopment) {
        throw new KernelLifecycleError({
          reasonCode: "authentication-required",
          message:
            "Kernel lifecycle API requires an authorizer when requireAuthentication is true",
          correlationId: context.correlationId,
        });
      }
      return;
    }

    const actor = await this.authorizer.authenticate(request);
    if (actor === null) {
      throw new KernelLifecycleError({
        reasonCode: "authentication-required",
        message: "Authentication required",
        correlationId: context.correlationId,
      });
    }

    const allowed = await this.authorizer[check](actor, {
      ...authContext,
      ...(authContext.correlationId === undefined
        ? { correlationId: context.correlationId }
        : {}),
    });
    if (!allowed) {
      throw new KernelLifecycleError({
        reasonCode: "authorization-failed",
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
      return this.errorResponse(
        error,
        correlationIdFromHeaders(request.headers),
      );
    }
  }

  private contextFromRequest(
    request: KernelLifecycleApiRequest,
  ): HandlerContext {
    const headers = normalizeHeaders(request.headers);
    const correlationId =
      headers["x-correlation-id"] ?? `kernel-api-${Date.now()}`;
    const scope = this.scopeFromHeaders(headers, correlationId);
    return {
      params: ParamsSchema.parse(request.params ?? {}),
      query: QuerySchema.parse(request.query ?? {}) as Record<
        string,
        string | number | boolean
      >,
      correlationId,
      ...(scope === undefined ? {} : { scope }),
    };
  }

  private scopeFromHeaders(
    headers: Record<string, string>,
    correlationId: string,
  ): ProductUnitScope | undefined {
    const tenantId = headers["x-ghatana-tenant-id"] ?? headers["x-tenant-id"];
    const workspaceId =
      headers["x-ghatana-workspace-id"] ?? headers["x-workspace-id"];
    const projectId =
      headers["x-ghatana-project-id"] ?? headers["x-project-id"];
    if (
      tenantId !== undefined &&
      workspaceId !== undefined &&
      projectId !== undefined
    ) {
      return { tenantId, workspaceId, projectId };
    }
    if (this.allowUnscopedLocalDevelopment || !this.requireScopeHeaders) {
      return undefined;
    }
    throw new KernelLifecycleError({
      reasonCode: "scope-headers-required",
      message:
        "Kernel lifecycle API requires tenant, workspace, and project headers",
      correlationId,
      safeDetails: {
        missingHeaders: [
          ...(tenantId === undefined ? ["X-Ghatana-Tenant-Id"] : []),
          ...(workspaceId === undefined ? ["X-Ghatana-Workspace-Id"] : []),
          ...(projectId === undefined ? ["X-Ghatana-Project-Id"] : []),
        ],
      },
    });
  }

  private ok(body: unknown, correlationId: string): KernelLifecycleApiResponse {
    return { statusCode: 200, headers: responseHeaders(correlationId), body };
  }

  private created(
    body: unknown,
    correlationId: string,
  ): KernelLifecycleApiResponse {
    return { statusCode: 201, headers: responseHeaders(correlationId), body };
  }

  private accepted(
    body: unknown,
    correlationId: string,
  ): KernelLifecycleApiResponse {
    return { statusCode: 202, headers: responseHeaders(correlationId), body };
  }

  private requireScope(context: HandlerContext): StudioWorkflowStoreScope {
    if (context.scope === undefined) {
      throw new KernelLifecycleError({
        reasonCode: "scope-headers-required",
        message:
          "Studio workflow persistence requires tenant, workspace, and project scope",
        correlationId: context.correlationId,
      });
    }
    return context.scope;
  }

  private errorResponse(
    error: unknown,
    fallbackCorrelationId?: string,
  ): KernelLifecycleApiResponse {
    if (error instanceof z.ZodError) {
      const correlationId = fallbackCorrelationId ?? `kernel-api-${Date.now()}`;
      return {
        statusCode: 400,
        headers: responseHeaders(correlationId),
        body: {
          reasonCode: "invalid-request",
          message: "Kernel lifecycle API request validation failed",
          correlationId,
          safeDetails: {
            issues: error.issues.map((issue) => ({
              path: issue.path,
              message: issue.message,
            })),
          },
        },
      };
    }
    const normalized =
      error instanceof KernelLifecycleError
        ? error
        : new KernelLifecycleError({
            reasonCode: "internal-error",
            message: error instanceof Error ? error.message : String(error),
            ...(fallbackCorrelationId === undefined
              ? {}
              : { correlationId: fallbackCorrelationId }),
          });
    const statusCode = statusCodeForReason(normalized.reasonCode);
    return {
      statusCode,
      headers: responseHeaders(
        normalized.correlationId ??
          fallbackCorrelationId ??
          `kernel-api-${Date.now()}`,
      ),
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

function requireParam(
  params: z.infer<typeof ParamsSchema>,
  name: "productUnitId" | "runId" | "approvalId" | "jobId",
): string {
  const value = params[name];
  if (value === undefined) {
    throw new KernelLifecycleError({
      reasonCode: "invalid-request",
      message: `Missing required path parameter: ${name}`,
    });
  }
  return value;
}

function optionalPhase(
  value: string | number | boolean | undefined,
): ProductLifecyclePhase | undefined {
  if (value === undefined) {
    return undefined;
  }
  const parsed = z.enum(PRODUCT_LIFECYCLE_PHASES).safeParse(String(value));
  if (!parsed.success) {
    throw new KernelLifecycleError({
      reasonCode: "invalid-request",
      message: `Unsupported lifecycle phase: ${String(value)}`,
    });
  }
  return parsed.data;
}

function optionalString(
  value: string | number | boolean | undefined,
): string | undefined {
  return value === undefined ? undefined : String(value);
}

function normalizeHeaders(
  headers: KernelLifecycleApiRequest["headers"],
): Record<string, string> {
  const normalized: Record<string, string> = {};
  for (const [key, value] of Object.entries(headers ?? {})) {
    if (value === undefined) {
      continue;
    }
    normalized[key.toLowerCase()] = Array.isArray(value)
      ? value.join(",")
      : value;
  }
  return normalized;
}

function correlationIdFromHeaders(
  headers: KernelLifecycleApiRequest["headers"],
): string | undefined {
  return normalizeHeaders(headers)["x-correlation-id"];
}

function idempotencyKeyFromHeaders(
  headers: KernelLifecycleApiRequest["headers"],
): string | undefined {
  return normalizeHeaders(headers)["idempotency-key"];
}

function responseHeaders(correlationId: string): Record<string, string> {
  return {
    "content-type": "application/json",
    "x-correlation-id": correlationId,
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
    ...(parsed.requestedAt === undefined
      ? {}
      : { requestedAt: parsed.requestedAt }),
    ...(parsed.environment === undefined
      ? {}
      : { environment: parsed.environment }),
    ...(parsed.action === undefined ? {} : { action: parsed.action }),
    ...(parsed.riskLevel === undefined ? {} : { riskLevel: parsed.riskLevel }),
    ...(parsed.evidenceRefs === undefined
      ? {}
      : { evidenceRefs: parsed.evidenceRefs }),
  };
}

function toApprovalDecision(
  parsed: z.infer<typeof ApprovalDecisionBodySchema>,
): ApprovalDecision {
  return {
    approvalId: parsed.approvalId,
    approved: parsed.approved,
    approvedBy: parsed.approvedBy,
    reason: parsed.reason,
    decidedAt: parsed.decidedAt,
    ...(parsed.evidenceRefs === undefined
      ? {}
      : { evidenceRefs: parsed.evidenceRefs }),
  };
}

function statusCodeForReason(reasonCode: string): number {
  if (
    reasonCode === "product-unit-not-found" ||
    reasonCode === "run-not-found" ||
    reasonCode === "manifest-not-found" ||
    reasonCode === "studio-workflow-state-not-found" ||
    reasonCode === "studio-workflow-evidence-not-found" ||
    reasonCode === "studio-source-acquisition-job-not-found"
  ) {
    return 404;
  }
  if (
    reasonCode === "invalid-request" ||
    reasonCode === "invalid-approval-decision"
  ) {
    return 400;
  }
  if (reasonCode === "invalid-acquisition-job-transition") {
    return 409;
  }
  if (reasonCode === "approval-required") {
    return 409;
  }
  if (reasonCode === "evidence-immutable") {
    return 409;
  }
  if (reasonCode === "not-ready" || reasonCode === "lifecycle-not-enabled") {
    return 409;
  }
  if (reasonCode === "authentication-required") {
    return 401;
  }
  if (
    reasonCode === "scope-headers-required" ||
    reasonCode === "authorization-failed"
  ) {
    return 403;
  }
  if (reasonCode === "provider-unavailable") {
    return 503;
  }
  return 500;
}

function scopeKey(scope: StudioWorkflowStoreScope): string {
  return `${scope.tenantId}:${scope.workspaceId}:${scope.projectId}`;
}

function stableStringify(value: unknown): string {
  if (Array.isArray(value)) {
    return `[${value.map(stableStringify).join(",")}]`;
  }
  if (value !== null && typeof value === "object") {
    const entries = Object.entries(value as Record<string, unknown>).sort(
      ([a], [b]) => a.localeCompare(b),
    );
    return `{${entries.map(([key, entry]) => `${JSON.stringify(key)}:${stableStringify(entry)}`).join(",")}}`;
  }
  return JSON.stringify(value);
}

function assertNoSecrets(value: unknown, correlationId: string): void {
  const secretKeys = new Set([
    "authorization",
    "authtoken",
    "accesstoken",
    "refreshtoken",
    "password",
    "secret",
  ]);
  const visit = (node: unknown): boolean => {
    if (Array.isArray(node)) {
      return node.some(visit);
    }
    if (node !== null && typeof node === "object") {
      return Object.entries(node as Record<string, unknown>).some(
        ([key, entry]) => secretKeys.has(key.toLowerCase()) || visit(entry),
      );
    }
    return false;
  };
  if (visit(value)) {
    throw new KernelLifecycleError({
      reasonCode: "invalid-request",
      message:
        "Studio workflow payloads must not contain credentials or secrets",
      correlationId,
    });
  }
}

function assertSupportedRepositoryUrl(
  kind: "github-repository" | "gitlab-repository",
  repositoryUrl: string,
  correlationId: string,
): void {
  let url: URL;
  try {
    url = new URL(repositoryUrl);
  } catch {
    throw new KernelLifecycleError({
      reasonCode: "invalid-request",
      message: "Repository source acquisition requires a valid HTTPS URL",
      correlationId,
    });
  }
  if (url.protocol !== "https:") {
    throw new KernelLifecycleError({
      reasonCode: "invalid-request",
      message:
        "Repository source acquisition only accepts HTTPS repository URLs",
      correlationId,
    });
  }
  if (url.username !== "" || url.password !== "") {
    throw new KernelLifecycleError({
      reasonCode: "invalid-request",
      message:
        "Repository source acquisition URLs must not contain embedded credentials",
      correlationId,
    });
  }
  const expectedHost =
    kind === "github-repository" ? "github.com" : "gitlab.com";
  if (
    url.hostname.toLowerCase() !== expectedHost ||
    !ALLOWED_REPOSITORY_HOSTS.has(url.hostname.toLowerCase())
  ) {
    throw new KernelLifecycleError({
      reasonCode: "invalid-request",
      message: `${kind} source acquisition only accepts ${expectedHost} repository URLs`,
      correlationId,
    });
  }
  const pathSegments = url.pathname.split("/").filter(Boolean);
  if (pathSegments.length < 2) {
    throw new KernelLifecycleError({
      reasonCode: "invalid-request",
      message:
        "Repository source acquisition requires owner/project path segments",
      correlationId,
    });
  }
}

function assertSafeArchiveName(fileName: string, correlationId: string): void {
  const normalizedName = fileName.replace(/\\/g, "/");
  const segments = normalizedName.split("/");
  if (
    normalizedName.startsWith("/") ||
    /^[a-z]:\//i.test(normalizedName) ||
    normalizedName.includes("\0") ||
    segments.some(
      (segment) => segment === "" || segment === "." || segment === "..",
    )
  ) {
    throw new KernelLifecycleError({
      reasonCode: "invalid-request",
      message: "Archive source acquisition requires a safe archive file name",
      correlationId,
    });
  }
  if (!/\.(zip|tar|tgz|tar\.gz)$/i.test(normalizedName)) {
    throw new KernelLifecycleError({
      reasonCode: "invalid-request",
      message:
        "Archive source acquisition only accepts ZIP, TAR, TGZ, or TAR.GZ uploads",
      correlationId,
    });
  }
}

function assertArchivePayloadSize(
  contentBase64: string,
  declaredSize: number,
  correlationId: string,
): void {
  let decodedSize = 0;
  try {
    decodedSize = Buffer.from(contentBase64, "base64").byteLength;
  } catch {
    throw new KernelLifecycleError({
      reasonCode: "invalid-request",
      message: "Archive upload payload must be valid base64",
      correlationId,
    });
  }
  if (decodedSize !== declaredSize) {
    throw new KernelLifecycleError({
      reasonCode: "invalid-request",
      message: "Archive upload size must match decoded payload bytes",
      correlationId,
    });
  }
}

class InMemoryStudioWorkflowPersistenceStore implements StudioWorkflowPersistenceStore {
  private readonly workflowStates = new Map<
    string,
    StudioWorkflowStateRecord
  >();
  private readonly evidence = new Map<string, StudioWorkflowEvidenceRecord>();

  async putWorkflowState(
    record: StudioWorkflowStateRecord,
  ): Promise<StudioWorkflowStateRecord> {
    const key = scopeKey(record.scope);
    const existing = this.workflowStates.get(key);
    if (
      existing?.idempotencyKey !== undefined &&
      existing.idempotencyKey === record.idempotencyKey
    ) {
      return existing;
    }
    this.workflowStates.set(key, record);
    return record;
  }

  async getWorkflowState(
    scope: StudioWorkflowStoreScope,
  ): Promise<StudioWorkflowStateRecord | null> {
    return this.workflowStates.get(scopeKey(scope)) ?? null;
  }

  async clearWorkflowState(scope: StudioWorkflowStoreScope): Promise<void> {
    this.workflowStates.delete(scopeKey(scope));
  }

  async putWorkflowEvidence(
    record: StudioWorkflowEvidenceRecord,
  ): Promise<StudioWorkflowEvidenceRecord> {
    const key = `${scopeKey(record.scope)}:${record.evidenceId}`;
    const existing = this.evidence.get(key);
    if (existing !== undefined) {
      if (
        stableStringify(existing.evidence) !== stableStringify(record.evidence)
      ) {
        throw new KernelLifecycleError({
          reasonCode: "evidence-immutable",
          message: "Studio workflow evidence is immutable once persisted",
        });
      }
      return existing;
    }
    this.evidence.set(key, record);
    return record;
  }

  async getWorkflowEvidence(
    scope: StudioWorkflowStoreScope,
    evidenceId: string,
  ): Promise<StudioWorkflowEvidenceRecord | null> {
    return this.evidence.get(`${scopeKey(scope)}:${evidenceId}`) ?? null;
  }
}

class InMemoryStudioSourceAcquisitionJobStore implements StudioSourceAcquisitionJobStore {
  private readonly jobs = new Map<string, StudioSourceAcquisitionJob>();

  async putJob(
    job: StudioSourceAcquisitionJob,
  ): Promise<StudioSourceAcquisitionJob> {
    this.jobs.set(this.key(job.scope, job.jobId), job);
    return job;
  }

  async getJob(
    scope: StudioWorkflowStoreScope,
    jobId: string,
  ): Promise<StudioSourceAcquisitionJob | null> {
    return this.jobs.get(this.key(scope, jobId)) ?? null;
  }

  async updateJob(
    scope: StudioWorkflowStoreScope,
    jobId: string,
    patch: StudioSourceAcquisitionJobUpdate,
  ): Promise<StudioSourceAcquisitionJob | null> {
    const key = this.key(scope, jobId);
    const existing = this.jobs.get(key);
    if (existing === undefined) {
      return null;
    }
    assertAllowedAcquisitionJobTransition(existing.status, patch.status);
    const updated: StudioSourceAcquisitionJob = {
      ...existing,
      status: patch.status,
      ...(patch.startedAt === undefined ? {} : { startedAt: patch.startedAt }),
      ...(patch.completedAt === undefined
        ? {}
        : { completedAt: patch.completedAt }),
      ...(patch.totalBytes === undefined
        ? {}
        : { totalBytes: patch.totalBytes }),
      ...(patch.fileCount === undefined ? {} : { fileCount: patch.fileCount }),
      ...(patch.localWorkspacePath === undefined
        ? {}
        : { localWorkspacePath: patch.localWorkspacePath }),
      ...(patch.errorMessage === undefined
        ? {}
        : { errorMessage: patch.errorMessage }),
    };
    this.jobs.set(key, updated);
    return updated;
  }

  private key(scope: StudioWorkflowStoreScope, jobId: string): string {
    return `${scopeKey(scope)}:${jobId}`;
  }
}

class InMemoryStudioSourceAcquisitionPayloadStore implements StudioSourceAcquisitionPayloadStore {
  private readonly payloads = new Map<
    string,
    StudioSourceAcquisitionArchivePayload
  >();

  async putArchivePayload(
    payload: StudioSourceAcquisitionArchivePayload,
  ): Promise<void> {
    this.payloads.set(this.key(payload.scope, payload.jobId), payload);
  }

  async getArchivePayload(
    scope: StudioWorkflowStoreScope,
    jobId: string,
  ): Promise<StudioSourceAcquisitionArchivePayload | null> {
    return this.payloads.get(this.key(scope, jobId)) ?? null;
  }

  async deleteArchivePayload(
    scope: StudioWorkflowStoreScope,
    jobId: string,
  ): Promise<void> {
    this.payloads.delete(this.key(scope, jobId));
  }

  private key(scope: StudioWorkflowStoreScope, jobId: string): string {
    return `${scopeKey(scope)}:${jobId}`;
  }
}

function assertAllowedAcquisitionJobTransition(
  currentStatus: StudioSourceAcquisitionJob["status"],
  nextStatus: StudioSourceAcquisitionJobUpdate["status"],
): void {
  const allowed: Record<
    StudioSourceAcquisitionJob["status"],
    readonly StudioSourceAcquisitionJobUpdate["status"][]
  > = {
    pending: ["running", "failed", "cancelled"],
    running: ["complete", "failed", "cancelled"],
    complete: [],
    failed: [],
    cancelled: [],
  };
  if (!allowed[currentStatus].includes(nextStatus)) {
    throw new KernelLifecycleError({
      reasonCode: "invalid-acquisition-job-transition",
      message: `Cannot transition Studio source acquisition job from ${currentStatus} to ${nextStatus}`,
    });
  }
}

function toSourceAcquisitionJobUpdate(
  body: z.infer<typeof SourceAcquisitionJobUpdateBodySchema>,
): StudioSourceAcquisitionJobUpdate {
  return {
    status: body.status,
    ...(body.startedAt === undefined ? {} : { startedAt: body.startedAt }),
    ...(body.completedAt === undefined
      ? {}
      : { completedAt: body.completedAt }),
    ...(body.totalBytes === undefined ? {} : { totalBytes: body.totalBytes }),
    ...(body.fileCount === undefined ? {} : { fileCount: body.fileCount }),
    ...(body.localWorkspacePath === undefined
      ? {}
      : { localWorkspacePath: body.localWorkspacePath }),
    ...(body.errorMessage === undefined
      ? {}
      : { errorMessage: body.errorMessage }),
  };
}

function createStudioAcquisitionJob(input: {
  readonly kind: "github" | "gitlab" | "archive";
  readonly uri: string;
  readonly label: string;
  readonly ref?: string;
  readonly correlationId: string;
  readonly totalBytes?: number;
  readonly scope: StudioWorkflowStoreScope;
}): StudioSourceAcquisitionJob {
  const createdAt = new Date().toISOString();
  return {
    jobId: `studio-acquisition:${input.kind}:${randomUUID()}`,
    status: "pending",
    scope: input.scope,
    descriptor: {
      kind: input.kind,
      uri: input.uri,
      label: input.label,
      ...(input.ref === undefined ? {} : { ref: input.ref }),
    },
    createdAt,
    ...(input.totalBytes === undefined ? {} : { totalBytes: input.totalBytes }),
    correlationId: input.correlationId,
  };
}

function toLifecyclePlanResponse(
  plan: ProductLifecyclePlan,
): Record<string, unknown> {
  return {
    ...plan,
    productUnitId: plan.productId,
    status: "planned",
  };
}

function toLifecycleRunResponse(
  result: ProductLifecycleResult,
): Record<string, unknown> {
  return {
    ...result,
    productUnitId: result.productId,
    status: lifecycleRunStatus(result.status),
    ...(result.failure?.reasonCode === undefined
      ? {}
      : { failureReasonCode: result.failure.reasonCode }),
  };
}

function toLifecycleSummaryResponse(
  summary: LifecycleRunSummary,
): Record<string, unknown> {
  return {
    ...summary,
    status:
      summary.status === "planned"
        ? "planned"
        : lifecycleRunStatus(summary.status),
  };
}

function lifecycleRunStatus(
  status: ProductLifecycleResult["status"] | LifecycleRunSummary["status"],
): string {
  if (status === "succeeded") {
    return "healthy";
  }
  return status;
}

export function createKernelLifecycleApiHandlers(
  options: KernelLifecycleApiHandlersOptions,
): KernelLifecycleApiHandlers {
  return new KernelLifecycleApiHandlers(options);
}
