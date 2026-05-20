/**
 * @fileoverview Typed contracts for extracted artifact source structure.
 */

import { z } from "zod";

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
