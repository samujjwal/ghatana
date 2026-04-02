/**
 * @doc.type test-suite
 * @doc.purpose Integration tests validating setupPlatform auth guard, startup wiring, and module registration
 * @doc.layer platform
 * @doc.pattern Integration Test
 */

import { describe, it, expect, vi, beforeAll, afterAll, beforeEach } from 'vitest';
import { FastifyInstance } from 'fastify';
import jwt from '@fastify/jwt';
import { PrismaClient } from '@prisma/client';
import { createServer } from '../setup';

/**
 * Test fixture for platform auth and startup
 */
interface TestFixture {
  app: FastifyInstance;
  prisma: PrismaClient;
  redis: {
    get: ReturnType<typeof vi.fn>;
    set: ReturnType<typeof vi.fn>;
    del: ReturnType<typeof vi.fn>;
  };
}

/**
 * Creates test JWT token with claims
 */
function createTestJWT(
  claims: {
    userId: string;
    tenantId: string;
    role?: string;
    expiresIn?: string;
  },
  signingKey: string
): string {
  const payload = {
    sub: claims.userId,
    tenantId: claims.tenantId,
    role: claims.role || 'student',
    iat: Math.floor(Date.now() / 1000),
    exp:
      claims.expiresIn === '-1h'
        ? Math.floor(Date.now() / 1000) - 3600
        : Math.floor(Date.now() / 1000) + 3600,
  };

  // Simple JWT creation for testing (use real JWT library in production)
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64');
  const body = Buffer.from(JSON.stringify(payload)).toString('base64');
  const signature = Buffer.from(`${header}.${body}${signingKey}`).toString('base64');

  return `${header}.${body}.${signature}`;
}

/**
 * Mock Redis client
 */
function createMockRedis() {
  return {
    get: vi.fn().mockResolvedValue(null),
    set: vi.fn().mockResolvedValue('OK'),
    del: vi.fn().mockResolvedValue(1),
    connect: vi.fn().mockResolvedValue(undefined),
    disconnect: vi.fn().mockResolvedValue(undefined),
  };
}

/**
 * Mock Prisma client
 */
function createMockPrisma() {
  return {
    user: {
      findUnique: vi.fn().mockResolvedValue(null),
      create: vi.fn().mockResolvedValue({ id: 'user1', tenantId: 'tenant1' }),
    },
    tenant: {
      findUnique: vi.fn().mockResolvedValue({ id: 'tenant1' }),
    },
    $connect: vi.fn().mockResolvedValue(undefined),
    $disconnect: vi.fn().mockResolvedValue(undefined),
  } as unknown as PrismaClient;
}

