/**
 * Extension Service - Integration with Browser Extension
 *
 * This service provides methods to connect to and communicate with
 * the DCMaar browser extension for capturing browser data, network
 * requests, and user interactions.
 */

export interface ExtensionConfig {
  wsPort: number;
  reconnectInterval?: number;
  maxReconnectAttempts?: number;
}

export interface BrowserEvent {
  type: 'navigation' | 'request' | 'response' | 'dom' | 'performance' | 'error' | 'scriptResult' | 'tabs' | 'screenshot' | 'networkRequests';
  timestamp: number;
  data: any;
  source?: string;
}

export interface NetworkRequest {
  id: string;
  url: string;
  method: string;
  headers: Record<string, string>;
  body?: any;
  timestamp: number;
}

export interface NetworkResponse {
  id: string;
  status: number;
  statusText: string;
  headers: Record<string, string>;
  body?: any;
  duration: number;
  timestamp: number;
}

export interface PerformanceMetrics {
  loadTime: number;
  domContentLoaded: number;
  firstPaint: number;
  firstContentfulPaint: number;
  largestContentfulPaint: number;
  cumulativeLayoutShift: number;
  firstInputDelay: number;
  timeToInteractive: number;
  latency?: number;
}

export interface ExtensionStatus {
  connected: boolean;
  version?: string;
  browser?: string;
  tabsCount?: number;
}

export class ExtensionService {
  private config: ExtensionConfig;
  private ws: WebSocket | null = null;
  private reconnectAttempts: number = 0;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private eventCallbacks: Map<string, Set<(event: BrowserEvent) => void>> =
    new Map();
  private statusCallback: ((status: ExtensionStatus) => void) | null = null;

  constructor(config: ExtensionConfig) {
    this.config = {
      reconnectInterval: 5000,
      maxReconnectAttempts: 10,
      ...config,
    };
  }

  /**
   * Connect to the extension WebSocket server
   */
  async connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        const wsUrl = `ws://localhost:${this.config.wsPort}`;
        console.log(`Connecting to extension at ${wsUrl}...`);

        this.ws = new WebSocket(wsUrl);

        this.ws.onopen = () => {
          console.log('Connected to browser extension');
          this.reconnectAttempts = 0;
          this.updateStatus({ connected: true });

          // Request extension info
          this.send({ type: 'getInfo' });

          resolve();
        };

        this.ws.onmessage = (event) => {
          this.handleMessage(event.data);
        };

        this.ws.onerror = (error) => {
          console.error('Extension WebSocket error:', error);
          reject(error);
        };

