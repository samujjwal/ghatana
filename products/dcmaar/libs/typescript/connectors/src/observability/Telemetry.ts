import { EventEmitter } from 'events';

/**
 * @fileoverview Telemetry primitives for span tracking and structured logging.
 *
 * Exposes a lightweight tracing system (`Telemetry`) with batching + exporting capabilities and a
 * JSON structured logger utility (`StructuredLogger`). Integrate with connectors to trace lifecycle
 * hooks, feed metrics into dashboards, or export spans to OTLP-compatible collectors.
 *
 * @see {@link Telemetry}
 * @see {@link StructuredLogger}
 * @see {@link ../connectors/HttpConnector.HttpConnector | HttpConnector}
 */

/**
 * Representation of a single timed operation within a distributed trace.
 *
 * **Fields:**
 * - `traceId`: ID shared by all spans in same trace
 * - `spanId`: Unique identifier for this span
 * - `parentSpanId`: Optional parent span linkage
 * - `name`: Operation name (e.g., `connector.connect`)
 * - `startTime`/`endTime`: Millisecond timestamps
 * - `attributes`: Key/value metadata captured with the span
 * - `events`: Time-ordered span events (logs)
 * - `status`: Completion status information
 */
export interface Span {
  traceId: string;
  spanId: string;
  parentSpanId?: string;
  name: string;
  startTime: number;
  endTime?: number;
  attributes: Record<string, any>;
  events: SpanEvent[];
  status: SpanStatus;
}

/**
 * Structured event captured during span execution.
 */
export interface SpanEvent {
  name: string;
  timestamp: number;
  attributes?: Record<string, any>;
}

/**
 * Status information describing final outcome of a span.
 */
export interface SpanStatus {
  code: 'OK' | 'ERROR' | 'UNSET';
  message?: string;
}

/**
 * Configuration options for `Telemetry` instrumentation.
 */
export interface TelemetryConfig {
  /** Logical service name attached to every span. */
  serviceName: string;

  /** Optional semantic version string. */
  serviceVersion?: string;

  /** Deployment environment label (e.g., `dev`, `prod`). */
  environment?: string;

  /** Enable/disable telemetry instrumentation globally. */
  enabled?: boolean;

  /** Sampling probability (0-1) for span creation. */
  sampleRate?: number;

  /** Attributes merged into every span. */
  defaultAttributes?: Record<string, any>;

  /** Async exporter invoked with completed span batches. */
  exportSpans?: (spans: Span[]) => Promise<void>;

  /** Maximum number of spans before automatic export. */
  batchSize?: number;

  /** Maximum duration (ms) between automatic exports. */
  batchTimeout?: number;
}

/**
 * Lightweight tracing/telemetry manager with batching export support.
 *
 * Tracks spans, applies sampling, enriches attributes, and exports completed spans in configurable
 * batches. Emits lifecycle events for instrumentation hooks and integrates with `ConnectorManager`
 * event streams.
 *
 * **Example (manual span lifecycle):**
 * ```ts
 * const telemetry = new Telemetry({ serviceName: 'connector-service' });
 * const spanId = telemetry.startSpan('connector.connect');
 * try {
 *   await connector.connect();
 *   telemetry.endSpan(spanId, { code: 'OK' });
 * } catch (error) {
 *   telemetry.recordException(spanId, error as Error);
 *   telemetry.endSpan(spanId, { code: 'ERROR', message: (error as Error).message });
 * }
 * ```
 *
 * **Example (automatic span via `trace` helper):**
 * ```ts
 * const telemetry = new Telemetry({
 *   serviceName: 'batch-service',
 *   exportSpans: async (spans) => sendToOtlp(spans),
 *   batchSize: 50,
 *   batchTimeout: 2000,
 * });
 * await telemetry.trace('batch.process', async spanId => {
 *   telemetry.addSpanEvent(spanId, 'fetch.start');
 *   await processBatch();
 *   telemetry.addSpanEvent(spanId, 'fetch.end');
 * });
 * ```
 */
export class Telemetry extends EventEmitter {
  /** Resolved configuration with defaults. */
  private config: Required<TelemetryConfig>;
  /** Map of active spans keyed by span ID. */
  private spans: Map<string, Span> = new Map();
  /** Buffer of completed spans awaiting export. */
  private completedSpans: Span[] = [];
  /** Active export timer handle when batching. */
  private exportTimer: NodeJS.Timeout | null = null;

  /**
   * Creates a telemetry instance with defaulted configuration.
   */
  constructor(config: TelemetryConfig) {
    super();
    this.config = {
      serviceName: config.serviceName,
      serviceVersion: config.serviceVersion ?? '1.0.0',
      environment: config.environment ?? 'development',
      enabled: config.enabled ?? true,
      sampleRate: config.sampleRate ?? 1.0,
      defaultAttributes: config.defaultAttributes ?? {},
      exportSpans: config.exportSpans ?? (async () => {}),
      batchSize: config.batchSize ?? 100,
      batchTimeout: config.batchTimeout ?? 5000,
    };

    if (this.config.enabled) {
      this.startExportTimer();
    }
  }

