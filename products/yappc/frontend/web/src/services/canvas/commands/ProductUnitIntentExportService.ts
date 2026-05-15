/**
 * ProductUnitIntent export service for YAPPC creator workflows.
 *
 * The frontend creates typed handoff contracts and sends them to backend/provider
 * endpoints. It never writes Kernel registry files directly.
 *
 * @doc.type service
 * @doc.purpose Export YAPPC creator artifacts as ProductUnitIntent and artifact intelligence evidence
 * @doc.layer product
 * @doc.pattern Repository Pattern
 * @doc.security Cookie-based auth, no localStorage tokens
 */

import {
  ArtifactGraphSummarySchema,
  GeneratedChangeSetSummarySchema,
  ProductUnitIntentSchema,
  ResidualIslandReportSchema,
  RiskHotspotReportSchema,
  SemanticArtifactReferenceSchema,
} from '@ghatana/kernel-product-contracts';
import type {
  ArtifactGraphSummary,
  GeneratedChangeSetSummary,
  ProductUnitIntent,
  ResidualIslandReport,
  RiskHotspotReport,
  SemanticArtifactReference,
} from '@ghatana/kernel-product-contracts';
import type {
  PageArtifactDocument,
  PageArtifactGraphNodeKind,
  PageArtifactGraphSnapshot,
} from '@/components/canvas/page/pageArtifactDocument';

export type ProductUnitIntentExportProviderMode = 'bootstrap' | 'platform';

export interface ProductUnitIntentExportScope {
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
}

export interface ProductUnitIntentExportRequest {
  readonly artifacts: readonly PageArtifactDocument[];
  readonly scope: ProductUnitIntentExportScope;
  readonly createdBy: string;
  readonly productUnitName?: string;
  readonly correlationId: string;
  readonly registryProvider: string;
  readonly sourceProvider: string;
  readonly providerMode: ProductUnitIntentExportProviderMode;
}

export interface ProductUnitIntentExportOptions {
  readonly endpoint?: string;
  readonly dataCloudEvidenceEndpoint?: string;
  readonly fetchImpl?: typeof fetch;
  readonly now?: () => string;
  readonly confidenceThreshold?: number;
}

export interface ProductUnitIntentExportResponse {
  readonly intentId: string;
  readonly status: 'accepted' | 'queued' | 'blocked' | 'failed';
  readonly evidenceRef?: string;
  readonly previewRef?: string;
  readonly blockedReasons?: readonly string[];
}

export interface YappcArtifactIntelligenceEvidenceBundle {
  readonly semanticArtifacts: readonly SemanticArtifactReference[];
  readonly graphSummaries: readonly ArtifactGraphSummary[];
  readonly residualIslandReports: readonly ResidualIslandReport[];
  readonly riskHotspotReports: readonly RiskHotspotReport[];
  readonly generatedChangeSets: readonly GeneratedChangeSetSummary[];
}

interface ProductUnitIntentExportPolicy {
  readonly mode: 'preview' | 'apply';
  readonly confidence: number;
  readonly residualIslandCount: number;
  readonly highestRiskLevel: 'low' | 'medium' | 'high' | 'critical';
  readonly blockedReasons: readonly string[];
}

interface EvidencePrivacyMetadata {
  readonly semanticArtifacts: 'internal';
  readonly graphSummaries: 'internal';
  readonly residualIslandReports: 'confidential';
  readonly riskHotspotReports: 'confidential';
  readonly generatedChangeSets: 'internal';
  readonly retentionDays?: number;
}

interface DataCloudEvidencePersistenceResponse {
  readonly evidenceRef?: string;
  readonly evidenceRefs?: readonly string[];
}

const DEFAULT_INTENT_ENDPOINT = '/api/v1/yappc/product-unit-intents';
const EVIDENCE_SOURCE = 'yappc-creator-ui';

