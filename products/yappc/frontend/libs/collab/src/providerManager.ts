export type ProviderType = 'websocket' | 'webrtc' | 'none';

export type ProviderStatus =
  | 'disconnected'
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'failed';

export interface ConnectionState {
  provider: ProviderType;
  status: ProviderStatus;
  connectedAt?: number;
  lastError?: string;
  reconnectAttempts: number;
  failoverActive: boolean;
}

export interface ProviderConfig {
  preferredProvider: ProviderType;
  enableWebSocket: boolean;
  enableWebRTC: boolean;
  websocketUrl?: string;
  webrtcSignalingUrl?: string;
  enableFailover: boolean;
  maxReconnectAttempts: number;
  reconnectDelay: number;
  enableAuth: boolean;
  getToken?: () => Promise<string> | string;
  tokenRefreshInterval?: number;
}

export interface JWTPayload {
  sub: string;
  iat: number;
  exp: number;
  room?: string;
  permissions?: string[];
}

export type ProviderEventType =
  | 'connecting'
  | 'connected'
  | 'disconnected'
  | 'reconnecting'
  | 'failover'
  | 'auth_success'
  | 'auth_failure'
  | 'error';

export interface ProviderEvent {
  type: ProviderEventType;
  timestamp: number;
  provider: ProviderType;
  data?: Record<string, unknown>;
  error?: string;
}

