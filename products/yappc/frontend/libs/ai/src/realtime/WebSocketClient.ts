/**
 * Unified WebSocket Client
 * 
 * Production-grade WebSocket client that integrates with backend MessageRouter.
 * Handles authentication, message routing, reconnection, and error recovery.
 * 
 * @module realtime
 * @doc.type infrastructure
 * @doc.purpose Real-time communication
 */

// No external dependencies - standalone WebSocket client

/**
 * WebSocket message types matching backend MessageRouter
 */
export type MessageType =
  // Canvas collaboration
  | 'canvas.join'
  | 'canvas.leave'
  | 'canvas.update'
  | 'canvas.cursor'
  | 'canvas.selection'
  // Chat
  | 'chat.send'
  | 'chat.typing'
  | 'chat.read'
  | 'chat.reaction'
  // Notifications
  | 'notification.subscribe'
  | 'notification.unsubscribe'
  | 'notification.read'
  | 'notification.send'
  // System
  | 'ping'
  | 'pong'
  | 'error'
  | 'auth';

/**
 * WebSocket message structure
 */
export interface WebSocketMessage<T = unknown> {
  type: MessageType;
  payload: T;
  timestamp: number;
  messageId?: string;
  userId?: string;
  tenantId?: string;
}

/**
 * WebSocket connection state
 */
export type ConnectionState =
  | 'disconnected'
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'error';

/**
 * WebSocket client configuration
 */
export interface WebSocketClientConfig {
  /** WebSocket endpoint URL */
  endpoint: string;

  /** Authentication token (JWT) */
  authToken?: string;

  /** Tenant ID for multi-tenancy */
  tenantId?: string;

  /** User ID */
  userId?: string;

  /** Auto-reconnect on disconnect */
  autoReconnect?: boolean;

  /** Maximum reconnection attempts */
  maxReconnectAttempts?: number;

  /** Initial reconnection delay (ms) */
  reconnectDelay?: number;

  /** Reconnection backoff multiplier */
  reconnectBackoff?: number;

  /** Heartbeat interval (ms) */
  heartbeatInterval?: number;

  /** Connection timeout (ms) */
  connectionTimeout?: number;

  /** Enable debug logging */
  debug?: boolean;
}

/**
 * Message handler function
 */
export type MessageHandler<T = unknown> = (payload: T, message: WebSocketMessage<T>) => void;

/**
 * Connection state change handler
 */
export type StateChangeHandler = (state: ConnectionState, error?: Error) => void;

/**
 * Unified WebSocket Client
 * 
 * Features:
 * - JWT authentication with backend
 * - Message type routing to handlers
 * - Automatic reconnection with exponential backoff
 * - Message queuing during disconnect
 * - Heartbeat/ping-pong for connection health
 * - TypeScript type safety
 * - Error handling and recovery
 * 
 * @example
 * ```ts
 * const client = new WebSocketClient({
 *   endpoint: 'ws://localhost:8080/ws',
 *   authToken: 'jwt-token',
 *   tenantId: 'tenant-123',
 *   userId: 'user-456',
 * });
 * 
 * // Connect
 * await client.connect();
 * 
 * // Subscribe to canvas updates
 * client.on('canvas.update', (payload) => {
 *   console.log('Canvas updated:', payload);
 * });
 * 
 * // Send canvas update
 * client.send('canvas.update', {
 *   canvasId: 'canvas-123',
 *   changes: [...]
 * });
 * 
 * // Disconnect
 * await client.disconnect();
 * ```
 */
export class WebSocketClient {
  private config: Required<WebSocketClientConfig>;
  private ws?: WebSocket;
  private state: ConnectionState = 'disconnected';
  private reconnectAttempts = 0;
  private heartbeatTimer?: ReturnType<typeof setInterval>;
  private messageQueue: WebSocketMessage[] = [];
  private handlers = new Map<MessageType, Set<MessageHandler>>();
  private stateHandlers = new Set<StateChangeHandler>();
  private connectionPromise?: Promise<void>;

  constructor(config: WebSocketClientConfig) {
    this.config = {
      endpoint: config.endpoint,
      authToken: config.authToken || '',
      tenantId: config.tenantId || '',
      userId: config.userId || '',
      autoReconnect: config.autoReconnect ?? true,
      maxReconnectAttempts: config.maxReconnectAttempts ?? 10,
      reconnectDelay: config.reconnectDelay ?? 1000,
      reconnectBackoff: config.reconnectBackoff ?? 2,
      heartbeatInterval: config.heartbeatInterval ?? 30000,
      connectionTimeout: config.connectionTimeout ?? 10000,
      debug: config.debug ?? false,
    };
  }

