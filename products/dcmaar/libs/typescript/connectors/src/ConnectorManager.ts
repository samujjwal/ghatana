/**
 * @fileoverview Connector orchestration and routing manager
 *
 * Coordinates source and sink connectors, manages event routing pipelines,
 * processor chains, lifecycle operations, and system-wide status reporting.
 * Provides the central control plane for wiring connectors together in
 * streaming topologies.
 *
 * **Key Responsibilities:**
 * - Instantiate and configure source/sink connectors
 * - Route events from sources to target sinks
 * - Apply per-connector processor chains
 * - Manage connector lifecycle (enable/disable, restart, shutdown)
 * - Track operational status and health information
 *
 * @module ConnectorManager
 * @since 1.0.0
 */

import { EventEmitter } from 'events';
import { IConnector, Event, ConnectionOptions } from './types';
import { HttpConnector } from './connectors/HttpConnector';
import { WebSocketConnector } from './connectors/WebSocketConnector';
import { NativeConnector } from './connectors/NativeConnector';
import { ProcessorRegistry } from './processors/ProcessorRegistry';
import { StorageProvider } from './storage/StorageProvider';
import { MemoryStorageProvider } from './storage/MemoryStorageProvider';
import { IpcChannel } from './ipc/IpcChannel';

/**
 * Event processor function signature.
 *
 * **How it works:**
 * Receives an event, optionally performs asynchronous transformations,
 * and returns the transformed event.
 *
 * @template T - Input event payload type
 * @template R - Output event payload type
 */
type Processor<T = any, R = any> = (event: Event<T>) => Promise<Event<R>> | Event<R>;

/**
 * Configuration schema for source connectors.
 *
 * @interface SourceConfig
 * @extends ConnectionOptions
 */
interface SourceConfig extends ConnectionOptions {
  /** Unique identifier for the source connector. */
  id: string;
  /** Connector type (maps to specific connector implementation). */
  type: string;
  /** Optional processor chain applied to outbound events. */
  processors?: Processor[];
  /** IDs of target sinks that should receive events from this source. */
  sinks?: string[];
  /** Whether the source is enabled (connected) by default. */
  enabled?: boolean;
  /** Arbitrary metadata for observability or configuration. */
  metadata?: Record<string, any>;
}

/**
 * Configuration schema for sink connectors.
 *
 * @interface SinkConfig
 * @extends ConnectionOptions
 */
interface SinkConfig extends ConnectionOptions {
  /** Unique identifier for the sink connector. */
  id: string;
  /** Connector type (maps to specific connector implementation). */
  type: string;
  /** Optional processor chain applied to inbound events before sending. */
  processors?: Processor[];
  /** IDs of source connectors whose events should be accepted. */
  sources?: string[];
  /** Whether the sink is enabled (connected) by default. */
  enabled?: boolean;
  /** Arbitrary metadata for observability or configuration. */
  metadata?: Record<string, any>;
}

/**
 * Central orchestrator for managing source and sink connectors.
 *
 * Handles connector instantiation, event routing, processor execution,
 * lifecycle operations, and system-wide status reporting.
 *
 * **Workflow:**
 * 1. `initialize()` loads source/sink configurations and connects them
 * 2. Events from sources flow through processor chains and routes
 * 3. Events are delivered to target sinks with optional processing
 * 4. Lifecycle operations (enable/disable/restart/shutdown) manage state
 *
 * @class ConnectorManager
 * @extends EventEmitter
 */
export class ConnectorManager extends EventEmitter {
  /** Registered source connectors keyed by ID. */
  private sources: Map<string, { connector: IConnector; config: SourceConfig }> = new Map();
  /** Registered sink connectors keyed by ID. */
  private sinks: Map<string, { connector: IConnector; config: SinkConfig }> = new Map();
  /** Event routing table: source ID -> set of sink IDs. */
  private eventRoutes: Map<string, Set<string>> = new Map();
  /** Processor chains per connector ID. */
  private processors: Map<string, Processor[]> = new Map();
  /** Indicates whether manager has been initialized. */
  private isInitialized: boolean = false;
  /** Current manager lifecycle status. */
  private _status: 'idle' | 'starting' | 'running' | 'stopping' = 'idle';

  /** Platform integrations (v1.1.0) */
  private storage: StorageProvider;
  private ipcChannel?: IpcChannel;
  private processorRegistry: ProcessorRegistry;

