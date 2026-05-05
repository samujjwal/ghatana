import Fastify, { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import fastifyWebsocket from '@fastify/websocket';
import fastifyCors from '@fastify/cors';
import WebSocket from 'ws';
import { randomUUID } from 'node:crypto';
import { verifyJwt, extractBearerToken } from './jwt.js';
import type { JwtPayload } from './jwt.js';
import { GatewayMetrics } from './metrics.js';
export type { GatewayMetricsSnapshot } from './metrics.js';
export { GatewayMetrics } from './metrics.js';

/**
 * Maximum proxy response body size in bytes (16 MiB).
 * Responses larger than this are truncated with a 502 to prevent unbounded memory use.
 * Exported so tests can verify the limit without hard-coding it.
 */
export const MAX_PROXY_BODY_BYTES = 16 * 1024 * 1024; // 16 MiB

/**
 * Read a Fetch API `Response` body up to `maxBytes`.
 * Returns `{ body, truncated }`:
 *  - `body`: concatenated bytes (at most `maxBytes` long).
 *  - `truncated`: true when the stream exceeded `maxBytes`.
 */
async function readBodyCapped(
  res: Response,
  maxBytes: number,
): Promise<{ body: Uint8Array; truncated: boolean }> {
  if (!res.body) {
    return { body: new Uint8Array(0), truncated: false };
  }
  const reader = res.body.getReader();
  const chunks: Uint8Array[] = [];
  let totalBytes = 0;
  let truncated = false;

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      const remaining = maxBytes - totalBytes;
      if (value.length <= remaining) {
        chunks.push(value);
        totalBytes += value.length;
      } else {
        // Partial last chunk
        chunks.push(value.slice(0, remaining));
        totalBytes += remaining;
        truncated = true;
        break;
      }
    }
  } finally {
    reader.cancel().catch(() => {});
  }

  const body = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    body.set(chunk, offset);
    offset += chunk.length;
  }
  return { body, truncated };
}

const CORRELATION_ID_HEADER = 'x-correlation-id';
const TRACEPARENT_HEADER = 'traceparent';

export const DEFAULT_REQUEST_BODY_LIMIT_BYTES = 1 * 1024 * 1024; // 1 MiB
export const DEFAULT_RATE_LIMIT_WINDOW_MS = 60 * 1000; // 60s
export const DEFAULT_RATE_LIMIT_MAX_REQUESTS = 300;
export const DEFAULT_BACKEND_TIMEOUT_MS = 10 * 1000; // 10s
export const DEFAULT_BACKEND_RETRY_ATTEMPTS = 3;
export const DEFAULT_BACKEND_RETRY_INITIAL_BACKOFF_MS = 150;
export const DEFAULT_BACKEND_RETRY_MAX_BACKOFF_MS = 2000;
export const DEFAULT_BREAKER_FAILURE_THRESHOLD = 5;
export const DEFAULT_BREAKER_OPEN_MS = 30 * 1000; // 30s

function extractHeaderTenantId(value: string | string[] | undefined): string | null {
  if (typeof value === 'string' && value.trim().length > 0) {
    return value.trim();
  }
  if (Array.isArray(value) && value.length > 0 && value[0].trim().length > 0) {
    return value[0].trim();
  }
  return null;
}

function extractPayloadTenantId(payload: JwtPayload): string | null {
  const tenantId = payload['tenantId'];
  return typeof tenantId === 'string' && tenantId.trim().length > 0 ? tenantId.trim() : null;
}

function extractCorrelationId(value: string | string[] | undefined): string | null {
  if (typeof value === 'string' && value.trim().length > 0) {
    return value.trim();
  }
  if (Array.isArray(value) && value.length > 0 && value[0].trim().length > 0) {
    return value[0].trim();
  }
  return null;
}

/**
 * Parse a W3C Trace Context `traceparent` header into its traceId and spanId
 * components for OTel-aligned structured log correlation.
 *
 * Format: `{version}-{traceId(32hex)}-{parentId(16hex)}-{flags(2hex)}`
 * Returns `null` when the header is absent or malformed.
 */
