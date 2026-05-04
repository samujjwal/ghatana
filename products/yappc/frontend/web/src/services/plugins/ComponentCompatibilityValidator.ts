/**
 * Component Compatibility Validation
 *
 * Validates component compatibility including:
 * - Renderer contract
 * - Prop adapter
 * - Fallback
 * - Preview policy
 *
 * @doc.type service
 * @doc.purpose Component compatibility validation
 * @doc.layer product
 */

export interface RendererContract {
  /** Required props */
  requiredProps: string[];
  /** Optional props */
  optionalProps: string[];
  /** Prop types */
  propTypes: Record<string, PropType>;
  /** Supported events */
  supportedEvents: string[];
  /** Version */
  version: string;
}

export interface PropType {
  /** Type name */
  type: 'string' | 'number' | 'boolean' | 'object' | 'array' | 'function' | 'enum' | 'custom';
  /** Required flag */
  required: boolean;
  /** Default value */
  defaultValue?: unknown;
  /** Enum values (if type is enum) */
  enumValues?: string[];
  /** Custom type name (if type is custom) */
  customType?: string;
  /** Validation function */
  validate?: (value: unknown) => boolean;
}

export interface PropAdapter {
  /** Source prop name */
  sourceProp: string;
  /** Target prop name */
  targetProp: string;
  /** Transform function */
  transform: (value: unknown) => unknown;
  /** Reverse transform function */
  reverseTransform?: (value: unknown) => unknown;
}

export interface FallbackConfig {
  /** Fallback component */
  fallbackComponent: string;
  /** Fallback reason */
  reason: string;
  /** Severity */
  severity: 'error' | 'warning' | 'info';
  /** User message */
  userMessage?: string;
}

export interface PreviewPolicy {
  /** Allow preview */
  allowPreview: boolean;
  /** Preview mode */
  previewMode: 'sandbox' | 'iframe' | 'inline';
  /** Required permissions */
  requiredPermissions: string[];
  /** Resource limits */
  resourceLimits: {
    maxMemory?: number;
    maxCPU?: number;
  };
}

export interface CompatibilityValidationResult {
  /** Is compatible */
  compatible: boolean;
  /** Renderer contract match */
  contractMatch: boolean;
  /** Missing props */
  missingProps: string[];
  /** Type mismatches */
  typeMismatches: Array<{ prop: string; expected: string; actual: string }>;
  /** Required adapters */
  requiredAdapters: PropAdapter[];
  /** Fallback config */
  fallback?: FallbackConfig;
  /** Preview policy */
  previewPolicy: PreviewPolicy;
  /** Warnings */
  warnings: string[];
  /** Errors */
  errors: string[];
}

/**
 * Validate component compatibility
 */
export function validateComponentCompatibility(
  componentContract: RendererContract,
  providedProps: Record<string, unknown>,
  targetContract: RendererContract
): CompatibilityValidationResult {
  const warnings: string[] = [];
  const errors: string[] = [];
  const missingProps: string[] = [];
  const typeMismatches: Array<{ prop: string; expected: string; actual: string }> = [];
  const requiredAdapters: PropAdapter[] = [];

  // Check version compatibility
  if (componentContract.version !== targetContract.version) {
    warnings.push(`Version mismatch: component ${componentContract.version}, target ${targetContract.version}`);
  }

  // Check required props
  for (const prop of targetContract.requiredProps) {
    if (!(prop in providedProps)) {
      missingProps.push(prop);
      errors.push(`Missing required prop: ${prop}`);
    }
  }

  // Check prop types
  for (const [propName, propType] of Object.entries(targetContract.propTypes)) {
    const value = providedProps[propName];
    if (value !== undefined) {
      const actualType = getActualType(value);
      const expectedType = propType.type;
      
      if (!isTypeCompatible(actualType, expectedType, propType)) {
        typeMismatches.push({
          prop: propName,
          expected: expectedType,
          actual: actualType,
        });
        warnings.push(`Type mismatch for ${propName}: expected ${expectedType}, got ${actualType}`);
        
        // Create adapter if possible
        const adapter = createPropAdapter(propName, actualType, expectedType);
        if (adapter) {
          requiredAdapters.push(adapter);
        }
      }
    }
  }

  // Check supported events
  const componentEvents = componentContract.supportedEvents || [];
  const targetEvents = targetContract.supportedEvents || [];
  const missingEvents = targetEvents.filter(e => !componentEvents.includes(e));
  if (missingEvents.length > 0) {
    warnings.push(`Component does not support events: ${missingEvents.join(', ')}`);
  }

  const contractMatch = missingProps.length === 0 && typeMismatches.length === 0;
  const compatible = contractMatch && errors.length === 0;

  // Determine fallback if not compatible
  let fallback: FallbackConfig | undefined;
  if (!compatible) {
    fallback = {
      fallbackComponent: 'FallbackComponent',
      reason: missingProps.length > 0 ? 'Missing required props' : 'Type mismatches',
      severity: errors.length > 0 ? 'error' : 'warning',
      userMessage: `Component compatibility issues: ${errors.concat(warnings).join('; ')}`,
    };
  }

  // Default preview policy
  const previewPolicy: PreviewPolicy = {
    allowPreview: true,
    previewMode: 'sandbox',
    requiredPermissions: [],
    resourceLimits: {
      maxMemory: 50 * 1024 * 1024,
      maxCPU: 10,
    },
  };

  return {
    compatible,
    contractMatch,
    missingProps,
    typeMismatches,
    requiredAdapters,
    fallback,
    previewPolicy,
    warnings,
    errors,
  };
}

