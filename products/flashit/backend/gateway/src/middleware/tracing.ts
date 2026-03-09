/**
 * OpenTelemetry Tracing Middleware for Fastify
 *
 * Adds FlashIt-specific span attributes (userId, tier, sphereId)
 * and creates custom spans for AI operations.
 *
 * @doc.type middleware
 * @doc.purpose Add custom trace context to OpenTelemetry spans
 * @doc.layer product
 * @doc.pattern Middleware
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { trace, context, SpanStatusCode, Span } from '@opentelemetry/api';

const tracer = trace.getTracer('flashit-gateway', '1.0.0');

/**
 * Register tracing hooks that enrich spans with FlashIt context
 */
export async function registerTracingMiddleware(app: FastifyInstance): Promise<void> {
  // Add user context to every span
  app.addHook('onRequest', async (request: FastifyRequest, _reply: FastifyReply) => {
    const span = trace.getActiveSpan();
    if (!span) return;

    // Add route info
    span.setAttribute('http.route.pattern', request.routeOptions?.url || request.url);

    // Add user info if authenticated
    const user = (request as any).user;
    if (user) {
      span.setAttribute('flashit.user.id', user.id);
      span.setAttribute('flashit.user.tier', user.tier || 'FREE');
      span.setAttribute('flashit.user.role', user.role || 'USER');
    }

    // Add request ID for correlation
    const requestId = request.headers['x-request-id'] as string;
    if (requestId) {
      span.setAttribute('flashit.request.id', requestId);
    }
  });

  // Record response status in the span
  app.addHook('onResponse', async (request: FastifyRequest, reply: FastifyReply) => {
    const span = trace.getActiveSpan();
    if (!span) return;

    span.setAttribute('http.response.status_code', reply.statusCode);

    if (reply.statusCode >= 500) {
      span.setStatus({ code: SpanStatusCode.ERROR });
    }
  });

  // Capture errors
  app.addHook('onError', async (request: FastifyRequest, _reply: FastifyReply, error: Error) => {
    const span = trace.getActiveSpan();
    if (!span) return;

    span.recordException(error);
    span.setStatus({
      code: SpanStatusCode.ERROR,
      message: error.message,
    });
  });
}

/**
 * Create a custom span for AI operations
 */
export function traceAIOperation<T>(
  operationName: string,
  attributes: Record<string, string | number>,
  fn: (span: Span) => Promise<T>
): Promise<T> {
  return tracer.startActiveSpan(`ai.${operationName}`, async (span) => {
    try {
      for (const [key, value] of Object.entries(attributes)) {
        span.setAttribute(`flashit.ai.${key}`, value);
      }
      const result = await fn(span);
      span.setStatus({ code: SpanStatusCode.OK });
      return result;
    } catch (error) {
      span.recordException(error as Error);
      span.setStatus({ code: SpanStatusCode.ERROR, message: (error as Error).message });
      throw error;
    } finally {
      span.end();
    }
  });
}

/**
 * Create a custom span for database operations
 */
export function traceDBOperation<T>(
  operationName: string,
  fn: (span: Span) => Promise<T>
): Promise<T> {
  return tracer.startActiveSpan(`db.${operationName}`, async (span) => {
    try {
      span.setAttribute('db.system', 'postgresql');
      span.setAttribute('db.operation', operationName);
      const result = await fn(span);
      span.setStatus({ code: SpanStatusCode.OK });
      return result;
    } catch (error) {
      span.recordException(error as Error);
      span.setStatus({ code: SpanStatusCode.ERROR, message: (error as Error).message });
      throw error;
    } finally {
      span.end();
    }
  });
}
