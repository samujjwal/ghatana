/**
 * WebSocket service for real-time workflow execution updates.
 *
 * <p><b>Purpose</b><br>
 * Manages WebSocket connections for real-time execution status updates, node progress tracking,
 * and error notifications. Handles connection lifecycle, reconnection logic, and message routing.
 *
 * <p><b>Features</b><br>
 * - WebSocket connection management
 * - Automatic reconnection with exponential backoff
 * - Message queuing for offline clients
 * - Heartbeat monitoring
 * - Event-driven architecture
 * - Connection state tracking
 * - Error handling and recovery
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { WebSocketService } from '@/lib/services/websocketService';
 *
 * const ws = new WebSocketService('ws://localhost:8080');
 * ws.connect();
 * ws.on('execution-update', (data) => {
 *   console.log('Execution updated:', data);
 * });
 * }</pre>
 *
 * @doc.type service
 * @doc.purpose WebSocket connection and message management
 * @doc.layer frontend
 */

export type WebSocketEventType =
  | 'execution-start'
  | 'execution-update'
  | 'node-start'
  | 'node-complete'
  | 'node-error'
  | 'execution-complete'
  | 'execution-error'
  | 'connection-open'
  | 'connection-close'
  | 'connection-error';

export interface WebSocketMessage {
  type: WebSocketEventType;
  executionId: string;
  nodeId?: string;
  timestamp: string;
  data?: Record<string, unknown>;
  error?: string;
}

export interface WebSocketOptions {
  /**
   * Maximum number of reconnection attempts
   */
  maxReconnectAttempts?: number;

  /**
   * Initial reconnection delay in milliseconds
   */
  initialReconnectDelay?: number;

  /**
   * Maximum reconnection delay in milliseconds
   */
  maxReconnectDelay?: number;

  /**
   * Heartbeat interval in milliseconds
   */
  heartbeatInterval?: number;

  /**
   * Message queue size
   */
  messageQueueSize?: number;
}

/**
 * WebSocket service for real-time updates.
 *
 * @doc.type class
 * @doc.purpose WebSocket connection management
 */
export class WebSocketService {
  private url: string;
  private ws: WebSocket | null = null;
  private listeners = new Map<WebSocketEventType, Set<(data: unknown) => void>>();
  private messageQueue: WebSocketMessage[] = [];
  private reconnectAttempts = 0;
  private reconnectDelay = 1000;
  private heartbeatTimer: NodeJS.Timeout | null = null;
  private isConnecting = false;
  private isConnected = false;

  private options: Required<WebSocketOptions> = {
    maxReconnectAttempts: 10,
    initialReconnectDelay: 1000,
    maxReconnectDelay: 30000,
    heartbeatInterval: 30000,
    messageQueueSize: 100,
  };

  /**
   * Creates a new WebSocket service.
   *
   * @param url the WebSocket URL
   * @param options connection options
   */
  constructor(url: string, options?: WebSocketOptions) {
    this.url = url;
    if (options) {
      this.options = { ...this.options, ...options };
    }
    this.initializeListeners();
  }

  /**
   * Initializes event listeners map.
   */
  private initializeListeners(): void {
    const eventTypes: WebSocketEventType[] = [
      'execution-start',
      'execution-update',
      'node-start',
      'node-complete',
      'node-error',
      'execution-complete',
      'execution-error',
      'connection-open',
      'connection-close',
      'connection-error',
    ];

    for (const type of eventTypes) {
      this.listeners.set(type, new Set());
    }
  }