  /**
   * Starts a new span and returns the generated span ID.
   *
   * **How it works:**
   * 1. Performs sampling check
   * 2. Generates span/trace IDs
   * 3. Seeds attributes from defaults + overrides
   * 4. Registers span in active map
   *
   * @param {string} name - Span name
   * @param {Record<string, any>} [attributes] - Additional attributes
   * @param {string} [parentSpanId] - Optional parent span linkage
   * @returns {string} Span ID (empty string if not sampled)
   * @fires Telemetry#spanStart
   */
  startSpan(
    name: string,
    attributes?: Record<string, any>,
    parentSpanId?: string
  ): string {
    if (!this.config.enabled || !this.shouldSample()) {
      return '';
    }

    const spanId = this.generateId();
    const traceId = parentSpanId 
      ? this.spans.get(parentSpanId)?.traceId ?? this.generateId()
      : this.generateId();

    const span: Span = {
      traceId,
      spanId,
      parentSpanId,
      name,
      startTime: Date.now(),
      attributes: {
        ...this.config.defaultAttributes,
        'service.name': this.config.serviceName,
        'service.version': this.config.serviceVersion,
        'deployment.environment': this.config.environment,
        ...attributes,
      },
      events: [],
      status: { code: 'UNSET' },
    };

    this.spans.set(spanId, span);

    this.emit('spanStart', {
      spanId,
      traceId,
      name,
      timestamp: span.startTime,
    });

    return spanId;
  }

  /**
   * Completes a span and queues it for export.
   *
   * @param {string} spanId - Span identifier
   * @param {SpanStatus} [status] - Final span status
   * @fires Telemetry#spanEnd
   */
  endSpan(spanId: string, status?: SpanStatus): void {
    if (!spanId || !this.config.enabled) {
      return;
    }

    const span = this.spans.get(spanId);
    if (!span) {
      return;
    }

    span.endTime = Date.now();
    span.status = status ?? { code: 'OK' };

    this.spans.delete(spanId);
    this.completedSpans.push(span);

    this.emit('spanEnd', {
      spanId,
      traceId: span.traceId,
      name: span.name,
      duration: span.endTime - span.startTime,
      status: span.status,
      timestamp: span.endTime,
    });

    // Export if batch is full
    if (this.completedSpans.length >= this.config.batchSize) {
      this.exportSpans().catch(error => {
        this.emit('error', error);
      });
    }
  }

  /**
   * Appends an event entry to an active span.
   *
   * @param {string} spanId - Span identifier
   * @param {string} name - Event name
   * @param {Record<string, any>} [attributes] - Event metadata
   */
  addSpanEvent(
    spanId: string,
    name: string,
    attributes?: Record<string, any>
  ): void {
    if (!spanId || !this.config.enabled) {
      return;
    }

    const span = this.spans.get(spanId);
    if (!span) {
      return;
    }

    span.events.push({
      name,
      timestamp: Date.now(),
      attributes,
    });
  }

  /**
   * Mutates attributes on an active span.
   *
   * @param {string} spanId - Span identifier
   * @param {Record<string, any>} attributes - Attributes to merge
   */
  setSpanAttributes(spanId: string, attributes: Record<string, any>): void {
    if (!spanId || !this.config.enabled) {
      return;
    }

    const span = this.spans.get(spanId);
    if (!span) {
      return;
    }

    Object.assign(span.attributes, attributes);
  }

  /**
   * Records an exception event and sets span status to error.
   *
   * @param {string} spanId - Span identifier
   * @param {Error} error - Error instance
   */
  recordException(spanId: string, error: Error): void {
    if (!spanId || !this.config.enabled) {
      return;
    }

    const span = this.spans.get(spanId);
    if (!span) {
      return;
    }

    span.events.push({
      name: 'exception',
      timestamp: Date.now(),
      attributes: {
        'exception.type': error.name,
        'exception.message': error.message,
        'exception.stacktrace': error.stack,
      },
    });

    span.status = {
      code: 'ERROR',
      message: error.message,
    };
  }

  /**
   * Executes async function within a managed span.
   *
   * @template T
   * @param {string} name - Span name
   * @param {(spanId: string) => Promise<T>} fn - Operation to trace
   * @param {Record<string, any>} [attributes] - Span attributes
   * @param {string} [parentSpanId] - Parent linkage
   * @returns {Promise<T>} Result of provided function
   */
  async trace<T>(
    name: string,
    fn: (spanId: string) => Promise<T>,
    attributes?: Record<string, any>,
    parentSpanId?: string
  ): Promise<T> {
    const spanId = this.startSpan(name, attributes, parentSpanId);

    try {
      const result = await fn(spanId);
      this.endSpan(spanId, { code: 'OK' });
      return result;
    } catch (error) {
      if (error instanceof Error) {
        this.recordException(spanId, error);
      }
      this.endSpan(spanId, {
        code: 'ERROR',
        message: error instanceof Error ? error.message : String(error),
      });
      throw error;
    }
  }

