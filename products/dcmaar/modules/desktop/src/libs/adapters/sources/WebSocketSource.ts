/**
 * WebSocket Source Adapter
 *
 * Receives telemetry data from agent or extension via WebSocket connection.
 * For now, delegates to MockSource for testing. Real WebSocket implementation pending.
 */

import type { TelemetrySource, TelemetrySnapshot, HealthStatus, SourceContext } from '../types';
import { MockSource, type MockSourceOptions } from './MockSource';

export interface WebSocketSourceOptions extends MockSourceOptions {
  /** WebSocket URL (ws:// or wss://) */
  url?: string;
  /** Use Tauri WebSocket server (for extension) */
  useTauriServer?: boolean;
  /** Tauri server port */
  tauriServerPort?: number;
  /** Reconnect on disconnect */
  reconnect?: boolean;
  /** Reconnect interval in ms */
  reconnectInterval?: number;
  /** Max reconnect attempts (0 = infinite) */
  maxReconnectAttempts?: number;
  /** Workspace ID */
  workspaceId?: string;
  /** Connection timeout in ms */
  connectionTimeout?: number;
  /** Authentication token */
  authToken?: string;
}

/**
 * WebSocket Source implementation
 *
 * TODO: Full WebSocket implementation with:
 * - Direct WebSocket connection to agent
 * - Tauri WebSocket server for extension
 * - Auto-reconnect logic
 * - Message parsing and validation
 *
 * For now, uses MockSource to enable end-to-end testing
 */
export class WebSocketSource implements TelemetrySource {
  readonly kind = 'custom' as const;
  private mockSource: MockSource;
  private options: WebSocketSourceOptions;

  constructor(options: WebSocketSourceOptions = {}) {
    this.options = options;
    // Delegate to mock source for now
    this.mockSource = new MockSource({
      refreshIntervalMs: options.refreshIntervalMs || 5000,
    });
  }

  async init(ctx: SourceContext): Promise<void> {
    ctx.logger.info('WebSocketSource initialized (using mock)', {
      url: this.options.url,
      useTauriServer: this.options.useTauriServer,
    });
    return this.mockSource.init(ctx);
  }

  async getInitialSnapshot(): Promise<TelemetrySnapshot> {
    return this.mockSource.getInitialSnapshot();
  }

  async subscribe(emit: (update: TelemetrySnapshot) => void): Promise<() => void> {
    return this.mockSource.subscribe(emit);
  }

  async healthCheck(): Promise<HealthStatus> {
    const mockHealth = await this.mockSource.healthCheck();
    return {
      ...mockHealth,
      details: {
        ...mockHealth.details,
        websocketConnected: false, // TODO: actual WebSocket status
        mode: 'mock',
      },
    };
  }

  async close(): Promise<void> {
    return this.mockSource.close();
  }
}

/**
 * Factory function for creating WebSocket sources
 */
export const createWebSocketSource = (options?: WebSocketSourceOptions): TelemetrySource => {
  return new WebSocketSource(options);
};
