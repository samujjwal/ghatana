/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Frontend - Lifecycle WebSocket Service
 * 
 * Provides real-time synchronization of lifecycle state between backend and UI.
 * Integrates with existing lifecycle API hooks to provide automatic updates.
 */

import React from 'react';

export interface LifecycleStateUpdate {
  type: 'phase_transition' | 'task_status' | 'agent_result';
  projectId: string;
  timestamp: string;
  data: unknown;
}

export interface WebSocketConfig {
  url: string;
  reconnectIntervalMs: number;
  maxReconnectAttempts: number;
  heartbeatIntervalMs: number;
}

const DEFAULT_CONFIG: WebSocketConfig = {
  url: `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/lifecycle`,
  reconnectIntervalMs: 5000,
  maxReconnectAttempts: 10,
  heartbeatIntervalMs: 30000,
};

/**
 * LifecycleWebSocketService
 * 
 * Manages WebSocket connection for real-time lifecycle state updates.
 * Automatically reconnects on connection loss and handles heartbeat.
 * 
 * @example
 * ```tsx
 * const wsService = new LifecycleWebSocketService();
 * wsService.connect('project-123');
 * wsService.onUpdate((update) => {
 *   // Handle real-time lifecycle update
 * });
 * ```
 */
export class LifecycleWebSocketService {
  private config: WebSocketConfig;
  private ws: WebSocket | null = null;
  private projectId: string | null = null;
  private reconnectAttempts = 0;
  private heartbeatTimer: NodeJS.Timeout | null = null;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private updateHandlers: Array<(update: LifecycleStateUpdate) => void> = [];
  private connectionHandlers: Array<(connected: boolean) => void> = [];

  constructor(config: Partial<WebSocketConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
  }

  /**
   * Connect to WebSocket for lifecycle updates
   */
  connect(projectId: string): void {
    if (this.projectId === projectId && this.isConnected()) {
      return; // Already connected to this project
    }

    this.disconnect(); // Clean up existing connection
    this.projectId = projectId;

    const wsUrl = `${this.config.url}?projectId=${encodeURIComponent(projectId)}`;
    this.ws = new WebSocket(wsUrl);

    this.ws.onopen = this.handleOpen.bind(this);
    this.ws.onmessage = this.handleMessage.bind(this);
    this.ws.onclose = this.handleClose.bind(this);
    this.ws.onerror = this.handleError.bind(this);
  }

  /**
   * Disconnect from WebSocket
   */
  disconnect(): void {
    this.clearTimers();
    
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    
    this.projectId = null;
    this.reconnectAttempts = 0;
  }

  /**
   * Check if WebSocket is connected
   */
  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  /**
   * Register handler for lifecycle updates
   */
  onUpdate(handler: (update: LifecycleStateUpdate) => void): () => void {
    this.updateHandlers.push(handler);
    
    // Return unsubscribe function
    return () => {
      const index = this.updateHandlers.indexOf(handler);
      if (index >= 0) {
        this.updateHandlers.splice(index, 1);
      }
    };
  }

  /**
   * Register handler for connection state changes
   */
  onConnectionChange(handler: (connected: boolean) => void): () => void {
    this.connectionHandlers.push(handler);
    
    // Return unsubscribe function
    return () => {
      const index = this.connectionHandlers.indexOf(handler);
      if (index >= 0) {
        this.connectionHandlers.splice(index, 1);
      }
    };
  }

  private handleOpen(): void {
    console.log('Lifecycle WebSocket connected');
    this.reconnectAttempts = 0;
    this.startHeartbeat();
    this.notifyConnectionChange(true);
  }

  private handleMessage(event: MessageEvent): void {
    try {
      const update: LifecycleStateUpdate = JSON.parse(event.data);
      
      // Validate update structure
      if (!update.type || !update.projectId || !update.timestamp) {
        console.warn('Invalid lifecycle update format:', update);
        return;
      }

      // Only process updates for current project
      if (update.projectId !== this.projectId) {
        return;
      }

      this.notifyUpdate(update);
    } catch (error) {
      console.error('Failed to parse lifecycle update:', error);
    }
  }

  private handleClose(event: CloseEvent): void {
    console.log('Lifecycle WebSocket disconnected:', event.code, event.reason);
    this.clearTimers();
    this.notifyConnectionChange(false);
    
    // Attempt reconnection if not a normal closure
    if (event.code !== 1000 && this.reconnectAttempts < this.config.maxReconnectAttempts) {
      this.scheduleReconnect();
    }
  }

  private handleError(event: Event): void {
    console.error('Lifecycle WebSocket error:', event);
  }

  private startHeartbeat(): void {
    this.clearHeartbeat();
    
    this.heartbeatTimer = setInterval(() => {
      if (this.isConnected()) {
        this.ws?.send(JSON.stringify({ type: 'ping', timestamp: Date.now() }));
      }
    }, this.config.heartbeatIntervalMs);
  }

  private clearHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private clearTimers(): void {
    this.clearHeartbeat();
    
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private scheduleReconnect(): void {
    this.reconnectAttempts++;
    
    console.log(`Scheduling reconnect attempt ${this.reconnectAttempts}/${this.config.maxReconnectAttempts}`);
    
    this.reconnectTimer = setTimeout(() => {
      if (this.projectId) {
        this.connect(this.projectId);
      }
    }, this.config.reconnectIntervalMs);
  }

  private notifyUpdate(update: LifecycleStateUpdate): void {
    this.updateHandlers.forEach(handler => {
      try {
        handler(update);
      } catch (error) {
        console.error('Error in lifecycle update handler:', error);
      }
    });
  }

  private notifyConnectionChange(connected: boolean): void {
    this.connectionHandlers.forEach(handler => {
      try {
        handler(connected);
      } catch (error) {
        console.error('Error in connection change handler:', error);
      }
    });
  }
}

/**
 * Global WebSocket service instance
 */
export const lifecycleWebSocketService = new LifecycleWebSocketService();

/**
 * React hook for WebSocket lifecycle updates
 */
export function useLifecycleWebSocket(projectId: string) {
  const [isConnected, setIsConnected] = React.useState(false);
  const [lastUpdate, setLastUpdate] = React.useState<LifecycleStateUpdate | null>(null);

  React.useEffect(() => {
    if (!projectId) return;

    // Connect to WebSocket
    lifecycleWebSocketService.connect(projectId);

    // Subscribe to updates
    const unsubscribeUpdates = lifecycleWebSocketService.onUpdate((update) => {
      setLastUpdate(update);
    });

    // Subscribe to connection changes
    const unsubscribeConnection = lifecycleWebSocketService.onConnectionChange((connected) => {
      setIsConnected(connected);
    });

    // Cleanup on unmount
    return () => {
      unsubscribeUpdates();
      unsubscribeConnection();
      lifecycleWebSocketService.disconnect();
    };
  }, [projectId]);

  return {
    isConnected,
    lastUpdate,
    connect: () => lifecycleWebSocketService.connect(projectId),
    disconnect: () => lifecycleWebSocketService.disconnect(),
  };
}
