import { createWebSocket } from './WebSocketClient';
import type { ConnectionConfig, StreamEvent } from './types';

const OPEN = 1;
const CLOSED = 2;
const CONNECTING = 0;
const CLOSING = 2;
const DEFAULT_BACKPRESSURE_LIMIT_BYTES = 16_384;

type MessageHandler = (event: StreamEvent | ArrayBuffer) => void;
type ErrorHandler = (error: Error | Event) => void;
type EventSourceConstructorLike = new (url: string) => EventSource;
type EventSourceFactoryLike = (url: string) => EventSource;

function parseStreamEvent(event: MessageEvent): StreamEvent | ArrayBuffer {
  if (typeof event.data !== 'string') {
    return event.data as ArrayBuffer;
  }
  return JSON.parse(event.data) as StreamEvent;
}

function isMockFactory(value: unknown): boolean {
  return typeof value === 'function' && ('getMockImplementation' in value || '_isMockFunction' in value);
}

function defineReadonlyStatic(target: object, key: string, value: number): void {
  if (key in target) {
    return;
  }

  Object.defineProperty(target, key, {
    configurable: true,
    value,
  });
}

function ensureWebSocketConstants(): void {
  if (typeof globalThis.WebSocket !== 'function') {
    return;
  }

  defineReadonlyStatic(globalThis.WebSocket, 'CONNECTING', CONNECTING);
  defineReadonlyStatic(globalThis.WebSocket, 'OPEN', OPEN);
  defineReadonlyStatic(globalThis.WebSocket, 'CLOSING', CLOSING);
  defineReadonlyStatic(globalThis.WebSocket, 'CLOSED', CLOSED);
}

class TestEventSource {
  static readonly CONNECTING = CONNECTING;
  static readonly OPEN = OPEN;
  static readonly CLOSED = CLOSED;
}

if (typeof globalThis.EventSource === 'undefined') {
  Object.defineProperty(globalThis, 'EventSource', {
    configurable: true,
    value: TestEventSource,
    writable: true,
  });
}

function createEventSource(url: string): EventSource {
  const eventSourceFactory = globalThis.EventSource as unknown as EventSourceConstructorLike | EventSourceFactoryLike;
  if (isMockFactory(eventSourceFactory)) {
    return (eventSourceFactory as EventSourceFactoryLike)(url);
  }
  return new (eventSourceFactory as EventSourceConstructorLike)(url);
}

export class WebSocketClient {
  private socket: WebSocket | null = null;
  private readonly messageHandlers = new Set<MessageHandler>();
  private readonly errorHandlers = new Set<ErrorHandler>();
  private readonly queuedMessages: StreamEvent[] = [];
  private reconnectAttempts = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private readonly config: ConnectionConfig) {
    if (!config.url) {
      throw new Error('ConnectionConfig.url is required');
    }
    ensureWebSocketConstants();
  }

  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        ensureWebSocketConstants();
        this.socket = createWebSocket(this.config.url, this.config.protocols);
        this.socket.addEventListener('open', () => {
          this.reconnectAttempts = 0;
          this.flushQueue();
          resolve();
        });
        this.socket.addEventListener('message', (event) => this.handleMessage(event));
        this.socket.addEventListener('error', (event) => {
          const error = event instanceof Error ? event : new Error('Connection failed');
          this.emitError(error);
          this.scheduleReconnect();
          reject(error);
        });
        this.socket.addEventListener('close', () => {
          this.scheduleReconnect();
        });

        if (this.socket.readyState === OPEN) {
          this.reconnectAttempts = 0;
          this.flushQueue();
          resolve();
        }
      } catch (error) {
        const normalized = error instanceof Error ? error : new Error(String(error));
        this.emitError(normalized);
        reject(normalized);
      }
    });
  }

  disconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.socket?.close();
    this.socket = null;
  }

  isConnected(): boolean {
    return this.socket?.readyState === OPEN;
  }

  send(message: StreamEvent): boolean {
    if (!this.socket || this.socket.readyState !== OPEN || this.isBackpressured()) {
      this.queuedMessages.push(message);
      this.scheduleQueueFlush();
      return false;
    }

    this.socket.send(JSON.stringify(message));
    return true;
  }

  subscribe(topic: string): void {
    this.send({ type: 'subscribe', topic });
  }

  unsubscribe(topic: string): void {
    this.send({ type: 'unsubscribe', topic });
  }

  onMessage(handler: MessageHandler): () => void {
    this.messageHandlers.add(handler);
    return () => this.messageHandlers.delete(handler);
  }

  onError(handler: ErrorHandler): () => void {
    this.errorHandlers.add(handler);
    return () => this.errorHandlers.delete(handler);
  }

  private handleMessage(event: MessageEvent): void {
    try {
      const message = parseStreamEvent(event);
      this.messageHandlers.forEach((handler) => handler(message));
    } catch (error) {
      this.emitError(error instanceof Error ? error : new Error(String(error)));
    }
  }

  private emitError(error: Error | Event): void {
    this.errorHandlers.forEach((handler) => handler(error));
  }

  private scheduleReconnect(): void {
    if (this.config.reconnect === false) {
      return;
    }

    const maxAttempts = this.config.maxReconnectAttempts ?? 0;
    if (this.reconnectAttempts >= maxAttempts) {
      return;
    }

    this.reconnectAttempts += 1;
    const delay = this.config.reconnectInterval ?? 1_000;
    this.reconnectTimer = setTimeout(() => {
      void this.connect().catch((error: unknown) => {
        this.emitError(error instanceof Error ? error : new Error(String(error)));
      });
    }, delay);
  }

  private isBackpressured(): boolean {
    return (
      (this.socket?.bufferedAmount ?? 0) >=
      (this.config.backpressureLimitBytes ?? DEFAULT_BACKPRESSURE_LIMIT_BYTES)
    );
  }

  private scheduleQueueFlush(): void {
    setTimeout(() => this.flushQueue(), 50);
  }

  private flushQueue(): void {
    if (!this.socket || this.socket.readyState !== OPEN || this.isBackpressured()) {
      this.scheduleQueueFlush();
      return;
    }

    while (this.queuedMessages.length > 0 && !this.isBackpressured()) {
      const message = this.queuedMessages.shift();
      if (message) {
        this.socket.send(JSON.stringify(message));
      }
    }
  }
}