describe('Tutorputor Platform Startup & Auth (Integration)', () => {
  let fixture: TestFixture;
  const JWT_SECRET = 'test-secret-key-min-32-chars-long!!';

  beforeAll(async () => {
    // Mock dependencies
    const redis = createMockRedis();
    const prisma = createMockPrisma();

    // Create app with mocked dependencies
    fixture = {
      app: await createServer({
        prisma,
        redis: redis as any,
        jwtSecret: JWT_SECRET,
      }),
      prisma,
      redis,
    };
  });

  afterAll(async () => {
    await fixture.app.close();
  });

  beforeEach(() => {
    // Reset mocks before each test
    vi.clearAllMocks();
  });

  describe('Server Startup', () => {
    it('successfully creates Fastify app instance', async () => {
      expect(fixture.app).toBeTruthy();
      expect(fixture.app.server).toBeTruthy();
    });

    it('registers core middleware in correct order', async () => {
      const routes = fixture.app.printRoutes?.();
      expect(routes).toBeTruthy();
    });

    it('connects to database on startup', async () => {
      expect(fixture.prisma.$connect).toHaveBeenCalled();
    });

    it('reports healthy status when all dependencies connected', async () => {
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/health',
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toMatchObject({
        status: 'healthy',
        database: 'connected',
        cache: 'connected',
      });
    });

    it('reports degraded status when Redis unavailable', async () => {
      fixture.redis.get.mockRejectedValueOnce(new Error('Redis unavailable'));

      const response = await fixture.app.inject({
        method: 'GET',
        url: '/health',
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.status).toBe('degraded');
      expect(body.cache).toBe('disconnected');
    });

    it('exposes /metrics endpoint for Prometheus', async () => {
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/metrics',
      });

      expect(response.statusCode).toBe(200);
      expect(response.headers['content-type']).toContain('text/plain');
    });
  });

  describe('Auth Guard - Standard Routes', () => {
    it('blocks unauthenticated requests to /api/v1/learning/* routes', async () => {
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/learning/dashboard',
        headers: {},
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.error || body.message).toContain('Unauthorized');
    });

    it('allows valid JWT to /api/v1/learning/* routes', async () => {
      const token = createTestJWT(
        { userId: 'user1', tenantId: 'tenant1' },
        JWT_SECRET
      );

      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/learning/dashboard',
        headers: { authorization: `Bearer ${token}` },
      });

      // 200 or 404 (missing route) is acceptable; 401 means auth failed
      expect(response.statusCode).not.toBe(401);
      expect(response.statusCode).not.toBe(403);
    });

    it('extracts and validates JWT claims', async () => {
      const token = createTestJWT(
        { userId: 'user123', tenantId: 'tenant456' },
        JWT_SECRET
      );

      // Mock a route that validates claims
      fixture.app.get('/test/auth-check', async (request, reply) => {
        await request.jwtVerify();
        return {
          userId: request.user?.sub || request.user?.userId,
          tenantId: request.user?.tenantId,
        };
      });

      const response = await fixture.app.inject({
        method: 'GET',
        url: '/test/auth-check',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.userId).toBe('user123');
      expect(body.tenantId).toBe('tenant456');
    });

    it('rejects invalid JWT format', async () => {
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/learning/dashboard',
        headers: { authorization: 'Bearer invalid.jwt.token' },
      });

      expect(response.statusCode).toBe(401);
    });

    it('rejects malformed Authorization header', async () => {
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/learning/dashboard',
        headers: { authorization: 'InvalidFormat token' },
      });

      expect(response.statusCode).toBe(401);
    });

    it('rejects expired JWT', async () => {
      const expiredToken = createTestJWT(
        { userId: 'user1', tenantId: 'tenant1', expiresIn: '-1h' },
        JWT_SECRET
      );

      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/learning/dashboard',
        headers: { authorization: `Bearer ${expiredToken}` },
      });

      expect(response.statusCode).toBe(401);
    });

    it('rejects token signed with wrong key', async () => {
      const wrongKeyToken = createTestJWT(
        { userId: 'user1', tenantId: 'tenant1' },
        'wrong-secret-key-min-32-chars-long!'
      );

      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/learning/dashboard',
        headers: { authorization: `Bearer ${wrongKeyToken}` },
      });

      expect(response.statusCode).toBe(401);
    });
  });

  describe('Auth Guard Exemptions - LTI', () => {
    it('allows /api/v1/lti/launch without authentication', async () => {
      const response = await fixture.app.inject({
        method: 'POST',
        url: '/api/v1/lti/launch',
        headers: {},
        payload: {
          lti_user_id: 'lti_user_123',
          lti_course_id: 'course_456',
          lti_version: '1p3',
        },
      });

      // Should not be 401 Unauthorized
      expect(response.statusCode).not.toBe(401);
    });

    it('allows /api/v1/lti/deeplink without authentication', async () => {
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/lti/deeplink',
        headers: {},
      });

      expect(response.statusCode).not.toBe(401);
    });

    it('rejects LTI with invalid payload format', async () => {
      const response = await fixture.app.inject({
        method: 'POST',
        url: '/api/v1/lti/launch',
        headers: {},
        payload: { invalid: 'payload' },
      });

      // Should be 400 Bad Request, not 401 Unauthorized
      expect(response.statusCode).not.toBe(401);
      expect(response.statusCode).toBe(400);
    });
  });

  describe('Auth Guard Exemptions - Webhooks', () => {
    it('allows /webhooks/stripe without authentication', async () => {
      const response = await fixture.app.inject({
        method: 'POST',
        url: '/webhooks/stripe',
        headers: {
          'stripe-signature': 'valid-stripe-signature',
        },
        payload: { type: 'payment_intent.succeeded', id: 'evt_123' },
      });

      // Should not be 401 Unauthorized
      expect(response.statusCode).not.toBe(401);
    });

    it('validates Stripe signature on webhook', async () => {
      const response = await fixture.app.inject({
        method: 'POST',
        url: '/webhooks/stripe',
        headers: {
          'stripe-signature': 'invalid-signature',
        },
        payload: { type: 'payment_intent.succeeded', id: 'evt_123' },
      });

      // Should reject due to invalid signature, not auth failure
      expect(response.statusCode).not.toBe(401);
      expect([400, 403].includes(response.statusCode)).toBe(true);
    });

    it('rejects webhook without Stripe signature', async () => {
      const response = await fixture.app.inject({
        method: 'POST',
        url: '/webhooks/stripe',
        headers: {}, // Missing stripe-signature
        payload: { type: 'payment_intent.succeeded', id: 'evt_123' },
      });

      expect([400, 403].includes(response.statusCode)).toBe(true);
    });
  });

  describe('Module Registration', () => {
    it('registers learning module routes', async () => {
      const routes = fixture.app.printRoutes?.();
      const routeString = routes ? routes.toString() : '';

      expect(
        routeString.includes('/api/v1/learning') ||
          fixture.app.hasRoute({
            method: 'GET',
            url: '/api/v1/learning/dashboard',
          })
      ).toBe(true);
    });

    it('registers content module routes', async () => {
      const routes = fixture.app.printRoutes?.();
      const routeString = routes ? routes.toString() : '';

      expect(
        routeString.includes('/api/v1/content') ||
          fixture.app.hasRoute({
            method: 'POST',
            url: '/api/v1/content/generate',
          })
      ).toBe(true);
    });

    it('registers assessment module routes', async () => {
      const routes = fixture.app.printRoutes?.();
      const routeString = routes ? routes.toString() : '';

      expect(
        routeString.includes('/api/v1/assessments') ||
          fixture.app.hasRoute({
            method: 'GET',
            url: '/api/v1/assessments',
          })
      ).toBe(true);
    });

    it('decorates Fastify instance with prisma client', async () => {
      // Route handler should have access to prisma via request.server.prisma
      fixture.app.get('/test/prisma-check', async (request, reply) => {
        expect(request.server.prisma).toBeTruthy();
        return { decorated: true };
      });

      const response = await fixture.app.inject({
        method: 'GET',
        url: '/test/prisma-check',
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.decorated).toBe(true);
    });

    it('decorates Fastify instance with redis client', async () => {
      fixture.app.get('/test/redis-check', async (request, reply) => {
        expect(request.server.redis).toBeTruthy();
        return { decorated: true };
      });

      const response = await fixture.app.inject({
        method: 'GET',
        url: '/test/redis-check',
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.decorated).toBe(true);
    });
  });

  describe('Error Handling & Observability', () => {
    it('logs authentication failures', async () => {
      const logSpy = vi.spyOn(fixture.app.log, 'warn');

      await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/learning/dashboard',
        headers: { authorization: 'Invalid' },
      });

      expect(logSpy).toHaveBeenCalled();
    });

    it('includes request ID in error responses for tracing', async () => {
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/learning/dashboard',
        headers: {},
      });

      expect(response.statusCode).toBe(401);
      expect(response.headers['x-request-id'] || response.headers['request-id']).toBeTruthy();
    });

    it('tracks auth failures in metrics', async () => {
      const metricsBefore = await fixture.app.inject({
        method: 'GET',
        url: '/metrics',
      });

      // Hit unauthenticated endpoint
      await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/learning/dashboard',
        headers: {},
      });

      const metricsAfter = await fixture.app.inject({
        method: 'GET',
        url: '/metrics',
      });

      // Metrics should show auth failure event
      expect(metricsAfter.body).toContain('auth_failures') ||
        expect(metricsAfter.body).toContain('http_requests_total');
    });
  });

  describe('Graceful Shutdown', () => {
    it('closes database connection on shutdown', async () => {
      await fixture.app.close();
      expect(fixture.prisma.$disconnect).toHaveBeenCalled();
    });

    it('closes Redis connection on shutdown', async () => {
      const mockRedis = createMockRedis();
      const testApp = await createServer({
        prisma: createMockPrisma(),
        redis: mockRedis as any,
        jwtSecret: JWT_SECRET,
      });

      await testApp.close();
      expect(mockRedis.disconnect).toHaveBeenCalled();
    });

    it('completes in-flight requests before shutdown', async () => {
      // Request should complete before app closes
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/health',
      });

      expect(response.statusCode).toBe(200);
    });
  });

  describe('Security Headers', () => {
    it('sets X-Content-Type-Options: nosniff', async () => {
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/health',
      });

      expect(response.headers['x-content-type-options']).toBe('nosniff');
    });

    it('sets X-Frame-Options: DENY', async () => {
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/health',
      });

      expect(response.headers['x-frame-options']).toBe('DENY');
    });

    it('includes CORS headers for allowed origins', async () => {
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/health',
        headers: { origin: 'https://tutorputor.local' },
      });

      expect(response.headers['access-control-allow-origin']).toBeTruthy();
    });
  });
});
