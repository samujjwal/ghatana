import { z } from 'zod';

import type { DrawingStroke } from '@/lib/canvas/DrawingManager';
import type { HierarchicalNode } from '@/lib/canvas/HierarchyManager';
import type { Connection } from '@/lib/canvas/NodeManipulation';

export const CANVAS_IMPORT_SCHEMA_VERSION = '1.0.0';

const finiteNumberSchema = z.number().finite();
const pointSchema = z.object({
  x: finiteNumberSchema,
  y: finiteNumberSchema,
});
const viewportSchema = pointSchema.extend({
  zoom: finiteNumberSchema.positive(),
});
const sizeSchema = z.object({
  width: finiteNumberSchema.positive(),
  height: finiteNumberSchema.positive(),
});
const paddingSchema = z.object({
  top: finiteNumberSchema,
  right: finiteNumberSchema,
  bottom: finiteNumberSchema,
  left: finiteNumberSchema,
});

const connectionTypeSchema = z.enum([
  'flow',
  'dependency',
  'reference',
  'inheritance',
  'composition',
  'association',
  'aggregation',
]);
const connectionStyleSchema = z.enum(['straight', 'bezier', 'step', 'smart']);

const importedNodeSchema = z.object({
  id: z.string().trim().min(1),
  type: z.string().trim().min(1).default('component'),
  position: pointSchema,
  size: sizeSchema.default({ width: 180, height: 100 }),
  data: z.record(z.string(), z.unknown()).default({}),
  parentId: z.string().trim().min(1).optional(),
  children: z.array(z.string().trim().min(1)).default([]),
  childrenVisible: z.boolean().default(true),
  depth: z.number().int().nonnegative().default(0),
  path: z.array(z.string().trim().min(1)).default([]),
  isContainer: z.boolean().default(false),
  containerType: z.enum(['group', 'frame', 'artifact']).optional(),
  autoResize: z.boolean().default(false),
  padding: paddingSchema.default({ top: 16, right: 16, bottom: 16, left: 16 }),
  childLayout: z.enum(['free', 'vertical', 'horizontal', 'grid']).default('free'),
  childSpacing: finiteNumberSchema.nonnegative().default(16),
  childAlignment: z.enum(['start', 'center', 'end']).default('start'),
  minZoom: finiteNumberSchema.positive().optional(),
  maxZoom: finiteNumberSchema.positive().optional(),
  childrenMinZoom: finiteNumberSchema.positive().optional(),
});

const importedConnectionSchema = z.object({
  id: z.string().trim().min(1),
  source: z.string().trim().min(1),
  target: z.string().trim().min(1),
  sourceHandle: z.string().trim().min(1).optional(),
  targetHandle: z.string().trim().min(1).optional(),
  type: connectionTypeSchema.default('flow'),
  label: z.string().optional(),
  style: connectionStyleSchema.default('bezier'),
  animated: z.boolean().optional(),
  bidirectional: z.boolean().optional(),
  data: z.record(z.string(), z.unknown()).optional(),
});

const importedDrawingStrokeSchema = z.object({
  id: z.string().trim().min(1),
  points: z.array(pointSchema.extend({ pressure: finiteNumberSchema.optional() })).min(1),
  color: z.string().trim().min(1),
  width: finiteNumberSchema.positive(),
  tool: z.enum(['pen', 'pencil', 'marker', 'highlighter', 'eraser']),
  opacity: finiteNumberSchema.min(0).max(1).optional(),
  timestamp: finiteNumberSchema,
});

const importedCanvasDocumentSchema = z.object({
  nodes: z.array(importedNodeSchema),
  connections: z.array(importedConnectionSchema).default([]),
  drawings: z.array(importedDrawingStrokeSchema).default([]),
  viewport: viewportSchema.default({ x: 0, y: 0, zoom: 0.5 }),
  metadata: z
    .object({
      version: z.string().trim().min(1).default(CANVAS_IMPORT_SCHEMA_VERSION),
      exportDate: z.string().optional(),
      projectId: z.string().optional(),
    })
    .default({ version: CANVAS_IMPORT_SCHEMA_VERSION }),
});

