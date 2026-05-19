/**
 * @fileoverview Binding expression validation
 *
 * Validates binding expressions for safety and correctness,
 * preventing injection attacks and ensuring proper syntax.
 *
 * @doc.type module
 * @doc.purpose Binding expression safety validation
 * @doc.layer platform
 * @doc.pattern SchemaValidation
 */

import type { Binding } from './types.js';

// ============================================================================
// BINDING EXPRESSION VALIDATION RESULT
// ============================================================================

export interface BindingExpressionError {
  bindingId: string;
  expression: string;
  code: string;
  message: string;
}

export interface BindingExpressionValidationResult {
  valid: boolean;
  errors: BindingExpressionError[];
}

// ============================================================================
// EXPRESSION SYNTAX VALIDATION
// ============================================================================

/**
 * Dangerous patterns that could indicate injection attacks or unsafe code.
 */
const DANGEROUS_PATTERNS = [
  // Code execution patterns
  /\beval\b/,
  /\bFunction\b/,
  /\bsetTimeout\b/,
  /\bsetInterval\b/,
  // Property access that could lead to prototype pollution
  /__proto__/,
  /constructor/,
  /prototype\./,
  // Global object access
  /window\./,
  /document\./,
  /global\./,
  // Immediate function invocation
  /\(\)\s*=>\s*\(/,
  // Function calls
  /\.\s*call\s*\(/,
  /\.\s*apply\s*\(/,
];

/**
 * Safe binding expression patterns.
 */
const SAFE_PATTERNS = [
  // Data source access: dataSource.users, theme.colors.primary
  /^[a-zA-Z_][a-zA-Z0-9_.]*$/,
  // Simple property access: props.value, state.name
  /^(props|state|theme|dataSource)\.[a-zA-Z_][a-zA-Z0-9_.]*$/,
  // Array access with index: users[0], items[i]
  /^[a-zA-Z_][a-zA-Z0-9_.]*\[\d+\]$/,
  // Safe transform functions: map, filter, reduce, find
  /^(map|filter|reduce|find|some|every)\(.*=>.*\)$/,
];

/**
 * Validates a binding expression for safety and correctness.
 */
export function validateBindingExpression(
  expression: string,
): BindingExpressionError | null {
  if (!expression || expression.trim().length === 0) {
    return {
      bindingId: '',
      expression,
      code: 'EMPTY_EXPRESSION',
      message: 'Binding expression cannot be empty',
    };
  }

  const trimmed = expression.trim();

  // Check for dangerous patterns
  for (const pattern of DANGEROUS_PATTERNS) {
    if (pattern.test(trimmed)) {
      return {
        bindingId: '',
        expression,
        code: 'UNSAFE_EXPRESSION',
        message: `Expression contains potentially unsafe pattern: ${pattern}`,
      };
    }
  }

  // Check if expression matches safe patterns
  const matchesSafePattern = SAFE_PATTERNS.some(pattern => pattern.test(trimmed));
  
  if (!matchesSafePattern) {
    // Expression doesn't match known safe patterns - warn but don't block
    // This allows custom expressions while still catching obvious issues
    return {
      bindingId: '',
      expression,
      code: 'UNKNOWN_EXPRESSION_PATTERN',
      message: 'Expression does not match known safe patterns - review manually',
    };
  }

  return null;
}

/**
 * Validates a binding's source expression.
 */
export function validateBindingSource(
  binding: Binding,
): BindingExpressionError | null {
  return validateBindingExpression(binding.source);
}

/**
 * Validates a binding's transform expression (if present).
 */
export function validateBindingTransform(
  binding: Binding,
): BindingExpressionError | null {
  if (!binding.transform) {
    return null;
  }
  return validateBindingExpression(binding.transform);
}

/**
 * Validates all expressions in a binding.
 */
export function validateBindingExpressions(
  binding: Binding,
): BindingExpressionValidationResult {
  const errors: BindingExpressionError[] = [];

  const sourceError = validateBindingSource(binding);
  if (sourceError) {
    errors.push({
      ...sourceError,
      bindingId: binding.id,
    });
  }

  const transformError = validateBindingTransform(binding);
  if (transformError) {
    errors.push({
      ...transformError,
      bindingId: binding.id,
    });
  }

  return {
    valid: errors.filter(e => e.code !== 'UNKNOWN_EXPRESSION_PATTERN').length === 0,
    errors,
  };
}

/**
 * Validates all bindings in an array.
 */
export function validateAllBindingExpressions(
  bindings: readonly Binding[],
): BindingExpressionValidationResult {
  const allErrors: BindingExpressionError[] = [];

  for (const binding of bindings) {
    const result = validateBindingExpressions(binding);
    allErrors.push(...result.errors);
  }

  return {
    valid: allErrors.filter(e => e.code !== 'UNKNOWN_EXPRESSION_PATTERN').length === 0,
    errors: allErrors,
  };
}

// ============================================================================
// EXPRESSION TYPE INFERENCE (Basic)
// ============================================================================

/**
 * Basic type inference for binding expressions.
 * Returns a likely type for the expression result.
 */
export function inferExpressionType(
  expression: string,
): 'string' | 'number' | 'boolean' | 'array' | 'object' | 'unknown' {
  const trimmed = expression.trim();

  // Array access patterns
  if (/\[\d+\]$/.test(trimmed) || /^map\(/.test(trimmed) || /^filter\(/.test(trimmed)) {
    return 'array';
  }

  // Boolean patterns
  if (/^(true|false)$/.test(trimmed) || /===|!==|>|<|>=|<=/.test(trimmed)) {
    return 'boolean';
  }

  // Number patterns
  if (/^\d+$/.test(trimmed) || /^length$/.test(trimmed) || /\.length/.test(trimmed)) {
    return 'number';
  }

  // Object patterns
  if (/^\{.*\}$/.test(trimmed) || /\.\w+\.\w+/.test(trimmed)) {
    return 'object';
  }

  // Default to unknown for complex expressions
  return 'unknown';
}
