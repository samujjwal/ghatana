import { createHash } from 'crypto';
import { getCanonicalExtractors } from '../extractors';
import type { EdgeResolutionRecord, GraphEdge, GraphNode, UnresolvedGraphEdge } from '../graph/types';
import {
  RepositorySnapshotSchema,
  type RepositorySnapshot,
} from '../source-providers/types';
import { SynthesisPipeline, type SynthesisPipelineResult } from '../synthesis/pipeline';
import { z } from 'zod';

/**
 * @doc.type module
 * @doc.purpose TypeScript extractor worker with contract validation, timeout, and version metadata for subprocess execution
 * @doc.layer product
 * @doc.pattern Worker
 * 
 * P1-17: Added subprocess contract validation using Zod schemas.
 * P1-17: Added timeout mechanism for extraction process.
 * P1-17: Added version metadata to response.
 * P1-18: Fixed contract validation with strict typing instead of unknown[].
 */

const EXTRACTOR_VERSION = '1.0.0';
const DEFAULT_TIMEOUT_MS = 300000;

const CanonicalExtractorWorkerRequestSchema = z.object({
  snapshot: RepositorySnapshotSchema,
});

const VersionMetadataSchema = z.object({
  extractorVersion: z.string().min(1),
  timestamp: z.string().datetime(),
  timeoutMs: z.number().int().positive(),
});

const WorkerNodeSchema = z.object({
  id: z.string().min(1),
  type: z.string().min(1),
  name: z.string().min(1),
  filePath: z.string().min(1).optional(),
  content: z.string().nullable().optional(),
  properties: z.record(z.string(), z.unknown()),
  tags: z.array(z.string()),
  sourceRef: z.string().optional(),
  symbolRef: z.string().optional(),
  sourceLocation: z.record(z.string(), z.unknown()).optional(),
  extractorId: z.string().min(1),
  extractorVersion: z.string().min(1),
  confidence: z.number().min(0).max(1),
  provenance: z.string().min(1),
  privacySecurityFlags: z.array(z.string()),
  residualFragmentIds: z.array(z.string()),
  metadata: z.record(z.string(), z.unknown()),
  tenantId: z.string().optional(),
  projectId: z.string().optional(),
  workspaceId: z.string().optional(),
});

const WorkerEdgeSchema = z.object({
  edgeId: z.string().min(1),
  sourceNodeId: z.string().min(1),
  targetNodeId: z.string().min(1),
  relationshipType: z.string().min(1),
  confidence: z.number().min(0).max(1),
  bidirectional: z.boolean(),
  metadata: z.record(z.string(), z.unknown()),
  properties: z.record(z.string(), z.unknown()),
  snapshotId: z.string().optional(),
  versionId: z.string().optional(),
});

const WorkerUnresolvedEdgeSchema = z.object({
  id: z.string().min(1),
  sourceNodeId: z.string().min(1),
  targetRef: z.string().min(1),
  relationshipType: z.string().min(1),
  targetKindHint: z.string().optional(),
  confidence: z.number().min(0).max(1),
  metadata: z.record(z.string(), z.unknown()),
  tenantId: z.string().optional(),
  projectId: z.string().optional(),
  workspaceId: z.string().optional(),
});

const WorkerEdgeResolutionRecordSchema = z.object({
  id: z.string().min(1),
  unresolvedEdgeId: z.string().min(1),
  status: z.string().min(1),
  resolvedTargetId: z.string().optional(),
  candidateIds: z.array(z.string()),
  reviewRequired: z.boolean(),
});

const WorkerResidualIslandSchema = z.object({
  id: z.string().min(1),
  islandType: z.string().min(1),
  summary: z.string(),
  // P0: Added originalSource for round-trip fidelity
  originalSource: z.string().min(1),
  // P0: Added sourceLocation for precise positioning
  sourceLocation: z.object({
    filePath: z.string().min(1),
    startLine: z.number().int().nonnegative(),
    startColumn: z.number().int().nonnegative(),
    endLine: z.number().int().nonnegative(),
    endColumn: z.number().int().nonnegative(),
  }),
  sourceSpan: z.string().min(1).optional(), // Kept for compatibility
  checksum: z.string().min(1), // P0: Made required
  rawFragmentRef: z.string().min(1), // P0: Made required
  reason: z.string().optional(),
  confidence: z.number().min(0).max(1),
  reviewRequired: z.boolean(),
  riskScore: z.number().min(0).max(1).optional(),
  fileCount: z.number().int().nonnegative().optional(),
  metadata: z.record(z.string(), z.string()).optional(),
  tenantId: z.string().optional(),
  projectId: z.string().optional(),
  workspaceId: z.string().optional(),
  snapshotId: z.string().optional(),
});

