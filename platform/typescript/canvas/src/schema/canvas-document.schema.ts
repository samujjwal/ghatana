/**
 * @fileoverview Canvas document schema and migrations.
 *
 * Provides Zod schema for CanvasDocument with versioning and migration support.
 * Ensures serialization/deserialization compatibility and enables round-trip tests.
 *
 * @doc.type module
 * @doc.purpose Canvas document schema and migrations
 * @doc.layer canvas
 */

import { z } from 'zod';

// ============================================================================
// Canvas Element Types
// ============================================================================

export const CanvasElementTypeSchema = z.enum([
  'node',
  'edge',
  'group',
  'container',
  'text',
  'shape',
  'image',
  'connector',
]);

export type CanvasElementType = z.infer<typeof CanvasElementTypeSchema>;

// ============================================================================
// Position and Size
// ============================================================================

export const PositionSchema = z.object({
  x: z.number(),
  y: z.number(),
});

export const SizeSchema = z.object({
  width: z.number().min(0),
  height: z.number().min(0),
});

// ============================================================================
// Canvas Element Base
// ============================================================================

export const CanvasElementBaseSchema = z.object({
  id: z.string(),
  type: CanvasElementTypeSchema,
  position: PositionSchema,
  size: SizeSchema.optional(),
  rotation: z.number().default(0),
  locked: z.boolean().default(false),
  hidden: z.boolean().default(false),
  metadata: z.record(z.string(), z.unknown()).optional(),
});

// ============================================================================
// Canvas Node
// ============================================================================

export const CanvasNodeSchema = CanvasElementBaseSchema.extend({
  type: z.literal('node'),
  data: z.object({
    label: z.string(),
    contractName: z.string().optional(),
    props: z.record(z.string(), z.unknown()).optional(),
  }),
});

export type CanvasNode = z.infer<typeof CanvasNodeSchema>;

// ============================================================================
// Canvas Edge
// ============================================================================

export const CanvasEdgeSchema = CanvasElementBaseSchema.extend({
  type: z.literal('edge'),
  source: z.string(),
  target: z.string(),
  data: z.object({
    label: z.string().optional(),
    slot: z.string().optional(),
    type: z.enum(['data', 'slot', 'dependency']).optional(),
  }),
});

export type CanvasEdge = z.infer<typeof CanvasEdgeSchema>;

// ============================================================================
// Canvas Element Union
// ============================================================================

export const CanvasElementSchema = z.union([
  CanvasNodeSchema,
  CanvasEdgeSchema,
]);

export type CanvasElement = z.infer<typeof CanvasElementSchema>;

// ============================================================================
// Canvas Viewport
// ============================================================================

export const CanvasViewportSchema = z.object({
  x: z.number().default(0),
  y: z.number().default(0),
  zoom: z.number().min(0.1).max(10).default(1),
});

export type CanvasViewport = z.infer<typeof CanvasViewportSchema>;

// ============================================================================
// Canvas Document
// ============================================================================

export const CanvasDocumentSchema = z.object({
  schemaVersion: z.string().default('1.0.0'),
  documentId: z.string(),
  name: z.string(),
  description: z.string().optional(),
  elements: z.record(z.string(), CanvasElementSchema),
  viewport: CanvasViewportSchema,
  metadata: z.object({
    createdAt: z.string(),
    updatedAt: z.string(),
    author: z.string().optional(),
    tags: z.array(z.string()).optional(),
  }),
});

export type CanvasDocument = z.infer<typeof CanvasDocumentSchema>;

// ============================================================================
// Migration Functions
// ============================================================================

export interface Migration {
  version: string;
  migrate: (doc: unknown) => CanvasDocument;
}

const migrations: Record<string, Migration> = {
  '1.0.0': {
    version: '1.0.0',
    migrate: (doc: unknown): CanvasDocument => {
      // Identity migration for current version
      return CanvasDocumentSchema.parse(doc);
    },
  },
};

export function migrateCanvasDocument(doc: unknown, targetVersion: string = '1.0.0'): CanvasDocument {
  const parsed = z.object({
    schemaVersion: z.string().optional(),
  }).safeParse(doc);

  if (!parsed.success) {
    throw new Error('Invalid document structure');
  }

  const currentVersion = parsed.data.schemaVersion || '0.0.0';

  if (currentVersion === targetVersion) {
    return CanvasDocumentSchema.parse(doc);
  }

  // Apply migrations in order
  let migrated = doc;
  for (const version of Object.keys(migrations).sort()) {
    if (version > currentVersion && version <= targetVersion) {
      migrated = migrations[version].migrate(migrated);
    }
  }

  return CanvasDocumentSchema.parse(migrated);
}

// ============================================================================
// Serialization/Deserialization
// ============================================================================

export function serializeCanvasDocument(doc: CanvasDocument): string {
  return JSON.stringify(doc, null, 2);
}

export function deserializeCanvasDocument(json: string): CanvasDocument {
  const parsed = JSON.parse(json);
  return migrateCanvasDocument(parsed);
}

// ============================================================================
// Validation
// ============================================================================

export function validateCanvasDocument(doc: unknown): {
  valid: boolean;
  errors: string[];
  document?: CanvasDocument;
} {
  const result = CanvasDocumentSchema.safeParse(doc);

  if (result.success) {
    return {
      valid: true,
      errors: [],
      document: result.data,
    };
  }

  return {
    valid: false,
    errors: result.error.issues.map((e) => `${e.path.join('.')}: ${e.message}`),
  };
}

// ============================================================================
// Version Constant and Factory
// ============================================================================

/**
 * Current schema version for CanvasDocument.
 * Bump this on breaking schema changes and add a corresponding migration.
 */
export const CANVAS_DOCUMENT_SCHEMA_VERSION = '1.0.0';

/**
 * Creates a minimal empty CanvasDocument with the current schema version.
 *
 * @param documentId - Stable unique ID (use `crypto.randomUUID()` at the call site).
 * @param name - Human-readable document name.
 */
export function createCanvasDocument(
  documentId: string,
  name: string,
): CanvasDocument {
  const now = new Date().toISOString();
  return {
    schemaVersion: CANVAS_DOCUMENT_SCHEMA_VERSION,
    documentId,
    name,
    elements: {},
    viewport: { x: 0, y: 0, zoom: 1 },
    metadata: {
      createdAt: now,
      updatedAt: now,
    },
  };
}