export interface CanvasImportDocument {
  readonly nodes: HierarchicalNode[];
  readonly connections: Connection[];
  readonly drawings: DrawingStroke[];
  readonly viewport: { readonly x: number; readonly y: number; readonly zoom: number };
  readonly metadata: {
    readonly version: string;
    readonly exportDate?: string;
    readonly projectId?: string;
    readonly migratedFromVersion?: string;
  };
}

export type CanvasImportFailureReason =
  | 'invalid-json'
  | 'invalid-schema'
  | 'duplicate-node-id'
  | 'dangling-connection';

export interface CanvasImportValidationSuccess {
  readonly ok: true;
  readonly document: CanvasImportDocument;
}

export interface CanvasImportValidationFailure {
  readonly ok: false;
  readonly reason: CanvasImportFailureReason;
  readonly message: string;
}

export type CanvasImportValidationResult =
  | CanvasImportValidationSuccess
  | CanvasImportValidationFailure;

function unwrapCanvasPayload(parsed: unknown): unknown {
  if (
    parsed &&
    typeof parsed === 'object' &&
    'data' in parsed &&
    !('nodes' in parsed)
  ) {
    return (parsed as { readonly data: unknown }).data;
  }

  return parsed;
}

function withNormalizedNodePaths(nodes: HierarchicalNode[]): HierarchicalNode[] {
  return nodes.map((node) => ({
    ...node,
    path: node.path.length > 0 ? node.path : node.parentId ? [node.parentId, node.id] : [node.id],
  }));
}

function toFailureMessage(error: z.ZodError): string {
  const firstIssue = error.issues[0];
  if (!firstIssue) {
    return 'Canvas import failed because the file does not match the canvas schema.';
  }

  const path = firstIssue.path.length > 0 ? firstIssue.path.join('.') : 'document';
  return `Canvas import failed because ${path} ${firstIssue.message.toLowerCase()}.`;
}

export function validateAndMigrateCanvasImport(json: string): CanvasImportValidationResult {
  let parsed: unknown;

  try {
    parsed = JSON.parse(json);
  } catch {
    return {
      ok: false,
      reason: 'invalid-json',
      message: 'Canvas import failed because the selected file is not valid JSON.',
    };
  }

  const payload = unwrapCanvasPayload(parsed);
  const result = importedCanvasDocumentSchema.safeParse(payload);

  if (!result.success) {
    return {
      ok: false,
      reason: 'invalid-schema',
      message: toFailureMessage(result.error),
    };
  }

  const nodeIds = new Set<string>();
  for (const node of result.data.nodes) {
    if (nodeIds.has(node.id)) {
      return {
        ok: false,
        reason: 'duplicate-node-id',
        message: `Canvas import failed because node id "${node.id}" appears more than once.`,
      };
    }
    nodeIds.add(node.id);
  }

  const danglingConnection = result.data.connections.find(
    (connection) => !nodeIds.has(connection.source) || !nodeIds.has(connection.target)
  );
  if (danglingConnection) {
    return {
      ok: false,
      reason: 'dangling-connection',
      message: `Canvas import failed because connection "${danglingConnection.id}" references a missing node.`,
    };
  }

  const importedVersion = result.data.metadata.version;

  return {
    ok: true,
    document: {
      nodes: withNormalizedNodePaths(result.data.nodes),
      connections: result.data.connections,
      drawings: result.data.drawings,
      viewport: result.data.viewport,
      metadata: {
        ...result.data.metadata,
        version: CANVAS_IMPORT_SCHEMA_VERSION,
        ...(importedVersion !== CANVAS_IMPORT_SCHEMA_VERSION
          ? { migratedFromVersion: importedVersion }
          : {}),
      },
    },
  };
}