  /**
   * Creates a new ConnectorManager instance.
   *
   * @param config - Optional configuration for dependency injection (v1.1.0+)
   *
   * @example
   * ```typescript
   * // Default (memory storage, no IPC)
   * const manager = new ConnectorManager();
   *
   * // With platform integrations
   * const manager = new ConnectorManager({
   *   storage: new ExtensionStorageProvider(),
   *   ipcChannel: new ExtensionIpcChannel(),
   *   processorRegistry: customRegistry
   * });
   * ```
   */
  constructor(config?: {
    /** Storage provider for configuration persistence */
    storage?: StorageProvider;
    /** IPC channel for cross-context messaging */
    ipcChannel?: IpcChannel;
    /** Processor registry for custom processors */
    processorRegistry?: ProcessorRegistry;
  }) {
    super();
    this.setMaxListeners(100); // Increase max listeners for potential many connectors

    // Initialize platform integrations (v1.1.0)
    this.storage = config?.storage ?? new MemoryStorageProvider();
    this.ipcChannel = config?.ipcChannel;
    this.processorRegistry = config?.processorRegistry ?? new ProcessorRegistry();
  }

  /**
   * Gets current manager lifecycle status.
   * @returns {'idle' | 'starting' | 'running' | 'stopping'}
   */
  get status() {
    return this._status;
  }

  /**
   * Creates a browser-safe connector instance.
   * Only supports http, websocket, and native connectors.
   * 
   * @private
   */
  private createBrowserConnector<T extends ConnectionOptions>(config: T & { type: string }): IConnector<T> {
    switch (config.type) {
      case 'http':
        return new HttpConnector(config as any) as unknown as IConnector<T>;
      case 'websocket':
        return new WebSocketConnector(config as any) as unknown as IConnector<T>;
      case 'native':
        return new NativeConnector(config as any) as unknown as IConnector<T>;
      default:
        throw new Error(`Unsupported connector type in browser: ${config.type}. Only http, websocket, and native are supported.`);
    }
  }

  /**
   * Initializes the manager with source and sink configurations.
   *
   * **How it works:**
   * 1. Validates initialization state
   * 2. Instantiates sources and sinks
   * 3. Sets up routes/processors
   * 4. Connects enabled connectors
   *
   * @param {{sources?: SourceConfig[]; sinks?: SinkConfig[]}} param0 - Initialization payload
   * @returns {Promise<void>}
   * @throws {Error} If already initialized or setup fails
   * @fires ConnectorManager#statusChange
   * @fires ConnectorManager#initialized
   */
  async initialize({
    sources = [],
    sinks = [],
  }: {
    sources?: SourceConfig[];
    sinks?: SinkConfig[];
  } = {}): Promise<void> {
    if (this.isInitialized) {
      throw new Error('ConnectorManager is already initialized');
    }

    this._status = 'starting';
    this.emit('statusChange', { status: this._status });

    try {
      // Create and configure sinks first (sources may reference them)
      for (const sinkConfig of sinks) {
        await this.addSink(sinkConfig);
      }

      // Create and configure sources
      for (const sourceConfig of sources) {
        await this.addSource(sourceConfig);
      }

      this.isInitialized = true;
      this._status = 'running';
      this.emit('initialized');
      this.emit('statusChange', { status: this._status });
    } catch (error) {
      this._status = 'idle';
      this.emit('error', error);
      this.emit('statusChange', { status: this._status, error });
      throw error;
    }
  }

  /**
   * Adds a new source connector and optionally connects it.
   *
   * @param {SourceConfig} config - Source configuration
   * @returns {Promise<IConnector>} Created connector instance
   * @throws {Error} If source already exists or setup fails
   * @fires ConnectorManager#sourceAdded
   */
  async addSource(config: SourceConfig): Promise<IConnector> {
    if (this.sources.has(config.id)) {
      throw new Error(`Source with ID '${config.id}' already exists`);
    }

    const connector = this.createBrowserConnector({
      ...config,
      id: config.id,
      type: config.type,
    });

    // Store the source BEFORE connecting so it's available for routing
    this.sources.set(config.id, {
      connector,
      config: { ...config, enabled: config.enabled ?? true },
    });

    // Set up event processors if any
    if (config.processors?.length) {
      this.processors.set(config.id, [...config.processors]);
    }

    // Set up event routing
    if (config.sinks?.length) {
      this.updateSourceRoutes(config.id, config.sinks);
    }

    try {
      // Connect if enabled
      if (config.enabled !== false) {
        await connector.connect();

        // Set up event listeners
        connector.onEvent('*', (event) => this.handleSourceEvent(config.id, event));
      }

      this.emit('sourceAdded', { sourceId: config.id, config });
      return connector;
    } catch (error) {
      this.emit('error', { source: 'addSource', error, config });
      // Don't throw - source is still registered, just not connected
      return connector;
    }
  }

