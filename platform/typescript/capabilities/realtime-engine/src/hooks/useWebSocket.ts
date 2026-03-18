/**
 * WebSocket React Hooks
 *
 * React hooks for WebSocket integration with automatic connection management,
 * real-time data updates, and optimistic UI patterns. Provides seamless
 * integration with React components and state management.
 */

import { useState, useEffect, useCallback, useRef, useMemo } from 'react';

import {
  getWebSocketClient,
} from '../client';

import type {
  WebSocketClient,
  WebSocketConnectionState,
  WebSocketMessage,
  WebSocketEventHandler} from '../client';

/**
 *
 */
export interface UseWebSocketOptions {
  url?: string;
  autoConnect?: boolean;
  reconnect?: boolean;
  maxReconnectAttempts?: number;
  protocols?: string[];
}

/**
 *
 */
export interface UseWebSocketReturn {
  connectionState: WebSocketConnectionState;
  isConnected: boolean;
  connect: () => Promise<void>;
  disconnect: () => void;
  send: <T>(message: WebSocketMessage<T>) => boolean;
  subscribe: <T>(
    messageType: string,
    handler: WebSocketEventHandler<T>
  ) => () => void;
  lastMessage: WebSocketMessage | null;
  client: WebSocketClient;
}

/**
 * Main WebSocket hook with comprehensive connection management
 * 
 * Provides real-time bidirectional communication with automatic reconnection:
 * - Automatic connection lifecycle management
 * - Configurable reconnection with exponential backoff
 * - Connection state tracking (connecting, connected, disconnected, error)
 * - Type-safe message sending and receiving
 * - Message subscription with automatic cleanup
 * - SSR-safe with mock support for development
 * 
 * By default, uses MSW mocks in development. Enable real WebSocket connections
 * by setting VITE_ENABLE_REAL_WS=true in environment variables.
 * 
 * @param options - Configuration options for WebSocket connection
 * @param options.url - WebSocket server URL (default: VITE_WEBSOCKET_URL or ws://localhost:3001/ws)
 * @param options.autoConnect - Connect automatically on mount (default: false in dev with mocks)
 * @param options.reconnect - Enable automatic reconnection (default: true)
 * @param options.maxReconnectAttempts - Maximum reconnection attempts (default: 5)
 * @param options.protocols - Optional WebSocket sub-protocols
 * @returns Object containing:
 *   - connectionState: Current connection state (connecting|connected|disconnected|error)
 *   - connect: Function to manually establish connection
 *   - disconnect: Function to manually close connection
 *   - send: Function to send typed messages
 *   - subscribe: Function to subscribe to specific message types
 *   - lastMessage: Most recently received message or null
 *   - client: Underlying WebSocket client instance
 * 
 * @example
 * ```tsx
 * function ChatRoom({ roomId }: { roomId: string }) {
 *   const { 
 *     connectionState, 
 *     connect, 
 *     disconnect, 
 *     send, 
 *     subscribe 
 *   } = useWebSocket({
 *     url: 'wss://api.example.com/chat',
 *     autoConnect: true,
 *     reconnect: true,
 *     maxReconnectAttempts: 10
 *   });
 *   
 *   useEffect(() => {
 *     // Subscribe to chat messages
 *     const unsubscribe = subscribe('chat:message', (message) => {
 *       console.log('New message:', message.data);
 *       setMessages(prev => [...prev, message.data]);
 *     });
 *     
 *     return unsubscribe;
 *   }, [subscribe]);
 *   
 *   const sendMessage = (text: string) => {
 *     send({
 *       type: 'chat:message',
 *       data: { roomId, text, userId: currentUser.id }
 *     });
 *   };
 *   
 *   return (
 *     <div>
 *       <div className={connectionState === 'connected' ? 'online' : 'offline'}>
 *         {connectionState}
 *       </div>
 *       {connectionState === 'disconnected' && (
 *         <button onClick={connect}>Reconnect</button>
 *       )}
 *       <MessageList messages={messages} />
 *       <MessageInput onSend={sendMessage} />
 *     </div>
 *   );
 * }
 * ```
 */
