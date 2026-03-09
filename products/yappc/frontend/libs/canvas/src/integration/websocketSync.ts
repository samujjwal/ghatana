/**
 * WebSocket Sync Adapter
 * 
 * Real-time bidirectional synchronization using WebSocket connection.
 * Implements event debouncing to maintain 60 FPS rendering performance.
 */

import type {
  SyncAdapter,
  SyncResult,
  CanvasChange,
  WebSocketMessage,
  SyncConfig,
} from './types';

/**
 * WebSocket connection state
 */
type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'reconnecting' | 'error';

/**
 * WebSocket Sync Configuration
 */
export interface WebSocketSyncConfig extends Pick<SyncConfig, 'endpoint' | 'authToken' | 'retry' | 'debounceInterval'> {
  /** Heartbeat interval in milliseconds */
  heartbeatInterval?: number;
  
  /** Reconnect on connection loss */
  autoReconnect?: boolean;
  
  /** Maximum reconnect attempts */
  maxReconnectAttempts?: number;
}

/**
 * WebSocket Sync Adapter
 * 
 * Features:
 * - Real-time bidirectional sync
 * - Event debouncing (maintains 60 FPS)
 * - Automatic reconnection
 * - Heartbeat/ping-pong
 * - Message queuing during disconnect
 * 
 * @example
 * ```ts
 * const adapter = new WebSocketSyncAdapter({
 *   endpoint: 'wss://api.example.com/sync',
 *   authToken: 'your-token',
 *   debounceInterval: 16, // ~60 FPS
 * });
 * 
 * await adapter.connect();
 * const unsubscribe = adapter.subscribe('doc-123', (change) => {
 *   console.log('Received change:', change);
 * });
 * ```
 */
export class WebSocketSyncAdapter implements SyncAdapter {
  readonly type = 'websocket' as const;
  
  private config: Required<WebSocketSyncConfig>;
  private ws?: WebSocket;
  private state: ConnectionState = 'disconnected';
  private reconnectAttempts = 0;
  private heartbeatTimer?: ReturnType<typeof setInterval>;
  private messageQueue: WebSocketMessage[] = [];
  private subscriptions = new Map<string, Set<(change: CanvasChange) => void>>();
  private debounceTimers = new Map<string, ReturnType<typeof setTimeout>>();
  
  /**
   *
   */
  constructor(config: WebSocketSyncConfig) {
    this.config = {
      endpoint: config.endpoint,
      authToken: config.authToken || '',
      debounceInterval: config.debounceInterval || 16, // ~60 FPS
      heartbeatInterval: config.heartbeatInterval || 30000, // 30 seconds
      autoReconnect: config.autoReconnect ?? true,
      maxReconnectAttempts: config.maxReconnectAttempts || 10,
      retry: config.retry || {
        maxRetries: 3,
        backoffMultiplier: 2,
        initialDelay: 1000,
      },
    };
  }
  
  /**
   * Connect to WebSocket server
   */
  async connect(): Promise<void> {
    if (this.state === 'connected' || this.state === 'connecting') {
      return;
    }
    
    return new Promise((resolve, reject) => {
      try {
        this.state = 'connecting';
        
        // Build WebSocket URL with auth token
        const url = new URL(this.config.endpoint);
        if (this.config.authToken) {
          url.searchParams.set('token', this.config.authToken);
        }
        
        this.ws = new WebSocket(url.toString());
        
        this.ws.onopen = () => {
          this.state = 'connected';
          this.reconnectAttempts = 0;
          this.startHeartbeat();
          this.flushMessageQueue();
          resolve();
        };
        
        this.ws.onmessage = (event) => {
          this.handleMessage(event.data);
        };
        
        this.ws.onerror = (error) => {
          console.error('WebSocket error:', error);
          
          if (this.state === 'connecting') {
            this.state = 'error';
            reject(new Error('WebSocket connection failed'));
          } else {
            this.state = 'error';
          }
        };
        
        this.ws.onclose = () => {
          this.state = 'disconnected';
          this.stopHeartbeat();
          
          if (this.config.autoReconnect && this.reconnectAttempts < this.config.maxReconnectAttempts) {
            this.reconnect();
          }
        };
        
        // Connection timeout
        setTimeout(() => {
          if (this.state === 'connecting') {
            this.ws?.close();
            reject(new Error('WebSocket connection timeout'));
          }
        }, 10000);
        
      } catch (error) {
        this.state = 'error';
        reject(error);
      }
    });
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
    
    this.state = 'disconnected';
    this.subscriptions.clear();
    this.debounceTimers.forEach(timer => clearTimeout(timer));
    this.debounceTimers.clear();
  }
  
  /**
   * Check if connected
   */
  isConnected(): boolean {
    return this.state === 'connected' && this.ws?.readyState === WebSocket.OPEN;
  }
  
  /**
   * Pull latest data (not applicable for WebSocket - use subscribe)
   */
  async pull(documentId: string): Promise<SyncResult> {
    // WebSocket doesn't support pull - it's push-based
    // Client should use subscribe() for real-time updates
    return {
      success: false,
      version: 0,
      changes: [],
      error: {
        code: 'NOT_SUPPORTED',
        message: 'WebSocket adapter uses push-based sync. Use subscribe() instead.',
      },
    };
  }
  
