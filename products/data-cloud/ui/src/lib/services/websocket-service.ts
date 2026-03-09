/**
 * WebSocket service for real-time execution updates.
 *
 * <p><b>Purpose</b><br>
 * Manages WebSocket connection for real-time workflow execution monitoring.
 * Handles reconnection, message parsing, and event dispatching.
 *
 * <p><b>Architecture</b><br>
 * - WebSocket connection management
 * - Automatic reconnection with backoff
 * - Message queuing for offline clients
 * - Event-driven architecture
 * - Heartbeat monitoring
 *
 * @doc.type service
 * @doc.purpose WebSocket real-time sync service
 * @doc.layer frontend
 * @doc.pattern Service
 */

import type { ExecutionUpdate } from '../../features/workflow/types/workflow.types';

/**
 * WebSocket service configuration.
 *
 * @doc.type interface
 */
export interface WebSocketConfig {
  url: string;
  reconnectInterval?: number;
  maxReconnectAttempts?: number;
  heartbeatInterval?: number;
}

/**
 * WebSocket event types.
 *
 * @doc.type enum
 */
export enum WebSocketEventType {
  CONNECTED = 'connected',
  DISCONNECTED = 'disconnected',
  MESSAGE = 'message',
  ERROR = 'error',
  RECONNECTING = 'reconnecting',
}

/**
 * WebSocket event.
 *
 * @doc.type interface
 */
export interface WebSocketEvent {
  type: WebSocketEventType;
  data?: unknown;
  error?: string;
}

/**
 * WebSocket service for real-time updates.
 *
 * @doc.type class
 */
export class WebSocketService {
  private ws: WebSocket | null = null;
  private config: Required<WebSocketConfig>;
  private reconnectAttempts = 0;
  private messageQueue: string[] = [];
  private listeners: Map<WebSocketEventType, Set<(event: WebSocketEvent) => void>> = new Map();
  private heartbeatTimer: NodeJS.Timeout | null = null;
  private reconnectTimer: NodeJS.Timeout | null = null;

  /**
   * Creates a new WebSocketService.
   *
   * @param config the service configuration
   */
  constructor(config: WebSocketConfig) {
    this.config = {
      reconnectInterval: 3000,
      maxReconnectAttempts: 10,
      heartbeatInterval: 30000,
      ...config,
    };

    this.listeners.set(WebSocketEventType.CONNECTED, new Set());
    this.listeners.set(WebSocketEventType.DISCONNECTED, new Set());
    this.listeners.set(WebSocketEventType.MESSAGE, new Set());
    this.listeners.set(WebSocketEventType.ERROR, new Set());
    this.listeners.set(WebSocketEventType.RECONNECTING, new Set());
  }

  /**
   * Connects to the WebSocket server.
   *
   * @returns promise that resolves when connected
   */
  async connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        this.ws = new WebSocket(this.config.url);

        this.ws.onopen = () => {
          this.reconnectAttempts = 0;
          this.emit(WebSocketEventType.CONNECTED);
          this.flushMessageQueue();
          this.startHeartbeat();
          resolve();
        };

        this.ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data) as ExecutionUpdate;
            this.emit(WebSocketEventType.MESSAGE, data);
          } catch (error) {
            console.error('Failed to parse WebSocket message:', error);
          }
        };

        this.ws.onerror = (event) => {
          const error = event instanceof Event ? 'WebSocket error' : String(event);
          this.emit(WebSocketEventType.ERROR, undefined, error);
          reject(new Error(error));
        };

        this.ws.onclose = () => {
          this.emit(WebSocketEventType.DISCONNECTED);
          this.stopHeartbeat();
          this.attemptReconnect();
        };
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        reject(new Error(message));
      }
    });
  }

  /**
   * Disconnects from the WebSocket server.
   */
  disconnect(): void {
    this.stopHeartbeat();
    this.clearReconnectTimer();

    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  /**
   * Sends a message to the server.
   *
   * @param data the message data
   */
  send(data: unknown): void {
    const message = JSON.stringify(data);

    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(message);
    } else {
      this.messageQueue.push(message);
    }
  }

  /**
   * Subscribes to WebSocket events.
   *
   * @param type the event type
   * @param listener the event listener
   * @returns unsubscribe function
   */
  on(type: WebSocketEventType, listener: (event: WebSocketEvent) => void): () => void {
    const listeners = this.listeners.get(type);
    if (listeners) {
      listeners.add(listener);
    }

    return () => {
      listeners?.delete(listener);
    };
  }

  /**
   * Checks if connected to the server.
   *
   * @returns true if connected
   */
  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  /**
   * Emits a WebSocket event.
   *
   * @param type the event type
   * @param data optional event data
   * @param error optional error message
   */
  private emit(type: WebSocketEventType, data?: unknown, error?: string): void {
    const listeners = this.listeners.get(type);
    if (listeners) {
      const event: WebSocketEvent = { type, data, error };
      listeners.forEach((listener) => listener(event));
    }
  }

  /**
   * Attempts to reconnect to the server.
   */
  private attemptReconnect(): void {
    if (this.reconnectAttempts >= this.config.maxReconnectAttempts) {
      this.emit(WebSocketEventType.ERROR, undefined, 'Max reconnect attempts reached');
      return;
    }

    this.reconnectAttempts++;
    this.emit(WebSocketEventType.RECONNECTING);

    this.reconnectTimer = setTimeout(() => {
      this.connect().catch((error) => {
        console.error('Reconnection failed:', error);
      });
    }, this.config.reconnectInterval * this.reconnectAttempts);
  }

  /**
   * Flushes queued messages.
   */
  private flushMessageQueue(): void {
    while (this.messageQueue.length > 0 && this.ws?.readyState === WebSocket.OPEN) {
      const message = this.messageQueue.shift();
      if (message) {
        this.ws.send(message);
      }
    }
  }

  /**
   * Starts heartbeat monitoring.
   */
  private startHeartbeat(): void {
    this.heartbeatTimer = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.send({ type: 'ping' });
      }
    }, this.config.heartbeatInterval);
  }

  /**
   * Stops heartbeat monitoring.
   */
  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  /**
   * Clears reconnect timer.
   */
  private clearReconnectTimer(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }
}
