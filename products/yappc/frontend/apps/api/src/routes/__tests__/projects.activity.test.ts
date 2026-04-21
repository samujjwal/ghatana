import fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const {
  projectFindUniqueMock,
  lifecycleActivityFindManyMock,
  auditLogEntryFindManyMock,
} = vi.hoisted(() => ({
  projectFindUniqueMock: vi.fn(),
  lifecycleActivityFindManyMock: vi.fn(),
  auditLogEntryFindManyMock: vi.fn(),
}));

vi.mock('../../db', () => ({
  default: {
    project: {
      findUnique: projectFindUniqueMock,
    },
    lifecycleActivityLog: {
      findMany: lifecycleActivityFindManyMock,
      create: vi.fn(),
    },
    auditLogEntry: {
      findMany: auditLogEntryFindManyMock,
    },
  },
}));

vi.mock('../../middleware/rbac.middleware', () => ({
  requirePermission: () => async () => undefined,
}));

vi.mock('../../services/audit/audit.service', () => ({
  getAuditService: () => ({
    log: vi.fn(),
  }),
}));

import projectRoutes from '../projects';

describe('project activity route', () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    vi.clearAllMocks();
    app = fastify({ logger: false });
    await app.register(projectRoutes, { prefix: '/api' });
  });

  afterEach(async () => {
    await app.close();
  });

  it('returns a combined project timeline sorted by newest event first', async () => {
    projectFindUniqueMock.mockResolvedValueOnce({ id: 'project-1' });
    lifecycleActivityFindManyMock.mockResolvedValueOnce([
      {
        id: 'life-1',
        action: 'PROJECT_LIFECYCLE_UPDATED',
        description: 'Project Alpha: lifecycle moved to VERIFY.',
        timestamp: new Date('2026-04-21T12:00:00.000Z'),
        userId: 'user-1',
      },
    ]);
    auditLogEntryFindManyMock.mockResolvedValueOnce([
      {
        id: 'audit-1',
        action: 'PROJECT_CREATED',
        details: 'Project Alpha created in workspace Platform Workspace',
        timestamp: new Date('2026-04-21T11:00:00.000Z'),
        actor: 'user-1',
        severity: 'info',
        success: true,
      },
    ]);

    const response = await app.inject({
      method: 'GET',
      url: '/api/projects/project-1/activity',
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({
      projectId: 'project-1',
      activity: [
        {
          id: 'lifecycle-life-1',
          source: 'lifecycle',
          action: 'PROJECT_LIFECYCLE_UPDATED',
          summary: 'Project Alpha: lifecycle moved to VERIFY.',
          timestamp: '2026-04-21T12:00:00.000Z',
          actor: 'user-1',
        },
        {
          id: 'audit-audit-1',
          source: 'audit',
          action: 'PROJECT_CREATED',
          summary: 'Project Alpha created in workspace Platform Workspace',
          timestamp: '2026-04-21T11:00:00.000Z',
          actor: 'user-1',
          severity: 'info',
          success: true,
        },
      ],
    });
  });
});