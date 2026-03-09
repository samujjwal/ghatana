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
import { type IConnector } from '@ghatana/dcmaar-connectors';
import type { AgentConnectorConfig, SourceConfig, SinkConfig, BridgeMessage, ConnectorStatus } from './types';
export declare class AgentConnectorManager extends EventEmitter {
    private connectorManager;
    private config;
    private initialized;
    private logger;
    private sourceMap;
    private sinkMap;
    constructor();
    /**
     * Initialize connector manager with configuration
     * Follows Desktop pattern from DesktopConnectorManager.initialize()
     */
    initialize(config: AgentConnectorConfig): Promise<void>;
    /**
     * Add source connector
     */
    addSource(config: SourceConfig): Promise<IConnector>;
    /**
     * Add sink connector
     */
    addSink(config: SinkConfig): Promise<IConnector>;
    /**
     * Handle message from Rust bridge
     * Converts BridgeMessage to Event and routes through connector manager
     */
    handleBridgeMessage(msg: BridgeMessage): Promise<void>;
    /**
     * Remove source connector
     */
    removeSource(sourceId: string): Promise<void>;
    /**
     * Remove sink connector
     */
    removeSink(sinkId: string): Promise<void>;
    /**
     * Shutdown all connectors
     */
    shutdown(): Promise<void>;
    /**
     * Get status of all connectors
     */
    getStatus(): ConnectorStatus;
    /**
     * Get configuration
     */
    getConfig(): AgentConnectorConfig | null;
    /**
     * Check if initialized
     */
    isInitialized(): boolean;
}
//# sourceMappingURL=AgentConnectorManager.d.ts.map