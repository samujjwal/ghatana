/**
 * @fileoverview WebSocket connector providing reconnection, heartbeat, message queuing, and
 * backpressure-friendly send buffering.
 *
 * Designed to demonstrate how to layer reconnection strategies, heartbeat monitoring, and queued
 * message delivery while remaining dependency-light. Use with `Telemetry` to trace message flow and
 * `DeadLetterQueue` to persist unsent payloads when reconnection fails.
 *
 * @see {@link WebSocketConnector}
 * @see {@link ../BaseConnector.BaseConnector | BaseConnector}
 */
import { v4 as uuidv4 } from 'uuid';
import { BaseConnector } from '../BaseConnector';
import { ConnectionOptions } from '../types';

/**
 * Configuration options accepted by `WebSocketConnector`.
 */
export interface WebSocketConnectorConfig extends ConnectionOptions {
  /**
   * WebSocket server URL (ws:// or wss://)
   */
  url: string;
  
  /**
   * Protocols to use
   */
  protocols?: string | string[];
  
  /**
   * Enable/disable automatic reconnection
   * @default true
   */
  autoReconnect?: boolean;
  
  /**
   * Reconnection delay in milliseconds
   * @default 1000
   */
  reconnectionDelay?: number;
  
  /**
   * Maximum reconnection attempts
   * @default Infinity
   */
  maxReconnectionAttempts?: number;
  
  /**
   * Enable/disable message queuing while disconnected
   * @default true
   */
  queueMessages?: boolean;
  
  /**
   * Maximum queue size
   * @default 1000
   */
  maxQueueSize?: number;
  
  /**
   * Enable/disable ping/pong for connection health checks
   * @default true
   */
  pingPong?: boolean;
  
  /**
   * Ping interval in milliseconds
   * @default 30000
   */
  pingInterval?: number;
  
  /**
   * Timeout in milliseconds for pong response
   * @default 5000
   */
  pongTimeout?: number;
}

/**
 * WebSocket connector managing lifecycle, reconnection, heartbeat, and message handling.
 *
 * **Example (basic usage):**
 * ```ts
 * const connector = new WebSocketConnector({ url: 'wss://example.com/socket', autoReconnect: true });
 * connector.onEvent('message', event => console.log(event.payload));
 * await connector.connect();
 * ```
 *
 * **Example (queued send with DLQ fallback):**
 * ```ts
 * const dlq = new DeadLetterQueue();
 * connector.onEvent('error', ({ payload }) => dlq.add(payload.event, payload.error));
 * await connector.send({ message: 'hello world' }).catch(error => {
 *   dlq.add({ id: uuidv4(), payload: 'hello world' } as any, error);
 * });
 * ```
 */
export class WebSocketConnector extends BaseConnector<WebSocketConnectorConfig> {
  private _socket: unknown | null = null;
  private _reconnectTimeout: NodeJS.Timeout | null = null;
  private _pingInterval: NodeJS.Timeout | null = null;
  private _pongTimeout: NodeJS.Timeout | null = null;
  private _messageQueue: Array<{ data: unknown; options?: unknown }> = [];
  private _reconnectionAttempts: number = 0;
  
  /**
   * Creates a new connector instance with sensible defaults.
   */
  constructor(config: WebSocketConnectorConfig) {
    super({
      autoReconnect: true,
      reconnectionDelay: 1000,
      maxReconnectionAttempts: Infinity,
      queueMessages: true,
      maxQueueSize: 1000,
      pingPong: true,
      pingInterval: 30000,
      pongTimeout: 5000,
      ...config,
      type: 'websocket',
    });
  }
  
  /** Returns the underlying `WebSocket` instance if connected. */
  public get socket(): unknown | null {
    return this._socket;
  }
  
  /** @inheritdoc */
  protected async _connect(): Promise<void> {
    if (this._socket) {
      if (this._socket.readyState === 1) {
        return; // Already connected
      }
      
      // Clean up existing connection
      this._cleanupSocket();
    }
    
    return new Promise((resolve, reject) => {
      try {
        this.status = 'connecting';
        
        const url = new URL(this._config.url);
        const protocols = Array.isArray(this._config.protocols) 
          ? this._config.protocols 
          : this._config.protocols ? [this._config.protocols] : undefined;
        
        // Add authentication to URL if needed
        this._applyAuthToUrl(url);
        
        // Use native WebSocket in browser, 'ws' module in Node.js
        const ws = typeof WebSocket !== 'undefined'
          ? new WebSocket(url.toString(), protocols)
          : (() => {
              // Node.js environment - dynamically require 'ws'
              const WS: any = require('ws');
              return new WS(url.toString(), {
                headers: (this._config as any).headers,
                protocols,
                rejectUnauthorized: (this._config as any).secure !== false,
                ...((this._config as any).fetchOptions || {}),
              });
            })();
        
        ws.addEventListener?.('open', () => this._handleOpen(resolve)) || (ws as any).on?.('open', () => this._handleOpen(resolve));
        ws.addEventListener?.('message', (event: MessageEvent) => this._handleMessage(event.data)) || (ws as any).on?.('message', (data: unknown) => this._handleMessage(data));
        ws.addEventListener?.('close', (event: CloseEvent) => this._handleClose(event.code, event.reason)) || (ws as any).on?.('close', (code: number, reason: unknown) => this._handleClose(code, String(reason)));
        ws.addEventListener?.('error', (event: Event) => this._handleError(new Error('WebSocket error'), reject)) || (ws as any).on?.('error', (error: unknown) => this._handleError(error as Error, reject));
        // Ping/pong only supported in Node.js ws library
        (ws as any).on?.('ping', (data: unknown) => this._handlePing(data as Buffer));
        (ws as any).on?.('pong', (data: unknown) => this._handlePong(data as Buffer));
        
        this._socket = ws;
      } catch (error) {
        this.status = 'error';
        reject(error);
      }
    });
  }
  