const WorkerDiagnosticSchema = z.object({
  level: z.enum(['INFO', 'WARNING', 'ERROR']),
  code: z.string().min(1),
  message: z.string().min(1),
  filePath: z.string().optional(),
  line: z.number().int().nonnegative().optional(),
  column: z.number().int().nonnegative().optional(),
});

const WorkerSemanticModelSchema = z.object({
  id: z.string().min(1),
  elementId: z.string().min(1),
  elementType: z.string().min(1),
  name: z.string().min(1),
  qualifiedName: z.string().optional(),
  filePath: z.string().optional(),
  sourceLocation: z.record(z.string(), z.unknown()).optional(),
  properties: z.record(z.string(), z.unknown()).optional(),
  dependencies: z.array(z.string()).optional(),
  dependents: z.array(z.string()).optional(),
  confidence: z.number().min(0).max(1).optional(),
  reviewRequired: z.boolean().optional(),
  reviewReason: z.string().optional(),
  securityFlags: z.array(z.string()).optional(),
  privacyFlags: z.array(z.string()).optional(),
  graphNodeIds: z.array(z.string()).optional(),
  residualIslandIds: z.array(z.string()).optional(),
  sourceRef: z.string().optional(),
  symbolRef: z.string().optional(),
  extractorId: z.string().optional(),
  extractorVersion: z.string().optional(),
  modelVersionId: z.string().optional(),
  syntheticReason: z.string().optional(),
  provenance: z.string().min(1),
  extractedAt: z.string().datetime().optional(),
  snapshotId: z.string().optional(),
  tenantId: z.string().optional(),
  projectId: z.string().optional(),
  workspaceId: z.string().optional(),
  metadata: z.record(z.string(), z.unknown()).optional(),
});

export const ExtractorWorkerRequestSchema = CanonicalExtractorWorkerRequestSchema;

export const ExtractorWorkerResponseSchema = z.object({
  nodes: z.array(WorkerNodeSchema),
  edges: z.array(WorkerEdgeSchema),
  unresolvedEdges: z.array(WorkerUnresolvedEdgeSchema),
  edgeResolutionRecords: z.array(WorkerEdgeResolutionRecordSchema),
  residualIslands: z.array(WorkerResidualIslandSchema),
  semanticModels: z.array(WorkerSemanticModelSchema).default([]),
  diagnostics: z.array(WorkerDiagnosticSchema).default([]),
  versionMetadata: VersionMetadataSchema,
});

export type ExtractorWorkerRequest = z.infer<typeof ExtractorWorkerRequestSchema>;
export type ExtractorWorkerResponse = z.infer<typeof ExtractorWorkerResponseSchema>;
export type WorkerResidualIsland = z.infer<typeof WorkerResidualIslandSchema>;

function buildHash(...segments: readonly string[]): string {
  const hash = createHash('sha256');
  for (const segment of segments) {
    hash.update(segment);
    hash.update('\u0000');
  }
  return hash.digest('hex');
}

export function normalizeExtractorWorkerRequest(input: unknown): RepositorySnapshot {
  return ExtractorWorkerRequestSchema.parse(input).snapshot;
}

function toUnresolvedEdgeId(edge: UnresolvedGraphEdge): string {
  return buildHash(
    edge.sourceId,
    edge.targetRef,
    edge.relationshipType, // P0: Canonical field name 'relationshipType'
    edge.targetKindHint ?? '',
    edge.sourceLocation.filePath,
    String(edge.sourceLocation.startLine),
    String(edge.sourceLocation.startColumn),
    String(edge.sourceLocation.endLine),
    String(edge.sourceLocation.endColumn),
  );
}

function toWorkerNode(node: GraphNode): ExtractorWorkerResponse['nodes'][number] {
  const filePath = node.sourceLocation?.filePath;
  return {
    ...node,
    type: node.type, // P0: Canonical field name 'type'
    id: node.id,
    name: node.label,
    ...(filePath ? { filePath } : {}),
    content: null,
    properties: node.metadata,
    tags: [],
  };
}

function toWorkerEdge(edge: GraphEdge): ExtractorWorkerResponse['edges'][number] {
  return {
    edgeId: edge.id,
    sourceNodeId: edge.sourceId,
    targetNodeId: edge.targetId,
    relationshipType: edge.relationshipType, // P0: Canonical field name 'relationshipType'
    confidence: edge.confidence,
    bidirectional: edge.bidirectional,
    metadata: edge.metadata,
    properties: edge.metadata,
    ...(edge.snapshotId ? { snapshotId: edge.snapshotId } : {}),
    ...(edge.versionId ? { versionId: edge.versionId } : {}),
  };
}

