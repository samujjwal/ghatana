/**
 * Base Kernel - Abstract base class for custom simulation kernels.
 *
 * @doc.type class
 * @doc.purpose Provides foundation for implementing custom domain kernels
 * @doc.layer product
 * @doc.pattern Template
 */

import type {
  SimulationManifest,
  SimKernelService,
  SimEntityBase,
  SimKeyframe,
  SimulationDomain,
  SimulationRunRequest,
  SimulationRunResult,
} from "@ghatana/tutorputor-contracts/v1/simulation";

/**
 * Kernel state snapshot.
 */
export interface KernelState<TEntity extends SimEntityBase = SimEntityBase> {
  /** Current simulation step */
  currentStep: number;
  /** Total steps */
  totalSteps: number;
  /** Current entities state */
  entities: TEntity[];
  /** Custom state data */
  customState: Record<string, unknown>;
  /** Whether simulation is complete */
  isComplete: boolean;
}

/**
 * Kernel analytics data.
 */
export interface KernelAnalytics {
  /** Total steps executed */
  stepsExecuted: number;
  /** Execution time per step (ms) */
  stepTimings: number[];
  /** Peak memory usage (bytes) */
  peakMemory?: number;
  /** Custom metrics */
  customMetrics: Record<string, number>;
}

/**
 * Kernel lifecycle hooks.
 */
export interface KernelHooks<TConfig = unknown> {
  /** Called before initialization */
  onBeforeInit?: (manifest: SimulationManifest, config?: TConfig) => void;
  /** Called after initialization */
  onAfterInit?: () => void;
  /** Called before each step */
  onBeforeStep?: (stepIndex: number) => void;
  /** Called after each step */
  onAfterStep?: (stepIndex: number) => void;
  /** Called on reset */
  onReset?: () => void;
  /** Called on error */
  onError?: (error: Error) => void;
}

/**
 * Abstract base class for simulation kernels.
 */
export abstract class BaseKernel<
  TEntity extends SimEntityBase = SimEntityBase,
  TConfig = unknown,
