/**
 * @fileoverview Canvas document Zod schemas with migration support.
 *
 * Provides runtime validation and versioned schema support for canvas documents
 * (HybridCanvasState, elements, nodes, edges, viewport, selection, etc.).
 * Enables safe serialization/deserialization and data migration across schema versions.
 *
 * @doc.type module
 * @doc.purpose Canvas document schema validation and migrations
 * @doc.layer canvas
 * @doc.pattern SchemaValidation
 */

import { z } from 'zod';

// ============================================================================
// SCHEMA VERSIONING
// ============================================================================

export const CURRENT_CANVAS_SCHEMA_VERSION = '1.0.0';

export type CanvasSchemaVersion = '1.0.0' | '0.9.0' | '0.8.0';

// ============================================================================
// COORDINATE SYSTEMS
// ============================================================================

export const PointSchema = z.object({
  x: z.number(),
  y: z.number(),
});

export const RectSchema = z.object({
  x: z.number(),
  y: z.number(),
  width: z.number(),
  height: z.number(),
});

// ============================================================================
// VIEWPORT
// ============================================================================

export const ViewportStateSchema = z.object({
  x: z.number(),
  y: z.number(),
  zoom: z.number().min(0.01).max(10),
  minZoom: z.number().min(0.01).max(1),
  maxZoom: z.number().min(1).max(20),
});

export const ViewportBoundsSchema = z.object({
  left: z.number(),
  top: z.number(),
  right: z.number(),
  bottom: z.number(),
  width: z.number(),
  height: z.number(),
});

// ============================================================================
// SELECTION
// ============================================================================

export const SelectionStateSchema = z.object({
  elementIds: z.array(z.string()),
  nodeIds: z.array(z.string()),
  edgeIds: z.array(z.string()),
  isMultiSelect: z.boolean(),
  bounds: RectSchema.nullable(),
});

// ============================================================================
// CANVAS ELEMENTS (Freeform Layer)
// ============================================================================

export const CanvasShadowSchema = z.object({
  offsetX: z.number(),
  offsetY: z.number(),
  blur: z.number(),
  color: z.string(),
});

export const CanvasElementStyleSchema = z.object({
  fill: z.string().optional(),
  stroke: z.string().optional(),
  strokeWidth: z.number().optional(),
  strokeDasharray: z.string().optional(),
  shadow: CanvasShadowSchema.optional(),
  borderRadius: z.number().optional(),
});

export const CanvasElementSchema = z.object({
  id: z.string().min(1),
  type: z.string().min(1),
  position: PointSchema,
  size: z.object({
    width: z.number().positive(),
    height: z.number().positive(),
  }),
  rotation: z.number().optional(),
  opacity: z.number().min(0).max(1).optional(),
  zIndex: z.number().int().optional(),
  locked: z.boolean().optional(),
  hidden: z.boolean().optional(),
  data: z.record(z.string(), z.unknown()),
  parentId: z.string().optional(),
  style: CanvasElementStyleSchema.optional(),
});

// ============================================================================
// GRAPH NODES & EDGES (Graph Layer)
// ============================================================================

export const CanvasNodeMetadataSchema = z.object({
  syncElement: z.string().optional(),
  layer: z.enum(['graph', 'overlay']).optional(),
}).optional();

export const CanvasNodeSchema: z.ZodType<any> = z.object({
  id: z.string().min(1),
  type: z.string().min(1),
  position: PointSchema,
  data: z.record(z.string(), z.unknown()),
  __canvas: CanvasNodeMetadataSchema.optional(),
}).passthrough(); // Allow additional ReactFlow node properties

export const CanvasEdgeMetadataSchema = z.object({
  syncConnection: z.string().optional(),
  layer: z.enum(['graph', 'overlay']).optional(),
}).optional();

export const CanvasEdgeSchema: z.ZodType<any> = z.object({
  id: z.string().min(1),
  source: z.string().min(1),
  target: z.string().min(1),
  data: z.record(z.string(), z.unknown()),
  __canvas: CanvasEdgeMetadataSchema.optional(),
}).passthrough(); // Allow additional ReactFlow edge properties

// ============================================================================
// LAYER CONFIGURATION
// ============================================================================

export const LayerConfigSchema = z.object({
  id: z.enum(['freeform', 'graph', 'overlay']),
  visible: z.boolean(),
  opacity: z.number().min(0).max(1),
  interactive: z.boolean(),
  zIndex: z.number().int(),
});

// ============================================================================
// GRID CONFIGURATION
// ============================================================================