function toWorkerUnresolvedEdge(edge: UnresolvedGraphEdge): ExtractorWorkerResponse['unresolvedEdges'][number] {
  return {
    id: toUnresolvedEdgeId(edge),
    sourceNodeId: edge.sourceId,
    targetRef: edge.targetRef,
    // P0: Use canonical relationshipType
    relationshipType: edge.relationshipType,
    ...(edge.targetKindHint ? { targetKindHint: edge.targetKindHint } : {}),
    confidence: edge.confidence,
    metadata: edge.metadata,
  };
}

function toWorkerEdgeResolutionRecord(
  record: EdgeResolutionRecord,
): ExtractorWorkerResponse['edgeResolutionRecords'][number] {
  const unresolvedEdgeId = toUnresolvedEdgeId(record.unresolvedEdge);
  return {
    id: buildHash(unresolvedEdgeId, record.status, record.resolvedTargetId ?? '', ...record.candidateIds),
    unresolvedEdgeId,
    status: record.status,
    ...(record.resolvedTargetId ? { resolvedTargetId: record.resolvedTargetId } : {}),
    candidateIds: record.candidateIds,
    reviewRequired: record.reviewRequired,
  };
}

function toWorkerResidualIsland(island: SynthesisPipelineResult['residualIslands'][number]): WorkerResidualIsland {
  // P0: Extract original source for round-trip fidelity - fail closed if missing
  const originalSource = 'originalSource' in island && typeof island.originalSource === 'string'
    ? island.originalSource
    : null;
  
  if (originalSource == null || originalSource.trim().length === 0) {
    throw new Error(
      `ResidualIsland '${island.id}' missing required field 'originalSource'. ` +
      'Lossy residual source rejected - full residual source fidelity required for production.'
    );
  }

  // P0: Extract source location - fail closed if missing
  const sourceLocation = 'sourceLocation' in island && island.sourceLocation != null
    ? island.sourceLocation as { filePath: string; startLine: number; startColumn: number; endLine: number; endColumn: number }
    : null;
  
  if (sourceLocation == null) {
    throw new Error(
      `ResidualIsland '${island.id}' missing required field 'sourceLocation'. ` +
      'Lossy residual source rejected - precise source location required for production.'
    );
  }

  // P0: Extract checksum - fail closed if missing
  const checksum = 'checksum' in island && typeof island.checksum === 'string'
    ? island.checksum
    : null;
  
  if (checksum == null || checksum.trim().length === 0) {
    throw new Error(
      `ResidualIsland '${island.id}' missing required field 'checksum'. ` +
      'Lossy residual source rejected - checksum required for production.'
    );
  }

  // P0: Extract raw fragment ref - fail closed if missing
  const rawFragmentRef = 'rawFragmentRef' in island && typeof island.rawFragmentRef === 'string'
    ? island.rawFragmentRef
    : null;
  
  if (rawFragmentRef == null || rawFragmentRef.trim().length === 0) {
    throw new Error(
      `ResidualIsland '${island.id}' missing required field 'rawFragmentRef'. ` +
      'Lossy residual source rejected - raw fragment ref required for production.'
    );
  }

  // P0: Generate source span from source location for compatibility
  const sourceSpan = `${sourceLocation.filePath}:${sourceLocation.startLine}:${sourceLocation.startColumn}-${sourceLocation.endLine}:${sourceLocation.endColumn}`;
  
  return {
    id: island.id,
    islandType: ('islandType' in island && typeof island.islandType === 'string')
      ? island.islandType
      : (('kind' in island && typeof island.kind === 'string') ? island.kind : 'unknown'),
    summary: ('normalizedSummary' in island && typeof island.normalizedSummary === 'string')
      ? island.normalizedSummary
      : (('summary' in island && typeof island.summary === 'string') ? island.summary : ''),
    originalSource,
    sourceLocation,
    sourceSpan,
    checksum,
    rawFragmentRef,
    reason: ('reasonUnmodeled' in island && typeof island.reasonUnmodeled === 'string')
      ? island.reasonUnmodeled
      : (('reason' in island && typeof island.reason === 'string') ? island.reason : undefined),
    confidence: typeof island.confidence === 'number' ? island.confidence : 0.5,
    reviewRequired: typeof island.reviewRequired === 'boolean' ? island.reviewRequired : false,
    riskScore: ('risk' in island && island.risk != null)
      ? ({ low: 0.2, medium: 0.5, high: 0.8, critical: 1.0 }[island.risk as string] ?? undefined)
      : undefined,
    fileCount: 1,
  };
}

