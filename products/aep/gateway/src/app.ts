import Fastify, { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import fastifyWebsocket from '@fastify/websocket';
import fastifyCors from '@fastify/cors';
import WebSocket from 'ws';
import { verifyJwt, extractBearerToken } from './jwt.js';
import type { JwtPayload } from './jwt.js';

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
    if (request.headers['x-tenant-id']) {
      proxyHeaders['x-tenant-id'] = request.headers['x-tenant-id'] as string;
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

  // ── WebSocket event-tailing proxy ────────────────────────────────────────────
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