export const GridConfigSchema = z.object({
  visible: z.boolean(),
  size: z.number().positive(),
  snap: z.boolean(),
  color: z.string(),
  type: z.enum(['lines', 'dots', 'none']),
});

// ============================================================================
// CANVAS STATE
// ============================================================================

export const HybridCanvasStateSchema = z.object({
  schemaVersion: z.literal(CURRENT_CANVAS_SCHEMA_VERSION),
  mode: z.enum(['hybrid-freeform', 'hybrid-graph', 'freeform-only', 'graph-only']),
  activeLayer: z.enum(['freeform', 'graph', 'both']),
  viewport: ViewportStateSchema,
  selection: SelectionStateSchema,
  layers: z.object({
    freeform: LayerConfigSchema,
    graph: LayerConfigSchema,
    overlay: LayerConfigSchema,
  }),
  elements: z.array(CanvasElementSchema),
  nodes: z.array(CanvasNodeSchema),
  edges: z.array(CanvasEdgeSchema),
  tool: z.string(),
  readOnly: z.boolean(),
  dimensions: z.object({
    width: z.number().positive(),
    height: z.number().positive(),
  }),
  grid: GridConfigSchema,
});

// ============================================================================
// HISTORY
// ============================================================================

export const HistoryEntrySnapshotSchema = z.object({
  elements: z.array(CanvasElementSchema),
  nodes: z.array(CanvasNodeSchema),
  edges: z.array(CanvasEdgeSchema),
});

export const HistoryEntrySchema = z.object({
  timestamp: z.number().int().positive(),
  action: z.string().min(1),
  snapshot: HistoryEntrySnapshotSchema,
});

export const HistoryStateSchema = z.object({
  past: z.array(HistoryEntrySchema),
  future: z.array(HistoryEntrySchema),
  maxSize: z.number().int().positive().max(1000),
});

// ============================================================================
// MIGRATIONS
// ============================================================================

export interface CanvasMigration {
  fromVersion: CanvasSchemaVersion;
  toVersion: CanvasSchemaVersion;
  migrate: (data: unknown) => unknown;
}

/**
 * Migration from 0.9.0 to 1.0.0
 * - Added schemaVersion field
 * - Renamed bounds.x/y to position.x/y in elements
 * - Added layer metadata to nodes/edges
 */
const migration_0_9_0_to_1_0_0: CanvasMigration = {
  fromVersion: '0.9.0',
  toVersion: '1.0.0',
  migrate: (data: unknown) => {
    const old = data as Record<string, unknown>;
    
    // Migrate elements: bounds.x/y -> position.x/y
    const elements = (old.elements as any[] | undefined)?.map((el: any) => ({
      ...el,
      position: {
        x: el.bounds?.x ?? el.position?.x ?? 0,
        y: el.bounds?.y ?? el.position?.y ?? 0,
      },
      size: {
        width: el.bounds?.width ?? el.size?.width ?? 100,
        height: el.bounds?.height ?? el.size?.height ?? 100,
      },
    })) ?? [];

    // Add layer metadata to nodes/edges
    const nodes = (old.nodes as any[] | undefined)?.map((node: any) => ({
      ...node,
      __canvas: {
        ...node.__canvas,
        layer: node.__canvas?.layer ?? 'graph',
      },
    })) ?? [];

    const edges = (old.edges as any[] | undefined)?.map((edge: any) => ({
      ...edge,
      __canvas: {
        ...edge.__canvas,
        layer: edge.__canvas?.layer ?? 'graph',
      },
    })) ?? [];

    return {
      ...old,
      schemaVersion: '1.0.0',
      elements,
      nodes,
      edges,
    };
  },
};

/**
 * Migration from 0.8.0 to 0.9.0
 * - Added selection.bounds field
 * - Added grid.type field
 */
const migration_0_8_0_to_0_9_0: CanvasMigration = {
  fromVersion: '0.8.0',
  toVersion: '0.9.0',
  migrate: (data: unknown) => {
    const old = data as Record<string, unknown>;
    
    return {
      ...old,
      schemaVersion: '0.9.0',
      selection: {
        ...(old.selection as Record<string, unknown>),
        bounds: null,
      },
      grid: {
        ...(old.grid as Record<string, unknown>),
        type: (old.grid as any)?.type ?? 'lines',
      },
    };
  },
};

export const CANVAS_MIGRATIONS: readonly CanvasMigration[] = [
  migration_0_8_0_to_0_9_0,
  migration_0_9_0_to_1_0_0,
];

