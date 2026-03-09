/**
 * @fileoverview Desktop Connector Manager
 *
 * Orchestrates telemetry sources and control sinks for the desktop application.
 * Provides unified interface for connecting to agent, extension, and third-party services.
 *
 * Similar pattern to ExtensionController but adapted for desktop environment:
 * - Manages source connectors (agent, extension, HTTP, gRPC, etc.)
 * - Manages sink connectors (agent, extension, file, bridge, etc.)
 * - Handles connector lifecycle (initialize, start, stop, shutdown)
 * - Coordinates data flow between sources and dashboard
 * - Emits events for state changes and data updates
 *
 * @module libs/connectors/DesktopConnectorManager
 */

import type {
  TelemetrySource,
  ControlSink,
  TelemetrySnapshot,
  ControlCommand,
  SinkAck,
  HealthStatus,
} from '../adapters/types';
import { adapterFactory } from '../adapters/adapterFactory';
import { createLogger } from '../adapters/logger';
import { createTracer } from '../adapters/tracer';
import { createQueue } from '../adapters/queue';
import { createKeyring } from '../adapters/keyring';
import type { Logger } from '../adapters/types';

/**
 * Connector configuration for a source or sink
 */
export interface ConnectorConfig {
  /** Unique identifier for this connector */
  id: string;
  /** Display name */
  name: string;
  /** Connector type (agent, extension, http, grpc, etc.) */
  type: 'agent' | 'extension' | 'http' | 'grpc' | 'file' | 'mock' | 'custom';
  /** Type-specific options */
  options: Record<string, unknown>;
  /** Whether this connector is enabled */
  enabled: boolean;
  /** Priority for multi-connector scenarios */
  priority?: number;
  /** Tags for filtering/grouping */
  tags?: string[];
}

/**
 * Desktop Connector Manager configuration
 */
export interface DesktopConnectorConfig {
  /** Workspace ID */
  workspaceId: string;
  /** Source connectors (data providers: agent, extension, etc.) */
  sources: ConnectorConfig[];
  /** Sink connectors (command recipients: agent, extension, etc.) */
  sinks: ConnectorConfig[];
  /** Enable auto-start of connectors */
  autoStart?: boolean;
  /** Logging configuration */
  logging?: {
    level?: 'debug' | 'info' | 'warn' | 'error';
    enabled?: boolean;
  };
  /** Health check interval in ms */
  healthCheckInterval?: number;
}

/**
 * Connector state
 */
export interface ConnectorState {
  /** Manager initialization status */
  initialized: boolean;
  /** Active source connectors count */
  activeSourcesCount: number;
  /** Active sink connectors count */
  activeSinksCount: number;
  /** Total sources configured */
  totalSourcesCount: number;
  /** Total sinks configured */
  totalSinksCount: number;
  /** Last health check timestamp */
  lastHealthCheck?: string;
  /** Overall health status */
  healthy: boolean;
  /** Current errors */
  errors: string[];
}

/**
 * Event listeners
 */
export type TelemetryUpdateListener = (snapshot: TelemetrySnapshot) => void;
export type StateChangeListener = (state: ConnectorState) => void;
export type ErrorListener = (error: Error, connectorId?: string) => void;

/**
 * Desktop Connector Manager
 *
 * Main orchestrator for all connector operations in the desktop app.
 * Manages lifecycle, health, and data flow for sources and sinks.
 */
export class DesktopConnectorManager {
  private config: DesktopConnectorConfig;
  private sources = new Map<string, TelemetrySource>();
  private sinks = new Map<string, ControlSink>();
  private sourceUnsubscribers = new Map<string, () => void>();
  private state: ConnectorState;
  private logger: Logger;
  private telemetryListeners = new Set<TelemetryUpdateListener>();
  private stateListeners = new Set<StateChangeListener>();
  private errorListeners = new Set<ErrorListener>();
  private healthCheckTimer?: ReturnType<typeof setInterval>;
  private tracer;
  private queue;
  private keyring;