function requireNonEmpty(value: string, field: string): string {
  const trimmed = value.trim();
  if (!trimmed) {
    throw new Error(`ProductUnitIntent export requires ${field}`);
  }
  return trimmed;
}

function artifactKindForNode(kind: PageArtifactGraphNodeKind): SemanticArtifactReference['artifactKind'] {
  switch (kind) {
    case 'product':
      return 'service';
    case 'page':
      return 'ui-route';
    case 'component':
      return 'source-file';
    case 'source':
      return 'source-file';
    case 'residual':
      return 'configuration';
  }
}

function firstGraph(artifacts: readonly PageArtifactDocument[]): PageArtifactGraphSnapshot | null {
  return artifacts.find((artifact) => artifact.artifactGraph)?.artifactGraph ?? null;
}

function buildEvidenceId(prefix: string, subject: string): string {
  return `${prefix}:${subject.replace(/[^a-zA-Z0-9:_-]+/g, '-')}`;
}

export function buildYappcArtifactIntelligenceEvidence(
  request: ProductUnitIntentExportRequest,
  options: Pick<ProductUnitIntentExportOptions, 'now'> = {},
): YappcArtifactIntelligenceEvidenceBundle {
  const createdAt = options.now?.() ?? new Date().toISOString();
  const productUnitId = requireNonEmpty(request.scope.projectId, 'scope.projectId');
  const provenanceRefs = request.artifacts.map((artifact) => `yappc:artifact:${artifact.artifactId}`);
  const base = {
    schemaVersion: '1.0.0',
    source: EVIDENCE_SOURCE,
    confidence: 0.9,
    provenanceRefs: provenanceRefs.length > 0 ? provenanceRefs : ['yappc:artifact:unknown'],
    createdAt,
    correlationId: requireNonEmpty(request.correlationId, 'correlationId'),
  } as const;

  const semanticArtifacts = request.artifacts.flatMap((artifact) => {
    const graph = artifact.artifactGraph;
    if (!graph) {
      return [
        SemanticArtifactReferenceSchema.parse({
          ...base,
          evidenceId: buildEvidenceId('semantic-artifact', artifact.artifactId),
          evidenceType: 'semantic-artifact-reference',
          productUnitId,
          artifactId: artifact.artifactId,
          artifactKind: 'ui-route',
          displayName: artifact.documentId,
          artifactRef: `yappc:artifact:${artifact.artifactId}`,
          semanticTags: [artifact.source, artifact.trustLevel],
          riskLevel: artifact.residualIslandIds && artifact.residualIslandIds.length > 0 ? 'medium' : 'low',
        }),
      ];
    }

    return graph.nodes.map((node) =>
      SemanticArtifactReferenceSchema.parse({
        ...base,
        evidenceId: buildEvidenceId('semantic-artifact', node.id),
        evidenceType: 'semantic-artifact-reference',
        productUnitId,
        artifactId: node.id,
        artifactKind: artifactKindForNode(node.kind),
        displayName: node.label,
        artifactRef: `yappc:artifact:${artifact.artifactId}`,
        ...(node.sourceLocation ? { path: node.sourceLocation.filePath } : {}),
        semanticTags: [graph.sourceType, node.kind],
        riskLevel: node.kind === 'residual' ? 'high' : 'low',
      })
    );
  });

  const graphSummaries = request.artifacts
    .filter((artifact): artifact is PageArtifactDocument & { readonly artifactGraph: PageArtifactGraphSnapshot } =>
      artifact.artifactGraph !== undefined
    )
    .map((artifact) =>
      ArtifactGraphSummarySchema.parse({
        ...base,
        evidenceId: buildEvidenceId('artifact-graph', artifact.artifactGraph.graphId),
        evidenceType: 'artifact-graph-summary',
        productUnitId,
        nodeCount: artifact.artifactGraph.nodes.length,
        edgeCount: artifact.artifactGraph.edges.length,
        nodes: artifact.artifactGraph.nodes.map((node) => ({
          artifactId: node.id,
          artifactKind: artifactKindForNode(node.kind),
          label: node.label,
        })),
        edges: artifact.artifactGraph.edges.map((edge) => ({
          fromArtifactId: edge.from,
          toArtifactId: edge.to,
          relationship: edge.kind,
        })),
        rootArtifactIds: artifact.artifactGraph.nodes
          .filter((node) => node.kind === 'product' || node.kind === 'page')
          .map((node) => node.id),
        orphanArtifactIds: artifact.artifactGraph.provenance.residualIslandIds,
      })
    );

  const residualRefs = request.artifacts.flatMap((artifact) => artifact.residualIslandIds ?? []);
  const residualIslandReports = [
    ResidualIslandReportSchema.parse({
      ...base,
      evidenceId: buildEvidenceId('residual-islands', productUnitId),
      evidenceType: 'residual-island-report',
      productUnitId,
      islandCount: residualRefs.length,
      residualArtifactRefs: residualRefs,
      recommendedActions:
        residualRefs.length > 0
          ? ['review residual islands before ProductUnit promotion']
          : ['no residual islands detected'],
    }),
  ];

  const graph = firstGraph(request.artifacts);
  const graphConfidence = graph?.provenance.confidence ?? 0.9;
  const hotspotRisk = residualRefs.length > 0 || graphConfidence < 0.75 ? 'high' : 'low';
  const riskHotspotReports = [
    RiskHotspotReportSchema.parse({
      ...base,
      evidenceId: buildEvidenceId('risk-hotspots', productUnitId),
      evidenceType: 'risk-hotspot-report',
      productUnitId,
      hotspotCount: hotspotRisk === 'high' ? 1 : 0,
      highestRiskLevel: hotspotRisk,
      hotspots:
        hotspotRisk === 'high'
          ? [
              {
                artifactId: residualRefs[0] ?? productUnitId,
                riskLevel: hotspotRisk,
                reason: 'residual artifact intelligence requires operator review before Kernel handoff',
                evidenceRefs: provenanceRefs,
              },
            ]
          : [],
    }),
  ];

  const generatedChangeSets = [
    GeneratedChangeSetSummarySchema.parse({
      ...base,
      evidenceId: buildEvidenceId('generated-changes', productUnitId),
      evidenceType: 'generated-change-set-summary',
      productUnitId,
      changeSetId: `yappc-export:${productUnitId}:${request.correlationId}`,
      changeCount: request.artifacts.length,
      affectedArtifactRefs: request.artifacts.map((artifact) => `yappc:artifact:${artifact.artifactId}`),
      generatedArtifactRefs: graphSummaries.map((summary) => summary.evidenceId),
      validationEvidenceRefs: residualIslandReports.map((report) => report.evidenceId),
    }),
  ];

  return {
    semanticArtifacts,
    graphSummaries,
    residualIslandReports,
    riskHotspotReports,
    generatedChangeSets,
  };
}