  /**
   * Disconnects from the WebSocket server.
   *
   * **Note:** This method will cancel any pending reconnection attempts and clean up ping/pong
   * intervals.
   *
   * @async
   */
  protected async _disconnect(): Promise<void> {
    this._cancelReconnection();
    this._cleanupPingPong();
    
    if (this._socket) {
      const socket = this._socket;
      this._socket = null;
      
      if (socket.readyState === 1) {
        socket.close(1000, 'Normal closure');
      }
      
      this._cleanupSocket(socket);
    }
    
    this.status = 'disconnected';
  }
  
  /**
   * Sends a message to the WebSocket server.
   *
   * Queues messages when not connected (if `queueMessages` is enabled) and emits structured errors
   * when delivery fails. Message options can provide callbacks compatible with `ws`.
   *
   * @param data Message data
   * @param options Optional message options
   * @async
   */
  public async send(data: unknown, options: unknown = {}): Promise<void> {
    if (!this._socket || this._socket.readyState !== WebSocket.OPEN) {
      if (this._config.queueMessages && this._messageQueue.length < (this._config.maxQueueSize || 1000)) {
        // Queue the message if we're not connected and queueing is enabled
        this._messageQueue.push({ data, options });
        return;
      }
      
      throw new Error('WebSocket is not connected');
    }
    
    try {
      const message = this._prepareMessage(data, options);
      this._socket.send(message, options.callback);
    } catch (error) {
      this._handleError(error as Error);
      throw error;
    }
  }
  
  /**
   * Destroys the WebSocket connector instance.
   *
   * Flushes message queues, stops timers, and delegates to `BaseConnector.destroy()` for listener
   * cleanup. Invoke during shutdown hooks to avoid resource leaks.
   *
   * @async
   */
  public override async destroy(): Promise<void> {
    await this.disconnect();
    this._messageQueue = [];
    await super.destroy();
  }
  
  /**
   * Handles the WebSocket connection open event.
   *
   * @param resolve Resolve function for the connection promise
   */
  private _handleOpen(resolve: () => void): void {
    this.status = 'connected';
    this._reconnectionAttempts = 0;
    
    // Start ping/pong if enabled
    if (this._config.pingPong) {
      this._setupPingPong();
    }
    
    // Process any queued messages
    this._processMessageQueue();
    
    this.emit('connected');
    resolve();
  }
  
  /**
   * Handles incoming WebSocket messages.
   *
   * Attempts JSON deserialization, falls back to raw payloads, and emits `message` events for
   * downstream subscribers.
   *
   * @param data Message data
   */
  private _handleMessage(data: unknown): void {
    try {
      let parsedData: unknown;
      
      // Try to parse JSON if the data is a string
      if (typeof data === 'string') {
        try {
          parsedData = JSON.parse(data);
        } catch {
          parsedData = data;
        }
      } else if (Buffer.isBuffer(data) || data instanceof ArrayBuffer) {
        // Handle binary data
        parsedData = Buffer.from(data as any);
      } else {
        parsedData = data;
      }
      
      this._emitEvent({
        id: uuidv4(),
        type: 'message',
        timestamp: Date.now(),
        payload: parsedData,
      });
    } catch (error) {
      this._handleError(error as Error);
    }
  }
  
  /**
   * Handles the WebSocket connection close event.
   *
   * @param code Close code
   * @param reason Close reason
   */
  private _handleClose(code: number, reason: string): void {
    this._cleanupPingPong();
    
    const wasConnected = this.status === 'connected';
    this.status = 'disconnected';
    
    this.emit('disconnected', { code, reason, wasConnected });
    
    // Attempt reconnection if needed
    if (wasConnected && this._config.autoReconnect) {
      this._scheduleReconnect();
    }
  }
  
  /**
   * Handles WebSocket errors.
   *
   * Emits structured error events, updates connector status, and optionally rejects the connect
   * promise to surface connection failures to callers.
   *
   * @param error Error instance
   * @param reject Reject function for the connection promise
   */
  private _handleError(error: Error, reject?: (error: Error) => void): void {
    this.status = 'error';
    
    const errorEvent = {
      id: uuidv4(),
      type: 'error',
      timestamp: Date.now(),
      payload: {
        message: error.message,
        stack: error.stack,
      },
    };
    
    this._emitEvent(errorEvent);
    
    if (reject) {
      reject(error);
    } else {
      this.emit('error', error);
    }
  }
  
