import { vi } from 'vitest';

const storage = new Map<string, any>();

export class BrowserStorageAdapter {
  async get<T>(key: string): Promise<T | undefined> {
    return storage.get(key);
  }

  async set(key: string, value: unknown): Promise<void> {
    storage.set(key, value);
  }

  async remove(key: string): Promise<void> {
    storage.delete(key);
  }

  async clear(): Promise<void> {
    storage.clear();
  }
}

export class BrowserMessageRouter {
  private handlers = new Map<string, (message: { type: string; payload?: unknown }) => any>();

  onMessageType(type: string, handler: (message: { type: string; payload?: unknown }) => any) {
    this.handlers.set(type, handler);
  }

  async dispatch(type: string, payload?: unknown): Promise<any> {
    const handler = this.handlers.get(type);
    if (!handler) {
      throw new Error(`No handler registered for ${type}`);
    }
    return handler({ type, payload });
  }

  async sendToBackground(message: { type: string; payload?: unknown }): Promise<any> {
    return this.dispatch(message.type, message.payload);
  }
}

export class BatchPageMetricsCollector {
  private callback: ((data: any) => void) | undefined;
  interval = 0;

  startAutoCollect(interval: number, callback: (data: any) => void) {
    this.interval = interval;
    this.callback = callback;
  }

  stopAutoCollect() {
    this.callback = undefined;
  }

  // Test helper method
  emit(data: any) {
    this.callback?.(data);
  }
}

export class UnifiedBrowserEventCapture {
  private handler: ((event: any) => void) | undefined;

  start() { }

  stop() {
    this.handler = undefined;
  }

  onEvent(handler: (event: any) => void) {
    this.handler = handler;
  }

  // Test helper method
  emit(event: any) {
    this.handler?.(event);
  }

  captureAll() { }
}

export class BaseEventSource<T = any> {
  protected listeners: Array<(event: T) => void> = [];
  status: 'idle' | 'started' | 'stopped' | 'error' = 'idle';

  protected stats = {
    sent: 0,
    errors: 0,
  };

  onEvent(handler: (event: T) => void) {
    this.listeners.push(handler);
  }

  protected emit(event: T) {
    for (const handler of this.listeners) {
      handler(event);
    }
    this.stats.sent += 1;
  }

  async start(): Promise<void> {
    this.status = 'started';
  }

  async stop(): Promise<void> {
    this.status = 'stopped';
  }
}

export class BaseEventProcessor<I = any, O = any> {
  readonly name = 'mock-processor';

  protected stats = {
    processed: 0,
    errors: 0,
  };

  async initialize(): Promise<void> { }

  async shutdown(): Promise<void> { }

  // In tests we usually don't rely on this type guard; return true by default
  // so processors will accept events.
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  canProcess(_event: unknown): _event is I {
    return true as any;
  }

  async process(event: I): Promise<O | null> {
    this.stats.processed += 1;
    return event as unknown as O;
  }
}

export class BaseEventSink<T = any> {
  readonly name = 'mock-sink';

  protected stats = {
    sent: 0,
    errors: 0,
  };

  async initialize(): Promise<void> { }

  async shutdown(): Promise<void> { }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  async send(_event: T): Promise<void> {
    this.stats.sent += 1;
  }

  async sendBatch(events: T[]): Promise<void> {
    for (const event of events) {
      await this.send(event);
    }
  }
}

export class EventPipeline {
  name: string;
  continueOnError?: boolean;

  private sources: any[] = [];
  private processors: any[] = [];
  private sinks: any[] = [];

  // For convenience in tests, expose the first registered sink
  storageSink: any;

  constructor(config: { name: string; continueOnError?: boolean }) {
    this.name = config.name;
    this.continueOnError = config.continueOnError;
  }

  registerSource(source: any): this {
    this.sources.push(source);
    return this;
  }

  registerProcessor(processor: any): this {
    this.processors.push(processor);
    return this;
  }

  registerSink(sink: any): this {
    this.sinks.push(sink);
    if (!this.storageSink) {
      this.storageSink = sink;
    }
    return this;
  }

  async start(): Promise<void> { }

  async stop(): Promise<void> { }
}

export class BaseExtensionController<C = Record<string, unknown>, S extends { initialized: boolean } = { initialized: boolean }> {
  protected config: C;
  protected state: S;

  constructor(initialState: S, config: C) {
    this.state = { ...initialState };
    this.config = { ...config };
  }

  async initialize(): Promise<void> {
    const loaded = (await this.loadConfiguration()) as C | null;
    if (loaded) {
      await this.applyConfigChanges(loaded, this.config);
      this.config = loaded;
    }
    await this.doInitialize();
    this.state = { ...this.state, initialized: true };
  }

  async shutdown(): Promise<void> {
    await this.doShutdown();
    this.state = { ...this.state, initialized: false };
  }

  // Hooks for subclasses

  protected async doInitialize(): Promise<void> { }

  protected async doShutdown(): Promise<void> { }

  protected async loadConfiguration(): Promise<C | null> {
    return null;
  }

  protected async saveConfiguration(config: C): Promise<void> {
    this.config = config;
  }

  protected async applyConfigChanges(_newConfig: C, _oldConfig: C): Promise<void> { }

  protected updateState(partial: Partial<S>): void {
    this.state = { ...this.state, ...partial };
  }

  protected getState(): S {
    return { ...this.state };
  }

  protected getConfig(): C {
    return { ...this.config };
  }

  protected async updateConfig(partial: Partial<C>): Promise<void> {
    const newConfig = { ...(this.config as any), ...(partial as any) } as C;
    await this.applyConfigChanges(newConfig, this.config);
    this.config = newConfig;
    await this.saveConfiguration(newConfig);
  }

  protected log(..._args: unknown[]): void { }
  protected logError(..._args: unknown[]): void { }
}

export const __storageStore = storage;

export type ControllerConfig = Record<string, any>;
export type ControllerState = { initialized: boolean };
