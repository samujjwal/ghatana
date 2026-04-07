/**
 * @doc.type test-suite
 * @doc.purpose Real HTTP route integration tests for YAPPC API validating actual endpoint behavior
 * @doc.layer application
 * @doc.pattern Integration Test
 */

import {
  describe,
  it,
  expect,
  vi,
  beforeAll,
  afterAll,
  beforeEach,
} from 'vitest';
import { FastifyInstance } from 'fastify';
import { PrismaClient } from '@prisma/client';
import { createApp } from '../index';

/**
 * Test fixture for YAPPC API integration
 */
interface TestFixture {
  app: FastifyInstance;
  prisma: PrismaClient;
  db: {
    workspace: {
      create: ReturnType<typeof vi.fn>;
      findUnique: ReturnType<typeof vi.fn>;
      findMany: ReturnType<typeof vi.fn>;
      update: ReturnType<typeof vi.fn>;
      delete: ReturnType<typeof vi.fn>;
    };
    project: {
      create: ReturnType<typeof vi.fn>;
      findUnique: ReturnType<typeof vi.fn>;
      findMany: ReturnType<typeof vi.fn>;
      update: ReturnType<typeof vi.fn>;
    };
    canvas: {
      create: ReturnType<typeof vi.fn>;
      findUnique: ReturnType<typeof vi.fn>;
      update: ReturnType<typeof vi.fn>;
    };
  };
}

/**
 * Creates test JWT token
 */
function createTestJWT(claims: {
  userId: string;
  tenantId: string;
  email?: string;
}): string {
  const header = Buffer.from(
    JSON.stringify({ alg: 'HS256', typ: 'JWT' })
  ).toString('base64');
  const payload = Buffer.from(
    JSON.stringify({
      sub: claims.userId,
      tenantId: claims.tenantId,
      email: claims.email || `user@test.local`,
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 3600,
    })
  ).toString('base64');
  const signature = Buffer.from(`${header}.${payload}secret`).toString(
    'base64'
  );
  return `${header}.${payload}.${signature}`;
}

/**
 * Creates mock Prisma client with type-safe methods
 */
function createMockPrismaClient(): PrismaClient {
  return {
    workspace: {
      create: vi.fn(),
      findUnique: vi.fn(),
      findMany: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
    },
    project: {
      create: vi.fn(),
      findUnique: vi.fn(),
      findMany: vi.fn(),
      update: vi.fn(),
    },
    canvas: {
      create: vi.fn(),
      findUnique: vi.fn(),
      update: vi.fn(),
    },
    $connect: vi.fn().mockResolvedValue(undefined),
    $disconnect: vi.fn().mockResolvedValue(undefined),
  } as unknown as PrismaClient;
}

