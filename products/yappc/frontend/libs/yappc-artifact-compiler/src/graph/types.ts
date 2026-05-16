/**
 * @fileoverview ArtifactGraph schemas - repository-wide symbol and relationship graph.
 *
 * The ArtifactGraph is the "reverse compiler IR": a persistent, queryable graph
 * of files, symbols, components, routes, styles, tokens, entities, and their
 * cross-references across all languages in the repository.
 *
 * Two-phase edge resolution model:
 *   Phase 1 — Extractors emit UnresolvedGraphEdge (targetRef: string name/path).
 *   Phase 2 — Graph builder resolves refs to UUID node IDs, producing ResolvedGraphEdge.
 *   Unresolved edges never enter the resolved edge table with fake IDs.
 */

import { z } from "zod";

// ============================================================================
// Branded types
// ============================================================================

/** Opaque artifact node ID — always a deterministic URN or UUID, never a name string. */
export type ArtifactNodeId = string & { readonly _brand: "ArtifactNodeId" };

export function toArtifactNodeId(raw: string): ArtifactNodeId {
  return raw as ArtifactNodeId;
}

// ============================================================================
// Snapshot Reference — stable cross-scan identity anchor
// ============================================================================

export const SnapshotRefSchema = z.object({
  provider: z.enum(["local-folder", "github", "gitlab", "zip", "artifact-registry"]),
  repoId: z.string().min(1),
  commitSha: z.string().optional(),
  branch: z.string().optional(),
});

export type SnapshotRef = z.infer<typeof SnapshotRefSchema>;

// ============================================================================
// Deterministic ID helpers (stable across repeat scans of the same commit)
// ============================================================================

/**
 * Build a deterministic artifact node URN:
 *   artifact://<provider>/<repoId>@<commitSha>/<relativePath>#<symbolKind>:<symbolName>
 *
 * Falls back to a random UUID only if snapshotRef is absent (user-created artifacts).
 */
export function buildDeterministicNodeId(
  snapshotRef: SnapshotRef | undefined,
  relativePath: string,
  symbolKind: string,
  symbolName: string,
): ArtifactNodeId {
  if (!snapshotRef) {
    return crypto.randomUUID() as ArtifactNodeId;
  }
  const ref = snapshotRef.commitSha
    ? `${snapshotRef.repoId}@${snapshotRef.commitSha}`
    : `${snapshotRef.repoId}`;
  const urn = `artifact://${snapshotRef.provider}/${ref}/${relativePath}#${symbolKind}:${symbolName}`;
  return urn as ArtifactNodeId;
}

// ============================================================================
// Graph Node Kinds
// ============================================================================

export const GraphNodeKindSchema = z.enum([
  "file",
  "symbol",
  "component",
  "route",
  "page",
  "layout",
  "style",
  "token",
  "theme",
  "entity",
  "database-object",
  "story",
  "api-endpoint",
  "api-schema",
  "state-store",
  "state-action",
  "state-reducer",
  "cache-config",
  "message-topic",
  "workflow-job",
  "script",
]);

export type GraphNodeKind = z.infer<typeof GraphNodeKindSchema>;

// ============================================================================
// Graph Edge Kinds
// ============================================================================

export const GraphEdgeKindSchema = z.enum([
  "contains", // Parent contains child (file -> symbol)
  "imports", // File/symbol imports from another
  "exports", // File/symbol exports
  "uses", // Symbol uses another symbol
  "renders", // Component renders another component
  "routes-to", // Route navigates to another route
  "extends", // Type/class extends another
  "implements", // Type implements interface
  "styles", // Component/file has styles
  "tokens", // Style/file references tokens
  "queries", // Component/page queries API/DB
  "mutates", // Component/page mutates state/DB
  "consumes", // Consumer of message queue/topic
  "produces", // Producer to message queue/topic
  "depends-on", // General dependency
  "auth-guarded-by", // Route/page guarded by auth
  "layout-of", // Layout applied to page/route
  "story-for", // Story tests/component documents
]);

export type GraphEdgeKind = z.infer<typeof GraphEdgeKindSchema>;

// ============================================================================
// Source Location
// ============================================================================

export const SourceLocationSchema = z.object({
  filePath: z.string().min(1),
  startLine: z.number().int().nonnegative(),
  startColumn: z.number().int().nonnegative(),
  endLine: z.number().int().nonnegative(),
  endColumn: z.number().int().nonnegative(),
});

export type SourceLocation = z.infer<typeof SourceLocationSchema>;

// ============================================================================
// Graph Node
// ============================================================================

export const GraphNodeSchema = z.object({
  /**
   * Node ID: deterministic URN (artifact://<provider>/…#kind:name) for source-derived nodes,
   * or a random UUID for user-created logical artifacts without a source anchor.
   */
  id: z.string().min(1),
  kind: GraphNodeKindSchema,
  label: z.string().min(1),
  /**
   * Stable source identity — the deterministic URN before resolution.
   * Set to the same value as `id` for deterministic nodes; absent for random-UUID nodes.
   */
  sourceRef: z.string().optional(),
  /**
   * Qualified symbol reference: "<relativePath>#<symbolKind>:<symbolName>"
   * Enables cross-scan identity matching independent of snapshot ref.
   */
  symbolRef: z.string().optional(),
  sourceLocation: SourceLocationSchema,
  extractorId: z.string().min(1),
  extractorVersion: z.string().min(1),
  confidence: z.number().min(0).max(1),
  provenance: z.enum(["exact", "inferred", "synthesized", "manual", "assumed"]),
  privacySecurityFlags: z.array(z.string()).default([]),
  residualFragmentIds: z.array(z.string()).default([]),
  metadata: z.record(z.string(), z.unknown()).default({}),
});