export function buildProductUnitIntentFromYappcArtifacts(
  request: ProductUnitIntentExportRequest,
): ProductUnitIntent {
  const productUnitId = requireNonEmpty(request.scope.projectId, 'scope.projectId');
  const artifact = request.artifacts[0];
  if (!artifact) {
    throw new Error('ProductUnitIntent export requires at least one page artifact');
  }
  const policy = evaluateExportPolicy(request);
  const sourceArtifactRefs = request.artifacts.map((pageArtifact) => `yappc:artifact:${pageArtifact.artifactId}`);

  return ProductUnitIntentSchema.parse({
    schemaVersion: '1.0.0',
    intentId: `intent:yappc:${productUnitId}:${request.correlationId}`,
    intentType: 'promote-candidate',
    scope: {
      tenantId: requireNonEmpty(request.scope.tenantId, 'scope.tenantId'),
      workspaceId: requireNonEmpty(request.scope.workspaceId, 'scope.workspaceId'),
      projectId: productUnitId,
    },
    producer: {
      id: 'yappc-creator-ui',
      type: 'yappc',
      correlationId: requireNonEmpty(request.correlationId, 'correlationId'),
    },
    target: {
      registryProvider: requireNonEmpty(request.registryProvider, 'registryProvider'),
      sourceProvider: requireNonEmpty(request.sourceProvider, 'sourceProvider'),
    },
    governanceHints: {
      privacyLevel: 'internal',
      evidencePrivacyClassification: policy.highestRiskLevel === 'high' || policy.highestRiskLevel === 'critical'
        ? 'confidential'
        : 'internal',
      evidenceRequired: true,
      requiresHumanApproval: policy.mode === 'preview' || policy.highestRiskLevel === 'high' || policy.highestRiskLevel === 'critical',
      retentionDays: 365,
    },
    productUnit: {
      id: productUnitId,
      name: request.productUnitName?.trim() || productUnitId,
      kind: 'business-product',
      scope: request.scope,
      owner: request.createdBy,
      surfaces: [
        {
          id: `${productUnitId}-web`,
          type: 'web',
          implementationStatus: artifact.trustLevel === 'TRUSTED_WORKSPACE' ? 'implemented' : 'experimental',
          sourceRef: `yappc:artifact:${artifact.artifactId}`,
        },
      ],
      lifecycleProfile: 'standard-web-product',
      metadata: {
        providerMode: request.providerMode,
        artifactCount: request.artifacts.length,
      },
    },
    requestedLifecycle: {
      profile: 'standard-web-product',
      enableExecution: false,
      phases: ['validate', 'build'],
    },
    provenance: {
      sourceSystem: 'yappc',
      sourceArtifactRefs,
      createdBy: request.createdBy,
      createdAt: new Date().toISOString(),
      evidenceRefs: [
        ...sourceArtifactRefs,
        `yappc:evidence:artifact-intelligence:${productUnitId}:${request.correlationId}`,
      ],
    },
  });
}

