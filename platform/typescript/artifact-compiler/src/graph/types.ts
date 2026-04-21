/**
 * @fileoverview ArtifactGraph schemas - repository-wide symbol and relationship graph.
 *
 * The ArtifactGraph is the "reverse compiler IR": a persistent, queryable graph
 * of files, symbols, components, routes, styles, tokens, entities, and their
 * cross-references across all languages in the repository.
 */

import { z } from "zod";

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
  id: z.string().uuid(),
  kind: GraphNodeKindSchema,
  label: z.string().min(1),
  sourceLocation: SourceLocationSchema,
  extractorId: z.string().min(1),
  extractorVersion: z.string().min(1),
  confidence: z.number().min(0).max(1),
  provenance: z.enum(["exact", "inferred", "synthesized", "manual", "assumed"]),
  privacySecurityFlags: z.array(z.string()).default([]),
  residualFragmentIds: z.array(z.string()).default([]),
  metadata: z.record(z.unknown()).default({}),
});

export type GraphNode = z.infer<typeof GraphNodeSchema>;

// ============================================================================
// Graph Edge
// ============================================================================

export const GraphEdgeSchema = z.object({
  id: z.string().uuid(),
  sourceId: z.string().uuid(),
  targetId: z.string().uuid(),
  kind: GraphEdgeKindSchema,
  confidence: z.number().min(0).max(1),
  bidirectional: z.boolean().default(false),
  metadata: z.record(z.unknown()).default({}),
});

export type GraphEdge = z.infer<typeof GraphEdgeSchema>;

// ============================================================================
// ArtifactGraph
// ============================================================================

export const ArtifactGraphSchema = z.object({
  id: z.string().uuid(),
  repositoryRoot: z.string().min(1),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  version: z.number().int().nonnegative(),
  nodes: z.array(GraphNodeSchema),
  edges: z.array(GraphEdgeSchema),
  nodeIndex: z.record(z.array(z.string().uuid())), // kind -> nodeIds
  edgeIndex: z.record(z.array(z.string().uuid())), // kind -> edgeIds
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
