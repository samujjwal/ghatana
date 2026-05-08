/**
 * Artifact Graph Merge Review Service
 *
 * Runs explicit graph-wide merge review before imported/decompiled graph snapshots
 * are treated as safe product graph handoff material.
 *
 * @doc.type service
 * @doc.purpose Review and retry artifact graph semantic merges from PageDesigner imports
 * @doc.layer product
 * @doc.pattern Repository Pattern
 * @doc.security Cookie-based auth, no localStorage tokens
 */

import type { PageArtifactGraphSnapshot } from '@/components/canvas/page/pageArtifactDocument';

export type ArtifactGraphMergeResolutionStrategy = 'merge' | 'union' | 'right-wins' | 'left-wins';

export interface ArtifactGraphMergeReviewRequest {
  readonly productId: string;
  readonly tenantId: string;
  readonly baseModel: Record<string, unknown>;
  readonly leftModel: Record<string, unknown>;
  readonly rightModel: Record<string, unknown>;
  readonly resolutionStrategy: ArtifactGraphMergeResolutionStrategy;
}

export interface ArtifactGraphMergeReviewResult {
  readonly success: boolean;
  readonly operation: 'merge';
  readonly mergedModel: Record<string, unknown>;
  readonly conflicts: readonly unknown[];
  readonly fieldProvenance: Record<string, unknown>;
  readonly conflictCount: number;
  readonly message: string;
}

interface ArtifactGraphMergeReviewResponse {
  readonly success: true;
  readonly operation: 'merge';
  readonly result: {
    readonly mergedModel: Record<string, unknown>;
    readonly conflicts: readonly unknown[];
    readonly fieldProvenance: Record<string, unknown>;
    readonly conflictCount: number;
  };
  readonly message: string;
}

const MERGE_ENDPOINT = '/api/v1/yappc/artifact/graph/merge';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function toSnapshotModel(snapshot: PageArtifactGraphSnapshot): Record<string, unknown> {
  return {
    graphId: snapshot.graphId,
    projectId: snapshot.projectId,
    sourceType: snapshot.sourceType,
    source: snapshot.source,
    importedAt: snapshot.importedAt,
    nodeIds: snapshot.nodes.map((node) => node.id),
    edgeIds: snapshot.edges.map((edge) => edge.id),
    nodesById: Object.fromEntries(snapshot.nodes.map((node) => [node.id, node])),
    edgesById: Object.fromEntries(snapshot.edges.map((edge) => [edge.id, edge])),
    residualIslandIds: snapshot.provenance.residualIslandIds,
    confidence: snapshot.provenance.confidence,
    compiler: snapshot.provenance.compiler,
  };
}

export function buildArtifactGraphMergeReviewRequest(
  snapshot: PageArtifactGraphSnapshot,
  tenantId: string,
  options: {
    readonly baseModel?: Record<string, unknown>;
    readonly currentModel?: Record<string, unknown>;
    readonly resolutionStrategy?: ArtifactGraphMergeResolutionStrategy;
  } = {},
): ArtifactGraphMergeReviewRequest {
  const incomingModel = toSnapshotModel(snapshot);
  return {
    productId: snapshot.graphId,
    tenantId,
    baseModel: options.baseModel ?? {
      graphId: snapshot.graphId,
      nodeIds: [],
      edgeIds: [],
      residualIslandIds: [],
    },
    leftModel: options.currentModel ?? {
      graphId: snapshot.graphId,
      nodeIds: [],
      edgeIds: [],
      residualIslandIds: [],
    },
    rightModel: incomingModel,
    resolutionStrategy: options.resolutionStrategy ?? 'merge',
  };
}

function isArtifactGraphMergeReviewResponse(value: unknown): value is ArtifactGraphMergeReviewResponse {
  if (!isRecord(value)) {
    return false;
  }

  const result = value.result;
  return (
    value.success === true &&
    value.operation === 'merge' &&
    typeof value.message === 'string' &&
    isRecord(result) &&
    isRecord(result.mergedModel) &&
    Array.isArray(result.conflicts) &&
    isRecord(result.fieldProvenance) &&
    typeof result.conflictCount === 'number'
  );
}

export async function runArtifactGraphMergeReview(
  request: ArtifactGraphMergeReviewRequest,
): Promise<ArtifactGraphMergeReviewResult> {
  if (!request.productId || !request.tenantId) {
    throw new Error('runArtifactGraphMergeReview: productId and tenantId are required');
  }

  let response: Response;
  try {
    response = await fetch(MERGE_ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify(request),
    });
  } catch (networkError) {
    throw new Error(
      `Artifact graph merge review failed due to a network error: ${
        networkError instanceof Error ? networkError.message : String(networkError)
      }`,
    );
  }

  if (!response.ok) {
    const body = await response.text().catch(() => '');
    throw new Error(`Artifact graph merge review failed (HTTP ${response.status}): ${body}`);
  }

  const payload: unknown = await response.json();
  if (!isArtifactGraphMergeReviewResponse(payload)) {
    throw new Error('Artifact graph merge review response had an unexpected shape');
  }

  return {
    success: true,
    operation: 'merge',
    mergedModel: payload.result.mergedModel,
    conflicts: payload.result.conflicts,
    fieldProvenance: payload.result.fieldProvenance,
    conflictCount: payload.result.conflictCount,
    message: payload.message,
  };
}