function isExportResponse(value: unknown): value is ProductUnitIntentExportResponse {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const record = value as Record<string, unknown>;
  return (
    typeof record['intentId'] === 'string' &&
    (record['status'] === 'accepted' ||
      record['status'] === 'queued' ||
      record['status'] === 'blocked' ||
      record['status'] === 'failed') &&
    (record['evidenceRef'] === undefined || typeof record['evidenceRef'] === 'string') &&
    (record['previewRef'] === undefined || typeof record['previewRef'] === 'string') &&
    (record['blockedReasons'] === undefined ||
      (Array.isArray(record['blockedReasons']) &&
        record['blockedReasons'].every((reason) => typeof reason === 'string')))
  );
}

function parseDataCloudEvidencePersistenceResponse(value: unknown): readonly string[] {
  if (typeof value !== 'object' || value === null) {
    throw new Error('Data Cloud evidence persistence response had an unexpected shape');
  }
  const record = value as DataCloudEvidencePersistenceResponse;
  const evidenceRefs = [
    ...(typeof record.evidenceRef === 'string' ? [record.evidenceRef] : []),
    ...(Array.isArray(record.evidenceRefs)
      ? record.evidenceRefs.filter((ref): ref is string => typeof ref === 'string' && ref.trim().length > 0)
      : []),
  ];
  if (evidenceRefs.length === 0) {
    throw new Error('Data Cloud evidence persistence response did not include evidence refs');
  }
  return [...new Set(evidenceRefs)];
}

async function postJson(
  fetchImpl: typeof fetch,
  endpoint: string,
  body: unknown,
  request: ProductUnitIntentExportRequest,
): Promise<Response> {
  return fetchImpl(endpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Correlation-Id': request.correlationId,
      'X-Ghatana-Tenant-Id': request.scope.tenantId,
      'X-Ghatana-Workspace-Id': request.scope.workspaceId,
      'X-Ghatana-Project-Id': request.scope.projectId,
    },
    credentials: 'include',
    body: JSON.stringify(body),
  });
}

