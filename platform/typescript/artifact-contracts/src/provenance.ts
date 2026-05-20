/**
 * @fileoverview Provenance and ownership contracts.
 *
 * Defines ProvenanceRecord and OwnershipRegion — the canonical models for
 * tracing artifact nodes back to their source origin and declaring which
 * regions of generated output are user-authored vs generated.
 *
 * @doc.type module
 * @doc.purpose Source provenance and ownership region contracts
 * @doc.layer platform
 * @doc.pattern Contracts
 */

import { z } from "zod";
import { SourceRefSchema } from "./source.js";

// ============================================================================
// PROVENANCE RECORD
// ============================================================================

/**
 * Records the derivation chain of a single artifact node:
 * where it originally came from and how it was transformed.
 */
export const ProvenanceRecordSchema = z.object({
  /** ID of the artifact node this provenance belongs to. */
  nodeId: z.string().min(1),
  /**
   * Chain of source refs, from original source to most recent.
   * Index 0 = original, last = most recent.
   */
  sourceChain: z.array(SourceRefSchema).min(1),
  /**
   * ISO-8601 timestamp of when this record was recorded.
   */
  recordedAt: z.string().datetime(),
  /**
   * How this node was produced.
   */
  derivationKind: z.enum([
    "scanned", // Discovered directly from source scan
    "inferred", // Inferred from context (e.g. implicit import)
    "generated", // Produced by code generator
    "user-authored", // Manually written by a user post-generation
    "merged", // Result of user + generated merge
  ]),
  /** Optional human-readable note about this provenance entry. */
  note: z.string().optional(),
});

export type ProvenanceRecord = z.infer<typeof ProvenanceRecordSchema>;

// ============================================================================
// OWNERSHIP REGION
// ============================================================================

/**
 * Authorship type for a region of output code.
 */
export type OwnershipKind =
  | "generated" // Fully generated; safe to overwrite on re-compile
  | "user-authored" // Written or significantly modified by a user; must be preserved
  | "protected" // Explicitly protected region (e.g. a @ghatana-protected block)
  | "manual-merge-required"; // Conflicts detected; human must resolve

export const OwnershipKindSchema = z.enum([
  "generated",
  "user-authored",
  "protected",
  "manual-merge-required",
]);

/**
 * Marks a contiguous span within a generated file as owned by a particular
 * party (generator or user). Used to prevent accidental overwrite of
 * user-authored code during re-compilation.
 */
export const OwnershipRegionSchema = z.object({
  /** Relative file path within the output workspace. */
  filePath: z.string().min(1),
  /** Start byte offset within the file (inclusive). */
  startOffset: z.number().int().nonnegative(),
  /** End byte offset within the file (exclusive). */
  endOffset: z.number().int().nonnegative(),
  /** Who owns this region. */
  kind: OwnershipKindSchema,
  /**
   * Stable marker string embedded in the source code.
   * Example: "@ghatana-region:my-button-click-handler"
   */
  regionMarker: z.string().optional(),
  /** Source ref this region was derived from, if scanned from existing code. */
  sourceRef: SourceRefSchema.optional(),
  /** Human-readable note about this region. */
  note: z.string().optional(),
});

export type OwnershipRegion = z.infer<typeof OwnershipRegionSchema>;

// ============================================================================
// OWNERSHIP MAP
// ============================================================================

/**
 * All ownership regions for a single generated file.
 */
export const FileOwnershipMapSchema = z.object({
  /** Relative file path. */
  filePath: z.string().min(1),
  /** Ordered regions (by startOffset). */
  regions: z.array(OwnershipRegionSchema),
  /** Whether this file has any user-authored or protected regions. */
  hasUserContent: z.boolean(),
});

export type FileOwnershipMap = z.infer<typeof FileOwnershipMapSchema>;

/**
 * Full ownership map for an entire generate output.
 * Key = relative file path.
 */
export type WorkspaceOwnershipMap = Record<string, FileOwnershipMap>;
