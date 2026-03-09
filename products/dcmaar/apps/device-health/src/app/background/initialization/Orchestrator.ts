/**
 * @fileoverview Extension Initialization Orchestrator
 *
 * Orchestrates the complete extension startup sequence:
 * 1. Load and validate configuration
 * 2. Register message handlers
 * 3. Connect to sources
 * 4. Start services (ingest, receive, etc.)
 *
 * Ensures that all dependencies are initialized in the correct order
 * and that the extension only starts after configuration is ready.
 */

import { EventEmitter } from 'eventemitter3';
import { devLog } from '@shared/utils/dev-logger';

import { ConfigLoader } from './ConfigLoader';
import { MessageRouter } from '../contracts/handlers';

import type { ISinkConfigHandler, ICommandHandler, IProcessHandler } from '../contracts/handlers';
import type { ExtensionConfig } from '../contracts/config';

/**
 * Orchestrator events
 */
type OrchestratorEvent =
  | 'config-loading'
  | 'config-loaded'
  | 'handlers-registering'
  | 'handlers-registered'
  | 'services-starting'
  | 'services-started'
  | 'ready'
  | 'error';

/**
 * Initialization result
 */
export interface InitializationResult {
  success: boolean;
  error?: string;
  config?: ExtensionConfig;
}

/**
 * Extension Initialization Orchestrator
 *
 * Manages the complete startup sequence with proper error handling
 * and event emission for monitoring.
 *
 * @example
 * ```typescript
 * const orchestrator = new Orchestrator();
 *
 * orchestrator.on('ready', (config) => {
 *   console.log('Extension ready with config:', config);
 * });
 *
 * orchestrator.on('error', (error) => {
 *   console.error('Initialization failed:', error);
 * });
 *
 * const result = await orchestrator.initialize({
 *   sinkConfigHandler,
 *   commandHandler,
 *   processHandler,
 * });
 * ```
 */
export class Orchestrator extends EventEmitter<OrchestratorEvent> {
  private readonly contextName = 'Orchestrator';
  private configLoader: ConfigLoader;
  private messageRouter: MessageRouter;
  private initialized = false;

  constructor() {
    super();
    this.configLoader = new ConfigLoader();
    this.messageRouter = new MessageRouter(this.contextName);
  }

  /**
   * Initialize the extension
   *
   * Performs the complete initialization sequence:
   * 1. Load configuration
   * 2. Register handlers
   * 3. Start services
   *
   * @param handlers - Message handlers for different message types
   * @returns Initialization result
   */
  async initialize(handlers: {
    sinkConfigHandler: ISinkConfigHandler;
    commandHandler: ICommandHandler;
    processHandler: IProcessHandler;
  }): Promise<InitializationResult> {
    if (this.initialized) {
      devLog.warn(`[${this.contextName}] Already initialized`);
      return { success: true, config: this.configLoader.getCurrentConfig() };
    }

    try {
      // Step 1: Load configuration
      devLog.info(`[${this.contextName}] Starting initialization sequence`);
      this.emit('config-loading');

      const configResult = await this.configLoader.load();
      if (!configResult.success) {
        const error = `Configuration loading failed: ${configResult.error}`;
        this.emit('error', { error });
        // Expected on first run - log as info instead of error
        devLog.info(`[${this.contextName}] ${error} (expected on first run)`);
        return { success: false, error };
      }

      const config = configResult.config!;
      this.emit('config-loaded', { config });
      devLog.info(`[${this.contextName}] Configuration loaded successfully`);

      // Step 2: Register message handlers
      devLog.info(`[${this.contextName}] Registering message handlers`);
      this.emit('handlers-registering');

      this.messageRouter.registerSinkConfigHandler(handlers.sinkConfigHandler);
      this.messageRouter.registerCommandHandler(handlers.commandHandler);
      this.messageRouter.registerProcessHandler(handlers.processHandler);

      if (!this.messageRouter.isReady()) {
        const error = 'Not all message handlers registered';
        this.emit('error', { error });
        devLog.error(`[${this.contextName}] ${error}`);
        return { success: false, error };
      }

      this.emit('handlers-registered');
      devLog.info(`[${this.contextName}] Message handlers registered successfully`);

      // Step 3: Services start (caller responsibility)
      devLog.info(`[${this.contextName}] Ready to start services`);
      this.emit('services-starting');

      // Mark as initialized
      this.initialized = true;
      this.emit('ready', { config });
      devLog.info(`[${this.contextName}] Extension initialization complete`);

      return { success: true, config };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      this.emit('error', { error: errorMessage });
      devLog.error(`[${this.contextName}] Initialization failed`, { error: errorMessage });
      return { success: false, error: errorMessage };
    }
  }

  /**
   * Get the message router for routing incoming messages
   *
   * @returns Message router instance
   */
  getMessageRouter(): MessageRouter {
    return this.messageRouter;
  }

  /**
   * Get the current configuration
   *
   * @returns Current configuration or undefined if not loaded
   */
  getConfig(): ExtensionConfig | undefined {
    return this.configLoader.getCurrentConfig();
  }

  /**
   * Check if initialization is complete
   *
   * @returns True if initialized
   */
  isInitialized(): boolean {
    return this.initialized;
  }

  /**
   * Get initialization status
   *
   * @returns Status object
   */
  getStatus(): {
    initialized: boolean;
    configLoaded: boolean;
    handlersRegistered: boolean;
    routerReady: boolean;
  } {
    return {
      initialized: this.initialized,
      configLoaded: this.configLoader.getCurrentConfig() !== undefined,
      handlersRegistered: this.messageRouter.isReady(),
      routerReady: this.messageRouter.isReady(),
    };
  }
}
