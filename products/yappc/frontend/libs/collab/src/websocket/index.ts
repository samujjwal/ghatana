import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  useWebSocket as useRealtimeWebSocket,
  useWebSocketData as useRealtimeWebSocketData,
  useWebSocketSubscription as useRealtimeWebSocketSubscription,
} from '@ghatana/realtime';

import type {
  UseWebSocketOptions as RealtimeUseWebSocketOptions,
  WebSocketMessage,
  WebSocketEventHandler,
} from '@ghatana/realtime';

type ConnectionStatus =
  | 'idle'
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'disconnected'
  | 'failed';

type MessageHandler = (message: unknown) => void;

export interface UseWebSocketOptions {
  url?: string;
  autoConnect?: boolean;
  reconnect?: boolean;
  maxReconnectAttempts?: number;
  protocols?: string[];
}

export interface UseWebSocketReturn {
  status: ConnectionStatus;
  lastError?: Error;
  reconnectAttempt: number;
  send: (payload: unknown) => boolean;
  subscribe: (topic: string, handler: MessageHandler) => () => void;
  disconnect: () => void;
  connect: () => void;
}

export function useWebSocket({
  url,
  autoConnect = false,
  reconnect = true,
  maxReconnectAttempts = 5,
  protocols,
}: UseWebSocketOptions): UseWebSocketReturn {
  const realtimeOptions: RealtimeUseWebSocketOptions = {
    url,
    autoConnect,
    reconnect,
    maxReconnectAttempts,
    protocols,
  };

  const {
    connectionState,
    send: sendRealtime,
    subscribe: subscribeRealtime,
    disconnect,
    connect: connectRealtime,
  } = useRealtimeWebSocket(realtimeOptions);

  const status: ConnectionStatus = connectionState.status;
  const reconnectAttempt = connectionState.reconnectAttempt;

  const send = useCallback(
    (payload: unknown): boolean => {
      if (
        typeof payload === 'object' &&
        payload !== null &&
        'type' in payload &&
        typeof (payload as { type: unknown }).type === 'string'
      ) {
        return sendRealtime(payload as WebSocketMessage<unknown>);
      }

      return sendRealtime({
        type: 'message',
        payload,
      });
    },
    [sendRealtime],
  );

  const subscribe = useCallback(
    (topic: string, handler: MessageHandler): (() => void) => {
      const bridgeHandler: WebSocketEventHandler<unknown> = (message) => {
        const candidate =
          message && typeof message === 'object' && 'payload' in message
            ? (message as { payload?: unknown }).payload
            : message;
        handler(candidate ?? message);
      };
      return subscribeRealtime(topic, bridgeHandler);
    },
    [subscribeRealtime],
  );

  const connect = useCallback((): void => {
    void connectRealtime();
  }, [connectRealtime]);

  return useMemo(
    () => ({
      status,
      lastError: connectionState.lastError,
      reconnectAttempt,
      send,
      subscribe,
      disconnect,
      connect,
    }),
    [status, connectionState.lastError, reconnectAttempt, send, subscribe, disconnect, connect],
  );
}

export interface UseWebSocketStatusReturn {
  status: ConnectionStatus;
  showNotification: boolean;
  reconnectAttempt: number;
  lastError?: Error;
}

export function useWebSocketStatus(): UseWebSocketStatusReturn {
  const { status, lastError, reconnectAttempt } = useWebSocket({
    autoConnect: true,
    reconnect: true,
  });

  const [showNotification, setShowNotification] = useState(status !== 'idle');

  useEffect(() => {
    setShowNotification(status !== 'idle');
  }, [status]);

  return {
    status,
    showNotification,
    reconnectAttempt,
    lastError,
  };
}

/**
 * Hook for subscribing to specific message types via WebSocket
 */
export function useWebSocketSubscription<T = unknown>(
  messageType: string,
  handler: MessageHandler,
  deps: React.DependencyList = [],
  options: UseWebSocketOptions = {}
) {
  useRealtimeWebSocketSubscription<T>(
    messageType,
    (message) => handler(message),
    deps,
    options,
  );
}

/**
 * Hook for real-time data fetching with automatic updates via WebSocket
 */
export function useWebSocketData<T = unknown>(
  messageType: string,
  initialData?: T,
  options: UseWebSocketOptions = {}
): {
  data: T | undefined;
  isLoading: boolean;
  error: Error | null;
  refresh: () => void;
} {
  const realtime = useRealtimeWebSocketData<T>(messageType, initialData, options);
  const { send } = useWebSocket(options);

  const refresh = useCallback(() => {
    send({
      type: 'refresh',
      payload: { messageType },
    });
  }, [messageType, send]);

  return {
    data: realtime.data,
    isLoading: realtime.isLoading,
    error: realtime.error,
    refresh,
  };
}
