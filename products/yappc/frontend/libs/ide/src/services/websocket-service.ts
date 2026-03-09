/**
 * @ghatana/yappc-ide - WebSocket Service
 * 
 * WebSocket service for real-time IDE collaboration with
 * connection management, retry logic, and performance optimization.
 * 
 * @doc.type service
 * @doc.purpose Real-time collaboration WebSocket service
 * @doc.layer product
 * @doc.pattern WebSocket Service
 */

import { WebsocketProvider } from 'y-websocket';
import * as Y from 'yjs';
import type { CRDTOperation } from '../../../crdt-ide/src';

import type { IDECRDTState } from '../crdt/ide-schema';
import { createInitialIDEState } from '../crdt/ide-schema';

/**
 * WebSocket service configuration
 */
export interface WebSocketServiceConfig {
  /** WebSocket server URL */
  url: string;
  /** Room ID for collaboration session */
  roomId: string;
  /** Connection timeout in milliseconds */
  connectionTimeout?: number;
  /** Reconnection attempts */
  maxReconnectAttempts?: number;
  /** Reconnection delay in milliseconds */
  reconnectDelay?: number;
  /** Enable connection health checks */
  enableHealthCheck?: boolean;
  /** Health check interval in milliseconds */
  healthCheckInterval?: number;
}

/**
 * Connection status
 */
export type ConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'reconnecting' | 'error';

/**
 * WebSocket service events
 */
export interface WebSocketServiceEvents {
  /** Connection status changed */
  statusChange: (status: ConnectionStatus) => void;
  /** Operation received from server */
  operationReceived: (operation: CRDTOperation) => void;
  /** State synchronized */
  stateSynced: (state: IDECRDTState) => void;
  /** Connection error */
  error: (error: Error) => void;
  /** Presence updated */
  presenceUpdated: (presence: Record<string, unknown>) => void;
}

/**
 * WebSocket Service
 * 
 * Manages real-time collaboration with WebSocket connection,
 * automatic reconnection, and performance optimization.
 */
export class WebSocketService {
  private config: Required<WebSocketServiceConfig>;
  private yDoc: Y.Doc | null = null;
  private provider: WebsocketProvider | null = null;
  private status: ConnectionStatus = 'disconnected';
  private reconnectAttempts = 0;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private healthCheckTimer: NodeJS.Timeout | null = null;
  private eventListeners: Map<keyof WebSocketServiceEvents, Set<(...args: unknown[]) => void>> = new Map();
  private operationQueue: CRDTOperation[] = [];
  private isProcessingQueue = false;

