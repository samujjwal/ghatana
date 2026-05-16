/**
 * @fileoverview Residual island schemas - explicit escape hatches for unmodelable artifacts.
 *
 * A serious decompiler needs a safe place to put information it cannot normalize.
 * Without residual islands, round-trip becomes lossy in hidden ways.
 */

import { z } from 'zod';

// ============================================================================
// Residual Island Kind
// ============================================================================

export const ResidualIslandKindSchema = z.enum([
  'code',
  'style',
  'query',
  'logic',
]);

export type ResidualIslandKind = z.infer<typeof ResidualIslandKindSchema>;

// ============================================================================
// Regeneration Strategy
// ============================================================================

/**
 * P0: Regeneration strategies for residual islands.
 * Note: 'placeholder-stub' removed - production code must not emit placeholder stubs.
 * Use 'require-manual-impl' or 'emit-warning' for unsupported cases.
 */
export const RegenerationStrategySchema = z.enum([
  'verbatim-preserve',   // Copy original source as-is
  'best-effort-approximate', // Generate closest possible equivalent
  'emit-warning',        // Emit warning but skip in codegen
  'require-manual-impl', // Flag for human implementation
]);

export type RegenerationStrategy = z.infer<typeof RegenerationStrategySchema>;

// ============================================================================
// Risk Level
// ============================================================================

export const RiskLevelSchema = z.enum(['low', 'medium', 'high', 'critical']);

export type RiskLevel = z.infer<typeof RiskLevelSchema>;

// ============================================================================
// Residual Island
// ============================================================================

export const ResidualIslandSchema = z.object({
  id: z.string().min(1),
  kind: ResidualIslandKindSchema,
  originalSource: z.string().min(1),
  normalizedSummary: z.string().min(1),
  reasonUnmodeled: z.string().min(1),
  reviewRequired: z.boolean(),
  reviewReason: z.string().optional(),
  regenerationStrategy: RegenerationStrategySchema,
  sourceLocation: z.object({
    filePath: z.string().min(1),
    startLine: z.number().int().nonnegative(),
    startColumn: z.number().int().nonnegative(),
    endLine: z.number().int().nonnegative(),
    endColumn: z.number().int().nonnegative(),
  }),
  extractorId: z.string().min(1).default('unknown-extractor'),
  extractorVersion: z.string().min(1).default('0.0.0'),
  extractedAt: z.string().datetime().default(() => new Date().toISOString()),
  confidence: z.number().min(0).max(1).default(0.5),
  /**
   * P0: Model element IDs linked to this residual island.
   * Accepts deterministic artifact:// URNs or UUIDs - not UUID-only.
   */
  linkedModelElementIds: z.array(z.string().refine(
    (val) => {
      // Accept UUID format
      if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(val)) {
        return true;
      }
      // Accept URN format (artifact://...)
      if (val.startsWith('artifact://')) {
        return true;
      }
      return false;
    },
    { message: 'Linked model element ID must be a UUID or artifact:// URN' }
  )).default([]),
  tags: z.array(z.string()).default([]),
  /**
   * Reference to the raw source fragment (e.g., a specific code block).
   * Enables precise round-trip reconstruction of unmodelable content.
   */
  rawFragmentRef: z.string().min(1),
  /**
   * Checksum of the raw fragment content for verification.
   * Ensures round-trip integrity and detects accidental modifications.
   */
  checksum: z.string().min(1),
  /**
   * Risk assessment for this residual island.
   * Indicates the impact of losing or incorrectly modifying this content.
   */
  risk: RiskLevelSchema,
  /**
   * P0: Related graph node IDs that this residual island references or is referenced by.
   * Accepts deterministic artifact:// URNs or UUIDs.
   */
  relatedGraphNodeIds: z.array(z.string().refine(
    (val) => {
      if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(val)) return true;
      if (val.startsWith('artifact://')) return true;
      if (val.startsWith('urn:')) return true;
      return false;
    },
    { message: 'Graph node ID must be a UUID or artifact:// URN' }
  )),
});

export type ResidualIsland = z.infer<typeof ResidualIslandSchema>;

// ============================================================================
// Residual Collection
// ============================================================================

/**
 * P0: Branded residual ID type for type safety.
 */
export type ResidualIslandId = string & { readonly _brand: 'ResidualIslandId' };

export function toResidualIslandId(raw: string): ResidualIslandId {
  return raw as ResidualIslandId;
}

export const ResidualCollectionSchema = z.object({
  modelId: z.string().refine(
    (val) => {
      if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(val)) return true;
      if (val.startsWith('artifact://')) return true;
      return false;
    },
    { message: 'Model ID must be a UUID or artifact:// URN' }
  ),
  islands: z.array(ResidualIslandSchema).default([]),
  summary: z.object({
    totalIslands: z.number().int().nonnegative(),
    byKind: z.record(z.string(), z.number().int().nonnegative()),
    reviewRequiredCount: z.number().int().nonnegative(),
    coverageRatio: z.number().min(0).max(1),
  }),
});

export type ResidualCollection = z.infer<typeof ResidualCollectionSchema>;
