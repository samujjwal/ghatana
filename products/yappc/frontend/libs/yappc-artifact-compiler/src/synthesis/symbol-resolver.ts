/**
 * @fileoverview Phase-2 symbol resolver — resolves UnresolvedGraphEdges to real node IDs.
 *
 * The resolver builds a multi-key symbol index from all GraphNodes (by symbolRef,
 * by label, by relativePath) and then attempts to match each UnresolvedGraphEdge.targetRef
 * to a real node ID. Produces:
 *   - Resolved GraphEdges (targetId is a real node ID)
 *   - EdgeResolutionRecord[] for every resolution attempt
 *   - Residual unresolvable/ambiguous edges retained for diagnostics
 */

import type {
  GraphNode,
  GraphEdge,
  UnresolvedGraphEdge,
  EdgeResolutionRecord,
  EdgeResolutionStatus,
} from '../graph/types';
import {
  buildSymbolIndex,
  resolvePathAlias,
  resolveRelativePath,
  normalizeWorkspacePackageImport,
  type SymbolIndex,
  type SymbolResolverOptions,
} from './symbol-index';

// ============================================================================
// Symbol Index
// ============================================================================

// ============================================================================
// Resolution logic
// ============================================================================

function resolveRef(
  targetRef: string,
  sourceId: string,
  sourcePath: string | undefined,
  kindHint: string | undefined,
  index: SymbolIndex,
  options?: SymbolResolverOptions,
): { status: EdgeResolutionStatus; resolvedId?: string; candidateIds: string[] } {
  // Apply alias resolution first
  let resolvedTarget = resolvePathAlias(targetRef, options);
  resolvedTarget = normalizeWorkspacePackageImport(resolvedTarget, options);

  // Apply relative path resolution if sourcePath is available
  if (sourcePath && (resolvedTarget.startsWith('./') || resolvedTarget.startsWith('../'))) {
    resolvedTarget = resolveRelativePath(resolvedTarget, sourcePath);
  }

  // Try exact match first
  let candidates = index.get(resolvedTarget) ?? [];

  // If no exact match, try with common extensions
  if (candidates.length === 0) {
    const extensions = ['.ts', '.tsx', '.js', '.jsx', '/index.ts', '/index.tsx', '/index.js', '/index.jsx'];
    for (const ext of extensions) {
      const withExt = resolvedTarget + (resolvedTarget.endsWith('/') ? ext.slice(1) : ext);
      candidates = index.get(withExt) ?? [];
      if (candidates.length > 0) break;
    }
  }

  // Filter by kind hint if provided
  const filtered = kindHint
    ? candidates.filter(c => c.kind === kindHint)
    : candidates;

  // Exclude self-references
  const nonSelf = filtered.filter(c => c.nodeId !== sourceId);

  if (nonSelf.length === 1) {
    return { status: 'resolved', resolvedId: nonSelf[0]!.nodeId, candidateIds: [] };
  }

  if (nonSelf.length > 1) {
    // Ambiguous — more than one node matches
    return {
      status: 'ambiguous',
      candidateIds: nonSelf.map(c => c.nodeId),
    };
  }

  // No internal match — check if it looks like a node_modules import
  const workspacePathPrefixes = ['src/', 'packages/', 'apps/', 'libs/', 'platform/', 'products/'];
  const referencesWorkspacePath = workspacePathPrefixes.some(prefix => resolvedTarget.startsWith(prefix));
  const isCrossRepo =
    !resolvedTarget.startsWith('.') &&
    !resolvedTarget.startsWith('/') &&
    !resolvedTarget.includes('#') &&
    !referencesWorkspacePath &&
    !resolvedTarget.endsWith('.tsx') &&
    !resolvedTarget.endsWith('.ts') &&
    !resolvedTarget.endsWith('.js') &&
    !resolvedTarget.endsWith('.jsx');

  return {
    status: isCrossRepo ? 'cross-repo' : 'unresolvable',
    candidateIds: [],
  };
}

// ============================================================================
// Public API
// ============================================================================

export interface SymbolResolutionResult {
  readonly resolvedEdges: GraphEdge[];
  readonly resolutionRecords: EdgeResolutionRecord[];
  readonly remainingUnresolved: UnresolvedGraphEdge[];
}

/**
 * Resolve all unresolved edges against the known node set.
 * Returns:
 *   - `resolvedEdges`: new GraphEdge objects with real targetIds
 *   - `resolutionRecords`: full record of every resolution attempt
 *   - `remainingUnresolved`: edges still unresolved (unresolvable / ambiguous / cross-repo)
 */
export function resolveSymbols(
  unresolvedEdges: readonly UnresolvedGraphEdge[],
  nodes: readonly GraphNode[],
  options?: SymbolResolverOptions,
): SymbolResolutionResult {
  const index = buildSymbolIndex(nodes);

  // Build a map of node ID to source path for relative resolution
  const nodePathMap = new Map(nodes.map(n => [n.id, n.sourceLocation.filePath]));

  const resolvedEdges: GraphEdge[] = [];
  const resolutionRecords: EdgeResolutionRecord[] = [];
  const remainingUnresolved: UnresolvedGraphEdge[] = [];

  for (const edge of unresolvedEdges) {
    const sourcePath = nodePathMap.get(edge.sourceId);
    const result = resolveRef(
      edge.targetRef,
      edge.sourceId,
      sourcePath,
      edge.targetKindHint,
      index,
      options,
    );

    const record: EdgeResolutionRecord = {
      unresolvedEdge: edge,
      status: result.status,
      resolvedTargetId: result.resolvedId,
      candidateIds: result.candidateIds,
      reviewRequired: result.status === 'ambiguous',
    };
    resolutionRecords.push(record);

    if (result.status === 'resolved' && result.resolvedId) {
      // Build a deterministic edge ID from source+target+kind
      const edgeId = `edge:${edge.sourceId}--${edge.relationship}-->${result.resolvedId}`;
      resolvedEdges.push({
        id: edgeId,
        sourceId: edge.sourceId,
        targetId: result.resolvedId,
        kind: edge.relationship as import('../graph/types').GraphEdgeKind,
        confidence: edge.confidence,
        bidirectional: false,
        metadata: {
          ...edge.metadata,
          resolvedFromRef: edge.targetRef,
        },
      });
    } else {
      remainingUnresolved.push(edge);
    }
  }

  return { resolvedEdges, resolutionRecords, remainingUnresolved };
}
