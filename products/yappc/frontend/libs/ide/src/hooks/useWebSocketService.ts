/**
 * @ghatana/yappc-ide - WebSocket Service Hook
 * 
 * WebSocket service for real-time collaboration and communication.
 * Provides connection management, message handling, and event system.
 * 
 * @doc.type module
 * @doc.purpose WebSocket service for IDE collaboration
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * WebSocket connection states
 */
export type ConnectionState = 'connecting' | 'connected' | 'disconnected' | 'error';

/**
 * WebSocket message types
 */
export interface WebSocketMessage {
  type: string;
  data: unknown;
  timestamp: number;
  userId?: string;
  messageId?: string;
}

/**
 * WebSocket service configuration
 */
export interface WebSocketConfig {
  url: string;
  reconnectInterval?: number;
  maxReconnectAttempts?: number;
  heartbeatInterval?: number;
}

/**
 * WebSocket service hook
 */
export function useWebSocketService(config?: Partial<WebSocketConfig>) {
  const [connectionState, setConnectionState] = useState<ConnectionState>('disconnected');
  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const heartbeatIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const eventListenersRef = useRef<Map<string, Set<(data: unknown) => void>>>(new Map());
  const reconnectAttemptsRef = useRef(0);

  const defaultConfig: WebSocketConfig = {
    url: 'ws://localhost:7004/ws', // Default WebSocket URL
    reconnectInterval: 3000,
    maxReconnectAttempts: 5,
    heartbeatInterval: 30000,
    ...config,
  };

  /**
   * Connect to WebSocket
   */
  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      return;
    }

    setConnectionState('connecting');
    setError(null);

    try {
      const ws = new WebSocket(defaultConfig.url);
      wsRef.current = ws;

      ws.onopen = () => {
        setConnectionState('connected');
        setIsConnected(true);
        reconnectAttemptsRef.current = 0;

        // Start heartbeat
        if (defaultConfig.heartbeatInterval) {
          heartbeatIntervalRef.current = setInterval(() => {
            sendMessage('heartbeat', { timestamp: Date.now() });
          }, defaultConfig.heartbeatInterval);
        }
      };

      ws.onmessage = (event) => {
        try {
          const message: WebSocketMessage = JSON.parse(event.data);

          // Handle heartbeat responses
          if (message.type === 'heartbeat') {
            return;
          }

          // Notify listeners
          const listeners = eventListenersRef.current.get(message.type);
          if (listeners) {
            listeners.forEach(listener => listener(message.data));
          }
        } catch (err) {
          console.error('Failed to parse WebSocket message:', err);
        }
      };

      ws.onclose = (event) => {
        setIsConnected(false);
        setConnectionState('disconnected');

        // Clear heartbeat
        if (heartbeatIntervalRef.current) {
          clearInterval(heartbeatIntervalRef.current);
        }

        // Attempt reconnection if not a normal closure
        if (!event.wasClean && reconnectAttemptsRef.current < (defaultConfig.maxReconnectAttempts || 5)) {
          reconnectAttemptsRef.current++;

          reconnectTimeoutRef.current = setTimeout(() => {
            connect();
          }, defaultConfig.reconnectInterval);
        }
      };

      ws.onerror = (event) => {
        setConnectionState('error');
        setError('WebSocket connection error');
        console.error('WebSocket error:', event);
      };

    } catch (err) {
      setConnectionState('error');
      setError('Failed to create WebSocket connection');
      console.error('Failed to create WebSocket connection:', err);
    }
  }, [defaultConfig]);

  /**
   * Disconnect from WebSocket
   */
  const disconnect = useCallback(() => {
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
    }

    if (heartbeatIntervalRef.current) {
      clearInterval(heartbeatIntervalRef.current);
    }

    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }

    setConnectionState('disconnected');
    setIsConnected(false);
    setError(null);
  }, []);

  /**
   * Send message through WebSocket
   */
  const sendMessage = useCallback((type: string, data: unknown) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      const message: WebSocketMessage = {
        type,
        data,
        timestamp: Date.now(),
        userId: 'current-user', // Will be replaced with actual user ID
        messageId: `msg-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      };

      wsRef.current.send(JSON.stringify(message));
    } else {
      console.warn('WebSocket not connected, cannot send message');
    }
  }, []);

  /**
   * Add event listener for specific message type
   */
  const addEventListener = useCallback((type: string, listener: (data: unknown) => void) => {
    if (!eventListenersRef.current.has(type)) {
      eventListenersRef.current.set(type, new Set());
    }
    eventListenersRef.current.get(type)!.add(listener);

    // Return cleanup function
    return () => {
      const listeners = eventListenersRef.current.get(type);
      if (listeners) {
        listeners.delete(listener);
        if (listeners.size === 0) {
          eventListenersRef.current.delete(type);
        }
      }
    };
  }, []);

  /**
   * Remove event listener
   */
  const removeEventListener = useCallback((type: string, listener: (data: unknown) => void) => {
    const listeners = eventListenersRef.current.get(type);
    if (listeners) {
      listeners.delete(listener);
      if (listeners.size === 0) {
        eventListenersRef.current.delete(type);
      }
    }
  }, []);

  /**
   * Get connection statistics
   */
  const getConnectionStats = useCallback(() => {
    return {
      state: connectionState,
      isConnected,
      error,
      reconnectAttempts: reconnectAttemptsRef.current,
      url: defaultConfig.url,
    };
  }, [connectionState, isConnected, error, defaultConfig.url]);

  // Auto-connect on mount
  useEffect(() => {
    connect();

    return () => {
      disconnect();
    };
  }, [connect, disconnect]);

  return {
    // Connection state
    connectionState,
    isConnected,
    error,

    // Actions
    connect,
    disconnect,
    sendMessage,
    addEventListener,
    removeEventListener,

    // Utilities
    getConnectionStats,
  };
}

/**
 * Mock WebSocket service for development/testing
 */
export function useMockWebSocketService() {
  const [isConnected, setIsConnected] = useState(true);
  const eventListenersRef = useRef<Map<string, Set<(data: unknown) => void>>>(new Map());

  const sendMessage = useCallback((type: string, data: unknown) => {
    console.log('Mock WebSocket message:', { type, data });

    // Simulate receiving a response after a short delay
    setTimeout(() => {
      const listeners = eventListenersRef.current.get(type);
      if (listeners) {
        listeners.forEach(listener => listener({ type, data }));
      }
    }, 100);
  }, []);

  const addEventListener = useCallback((type: string, listener: (data: unknown) => void) => {
    if (!eventListenersRef.current.has(type)) {
      eventListenersRef.current.set(type, new Set());
    }
    eventListenersRef.current.get(type)!.add(listener);

    return () => {
      const listeners = eventListenersRef.current.get(type);
      if (listeners) {
        listeners.delete(listener);
        if (listeners.size === 0) {
          eventListenersRef.current.delete(type);
        }
      }
    };
  }, []);

  const removeEventListener = useCallback((type: string, listener: (data: unknown) => void) => {
    const listeners = eventListenersRef.current.get(type);
    if (listeners) {
      listeners.delete(listener);
      if (listeners.size === 0) {
        eventListenersRef.current.delete(type);
      }
    }
  }, []);

  return {
    connectionState: 'connected' as ConnectionState,
    isConnected,
    error: null,
    connect: () => setIsConnected(true),
    disconnect: () => setIsConnected(false),
    sendMessage,
    addEventListener,
    removeEventListener,
    getConnectionStats: () => ({
      state: 'connected' as ConnectionState,
      isConnected: true,
      error: null,
      reconnectAttempts: 0,
      url: 'mock://websocket',
    }),
  };
}
