/**
 * KernelPluginBinding - represents a binding between a plugin and a ProductUnit.
 *
 * Bindings determine which plugins are active for which ProductUnits and
 * with what configuration.
 *
 * @doc.type interface
 * @doc.purpose Plugin-to-ProductUnit binding representation
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

import type { PluginRef } from './PluginRef';

/**
 * Represents a binding between a plugin and a ProductUnit.
 */
export interface KernelPluginBinding {
  /**
   * Unique identifier for the binding.
   */
  readonly id: string;

  /**
   * Reference to the plugin being bound.
   */
  readonly pluginRef: PluginRef;

  /**
   * ProductUnit identifier this binding applies to.
   */
  readonly productUnitId: string;

  /**
   * Whether the binding is enabled.
   */
  readonly enabled: boolean;

  /**
   * Plugin-specific configuration for this binding.
   */
  readonly config?: Record<string, unknown>;

  /**
   * Lifecycle hooks this binding subscribes to (subset of plugin's available hooks).
   */
  readonly lifecycleHooks: readonly string[];

  /**
   * Priority of this binding (higher values take precedence).
   */
  readonly priority: number;

  /**
   * Conditions under which this binding is active.
   */
  readonly conditions?: PluginBindingCondition[];
}

/**
 * Conditions under which a plugin binding is active.
 */
export interface PluginBindingCondition {
  /**
   * Type of condition (e.g., "phase", "environment", "surface").
   */
  readonly type: string;

  /**
   * Operator for the condition (e.g., "equals", "matches", "in").
   */
  readonly operator: string;

  /**
   * Value to compare against.
   */
  readonly value: string | string[];
}

/**
 * Type guard to check if a value is a KernelPluginBinding.
 */
export function isKernelPluginBinding(value: unknown): value is KernelPluginBinding {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const binding = value as Record<string, unknown>;
  return (
    typeof binding.id === 'string' &&
    typeof binding.pluginRef === 'object' &&
    typeof binding.productUnitId === 'string' &&
    typeof binding.enabled === 'boolean' &&
    Array.isArray(binding.lifecycleHooks) &&
    typeof binding.priority === 'number'
  );
}
