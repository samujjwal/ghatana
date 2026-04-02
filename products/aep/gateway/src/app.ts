import Fastify, { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import fastifyWebsocket from '@fastify/websocket';
import fastifyCors from '@fastify/cors';
import WebSocket from 'ws';
import { verifyJwt, extractBearerToken } from './jwt.js';
import type { JwtPayload } from './jwt.js';

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
    allowedHeaders: ['Content-Type', 'Authorization', 'X-Tenant-Id'],
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
  fastify.get('/health', async () => ({ status: 'ok', timestamp: new Date().toISOString() }));

  // ── HTTP reverse-proxy → AEP Java backend ───────────────────────────────────
  fastify.all('/api/*', { preHandler: [authenticate] }, async (request, reply) => {
    const targetUrl = `${config.backendUrl}${request.url}`;

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

    const backendUrl = `${config.backendUrl}/events/stream?${params.toString()}`;
    const backendRes = await fetch(backendUrl, {
      headers: { authorization: `Bearer ${token}`, accept: 'text/event-stream' },
    }).catch(() => null);

    if (!backendRes || !backendRes.ok || !backendRes.body) {
      return reply.status(502).send({ error: 'Bad Gateway', message: 'SSE backend unreachable' });
    }

    reply.raw.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
      'X-Accel-Buffering': 'no',
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
      const queryToken = (req.query as Record<string, string>)['token'];
      const token = extractBearerToken(req.headers.authorization) ?? queryToken ?? null;
      if (!token) {
        con.socket.close(4001, 'Authentication required');
        return;
      }
      try {
        verifyJwt(token, config.jwtSecret);
      } catch {
        con.socket.close(4003, 'Invalid or expired token');
        return;
      }

      const backendWsUrl = config.backendUrl.replace(/^http/, 'ws') + '/api/v1/tail/events';
      const backendWs = new WebSocket(backendWsUrl, {
        headers: { authorization: `Bearer ${token}` },
      });

      backendWs.on('message', (data) => {
        if (con.socket.readyState === WebSocket.OPEN) {
          con.socket.send(data.toString());
        }
      });
      backendWs.on('error', (err) => {
        scopedFastify.log.error(err, 'Backend WebSocket error');
        con.socket.close(1011, 'Backend connection failed');
      });
      backendWs.on('close', () => {
        if (con.socket.readyState === WebSocket.OPEN) {
          con.socket.close(1000, 'Backend closed connection');
        }
      });

      con.socket.on('message', (msg) => {
        if (backendWs.readyState === WebSocket.OPEN) {
          backendWs.send(msg.toString());
        }
      });
      con.socket.on('close', () => {
        if (backendWs.readyState !== WebSocket.CLOSED) {
          backendWs.close();
        }
      });
    });
  });

  return fastify;
}
