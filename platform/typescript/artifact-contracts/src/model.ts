/**
 * @fileoverview Logical artifact model contracts.
 *
 * Defines LogicalArtifactModel — the canonical in-memory representation of a
 * repository/workspace produced by the decompiler pipeline. It is the shared
 * interchange model between scanner, Canvas projection, UI Builder projection,
 * DS Generator projection, and Studio review.
 *
 * @doc.type module
 * @doc.purpose Logical artifact model for the compiler/decompiler pipeline
 * @doc.layer platform
 * @doc.pattern Contracts
 */

import { z } from "zod";
import { SourceRefSchema } from "./source.js";

// ============================================================================
// ARTIFACT KIND
// ============================================================================

/**
 * Classification of an artifact node by its semantic role.
 */
export type ArtifactKind =
  | "component" // UI component
  | "page" // Route-level page
  | "layout" // Layout component / wrapper
  | "hook" // React hook
  | "utility" // Pure function / utility
  | "service" // Data fetching / business logic service
  | "store" // State management atom/slice
  | "schema" // Zod/JSON schema
  | "type" // Type-only declaration
  | "style" // Style module / Tailwind config
  | "config" // Build/project configuration
  | "test" // Test suite
  | "story" // Storybook story
  | "asset" // Binary asset
  | "unknown"; // Unclassified

export const ArtifactKindSchema = z.enum([
  "component",
  "page",
  "layout",
  "hook",
  "utility",
  "service",
  "store",
  "schema",
  "type",
  "style",
  "config",
  "test",
  "story",
  "asset",
  "unknown",
]);

// ============================================================================
// ARTIFACT DEPENDENCY
// ============================================================================

/** Relationship type between two artifact nodes. */
export type ArtifactDependencyKind =
  | "import" // Static import
  | "dynamic-import" // Dynamic import()
  | "re-export" // Re-exports from another module
  | "slot" // Renders via JSX slot/children
  | "compose" // Wraps / HOC
  | "data" // Fetches data from
  | "type-only"; // Import type only (erased at runtime)

export const ArtifactDependencyKindSchema = z.enum([
  "import",
  "dynamic-import",
  "re-export",
  "slot",
  "compose",
  "data",
  "type-only",
]);

export const ArtifactEdgeSchema = z.object({
  /** Unique edge ID within this model. */
  id: z.string().min(1),
  /** Source artifact node ID. */
  fromId: z.string().min(1),
  /** Target artifact node ID. */
  toId: z.string().min(1),
  /** Relationship type. */
  kind: ArtifactDependencyKindSchema,
  /** Import specifier string (e.g. "@ghatana/design-system"). */
  importSpecifier: z.string().optional(),
});

export type ArtifactEdge = z.infer<typeof ArtifactEdgeSchema>;

// ============================================================================
// ARTIFACT NODE
// ============================================================================

/**
 * A node in the logical artifact graph. Represents one meaningful
 * logical unit of the source codebase.
 */
export const ArtifactNodeSchema = z.object({
  /** Stable identifier (derived from relativePath for file-level nodes). */
  id: z.string().min(1),
  /** Display name for human-readable UIs. */
  displayName: z.string().min(1),
  /** Semantic kind. */
  kind: ArtifactKindSchema,
  /** Primary source reference. */
  sourceRef: SourceRefSchema.optional(),
  /**
   * Names of exported symbols from this node.
   * For a component file, lists the exported component name(s).
   */
  exportedSymbols: z.array(z.string()).default([]),
  /**
   * Props contract inferred from TypeScript types / JSDoc.
   * Key = prop name, value = inferred type string.
   */
  inferredProps: z.record(z.string(), z.string()).default({}),
  /**
   * Whether this node is a design-system boundary (consumes DS tokens).
   */
  usesDesignSystem: z.boolean().default(false),
  /**
   * Confidence of the classification (0–1).
   * Low confidence nodes should be surfaced for human review.
   */
  classificationConfidence: z.number().min(0).max(1).default(1),
  /** Additional key-value metadata for extensibility. */
  metadata: z.record(z.string(), z.unknown()).default({}),
});

export type ArtifactNode = z.infer<typeof ArtifactNodeSchema>;

// ============================================================================
// LOGICAL ARTIFACT MODEL
// ============================================================================

/**
 * Schema version for LogicalArtifactModel. Bump on breaking changes.
 */
export const LOGICAL_ARTIFACT_MODEL_SCHEMA_VERSION = "1.0.0";

/**
 * The canonical in-memory representation of a scanned repository or workspace.
 *
 * Produced by the decompiler pipeline, consumed by Canvas/UI Builder/DS
 * projections and Studio UX.
 */
export const LogicalArtifactModelSchema = z.object({
  /** Model schema version for migrations. */
  schemaVersion: z.literal(LOGICAL_ARTIFACT_MODEL_SCHEMA_VERSION),
  /** Unique model ID (UUID). */
  modelId: z.string().min(1),
  /** Human-readable label (repo name / workspace label). */
  label: z.string().min(1),
  /** When this model was produced. */
  scannedAt: z.string().datetime(),
  /**
   * Source acquisition descriptor describing where the source came from.
   * Absent for ephemeral/in-memory models.
   */
  acquisitionRef: z
    .object({
      repositoryUri: z.string(),
      commitRef: z.string(),
      label: z.string().optional(),
    })
    .optional(),
  /** All artifact nodes in this model. Keyed by node ID. */
  nodes: z.record(z.string(), ArtifactNodeSchema),
  /** Dependency edges between nodes. */
  edges: z.array(ArtifactEdgeSchema),
  /**
   * Top-level entry nodes (pages, exported components, etc.).
   * Subset of node IDs; used as roots for graph traversal.
   */
  entryNodeIds: z.array(z.string()).default([]),
  /** Additional key-value metadata for extensibility. */
  metadata: z.record(z.string(), z.unknown()).default({}),
});

export type LogicalArtifactModel = z.infer<typeof LogicalArtifactModelSchema>;

// ============================================================================
// FACTORY HELPERS
// ============================================================================

/**
 * Creates a minimal empty LogicalArtifactModel with the current schema version.
 */
export function createLogicalArtifactModel(
  modelId: string,
  label: string,
): LogicalArtifactModel {
  return {
    schemaVersion: LOGICAL_ARTIFACT_MODEL_SCHEMA_VERSION,
    modelId,
    label,
    scannedAt: new Date().toISOString(),
    nodes: {},
    edges: [],
    entryNodeIds: [],
    metadata: {},
  };
}
