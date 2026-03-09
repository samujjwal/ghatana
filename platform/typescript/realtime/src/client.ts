/**
 * WebSocket Client for @ghatana/realtime
 *
 * Production-grade WebSocket client with automatic reconnection, error handling,
 * and connection state management. Provides reliable real-time communication
 * with proper error recovery and user notifications.
 *
 * @doc.type class
 * @doc.purpose Production WebSocket client with auto-reconnect and state management
 * @doc.layer platform
 * @doc.pattern Client
 */

/**
 * WebSocket configuration options
 */
export interface WebSocketConfig {
  url: string;
  protocols?: string[];
  maxReconnectAttempts?: number;
  reconnectDelay?: number;
  heartbeatInterval?: number;
  connectionTimeout?: number;
}

/**
 * WebSocket message structure
 */
export interface WebSocketMessage<T = unknown> {
  type: string;
  payload: T;
  timestamp?: number;
  id?: string;
}

/**
 * WebSocket connection state
 */
export interface WebSocketConnectionState {
  status:
    | 'connecting'
    | 'connected'
    | 'disconnected'
    | 'reconnecting'
    | 'failed';
  reconnectAttempt: number;
  lastConnected?: Date;
  lastError?: Error;
}

/**
 * WebSocket event handler type
 */
export type WebSocketEventHandler<T = unknown> = (
  message: WebSocketMessage<T>
) => void;

/**
 * WebSocket Client with automatic reconnection and state management.
 *
 * <p><b>Purpose</b><br>
 * Provides production-grade WebSocket communication with:
 * - Automatic reconnection with exponential backoff
 * - Connection state tracking
 * - Message queueing during disconnections
 * - Type-safe message sending/receiving
 * - Heartbeat for connection monitoring
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const client = new WebSocketClient({
 *   url: 'wss://api.example.com/ws',
 *   maxReconnectAttempts: 5,
 *   reconnectDelay: 1000
 * });
 *
 * await client.connect();
 *
 * client.subscribe('chat:message', (msg) => {
 *   console.log('Received:', msg.payload);
 * });
 *
 * client.send({ type: 'chat:send', payload: { text: 'Hello' } });
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Production WebSocket client with auto-reconnect
 * @doc.layer platform
 * @doc.pattern Client
 */
export class WebSocketClient {
  private ws: WebSocket | null = null;
  private config: Required<WebSocketConfig>;
  private eventHandlers: Map<string, Set<WebSocketEventHandler>> = new Map();
  private connectionState: WebSocketConnectionState = {
    status: 'disconnected',
    reconnectAttempt: 0,
  };
  private heartbeatTimer: NodeJS.Timeout | null = null;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private connectionTimeout: NodeJS.Timeout | null = null;
  private messageQueue: WebSocketMessage[] = [];

  // State change listeners
  private stateChangeListeners: Set<(state: WebSocketConnectionState) => void> =
    new Set();

  /**
   * Create a new WebSocket client
   *
   * @param config - WebSocket configuration
   */
  constructor(config: WebSocketConfig) {
    // Respect explicit config first. In development, avoid aggressive reconnects
    // unless the environment opt-in VITE_ENABLE_REAL_WS is set to 'true'. This
    // prevents the client from repeatedly trying to connect when MSW (mocks)
    // are active and no real WebSocket server is intended to run.
    const g = globalThis as unknown as Record<string, unknown>;
    const envEnableRealWs =
      typeof g['importMetaEnv'] !== 'undefined'
        ? (g['importMetaEnv'] as Record<string, string> | undefined)?.VITE_ENABLE_REAL_WS === 'true'
        : (typeof g['process'] !== 'undefined' && (g['process'] as { env?: Record<string, string> } | undefined)?.env)
        ? (g['process'] as { env: Record<string, string> }).env?.VITE_ENABLE_REAL_WS === 'true'
        : g['__VITE_ENABLE_REAL_WS__'] === true;

    this.config = {
      maxReconnectAttempts: envEnableRealWs ? 5 : 0,
      reconnectDelay: 1000,
      heartbeatInterval: 30000,
      connectionTimeout: 10000,
      protocols: [],
      ...config,
    };
  }

