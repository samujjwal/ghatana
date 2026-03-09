/**
 * Type definitions for Agent connector integration
 * Following Desktop pattern from DesktopConnectorManager.ts
 */

import type { ConnectionOptions } from '@ghatana/dcmaar-connectors';

export interface AgentConnectorConfig {
  version: string;
  sources: SourceConfig[];
  sinks: SinkConfig[];
  routing: RoutingConfig[];
  processors?: ProcessorConfig[];
}

export interface SourceConfig extends ConnectionOptions {
  id: string;
  type: string;
  enabled?: boolean;
  debug?: boolean;
  url?: string; // Connection URL (optional, for connectors that need it)
  port?: number; // Connection port (optional)
  host?: string; // Connection host (optional)
  metadata?: Record<string, any>;
}

export interface SinkConfig extends ConnectionOptions {
  id: string;
  type: string;
  enabled?: boolean;
  url?: string; // Connection URL (optional, for connectors that need it)
  port?: number; // Connection port (optional)
  host?: string; // Connection host (optional)
  metadata?: Record<string, any>;
}

export interface RoutingConfig {
  sourceId: string;
  sinkIds: string[];
  processors?: string[];
}

export interface ProcessorConfig {
  id: string;
  type: string;
  config: Record<string, any>;
}

/**
 * Message passed from Rust bridge to TypeScript connectors
 */
export interface BridgeMessage {
  id: string;
  eventType: string;
  payload: unknown;
  timestamp: number;
  metadata?: Record<string, any>;
}

/**
 * Response sent back to Rust from TypeScript
 */
export interface BridgeResponse {
  id: string;
  success: boolean;
  error?: string;
  data?: unknown;
}

/**
 * Status of connector manager
 */
export interface ConnectorStatus {
  initialized: boolean;
  sources: Array<{
    id: string;
    type: string;
    status: 'connected' | 'disconnected' | 'error';
    error?: string;
  }>;
  sinks: Array<{
    id: string;
    type: string;
    status: 'connected' | 'disconnected' | 'error';
    error?: string;
  }>;
}
