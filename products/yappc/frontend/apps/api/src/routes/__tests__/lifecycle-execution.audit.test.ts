import Fastify, { type FastifyInstance } from 'fastify';
import { randomUUID } from 'node:crypto';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const {
  lifecycleExecutionFindUniqueMock,
  lifecycleExecutionCreateMock,
  lifecycleExecutionUpdateMock,
  lifecycleExecutionFindManyMock,
  lifecycleExecutionCountMock,
  auditLogMock,
} = vi.hoisted(() => ({
  lifecycleExecutionFindUniqueMock: vi.fn(),
  lifecycleExecutionCreateMock: vi.fn(),
  lifecycleExecutionUpdateMock: vi.fn(),
  lifecycleExecutionFindManyMock: vi.fn(),
  lifecycleExecutionCountMock: vi.fn(),
  auditLogMock: vi.fn(),
}));

vi.mock('../../database/client.js', () => ({
  getPrismaClient: () => ({
    lifecycleExecutionResult: {
      findUnique: lifecycleExecutionFindUniqueMock,
      create: lifecycleExecutionCreateMock,
      update: lifecycleExecutionUpdateMock,
      findMany: lifecycleExecutionFindManyMock,
      count: lifecycleExecutionCountMock,
    },
  }),
}));

vi.mock('../../middleware/rbac.middleware', () => ({
  requirePermission: () => async () => undefined,
}));

vi.mock('../../services/audit/audit.service', () => ({
  getAuditService: () => ({
    log: auditLogMock,
  }),
}));

import lifecycleExecutionRoutes from '../lifecycle-execution';

const serviceToken = 'test-java-backend-token';

function buildExecutionPayload(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    projectId: 'project-123',
    executionId: `execution-${randomUUID()}`,
    status: 'SUCCESS',
    startedAt: '2026-05-07T12:00:00.000Z',
    completedAt: '2026-05-07T12:01:00.000Z',
    totalDurationMs: 60000,
    executedPhases: ['INTENT', 'RUN'],
    phaseDurationsMs: { INTENT: 15000, RUN: 45000 },
    success: true,
    tokenUsage: { total: 1234 },
    fallbacksUsed: [],
    ...overrides,
  };
}

function buildServiceHeaders(idempotencyKey?: string): Record<string, string> {
  return {
    authorization: `Bearer ${serviceToken}`,
    'x-tenant-id': 'tenant-123',
    'x-workspace-id': 'workspace-123',
    'x-user-id': 'java-runner',
    'x-user-role': 'SERVICE',
    'x-correlation-id': 'corr-123',
    'user-agent': 'vitest',
    ...(idempotencyKey ? { 'idempotency-key': idempotencyKey } : {}),
  };
}

