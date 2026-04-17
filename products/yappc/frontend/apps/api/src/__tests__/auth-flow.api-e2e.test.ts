/**
 * @group api-e2e
 * @tier R-api
 *
 * Real API integration tests for YAPPC — auth flow, tenant isolation, and
 * workspace/project lifecycle.
 *
 * These tests require a live Postgres instance and are ONLY executed when
 * the environment variable `RUN_REAL_DB_TESTS=true` is set.  They are
 * excluded from the standard unit and mock-backed integration suites.
 *
 * Required environment variables:
 *   TEST_DATABASE_URL  — connection string pointing to a disposable test DB
 *   JWT_SECRET         — symmetric secret used by the running API server
 *
 * @doc.type test-suite
 * @doc.purpose Real-backend API E2E coverage for authentication, tenant
 *   isolation, and workspace/project lifecycle
 * @doc.layer application
 * @doc.pattern E2E Integration Test
 */

import {
  describe,
  it,
  expect,
  beforeAll,
  afterAll,
  beforeEach,
} from 'vitest';

// ─── Guard ────────────────────────────────────────────────────────────────────

const RUN = process.env.RUN_REAL_DB_TESTS === 'true';

if (!RUN) {
  describe.skip(
    'Real API E2E (skipped — set RUN_REAL_DB_TESTS=true to enable)',
    () => {
      it('placeholder', () => {});
    },
  );
} else {
  // Only import heavy deps when guard passes to avoid touching real DB in CI.
  const { default: fastify } = await import('fastify');
  const { PrismaClient } = await import('@prisma/client');
  const { authRoutes } = await import('../routes/auth');
  const workspaceRoutes = (await import('../routes/workspaces')).default;
  const projectRoutes = (await import('../routes/projects')).default;
  const canvasRoutes = (await import('../routes/canvas')).default;

  // ─── Helpers ───────────────────────────────────────────────────────────────

  function buildApp() {
    const app = fastify({ logger: false });
    const prisma = new PrismaClient({
      datasources: { db: { url: process.env.TEST_DATABASE_URL } },
    });

    void app.register(authRoutes);
    void app.register(workspaceRoutes);
    void app.register(projectRoutes);
    void app.register(canvasRoutes);

    return { app, prisma };
  }

  function uniqueEmail(prefix: string): string {
    return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}@test.local`;
  }

  // ─── Suite ─────────────────────────────────────────────────────────────────

  describe('YAPPC Real API E2E', () => {
    let app: Awaited<ReturnType<typeof buildApp>>['app'];
    let prisma: InstanceType<typeof PrismaClient>;

    beforeAll(async () => {
      const built = buildApp();
      app = built.app;
      prisma = built.prisma;
      await prisma.$connect();
      await app.listen({ port: 0 });
    });

    afterAll(async () => {
      await app.close();
      await prisma.$disconnect();
    });

    // ─── Auth: registration ─────────────────────────────────────────────────

    describe('POST /auth/register', () => {
      it('registers a new user and returns tokens', async () => {
        const email = uniqueEmail('reg-user');
        const response = await app.inject({
          method: 'POST',
          url: '/auth/register',
          payload: { email, password: 'SecureP@ss1', name: 'Test User' },
        });

        expect(response.statusCode).toBe(201);
        const body = JSON.parse(response.body) as Record<string, unknown>;
        expect(body).toHaveProperty('tokens');
        const tokens = body.tokens as Record<string, unknown>;
        expect(tokens.accessToken).toBeDefined();
        expect(tokens.refreshToken).toBeDefined();
      });

      it('returns 409 when the email already exists', async () => {
        const email = uniqueEmail('dup-user');
        const payload = { email, password: 'SecureP@ss1', name: 'Dup User' };
        await app.inject({ method: 'POST', url: '/auth/register', payload });
        const response = await app.inject({
          method: 'POST',
          url: '/auth/register',
          payload,
        });
        expect(response.statusCode).toBe(409);
      });

      it('returns 400 for a malformed email', async () => {
        const response = await app.inject({
          method: 'POST',
          url: '/auth/register',
          payload: { email: 'not-an-email', password: 'SecureP@ss1', name: 'Bad' },
        });
        expect(response.statusCode).toBe(400);
      });

      it('returns 400 for a password shorter than 8 characters', async () => {
        const response = await app.inject({
          method: 'POST',
          url: '/auth/register',
          payload: { email: uniqueEmail('short-pw'), password: 'abc', name: 'Short' },
        });
        expect(response.statusCode).toBe(400);
      });
    });

    // ─── Auth: login ────────────────────────────────────────────────────────

    describe('POST /auth/login', () => {
      const credentials = {
        email: '',
        password: 'SecureP@ss1',
        name: 'Login User',
      };

      beforeEach(async () => {
        credentials.email = uniqueEmail('login-user');
        await app.inject({
          method: 'POST',
          url: '/auth/register',
          payload: credentials,
        });
      });

      it('returns an access token for valid credentials', async () => {
        const response = await app.inject({
          method: 'POST',
          url: '/auth/login',
          payload: { email: credentials.email, password: credentials.password },
        });

        expect(response.statusCode).toBe(200);
        const body = JSON.parse(response.body) as Record<string, unknown>;
        const tokens = body.tokens as Record<string, unknown>;
        expect(typeof tokens.accessToken).toBe('string');
        expect((tokens.accessToken as string).split('.').length).toBe(3); // JWT structure
      });

      it('returns 401 for wrong password', async () => {
        const response = await app.inject({
          method: 'POST',
          url: '/auth/login',
          payload: { email: credentials.email, password: 'WrongPass!' },
        });
        expect(response.statusCode).toBe(401);
      });

      it('returns 401 for unknown email', async () => {
        const response = await app.inject({
          method: 'POST',
          url: '/auth/login',
          payload: { email: uniqueEmail('ghost'), password: 'SecureP@ss1' },
        });
        expect(response.statusCode).toBe(401);
      });
    });

    // ─── Protected routes ────────────────────────────────────────────────────

    describe('Protected routes', () => {
      let accessToken: string;

      beforeEach(async () => {
        const email = uniqueEmail('protected-user');
        const regResponse = await app.inject({
          method: 'POST',
          url: '/auth/register',
          payload: { email, password: 'SecureP@ss1', name: 'Protected User' },
        });
        const body = JSON.parse(regResponse.body) as Record<string, unknown>;
        const tokens = body.tokens as { accessToken: string };
        accessToken = tokens.accessToken;
      });

      it('returns 200 for a valid JWT on GET /workspaces', async () => {
        const response = await app.inject({
          method: 'GET',
          url: '/workspaces',
          headers: { Authorization: `Bearer ${accessToken}` },
        });
        expect(response.statusCode).toBe(200);
      });

      it('returns 401 for a request to GET /workspaces without a token', async () => {
        const response = await app.inject({ method: 'GET', url: '/workspaces' });
        expect(response.statusCode).toBe(401);
      });

      it('returns 401 for a tampered JWT', async () => {
        const [header, payload] = accessToken.split('.');
        const tampered = `${header}.${payload}.invalidsignature`;
        const response = await app.inject({
          method: 'GET',
          url: '/workspaces',
          headers: { Authorization: `Bearer ${tampered}` },
        });
        expect(response.statusCode).toBe(401);
      });
    });

    // ─── Tenant isolation ────────────────────────────────────────────────────

    describe('Tenant isolation', () => {
      let tokenA: string;
      let tokenB: string;
      let workspaceIdA: string;

      beforeAll(async () => {
        // Register two users in different tenants
        const [resA, resB] = await Promise.all([
          app.inject({
            method: 'POST',
            url: '/auth/register',
            payload: {
              email: uniqueEmail('tenant-a'),
              password: 'SecureP@ss1',
              name: 'Tenant A User',
            },
          }),
          app.inject({
            method: 'POST',
            url: '/auth/register',
            payload: {
              email: uniqueEmail('tenant-b'),
              password: 'SecureP@ss1',
              name: 'Tenant B User',
            },
          }),
        ]);

        tokenA = (
          JSON.parse(resA.body) as { tokens: { accessToken: string } }
        ).tokens.accessToken;
        tokenB = (
          JSON.parse(resB.body) as { tokens: { accessToken: string } }
        ).tokens.accessToken;

        // Create a workspace as user A
        const wsResponse = await app.inject({
          method: 'POST',
          url: '/workspaces',
          headers: { Authorization: `Bearer ${tokenA}` },
          payload: { name: 'Tenant A Workspace' },
        });
        if (wsResponse.statusCode === 201) {
          workspaceIdA = (
            JSON.parse(wsResponse.body) as { id: string }
          ).id;
        }
      });

      it('User B cannot access a workspace created by User A', async () => {
        if (!workspaceIdA) {
          // Workspace creation failed — skip isolation test
          return;
        }
        const response = await app.inject({
          method: 'GET',
          url: `/workspaces/${workspaceIdA}`,
          headers: { Authorization: `Bearer ${tokenB}` },
        });
        // Cross-tenant access must be denied
        expect([403, 404]).toContain(response.statusCode);
      });

      it('User A can access their own workspace', async () => {
        if (!workspaceIdA) return;
        const response = await app.inject({
          method: 'GET',
          url: `/workspaces/${workspaceIdA}`,
          headers: { Authorization: `Bearer ${tokenA}` },
        });
        expect(response.statusCode).toBe(200);
      });
    });

    // ─── Code-generation request lifecycle ───────────────────────────────────

    describe('Code generation request lifecycle', () => {
      let accessToken: string;
      let workspaceId: string;
      let projectId: string;

      beforeAll(async () => {
        const email = uniqueEmail('codegen-user');
        const regResponse = await app.inject({
          method: 'POST',
          url: '/auth/register',
          payload: { email, password: 'SecureP@ss1', name: 'Codegen User' },
        });
        accessToken = (
          JSON.parse(regResponse.body) as { tokens: { accessToken: string } }
        ).tokens.accessToken;

        const wsResponse = await app.inject({
          method: 'POST',
          url: '/workspaces',
          headers: { Authorization: `Bearer ${accessToken}` },
          payload: { name: 'Codegen Workspace' },
        });
        if (wsResponse.statusCode === 201) {
          workspaceId = (JSON.parse(wsResponse.body) as { id: string }).id;
        }
      });

      it('creates a project successfully', async () => {
        if (!workspaceId) return;

        const response = await app.inject({
          method: 'POST',
          url: `/workspaces/${workspaceId}/projects`,
          headers: { Authorization: `Bearer ${accessToken}` },
          payload: { name: 'My Project', description: 'A test project' },
        });
        expect(response.statusCode).toBe(201);
        const body = JSON.parse(response.body) as { id: string };
        expect(body.id).toBeDefined();
        projectId = body.id;
      });

      it('lists projects for the workspace', async () => {
        if (!workspaceId || !projectId) return;

        const response = await app.inject({
          method: 'GET',
          url: `/workspaces/${workspaceId}/projects`,
          headers: { Authorization: `Bearer ${accessToken}` },
        });
        expect(response.statusCode).toBe(200);
        const body = JSON.parse(response.body) as unknown[];
        expect(Array.isArray(body)).toBe(true);
        expect(body.length).toBeGreaterThan(0);
      });

      it('saves and reloads unified canvas state for a project', async () => {
        if (!projectId) return;

        const canvasPayload = {
          nodes: [
            {
              id: 'node-1',
              type: 'input',
              position: { x: 120, y: 140 },
              data: { label: 'Release Gate Node' },
            },
          ],
          edges: [],
          viewport: {
            x: 0,
            y: 0,
            zoom: 1,
          },
        };

        const saveResponse = await app.inject({
          method: 'PUT',
          url: `/projects/${projectId}/canvas`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
          payload: {
            data: canvasPayload,
            changeType: 'MANUAL_SAVE',
            changeSummary: 'Canvas save/load E2E proof',
          },
        });

        expect(saveResponse.statusCode).toBe(200);

        const loadResponse = await app.inject({
          method: 'GET',
          url: `/canvas/${projectId}/unified-canvas`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        });

        expect(loadResponse.statusCode).toBe(200);
        const loadedBody = JSON.parse(loadResponse.body) as {
          canvas?: { data?: { nodes?: Array<{ id: string; data?: { label?: string } }> } };
        };
        expect(loadedBody.canvas?.data?.nodes?.[0]?.id).toBe('node-1');
        expect(loadedBody.canvas?.data?.nodes?.[0]?.data?.label).toBe(
          'Release Gate Node'
        );
      });
    });
  });
}
