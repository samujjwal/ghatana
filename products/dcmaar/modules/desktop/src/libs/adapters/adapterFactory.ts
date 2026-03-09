/**
 * Adapter factory for dynamic creation of sources and sinks.
 * Central registry for all adapter types.
 */

import type { TelemetrySource, ControlSink, SourceConfig, SinkConfig } from './types';
import { createMockSource } from './sources/MockSource';
import { createLocalFileSource } from './sources/LocalFileSource';
import { createLoopbackDaemonSource } from './sources/LoopbackDaemonSource';
import { createHttpSource } from './sources/HttpSource';
import { createGrpcSource } from './sources/GrpcSource';
import { createWebSocketSource } from './sources/WebSocketSource';
import { createMockSink } from './sinks/MockSink';
import { createLocalFileSink } from './sinks/LocalFileSink';
import { createLoopbackDaemonSink } from './sinks/LoopbackDaemonSink';
import { createHttpSink } from './sinks/HttpSink';
import { createGrpcSink } from './sinks/GrpcSink';
import { createWebSocketSink } from './sinks/WebSocketSink';

export type SourceFactory = (options: Record<string, unknown>) => TelemetrySource;
export type SinkFactory = (options: Record<string, unknown>) => ControlSink;

export class AdapterFactory {
  private sourceFactories: Map<string, SourceFactory>;
  private sinkFactories: Map<string, SinkFactory>;

  constructor() {
    this.sourceFactories = new Map();
    this.sinkFactories = new Map();
    this.registerBuiltInAdapters();
  }

  registerSourceFactory(type: string, factory: SourceFactory): void {
    this.sourceFactories.set(type, factory);
  }

  registerSinkFactory(type: string, factory: SinkFactory): void {
    this.sinkFactories.set(type, factory);
  }

  createSource(config: SourceConfig): TelemetrySource {
    const factory = this.sourceFactories.get(config.type);

    if (!factory) {
      throw new Error(`Unknown source type: ${config.type}`);
    }

    return factory(config.options);
  }

  createSink(config: SinkConfig): ControlSink {
    const factory = this.sinkFactories.get(config.type);

    if (!factory) {
      throw new Error(`Unknown sink type: ${config.type}`);
    }

    return factory(config.options);
  }

  listSourceTypes(): string[] {
    return Array.from(this.sourceFactories.keys());
  }

  listSinkTypes(): string[] {
    return Array.from(this.sinkFactories.keys());
  }

  private registerBuiltInAdapters(): void {
    // Sources
    this.registerSourceFactory('mock', opts => createMockSource(opts as any));
    this.registerSourceFactory('file', opts => createLocalFileSource(opts as any));
    this.registerSourceFactory('daemon', opts => createLoopbackDaemonSource(opts as any));
    this.registerSourceFactory('http', opts => createHttpSource(opts as any));
    this.registerSourceFactory('grpc', opts => createGrpcSource(opts as any));
    // WebSocket adapters for agent and extension
    this.registerSourceFactory('agent', opts => createWebSocketSource(opts as any));
    this.registerSourceFactory('extension', opts => createWebSocketSource(opts as any));

    // Sinks
    this.registerSinkFactory('mock', opts => createMockSink(opts as any));
    this.registerSinkFactory('file', opts => createLocalFileSink(opts as any));
    this.registerSinkFactory('daemon', opts => createLoopbackDaemonSink(opts as any));
    this.registerSinkFactory('http', opts => createHttpSink(opts as any));
    this.registerSinkFactory('grpc', opts => createGrpcSink(opts as any));
    // WebSocket adapters for agent and extension
    this.registerSinkFactory('agent', opts => createWebSocketSink(opts as any));
    this.registerSinkFactory('extension', opts => createWebSocketSink(opts as any));
  }
}

export const createAdapterFactory = (): AdapterFactory => {
  return new AdapterFactory();
};

// Singleton instance
export const adapterFactory = createAdapterFactory();