function parseTraceparent(
  traceparent: string,
): { traceId: string; spanId: string } | null {
  const parts = traceparent.split('-');
  if (parts.length < 4) {
    return null;
  }
  const traceId = parts[1];
  const spanId = parts[2];
  if (
    typeof traceId === 'string' && traceId.length === 32 &&
    typeof spanId === 'string' && spanId.length === 16
  ) {
    return { traceId, spanId };
  }
  return null;
}

function resolveCorrelationId(request: FastifyRequest): string {
  return extractCorrelationId(request.headers[CORRELATION_ID_HEADER]) ?? randomUUID();
}

function resolveTraceparent(request: FastifyRequest): string {
  const current = request.headers[TRACEPARENT_HEADER];
  if (typeof current === 'string' && current.trim().length > 0) {
    return current.trim();
  }
  const traceId = randomUUID().replace(/-/g, '');
  const spanId = randomUUID().replace(/-/g, '').slice(0, 16);
  return `00-${traceId}-${spanId}-01`;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function shouldRetryStatus(status: number): boolean {
  return status === 502 || status === 503 || status === 504;
}

async function checkBackendReadiness(backendUrl: string, correlationId: string): Promise<Response> {
  return fetch(`${backendUrl}/health`, {
    method: 'GET',
    headers: { [CORRELATION_ID_HEADER]: correlationId },
  });
}

/**
 * Maximum WebSocket message size in bytes (1 MiB).
 * Messages from clients exceeding this are rejected and the connection is closed.
 * Exported so tests can verify the limit without hard-coding it.
 */
export const MAX_WS_MESSAGE_BYTES = 1 * 1024 * 1024; // 1 MiB

/**
 * WebSocket idle timeout in milliseconds (5 minutes).
 * A session is considered idle when no message arrives from either side within this period.
 * An idle session is closed with code 1001 (Going Away).
 * Exported so tests can verify the default without hard-coding it.
 */
export const WS_IDLE_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

/**
 * WebSocket heartbeat (ping) interval in milliseconds (30 seconds).
 * The gateway pings the client at this interval; if no pong is received within the interval
 * the session is considered dead and is terminated.
 * Exported so tests can verify the default without hard-coding it.
 */
export const WS_HEARTBEAT_INTERVAL_MS = 30 * 1000; // 30 seconds

export interface GatewayConfig {
  jwtSecret: string;
  backendUrl: string;
  allowedOrigins: string[];
  logger?: boolean;
  metrics?: GatewayMetrics;
  /** Override the WebSocket idle timeout (ms). Default: WS_IDLE_TIMEOUT_MS. */
  wsIdleTimeoutMs?: number;
  /** Override the WebSocket heartbeat interval (ms). Default: WS_HEARTBEAT_INTERVAL_MS. */
  wsHeartbeatIntervalMs?: number;
  /** Override the maximum WebSocket message size (bytes). Default: MAX_WS_MESSAGE_BYTES. */
  wsMaxMessageBytes?: number;
  /** Max accepted JSON/body payload size. Default: DEFAULT_REQUEST_BODY_LIMIT_BYTES. */
  requestBodyLimitBytes?: number;
  /** Rate-limit window in milliseconds. Default: DEFAULT_RATE_LIMIT_WINDOW_MS. */
  rateLimitWindowMs?: number;
  /** Maximum requests per window per key. Default: DEFAULT_RATE_LIMIT_MAX_REQUESTS. */
  rateLimitMaxRequests?: number;
  /** Backend timeout in milliseconds. Default: DEFAULT_BACKEND_TIMEOUT_MS. */
  backendTimeoutMs?: number;
  /** Retry attempts for transient backend failures. Default: DEFAULT_BACKEND_RETRY_ATTEMPTS. */
  backendRetryAttempts?: number;
  /** Initial retry backoff in milliseconds. Default: DEFAULT_BACKEND_RETRY_INITIAL_BACKOFF_MS. */
  backendRetryInitialBackoffMs?: number;
  /** Maximum retry backoff in milliseconds. Default: DEFAULT_BACKEND_RETRY_MAX_BACKOFF_MS. */
  backendRetryMaxBackoffMs?: number;
  /** Number of failures before opening breaker. Default: DEFAULT_BREAKER_FAILURE_THRESHOLD. */
  backendBreakerFailureThreshold?: number;
  /** Breaker open duration in milliseconds. Default: DEFAULT_BREAKER_OPEN_MS. */
  backendBreakerOpenMs?: number;
}

export async function buildApp(config: GatewayConfig): Promise<FastifyInstance> {
  const fastify = Fastify({
    logger: config.logger ?? false,
    bodyLimit: config.requestBodyLimitBytes ?? DEFAULT_REQUEST_BODY_LIMIT_BYTES,
  });
  const metrics = config.metrics ?? new GatewayMetrics();
  const wsIdleTimeoutMs = config.wsIdleTimeoutMs ?? WS_IDLE_TIMEOUT_MS;
  const wsHeartbeatIntervalMs = config.wsHeartbeatIntervalMs ?? WS_HEARTBEAT_INTERVAL_MS;
  const wsMaxMessageBytes = config.wsMaxMessageBytes ?? MAX_WS_MESSAGE_BYTES;
  const rateLimitWindowMs = config.rateLimitWindowMs ?? DEFAULT_RATE_LIMIT_WINDOW_MS;
  const rateLimitMaxRequests = config.rateLimitMaxRequests ?? DEFAULT_RATE_LIMIT_MAX_REQUESTS;
  const backendTimeoutMs = config.backendTimeoutMs ?? DEFAULT_BACKEND_TIMEOUT_MS;
  const backendRetryAttempts = config.backendRetryAttempts ?? DEFAULT_BACKEND_RETRY_ATTEMPTS;
  const backendRetryInitialBackoffMs =
    config.backendRetryInitialBackoffMs ?? DEFAULT_BACKEND_RETRY_INITIAL_BACKOFF_MS;
  const backendRetryMaxBackoffMs =
    config.backendRetryMaxBackoffMs ?? DEFAULT_BACKEND_RETRY_MAX_BACKOFF_MS;
  const backendBreakerFailureThreshold =
    config.backendBreakerFailureThreshold ?? DEFAULT_BREAKER_FAILURE_THRESHOLD;
  const backendBreakerOpenMs = config.backendBreakerOpenMs ?? DEFAULT_BREAKER_OPEN_MS;

  const requestCounters = new Map<string, { count: number; resetAtMs: number }>();
  const breakerState: { failures: number; openUntilMs: number } = { failures: 0, openUntilMs: 0 };

  function rateLimitKey(request: FastifyRequest): string {
    const tenantId = extractHeaderTenantId(request.headers['x-tenant-id']);
    if (tenantId) {
      return `tenant:${tenantId}`;
    }
    return `ip:${request.ip}`;
  }

  function isRateLimited(request: FastifyRequest, nowMs: number): boolean {
    const key = rateLimitKey(request);
    const current = requestCounters.get(key);
    if (!current || nowMs > current.resetAtMs) {
      requestCounters.set(key, { count: 1, resetAtMs: nowMs + rateLimitWindowMs });
      return false;
    }
    current.count += 1;
    requestCounters.set(key, current);
    return current.count > rateLimitMaxRequests;
  }

  function isBreakerOpen(nowMs: number): boolean {
    return nowMs < breakerState.openUntilMs;
  }

  function markBackendSuccess(): void {
    breakerState.failures = 0;
  }

  function markBackendFailure(nowMs: number): void {
    breakerState.failures += 1;
    if (breakerState.failures >= backendBreakerFailureThreshold) {
      breakerState.openUntilMs = nowMs + backendBreakerOpenMs;
      breakerState.failures = 0;
    }
  }

  async function fetchWithResilience(
    url: string,
    init: RequestInit,
    correlationId: string,
  ): Promise<Response> {
    const nowMs = Date.now();
    if (isBreakerOpen(nowMs)) {
      throw new Error('BACKEND_CIRCUIT_OPEN');
    }

    let attempt = 0;
    let backoffMs = backendRetryInitialBackoffMs;
    let lastErr: unknown = null;

    while (attempt < backendRetryAttempts) {
      attempt += 1;
      const abortController = new AbortController();
      const timeout = setTimeout(() => abortController.abort(), backendTimeoutMs);

      try {
        const response = await fetch(url, {
          ...init,
          signal: abortController.signal,
        });
        clearTimeout(timeout);

        if (shouldRetryStatus(response.status) && attempt < backendRetryAttempts) {
          await sleep(backoffMs);
          backoffMs = Math.min(backoffMs * 2, backendRetryMaxBackoffMs);
          continue;
        }

        markBackendSuccess();
        return response;
      } catch (err: unknown) {
        clearTimeout(timeout);
        lastErr = err;
        if (attempt < backendRetryAttempts) {
          fastify.log.warn(
            { correlationId, attempt, err },
            'Gateway backend call failed; retrying with backoff',
          );
          await sleep(backoffMs);
          backoffMs = Math.min(backoffMs * 2, backendRetryMaxBackoffMs);
          continue;
        }
      }
    }

    markBackendFailure(Date.now());
    throw lastErr instanceof Error ? lastErr : new Error('BACKEND_UNREACHABLE');
  }

  fastify.addHook('onRequest', async (request, reply) => {
    const correlationId = resolveCorrelationId(request);
    const traceparent = resolveTraceparent(request);
    const startedAtMs = Date.now();
    const traceContext = parseTraceparent(traceparent);

    reply.header(CORRELATION_ID_HEADER, correlationId);
    reply.header(TRACEPARENT_HEADER, traceparent);

    (request as FastifyRequest & { correlationId?: string }).correlationId = correlationId;
    (request as FastifyRequest & { traceparent?: string }).traceparent = traceparent;
    (request as FastifyRequest & { startedAtMs?: number }).startedAtMs = startedAtMs;
    (request as FastifyRequest & { traceId?: string }).traceId = traceContext?.traceId;
    (request as FastifyRequest & { spanId?: string }).spanId = traceContext?.spanId;

    if (isRateLimited(request, startedAtMs)) {
      metrics.recordAuthFailure('rate_limited');
      fastify.log.warn(
        {
          correlationId,
          traceId: traceContext?.traceId,
          spanId: traceContext?.spanId,
          method: request.method,
          path: request.url,
          rateLimitKey: rateLimitKey(request),
        },
        'Gateway rate limit exceeded',
      );
      return reply.status(429).send({
        error: 'Too Many Requests',
        message: 'Rate limit exceeded for gateway',
        correlationId,
      });
    }
  });

  fastify.addHook('onResponse', async (request, reply) => {
    const startedAtMs = (request as FastifyRequest & { startedAtMs?: number }).startedAtMs;
    const correlationId =
      (request as FastifyRequest & { correlationId?: string }).correlationId ??
      resolveCorrelationId(request);
    const traceId = (request as FastifyRequest & { traceId?: string }).traceId;
    const spanId = (request as FastifyRequest & { spanId?: string }).spanId;
    const durationMs = typeof startedAtMs === 'number' ? Date.now() - startedAtMs : undefined;

    fastify.log.info(
      {
        correlationId,
        traceId,
        spanId,
        method: request.method,
        path: request.url,
        statusCode: reply.statusCode,
        durationMs,
      },
      'Gateway request completed',
    );
  });

  await fastify.register(fastifyCors, {
    origin: config.allowedOrigins,
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'X-Tenant-Id', 'X-Correlation-ID'],
    credentials: true,
  });

  await fastify.register(fastifyWebsocket);

  // ── Authentication preHandler ────────────────────────────────────────────────
  async function authenticate(request: FastifyRequest, reply: FastifyReply): Promise<void> {
    const correlationId =
      (request as FastifyRequest & { correlationId?: string }).correlationId ??
      resolveCorrelationId(request);
    const token = extractBearerToken(request.headers.authorization);
    if (!token) {
      metrics.recordAuthFailure('missing_token');
      reply.header(CORRELATION_ID_HEADER, correlationId);
      void reply.status(401).send({ error: 'Unauthorized', message: 'Missing Bearer token', correlationId });
      return;
    }
    try {
      const payload = verifyJwt(token, config.jwtSecret);
      const headerTenantId = extractHeaderTenantId(request.headers['x-tenant-id']);
      const payloadTenantId = extractPayloadTenantId(payload);
      if (headerTenantId && payloadTenantId && headerTenantId !== payloadTenantId) {
        metrics.recordTenantMismatch();
        metrics.recordAuthFailure('tenant_mismatch');
        reply.header(CORRELATION_ID_HEADER, correlationId);
        void reply.status(403).send({
          error: 'Forbidden',
          message: 'Tenant mismatch between X-Tenant-Id header and JWT payload',
          correlationId,
        });
        return;
      }
      (request as FastifyRequest & { user: JwtPayload }).user = payload;
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Invalid token';
      metrics.recordAuthFailure('invalid_token');
      reply.header(CORRELATION_ID_HEADER, correlationId);
      void reply.status(401).send({ error: 'Unauthorized', message: msg, correlationId });
    }
  }

  // ── Health probe (no auth) ───────────────────────────────────────────────────
  fastify.get('/health', async (request, reply) => {
    const correlationId = resolveCorrelationId(request);
    reply.header('x-correlation-id', correlationId);
    return { status: 'ok', timestamp: new Date().toISOString(), correlationId };
  });

  fastify.get('/ready', async (request, reply) => {
    const correlationId =
      (request as FastifyRequest & { correlationId?: string }).correlationId ??
      resolveCorrelationId(request);
    reply.header('x-correlation-id', correlationId);

    let backendRes: Response;
    try {
      backendRes = await fetchWithResilience(
        `${config.backendUrl}/health`,
        {
          method: 'GET',
          headers: { [CORRELATION_ID_HEADER]: correlationId },
        },
        correlationId,
      );
    } catch (err: unknown) {
      fastify.log.error(err, `Backend readiness probe failed at ${config.backendUrl}/health`);
      return reply.status(503).send({
        status: 'not-ready',
        dependency: 'aep-backend',
        message: 'AEP backend unreachable',
        correlationId,
      });
    }

    if (!backendRes.ok) {
      return reply.status(503).send({
        status: 'not-ready',
        dependency: 'aep-backend',
        message: `AEP backend health probe returned ${backendRes.status}`,
        correlationId,
      });
    }

    return {
      status: 'ready',
      dependency: 'aep-backend',
      correlationId,
    };
  });

  // ── HTTP reverse-proxy → AEP Java backend ───────────────────────────────────
  // T-17: Gateway is the sole external edge; backend auth becomes trust-internal-only
  fastify.all('/api/*', { preHandler: [authenticate] }, async (request, reply) => {
    const targetUrl = `${config.backendUrl}${request.url}`;
    const correlationId =
      (request as FastifyRequest & { correlationId?: string }).correlationId ??
      resolveCorrelationId(request);
    const traceparent =
      (request as FastifyRequest & { traceparent?: string }).traceparent ??
      resolveTraceparent(request);

    const proxyHeaders: Record<string, string> = {};
    if (request.headers['content-type']) {
      proxyHeaders['content-type'] = request.headers['content-type'];
    }
    if (request.headers.authorization) {
      proxyHeaders['authorization'] = request.headers.authorization;
    }
    const payloadTenantId = extractPayloadTenantId((request as FastifyRequest & { user: JwtPayload }).user);
    const headerTenantId = extractHeaderTenantId(request.headers['x-tenant-id']);
    const effectiveTenantId = payloadTenantId ?? headerTenantId;
    if (effectiveTenantId) {
      proxyHeaders['x-tenant-id'] = effectiveTenantId;
    }
    // T-17: Mark request as coming from trusted gateway (internal auth)
    proxyHeaders['x-gateway-trusted'] = 'true';
    proxyHeaders['x-gateway-source'] = 'aep-gateway';
    proxyHeaders[CORRELATION_ID_HEADER] = correlationId;
    proxyHeaders[TRACEPARENT_HEADER] = traceparent;

    const method = request.method;
    const hasBody = method !== 'GET' && method !== 'HEAD' && method !== 'DELETE';
    const body = hasBody && request.body != null ? JSON.stringify(request.body) : undefined;

    let backendRes: Response;
    try {
      const startMs = Date.now();
      backendRes = await fetchWithResilience(
        targetUrl,
        { method, headers: proxyHeaders, body },
        correlationId,
      );
      metrics.recordBackendLatency(Date.now() - startMs);
    } catch (err: unknown) {
      fastify.log.error(err, `Backend unreachable at ${targetUrl}`);
      metrics.recordBackendUnreachable();
      metrics.recordHttpProxyRequest(502);
      reply.header(CORRELATION_ID_HEADER, correlationId);
      return reply.status(502).send({ error: 'Bad Gateway', message: 'AEP backend unreachable', correlationId });
    }

    metrics.recordHttpProxyRequest(backendRes.status);
    const ct = backendRes.headers.get('content-type');

    const { body: responseBody, truncated } = await readBodyCapped(backendRes, MAX_PROXY_BODY_BYTES);
    if (truncated) {
      fastify.log.warn({ correlationId, targetUrl }, 'Proxy response exceeded size limit; body was capped');
      reply.header('x-correlation-id', correlationId);
      return reply.status(502).send({
        error: 'Bad Gateway',
        message: 'Backend response too large',
        correlationId,
      });
    }

    reply.status(backendRes.status);
    reply.header('x-correlation-id', correlationId);
    if (ct) reply.header('content-type', ct);
    return reply.send(Buffer.from(responseBody));
  });

  // ── SSE event stream proxy (canonical path: /events/stream) ──────────────────
  fastify.get('/events/stream', async (request, reply) => {
    const correlationId =
      (request as FastifyRequest & { correlationId?: string }).correlationId ??
      resolveCorrelationId(request);
    const traceparent =
      (request as FastifyRequest & { traceparent?: string }).traceparent ??
      resolveTraceparent(request);
    const token = extractBearerToken(request.headers.authorization) ?? (request.query as Record<string, string>)['token'] ?? null;
    if (!token) {
      metrics.recordSseRejected();
      metrics.recordAuthFailure('missing_token');
      reply.header(CORRELATION_ID_HEADER, correlationId);
      return reply.status(401).send({ error: 'Authentication required', correlationId });
    }
    let payload: JwtPayload;
    try {
      payload = verifyJwt(token, config.jwtSecret);
    } catch {
      metrics.recordSseRejected();
      metrics.recordAuthFailure('invalid_token');
      reply.header(CORRELATION_ID_HEADER, correlationId);
      return reply.status(403).send({ error: 'Invalid or expired token', correlationId });
    }

    const query = request.query as Record<string, string>;
    const queryTenantId = typeof query.tenantId === 'string' && query.tenantId.trim().length > 0
      ? query.tenantId.trim()
      : null;
    const jwtTenantId = extractPayloadTenantId(payload);
    if (queryTenantId && jwtTenantId && queryTenantId !== jwtTenantId) {
      metrics.recordSseRejected();
      metrics.recordTenantMismatch();
      reply.header(CORRELATION_ID_HEADER, correlationId);
      return reply.status(403).send({
        error: 'Forbidden',
        message: 'Tenant mismatch between tenantId query parameter and JWT payload',
        correlationId,
      });
    }

    const params = new URLSearchParams();
    const effectiveTenantId = jwtTenantId ?? queryTenantId;
    if (effectiveTenantId) params.set('tenantId', effectiveTenantId);

    const backendUrl = `${config.backendUrl}/events/stream?${params.toString()}`;
    const backendRes = await fetchWithResilience(
      backendUrl,
      {
        method: 'GET',
        headers: {
          authorization: `Bearer ${token}`,
          accept: 'text/event-stream',
          [CORRELATION_ID_HEADER]: correlationId,
          [TRACEPARENT_HEADER]: traceparent,
        },
      },
      correlationId,
    ).catch(() => null);

    if (!backendRes || !backendRes.ok || !backendRes.body) {
      metrics.recordSseRejected();
      metrics.recordBackendUnreachable();
      reply.header(CORRELATION_ID_HEADER, correlationId);
      return reply.status(502).send({ error: 'Bad Gateway', message: 'SSE backend unreachable', correlationId });
    }

    metrics.recordSseAccepted();
    reply.raw.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
      'X-Accel-Buffering': 'no',
      'X-Correlation-ID': correlationId,
    });

    const reader = backendRes.body.getReader();
    const pump = async () => {
      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          reply.raw.write(value);
        }
      } finally {
        reply.raw.end();
      }
    };
    pump();

    request.raw.on('close', () => {
      reader.cancel().catch(() => {});
    });
  });

  // ── WebSocket event-tailing proxy (legacy path: /tail/events) ────────────────
  await fastify.register(async function wsRoutes(scopedFastify) {
    scopedFastify.get('/tail/events', { websocket: true }, (con, req) => {
      const clientSocket = ('socket' in con ? con.socket : con) as WebSocket;
      const correlationId =
        extractCorrelationId(req.headers[CORRELATION_ID_HEADER]) ??
        extractCorrelationId((req.query as Record<string, string>)['correlationId']) ??
        randomUUID();
      const traceparent =
        (typeof req.headers[TRACEPARENT_HEADER] === 'string' &&
          req.headers[TRACEPARENT_HEADER].trim().length > 0
          ? req.headers[TRACEPARENT_HEADER].trim()
          : `00-${randomUUID().replace(/-/g, '')}-${randomUUID().replace(/-/g, '').slice(0, 16)}-01`);
      const queryToken = (req.query as Record<string, string>)['token'];
      const token = extractBearerToken(req.headers.authorization) ?? queryToken ?? null;
      if (!token) {
        metrics.recordWsRejected();
        metrics.recordAuthFailure('missing_token');
        scopedFastify.log.warn({ correlationId }, 'WS /tail/events rejected: missing token (4001)');
        clientSocket.close(4001, 'Authentication required');
        return;
      }
      let payload: JwtPayload;
      try {
        payload = verifyJwt(token, config.jwtSecret);
      } catch {
        metrics.recordWsRejected();
        metrics.recordAuthFailure('invalid_token');
        scopedFastify.log.warn({ correlationId }, 'WS /tail/events rejected: invalid token (4003)');
        clientSocket.close(4003, 'Invalid or expired token');
        return;
      }
      metrics.recordWsAccepted();

      const tenantId = extractPayloadTenantId(payload) ?? extractHeaderTenantId(req.headers['x-tenant-id']);

      const backendWsUrl = config.backendUrl.replace(/^http/, 'ws') + '/api/v1/tail/events';
      const backendHeaders: Record<string, string> = {
        authorization: `Bearer ${token}`,
        [CORRELATION_ID_HEADER]: correlationId,
        [TRACEPARENT_HEADER]: traceparent,
        'x-gateway-trusted': 'true',
        'x-gateway-source': 'aep-gateway',
      };
      if (tenantId) {
        backendHeaders['x-tenant-id'] = tenantId;
      }

      const backendWs = new WebSocket(backendWsUrl, {
        headers: backendHeaders,
        maxPayload: wsMaxMessageBytes,
      });

      // Buffer messages from the client until the backend WS is open.
      // Without this, messages sent before the backend handshake completes are silently dropped.
      const pendingClientMessages: string[] = [];

      // ── Idle timeout ──────────────────────────────────────────────────────────
      // Reset on any message from either side; close with 1001 if no traffic arrives.
      let idleTimer: ReturnType<typeof setTimeout> | null = null;

      function resetIdleTimer(): void {
        if (idleTimer !== null) clearTimeout(idleTimer);
        idleTimer = setTimeout(() => {
          scopedFastify.log.warn({ correlationId }, 'WS /tail/events idle timeout; closing (1001)');
          clientSocket.close(1001, 'Idle timeout');
          if (backendWs.readyState !== WebSocket.CLOSED) {
            backendWs.close(1001, 'Idle timeout');
          }
        }, wsIdleTimeoutMs);
      }

      function clearIdleTimer(): void {
        if (idleTimer !== null) {
          clearTimeout(idleTimer);
          idleTimer = null;
        }
      }

      resetIdleTimer();

      // ── Heartbeat (client) ────────────────────────────────────────────────────
      // Gateway pings the client periodically. If no pong arrives within the next
      // interval the session is considered dead and terminated.
      let clientAlive = true;
      const heartbeatTimer = setInterval(() => {
        if (!clientAlive) {
          scopedFastify.log.warn({ correlationId }, 'WS /tail/events client missed pong; terminating');
          clearIdleTimer();
          clientSocket.terminate();
          if (backendWs.readyState !== WebSocket.CLOSED) {
            backendWs.close(1001, 'Client heartbeat failed');
          }
          return;
        }
        clientAlive = false;
        if (clientSocket.readyState === WebSocket.OPEN) {
          clientSocket.ping();
        }
      }, wsHeartbeatIntervalMs);

      clientSocket.on('pong', () => {
        clientAlive = true;
        // Note: pong (heartbeat response) does NOT reset the idle timer.
        // Idle timeout tracks application-level DATA messages, not control frames.
      });

      // ── Cleanup ───────────────────────────────────────────────────────────────
      function cleanup(): void {
        clearIdleTimer();
        clearInterval(heartbeatTimer);
      }

      backendWs.on('open', () => {
        for (const msg of pendingClientMessages) {
          if (backendWs.readyState === WebSocket.OPEN) {
            backendWs.send(msg);
          }
        }
        pendingClientMessages.length = 0;
      });
      backendWs.on('message', (data) => {
        resetIdleTimer();
        if (clientSocket.readyState === WebSocket.OPEN) {
          clientSocket.send(data.toString());
        }
      });
      backendWs.on('error', (err) => {
        cleanup();
        metrics.recordBackendUnreachable();
        scopedFastify.log.error({ err, correlationId }, 'WS /tail/events backend error; closing client (1011)');
        clientSocket.close(1011, 'Backend connection failed');
      });
      backendWs.on('close', () => {
        cleanup();
        if (clientSocket.readyState === WebSocket.OPEN) {
          scopedFastify.log.info({ correlationId }, 'WS /tail/events backend closed; closing client (1000)');
          clientSocket.close(1000, 'Backend closed connection');
        }
      });

      clientSocket.on('message', (msg) => {
        resetIdleTimer();
        // ── Backpressure: enforce max message size ─────────────────────────────
        const msgStr = msg.toString();
        if (Buffer.byteLength(msgStr, 'utf8') > wsMaxMessageBytes) {
          scopedFastify.log.warn({ correlationId, size: Buffer.byteLength(msgStr, 'utf8') },
            'WS /tail/events: client message exceeds max size; closing (1009)');
          cleanup();
          clientSocket.close(1009, 'Message too large');
          if (backendWs.readyState !== WebSocket.CLOSED) {
            backendWs.close(1009, 'Client message too large');
          }
          return;
        }
        if (backendWs.readyState === WebSocket.OPEN) {
          backendWs.send(msgStr);
        } else {
          pendingClientMessages.push(msgStr);
        }
      });
      clientSocket.on('close', () => {
        cleanup();
        metrics.recordWsClosed();
        if (backendWs.readyState !== WebSocket.CLOSED) {
          backendWs.close();
        }
      });
    });
  });

  // ── Metrics endpoint (no auth — internal/ops use only) ───────────────────────
  fastify.get('/metrics', async (_request, reply) => {
    return reply.status(200).send(metrics.snapshot());
  });

  return fastify;
}
