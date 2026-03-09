/**
 * Configuration for MetricsBridge.
 *
 * Provides centralized configuration for the native messaging bridge
 * between Phase 3G (TypeScript) and agent-desktop (Rust).
 */

export interface BridgeConfig {
  // Native messaging host name (must match agent-desktop manifest)
  nativeMessagingHost: string;

  // Message queue settings
  maxQueueSize: number;
  messageTimeoutMs: number;

  // Metrics filtering
  metricsChangeThreshold: number; // Percent change to trigger forward
  minForwardIntervalMs: number; // Minimum time between forwards

  // Retry settings
  maxRetries: number;
  retryBackoffMs: number;

  // Monitoring
  enableMetrics: boolean;
  enableLogging: boolean;
}

export const DEFAULT_BRIDGE_CONFIG: BridgeConfig = {
  nativeMessagingHost: "com.ghatana.guardian.desktop",
  maxQueueSize: 100,
  messageTimeoutMs: 5000,
  metricsChangeThreshold: 5, // Only forward if >5% change
  minForwardIntervalMs: 2000, // Max once every 2 seconds
  maxRetries: 3,
  retryBackoffMs: 1000,
  enableMetrics: true,
  enableLogging: true,
};

/**
 * Native messaging message types.
 */
export enum MessageType {
  PING = "PING",
  METRICS_UPDATE = "METRICS_UPDATE",
  USAGE_UPDATE = "USAGE_UPDATE",
  ERROR = "ERROR",
}

/**
 * Native messaging message envelope.
 */
export interface NativeMessage {
  type: MessageType;
  messageId: string;
  payload?: Record<string, unknown>;
  timestamp?: number;
}
