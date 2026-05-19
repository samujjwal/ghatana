/**
 * @fileoverview Binding Runtime with Safe Policies
 *
 * Evaluates data bindings with safety policies including expression sandboxing,
 * recursion limits, and resource limits to prevent abuse and ensure stability.
 *
 * @doc.type module
 * @doc.purpose Safe binding evaluation with policies
 * @doc.layer platform
 * @doc.pattern RuntimeSafety
 */

import type { Binding } from './types.js';
import { validateBindingExpression } from './binding-expression-validation.js';

// ============================================================================
// BINDING EVALUATION POLICIES
// ============================================================================

export interface BindingEvaluationPolicy {
  /** Maximum time for binding evaluation (ms) */
  timeoutMs: number;
  /** Maximum recursion depth for nested object access */
  maxRecursionDepth: number;
  /** Maximum number of property accesses per evaluation */
  maxPropertyAccesses: number;
  /** Whether to allow function calls in expressions */
  allowFunctionCalls: boolean;
  /** Whether to allow array access */
  allowArrayAccess: boolean;
}

export const DEFAULT_BINDING_POLICY: BindingEvaluationPolicy = {
  timeoutMs: 1000,
  maxRecursionDepth: 10,
  maxPropertyAccesses: 50,
  allowFunctionCalls: false,
  allowArrayAccess: true,
};

export const RESTRICTED_BINDING_POLICY: BindingEvaluationPolicy = {
  timeoutMs: 500,
  maxRecursionDepth: 5,
  maxPropertyAccesses: 10,
  allowFunctionCalls: false,
  allowArrayAccess: false,
};

// ============================================================================
// BINDING EVALUATION RESULT
// ============================================================================

export interface BindingEvaluationResult {
  success: boolean;
  value?: unknown;
  error?: string;
  executionTimeMs: number;
}

// ============================================================================
// BINDING EVALUATION CONTEXT
// ============================================================================

export interface BindingEvaluationContext {
  /** Component props */
  props: Record<string, unknown>;
  /** Component state */
  state?: Record<string, unknown>;
  /** Theme tokens */
  theme?: Record<string, unknown>;
  /** External data sources */
  dataSource?: Record<string, unknown>;
  /** Global application context */
  app?: Record<string, unknown>;
}

// ============================================================================
// BINDING EVALUATOR
// ============================================================================

export class BindingEvaluator {
  private policy: BindingEvaluationPolicy;

  constructor(policy: Partial<BindingEvaluationPolicy> = {}) {
    this.policy = { ...DEFAULT_BINDING_POLICY, ...policy };
  }

