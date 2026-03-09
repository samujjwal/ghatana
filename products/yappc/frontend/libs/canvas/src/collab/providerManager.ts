/**
 * Provider Management - WebSocket/WebRTC provider management with auth
 * 
 * Implements provider selection, failover, and JWT authentication
 * for real-time collaboration providers.
 */

/**
 * Provider types
 */
export type ProviderType = 'websocket' | 'webrtc' | 'none';

/**
 * Provider status
 */
export type ProviderStatus = 'disconnected' | 'connecting' | 'connected' | 'reconnecting' | 'failed';

/**
 * Connection state
 */
export interface ConnectionState {
  /** Current provider type */
  provider: ProviderType;
  /** Connection status */
  status: ProviderStatus;
  /** Connected timestamp */
  connectedAt?: number;
  /** Last error */
  lastError?: string;
  /** Reconnect attempts */
  reconnectAttempts: number;
  /** Is failover active */
  failoverActive: boolean;
}

/**
 * Provider configuration
 */
export interface ProviderConfig {
  /** Preferred provider type */
  preferredProvider: ProviderType;
  /** Enable WebSocket */
  enableWebSocket: boolean;
  /** Enable WebRTC fallback */
  enableWebRTC: boolean;
  /** WebSocket URL */
  websocketUrl?: string;
  /** WebRTC signaling server URL */
  webrtcSignalingUrl?: string;
  /** Enable automatic failover */
  enableFailover: boolean;
  /** Max reconnect attempts before failover */
  maxReconnectAttempts: number;
  /** Reconnect delay in ms */
  reconnectDelay: number;
  /** Enable JWT authentication */
  enableAuth: boolean;
  /** JWT token provider function */
  getToken?: () => Promise<string> | string;
  /** Token refresh interval in ms */
  tokenRefreshInterval?: number;
}

/**
 * JWT payload structure
 */
export interface JWTPayload {
  /** User ID */
  sub: string;
  /** Issued at timestamp */
  iat: number;
  /** Expiration timestamp */
  exp: number;
  /** Room/document ID */
  room?: string;
  /** Permissions */
  permissions?: string[];
}

/**
 * Provider event types
 */
export type ProviderEventType =
  | 'connecting'
  | 'connected'
  | 'disconnected'
  | 'reconnecting'
  | 'failover'
  | 'auth_success'
  | 'auth_failure'
  | 'error';

/**
 * Provider event
 */
export interface ProviderEvent {
  /** Event type */
  type: ProviderEventType;
  /** Timestamp */
  timestamp: number;
  /** Provider type */
  provider: ProviderType;
  /** Additional data */
  data?: Record<string, unknown>;
  /** Error message */
  error?: string;
}

/**
 * Connection statistics
 */
export interface ConnectionStatistics {
  /** Total connection attempts */
  totalConnections: number;
  /** Successful connections */
  successfulConnections: number;
  /** Failed connections */
  failedConnections: number;
  /** Failover count */
  failoverCount: number;
  /** Current uptime in ms */
  currentUptime?: number;
  /** Average connection time in ms */
  averageConnectionTime: number;
  /** WebSocket usage count */
  websocketUsage: number;
  /** WebRTC usage count */
  webrtcUsage: number;
}

/**
 * Provider Manager
 */
export class ProviderManager {
  private config: ProviderConfig;
  private state: ConnectionState;
  private events: ProviderEvent[] = [];
  private listeners: Array<(event: ProviderEvent) => void> = [];
  private reconnectTimer?: ReturnType<typeof setTimeout>;
  private tokenRefreshTimer?: ReturnType<typeof setInterval>;
  private currentToken?: string;
  private connectionAttemptStart?: number;
  private statistics: ConnectionStatistics;

