/**
 * Agent Adapter Factory
 * Creates pluggable source and sink adapters
 * Following Desktop pattern from DesktopConnectorManager
 */

import type { IConnector } from '@ghatana/dcmaar-connectors';
import type { SourceAdapter, SinkAdapter, AdapterRegistry } from './types';
import { Logger } from '../utils/logger';
import { BridgeSourceAdapter } from './sources/BridgeSourceAdapter';
import { IngestSinkAdapter } from './sinks/IngestSinkAdapter';
import { ConsoleSinkAdapter } from './sinks/ConsoleSinkAdapter';

export class AgentAdapterFactory implements AdapterRegistry {
  private sources: Map<string, SourceAdapter> = new Map();
  private sinks: Map<string, SinkAdapter> = new Map();
  private logger: Logger;

  constructor() {
    this.logger = new Logger('AgentAdapterFactory');
    this.registerDefaultAdapters();
  }

  /**
   * Register default adapters
   */
  private registerDefaultAdapters(): void {
    this.logger.debug('Registering default adapters');

    // Register source adapters
    this.registerSource('bridge', new BridgeSourceAdapter());
    // TODO: Add more source adapters
    // this.registerSource('websocket', new WebSocketSourceAdapter());
    // this.registerSource('internal', new InternalMetricsSourceAdapter());

    // Register sink adapters
    this.registerSink('ingest', new IngestSinkAdapter());
    this.registerSink('console', new ConsoleSinkAdapter());
    // TODO: Add more sink adapters
    // this.registerSink('grpc', new GrpcSinkAdapter());
    // this.registerSink('websocket', new WebSocketSinkAdapter());

    const { sources, sinks } = this.getRegisteredTypes();
    this.logger.info('Default adapters registered', {
      sources: sources.length,
      sinks: sinks.length,
      sourceTypes: sources,
      sinkTypes: sinks,
    });
  }

  registerSource(type: string, adapter: SourceAdapter): void {
    this.sources.set(type, adapter);
    this.logger.debug('Registered source adapter', { type });
  }

  registerSink(type: string, adapter: SinkAdapter): void {
    this.sinks.set(type, adapter);
    this.logger.debug('Registered sink adapter', { type });
  }

  async createSource(config: unknown): Promise<IConnector> {
    const adapter = this.sources.get(config.type);
    if (!adapter) {
      throw new Error(`No source adapter registered for type: ${config.type}`);
    }

    this.logger.debug('Creating source', { type: config.type, id: config.id });
    return await adapter.create(config);
  }

  async createSink(config: unknown): Promise<IConnector> {
    const adapter = this.sinks.get(config.type);
    if (!adapter) {
      throw new Error(`No sink adapter registered for type: ${config.type}`);
    }

    this.logger.debug('Creating sink', { type: config.type, id: config.id });
    return await adapter.create(config);
  }

  /**
   * Get list of registered adapter types
   */
  getRegisteredTypes(): { sources: string[]; sinks: string[] } {
    return {
      sources: Array.from(this.sources.keys()),
      sinks: Array.from(this.sinks.keys()),
    };
  }
}
