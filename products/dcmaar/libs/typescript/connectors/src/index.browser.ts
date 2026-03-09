/**
 * @fileoverview Browser-safe connector exports - Restricted to core browser types
 * 
 * This entry point provides a minimal, tree-shake-friendly set of connectors
 * restricted to only browser-compatible types:
 * - Http (standard fetch-based HTTP)
 * - WebSocket (native browser WebSocket API)  
 * - Native (chrome.* extension APIs)
 * 
 * Note: FileSystemConnector uses Node.js fs module and is NOT available in browser context.
 * Use chrome.storage API directly for extension data storage.
 * 
 * Tree-shaking will eliminate all unreferenced utilities and Node.js-specific code.
 * 
 * @see index.ts for full connector list (Node.js + browser)
 */

// Core types & interfaces (minimalist)
export type { ConnectionOptions, IConnector, Event, ConnectionStatus } from './types';

// Restricted connector implementations - only 3 browser-safe types
export { HttpConnector } from './connectors/HttpConnector';
export { WebSocketConnector } from './connectors/WebSocketConnector';
export { NativeConnector } from './connectors/NativeConnector';

// Base abstractions
export { BaseConnector } from './BaseConnector';
export { ConnectorManager } from './ConnectorManager';

// Essential utilities (tree-shake friendly)
export { CircuitBreaker } from './resilience/CircuitBreaker';
export { RetryPolicy } from './resilience/RetryPolicy';
export { ConnectionPool } from './pooling/ConnectionPool';
export { MetricsCollector } from './monitoring/MetricsCollector';
export { RateLimiter } from './security/RateLimiter';

// Error types
export { ConnectorError, ConnectionError, TimeoutError } from './errors/ConnectorErrors';

/**
 * Factory function for restricted browser connectors
 * Supports only: http, websocket, native
 * Tree-shaken at build time for unused branches
 * Rejects: grpc, mqtt, nats, ipc, mtls, mqtts, filesystem (require Node.js or external libraries)
 */
import { ConnectionOptions, IConnector } from './types';
import { HttpConnector } from './connectors/HttpConnector';
import { WebSocketConnector } from './connectors/WebSocketConnector';
import { NativeConnector } from './connectors/NativeConnector';

export function createConnector<T extends ConnectionOptions>(
  config: T & { type: 'http' | 'websocket' | 'native' }
): IConnector<T> {
  switch (config.type) {
    case 'http':
      return new HttpConnector(config as any) as unknown as IConnector<T>;
    case 'websocket':
      return new WebSocketConnector(config as any) as unknown as IConnector<T>;
    case 'native':
      return new NativeConnector(config as any) as unknown as IConnector<T>;
    default:
      const _never: never = config.type;
      throw new Error(`Unsupported connector type in browser: ${_never}`);
  }
}