  constructor(config: DesktopConnectorConfig) {
    this.config = config;
    this.state = {
      initialized: false,
      activeSourcesCount: 0,
      activeSinksCount: 0,
      totalSourcesCount: config.sources.length,
      totalSinksCount: config.sinks.length,
      healthy: false,
      errors: [],
    };

    // Initialize logger
    this.logger = createLogger({
      level: config.logging?.level ?? 'info',
      workspaceId: config.workspaceId,
    });

    this.tracer = createTracer({
      serviceName: 'desktop-connector-manager',
      enabled: true,
    });

    // Initialize queue and keyring (will be properly initialized in init())
    this.queue = null as any;
    this.keyring = null as any;
  }

  /**
   * Initialize the connector manager and all configured connectors
   */
  async initialize(): Promise<void> {
    if (this.state.initialized) {
      this.logger.warn('DesktopConnectorManager already initialized');
      return;
    }

    this.logger.info('Initializing DesktopConnectorManager', {
      workspaceId: this.config.workspaceId,
      sourcesCount: this.config.sources.length,
      sinksCount: this.config.sinks.length,
    });

    const span = this.tracer.startSpan('DesktopConnectorManager.initialize');

    try {
      // Initialize infrastructure
      this.queue = await createQueue({
        dbName: `workspace-${this.config.workspaceId}`,
        storeName: 'commands',
        maxSizeMB: 100,
      });

      this.keyring = createKeyring({ keys: [] });

      // Initialize sources
      await this.initializeSources();

      // Initialize sinks
      await this.initializeSinks();

      // Update state
      this.state.initialized = true;
      this.state.healthy = true;
      this.updateState();

      // Start health checks
      this.startHealthChecks();

      // Auto-start if enabled
      if (this.config.autoStart) {
        await this.startAll();
      }

      span.setStatus({ code: 'ok' });
      this.logger.info('DesktopConnectorManager initialized successfully');
    } catch (error) {
      span.setStatus({ code: 'error', message: (error as Error).message });
      this.logger.error('Failed to initialize DesktopConnectorManager', error as Error);
      this.state.errors.push((error as Error).message);
      this.updateState();
      throw error;
    } finally {
      span.end();
    }
  }

  /**
   * Start all enabled connectors
   */
  async startAll(): Promise<void> {
    this.logger.info('Starting all connectors');

    // Start sources
    for (const [id] of this.sources) {
      try {
        await this.startSource(id);
      } catch (error) {
        this.logger.error(`Failed to start source: ${id}`, error as Error);
        this.notifyError(error as Error, id);
      }
    }

    // Sinks are passive, no explicit start needed
    this.state.activeSinksCount = this.sinks.size;
    this.updateState();
  }

  /**
   * Start a specific source connector
   */
  async startSource(sourceId: string): Promise<void> {
    const source = this.sources.get(sourceId);
    if (!source) {
      throw new Error(`Source not found: ${sourceId}`);
    }

    if (source.subscribe) {
      // Subscribe to updates
      const unsubscribe = await source.subscribe(snapshot => {
        this.handleTelemetryUpdate(snapshot, sourceId);
      });
      this.sourceUnsubscribers.set(sourceId, unsubscribe);
    }

    this.state.activeSourcesCount++;
    this.logger.info(`Source started: ${sourceId}`, { kind: source.kind });
    this.updateState();
  }

  /**
   * Stop a specific source connector
   */
  async stopSource(sourceId: string): Promise<void> {
    const unsubscribe = this.sourceUnsubscribers.get(sourceId);
    if (unsubscribe) {
      unsubscribe();
      this.sourceUnsubscribers.delete(sourceId);
      this.state.activeSourcesCount = Math.max(0, this.state.activeSourcesCount - 1);
      this.logger.info(`Source stopped: ${sourceId}`);
      this.updateState();
    }
  }

