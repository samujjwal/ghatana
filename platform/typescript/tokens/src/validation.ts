/**
 * Lightweight token validation without Zod to avoid SSR issues.
 * Simply validates that token values are of expected primitive types.
 */

export interface TokenValidationResult {
  success: boolean;
  errors?: string[];
}

/**
 * Recursively validates token values are primitives or objects/arrays.
 * Skips validation of function helpers.
 */
function validateValue(value: unknown, path: string = ''): string[] {
  const errors: string[] = [];

  if (value === null || value === undefined) {
    return errors; // nullish is valid
  }

  // Functions are OK - they're helper utilities
  if (typeof value === 'function') {
    return errors;
  }

  // Primitives are OK
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return errors;
  }

  // Validate arrays recursively
  if (Array.isArray(value)) {
    for (let i = 0; i < value.length; i++) {
      const itemPath = path ? `${path}[${i}]` : `[${i}]`;
      const itemErrors = validateValue(value[i], itemPath);
      errors.push(...itemErrors);
    }
    return errors;
  }

  // Validate objects recursively
  if (typeof value === 'object') {
    for (const [key, val] of Object.entries(value as Record<string, unknown>)) {
      const propPath = path ? `${path}.${key}` : key;

      // Skip functions in objects - they're helpers
      if (typeof val === 'function') {
        continue;
      }

      const propErrors = validateValue(val, propPath);
      errors.push(...propErrors);
    }
    return errors;
  }

  // Unsupported type
  if (path) {
    errors.push(`${path}: Unsupported value type ${typeof value}`);
  }
  return errors;
}

/**
 * Validates a token registry
 */
export function validateTokens(registry: Record<string, unknown>): TokenValidationResult {
  const errors = validateValue(registry);
  return {
    success: errors.length === 0,
    errors: errors.length > 0 ? errors : undefined,
  };
}

/**
 * Asserts tokens are valid, throws if not
 */
export function assertTokensValid(registry: Record<string, unknown>): void {
  const result = validateTokens(registry);
  if (!result.success) {
    const errorText = result.errors?.join('\n') || 'Unknown validation error';
    throw new Error(`Design token validation failed:\n${errorText}`);
  }
}