  /**
   *
   */
  constructor(config: Partial<ProviderConfig> = {}) {
    this.config = {
      preferredProvider: 'websocket',
      enableWebSocket: true,
      enableWebRTC: false,
      enableFailover: true,
      maxReconnectAttempts: 5,
      reconnectDelay: 1000,
      enableAuth: true,
      tokenRefreshInterval: 15 * 60 * 1000, // 15 minutes
      ...config,
    };

    this.state = {
      provider: 'none',
      status: 'disconnected',
      reconnectAttempts: 0,
      failoverActive: false,
    };

    this.statistics = {
      totalConnections: 0,
      successfulConnections: 0,
      failedConnections: 0,
      failoverCount: 0,
      averageConnectionTime: 0,
      websocketUsage: 0,
      webrtcUsage: 0,
    };
  }

  /**
   * Connect to provider
   */
  async connect(roomId?: string): Promise<boolean> {
    if (this.state.status === 'connected' || this.state.status === 'connecting') {
      return false;
    }

    // Determine which provider to use
    const provider = this.selectProvider();
    if (provider === 'none') {
      this.emitEvent({
        type: 'error',
        timestamp: Date.now(),
        provider: 'none',
        error: 'No providers enabled',
      });
      return false;
    }

    // Authenticate if enabled
    if (this.config.enableAuth) {
      const authSuccess = await this.authenticate(roomId);
      if (!authSuccess) {
        return false;
      }
    }

    // Start connection attempt
    this.connectionAttemptStart = Date.now();
    this.state.provider = provider;
    this.state.status = 'connecting';
    this.statistics.totalConnections++;

    this.emitEvent({
      type: 'connecting',
      timestamp: Date.now(),
      provider,
      data: { roomId },
    });

    // Simulate connection (in real implementation, this would connect to actual provider)
    const connected = await this.attemptConnection(provider, roomId);

    if (connected) {
      this.handleConnectionSuccess();
      return true;
    } else {
      this.handleConnectionFailure('Connection failed');
      return false;
    }
  }

  /**
   * Disconnect from provider
   */
  disconnect(): void {
    if (this.state.status === 'disconnected') {
      return;
    }

    this.clearTimers();

    const previousProvider = this.state.provider;

    this.state.status = 'disconnected';
    this.state.provider = 'none';
    this.state.connectedAt = undefined;
    this.state.reconnectAttempts = 0;
    this.state.failoverActive = false;

    this.emitEvent({
      type: 'disconnected',
      timestamp: Date.now(),
      provider: previousProvider,
    });
  }

  /**
   * Get current connection state
   */
  getState(): ConnectionState {
    return { ...this.state };
  }

  /**
   * Get configuration
   */
  getConfig(): ProviderConfig {
    return { ...this.config };
  }

  /**
   * Update configuration
   */
  updateConfig(updates: Partial<ProviderConfig>): void {
    Object.assign(this.config, updates);
  }

  /**
   * Get connection statistics
   */
  getStatistics(): ConnectionStatistics {
    const stats = { ...this.statistics };

    if (this.state.status === 'connected' && this.state.connectedAt) {
      stats.currentUptime = Date.now() - this.state.connectedAt;
    }

    return stats;
  }

  /**
   * Get event history
   */
  getEventHistory(filter?: {
    type?: ProviderEventType;
    provider?: ProviderType;
    startDate?: number;
    endDate?: number;
  }): ProviderEvent[] {
    let events = [...this.events];

    if (filter) {
      if (filter.type) {
        events = events.filter(e => e.type === filter.type);
      }

      if (filter.provider) {
        events = events.filter(e => e.provider === filter.provider);
      }

      if (filter.startDate) {
        events = events.filter(e => e.timestamp >= filter.startDate!);
      }

      if (filter.endDate) {
        events = events.filter(e => e.timestamp <= filter.endDate!);
      }
    }

    return events.sort((a, b) => b.timestamp - a.timestamp);
  }

  /**
   * Add event listener
   */
  onEvent(listener: (event: ProviderEvent) => void): () => void {
    this.listeners.push(listener);

    // Return unsubscribe function
    return () => {
      const index = this.listeners.indexOf(listener);
      if (index >= 0) {
        this.listeners.splice(index, 1);
      }
    };
  }