  /**
   * Connects to the WebSocket server.
   *
   * @returns promise that resolves when connected
   */
  public connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.isConnected) {
        resolve();
        return;
      }

      if (this.isConnecting) {
        reject(new Error('Connection already in progress'));
        return;
      }

      this.isConnecting = true;

      try {
        this.ws = new WebSocket(this.url);

        this.ws.onopen = () => {
          this.isConnected = true;
          this.isConnecting = false;
          this.reconnectAttempts = 0;
          this.reconnectDelay = this.options.initialReconnectDelay;

          this.emit('connection-open', { timestamp: new Date().toISOString() });
          this.startHeartbeat();
          this.flushMessageQueue();

          resolve();
        };

        this.ws.onmessage = (event) => {
          this.handleMessage(event.data);
        };

        this.ws.onerror = (error) => {
          this.isConnecting = false;
          this.emit('connection-error', { error: error.toString() });
          reject(error);
        };

        this.ws.onclose = () => {
          this.isConnected = false;
          this.isConnecting = false;
          this.stopHeartbeat();
          this.emit('connection-close', { timestamp: new Date().toISOString() });
          this.attemptReconnect();
        };
      } catch (error) {
        this.isConnecting = false;
        reject(error);
      }
    });
  }

  /**
   * Disconnects from the WebSocket server.
   */
  public disconnect(): void {
    this.stopHeartbeat();
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.isConnected = false;
  }

  /**
   * Sends a message to the server.
   *
   * @param message the message to send
   */
  public send(message: WebSocketMessage): void {
    if (this.isConnected && this.ws) {
      this.ws.send(JSON.stringify(message));
    } else {
      // Queue message for later delivery
      if (this.messageQueue.length < this.options.messageQueueSize) {
        this.messageQueue.push(message);
      }
    }
  }

  /**
   * Registers an event listener.
   *
   * @param type the event type
   * @param callback the callback function
   */
  public on(type: WebSocketEventType, callback: (data: unknown) => void): void {
    const listeners = this.listeners.get(type);
    if (listeners) {
      listeners.add(callback);
    }
  }

  /**
   * Unregisters an event listener.
   *
   * @param type the event type
   * @param callback the callback function
   */
  public off(type: WebSocketEventType, callback: (data: unknown) => void): void {
    const listeners = this.listeners.get(type);
    if (listeners) {
      listeners.delete(callback);
    }
  }

  /**
   * Gets the connection status.
   *
   * @returns true if connected
   */
  public isConnectedStatus(): boolean {
    return this.isConnected;
  }

  /**
   * Gets the message queue size.
   *
   * @returns queue size
   */
  public getQueueSize(): number {
    return this.messageQueue.length;
  }

  /**
   * Handles incoming messages.
   *
   * @param data the message data
   */
  private handleMessage(data: string): void {
    try {
      const message = JSON.parse(data) as WebSocketMessage;
      this.emit(message.type, message);
    } catch (error) {
      console.error('Failed to parse WebSocket message:', error);
    }
  }

  /**
   * Emits an event to all registered listeners.
   *
   * @param type the event type
   * @param data the event data
   */
  private emit(type: WebSocketEventType, data: unknown): void {
    const listeners = this.listeners.get(type);
    if (listeners) {
      for (const callback of listeners) {
        try {
          callback(data);
        } catch (error) {
          console.error(`Error in ${type} listener:`, error);
        }
      }
    }
  }

  /**
   * Starts the heartbeat timer.
   */
  private startHeartbeat(): void {
    this.heartbeatTimer = setInterval(() => {
      if (this.isConnected && this.ws) {
        this.ws.send(JSON.stringify({ type: 'ping', timestamp: new Date().toISOString() }));
      }
    }, this.options.heartbeatInterval);
  }

  /**
   * Stops the heartbeat timer.
   */
  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  /**
   * Attempts to reconnect with exponential backoff.
   */
  private attemptReconnect(): void {
    if (this.reconnectAttempts >= this.options.maxReconnectAttempts) {
      console.error('Max reconnection attempts reached');
      return;
    }

    this.reconnectAttempts++;
    const delay = Math.min(
      this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1),
      this.options.maxReconnectDelay
    );

    console.log(`Attempting to reconnect in ${delay}ms (attempt ${this.reconnectAttempts})`);

    setTimeout(() => {
      this.connect().catch((error) => {
        console.error('Reconnection failed:', error);
      });
    }, delay);
  }

  /**
   * Flushes the message queue.
   */
  private flushMessageQueue(): void {
    while (this.messageQueue.length > 0 && this.isConnected && this.ws) {
      const message = this.messageQueue.shift();
      if (message) {
        this.ws.send(JSON.stringify(message));
      }
    }
  }
}

/**
 * Singleton instance of WebSocket service.
 */
let wsInstance: WebSocketService | null = null;

/**
 * Gets or creates the WebSocket service instance.
 *
 * @param url the WebSocket URL
 * @param options connection options
 * @returns WebSocket service instance
 */
export function getWebSocketService(url: string, options?: WebSocketOptions): WebSocketService {
  if (!wsInstance) {
    wsInstance = new WebSocketService(url, options);
  }
  return wsInstance;
}
