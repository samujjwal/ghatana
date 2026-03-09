export type MessageHandler = (data: string) => void;
export type ErrorHandler = (error: unknown) => void;
export type CloseHandler = (code?: number, reason?: string) => void;

export interface BridgeTransport {
  connect(): Promise<void>;
  disconnect(): Promise<void>;
  send(data: string): Promise<void>;
  setMessageHandler(handler: MessageHandler): void;
  setErrorHandler?(handler: ErrorHandler): void;
  setCloseHandler?(handler: CloseHandler): void;
}

export interface WebSocketBridgeTransportOptions {
  url: string;
  protocols?: string | string[];
  connectTimeoutMs?: number;
}

export class WebSocketBridgeTransport implements BridgeTransport {
  private socket: WebSocket | null = null;
  private options: WebSocketBridgeTransportOptions;
  private messageHandler: MessageHandler = () => {};
  private errorHandler: ErrorHandler = () => {};
  private closeHandler: CloseHandler = () => {};
  private connectPromise?: Promise<void>;

  constructor(options: WebSocketBridgeTransportOptions) {
    this.options = { connectTimeoutMs: 5_000, ...options };
  }

  async connect(): Promise<void> {
    if (this.socket && this.socket.readyState === 1) {
      return;
    }

    const WebSocketCtor = typeof globalThis !== 'undefined' ? (globalThis as typeof globalThis & { WebSocket?: typeof WebSocket }).WebSocket : undefined;

    if (!WebSocketCtor) {
      throw new Error('WebSocketBridgeTransport requires WebSocket support in the current environment');
    }

    if (this.connectPromise) {
      return this.connectPromise;
    }

    this.connectPromise = new Promise<void>((resolve, reject) => {
      try {
        this.socket = new WebSocketCtor(this.options.url, this.options.protocols);
      } catch (error) {
        this.connectPromise = undefined;
        reject(error);
        return;
      }

      const socket = this.socket;
      let settled = false;
      const timeout = setTimeout(() => {
        if (!settled) {
          settled = true;
          socket.close();
          this.connectPromise = undefined;
          reject(new Error(`Timed out connecting to ${this.options.url}`));
        }
      }, this.options.connectTimeoutMs);

      socket.addEventListener('open', () => {
        if (settled) {
          return;
        }
        clearTimeout(timeout);
        settled = true;
        this.connectPromise = undefined;
        resolve();
      });

      socket.addEventListener('message', (event) => {
        const data = typeof event.data === 'string' ? event.data : String(event.data);
        this.messageHandler(data);
      });

      socket.addEventListener('error', (event) => {
        if (typeof this.errorHandler === 'function') {
          this.errorHandler(event instanceof Error ? event : new Error('WebSocket error'));
        }
      });

      socket.addEventListener('close', (event) => {
        if (typeof this.closeHandler === 'function') {
          this.closeHandler(event.code, event.reason);
        }
        this.socket = null;
        this.connectPromise = undefined;
      });
    });

    return this.connectPromise;
  }

  async disconnect(): Promise<void> {
    if (this.socket && this.socket.readyState <= 1) {
      this.socket.close();
    }
    this.socket = null;
    this.connectPromise = undefined;
  }

  async send(data: string): Promise<void> {
    if (!this.socket || this.socket.readyState !== 1) {
      throw new Error('WebSocketBridgeTransport is not connected');
    }
    this.socket.send(data);
  }

  setMessageHandler(handler: MessageHandler): void {
    this.messageHandler = handler;
  }

  setErrorHandler(handler: ErrorHandler): void {
    this.errorHandler = handler;
  }

  setCloseHandler(handler: CloseHandler): void {
    this.closeHandler = handler;
  }
}