export type GraphNode = z.infer<typeof GraphNodeSchema>;

// ============================================================================
// Graph Edge
// ============================================================================

export const GraphEdgeSchema = z.object({
  /** Edge ID: deterministic URN or random UUID. */
  id: z.string().min(1),
  /** Resolved source node ID — must be a valid node ID in the same graph. */
  sourceId: z.string().min(1),
  /** Resolved target node ID — must be a valid node ID in the same graph. Never a component name string. */
  targetId: z.string().min(1),
  kind: GraphEdgeKindSchema,
  confidence: z.number().min(0).max(1),
  bidirectional: z.boolean().default(false),
  metadata: z.record(z.string(), z.unknown()).default({}),
});

export type GraphEdge = z.infer<typeof GraphEdgeSchema>;

// ============================================================================
// Unresolved Graph Edge — Phase 1 extractor output (pre-resolution)
// ============================================================================

/**
 * An unresolved graph edge emitted by extractors during Phase 1 extraction.
 * Carries a string reference (import path, component name, symbol name) that
 * must be resolved to an actual ArtifactNodeId in Phase 2 before entering the
 * resolved edge table. Unresolved edges NEVER enter the resolved graph with
 * fake IDs.
 */
export const UnresolvedGraphEdgeSchema = z.object({
  /** Source node ID — already resolved (the node we are extracting from). */
  sourceId: z.string().min(1),
  /**
   * String reference to the target: import path, component name, symbol name, etc.
   * Will be resolved to a real ArtifactNodeId in Phase 2.
   */
  targetRef: z.string().min(1),
  /** Hint about what kind of node targetRef likely resolves to. */
  targetKindHint: GraphNodeKindSchema.optional(),
  relationship: GraphEdgeKindSchema,
  sourceLocation: SourceLocationSchema,
  /** Extractor-assigned confidence in the reference (0-1). */
  confidence: z.number().min(0).max(1),
  metadata: z.record(z.string(), z.unknown()).default({}),
});

export type UnresolvedGraphEdge = z.infer<typeof UnresolvedGraphEdgeSchema>;

/**
 * Resolution status of an unresolved edge after the Phase 2 symbol pass.
 */
export const EdgeResolutionStatusSchema = z.enum([
  "resolved",         // Successfully resolved to a real node ID
  "unresolvable",     // No matching node found in the repo-wide index
  "ambiguous",        // Multiple candidates found; human review needed
  "cross-repo",       // Target belongs to an external package (e.g. node_modules)
]);

export type EdgeResolutionStatus = z.infer<typeof EdgeResolutionStatusSchema>;

/**
 * Record of a resolution attempt — kept as residual debt in the graph.
 */
export const EdgeResolutionRecordSchema = z.object({
  unresolvedEdge: UnresolvedGraphEdgeSchema,
  status: EdgeResolutionStatusSchema,
  resolvedTargetId: z.string().optional(),
  candidateIds: z.array(z.string()).default([]),
  reviewRequired: z.boolean().default(false),
});

export type EdgeResolutionRecord = z.infer<typeof EdgeResolutionRecordSchema>;

// ============================================================================
// ArtifactGraph
// ============================================================================

export const ArtifactGraphSchema = z.object({
  /** Graph ID: deterministic based on snapshotRef or random UUID for in-memory graphs. */
  id: z.string().min(1),
  repositoryRoot: z.string().min(1),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  version: z.number().int().nonnegative(),
  /** Snapshot reference used to produce deterministic node IDs. */
  snapshotRef: SnapshotRefSchema.optional(),
  nodes: z.array(GraphNodeSchema),
  /** Fully resolved edges only. All targetIds are valid node IDs in this graph. */
  edges: z.array(GraphEdgeSchema),
  /**
   * Unresolved edges from Phase 1 extraction that could not be resolved to real node IDs.
   * These are residual reference debt — kept for diagnostics and incremental re-resolution.
   */
  unresolvedEdges: z.array(UnresolvedGraphEdgeSchema).default([]),
  /** Resolution records for all attempted resolutions in Phase 2. */
  edgeResolutionRecords: z.array(EdgeResolutionRecordSchema).default([]),
  nodeIndex: z.record(z.string(), z.array(z.string())), // kind -> nodeIds
  edgeIndex: z.record(z.string(), z.array(z.string())), // kind -> edgeIds
});

export type ArtifactGraph = z.infer<typeof ArtifactGraphSchema>;

// ============================================================================
// Graph Query
// ============================================================================

export const GraphQuerySchema = z.object({
  nodeKinds: z.array(GraphNodeKindSchema).optional(),
  edgeKinds: z.array(GraphEdgeKindSchema).optional(),
  fromNodeId: z.string().uuid().optional(),
  toNodeId: z.string().uuid().optional(),
  minConfidence: z.number().min(0).max(1).optional(),
  sourcePath: z.string().optional(),
  labelContains: z.string().optional(),
  limit: z.number().int().nonnegative().optional(),
});

export type GraphQuery = z.infer<typeof GraphQuerySchema>;

export const GraphQueryResultSchema = z.object({
  nodes: z.array(GraphNodeSchema),
  edges: z.array(GraphEdgeSchema),
  totalNodes: z.number().int().nonnegative(),
  totalEdges: z.number().int().nonnegative(),
});

export type GraphQueryResult = z.infer<typeof GraphQueryResultSchema>;
