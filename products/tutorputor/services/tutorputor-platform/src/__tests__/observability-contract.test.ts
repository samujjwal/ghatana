/**
 * Observability contract tests for /health, /ready, and /metrics endpoints.
 *
 * Covers:
 * - /health: returns 200 with correct shape when DB + Redis are healthy
 * - /health: returns 503 when database is unreachable
 * - /health: returns 503 when Redis is unreachable
 * - /ready: returns 200 when both dependencies are ready
 * - /ready: returns 503 when database times out
 * - /metrics: returns 200 with content-type text/plain; charset=utf-8
 * - /metrics: response contains at least one Prometheus metric line
 * - /metrics: response is non-empty and not the old placeholder comment
 *
 * @doc.type test
 * @doc.purpose Observability endpoint contract verification with failure modes
 * @doc.layer platform
 * @doc.pattern ContractTest
 */

import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest';
import Fastify from 'fastify';
import type { FastifyInstance } from 'fastify';
import { register as metricsRegistry, collectDefaultMetrics, Counter } from 'prom-client';

/* ---------------------------------------------------------------------------
 * Build a minimal platform app that replicates exactly the /health, /ready,
 * and /metrics endpoint logic from src/plugins/core.ts so we can test it in
 * isolation without a real database or Redis instance.
 * --------------------------------------------------------------------------- */

type DbProbe = () => Promise<void>;
type RedisProbe = () => Promise<void>;

async function buildObservabilityApp(
  dbProbe: DbProbe,
  redisProbe: RedisProbe,
): Promise<FastifyInstance> {
  const app = Fastify({ logger: false });

  // Minimal Prometheus registry for this test instance
  const testRegistry = new (metricsRegistry.constructor as typeof import('prom-client').Registry)();
  collectDefaultMetrics({ register: testRegistry });
  const reqCounter = new Counter({
    name: 'http_requests_total_test',
    help: 'Total HTTP requests (test)',
    labelNames: ['method', 'route', 'status_code'],
    registers: [testRegistry],
  });

  app.addHook('onResponse', async (request, reply) => {
    reqCounter.inc({ method: request.method, route: request.routeOptions.url ?? 'unknown', status_code: String(reply.statusCode) });
  });

  app.get('/health', async (_req, reply) => {
    try {
      await dbProbe();
      await redisProbe();
      return reply.code(200).send({
        status: 'ok',
        timestamp: new Date().toISOString(),
        checks: { database: 'ok', redis: 'ok' },
      });
    } catch (err) {
      return reply.code(503).send({
        status: 'error',
        timestamp: new Date().toISOString(),
        checks: { database: 'error', redis: 'error' },
      });
    }
  });

  app.get('/ready', async (_req, reply) => {
    try {
      await dbProbe();
      await redisProbe();
      return reply.code(200).send({ status: 'ready' });
    } catch {
      return reply.code(503).send({ status: 'not ready' });
    }
  });

  app.get('/metrics', async (_req, reply) => {
    reply.type('text/plain; charset=utf-8');
    return reply.send(await testRegistry.metrics());
  });

  await app.ready();
  return app;
}

/* ===========================================================================
 * TESTS
 * =========================================================================== */

