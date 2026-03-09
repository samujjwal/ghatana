declare module '@ghatana/realtime' {
  export type ActiveJConnectionState = 'disconnected' | 'connecting' | 'connected' | 'error';

  export interface ActiveJStreamMessage<T = unknown> {
    topic: string;
    payload: T;
    timestamp: number;
  }

  export interface UseActiveJStreamOptions {
    authToken?: string;
    topics?: string[];
    autoConnect?: boolean;
    reconnectDelay?: number;
    maxReconnectAttempts?: number;
  }

  export function useActiveJStream<T = unknown>(
    serverUrl: string,
    tenantId: string,
    path?: string,
    options?: UseActiveJStreamOptions
  ): {
    state: ActiveJConnectionState;
    error: Error | null;
    connect: () => Promise<void>;
    disconnect: () => void;
    subscribe: (topic: string, handler: (message: ActiveJStreamMessage<T>) => void) => () => void;
    send: (message: unknown) => void;
    isConnected: boolean;
  };

  export function useActiveJSubscription<T = unknown>(
    subscribe: (topic: string, handler: (message: ActiveJStreamMessage<unknown>) => void) => () => void,
    topic: string,
    handler: (message: ActiveJStreamMessage<T>) => void,
    deps?: readonly unknown[]
  ): void;

  export function useWebSocket(...args: unknown[]): unknown;
}
