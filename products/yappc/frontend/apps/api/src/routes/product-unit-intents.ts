/**
 * ProductUnitIntent handoff routes.
 *
 * @doc.type router
 * @doc.purpose Accept YAPPC ProductUnitIntent handoffs and wire to Kernel application service
 * @doc.layer product
 * @doc.pattern REST API
 */

import type { FastifyInstance, FastifyRequest } from 'fastify';
import {
  ArtifactGraphSummarySchema,
  GeneratedChangeSetSummarySchema,
  ProductUnitIntentApplicationResultSchema,
  ProductUnitIntentSchema,
  ResidualIslandReportSchema,
  RiskHotspotReportSchema,
  SemanticArtifactReferenceSchema,
  type ProductUnitIntent,
  type ProductUnitIntentApplicationReasonCode,
  type ProductUnitIntentApplicationResult,
  type ProductUnitIntentApplicationStatus,
  type ProductUnitIntentApplyMode,
  type ProductUnitIntentStatus,
} from '@ghatana/kernel-product-contracts';

interface ProductUnitIntentRequestBody {
  readonly intent?: unknown;
  readonly evidence?: unknown;
  readonly mode?: ProductUnitIntentApplyMode;
  readonly providerMode?: 'bootstrap' | 'platform';
}

interface ProductUnitIntentResponse {
  readonly intentId: string;
  readonly status: ProductUnitIntentStatus;
  readonly evidenceRef?: string;
  readonly previewRef?: string;
  readonly blockedReasons: readonly string[];
  readonly providerMode?: 'bootstrap' | 'platform';
}

interface EvidenceBundle {
  readonly evidenceRefs?: readonly string[];
  readonly semanticArtifacts?: readonly unknown[];
  readonly graphSummaries?: readonly unknown[];
  readonly residualIslandReports?: readonly unknown[];
  readonly riskHotspotReports?: readonly unknown[];
  readonly generatedChangeSets?: readonly unknown[];
}

interface NormalizedEvidenceBundle {
  readonly evidenceRefs: readonly string[];
  readonly issues: readonly string[];
}

interface EvidencePersistenceResult {
  readonly success: boolean;
  readonly refs: readonly string[];
  readonly error?: string;
}

interface DataCloudClientConfig {
  readonly baseUrl: string;
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
  readonly authToken: string;
}

interface ApplyProductUnitIntentOptions {
  readonly mode: 'bootstrap' | 'platform';
  readonly allowWrite: boolean;
  readonly evidenceRefs: readonly string[];
  readonly correlationId: string;
  readonly kernelBaseUrl?: string;
  readonly kernelAuthToken?: string;
}

