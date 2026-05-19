/**
 * @fileoverview Zod schemas for CanvasDocument validation and migrations.
 *
 * Provides runtime validation, schema versioning, and migration paths for
 * canvas documents.
 *
 * @doc.type module
 * @doc.purpose Canvas document validation and migrations
 * @doc.layer canvas
 * @doc.pattern Schema
 */

import { z } from 'zod';

// ============================================================================
// Schema Version
// ============================================================================

export const CANVAS_SCHEMA_VERSION = '1.0.0' as const;

// ============================================================================
// Core Canvas Types
// ============================================================================

/**
 * Zod schema for Point (2D coordinate)
 */
export const PointSchema = z.object({
  x: z.number(),
  y: z.number(),
});

/**
 * Zod schema for Bounds (rectangle with position and size)
 */
export const BoundsSchema = z.object({
  x: z.number(),
  y: z.number(),
  width: z.number(),
  height: z.number(),
});

/**
 * Zod schema for Transform (position, scale, rotation)
 */
export const TransformSchema = z.object({
  position: PointSchema,
  scale: z.number(),
  rotation: z.number(),
});

/**
 * Zod schema for CanvasViewport
 */
export const CanvasViewportSchema = z.object({
  center: PointSchema,
  zoom: z.number(),
});

/**
 * Zod schema for CanvasElementMetadata
 */
export const CanvasElementMetadataSchema = z.object({
  custom: z.record(z.string(), z.unknown()).optional(),
  version: z.string().optional(),
});

/**
 * Zod schema for CanvasCapabilities
 */
export const CanvasCapabilitiesSchema = z.object({
  canEdit: z.boolean(),
  canZoom: z.boolean(),
  canPan: z.boolean(),
  canSelect: z.boolean(),
  canUndo: z.boolean(),
  canRedo: z.boolean(),
  canExport: z.boolean(),
  canImport: z.boolean(),
  canCollaborate: z.boolean(),
  canPersist: z.boolean(),
  allowedElementTypes: z.array(z.string()),
});

/**
 * Zod schema for CanvasElement (base element)
 */
export const CanvasElementSchema = z.object({
  id: z.string(),
  type: z.string(),
  transform: TransformSchema,
  bounds: BoundsSchema,
  visible: z.boolean(),
  locked: z.boolean(),
  selected: z.boolean(),
  zIndex: z.number(),
  metadata: CanvasElementMetadataSchema,
  version: z.string(),
  createdAt: z.string(), // ISO date string
  updatedAt: z.string(), // ISO date string
});

/**
 * Zod schema for CanvasNode (extends base element)
 */
export const CanvasNodeSchema = CanvasElementSchema.extend({
  type: z.literal('node'),
  nodeType: z.string(),
  data: z.record(z.string(), z.unknown()),
  inputs: z.array(z.object({
    id: z.string(),
    name: z.string(),
  })),
  outputs: z.array(z.object({
    id: z.string(),
    name: z.string(),
  })),
  style: z.record(z.string(), z.unknown()),
});

/**
 * Zod schema for CanvasEdge (extends base element)
 */
export const CanvasEdgeSchema = CanvasElementSchema.extend({
  type: z.literal('edge'),
  sourceId: z.string(),
  targetId: z.string(),
  path: z.array(PointSchema),
  style: z.record(z.string(), z.unknown()),
});

/**
 * Discriminated union for all canvas element types
 */
export const CanvasElementUnionSchema = z.discriminatedUnion('type', [
  CanvasNodeSchema,
  CanvasEdgeSchema,
]);

/**
 * Zod schema for CanvasDocument
 */
export const CanvasDocumentSchema = z.object({
  schemaVersion: z.literal(CANVAS_SCHEMA_VERSION),
  id: z.string(),
  title: z.string(),
  viewport: CanvasViewportSchema,
  elements: z.record(z.string(), CanvasElementUnionSchema),
  elementOrder: z.array(z.string()),
  metadata: z.record(z.string(), z.unknown()),
  capabilities: CanvasCapabilitiesSchema,
  createdAt: z.string(), // ISO date string
  updatedAt: z.string(), // ISO date string
});

// ============================================================================
// Type Inference
// ============================================================================

export type Point = z.infer<typeof PointSchema>;
export type Bounds = z.infer<typeof BoundsSchema>;
export type Transform = z.infer<typeof TransformSchema>;
export type CanvasViewport = z.infer<typeof CanvasViewportSchema>;
export type CanvasElementMetadata = z.infer<typeof CanvasElementMetadataSchema>;
export type CanvasCapabilities = z.infer<typeof CanvasCapabilitiesSchema>;
export type CanvasElement = z.infer<typeof CanvasElementUnionSchema>;
export type CanvasNode = z.infer<typeof CanvasNodeSchema>;
export type CanvasEdge = z.infer<typeof CanvasEdgeSchema>;
export type CanvasDocument = z.infer<typeof CanvasDocumentSchema>;

// ============================================================================
// Validation Functions
// ============================================================================

/**
 * Validate a CanvasDocument against the schema
 */
export function validateCanvasDocument(data: unknown): CanvasDocument {
  return CanvasDocumentSchema.parse(data);
}

/**
 * Safely validate a CanvasDocument, returning success or error
 */
export function safeValidateCanvasDocument(
  data: unknown,
): z.ZodSafeParseResult<CanvasDocument> {
  return CanvasDocumentSchema.safeParse(data);
}

// ============================================================================
// Migrations
// ============================================================================

export interface Migration {
  readonly fromVersion: string;
  readonly toVersion: string;
  readonly migrate: (data: unknown) => unknown;
}

export const MIGRATIONS: Record<string, Migration> = {
  // No migrations yet for version 1.0.0
};

/**
 * Get the latest schema version
 */
export function getLatestSchemaVersion(): string {
  return CANVAS_SCHEMA_VERSION;
}

/**
 * Migrate a document to the latest schema version
 */
export function migrateToLatest(data: unknown): CanvasDocument {
  // If no schemaVersion, assume legacy and apply default migration
  if (typeof data !== 'object' || data === null) {
    throw new Error('Invalid document: not an object');
  }

  const doc = data as Record<string, unknown>;
  const currentVersion = (doc.schemaVersion as string) ?? '0.0.0';

  if (currentVersion === CANVAS_SCHEMA_VERSION) {
    return validateCanvasDocument(data);
  }

  // Apply migrations in sequence
  let migrated = data;
  let version = currentVersion;

  // For now, since we only have 1.0.0, just validate
  // Future: iterate through MIGRATIONS to upgrade step by step

  return validateCanvasDocument(migrated);
}

// ============================================================================
// Serialization
// ============================================================================

/**
 * Serialize a CanvasDocument to JSON
 */
export function serializeCanvasDocument(document: CanvasDocument): string {
  return JSON.stringify(document);
}

/**
 * Deserialize a CanvasDocument from JSON and validate it
 */
export function deserializeCanvasDocument(json: string): CanvasDocument {
  const parsed = JSON.parse(json);
  return migrateToLatest(parsed);
}
