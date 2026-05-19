/**
 * @fileoverview Builder Action Zod Schema
 *
 * Runtime validation for all builder actions with strict type checking.
 * Ensures action payloads are valid before execution.
 *
 * @doc.type module
 * @doc.purpose Builder action schema validation
 * @doc.layer platform
 * @doc.pattern SchemaValidation
 */

import { z } from 'zod';

// ============================================================================
// BASE ACTION SCHEMAS
// ============================================================================

export const PositionSchema = z.object({
  x: z.number(),
  y: z.number(),
});

export const SizeSchema = z.object({
  width: z.number().positive(),
  height: z.number().positive(),
});

export const BaseActionSchema = z.object({
  id: z.string().min(1),
  type: z.string().min(1),
  timestamp: z.string().datetime(),
  description: z.string().min(1),
});

export const ActionResultSchema = z.object({
  success: z.boolean(),
  document: z.any(), // BuilderDocument - circular reference, validated separately
  errors: z.array(z.string()),
  warnings: z.array(z.string()),
});

export const HistoryEntrySchema = z.object({
  action: z.any(), // BuilderAction - recursive
  result: ActionResultSchema,
  timestamp: z.string().datetime(),
});

// ============================================================================
// SPECIFIC ACTION SCHEMAS
// ============================================================================

export const AddNodeActionSchema = BaseActionSchema.extend({
  type: z.literal('add-node'),
  nodeId: z.string().min(1),
  parentId: z.string().min(1),
  component: z.any(), // ComponentInstance
  position: PositionSchema.optional(),
  size: SizeSchema.optional(),
});

export const RemoveNodeActionSchema = BaseActionSchema.extend({
  type: z.literal('remove-node'),
  nodeId: z.string().min(1),
  component: z.any(), // ComponentInstance
});

export const MoveNodeActionSchema = BaseActionSchema.extend({
  type: z.literal('move-node'),
  nodeId: z.string().min(1),
  oldPosition: PositionSchema,
  newPosition: PositionSchema,
});

export const UpdateNodePropsActionSchema = BaseActionSchema.extend({
  type: z.literal('update-node-props'),
  nodeId: z.string().min(1),
  oldProps: z.record(z.unknown()),
  newProps: z.record(z.unknown()),
  path: z.string().optional(),
});

export const BindComponentActionSchema = BaseActionSchema.extend({
  type: z.literal('bind-component'),
  binding: z.any(), // Binding
});

export const UnbindComponentActionSchema = BaseActionSchema.extend({
  type: z.literal('unbind-component'),
  bindingId: z.string().min(1),
  binding: z.any(), // Binding
});

export const UpdateLayoutActionSchema = BaseActionSchema.extend({
  type: z.literal('update-layout'),
  oldLayout: z.any(), // Layout
  newLayout: z.any(), // Layout
});

export const ValidateDocumentActionSchema = BaseActionSchema.extend({
  type: z.literal('validate-document'),
});

export const UndoActionSchema = BaseActionSchema.extend({
  type: z.literal('undo'),
});

export const RedoActionSchema = BaseActionSchema.extend({
  type: z.literal('redo'),
});

export const BatchActionSchema = BaseActionSchema.extend({
  type: z.literal('batch'),
  actionCount: z.number().int().positive(),
});

// ============================================================================
// UNION SCHEMA
// ============================================================================

export const BuilderActionSchema = z.discriminatedUnion('type', [
  AddNodeActionSchema,
  RemoveNodeActionSchema,
  MoveNodeActionSchema,
  UpdateNodePropsActionSchema,
  BindComponentActionSchema,
  UnbindComponentActionSchema,
  UpdateLayoutActionSchema,
  ValidateDocumentActionSchema,
  UndoActionSchema,
  RedoActionSchema,
  BatchActionSchema,
]);

// ============================================================================
// ACTION CONTEXT SCHEMA
// ============================================================================

export const ActionContextSchema = z.object({
  document: z.any(), // BuilderDocument
  userId: z.string().optional(),
  sessionId: z.string().optional(),
  dryRun: z.boolean().optional(),
  skipValidation: z.boolean().optional(),
});

// ============================================================================
// ACTION REGISTRATION SCHEMA
// ============================================================================

export const ActionRegistrationSchema = z.object({
  type: z.string().min(1),
  validator: z.function(),
  executor: z.function(),
  description: z.string().min(1),
  category: z.enum(['node', 'layout', 'binding', 'document', 'history']),
});

// ============================================================================
// ACTION MANAGER CONFIG SCHEMA
// ============================================================================

export const ActionManagerConfigSchema = z.object({
  maxHistorySize: z.number().int().positive().max(1000).optional(),
  enableUndoRedo: z.boolean().optional(),
  autoValidate: z.boolean().optional(),
  trackChanges: z.boolean().optional(),
});

// ============================================================================
// VALIDATION HELPERS
// ============================================================================

export interface ValidationResult<T> {
  success: boolean;
  data?: T;
  errors: string[];
}

/**
 * Validate a builder action.
 */
export function validateBuilderAction(
  action: unknown,
): ValidationResult<z.infer<typeof BuilderActionSchema>> {
  const result = BuilderActionSchema.safeParse(action);
  
  if (!result.success) {
    return {
      success: false,
      errors: result.error.issues.map(e => `${e.path.join('.')}: ${e.message}`),
    };
  }

  return {
    success: true,
    data: result.data,
    errors: [],
  };
}

/**
 * Validate an action context.
 */
export function validateActionContext(
  context: unknown,
): ValidationResult<z.infer<typeof ActionContextSchema>> {
  const result = ActionContextSchema.safeParse(context);
  
  if (!result.success) {
    return {
      success: false,
      errors: result.error.issues.map(e => `${e.path.join('.')}: ${e.message}`),
    };
  }

  return {
    success: true,
    data: result.data,
    errors: [],
  };
}

/**
 * Validate action manager configuration.
 */
export function validateActionManagerConfig(
  config: unknown,
): ValidationResult<z.infer<typeof ActionManagerConfigSchema>> {
  const result = ActionManagerConfigSchema.safeParse(config);
  
  if (!result.success) {
    return {
      success: false,
      errors: result.error.issues.map(e => `${e.path.join('.')}: ${e.message}`),
    };
  }

  return {
    success: true,
    data: result.data,
    errors: [],
  };
}