  /**
   * Get current telemetry snapshot from a source
   */
  async getSnapshot(sourceId?: string): Promise<TelemetrySnapshot> {
    let source: TelemetrySource | undefined;

    if (sourceId) {
      source = this.sources.get(sourceId);
      if (!source) {
        throw new Error(`Source not found: ${sourceId}`);
      }
    } else {
      // Use first available source
      source = Array.from(this.sources.values())[0];
      if (!source) {
        throw new Error('No sources available');
      }
    }

    return await source.getInitialSnapshot();
  }

  /**
   * Send a command to sink(s)
   */
  async sendCommand(command: ControlCommand, sinkId?: string): Promise<SinkAck[]> {
    const targetSinks = sinkId
      ? ([this.sinks.get(sinkId)].filter(Boolean) as ControlSink[])
      : Array.from(this.sinks.values());

    if (targetSinks.length === 0) {
      throw new Error(sinkId ? `Sink not found: ${sinkId}` : 'No sinks available');
    }

    const acks: SinkAck[] = [];

    for (const sink of targetSinks) {
      try {
        await sink.enqueue(command);
        const sinkAcks = await sink.flush();
        acks.push(...sinkAcks);
      } catch (error) {
        this.logger.error(`Failed to send command to sink`, error as Error);
        acks.push({
          ok: false,
          commandId: command.id,
          error: (error as Error).message,
        });
      }
    }

    return acks;
  }

  /**
   * Add a connector at runtime
   */
  async addConnector(config: ConnectorConfig, type: 'source' | 'sink'): Promise<void> {
    if (!config.enabled) {
      this.logger.info(`Skipping disabled connector: ${config.id}`);
      return;
    }

    if (type === 'source') {
      await this.addSource(config);
    } else {
      await this.addSink(config);
    }

    this.updateState();
  }

  /**
   * Remove a connector at runtime
   */
  async removeConnector(connectorId: string, type: 'source' | 'sink'): Promise<void> {
    if (type === 'source') {
      await this.stopSource(connectorId);
      const source = this.sources.get(connectorId);
      if (source?.close) {
        await source.close();
      }
      this.sources.delete(connectorId);
      this.state.totalSourcesCount--;
    } else {
      const sink = this.sinks.get(connectorId);
      if (sink?.close) {
        await sink.close();
      }
      this.sinks.delete(connectorId);
      this.state.totalSinksCount--;
      this.state.activeSinksCount--;
    }

    this.logger.info(`Connector removed: ${connectorId}`);
    this.updateState();
  }

  /**
   * Get current state
   */
  getState(): Readonly<ConnectorState> {
    return { ...this.state };
  }

  /**
   * Get list of all connectors
   */
  getConnectors(): {
    sources: Array<{ id: string; kind: string; config: ConnectorConfig }>;
    sinks: Array<{ id: string; kind: string; config: ConnectorConfig }>;
  } {
    const sourceConfigs = this.config.sources;
    const sinkConfigs = this.config.sinks;

    return {
      sources: Array.from(this.sources.entries()).map(([id, source]) => ({
        id,
        kind: source.kind,
        config: sourceConfigs.find(c => c.id === id)!,
      })),
      sinks: Array.from(this.sinks.entries()).map(([id, sink]) => ({
        id,
        kind: sink.kind,
        config: sinkConfigs.find(c => c.id === id)!,
      })),
    };
  }

  /**
   * Perform health check on all connectors
   */
  async healthCheck(): Promise<Record<string, HealthStatus>> {
    const results: Record<string, HealthStatus> = {};

    // Check sources
    for (const [id, source] of this.sources) {
      if (source.healthCheck) {
        try {
          results[`source:${id}`] = await source.healthCheck();
        } catch (error) {
          results[`source:${id}`] = {
            healthy: false,
            lastCheck: new Date().toISOString(),
            error: (error as Error).message,
          };
        }
      }
    }

    // Check sinks
    for (const [id, sink] of this.sinks) {
      if (sink.healthCheck) {
        try {
          results[`sink:${id}`] = await sink.healthCheck();
        } catch (error) {
          results[`sink:${id}`] = {
            healthy: false,
            lastCheck: new Date().toISOString(),
            error: (error as Error).message,
          };
        }
      }
    }

    this.state.lastHealthCheck = new Date().toISOString();
    this.state.healthy = Object.values(results).every(r => r.healthy);
    this.updateState();

    return results;
  }

