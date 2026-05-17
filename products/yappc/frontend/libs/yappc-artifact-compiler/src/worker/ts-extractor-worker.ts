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

const SnapshotProviderSchema = z.enum([
  'local-folder',
  'github',
  'gitlab',
  'zip',
  'archive',
  'artifact-registry',
]);

const WorkerFileSchema = z.object({
  relativePath: z.string().min(1),
  absolutePath: z.string().min(1).optional(),
  sizeBytes: z.number().int().nonnegative(),
  lastModifiedAt: z.string().datetime().optional(),
  lastModified: z.string().datetime().optional(),
});

const CanonicalExtractorWorkerRequestSchema = z.object({
  snapshot: RepositorySnapshotSchema,
});

const NestedJavaExtractorWorkerRequestSchema = z.object({
  snapshot: z.object({
    snapshotRef: z.object({
      provider: SnapshotProviderSchema,
      repoId: z.string().min(1),
      commitSha: z.string().optional().nullable(),
      branch: z.string().optional().nullable(),
      capturedAt: z.string().datetime().optional().nullable(),
    }).passthrough(),
    localRootPath: z.string().min(1),
    files: z.array(WorkerFileSchema),
    snapshotAt: z.string().datetime().optional(),
    shallow: z.boolean().optional(),
    diagnostics: z.array(z.unknown()).optional(),
  }).passthrough(),
});

const FlatJavaExtractorWorkerRequestSchema = z.object({
  snapshotId: z.string().min(1),
  provider: SnapshotProviderSchema,
  repoId: z.string().min(1),
  materializedRoot: z.string().min(1),
  files: z.array(WorkerFileSchema),
});

const VersionMetadataSchema = z.object({
  extractorVersion: z.string().min(1),
  timestamp: z.string().datetime(),
  timeoutMs: z.number().int().positive(),
});

const WorkerNodeSchema = z.object({
  id: z.string().min(1),
  kind: z.string().min(1),
  label: z.string().min(1),
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
});

const WorkerEdgeSchema = z.object({
  id: z.string().min(1),
  edgeId: z.string().min(1),
  sourceId: z.string().min(1),
  targetId: z.string().min(1),
  sourceNodeId: z.string().min(1),
  targetNodeId: z.string().min(1),
  kind: z.string().min(1),
  type: z.string().min(1),
  relationshipType: z.string().min(1),
  confidence: z.number().min(0).max(1),
  bidirectional: z.boolean(),
  metadata: z.record(z.string(), z.unknown()),
  properties: z.record(z.string(), z.unknown()),
  source: z.string().min(1),
  target: z.string().min(1),
  snapshotId: z.string().optional(),
  versionId: z.string().optional(),
});

const WorkerUnresolvedEdgeSchema = z.object({
  id: z.string().min(1),
  sourceNodeId: z.string().min(1),
  targetRef: z.string().min(1),
  relationship: z.string().min(1),
  targetKindHint: z.string().optional(),
  confidence: z.number().min(0).max(1),
  metadata: z.record(z.string(), z.unknown()),
});

const WorkerEdgeResolutionRecordSchema = z.object({
  id: z.string().min(1),
  unresolvedEdgeId: z.string().min(1),
  status: z.string().min(1),
  resolvedTargetId: z.string().optional(),
  candidateIds: z.array(z.string()),
  reviewRequired: z.boolean(),
});

const WorkerDiagnosticSchema = z.object({
  level: z.enum(['INFO', 'WARNING', 'ERROR']),
  code: z.string().min(1),
  message: z.string().min(1),
  filePath: z.string().optional(),
  line: z.number().int().nonnegative().optional(),
  column: z.number().int().nonnegative().optional(),
});

export const ExtractorWorkerRequestSchema = z.union([
  CanonicalExtractorWorkerRequestSchema,
  NestedJavaExtractorWorkerRequestSchema,
  FlatJavaExtractorWorkerRequestSchema,
]);

export const ExtractorWorkerResponseSchema = z.object({
  nodes: z.array(WorkerNodeSchema),
  edges: z.array(WorkerEdgeSchema),
  unresolvedEdges: z.array(WorkerUnresolvedEdgeSchema),
  edgeResolutionRecords: z.array(WorkerEdgeResolutionRecordSchema),
  residualIslandIds: z.array(z.string()),
  diagnostics: z.array(WorkerDiagnosticSchema).default([]),
  versionMetadata: VersionMetadataSchema,
});

