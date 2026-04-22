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

export const RegenerationStrategySchema = z.enum([
  'verbatim-preserve',   // Copy original source as-is
  'best-effort-approximate', // Generate closest possible equivalent
  'emit-warning',        // Emit warning but skip in codegen
  'require-manual-impl', // Flag for human implementation
  'placeholder-stub',    // Generate stub with TODO comment
]);

export type RegenerationStrategy = z.infer<typeof RegenerationStrategySchema>;

// ============================================================================
// Residual Island
// ============================================================================

export const ResidualIslandSchema = z.object({
  id: z.string().uuid(),
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
  extractorId: z.string().min(1),
  extractorVersion: z.string().min(1),
  extractedAt: z.string().datetime(),
  confidence: z.number().min(0).max(1),
  linkedModelElementIds: z.array(z.string().uuid()).default([]),
  tags: z.array(z.string()).default([]),
});

export type ResidualIsland = z.infer<typeof ResidualIslandSchema>;

// ============================================================================
// Residual Collection
// ============================================================================

export const ResidualCollectionSchema = z.object({
  modelId: z.string().uuid(),
  islands: z.array(ResidualIslandSchema).default([]),
  summary: z.object({
    totalIslands: z.number().int().nonnegative(),
    byKind: z.record(z.string(), z.number().int().nonnegative()),
    reviewRequiredCount: z.number().int().nonnegative(),
    coverageRatio: z.number().min(0).max(1),
  }),
});

export type ResidualCollection = z.infer<typeof ResidualCollectionSchema>;