  /**
   * Adds a new sink connector and optionally connects it.
   *
   * @param {SinkConfig} config - Sink configuration
   * @returns {Promise<IConnector>} Created connector instance
   * @throws {Error} If sink already exists or setup fails
   * @fires ConnectorManager#sinkAdded
   */
  async addSink(config: SinkConfig): Promise<IConnector> {
    if (this.sinks.has(config.id)) {
      throw new Error(`Sink with ID '${config.id}' already exists`);
    }

    const connector = this.createBrowserConnector({
      ...config,
      id: config.id,
      type: config.type,
    });

    // Store the sink BEFORE connecting so it's available for routing validation
    this.sinks.set(config.id, {
      connector,
      config: { ...config, enabled: config.enabled ?? true },
    });

    // Set up event processors if any
    if (config.processors?.length) {
      this.processors.set(config.id, [...config.processors]);
    }

    try {
      // Connect if enabled
      if (config.enabled !== false) {
        await connector.connect();
      }

      this.emit('sinkAdded', { sinkId: config.id, config });
      return connector;
    } catch (error) {
      this.emit('error', { source: 'addSink', error, config });
      // Don't throw - sink is still registered, just not connected
      return connector;
    }
  }

  /**
   * Updates routing table for a source to target sinks.
   *
   * @param {string} sourceId - Source connector ID
   * @param {string[]} sinkIds - Target sink IDs
   * @throws {Error} If source or sink IDs are invalid
   * @fires ConnectorManager#routesUpdated
   */
  updateSourceRoutes(sourceId: string, sinkIds: string[]): void {
    if (!this.sources.has(sourceId)) {
      throw new Error(`Source '${sourceId}' not found`);
    }

    // Validate all sink IDs
    for (const sinkId of sinkIds) {
      if (!this.sinks.has(sinkId)) {
        throw new Error(`Sink '${sinkId}' not found`);
      }
    }

    this.eventRoutes.set(sourceId, new Set(sinkIds));
    this.emit('routesUpdated', { sourceId, sinkIds });
  }

  /**
   * Registers a processor in a connector's processor chain.
   *
   * @param {string} connectorId - Connector identifier
   * @param {Processor} processor - Processor function
   * @param {number} [index] - Optional insertion index
   * @fires ConnectorManager#processorAdded
   */
  addProcessor(connectorId: string, processor: Processor, index?: number): void {
    const processors = this.processors.get(connectorId) || [];

    if (index !== undefined && index >= 0 && index < processors.length) {
      processors.splice(index, 0, processor);
    } else {
      processors.push(processor);
    }

    this.processors.set(connectorId, processors);
    this.emit('processorAdded', { connectorId, processor });
  }

  /**
   * Removes a processor by reference or index.
   *
   * @param {string} connectorId - Connector identifier
   * @param {Processor | number} processorOrIndex - Processor or index to remove
   * @returns {boolean} True if processor removed
   * @fires ConnectorManager#processorRemoved
   */
  removeProcessor(connectorId: string, processorOrIndex: Processor | number): boolean {
    const processors = this.processors.get(connectorId);
    if (!processors) return false;

    let removed = false;

    if (typeof processorOrIndex === 'number') {
      if (processorOrIndex >= 0 && processorOrIndex < processors.length) {
        processors.splice(processorOrIndex, 1);
        removed = true;
      }
    } else {
      const index = processors.indexOf(processorOrIndex);
      if (index !== -1) {
        processors.splice(index, 1);
        removed = true;
      }
    }

    if (removed) {
      this.emit('processorRemoved', { connectorId, processor: processorOrIndex });
      if (processors.length === 0) {
        this.processors.delete(connectorId);
      }
    }

    return removed;
  }