  /**
   * Connect to WebSocket server
   */
  async connect(): Promise<void> {
    if (this.state === 'connected' || this.state === 'connecting') {
      return this.connectionPromise;
    }

    this.connectionPromise = new Promise((resolve, reject) => {
      try {
        this.setState('connecting');
        this.log('Connecting to WebSocket server...');

        // Build WebSocket URL
        const url = new URL(this.config.endpoint);

        // Create WebSocket connection
        this.ws = new WebSocket(url.toString());

        // Connection opened
        this.ws.onopen = () => {
          this.log('WebSocket connected');

          // Send authentication message
          this.sendAuth();

          this.setState('connected');
          this.reconnectAttempts = 0;
          this.startHeartbeat();
          this.flushMessageQueue();
          resolve();
        };

        // Message received
        this.ws.onmessage = (event) => {
          this.handleMessage(event.data);
        };

        // Connection error
        this.ws.onerror = (error) => {
          this.log('WebSocket error:', error);

          if (this.state === 'connecting') {
            const err = new Error('WebSocket connection failed');
            this.setState('error', err);
            reject(err);
          } else {
            this.setState('error', new Error('WebSocket error'));
          }
        };

        // Connection closed
        this.ws.onclose = (event) => {
          this.log('WebSocket closed:', event.code, event.reason);
          this.stopHeartbeat();

          const wasConnected = this.state === 'connected';
          this.setState('disconnected');

          // Auto-reconnect if enabled and was previously connected
          if (
            this.config.autoReconnect &&
            wasConnected &&
            this.reconnectAttempts < this.config.maxReconnectAttempts
          ) {
            this.reconnect();
          }
        };

        // Connection timeout
        setTimeout(() => {
          if (this.state === 'connecting') {
            this.ws?.close();
            const err = new Error('WebSocket connection timeout');
            this.setState('error', err);
            reject(err);
          }
        }, this.config.connectionTimeout);

      } catch (error) {
        const err = error instanceof Error ? error : new Error('Connection failed');
        this.setState('error', err);
        reject(err);
      }
    });

    return this.connectionPromise;
  }

  /**
   * Disconnect from WebSocket server
   */
  async disconnect(): Promise<void> {
    this.config.autoReconnect = false;
    this.stopHeartbeat();

    if (this.ws) {
      this.ws.close(1000, 'Client disconnect');
      this.ws = undefined;
    }

    this.setState('disconnected');
    this.handlers.clear();
    this.messageQueue = [];
    this.connectionPromise = undefined;
  }

  /**
   * Check if connected
   */
  isConnected(): boolean {
    return this.state === 'connected' && this.ws?.readyState === WebSocket.OPEN;
  }

  /**
   * Get current connection state
   */
  getState(): ConnectionState {
    return this.state;
  }

  /**
   * Send message to server
   */
  send<T = unknown>(type: MessageType, payload: T): void {
    const message: WebSocketMessage<T> = {
      type,
      payload,
      timestamp: Date.now(),
      messageId: this.generateMessageId(),
      userId: this.config.userId,
      tenantId: this.config.tenantId,
    };

    if (!this.isConnected()) {
      // Queue message if disconnected
      this.log('Queueing message (disconnected):', type);
      this.messageQueue.push(message);
      return;
    }

    try {
      this.ws!.send(JSON.stringify(message));
      this.log('Sent message:', type, payload);
    } catch (error) {
      this.log('Failed to send message:', error);
      this.messageQueue.push(message);
    }
  }

  /**
   * Register message handler
   */
  on<T = unknown>(type: MessageType, handler: MessageHandler<T>): () => void {
    if (!this.handlers.has(type)) {
      this.handlers.set(type, new Set());
    }

    this.handlers.get(type)!.add(handler as MessageHandler);

    // Return unsubscribe function
    return () => {
      this.handlers.get(type)?.delete(handler as MessageHandler);
      if (this.handlers.get(type)?.size === 0) {
        this.handlers.delete(type);
      }
    };
  }

  /**
   * Register connection state change handler
   */
  onStateChange(handler: StateChangeHandler): () => void {
    this.stateHandlers.add(handler);

    // Return unsubscribe function
    return () => {
      this.stateHandlers.delete(handler);
    };
  }