export function useWebSocket(
  options: UseWebSocketOptions = {}
): UseWebSocketReturn {
  const {
    url = ((import.meta as unknown as { env?: Record<string, string> }).env?.VITE_WEBSOCKET_URL) ?? 'ws://localhost:3001/ws',
    // Default to not auto-connecting in development when mocks (MSW) are enabled.
    // Enable real WebSocket connections by setting VITE_ENABLE_REAL_WS=true in env.
    autoConnect = (typeof (import.meta as unknown as { env?: Record<string, string> }).env?.VITE_ENABLE_REAL_WS === 'string'
      ? (import.meta as unknown as { env: Record<string, string> }).env.VITE_ENABLE_REAL_WS === 'true'
      : false),
    reconnect = true,
    maxReconnectAttempts = 5,
    protocols = [],
  } = options;

  // WebSocket client instance
  const client = useMemo(
    () =>
      getWebSocketClient({
        url,
        protocols,
        maxReconnectAttempts: reconnect ? maxReconnectAttempts : 0,
      }),
    [url, protocols, reconnect, maxReconnectAttempts]
  );

  // Connection state
  const [connectionState, setConnectionState] =
    useState<WebSocketConnectionState>(() => client.getConnectionState());

  // Last received message
  const [lastMessage, setLastMessage] = useState<WebSocketMessage | null>(null);

  // Track if component is mounted
  const isMounted = useRef(true);

  // Subscribe to connection state changes
  useEffect(() => {
    const unsubscribe = client.onStateChange((state) => {
      if (isMounted.current) {
        setConnectionState(state);
      }
    });

    return unsubscribe;
  }, [client]);

  // Auto-connect on mount
  useEffect(() => {
    if (autoConnect && connectionState.status === 'disconnected') {
      client.connect().catch((error) => {
        console.error('Auto-connect failed:', error);
      });
    }

    return () => {
      isMounted.current = false;
    };
  }, [client, autoConnect, connectionState.status]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      isMounted.current = false;
    };
  }, []);

  const connect = useCallback(async () => {
    try {
      await client.connect();
    } catch (error) {
      console.error('Connection failed:', error);
      throw error;
    }
  }, [client]);

  const disconnect = useCallback(() => {
    client.disconnect();
  }, [client]);

  const send = useCallback(
    <T>(message: WebSocketMessage<T>): boolean => {
      return client.send(message);
    },
    [client]
  );

  const subscribe = useCallback(
    <T>(
      messageType: string,
      handler: WebSocketEventHandler<T>
    ): (() => void) => {
      // Wrap handler to update lastMessage
      const wrappedHandler = (message: WebSocketMessage<T>) => {
        if (isMounted.current) {
          setLastMessage(message);
        }
        handler(message);
      };

      return client.subscribe(messageType, wrappedHandler);
    },
    [client]
  );

  const isConnected = connectionState.status === 'connected';

  return {
    connectionState,
    isConnected,
    connect,
    disconnect,
    send,
    subscribe,
    lastMessage,
    client,
  };
}

/**
 * Hook for subscribing to specific WebSocket message types with automatic cleanup
 * 
 * Convenience wrapper around useWebSocket that handles subscription lifecycle.
 * Automatically subscribes on mount and unsubscribes on unmount or dependency changes.
 * 
 * @template T - Type of message payload data
 * @param messageType - Message type to subscribe to (e.g., 'chat:message', 'user:status')
 * @param handler - Callback function invoked when messages of this type are received
 * @param deps - Dependency array for re-subscribing when values change
 * @param options - WebSocket connection options (same as useWebSocket)
 * 
 * @example
 * ```tsx
 * function ChatMessages({ roomId }: { roomId: string }) {
 *   const [messages, setMessages] = useState<Message[]>([]);
 *   
 *   useWebSocketSubscription<Message>(
 *     'chat:message',
 *     (message) => {
 *       if (message.data.roomId === roomId) {
 *         setMessages(prev => [...prev, message.data]);
 *       }
 *     },
 *     [roomId], // Re-subscribe when roomId changes
 *     { url: 'wss://chat.example.com' }
 *   );
 *   
 *   return (
 *     <div>
 *       {messages.map(msg => (
 *         <ChatMessage key={msg.id} {...msg} />
 *       ))}
 *     </div>
 *   );
 * }
 * ```
 */
