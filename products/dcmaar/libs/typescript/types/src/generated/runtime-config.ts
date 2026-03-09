// AUTO-GENERATED FILE. DO NOT EDIT.
// Generated from contracts/json-schema/runtime_config.schema.json.
// To regenerate, run: pnpm run codegen:runtime-config

/**
 * Authoritative schema for bootstrap, source, sink, and runtime configuration shared across all runtimes (Rust agent, desktop, browser extension, mobile).
 */
export interface DcmaarRuntimeConfiguration {
  bootstrap: BootstrapConfig;
  runtime?: RuntimeConfig;
}
export interface BootstrapConfig {
  version?: string;
  source: SourceConfig;
  fallbackSources?: SourceConfig[];
  waitForSourceConnection?: boolean;
  sourceConnectionTimeoutMs?: number;
}
export interface SourceConfig {
  sourceId: string;
  sourceType: "ipc" | "http" | "websocket" | "custom";
  connectionOptions: ConnectionOptions;
  autoReconnect?: boolean;
  reconnectDelayMs?: number;
  maxReconnectAttempts?: number;
}
export interface ConnectionOptions {
  id: string;
  type: string;
  maxRetries?: number;
  /**
   * Timeout in milliseconds
   */
  timeout?: number;
  secure?: boolean;
  headers?: {
    [k: string]: string;
  };
  /**
   * Authentication payload (opaque to the schema)
   */
  auth?: {} | string | number | unknown[] | boolean | null;
  debug?: boolean;
}
export interface RuntimeConfig {
  version?: string;
  /**
   * @minItems 1
   */
  sinks: [SinkConfig, ...SinkConfig[]];
  extension?: ExtensionSettings;
  monitoring?: MonitoringSettings;
  security?: SecuritySettings;
}
export interface SinkConfig {
  sinkId: string;
  sinkType: "http" | "websocket" | "file" | "memory" | "ipc" | "custom";
  connectionOptions: ConnectionOptions;
  enabled?: boolean;
  batch?: SinkBatchConfig;
  retryPolicy?: SinkRetryPolicy;
}
export interface SinkBatchConfig {
  size?: number;
  flushIntervalMs?: number;
}
export interface SinkRetryPolicy {
  maxRetries?: number;
  backoffMs?: number;
  maxBackoffMs?: number;
}
export interface ExtensionSettings {
  captureEnabled?: boolean;
  captureTypes?: ("network" | "performance" | "user-interaction" | "custom")[];
  samplingRate?: number;
  redactionRules?: RedactionRule[];
}
export interface RedactionRule {
  pattern: string;
  replacement?: string;
}
export interface MonitoringSettings {
  enabled?: boolean;
  logLevel?: "debug" | "info" | "warn" | "error";
  metricsEnabled?: boolean;
  tracingEnabled?: boolean;
}
export interface SecuritySettings {
  tlsEnabled?: boolean;
  tlsVerify?: boolean;
  allowedDomains?: string[];
}
