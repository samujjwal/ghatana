import { z } from 'zod';

import { EdgeSchema } from './edge-schemas';
import { NodeSchema } from './node-schemas';

// Canvas and Layer Schemas
export const LayerSchema = z.object({
  id: z.string(),
  name: z.string().min(1),
  description: z.string().optional(),
  visible: z.boolean().default(true),
  opacity: z.number().min(0).max(1).default(1),
  locked: z.boolean().default(false),
  zIndex: z.number().default(0),
  color: z.string().optional(),
  nodeIds: z.array(z.string()).default([]),
  edgeIds: z.array(z.string()).default([]),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
});

export const ViewportSchema = z.object({
  x: z.number(),
  y: z.number(),
  zoom: z.number().min(0.1).max(10),
});

export const CanvasSettingsSchema = z.object({
  snapToGrid: z.boolean().default(false),
  gridSize: z.number().positive().default(20),
  showGrid: z.boolean().default(false),
  showMinimap: z.boolean().default(true),
  showControls: z.boolean().default(true),
  nodesDraggable: z.boolean().default(true),
  nodesConnectable: z.boolean().default(true),
  elementsSelectable: z.boolean().default(true),
  multiSelectionKeyCode: z.string().default('Meta'),
  deleteKeyCode: z.string().default('Backspace'),
  panOnDrag: z.boolean().default(true),
  zoomOnScroll: z.boolean().default(true),
  zoomOnPinch: z.boolean().default(true),
  fitViewOnInit: z.boolean().default(false),
  attributionPosition: z
    .enum(['top-left', 'top-right', 'bottom-left', 'bottom-right'])
    .default('bottom-right'),
});

export const CanvasMetadataSchema = z.object({
  id: z.string(),
  name: z.string().min(1),
  description: z.string().optional(),
  version: z.number().positive().default(1),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  createdBy: z.string(),
  lastModifiedBy: z.string(),
  tags: z.array(z.string()).default([]),
  category: z.string().optional(),
  isTemplate: z.boolean().default(false),
  isPublic: z.boolean().default(false),
  thumbnail: z.string().optional(),
});

export const CanvasDataSchema = z.object({
  metadata: CanvasMetadataSchema,
  nodes: z.array(NodeSchema).default([]),
  edges: z.array(EdgeSchema).default([]),
  layers: z.array(LayerSchema).default([]),
  viewport: ViewportSchema.default({ x: 0, y: 0, zoom: 1 }),
  settings: CanvasSettingsSchema.default(() => ({}) as unknown),
});

// Canvas operation schemas for collaboration
export const CanvasOperationSchema = z.object({
  id: z.string(),
  type: z.enum(['node', 'edge', 'layer', 'canvas', 'viewport']),
  action: z.enum(['create', 'update', 'delete', 'move', 'select']),
  targetId: z.string().optional(),
  data: z.record(z.string(), z.unknown()),
  timestamp: z.string().datetime(),
  userId: z.string(),
  version: z.number(),
  dependencies: z.array(z.string()).default([]),
});

// Canvas save/load schemas
export const SaveCanvasRequestSchema = z.object({
  canvasData: CanvasDataSchema,
  saveAsTemplate: z.boolean().default(false),
  templateMetadata: z
    .object({
      name: z.string(),
      description: z.string().optional(),
      category: z.string().optional(),
      tags: z.array(z.string()).default([]),
    })
    .optional(),
});

export const LoadCanvasRequestSchema = z.object({
  canvasId: z.string(),
  version: z.number().optional(),
});

// Canvas validation schemas
export const CanvasValidationResultSchema = z.object({
  isValid: z.boolean(),
  errors: z
    .array(
      z.object({
        id: z.string(),
        type: z.enum(['node', 'edge', 'connection', 'layer']),
        elementId: z.string().optional(),
        message: z.string(),
        severity: z.enum(['error', 'warning', 'info']),
        fixSuggestion: z.string().optional(),
      })
    )
    .default([]),
  warnings: z
    .array(
      z.object({
        id: z.string(),
        type: z.enum(['performance', 'accessibility', 'best-practice']),
        message: z.string(),
        recommendation: z.string().optional(),
      })
    )
    .default([]),
  stats: z.object({
    nodeCount: z.number(),
    edgeCount: z.number(),
    layerCount: z.number(),
    isolatedNodes: z.number(),
    cyclicPaths: z.number(),
    maxDepth: z.number(),
  }),
});

// Type inference
/**
 *
 */
export type Layer = z.infer<typeof LayerSchema>;
/**
 *
 */
export type Viewport = z.infer<typeof ViewportSchema>;
/**
 *
 */
export type CanvasSettings = z.infer<typeof CanvasSettingsSchema>;
/**
 *
 */
export type CanvasMetadata = z.infer<typeof CanvasMetadataSchema>;
/**
 *
 */
export type CanvasData = z.infer<typeof CanvasDataSchema>;
/**
 *
 */
export type CanvasOperation = z.infer<typeof CanvasOperationSchema>;
/**
 *
 */
export type SaveCanvasRequest = z.infer<typeof SaveCanvasRequestSchema>;
/**
 *
 */
export type LoadCanvasRequest = z.infer<typeof LoadCanvasRequestSchema>;
/**
 *
 */
export type CanvasValidationResult = z.infer<
  typeof CanvasValidationResultSchema
>;

// Canvas helpers
export const createDefaultLayer = (name: string = 'Default Layer'): Layer => ({
  id: `layer-${Date.now()}`,
  name,
  visible: true,
  opacity: 1,
  locked: false,
  zIndex: 0,
  nodeIds: [],
  edgeIds: [],
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
});

export const createEmptyCanvas = (
  name: string,
  createdBy: string
): CanvasData => ({
  metadata: {
    id: `canvas-${Date.now()}`,
    name,
    version: 1,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    createdBy,
    lastModifiedBy: createdBy,
    tags: [],
    isTemplate: false,
    isPublic: false,
  },
  nodes: [],
  edges: [],
  layers: [createDefaultLayer()],
  viewport: { x: 0, y: 0, zoom: 1 },
  settings: {
    snapToGrid: false,
    gridSize: 20,
    showGrid: false,
    showMinimap: true,
    showControls: true,
    nodesDraggable: true,
    nodesConnectable: true,
    elementsSelectable: true,
    multiSelectionKeyCode: 'Meta',
    deleteKeyCode: 'Backspace',
    panOnDrag: true,
    zoomOnScroll: true,
    zoomOnPinch: true,
    fitViewOnInit: false,
    attributionPosition: 'bottom-right' as const,
  },
});

export const validateCanvasData = (data: unknown): CanvasData => {
  return CanvasDataSchema.parse(data);
};

export const isValidCanvasData = (data: unknown): data is CanvasData => {
  return CanvasDataSchema.safeParse(data).success;
};
