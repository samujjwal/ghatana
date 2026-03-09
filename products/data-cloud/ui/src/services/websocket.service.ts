/**
 * WebSocket service for real-time workflow execution monitoring.
 *
 * <p><b>Purpose</b><br>
 * Manages WebSocket connections with automatic reconnection, heartbeat monitoring,
 * and message queuing for offline scenarios.
 *
 * <p><b>Features</b><br>
 * - Automatic reconnection with exponential backoff
 * - Heartbeat monitoring (30s interval)
 * - Message queuing for offline clients
 * - Event-driven architecture
 * - Comprehensive error handling
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const ws = new WebSocketService('ws://localhost:8080/ws');
 * ws.on('execution:start', (data) => console.log('Started', data));
 * ws.send('execute', { workflowId: '123' });
 * ```
 *
 * @doc.type service
 * @doc.purpose Real-time WebSocket management
 * @doc.layer frontend
 * @doc.pattern Service
 */

export type WebSocketMessage = {
  type: string;
  payload: Record<string, any>;
  timestamp: number;
};

export type WebSocketEventHandler = (data: unknown) => void;

export class WebSocketService {
  private ws: WebSocket | null = null;
  private url: string;
  private messageQueue: WebSocketMessage[] = [];
  private eventHandlers: Map<string, Set<WebSocketEventHandler>> = new Map();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 1000;
  private heartbeatInterval: NodeJS.Timeout | null = null;
  private isConnecting = false;

  constructor(url: string) {
    this.url = url;
  }

  /**
   * Connects to WebSocket server.
   */
  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.isConnecting) {
        reject(new Error('Already connecting'));
        return;
      }

      this.isConnecting = true;

      try {
        this.ws = new WebSocket(this.url);

        this.ws.onopen = () => {
          console.log('WebSocket connected');
          this.isConnecting = false;
          this.reconnectAttempts = 0;
          this.startHeartbeat();
          this.flushMessageQueue();
          resolve();
        };

        this.ws.onmessage = (event) => {
          try {
            const message = JSON.parse(event.data) as WebSocketMessage;
            this.emit(message.type, message.payload);
          } catch (error) {
            console.error('Failed to parse WebSocket message', error);
          }
        };

        this.ws.onerror = (error) => {
          console.error('WebSocket error', error);
          this.isConnecting = false;
          reject(error);
        };

        this.ws.onclose = () => {
          console.log('WebSocket closed');
          this.isConnecting = false;
          this.stopHeartbeat();
          this.attemptReconnect();
        };
      } catch (error) {
        this.isConnecting = false;
        reject(error);
      }
    });
  }

  /**
   * Sends message to server.
   */
  send(type: string, payload: Record<string, any>): void {
    const message: WebSocketMessage = {
      type,
      payload,
      timestamp: Date.now(),
    };

    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    } else {
      this.messageQueue.push(message);
    }
  }

  /**
   * Registers event handler.
   */
  on(event: string, handler: WebSocketEventHandler): void {
    if (!this.eventHandlers.has(event)) {
      this.eventHandlers.set(event, new Set());
    }
    this.eventHandlers.get(event)!.add(handler);
  }

  /**
   * Unregisters event handler.
   */
  off(event: string, handler: WebSocketEventHandler): void {
    this.eventHandlers.get(event)?.delete(handler);
  }

  /**
   * Emits event to all handlers.
   */
  private emit(event: string, data: unknown): void {
    this.eventHandlers.get(event)?.forEach((handler) => handler(data));
  }

  /**
   * Flushes queued messages.
   */
  private flushMessageQueue(): void {
    while (this.messageQueue.length > 0 && this.ws?.readyState === WebSocket.OPEN) {
      const message = this.messageQueue.shift();
      if (message) {
        this.ws.send(JSON.stringify(message));
      }
    }
  }

  /**
   * Starts heartbeat monitoring.
   */
  private startHeartbeat(): void {
    this.heartbeatInterval = setInterval(() => {
      this.send('ping', {});
    }, 30000);
  }

  /**
   * Stops heartbeat monitoring.
   */
  private stopHeartbeat(): void {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }
  }

  /**
   * Attempts to reconnect with exponential backoff.
   */
  private attemptReconnect(): void {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1);
      console.log(`Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`);

      setTimeout(() => {
        this.connect().catch((error) => {
          console.error('Reconnection failed', error);
        });
      }, delay);
    } else {
      console.error('Max reconnection attempts reached');
      this.emit('error', new Error('Connection failed'));
    }
  }

  /**
   * Disconnects from server.
   */
  disconnect(): void {
    this.stopHeartbeat();
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  /**
   * Gets connection status.
   */
  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  /**
   * Gets queue size.
   */
  getQueueSize(): number {
    return this.messageQueue.length;
  }
}
