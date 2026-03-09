/**
 * Adapter interfaces for pluggable connector architecture
 * Following Desktop pattern from DesktopConnectorManager
 */

import type { IConnector } from '@ghatana/dcmaar-connectors';

/**
 * Source adapter interface
 * Sources receive data and emit events to sinks
 */
export interface SourceAdapter {
  readonly type: string;
  create(config: unknown): Promise<IConnector>;
}

/**
 * Sink adapter interface
 * Sinks receive events from sources and process/forward them
 */
export interface SinkAdapter {
  readonly type: string;
  create(config: unknown): Promise<IConnector>;
}

/**
 * Adapter registry interface
 * Manages registration and creation of adapters
 */
export interface AdapterRegistry {
  registerSource(type: string, adapter: SourceAdapter): void;
  registerSink(type: string, adapter: SinkAdapter): void;
  createSource(config: unknown): Promise<IConnector>;
  createSink(config: unknown): Promise<IConnector>;
}
