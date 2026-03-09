/**
 * Notification Route Tests
 *
 * Tests for in-app notification CRUD endpoints.
 * Covers listing, marking read, dismissing, and unread count.
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import jwt from '@fastify/jwt';

// Mock prisma before importing routes
vi.mock('../../lib/prisma', () => {
  const mockNotifications = [
    {
      id: 'notif-1',
      userId: 'user-1',
      type: 'comment_added',
      title: 'New comment',
      body: 'Someone commented on your moment',
      read: false,
      dismissed: false,
      createdAt: new Date('2026-02-20'),
    },
    {
      id: 'notif-2',
      userId: 'user-1',
      type: 'sphere_shared',
      title: 'Sphere shared',
      body: 'A sphere was shared with you',
      read: true,
      dismissed: false,
      createdAt: new Date('2026-02-19'),
    },
    {
      id: 'notif-3',
      userId: 'user-1',
      type: 'insight_generated',
      title: 'New insight',
      body: 'AI detected a pattern',
      read: false,
      dismissed: false,
      createdAt: new Date('2026-02-18'),
    },
  ];

  return {
    prisma: {
      notification: {
        findMany: vi.fn().mockResolvedValue(mockNotifications),
        count: vi.fn().mockResolvedValue(2), // 2 unread
        update: vi.fn().mockImplementation(({ where }) => {
          const notif = mockNotifications.find((n) => n.id === where.id);
          return Promise.resolve(notif ? { ...notif, read: true } : null);
        }),
        updateMany: vi.fn().mockResolvedValue({ count: 2 }),
        delete: vi.fn().mockResolvedValue({ id: 'notif-1' }),
        deleteMany: vi.fn().mockResolvedValue({ count: 1 }),
      },
    },
  };
});

describe('Notification Routes', () => {
  let app: FastifyInstance;
  const userId = 'user-1';

  beforeAll(async () => {
    app = Fastify();
    await app.register(jwt, {
      secret: 'test-secret-key',
    });

    // Auth decorator
    app.decorate('authenticate', async (request: any, reply: any) => {
      try {
        await request.jwtVerify();
      } catch {
        reply.code(401).send({ error: 'Unauthorized' });
      }
    });

    // Dynamic import to get mocked version
    const { default: notificationRoutes } = await import('../notifications');
    await app.register(notificationRoutes, { prefix: '/api/notifications' });
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  function getToken() {
    return app.jwt.sign({ userId, email: 'test@example.com' });
  }

  describe('GET /api/notifications', () => {
    it('should return paginated notifications', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/notifications?limit=10&offset=0',
        headers: { authorization: `Bearer ${getToken()}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty('notifications');
      expect(Array.isArray(body.notifications)).toBe(true);
    });

    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/notifications',
      });

      expect(response.statusCode).toBe(401);
    });
  });

  describe('GET /api/notifications/unread-count', () => {
    it('should return unread count', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/notifications/unread-count',
        headers: { authorization: `Bearer ${getToken()}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty('count');
      expect(typeof body.count).toBe('number');
    });
  });

  describe('PATCH /api/notifications/:id/read', () => {
    it('should mark a notification as read', async () => {
      const response = await app.inject({
        method: 'PATCH',
        url: '/api/notifications/notif-1/read',
        headers: { authorization: `Bearer ${getToken()}` },
      });

      expect(response.statusCode).toBe(200);
    });
  });

  describe('PATCH /api/notifications/read-all', () => {
    it('should mark all notifications as read', async () => {
      const response = await app.inject({
        method: 'PATCH',
        url: '/api/notifications/read-all',
        headers: { authorization: `Bearer ${getToken()}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty('updated');
    });
  });

  describe('DELETE /api/notifications/:id', () => {
    it('should dismiss a notification', async () => {
      const response = await app.inject({
        method: 'DELETE',
        url: '/api/notifications/notif-1',
        headers: { authorization: `Bearer ${getToken()}` },
      });

      expect(response.statusCode).toBe(200);
    });
  });

  describe('DELETE /api/notifications/dismissed', () => {
    it('should bulk delete read notifications', async () => {
      const response = await app.inject({
        method: 'DELETE',
        url: '/api/notifications/dismissed',
        headers: { authorization: `Bearer ${getToken()}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body).toHaveProperty('deleted');
    });
  });
});
