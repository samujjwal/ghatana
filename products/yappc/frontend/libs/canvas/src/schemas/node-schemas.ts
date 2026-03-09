import { z } from 'zod';

// Base node schema - all nodes extend this
export const BaseNodeSchema = z.object({
  id: z.string().min(1),
  type: z.string().min(1),
  position: z.object({
    x: z.number(),
    y: z.number(),
  }),
  data: z.record(z.string(), z.unknown()).optional(),
  width: z.number().optional(),
  height: z.number().optional(),
  selected: z.boolean().optional(),
  dragging: z.boolean().optional(),
  deletable: z.boolean().optional(),
  selectable: z.boolean().optional(),
  connectable: z.boolean().optional(),
  style: z.record(z.string(), z.unknown()).optional(),
  className: z.string().optional(),
});

// Process Node Schema
export const ProcessNodeDataSchema = z.object({
  label: z.string().min(1),
  description: z.string().optional(),
  icon: z.string().optional(),
  color: z.string().optional(),
  category: z.string().optional(),
  properties: z.record(z.string(), z.unknown()).optional(),
  executable: z.boolean().optional(),
  timeout: z.number().optional(),
  retryCount: z.number().optional(),
});

export const ProcessNodeSchema = BaseNodeSchema.extend({
  type: z.literal('process'),
  data: ProcessNodeDataSchema,
});

// Decision Node Schema
export const DecisionNodeDataSchema = z.object({
  label: z.string().min(1),
  description: z.string().optional(),
  conditions: z
    .array(
      z.object({
        id: z.string(),
        expression: z.string(),
        label: z.string().optional(),
      })
    )
    .optional(),
  defaultPath: z.string().optional(),
  icon: z.string().optional(),
  color: z.string().optional(),
});

export const DecisionNodeSchema = BaseNodeSchema.extend({
  type: z.literal('decision'),
  data: DecisionNodeDataSchema,
});

// Database Node Schema
export const DatabaseNodeDataSchema = z.object({
  label: z.string().min(1),
  description: z.string().optional(),
  dbType: z
    .enum(['postgresql', 'mysql', 'mongodb', 'redis', 'sqlite'])
    .optional(),
  connectionString: z.string().optional(),
  tableName: z.string().optional(),
  query: z.string().optional(),
  icon: z.string().optional(),
  color: z.string().optional(),
  credentials: z
    .object({
      host: z.string().optional(),
      port: z.number().optional(),
      username: z.string().optional(),
      database: z.string().optional(),
    })
    .optional(),
});

export const DatabaseNodeSchema = BaseNodeSchema.extend({
  type: z.literal('database'),
  data: DatabaseNodeDataSchema,
});

// Group Node Schema
export const GroupNodeDataSchema = z.object({
  label: z.string().min(1),
  description: z.string().optional(),
  childNodeIds: z.array(z.string()),
  collapsed: z.boolean().optional(),
  color: z.string().optional(),
  borderStyle: z.enum(['solid', 'dashed', 'dotted']).optional(),
});

export const GroupNodeSchema = BaseNodeSchema.extend({
  type: z.literal('group'),
  data: GroupNodeDataSchema,
});

// Union of all node types
export const NodeSchema = z.discriminatedUnion('type', [
  ProcessNodeSchema,
  DecisionNodeSchema,
  DatabaseNodeSchema,
  GroupNodeSchema,
]);

// Type inference
/**
 *
 */
export type BaseNode = z.infer<typeof BaseNodeSchema>;
/**
 *
 */
export type ProcessNodeData = z.infer<typeof ProcessNodeDataSchema>;
/**
 *
 */
export type ProcessNode = z.infer<typeof ProcessNodeSchema>;
/**
 *
 */
export type DecisionNodeData = z.infer<typeof DecisionNodeDataSchema>;
/**
 *
 */
export type DecisionNode = z.infer<typeof DecisionNodeSchema>;
/**
 *
 */
export type DatabaseNodeData = z.infer<typeof DatabaseNodeDataSchema>;
/**
 *
 */
export type DatabaseNode = z.infer<typeof DatabaseNodeSchema>;
/**
 *
 */
export type GroupNodeData = z.infer<typeof GroupNodeDataSchema>;
/**
 *
 */
export type GroupNode = z.infer<typeof GroupNodeSchema>;
/**
 *
 */
export type CanvasNode = z.infer<typeof NodeSchema>;

// Node validation helpers
export const validateNode = (node: unknown): CanvasNode => {
  return NodeSchema.parse(node);
};

export const isValidNode = (node: unknown): node is CanvasNode => {
  return NodeSchema.safeParse(node).success;
};

// Node creation helpers
export const createProcessNode = (
  id: string,
  position: { x: number; y: number },
  data: ProcessNodeData
): ProcessNode => ({
  id,
  type: 'process',
  position,
  data,
  selectable: true,
  connectable: true,
  deletable: true,
});

export const createDecisionNode = (
  id: string,
  position: { x: number; y: number },
  data: DecisionNodeData
): DecisionNode => ({
  id,
  type: 'decision',
  position,
  data,
  selectable: true,
  connectable: true,
  deletable: true,
});

export const createDatabaseNode = (
  id: string,
  position: { x: number; y: number },
  data: DatabaseNodeData
): DatabaseNode => ({
  id,
  type: 'database',
  position,
  data,
  selectable: true,
  connectable: true,
  deletable: true,
});

export const createGroupNode = (
  id: string,
  position: { x: number; y: number },
  data: GroupNodeData
): GroupNode => ({
  id,
  type: 'group',
  position,
  data,
  selectable: true,
  connectable: false,
  deletable: true,
});
