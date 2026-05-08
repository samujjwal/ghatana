/**
 * Behavioral observability contract tests for FlashIt gateway.
 *
 * Exercises real production modules — Logger, Prometheus metrics plugin,
 * registerTracingMiddleware, and token redaction — to prove that the
 * observability stack actually runs, not just that the tokens exist in source.
 *
 * Anti-theater rule (Section 29 / 35.3): every assertion invokes the real
 * production module.  No object-literal assertions, no dummy stubs.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import Fastify from 'fastify';
import type { FastifyInstance } from 'fastify';
import { Logger, registerLoggerPlugin } from '../lib/logger.js';

// ---------------------------------------------------------------------------
// Logger behavioral tests
// ---------------------------------------------------------------------------

describe('Logger — structured logging observability contract', () => {
  let app: FastifyInstance;
  const capturedLogs: string[] = [];
  let originalLog: typeof console.log;

  beforeEach(async () => {
    capturedLogs.length = 0;
    originalLog = console.log;
    console.log = (...args: unknown[]) => {
      capturedLogs.push(args.join(' '));
    };

    app = Fastify({ logger: false });
    await registerLoggerPlugin(app as Parameters<typeof registerLoggerPlugin>[0]);
    app.get('/ping', (req, reply) => {
      (req as unknown as { logger: Logger }).logger.info('ping handled', { route: '/ping' });
      return reply.send({ ok: true });
    });
    await app.ready();
  });

  afterEach(async () => {
    console.log = originalLog;
    await app.close();
  });

  it('decorates request with a Logger instance', async () => {
    let requestLogger: unknown;
    app.addHook('onRequest', (req, _reply, done) => {
      requestLogger = (req as unknown as { logger: Logger }).logger;
      done();
    });
    await app.inject({ method: 'GET', url: '/ping' });
    expect(requestLogger).toBeInstanceOf(Logger);
  });

  it('emits a structured log entry from the real Logger.info()', async () => {
    await app.inject({
      method: 'GET',
      url: '/ping',
      headers: { 'x-correlation-id': 'test-corr-001' },
    });
    expect(capturedLogs.some((l) => l.includes('ping handled') || l.includes('/ping'))).toBe(true);
  });

  it('adds X-Correlation-ID response header via onSend hook', async () => {
    const response = await app.inject({
      method: 'GET',
      url: '/ping',
      headers: { 'x-correlation-id': 'corr-hdr-test' },
    });
    // The logger plugin sets the correlation-id header in its onSend hook
    const corrId = response.headers['x-correlation-id'];
    expect(typeof corrId).toBe('string');
    expect((corrId as string).length).toBeGreaterThan(0);
  });
});

// ---------------------------------------------------------------------------
// Logger.logBusinessEvent behavioral test
// ---------------------------------------------------------------------------

describe('Logger.logBusinessEvent — structured business event emission', () => {
  it('emits a log entry containing the event name', () => {
    const captured: string[] = [];
    const orig = console.log;
    console.log = (...args: unknown[]) => { captured.push(args.join(' ')); };

    try {
      const logger = new Logger({ correlationId: 'biz-test-1', userId: 'user-001' });
      logger.logBusinessEvent('MOMENT_CREATED', { momentId: 'moment-abc' });
      expect(captured.some((l) => l.includes('MOMENT_CREATED'))).toBe(true);
    } finally {
      console.log = orig;
    }
  });

  it('emits a log entry in JSON format in production mode', () => {
    const captured: string[] = [];
    const orig = console.log;
    const origEnv = process.env.NODE_ENV;
    console.log = (...args: unknown[]) => { captured.push(args.join(' ')); };
    process.env.NODE_ENV = 'production';

    try {
      const logger = new Logger({ correlationId: 'prod-test-2', userId: 'user-002' });
      logger.info('audit event recorded');
      expect(captured.length).toBeGreaterThan(0);
      // Production mode outputs JSON
      const lastLog = captured[captured.length - 1]!;
      expect(() => JSON.parse(lastLog)).not.toThrow();
      const parsed = JSON.parse(lastLog) as Record<string, unknown>;
      expect(parsed).toHaveProperty('correlationId', 'prod-test-2');
    } finally {
      console.log = orig;
      process.env.NODE_ENV = origEnv;
    }
  });
});

// ---------------------------------------------------------------------------
// Prometheus metrics plugin behavioral test
// ---------------------------------------------------------------------------

describe('Prometheus metrics plugin — /metrics endpoint', () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    // Dynamically import to avoid top-level module side effects
    const { default: metricsPlugin } = await import('../plugins/prometheus.js');
    app = Fastify({ logger: false });
    await app.register(metricsPlugin);
    await app.ready();
  });

  afterEach(async () => {
    await app.close();
  });

  it('exposes a /metrics endpoint that returns Prometheus text format', async () => {
    const response = await app.inject({ method: 'GET', url: '/metrics' });
    expect(response.statusCode).toBe(200);
    expect(response.headers['content-type']).toMatch(/text\/plain/);
    // Standard prom-client default metrics always include process_cpu_seconds_total
    expect(response.body).toMatch(/^# HELP|^# TYPE|flashit_http_requests_total/m);
  });

  it('registers flashit_http_requests_total counter', async () => {
    const response = await app.inject({ method: 'GET', url: '/metrics' });
    expect(response.body).toContain('flashit_http_requests_total');
  });

  it('registers flashit_http_request_duration_seconds histogram', async () => {
    const response = await app.inject({ method: 'GET', url: '/metrics' });
    expect(response.body).toContain('flashit_http_request_duration_seconds');
  });
});

// ---------------------------------------------------------------------------
// Token redaction behavioral test
// ---------------------------------------------------------------------------

describe('Token redaction — session tokens must not appear in plain text', () => {
  it('redacts accessToken and refreshToken in session responses', async () => {
    const { getUserSessions } = await import('../lib/session.js');
    const { prisma } = await import('../lib/prisma.js');

    vi.mocked(prisma.session.findMany).mockResolvedValueOnce([
      {
        id: 'sess-1',
        userId: 'user-redact-1',
        accessToken: 'secret-access-token-abc',
        refreshToken: 'secret-refresh-token-xyz',
        userAgent: 'test-agent',
        ipAddress: '127.0.0.1',
        isActive: true,
        expiresAt: new Date(Date.now() + 86400000),
        createdAt: new Date(),
        updatedAt: new Date(),
        lastUsedAt: new Date(),
        deviceFingerprint: null,
        sessionData: null,
      } as never,
    ]);

    const sessions = await getUserSessions('user-redact-1');
    expect(sessions).toHaveLength(1);
    expect(sessions[0]!.accessToken).toBe('[REDACTED]');
    expect(sessions[0]!.refreshToken).toBe('[REDACTED]');
    // The real token must not appear in the session response
    expect(sessions[0]!.accessToken).not.toContain('secret-access-token-abc');
    expect(sessions[0]!.refreshToken).not.toContain('secret-refresh-token-xyz');
  });
});

// ---------------------------------------------------------------------------
// Tracing middleware behavioral test
// ---------------------------------------------------------------------------

describe('Tracing middleware — enriches OTEL spans with FlashIt context', () => {
  it('registers the tracing middleware plugin on a Fastify instance', async () => {
    const { registerTracingMiddleware } = await import('../middleware/tracing.js');
    const tracingApp = Fastify({ logger: false });
    // Must not throw during registration
    await expect(
      tracingApp.register(registerTracingMiddleware)
    ).resolves.toBeUndefined();
    await tracingApp.close();
  });
});