> implements SimKernelService {
  /** The simulation manifest */
  protected manifest: SimulationManifest | null = null;

  /** Domain of this kernel */
  public readonly domain: SimulationDomain;

  /** Domain configuration */
  protected config: TConfig | undefined;

  /** Current kernel state */
  protected state: KernelState<TEntity>;

  /** Analytics data */
  protected analytics: KernelAnalytics;

  /** Lifecycle hooks */
  protected hooks: KernelHooks<TConfig> = {};

  /** Whether kernel is initialized */
  protected isInitialized = false;

  constructor(
    domain: SimulationDomain,
    config?: TConfig,
    hooks?: KernelHooks<TConfig>,
  ) {
    this.domain = domain;
    this.config = config;
    this.hooks = hooks ?? {};
    this.state = this.createInitialState();
    this.analytics = this.createInitialAnalytics();
  }

  /**Serialize current state.
   */
  serialize(): string {
    return JSON.stringify(this.state);
  }

  /**
   * Deserialize state.
   */
  deserialize(state: string): void {
    try {
      this.state = JSON.parse(state);
    } catch (error) {
      throw new Error(`Failed to deserialize state: ${error}`);
    }
  }

  /**
   * Check if kernel can execute manifest.
   */
  canExecute(manifest: SimulationManifest): boolean {
    return manifest.domain === this.domain;
  }

  /**
   * Check kernel health.
   */
  async checkHealth(): Promise<boolean> {
    return true;
  }

  /**
   * Run simulation.
   */
  async run(request: SimulationRunRequest): Promise<SimulationRunResult> {
    // Initialize if needed
    if (
      request.manifest &&
      (!this.manifest || this.manifest.id !== request.manifest.id)
    ) {
      this.initialize(request.manifest);
    }

    if (!this.manifest) {
      throw new Error("Kernel not initialized");
    }

    const startTime = Date.now();
    const keyframes: SimKeyframe[] = [];
    const startStep = request.startStep ?? 0;
    const endStep = request.endStep ?? this.manifest.steps.length;

    // Fast forward to startStep
    while (this.state.currentStep < startStep && !this.state.isComplete) {
      this.step();
    }

    // Run to endStep
    while (!this.state.isComplete && this.state.currentStep < endStep) {
      this.step();
      // Capture keyframe (simple implementation)
      // In a real implementation, we would snapshot entities and annotations
    }

    return {
      simulationId: this.manifest.id,
      keyframes,
      totalSteps: this.state.currentStep,
      executionTimeMs: Date.now() - startTime,
      warnings: [],
      errors: [],
    };
  }

  /**
   *
   * Create initial state.
   */
  protected createInitialState(): KernelState<TEntity> {
    return {
      currentStep: 0,
      totalSteps: 0,
      entities: [],
      customState: {},
      isComplete: false,
    };
  }

  /**
   * Create initial analytics.
   */
  protected createInitialAnalytics(): KernelAnalytics {
    return {
      stepsExecuted: 0,
      stepTimings: [],
      customMetrics: {},
    };
  }

  /**
   * Initialize the kernel with a manifest.
   */
  initialize(manifest: SimulationManifest): void {
    try {
      this.hooks.onBeforeInit?.(manifest, this.config);

      this.manifest = manifest;
      this.state.totalSteps = manifest.steps.length;
      this.state.entities = this.initializeEntities(
        manifest.initialEntities as TEntity[],
      );
      this.state.customState = this.initializeCustomState(manifest);
      this.isInitialized = true;

      this.hooks.onAfterInit?.();
    } catch (error) {
      this.hooks.onError?.(error as Error);
      throw error;
    }
  }

  /**
   * Initialize entities. Override for custom entity setup.
   */
  protected initializeEntities(entities: TEntity[]): TEntity[] {
    return entities.map((e) => ({ ...e }));
  }

  /**
   * Initialize custom state. Override for domain-specific state.
   */
  protected initializeCustomState(
    _manifest: SimulationManifest,
  ): Record<string, unknown> {
    return {};
  }

  /**
   * Execute one simulation step.
   */
  step(): void {
    if (!this.isInitialized || !this.manifest) {
      throw new Error("Kernel not initialized");
    }

    if (this.state.isComplete) {
      return;
    }

    const startTime = performance.now();

    try {
      this.hooks.onBeforeStep?.(this.state.currentStep);

      const stepDef = this.manifest.steps[this.state.currentStep];
      if (stepDef) {
        this.executeStep(this.state.currentStep, stepDef);
      }

      this.state.currentStep++;
      this.state.isComplete = this.state.currentStep >= this.state.totalSteps;

      this.analytics.stepsExecuted++;
      this.analytics.stepTimings.push(performance.now() - startTime);

      this.hooks.onAfterStep?.(this.state.currentStep - 1);
    } catch (error) {
      this.hooks.onError?.(error as Error);
      throw error;
    }
  }

  /**
   * Execute a specific step. Must be implemented by subclasses.
   */
  protected abstract executeStep(
    stepIndex: number,
    stepDef: SimulationManifest["steps"][number],
  ): void;

  /**
   * Interpolate state between steps.
   */
  interpolate(t: number): Partial<SimKeyframe> {
    if (!this.isInitialized) {
      return { entities: [] };
    }

    return {
      entities: this.interpolateEntities(t),
      timestamp: this.calculateTimestamp(t),
      stepIndex: this.state.currentStep,
    };
  }

  /**
   * Interpolate entities. Override for custom interpolation.
   */
  protected interpolateEntities(t: number): SimEntityBase[] {
    // Default: no interpolation, just return current state
    return this.state.entities.map((e) => ({ ...e }));
  }

  /**
   * Get step duration. Can be overridden for custom duration calculation.
   */
  protected getStepDuration(step: SimulationManifest["steps"][number]): number {
    // Default: 1 second per action, minimum 1 second
    return Math.max(1000, (step?.actions?.length ?? 1) * 1000);
  }

  /**
   * Calculate timestamp for interpolation.
   */
  protected calculateTimestamp(t: number): number {
    if (!this.manifest) return 0;

    let time = 0;
    for (let i = 0; i < this.state.currentStep; i++) {
      const step = this.manifest.steps[i];
      if (step) {
        time += this.getStepDuration(step);
      }
    }
    const currentStep = this.manifest.steps[this.state.currentStep];
    if (currentStep) {
      const currentStepDuration = this.getStepDuration(currentStep);
      return time + currentStepDuration * t;
    }
    return time;
  }

  /**
   * Reset the kernel to initial state.
   */
  reset(): void {
    this.hooks.onReset?.();

    if (this.manifest) {
      this.state = this.createInitialState();
      this.state.totalSteps = this.manifest.steps.length;
      this.state.entities = this.initializeEntities(
        this.manifest.initialEntities as TEntity[],
      );
      this.state.customState = this.initializeCustomState(this.manifest);
    } else {
      this.state = this.createInitialState();
    }

    this.analytics = this.createInitialAnalytics();
  }

  /**
   * Get kernel analytics.
   */
  getAnalytics(): Record<string, unknown> {
    const avgStepTime =
      this.analytics.stepTimings.length > 0
        ? this.analytics.stepTimings.reduce((a, b) => a + b, 0) /
          this.analytics.stepTimings.length
        : 0;

    return {
      stepsExecuted: this.analytics.stepsExecuted,
      avgStepTimeMs: avgStepTime,
      totalSteps: this.state.totalSteps,
      currentStep: this.state.currentStep,
      isComplete: this.state.isComplete,
      ...this.analytics.customMetrics,
      ...this.getCustomAnalytics(),
    };
  }

  /**
   * Get custom analytics. Override for domain-specific metrics.
   */
  protected getCustomAnalytics(): Record<string, unknown> {
    return {};
  }

  /**
   * Get current state.
   */
  getState(): KernelState<TEntity> {
    return { ...this.state };
  }

  /**
   * Set lifecycle hooks.
   */
  setHooks(hooks: KernelHooks<TConfig>): void {
    this.hooks = { ...this.hooks, ...hooks };
  }

  /**
   * Update custom metric.
   */
  protected updateMetric(name: string, value: number): void {
    this.analytics.customMetrics[name] = value;
  }

  /**
   * Increment custom metric.
   */
  protected incrementMetric(name: string, amount = 1): void {
    this.analytics.customMetrics[name] =
      (this.analytics.customMetrics[name] ?? 0) + amount;
  }
}
