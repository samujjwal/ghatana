/**
 * Manifest Schema - Zod schemas for validating simulation manifests.
 *
 * @doc.type module
 * @doc.purpose Provides validation schemas for simulation data structures
 * @doc.layer product
 * @doc.pattern Schema
 */

import { z } from "zod";

/**
 * Position schema.
 */
export const PositionSchema = z.object({
  x: z.number(),
  y: z.number(),
  z: z.number().optional(),
});

/**
 * Visual style schema.
 */
export const VisualStyleSchema = z.object({
  color: z.string().optional(),
  size: z.number().min(0).optional(),
  shape: z
    .enum(["rectangle", "circle", "triangle", "diamond", "hexagon", "custom"])
    .optional(),
  opacity: z.number().min(0).max(1).optional(),
  stroke: z
    .object({
      color: z.string(),
      width: z.number().min(0),
    })
    .optional(),
  label: z
    .object({
      visible: z.boolean().optional(),
      color: z.string().optional(),
      fontSize: z.number().min(1).optional(),
      fontFamily: z.string().optional(),
      position: z.enum(["top", "bottom", "center", "left", "right"]).optional(),
    })
    .optional(),
});

/**
 * Entity schema.
 */
export const EntitySchema = z.object({
  id: z.string().uuid(),
  label: z.string(),
  entityType: z.string(),
  visual: VisualStyleSchema.optional(),
  position: PositionSchema.optional(),
  value: z.unknown().optional(),
  metadata: z.record(z.string(), z.unknown()).optional(),
});

/**
 * Step action schema.
 */
export const StepActionSchema = z.object({
  actionType: z.enum([
    "highlight",
    "move",
    "swap",
    "compare",
    "insert",
    "remove",
    "set_value",
    "custom",
  ]),
  targetIds: z.array(z.string()).optional(),
  params: z.record(z.string(), z.unknown()).optional(),
  duration: z.number().min(0).optional(),
});

/**
 * Annotation schema.
 */
export const AnnotationSchema = z.object({
  id: z.string(),
  text: z.string(),
  position: PositionSchema.optional(),
  style: z.enum(["callout", "tooltip", "label", "arrow"]).optional(),
  targetEntityId: z.string().optional(),
});

/**
 * Step schema.
 */
export const StepSchema = z.object({
  id: z.string(),
  stepNumber: z.number().int().min(1).optional(),
  description: z.string(),
  algorithm: z.string().optional(),
  actions: z.array(StepActionSchema).optional(),
  annotations: z.array(AnnotationSchema).optional(),
  duration: z.number().min(0).optional(),
  easing: z.string().optional(),
  audio: z
    .object({
      url: z.string().url().optional(),
      narration: z.string().optional(),
    })
    .optional(),
  camera: z
    .object({
      x: z.number(),
      y: z.number(),
      zoom: z.number().min(0).optional(),
    })
    .optional(),
});

/**
 * Keyframe schema.
 */
export const KeyframeSchema = z.object({
  id: z.string(),
  timestamp: z.number().min(0),
  stepIndex: z.number().int().min(0),
  entities: z.array(EntitySchema),
  annotations: z.array(AnnotationSchema).optional(),
  audio: z
    .object({
      url: z.string().url().optional(),
      narration: z.string().optional(),
    })
    .optional(),
  camera: z
    .object({
      x: z.number(),
      y: z.number(),
      zoom: z.number().min(0).optional(),
    })
    .optional(),
});

/**
 * Domain config schemas.
 */
export const PhysicsConfigSchema = z.object({
  gravity: z
    .object({
      x: z.number(),
      y: z.number(),
    })
    .optional(),
  friction: z.number().min(0).max(1).optional(),
  restitution: z.number().min(0).max(1).optional(),
  timeStep: z.number().min(0).optional(),
});

export const ChemistryConfigSchema = z.object({
  temperature: z.number().optional(),
  pressure: z.number().optional(),
  pH: z.number().min(0).max(14).optional(),
  solvent: z.string().optional(),
});

export const EconomicsConfigSchema = z.object({
  interestRate: z.number().optional(),
  inflationRate: z.number().optional(),
  taxRate: z.number().min(0).max(1).optional(),
  timeUnit: z.enum(["day", "week", "month", "quarter", "year"]).optional(),
});

export const BiologyConfigSchema = z.object({
  temperature: z.number().optional(),
  oxygenLevel: z.number().min(0).max(100).optional(),
  pH: z.number().min(0).max(14).optional(),
  nutrientLevel: z.number().min(0).max(100).optional(),
});

export const MedicineConfigSchema = z.object({
  patientWeight: z.number().min(0).optional(),
  age: z.number().min(0).optional(),
  renalFunction: z.number().min(0).max(100).optional(),
  hepaticFunction: z.number().min(0).max(100).optional(),
});

/**
 * Domain config union schema.
 */
export const DomainConfigSchema = z.union([
  PhysicsConfigSchema,
  ChemistryConfigSchema,
  EconomicsConfigSchema,
  BiologyConfigSchema,
  MedicineConfigSchema,
  z.record(z.string(), z.unknown()),
]);

/**
 * Simulation domain enum.
 */
export const SimulationDomainSchema = z.enum([
  "discrete",
  "physics",
  "chemistry",
  "biology",
  "economics",
  "medicine",
]);

/**
 * Complete manifest schema.
 */
export const ManifestSchema = z.object({
  id: z.string(),
  version: z.string().optional(),
  domain: SimulationDomainSchema,
  title: z.string().min(1).max(200),
  description: z.string().max(2000).optional(),
  entities: z.array(EntitySchema),
  steps: z.array(StepSchema),
  keyframes: z.array(KeyframeSchema).optional(),
  domainConfig: DomainConfigSchema.optional(),
  metadata: z.record(z.string(), z.unknown()).optional(),
});

/**
 * Validate a manifest.
 */
export function validateManifest(manifest: unknown): {
  success: boolean;
  data?: z.infer<typeof ManifestSchema>;
  errors?: z.ZodIssue[];
} {
  const result = ManifestSchema.safeParse(manifest);
  if (result.success) {
    return { success: true, data: result.data };
  }
  return { success: false, errors: result.error.issues };
}

/**
 * Validate an entity.
 */
export function validateEntity(entity: unknown): {
  success: boolean;
  data?: z.infer<typeof EntitySchema>;
  errors?: z.ZodIssue[];
} {
  const result = EntitySchema.safeParse(entity);
  if (result.success) {
    return { success: true, data: result.data };
  }
  return { success: false, errors: result.error.issues };
}

/**
 * Validate a step.
 */
export function validateStep(step: unknown): {
  success: boolean;
  data?: z.infer<typeof StepSchema>;
  errors?: z.ZodIssue[];
} {
  const result = StepSchema.safeParse(step);
  if (result.success) {
    return { success: true, data: result.data };
  }
  return { success: false, errors: result.error.issues };
}

// Export types
export type Position = z.infer<typeof PositionSchema>;
export type VisualStyle = z.infer<typeof VisualStyleSchema>;
export type Entity = z.infer<typeof EntitySchema>;
export type StepAction = z.infer<typeof StepActionSchema>;
export type Annotation = z.infer<typeof AnnotationSchema>;
export type Step = z.infer<typeof StepSchema>;
export type Keyframe = z.infer<typeof KeyframeSchema>;
export type Manifest = z.infer<typeof ManifestSchema>;