export default async function productUnitIntentRoutes(fastify: FastifyInstance): Promise<void> {
  const dataCloudBaseUrl = process.env.DATACLOUD_PROVIDER_BASE_URL ?? 'http://localhost:8080';
  const dataCloudTenantId = process.env.DATACLOUD_TENANT_ID ?? 'default-tenant';
  const dataCloudWorkspaceId = process.env.DATACLOUD_WORKSPACE_ID ?? 'default-workspace';
  const dataCloudProjectId = process.env.DATACLOUD_PROJECT_ID ?? 'default-project';
  const dataCloudAuthToken = process.env.DATACLOUD_AUTH_TOKEN;

  const kernelBaseUrl = process.env.KERNEL_LIFECYCLE_BASE_URL;
  const kernelAuthToken = process.env.KERNEL_LIFECYCLE_AUTH_TOKEN;

  fastify.post<{ Body: ProductUnitIntentRequestBody }>(
    '/yappc/product-unit-intents',
    async (request, reply) => {
      const actor = authenticatedActor(request);
      if (actor === null) {
        return reply.status(401).send({ error: 'Authentication required' });
      }

      const parsedIntent = ProductUnitIntentSchema.safeParse(request.body?.intent);
      if (!parsedIntent.success) {
        return reply.status(400).send({
          error: 'Invalid ProductUnitIntent',
          issues: parsedIntent.error.issues.map((issue) => ({
            path: issue.path.join('.'),
            message: issue.message,
          })),
        });
      }

      const intent = parsedIntent.data;
      const scopeError = validateScope(intent, request);
      if (scopeError !== null) {
        return reply.status(403).send({ error: scopeError });
      }

      const evidence = normalizeEvidenceBundle(request.body?.evidence);
      if (evidence.issues.length > 0) {
        return reply.status(400).send({
          error: 'Invalid artifact intelligence evidence',
          issues: evidence.issues,
          blockedReasons: ['invalid-evidence'],
        });
      }

      if (intent.intentType === 'promote-candidate' && evidence.evidenceRefs.length === 0) {
        return reply.status(400).send({
          error: 'promote-candidate requires evidence refs',
          blockedReasons: ['missing-evidence'],
        });
      }

      const providerMode = request.body?.providerMode ?? 'bootstrap';
      let evidenceRefsForKernel = evidence.evidenceRefs;

      if (providerMode === 'platform') {
        const hasDataCloudEvidenceRef = evidenceRefsForKernel.some(isDataCloudEvidenceRef);

        if (!hasDataCloudEvidenceRef && dataCloudAuthToken !== undefined && dataCloudAuthToken.trim().length > 0) {
          const persistedEvidenceRefs = await persistEvidenceToDataCloud(
            request.body?.evidence,
            {
              baseUrl: dataCloudBaseUrl,
              tenantId: dataCloudTenantId,
              workspaceId: dataCloudWorkspaceId,
              projectId: dataCloudProjectId,
              authToken: dataCloudAuthToken,
            },
            intent.intentId,
            intent.scope.tenantId,
          );

          if (!persistedEvidenceRefs.success) {
            return reply.status(503).send({
              intentId: intent.intentId,
              status: 'blocked',
              blockedReasons: [`platform-mode-evidence-persistence-failed: ${persistedEvidenceRefs.error}`],
            } satisfies ProductUnitIntentResponse);
          }

          evidenceRefsForKernel = persistedEvidenceRefs.refs;
        }

        if (!evidenceRefsForKernel.some(isDataCloudEvidenceRef)) {
          return reply.status(409).send({
            intentId: intent.intentId,
            status: 'blocked',
            blockedReasons: ['platform-mode-requires-data-cloud-evidence-ref'],
          } satisfies ProductUnitIntentResponse);
        }

        if (dataCloudAuthToken === undefined || dataCloudAuthToken.trim().length === 0) {
          return reply.status(503).send({
            intentId: intent.intentId,
            status: 'blocked',
            blockedReasons: ['platform-mode-requires-data-cloud-provider-client'],
          } satisfies ProductUnitIntentResponse);
        }
      }

      const requestedMode = request.body?.mode ?? 'preview';
      const requestedAction = requestedMode === 'apply' ? 'apply' : 'preview';
      if (requestedAction === 'apply' && !hasApplyPermission(request)) {
        return reply.status(403).send({
          intentId: intent.intentId,
          status: 'blocked',
          evidenceRef: evidence.evidenceRefs[0],
          previewRef: buildPreviewRef(intent),
          blockedReasons: ['apply-requires-explicit-permission'],
        } satisfies ProductUnitIntentResponse);
      }

      const applicationResult = await applyProductUnitIntent(intent, {
        mode: providerMode,
        allowWrite: requestedAction === 'apply',
        evidenceRefs: evidenceRefsForKernel,
        correlationId: request.id,
        kernelBaseUrl,
        kernelAuthToken,
      });

      const kernelResult = ProductUnitIntentApplicationResultSchema.parse(applicationResult);
      const status = mapKernelStatusToYappcStatus(kernelResult.status);

      return reply.status(kernelResult.status === 'applied' ? 202 : 200).send({
        intentId: kernelResult.intentId,
        status,
        evidenceRef: kernelResult.provenanceRefs[0],
        previewRef: kernelResult.status === 'previewed' ? buildPreviewRef(intent) : undefined,
        blockedReasons: kernelResult.blockedReasons,
        providerMode,
      } satisfies ProductUnitIntentResponse);
    },
  );
}

