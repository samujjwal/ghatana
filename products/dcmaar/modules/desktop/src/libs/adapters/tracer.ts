/**
 * Minimal OpenTelemetry-compatible tracer for adapter instrumentation.
 * Supports span creation, attributes, and status tracking.
 */

import type { Tracer, Span } from './types';

export interface TracerConfig {
  serviceName: string;
  enabled: boolean;
  sink?: (span: SpanData) => void;
}

export interface SpanData {
  traceId: string;
  spanId: string;
  parentSpanId?: string;
  name: string;
  startTime: number;
  endTime?: number;
  attributes: Record<string, unknown>;
  status: { code: 'ok' | 'error'; message?: string };
}

export class SimpleTracer implements Tracer {
  private readonly config: TracerConfig;
  private readonly traceId: string;

  constructor(config: TracerConfig) {
    this.config = config;
    this.traceId = this.generateId();
  }

  startSpan(name: string, attributes?: Record<string, unknown>): Span {
    if (!this.config.enabled) {
      return new NoOpSpan();
    }

    return new SimpleSpan(
      {
        traceId: this.traceId,
        spanId: this.generateId(),
        name,
        startTime: Date.now(),
        attributes: attributes ?? {},
        status: { code: 'ok' },
      },
      this.config.sink,
    );
  }

  private generateId(): string {
    return Math.random().toString(36).substring(2, 15) +
      Math.random().toString(36).substring(2, 15);
  }
}

class SimpleSpan implements Span {
  private readonly data: SpanData;
  private readonly sink?: (span: SpanData) => void;

  constructor(data: SpanData, sink?: (span: SpanData) => void) {
    this.data = data;
    this.sink = sink;
  }

  setAttribute(key: string, value: unknown): void {
    this.data.attributes[key] = value;
  }

  setStatus(status: { code: 'ok' | 'error'; message?: string }): void {
    this.data.status = status;
  }

  end(): void {
    this.data.endTime = Date.now();

    if (this.sink) {
      this.sink(this.data);
    } else {
      this.defaultSink();
    }
  }

  private defaultSink(): void {
    const duration = this.data.endTime
      ? this.data.endTime - this.data.startTime
      : 0;

    console.debug('[TRACE]', {
      name: this.data.name,
      traceId: this.data.traceId,
      spanId: this.data.spanId,
      durationMs: duration,
      status: this.data.status,
      attributes: this.data.attributes,
    });
  }
}

class NoOpSpan implements Span {
  setAttribute(): void {
    // no-op
  }

  setStatus(): void {
    // no-op
  }

  end(): void {
    // no-op
  }
}

export const createTracer = (config: TracerConfig): Tracer => {
  return new SimpleTracer(config);
};
