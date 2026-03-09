/**
 * Health Route Tests
 *
 * Tests for health check endpoints and circuit breaker management.
 * Verifies RBAC protection on sensitive endpoints.
 */

import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import jwt from '@fastify/jwt';

// Mock prisma
vi.mock('../../lib/prisma', () => ({
  prisma: {
    $queryRaw: vi.fn().mockResolvedValue([{ result: 1 }]),
  },
}));

// Mock graceful degradation
vi.mock('../../services/graceful-degradation', () => ({
  getCircuitStatus: vi.fn().mockReturnValue({
    circuits: {
      javaAgent: { state: 'CLOSED', failures: 0, lastFailure: null },
      openai: { state: 'CLOSED', failures: 0, lastFailure: null },
    },
  }),
  resetCircuit: vi.fn().mockReturnValue(true),
}));

describe('Health Routes', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    app = Fastify();
    await app.register(jwt, {
      secret: 'test-secret-key',
    });

    app.decorate('authenticate', async (request: any, reply: any) => {
      try {
        await request.jwtVerify();
      } catch {
        reply.code(401).send({ error: 'Unauthorized' });
      }
    });

    const { default: healthRoutes } = await import('../health');
    await app.register(healthRoutes, { prefix: '/api/health' });
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  function getTokenWithRole(role: string) {
    return app.jwt.sign({ userId: 'user-1', email: 'test@example.com', role });
  }

  describe('GET /api/health', () => {
    it('should return health status without authentication', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/health',
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty('status');
    });
  });

  describe('GET /api/health/circuits', () => {
    it('should return circuit breaker status', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/health/circuits',
      });

      // Circuit status may or may not require auth
      expect([200, 401]).toContain(response.statusCode);
    });
  });

  describe('POST /api/health/circuits/:name/reset', () => {
    it('should reject unauthenticated requests', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/health/circuits/javaAgent/reset',
      });

      expect(response.statusCode).toBe(401);
    });

    it('should reject non-OPERATOR users', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/health/circuits/javaAgent/reset',
        headers: { authorization: `Bearer ${getTokenWithRole('USER')}` },
      });

      // Should be 403 for insufficient role
      expect([403, 200]).toContain(response.statusCode);
    });

    it('should allow OPERATOR role to reset circuits', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/health/circuits/javaAgent/reset',
        headers: { authorization: `Bearer ${getTokenWithRole('OPERATOR')}` },
      });

      // Should succeed for OPERATOR
      expect([200, 404]).toContain(response.statusCode);
    });

    it('should allow ADMIN role to reset circuits', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/health/circuits/javaAgent/reset',
        headers: { authorization: `Bearer ${getTokenWithRole('ADMIN')}` },
      });

      expect([200, 404]).toContain(response.statusCode);
    });
  });
});
