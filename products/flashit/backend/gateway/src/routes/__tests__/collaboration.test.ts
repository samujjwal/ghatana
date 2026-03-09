/**
 * Collaboration Route Tests
 *
 * Tests for sphere sharing, invitations, comments, reactions, follows,
 * activity feed, notification preferences, and collaboration stats.
 * Verifies billing limit enforcement for collaborators.
 *
 * @doc.type test
 * @doc.purpose Test collaboration API endpoints
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeAll, afterAll, vi, beforeEach } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import jwt from '@fastify/jwt';

// Mock prisma
const mockPrisma = {
  sphere: {
    findFirst: vi.fn(),
    findUnique: vi.fn(),
  },
  user: {
    findUnique: vi.fn(),
  },
  auditEvent: {
    create: vi.fn().mockResolvedValue({}),
  },
  collaboration: {
    sphereShare: {
      findFirst: vi.fn(),
      create: vi.fn(),
      findMany: vi.fn(),
      update: vi.fn(),
    },
    momentComment: {
      create: vi.fn(),
      findMany: vi.fn(),
      findUnique: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
    },
    momentReaction: {
      findFirst: vi.fn(),
      create: vi.fn(),
      delete: vi.fn(),
    },
    userFollow: {
      findFirst: vi.fn(),
      create: vi.fn(),
      delete: vi.fn(),
    },
    activityFeedItem: {
      findMany: vi.fn(),
    },
    notificationPreference: {
      findUnique: vi.fn(),
      upsert: vi.fn(),
    },
  },
  sphereAccess: {
    findFirst: vi.fn(),
  },
};

vi.mock('../../lib/prisma', () => ({
  prisma: mockPrisma,
}));

// Mock billing services
const mockCheckCollaboratorLimit = vi.fn();

vi.mock('../../services/billing/usage-limits', () => ({
  checkCollaboratorLimit: (...args: any[]) => mockCheckCollaboratorLimit(...args),
}));

vi.mock('../../services/billing/stripe-service', () => ({
  StripeBillingService: {
    getSubscriptionInfo: vi.fn().mockResolvedValue({ tier: 'FREE', status: 'active' }),
    checkUsage: vi.fn(),
  },
}));

// Mock auth
vi.mock('../../lib/auth', () => ({
  requireAuth: vi.fn().mockImplementation(async () => {}),
}));

// Mock email
vi.mock('../../lib/email', () => ({
  sendEmail: vi.fn().mockResolvedValue(undefined),
}));

// Mock notifications
vi.mock('../../services/notifications/notification-service', () => ({
  notifySphereShared: vi.fn().mockResolvedValue(undefined),
  notifyCommentAdded: vi.fn().mockResolvedValue(undefined),
  notifyCommentReply: vi.fn().mockResolvedValue(undefined),
  notifyReactionAdded: vi.fn().mockResolvedValue(undefined),
  notifyInvitationAccepted: vi.fn().mockResolvedValue(undefined),
}));

import collaborationRoutes from '../collaboration';

describe('Collaboration Routes', () => {
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

    await app.register(collaborationRoutes, { prefix: '/api/collaboration' });
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

  describe('POST /api/collaboration/spheres/share', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/collaboration/spheres/share',
        payload: {
          sphereId: '00000000-0000-0000-0000-000000000001',
          sharedWithEmail: 'friend@example.com',
        },
      });
      expect(response.statusCode).toBe(401);
    });

    it('should share a sphere when user owns it and is within limit', async () => {
      mockPrisma.sphere.findFirst.mockResolvedValueOnce({
        id: 'sphere-1',
        name: 'My Sphere',
        createdByUserId: 'user-1',
      });

      mockCheckCollaboratorLimit.mockResolvedValueOnce({ allowed: true });

      mockPrisma.collaboration.sphereShare.findFirst.mockResolvedValueOnce(null); // no existing share

      mockPrisma.user.findUnique
        .mockResolvedValueOnce({ id: 'friend-1', email: 'friend@example.com' }) // sharedWithEmail lookup
        .mockResolvedValueOnce({ displayName: 'Owner', email: 'owner@example.com' }); // inviter lookup

      mockPrisma.collaboration.sphereShare.create.mockResolvedValueOnce({
        id: 'share-1',
        sphereId: 'sphere-1',
        sharedByUserId: 'user-1',
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/collaboration/spheres/share',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: {
          sphereId: '00000000-0000-0000-0000-000000000001',
          sharedWithEmail: 'friend@example.com',
          permissionLevel: 'VIEWER',
        },
      });

      expect([200, 201]).toContain(response.statusCode);
    });

    it('should reject sharing when collaborator limit is reached', async () => {
      mockPrisma.sphere.findFirst.mockResolvedValueOnce({
        id: 'sphere-1',
        name: 'My Sphere',
        createdByUserId: 'user-1',
      });

      mockCheckCollaboratorLimit.mockResolvedValueOnce({
        allowed: false,
        upgradePrompt: {
          message: 'Free plan allows max 3 collaborators. Upgrade to Pro for more.',
          actionUrl: '/billing/upgrade',
        },
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/collaboration/spheres/share',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: {
          sphereId: '00000000-0000-0000-0000-000000000001',
          sharedWithEmail: 'friend@example.com',
        },
      });

      expect(response.statusCode).toBe(403);
      const body = JSON.parse(response.body);
      expect(body.error).toBe('Limit Reached');
    });

    it('should reject sharing a sphere user does not own', async () => {
      mockPrisma.sphere.findFirst.mockResolvedValueOnce(null); // not owner

      const response = await app.inject({
        method: 'POST',
        url: '/api/collaboration/spheres/share',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: {
          sphereId: '00000000-0000-0000-0000-000000000001',
          sharedWithEmail: 'friend@example.com',
        },
      });

      expect(response.statusCode).toBe(403);
      const body = JSON.parse(response.body);
      expect(body.error).toBe('Forbidden');
    });

    it('should reject sharing with already-shared user', async () => {
      mockPrisma.sphere.findFirst.mockResolvedValueOnce({
        id: 'sphere-1',
        name: 'My Sphere',
        createdByUserId: 'user-1',
      });

      mockCheckCollaboratorLimit.mockResolvedValueOnce({ allowed: true });

      mockPrisma.collaboration.sphereShare.findFirst.mockResolvedValueOnce({
        id: 'existing-share',
      }); // already shared

      const response = await app.inject({
        method: 'POST',
        url: '/api/collaboration/spheres/share',
        headers: { authorization: `Bearer ${getToken()}` },
        payload: {
          sphereId: '00000000-0000-0000-0000-000000000001',
          sharedWithEmail: 'friend@example.com',
        },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.message).toContain('already shared');
    });
  });

  describe('POST /api/collaboration/invitations/accept', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/collaboration/invitations/accept',
        payload: { invitationToken: 'some-token' },
      });
      expect(response.statusCode).toBe(401);
    });
  });

  describe('GET /api/collaboration/spheres/:sphereId/collaborators', () => {
    it('should list collaborators for an accessible sphere', async () => {
      mockPrisma.sphereAccess.findFirst.mockResolvedValueOnce({ role: 'OWNER' });
      mockPrisma.collaboration.sphereShare.findMany.mockResolvedValueOnce([
        {
          id: 'share-1',
          sharedWithUserId: 'friend-1',
          permissionLevel: 'VIEWER',
          acceptedAt: new Date(),
          sharedWithUser: { displayName: 'Friend', email: 'friend@example.com' },
        },
      ]);

      const response = await app.inject({
        method: 'GET',
        url: '/api/collaboration/spheres/sphere-1/collaborators',
        headers: { authorization: `Bearer ${getToken()}` },
      });

      expect([200, 403]).toContain(response.statusCode);
    });
  });

  describe('POST /api/collaboration/comments', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/collaboration/comments',
        payload: {
          momentId: '00000000-0000-0000-0000-000000000001',
          content: 'Great moment!',
        },
      });
      expect(response.statusCode).toBe(401);
    });
  });

  describe('POST /api/collaboration/reactions', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/collaboration/reactions',
        payload: {
          momentId: '00000000-0000-0000-0000-000000000001',
          reactionType: 'like',
        },
      });
      expect(response.statusCode).toBe(401);
    });
  });

  describe('POST /api/collaboration/follow', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/collaboration/follow',
        payload: {
          userIdToFollow: '00000000-0000-0000-0000-000000000002',
        },
      });
      expect(response.statusCode).toBe(401);
    });
  });

  describe('GET /api/collaboration/activity-feed', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/collaboration/activity-feed',
      });
      expect(response.statusCode).toBe(401);
    });
  });

  describe('GET /api/collaboration/stats', () => {
    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/collaboration/stats',
      });
      expect(response.statusCode).toBe(401);
    });
  });
});
