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
// Canvas Group
// ============================================================================

export const CanvasGroupSchema = CanvasElementBaseSchema.extend({
  type: z.literal('group'),
  children: z.array(z.string()),
  collapsed: z.boolean().default(false),
  data: z.object({
    label: z.string(),
  }),
});

export type CanvasGroup = z.infer<typeof CanvasGroupSchema>;

// ============================================================================
// Canvas Element Union
// ============================================================================

export const CanvasElementSchema = z.union([
  CanvasNodeSchema,
  CanvasEdgeSchema,
  CanvasGroupSchema,
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
// Canvas Selection and Layers
// ============================================================================

export const CanvasSelectionSchema = z.object({
  elementIds: z.array(z.string()).default([]),
  nodeIds: z.array(z.string()).default([]),
  edgeIds: z.array(z.string()).default([]),
  groupIds: z.array(z.string()).default([]),
});

export type CanvasSelection = z.infer<typeof CanvasSelectionSchema>;

export const CanvasLayerSchema = z.object({
  id: z.string(),
  name: z.string(),
  visible: z.boolean().default(true),
  locked: z.boolean().default(false),
  zIndex: z.number().int().default(0),
  elementIds: z.array(z.string()).default([]),
});

export type CanvasLayer = z.infer<typeof CanvasLayerSchema>;

// ============================================================================
// Canvas Document
// ============================================================================

const CanvasDocumentBaseSchema = z.object({
  schemaVersion: z.string().default('1.0.0'),
  documentId: z.string(),
  name: z.string(),
  description: z.string().optional(),
  elements: z.record(z.string(), CanvasElementSchema),
  viewport: CanvasViewportSchema,
  selection: CanvasSelectionSchema.default({
    elementIds: [],
    nodeIds: [],
    edgeIds: [],
    groupIds: [],
  }),
  layers: z.record(z.string(), CanvasLayerSchema).default({}),
  metadata: z.object({
    createdAt: z.string(),
    updatedAt: z.string(),
    author: z.string().optional(),
    tags: z.array(z.string()).optional(),
  }),
});

export const CanvasDocumentSchema = CanvasDocumentBaseSchema.superRefine((doc, ctx) => {
  const elementIds = new Set(Object.keys(doc.elements));
  const nodeIds = new Set<string>();
  const edgeIds = new Set<string>();
  const groupIds = new Set<string>();

  for (const [elementKey, element] of Object.entries(doc.elements)) {
    if (element.id !== elementKey) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['elements', elementKey, 'id'],
        message: 'Element id must match its record key',
      });
    }

    if (element.type === 'node') nodeIds.add(element.id);
    if (element.type === 'edge') edgeIds.add(element.id);
    if (element.type === 'group') groupIds.add(element.id);
  }

  for (const [elementKey, element] of Object.entries(doc.elements)) {
    if (element.type === 'edge') {
      if (!nodeIds.has(element.source)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['elements', elementKey, 'source'],
          message: `Edge source "${element.source}" does not reference a node`,
        });
      }
      if (!nodeIds.has(element.target)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['elements', elementKey, 'target'],
          message: `Edge target "${element.target}" does not reference a node`,
        });
      }
    }

    if (element.type === 'group') {
      for (const childId of element.children) {
        if (!elementIds.has(childId)) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ['elements', elementKey, 'children'],
            message: `Group child "${childId}" does not reference an element`,
          });
        }
      }
    }
  }

  for (const [layerKey, layer] of Object.entries(doc.layers)) {
    if (layer.id !== layerKey) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['layers', layerKey, 'id'],
        message: 'Layer id must match its record key',
      });
    }
    for (const elementId of layer.elementIds) {
      if (!elementIds.has(elementId)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['layers', layerKey, 'elementIds'],
          message: `Layer element "${elementId}" does not reference an element`,
        });
      }
    }
  }

  for (const selectedId of doc.selection.elementIds) {
    if (!elementIds.has(selectedId)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['selection', 'elementIds'],
        message: `Selected element "${selectedId}" does not reference an element`,
      });
    }
  }
  for (const selectedId of doc.selection.nodeIds) {
    if (!nodeIds.has(selectedId)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['selection', 'nodeIds'],
        message: `Selected node "${selectedId}" does not reference a node`,
      });
    }
  }
  for (const selectedId of doc.selection.edgeIds) {
    if (!edgeIds.has(selectedId)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['selection', 'edgeIds'],
        message: `Selected edge "${selectedId}" does not reference an edge`,
      });
    }
  }
  for (const selectedId of doc.selection.groupIds) {
    if (!groupIds.has(selectedId)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['selection', 'groupIds'],
        message: `Selected group "${selectedId}" does not reference a group`,
      });
    }
  }
});

export type CanvasDocument = z.infer<typeof CanvasDocumentSchema>;

// ============================================================================
// Migration Functions
// ============================================================================

export interface Migration {
  fromVersion: string;
  toVersion: string;
  migrate: (doc: unknown) => unknown;
}

const migrations: readonly Migration[] = [
  {
    fromVersion: '0.0.0',
    toVersion: '1.0.0',
    migrate: (doc: unknown): unknown => {
      if (typeof doc !== 'object' || doc === null) {
        throw new Error('Invalid document structure');
      }
      return {
        ...doc,
        schemaVersion: '1.0.0',
        selection: {
          elementIds: [],
          nodeIds: [],
          edgeIds: [],
          groupIds: [],
        },
        layers: {},
      };
    },
  },
];

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

  let migrated = doc;
  let version = currentVersion;

  while (version !== targetVersion) {
    const migration = migrations.find((candidate) => candidate.fromVersion === version);
    if (migration === undefined) {
      throw new Error(`No canvas document migration from ${version} to ${targetVersion}`);
    }
    migrated = migration.migrate(migrated);
    version = migration.toVersion;
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
    selection: {
      elementIds: [],
      nodeIds: [],
      edgeIds: [],
      groupIds: [],
    },
    layers: {},
    metadata: {
      createdAt: now,
      updatedAt: now,
    },
  };
}