export function useWebSocketSubscription<T = any>(
  messageType: string,
  handler: WebSocketEventHandler<T>,
  deps: React.DependencyList = [],
  options: UseWebSocketOptions = {}
) {
  const { subscribe } = useWebSocket(options);

  useEffect(() => {
    const unsubscribe = subscribe<T>(messageType, handler);
    return unsubscribe;
  }, [subscribe, messageType, ...deps]); // eslint-disable-line react-hooks/exhaustive-deps
}

/**
 * Hook for real-time data fetching with automatic updates via WebSocket
 * 
 * Provides a data-fetching pattern with WebSocket for real-time updates.
 * Automatically subscribes to data updates, handles loading states, and provides refresh capability.
 * 
 * @template T - Type of data being fetched
 * @param messageType - Message type for data subscription (e.g., 'user:profile', 'dashboard:stats')
 * @param initialData - Optional initial data while loading
 * @param options - WebSocket connection options
 * @returns Object containing data, loading state, error, and refresh function
 * 
 * @example
 * ```tsx
 * function UserProfile({ userId }: { userId: string }) {
 *   const {
 *     data: profile,
 *     isLoading,
 *     error,
 *     refresh
 *   } = useWebSocketData<UserProfile>(`user:profile:${userId}`, undefined, {
 *     url: 'wss://api.example.com',
 *     autoConnect: true
 *   });
 *   
 *   if (isLoading) return <Spinner />;
 *   if (error) return <Error message={error.message} onRetry={refresh} />;
 *   if (!profile) return <NotFound />;
 *   
 *   return (
 *     <div>
 *       <h1>{profile.name}</h1>
 *       <p>{profile.email}</p>
 *       <button onClick={refresh}>Refresh Profile</button>
 *     </div>
 *   );
 * }
 * 
 * function LiveDashboard() {
 *   const { data: stats, isLoading } = useWebSocketData<DashboardStats>(
 *     'dashboard:stats',
 *     { activeUsers: 0, requests: 0 }
 *   );
 *   
 *   return (
 *     <div>
 *       <h2>Live Stats</h2>
 *       <p>Active Users: {stats?.activeUsers}</p>
 *       <p>Requests: {stats?.requests}</p>
 *       {isLoading && <span>Updating...</span>}
 *     </div>
 *   );
 * }
 * ```
 */
export function useWebSocketData<T = any>(
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

  const { send, isConnected } = useWebSocket(options);

  // Request initial data when connected
  useEffect(() => {
    if (isConnected) {
      setIsLoading(true);
      setError(null);
      send({
        type: 'subscribe',
        payload: { messageType },
      });
    }
  }, [isConnected, messageType, send]);

  // Subscribe to data updates
  useWebSocketSubscription<T>(
    messageType,
    (message) => {
      setData(message.payload);
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
      setError(new Error(message.payload.error));
      setIsLoading(false);
    },
    [messageType],
    options
  );

  const refresh = useCallback(() => {
    if (isConnected) {
      setIsLoading(true);
      send({
        type: 'refresh',
        payload: { messageType },
      });
    }
  }, [isConnected, messageType, send]);

  return { data, isLoading, error, refresh };
}

/**
 * Hook for optimistic UI updates with automatic rollback on failure
 * 
 * Implements optimistic update pattern for instant user feedback while waiting for server confirmation.
 * Automatically rolls back changes if the server rejects the update or connection fails.
 * 
 * @template T - Type of data being updated
 * @param messageType - Message type for update operations (e.g., 'user:profile', 'task:status')
 * @param options - WebSocket connection options
 * @returns Object containing data, update function, loading state, and error
 * 
 * @example
 * ```tsx
 * function TaskItem({ taskId }: { taskId: string }) {
 *   const {
 *     data: task,
 *     update,
 *     isUpdating,
 *     error
 *   } = useOptimisticUpdate<Task>(`task:${taskId}`, {
 *     url: 'wss://api.example.com'
 *   });
 *   
 *   const handleToggleComplete = async () => {
 *     try {
 *       // Optimistic update - UI updates immediately
 *       await update(
 *         { ...task, completed: !task.completed },
 *         { completed: !task.completed } // Partial optimistic data
 *       );
 *     } catch (err) {
 *       // Update will auto-rollback on error
 *       console.error('Failed to update task:', err);
 *     }
 *   };
 *   
 *   return (
 *     <div className={task?.completed ? 'line-through' : ''}>
 *       <input
 *         type="checkbox"
 *         checked={task?.completed || false}
 *         onChange={handleToggleComplete}
 *         disabled={isUpdating}
 *       />
 *       <span>{task?.title}</span>
 *       {error && <ErrorTooltip message={error.message} />}
 *       {isUpdating && <Spinner size="sm" />}
 *     </div>
 *   );
 * }
 * 
 * function CommentLike({ commentId }: { commentId: string }) {
 *   const { data: comment, update } = useOptimisticUpdate<Comment>(
 *     `comment:${commentId}`
 *   );
 *   
 *   const handleLike = async () => {
 *     const newLikeCount = (comment?.likes || 0) + 1;
 *     
 *     // UI updates immediately, rolls back if server rejects
 *     await update(
 *       { ...comment, likes: newLikeCount },
 *       { likes: newLikeCount }
 *     );
 *   };
 *   
 *   return (
 *     <button onClick={handleLike}>
 *       ❤️ {comment?.likes || 0}
 *     </button>
 *   );
 * }
 * ```
 */
