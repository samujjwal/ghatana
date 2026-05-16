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

// ============================================================================
// Symbol Index
// ============================================================================

interface SymbolIndexEntry {
  readonly nodeId: string;
  readonly symbolRef: string | undefined;
  readonly label: string;
  readonly relativePath: string;
  readonly kind: string;
}

type SymbolIndex = Map<string, SymbolIndexEntry[]>;

function buildSymbolIndex(nodes: readonly GraphNode[]): SymbolIndex {
  const index: SymbolIndex = new Map();

  const add = (key: string, entry: SymbolIndexEntry): void => {
    const existing = index.get(key);
    if (existing) {
      existing.push(entry);
    } else {
      index.set(key, [entry]);
    }
  };

  for (const node of nodes) {
    const entry: SymbolIndexEntry = {
      nodeId: node.id,
      symbolRef: node.symbolRef,
      label: node.label,
      relativePath: node.sourceLocation.filePath,
      kind: node.kind,
    };

    // Key 1: exact symbolRef (highest confidence match)
    if (node.symbolRef) add(node.symbolRef, entry);

    // Key 2: label (component name, function name, etc.)
    add(node.label, entry);

    // Key 3: relative path (for file-level references)
    add(node.sourceLocation.filePath, entry);

    // Key 4: basename without extension (for import shorthand: 'Button' -> 'Button.tsx')
    const base = node.sourceLocation.filePath.split('/').pop() ?? '';
    const nameNoExt = base.includes('.') ? base.slice(0, base.lastIndexOf('.')) : base;
    if (nameNoExt && nameNoExt !== node.label) add(nameNoExt, entry);
  }

  return index;
}

// ============================================================================
// Resolution logic
// ============================================================================

function resolveRef(
  targetRef: string,
  sourceId: string,
  kindHint: string | undefined,
  index: SymbolIndex,
): { status: EdgeResolutionStatus; resolvedId?: string; candidateIds: string[] } {
  const candidates = index.get(targetRef) ?? [];

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
  const isCrossRepo =
    !targetRef.startsWith('.') &&
    !targetRef.startsWith('/') &&
    !targetRef.includes('#') &&
    !targetRef.endsWith('.tsx') &&
    !targetRef.endsWith('.ts');

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
): SymbolResolutionResult {
  const index = buildSymbolIndex(nodes);

  const resolvedEdges: GraphEdge[] = [];
  const resolutionRecords: EdgeResolutionRecord[] = [];
  const remainingUnresolved: UnresolvedGraphEdge[] = [];

  for (const edge of unresolvedEdges) {
    const result = resolveRef(
      edge.targetRef,
      edge.sourceId,
      edge.targetKindHint,
      index,
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