/**
 * Get actual type of value
 */
function getActualType(value: unknown): string {
  if (value === null) return 'null';
  if (Array.isArray(value)) return 'array';
  return typeof value;
}

/**
 * Check if types are compatible
 */
function isTypeCompatible(actual: string, expected: PropType['type'], expectedDef: PropType): boolean {
  if (expected === 'custom') {
    return expectedDef.validate ? expectedDef.validate(actual) : true;
  }
  
  const typeMap: Record<string, string[]> = {
    string: ['string'],
    number: ['number'],
    boolean: ['boolean'],
    object: ['object'],
    array: ['array'],
    function: ['function'],
    enum: ['string'], // Enums are represented as strings
  };
  
  const compatibleTypes = typeMap[expected] || [];
  return compatibleTypes.includes(actual);
}

/**
 * Create prop adapter if possible
 */
function createPropAdapter(
  propName: string,
  actualType: string,
  expectedType: PropType['type']
): PropAdapter | null {
  // Define common type conversions
  const conversions: Record<string, Record<string, (value: unknown) => unknown>> = {
    'string->number': { transform: (v) => Number(v), reverseTransform: (v) => String(v) },
    'number->string': { transform: (v) => String(v), reverseTransform: (v) => Number(v) },
    'boolean->string': { transform: (v) => String(v), reverseTransform: (v) => v === 'true' },
    'string->boolean': { transform: (v) => v === 'true', reverseTransform: (v) => String(v) },
  };

  const key = `${actualType}->${expectedType}`;
  const conversion = conversions[key];

  if (conversion) {
    return {
      sourceProp: propName,
      targetProp: propName,
      ...conversion,
    };
  }

  return null;
}

/**
 * Apply prop adapters
 */
export function applyPropAdapters(
  props: Record<string, unknown>,
  adapters: PropAdapter[]
): Record<string, unknown> {
  const adaptedProps = { ...props };

  for (const adapter of adapters) {
    const value = adaptedProps[adapter.sourceProp];
    if (value !== undefined) {
      adaptedProps[adapter.targetProp] = adapter.transform(value);
    }
  }

  return adaptedProps;
}

/**
 * Validate preview policy
 */
export function validatePreviewPolicy(
  policy: PreviewPolicy,
  availablePermissions: string[]
): { allowed: boolean; missingPermissions: string[] } {
  const missingPermissions = policy.requiredPermissions.filter(
    perm => !availablePermissions.includes(perm)
  );

  return {
    allowed: missingPermissions.length === 0,
    missingPermissions,
  };
}

/**
 * Create fallback component
 */
export function createFallbackComponent(config: FallbackConfig): string {
  return `
import React from 'react';

export const FallbackComponent: React.FC<{ reason: string; userMessage?: string }> = ({
  reason,
  userMessage,
}) => {
  return (
    <div className="p-4 border border-gray-300 rounded bg-gray-50">
      <div className="text-sm text-gray-600">
        <strong>Component unavailable:</strong> {reason}
      </div>
      {userMessage && (
        <div className="text-xs text-gray-500 mt-1">
          {userMessage}
        </div>
      )}
    </div>
  );
};
  `.trim();
}

export default {
  validateComponentCompatibility,
  applyPropAdapters,
  validatePreviewPolicy,
  createFallbackComponent,
};