export function useOptimisticUpdate<T = any>(
  messageType: string,
  options: UseWebSocketOptions = {}
): {
  data: T | undefined;
  update: (newData: T, optimisticData?: Partial<T>) => Promise<void>;
  isUpdating: boolean;
  error: Error | null;
} {
  const [data, setData] = useState<T | undefined>();
  const [isUpdating, setIsUpdating] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const rollbackData = useRef<T | undefined>(undefined);

  const { send, isConnected } = useWebSocket(options);

  // Subscribe to update confirmations
  useWebSocketSubscription<T>(
    `${messageType}_updated`,
    (message) => {
      setData(message.payload);
      setIsUpdating(false);
      setError(null);
      rollbackData.current = undefined;
    },
    [messageType],
    options
  );

  // Subscribe to update failures
  useWebSocketSubscription<{ error: string }>(
    `${messageType}_update_failed`,
    (message) => {
      // Rollback to previous data
      if (rollbackData.current !== undefined) {
        setData(rollbackData.current);
      }
      setError(new Error(message.payload.error));
      setIsUpdating(false);
      rollbackData.current = undefined;
    },
    [messageType],
    options
  );

  const update = useCallback(
    async (newData: T, optimisticData?: Partial<T>) => {
      if (!isConnected) {
        throw new Error('WebSocket not connected');
      }

      setError(null);
      setIsUpdating(true);

      // Store current data for potential rollback
      rollbackData.current = data;

      // Apply optimistic update
      const optimisticUpdate = optimisticData
        ? ({ ...data, ...optimisticData } as T)
        : newData;
      setData(optimisticUpdate);

      // Send update to server
      const success = send({
        type: `${messageType}_update`,
        payload: newData,
      });

      if (!success) {
        // Rollback immediately if send failed
        setData(rollbackData.current);
        setIsUpdating(false);
        rollbackData.current = undefined;
        throw new Error('Failed to send update');
      }
    },
    [isConnected, data, messageType, send]
  );

  return { data, update, isUpdating, error };
}

/**
 * Hook for WebSocket connection status monitoring with user notifications
 * 
 * Monitors WebSocket connection state and provides notification triggers
 * for important status changes like disconnections and reconnections.
 * 
 * @param options - WebSocket connection options
 * @returns Object containing connection status, flags, and notification state
 * 
 * @example
 * ```tsx
 * function ConnectionStatusBar() {
 *   const {
 *     status,
 *     isConnected,
 *     reconnectAttempt,
 *     lastError,
 *     showNotification
 *   } = useWebSocketStatus({ url: 'wss://api.example.com' });
 *   
 *   return (
 *     <div className="status-bar">
 *       <StatusIndicator
 *         status={status}
 *         className={isConnected ? 'online' : 'offline'}
 *       />
 *       {status === 'reconnecting' && (
 *         <span>Reconnecting... (attempt {reconnectAttempt})</span>
 *       )}
 *       {showNotification && lastError && (
 *         <Toast type="error" message={lastError.message} />
 *       )}
 *     </div>
 *   );
 * }
 * 
 * function AppWithConnectionMonitor() {
 *   const { status, showNotification } = useWebSocketStatus();
 *   
 *   // Show banner when disconnected
 *   if (status === 'disconnected' && showNotification) {
 *     return <OfflineBanner />;
 *   }
 *   
 *   return <App />;
 * }
 * ```
 */
