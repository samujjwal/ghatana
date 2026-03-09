/**
 * Desktop Adapter System - Main exports
 * Pluggable telemetry sources and control sinks for standalone operation.
 */

// Core types
export * from './types';

// Infrastructure
export { createLogger, type LoggerConfig, type StructuredLogger } from './logger';
export { createTracer, type TracerConfig, type SimpleTracer } from './tracer';
export { createQueue, type QueueConfig, type IndexedDBQueue } from './queue';
export { createKeyring, type KeyringConfig, type Keyring } from './keyring';
export { createAuditChain, type AuditChainConfig, type AuditChain, type AuditEntry } from './auditChain';

// Sources
export { createMockSource, MockSource, type MockSourceOptions } from './sources/MockSource';
export { createLocalFileSource, LocalFileSource, type LocalFileSourceOptions } from './sources/LocalFileSource';
export { createLoopbackDaemonSource, LoopbackDaemonSource, type LoopbackDaemonSourceOptions } from './sources/LoopbackDaemonSource';
export { createHttpSource, HttpSource, type HttpSourceOptions } from './sources/HttpSource';
export { createGrpcSource, GrpcSource, type GrpcSourceOptions } from './sources/GrpcSource';

// Sinks
export { createMockSink, MockSink, type MockSinkOptions } from './sinks/MockSink';
export { createLocalFileSink, LocalFileSink, type LocalFileSinkOptions } from './sinks/LocalFileSink';
export { createLoopbackDaemonSink, LoopbackDaemonSink, type LoopbackDaemonSinkOptions } from './sinks/LoopbackDaemonSink';
export { createHttpSink, HttpSink, type HttpSinkOptions } from './sinks/HttpSink';
export { createGrpcSink, GrpcSink, type GrpcSinkOptions } from './sinks/GrpcSink';

// Advanced features
export { createMultiSinkRouter, MultiSinkRouter, type MultiSinkRouterOptions, type SinkRoute } from './multiSinkRouter';
export { createSchemaMigrator, SchemaMigrator, type Migration, type MigrationContext } from './schemaMigration';
export { createPolicyEngine, PolicyEngine, type PolicyEngineConfig, type PolicyRule, type PolicyContext, type PolicyResult } from './policyEngine';

// Crypto & Factory
export { createCryptoService, CryptoService, type EncryptedData } from './crypto';
export { createAdapterFactory, adapterFactory, AdapterFactory, type SourceFactory, type SinkFactory } from './adapterFactory';

// Manager
export { createAdapterManager, AdapterManager, type AdapterManagerConfig } from './adapterManager';