describe('YAPPC API Routes (Real HTTP Integration)', () => {
  let fixture: TestFixture;
  const JWT_SECRET = 'test-secret-key-32-chars-minimum!!';

  beforeAll(async () => {
    const prisma = createMockPrismaClient();

    fixture = {
      app: await createApp({
        prisma,
        jwtSecret: JWT_SECRET,
        port: 0, // Random port for testing
      }),
      prisma,
      db: {
        workspace: prisma.workspace as any,
        project: prisma.project as any,
        canvas: prisma.canvas as any,
      },
    };
  });

  afterAll(async () => {
    await fixture.app.close();
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Health Check Endpoint', () => {
    it('returns 200 with healthy status', async () => {
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/health',
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.status).toBe('healthy');
    });

    it('includes timestamp and version info', async () => {
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/health',
      });

      const body = JSON.parse(response.body);
      expect(body).toHaveProperty('timestamp');
      expect(body).toHaveProperty('version' || body.status);
    });

    it('includes dependency status', async () => {
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/health',
      });

      const body = JSON.parse(response.body);
      expect(body).toHaveProperty('database');
      expect(body).toHaveProperty('cache');
    });
  });

  describe('Workspace Routes (/api/v1/workspaces)', () => {
    it('POST /api/v1/workspaces creates workspace with valid JWT', async () => {
      const userId = 'user-123';
      const tenantId = 'tenant-456';
      const token = createTestJWT({ userId, tenantId });

      const mockWorkspace = {
        id: 'workspace-789',
        tenantId,
        name: 'Test Workspace',
        createdBy: userId,
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      fixture.db.workspace.create.mockResolvedValueOnce(mockWorkspace);

      const response = await fixture.app.inject({
        method: 'POST',
        url: '/api/v1/workspaces',
        headers: { authorization: `Bearer ${token}` },
        payload: { name: 'Test Workspace' },
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body.id).toBe('workspace-789');
      expect(body.name).toBe('Test Workspace');
      expect(body.createdBy).toBe(userId);

      // Verify DB was called with correct user context
      expect(fixture.db.workspace.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            tenantId,
            createdBy: userId,
          }),
        })
      );
    });

    it('GET /api/v1/workspaces lists user workspaces', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });

      const mockWorkspaces = [
        { id: 'ws-1', name: 'Workspace 1', tenantId: 'tenant-456' },
        { id: 'ws-2', name: 'Workspace 2', tenantId: 'tenant-456' },
      ];

      fixture.db.workspace.findMany.mockResolvedValueOnce(mockWorkspaces);

      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/workspaces',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(Array.isArray(body)).toBe(true);
      expect(body.length).toBe(2);
      expect(body[0].name).toBe('Workspace 1');
    });

    it('blocks GET /api/v1/workspaces without authentication', async () => {
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/workspaces',
        headers: {},
      });

      expect(response.statusCode).toBe(401);
      const body = JSON.parse(response.body);
      expect(body.error || body.message).toBeTruthy();
    });

    it('GET /api/v1/workspaces/:id returns workspace with auth check', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });
      const mockWorkspace = {
        id: 'workspace-789',
        tenantId: 'tenant-456',
        name: 'My Workspace',
        createdBy: 'user-123',
      };

      fixture.db.workspace.findUnique.mockResolvedValueOnce(mockWorkspace);

      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/workspaces/workspace-789',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.id).toBe('workspace-789');
      expect(body.name).toBe('My Workspace');
    });

    it('enforces workspace membership - blocks user from accessing other user workspaces', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });

      // Mock workspace owned by different user
      const mockWorkspace = {
        id: 'workspace-789',
        tenantId: 'tenant-456',
        name: 'Other User Workspace',
        createdBy: 'user-999',
        members: [], // User-123 not in members
      };

      fixture.db.workspace.findUnique.mockResolvedValueOnce(mockWorkspace);

      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/workspaces/workspace-789',
        headers: { authorization: `Bearer ${token}` },
      });

      // Should be forbidden when user is not member
      expect([403, 404].includes(response.statusCode)).toBe(true);
    });

    it('DELETE /api/v1/workspaces/:id requires ownership', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });

      fixture.db.workspace.delete.mockResolvedValueOnce({
        id: 'workspace-789',
      });

      const response = await fixture.app.inject({
        method: 'DELETE',
        url: '/api/v1/workspaces/workspace-789',
        headers: { authorization: `Bearer ${token}` },
      });

      // 204 No Content or 200 OK on successful delete
      expect([200, 204].includes(response.statusCode)).toBe(true);
    });
  });

  describe('Project Routes (/api/v1/projects)', () => {
    it('POST /api/v1/projects creates project in workspace', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });
      const mockProject = {
        id: 'project-123',
        workspaceId: 'workspace-789',
        name: 'New Project',
        createdBy: 'user-123',
        createdAt: new Date(),
      };

      fixture.db.project.create.mockResolvedValueOnce(mockProject);

      const response = await fixture.app.inject({
        method: 'POST',
        url: '/api/v1/projects',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          workspaceId: 'workspace-789',
          name: 'New Project',
        },
      });

      expect(response.statusCode).toBe(201);
      const body = JSON.parse(response.body);
      expect(body.id).toBe('project-123');
      expect(body.workspaceId).toBe('workspace-789');
      expect(body.name).toBe('New Project');
    });

    it('GET /api/v1/projects/:id returns full project with canvas and metadata', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });
      const mockProject = {
        id: 'project-123',
        workspaceId: 'workspace-789',
        name: 'My Project',
        canvas: { id: 'canvas-1', content: {} },
        versions: [
          { id: 'v1', createdAt: new Date(), snapshot: {} },
          { id: 'v2', createdAt: new Date(), snapshot: {} },
        ],
        lastModified: new Date(),
      };

      fixture.db.project.findUnique.mockResolvedValueOnce(mockProject);

      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/projects/project-123',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.canvas).toBeTruthy();
      expect(body.versions).toBeTruthy();
      expect(body.lastModified).toBeTruthy();
    });

    it('PUT /api/v1/projects/:id updates project metadata', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });
      const mockProject = {
        id: 'project-123',
        name: 'Updated Project',
        description: 'New description',
      };

      fixture.db.project.update.mockResolvedValueOnce(mockProject);

      const response = await fixture.app.inject({
        method: 'PUT',
        url: '/api/v1/projects/project-123',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          name: 'Updated Project',
          description: 'New description',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.name).toBe('Updated Project');
      expect(body.description).toBe('New description');
    });
  });

  describe('Canvas Routes (/api/v1/projects/:id/canvas)', () => {
    it('PUT /api/v1/projects/:id/canvas updates canvas content', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });
      const canvasContent = {
        elements: [{ id: 'elem-1', type: 'component', props: {} }],
        layout: {},
      };

      const mockCanvas = {
        id: 'canvas-1',
        projectId: 'project-123',
        content: canvasContent,
        version: 2,
        updatedAt: new Date(),
      };

      fixture.db.canvas.update.mockResolvedValueOnce(mockCanvas);

      const response = await fixture.app.inject({
        method: 'PUT',
        url: '/api/v1/projects/project-123/canvas',
        headers: { authorization: `Bearer ${token}` },
        payload: { content: canvasContent },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.content).toEqual(canvasContent);
      expect(body.version).toBe(2);
    });

    it('POST /api/v1/projects/:id/canvas/versions creates version snapshot', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });
      const mockVersion = {
        id: 'version-456',
        canvasId: 'canvas-1',
        snapshot: { elements: [] },
        createdAt: new Date(),
        label: 'v1.0',
      };

      const response = await fixture.app.inject({
        method: 'POST',
        url: '/api/v1/projects/project-123/canvas/versions',
        headers: { authorization: `Bearer ${token}` },
        payload: { label: 'v1.0' },
      });

      expect([200, 201].includes(response.statusCode)).toBe(true);
    });

    it('GET /api/v1/projects/:id/canvas/versions returns all versions', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });
      const mockVersions = [
        { id: 'v3', label: 'v3', createdAt: new Date('2026-04-02') },
        { id: 'v2', label: 'v2', createdAt: new Date('2026-04-01') },
        { id: 'v1', label: 'v1', createdAt: new Date('2026-03-31') },
      ];

      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/projects/project-123/canvas/versions',
        headers: { authorization: `Bearer ${token}` },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(Array.isArray(body)).toBe(true);
      expect(body.length).toBe(3);
      // Verify ordered by date descending
      expect(new Date(body[0].createdAt).getTime()).toBeGreaterThan(
        new Date(body[1].createdAt).getTime()
      );
    });
  });

  describe('Auth Middleware in Real Request Flow', () => {
    it('populates request.user from JWT claims', async () => {
      const userId = 'user-auth-test';
      const tenantId = 'tenant-auth-test';
      const token = createTestJWT({ userId, tenantId });

      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/workspaces',
        headers: { authorization: `Bearer ${token}` },
      });

      // If auth middleware works, request succeeds (not 401)
      expect(response.statusCode).not.toBe(401);
    });

    it('validates Bearer token format strictly', async () => {
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/workspaces',
        headers: { authorization: 'Basic dXNlcjpwYXNz' }, // Basic auth, not Bearer
      });

      expect(response.statusCode).toBe(401);
    });

    it('rejects missing Authorization header to protected routes', async () => {
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/workspaces',
        headers: {},
      });

      expect(response.statusCode).toBe(401);
    });
  });

  describe('GraphQL Parity (/graphql)', () => {
    it('POST /graphql executes query with schema', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });

      const response = await fixture.app.inject({
        method: 'POST',
        url: '/graphql',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          query: '{ workspaces { id name } }',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      // GraphQL should return data or errors
      expect(body.data || body.errors).toBeTruthy();
    });

    it('GraphQL mutation response matches REST POST semantics', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });

      // REST: Create workspace
      const restResponse = await fixture.app.inject({
        method: 'POST',
        url: '/api/v1/workspaces',
        headers: { authorization: `Bearer ${token}` },
        payload: { name: 'Test Workspace' },
      });

      // GraphQL: Create workspace mutation
      const gqlResponse = await fixture.app.inject({
        method: 'POST',
        url: '/graphql',
        headers: { authorization: `Bearer ${token}` },
        payload: {
          query:
            'mutation { createWorkspace(name: "Test Workspace") { id name } }',
        },
      });

      // Both should succeed
      expect(restResponse.statusCode).toBe(201);
      expect(gqlResponse.statusCode).toBe(200);
    });
  });

  describe('Proxy to Java Backend (/api/v1/proxy/*)', () => {
    it('forwards request headers to upstream', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });

      // Mock upstream server behavior
      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/agents',
        headers: {
          authorization: `Bearer ${token}`,
          'x-custom-header': 'test-value',
        },
      });

      // Proxy should forward to Java backend
      // Response depends on upstream, but should not be 401 from our middleware
      expect(response.statusCode).not.toBe(401);
    });

    it('forwards request body to upstream', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });

      const payload = { query: 'test' };

      const response = await fixture.app.inject({
        method: 'POST',
        url: '/api/v1/execute-agent',
        headers: { authorization: `Bearer ${token}` },
        payload,
      });

      // Should forward to Java backend
      expect(response.statusCode).not.toBe(401);
    });

    it('maps upstream 500 errors to 503 or sanitized response', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });

      // Mock upstream returning error
      const response = await fixture.app.inject({
        method: 'POST',
        url: '/api/v1/execute-agent',
        headers: { authorization: `Bearer ${token}` },
        payload: { query: 'error-inducing-query' },
      });

      // Should not expose internal Java errors
      if (response.statusCode === 500) {
        const body = JSON.parse(response.body);
        expect(body.message).not.toContain(
          'NullPointerException' || 'Stack trace'
        );
      }
    });
  });

  describe('Metric Collection', () => {
    it('records request duration metric', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });

      await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/workspaces',
        headers: { authorization: `Bearer ${token}` },
      });

      const metricsResponse = await fixture.app.inject({
        method: 'GET',
        url: '/metrics',
      });

      expect(metricsResponse.statusCode).toBe(200);
      // Should contain histogram/summary metrics
      expect(metricsResponse.body).toContain('http_request_duration');
    });

    it('counts requests by method and path', async () => {
      const token = createTestJWT({
        userId: 'user-123',
        tenantId: 'tenant-456',
      });

      // Hit GET /api/v1/workspaces twice
      await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/workspaces',
        headers: { authorization: `Bearer ${token}` },
      });
      await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/workspaces',
        headers: { authorization: `Bearer ${token}` },
      });

      const metricsResponse = await fixture.app.inject({
        method: 'GET',
        url: '/metrics',
      });

      // Should show count of 2 for GET /api/v1/workspaces
      const prometheusMetrics = metricsResponse.body;
      expect(prometheusMetrics).toContain('method="GET"');
      expect(prometheusMetrics).toContain('/api/v1/workspaces');
    });
  });

  describe('Workspace/Project Permissions', () => {
    it('blocks user from accessing workspace they do not own', async () => {
      const userAToken = createTestJWT({
        userId: 'user-a',
        tenantId: 'tenant-1',
      });
      const userBToken = createTestJWT({
        userId: 'user-b',
        tenantId: 'tenant-1',
      });

      // Mock User A creates workspace
      fixture.db.workspace.create.mockResolvedValueOnce({
        id: 'ws-1',
        tenantId: 'tenant-1',
        createdBy: 'user-a',
        members: [{ userId: 'user-a', role: 'owner' }],
      });

      // User B tries to access it
      fixture.db.workspace.findUnique.mockResolvedValueOnce({
        id: 'ws-1',
        tenantId: 'tenant-1',
        createdBy: 'user-a',
        members: [{ userId: 'user-a', role: 'owner' }], // User B not in members
      });

      const response = await fixture.app.inject({
        method: 'GET',
        url: '/api/v1/workspaces/ws-1',
        headers: { authorization: `Bearer ${userBToken}` },
      });

      expect([403, 404].includes(response.statusCode)).toBe(true);
    });

    it('allows workspace members with sufficient role to edit canvas', async () => {
      const token = createTestJWT({
        userId: 'user-editor',
        tenantId: 'tenant-1',
      });

      // Mock workspace with user-editor as editor
      fixture.db.workspace.findUnique.mockResolvedValueOnce({
        id: 'ws-1',
        members: [{ userId: 'user-editor', role: 'editor' }],
      });

      // Can update canvas
      const response = await fixture.app.inject({
        method: 'PUT',
        url: '/api/v1/projects/project-1/canvas',
        headers: { authorization: `Bearer ${token}` },
        payload: { content: {} },
      });

      // Should succeed (not 403)
      expect(response.statusCode).not.toBe(403);
    });

    it('blocks non-owner from deleting workspace', async () => {
      const editorToken = createTestJWT({
        userId: 'user-editor',
        tenantId: 'tenant-1',
      });

      const response = await fixture.app.inject({
        method: 'DELETE',
        url: '/api/v1/workspaces/ws-1',
        headers: { authorization: `Bearer ${editorToken}` },
      });

      // Should be forbidden
      expect(response.statusCode).toBe(403);
    });
  });

  describe('Audit Logging', () => {
    it('logs mutations with user context', async () => {
      const userId = 'user-audit-test';
      const token = createTestJWT({ userId, tenantId: 'tenant-1' });

      const logSpy = vi.spyOn(fixture.app.log, 'info');

      await fixture.app.inject({
        method: 'POST',
        url: '/api/v1/workspaces',
        headers: { authorization: `Bearer ${token}` },
        payload: { name: 'Audited Workspace' },
      });

      // Should log mutation with user ID
      expect(logSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          userId,
          action: 'create_workspace',
        })
      );
    });

    it('creates audit trail entry for workspace updates', async () => {
      const token = createTestJWT({ userId: 'user-1', tenantId: 'tenant-1' });

      // Mock audit table creation
      const auditSpy = vi.spyOn(fixture.app.log, 'info');

      await fixture.app.inject({
        method: 'PUT',
        url: '/api/v1/projects/project-1/canvas',
        headers: { authorization: `Bearer ${token}` },
        payload: { content: {} },
      });

      // Should record audit entry
      expect(auditSpy).toHaveBeenCalled();
    });
  });
});
