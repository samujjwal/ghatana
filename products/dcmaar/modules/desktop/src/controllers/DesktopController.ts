/**
 * @fileoverview Desktop Controller
 *
 * Main application controller that orchestrates:
 * - Connector management (sources and sinks)
 * - Application lifecycle
 * - State synchronization
 * - Configuration persistence
 *
 * Similar to ExtensionController but for desktop environment.
 */

import {
  DesktopConnectorManager,
  DesktopConnectorConfig,
  ConnectorState,
  TelemetrySnapshot,
} from '../libs/connectors';
import { createDesktopConnectorManager } from '../libs/connectors';

export interface DesktopControllerConfig {
  /** Initial connector configuration */
  connectorConfig?: DesktopConnectorConfig;
  /** Workspace ID */
  workspaceId: string;
  /** Load config from storage */
  loadFromStorage?: boolean;
  /** Storage key for persisting config */
  storageKey?: string;
}

export interface DesktopControllerState {
  /** Controller initialization status */
  initialized: boolean;
  /** Connector manager state */
  connectorState?: ConnectorState;
  /** Latest telemetry snapshot */
  latestTelemetry?: TelemetrySnapshot;
  /** Configuration loaded/active */
  configLoaded: boolean;
  /** Any errors */
  errors: string[];
}

/**
 * Desktop Controller
 *
 * Main entry point for desktop application logic.
 * Manages connector lifecycle and coordinates with UI.
 */
export class DesktopController {
  private config: DesktopControllerConfig;
  private connectorManager?: DesktopConnectorManager;
  private state: DesktopControllerState;
  private stateListeners = new Set<(state: DesktopControllerState) => void>();
  private telemetryListeners = new Set<(snapshot: TelemetrySnapshot) => void>();

  constructor(config: DesktopControllerConfig) {
    this.config = config;
    this.state = {
      initialized: false,
      configLoaded: false,
      errors: [],
    };
  }

  /**
   * Initialize the desktop controller
   */
  async initialize(): Promise<void> {
    if (this.state.initialized) {
      console.warn('[DesktopController] Already initialized');
      return;
    }

    console.log('[DesktopController] Initializing...', {
      workspaceId: this.config.workspaceId,
    });

    try {
      // Load configuration
      const connectorConfig = await this.loadConfiguration();

      if (!connectorConfig) {
        this.state.errors.push('No connector configuration available');
        this.updateState();
        return;
      }

      // Initialize connector manager
      this.connectorManager = await createDesktopConnectorManager(connectorConfig);

      // Subscribe to connector events
      this.setupConnectorListeners();

      // Update state
      this.state.initialized = true;
      this.state.configLoaded = true;
      this.state.connectorState = this.connectorManager.getState();
      this.updateState();

      console.log('[DesktopController] Initialized successfully');
    } catch (error) {
      const message = (error as Error).message;
      console.error('[DesktopController] Initialization failed:', error);
      this.state.errors.push(message);
      this.updateState();
      throw error;
    }
  }

  /**
   * Apply new connector configuration
   */
  async applyConnectorConfig(config: DesktopConnectorConfig): Promise<void> {
    console.log('[DesktopController] Applying new connector configuration');

    // Shutdown existing manager
    if (this.connectorManager) {
      await this.connectorManager.shutdown();
    }

    // Create new manager with new config
    this.connectorManager = await createDesktopConnectorManager(config);
    this.setupConnectorListeners();

    // Save to storage
    await this.saveConfiguration(config);

    // Update state
    this.state.connectorState = this.connectorManager.getState();
    this.updateState();

    console.log('[DesktopController] Configuration applied');
  }

  /**
   * Get current telemetry snapshot
   */
  async getSnapshot(sourceId?: string): Promise<TelemetrySnapshot> {
    if (!this.connectorManager) {
      throw new Error('Connector manager not initialized');
    }

    return await this.connectorManager.getSnapshot(sourceId);
  }

  /**
   * Send command to sink(s)
   */
  async sendCommand(
    command: any,
    sinkId?: string
  ): Promise<Array<{ ok: boolean; commandId: string; error?: string }>> {
    if (!this.connectorManager) {
      throw new Error('Connector manager not initialized');
    }

    return await this.connectorManager.sendCommand(command as any, sinkId);
  }

