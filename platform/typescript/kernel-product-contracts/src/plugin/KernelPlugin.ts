/**
 * KernelPlugin - base contract for Kernel plugins.
 *
 * @doc.type interface
 * @doc.purpose Base contract for Kernel plugins
 * @doc.layer kernel-product-contracts
 * @doc.pattern Plugin
 */

import type { PluginKind } from "./PluginKind.js";

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