async function applyProductUnitIntent(
  intent: ProductUnitIntent,
  options: ApplyProductUnitIntentOptions,
): Promise<ProductUnitIntentApplicationResult> {
  const correlationId = options.correlationId;
  if (options.kernelBaseUrl === undefined || options.kernelBaseUrl.trim().length === 0) {
    return buildFallbackApplicationResult(intent, options, correlationId, []);
  }

  const endpoint = new URL('/api/v1/kernel/lifecycle/product-unit-intents', options.kernelBaseUrl);
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      ...(options.kernelAuthToken ? { authorization: `Bearer ${options.kernelAuthToken}` } : {}),
    },
    body: JSON.stringify({
      intent,
      providerMode: options.mode,
      requestedAction: options.allowWrite ? 'apply' : 'preview',
      evidenceRefs: options.evidenceRefs,
      correlationId,
    }),
  });

  if (!response.ok) {
    return buildFallbackApplicationResult(intent, options, correlationId, [`kernel-service-http-${response.status}`]);
  }

  const payload = (await response.json()) as unknown;
  const parsed = ProductUnitIntentApplicationResultSchema.safeParse(payload);
  if (!parsed.success) {
    return buildFallbackApplicationResult(intent, options, correlationId, ['kernel-service-response-invalid']);
  }

  return parsed.data;
}

function buildFallbackApplicationResult(
  intent: ProductUnitIntent,
  options: ApplyProductUnitIntentOptions,
  correlationId: string,
  blockedReasons: readonly string[],
): ProductUnitIntentApplicationResult {
  const status: ProductUnitIntentApplicationStatus = blockedReasons.length > 0
    ? 'blocked'
    : options.allowWrite
      ? 'applied'
      : 'previewed';

  return {
    schemaVersion: '1.0.0',
    intentId: intent.intentId,
    status,
    productUnitId: intent.productUnit.id,
    providerMode: options.mode,
    registryProviderId: intent.target.registryProvider,
    sourceProviderId: intent.target.sourceProvider,
    ...(status === 'previewed' ? { previewRef: buildPreviewRef(intent) } : {}),
    ...(status === 'applied' ? { applicationRef: `kernel://product-unit-intents/${encodeURIComponent(intent.intentId)}` } : {}),
    lifecycleEventRefs: [],
    provenanceRefs: options.evidenceRefs,
    runtimeTruthRefs: [],
    blockedReasons: blockedReasons as readonly ProductUnitIntentApplicationReasonCode[],
    errors: [],
    correlationId,
    appliedAt: new Date().toISOString(),
  };
}

function mapKernelStatusToYappcStatus(
  kernelStatus: ProductUnitIntentApplicationStatus,
): ProductUnitIntentStatus {
  switch (kernelStatus) {
    case 'applied':
      return 'queued';
    case 'previewed':
      return 'accepted';
    case 'blocked':
      return 'blocked';
    case 'failed':
      return 'failed';
    default:
      return 'accepted';
  }
}

function authenticatedActor(request: FastifyRequest): string | null {
  return request.user?.userId ?? headerValue(request, 'x-user-id');
}

function validateScope(intent: ProductUnitIntent, request: FastifyRequest): string | null {
  const tenantId = headerValue(request, 'x-tenant-id') ?? headerValue(request, 'x-ghatana-tenant-id');
  const workspaceId =
    headerValue(request, 'x-workspace-id') ?? headerValue(request, 'x-ghatana-workspace-id');
  const projectId = headerValue(request, 'x-project-id') ?? headerValue(request, 'x-ghatana-project-id');
  if (tenantId !== null && tenantId !== intent.scope.tenantId) {
    return 'tenant scope mismatch';
  }
  if (workspaceId !== null && workspaceId !== intent.scope.workspaceId) {
    return 'workspace scope mismatch';
  }
  if (projectId !== null && projectId !== intent.scope.projectId) {
    return 'project scope mismatch';
  }
  return null;
}

