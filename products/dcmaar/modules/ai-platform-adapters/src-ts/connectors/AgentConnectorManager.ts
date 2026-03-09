/**
 * Agent Connector Manager
 * Main orchestrator for connector integration
 * Following Desktop pattern from DesktopConnectorManager.ts (738 lines)
 *
 * Responsibilities:
 * - Initialize sources and sinks
 * - Manage routing between sources and sinks
 * - Handle bridge messages from Rust
 * - Coordinate processor chains
 * - Emit lifecycle events
 */

import { EventEmitter } from 'eventemitter3';
import {
  ConnectorManager,
  type IConnector,
  type Event,
} from '@ghatana/dcmaar-connectors';
import type {
  AgentConnectorConfig,
  SourceConfig,
  SinkConfig,
  BridgeMessage,
  ConnectorStatus,
} from './types';
import { Logger } from '../utils/logger';

export class AgentConnectorManager extends EventEmitter {
  private connectorManager: ConnectorManager;
  private config: AgentConnectorConfig | null = null;
  private initialized = false;
  private logger: Logger;
  private sourceMap: Map<string, IConnector> = new Map();
  private sinkMap: Map<string, IConnector> = new Map();

  constructor() {
    super();
    this.connectorManager = new ConnectorManager();
    this.logger = new Logger('AgentConnectorManager');
  }

  /**
   * Initialize connector manager with configuration
   * Follows Desktop pattern from DesktopConnectorManager.initialize()
   */
  async initialize(config: AgentConnectorConfig): Promise<void> {
    this.logger.info('Initializing AgentConnectorManager', { version: config.version });

    try {
      if (this.initialized) {
        throw new Error('AgentConnectorManager already initialized');
      }

      this.config = config;

      // 1. Initialize sources
      this.logger.debug('Initializing sources', { count: config.sources.length });
      for (const sourceConfig of config.sources) {
        if (sourceConfig.enabled !== false) {
          await this.addSource(sourceConfig);
        } else {
          this.logger.debug('Skipping disabled source', { id: sourceConfig.id });
        }
      }

      // 2. Initialize sinks
      this.logger.debug('Initializing sinks', { count: config.sinks.length });
      for (const sinkConfig of config.sinks) {
        if (sinkConfig.enabled !== false) {
          await this.addSink(sinkConfig);
        } else {
          this.logger.debug('Skipping disabled sink', { id: sinkConfig.id });
        }
      }

      // 3. Setup routing
      this.logger.debug('Setting up routing', { routes: config.routing.length });
      for (const route of config.routing) {
        this.connectorManager.updateSourceRoutes(route.sourceId, route.sinkIds);
        this.logger.debug('Route configured', {
          source: route.sourceId,
          sinks: route.sinkIds,
        });
      }

      // 4. Initialize processors (if any)
      if (config.processors && config.processors.length > 0) {
        this.logger.debug('Initializing processors', { count: config.processors.length });
        for (const procConfig of config.processors) {
          // TODO: Add processor support in Phase 2
          this.logger.warn('Processor support not yet implemented', { id: procConfig.id });
        }
      }

      this.initialized = true;
      this.emit('initialized', { config });
      this.logger.info('AgentConnectorManager initialized successfully', {
        sources: this.sourceMap.size,
        sinks: this.sinkMap.size,
      });
    } catch (error) {
      this.logger.error('Failed to initialize AgentConnectorManager', { error });
      throw error;
    }
  }

  /**
   * Add source connector
   */
  async addSource(config: SourceConfig): Promise<IConnector> {
    this.logger.debug('Adding source', { sourceId: config.id, type: config.type });

    try {
      // ConnectorManager creates the connector internally
      const connector = await this.connectorManager.addSource(config);

      // Store in local map for quick access
      this.sourceMap.set(config.id, connector);

      this.emit('sourceAdded', { sourceId: config.id, config });
      this.logger.info('Source added successfully', { sourceId: config.id, type: config.type });

      return connector;
    } catch (error) {
      this.logger.error('Failed to add source', { sourceId: config.id, error });
      throw error;
    }
  }