  /**
   * Connect to WebSocket server
   *
   * @returns Promise that resolves when connected
   */
  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        this.updateConnectionState({
          status: 'connecting',
          reconnectAttempt: 0,
        });

        // Create WebSocket connection
        this.ws = new WebSocket(this.config.url, this.config.protocols);

        // Set connection timeout
        this.connectionTimeout = setTimeout(() => {
          this.handleConnectionTimeout();
          reject(new Error('Connection timeout'));
        }, this.config.connectionTimeout);

        // Setup event listeners
        this.ws.addEventListener('open', (event) => {
          this.handleOpen(event);
          resolve();
        });

        this.ws.addEventListener('message', this.handleMessage.bind(this));
        this.ws.addEventListener('close', this.handleClose.bind(this));
        this.ws.addEventListener('error', (event) => {
          this.handleError(event);
          reject(new Error('Connection failed'));
        });
      } catch (error) {
        this.handleError(error as Error);
        reject(error);
      }
    });
  }

  /**
   * Disconnect from WebSocket server
   */
  disconnect(): void {
    this.clearTimers();

    if (this.ws) {
      this.ws.close(1000, 'Client disconnect');
      this.ws = null;
    }

    this.updateConnectionState({ status: 'disconnected', reconnectAttempt: 0 });
  }

  /**
   * Send message to server
   *
   * @param message - Message to send
   * @returns true if sent immediately, false if queued
   */
  send<T>(message: WebSocketMessage<T>): boolean {
    if (this.connectionState.status !== 'connected' || !this.ws) {
      // Queue message for when connection is restored
      this.messageQueue.push(message);
      return false;
    }

    try {
      const messageWithTimestamp = {
        ...message,
        timestamp: Date.now(),
        id: message.id || this.generateMessageId(),
      };

      this.ws.send(JSON.stringify(messageWithTimestamp));
      return true;
    } catch (error) {
      console.error('Failed to send WebSocket message:', error);
      return false;
    }
  }

  /**
   * Subscribe to specific message type
   *
   * @param messageType - Type of message to subscribe to
   * @param handler - Handler function to call when message received
   * @returns Unsubscribe function
   */
  subscribe<T>(
    messageType: string,
    handler: WebSocketEventHandler<T>
  ): () => void {
    if (!this.eventHandlers.has(messageType)) {
      this.eventHandlers.set(messageType, new Set());
    }

    this.eventHandlers.get(messageType)!.add(handler);

    // Return unsubscribe function
    return () => {
      const handlers = this.eventHandlers.get(messageType);
      if (handlers) {
        handlers.delete(handler);
        if (handlers.size === 0) {
          this.eventHandlers.delete(messageType);
        }
      }
    };
  }

  /**
   * Subscribe to connection state changes
   *
   * @param listener - Listener function to call on state changes
   * @returns Unsubscribe function
   */
  onStateChange(
    listener: (state: WebSocketConnectionState) => void
  ): () => void {
    this.stateChangeListeners.add(listener);

    // Immediately call with current state
    listener(this.connectionState);

    // Return unsubscribe function
    return () => {
      this.stateChangeListeners.delete(listener);
    };
  }

  /**
   * Get current connection state
   *
   * @returns Current connection state
   */
  getConnectionState(): WebSocketConnectionState {
    return { ...this.connectionState };
  }

  /**
   * Get connection status
   *
   * @returns true if connected
   */
  isConnected(): boolean {
    return this.connectionState.status === 'connected';
  }

  // Private methods

  private handleOpen(_event: Event): void {
    if (this.connectionTimeout) {
      clearTimeout(this.connectionTimeout);
      this.connectionTimeout = null;
    }

    this.updateConnectionState({
      status: 'connected',
      reconnectAttempt: 0,
      lastConnected: new Date(),
      lastError: undefined,
    });

    // Process queued messages
    this.processMessageQueue();

    // Start heartbeat
    this.startHeartbeat();

    console.log('WebSocket connected successfully');
  }

  private handleMessage(event: MessageEvent): void {
    try {
      const message = JSON.parse(event.data) as WebSocketMessage;

      // Handle heartbeat responses
      if (message.type === 'pong') {
        return;
      }

      // Dispatch to registered handlers
      const handlers = this.eventHandlers.get(message.type);
      if (handlers) {
        handlers.forEach((handler) => {
          try {
            handler(message);
          } catch (error) {
            console.error(
              `Error in WebSocket message handler for type "${message.type}":`,
              error
            );
          }
        });
      }
    } catch (error) {
      console.error('Failed to parse WebSocket message:', error);
    }
  }

  private handleClose(event: CloseEvent): void {
    console.log(`WebSocket closed: ${event.code} - ${event.reason}`);

    this.clearTimers();

    // Check if we should attempt reconnection
    if (
      event.code !== 1000 &&
      this.connectionState.reconnectAttempt < this.config.maxReconnectAttempts
    ) {
      this.attemptReconnect();
    } else {
      this.updateConnectionState({
        status: event.code === 1000 ? 'disconnected' : 'failed',
        lastError:
          event.code !== 1000
            ? new Error(`Connection closed: ${event.reason}`)
            : undefined,
      });
    }
  }

  private handleError(error: Event | Error): void {
    const errorMessage =
      error instanceof Error ? error.message : 'WebSocket error occurred';
    console.error('WebSocket error:', errorMessage);

    this.updateConnectionState({
      status: 'failed',
      lastError: error instanceof Error ? error : new Error(errorMessage),
    });
  }

  private handleConnectionTimeout(): void {
    console.error('WebSocket connection timeout');

    if (this.ws) {
      this.ws.close();
    }

    this.attemptReconnect();
  }

  private attemptReconnect(): void {
    const attempt = this.connectionState.reconnectAttempt + 1;

    if (attempt > this.config.maxReconnectAttempts) {
      this.updateConnectionState({
        status: 'failed',
        lastError: new Error(
          `Max reconnection attempts (${this.config.maxReconnectAttempts}) exceeded`
        ),
      });
      return;
    }

    this.updateConnectionState({
      status: 'reconnecting',
      reconnectAttempt: attempt,
    });

    const delay = this.config.reconnectDelay * Math.pow(2, attempt - 1); // Exponential backoff

    console.log(
      `Attempting to reconnect (${attempt}/${this.config.maxReconnectAttempts}) in ${delay}ms...`
    );

    this.reconnectTimer = setTimeout(() => {
      this.connect().catch((error) => {
        console.error('Reconnection failed:', error);
      });
    }, delay);
  }

  private startHeartbeat(): void {
    this.heartbeatTimer = setInterval(() => {
      if (this.connectionState.status === 'connected') {
        this.send({ type: 'ping', payload: null });
      }
    }, this.config.heartbeatInterval);
  }

  private clearTimers(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    if (this.connectionTimeout) {
      clearTimeout(this.connectionTimeout);
      this.connectionTimeout = null;
    }
  }

  private processMessageQueue(): void {
    while (this.messageQueue.length > 0) {
      const message = this.messageQueue.shift()!;
      this.send(message);
    }
  }

  private updateConnectionState(
    updates: Partial<WebSocketConnectionState>
  ): void {
    this.connectionState = { ...this.connectionState, ...updates };

    // Notify all state change listeners
    this.stateChangeListeners.forEach((listener) => {
      try {
        listener(this.connectionState);
      } catch (error) {
        console.error('Error in WebSocket state change listener:', error);
      }
    });
  }

  private generateMessageId(): string {
    return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }
}

/**
 * Default WebSocket client instance
 */
let defaultClient: WebSocketClient | null = null;

/**
 * Get or create default WebSocket client
 *
 * @param config - Optional configuration for first initialization
 * @returns WebSocket client instance
 */
export function getWebSocketClient(config?: WebSocketConfig): WebSocketClient {
  if (!defaultClient) {
    if (!config) {
      throw new Error(
        'WebSocket configuration required for first initialization'
      );
    }
    defaultClient = new WebSocketClient(config);
  }
  return defaultClient;
}

/**
 * Reset default client (useful for testing)
 */
export function resetWebSocketClient(): void {
  if (defaultClient) {
    defaultClient.disconnect();
    defaultClient = null;
  }
}
