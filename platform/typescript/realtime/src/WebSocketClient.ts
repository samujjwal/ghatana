export type WebSocketEvent =
  | { type: 'open'; event: Event }
  | { type: 'message'; event: MessageEvent }
  | { type: 'error'; event: Event }
  | { type: 'close'; event: CloseEvent }
  | { type: 'reconnect'; attempt: number };

export type WebSocketEventType = WebSocketEvent['type'];

export type WebSocketListener<T extends WebSocketEventType> = (
  event: Extract<WebSocketEvent, { type: T }>
) => void;

export interface RealtimeClientOptions {
  url: string | (() => string);
  protocols?: string | string[];
  reconnect?: boolean;
  maxReconnectAttempts?: number;
  backoffMs?: {
    initial: number;
    max: number;
    multiplier: number;
  };
  heartbeatIntervalMs?: number;
  heartbeatPayload?: string | (() => string);
}

const DEFAULT_OPTIONS: Required<Omit<RealtimeClientOptions, 'url' | 'protocols'>> = {
  reconnect: true,
  maxReconnectAttempts: Infinity,
  backoffMs: {
    initial: 500,
    max: 15_000,
    multiplier: 1.8,
  },
  heartbeatIntervalMs: 30_000,
  heartbeatPayload: 'ping',
};

export class WebSocketClient {
  private socket: WebSocket | null = null;
  private reconnectAttempts = 0;
  private heartbeatTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly listeners = new Map<WebSocketEventType, Set<WebSocketListener<WebSocketEventType>>>();

  private readonly options: RealtimeClientOptions & typeof DEFAULT_OPTIONS;

  constructor(options: RealtimeClientOptions) {
    if (!options || !options.url) {
      throw new Error('RealtimeClient requires a URL');
    }

    this.options = {
      ...DEFAULT_OPTIONS,
      ...options,
      backoffMs: {
        ...DEFAULT_OPTIONS.backoffMs,
        ...(options.backoffMs ?? {}),
      },
    };
  }

  connect(): void {
    if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      return;
    }

    const url = typeof this.options.url === 'function' ? this.options.url() : this.options.url;
    this.socket = new WebSocket(url, this.options.protocols);

    this.socket.addEventListener('open', this.handleOpen);
    this.socket.addEventListener('message', this.handleMessage);
    this.socket.addEventListener('error', this.handleError);
    this.socket.addEventListener('close', this.handleClose);
  }

  disconnect(): void {
    this.clearHeartbeat();
    if (this.socket) {
      this.socket.removeEventListener('open', this.handleOpen);
      this.socket.removeEventListener('message', this.handleMessage);
      this.socket.removeEventListener('error', this.handleError);
      this.socket.removeEventListener('close', this.handleClose);
      this.socket.close();
      this.socket = null;
    }
    this.reconnectAttempts = 0;
  }

  send(data: string | ArrayBufferLike | Blob | ArrayBufferView): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      throw new Error('WebSocket is not open');
    }
    this.socket.send(data);
  }

  on<T extends WebSocketEventType>(type: T, listener: WebSocketListener<T>): () => void {
    if (!this.listeners.has(type)) {
      this.listeners.set(type, new Set());
    }
    const set = this.listeners.get(type)!;
    set.add(listener as unknown as WebSocketListener<WebSocketEventType>);
    return () => {
      set.delete(listener as unknown as WebSocketListener<WebSocketEventType>);
    };
  }

  private emit(event: WebSocketEvent): void {
    const listeners = this.listeners.get(event.type);
    if (!listeners) return;
    listeners.forEach((listener) => listener(event));
  }

  private handleOpen = (event: Event) => {
    this.reconnectAttempts = 0;
    this.emit({ type: 'open', event });
    this.scheduleHeartbeat();
  };

  private handleMessage = (event: MessageEvent) => {
    this.emit({ type: 'message', event });
  };

  private handleError = (event: Event) => {
    this.emit({ type: 'error', event });
  };

  private handleClose = (event: CloseEvent) => {
    this.clearHeartbeat();
    this.emit({ type: 'close', event });

    if (this.shouldReconnect(event)) {
      this.scheduleReconnect();
    }
  };

  private shouldReconnect(event: CloseEvent): boolean {
    if (!this.options.reconnect) return false;
    if (event.code === 1000) return false; // normal close
    return this.reconnectAttempts < this.options.maxReconnectAttempts;
  }

  private scheduleReconnect(): void {
    this.reconnectAttempts += 1;
    const { initial, multiplier, max } = this.options.backoffMs;
    const delay = Math.min(initial * Math.pow(multiplier, this.reconnectAttempts - 1), max);

    this.emit({ type: 'reconnect', attempt: this.reconnectAttempts });

    setTimeout(() => {
      this.disconnect();
      this.connect();
    }, delay);
  }

  private scheduleHeartbeat(): void {
    if (this.options.heartbeatIntervalMs <= 0) return;

    this.clearHeartbeat();

    this.heartbeatTimer = setTimeout(() => {
      try {
        const payload =
          typeof this.options.heartbeatPayload === 'function'
            ? this.options.heartbeatPayload()
            : this.options.heartbeatPayload;
        this.send(payload);
      } catch {
        // ignore heartbeat send errors
      } finally {
        this.scheduleHeartbeat();
      }
    }, this.options.heartbeatIntervalMs);
  }

  private clearHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearTimeout(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }
}