// ============================================================================
// VALIDATION & SERIALIZATION
// ============================================================================

export interface ValidationResult<T> {
  success: boolean;
  data?: T;
  errors: string[];
}

/**
 * Validate a canvas document against the current schema.
 * Automatically migrates older versions if possible.
 */
export function validateCanvasDocument(
  data: unknown,
): ValidationResult<z.infer<typeof HybridCanvasStateSchema>> {
  const errors: string[] = [];
  let currentData = data;

  // Check if data has schemaVersion
  if (typeof currentData === 'object' && currentData !== null) {
    const record = currentData as Record<string, unknown>;
    const version = record.schemaVersion as string | undefined;

    if (!version) {
      // No version - assume oldest and migrate
      for (const migration of CANVAS_MIGRATIONS) {
        try {
          currentData = migration.migrate(currentData);
        } catch (err) {
          errors.push(`Migration from ${migration.fromVersion} to ${migration.toVersion} failed: ${err instanceof Error ? err.message : String(err)}`);
        }
      }
    } else if (version !== CURRENT_CANVAS_SCHEMA_VERSION) {
      // Migrate from known version
      for (const migration of CANVAS_MIGRATIONS) {
        if (migration.fromVersion === version) {
          try {
            currentData = migration.migrate(currentData);
          } catch (err) {
            errors.push(`Migration from ${migration.fromVersion} to ${migration.toVersion} failed: ${err instanceof Error ? err.message : String(err)}`);
          }
        }
      }
    }
  }

  // Validate against current schema
  const result = HybridCanvasStateSchema.safeParse(currentData);
  
  if (!result.success) {
    return {
      success: false,
      errors: [...errors, ...result.error.issues.map(e => `${e.path.join('.')}: ${e.message}`)],
    };
  }

  return {
    success: true,
    data: result.data,
    errors,
  };
}

/**
 * Serialize a canvas document to JSON for storage/transmission.
 */
export function serializeCanvasDocument(
  document: z.infer<typeof HybridCanvasStateSchema>,
): string {
  return JSON.stringify(document, null, 2);
}

/**
 * Deserialize a canvas document from JSON with validation and migration.
 */
export function deserializeCanvasDocument(
  json: string,
): ValidationResult<z.infer<typeof HybridCanvasStateSchema>> {
  try {
    const parsed = JSON.parse(json);
    return validateCanvasDocument(parsed);
  } catch (err) {
    return {
      success: false,
      errors: [`JSON parse error: ${err instanceof Error ? err.message : String(err)}`],
    };
  }
}

/**
 * Create a new canvas document with default values.
 * Does NOT validate the document — use validateCanvasDocument() for validation.
 * This allows creation of intentionally invalid documents for testing.
 */
export function createCanvasDocument(
  options: Partial<z.infer<typeof HybridCanvasStateSchema>> & Record<string, unknown> = {},
): z.infer<typeof HybridCanvasStateSchema> {
  const doc = {
    schemaVersion: CURRENT_CANVAS_SCHEMA_VERSION,
    mode: 'hybrid-freeform' as const,
    activeLayer: 'both' as const,
    viewport: {
      x: 0,
      y: 0,
      zoom: 1,
      minZoom: 0.1,
      maxZoom: 5,
    },
    selection: {
      elementIds: [] as string[],
      nodeIds: [] as string[],
      edgeIds: [] as string[],
      isMultiSelect: false,
      bounds: null,
    },
    layers: {
      freeform: {
        id: 'freeform' as const,
        visible: true,
        opacity: 1,
        interactive: true,
        zIndex: 0,
      },
      graph: {
        id: 'graph' as const,
        visible: true,
        opacity: 1,
        interactive: true,
        zIndex: 1,
      },
      overlay: {
        id: 'overlay' as const,
        visible: true,
        opacity: 1,
        interactive: false,
        zIndex: 2,
      },
    },
    elements: [] as z.infer<typeof CanvasElementSchema>[],
    nodes: [] as z.infer<typeof CanvasNodeSchema>[],
    edges: [] as z.infer<typeof CanvasEdgeSchema>[],
    tool: 'select',
    readOnly: false,
    dimensions: {
      width: 1920,
      height: 1080,
    },
    grid: {
      visible: true,
      size: 20,
      snap: true,
      color: '#e0e0e0',
      type: 'lines' as const,
    },
    ...options,
  };
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return doc as any as z.infer<typeof HybridCanvasStateSchema>;
}