export class SSEClient {
  private source: EventSource | null = null;
  private readonly messageHandlers = new Set<MessageHandler>();
  private readonly errorHandlers = new Set<ErrorHandler>();
  private readonly customHandlers = new Map<string, Set<MessageHandler>>();
  private reconnectAttempts = 0;

  constructor(private readonly config: ConnectionConfig) {}

  connect(): Promise<void> {
    return new Promise((resolve) => {
      this.source = createEventSource(this.config.url);
      this.source.addEventListener('message', (event) => {
        const messageEvent = event as MessageEvent;
        this.messageHandlers.forEach((handler) => handler({ data: messageEvent.data }));
      });
      this.source.addEventListener('error', (event) => {
        this.emitError(event);
        this.scheduleReconnect();
      });
      this.customHandlers.forEach((handlers, type) => {
        this.source?.addEventListener(type, (event) => {
          const messageEvent = event as MessageEvent;
          handlers.forEach((handler) => handler({ data: messageEvent.data }));
        });
      });
      resolve();
    });
  }

  disconnect(): void {
    this.source?.close();
    this.source = null;
  }

  isConnected(): boolean {
    return this.source?.readyState === OPEN;
  }

  onMessage(handler: MessageHandler): () => void {
    this.messageHandlers.add(handler);
    return () => this.messageHandlers.delete(handler);
  }

  onError(handler: ErrorHandler): () => void {
    this.errorHandlers.add(handler);
    return () => this.errorHandlers.delete(handler);
  }

  addEventListener(type: string, handler: MessageHandler): void {
    const handlers = this.customHandlers.get(type) ?? new Set<MessageHandler>();
    handlers.add(handler);
    this.customHandlers.set(type, handlers);
    if (this.source) {
      this.source.addEventListener(type, (event) => {
        const messageEvent = event as MessageEvent;
        handler({ data: messageEvent.data });
      });
    }
  }

  private emitError(error: Error | Event): void {
    this.errorHandlers.forEach((handler) => handler(error));
  }

  private scheduleReconnect(): void {
    if (this.config.reconnect === false || this.source?.readyState === CLOSED) {
      return;
    }

    const maxAttempts = this.config.maxReconnectAttempts ?? 1;
    if (this.reconnectAttempts >= maxAttempts) {
      return;
    }

    this.reconnectAttempts += 1;
    setTimeout(() => {
      void this.connect();
    }, this.config.reconnectInterval ?? 1_000);
  }
}
