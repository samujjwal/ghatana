// Core exports
export * from './types';
export * from './BaseConnector';
export * from './ConnectorManager';

// Connector implementations
export * from './connectors/HttpConnector';
export * from './connectors/WebSocketConnector';
export * from './connectors/GrpcConnector';
export * from './connectors/MqttConnector';
export * from './connectors/MqttsConnector';
export * from './connectors/NatsConnector';
export * from './connectors/FileSystemConnector';
export * from './connectors/IpcConnector';
export * from './connectors/NativeConnector';
export * from './connectors/MtlsConnector';

// Monitoring & Observability
export * from './monitoring/MetricsCollector';
export * from './monitoring/HealthChecker';
export * from './observability/Telemetry';

// Resilience
export * from './resilience/CircuitBreaker';
export * from './resilience/RetryPolicy';
export * from './resilience/DeadLetterQueue';

// Pooling & Batching
export * from './pooling/ConnectionPool';
export * from './batching/BatchProcessor';

// Security
export * from './security/RateLimiter';
export * from './utils/security';
export * from './utils/validation';

// Error Handling
export * from './errors/ConnectorErrors';

// Processors (v1.1.0)
export * from './processors/types';
export * from './processors/ProcessorRegistry';
export * from './processors/built-in';

// Storage (v1.1.0)
export * from './storage/StorageProvider';
export * from './storage/MemoryStorageProvider';

// IPC (v1.1.0)
export * from './ipc/IpcChannel';

// Factory function for creating connectors
import { ConnectionOptions, IConnector } from './types';
import { HttpConnector } from './connectors/HttpConnector';
import { WebSocketConnector } from './connectors/WebSocketConnector';
import { GrpcConnector } from './connectors/GrpcConnector';
import { MqttConnector } from './connectors/MqttConnector';
import { MqttsConnector } from './connectors/MqttsConnector';
import { NatsConnector } from './connectors/NatsConnector';
import { FileSystemConnector } from './connectors/FileSystemConnector';
import { IpcConnector } from './connectors/IpcConnector';
import { NativeConnector } from './connectors/NativeConnector';
import { MtlsConnector } from './connectors/MtlsConnector';

export function createConnector<T extends ConnectionOptions>(config: T): IConnector<T> {
  switch (config.type) {
    case 'http':
      return new HttpConnector(config as any) as unknown as IConnector<T>;
    case 'websocket':
      return new WebSocketConnector(config as any) as unknown as IConnector<T>;
    case 'grpc':
      return new GrpcConnector(config as any) as unknown as IConnector<T>;
    case 'mqtt':
      return new MqttConnector(config as any) as unknown as IConnector<T>;
    case 'mqtts':
      return new MqttsConnector(config as any) as unknown as IConnector<T>;
    case 'nats':
      return new NatsConnector(config as any) as unknown as IConnector<T>;
    case 'filesystem':
      return new FileSystemConnector(config as any) as unknown as IConnector<T>;
    case 'ipc':
      return new IpcConnector(config as any) as unknown as IConnector<T>;
    case 'native':
      return new NativeConnector(config as any) as unknown as IConnector<T>;
    case 'mtls':
      return new MtlsConnector(config as any) as unknown as IConnector<T>;
    default:
      throw new Error(`Unsupported connector type: ${config.type}`);
  }
}

// Re-export common types for convenience
export type { Event, ConnectionOptions, IConnector, ConnectionStatus } from './types';