  /**
   * Validate JWT token
   */
  validateToken(token: string): { valid: boolean; payload?: JWTPayload; error?: string } {
    try {
      // Parse JWT (simplified - in production use proper JWT library)
      const parts = token.split('.');
      if (parts.length !== 3) {
        return { valid: false, error: 'Invalid token format' };
      }

      // Decode payload
      const payload = JSON.parse(atob(parts[1])) as JWTPayload;

      // Check expiration
      const now = Math.floor(Date.now() / 1000);
      if (payload.exp && payload.exp < now) {
        return { valid: false, error: 'Token expired' };
      }

      // Check issued time
      if (payload.iat && payload.iat > now + 60) {
        return { valid: false, error: 'Token issued in future' };
      }

      return { valid: true, payload };
    } catch (error) {
      return { valid: false, error: 'Token parsing failed' };
    }
  }

  /**
   * Select provider based on configuration
   */
  private selectProvider(): ProviderType {
    // If failover is active, try alternate provider
    if (this.state.failoverActive) {
      if (this.config.preferredProvider === 'websocket' && this.config.enableWebRTC) {
        return 'webrtc';
      }
      if (this.config.preferredProvider === 'webrtc' && this.config.enableWebSocket) {
        return 'websocket';
      }
    }

    // Use preferred provider if enabled
    if (this.config.preferredProvider === 'websocket' && this.config.enableWebSocket) {
      return 'websocket';
    }

    if (this.config.preferredProvider === 'webrtc' && this.config.enableWebRTC) {
      return 'webrtc';
    }

    // Fallback to any enabled provider
    if (this.config.enableWebSocket) {
      return 'websocket';
    }

    if (this.config.enableWebRTC) {
      return 'webrtc';
    }

    return 'none';
  }

  /**
   * Authenticate with JWT token
   */
  private async authenticate(roomId?: string): Promise<boolean> {
    if (!this.config.getToken) {
      this.emitEvent({
        type: 'auth_failure',
        timestamp: Date.now(),
        provider: this.state.provider,
        error: 'No token provider configured',
      });
      return false;
    }

    try {
      // Get token
      const token = await this.config.getToken();
      this.currentToken = token;

      // Validate token
      const validation = this.validateToken(token);
      if (!validation.valid) {
        this.emitEvent({
          type: 'auth_failure',
          timestamp: Date.now(),
          provider: this.state.provider,
          error: validation.error,
        });
        return false;
      }

      // Verify room access if provided
      if (roomId && validation.payload?.room && validation.payload.room !== roomId) {
        this.emitEvent({
          type: 'auth_failure',
          timestamp: Date.now(),
          provider: this.state.provider,
          error: 'Token room mismatch',
        });
        return false;
      }

      this.emitEvent({
        type: 'auth_success',
        timestamp: Date.now(),
        provider: this.state.provider,
        data: { userId: validation.payload?.sub },
      });

      // Start token refresh if configured
      if (this.config.tokenRefreshInterval) {
        this.startTokenRefresh();
      }

      return true;
    } catch (error) {
      this.emitEvent({
        type: 'auth_failure',
        timestamp: Date.now(),
        provider: this.state.provider,
        error: error instanceof Error ? error.message : 'Authentication failed',
      });
      return false;
    }
  }

  /**
   * Attempt connection to provider
   */
  private async attemptConnection(provider: ProviderType, roomId?: string): Promise<boolean> {
    // Simulate connection attempt (in real implementation, connect to actual provider)
    // For testing: use synchronous random instead of setTimeout
    // This makes the code testable with fake timers
    await Promise.resolve(); // Make it async for consistent behavior
    
    // Simulate random failures for testing
    const success = Math.random() > 0.1; // 90% success rate
    return success;
  }

