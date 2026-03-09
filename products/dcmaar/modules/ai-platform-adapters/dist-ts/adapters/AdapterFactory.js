/**
 * Agent Adapter Factory
 * Creates pluggable source and sink adapters
 * Following Desktop pattern from DesktopConnectorManager
 */
import { Logger } from '../utils/logger';
import { BridgeSourceAdapter } from './sources/BridgeSourceAdapter';
import { IngestSinkAdapter } from './sinks/IngestSinkAdapter';
import { ConsoleSinkAdapter } from './sinks/ConsoleSinkAdapter';
export class AgentAdapterFactory {
    sources = new Map();
    sinks = new Map();
    logger;
    constructor() {
        this.logger = new Logger('AgentAdapterFactory');
        this.registerDefaultAdapters();
    }
    /**
     * Register default adapters
     */
    registerDefaultAdapters() {
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
    registerSource(type, adapter) {
        this.sources.set(type, adapter);
        this.logger.debug('Registered source adapter', { type });
    }
    registerSink(type, adapter) {
        this.sinks.set(type, adapter);
        this.logger.debug('Registered sink adapter', { type });
    }
    async createSource(config) {
        const adapter = this.sources.get(config.type);
        if (!adapter) {
            throw new Error(`No source adapter registered for type: ${config.type}`);
        }
        this.logger.debug('Creating source', { type: config.type, id: config.id });
        return await adapter.create(config);
    }
    async createSink(config) {
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
    getRegisteredTypes() {
        return {
            sources: Array.from(this.sources.keys()),
            sinks: Array.from(this.sinks.keys()),
        };
    }
}
//# sourceMappingURL=AdapterFactory.js.map