function normalizeEvidenceBundle(value: unknown): NormalizedEvidenceBundle {
  if (!isRecord(value)) {
    return { evidenceRefs: [], issues: [] };
  }
  const bundle = value as EvidenceBundle;
  const issues: string[] = [];
  const schemaBackedRefs = [
    ...extractSchemaRefs('semanticArtifacts', bundle.semanticArtifacts, SemanticArtifactReferenceSchema, issues),
    ...extractSchemaRefs('graphSummaries', bundle.graphSummaries, ArtifactGraphSummarySchema, issues),
    ...extractSchemaRefs('residualIslandReports', bundle.residualIslandReports, ResidualIslandReportSchema, issues),
    ...extractSchemaRefs('riskHotspotReports', bundle.riskHotspotReports, RiskHotspotReportSchema, issues),
    ...extractSchemaRefs('generatedChangeSets', bundle.generatedChangeSets, GeneratedChangeSetSummarySchema, issues),
  ];
  return {
    evidenceRefs: uniqueRefs([
      ...(Array.isArray(bundle.evidenceRefs)
        ? bundle.evidenceRefs.filter((ref): ref is string => typeof ref === 'string' && ref.trim().length > 0)
        : []),
      ...schemaBackedRefs,
    ]),
    issues,
  };
}

function extractSchemaRefs<T extends { readonly evidenceId: string }>(
  field: keyof EvidenceBundle,
  values: readonly unknown[] | undefined,
  schema: { safeParse: (value: unknown) => { success: true; data: T } | { success: false } },
  issues: string[],
): readonly string[] {
  if (values === undefined) {
    return [];
  }
  if (!Array.isArray(values)) {
    issues.push(`${String(field)} must be an array`);
    return [];
  }
  return values.flatMap((value, index) => {
    const parsed = schema.safeParse(value);
    if (!parsed.success) {
      issues.push(`${String(field)}.${index} did not match its artifact intelligence schema`);
      return [];
    }
    return [parsed.data.evidenceId];
  });
}

function uniqueRefs(refs: readonly string[]): readonly string[] {
  return [...new Set(refs.map((ref) => ref.trim()).filter((ref) => ref.length > 0))];
}

function isDataCloudEvidenceRef(ref: string): boolean {
  return ref.startsWith('datacloud://') || ref.startsWith('datacloud:');
}

function hasApplyPermission(request: FastifyRequest): boolean {
  const explicit = headerValue(request, 'x-yappc-intent-apply');
  if (explicit === 'true') {
    return true;
  }
  const role = request.user?.role ?? headerValue(request, 'x-user-role');
  return role === 'ADMIN' || role === 'OWNER';
}

function buildPreviewRef(intent: ProductUnitIntent): string {
  return `yappc://product-unit-intents/${encodeURIComponent(intent.intentId)}/preview`;
}