  /**
   * Handle successful connection
   */
  private handleConnectionSuccess(): void {
    const connectionTime = this.connectionAttemptStart
      ? Date.now() - this.connectionAttemptStart
      : 0;

    this.state.status = 'connected';
    this.state.connectedAt = Date.now();
    this.state.reconnectAttempts = 0;

    this.statistics.successfulConnections++;
    if (this.state.provider === 'websocket') {
      this.statistics.websocketUsage++;
    } else if (this.state.provider === 'webrtc') {
      this.statistics.webrtcUsage++;
    }

    // Update average connection time
    const totalSuccessful = this.statistics.successfulConnections;
    this.statistics.averageConnectionTime =
      (this.statistics.averageConnectionTime * (totalSuccessful - 1) + connectionTime) /
      totalSuccessful;

    this.emitEvent({
      type: 'connected',
      timestamp: Date.now(),
      provider: this.state.provider,
      data: { connectionTime },
    });
  }

  /**
   * Handle connection failure
   */
  private handleConnectionFailure(error: string): void {
    this.state.lastError = error;
    this.statistics.failedConnections++;

    // Check if we should attempt reconnect
    if (this.state.reconnectAttempts < this.config.maxReconnectAttempts) {
      this.state.status = 'reconnecting';
      this.state.reconnectAttempts++;

      this.emitEvent({
        type: 'reconnecting',
        timestamp: Date.now(),
        provider: this.state.provider,
        data: {
          attempt: this.state.reconnectAttempts,
          maxAttempts: this.config.maxReconnectAttempts,
        },
      });

      // Schedule reconnect
      this.reconnectTimer = setTimeout(() => {
        this.connect();
      }, this.config.reconnectDelay * this.state.reconnectAttempts);
    } else {
      // Max reconnect attempts reached, try failover
      if (this.config.enableFailover && !this.state.failoverActive) {
        this.initiateFailover();
      } else {
        this.state.status = 'failed';

        this.emitEvent({
          type: 'error',
          timestamp: Date.now(),
          provider: this.state.provider,
          error: 'Max reconnect attempts exceeded',
        });
      }
    }
  }

  /**
   * Initiate failover to alternate provider
   */
  private initiateFailover(): void {
    this.state.failoverActive = true;
    this.state.reconnectAttempts = 0;
    this.statistics.failoverCount++;

    const previousProvider = this.state.provider;
    const newProvider = this.selectProvider();

    this.emitEvent({
      type: 'failover',
      timestamp: Date.now(),
      provider: previousProvider,
      data: {
        from: previousProvider,
        to: newProvider,
      },
    });

    // Attempt connection with failover provider
    this.connect();
  }

  /**
   * Start token refresh timer
   */
  private startTokenRefresh(): void {
    if (this.tokenRefreshTimer) {
      clearInterval(this.tokenRefreshTimer);
    }

    this.tokenRefreshTimer = setInterval(async () => {
      if (this.state.status === 'connected' && this.config.getToken) {
        try {
          const token = await this.config.getToken();
          this.currentToken = token;

          const validation = this.validateToken(token);
          if (!validation.valid) {
            this.disconnect();
          }
        } catch (error) {
          // Token refresh failed, disconnect
          this.disconnect();
        }
      }
    }, this.config.tokenRefreshInterval);
  }

  /**
   * Clear all timers
   */
  private clearTimers(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = undefined;
    }

    if (this.tokenRefreshTimer) {
      clearInterval(this.tokenRefreshTimer);
      this.tokenRefreshTimer = undefined;
    }
  }

  /**
   * Emit provider event
   */
  private emitEvent(event: ProviderEvent): void {
    this.events.push(event);

    // Keep only last 1000 events
    if (this.events.length > 1000) {
      this.events = this.events.slice(-1000);
    }

    // Notify listeners
    for (const listener of this.listeners) {
      listener(event);
    }
  }
}

/**
 * Create provider manager
 */
export function createProviderManager(config?: Partial<ProviderConfig>): ProviderManager {
  return new ProviderManager(config);
}