  /**
   * Returns currently active (open) spans.
   *
   * Handy for debugging stuck spans or implementing admin endpoints that surface in-flight traces.
   */
  getActiveSpans(): Span[] {
    return Array.from(this.spans.values());
  }

  /**
   * Returns snapshot of spans awaiting export.
   *
   * Callers can inspect this before shutdown to understand pending telemetry volume or perform
   * custom persistence.
   */
  getCompletedSpans(): Span[] {
    return [...this.completedSpans];
  }

  /**
   * Forces export pipeline to run immediately.
   *
   * Useful for flushing spans during integration tests or graceful shutdown sequences.
   */
  async flush(): Promise<void> {
    await this.exportSpans();
  }

  /**
   * Cleans up timers, flushes spans, and removes listeners.
   *
   * Pair with `ConnectorManager.destroy()` to ensure telemetry buffers are drained before process
   * exit.
   */
  async destroy(): Promise<void> {
    if (this.exportTimer) {
      clearInterval(this.exportTimer);
      this.exportTimer = null;
    }

    await this.flush();
    this.spans.clear();
    this.completedSpans = [];
    this.removeAllListeners();
  }

  /**
   * Determines whether a span should be sampled based on sample rate.
   */
  private shouldSample(): boolean {
    return Math.random() < this.config.sampleRate;
  }

  /**
   * Generates unique identifier for span/trace.
   *
   * Override in subclasses to plug deterministic ID generators or 128-bit trace IDs.
   */
  private generateId(): string {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Starts periodic export timer for batch flushing.
   */
  private startExportTimer(): void {
    this.exportTimer = setInterval(async () => {
      await this.exportSpans().catch(error => {
        this.emit('error', error);
      });
    }, this.config.batchTimeout);
  }

  /**
   * Ships completed spans via configured exporter.
   */
  private async exportSpans(): Promise<void> {
    if (this.completedSpans.length === 0) {
      return;
    }

    const spans = this.completedSpans.splice(0);

    try {
      await this.config.exportSpans(spans);
      
      this.emit('spansExported', {
        count: spans.length,
        timestamp: Date.now(),
      });
    } catch (error) {
      // Put spans back if export failed
      this.completedSpans.unshift(...spans);
      throw error;
    }
  }
}

/**
 * Factory helper for creating telemetry with defaults.
 *
 * Wraps `new Telemetry()` for parity with other connector factories such as `createLogger()`.
 */
export function createTelemetry(config: TelemetryConfig): Telemetry {
  return new Telemetry(config);
}

/**
 * Structured logger emitting consistent JSON log entries.
 *
 * Works alongside `Telemetry` to provide unified tracing + logging instrumentation without pulling
 * in heavier logging frameworks.
 */
export class StructuredLogger {
  private serviceName: string;
  private defaultContext: Record<string, any>;

  /**
   * @param {string} serviceName - Service identifier
   * @param {Record<string, any>} [defaultContext] - Default log context
   */
  constructor(serviceName: string, defaultContext?: Record<string, any>) {
    this.serviceName = serviceName;
    this.defaultContext = defaultContext ?? {};
  }

  /** Writes a JSON log entry with provided level. */
  private log(
    level: 'debug' | 'info' | 'warn' | 'error',
    message: string,
    context?: Record<string, any>
  ): void {
    const logEntry = {
      timestamp: new Date().toISOString(),
      level,
      service: this.serviceName,
      message,
      ...this.defaultContext,
      ...context,
    };

    console.log(JSON.stringify(logEntry));
  }

  /** Emits debug-level log. */
  debug(message: string, context?: Record<string, any>): void {
    this.log('debug', message, context);
  }

  /** Emits info-level log. */
  info(message: string, context?: Record<string, any>): void {
    this.log('info', message, context);
  }

  /** Emits warning-level log. */
  warn(message: string, context?: Record<string, any>): void {
    this.log('warn', message, context);
  }

  /** Emits error-level log with optional serialized error. */
  error(message: string, error?: Error, context?: Record<string, any>): void {
    this.log('error', message, {
      ...context,
      error: error ? {
        name: error.name,
        message: error.message,
        stack: error.stack,
      } : undefined,
    });
  }
}

/**
 * Factory helper for structured logger creation.
 */
export function createLogger(
  serviceName: string,
  defaultContext?: Record<string, any>
): StructuredLogger {
  return new StructuredLogger(serviceName, defaultContext);
}
