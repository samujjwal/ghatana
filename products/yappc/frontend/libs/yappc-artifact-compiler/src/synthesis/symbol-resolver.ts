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
  readonly baseName: string; // filename without extension
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
    const filePath = node.sourceLocation.filePath;
    const base = filePath.split('/').pop() ?? '';
    const nameNoExt = base.includes('.') ? base.slice(0, base.lastIndexOf('.')) : base;

    const entry: SymbolIndexEntry = {
      nodeId: node.id,
      symbolRef: node.symbolRef,
      label: node.label,
      relativePath: filePath,
      kind: node.kind,
      baseName: nameNoExt,
    };

    // Key 1: exact symbolRef (highest confidence match)
    if (node.symbolRef) add(node.symbolRef, entry);

    // Key 2: label (component name, function name, etc.)
    add(node.label, entry);

    // Key 3: relative path (for file-level references)
    add(filePath, entry);

    // Key 4: basename without extension (for import shorthand: 'Button' -> 'Button.tsx')
    if (nameNoExt && nameNoExt !== node.label) add(nameNoExt, entry);

    // Key 5: path with .ts extension (TypeScript imports)
    if (!filePath.endsWith('.ts')) {
      add(filePath + '.ts', entry);
    }

    // Key 6: path with .tsx extension (React imports)
    if (!filePath.endsWith('.tsx')) {
      add(filePath + '.tsx', entry);
    }

    // Key 7: path with .js extension (JavaScript imports)
    if (!filePath.endsWith('.js')) {
      add(filePath + '.js', entry);
    }

    // Key 8: path with .jsx extension (React JSX imports)
    if (!filePath.endsWith('.jsx')) {
      add(filePath + '.jsx', entry);
    }

    // Key 9: index file references (directory -> index.ts)
    const dirPath = filePath.slice(0, filePath.lastIndexOf('/'));
    if (dirPath && (nameNoExt === 'index' || nameNoExt === 'Index')) {
      add(dirPath, entry);
      add(dirPath + '/index', entry);
      add(dirPath + '/index.ts', entry);
      add(dirPath + '/index.tsx', entry);
      add(dirPath + '/index.js', entry);
      add(dirPath + '/index.jsx', entry);
    }
  }

  return index;
}

// ============================================================================
// Resolution logic
// ============================================================================

/**
 * Resolves a relative import path against a source file path.
 * e.g., './foo' from 'src/components/Button.tsx' -> 'src/components/foo'
 *      '../utils' from 'src/components/Button.tsx' -> 'src/utils'
 */
function resolveRelativePath(importPath: string, sourcePath: string): string {
  if (!importPath.startsWith('./') && !importPath.startsWith('../')) {
    return importPath; // Not a relative import
  }

  const sourceDir = sourcePath.slice(0, sourcePath.lastIndexOf('/'));
  const segments = sourceDir.split('/');

  const importSegments = importPath.split('/').filter(s => s !== '.');

  for (const seg of importSegments) {
    if (seg === '..') {
      segments.pop();
    } else {
      segments.push(seg);
    }
  }

  return segments.join('/');
}

/**
 * Resolves path aliases (e.g., @/components/Button -> src/components/Button).
 * This is a basic implementation; real-world usage would need configurable alias maps.
 */
function resolvePathAlias(importPath: string): string {
  // Common alias patterns
  if (importPath.startsWith('@/')) {
    return 'src/' + importPath.slice(2);
  }
  if (importPath.startsWith('~@/')) {
    return 'src/' + importPath.slice(3);
  }
  if (importPath.startsWith('#/')) {
    return 'src/' + importPath.slice(2);
  }
  if (importPath.startsWith('~/')) {
    return importPath.slice(2);
  }
  return importPath;
}

function resolveRef(
  targetRef: string,
  sourceId: string,
  sourcePath: string | undefined,
  kindHint: string | undefined,
  index: SymbolIndex,
): { status: EdgeResolutionStatus; resolvedId?: string; candidateIds: string[] } {
  // Apply alias resolution first
  let resolvedTarget = resolvePathAlias(targetRef);

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
  const isCrossRepo =
    !resolvedTarget.startsWith('.') &&
    !resolvedTarget.startsWith('/') &&
    !resolvedTarget.includes('#') &&
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
