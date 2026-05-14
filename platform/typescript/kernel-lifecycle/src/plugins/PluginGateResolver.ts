/**
 * PluginGateResolver - converts plugin bindings into gate plan items.
 *
 * This class is responsible for resolving which gates should be added to a
 * lifecycle plan based on plugin bindings. It operates without product-specific
 * logic, using only the generic plugin contract information.
 *
 * @doc.type class
 * @doc.purpose Converts plugin bindings into gate plan items
 * @doc.layer platform
 * @doc.pattern Resolver
 */

import type {
  KernelPluginBinding,
  KernelPluginGateResult,
} from '@ghatana/kernel-product-contracts';
import type { ProductGatePlan, ProductLifecyclePhase } from '../domain/ProductLifecyclePhase.js';

/**
 * Configuration for plugin gate resolution.
 */
export interface PluginGateResolverConfig {
  /**
   * Whether to include safe-for-default plugins.
   */
  readonly includeSafeForDefault: boolean;

  /**
   * Whether to include declared-only plugins.
   */
  readonly includeDeclaredOnly: boolean;
}

/**
 * Resolves gates from plugin bindings for lifecycle planning.
 */
export class PluginGateResolver {
  constructor() {}

  /**
   * Resolves gates from plugin bindings for a given ProductUnit.
   *
   * @param pluginBindings - Plugin bindings for the ProductUnit
   * @param phase - Current lifecycle phase
   * @returns Gate plan items derived from plugin bindings
   */
  resolveGatesFromBindings(
    pluginBindings: readonly KernelPluginBinding[],
    phase: ProductLifecyclePhase,
  ): ProductGatePlan[] {
    const gates: ProductGatePlan[] = [];

    for (const binding of pluginBindings) {
      if (!this.shouldIncludeBinding(binding)) {
        continue;
      }

      if (!this.isBindingActiveForPhase(binding, phase)) {
        continue;
      }

      const pluginGates = this.extractGateOutputs(binding);
      for (const gateId of pluginGates) {
        gates.push({
          gateId,
          gateName: gateId,
          required: true,
          phase,
          status: 'pending',
        });
      }
    }

    return this.deduplicateGates(gates);
  }

  /**
   * Determines whether a binding should be included based on configuration.
   */
  private shouldIncludeBinding(binding: KernelPluginBinding): boolean {
    if (!binding.enabled) {
      return false;
    }

    // Apply configuration-based filtering
    // For now, all enabled bindings are included
    // Future implementation would check plugin status against this._config
    return true;
  }

  /**
   * Determines whether a binding is active for a given phase.
   */
  private isBindingActiveForPhase(binding: KernelPluginBinding, phase: string): boolean {
    // Check if the binding has lifecycle hooks that match the phase
    // This is a simplified check - in production, you'd map phase names to hook names
    const phaseHookMap: Record<string, string> = {
      validate: 'onProductValidated',
      test: 'onProductTested',
      build: 'onProductBuildCompleted',
      package: 'onProductPackaged',
      deploy: 'onProductDeployed',
      verify: 'onProductVerified',
      promote: 'onProductPromoted',
      rollback: 'onProductRolledBack',
    };

    const expectedHook = phaseHookMap[phase];
    if (!expectedHook) {
      return false;
    }

    return binding.lifecycleHooks.includes(expectedHook);
  }

  /**
   * Extracts gate outputs from a plugin binding.
   *
   * In a full implementation, this would load the plugin registry and
   * retrieve the gateOutput declarations for the plugin. For now,
   * this is a placeholder that would be implemented when the plugin
   * registry is fully integrated.
   */
  private extractGateOutputs(_binding: KernelPluginBinding): string[] {
    // Placeholder: In production, this would:
    // 1. Load plugin from registry using _binding.pluginRef
    // 2. Extract gateOutput array from plugin definition
    // 3. Return the gate IDs

    // For now, return empty array as plugin registry integration is pending
    return [];
  }

  /**
   * Deduplicates gates by gateId, keeping the first occurrence.
   */
  private deduplicateGates(gates: ProductGatePlan[]): ProductGatePlan[] {
    const seen = new Set<string>();
    const deduplicated: ProductGatePlan[] = [];

    for (const gate of gates) {
      if (!seen.has(gate.gateId)) {
        seen.add(gate.gateId);
        deduplicated.push(gate);
      }
    }

    return deduplicated;
  }

  /**
   * Converts plugin gate results into gate plan items for re-evaluation.
   *
   * @param gateResults - Previous gate results from plugins
   * @returns Gate plan items for re-evaluation
   */
  resolveGatesFromResults(gateResults: readonly KernelPluginGateResult[]): ProductGatePlan[] {
    return gateResults.map((result) => ({
      gateId: result.gateId,
      gateName: result.gateId,
      required: true,
      phase: 'validate', // Use validate phase for re-evaluation
      status: result.passed ? 'passed' : 'failed',
    }));
  }
}