  /**
   * Add sink connector
   */
  async addSink(config: SinkConfig): Promise<IConnector> {
    this.logger.debug('Adding sink', { sinkId: config.id, type: config.type });

    try {
      // ConnectorManager creates the connector internally
      const connector = await this.connectorManager.addSink(config);

      // Store in local map for quick access
      this.sinkMap.set(config.id, connector);

      this.emit('sinkAdded', { sinkId: config.id, config });
      this.logger.info('Sink added successfully', { sinkId: config.id, type: config.type });

      return connector;
    } catch (error) {
      this.logger.error('Failed to add sink', { sinkId: config.id, error });
      throw error;
    }
  }

  /**
   * Handle message from Rust bridge
   * Converts BridgeMessage to Event and routes through connector manager
   */
  async handleBridgeMessage(msg: BridgeMessage): Promise<void> {
    this.logger.debug('Handling bridge message', { id: msg.id, type: msg.eventType });

    try {
      const event: Event = {
        id: msg.id,
        type: msg.eventType,
        timestamp: msg.timestamp,
        payload: msg.payload,
        metadata: msg.metadata,
      };

      // Emit to connector system
      // The connector manager will route through sources → processors → sinks
      this.emit('bridgeMessage', event);

      this.logger.debug('Bridge message handled', { id: event.id });
    } catch (error) {
      this.logger.error('Failed to handle bridge message', { id: msg.id, error });
      throw error;
    }
  }

  /**
   * Remove source connector
   */
  async removeSource(sourceId: string): Promise<void> {
    this.logger.debug('Removing source', { sourceId });

    try {
      await this.connectorManager.removeSource(sourceId);
      this.sourceMap.delete(sourceId);
      this.emit('sourceRemoved', { sourceId });
      this.logger.info('Source removed', { sourceId });
    } catch (error) {
      this.logger.error('Failed to remove source', { sourceId, error });
      throw error;
    }
  }

  /**
   * Remove sink connector
   */
  async removeSink(sinkId: string): Promise<void> {
    this.logger.debug('Removing sink', { sinkId });

    try {
      await this.connectorManager.removeSink(sinkId);
      this.sinkMap.delete(sinkId);
      this.emit('sinkRemoved', { sinkId });
      this.logger.info('Sink removed', { sinkId });
    } catch (error) {
      this.logger.error('Failed to remove sink', { sinkId, error });
      throw error;
    }
  }

  /**
   * Shutdown all connectors
   */
  async shutdown(): Promise<void> {
    this.logger.info('Shutting down AgentConnectorManager');

    try {
      // Shutdown connector manager (will disconnect all connectors)
      await this.connectorManager.shutdown();

      // Clear local maps
      this.sourceMap.clear();
      this.sinkMap.clear();

      this.initialized = false;
      this.emit('shutdown');
      this.logger.info('AgentConnectorManager shut down successfully');
    } catch (error) {
      this.logger.error('Error during shutdown', { error });
      throw error;
    }
  }

  /**
   * Get status of all connectors
   */
  getStatus(): ConnectorStatus {
    const sources = Array.from(this.sourceMap.entries()).map(([id, connector]) => ({
      id,
      type: connector.type || 'unknown',
      status: connector.status === 'connected'
        ? ('connected' as const)
        : connector.status === 'error'
        ? ('error' as const)
        : ('disconnected' as const),
    }));

    const sinks = Array.from(this.sinkMap.entries()).map(([id, connector]) => ({
      id,
      type: connector.type || 'unknown',
      status: connector.status === 'connected'
        ? ('connected' as const)
        : connector.status === 'error'
        ? ('error' as const)
        : ('disconnected' as const),
    }));

    return {
      initialized: this.initialized,
      sources,
      sinks,
    };
  }

  /**
   * Get configuration
   */
  getConfig(): AgentConnectorConfig | null {
    return this.config;
  }

  /**
   * Check if initialized
   */
  isInitialized(): boolean {
    return this.initialized;
  }
}
