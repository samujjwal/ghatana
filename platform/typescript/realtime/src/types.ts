export interface StreamEvent<TData = unknown> {
  readonly type?: string;
  readonly topic?: string;
  readonly data?: TData;
  readonly payload?: TData;
  readonly [key: string]: unknown;
}

export interface ConnectionConfig {
  readonly url: string;
  readonly protocols?: string[];
  readonly reconnect?: boolean;
  readonly maxReconnectAttempts?: number;
  readonly reconnectInterval?: number;
  readonly connectionTimeout?: number;
  readonly backpressureLimitBytes?: number;
}