function headerValue(request: FastifyRequest, name: string): string | null {
  const value = request.headers[name];
  if (Array.isArray(value)) {
    return value[0] ?? null;
  }
  return typeof value === 'string' && value.trim().length > 0 ? value : null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

async function persistEvidenceToDataCloud(
  evidenceBundle: unknown,
  dataCloudClient: DataCloudClientConfig,
  intentId: string,
  tenantId: string,
): Promise<EvidencePersistenceResult> {
  if (!isRecord(evidenceBundle)) {
    return { success: true, refs: [] };
  }

  const bundle = evidenceBundle as EvidenceBundle;
  const refs: string[] = [];
  const correlationId = `yappc-intent-${intentId}`;

  try {
    if (Array.isArray(bundle.semanticArtifacts)) {
      for (const artifact of bundle.semanticArtifacts) {
        const parsed = SemanticArtifactReferenceSchema.safeParse(artifact);
        if (parsed.success) {
          const ref = await postDataCloudMemoryRecord(dataCloudClient, {
            memoryId: `artifact-${parsed.data.evidenceId}`,
            contentRef: parsed.data.evidenceId,
            productUnitId: parsed.data.productUnitId,
            tenantId,
            correlationId,
          });
          if (ref !== null) {
            refs.push(`datacloud://memory/${ref}`);
          }
        }
      }
    }

    if (Array.isArray(bundle.graphSummaries)) {
      for (const summary of bundle.graphSummaries) {
        const parsed = ArtifactGraphSummarySchema.safeParse(summary);
        if (parsed.success) {
          const ref = await postDataCloudMemoryRecord(dataCloudClient, {
            memoryId: `graph-${parsed.data.evidenceId}`,
            contentRef: parsed.data.evidenceId,
            productUnitId: parsed.data.productUnitId,
            tenantId,
            correlationId,
          });
          if (ref !== null) {
            refs.push(`datacloud://memory/${ref}`);
          }
        }
      }
    }

    if (Array.isArray(bundle.residualIslandReports)) {
      for (const report of bundle.residualIslandReports) {
        const parsed = ResidualIslandReportSchema.safeParse(report);
        if (parsed.success) {
          const ref = await postDataCloudMemoryRecord(dataCloudClient, {
            memoryId: `island-${parsed.data.evidenceId}`,
            contentRef: parsed.data.evidenceId,
            productUnitId: parsed.data.productUnitId,
            tenantId,
            correlationId,
          });
          if (ref !== null) {
            refs.push(`datacloud://memory/${ref}`);
          }
        }
      }
    }

    if (Array.isArray(bundle.riskHotspotReports)) {
      for (const report of bundle.riskHotspotReports) {
        const parsed = RiskHotspotReportSchema.safeParse(report);
        if (parsed.success) {
          const ref = await postDataCloudMemoryRecord(dataCloudClient, {
            memoryId: `risk-${parsed.data.evidenceId}`,
            contentRef: parsed.data.evidenceId,
            productUnitId: parsed.data.productUnitId,
            tenantId,
            correlationId,
          });
          if (ref !== null) {
            refs.push(`datacloud://memory/${ref}`);
          }
        }
      }
    }

    if (Array.isArray(bundle.generatedChangeSets)) {
      for (const changeSet of bundle.generatedChangeSets) {
        const parsed = GeneratedChangeSetSummarySchema.safeParse(changeSet);
        if (parsed.success) {
          const ref = await postDataCloudMemoryRecord(dataCloudClient, {
            memoryId: `changeset-${parsed.data.evidenceId}`,
            contentRef: parsed.data.evidenceId,
            productUnitId: parsed.data.productUnitId,
            tenantId,
            correlationId,
          });
          if (ref !== null) {
            refs.push(`datacloud://memory/${ref}`);
          }
        }
      }
    }

    return { success: true, refs };
  } catch (error) {
    return {
      success: false,
      refs: [],
      error: error instanceof Error ? error.message : String(error),
    };
  }
}

async function postDataCloudMemoryRecord(
  dataCloudClient: DataCloudClientConfig,
  payload: Record<string, unknown>,
): Promise<string | null> {
  const endpoint = new URL('/api/v1/kernel/providers/memory', dataCloudClient.baseUrl);
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      authorization: `Bearer ${dataCloudClient.authToken}`,
      'x-tenant-id': dataCloudClient.tenantId,
      'x-workspace-id': dataCloudClient.workspaceId,
      'x-project-id': dataCloudClient.projectId,
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    return null;
  }

  const body = (await response.json()) as { ref?: unknown };
  return typeof body.ref === 'string' ? body.ref : null;
}
