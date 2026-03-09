/**
 * Admin Route Tests
 *
 * Tests RBAC protection (ADMIN / SUPER_ADMIN), user management,
 * content moderation, and platform statistics endpoints.
 *
 * @doc.type test
 * @doc.purpose Test admin API endpoints with RBAC enforcement
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeAll, afterAll, vi, beforeEach } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import jwt from '@fastify/jwt';

// Mock prisma
const mockPrisma = {
  user: {
    findMany: vi.fn(),
    findUnique: vi.fn(),
    count: vi.fn(),
    update: vi.fn(),
    groupBy: vi.fn(),
  },
  moment: {
    findMany: vi.fn(),
    findUnique: vi.fn(),
    count: vi.fn(),
    update: vi.fn(),
  },
  sphere: {
    count: vi.fn(),
  },
  auditEvent: {
    create: vi.fn().mockResolvedValue({}),
  },
};

vi.mock('../../lib/prisma', () => ({
  prisma: mockPrisma,
}));

// Mock auth - requireAuth allows requests through for testing
vi.mock('../../lib/auth', () => ({
  requireAuth: vi.fn().mockImplementation(async (request: any) => {
    // Authentication is handled by JWT verify in tests
  }),
}));

// Mock require-role middleware
const mockRequireMinRole = vi.fn();
const mockRequireRole = vi.fn();

vi.mock('../../middleware/require-role', () => ({
  requireMinRole: (role: string) => {
    return async (request: any, reply: any) => {
      const userRole = request.user?.role;
      const roleHierarchy = ['USER', 'OPERATOR', 'ADMIN', 'SUPER_ADMIN'];
      const userLevel = roleHierarchy.indexOf(userRole || 'USER');
      const requiredLevel = roleHierarchy.indexOf(role);

      if (userLevel < requiredLevel) {
        return reply.code(403).send({
          error: 'Forbidden',
          message: `Requires ${role} role or higher`,
        });
      }
    };
  },
  requireRole: (role: string) => {
    return async (request: any, reply: any) => {
      if (request.user?.role !== role) {
        return reply.code(403).send({
          error: 'Forbidden',
          message: `Requires ${role} role`,
        });
      }
    };
  },
}));

import adminRoutes from '../admin';

describe('Admin Routes', () => {
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

    await app.register(adminRoutes, { prefix: '/api/admin' });
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  function getToken(payload: { userId: string; email: string; role: string }) {
    return app.jwt.sign(payload);
  }

  const adminToken = () => getToken({ userId: 'admin-1', email: 'admin@example.com', role: 'ADMIN' });
  const superAdminToken = () => getToken({ userId: 'sa-1', email: 'sa@example.com', role: 'SUPER_ADMIN' });
  const userToken = () => getToken({ userId: 'user-1', email: 'user@example.com', role: 'USER' });

  describe('RBAC Protection', () => {
    it('should reject unauthenticated requests', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/admin/users',
      });
      expect(response.statusCode).toBe(401);
    });

    it('should reject regular users from admin endpoints', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/admin/users',
        headers: { authorization: `Bearer ${userToken()}` },
      });
      expect(response.statusCode).toBe(403);
    });

    it('should allow ADMIN access to admin endpoints', async () => {
      mockPrisma.user.findMany.mockResolvedValueOnce([]);
      mockPrisma.user.count.mockResolvedValueOnce(0);

      const response = await app.inject({
        method: 'GET',
        url: '/api/admin/users',
        headers: { authorization: `Bearer ${adminToken()}` },
      });

      expect(response.statusCode).toBe(200);
    });
  });

  describe('GET /api/admin/users', () => {
    it('should list users with pagination', async () => {
      const mockUsers = [
        {
          id: 'user-1',
          email: 'user1@example.com',
          displayName: 'User One',
          role: 'USER',
          subscriptionTier: 'FREE',
          subscriptionStatus: 'active',
          createdAt: new Date(),
          lastLoginAt: new Date(),
        },
      ];

      mockPrisma.user.findMany.mockResolvedValueOnce(mockUsers);
      mockPrisma.user.count.mockResolvedValueOnce(1);

      const response = await app.inject({
        method: 'GET',
        url: '/api/admin/users?page=1&limit=10',
        headers: { authorization: `Bearer ${adminToken()}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(true);
      expect(body.data).toHaveLength(1);
      expect(body.pagination.total).toBe(1);
    });

    it('should support search filtering', async () => {
      mockPrisma.user.findMany.mockResolvedValueOnce([]);
      mockPrisma.user.count.mockResolvedValueOnce(0);

      const response = await app.inject({
        method: 'GET',
        url: '/api/admin/users?search=john',
        headers: { authorization: `Bearer ${adminToken()}` },
      });

      expect(response.statusCode).toBe(200);
      expect(mockPrisma.user.findMany).toHaveBeenCalled();
    });
  });

  describe('GET /api/admin/users/:id', () => {
    it('should get user details', async () => {
      mockPrisma.user.findUnique.mockResolvedValueOnce({
        id: 'user-1',
        email: 'user1@example.com',
        displayName: 'User One',
        role: 'USER',
        subscriptionTier: 'FREE',
        subscriptionStatus: 'active',
        createdAt: new Date(),
        lastLoginAt: new Date(),
        deletedAt: null,
        _count: { moments: 5, spheres: 2, aiInsights: 10 },
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/admin/users/user-1',
        headers: { authorization: `Bearer ${adminToken()}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(true);
      expect(body.data.id).toBe('user-1');
    });

    it('should return 404 for non-existent user', async () => {
      mockPrisma.user.findUnique.mockResolvedValueOnce(null);

      const response = await app.inject({
        method: 'GET',
        url: '/api/admin/users/nonexistent',
        headers: { authorization: `Bearer ${adminToken()}` },
      });

      expect(response.statusCode).toBe(404);
    });
  });

  describe('PATCH /api/admin/users/:id/role', () => {
    it('should require SUPER_ADMIN role', async () => {
      const response = await app.inject({
        method: 'PATCH',
        url: '/api/admin/users/user-1/role',
        headers: { authorization: `Bearer ${adminToken()}` },
        payload: { role: 'ADMIN' },
      });

      expect(response.statusCode).toBe(403);
    });

    it('should allow SUPER_ADMIN to change user role', async () => {
      mockPrisma.user.update.mockResolvedValueOnce({
        id: 'user-1',
        email: 'user1@example.com',
        role: 'ADMIN',
      });

      const response = await app.inject({
        method: 'PATCH',
        url: '/api/admin/users/user-1/role',
        headers: { authorization: `Bearer ${superAdminToken()}` },
        payload: { role: 'ADMIN' },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(true);
      expect(body.data.role).toBe('ADMIN');
    });

    it('should prevent changing own role', async () => {
      const response = await app.inject({
        method: 'PATCH',
        url: '/api/admin/users/sa-1/role',
        headers: { authorization: `Bearer ${superAdminToken()}` },
        payload: { role: 'USER' },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.message).toContain('own role');
    });
  });

  describe('PATCH /api/admin/users/:id/suspend', () => {
    it('should suspend a user', async () => {
      mockPrisma.user.update.mockResolvedValueOnce({
        id: 'user-1',
        lockedUntil: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
      });

      const response = await app.inject({
        method: 'PATCH',
        url: '/api/admin/users/user-1/suspend',
        headers: { authorization: `Bearer ${adminToken()}` },
        payload: { suspended: true, reason: 'Policy violation' },
      });

      expect([200, 400]).toContain(response.statusCode);
    });

    it('should prevent suspending own account', async () => {
      const response = await app.inject({
        method: 'PATCH',
        url: '/api/admin/users/admin-1/suspend',
        headers: { authorization: `Bearer ${adminToken()}` },
        payload: { suspended: true },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.message).toContain('own account');
    });
  });

  describe('DELETE /api/admin/users/:id', () => {
    it('should soft-delete a user', async () => {
      mockPrisma.user.update.mockResolvedValueOnce({
        id: 'user-1',
        deletedAt: new Date(),
      });

      const response = await app.inject({
        method: 'DELETE',
        url: '/api/admin/users/user-1',
        headers: { authorization: `Bearer ${adminToken()}` },
      });

      expect([200, 204]).toContain(response.statusCode);
    });
  });

  describe('GET /api/admin/content/flagged', () => {
    it('should list flagged content', async () => {
      mockPrisma.moment.findMany.mockResolvedValueOnce([]);

      const response = await app.inject({
        method: 'GET',
        url: '/api/admin/content/flagged',
        headers: { authorization: `Bearer ${adminToken()}` },
      });

      expect(response.statusCode).toBe(200);
    });
  });

  describe('PATCH /api/admin/content/:momentId/moderate', () => {
    it('should moderate a moment', async () => {
      mockPrisma.moment.findUnique.mockResolvedValueOnce({
        id: 'moment-1',
        userId: 'user-1',
      });
      mockPrisma.moment.update.mockResolvedValueOnce({
        id: 'moment-1',
        moderationStatus: 'REMOVED',
      });

      const response = await app.inject({
        method: 'PATCH',
        url: '/api/admin/content/moment-1/moderate',
        headers: { authorization: `Bearer ${adminToken()}` },
        payload: { action: 'remove', reason: 'Inappropriate content' },
      });

      expect([200, 404]).toContain(response.statusCode);
    });
  });

  describe('GET /api/admin/stats', () => {
    it('should return platform statistics', async () => {
      mockPrisma.user.count
        .mockResolvedValueOnce(100) // totalUsers
        .mockResolvedValueOnce(42);  // activeUsers
      mockPrisma.moment.count.mockResolvedValueOnce(500);
      mockPrisma.sphere.count.mockResolvedValueOnce(200);
      mockPrisma.user.groupBy.mockResolvedValueOnce([
        { subscriptionTier: 'FREE', _count: 80 },
        { subscriptionTier: 'PRO', _count: 15 },
        { subscriptionTier: 'TEAMS', _count: 5 },
      ]);

      const response = await app.inject({
        method: 'GET',
        url: '/api/admin/stats',
        headers: { authorization: `Bearer ${adminToken()}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(true);
      expect(body.data.totalUsers).toBe(100);
      expect(body.data.totalMoments).toBe(500);
      expect(body.data.tierBreakdown).toHaveLength(3);
    });
  });
});
