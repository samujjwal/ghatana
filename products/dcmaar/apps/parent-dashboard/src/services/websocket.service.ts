import { io, type Socket } from 'socket.io-client';
import { authService } from './auth.service';

const WS_URL = import.meta.env.VITE_WS_URL || 'http://localhost:3001';

export type WebSocketEventType = 
  | 'policy_created'
  | 'policy_updated'
  | 'usage_data'
  | 'block_event'
  | 'device_status'
  | 'device_online'
  | 'device_offline';

export interface UsageEvent {
  usageSession: {
    id: string;
    device_id: string;
    item_name: string;
    session_type: string;
    duration_seconds: number;
    timestamp: string;
  };
  device: {
    id: string;
    name: string;
    type: string;
  };
}

export interface BlockEvent {
  blockEvent: {
    id: string;
    device_id: string;
    blocked_item: string;
    event_type: string;
    reason: string;
    timestamp: string;
  };
  device: {
    id: string;
    name: string;
    type: string;
  };
}

export interface PolicyEvent {
  policy: {
    id: string;
    name: string;
    policy_type: string;
    config: Record<string, unknown>;
    is_active: boolean;
  };
}

export interface DeviceStatusEvent {
  device: {
    id: string;
    name: string;
    type: string;
    status: 'online' | 'offline';
    last_seen: string;
  };
}

class WebSocketService {
  private socket: Socket | null = null;
  private eventHandlers: Map<WebSocketEventType, Set<(data: Record<string, unknown>) => void>> = new Map();

  connect(): void {
    const token = authService.getToken();
    if (!token) {
      console.error('No auth token available for WebSocket connection');
      return;
    }

    this.socket = io(WS_URL, {
      auth: { token },
      reconnection: true,
      reconnectionDelay: 1000,
      reconnectionAttempts: 5,
    });

    this.setupEventListeners();
  }

  private setupEventListeners(): void {
    if (!this.socket) return;

    this.socket.on('connect', () => {
      console.log('✅ WebSocket connected');
    });

    this.socket.on('disconnect', (reason) => {
      console.log('❌ WebSocket disconnected:', reason);
    });

    this.socket.on('error', (error) => {
      console.error('WebSocket error:', error);
    });

    // Setup handlers for all event types
    const eventTypes: WebSocketEventType[] = [
      'policy_created',
      'policy_updated',
      'usage_data',
      'block_event',
      'device_status',
      'device_online',
      'device_offline',
    ];

    eventTypes.forEach((eventType) => {
      this.socket?.on(eventType, (data: unknown) => {
        console.log(`📨 Received ${eventType}:`, data);
        this.notifyHandlers(eventType, data as Record<string, unknown>);
      });
    });
  }

  private notifyHandlers(eventType: WebSocketEventType, data: Record<string, unknown>): void {
    const handlers = this.eventHandlers.get(eventType);
    if (handlers) {
      handlers.forEach((handler) => handler(data));
    }
  }

  on(eventType: WebSocketEventType, handler: (data: Record<string, unknown>) => void): () => void {
    if (!this.eventHandlers.has(eventType)) {
      this.eventHandlers.set(eventType, new Set());
    }
    this.eventHandlers.get(eventType)!.add(handler);

    // Return unsubscribe function
    return () => {
      this.eventHandlers.get(eventType)?.delete(handler);
    };
  }

  disconnect(): void {
    if (this.socket) {
      this.socket.disconnect();
      this.socket = null;
    }
    this.eventHandlers.clear();
  }

  isConnected(): boolean {
    return this.socket?.connected ?? false;
  }
}

export const websocketService = new WebSocketService();
