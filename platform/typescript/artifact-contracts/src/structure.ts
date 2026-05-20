/**
 * @fileoverview Typed contracts for extracted artifact source structure.
 */

import { z } from "zod";

export const EXTRACTED_STRUCTURE_SCHEMA_VERSION = "1.0.0" as const;

export const JsxTreeNodeSchema: z.ZodType<{
  readonly tagName: string;
  readonly isIntrinsic: boolean;
  readonly children: readonly {
    readonly tagName: string;
    readonly isIntrinsic: boolean;
    readonly children: readonly unknown[];
    readonly startLine: number;
    readonly endLine: number;
  }[];
  readonly startLine: number;
  readonly endLine: number;
}> = z.lazy(() =>
  z.object({
    tagName: z.string().min(1),
    isIntrinsic: z.boolean(),
    children: z.array(JsxTreeNodeSchema),
    startLine: z.number().int().positive(),
    endLine: z.number().int().positive(),
  }),
);

export type JsxTreeNode = z.infer<typeof JsxTreeNodeSchema>;

export const DetectedRouteSchema = z.object({
  path: z.string(),
  componentName: z.string().min(1),
  isIndex: z.boolean(),
  sourceLine: z.number().int().positive(),
});

export type DetectedRoute = z.infer<typeof DetectedRouteSchema>;

export const ComponentUsageRecordSchema = z.object({
  tagName: z.string().min(1),
  isDesignSystem: z.boolean(),
  sourceLine: z.number().int().positive(),
  importedFrom: z.string().nullable(),
});

export type ComponentUsageRecord = z.infer<typeof ComponentUsageRecordSchema>;

export const ExtractedProtectedRegionSchema = z.object({
  regionId: z.string().min(1),
  ownerKind: z.string().min(1),
  startLine: z.number().int().positive(),
  endLine: z.number().int().positive(),
  contentLines: z.array(z.string()),
});

export type ExtractedProtectedRegion = z.infer<typeof ExtractedProtectedRegionSchema>;

export const SourceImportRecordSchema = z.object({
  moduleSpecifier: z.string().min(1),
  importClauseText: z.string().nullable(),
  isTypeOnly: z.boolean(),
  sourceLine: z.number().int().positive(),
  text: z.string().min(1),
});

export type SourceImportRecord = z.infer<typeof SourceImportRecordSchema>;

/**
 * Versioned envelope for extracted structure payloads.
 *
 * This enables additive schema evolution without breaking existing evidence
 * packs and persisted workflow snapshots.
 */
export const ExtractedStructureEnvelopeSchema = z.object({
  schemaVersion: z.literal(EXTRACTED_STRUCTURE_SCHEMA_VERSION),
  jsxTree: z.array(JsxTreeNodeSchema).default([]),
  detectedRoutes: z.array(DetectedRouteSchema).default([]),
  componentUsages: z.array(ComponentUsageRecordSchema).default([]),
  protectedRegions: z.array(ExtractedProtectedRegionSchema).default([]),
  sourceImports: z.array(SourceImportRecordSchema).default([]),
});

export type ExtractedStructureEnvelope = z.infer<typeof ExtractedStructureEnvelopeSchema>;

/**
 * Migrates known historical extracted-structure shapes into the current
 * versioned envelope contract.
 */
export function migrateExtractedStructureEnvelope(input: unknown): ExtractedStructureEnvelope {
  const envelope = z.object({
    schemaVersion: z.string(),
    jsxTree: z.array(JsxTreeNodeSchema).optional(),
    detectedRoutes: z.array(DetectedRouteSchema).optional(),
    componentUsages: z.array(ComponentUsageRecordSchema).optional(),
    protectedRegions: z.array(ExtractedProtectedRegionSchema).optional(),
    sourceImports: z.array(SourceImportRecordSchema).optional(),
  }).safeParse(input);

  if (envelope.success && envelope.data.schemaVersion === EXTRACTED_STRUCTURE_SCHEMA_VERSION) {
    return ExtractedStructureEnvelopeSchema.parse(envelope.data);
  }

  return ExtractedStructureEnvelopeSchema.parse({
    schemaVersion: EXTRACTED_STRUCTURE_SCHEMA_VERSION,
    jsxTree: (input as { jsxTree?: unknown })?.jsxTree,
    detectedRoutes: (input as { detectedRoutes?: unknown })?.detectedRoutes,
    componentUsages: (input as { componentUsages?: unknown })?.componentUsages,
    protectedRegions: (input as { protectedRegions?: unknown })?.protectedRegions,
    sourceImports: (input as { sourceImports?: unknown })?.sourceImports,
  });
}
