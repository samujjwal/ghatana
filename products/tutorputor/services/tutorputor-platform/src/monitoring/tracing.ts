/**
 * Distributed Tracing Configuration
 *
 * Configures OpenTelemetry for distributed tracing across the TutorPutor platform.
 * Exports traces to OTLP collector for visualization and analysis.
 *
 * @doc.type module
 * @doc.purpose Distributed tracing setup for observability
 * @doc.layer platform
 * @doc.pattern Infrastructure
 */

import { trace, context, propagation, Span, SpanStatusCode, SpanKind } from '@opentelemetry/api';
import { NodeTracerProvider } from '@opentelemetry/sdk-trace-node';
import { Resource } from '@opentelemetry/resources';
import { SemanticResourceAttributes } from '@opentelemetry/semantic-conventions';
import { SimpleSpanProcessor } from '@opentelemetry/sdk-trace-base';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-grpc';
import { createStandaloneLogger } from '@tutorputor/core/logger';

const logger = createStandaloneLogger({ component: 'Tracing' });

// Configuration
const OTLP_ENDPOINT = process.env.OTLP_ENDPOINT || 'http://localhost:4317';
const SERVICE_NAME = process.env.SERVICE_NAME || 'tutorputor-platform';
const ENVIRONMENT = process.env.NODE_ENV || 'development';

/**
 * Initialize OpenTelemetry tracing
 */
export function initializeTracing(): void {
  try {
    const provider = new NodeTracerProvider({
      resource: new Resource({
        [SemanticResourceAttributes.SERVICE_NAME]: SERVICE_NAME,
        [SemanticResourceAttributes.DEPLOYMENT_ENVIRONMENT]: ENVIRONMENT,
      }),
    });

    const exporter = new OTLPTraceExporter({
      url: OTLP_ENDPOINT,
    });

    provider.addSpanProcessor(new SimpleSpanProcessor(exporter));

    provider.register();

    logger.info({
      message: 'Tracing initialized',
      service: SERVICE_NAME,
      endpoint: OTLP_ENDPOINT,
    });
  } catch (error) {
    logger.error({ error }, 'Failed to initialize tracing');
  }
}

/**
 * Create a span with automatic error handling
 */
export async function withSpan<T>(
  name: string,
  fn: (span: Span) => Promise<T>,
  kind: SpanKind = SpanKind.INTERNAL,
): Promise<T> {
  const tracer = trace.getTracer(SERVICE_NAME);
  const span = tracer.startSpan(name, { kind });

  try {
    const result = await fn(span);
    span.setStatus({ code: SpanStatusCode.OK });
    return result;
  } catch (error) {
    span.setStatus({
      code: SpanStatusCode.ERROR,
      message: error instanceof Error ? error.message : String(error),
    });
    span.recordException(error instanceof Error ? error : new Error(String(error)));
    throw error;
  } finally {
    span.end();
  }
}

/**
 * Add attributes to the current span
 */
export function addSpanAttributes(attributes: Record<string, unknown>): void {
  const span = trace.getActiveSpan();
  if (span) {
    span.setAttributes(attributes);
  }
}

/**
 * Add event to the current span
 */
export function addSpanEvent(name: string, attributes?: Record<string, unknown>): void {
  const span = trace.getActiveSpan();
  if (span) {
    span.addEvent(name, attributes);
  }
}

/**
 * Get the current trace ID
 */
export function getCurrentTraceId(): string | undefined {
  const span = trace.getActiveSpan();
  return span?.spanContext().traceId;
}

/**
 * Get the current span ID
 */
export function getCurrentSpanId(): string | undefined {
  const span = trace.getActiveSpan();
  return span?.spanContext().spanId;
}
