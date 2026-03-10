/**
 * Kernel Builder - Fluent API for building custom kernels.
 *
 * @doc.type class
 * @doc.purpose Provides fluent builder pattern for kernel configuration
 * @doc.layer product
 * @doc.pattern Builder
 */

import type {
  SimulationManifest,
  SimEntityBase,
  SimKernelService,
  SimKeyframe,
  SimulationDomain,
} from "@ghatana/tutorputor-contracts/v1/simulation";
import { BaseKernel, type KernelHooks, type KernelState } from "./base-kernel";

/**
 * Step executor function type.
 */
type StepExecutor<TState = Record<string, unknown>> = (
  entities: SimEntityBase[],
  state: TState,
  stepDef: SimulationManifest["steps"][number],
) => { entities: SimEntityBase[]; state: TState };

/**
 * Entity initializer function type.
 */
type EntityInitializer = (entities: SimEntityBase[]) => SimEntityBase[];

/**
 * State initializer function type.
 */
type StateInitializer<TState = Record<string, unknown>> = (
  manifest: SimulationManifest,
) => TState;

/**
 * Interpolator function type.
 */
type Interpolator = (entities: SimEntityBase[], t: number) => SimEntityBase[];

/**
 * Analytics extractor function type.
 */
type AnalyticsExtractor<TState = Record<string, unknown>> = (
  state: TState,
) => Record<string, unknown>;

/**
 * Built kernel class.
 */
class BuiltKernel<TState = Record<string, unknown>>
  extends BaseKernel<SimEntityBase, unknown>
  implements SimKernelService
{
  private stepExecutor: StepExecutor<TState>;
  private entityInit: EntityInitializer;
  private stateInit: StateInitializer<TState>;
  private entityInterpolator: Interpolator;
  private analyticsExtractor: AnalyticsExtractor<TState>;
  private customState: TState = {} as TState;

  constructor(
    domain: SimulationDomain,
    stepExecutor: StepExecutor<TState>,
    entityInit: EntityInitializer,
    stateInit: StateInitializer<TState>,
    entityInterpolator: Interpolator,
    analyticsExtractor: AnalyticsExtractor<TState>,
    hooks: KernelHooks<unknown>,
  ) {
    super(domain, undefined, hooks);
    this.stepExecutor = stepExecutor;
    this.entityInit = entityInit;
    this.stateInit = stateInit;
    this.entityInterpolator = entityInterpolator;
    this.analyticsExtractor = analyticsExtractor;
  }

  protected override initializeEntities(
    entities: SimEntityBase[],
  ): SimEntityBase[] {
    return this.entityInit(entities);
  }

  protected override initializeCustomState(
    manifest: SimulationManifest,
  ): Record<string, unknown> {
    this.customState = this.stateInit(manifest);
    return this.customState as Record<string, unknown>;
  }

  protected override executeStep(
    _stepIndex: number,
    stepDef: SimulationManifest["steps"][number],
  ): void {
    const result = this.stepExecutor(
      this.state.entities,
      this.customState,
      stepDef,
    );
    this.state.entities = result.entities;
    this.customState = result.state;
  }

  protected override interpolateEntities(t: number): SimEntityBase[] {
    return this.entityInterpolator(this.state.entities, t);
  }

  protected override getCustomAnalytics(): Record<string, unknown> {
    return this.analyticsExtractor(this.customState);
  }
}

/**
 * Kernel Builder class.
 */
export class KernelBuilder<TState = Record<string, unknown>> {
  private stepExecutor: StepExecutor<TState> = (entities, state) => ({
    entities,
    state,
  });
  private entityInit: EntityInitializer = (e) => e.map((x) => ({ ...x }));
  private stateInit: StateInitializer<TState> = () => ({}) as TState;
  private entityInterpolator: Interpolator = (e) => e;
  private analyticsExtractor: AnalyticsExtractor<TState> = () => ({});
  private hooks: KernelHooks<unknown> = {};
  private domainName = "custom";
  private description = "";

  /**
   * Set the domain name.
   */
  domain(name: string): this {
    this.domainName = name;
    return this;
  }

  /**
   * Set the kernel description.
   */
  describe(desc: string): this {
    this.description = desc;
    return this;
  }

  /**
   * Set the step executor function.
   */
  onStep(executor: StepExecutor<TState>): this {
    this.stepExecutor = executor;
    return this;
  }

  /**
   * Set the entity initializer.
   */
  initEntities(initializer: EntityInitializer): this {
    this.entityInit = initializer;
    return this;
  }

  /**
   * Set the state initializer.
   */
  initState(initializer: StateInitializer<TState>): this {
    this.stateInit = initializer;
    return this;
  }

  /**
   * Set the interpolator.
   */
  interpolate(interpolator: Interpolator): this {
    this.entityInterpolator = interpolator;
    return this;
  }

  /**
   * Set the analytics extractor.
   */
  analytics(extractor: AnalyticsExtractor<TState>): this {
    this.analyticsExtractor = extractor;
    return this;
  }

  /**
   * Add lifecycle hooks.
   */
  withHooks(hooks: KernelHooks<unknown>): this {
    this.hooks = { ...this.hooks, ...hooks };
    return this;
  }

  /**
   * Add before-init hook.
   */
  onBeforeInit(handler: KernelHooks<unknown>["onBeforeInit"]): this {
    this.hooks.onBeforeInit = handler;
    return this;
  }

  /**
   * Add after-init hook.
   */
  onAfterInit(handler: KernelHooks<unknown>["onAfterInit"]): this {
    this.hooks.onAfterInit = handler;
    return this;
  }

  /**
   * Add before-step hook.
   */
  onBeforeStep(handler: KernelHooks<unknown>["onBeforeStep"]): this {
    this.hooks.onBeforeStep = handler;
    return this;
  }

  /**
   * Add after-step hook.
   */
  onAfterStep(handler: KernelHooks<unknown>["onAfterStep"]): this {
    this.hooks.onAfterStep = handler;
    return this;
  }

  /**
   * Add reset hook.
   */
  onReset(handler: KernelHooks<unknown>["onReset"]): this {
    this.hooks.onReset = handler;
    return this;
  }

  /**
   * Add error hook.
   */
  onError(handler: KernelHooks<unknown>["onError"]): this {
    this.hooks.onError = handler;
    return this;
  }

  /**
   * Build the kernel factory.
   */
  build(): () => SimKernelService {
    return () =>
      new BuiltKernel<TState>(
        this.domainName as SimulationDomain,
        this.stepExecutor,
        this.entityInit,
        this.stateInit,
        this.entityInterpolator,
        this.analyticsExtractor,
        this.hooks,
      );
  }

  /**
   * Build and create an instance.
   */
  create(): SimKernelService {
    return this.build()();
  }

  /**
   * Get kernel metadata.
   */
  getMetadata(): { domain: string; description: string } {
    return {
      domain: this.domainName,
      description: this.description,
    };
  }
}

/**
 * Create a new kernel builder.
 */
export function createKernel<
  TState = Record<string, unknown>,
>(): KernelBuilder<TState> {
  return new KernelBuilder<TState>();
}
