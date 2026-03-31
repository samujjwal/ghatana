/**
 * Distributed Tracing with OpenTelemetry
 * Part of Execution Plan item #6: Add Distributed Tracing
 * 
 * Provides automatic instrumentation for HTTP requests, database queries,
 * and custom spans across the TutorPutor platform.
 */

import { NodeSDK } from '@opentelemetry/sdk-node';
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-http';
import { PeriodicExportingMetricReader } from '@opentelemetry/sdk-metrics';
import { Resource } from '@opentelemetry/resources';
import { SemanticResourceAttributes } from '@opentelemetry/semantic-conventions';
import { trace, SpanStatusCode, context } from '@opentelemetry/api';
import type { FastifyInstance, FastifyPluginAsync } from 'fastify';

/**
 * OpenTelemetry configuration options
 */
export interface TracingConfig {
  /** Service name for identification */
  serviceName: string;
  /** Service version */
  serviceVersion?: string;
  /** OTLP endpoint for traces */
  traceEndpoint?: string;
  /** OTLP endpoint for metrics */
  metricsEndpoint?: string;
  /** Sampling ratio (0-1) */
  samplingRatio?: number;
  /** Environment */
  environment?: string;
}

/**
 * Initialize OpenTelemetry SDK
 */
export function initTracing(config: TracingConfig): NodeSDK {
  const resource = new Resource({
    [SemanticResourceAttributes.SERVICE_NAME]: config.serviceName,
    [SemanticResourceAttributes.SERVICE_VERSION]: config.serviceVersion || '1.0.0',
    [SemanticResourceAttributes.DEPLOYMENT_ENVIRONMENT]: config.environment || 'development',
  });

  const traceExporter = new OTLPTraceExporter({
    url: config.traceEndpoint || 'http://localhost:4318/v1/traces',
  });

  const metricExporter = new OTLPMetricExporter({
    url: config.metricsEndpoint || 'http://localhost:4318/v1/metrics',
  });

  const sdk = new NodeSDK({
    resource,
    traceExporter,
    metricReader: new PeriodicExportingMetricReader({
      exporter: metricExporter,
      exportIntervalMillis: 60000,
    }),
    instrumentations: getNodeAutoInstrumentations({
      // Enable specific instrumentations
      '@opentelemetry/instrumentation-http': { enabled: true },
      '@opentelemetry/instrumentation-fastify': { enabled: true },
      '@opentelemetry/instrumentation-pg': { enabled: true },
      '@opentelemetry/instrumentation-redis': { enabled: true },
    }),
  });

  sdk.start();
  console.log(`✅ OpenTelemetry tracing initialized for ${config.serviceName}`);

  return sdk;
}

/**
 * Fastify plugin for distributed tracing
 */
export const tracingPlugin: FastifyPluginAsync<TracingConfig> = async (
  fastify: FastifyInstance,
  options: TracingConfig
) => {
  const tracer = trace.getTracer(options.serviceName, options.serviceVersion);

  // Add trace ID to each request
  fastify.addHook('onRequest', async (request, reply) => {
    const span = tracer.startSpan(`${request.method} ${request.routerPath}`, {
      attributes: {
        'http.method': request.method,
        'http.url': request.url,
        'http.route': request.routerPath,
        'http.host': request.hostname,
        'http.user_agent': request.headers['user-agent'],
        'http.request_id': request.id,
      },
    });

    // Store span in request context
    request.tracing = { span, tracer };

    // Add trace ID to response headers
    const spanContext = span.spanContext();
    reply.header('X-Trace-Id', spanContext.traceId);
    reply.header('X-Span-Id', spanContext.spanId);
  });

  // End span on response
  fastify.addHook('onSend', async (request, reply, payload) => {
    if (request.tracing?.span) {
      request.tracing.span.setAttribute('http.status_code', reply.statusCode);
      
      if (reply.statusCode >= 400) {
        request.tracing.span.setStatus({
          code: SpanStatusCode.ERROR,
          message: `HTTP ${reply.statusCode}`,
        });
      }

      request.tracing.span.end();
    }
  });

  // Decorate fastify with tracing utilities
  fastify.decorate('trace', {
    startSpan: (name: string, attributes?: Record<string, any>) => {
      return tracer.startSpan(name, { attributes });
    },
    withSpan: async <T>(
      name: string,
      fn: () => Promise<T>,
      attributes?: Record<string, any>
    ): Promise<T> => {
      const span = tracer.startSpan(name, { attributes });
      try {
        const result = await fn();
        span.setStatus({ code: SpanStatusCode.OK });
        return result;
      } catch (error) {
        span.setStatus({
          code: SpanStatusCode.ERROR,
          message: error instanceof Error ? error.message : 'Unknown error',
        });
        span.recordException(error as Error);
        throw error;
      } finally {
        span.end();
      }
    },
  });
};

/**
 * Database query tracing decorator
 */
export function traceQuery<T extends (...args: any[]) => Promise<unknown>>(
  operation: string,
  fn: T
): T {
  const tracer = trace.getTracer('tutorputor-db');

  return (async (...args: any[]) => {
    const span = tracer.startSpan(`db.${operation}`, {
      attributes: {
        'db.system': 'postgresql',
        'db.operation': operation,
      },
    });

    try {
      const result = await fn(...args);
      span.setStatus({ code: SpanStatusCode.OK });
      return result;
    } catch (error) {
      span.setStatus({
        code: SpanStatusCode.ERROR,
        message: error instanceof Error ? error.message : 'Query failed',
      });
      span.recordException(error as Error);
      throw error;
    } finally {
      span.end();
    }
  }) as T;
}

/**
 * AI service call tracing
 */
export function traceAIRequest<T extends (...args: any[]) => Promise<unknown>>(
  model: string,
  fn: T
): T {
  const tracer = trace.getTracer('tutorputor-ai');

  return (async (...args: any[]) => {
    const span = tracer.startSpan(`ai.request`, {
      attributes: {
        'ai.model': model,
        'ai.provider': 'openai',
      },
    });

    const startTime = Date.now();

    try {
      const result = await fn(...args);
      const duration = Date.now() - startTime;
      
      span.setAttributes({
        'ai.response_time_ms': duration,
        'ai.tokens_input': (result as Record<string, unknown>)?.usage?.prompt_tokens || 0,
        'ai.tokens_output': (result as Record<string, unknown>)?.usage?.completion_tokens || 0,
      });
      span.setStatus({ code: SpanStatusCode.OK });
      
      return result;
    } catch (error) {
      span.setStatus({
        code: SpanStatusCode.ERROR,
        message: error instanceof Error ? error.message : 'AI request failed',
      });
      span.recordException(error as Error);
      throw error;
    } finally {
      span.end();
    }
  }) as T;
}

// Type augmentations
declare module 'fastify' {
  interface FastifyRequest {
    tracing?: {
      span: unknown;
      tracer: unknown;
    };
  }

  interface FastifyInstance {
    trace: {
      startSpan: (name: string, attributes?: Record<string, any>) => any;
      withSpan: <T>(
        name: string,
        fn: () => Promise<T>,
        attributes?: Record<string, any>
      ) => Promise<T>;
    };
  }
}

export default tracingPlugin;
