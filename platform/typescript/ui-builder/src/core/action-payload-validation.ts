/**
 * @fileoverview Action payload validation
 *
 * Validates action payloads against expected schemas for different
 * action target kinds, ensuring type safety and preventing injection.
 *
 * @doc.type module
 * @doc.purpose Action payload schema validation
 * @doc.layer platform
 * @doc.pattern SchemaValidation
 */

import type { ActionDefinition, ActionTargetKind } from './types.js';
import { z } from 'zod';

// ============================================================================
// ACTION PAYLOAD VALIDATION RESULT
// ============================================================================

export interface ActionPayloadError {
  actionId: string;
  targetKind: ActionTargetKind;
  code: string;
  message: string;
  field?: string;
}

export interface ActionPayloadValidationResult {
  valid: boolean;
  errors: ActionPayloadError[];
}

// ============================================================================
// ACTION PAYLOAD SCHEMAS
// ============================================================================

/**
 * Navigate action payload schema.
 */
const NavigatePayloadSchema = z.object({
  route: z.string().min(1),
  params: z.record(z.string(), z.string()).optional(),
  query: z.record(z.string(), z.string()).optional(),
  hash: z.string().optional(),
  state: z.record(z.string(), z.unknown()).optional(),
});

/**
 * Toggle state action payload schema.
 */
const ToggleStatePayloadSchema = z.object({
  stateKey: z.string().min(1),
  value: z.unknown(),
});

/**
 * Emit event action payload schema.
 */
const EmitEventPayloadSchema = z.object({
  eventName: z.string().min(1),
  payload: z.record(z.string(), z.unknown()).optional(),
});

/**
 * Call API action payload schema.
 */
const CallApiPayloadSchema = z.object({
  endpoint: z.string().min(1).url(),
  method: z.enum(['GET', 'POST', 'PUT', 'DELETE', 'PATCH']).optional(),
  headers: z.record(z.string(), z.string()).optional(),
  body: z.record(z.string(), z.unknown()).optional(),
  queryParams: z.record(z.string(), z.string()).optional(),
});

/**
 * Update binding action payload schema.
 */
const UpdateBindingPayloadSchema = z.object({
  bindingId: z.string().min(1),
  value: z.unknown(),
});

/**
 * Custom action payload schema (minimal validation).
 */
const CustomPayloadSchema = z.record(z.string(), z.unknown());

// ============================================================================
// PAYLOAD VALIDATION
// ============================================================================

/**
 * Validates an action payload based on its target kind.
 */
export function validateActionPayload(
  action: ActionDefinition,
): ActionPayloadValidationResult {
  if (!action.payload) {
    // No payload is valid for all action types
    return { valid: true, errors: [] };
  }

  let schema: z.ZodSchema;
  
  switch (action.targetKind) {
    case 'navigate':
      schema = NavigatePayloadSchema;
      break;
    case 'toggle-state':
      schema = ToggleStatePayloadSchema;
      break;
    case 'emit-event':
      schema = EmitEventPayloadSchema;
      break;
    case 'call-api':
      schema = CallApiPayloadSchema;
      break;
    case 'update-binding':
      schema = UpdateBindingPayloadSchema;
      break;
    case 'custom':
      schema = CustomPayloadSchema;
      break;
    default:
      // Unknown target kind - skip validation
      return { valid: true, errors: [] };
  }

  const result = schema.safeParse(action.payload);
  
  if (!result.success) {
    return {
      valid: false,
      errors: result.error.issues.map((issue: z.ZodIssue) => ({
        actionId: action.id,
        targetKind: action.targetKind,
        code: 'PAYLOAD_VALIDATION_ERROR',
        message: issue.message,
        field: issue.path.join('.'),
      })),
    };
  }

  return { valid: true, errors: [] };
}

/**
 * Validates all action definitions in an array.
 */
export function validateAllActionPayloads(
  actions: readonly ActionDefinition[],
): ActionPayloadValidationResult {
  const allErrors: ActionPayloadError[] = [];

  for (const action of actions) {
    const result = validateActionPayload(action);
    allErrors.push(...result.errors);
  }

  return {
    valid: allErrors.length === 0,
    errors: allErrors,
  };
}

// ============================================================================
// PAYLOAD SECURITY VALIDATION
// ============================================================================

/**
 * Checks for potentially dangerous values in action payloads.
 */
export function validatePayloadSecurity(
  payload: Record<string, unknown>,
): ActionPayloadError[] {
  const errors: ActionPayloadError[] = [];

  for (const [key, value] of Object.entries(payload)) {
    // Check for function values (potential code execution)
    if (typeof value === 'function') {
      errors.push({
        actionId: '',
        targetKind: 'custom',
        code: 'FUNCTION_IN_PAYLOAD',
        message: `Payload field "${key}" contains a function - not allowed in action payloads`,
        field: key,
      });
    }

    // Check for object prototypes (potential prototype pollution)
    if (key === '__proto__' || key === 'constructor' || key === 'prototype') {
      errors.push({
        actionId: '',
        targetKind: 'custom',
        code: 'DANGEROUS_KEY',
        message: `Payload contains dangerous key "${key}" - potential prototype pollution`,
        field: key,
      });
    }

    // Recursively check nested objects
    if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
      errors.push(...validatePayloadSecurity(value as Record<string, unknown>));
    }
  }

  return errors;
}

/**
 * Validates an action payload for security issues.
 */
export function validateActionPayloadSecurity(
  action: ActionDefinition,
): ActionPayloadValidationResult {
  if (!action.payload) {
    return { valid: true, errors: [] };
  }

  const payload = action.payload as Record<string, unknown>;
  const errors = validatePayloadSecurity(payload);

  return {
    valid: errors.length === 0,
    errors: errors.map(e => ({ ...e, actionId: action.id, targetKind: action.targetKind })),
  };
}
