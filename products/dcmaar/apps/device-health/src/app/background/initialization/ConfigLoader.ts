/**
 * @fileoverview Configuration Loader
 *
 * Responsible for loading and validating bootstrap configuration before
 * extension services are initialized. Implements the principle that the
 * extension should only start after configuration is loaded.
 *
 * Loading sequence:
 * 1. Load bootstrap config from storage
 * 2. Validate bootstrap config
 * 3. Connect to source (if required)
 * 4. Load runtime config from source
 * 5. Validate runtime config
 * 6. Emit ready event
 */

import browser from 'webextension-polyfill';
import { devLog } from '@shared/utils/dev-logger';

import type { BootstrapConfig, RuntimeConfig, ExtensionConfig } from '../contracts/config';
import { validateBootstrapConfig, validateRuntimeConfig } from '../contracts/config';

/**
 * Configuration loader events
 */
type ConfigLoaderEvent =
  | 'bootstrap-loaded'
  | 'runtime-loaded'
  | 'config-ready'
  | 'config-error'
  | 'source-connecting'
  | 'source-connected'
  | 'source-error';

/**
 * Configuration loading result
 */
export interface ConfigLoadingResult {
  success: boolean;
  error?: string;
  config?: ExtensionConfig;
}

/**
 * Configuration Loader
 *
 * Orchestrates the configuration loading process with proper error handling
 * and event emission for monitoring.
 *
 * @example
 * ```typescript
 * const loader = new ConfigLoader();
 * loader.on('config-ready', (config) => {
 *   console.log('Config loaded:', config);
 *   startServices(config);
 * });
 * loader.on('config-error', (error) => {
 *   console.error('Config loading failed:', error);
 * });
 *
 * const result = await loader.load();
 * ```
 */
export class ConfigLoader {
  private readonly contextName = 'ConfigLoader';
  private readonly storageKey = 'dcmaar_bootstrap_config';
  private currentConfig?: ExtensionConfig;

  /**
   * Load configuration
   *
   * Attempts to load bootstrap config from storage. If successful and
   * waitForSourceConnection is true, connects to the source and loads
   * runtime config.
   *
   * @returns Configuration loading result
   */
  async load(): Promise<ConfigLoadingResult> {
    try {
      devLog.info(`[${this.contextName}] Starting configuration load`);

      // Step 1: Load bootstrap config
      const bootstrapConfig = await this.loadBootstrapConfig();
      if (!bootstrapConfig) {
        const errorMsg = 'No bootstrap configuration found in storage';
        // Expected on first run - extension will use DEFAULT_EXTENSION_CONFIG from ExtensionController
        devLog.info(`[${this.contextName}] ${errorMsg} (expected on first run)`);
        return { success: false, error: errorMsg };
      }

      devLog.info(`[${this.contextName}] Bootstrap config loaded`, {
        sourceId: bootstrapConfig.source.sourceId,
      });

      // Step 2: Validate bootstrap config
      const bootstrapValidation = validateBootstrapConfig(bootstrapConfig);
      if (!bootstrapValidation.valid) {
        const error = `Bootstrap config validation failed: ${bootstrapValidation.error}`;
        devLog.error(`[${this.contextName}] ${error}`);
        return { success: false, error };
      }

      const validBootstrapConfig = bootstrapValidation.data!.bootstrap;

      // Step 3: If configured, connect to source and load runtime config
      let runtimeConfig: RuntimeConfig | undefined;

      if (validBootstrapConfig.waitForSourceConnection) {
        try {
          devLog.info(`[${this.contextName}] Connecting to source`, {
            sourceId: validBootstrapConfig.source.sourceId,
          });

          runtimeConfig = await this.loadRuntimeConfigFromSource(validBootstrapConfig);

          if (runtimeConfig) {
            devLog.info(`[${this.contextName}] Runtime config loaded from source`, {
              sinkCount: runtimeConfig.sinks.length,
            });
          }
        } catch (error) {
          const errorMessage = error instanceof Error ? error.message : String(error);
          devLog.warn(`[${this.contextName}] Failed to load runtime config from source`, {
            error: errorMessage,
          });
          // Continue with bootstrap config only
        }
      }

      // Step 4: Build final config
      this.currentConfig = {
        bootstrap: validBootstrapConfig,
        runtime: runtimeConfig,
      };

      devLog.info(`[${this.contextName}] Configuration loaded successfully`);
      return { success: true, config: this.currentConfig };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      devLog.error(`[${this.contextName}] Configuration loading failed`, {
        error: errorMessage,
      });
      return { success: false, error: errorMessage };
    }
  }

