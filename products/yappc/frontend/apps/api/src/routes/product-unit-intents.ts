/**
 * ProductUnitIntent handoff routes.
 *
 * @doc.type router
 * @doc.purpose Accept YAPPC ProductUnitIntent handoffs without mutating Kernel registry files
 * @doc.layer product
 * @doc.pattern REST API
 */

import type { FastifyInstance, FastifyRequest } from 'fastify';
import {
  ArtifactGraphSummarySchema,
  GeneratedChangeSetSummarySchema,
  ProductUnitIntentSchema,
  ResidualIslandReportSchema,
  RiskHotspotReportSchema,
  SemanticArtifactReferenceSchema,
  type ProductUnitIntent,
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

export default async function productUnitIntentRoutes(fastify: FastifyInstance): Promise<void> {
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
      if (providerMode === 'platform' && !evidence.evidenceRefs.some(isDataCloudEvidenceRef)) {
        return reply.status(409).send({
          intentId: intent.intentId,
          status: 'blocked',
          blockedReasons: ['platform-mode-requires-data-cloud-evidence-ref'],
        } satisfies ProductUnitIntentResponse);
      }

      const requestedMode = request.body?.mode ?? 'preview';
      if (requestedMode === 'apply' && !hasApplyPermission(request)) {
        return reply.status(403).send({
          intentId: intent.intentId,
          status: 'blocked',
          evidenceRef: evidence.evidenceRefs[0],
          previewRef: buildPreviewRef(intent),
          blockedReasons: ['apply-requires-explicit-permission'],
        } satisfies ProductUnitIntentResponse);
      }

      const status: ProductUnitIntentStatus = requestedMode === 'apply' ? 'queued' : 'accepted';
      return reply.status(requestedMode === 'apply' ? 202 : 200).send({
        intentId: intent.intentId,
        status,
        evidenceRef: evidence.evidenceRefs[0],
        previewRef: buildPreviewRef(intent),
        blockedReasons: [],
      } satisfies ProductUnitIntentResponse);
    },
  );
}

function authenticatedActor(request: FastifyRequest): string | null {
  return request.user?.userId ?? headerValue(request, 'x-user-id');
}

function validateScope(intent: ProductUnitIntent, request: FastifyRequest): string | null {
  const tenantId = headerValue(request, 'x-tenant-id') ?? headerValue(request, 'x-ghatana-tenant-id');
  const workspaceId = headerValue(request, 'x-workspace-id') ?? headerValue(request, 'x-ghatana-workspace-id');
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
