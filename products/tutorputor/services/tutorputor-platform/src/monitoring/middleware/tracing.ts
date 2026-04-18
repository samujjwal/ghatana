/**
 * Tracing Middleware
 *
 * Fastify middleware for automatic HTTP request tracing.
 * Adds span context to all incoming requests.
 *
 * @doc.type module
 * @doc.purpose HTTP request tracing middleware
 * @doc.layer platform
 * @doc.pattern Middleware
 */

import type { FastifyRequest, FastifyReply } from 'fastify';
import { trace, SpanKind, SpanStatusCode } from '@opentelemetry/api';
import { addSpanAttributes, addSpanEvent } from '../tracing.js';

/**
 * Tracing middleware for Fastify
 */
export async function tracingMiddleware(
  request: FastifyRequest,
  reply: FastifyReply,
): Promise<void> {
  const tracer = trace.getTracer(process.env.SERVICE_NAME || 'tutorputor-platform');
  const spanName = `${request.method} ${request.url}`;

  const span = tracer.startSpan(spanName, {
    kind: SpanKind.SERVER,
  });

  // Add HTTP attributes
  span.setAttributes({
    'http.method': request.method,
    'http.url': request.url,
    'http.host': request.headers.host,
    'http.scheme': request.protocol,
    'http.user_agent': request.headers['user-agent'],
    'http.status_code': reply.statusCode,
  });

  // Add user context if available
  if (request.user) {
    span.setAttributes({
      'user.id': request.user.id,
      'user.email': request.user.email,
    });
  }

  // Add tenant context if available
  if (request.tenantId) {
    span.setAttribute('tenant.id', request.tenantId);
  }

  // Add request ID for correlation
  const requestId = request.id;
  span.setAttribute('request.id', requestId);

  // Attach span to request context
  request.span = span as any;

  // Log request start
  addSpanEvent('request.start', {
    method: request.method,
    url: request.url,
    requestId,
  });

  // Hook into response
  reply.raw.on('finish', () => {
    span.setAttribute('http.status_code', reply.statusCode);

    if (reply.statusCode >= 400) {
      span.setStatus({
        code: SpanStatusCode.ERROR,
        message: `HTTP ${reply.statusCode}`,
      });
    } else {
      span.setStatus({ code: SpanStatusCode.OK });
    }

    addSpanEvent('request.end', {
      statusCode: reply.statusCode,
      duration: reply.getResponseTime(),
    });

    span.end();
  });
}
