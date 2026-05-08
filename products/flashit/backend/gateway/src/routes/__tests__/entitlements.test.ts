/**
 * Behavioral integration tests for the FlashIt /route-entitlements endpoint.
 *
 * These tests register the REAL production route handler on a Fastify test
 * server.  Only Prisma is mocked — the entitlement logic and response shape
 * are exercised from the actual production module.
 *
 * Anti-theater rule (Section 29): every assertion exercises a real code path
 * through the production module under test.
 */

import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest';
import Fastify from 'fastify';
import type { FastifyInstance } from 'fastify';

// Mock Prisma at module level before importing the route
vi.mock('../../lib/prisma.js', () => ({
  prisma: {
    user: {
      findUnique: vi.fn(),
    },
  },
}));

import entitlementRoutes from '../entitlements.js';
import { prisma } from '../../lib/prisma.js';

const mockFindUnique = vi.mocked(prisma.user.findUnique);

function buildTestServer(role: string, subscriptionTier: string): FastifyInstance {
  const app = Fastify();

  // Replicate the authenticate decorator used in production (no network call)
  app.decorate('authenticate', async function (request: Parameters<typeof app.authenticate>[0]) {
    (request as unknown as Record<string, unknown>).user = {
      userId: 'test-user-id',
      email: 'test@flashit.io',
    };
  });

  // Add @fastify/sensible so httpErrors is available
  void app.register(import('@fastify/sensible'));
  void app.register(entitlementRoutes);

  return app;
}

describe('/route-entitlements – behavioral contract', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    mockFindUnique.mockResolvedValue({
      role: 'USER',
      subscriptionTier: 'FREE',
      deletedAt: null,
    } as never);

    app = buildTestServer('USER', 'FREE');
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  it('returns the required top-level fields from the real handler', async () => {
    const response = await app.inject({
      method: 'GET',
      url: '/route-entitlements',
    });

    expect(response.statusCode).toBe(200);
    const body = response.json<Record<string, unknown>>();

    // Verify canonical response shape — these are the fields the product-shell
    // contract requires (ProductRouteEntitlement)
    expect(body).toHaveProperty('product', 'flashit');
    expect(body).toHaveProperty('principalId');
    expect(body).toHaveProperty('role');
    expect(body).toHaveProperty('tier');
    expect(body).toHaveProperty('routes');
    expect(body).toHaveProperty('actions');
    expect(body).toHaveProperty('cards');
  });

  it('does NOT expose server-only role metadata in routes array items', async () => {
    const response = await app.inject({
      method: 'GET',
      url: '/route-entitlements',
    });

    const body = response.json<{ routes: Array<Record<string, unknown>> }>();
    const hasMinimumRoleLeakage = body.routes.some(
      (r) => Object.prototype.hasOwnProperty.call(r, 'minimumRole'),
    );

    // minimumRole is an internal server-side field and must not be returned
    expect(hasMinimumRoleLeakage).toBe(false);
  });

  it('returns 410 Gone when the user account is deleted', async () => {
    mockFindUnique.mockResolvedValueOnce({
      role: 'USER',
      subscriptionTier: 'FREE',
      deletedAt: new Date(),
    } as never);

    const response = await app.inject({
      method: 'GET',
      url: '/route-entitlements',
    });

    expect(response.statusCode).toBe(410);
  });

  it('returns 410 Gone when the user record does not exist', async () => {
    mockFindUnique.mockResolvedValueOnce(null);

    const response = await app.inject({
      method: 'GET',
      url: '/route-entitlements',
    });

    expect(response.statusCode).toBe(410);
  });

  it('role is derived from server DB record, never from caller-supplied input', async () => {
    // Even if a malicious caller adds X-Role header, the server reads role from DB
    const response = await app.inject({
      method: 'GET',
      url: '/route-entitlements',
      headers: {
        'x-role': 'admin',  // should be ignored
      },
    });

    const body = response.json<{ role: string }>();
    // Role must reflect the DB value (mapped to FlashIt role space), not the header
    expect(['member', 'premium', 'admin']).toContain(body.role);
    expect(body.role).not.toBe('admin'); // 'USER' DB role maps to 'member', not 'admin'
  });
});
