/**
 * Memory Expansion Route Tests
 *
 * Tests for memory expansion API: create expansion, get result,
 * list expansions, get specific result, and batch expansion.
 *
 * @doc.type test
 * @doc.purpose Test memory expansion API endpoints
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeAll, afterAll, vi, beforeEach } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import jwt from '@fastify/jwt';

// Mock reflection client
const mockGenerateInsights = vi.fn();
const mockDetectPatterns = vi.fn();
const mockFindConnections = vi.fn();

vi.mock('../../services/java-agents/reflection-client', () => ({
  generateInsights: (...args: any[]) => mockGenerateInsights(...args),
  detectPatterns: (...args: any[]) => mockDetectPatterns(...args),
  findConnections: (...args: any[]) => mockFindConnections(...args),
}));

// Mock prisma
const mockPrisma = {
  auditEvent: {
    create: vi.fn().mockResolvedValue({}),
  },
};

vi.mock('../../lib/prisma', () => ({
  prisma: mockPrisma,
}));

import memoryExpansionRoutes from '../memory-expansion';

describe('Memory Expansion Routes', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    app = Fastify();
    await app.register(jwt, { secret: 'test-secret-key' });

    app.decorate('authenticate', async (request: any, reply: any) => {
      try {
        await request.jwtVerify();
      } catch {
        reply.code(401).send({ error: 'Unauthorized' });
      }
    });

    await app.register(memoryExpansionRoutes);
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  function getToken(userId = 'user-1') {
    return app.jwt.sign({ userId, email: 'test@example.com' });
  }

  describe('POST /api/memory-expansion', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/memory-expansion',
        payload: {
          expansionType: 'summarize',
          sphereId: '00000000-0000-0000-0000-000000000001',
        },
      });
      expect(response.statusCode).toBe(401);
    });

    it('should reject requests with no selection criteria', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/memory-expansion',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: {
          expansionType: 'summarize',
        },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.error).toContain('Must provide');
    });

    it('should create a summarize expansion for a sphere', async () => {
      mockGenerateInsights.mockResolvedValueOnce({
        summary: 'Your sphere contains themes around productivity and learning.',
        insights: ['You reflect frequently on goals', 'Learning patterns detected'],
        themes: ['productivity', 'learning'],
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/memory-expansion',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: {
          expansionType: 'summarize',
          sphereId: '00000000-0000-0000-0000-000000000001',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.message).toBe('Memory expansion completed');
      expect(body.result).toBeDefined();
    });

    it('should create an identify_patterns expansion', async () => {
      mockDetectPatterns.mockResolvedValueOnce({
        patterns: [
          { name: 'Weekly reflection', frequency: 4, description: 'You reflect every week' },
        ],
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/memory-expansion',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: {
          expansionType: 'identify_patterns',
          sphereId: '00000000-0000-0000-0000-000000000001',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.result.patterns).toHaveLength(1);
    });

    it('should create a find_connections expansion', async () => {
      mockFindConnections.mockResolvedValueOnce({
        connections: [
          { sourceId: 'm1', targetId: 'm2', reason: 'Both about learning' },
        ],
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/memory-expansion',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: {
          expansionType: 'find_connections',
          sphereId: '00000000-0000-0000-0000-000000000001',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.result.connections).toHaveLength(1);
    });

    it('should handle time range based expansion', async () => {
      mockGenerateInsights.mockResolvedValueOnce({
        summary: 'Weekly summary',
        insights: [],
        themes: [],
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/memory-expansion',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: {
          expansionType: 'summarize',
          timeRange: {
            startDate: '2026-02-01T00:00:00.000Z',
            endDate: '2026-02-07T00:00:00.000Z',
          },
        },
      });

      expect(response.statusCode).toBe(200);
      // For a 7-day range, timeRangeOption should be 'week'
      expect(mockGenerateInsights).toHaveBeenCalledWith(
        'user-1',
        undefined,
        'week'
      );
    });

    it('should handle service errors gracefully', async () => {
      mockGenerateInsights.mockRejectedValueOnce(new Error('Service unavailable'));

      const response = await app.inject({
        method: 'POST',
        url: '/api/memory-expansion',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: {
          expansionType: 'summarize',
          sphereId: '00000000-0000-0000-0000-000000000001',
        },
      });

      expect(response.statusCode).toBe(500);
      const body = JSON.parse(response.body);
      expect(body.error).toContain('Failed');
    });
  });

  describe('GET /api/memory-expansion', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/memory-expansion',
      });
      expect(response.statusCode).toBe(401);
    });
  });

  describe('GET /api/memory-expansion/:jobId', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/memory-expansion/job-1',
      });
      expect(response.statusCode).toBe(401);
    });
  });

  describe('GET /api/memory-expansion/result/:expansionId', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/memory-expansion/result/expansion-1',
      });
      expect(response.statusCode).toBe(401);
    });
  });

  describe('POST /api/memory-expansion/batch', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/memory-expansion/batch',
        payload: {
          requests: [
            { expansionType: 'summarize', sphereId: '00000000-0000-0000-0000-000000000001' },
          ],
        },
      });
      expect(response.statusCode).toBe(401);
    });
  });
});