  /**
   * Push local changes to server
   */
  async push(documentId: string, changes: CanvasChange[]): Promise<SyncResult> {
    if (!this.isConnected()) {
      // Queue messages if disconnected
      const message: WebSocketMessage = {
        type: 'change',
        documentId,
        data: changes,
        timestamp: Date.now(),
      };
      this.messageQueue.push(message);
      
      return {
        success: false,
        version: 0,
        changes: [],
        error: {
          code: 'DISCONNECTED',
          message: 'WebSocket disconnected. Message queued for delivery.',
        },
      };
    }
    
    try {
      const message: WebSocketMessage = {
        type: 'change',
        documentId,
        data: changes,
        timestamp: Date.now(),
      };
      
      this.ws!.send(JSON.stringify(message));
      
      return {
        success: true,
        version: 0, // Server will broadcast version in response
        changes: [],
      };
    } catch (error) {
      return {
        success: false,
        version: 0,
        changes: [],
        error: {
          code: 'SEND_ERROR',
          message: error instanceof Error ? error.message : 'Unknown error',
          details: error,
        },
      };
    }
  }
  
  /**
   * Subscribe to real-time updates
   * Implements debouncing to maintain 60 FPS
   */
  subscribe(documentId: string, callback: (change: CanvasChange) => void): () => void {
    if (!this.subscriptions.has(documentId)) {
      this.subscriptions.set(documentId, new Set());
    }
    
    // Wrap callback with debounce
    const debouncedCallback = this.createDebouncedCallback(documentId, callback);
    this.subscriptions.get(documentId)!.add(debouncedCallback);
    
    // Subscribe to document on server
    if (this.isConnected()) {
      this.ws!.send(JSON.stringify({
        type: 'subscribe',
        documentId,
        timestamp: Date.now(),
      }));
    }
    
    // Return unsubscribe function
    return () => {
      this.subscriptions.get(documentId)?.delete(debouncedCallback);
      
      if (this.subscriptions.get(documentId)?.size === 0) {
        this.subscriptions.delete(documentId);
        
        // Unsubscribe from document on server
        if (this.isConnected()) {
          this.ws!.send(JSON.stringify({
            type: 'unsubscribe',
            documentId,
            timestamp: Date.now(),
          }));
        }
      }
    };
  }
  
  /**
   * Handle incoming WebSocket message
   */
  private handleMessage(data: string): void {
    try {
      const message: WebSocketMessage = JSON.parse(data);
      
      switch (message.type) {
        case 'change':
          if (message.documentId) {
            this.notifySubscribers(message.documentId, message.data as CanvasChange);
          }
          break;
          
        case 'ping':
          // Respond to server ping
          this.ws!.send(JSON.stringify({ type: 'pong', timestamp: Date.now() }));
          break;
          
        case 'pong':
          // Server responded to our ping
          break;
          
        case 'error':
          console.error('Server error:', message.data);
          break;
          
        default:
          console.warn('Unknown message type:', message.type);
      }
    } catch (error) {
      console.error('Failed to parse WebSocket message:', error);
    }
  }
  
  /**
   * Notify subscribers of change (with debouncing)
   */
  private notifySubscribers(documentId: string, change: CanvasChange): void {
    const callbacks = this.subscriptions.get(documentId);
    if (!callbacks) return;
    
    callbacks.forEach((callback) => {
      callback(change);
    });
  }
  
  /**
   * Create debounced callback to maintain 60 FPS
   */
  private createDebouncedCallback(
    documentId: string,
    callback: (change: CanvasChange) => void
  ): (change: CanvasChange) => void {
    let pendingChanges: CanvasChange[] = [];
    
    return (change: CanvasChange) => {
      pendingChanges.push(change);
      
      const key = `${documentId}-${callback}`;
      
      // Clear existing timer
      if (this.debounceTimers.has(key)) {
        clearTimeout(this.debounceTimers.get(key)!);
      }
      
      // Set new timer
      const timer = setTimeout(() => {
        // Batch all pending changes
        if (pendingChanges.length > 0) {
          // Merge changes if possible, or call with each
          pendingChanges.forEach((c) => callback(c));
          pendingChanges = [];
        }
        this.debounceTimers.delete(key);
      }, this.config.debounceInterval);
      
      this.debounceTimers.set(key, timer);
    };
  }
  
  /**
   * Start heartbeat to keep connection alive
   */
  private startHeartbeat(): void {
    this.heartbeatTimer = setInterval(() => {
      if (this.isConnected()) {
        this.ws!.send(JSON.stringify({ type: 'ping', timestamp: Date.now() }));
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
   * Attempt to reconnect
   */
  private async reconnect(): Promise<void> {
    this.state = 'reconnecting';
    this.reconnectAttempts++;
    
    const delay = this.config.retry.initialDelay * 
      Math.pow(this.config.retry.backoffMultiplier, this.reconnectAttempts - 1);
    
    await new Promise((resolve) => setTimeout(resolve, delay));
    
    try {
      await this.connect();
    } catch (error) {
      console.error('Reconnection failed:', error);
      
      if (this.reconnectAttempts < this.config.maxReconnectAttempts) {
        this.reconnect();
      } else {
        this.state = 'error';
        console.error('Max reconnect attempts reached');
      }
    }
  }
  
  /**
   * Flush queued messages after reconnection
   */
  private flushMessageQueue(): void {
    while (this.messageQueue.length > 0 && this.isConnected()) {
      const message = this.messageQueue.shift()!;
      this.ws!.send(JSON.stringify(message));
    }
  }
}

/**
 * Create WebSocket sync adapter
 */
export function createWebSocketSyncAdapter(config: WebSocketSyncConfig): WebSocketSyncAdapter {
  return new WebSocketSyncAdapter(config);
}
