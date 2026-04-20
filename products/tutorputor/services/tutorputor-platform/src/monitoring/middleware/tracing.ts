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
import type { Span } from '@opentelemetry/api';
import { trace, SpanKind, SpanStatusCode } from '@opentelemetry/api';
import { addSpanAttributes, addSpanEvent } from '../tracing.js';

type RequestUser = {
  id?: string;
  email?: string;
};

type TracedRequest = FastifyRequest & {
  user?: string | object | Buffer;
  tenantId?: string;
  span?: Span;
  startedAt?: number;
};

function isRequestUser(value: string | object | Buffer | undefined): value is RequestUser {
  return !!value && typeof value === 'object' && !Buffer.isBuffer(value);
}

/**
 * Tracing middleware for Fastify
 */
export async function tracingMiddleware(
  request: FastifyRequest,
  reply: FastifyReply,
): Promise<void> {
  const tracedRequest = request as TracedRequest;
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
  if (isRequestUser(tracedRequest.user)) {
    span.setAttributes({
      ...(tracedRequest.user.id ? { 'user.id': tracedRequest.user.id } : {}),
      ...(tracedRequest.user.email ? { 'user.email': tracedRequest.user.email } : {}),
    });
  }

  // Add tenant context if available
  if (tracedRequest.tenantId) {
    span.setAttribute('tenant.id', tracedRequest.tenantId);
  }

  // Add request ID for correlation
  const requestId = request.id;
  span.setAttribute('request.id', requestId);

  // Attach span to request context
  tracedRequest.span = span;
  tracedRequest.startedAt = Date.now();

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
      duration: tracedRequest.startedAt
        ? Date.now() - tracedRequest.startedAt
        : 0,
    });

    span.end();
  });
}
