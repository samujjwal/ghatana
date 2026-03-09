/**
 * WebSocket Client for Real-Time Updates
 * Handles real-time data synchronization with mocked events
 */

/**
 * WebSocket event types for DevSecOps updates
 */
export type WebSocketEventType =
  | 'item:updated'
  | 'item:created'
  | 'item:deleted'
  | 'phase:updated'
  | 'status:changed'
  | 'notification:alert';

/**
 * WebSocket event structure
 */
export interface WebSocketEvent {
  type: WebSocketEventType;
  timestamp: Date;
  data: Record<string, unknown>;
}

/**
 * WebSocket event handler function type
 */
export type WebSocketEventHandler = (event: WebSocketEvent) => void;

/**
 * WebSocket client for DevSecOps real-time updates
 * 
 * Provides real-time event notifications for item updates, status changes,
 * and system notifications. Currently uses mock data for development.
 * 
 * @example
 * ```ts
 * const ws = new DevSecOpsWebSocket('ws://api.example.com/ws');
 * 
 * // Connect
 * await ws.connect();
 * 
 * // Subscribe to events
 * ws.on('item:updated', (event) => {
 *   console.log('Item updated:', event.data.itemId);
 *   refreshItemData(event.data.itemId);
 * });
 * 
 * ws.on('notification:alert', (event) => {
 *   toast.info(event.data.message);
 * });
 * 
 * // Disconnect when done
 * ws.disconnect();
 * ```
 */
export class DevSecOpsWebSocket {
  private url: string;
  private ws: WebSocket | null = null;
  private handlers: Map<WebSocketEventType, Set<WebSocketEventHandler>> = new Map();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 1000;
  private mockInterval: NodeJS.Timeout | null = null;

  /**
   * Create a new WebSocket client
   * 
   * @param url - WebSocket server URL (default: ws://localhost:3000/ws)
   */
  constructor(url: string = 'ws://localhost:3000/ws') {
    this.url = url;
  }

  /**
   * Connect to the WebSocket server
   * 
   * @returns Promise that resolves when connected
   * @throws Error if connection fails after retries
   */
  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        // NOTE: Replace with real WebSocket connection
        // this.ws = new WebSocket(this.url);
        // this.ws.onopen = () => resolve();
        // this.ws.onmessage = (event) => this.handleMessage(event.data);
        // this.ws.onerror = (error) => reject(error);

        // Mock connection
        console.warn('[WebSocket] Using mock connection');
        this.startMockUpdates();
        resolve();
      } catch (error) {
        reject(error);
      }
    });
  }

  /**
   * Disconnect from the WebSocket server
   * 
   * Cleans up event handlers and closes the connection.
   */
  disconnect(): void {
    if (this.mockInterval) {
      clearInterval(this.mockInterval);
      this.mockInterval = null;
    }
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  /**
   * Subscribe to a specific event type
   * 
   * @param type - Event type to listen for
   * @param handler - Function to call when event is received
   * @example
   * ```ts
   * ws.on('item:updated', (event) => {
   *   console.log('Item updated:', event.data);
   * });
   * ```
   */
  on(type: WebSocketEventType, handler: WebSocketEventHandler): void {
    if (!this.handlers.has(type)) {
      this.handlers.set(type, new Set());
    }
    this.handlers.get(type)!.add(handler);
  }

  /**
   * Unsubscribe from a specific event type
   * 
   * @param type - Event type to stop listening for
   * @param handler - Handler function to remove
   */
  off(type: WebSocketEventType, handler: WebSocketEventHandler): void {
    this.handlers.get(type)?.delete(handler);
  }

  /**
   * Emit an event to all registered handlers
   * 
   * @param type - Event type
   * @param data - Event data
   * @private
   */
  private emit(type: WebSocketEventType, data: Record<string, unknown>): void {
    const event: WebSocketEvent = {
      type,
      timestamp: new Date(),
      data,
    };

    const handlers = this.handlers.get(type);
    if (handlers) {
      handlers.forEach(handler => handler(event));
    }
  }

  /**
   * Handle incoming WebSocket message
   * 
   * @param message - Raw message string
   * @private
   */
  private handleMessage(message: string): void {
    try {
      const event = JSON.parse(message);
      this.emit(event.type, event.data);
    } catch (error) {
      console.warn('[WebSocket] Failed to parse message:', error);
    }
  }

  /**
   * Start sending mock events for development
   * 
   * Emits random events every 5 seconds to simulate real-time updates.
   * Replace with real WebSocket connection in production.
   * 
   * @private
   */
  private startMockUpdates(): void {
    this.mockInterval = setInterval(() => {
      const events: Array<[WebSocketEventType, Record<string, unknown>]> = [
        ['item:updated', { itemId: '1', status: 'in-progress' }],
        ['status:changed', { phaseId: 'development', status: 'active' }],
        ['notification:alert', { severity: 'info', message: 'New update available' }],
      ];

      const randomEvent = events[Math.floor(Math.random() * events.length)];
      this.emit(randomEvent[0], randomEvent[1]);
    }, 5000); // Emit mock event every 5 seconds
  }

  /**
   * Check if WebSocket is currently connected
   * 
   * @returns True if connected, false otherwise
   */
  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN || this.mockInterval !== null;
  }
}

// Export singleton instance
export const devsecopsWebSocket = new DevSecOpsWebSocket();
