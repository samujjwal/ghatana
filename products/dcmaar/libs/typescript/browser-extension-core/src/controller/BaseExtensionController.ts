/**
 * @fileoverview Base Extension Controller
 *
 * Abstract base class providing common lifecycle management patterns
 * for browser extension controllers. Implements standard initialization,
 * configuration, state management, and shutdown sequences.
 *
 * @module controller/BaseExtensionController
 */

/**
 * Base controller state interface
 *
 * Tracks initialization status and lifecycle state.
 */
export interface ControllerState {
  /** Whether controller has been initialized */
  initialized: boolean;
  /** Any additional state properties */
  [key: string]: unknown;
}

/**
 * Base configuration interface
 *
 * Minimal configuration structure that all controllers should support.
 */
export interface ControllerConfig {
  /** Configuration version for migration support */
  version?: string;
  /** Any additional configuration properties */
  [key: string]: unknown;
}

/**
 * Controller lifecycle hooks interface
 *
 * Optional hooks that implementations can override for lifecycle events.
 */
export interface ControllerLifecycleHooks<
  TConfig extends ControllerConfig = ControllerConfig,
  TState extends ControllerState = ControllerState
> {
  /** Called before initialization starts */
  onBeforeInit?: () => Promise<void> | void;
  /** Called after successful initialization */
  onAfterInit?: () => Promise<void> | void;
  /** Called before shutdown starts */
  onBeforeShutdown?: () => Promise<void> | void;
  /** Called after shutdown completes */
  onAfterShutdown?: () => Promise<void> | void;
  /** Called when configuration is updated */
  onConfigUpdate?: (
    config: TConfig,
    oldConfig: TConfig
  ) => Promise<void> | void;
  /** Called when state changes */
  onStateChange?: (state: TState, oldState: TState) => void;
}

/**
 * Abstract Base Extension Controller
 *
 * Provides standardized lifecycle management for browser extension controllers.
 * Implements common patterns for initialization, configuration management,
 * state tracking, and resource cleanup.
 *
 * @example
 * ```typescript
 * interface MyConfig extends ControllerConfig {
 *   apiKey: string;
 *   enabled: boolean;
 * }
 *
 * interface MyState extends ControllerState {
 *   connected: boolean;
 *   dataCollecting: boolean;
 * }
 *
 * class MyController extends BaseExtensionController<MyConfig, MyState> {
 *   constructor() {
 *     super({
 *       initialized: false,
 *       connected: false,
 *       dataCollecting: false,
 *     });
 *   }
 *
 *   protected async doInitialize(): Promise<void> {
 *     // Custom initialization logic
 *     await this.connectToService();
 *     await this.startDataCollection();
 *   }
 *
 *   protected async doShutdown(): Promise<void> {
 *     // Custom cleanup logic
 *     await this.stopDataCollection();
 *     await this.disconnectFromService();
 *   }
 * }
 * ```
 *
 * @typeParam TConfig - Controller configuration type extending ControllerConfig
 * @typeParam TState - Controller state type extending ControllerState
 */
export abstract class BaseExtensionController<
  TConfig extends ControllerConfig = ControllerConfig,
  TState extends ControllerState = ControllerState