export function useWebSocketStatus(options: UseWebSocketOptions = {}): {
  status: WebSocketConnectionState['status'];
  isConnected: boolean;
  reconnectAttempt: number;
  lastError: Error | null;
  showNotification: boolean;
} {
  const { connectionState } = useWebSocket(options);
  const [showNotification, setShowNotification] = useState(false);
  const previousStatus =
    useRef<WebSocketConnectionState['status']>('disconnected');

  // Show notification on status changes
  useEffect(() => {
    const { status } = connectionState;

    // Show notification for important status changes
    if (
      (previousStatus.current === 'connected' && status === 'disconnected') ||
      (previousStatus.current === 'connecting' && status === 'failed') ||
      (previousStatus.current === 'reconnecting' && status === 'connected')
    ) {
      setShowNotification(true);

      // Auto-hide notification after 5 seconds
      const timer = setTimeout(() => {
        setShowNotification(false);
      }, 5000);

      return () => clearTimeout(timer);
    }

    previousStatus.current = status;

    return undefined;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connectionState.status]);

  return {
    status: connectionState.status,
    isConnected: connectionState.status === 'connected',
    reconnectAttempt: connectionState.reconnectAttempt,
    lastError: connectionState.lastError || null,
    showNotification,
  };
}

/**
 * Hook for batched WebSocket message operations
 * 
 * Provides batching functionality to group multiple WebSocket messages
 * and send them together, reducing network overhead for high-frequency updates.
 * 
 * @param options - WebSocket connection options
 * @returns Object with methods to add, send, and clear batched messages
 * 
 * @example
 * ```tsx
 * function BulkUpdateForm({ items }: { items: Item[] }) {
 *   const {
 *     addToBatch,
 *     sendBatch,
 *     clearBatch,
 *     batchSize
 *   } = useWebSocketBatch({ url: 'wss://api.example.com' });
 *   
 *   const handleItemUpdate = (item: Item) => {
 *     addToBatch({
 *       type: 'item:update',
 *       payload: item
 *     });
 *   };
 *   
 *   const handleSaveAll = async () => {
 *     const success = sendBatch();
 *     if (success) {
 *       toast.success(`Saved ${batchSize} items`);
 *     }
 *   };
 *   
 *   return (
 *     <div>
 *       {items.map(item => (
 *         <ItemEditor
 *           key={item.id}
 *           item={item}
 *           onChange={handleItemUpdate}
 *         />
 *       ))}
 *       <div className="actions">
 *         <button onClick={handleSaveAll} disabled={batchSize === 0}>
 *           Save All ({batchSize})
 *         </button>
 *         <button onClick={clearBatch}>Clear</button>
 *       </div>
 *     </div>
 *   );
 * }
 * 
 * function CanvasChangeBatcher() {
 *   const { addToBatch, sendBatch } = useWebSocketBatch();
 *   const batchTimerRef = useRef<NodeJS.Timeout>();
 *   
 *   const handleNodeChange = (node: Node) => {
 *     addToBatch({ type: 'node:update', payload: node });
 *     
 *     // Debounce batch sending
 *     clearTimeout(batchTimerRef.current);
 *     batchTimerRef.current = setTimeout(() => {
 *       sendBatch();
 *     }, 500);
 *   };
 *   
 *   return <Canvas onNodeChange={handleNodeChange} />;
 * }
 * ```
 */
export function useWebSocketBatch(options: UseWebSocketOptions = {}): {
  addToBatch: <T>(message: WebSocketMessage<T>) => void;
  sendBatch: () => boolean;
  clearBatch: () => void;
  batchSize: number;
} {
  const [batch, setBatch] = useState<WebSocketMessage[]>([]);
  const { send } = useWebSocket(options);

  const addToBatch = useCallback(<T>(message: WebSocketMessage<T>) => {
    setBatch((prev) => [...prev, message]);
  }, []);

  const sendBatch = useCallback((): boolean => {
    if (batch.length === 0) return true;

    const success = send({
      type: 'batch',
      payload: batch,
    });

    if (success) {
      setBatch([]);
    }

    return success;
  }, [batch, send]);

  const clearBatch = useCallback(() => {
    setBatch([]);
  }, []);

  return {
    addToBatch,
    sendBatch,
    clearBatch,
    batchSize: batch.length,
  };
}
