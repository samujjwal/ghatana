/**
 * @fileoverview ArtifactGraph barrel export.
 */

export {
  GraphNodeKindSchema,
  GraphEdgeKindSchema,
  SourceLocationSchema,
  GraphNodeSchema,
  GraphEdgeSchema,
  UnresolvedGraphEdgeSchema,
  EdgeResolutionStatusSchema,
  EdgeResolutionRecordSchema,
  ArtifactGraphSchema,
  GraphQuerySchema,
  GraphQueryResultSchema,
  SnapshotRefSchema,
  buildDeterministicNodeId,
  toArtifactNodeId,
} from "./types";

export type {
  ArtifactNodeId,
  SnapshotRef,
  GraphNodeKind,
  GraphEdgeKind,
  SourceLocation,
  GraphNode,
  GraphEdge,
  UnresolvedGraphEdge,
  EdgeResolutionStatus,
  EdgeResolutionRecord,
  ArtifactGraph,
  GraphQuery,
  GraphQueryResult,
} from "./types";

export {
  validateGraph,
} from "./validateGraph";

export type {
  GraphValidationError,
  GraphValidationResult,
} from "./validateGraph";