> {
  /** Current controller configuration */
  protected config: TConfig;

  /** Current controller state */
  protected state: TState;

  /** Lifecycle hooks */
  protected hooks: ControllerLifecycleHooks<TConfig, TState>;

  /** Initialization promise for preventing concurrent initialization */
  private initializePromise?: Promise<void>;

  /** Shutdown promise for preventing concurrent shutdown */
  private shutdownPromise?: Promise<void>;

  /**
   * Create a new base controller
   *
   * @param initialState - Initial state object
   * @param initialConfig - Initial configuration object
   * @param hooks - Optional lifecycle hooks
   */
  constructor(
    initialState: TState,
    initialConfig?: TConfig,
    hooks?: ControllerLifecycleHooks<TConfig, TState>
  ) {
    this.state = { ...initialState };
    this.config = initialConfig || ({} as TConfig);
    this.hooks = hooks || {};
  }

  /**
   * Initialize the controller
   *
   * Orchestrates initialization sequence with lifecycle hooks.
   * Safe to call multiple times - subsequent calls return same promise.
   *
   * @returns Promise that resolves when initialization completes
   * @throws Error if initialization fails
   */
  async initialize(): Promise<void> {
    // Return existing initialization promise if in progress
    if (this.initializePromise) {
      return this.initializePromise;
    }

    // Already initialized
    if (this.state.initialized) {
      return Promise.resolve();
    }

    // Create and store initialization promise
    this.initializePromise = this.executeInitialization();

    try {
      await this.initializePromise;
    } finally {
      this.initializePromise = undefined;
    }
  }

  /**
   * Execute initialization sequence
   *
   * @private
   */
  private async executeInitialization(): Promise<void> {
    try {
      // Pre-initialization hook
      await this.hooks.onBeforeInit?.();

      // Load configuration if needed
      const loadedConfig = await this.loadConfiguration();
      if (loadedConfig) {
        this.config = loadedConfig;
      }

      // Perform implementation-specific initialization
      await this.doInitialize();

      // Update state
      const oldState = { ...this.state };
      this.state.initialized = true;
      this.hooks.onStateChange?.(this.state, oldState);

      // Post-initialization hook
      await this.hooks.onAfterInit?.();

      this.log("Initialized successfully");
    } catch (error) {
      this.logError("Initialization failed", error);
      throw error;
    }
  }

  /**
   * Shutdown the controller and cleanup resources
   *
   * Orchestrates shutdown sequence with lifecycle hooks.
   * Safe to call multiple times - subsequent calls return same promise.
   *
   * @returns Promise that resolves when shutdown completes
   */
  async shutdown(): Promise<void> {
    // Return existing shutdown promise if in progress
    if (this.shutdownPromise) {
      return this.shutdownPromise;
    }

    // Already shutdown
    if (!this.state.initialized) {
      return Promise.resolve();
    }

    // Create and store shutdown promise
    this.shutdownPromise = this.executeShutdown();

    try {
      await this.shutdownPromise;
    } finally {
      this.shutdownPromise = undefined;
    }
  }

  /**
   * Execute shutdown sequence
   *
   * @private
   */
  private async executeShutdown(): Promise<void> {
    try {
      // Pre-shutdown hook
      await this.hooks.onBeforeShutdown?.();

      // Perform implementation-specific shutdown
      await this.doShutdown();

      // Update state
      const oldState = { ...this.state };
      this.state.initialized = false;
      this.hooks.onStateChange?.(this.state, oldState);

      // Post-shutdown hook
      await this.hooks.onAfterShutdown?.();

      this.log("Shutdown complete");
    } catch (error) {
      this.logError("Shutdown failed", error);
      throw error;
    }
  }

  /**
   * Update configuration
   *
   * Validates and applies new configuration, optionally persisting it.
   *
   * @param newConfig - Partial or full configuration update
   * @param persist - Whether to persist configuration to storage (default: true)
   * @returns Promise that resolves when configuration is updated
   */
  async updateConfig(
    newConfig: Partial<TConfig>,
    persist = true
  ): Promise<void> {
    const oldConfig = { ...this.config };
    this.config = { ...this.config, ...newConfig };

    // Validate configuration
    await this.validateConfig(this.config);

    // Persist if requested
    if (persist) {
      await this.saveConfiguration(this.config);
    }

    // Configuration update hook
    await this.hooks.onConfigUpdate?.(this.config, oldConfig);

    // Apply configuration changes
    await this.applyConfigChanges(this.config, oldConfig);

    this.log("Configuration updated");
  }

  /**
   * Get current controller state
   *
   * @returns Readonly copy of current state
   */
  getState(): Readonly<TState> {
    return { ...this.state };
  }

  /**
   * Get current controller configuration
   *
   * @returns Readonly copy of current configuration
   */
  getConfig(): Readonly<TConfig> {
    return { ...this.config };
  }

  /**
   * Update internal state
   *
   * @param updates - Partial state updates
   * @protected
   */
  protected updateState(updates: Partial<TState>): void {
    const oldState = { ...this.state };
    this.state = { ...this.state, ...updates };
    this.hooks.onStateChange?.(this.state, oldState);
  }

  /**
   * Check if controller is initialized
   *
   * @returns True if controller is initialized
   */
  isInitialized(): boolean {
    return this.state.initialized;
  }

  /**
   * Logging helper - override for custom logging
   *
   * @param message - Log message
   * @param data - Optional data to log
   * @protected
   */
  protected log(message: string, data?: unknown): void {
    const prefix = `[${this.constructor.name}]`;
    if (data !== undefined) {
      console.log(prefix, message, data);
    } else {
      console.log(prefix, message);
    }
  }

  /**
   * Error logging helper - override for custom error logging
   *
   * @param message - Error message
   * @param error - Error object or data
   * @protected
   */
  protected logError(message: string, error?: unknown): void {
    const prefix = `[${this.constructor.name}]`;
    console.error(prefix, message, error);
  }

  // Abstract methods that implementations must provide

  /**
   * Perform implementation-specific initialization
   *
   * Called during initialize() after configuration is loaded.
   *
   * @protected
   * @abstract
   */
  protected abstract doInitialize(): Promise<void>;

  /**
   * Perform implementation-specific shutdown and cleanup
   *
   * Called during shutdown() before state is reset.
   *
   * @protected
   * @abstract
   */
  protected abstract doShutdown(): Promise<void>;

  // Optional methods that implementations can override

  /**
   * Load configuration from storage
   *
   * Override to implement custom configuration loading logic.
   * Return null to use initial configuration.
   *
   * @returns Promise resolving to loaded configuration or null
   * @protected
   */
  protected async loadConfiguration(): Promise<TConfig | null> {
    return null;
  }

  /**
   * Save configuration to storage
   *
   * Override to implement custom configuration persistence logic.
   *
   * @param _config - Configuration to save
   * @protected
   */
  protected async saveConfiguration(_config: TConfig): Promise<void> {
    // Default: no-op
    // Implementations can override to persist configuration
    return Promise.resolve();
  }

  /**
   * Validate configuration
   *
   * Override to implement custom configuration validation logic.
   * Throw error if configuration is invalid.
   *
   * @param _config - Configuration to validate
   * @throws Error if configuration is invalid
   * @protected
   */
  protected async validateConfig(_config: TConfig): Promise<void> {
    // Default: no validation
    // Implementations can override to add validation
    return Promise.resolve();
  }

  /**
   * Apply configuration changes
   *
   * Override to implement custom logic when configuration changes.
   * Called after new configuration is validated and saved.
   *
   * @param _newConfig - New configuration
   * @param _oldConfig - Previous configuration
   * @protected
   */
  protected async applyConfigChanges(
    _newConfig: TConfig,
    _oldConfig: TConfig
  ): Promise<void> {
    // Default: no-op
    // Implementations can override to react to config changes
    return Promise.resolve();
  }
}