  /**
   * Handles inbound events from source connectors.
   *
   * **How it works:**
   * 1. Skips if source disabled
   * 2. Enriches event with metadata
   * 3. Runs source processor pipeline
   * 4. Routes event to configured sinks
   * 5. Emits success/error telemetry
   *
   * @param {string} sourceId - Source connector ID
   * @param {Event} event - Incoming event
   * @private
   */
  private async handleSourceEvent(sourceId: string, event: Event): Promise<void> {
    try {
      // Get the source
      const source = this.sources.get(sourceId);
      if (!source || source.config.enabled === false) return;

      // Add source metadata if not present
      const enrichedEvent: Event = {
        ...event,
        metadata: {
          ...event.metadata,
          sourceId,
          receivedAt: Date.now(),
        },
      };

      // Process the event through source processors
      const processedEvent = await this.processEvent(sourceId, enrichedEvent);

      // Get the target sinks for this source
      const sinkIds = this.eventRoutes.get(sourceId) || new Set();

      // Send to all target sinks
      await Promise.all(
        Array.from(sinkIds).map(async (sinkId) => {
          const sink = this.sinks.get(sinkId);
          if (!sink || sink.config.enabled === false) return;

          try {
            // Process the event through sink processors
            const sinkEvent = await this.processEvent(sinkId, {
              ...processedEvent,
              metadata: {
                ...processedEvent.metadata,
                sinkId,
              },
            });

            // Send to sink
            await sink.connector.send(sinkEvent.payload, {
              eventId: sinkEvent.id,
              eventType: sinkEvent.type,
              metadata: sinkEvent.metadata,
            });

            this.emit('eventProcessed', {
              sourceId,
              sinkId,
              event: sinkEvent,
              status: 'success',
            });
          } catch (error) {
            this.emit('eventError', {
              sourceId,
              sinkId,
              event: processedEvent,
              error,
              status: 'error',
            });
          }
        })
      );
    } catch (error) {
      this.emit('error', {
        source: 'handleSourceEvent',
        error,
        sourceId,
        event,
      });
    }
  }

  /**
   * Executes processor pipeline for a connector.
   *
   * @param {string} connectorId - Connector identifier
   * @param {Event} event - Event to process
   * @returns {Promise<Event>} Processed event
   * @throws {Error} If processor throws or returns invalid event
   * @private
   */
  private async processEvent(connectorId: string, event: Event): Promise<Event> {
    const processors = this.processors.get(connectorId) || [];

    let currentEvent = { ...event };

    for (const processor of processors) {
      try {
        currentEvent = await processor(currentEvent);

        // If processor returns null/undefined, stop processing
        if (currentEvent == null) {
          throw new Error('Processor returned null/undefined event');
        }
      } catch (error) {
        this.emit('processorError', {
          connectorId,
          event: currentEvent,
          processor,
          error,
        });
        throw error;
      }
    }

    return currentEvent;
  }

  /**
   * Retrieves source connector instance by ID.
   *
   * @param {string} sourceId - Source identifier
   * @returns {IConnector | undefined} Connector instance if registered
   */
  getSource(sourceId: string): IConnector | undefined {
    return this.sources.get(sourceId)?.connector;
  }

  /**
   * Retrieves sink connector instance by ID.
   *
   * @param {string} sinkId - Sink identifier
   * @returns {IConnector | undefined} Connector instance if registered
   */
  getSink(sinkId: string): IConnector | undefined {
    return this.sinks.get(sinkId)?.connector;
  }

  /**
   * Lists all registered source connectors.
   *
   * @returns {{ id: string; connector: IConnector; config: SourceConfig }[]}
   */
  getSources(): { id: string; connector: IConnector; config: SourceConfig }[] {
    return Array.from(this.sources.entries()).map(([id, { connector, config }]) => ({
      id,
      connector,
      config,
    }));
  }

  /**
   * Lists all registered sink connectors.
   *
   * @returns {{ id: string; connector: IConnector; config: SinkConfig }[]}
   */
  getSinks(): { id: string; connector: IConnector; config: SinkConfig }[] {
    return Array.from(this.sinks.entries()).map(([id, { connector, config }]) => ({
      id,
      connector,
      config,
    }));
  }

  /**
   * Removes a source connector and disconnects it.
   *
   * @param {string} sourceId - Source identifier
   * @returns {Promise<boolean>} True if source removed
   * @fires ConnectorManager#sourceRemoved
   */
  async removeSource(sourceId: string): Promise<boolean> {
    const source = this.sources.get(sourceId);
    if (!source) return false;

    try {
      await source.connector.disconnect();
    } catch (error) {
      this.emit('error', { source: 'removeSource', error, sourceId });
    }

    this.sources.delete(sourceId);
    this.eventRoutes.delete(sourceId);
    this.processors.delete(sourceId);

    this.emit('sourceRemoved', { sourceId });
    return true;
  }

