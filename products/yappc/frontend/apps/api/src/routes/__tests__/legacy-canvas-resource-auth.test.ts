/**
 * @doc.type test-suite
 * @doc.purpose Verify resource-auth guard on legacy canvas GET endpoint
 * @doc.layer application
 * @doc.pattern Security Test
 *
 * Security regression for P1-A: GET /api/canvas/:projectId/:canvasId?
 * must be protected by requireCanvasReadable() so that cross-user canvas
 * reads are rejected with 403.
 */

import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { FastifyInstance } from 'fastify';

// ---------------------------------------------------------------------------
// We test the Fastify route plugin in isolation by constructing a minimal
// Fastify instance, registering only the canvas routes and a stub auth
// middleware, then injecting requests.
// ---------------------------------------------------------------------------

import fastify from 'fastify';
import { canvasRoutes } from '../../routes/canvas';

// ---------------------------------------------------------------------------
// Minimal prisma mock — the route reads canvasDocument but we only need
// to verify the preHandler fires before the handler.
// ---------------------------------------------------------------------------

vi.mock('../../database/client', () => ({
  getPrismaClient: () => ({
    canvasDocument: {
      findFirst: vi.fn().mockResolvedValue(null),
    },
    $disconnect: vi.fn(),
  }),
}));

// ---------------------------------------------------------------------------
// Stub requireCanvasReadable to record whether it was called and to return
// 403 when the requesting user is not the canvas owner.
// ---------------------------------------------------------------------------

const guardInvocations: Array<{ userId: string; projectId: string }> = [];

vi.mock('../../middleware/resource-auth.middleware', () => ({
  requireCanvasReadable: () =>
    // Returns a Fastify preHandler hook
    async (request: { params: { projectId: string }; user?: { userId: string } }, reply: { status: (s: number) => { send: (b: unknown) => void } }) => {
      const userId = request.user?.userId ?? '';
      const { projectId } = request.params;
      guardInvocations.push({ userId, projectId });

      // Simulate: project owned by 'owner-user', so anyone else is forbidden
      if (userId !== 'owner-user') {
        reply.status(403).send({ error: 'Forbidden' });
      }
    },
  requireWorkspaceMember: () => async () => undefined,
  requireProjectReadable: () => async () => undefined,
  requirePermission: () => async () => undefined,
}));

// ---------------------------------------------------------------------------
// Test setup
// ---------------------------------------------------------------------------

describe('Legacy canvas GET resource-auth guard (P1-A)', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    app = fastify({ logger: false });

    // Populate request.user before route handlers run
    app.addHook('onRequest', (request, _reply, done) => {
      // Default: no authenticated user
      done();
    });

    await app.register(canvasRoutes, { prefix: '/api' });
    await app.ready();
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(() => {
    guardInvocations.length = 0;
  });

  it('invokes requireCanvasReadable() preHandler on GET /api/canvas/:projectId', async () => {
    const response = await app.inject({
      method: 'GET',
      url: '/api/canvas/proj-abc',
      headers: {},
    });

    // Guard ran (may return 403 since no user, but guard must be invoked)
    expect(guardInvocations).toHaveLength(1);
    expect(guardInvocations[0]!.projectId).toBe('proj-abc');
    // Non-owner (no user) should be forbidden
    expect(response.statusCode).toBe(403);
  });

  it('allows the canvas owner to read their canvas', async () => {
    // Inject request.user via a hook for this specific test
    const ownerApp = fastify({ logger: false });
    ownerApp.addHook('onRequest', (request, _reply, done) => {
      (request as unknown as { user: { userId: string; role: string } }).user = {
        userId: 'owner-user',
        role: 'user',
      };
      done();
    });
    await ownerApp.register(canvasRoutes, { prefix: '/api' });
    await ownerApp.ready();

    const response = await ownerApp.inject({
      method: 'GET',
      url: '/api/canvas/proj-abc',
    });

    // Guard ran — owner is allowed; handler returns 200 or 404 (no canvas in DB), not 403
    expect(response.statusCode).not.toBe(403);
    await ownerApp.close();
  });

  it('denies a different user from reading another user canvas', async () => {
    const attackerApp = fastify({ logger: false });
    attackerApp.addHook('onRequest', (request, _reply, done) => {
      (request as unknown as { user: { userId: string; role: string } }).user = {
        userId: 'attacker-user',
        role: 'user',
      };
      done();
    });
    await attackerApp.register(canvasRoutes, { prefix: '/api' });
    await attackerApp.ready();

    const response = await attackerApp.inject({
      method: 'GET',
      url: '/api/canvas/proj-abc',
    });

    // Guard ran and denied attacker
    expect(response.statusCode).toBe(403);
    await attackerApp.close();
  });
});
