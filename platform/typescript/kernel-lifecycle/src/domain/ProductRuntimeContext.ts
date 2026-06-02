/**
 * ProductRuntimeContext - runtime execution context for product lifecycle.
 *
 * <p>Encapsulates the runtime state and metadata for a product during lifecycle
 * execution. This context is passed through lifecycle phases and provides
 * access to execution metadata, surface-specific state, and plugin execution context.
 *
 * @doc.type module
 * @doc.purpose Runtime context for product lifecycle execution
 * @doc.layer kernel-lifecycle
 * @doc.pattern Context
 */

import type { KernelProviderMode } from '@ghatana/kernel-product-contracts';
import type { ProductLifecyclePhase } from './ProductLifecyclePhase';

/**
 * Surface-specific runtime context.
 */
export interface SurfaceRuntimeContext {
  /**
   * Surface identifier (e.g., 'web', 'mobile', 'backend-api').
   */
  readonly surface: string;

  /**
   * Surface-specific configuration.
   */
  readonly config?: Record<string, unknown>;

  /**
   * Surface deployment target.
   */
  readonly deploymentTarget?: string;

  /**
   * Surface health status.
   */
  readonly healthStatus?: 'healthy' | 'degraded' | 'unhealthy';
}

/**
 * Plugin execution state within the runtime context.
 */
export interface PluginExecutionState {
  /**
   * Plugin identifier.
   */
  readonly pluginId: string;

  /**
   * Plugin execution status.
   */
  readonly status: 'pending' | 'running' | 'completed' | 'failed' | 'skipped';

  /**
   * Plugin execution timestamp.
   */
  readonly executedAt?: string;

  /**
   * Plugin error message (if failed).
   */
  readonly error?: string;

  /**
   * Plugin output data.
   */
  readonly output?: Record<string, unknown>;
}

/**
 * Product runtime context for lifecycle execution.
 *
 * <p>This context is created at the start of a lifecycle execution and is
 * passed through all phases. It provides a consistent view of the product's
 * runtime state and is used by plugins, gates, and lifecycle steps.
 */
export interface ProductRuntimeContext {
  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Lifecycle run identifier (unique per execution).
   */
  readonly runId: string;

  /**
   * Current lifecycle phase.
   */
  readonly phase: ProductLifecyclePhase;

  /**
   * Kernel provider mode (bootstrap, platform, provider).
   */
  readonly mode: KernelProviderMode;

  /**
   * Correlation ID for tracing.
   */
  readonly correlationId: string;

  /**
   * Execution start timestamp.
   */
  readonly startedAt: string;

  /**
   * Current execution timestamp.
   */
  readonly currentTimestamp: string;

  /**
   * Surface-specific runtime context (if applicable).
   */
  readonly surfaceContext: SurfaceRuntimeContext | undefined;

  /**
   * Plugin execution state map.
   */
  readonly pluginState: ReadonlyMap<string, PluginExecutionState>;

  /**
   * Runtime metadata collected during execution.
   */
  readonly metadata: Record<string, unknown>;

  /**
   * Execution flags.
   */
  readonly flags: {
    /**
     * Whether this is a dry-run execution.
     */
    readonly dryRun: boolean;

    /**
     * Whether to skip gate validation.
     */
    readonly skipGates: boolean;

    /**
     * Whether to continue on failure.
     */
    readonly continueOnFailure: boolean;
  };
}

/**
 * Creates a new ProductRuntimeContext.
 */
export function createProductRuntimeContext(params: {
  productUnitId: string;
  runId: string;
  phase: ProductLifecyclePhase;
  mode: KernelProviderMode;
  correlationId: string;
  surfaceContext?: SurfaceRuntimeContext;
  flags?: Partial<ProductRuntimeContext['flags']>;
}): ProductRuntimeContext {
  const now = new Date().toISOString();
  return {
    productUnitId: params.productUnitId,
    runId: params.runId,
    phase: params.phase,
    mode: params.mode,
    correlationId: params.correlationId,
    startedAt: now,
    currentTimestamp: now,
    surfaceContext: params.surfaceContext,
    pluginState: new Map(),
    metadata: {},
    flags: {
      dryRun: params.flags?.dryRun ?? false,
      skipGates: params.flags?.skipGates ?? false,
      continueOnFailure: params.flags?.continueOnFailure ?? false,
    },
  };
}

/**
 * Updates the phase in a ProductRuntimeContext.
 */
export function updatePhase(
  context: ProductRuntimeContext,
  newPhase: ProductLifecyclePhase,
): ProductRuntimeContext {
  return {
    ...context,
    phase: newPhase,
    currentTimestamp: new Date().toISOString(),
  };
}

/**
 * Updates plugin execution state in a ProductRuntimeContext.
 */
export function updatePluginState(
  context: ProductRuntimeContext,
  pluginId: string,
  state: PluginExecutionState,
): ProductRuntimeContext {
  const newPluginState = new Map(context.pluginState);
  newPluginState.set(pluginId, state);
  return {
    ...context,
    pluginState: newPluginState,
    currentTimestamp: new Date().toISOString(),
  };
}

/**
 * Adds metadata to a ProductRuntimeContext.
 */
export function addMetadata(
  context: ProductRuntimeContext,
  key: string,
  value: unknown,
): ProductRuntimeContext {
  return {
    ...context,
    metadata: {
      ...context.metadata,
      [key]: value,
    },
    currentTimestamp: new Date().toISOString(),
  };
}