  /**
   * Handles incoming ping messages.
   *
   * Echoes `pong` responses when operating as a client. Consumers can observe `ping` to drive
   * metrics or external heartbeat dashboards.
   *
   * @param data Ping data
   */
  private _handlePing(data: Buffer): void {
    this.emit('ping', data);
    
    // Respond to ping with pong if we're the client
    if (this._socket && this._socket.readyState === 1) {
      this._socket.pong(data);
    }
  }
  
  /**
   * Handles incoming pong messages.
   *
   * Clears pending pong timeout to avoid spuriously terminating healthy connections.
   *
   * @param data Pong data
   */
  private _handlePong(data: Buffer): void {
    this.emit('pong', data);
    
    // Clear the pong timeout
    if (this._pongTimeout) {
      clearTimeout(this._pongTimeout);
      this._pongTimeout = null;
    }
  }
  
  /**
   * Sets up the ping/pong interval.
   *
   * Schedules periodic pings and terminates connections when pong responses are missed, enabling
   * automated failover or reconnect logic.
   */
  private _setupPingPong(): void {
    if (!this._config.pingPong || !this._socket) return;
    
    // Clear any existing interval
    this._cleanupPingPong();
    
    // Set up ping interval
    this._pingInterval = setInterval(() => {
      if (this._socket && this._socket.readyState === 1) {
        try {
          this._socket.ping();
          
          // Set a timeout for the pong response
          this._pongTimeout = setTimeout(() => {
            if (this._socket) {
              this._socket.terminate();
              this._handleError(new Error('Pong timeout'));
            }
          }, this._config.pongTimeout || 5000);
          
          this.emit('ping');
        } catch (error) {
          this._handleError(error as Error);
        }
      }
    }, this._config.pingInterval || 30000);
  }
  
  private _cleanupPingPong(): void {
    if (this._pingInterval) {
      clearInterval(this._pingInterval);
      this._pingInterval = null;
    }
    
    if (this._pongTimeout) {
      clearTimeout(this._pongTimeout);
      this._pongTimeout = null;
    }
  }
  
  private _scheduleReconnect(): void {
    if (this._reconnectTimeout || this._reconnectionAttempts >= (this._config.maxReconnectionAttempts || Infinity)) {
      return;
    }
    
    this._reconnectionAttempts++;
    const delay = Math.min(
      (this._config.reconnectionDelay || 1000) * Math.pow(1.5, this._reconnectionAttempts - 1),
      30000 // Max 30 seconds between reconnects
    );
    
    this.emit('reconnecting', {
      attempt: this._reconnectionAttempts,
      delay,
    });
    
    this._reconnectTimeout = setTimeout(() => {
      this._reconnectTimeout = null;
      this.connect().catch(() => {
        // Reconnection failed, schedule the next attempt
        if (this.status !== 'connected') {
          this._scheduleReconnect();
        }
      });
    }, delay);
  }
  
  private _cancelReconnection(): void {
    if (this._reconnectTimeout) {
      clearTimeout(this._reconnectTimeout);
      this._reconnectTimeout = null;
    }
  }
  
  private _cleanupSocket(socket: unknown | null = this._socket): void {
    if (!socket) return;
    
    socket.removeAllListeners('open');
    socket.removeAllListeners('message');
    socket.removeAllListeners('close');
    socket.removeAllListeners('error');
    socket.removeAllListeners('ping');
    socket.removeAllListeners('pong');
    
    if (socket.readyState === 1) {
      socket.terminate();
    }
  }
  
  private _processMessageQueue(): void {
    if (!this._socket || this._socket.readyState !== 1) {
      return;
    }
    
    // Process all queued messages
    while (this._messageQueue.length > 0) {
      const { data, options } = this._messageQueue.shift()!;
      this.send(data, options).catch(error => {
        this._handleError(error);
      });
    }
  }
  
  private _prepareMessage(data: unknown, _options: unknown): string | Buffer | ArrayBuffer | Buffer[] {
    if (typeof data === 'string') {
      return data;
    } else if (Buffer.isBuffer(data) || data instanceof ArrayBuffer || ArrayBuffer.isView(data)) {
      return data as Buffer | ArrayBuffer | Buffer[];
    } else if (typeof data === 'object') {
      return JSON.stringify(data);
    } else {
      return String(data);
    }
  }
  
  private _applyAuthToUrl(url: URL): void {
    if (!this._config.auth) return;
    
    const { auth } = this._config;
    
    switch (auth.type) {
      case 'basic':
        url.username = auth.username || '';
        url.password = auth.password || '';
        break;
        
      case 'bearer':
        url.searchParams.append('token', auth.token);
        break;
        
      case 'api_key':
        const paramName = auth.paramName || 'api_key';
        url.searchParams.append(paramName, auth.apiKey);
        break;
    }
  }
}
