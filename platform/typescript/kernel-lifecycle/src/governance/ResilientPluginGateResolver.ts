/**
 * ResilientPluginGateResolver - plugin gate resolver with timeout, circuit breaker, and backpressure.
 *
 * This extends the base PluginGateResolver with production-grade resilience features:
 * - Timeout enforcement for plugin execution
 * - Circuit breaker pattern to prevent cascading failures
 * - Backpressure control via concurrent execution limits
 *
 * @doc.type class
 * @doc.purpose Resilient plugin gate resolution with timeout, circuit breaker, and backpressure
 * @doc.layer kernel-lifecycle
 * @doc.pattern Service
 */

import type {
  PluginKind,
  PluginRef,
  PluginExecutionContext,
  PluginExecutionResult,
  KernelPlugin,
} from "@ghatana/kernel-product-contracts";

/**
 * Circuit breaker state for a plugin.
 */
interface CircuitBreakerState {
  readonly state: "closed" | "open" | "half-open";
  readonly failureCount: number;
  readonly lastFailureTime: number;
  readonly lastSuccessTime: number;
}

/**
 * Resilient plugin gate resolver options.
 */
export interface ResilientPluginGateResolverOptions {
  /**
   * Path to the plugin registry file.
   */
  readonly registryPath?: string;

  /**
   * Available runtime services.
   */
  readonly availableRuntimeServices?: readonly string[];

  /**
   * Default timeout for plugin execution in milliseconds.
   */
  readonly defaultTimeoutMs?: number;

  /**
   * Maximum concurrent plugin executions (backpressure limit).
   */
  readonly maxConcurrentExecutions?: number;

  /**
   * Circuit breaker failure threshold before opening.
   */
  readonly circuitBreakerFailureThreshold?: number;

  /**
   * Circuit breaker reset timeout in milliseconds.
   */
  readonly circuitBreakerResetTimeoutMs?: number;
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

function writePluginGateDiagnostic(
  level: "warn" | "error",
  message: string,
  context?: Record<string, unknown>,
): void {
  process.stderr.write(
    `${JSON.stringify({ level, message, ...context, ts: new Date().toISOString() })}\n`,
  );
}

/**
 * Resilient plugin gate resolver with timeout, circuit breaker, and backpressure.
 */
export class ResilientPluginGateResolver {
  private readonly registryPath: string;
  private readonly availableRuntimeServices: Set<string>;
  private readonly defaultTimeoutMs: number;
  private readonly maxConcurrentExecutions: number;
  private readonly circuitBreakerFailureThreshold: number;
  private readonly circuitBreakerResetTimeoutMs: number;
  private cachedRegistry: PluginRegistry | null = null;
  private readonly circuitBreakers = new Map<string, CircuitBreakerState>();
  private activeExecutions = 0;
  private readonly executionQueue: Array<() => void> = [];

  constructor(options: ResilientPluginGateResolverOptions = {}) {
    this.registryPath =
      options.registryPath ??
      "config/kernel-plugin-registry.json";
    this.availableRuntimeServices = new Set(
      options.availableRuntimeServices ?? []
    );
    this.defaultTimeoutMs = options.defaultTimeoutMs ?? 30000; // 30 seconds default
    this.maxConcurrentExecutions = options.maxConcurrentExecutions ?? 10;
    this.circuitBreakerFailureThreshold = options.circuitBreakerFailureThreshold ?? 5;
    this.circuitBreakerResetTimeoutMs = options.circuitBreakerResetTimeoutMs ?? 60000; // 1 minute
  }

