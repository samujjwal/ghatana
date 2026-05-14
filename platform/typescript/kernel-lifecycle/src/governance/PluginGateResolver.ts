/**
 * PluginGateResolver - resolves plugin gates for lifecycle phases.
 *
 * This component resolves and executes plugin gates for lifecycle phases,
 * filtering plugins by kind and checking their required runtime services.
 *
 * @doc.type class
 * @doc.purpose Resolves plugin gates for lifecycle phases
 * @doc.layer kernel-lifecycle
 * @doc.pattern Service
 */

import * as fs from "node:fs/promises";
import * as path from "node:path";
import type {
  PluginKind,
  PluginRef,
  PluginExecutionContext,
  PluginExecutionResult,
  KernelPlugin,
} from "@ghatana/kernel-product-contracts";

/**
 * Plugin gate resolver options.
 */
export interface PluginGateResolverOptions {
  /**
   * Path to the plugin registry file.
   */
  readonly registryPath?: string;

  /**
   * Available runtime services.
   */
  readonly availableRuntimeServices?: readonly string[];
}

/**
 * Plugin registry entry.
 */
interface PluginRegistryEntry {
  readonly id: string;
  readonly name: string;
  readonly kind: PluginKind;
  readonly enabled: boolean;
  readonly contractPath: string;
  readonly capabilities: readonly string[];
  readonly requiredRuntimeServices?: readonly string[];
  readonly lifecycleHooks: readonly string[];
}

/**
 * Plugin registry structure.
 */
interface PluginRegistry {
  readonly version: string;
  readonly plugins: Record<string, PluginRegistryEntry>;
}

/**
 * Plugin gate resolver.
 */
export class PluginGateResolver {
  private readonly registryPath: string;
  private readonly availableRuntimeServices: Set<string>;
  private cachedRegistry: PluginRegistry | null = null;

  constructor(options: PluginGateResolverOptions = {}) {
    this.registryPath =
      options.registryPath ??
      path.join(process.cwd(), "config/kernel-plugin-registry.json");
    this.availableRuntimeServices = new Set(
      options.availableRuntimeServices ?? []
    );
  }

  private async loadRegistry(): Promise<PluginRegistry> {
    if (this.cachedRegistry) {
      return this.cachedRegistry;
    }

    const content = await fs.readFile(this.registryPath, "utf-8");
    this.cachedRegistry = JSON.parse(content) as PluginRegistry;
    return this.cachedRegistry;
  }

  /**
   * Resolve plugin gates for a lifecycle phase.
   */
  async resolvePluginGates(
    _phase: string,
    phaseKind: "phase" | "gate" | "deployment"
  ): Promise<readonly PluginRef[]> {
    const registry = await this.loadRegistry();
    const pluginRefs: PluginRef[] = [];

    for (const [_key, entry] of Object.entries(registry.plugins)) {
      if (!entry.enabled) {
        continue; // Skip disabled plugins
      }

      // Filter by kind matching phase
      const kindMatch = this.matchesPhaseKind(entry.kind, phaseKind);
      if (!kindMatch) {
        continue;
      }

      // Check if required runtime services are available
      if (!this.hasRequiredServices(entry.requiredRuntimeServices)) {
        console.warn(
          `[PluginGateResolver] Skipping plugin "${entry.id}" - missing required runtime services: ${entry.requiredRuntimeServices?.join(", ")}`
        );
        continue;
      }

      pluginRefs.push({
        pluginId: entry.id,
        kind: entry.kind,
        enabled: entry.enabled,
        contractPath: entry.contractPath,
      });
    }

    return pluginRefs;
  }

  private matchesPhaseKind(kind: PluginKind, phaseKind: "phase" | "gate" | "deployment"): boolean {
    if (phaseKind === "phase") {
      return kind === "pre-phase" || kind === "post-phase";
    }
    if (phaseKind === "gate") {
      return kind === "pre-gate" || kind === "post-gate";
    }
    if (phaseKind === "deployment") {
      return kind === "pre-deployment" || kind === "post-deployment";
    }
    return false;
  }

  private hasRequiredServices(requiredServices?: readonly string[]): boolean {
    if (!requiredServices || requiredServices.length === 0) {
      return true;
    }

    return requiredServices.every((service) =>
      this.availableRuntimeServices.has(service)
    );
  }

  /**
   * Execute plugin gates for a lifecycle phase.
   */
  async executePluginGates(
    phase: string,
    phaseKind: "phase" | "gate" | "deployment",
    context: PluginExecutionContext,
    pluginLoader: (contractPath: string) => Promise<KernelPlugin>
  ): Promise<readonly PluginExecutionResult[]> {
    const pluginRefs = await this.resolvePluginGates(phase, phaseKind);
    const results: PluginExecutionResult[] = [];

    for (const pluginRef of pluginRefs) {
      const startTime = Date.now();

      try {
        const plugin = await pluginLoader(pluginRef.contractPath);
        const result = await plugin.execute(context);
        results.push(result);
      } catch (error) {
        const duration = Date.now() - startTime;
        results.push({
          success: false,
          message: error instanceof Error ? error.message : String(error),
          durationMs: duration,
        });
        console.error(
          `[PluginGateResolver] Plugin "${pluginRef.pluginId}" execution failed:`,
          error
        );
      }
    }

    return results;
  }

  /**
   * Check if a plugin gate is enabled.
   */
  async checkPluginGateEnabled(pluginId: string): Promise<boolean> {
    const registry = await this.loadRegistry();
    const entry = registry.plugins[pluginId];

    if (!entry) {
      return false;
    }

    if (!entry.enabled) {
      return false;
    }

    if (!this.hasRequiredServices(entry.requiredRuntimeServices)) {
      return false;
    }

    return true;
  }
}
