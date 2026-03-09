/**
 * Agent Adapter Factory
 * Creates pluggable source and sink adapters
 * Following Desktop pattern from DesktopConnectorManager
 */
import type { IConnector } from '@ghatana/dcmaar-connectors';
import type { SourceAdapter, SinkAdapter, AdapterRegistry } from './types';
export declare class AgentAdapterFactory implements AdapterRegistry {
    private sources;
    private sinks;
    private logger;
    constructor();
    /**
     * Register default adapters
     */
    private registerDefaultAdapters;
    registerSource(type: string, adapter: SourceAdapter): void;
    registerSink(type: string, adapter: SinkAdapter): void;
    createSource(config: unknown): Promise<IConnector>;
    createSink(config: unknown): Promise<IConnector>;
    /**
     * Get list of registered adapter types
     */
    getRegisteredTypes(): {
        sources: string[];
        sinks: string[];
    };
}
//# sourceMappingURL=AdapterFactory.d.ts.map