function toWorkerSemanticModelFromElement(
  element: import('../model/types').SemanticModelElement,
): ExtractorWorkerResponse['semanticModels'][number] {
  const firstSourceRef = element.sourceRefs[0];
  const sourceRef =
    firstSourceRef == null
      ? undefined
      : typeof firstSourceRef === 'string'
        ? firstSourceRef
        : JSON.stringify(firstSourceRef);

  // P1: Create semantic models from true semantic synthesis output, not every graph node
  return {
    id: element.id,
    elementId: element.id,
    elementType: element.kind,
    name: element.name,
    qualifiedName: element.name, // P1: Use name as qualifiedName for model elements
    filePath: element.provenance.sourcePaths[0] ?? undefined,
    sourceLocation: element.provenance.sourcePaths[0] ? {
      filePath: element.provenance.sourcePaths[0],
      startLine: 0,
      startColumn: 0,
      endLine: 0,
      endColumn: 0,
    } : undefined,
    properties: {},
    dependencies: [],
    dependents: [],
    confidence: element.confidence,
    reviewRequired: element.reviewRequirement?.required ?? false,
    reviewReason: element.reviewRequirement?.reason,
    securityFlags: element.securityFlags,
    privacyFlags: element.privacyFlags,
    graphNodeIds: element.graphNodeIds,
    residualIslandIds: element.residualIslandIds,
    sourceRef,
    symbolRef: undefined,
    extractorId: element.provenance.extractorId,
    extractorVersion: element.provenance.extractorVersion,
    modelVersionId: undefined,
    syntheticReason: element.provenance.kind === 'synthesized' ? 'Synthesized from graph analysis' : undefined,
    provenance: element.provenance.kind.toUpperCase() as any,
    extractedAt: element.provenance.extractedAt,
  };
}

export function serializeExtractionWorkerResponse(
  result: SynthesisPipelineResult,
  timeoutMs: number = DEFAULT_TIMEOUT_MS,
): ExtractorWorkerResponse {
  return ExtractorWorkerResponseSchema.parse({
    nodes: result.graph.nodes.map((node) => toWorkerNode(node)),
    edges: result.graph.edges.map((edge) => toWorkerEdge(edge)),
    unresolvedEdges: result.graph.unresolvedEdges.map((edge) => toWorkerUnresolvedEdge(edge)),
    edgeResolutionRecords: result.graph.edgeResolutionRecords.map((record) => toWorkerEdgeResolutionRecord(record)),
    residualIslands: result.residualIslands.map((island) => toWorkerResidualIsland(island)),
    semanticModels: result.model.elements.map((element) => toWorkerSemanticModelFromElement(element)),
    diagnostics: [
      ...result.warnings.map((warning) => ({
        level: 'WARNING' as const,
        code: `PIPELINE_${warning.phase.toUpperCase()}_WARNING`,
        message: warning.message,
        ...(warning.artifactPath ? { filePath: warning.artifactPath } : {}),
        line: 0,
        column: 0,
      })),
      ...result.errors.map((error) => ({
        level: 'ERROR' as const,
        code: `PIPELINE_${error.phase.toUpperCase()}_ERROR`,
        message: error.message,
        ...(error.artifactPath ? { filePath: error.artifactPath } : {}),
        line: 0,
        column: 0,
      })),
    ],
    versionMetadata: {
      extractorVersion: EXTRACTOR_VERSION,
      timestamp: new Date().toISOString(),
      timeoutMs,
    },
  });
}

export async function runExtractionWorker(
  request: ExtractorWorkerRequest,
  timeoutMs: number = DEFAULT_TIMEOUT_MS,
): Promise<ExtractorWorkerResponse> {
  const normalizedSnapshot = normalizeExtractorWorkerRequest(request);

  const extractionPromise = (async () => {
    const pipeline = new SynthesisPipeline({
      extractors: getCanonicalExtractors(),
      residualConfidenceThreshold: 0.5,
    });

    const result = await pipeline.runFromSnapshot(normalizedSnapshot);
    return serializeExtractionWorkerResponse(result, timeoutMs);
  })();

  let timer: NodeJS.Timeout | undefined;
  const timeoutPromise = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new Error(`Extraction timed out after ${timeoutMs}ms`)), timeoutMs);
  });

  try {
    return await Promise.race([extractionPromise, timeoutPromise]);
  } finally {
    if (timer) {
      clearTimeout(timer);
    }
  }
}

async function main(): Promise<void> {
  const input = await new Promise<string>((resolve, reject) => {
    let data = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', (chunk) => {
      data += chunk;
    });
    process.stdin.on('end', () => resolve(data));
    process.stdin.on('error', reject);
  });

  const payload: unknown = JSON.parse(input);
  const response = await runExtractionWorker(payload);
  process.stdout.write(JSON.stringify(response));
}

if (require.main === module) {
  main().catch((error) => {
    const message = error instanceof Error ? error.message : String(error);
    process.stderr.write(message);
    process.exitCode = 1;
  });
}