  /**
   * Subscribe to telemetry updates
   */
  onTelemetryUpdate(listener: TelemetryUpdateListener): () => void {
    this.telemetryListeners.add(listener);
    return () => this.telemetryListeners.delete(listener);
  }

  /**
   * Subscribe to state changes
   */
  onStateChange(listener: StateChangeListener): () => void {
    this.stateListeners.add(listener);
    return () => this.stateListeners.delete(listener);
  }

  /**
   * Subscribe to errors
   */
  onError(listener: ErrorListener): () => void {
    this.errorListeners.add(listener);
    return () => this.errorListeners.delete(listener);
  }

  /**
   * Shutdown the connector manager
   */
  async shutdown(): Promise<void> {
    this.logger.info('Shutting down DesktopConnectorManager');

    // Stop health checks
    if (this.healthCheckTimer) {
      clearInterval(this.healthCheckTimer);
    }

    // Stop all sources
    for (const id of this.sourceUnsubscribers.keys()) {
      await this.stopSource(id);
    }

    // Close all sources
    for (const source of this.sources.values()) {
      if (source.close) {
        await source.close();
      }
    }

    // Close all sinks
    for (const sink of this.sinks.values()) {
      if (sink.close) {
        await sink.close();
      }
    }

    this.state.initialized = false;
    this.state.activeSourcesCount = 0;
    this.state.activeSinksCount = 0;
    this.updateState();

    this.logger.info('DesktopConnectorManager shutdown complete');
  }

  // ===== Private Methods =====

  private async initializeSources(): Promise<void> {
    const sourceContext = {
      workspaceId: this.config.workspaceId,
      keyring: this.keyring,
      logger: this.logger,
      tracer: this.tracer,
    };

    for (const config of this.config.sources) {
      if (!config.enabled) {
        this.logger.info(`Skipping disabled source: ${config.id}`);
        continue;
      }

      try {
        const source = adapterFactory.createSource({
          type: this.mapConnectorType(config.type),
          options: config.options,
        });

        await source.init(sourceContext);
        this.sources.set(config.id, source);

        this.logger.info(`Source initialized: ${config.id}`, {
          type: config.type,
          kind: source.kind,
        });
      } catch (error) {
        this.logger.error(`Failed to initialize source: ${config.id}`, error as Error);
        this.state.errors.push(`Source ${config.id}: ${(error as Error).message}`);
        this.notifyError(error as Error, config.id);
      }
    }
  }

  private async initializeSinks(): Promise<void> {
    const sinkContext = {
      workspaceId: this.config.workspaceId,
      keyring: this.keyring,
      queue: this.queue,
      logger: this.logger,
      tracer: this.tracer,
    };

    for (const config of this.config.sinks) {
      if (!config.enabled) {
        this.logger.info(`Skipping disabled sink: ${config.id}`);
        continue;
      }

      try {
        const sink = adapterFactory.createSink({
          type: this.mapConnectorType(config.type),
          options: config.options,
        });

        await sink.init(sinkContext);
        this.sinks.set(config.id, sink);

        this.logger.info(`Sink initialized: ${config.id}`, {
          type: config.type,
          kind: sink.kind,
        });
      } catch (error) {
        this.logger.error(`Failed to initialize sink: ${config.id}`, error as Error);
        this.state.errors.push(`Sink ${config.id}: ${(error as Error).message}`);
        this.notifyError(error as Error, config.id);
      }
    }
  }

  private async addSource(config: ConnectorConfig): Promise<void> {
    const sourceContext = {
      workspaceId: this.config.workspaceId,
      keyring: this.keyring,
      logger: this.logger,
      tracer: this.tracer,
    };

    const source = adapterFactory.createSource({
      type: this.mapConnectorType(config.type),
      options: config.options,
    });

    await source.init(sourceContext);
    this.sources.set(config.id, source);
    this.config.sources.push(config);
    this.state.totalSourcesCount++;

    this.logger.info(`Source added: ${config.id}`, { type: config.type });
  }