  addEventListener<T extends keyof WebSocketServiceEvents>(
    event: T,
    listener: WebSocketServiceEvents[T]
  ): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      listeners.add(listener as (...args: unknown[]) => void);
    }
  }

  constructor(config: WebSocketServiceConfig) {
    this.config = {
      connectionTimeout: 10000,
      maxReconnectAttempts: 5,
      reconnectDelay: 2000,
      enableHealthCheck: true,
      healthCheckInterval: 30000,
      ...config,
    };

    this.initializeEventListeners();
  }

  /**
   * Initialize event listeners map
   */
  private initializeEventListeners(): void {
    const events: (keyof WebSocketServiceEvents)[] = [
      'statusChange',
      'operationReceived',
      'stateSynced',
      'error',
      'presenceUpdated',
    ];

    events.forEach(event => {
      this.eventListeners.set(event, new Set());
    });
  }

  /**
   * Connect to WebSocket server
   * 
   * @doc.returns Connection promise
   */
  async connect(): Promise<void> {
    if (this.status === 'connected' || this.status === 'connecting') {
      return;
    }

    this.setStatus('connecting');

    try {
      // Initialize Yjs document
      this.yDoc = new Y.Doc();

      // Initialize WebSocket provider
      this.provider = new WebsocketProvider(this.config.url, this.config.roomId, this.yDoc, {
        connect: true,
      });

      // Set up provider event listeners
      this.setupProviderListeners();

      // Wait for connection with timeout
      await this.waitForConnection();

      // Start health checks
      if (this.config.enableHealthCheck) {
        this.startHealthCheck();
      }

      // Process queued operations
      this.processOperationQueue();

      this.setStatus('connected');
      this.reconnectAttempts = 0;

    } catch (error) {
      this.setStatus('error');
      this.emit('error', error instanceof Error ? error : new Error('Connection failed'));

      // Attempt reconnection
      this.attemptReconnection();
    }
  }

  /**
   * Disconnect from WebSocket server
   */
  disconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    if (this.healthCheckTimer) {
      clearInterval(this.healthCheckTimer);
      this.healthCheckTimer = null;
    }

    if (this.provider) {
      this.provider.destroy();
      this.provider = null;
    }

    if (this.yDoc) {
      this.yDoc.destroy();
      this.yDoc = null;
    }

    this.setStatus('disconnected');
  }

  /**
   * Send operation to server
   * 
   * @doc.param operation - Operation to send
   * @doc.returns Send success
   */
  async sendOperation(operation: CRDTOperation): Promise<boolean> {
    if (this.status !== 'connected') {
      // Queue operation for later
      this.operationQueue.push(operation);
      return false;
    }

    try {
      const yMap = this.yDoc?.getMap('operations');
      if (yMap) {
        yMap.set(operation.id, operation);
        return true;
      }
      return false;
    } catch (error) {
      console.error('Failed to send operation:', error);
      return false;
    }
  }

  /**
   * Send state to server
   * 
   * @doc.param state - State to send
   * @doc.returns Send success
   */
  async sendState(state: IDECRDTState): Promise<boolean> {
    if (this.status !== 'connected') {
      return false;
    }

    try {
      const yMap = this.yDoc?.getMap('state');
      if (yMap) {
        yMap.set('current', state);
        return true;
      }
      return false;
    } catch (error) {
      console.error('Failed to send state:', error);
      return false;
    }
  }

  /**
   * Get current connection status
   * 
   * @doc.returns Connection status
   */
  getStatus(): ConnectionStatus {
    return this.status;
  }

  /**
   * Check if connected
   * 
   * @doc.returns Connection state
   */
  isConnected(): boolean {
    return this.status === 'connected';
  }

  /**
   * Remove event listener
   * 
   * @doc.param event - Event name
   * @doc.param listener - Event listener
   */
  removeEventListener<T extends keyof WebSocketServiceEvents>(
    event: T,
    listener: WebSocketServiceEvents[T]
  ): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      listeners.delete(listener as (...args: unknown[]) => void);
    }
  }

  /**
   * Setup provider event listeners
   */
  private setupProviderListeners(): void {
    if (!this.provider) return;

    this.provider.on('status', (event: { status: string }) => {
      switch (event.status) {
        case 'connected':
          this.setStatus('connected');
          break;
        case 'disconnected':
          this.setStatus('disconnected');
          break;
        case 'connecting':
          this.setStatus('connecting');
          break;
      }
    });

    this.provider.on('sync', (isSynced: boolean) => {
      if (!isSynced) {
        this.emit('stateSynced', this.getCurrentState());
      }
    });

    this.provider.on('connection-error', (event: Event) => {
      this.emit('error', new Error(`Connection error: ${event.type}`));
    });

    // Set up operation listeners
    const yMap = this.yDoc?.getMap('operations');
    if (yMap) {
      yMap.observe((event: Y.YMapEvent<unknown>) => {
        event.changes.keys.forEach((change, key) => {
          if (change.action === 'add' || change.action === 'update') {
            const operation = yMap.get(key) as CRDTOperation;
            if (operation) {
              this.emit('operationReceived', operation);
            }
          }
        });
      });
    }

    // Set up presence listeners
    if (this.provider?.awareness) {
      this.provider.awareness.on('change', () => {
        const states = this.provider!.awareness?.getStates() || new Map();
        const presence: Record<string, unknown> = {};

        for (const [key, state] of states) {
          presence[key] = state;
        }

        this.emit('presenceUpdated', presence);
      });
    }
  }

  /**
   * Wait for connection with timeout
   */
  private waitForConnection(): Promise<void> {
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(new Error('Connection timeout'));
      }, this.config.connectionTimeout);

      const checkConnection = () => {
        if (this.status === 'connected') {
          clearTimeout(timeout);
          resolve();
        } else if (this.status === 'error') {
          clearTimeout(timeout);
          reject(new Error('Connection failed'));
        } else {
          setTimeout(checkConnection, 100);
        }
      };

      checkConnection();
    });
  }

  /**
   * Attempt reconnection
   */
  private attemptReconnection(): void {
    if (this.reconnectAttempts >= this.config.maxReconnectAttempts) {
      this.setStatus('error');
      return;
    }

    this.reconnectAttempts++;
    this.setStatus('reconnecting');

    this.reconnectTimer = setTimeout(() => {
      this.connect().catch(() => {
        // Reconnection failed, try again
        this.attemptReconnection();
      });
    }, this.config.reconnectDelay * this.reconnectAttempts);
  }

  /**
   * Start health check
   */
  private startHealthCheck(): void {
    this.healthCheckTimer = setInterval(() => {
      if (this.status === 'connected') {
        // Send ping
        this.sendPing();
      }
    }, this.config.healthCheckInterval);
  }

  /**
   * Send ping to server
   */
  private sendPing(): void {
    try {
      const yMap = this.yDoc?.getMap('ping');
      if (yMap) {
        yMap.set('timestamp', Date.now());
      }
    } catch (error) {
      console.error('Failed to send ping:', error);
    }
  }

  /**
   * Process queued operations
   */
  private async processOperationQueue(): Promise<void> {
    if (this.isProcessingQueue || this.operationQueue.length === 0) {
      return;
    }

    this.isProcessingQueue = true;

    try {
      const operations = [...this.operationQueue];
      this.operationQueue = [];

      for (const operation of operations) {
        await this.sendOperation(operation);
      }
    } catch (error) {
      console.error('Failed to process operation queue:', error);
    } finally {
      this.isProcessingQueue = false;
    }
  }

  /**
   * Get current state from Yjs document
   */
  private getCurrentState(): IDECRDTState {
    const yMap = this.yDoc?.getMap('state');
    const state = yMap?.get('current') as IDECRDTState | undefined;
    if (state) return state;

    // Return a typed initial CRDT state (Y.Map-backed) for safe defaults
    return createInitialIDEState();
  }

  /**
   * Set connection status
   */
  private setStatus(status: ConnectionStatus): void {
    if (this.status !== status) {
      this.status = status;
      this.emit('statusChange', status);
    }
  }

  /**
   * Emit event to listeners
   */
  private emit<T extends keyof WebSocketServiceEvents>(
    event: T,
    ...args: Parameters<WebSocketServiceEvents[T]>
  ): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      listeners.forEach(listener => {
        try {
          listener(...args);
        } catch (error) {
          console.error(`Error in event listener for ${event}:`, error);
        }
      });
    }
  }

  /**
   * Get connection statistics
   */
  getStatistics(): {
    status: ConnectionStatus;
    reconnectAttempts: number;
    queuedOperations: number;
    isProcessingQueue: boolean;
  } {
    return {
      status: this.status,
      reconnectAttempts: this.reconnectAttempts,
      queuedOperations: this.operationQueue.length,
      isProcessingQueue: this.isProcessingQueue,
    };
  }

  /**
   * Clear operation queue
   */
  clearOperationQueue(): void {
    this.operationQueue = [];
  }

  /**
   * Force reconnection
   */
  async forceReconnect(): Promise<void> {
    this.disconnect();
    this.reconnectAttempts = 0;
    await this.connect();
  }
}

/**
 * Create WebSocket service instance
 * 
 * @doc.param config - Service configuration
 * @doc.returns WebSocket service instance
 */
export function createWebSocketService(config: WebSocketServiceConfig): WebSocketService {
  return new WebSocketService(config);
}

/**
 * Default WebSocket service configuration
 */
export const DEFAULT_WEBSOCKET_CONFIG: Partial<WebSocketServiceConfig> = {
  url: 'ws://localhost:1234',
  roomId: 'ide-room',
  connectionTimeout: 10000,
  maxReconnectAttempts: 5,
  reconnectDelay: 2000,
  enableHealthCheck: true,
  healthCheckInterval: 30000,
};
