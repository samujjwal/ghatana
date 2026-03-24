import { z } from 'zod';

// Base edge schema
export const BaseEdgeSchema = z.object({
  id: z.string().min(1),
  source: z.string().min(1),
  target: z.string().min(1),
  type: z.string().optional(),
  sourceHandle: z.string().optional(),
  targetHandle: z.string().optional(),
  data: z.record(z.string(), z.unknown()).optional(),
  style: z.record(z.string(), z.unknown()).optional(),
  className: z.string().optional(),
  animated: z.boolean().optional(),
  selected: z.boolean().optional(),
  deletable: z.boolean().optional(),
  label: z.string().optional(),
  labelStyle: z.record(z.string(), z.unknown()).optional(),
  labelShowBg: z.boolean().optional(),
  labelBgStyle: z.record(z.string(), z.unknown()).optional(),
});

// Standard Edge Schema
export const StandardEdgeDataSchema = z.object({
  label: z.string().optional(),
  description: z.string().optional(),
  condition: z.string().optional(),
  weight: z.number().optional(),
  color: z.string().optional(),
  strokeWidth: z.number().optional(),
  strokeDasharray: z.string().optional(),
});

export const StandardEdgeSchema = BaseEdgeSchema.extend({
  type: z.literal('default').optional(),
  data: StandardEdgeDataSchema.optional(),
});

// Conditional Edge Schema (for decision nodes)
export const ConditionalEdgeDataSchema = z.object({
  label: z.string().min(1),
  condition: z.string().min(1),
  description: z.string().optional(),
  priority: z.number().optional(),
  color: z.string().optional(),
  isDefault: z.boolean().optional(),
});

export const ConditionalEdgeSchema = BaseEdgeSchema.extend({
  type: z.literal('conditional'),
  data: ConditionalEdgeDataSchema,
});

// Bezier Edge Schema
export const BezierEdgeDataSchema = z.object({
  label: z.string().optional(),
  description: z.string().optional(),
  curvature: z.number().optional(),
  color: z.string().optional(),
});

export const BezierEdgeSchema = BaseEdgeSchema.extend({
  type: z.literal('smoothstep'),
  data: BezierEdgeDataSchema.optional(),
});

// Step Edge Schema
export const StepEdgeDataSchema = z.object({
  label: z.string().optional(),
  description: z.string().optional(),
  color: z.string().optional(),
});

export const StepEdgeSchema = BaseEdgeSchema.extend({
  type: z.literal('step'),
  data: StepEdgeDataSchema.optional(),
});

// Union of all edge types
export const EdgeSchema = z
  .discriminatedUnion('type', [
    StandardEdgeSchema.extend({ type: z.literal('default') }),
    ConditionalEdgeSchema,
    BezierEdgeSchema,
    StepEdgeSchema,
  ])
  .or(StandardEdgeSchema); // Allow undefined type to default to standard

// Type inference
/**
 *
 */
export type BaseEdge = z.infer<typeof BaseEdgeSchema>;
/**
 *
 */
export type StandardEdgeData = z.infer<typeof StandardEdgeDataSchema>;
/**
 *
 */
export type StandardEdge = z.infer<typeof StandardEdgeSchema>;
/**
 *
 */
export type ConditionalEdgeData = z.infer<typeof ConditionalEdgeDataSchema>;
/**
 *
 */
export type ConditionalEdge = z.infer<typeof ConditionalEdgeSchema>;
/**
 *
 */
export type BezierEdgeData = z.infer<typeof BezierEdgeDataSchema>;
/**
 *
 */
export type BezierEdge = z.infer<typeof BezierEdgeSchema>;
/**
 *
 */
export type StepEdgeData = z.infer<typeof StepEdgeDataSchema>;
/**
 *
 */
export type StepEdge = z.infer<typeof StepEdgeSchema>;
/**
 *
 */
export type CanvasEdge = z.infer<typeof EdgeSchema>;

// Edge validation helpers
export const validateEdge = (edge: unknown): CanvasEdge => {
  return EdgeSchema.parse(edge);
};

export const isValidEdge = (edge: unknown): edge is CanvasEdge => {
  return EdgeSchema.safeParse(edge).success;
};

// Edge creation helpers
export const createStandardEdge = (
  id: string,
  source: string,
  target: string,
  data?: StandardEdgeData
): StandardEdge => ({
  id,
  source,
  target,
  type: 'default',
  data,
  deletable: true,
});

export const createConditionalEdge = (
  id: string,
  source: string,
  target: string,
  data: ConditionalEdgeData
): ConditionalEdge => ({
  id,
  source,
  target,
  type: 'conditional',
  data,
  deletable: true,
});

export const createBezierEdge = (
  id: string,
  source: string,
  target: string,
  data?: BezierEdgeData
): BezierEdge => ({
  id,
  source,
  target,
  type: 'smoothstep',
  data,
  deletable: true,
});

export const createStepEdge = (
  id: string,
  source: string,
  target: string,
  data?: StepEdgeData
): StepEdge => ({
  id,
  source,
  target,
  type: 'step',
  data,
  deletable: true,
});