        this.ws.onclose = () => {
          console.log('Disconnected from browser extension');
          this.ws = null;
          this.updateStatus({ connected: false });

          // Attempt reconnection
          this.attemptReconnect();
        };
      } catch (error) {
        console.error('Failed to connect to extension:', error);
        reject(error);
      }
    });
  }

  /**
   * Disconnect from extension
   */
  disconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }

    this.updateStatus({ connected: false });
  }

  /**
   * Check if connected to extension
   */
  isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN;
  }

  /**
   * Subscribe to browser events
   */
  on(eventType: string, callback: (event: BrowserEvent) => void): () => void {
    if (!this.eventCallbacks.has(eventType)) {
      this.eventCallbacks.set(eventType, new Set());
    }

    this.eventCallbacks.get(eventType)!.add(callback);

    // Return unsubscribe function
    return () => {
      const callbacks = this.eventCallbacks.get(eventType);
      if (callbacks) {
        callbacks.delete(callback);
        if (callbacks.size === 0) {
          this.eventCallbacks.delete(eventType);
        }
      }
    };
  }

  /**
   * Subscribe to connection status changes
   */
  onStatusChange(callback: (status: ExtensionStatus) => void): void {
    this.statusCallback = callback;
  }

  /**
   * Request current browser tabs
   */
  async getTabs(): Promise<any[]> {
    if (!this.isConnected()) {
      throw new Error('Not connected to extension');
    }

    return new Promise((resolve) => {
      const handler = (event: BrowserEvent) => {
        if (event.type === 'tabs') {
          resolve(event.data);
          this.off('tabs', handler);
        }
      };

      this.on('tabs', handler);
      this.send({ type: 'getTabs' });

      // Timeout after 5 seconds
      setTimeout(() => {
        this.off('tabs', handler);
        resolve([]);
      }, 5000);
    });
  }

  /**
   * Capture screenshot of current tab
   */
  async captureScreenshot(): Promise<string> {
    if (!this.isConnected()) {
      throw new Error('Not connected to extension');
    }

    return new Promise((resolve, reject) => {
      const handler = (event: BrowserEvent) => {
        if (event.type === 'screenshot') {
          resolve(event.data.dataUrl);
          this.off('screenshot', handler);
        }
      };

      this.on('screenshot', handler);
      this.send({ type: 'captureScreenshot' });

      // Timeout after 10 seconds
      setTimeout(() => {
        this.off('screenshot', handler);
        reject(new Error('Screenshot capture timeout'));
      }, 10000);
    });
  }

  /**
   * Get network requests history
   */
  async getNetworkRequests(filter?: {
    startTime?: number;
    endTime?: number;
    url?: string;
  }): Promise<NetworkRequest[]> {
    if (!this.isConnected()) {
      throw new Error('Not connected to extension');
    }

    return new Promise((resolve) => {
      const handler = (event: BrowserEvent) => {
        if (event.type === 'networkRequests') {
          resolve(event.data);
          this.off('networkRequests', handler);
        }
      };

      this.on('networkRequests', handler);
      this.send({ type: 'getNetworkRequests', filter });

      // Timeout after 5 seconds
      setTimeout(() => {
        this.off('networkRequests', handler);
        resolve([]);
      }, 5000);
    });
  }

  /**
   * Get performance metrics for current page
   */
  async getPerformanceMetrics(): Promise<PerformanceMetrics | null> {
    if (!this.isConnected()) {
      throw new Error('Not connected to extension');
    }

    return new Promise((resolve) => {
      const handler = (event: BrowserEvent) => {
        if (event.type === 'performance') {
          resolve(event.data);
          this.off('performance', handler);
        }
      };

      this.on('performance', handler);
      this.send({ type: 'getPerformanceMetrics' });

      // Timeout after 5 seconds
      setTimeout(() => {
        this.off('performance', handler);
        resolve(null);
      }, 5000);
    });
  }

  /**
   * Start monitoring a specific tab
   */
  async startMonitoring(tabId: number): Promise<void> {
    if (!this.isConnected()) {
      throw new Error('Not connected to extension');
    }

    this.send({ type: 'startMonitoring', tabId });
  }

  /**
   * Stop monitoring a tab
   */
  async stopMonitoring(tabId: number): Promise<void> {
    if (!this.isConnected()) {
      throw new Error('Not connected to extension');
    }

    this.send({ type: 'stopMonitoring', tabId });
  }

  /**
   * Execute script in a tab
   */
  async executeScript(tabId: number, code: string): Promise<unknown> {
    if (!this.isConnected()) {
      throw new Error('Not connected to extension');
    }

    return new Promise((resolve, reject) => {
      const messageId = `exec-${Date.now()}`;

      // Set up one-time listener for response
      const responseHandler = (event: BrowserEvent) => {
        if (event.type === 'scriptResult' && event.data.messageId === messageId) {
          this.off('scriptResult', responseHandler);
          if (event.data.error) {
            reject(new Error(event.data.error));
          } else {
            resolve(event.data.result);
          }
        }
      };

      this.on('scriptResult', responseHandler);

      // Send execute request
      this.send({
        type: 'executeScript',
        messageId,
        tabId,
        code
      });

      // Timeout after 10 seconds
      setTimeout(() => {
        this.off('scriptResult', responseHandler);
        reject(new Error('Script execution timed out'));
      }, 10000);
    });
  }

  // Private methods

  private send(message: unknown): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    } else {
      console.warn('Cannot send message: WebSocket not connected');
    }
  }

  private handleMessage(data: string): void {
    try {
      const message = JSON.parse(data);

      // Handle status updates
      if (message.type === 'info') {
        this.updateStatus({
          connected: true,
          version: message.data.version,
          browser: message.data.browser,
          tabsCount: message.data.tabsCount,
        });
        return;
      }

      // Create browser event
      const event: BrowserEvent = {
        type: message.type,
        timestamp: message.timestamp || Date.now(),
        data: message.data,
      };

      // Notify all callbacks for this event type
      const callbacks = this.eventCallbacks.get(event.type);
      if (callbacks) {
        callbacks.forEach((callback) => callback(event));
      }

      // Notify generic event listeners
      const allCallbacks = this.eventCallbacks.get('*');
      if (allCallbacks) {
        allCallbacks.forEach((callback) => callback(event));
      }
    } catch (error) {
      console.error('Failed to parse extension message:', error);
    }
  }

  private updateStatus(status: ExtensionStatus): void {
    if (this.statusCallback) {
      this.statusCallback(status);
    }
  }

  private attemptReconnect(): void {
    if (this.reconnectAttempts >= (this.config.maxReconnectAttempts || 10)) {
      console.error('Max reconnection attempts reached');
      return;
    }

    this.reconnectAttempts++;
    const delay = this.config.reconnectInterval || 5000;

    console.log(
      `Attempting to reconnect to extension in ${delay}ms (attempt ${this.reconnectAttempts})...`
    );

    this.reconnectTimer = setTimeout(() => {
      this.connect().catch((error) => {
        console.error('Reconnection failed:', error);
      });
    }, delay);
  }

  private off(eventType: string, callback: (event: BrowserEvent) => void): void {
    const callbacks = this.eventCallbacks.get(eventType);
    if (callbacks) {
      callbacks.delete(callback);
    }
  }
}

// Singleton instance
let extensionServiceInstance: ExtensionService | null = null;

/**
 * Get or create the extension service instance
 */
export function getExtensionService(config?: ExtensionConfig): ExtensionService {
  if (!extensionServiceInstance && config) {
    extensionServiceInstance = new ExtensionService(config);
  } else if (!extensionServiceInstance) {
    // Use default config from environment
    extensionServiceInstance = new ExtensionService({
      wsPort: parseInt(import.meta.env.VITE_EXTENSION_WS_PORT || '9001'),
    });
  }

  return extensionServiceInstance;
}

export default ExtensionService;
