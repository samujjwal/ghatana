import fastify, { type FastifyInstance } from 'fastify';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';

const {
  workspaceFindManyMock,
  projectFindManyMock,
  workspaceCountMock,
} = vi.hoisted(() => ({
  workspaceFindManyMock: vi.fn(),
  projectFindManyMock: vi.fn(),
  workspaceCountMock: vi.fn(),
}));

vi.mock('../db', () => ({
  default: {
    $queryRaw: vi.fn().mockResolvedValue([1]),
    workspace: {
      findMany: workspaceFindManyMock,
      count: workspaceCountMock,
    },
    project: {
      findMany: projectFindManyMock,
    },
  },
}));

vi.mock('../middleware/rbac.middleware', () => ({
  requirePermission: () => async () => undefined,
}));

vi.mock('../services/audit/audit.service', () => ({
  getAuditService: () => ({ log: vi.fn() }),
}));

import workspaceRoutes from '../routes/workspaces';
import projectRoutes from '../routes/projects';

describe('route migration compatibility prefixes', () => {
  let app: FastifyInstance;

  beforeAll(async () => {
    workspaceFindManyMock.mockResolvedValue([]);
    workspaceCountMock.mockResolvedValue(0);
    projectFindManyMock.mockResolvedValue([]);

    app = fastify({ logger: false });
    app.addHook('onRequest', async (request) => {
      request.user = {
        userId: 'user-123',
        email: 'user@yappc.local',
        role: 'ADMIN',
        tenantId: 'tenant-123',
        workspaceId: 'ws-123',
      };
    });

    await app.register(workspaceRoutes, { prefix: '/api' });
    await app.register(workspaceRoutes, { prefix: '/v1' });
    await app.register(workspaceRoutes, { prefix: '/api/v1' });
    await app.register(projectRoutes, { prefix: '/api' });
    await app.register(projectRoutes, { prefix: '/v1' });
    await app.register(projectRoutes, { prefix: '/api/v1' });
  });

  afterAll(async () => {
    await app.close();
  });

  it('registers workspace routes under /api, /v1, and /api/v1', async () => {
    const [apiResponse, v1Response, apiV1Response] = await Promise.all([
      app.inject({ method: 'GET', url: '/api/workspaces' }),
      app.inject({ method: 'GET', url: '/v1/workspaces' }),
      app.inject({ method: 'GET', url: '/api/v1/workspaces' }),
    ]);

    expect(apiResponse.statusCode).not.toBe(404);
    expect(v1Response.statusCode).not.toBe(404);
    expect(apiV1Response.statusCode).not.toBe(404);

    expect(apiV1Response.headers.deprecation).toBeUndefined();
  });

  it('registers project routes under /api, /v1, and /api/v1', async () => {
    const [apiResponse, v1Response, apiV1Response] = await Promise.all([
      app.inject({ method: 'GET', url: '/api/projects?workspaceId=test' }),
      app.inject({ method: 'GET', url: '/v1/projects?workspaceId=test' }),
      app.inject({ method: 'GET', url: '/api/v1/projects?workspaceId=test' }),
    ]);

    expect(apiResponse.statusCode).not.toBe(404);
    expect(v1Response.statusCode).not.toBe(404);
    expect(apiV1Response.statusCode).not.toBe(404);

    expect(apiV1Response.headers.deprecation).toBeUndefined();
  });
});
