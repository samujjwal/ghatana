import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

type ConnectionStatus =
  | 'idle'
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'disconnected'
  | 'failed';

type MessageHandler = (message: unknown) => void;

type Subscription = {
  topic: string;
  handler: MessageHandler;
  unsubscribe: () => void;
};

export interface UseWebSocketOptions {
  url: string;
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
  const socketRef = useRef<WebSocket | null>(null);
  const [status, setStatus] = useState<ConnectionStatus>('idle');
  const [lastError, setLastError] = useState<Error | undefined>();
  const reconnectAttemptsRef = useRef(0);
  const handlersRef = useRef<Map<string, Set<MessageHandler>>>(new Map());

  const cleanupSocket = () => {
    if (socketRef.current) {
      socketRef.current.onopen = null;
      socketRef.current.onclose = null;
      socketRef.current.onerror = null;
      socketRef.current.onmessage = null;
      socketRef.current.close();
      socketRef.current = null;
    }
  };

  const connect = () => {
    cleanupSocket();
    setStatus(reconnectAttemptsRef.current > 0 ? 'reconnecting' : 'connecting');

    try {
      const socket = new WebSocket(url, protocols);
      socketRef.current = socket;

      socket.onopen = () => {
        setStatus('connected');
        reconnectAttemptsRef.current = 0;
      };

      socket.onclose = () => {
        const shouldRetry =
          reconnect && reconnectAttemptsRef.current < maxReconnectAttempts;
        if (shouldRetry) {
          reconnectAttemptsRef.current += 1;
          const delay = Math.min(1000 * reconnectAttemptsRef.current, 5000);
          setTimeout(connect, delay);
        } else {
          setStatus('disconnected');
        }
      };

      socket.onerror = (event) => {
        setLastError(new Error('WebSocket error'));
        setStatus('failed');
        socket.close();
      };

      socket.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data);
          const topic = message.type || message.topic;
          if (topic && handlersRef.current.has(topic)) {
            handlersRef.current
              .get(topic)!
              .forEach((handler) => handler(message));
          }
        } catch (err) {
          console.error('Failed to parse WebSocket message', err);
        }
      };
    } catch (error: unknown) {
      setLastError(error);
      setStatus('failed');
    }
  };

  useEffect(() => {
    if (autoConnect) {
      connect();
    }
    return () => cleanupSocket();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url]);

  const send = (payload: unknown) => {
    if (socketRef.current && socketRef.current.readyState === WebSocket.OPEN) {
      socketRef.current.send(JSON.stringify(payload));
      return true;
    }
    return false;
  };

  const subscribe = (topic: string, handler: MessageHandler) => {
    if (!handlersRef.current.has(topic)) {
      handlersRef.current.set(topic, new Set());
    }
    handlersRef.current.get(topic)!.add(handler);

    const subscription: Subscription = {
      topic,
      handler,
      unsubscribe: () => {
        const set = handlersRef.current.get(topic);
        if (set) {
          set.delete(handler);
          if (set.size === 0) handlersRef.current.delete(topic);
        }
      },
    };

    return () => subscription.unsubscribe();
  };

  const disconnect = () => {
    reconnectAttemptsRef.current = maxReconnectAttempts; // stop reconnection attempts
    cleanupSocket();
    setStatus('disconnected');
  };

  return useMemo(
    () => ({
      status,
      lastError,
      reconnectAttempt: reconnectAttemptsRef.current,
      send,
      subscribe,
      disconnect,
      connect,
    }),
    [status, lastError]
  );
}

export interface UseWebSocketStatusReturn {
  status: ConnectionStatus;
  showNotification: boolean;
  reconnectAttempt: number;
  lastError?: Error;
}

export function useWebSocketStatus(): UseWebSocketStatusReturn {
  const [status, setStatus] = useState<ConnectionStatus>('idle');
  const [lastError, setLastError] = useState<Error | undefined>();
  const [reconnectAttempt, setReconnectAttempt] = useState(0);
  const [showNotification, setShowNotification] = useState(false);

  useEffect(() => {
    // Placeholder: in a real implementation this would subscribe to global WS events
    // Expose setters so consuming code can manage status display.
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
  const { subscribe } = useWebSocket(options);

  useEffect(() => {
    const unsubscribe = subscribe(messageType, handler);
    return unsubscribe;
  }, [subscribe, messageType, ...deps]); // eslint-disable-line react-hooks/exhaustive-deps
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
  const [data, setData] = useState<T | undefined>(initialData);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const { send, status } = useWebSocket(options);

  // Request initial data when connected
  useEffect(() => {
    if (status === 'connected') {
      setIsLoading(true);
      setError(null);
      send({
        type: 'subscribe',
        payload: { messageType },
      });
    }
  }, [status, messageType, send]);

  // Subscribe to data updates
  useWebSocketSubscription<T>(
    messageType,
    (message) => {
      setData(message);
      setIsLoading(false);
      setError(null);
    },
    [messageType],
    options
  );

  // Subscribe to error messages
  useWebSocketSubscription<{ error: string }>(
    `${messageType}_error`,
    (message) => {
      const errorMsg =
        typeof message === 'object' && message !== null && 'error' in message
          ? (message as unknown).error
          : 'Unknown error';
      setError(new Error(errorMsg));
      setIsLoading(false);
    },
    [messageType],
    options
  );

  const refresh = useCallback(() => {
    setIsLoading(true);
    setError(null);
    send({
      type: 'refresh',
      payload: { messageType },
    });
  }, [messageType, send]);

  return {
    data,
    isLoading,
    error,
    refresh,
  };
}