  private async loadRegistry(): Promise<PluginRegistry> {
    if (this.cachedRegistry) {
      return this.cachedRegistry;
    }

    const content = await import("node:fs/promises").then(fs => fs.readFile(this.registryPath, "utf-8"));
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
        writePluginGateDiagnostic("warn", "Skipping plugin with missing required runtime services", {
          pluginId: entry.id,
          requiredRuntimeServices: entry.requiredRuntimeServices,
        });
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
   * Get or create circuit breaker state for a plugin.
   */
  private getCircuitBreakerState(pluginId: string): CircuitBreakerState {
    const existing = this.circuitBreakers.get(pluginId);
    if (existing) {
      return existing;
    }

    const newState: CircuitBreakerState = {
      state: "closed",
      failureCount: 0,
      lastFailureTime: 0,
      lastSuccessTime: 0,
    };
    this.circuitBreakers.set(pluginId, newState);
    return newState;
  }

  /**
   * Check if circuit breaker allows execution for a plugin.
   */
  private canExecutePlugin(pluginId: string): boolean {
    const state = this.getCircuitBreakerState(pluginId);
    const now = Date.now();

    if (state.state === "open") {
      // Check if reset timeout has elapsed
      if (now - state.lastFailureTime > this.circuitBreakerResetTimeoutMs) {
        // Transition to half-open
        this.circuitBreakers.set(pluginId, {
          ...state,
          state: "half-open",
        });
        return true;
      }
      return false;
    }

    return true;
  }

  /**
   * Record plugin execution success.
   */
  private recordPluginSuccess(pluginId: string): void {
    const state = this.getCircuitBreakerState(pluginId);
    this.circuitBreakers.set(pluginId, {
      state: "closed",
      failureCount: 0,
      lastFailureTime: state.lastFailureTime,
      lastSuccessTime: Date.now(),
    });
  }

  /**
   * Record plugin execution failure.
   */
  private recordPluginFailure(pluginId: string): void {
    const state = this.getCircuitBreakerState(pluginId);
    const newFailureCount = state.failureCount + 1;

    if (newFailureCount >= this.circuitBreakerFailureThreshold) {
      // Open the circuit breaker
      this.circuitBreakers.set(pluginId, {
        state: "open",
        failureCount: newFailureCount,
        lastFailureTime: Date.now(),
        lastSuccessTime: state.lastSuccessTime,
      });
      writePluginGateDiagnostic("warn", "Circuit breaker opened for plugin", {
        pluginId,
        failureCount: newFailureCount,
      });
    } else {
      this.circuitBreakers.set(pluginId, {
        ...state,
        failureCount: newFailureCount,
        lastFailureTime: Date.now(),
      });
    }
  }

  /**
   * Execute with timeout enforcement.
   */
  private async executeWithTimeout<T>(
    pluginId: string,
    fn: () => Promise<T>,
    timeoutMs: number
  ): Promise<T> {
    const timeoutPromise = new Promise<never>((_, reject) => {
      setTimeout(() => {
        reject(new Error(`Plugin ${pluginId} execution timed out after ${timeoutMs}ms`));
      }, timeoutMs);
    });

    return Promise.race([fn(), timeoutPromise]);
  }

  /**
   * Acquire execution slot (backpressure).
   */
  private async acquireExecutionSlot(): Promise<void> {
    if (this.activeExecutions < this.maxConcurrentExecutions) {
      this.activeExecutions++;
      return;
    }

    // Wait for a slot to become available
    await new Promise<void>((resolve) => {
      this.executionQueue.push(resolve);
    });
    this.activeExecutions++;
  }

  /**
   * Release execution slot.
   */
  private releaseExecutionSlot(): void {
    this.activeExecutions--;
    const next = this.executionQueue.shift();
    if (next) {
      next();
    }
  }

  /**
   * Execute plugin gates for a lifecycle phase with resilience features.
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
      // Check circuit breaker
      if (!this.canExecutePlugin(pluginRef.pluginId)) {
        results.push({
          success: false,
          message: `Plugin execution skipped: circuit breaker is open`,
          durationMs: 0,
        });
        writePluginGateDiagnostic("warn", "Plugin execution skipped due to open circuit breaker", {
          pluginId: pluginRef.pluginId,
        });
        continue;
      }

      // Acquire execution slot (backpressure)
      await this.acquireExecutionSlot();

      try {
        const result = await this.executeWithTimeout(
          pluginRef.pluginId,
          async () => {
            const plugin = await pluginLoader(pluginRef.contractPath);
            return await plugin.execute(context);
          },
          this.defaultTimeoutMs
        );

        results.push(result);
        this.recordPluginSuccess(pluginRef.pluginId);
      } catch (error) {
        results.push({
          success: false,
          message: error instanceof Error ? error.message : String(error),
          durationMs: 0,
        });
        this.recordPluginFailure(pluginRef.pluginId);
        writePluginGateDiagnostic("error", "Plugin execution failed", {
          pluginId: pluginRef.pluginId,
          error: error instanceof Error ? error.message : String(error),
        });
      } finally {
        this.releaseExecutionSlot();
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

    // Check circuit breaker state
    if (!this.canExecutePlugin(pluginId)) {
      return false;
    }

    return true;
  }

  /**
   * Get circuit breaker state for a plugin (for observability).
   */
  getCircuitBreakerStateForPlugin(pluginId: string): CircuitBreakerState | undefined {
    return this.circuitBreakers.get(pluginId);
  }

  /**
   * Reset circuit breaker for a plugin (for recovery).
   */
  resetCircuitBreaker(pluginId: string): void {
    this.circuitBreakers.set(pluginId, {
      state: "closed",
      failureCount: 0,
      lastFailureTime: 0,
      lastSuccessTime: 0,
    });
    writePluginGateDiagnostic("warn", "Circuit breaker manually reset for plugin", {
      pluginId,
    });
  }
}