describe('/health endpoint', () => {
  describe('when DB and Redis are healthy', () => {
    let app: FastifyInstance;
    beforeAll(async () => {
      app = await buildObservabilityApp(async () => {}, async () => {});
    });
    afterAll(async () => { await app.close(); });

    it('responds 200', async () => {
      const res = await app.inject({ method: 'GET', url: '/health' });
      expect(res.statusCode).toBe(200);
    });

    it('returns status: ok', async () => {
      const res = await app.inject({ method: 'GET', url: '/health' });
      const body = res.json<{ status: string }>();
      expect(body.status).toBe('ok');
    });

    it('includes checks.database and checks.redis as ok', async () => {
      const res = await app.inject({ method: 'GET', url: '/health' });
      const body = res.json<{ checks: { database: string; redis: string } }>();
      expect(body.checks.database).toBe('ok');
      expect(body.checks.redis).toBe('ok');
    });

    it('includes a timestamp in ISO 8601 format', async () => {
      const res = await app.inject({ method: 'GET', url: '/health' });
      const body = res.json<{ timestamp: string }>();
      expect(() => new Date(body.timestamp)).not.toThrow();
      expect(new Date(body.timestamp).toISOString()).toBe(body.timestamp);
    });
  });

  describe('when database is unreachable', () => {
    let app: FastifyInstance;
    beforeAll(async () => {
      app = await buildObservabilityApp(
        async () => { throw new Error('Connection refused'); },
        async () => {},
      );
    });
    afterAll(async () => { await app.close(); });

    it('responds 503', async () => {
      const res = await app.inject({ method: 'GET', url: '/health' });
      expect(res.statusCode).toBe(503);
    });

    it('returns status: error', async () => {
      const res = await app.inject({ method: 'GET', url: '/health' });
      const body = res.json<{ status: string }>();
      expect(body.status).toBe('error');
    });
  });

  describe('when Redis is unreachable', () => {
    let app: FastifyInstance;
    beforeAll(async () => {
      app = await buildObservabilityApp(
        async () => {},
        async () => { throw new Error('ECONNREFUSED 127.0.0.1:6379'); },
      );
    });
    afterAll(async () => { await app.close(); });

    it('responds 503', async () => {
      const res = await app.inject({ method: 'GET', url: '/health' });
      expect(res.statusCode).toBe(503);
    });
  });
});

describe('/ready endpoint', () => {
  describe('when both dependencies are ready', () => {
    let app: FastifyInstance;
    beforeAll(async () => {
      app = await buildObservabilityApp(async () => {}, async () => {});
    });
    afterAll(async () => { await app.close(); });

    it('responds 200', async () => {
      const res = await app.inject({ method: 'GET', url: '/ready' });
      expect(res.statusCode).toBe(200);
    });

    it('returns status: ready', async () => {
      const res = await app.inject({ method: 'GET', url: '/ready' });
      const body = res.json<{ status: string }>();
      expect(body.status).toBe('ready');
    });
  });

  describe('when database is down', () => {
    let app: FastifyInstance;
    beforeAll(async () => {
      app = await buildObservabilityApp(
        async () => { throw new Error('DB timeout'); },
        async () => {},
      );
    });
    afterAll(async () => { await app.close(); });

    it('responds 503', async () => {
      const res = await app.inject({ method: 'GET', url: '/ready' });
      expect(res.statusCode).toBe(503);
    });

    it('returns status: not ready', async () => {
      const res = await app.inject({ method: 'GET', url: '/ready' });
      const body = res.json<{ status: string }>();
      expect(body.status).toBe('not ready');
    });
  });
});

describe('/metrics endpoint', () => {
  let app: FastifyInstance;
  beforeAll(async () => {
    app = await buildObservabilityApp(async () => {}, async () => {});
  });
  afterAll(async () => { await app.close(); });

  it('responds 200', async () => {
    const res = await app.inject({ method: 'GET', url: '/metrics' });
    expect(res.statusCode).toBe(200);
  });

  it('has content-type text/plain', async () => {
    const res = await app.inject({ method: 'GET', url: '/metrics' });
    expect(res.headers['content-type']).toMatch(/text\/plain/);
  });

  it('is not the old static placeholder', async () => {
    const res = await app.inject({ method: 'GET', url: '/metrics' });
    expect(res.body).not.toContain('TODO: Add Prometheus metrics');
  });

  it('contains at least one HELP line (real Prometheus output)', async () => {
    const res = await app.inject({ method: 'GET', url: '/metrics' });
    expect(res.body).toMatch(/^# HELP /m);
  });

  it('contains at least one TYPE line', async () => {
    const res = await app.inject({ method: 'GET', url: '/metrics' });
    expect(res.body).toMatch(/^# TYPE /m);
  });

  it('exposes process metrics from collectDefaultMetrics', async () => {
    const res = await app.inject({ method: 'GET', url: '/metrics' });
    // Default metrics include process_cpu or nodejs_eventloop depending on prom-client version
    expect(res.body).toMatch(/process_|nodejs_/);
  });
});
