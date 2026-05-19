/**
 * @fileoverview Deep prop validation with type checking
 *
 * Validates component props against their contract type definitions,
 * including type checking, enum validation, and constraint validation.
 *
 * @doc.type module
 * @doc.purpose Deep prop validation with type safety
 * @doc.layer platform
 * @doc.pattern SchemaValidation
 */

import type { ComponentInstance, NodeId } from './types.js';
import type { ComponentContract, PropDefinition } from '@ghatana/ds-schema';
import { z } from 'zod';

// ============================================================================
// PROP VALIDATION RESULT
// ============================================================================

export interface PropValidationError {
  nodeId: NodeId;
  propName: string;
  code: string;
  message: string;
  expectedType?: string;
  actualValue?: unknown;
}

export interface PropValidationResult {
  valid: boolean;
  errors: PropValidationError[];
}

// ============================================================================
// PROP TYPE VALIDATION
// ============================================================================

/**
 * Validates a single prop value against its contract definition.
 */
export function validatePropValue(
  propName: string,
  value: unknown,
  propDef: PropDefinition,
): PropValidationError | null {
  // Check required props
  if (propDef.required && value === undefined) {
    return {
      nodeId: '' as NodeId, // Will be filled by caller
      propName,
      code: 'PROP_REQUIRED',
      message: `Required prop "${propName}" is missing`,
    };
  }

  // Skip validation if value is undefined and prop is optional
  if (value === undefined && !propDef.required) {
    return null;
  }

  // Type validation based on prop type
  switch (propDef.type) {
    case 'string':
      if (typeof value !== 'string') {
        return {
          nodeId: '' as NodeId,
          propName,
          code: 'PROP_TYPE_MISMATCH',
          message: `Prop "${propName}" expected string, got ${typeof value}`,
          expectedType: 'string',
          actualValue: value,
        };
      }
      // String constraints
      if (propDef.minLength !== undefined && (value as string).length < propDef.minLength) {
        return {
          nodeId: '' as NodeId,
          propName,
          code: 'PROP_MIN_LENGTH',
          message: `Prop "${propName}" must be at least ${propDef.minLength} characters`,
          actualValue: value,
        };
      }
      if (propDef.maxLength !== undefined && (value as string).length > propDef.maxLength) {
        return {
          nodeId: '' as NodeId,
          propName,
          code: 'PROP_MAX_LENGTH',
          message: `Prop "${propName}" must be at most ${propDef.maxLength} characters`,
          actualValue: value,
        };
      }
      if (propDef.pattern && !new RegExp(propDef.pattern).test(value as string)) {
        return {
          nodeId: '' as NodeId,
          propName,
          code: 'PROP_PATTERN_MISMATCH',
          message: `Prop "${propName}" does not match required pattern`,
          actualValue: value,
        };
      }
      // Enum validation
      if (propDef.enum && !propDef.enum.includes(value as string)) {
        return {
          nodeId: '' as NodeId,
          propName,
          code: 'PROP_ENUM_MISMATCH',
          message: `Prop "${propName}" must be one of: ${propDef.enum.join(', ')}`,
          actualValue: value,
        };
      }
      break;

    case 'number':
      if (typeof value !== 'number' || isNaN(value)) {
        return {
          nodeId: '' as NodeId,
          propName,
          code: 'PROP_TYPE_MISMATCH',
          message: `Prop "${propName}" expected number, got ${typeof value}`,
          expectedType: 'number',
          actualValue: value,
        };
      }
      // Number constraints
      if (propDef.min !== undefined && (value as number) < propDef.min) {
        return {
          nodeId: '' as NodeId,
          propName,
          code: 'PROP_MIN_VALUE',
          message: `Prop "${propName}" must be at least ${propDef.min}`,
          actualValue: value,
        };
      }
      if (propDef.max !== undefined && (value as number) > propDef.max) {
        return {
          nodeId: '' as NodeId,
          propName,
          code: 'PROP_MAX_VALUE',
          message: `Prop "${propName}" must be at most ${propDef.max}`,
          actualValue: value,
        };
      }
      break;

    case 'boolean':
      if (typeof value !== 'boolean') {
        return {
          nodeId: '' as NodeId,
          propName,
          code: 'PROP_TYPE_MISMATCH',
          message: `Prop "${propName}" expected boolean, got ${typeof value}`,
          expectedType: 'boolean',
          actualValue: value,
        };
      }
      break;

    case 'array':
      if (!Array.isArray(value)) {
        return {
          nodeId: '' as NodeId,
          propName,
          code: 'PROP_TYPE_MISMATCH',
          message: `Prop "${propName}" expected array, got ${typeof value}`,
          expectedType: 'array',
          actualValue: value,
        };
      }
      // Array constraints
      if (propDef.minItems !== undefined && (value as unknown[]).length < propDef.minItems) {
        return {
          nodeId: '' as NodeId,
          propName,
          code: 'PROP_MIN_ITEMS',
          message: `Prop "${propName}" must have at least ${propDef.minItems} items`,
          actualValue: value,
        };
      }
      if (propDef.maxItems !== undefined && (value as unknown[]).length > propDef.maxItems) {
        return {
          nodeId: '' as NodeId,
          propName,
          code: 'PROP_MAX_ITEMS',
          message: `Prop "${propName}" must have at most ${propDef.maxItems} items`,
          actualValue: value,
        };
      }
      break;

    case 'object':
      if (typeof value !== 'object' || value === null || Array.isArray(value)) {
        return {
          nodeId: '' as NodeId,
          propName,
          code: 'PROP_TYPE_MISMATCH',
          message: `Prop "${propName}" expected object, got ${typeof value}`,
          expectedType: 'object',
          actualValue: value,
        };
      }
      // Schema validation if provided
      if (propDef.schema) {
        try {
          const schema = z.object(propDef.schema);
          const result = schema.safeParse(value);
          if (!result.success) {
            return {
              nodeId: '' as NodeId,
              propName,
              code: 'PROP_SCHEMA_MISMATCH',
              message: `Prop "${propName}" does not match schema: ${result.error.issues[0]?.message}`,
              actualValue: value,
            };
          }
        } catch (err) {
          // If schema parsing fails, skip validation
          console.warn(`Failed to parse schema for prop "${propName}":`, err);
        }
      }
      break;

    case 'function':
      if (typeof value !== 'function') {
        return {
          nodeId: '' as NodeId,
          propName,
          code: 'PROP_TYPE_MISMATCH',
          message: `Prop "${propName}" expected function, got ${typeof value}`,
          expectedType: 'function',
          actualValue: value,
        };
      }
      break;

    default:
      // Unknown type - skip validation
      break;
  }

  return null;
}