  /**
   * Send authentication message
   */
  private sendAuth(): void {
    if (!this.config.authToken) {
      this.log('No auth token provided, skipping authentication');
      return;
    }

    this.send('auth', {
      token: this.config.authToken,
      tenantId: this.config.tenantId,
      userId: this.config.userId,
    });
  }

  /**
   * Handle incoming message
   */
  private handleMessage(data: string): void {
    try {
      const message: WebSocketMessage = JSON.parse(data);
      this.log('Received message:', message.type, message.payload);

      // Handle system messages
      switch (message.type) {
        case 'ping':
          this.send('pong', { timestamp: Date.now() });
          return;

        case 'pong':
          // Heartbeat acknowledged
          return;

        case 'error':
          this.log('Server error:', message.payload);
          return;
      }

      // Dispatch to registered handlers
      const handlers = this.handlers.get(message.type);
      if (handlers) {
        handlers.forEach((handler) => {
          try {
            handler(message.payload, message);
          } catch (error) {
            this.log('Handler error:', error);
          }
        });
      }
    } catch (error) {
      this.log('Failed to parse message:', error);
    }
  }

  /**
   * Start heartbeat
   */
  private startHeartbeat(): void {
    this.heartbeatTimer = setInterval(() => {
      if (this.isConnected()) {
        this.send('ping', { timestamp: Date.now() });
      }
    }, this.config.heartbeatInterval);
  }

  /**
   * Stop heartbeat
   */
  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = undefined;
    }
  }

  /**
   * Reconnect to server
   */
  private async reconnect(): Promise<void> {
    this.setState('reconnecting');
    this.reconnectAttempts++;

    const delay = this.config.reconnectDelay *
      Math.pow(this.config.reconnectBackoff, this.reconnectAttempts - 1);

    this.log(`Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts}/${this.config.maxReconnectAttempts})...`);

    await new Promise((resolve) => setTimeout(resolve, delay));

    try {
      await this.connect();
      this.log('Reconnected successfully');
    } catch (error) {
      this.log('Reconnection failed:', error);

      if (this.reconnectAttempts < this.config.maxReconnectAttempts) {
        this.reconnect();
      } else {
        this.log('Max reconnect attempts reached');
        this.setState('error', new Error('Max reconnect attempts reached'));
      }
    }
  }

  /**
   * Flush queued messages
   */
  private flushMessageQueue(): void {
    this.log(`Flushing ${this.messageQueue.length} queued messages...`);

    while (this.messageQueue.length > 0 && this.isConnected()) {
      const message = this.messageQueue.shift()!;
      try {
        this.ws!.send(JSON.stringify(message));
      } catch (error) {
        this.log('Failed to flush message:', error);
        this.messageQueue.unshift(message);
        break;
      }
    }
  }

  /**
   * Set connection state
   */
  private setState(state: ConnectionState, error?: Error): void {
    if (this.state === state) return;

    this.state = state;
    this.log('State changed:', state);

    // Notify state change handlers
    this.stateHandlers.forEach((handler) => {
      try {
        handler(state, error);
      } catch (err) {
        this.log('State handler error:', err);
      }
    });
  }

  /**
   * Generate unique message ID
   */
  private generateMessageId(): string {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Debug logging
   */
  private log(...args: unknown[]): void {
    if (this.config.debug) {
      console.log('[WebSocketClient]', ...args);
    }
  }
}

/**
 * Create WebSocket client
 */
export function createWebSocketClient(
  endpoint: string,
  options?: Partial<WebSocketClientConfig>
): WebSocketClient {
  return new WebSocketClient({
    endpoint,
    ...options,
  });
}

/**
 * Singleton WebSocket client instance
 */
let globalClient: WebSocketClient | null = null;

/**
 * Get or create global WebSocket client
 */
export function getWebSocketClient(
  endpoint?: string,
  options?: Partial<WebSocketClientConfig>
): WebSocketClient {
  if (!globalClient) {
    if (!endpoint) {
      throw new Error('WebSocket endpoint required for first initialization');
    }
    globalClient = createWebSocketClient(endpoint, options);
  }
  return globalClient;
}

/**
 * Reset global WebSocket client (for testing)
 */
export function resetWebSocketClient(): void {
  if (globalClient) {
    globalClient.disconnect();
    globalClient = null;
  }
}