export interface ConnectionStatistics {
  totalConnections: number;
  successfulConnections: number;
  failedConnections: number;
  failoverCount: number;
  currentUptime?: number;
  averageConnectionTime: number;
  websocketUsage: number;
  webrtcUsage: number;
}

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

  constructor(config: Partial<ProviderConfig> = {}) {
    this.config = {
      preferredProvider: 'websocket',
      enableWebSocket: true,
      enableWebRTC: false,
      enableFailover: true,
      maxReconnectAttempts: 5,
      reconnectDelay: 1000,
      enableAuth: true,
      tokenRefreshInterval: 15 * 60 * 1000,
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

  async connect(roomId?: string): Promise<boolean> {
    if (this.state.status === 'connected' || this.state.status === 'connecting') {
      return false;
    }

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

    if (this.config.enableAuth) {
      const authSuccess = await this.authenticate(roomId);
      if (!authSuccess) {
        return false;
      }
    }

    this.connectionAttemptStart = Date.now();
    this.state.provider = provider;
    this.state.status = 'connecting';
    this.statistics.totalConnections += 1;

    this.emitEvent({
      type: 'connecting',
      timestamp: Date.now(),
      provider,
      data: { roomId },
    });

    const connected = await this.attemptConnection(provider, roomId);
    if (connected) {
      this.handleConnectionSuccess();
      return true;
    }

    this.handleConnectionFailure('Connection failed');
    return false;
  }

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

  getState(): ConnectionState {
    return { ...this.state };
  }

  getConfig(): ProviderConfig {
    return { ...this.config };
  }

  updateConfig(updates: Partial<ProviderConfig>): void {
    Object.assign(this.config, updates);
  }

  getStatistics(): ConnectionStatistics {
    const stats = { ...this.statistics };
    if (this.state.status === 'connected' && this.state.connectedAt) {
      stats.currentUptime = Date.now() - this.state.connectedAt;
    }
    return stats;
  }

  getEventHistory(filter?: {
    type?: ProviderEventType;
    provider?: ProviderType;
    startDate?: number;
    endDate?: number;
  }): ProviderEvent[] {
    let events = [...this.events];
    if (filter?.type) {
      events = events.filter((event) => event.type === filter.type);
    }
    if (filter?.provider) {
      events = events.filter((event) => event.provider === filter.provider);
    }
    if (filter?.startDate) {
      const startDate = filter.startDate;
      events = events.filter((event) => event.timestamp >= startDate);
    }
    if (filter?.endDate) {
      const endDate = filter.endDate;
      events = events.filter((event) => event.timestamp <= endDate);
    }
    return events;
  }

  subscribe(listener: (event: ProviderEvent) => void): () => void {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter((entry) => entry !== listener);
    };
  }

  private selectProvider(): ProviderType {
    if (this.config.preferredProvider === 'websocket' && this.config.enableWebSocket) {
      return 'websocket';
    }
    if (this.config.preferredProvider === 'webrtc' && this.config.enableWebRTC) {
      return 'webrtc';
    }
    if (this.config.enableWebSocket) {
      return 'websocket';
    }
    if (this.config.enableWebRTC) {
      return 'webrtc';
    }
    return 'none';
  }

  private async authenticate(roomId?: string): Promise<boolean> {
    if (!this.config.getToken) {
      return true;
    }

    try {
      const token = await this.config.getToken();
      if (!token) {
        throw new Error('Token provider returned empty token');
      }

      this.currentToken = token;
      this.emitEvent({
        type: 'auth_success',
        timestamp: Date.now(),
        provider: this.state.provider,
        data: { roomId },
      });

      if (this.config.tokenRefreshInterval) {
        this.tokenRefreshTimer = setInterval(() => {
          void this.authenticate(roomId);
        }, this.config.tokenRefreshInterval);
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

  private async attemptConnection(
    provider: ProviderType,
    _roomId?: string
  ): Promise<boolean> {
    await new Promise((resolve) => setTimeout(resolve, 100));
    return provider !== 'none';
  }

  private handleConnectionSuccess(): void {
    this.state.status = 'connected';
    this.state.connectedAt = Date.now();
    this.state.reconnectAttempts = 0;

    this.statistics.successfulConnections += 1;
    if (this.state.provider === 'websocket') {
      this.statistics.websocketUsage += 1;
    }
    if (this.state.provider === 'webrtc') {
      this.statistics.webrtcUsage += 1;
    }

    if (this.connectionAttemptStart) {
      const duration = Date.now() - this.connectionAttemptStart;
      const successfulConnections = this.statistics.successfulConnections;
      this.statistics.averageConnectionTime =
        ((this.statistics.averageConnectionTime * (successfulConnections - 1)) + duration) /
        successfulConnections;
    }

    this.emitEvent({
      type: 'connected',
      timestamp: Date.now(),
      provider: this.state.provider,
    });
  }

  private handleConnectionFailure(error: string): void {
    this.state.status = 'failed';
    this.state.lastError = error;
    this.statistics.failedConnections += 1;

    this.emitEvent({
      type: 'error',
      timestamp: Date.now(),
      provider: this.state.provider,
      error,
    });

    if (
      this.config.enableFailover &&
      this.state.reconnectAttempts >= this.config.maxReconnectAttempts
    ) {
      this.activateFailover();
      return;
    }

    this.scheduleReconnect();
  }

  private scheduleReconnect(): void {
    this.state.status = 'reconnecting';
    this.state.reconnectAttempts += 1;

    this.emitEvent({
      type: 'reconnecting',
      timestamp: Date.now(),
      provider: this.state.provider,
      data: { reconnectAttempts: this.state.reconnectAttempts },
    });

    this.reconnectTimer = setTimeout(() => {
      void this.connect();
    }, this.config.reconnectDelay);
  }

  private activateFailover(): void {
    const previousProvider = this.state.provider;
    const nextProvider = previousProvider === 'websocket' ? 'webrtc' : 'websocket';

    if (
      (nextProvider === 'websocket' && !this.config.enableWebSocket) ||
      (nextProvider === 'webrtc' && !this.config.enableWebRTC)
    ) {
      return;
    }

    this.state.provider = nextProvider;
    this.state.failoverActive = true;
    this.state.reconnectAttempts = 0;
    this.statistics.failoverCount += 1;

    this.emitEvent({
      type: 'failover',
      timestamp: Date.now(),
      provider: nextProvider,
      data: { previousProvider },
    });

    void this.connect();
  }

  private emitEvent(event: ProviderEvent): void {
    this.events.push(event);
    for (const listener of this.listeners) {
      listener(event);
    }
  }

  private clearTimers(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }
    if (this.tokenRefreshTimer) {
      clearInterval(this.tokenRefreshTimer);
    }
  }
}