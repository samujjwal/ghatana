/**
 * KernelPlugin - base contract for Kernel plugins.
 *
 * @doc.type interface
 * @doc.purpose Base contract for Kernel plugins
 * @doc.layer kernel-product-contracts
 * @doc.pattern Plugin
 */

import { z } from "zod";
import type { PluginKind } from "./PluginKind.js";
import { PluginKindSchema } from "./PluginKind.js";

/**
 * Plugin execution context.
 */
export interface PluginExecutionContext {
  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Lifecycle run identifier.
   */
  readonly runId: string;

  /**
   * Current lifecycle phase.
   */
  readonly phase: string;

  /**
   * Current surface (if applicable).
   */
  readonly surface?: string;

  /**
   * Current environment (if applicable).
   */
  readonly environment?: string;

  /**
   * Additional metadata.
   */
  readonly metadata?: Record<string, unknown>;
}

export const PluginExecutionContextSchema = z
  .object({
    productUnitId: z.string().trim().min(1),
    runId: z.string().trim().min(1),
    phase: z.string().trim().min(1),
    surface: z.string().trim().min(1).optional(),
    environment: z.string().trim().min(1).optional(),
    metadata: z.record(z.string(), z.unknown()).optional(),
  })
  .strict();

/**
 * Plugin execution result.
 */
export interface PluginExecutionResult {
  /**
   * Whether the plugin execution succeeded.
   */
  readonly success: boolean;

  /**
   * Result message.
   */
  readonly message: string;

  /**
   * Additional data from plugin execution.
   */
  readonly data?: Record<string, unknown>;

  /**
   * Execution duration in milliseconds.
   */
  readonly durationMs: number;
}

export const PluginExecutionResultSchema = z
  .object({
    success: z.boolean(),
    message: z.string().trim().min(1),
    data: z.record(z.string(), z.unknown()).optional(),
    durationMs: z.number().nonnegative(),
  })
  .strict();

/**
 * Kernel plugin contract.
 */
export interface KernelPlugin {
  /**
   * Plugin identifier.
   */
  readonly pluginId: string;

  /**
   * Plugin kind.
   */
  readonly kind: PluginKind;

  /**
   * Plugin capabilities.
   */
  readonly capabilities: readonly string[];

  /**
   * Lifecycle hooks the plugin subscribes to.
   */
  readonly lifecycleHooks: readonly string[];

  /**
   * Required runtime services.
   */
  readonly requiredRuntimeServices?: readonly string[];

  /**
   * Execute the plugin.
   */
  execute(
    context: PluginExecutionContext
  ): Promise<PluginExecutionResult>;
}

export const KernelPluginSchema = z.custom<KernelPlugin>(
  (value) => {
    if (typeof value !== "object" || value === null) {
      return false;
    }
    const plugin = value as Record<string, unknown>;
    return (
      typeof plugin.pluginId === "string" &&
      PluginKindSchema.safeParse(plugin.kind).success &&
      Array.isArray(plugin.capabilities) &&
      Array.isArray(plugin.lifecycleHooks) &&
      typeof plugin.execute === "function"
    );
  },
  "KernelPlugin requires plugin metadata and an execute function"
);

export function validatePluginExecutionContext(
  value: unknown
): value is PluginExecutionContext {
  return PluginExecutionContextSchema.safeParse(value).success;
}

export function validatePluginExecutionResult(
  value: unknown
): value is PluginExecutionResult {
  return PluginExecutionResultSchema.safeParse(value).success;
}

export function validateKernelPlugin(value: unknown): value is KernelPlugin {
  return KernelPluginSchema.safeParse(value).success;
}