describe('lifecycle execution audit logging', () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    vi.clearAllMocks();
    process.env.JAVA_BACKEND_API_KEY = serviceToken;
    app = Fastify({ logger: false });
    await app.register(lifecycleExecutionRoutes, { prefix: '/api/lifecycle-execution' });
  });

  afterEach(async () => {
    await app.close();
  });

  it('records scoped audit metadata when creating an execution result', async () => {
    const payload = buildExecutionPayload({ executionId: 'execution-create-audit' });
    const execution = {
      id: 'db-execution-1',
      projectId: payload.projectId,
      executionId: payload.executionId,
      status: payload.status,
      startedAt: new Date(String(payload.startedAt)),
      completedAt: new Date(String(payload.completedAt)),
    };

    lifecycleExecutionFindUniqueMock.mockResolvedValueOnce(null);
    lifecycleExecutionFindUniqueMock.mockResolvedValueOnce(null);
    lifecycleExecutionCreateMock.mockResolvedValueOnce(execution);
    auditLogMock.mockResolvedValueOnce({ id: 'audit-1' });

    const response = await app.inject({
      method: 'POST',
      url: '/api/lifecycle-execution/results',
      headers: buildServiceHeaders('create-audit-key'),
      payload,
    });

    expect(response.statusCode).toBe(201);
    expect(response.json()).toMatchObject({
      success: true,
      auditRecorded: true,
      execution: {
        id: 'db-execution-1',
        projectId: 'project-123',
        executionId: 'execution-create-audit',
      },
    });
    expect(auditLogMock).toHaveBeenCalledWith({
      action: 'YAPPC_LIFECYCLE_EXECUTION_RESULT',
      actor: 'java-runner',
      actorRole: 'SERVICE',
      resource: 'lifecycle-execution/execution-create-audit',
      severity: 'info',
      details: 'Lifecycle execution create success',
      ipAddress: expect.any(String),
      userAgent: 'vitest',
      method: 'POST',
      status: 201,
      tenantId: 'tenant-123',
      success: true,
      error: undefined,
      metadata: expect.objectContaining({
        workspaceId: 'workspace-123',
        projectId: 'project-123',
        executionId: 'execution-create-audit',
        executionStatus: 'SUCCESS',
        durationMs: 60000,
        outcome: 'SUCCESS',
        operation: 'create',
        correlationId: 'corr-123',
        route: '/api/lifecycle-execution/results',
        executedPhases: ['INTENT', 'RUN'],
        fallbacksUsed: [],
      }),
    });
  });

  it('returns the stored idempotent response without writing duplicate audit events', async () => {
    const payload = buildExecutionPayload({ executionId: 'execution-idempotent-audit' });
    const execution = {
      id: 'db-execution-2',
      projectId: payload.projectId,
      executionId: payload.executionId,
      status: payload.status,
      startedAt: new Date(String(payload.startedAt)),
      completedAt: new Date(String(payload.completedAt)),
    };

    lifecycleExecutionFindUniqueMock.mockResolvedValueOnce(null);
    lifecycleExecutionFindUniqueMock.mockResolvedValueOnce(null);
    lifecycleExecutionCreateMock.mockResolvedValueOnce(execution);
    auditLogMock.mockResolvedValueOnce({ id: 'audit-2' });

    const firstResponse = await app.inject({
      method: 'POST',
      url: '/api/lifecycle-execution/results',
      headers: buildServiceHeaders('dedupe-audit-key'),
      payload,
    });
    const secondResponse = await app.inject({
      method: 'POST',
      url: '/api/lifecycle-execution/results',
      headers: buildServiceHeaders('dedupe-audit-key'),
      payload,
    });

    expect(firstResponse.statusCode).toBe(201);
    expect(secondResponse.statusCode).toBe(200);
    expect(secondResponse.json()).toMatchObject({
      success: true,
      auditRecorded: true,
      execution: { executionId: 'execution-idempotent-audit' },
    });
    expect(auditLogMock).toHaveBeenCalledTimes(1);
    expect(lifecycleExecutionCreateMock).toHaveBeenCalledTimes(1);
  });

  it('records failed phase-result outcomes with project and phase scope', async () => {
    lifecycleExecutionFindUniqueMock.mockResolvedValueOnce({
      id: 'db-execution-3',
      projectId: 'project-123',
      executionId: 'execution-phase-audit',
      status: 'RUNNING',
      startedAt: new Date('2026-05-07T12:00:00.000Z'),
      completedAt: null,
      phaseDurationsMs: { INTENT: 1000 },
    });
    lifecycleExecutionUpdateMock.mockResolvedValueOnce({
      id: 'db-execution-3',
      projectId: 'project-123',
      executionId: 'execution-phase-audit',
      status: 'RUNNING',
    });
    auditLogMock.mockResolvedValueOnce({ id: 'audit-3' });

    const response = await app.inject({
      method: 'POST',
      url: '/api/lifecycle-execution/results/execution-phase-audit/phase',
      headers: buildServiceHeaders(),
      payload: {
        phase: 'run',
        result: { failedChecks: ['smoke-test'] },
        durationMs: 42000,
        status: 'FAILED',
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      success: true,
      auditRecorded: true,
      execution: { executionId: 'execution-phase-audit' },
    });
    expect(auditLogMock).toHaveBeenCalledWith(
      expect.objectContaining({
        action: 'YAPPC_LIFECYCLE_EXECUTION_PHASE_RESULT',
        resource: 'lifecycle-execution/execution-phase-audit',
        severity: 'warn',
        status: 200,
        success: false,
        metadata: expect.objectContaining({
          projectId: 'project-123',
          executionId: 'execution-phase-audit',
          phase: 'run',
          executionStatus: 'FAILED',
          durationMs: 42000,
          outcome: 'FAILED',
          operation: 'phase-update',
        }),
      }),
    );
  });

  it('surfaces audit persistence failure in the execution response', async () => {
    const payload = buildExecutionPayload({ executionId: 'execution-audit-failure' });
    const execution = {
      id: 'db-execution-4',
      projectId: payload.projectId,
      executionId: payload.executionId,
      status: payload.status,
      startedAt: new Date(String(payload.startedAt)),
      completedAt: new Date(String(payload.completedAt)),
    };

    lifecycleExecutionFindUniqueMock.mockResolvedValueOnce(null);
    lifecycleExecutionFindUniqueMock.mockResolvedValueOnce(null);
    lifecycleExecutionCreateMock.mockResolvedValueOnce(execution);
    auditLogMock.mockRejectedValueOnce(new Error('audit unavailable'));

    const response = await app.inject({
      method: 'POST',
      url: '/api/lifecycle-execution/results',
      headers: buildServiceHeaders('audit-failure-key'),
      payload,
    });

    expect(response.statusCode).toBe(201);
    expect(response.json()).toMatchObject({
      success: true,
      auditRecorded: false,
      execution: { executionId: 'execution-audit-failure' },
    });
  });
});