  /**
   * Removes a sink connector and disconnects it.
   *
   * @param {string} sinkId - Sink identifier
   * @returns {Promise<boolean>} True if sink removed
   * @fires ConnectorManager#sinkRemoved
   */
  async removeSink(sinkId: string): Promise<boolean> {
    const sink = this.sinks.get(sinkId);
    if (!sink) return false;

    try {
      await sink.connector.disconnect();
    } catch (error) {
      this.emit('error', { source: 'removeSink', error, sinkId });
    }

    // Remove from all routes
    for (const [sourceId, sinkIds] of this.eventRoutes.entries()) {
      if (sinkIds.has(sinkId)) {
        sinkIds.delete(sinkId);
        if (sinkIds.size === 0) {
          this.eventRoutes.delete(sourceId);
        }
      }
    }

    this.sinks.delete(sinkId);
    this.processors.delete(sinkId);

    this.emit('sinkRemoved', { sinkId });
    return true;
  }

  /**
   * Enables or disables a source connector.
   *
   * @param {string} sourceId - Source identifier
   * @param {boolean} enabled - Desired state
   * @returns {Promise<void>}
   * @throws {Error} If source not found
   * @fires ConnectorManager#sourceUpdated
   */
  async setSourceEnabled(sourceId: string, enabled: boolean): Promise<void> {
    const source = this.sources.get(sourceId);
    if (!source) throw new Error(`Source '${sourceId}' not found`);

    if (source.config.enabled === enabled) return;

    if (enabled) {
      await source.connector.connect();
    } else {
      await source.connector.disconnect();
    }

    source.config.enabled = enabled;
    this.emit('sourceUpdated', { sourceId, enabled });
  }

  /**
   * Enables or disables a sink connector.
   *
   * @param {string} sinkId - Sink identifier
   * @param {boolean} enabled - Desired state
   * @returns {Promise<void>}
   * @throws {Error} If sink not found
   * @fires ConnectorManager#sinkUpdated
   */
  async setSinkEnabled(sinkId: string, enabled: boolean): Promise<void> {
    const sink = this.sinks.get(sinkId);
    if (!sink) throw new Error(`Sink '${sinkId}' not found`);

    if (sink.config.enabled === enabled) return;

    if (enabled) {
      await sink.connector.connect();
    } else {
      await sink.connector.disconnect();
    }

    sink.config.enabled = enabled;
    this.emit('sinkUpdated', { sinkId, enabled });
  }

  /**
   * Collects current status snapshot of manager and connectors.
   *
   * @returns {{ status: string; sources: any[]; sinks: any[]; routes: any[] }}
   */
  getStatus() {
    return {
      status: this._status,
      sources: Array.from(this.sources.entries()).map(([id, { connector, config }]) => ({
        id,
        type: config.type,
        status: connector.status,
        enabled: config.enabled,
        metadata: config.metadata,
      })),
      sinks: Array.from(this.sinks.entries()).map(([id, { connector, config }]) => ({
        id,
        type: config.type,
        status: connector.status,
        enabled: config.enabled,
        metadata: config.metadata,
      })),
      routes: Array.from(this.eventRoutes.entries()).map(([sourceId, sinkIds]) => ({
        sourceId,
        sinkIds: Array.from(sinkIds),
      })),
    };
  }

  /**
   * Gracefully shuts down all connectors and resets state.
   *
   * @returns {Promise<void>}
   * @fires ConnectorManager#statusChange
   * @fires ConnectorManager#shutdown
   */
  async shutdown(): Promise<void> {
    if (this._status === 'stopping' || this._status === 'idle') return;

    this._status = 'stopping';
    this.emit('statusChange', { status: this._status });

    // Disconnect all sources and sinks
    await Promise.all([
      ...Array.from(this.sources.values()).map(async ({ connector }) => {
        try {
          await connector.disconnect();
        } catch (error) {
          this.emit('error', { source: 'shutdown', error, type: 'source' });
        }
      }),
      ...Array.from(this.sinks.values()).map(async ({ connector }) => {
        try {
          await connector.disconnect();
        } catch (error) {
          this.emit('error', { source: 'shutdown', error, type: 'sink' });
        }
      }),
    ]);

    // Clear all collections
    this.sources.clear();
    this.sinks.clear();
    this.eventRoutes.clear();
    this.processors.clear();

    this.isInitialized = false;
    this._status = 'idle';
    this.emit('shutdown');
    this.emit('statusChange', { status: this._status });
  }

  /**
   * Restarts the manager by preserving configuration and reinitializing.
   *
   * @returns {Promise<void>}
   * @throws {Error} If called during transitional states
   */
  async restart(): Promise<void> {
    if (this._status === 'starting' || this._status === 'stopping') {
      throw new Error(`Cannot restart while ${this._status}`);
    }

    const currentState = {
      sources: this.getSources().map(({ config }) => config),
      sinks: this.getSinks().map(({ config }) => config),
    };

    await this.shutdown();
    await this.initialize(currentState);
  }
}