export type ExtractorWorkerRequest = z.infer<typeof ExtractorWorkerRequestSchema>;
export type ExtractorWorkerResponse = z.infer<typeof ExtractorWorkerResponseSchema>;

function buildHash(...segments: readonly string[]): string {
  const hash = createHash('sha256');
  for (const segment of segments) {
    hash.update(segment);
    hash.update('\u0000');
  }
  return hash.digest('hex');
}

function normalizeTimestamp(file: z.infer<typeof WorkerFileSchema>): string {
  return file.lastModifiedAt ?? file.lastModified ?? new Date().toISOString();
}

function toCanonicalSnapshotFile(file: z.infer<typeof WorkerFileSchema>) {
  return {
    relativePath: file.relativePath,
    ...(file.absolutePath ? { absolutePath: file.absolutePath, materialized: true } : { materialized: false }),
    sizeBytes: file.sizeBytes,
    lastModifiedAt: normalizeTimestamp(file),
  };
}

export function normalizeExtractorWorkerRequest(input: unknown): RepositorySnapshot {
  const parsed = ExtractorWorkerRequestSchema.parse(input);

  if ('snapshotId' in parsed) {
    return RepositorySnapshotSchema.parse({
      snapshotRef: {
        provider: parsed.provider,
        repoId: parsed.repoId,
      },
      localRootPath: parsed.materializedRoot,
      files: parsed.files.map((file) => toCanonicalSnapshotFile(file)),
      snapshotAt: new Date().toISOString(),
      shallow: false,
      diagnostics: [],
    });
  }

  const canonical = RepositorySnapshotSchema.safeParse(parsed.snapshot);
  if (canonical.success) {
    return canonical.data;
  }

  const nested = NestedJavaExtractorWorkerRequestSchema.parse(parsed).snapshot;
  return RepositorySnapshotSchema.parse({
    snapshotRef: {
      provider: nested.snapshotRef.provider,
      repoId: nested.snapshotRef.repoId,
      ...(nested.snapshotRef.commitSha ? { commitSha: nested.snapshotRef.commitSha } : {}),
      ...(nested.snapshotRef.branch ? { branch: nested.snapshotRef.branch } : {}),
    },
    localRootPath: nested.localRootPath,
    files: nested.files.map((file) => toCanonicalSnapshotFile(file)),
    snapshotAt: nested.snapshotAt ?? nested.snapshotRef.capturedAt ?? new Date().toISOString(),
    shallow: nested.shallow ?? false,
    diagnostics: [],
  });
}

function toUnresolvedEdgeId(edge: UnresolvedGraphEdge): string {
  return buildHash(
    edge.sourceId,
    edge.targetRef,
    edge.relationship,
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
    type: node.kind,
    name: node.label,
    ...(filePath ? { filePath } : {}),
    content: null,
    properties: node.metadata,
    tags: [],
  };
}

function toWorkerEdge(edge: GraphEdge): ExtractorWorkerResponse['edges'][number] {
  return {
    ...edge,
    edgeId: edge.id,
    sourceNodeId: edge.sourceId,
    targetNodeId: edge.targetId,
    relationshipType: edge.kind,
    properties: edge.metadata,
    source: edge.sourceId,
    target: edge.targetId,
    type: edge.kind,
  };
}

function toWorkerUnresolvedEdge(edge: UnresolvedGraphEdge): ExtractorWorkerResponse['unresolvedEdges'][number] {
  return {
    id: toUnresolvedEdgeId(edge),
    sourceNodeId: edge.sourceId,
    targetRef: edge.targetRef,
    relationship: edge.relationship,
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

export function serializeExtractionWorkerResponse(
  result: SynthesisPipelineResult,
  timeoutMs: number = DEFAULT_TIMEOUT_MS,
): ExtractorWorkerResponse {
  return ExtractorWorkerResponseSchema.parse({
    nodes: result.graph.nodes.map((node) => toWorkerNode(node)),
    edges: result.graph.edges.map((edge) => toWorkerEdge(edge)),
    unresolvedEdges: result.graph.unresolvedEdges.map((edge) => toWorkerUnresolvedEdge(edge)),
    edgeResolutionRecords: result.graph.edgeResolutionRecords.map((record) => toWorkerEdgeResolutionRecord(record)),
    residualIslandIds: result.residualIslands.map((island) => island.id),
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