  private async addSink(config: ConnectorConfig): Promise<void> {
    const sinkContext = {
      workspaceId: this.config.workspaceId,
      keyring: this.keyring,
      queue: this.queue,
      logger: this.logger,
      tracer: this.tracer,
    };

    const sink = adapterFactory.createSink({
      type: this.mapConnectorType(config.type),
      options: config.options,
    });

    await sink.init(sinkContext);
    this.sinks.set(config.id, sink);
    this.config.sinks.push(config);
    this.state.totalSinksCount++;
    this.state.activeSinksCount++;

    this.logger.info(`Sink added: ${config.id}`, { type: config.type });
  }

  private mapConnectorType(type: string): string {
    // Map desktop connector types to adapter factory types
    const typeMap: Record<string, string> = {
      agent: 'bridge',
      extension: 'bridge',
      http: 'http',
      grpc: 'grpc',
      bridge: 'bridge',
      file: 'file',
      mock: 'mock',
      custom: 'custom',
    };

    return typeMap[type] || type;
  }

  private handleTelemetryUpdate(snapshot: TelemetrySnapshot, sourceId: string): void {
    this.logger.debug('Telemetry update received', {
      sourceId,
      version: snapshot.version,
      agentsCount: snapshot.agents?.length ?? 0,
    });

    // Notify all listeners
    for (const listener of this.telemetryListeners) {
      try {
        listener(snapshot);
      } catch (error) {
        this.logger.error('Error in telemetry listener', error as Error);
      }
    }
  }

  private updateState(): void {
    // Notify state listeners
    for (const listener of this.stateListeners) {
      try {
        listener(this.getState());
      } catch (error) {
        this.logger.error('Error in state listener', error as Error);
      }
    }
  }

  private notifyError(error: Error, connectorId?: string): void {
    for (const listener of this.errorListeners) {
      try {
        listener(error, connectorId);
      } catch (err) {
        this.logger.error('Error in error listener', err as Error);
      }
    }
  }

  private startHealthChecks(): void {
    const interval = this.config.healthCheckInterval ?? 60000; // Default 1 minute

    this.healthCheckTimer = setInterval(() => {
      this.healthCheck().catch(error => {
        this.logger.error('Health check failed', error as Error);
      });
    }, interval);
  }

  /**
   * Subscribe to events
   */
  public on(event: 'telemetryUpdate', listener: TelemetryUpdateListener): void;
  public on(event: 'stateChange', listener: StateChangeListener): void;
  public on(event: 'error', listener: ErrorListener): void;
  public on(event: string, listener: any): void {
    switch (event) {
      case 'telemetryUpdate':
        this.telemetryListeners.add(listener as TelemetryUpdateListener);
        break;
      case 'stateChange':
        this.stateListeners.add(listener as StateChangeListener);
        break;
      case 'error':
        this.errorListeners.add(listener as ErrorListener);
        break;
      default:
        throw new Error(`Unknown event type: ${event}`);
    }
  }

  /**
   * Unsubscribe from events
   */
  public off(event: 'telemetryUpdate', listener: TelemetryUpdateListener): void;
  public off(event: 'stateChange', listener: StateChangeListener): void;
  public off(event: 'error', listener: ErrorListener): void;
  public off(event: string, listener: any): void {
    switch (event) {
      case 'telemetryUpdate':
        this.telemetryListeners.delete(listener as TelemetryUpdateListener);
        break;
      case 'stateChange':
        this.stateListeners.delete(listener as StateChangeListener);
        break;
      case 'error':
        this.errorListeners.delete(listener as ErrorListener);
        break;
      default:
        throw new Error(`Unknown event type: ${event}`);
    }
  }
}

/**
 * Factory function to create and initialize a connector manager
 */
export const createDesktopConnectorManager = async (
  config: DesktopConnectorConfig
): Promise<DesktopConnectorManager> => {
  const manager = new DesktopConnectorManager(config);
  await manager.initialize();
  return manager;
};
