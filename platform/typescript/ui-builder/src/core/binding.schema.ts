/**
 * @fileoverview Binding Zod Schema
 *
 * Runtime validation for data bindings with strict type checking.
 * Ensures binding definitions are valid before application.
 *
 * @doc.type module
 * @doc.purpose Binding schema validation
 * @doc.layer platform
 * @doc.pattern SchemaValidation
 */

import { z } from 'zod';

// ============================================================================
// BINDING TYPE SCHEMAS
// ============================================================================

export const BindingTypeSchema = z.enum(['data', 'event', 'slot', 'theme', 'computed']);

export const BindingSchema = z.object({
  id: z.string().min(1, 'Binding ID is required'),
  type: BindingTypeSchema,
  source: z.string().min(1, 'Binding source is required'),
  target: z.string().min(1, 'Binding target is required'),
  transform: z.string().optional(),
  bidirectional: z.boolean().optional(),
});

// ============================================================================
// ACTION DEFINITION SCHEMAS
// ============================================================================

export const ActionTargetKindSchema = z.enum([
  'navigate',
  'toggle-state',
  'emit-event',
  'call-api',
  'update-binding',
  'custom',
]);

export const ActionDefinitionSchema = z.object({
  id: z.string().min(1, 'Action ID is required'),
  label: z.string().optional(),
  triggerEvent: z.string().min(1, 'Trigger event is required'),
  targetKind: ActionTargetKindSchema,
  payload: z.record(z.string(), z.unknown()).optional(),
  condition: z.string().optional(),
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
 * Validate a binding definition.
 */
export function validateBinding(
  binding: unknown,
): ValidationResult<z.infer<typeof BindingSchema>> {
  const result = BindingSchema.safeParse(binding);
  
  if (!result.success) {
    return {
      success: false,
      errors: result.error.issues.map((e: z.ZodIssue) => `${e.path.join('.')}: ${e.message}`),
    };
  }

  // Additional validation for binding source/target syntax
  const data = result.data;
  const validationErrors: string[] = [];

  // Validate source path format (e.g., 'dataSource.users' or 'theme.colors.primary')
  if (!/^[a-zA-Z_][a-zA-Z0-9_.]*$/.test(data.source)) {
    validationErrors.push(`Invalid source path: ${data.source}`);
  }

  // Validate target property path (e.g., 'props.value' or 'style.color')
  if (!/^[a-zA-Z_][a-zA-Z0-9_.]*$/.test(data.target)) {
    validationErrors.push(`Invalid target path: ${data.target}`);
  }

  // Validate transform expression if provided
  if (data.transform && data.transform.trim().length === 0) {
    validationErrors.push('Transform expression cannot be empty');
  }

  if (validationErrors.length > 0) {
    return {
      success: false,
      errors: validationErrors,
    };
  }

  return {
    success: true,
    data,
    errors: [],
  };
}

/**
 * Validate an action definition.
 */
export function validateActionDefinition(
  action: unknown,
): ValidationResult<z.infer<typeof ActionDefinitionSchema>> {
  const result = ActionDefinitionSchema.safeParse(action);
  
  if (!result.success) {
    return {
      success: false,
      errors: result.error.issues.map((e: z.ZodIssue) => `${e.path.join('.')}: ${e.message}`),
    };
  }

  // Additional validation for action definition
  const data = result.data;
  const validationErrors: string[] = [];

  // Validate trigger event format (e.g., 'onClick', 'onChange')
  if (!/^on[A-Z][a-zA-Z]*$/.test(data.triggerEvent)) {
    validationErrors.push(`Invalid trigger event: ${data.triggerEvent}. Expected format: onEventName`);
  }

  // Validate condition expression if provided
  if (data.condition && data.condition.trim().length === 0) {
    validationErrors.push('Condition expression cannot be empty');
  }

  if (validationErrors.length > 0) {
    return {
      success: false,
      errors: validationErrors,
    };
  }

  return {
    success: true,
    data,
    errors: [],
  };
}

/**
 * Validate an array of bindings.
 */
export function validateBindings(
  bindings: unknown,
): ValidationResult<z.infer<typeof BindingSchema>[]> {
  const arrayResult = z.array(BindingSchema).safeParse(bindings);
  
  if (!arrayResult.success) {
    return {
      success: false,
      errors: (arrayResult.error as z.ZodError).issues.map((e: z.ZodIssue) => `${e.path.join('.')}: ${e.message}`),
    };
  }

  const allErrors: string[] = [];
  const validBindings: z.infer<typeof BindingSchema>[] = [];

  for (const binding of arrayResult.data) {
    const result = validateBinding(binding);
    if (result.success && result.data) {
      validBindings.push(result.data);
    } else {
      allErrors.push(...result.errors);
    }
  }

  if (allErrors.length > 0) {
    return {
      success: false,
      errors: allErrors,
    };
  }

  return {
    success: true,
    data: validBindings,
    errors: [],
  };
}

/**
 * Validate an array of action definitions.
 */
export function validateActionDefinitions(
  actions: unknown,
): ValidationResult<z.infer<typeof ActionDefinitionSchema>[]> {
  const arrayResult = z.array(ActionDefinitionSchema).safeParse(actions);
  
  if (!arrayResult.success) {
    return {
      success: false,
      errors: arrayResult.error.issues.map((e: z.ZodIssue) => `${e.path.join('.')}: ${e.message}`),
    };
  }

  const allErrors: string[] = [];
  const validActions: z.infer<typeof ActionDefinitionSchema>[] = [];

  for (const action of arrayResult.data) {
    const result = validateActionDefinition(action);
    if (result.success && result.data) {
      validActions.push(result.data);
    } else {
      allErrors.push(...result.errors);
    }
  }

  if (allErrors.length > 0) {
    return {
      success: false,
      errors: allErrors,
    };
  }

  return {
    success: true,
    data: validActions,
    errors: [],
  };
}

/**
 * Check if a binding is bidirectional and validate it's appropriate for the type.
 */
export function validateBidirectionalBinding(
  binding: z.infer<typeof BindingSchema>,
): ValidationResult<z.infer<typeof BindingSchema>> {
  const errors: string[] = [];

  if (binding.bidirectional) {
    // Only 'data' bindings can be bidirectional
    if (binding.type !== 'data') {
      errors.push(`Bidirectional binding not supported for type '${binding.type}'`);
    }

    // Transform expressions are not allowed with bidirectional bindings
    if (binding.transform) {
      errors.push('Transform expressions are not allowed with bidirectional bindings.');
    }
  }

  if (errors.length > 0) {
    return {
      success: false,
      errors,
    };
  }

  return {
    success: true,
    data: binding,
    errors: [],
  };
}