  /**
   * Evaluate a binding expression with safety policies.
   */
  async evaluate(
    binding: Binding,
    context: BindingEvaluationContext,
  ): Promise<BindingEvaluationResult> {
    const startTime = Date.now();

    // Validate binding expression
    const expressionError = validateBindingExpression(binding.source);
    if (expressionError && expressionError.code !== 'UNKNOWN_EXPRESSION_PATTERN') {
      return {
        success: false,
        error: expressionError.message,
        executionTimeMs: Date.now() - startTime,
      };
    }

    // Evaluate with timeout
    try {
      const value = await this.evaluateWithTimeout(binding, context);
      return {
        success: true,
        value,
        executionTimeMs: Date.now() - startTime,
      };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : String(error),
        executionTimeMs: Date.now() - startTime,
      };
    }
  }

  private async evaluateWithTimeout(
    binding: Binding,
    context: BindingEvaluationContext,
  ): Promise<unknown> {
    return new Promise((resolve, reject) => {
      const timeoutId = setTimeout(() => {
        reject(new Error(`Binding evaluation timeout after ${this.policy.timeoutMs}ms`));
      }, this.policy.timeoutMs);

      try {
        const value = this.evaluateBinding(binding, context);
        resolve(value);
      } catch (error) {
        reject(error);
      } finally {
        clearTimeout(timeoutId);
      }
    });
  }

  private evaluateBinding(
    binding: Binding,
    context: BindingEvaluationContext,
  ): unknown {
    let value = this.evaluateExpression(binding.source, context, 0, 0);

    // Apply transform if present
    if (binding.transform) {
      value = this.applyTransform(binding.transform, value, context);
    }

    return value;
  }

  private evaluateExpression(
    expression: string,
    context: BindingEvaluationContext,
    depth: number,
    accessCount: number,
  ): unknown {
    // Check recursion depth
    if (depth > this.policy.maxRecursionDepth) {
      throw new Error(`Maximum recursion depth ${this.policy.maxRecursionDepth} exceeded`);
    }

    // Check property access count
    if (accessCount > this.policy.maxPropertyAccesses) {
      throw new Error(`Maximum property accesses ${this.policy.maxPropertyAccesses} exceeded`);
    }

    const trimmed = expression.trim();

    // Handle simple property access: props.value, state.name, theme.colors.primary
    const parts = trimmed.split('.');
    if (parts.length > 1) {
      const root = parts[0];
      const remainingPath = parts.slice(1).join('.');

      let rootValue: unknown;
      switch (root) {
        case 'props':
          rootValue = context.props;
          break;
        case 'state':
          rootValue = context.state;
          break;
        case 'theme':
          rootValue = context.theme;
          break;
        case 'dataSource':
          rootValue = context.dataSource;
          break;
        case 'app':
          rootValue = context.app;
          break;
        default:
          // Unknown root - return undefined
          return undefined;
      }

      if (rootValue === undefined) {
        return undefined;
      }

      return this.evaluatePath(rootValue, remainingPath, depth + 1, accessCount);
    }

    // Handle array access: users[0]
    const arrayMatch = trimmed.match(/^(\w+)\[(\d+)\]$/);
    if (arrayMatch) {
      if (!this.policy.allowArrayAccess) {
        throw new Error('Array access is not allowed under current policy');
      }

      const [, root, indexStr] = arrayMatch;
      const index = parseInt(indexStr, 10);

      let rootValue: unknown;
      switch (root) {
        case 'props':
          rootValue = context.props;
          break;
        case 'state':
          rootValue = context.state;
          break;
        case 'dataSource':
          rootValue = context.dataSource;
          break;
        default:
          return undefined;
      }

      if (Array.isArray(rootValue) && index >= 0 && index < rootValue.length) {
        return rootValue[index];
      }

      return undefined;
    }

    // Handle simple root access
    switch (trimmed) {
      case 'props':
        return context.props;
      case 'state':
        return context.state;
      case 'theme':
        return context.theme;
      case 'dataSource':
        return context.dataSource;
      case 'app':
        return context.app;
      default:
        return undefined;
    }
  }

  private evaluatePath(
    obj: unknown,
    path: string,
    depth: number,
    accessCount: number,
  ): unknown {
    if (obj === null || obj === undefined) {
      return undefined;
    }

    const parts = path.split('.');
    let current: unknown = obj;

    for (let i = 0; i < parts.length; i++) {
      const part = parts[i];
      const newAccessCount = accessCount + i + 1;

      if (newAccessCount > this.policy.maxPropertyAccesses) {
        throw new Error(`Maximum property accesses ${this.policy.maxPropertyAccesses} exceeded`);
      }

      if (typeof current === 'object' && current !== null) {
        current = (current as Record<string, unknown>)[part];
      } else {
        return undefined;
      }
    }

    return current;
  }

  private applyTransform(
    transform: string,
    value: unknown,
    context: BindingEvaluationContext,
  ): unknown {
    // Check if function calls are allowed
    if (!this.policy.allowFunctionCalls) {
      throw new Error('Transform functions are not allowed under current policy');
    }

    // Simple transform: map(u => u.name)
    const mapMatch = transform.match(/^map\((.*)\s*=>\s*(.*)\)$/);
    if (mapMatch && Array.isArray(value)) {
      const [, param, expr] = mapMatch;
      return value.map((item) => {
        // Create a temporary context with the parameter
        const tempContext = { ...context, [param.trim()]: item };
        return this.evaluateExpression(expr.trim(), tempContext, 0, 0);
      });
    }

    // If transform doesn't match known patterns, return value as-is
    return value;
  }

  /**
   * Update the evaluation policy.
   */
  setPolicy(policy: Partial<BindingEvaluationPolicy>): void {
    this.policy = { ...this.policy, ...policy };
  }

  /**
   * Get the current evaluation policy.
   */
  getPolicy(): BindingEvaluationPolicy {
    return { ...this.policy };
  }

  /**
   * Evaluate multiple bindings in batch.
   */
  async evaluateBatch(
    bindings: readonly Binding[],
    context: BindingEvaluationContext,
  ): Promise<Map<string, BindingEvaluationResult>> {
    const results = new Map<string, BindingEvaluationResult>();

    for (const binding of bindings) {
      const result = await this.evaluate(binding, context);
      results.set(binding.id, result);
    }

    return results;
  }
}