  /**
   * Get current state
   */
  getState(): Readonly<DesktopControllerState> {
    return { ...this.state };
  }

  /**
   * Get connector manager instance
   */
  getConnectorManager(): DesktopConnectorManager | undefined {
    return this.connectorManager;
  }

  /**
   * Subscribe to state changes
   */
  onStateChange(listener: (state: DesktopControllerState) => void): () => void {
    this.stateListeners.add(listener);
    return () => this.stateListeners.delete(listener);
  }

  /**
   * Subscribe to telemetry updates
   */
  onTelemetryUpdate(listener: (snapshot: TelemetrySnapshot) => void): () => void {
    this.telemetryListeners.add(listener);
    return () => this.telemetryListeners.delete(listener);
  }

  /**
   * Shutdown the controller
   */
  async shutdown(): Promise<void> {
    console.log('[DesktopController] Shutting down...');

    if (this.connectorManager) {
      await this.connectorManager.shutdown();
    }

    this.state.initialized = false;
    this.state.connectorState = undefined;
    this.updateState();

    console.log('[DesktopController] Shutdown complete');
  }

  // ===== Private Methods =====

  private async loadConfiguration(): Promise<DesktopConnectorConfig | null> {
    // If provided in constructor, use it
    if (this.config.connectorConfig) {
      return this.config.connectorConfig;
    }

    // Otherwise, load from storage
    if (this.config.loadFromStorage) {
      const key = this.config.storageKey || 'desktop-connector-config';
      try {
        const storage = (globalThis as any).localStorage;
        if (storage && typeof storage.getItem === 'function') {
          const stored = storage.getItem(key);

          if (stored) {
            try {
              return JSON.parse(stored) as DesktopConnectorConfig;
            } catch (error) {
              console.error('[DesktopController] Failed to parse stored config:', error);
            }
          }
        }
      } catch (error) {
        // Environment may not support storage (e.g., Node). Fail gracefully.
        console.debug('[DesktopController] Storage not available:', error);
      }
    }

    return null;
  }

  private async saveConfiguration(config: DesktopConnectorConfig): Promise<void> {
    const key = this.config.storageKey || 'desktop-connector-config';

    try {
      const storage = (globalThis as any).localStorage;
      if (storage && typeof storage.setItem === 'function') {
        storage.setItem(key, JSON.stringify(config));
        console.log('[DesktopController] Configuration saved to storage');
      } else {
        console.debug('[DesktopController] No storage available to persist configuration');
      }
    } catch (error) {
      console.error('[DesktopController] Failed to save configuration:', error);
    }
  }

  private setupConnectorListeners(): void {
    if (!this.connectorManager) return;

    // Listen to telemetry updates
    this.connectorManager.onTelemetryUpdate(snapshot => {
      this.state.latestTelemetry = snapshot;
      this.updateState();

      // Notify telemetry listeners
      for (const listener of this.telemetryListeners) {
        try {
          listener(snapshot);
        } catch (error) {
          console.error('[DesktopController] Error in telemetry listener:', error);
        }
      }
    });

    // Listen to connector state changes
    this.connectorManager.onStateChange(connectorState => {
      this.state.connectorState = connectorState;
      this.updateState();
    });

    // Listen to errors
    this.connectorManager.onError((error, connectorId) => {
      const message = `${connectorId ? `[${connectorId}] ` : ''}${error.message}`;
      this.state.errors.push(message);
      this.updateState();
    });
  }

  private updateState(): void {
    // Notify state listeners
    for (const listener of this.stateListeners) {
      try {
        listener(this.getState());
      } catch (error) {
        console.error('[DesktopController] Error in state listener:', error);
      }
    }
  }
}

/**
 * Factory function to create and initialize the desktop controller
 */
export const createDesktopController = async (
  config: DesktopControllerConfig
): Promise<DesktopController> => {
  const controller = new DesktopController(config);
  await controller.initialize();
  return controller;
};