  /**
   * Load bootstrap configuration from storage
   *
   * @returns Bootstrap config or undefined if not found
   */
  private async loadBootstrapConfig(): Promise<BootstrapConfig | undefined> {
    try {
      const result = await browser.storage.local.get(this.storageKey);
      return result[this.storageKey] as BootstrapConfig | undefined;
    } catch (error) {
      devLog.warn(`[${this.contextName}] Failed to read bootstrap config from storage`, {
        error: error instanceof Error ? error.message : String(error),
      });
      return undefined;
    }
  }

  /**
   * Load runtime configuration from source
   *
   * Connects to the source (desktop/agent) and fetches runtime configuration.
   * Uses the SourceConnector which delegates to connectors library.
   *
   * @param bootstrapConfig - Bootstrap configuration
   * @returns Runtime config or undefined if not available
   */
  private async loadRuntimeConfigFromSource(
    bootstrapConfig: BootstrapConfig
  ): Promise<RuntimeConfig | undefined> {
    try {
      // TODO: SourceConnector implementation missing - temporarily disabled
      // This was importing from archive which no longer exists
      devLog.warn(
        `[${this.contextName}] SourceConnector not implemented - skipping runtime config fetch`,
        {
          sourceId: bootstrapConfig.source?.sourceId,
          sourceType: bootstrapConfig.source?.sourceType,
        }
      );
      return undefined;

      /* DISABLED - SourceConnector missing
      // Import SourceConnector dynamically to avoid circular dependencies
      const { SourceConnector } = await import('../connectors/SourceConnector');

      devLog.debug(`[${this.contextName}] Connecting to source for runtime config`, {
        sourceId: bootstrapConfig.source.sourceId,
        sourceType: bootstrapConfig.source.sourceType,
      });
      */

      /* DISABLED - All SourceConnector code commented out since implementation missing
      // Create source connector
      const sourceConnector = new SourceConnector(bootstrapConfig.source);

      // Set up error handling
      sourceConnector.on('error', (error) => {
        devLog.warn(`[${this.contextName}] Source connector error`, {
          error: error instanceof Error ? error.message : String(error),
        });
      });

      // Connect to source with timeout
      const connectTimeout = bootstrapConfig.sourceConnectionTimeoutMs || 30000;
      await Promise.race([
        sourceConnector.connect(),
        new Promise<void>((_, reject) =>
          setTimeout(() => reject(new Error('Source connection timeout')), connectTimeout)
        ),
      ]);

      devLog.info(`[${this.contextName}] Connected to source`, {
        sourceId: bootstrapConfig.source.sourceId,
      });

      // Fetch runtime configuration
      const runtimeConfig = await sourceConnector.getRuntimeConfig();

      // Disconnect from source
      await sourceConnector.disconnect();

      return runtimeConfig;
      */ // END DISABLED BLOCK - SourceConnector missing
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      devLog.warn(`[${this.contextName}] Failed to load runtime config from source`, {
        error: errorMessage,
        sourceId: bootstrapConfig.source.sourceId,
      });
      return undefined;
    }
  }

  /**
   * Save bootstrap configuration to storage
   *
   * @param config - Bootstrap configuration to save
   */
  async saveBootstrapConfig(config: BootstrapConfig): Promise<void> {
    try {
      await browser.storage.local.set({ [this.storageKey]: config });
      devLog.info(`[${this.contextName}] Bootstrap config saved to storage`);
    } catch (error) {
      devLog.error(`[${this.contextName}] Failed to save bootstrap config`, {
        error: error instanceof Error ? error.message : String(error),
      });
      throw error;
    }
  }

  /**
   * Get current configuration
   *
   * @returns Current configuration or undefined if not loaded
   */
  getCurrentConfig(): ExtensionConfig | undefined {
    return this.currentConfig;
  }

  /**
   * Clear stored configuration
   */
  async clearStoredConfig(): Promise<void> {
    try {
      await browser.storage.local.remove(this.storageKey);
      devLog.info(`[${this.contextName}] Stored config cleared`);
    } catch (error) {
      devLog.warn(`[${this.contextName}] Failed to clear stored config`, {
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }
}
