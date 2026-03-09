/**
 * WebSocket Sink Adapter
 *
 * Sends commands to agent or extension via WebSocket connection.
 * For now, delegates to MockSink for testing. Real WebSocket implementation pending.
 */

import type { ControlSink, ControlCommand, SinkAck, HealthStatus, SinkContext } from '../types';
import { MockSink, type MockSinkOptions } from './MockSink';

export interface WebSocketSinkOptions extends MockSinkOptions {
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
  /** Connection timeout in ms */
  connectionTimeout?: number;
  /** Authentication token */
  authToken?: string;
}

/**
 * WebSocket Sink implementation
 *
 * TODO: Full WebSocket implementation with:
 * - Direct WebSocket connection to agent
 * - Tauri WebSocket server for extension
 * - Command queueing and delivery confirmation
 * - Auto-reconnect logic
 *
 * For now, uses MockSink to enable end-to-end testing
 */
export class WebSocketSink implements ControlSink {
  readonly kind = 'custom' as const;
  private mockSink: MockSink;
  private options: WebSocketSinkOptions;

  constructor(options: WebSocketSinkOptions = {}) {
    this.options = options;
    // Delegate to mock sink for now
    this.mockSink = new MockSink(options);
  }

  async init(ctx: SinkContext): Promise<void> {
    ctx.logger.info('WebSocketSink initialized (using mock)', {
      url: this.options.url,
      useTauriServer: this.options.useTauriServer,
    });
    return this.mockSink.init(ctx);
  }

  async enqueue(command: ControlCommand): Promise<void> {
    return this.mockSink.enqueue(command);
  }

  async flush(): Promise<SinkAck[]> {
    return this.mockSink.flush();
  }

  async healthCheck(): Promise<HealthStatus> {
    const mockHealth = await this.mockSink.healthCheck();
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
    return this.mockSink.close();
  }
}

/**
 * Factory function for creating WebSocket sinks
 */
export const createWebSocketSink = (options?: WebSocketSinkOptions): ControlSink => {
  return new WebSocketSink(options);
};