/**
 * Validates all props on a component instance against its contract.
 */
export function validateComponentProps(
  instance: ComponentInstance,
  contract: ComponentContract,
): PropValidationResult {
  const errors: PropValidationError[] = [];

  for (const propDef of contract.props) {
    const value = instance.props[propDef.name];
    const error = validatePropValue(propDef.name, value, propDef);
    
    if (error) {
      errors.push({
        ...error,
        nodeId: instance.id,
      });
    }
  }

  // Check for unknown props (props not in contract)
  const contractPropNames = new Set(contract.props.map(p => p.name));
  for (const propName of Object.keys(instance.props)) {
    if (!contractPropNames.has(propName)) {
      errors.push({
        nodeId: instance.id,
        propName,
        code: 'UNKNOWN_PROP',
        message: `Prop "${propName}" is not defined in contract for "${contract.name}"`,
        actualValue: instance.props[propName],
      });
    }
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}

/**
 * Validates all props across a document.
 */
export function validateDocumentProps(
  nodes: Record<string, ComponentInstance>,
  contracts: ReadonlyMap<string, ComponentContract>,
): PropValidationResult {
  const allErrors: PropValidationError[] = [];

  for (const instance of Object.values(nodes)) {
    if (instance.contractName === 'RootContainer') continue;
    
    const contract = contracts.get(instance.contractName);
    if (!contract) {
      // Skip validation for missing contracts (handled by main validation)
      continue;
    }

    const result = validateComponentProps(instance, contract);
    allErrors.push(...result.errors);
  }

  return {
    valid: allErrors.length === 0,
    errors: allErrors,
  };
}