function evaluateExportPolicy(
  request: ProductUnitIntentExportRequest,
  confidenceThreshold = 0.75,
): ProductUnitIntentExportPolicy {
  const graphConfidence = firstGraph(request.artifacts)?.provenance.confidence ?? 0.9;
  const residualIslandCount = request.artifacts.reduce(
    (count, artifact) => count + (artifact.residualIslandIds?.length ?? 0),
    0,
  );
  const highestRiskLevel = residualIslandCount > 0 || graphConfidence < confidenceThreshold ? 'high' : 'low';
  const blockedReasons = [
    ...(graphConfidence < confidenceThreshold ? ['artifact-confidence-below-threshold'] : []),
    ...(residualIslandCount > 0 ? ['residual-island-review-required'] : []),
  ];
  return {
    mode: blockedReasons.length > 0 ? 'preview' : 'apply',
    confidence: graphConfidence,
    residualIslandCount,
    highestRiskLevel,
    blockedReasons,
  };
}

function evidencePrivacyMetadata(policy: ProductUnitIntentExportPolicy): EvidencePrivacyMetadata {
  return {
    semanticArtifacts: 'internal',
    graphSummaries: 'internal',
    residualIslandReports: 'confidential',
    riskHotspotReports: 'confidential',
    generatedChangeSets: 'internal',
    ...(policy.highestRiskLevel === 'high' || policy.highestRiskLevel === 'critical' ? { retentionDays: 365 } : {}),
  };
}

export async function exportProductUnitIntentFromYappcArtifacts(
  request: ProductUnitIntentExportRequest,
  options: ProductUnitIntentExportOptions = {},
): Promise<ProductUnitIntentExportResponse> {
  const fetchImpl = options.fetchImpl ?? fetch;
  const policy = evaluateExportPolicy(request, options.confidenceThreshold);
  const intent = buildProductUnitIntentFromYappcArtifacts(request);
  const evidence = buildYappcArtifactIntelligenceEvidence(request, options);
  let persistedEvidenceRefs: readonly string[] = [];

  if (request.providerMode === 'platform') {
    if (!options.dataCloudEvidenceEndpoint) {
      throw new Error('Platform-mode ProductUnitIntent export requires a Data Cloud evidence endpoint');
    }
    const evidenceResponse = await postJson(fetchImpl, options.dataCloudEvidenceEndpoint, {
      evidence,
      evidenceMetadata: evidencePrivacyMetadata(policy),
      correlationId: request.correlationId,
      scope: request.scope,
    }, request);
    if (!evidenceResponse.ok) {
      const body = await evidenceResponse.text().catch(() => '');
      throw new Error(`Data Cloud evidence persistence failed (HTTP ${evidenceResponse.status}): ${body}`);
    }
    persistedEvidenceRefs = parseDataCloudEvidencePersistenceResponse(await evidenceResponse.json());
  }

  const handoffBody = {
    mode: policy.mode,
    providerMode: request.providerMode,
    intent,
    evidence:
      persistedEvidenceRefs.length > 0
        ? { evidenceRefs: persistedEvidenceRefs }
        : evidence,
    evidenceMetadata: evidencePrivacyMetadata(policy),
    blockedReasons: policy.blockedReasons,
  };

  const response = await postJson(fetchImpl, options.endpoint ?? DEFAULT_INTENT_ENDPOINT, handoffBody, request);
  if (!response.ok) {
    const body = await response.text().catch(() => '');
    throw new Error(`ProductUnitIntent export failed (HTTP ${response.status}): ${body}`);
  }

  const payload: unknown = await response.json();
  if (!isExportResponse(payload)) {
    throw new Error('ProductUnitIntent export response had an unexpected shape');
  }
  return payload;
}
