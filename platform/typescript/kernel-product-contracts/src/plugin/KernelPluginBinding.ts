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

import { z } from "zod";
import type { PluginRef } from './PluginRef.js';
import { PluginRefSchema } from './PluginRef.js';

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

export const PluginBindingConditionSchema = z
  .object({
    type: z.string().trim().min(1),
    operator: z.string().trim().min(1),
    value: z.union([
      z.string().trim().min(1),
      z.array(z.string().trim().min(1)),
    ]),
  })
  .strict();

export const KernelPluginBindingSchema = z
  .object({
    id: z.string().trim().min(1),
    pluginRef: PluginRefSchema,
    productUnitId: z.string().trim().min(1),
    enabled: z.boolean(),
    config: z.record(z.string(), z.unknown()).optional(),
    lifecycleHooks: z.array(z.string().trim().min(1)),
    priority: z.number(),
    conditions: z.array(PluginBindingConditionSchema).optional(),
  })
  .strict();

/**
 * Type guard to check if a value is a KernelPluginBinding.
 */
export function isKernelPluginBinding(value: unknown): value is KernelPluginBinding {
  return KernelPluginBindingSchema.safeParse(value).success;
}

export function validatePluginBindingCondition(
  value: unknown
): value is PluginBindingCondition {
  return PluginBindingConditionSchema.safeParse(value).success;
}
