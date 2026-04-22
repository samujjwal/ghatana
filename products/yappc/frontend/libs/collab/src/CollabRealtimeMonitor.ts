/**
 * @fileoverview Collaboration realtime health monitor.
 *
 * Uses `@ghatana/realtime` `WebSocketClient` to maintain a separate
 * health/telemetry channel alongside the Yjs CRDT sync channel managed by
 * `y-websocket`. This allows the collab layer to emit structured platform
 * events and reconnect metrics without coupling those concerns to the
 * Yjs-specific provider.
 *
 * Separation of concerns:
 * - `y-websocket` WebsocketProvider: Yjs CRDT state sync (document updates,
 *   awareness). This is Yjs-specific and cannot be replaced.
 * - `@ghatana/realtime` WebSocketClient: Generic transport for health checks,
 *   structured telemetry messages, and connection quality signals.
 *
 * Usage:
 * ```ts
 * const monitor = new CollabRealtimeMonitor({
 *   healthUrl: 'ws://collab-server/health',
 *   sessionId: 'session-123',
 *   documentId: 'doc-xyz',
 *   onHealthChange: (healthy) => console.log('collab health', healthy),
 *   onMetric: (metric) => metricsService.record(metric),
 * });
 * monitor.start();
 * // ... later:
 * monitor.stop();
 * ```
 *
 * @doc.type class
 * @doc.purpose Generic transport health monitor for collab sessions using @ghatana/realtime
 * @doc.layer product
 * @doc.pattern Adapter
 */

import { WebSocketClient } from '@ghatana/realtime';
import type { WebSocketConnectionState } from '@ghatana/realtime';

// ============================================================================
// Types
// ============================================================================

/** Health telemetry metric emitted by the monitor. */
export interface CollabHealthMetric {
  readonly sessionId: string;
  readonly documentId: string;
  readonly timestamp: number;
  readonly type: 'connected' | 'disconnected' | 'latency' | 'reconnect';
  readonly value?: number;
}

/** Configuration for CollabRealtimeMonitor. */
export interface CollabRealtimeMonitorConfig {
  /**
   * URL for the generic health/telemetry WebSocket endpoint.
   * This is SEPARATE from the Yjs CRDT sync URL used by y-websocket.
   */
  readonly healthUrl: string;
  /** Platform-level session identifier. */
  readonly sessionId: string;
  /** Builder document identifier. */
  readonly documentId: string;
  /**
   * Called when the health channel connection state changes.
   * `true` = channel is healthy, `false` = channel is down.
   */
  readonly onHealthChange?: (healthy: boolean) => void;
  /** Called whenever a health metric is produced. */
  readonly onMetric?: (metric: CollabHealthMetric) => void;
  /** Heartbeat interval in ms. Defaults to 30,000. */
  readonly heartbeatIntervalMs?: number;
}

/** Pong message payload shape returned by the server. */
interface PongPayload {
  sentAt: number;
}

// ============================================================================
// Monitor
// ============================================================================

/**
 * Maintains a health/telemetry WebSocket channel for a collaboration session
 * using `@ghatana/realtime`'s `WebSocketClient`.
 *
 * This channel operates independently of the Yjs CRDT sync channel and is
 * used solely for observability, health checks, and connection quality
 * reporting. The Yjs `WebsocketProvider` from `y-websocket` remains
 * responsible for all document CRDT state.
 */
export class CollabRealtimeMonitor {
  private readonly config: CollabRealtimeMonitorConfig;
  private readonly client: WebSocketClient;
  private healthy = false;
  private unsubscribePong: (() => void) | null = null;
  private unsubscribeState: (() => void) | null = null;

  constructor(config: CollabRealtimeMonitorConfig) {
    this.config = config;
    this.client = new WebSocketClient({
      url: config.healthUrl,
      maxReconnectAttempts: Infinity,
      heartbeatInterval: config.heartbeatIntervalMs ?? 30_000,
    });
  }

  /** Start the health channel. Idempotent. */
  start(): void {
    // Subscribe to pong responses for round-trip latency measurement.
    this.unsubscribePong = this.client.subscribe<PongPayload>(
      'collab.health.pong',
      (message) => {
        if (typeof message.payload?.sentAt === 'number') {
          const latencyMs = Date.now() - message.payload.sentAt;
          this.emitMetric('latency', latencyMs);
        }
      },
    );

    // Monitor connection state changes.
    this.unsubscribeState = this.client.onStateChange(this.handleStateChange);

    // Connect (non-blocking — connection errors are handled via state changes).
    void this.client.connect().catch(() => {
      // Connection failure is surfaced through onStateChange transitions.
    });
  }

  /** Stop the health channel. Idempotent. */
  stop(): void {
    this.unsubscribePong?.();
    this.unsubscribePong = null;
    this.unsubscribeState?.();
    this.unsubscribeState = null;
    this.client.disconnect();
    this.healthy = false;
  }

  /** Returns `true` when the health channel is currently connected. */
  isHealthy(): boolean {
    return this.healthy;
  }

  // --------------------------------------------------------------------------
  // State handler
  // --------------------------------------------------------------------------

  private readonly handleStateChange = (state: WebSocketConnectionState): void => {
    const wasHealthy = this.healthy;

    if (state.status === 'connected') {
      this.healthy = true;
      if (!wasHealthy) {
        this.emitMetric('connected');
        this.config.onHealthChange?.(true);
      }
    } else if (state.status === 'disconnected') {
      if (this.healthy) {
        this.healthy = false;
        this.emitMetric('disconnected');
        this.config.onHealthChange?.(false);
      }
    } else if (state.status === 'reconnecting') {
      if (this.healthy) {
        this.healthy = false;
        this.config.onHealthChange?.(false);
      }
      this.emitMetric('reconnect', state.reconnectAttempt);
    }
  };

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private emitMetric(type: CollabHealthMetric['type'], value?: number): void {
    this.config.onMetric?.({
      sessionId: this.config.sessionId,
      documentId: this.config.documentId,
      timestamp: Date.now(),
      type,
      value,
    });
  }
}

