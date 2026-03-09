/**
 * API Key Route Tests
 *
 * Tests for API key CRUD endpoints.
 * Covers creation, listing, updating, revoking, tier enforcement, and key limits.
 */

import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import jwt from '@fastify/jwt';

// Mock prisma
vi.mock('../../lib/prisma', () => {
  const mockKeys = [
    {
      id: 'key-1',
      userId: 'user-1',
      name: 'My API Key',
      keyHash: 'abc123hash',
      keyPrefix: 'fli_abc',
      scopes: ['read'],
      lastUsedAt: null,
      expiresAt: null,
      revokedAt: null,
      createdAt: new Date('2026-03-01'),
    },
    {
      id: 'key-2',
      userId: 'user-1',
      name: 'Revoked Key',
      keyHash: 'def456hash',
      keyPrefix: 'fli_def',
      scopes: ['read', 'write'],
      lastUsedAt: new Date('2026-03-10'),
      expiresAt: null,
      revokedAt: new Date('2026-03-15'),
      createdAt: new Date('2026-02-15'),
    },
  ];

  return {
    prisma: {
      user: {
        findUnique: vi.fn().mockImplementation(({ where }) => {
          if (where.id === 'user-1') {
            return Promise.resolve({ subscriptionTier: 'teams' });
          }
          if (where.id === 'user-free') {
            return Promise.resolve({ subscriptionTier: 'free' });
          }
          return Promise.resolve(null);
        }),
      },
      apiKey: {
        count: vi.fn().mockResolvedValue(1),
        create: vi.fn().mockImplementation(({ data, select }) => {
          return Promise.resolve({
            id: 'key-new',
            name: data.name,
            keyPrefix: data.keyPrefix,
            scopes: data.scopes,
            expiresAt: data.expiresAt,
            createdAt: new Date(),
          });
        }),
        findMany: vi.fn().mockResolvedValue(mockKeys),
        findFirst: vi.fn().mockImplementation(({ where }) => {
          const key = mockKeys.find(
            (k) => k.id === where.id && k.userId === where.userId
          );
          return Promise.resolve(key || null);
        }),
        update: vi.fn().mockImplementation(({ where, data, select }) => {
          const key = mockKeys.find((k) => k.id === where.id);
          if (!key) return Promise.resolve(null);
          return Promise.resolve({ ...key, ...data });
        }),
      },
    },
  };
});

// Mock api-key-auth generateApiKey
vi.mock('../../middleware/api-key-auth', () => ({
  generateApiKey: vi.fn().mockReturnValue({
    plaintext: 'fli_test_key_abc123def456',
    hash: 'hashed_value_here',
    prefix: 'fli_test_',
  }),
}));

describe('API Key Routes', () => {
  let app: FastifyInstance;
  const userId = 'user-1';

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

    const { default: apiKeyRoutes } = await import('../api-keys');
    await app.register(apiKeyRoutes, { prefix: '/api/api-keys' });
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  function getToken(uid: string = userId) {
    return app.jwt.sign({ userId: uid, email: 'test@example.com' });
  }

  // --- CREATE ---
  describe('POST /api/api-keys', () => {
    it('should create a new API key for Teams tier user', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/api-keys',
        headers: { Authorization: `Bearer ${getToken()}` },
        payload: { name: 'Test Key', scopes: ['read'] },
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body.key).toBeDefined();
      expect(body.key).toMatch(/^fli_/);
      expect(body.warning).toContain('will not be shown again');
      expect(body.name).toBe('Test Key');
    });

    it('should reject non-Teams tier users', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/api-keys',
        headers: { Authorization: `Bearer ${getToken('user-free')}` },
        payload: { name: 'Test Key', scopes: ['read'] },
      });

      expect(response.statusCode).toBe(403);
      const body = JSON.parse(response.body);
      expect(body.error).toContain('Teams tier');
    });

    it('should reject invalid scopes', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/api-keys',
        headers: { Authorization: `Bearer ${getToken()}` },
        payload: { name: 'Test Key', scopes: ['invalid_scope'] },
      });

      expect(response.statusCode).toBe(400);
    });

    it('should reject missing name', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/api-keys',
        headers: { Authorization: `Bearer ${getToken()}` },
        payload: { scopes: ['read'] },
      });

      expect(response.statusCode).toBe(400);
    });

    it('should reject unauthenticated requests', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/api-keys',
        payload: { name: 'Test Key', scopes: ['read'] },
      });

      expect(response.statusCode).toBe(401);
    });
  });

  // --- LIST ---
  describe('GET /api/api-keys', () => {
    it('should list all API keys for the user', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/api-keys',
        headers: { Authorization: `Bearer ${getToken()}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.keys).toBeInstanceOf(Array);
      expect(body.total).toBe(2);
      expect(body.active).toBe(1); // 1 active, 1 revoked
    });

    it('should not expose key hashes', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/api-keys',
        headers: { Authorization: `Bearer ${getToken()}` },
      });

      const body = JSON.parse(response.body);
      for (const key of body.keys) {
        expect(key.keyHash).toBeUndefined();
      }
    });
  });

  // --- GET SINGLE ---
  describe('GET /api/api-keys/:id', () => {
    it('should return a single API key', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/api-keys/key-1',
        headers: { Authorization: `Bearer ${getToken()}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.name).toBe('My API Key');
    });

    it('should return 404 for non-existent key', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/api-keys/key-nonexistent',
        headers: { Authorization: `Bearer ${getToken()}` },
      });

      expect(response.statusCode).toBe(404);
    });
  });

  // --- UPDATE ---
  describe('PATCH /api/api-keys/:id', () => {
    it('should update key name', async () => {
      const response = await app.inject({
        method: 'PATCH',
        url: '/api/api-keys/key-1',
        headers: { Authorization: `Bearer ${getToken()}` },
        payload: { name: 'Updated Name' },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.name).toBe('Updated Name');
    });

    it('should reject update of revoked key', async () => {
      const response = await app.inject({
        method: 'PATCH',
        url: '/api/api-keys/key-2',
        headers: { Authorization: `Bearer ${getToken()}` },
        payload: { name: 'Should Fail' },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.error).toContain('revoked');
    });

    it('should return 404 for non-existent key', async () => {
      const response = await app.inject({
        method: 'PATCH',
        url: '/api/api-keys/key-nonexistent',
        headers: { Authorization: `Bearer ${getToken()}` },
        payload: { name: 'Test' },
      });

      expect(response.statusCode).toBe(404);
    });
  });

  // --- REVOKE ---
  describe('DELETE /api/api-keys/:id', () => {
    it('should revoke an active API key', async () => {
      const response = await app.inject({
        method: 'DELETE',
        url: '/api/api-keys/key-1',
        headers: { Authorization: `Bearer ${getToken()}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(true);
    });

    it('should reject revoking already-revoked key', async () => {
      const response = await app.inject({
        method: 'DELETE',
        url: '/api/api-keys/key-2',
        headers: { Authorization: `Bearer ${getToken()}` },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.error).toContain('already revoked');
    });

    it('should return 404 for non-existent key', async () => {
      const response = await app.inject({
        method: 'DELETE',
        url: '/api/api-keys/key-nonexistent',
        headers: { Authorization: `Bearer ${getToken()}` },
      });

      expect(response.statusCode).toBe(404);
    });
  });
});
