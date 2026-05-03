import Fastify, { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import fastifyWebsocket from '@fastify/websocket';
import fastifyCors from '@fastify/cors';
import WebSocket from 'ws';
import { randomUUID } from 'node:crypto';
import { verifyJwt, extractBearerToken } from './jwt.js';
import type { JwtPayload } from './jwt.js';

const CORRELATION_ID_HEADER = 'x-correlation-id';

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

function resolveCorrelationId(request: FastifyRequest): string {
  return extractCorrelationId(request.headers[CORRELATION_ID_HEADER]) ?? randomUUID();
}

async function checkBackendReadiness(backendUrl: string, correlationId: string): Promise<Response> {
  return fetch(`${backendUrl}/health`, {
    method: 'GET',
    headers: { [CORRELATION_ID_HEADER]: correlationId },
  });
}

export interface GatewayConfig {
  jwtSecret: string;
  backendUrl: string;
  allowedOrigins: string[];
  logger?: boolean;
}

export async function buildApp(config: GatewayConfig): Promise<FastifyInstance> {
  const fastify = Fastify({ logger: config.logger ?? false });

  await fastify.register(fastifyCors, {
    origin: config.allowedOrigins,
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'X-Tenant-Id', 'X-Correlation-ID'],
    credentials: true,
  });

  await fastify.register(fastifyWebsocket);

  // ── Authentication preHandler ────────────────────────────────────────────────
  async function authenticate(request: FastifyRequest, reply: FastifyReply): Promise<void> {
    const token = extractBearerToken(request.headers.authorization);
    if (!token) {
      void reply.status(401).send({ error: 'Unauthorized', message: 'Missing Bearer token' });
      return;
    }
    try {
      const payload = verifyJwt(token, config.jwtSecret);
      const headerTenantId = extractHeaderTenantId(request.headers['x-tenant-id']);
      const payloadTenantId = extractPayloadTenantId(payload);
      if (headerTenantId && payloadTenantId && headerTenantId !== payloadTenantId) {
        void reply.status(403).send({
          error: 'Forbidden',
          message: 'Tenant mismatch between X-Tenant-Id header and JWT payload',
        });
        return;
      }
      (request as FastifyRequest & { user: JwtPayload }).user = payload;
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Invalid token';
      void reply.status(401).send({ error: 'Unauthorized', message: msg });
    }
  }

  // ── Health probe (no auth) ───────────────────────────────────────────────────
  fastify.get('/health', async (request, reply) => {
    const correlationId = resolveCorrelationId(request);
    reply.header('x-correlation-id', correlationId);
    return { status: 'ok', timestamp: new Date().toISOString(), correlationId };
  });

  fastify.get('/ready', async (request, reply) => {
    const correlationId = resolveCorrelationId(request);
    reply.header('x-correlation-id', correlationId);

    let backendRes: Response;
    try {
      backendRes = await checkBackendReadiness(config.backendUrl, correlationId);
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
    const correlationId = resolveCorrelationId(request);

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

    const method = request.method;
    const hasBody = method !== 'GET' && method !== 'HEAD' && method !== 'DELETE';
    const body = hasBody && request.body != null ? JSON.stringify(request.body) : undefined;

    let backendRes: Response;
    try {
      backendRes = await fetch(targetUrl, { method, headers: proxyHeaders, body });
    } catch (err: unknown) {
      fastify.log.error(err, `Backend unreachable at ${targetUrl}`);
      return reply.status(502).send({ error: 'Bad Gateway', message: 'AEP backend unreachable' });
    }

    reply.status(backendRes.status);
    reply.header('x-correlation-id', correlationId);
    const ct = backendRes.headers.get('content-type');
    if (ct) reply.header('content-type', ct);
    return reply.send(await backendRes.text());
  });

  // ── SSE event stream proxy (canonical path: /events/stream) ──────────────────
  fastify.get('/events/stream', async (request, reply) => {
    const token = extractBearerToken(request.headers.authorization) ?? (request.query as Record<string, string>)['token'] ?? null;
    if (!token) {
      return reply.status(401).send({ error: 'Authentication required' });
    }
    let payload: JwtPayload;
    try {
      payload = verifyJwt(token, config.jwtSecret);
    } catch {
      return reply.status(403).send({ error: 'Invalid or expired token' });
    }

    const query = request.query as Record<string, string>;
    const queryTenantId = typeof query.tenantId === 'string' && query.tenantId.trim().length > 0
      ? query.tenantId.trim()
      : null;
    const jwtTenantId = extractPayloadTenantId(payload);
    if (queryTenantId && jwtTenantId && queryTenantId !== jwtTenantId) {
      return reply.status(403).send({
        error: 'Forbidden',
        message: 'Tenant mismatch between tenantId query parameter and JWT payload',
      });
    }

    const params = new URLSearchParams();
    const effectiveTenantId = jwtTenantId ?? queryTenantId;
    if (effectiveTenantId) params.set('tenantId', effectiveTenantId);
    const correlationId = resolveCorrelationId(request);

    const backendUrl = `${config.backendUrl}/events/stream?${params.toString()}`;
    const backendRes = await fetch(backendUrl, {
      headers: {
        authorization: `Bearer ${token}`,
        accept: 'text/event-stream',
        [CORRELATION_ID_HEADER]: correlationId,
      },
    }).catch(() => null);

    if (!backendRes || !backendRes.ok || !backendRes.body) {
      return reply.status(502).send({ error: 'Bad Gateway', message: 'SSE backend unreachable' });
    }

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
      const queryToken = (req.query as Record<string, string>)['token'];
      const token = extractBearerToken(req.headers.authorization) ?? queryToken ?? null;
      if (!token) {
        clientSocket.close(4001, 'Authentication required');
        return;
      }
      let payload: JwtPayload;
      try {
        payload = verifyJwt(token, config.jwtSecret);
      } catch {
        clientSocket.close(4003, 'Invalid or expired token');
        return;
      }

      const correlationId = resolveCorrelationId(req);
      const tenantId = extractPayloadTenantId(payload) ?? extractHeaderTenantId(req.headers['x-tenant-id']);

      const backendWsUrl = config.backendUrl.replace(/^http/, 'ws') + '/api/v1/tail/events';
      const backendHeaders: Record<string, string> = {
        authorization: `Bearer ${token}`,
        [CORRELATION_ID_HEADER]: correlationId,
        'x-gateway-trusted': 'true',
        'x-gateway-source': 'aep-gateway',
      };
      if (tenantId) {
        backendHeaders['x-tenant-id'] = tenantId;
      }

      const backendWs = new WebSocket(backendWsUrl, { headers: backendHeaders });

      backendWs.on('message', (data) => {
        if (clientSocket.readyState === WebSocket.OPEN) {
          clientSocket.send(data.toString());
        }
      });
      backendWs.on('error', (err) => {
        scopedFastify.log.error(err, 'Backend WebSocket error');
        clientSocket.close(1011, 'Backend connection failed');
      });
      backendWs.on('close', () => {
        if (clientSocket.readyState === WebSocket.OPEN) {
          clientSocket.close(1000, 'Backend closed connection');
        }
      });

      clientSocket.on('message', (msg) => {
        if (backendWs.readyState === WebSocket.OPEN) {
          backendWs.send(msg.toString());
        }
      });
      clientSocket.on('close', () => {
        if (backendWs.readyState !== WebSocket.CLOSED) {
          backendWs.close();
        }
      });
    });
  });

  return fastify;
}
