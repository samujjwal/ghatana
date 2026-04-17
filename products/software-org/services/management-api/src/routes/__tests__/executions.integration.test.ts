import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import executionRoutes from '../executions.js';

const { mockPrisma } = vi.hoisted(() => ({
    mockPrisma: {
        workflow: {
            findFirst: vi.fn(),
        },
        workflowExecution: {
            create: vi.fn(),
            findUnique: vi.fn(),
            update: vi.fn(),
        },
        workflowExecutionStep: {
            create: vi.fn(),
        },
        workflowTrigger: {
            findMany: vi.fn(),
            create: vi.fn(),
            findUnique: vi.fn(),
            update: vi.fn(),
            delete: vi.fn(),
        },
    },
}));

vi.mock('../../db/client.js', () => ({
    prisma: mockPrisma,
}));

vi.mock('../../../generated/prisma-client/index.js', () => ({
    Prisma: {
        JsonNull: null,
    },
}));

function buildWorkflow(overrides: Record<string, unknown> = {}) {
    return {
        id: 'workflow-1',
        name: 'release-train',
        ...overrides,
    };
}

function buildTrigger(overrides: Record<string, unknown> = {}) {
    return {
        id: 'trigger-1',
        workflowId: 'workflow-1',
        type: 'webhook',
        enabled: true,
        config: { branch: 'main' },
        createdAt: new Date('2026-04-16T00:00:00.000Z'),
        ...overrides,
    };
}

describe('Execution Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(executionRoutes, { prefix: '/api/v1' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should execute a workflow resolved by name and create an initial step', async () => {
        mockPrisma.workflow.findFirst.mockResolvedValue(buildWorkflow());
        mockPrisma.workflowExecution.create.mockResolvedValue({
            id: 'execution-1',
            workflowId: 'workflow-1',
            status: 'pending',
            startedAt: new Date('2026-04-16T10:00:00.000Z'),
        });
        mockPrisma.workflowExecutionStep.create.mockResolvedValue({ id: 'step-1' });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/workflows/release-train/execute',
        });

        expect(response.statusCode).toBe(201);
        expect(mockPrisma.workflow.findFirst).toHaveBeenCalledWith({
            where: { OR: [{ id: 'release-train' }, { name: 'release-train' }] },
        });
        expect(mockPrisma.workflowExecution.create).toHaveBeenCalledWith(
            expect.objectContaining({
                data: expect.objectContaining({
                    workflowId: 'workflow-1',
                    workflowKey: 'release-train',
                    status: 'pending',
                    logs: ['Execution started'],
                }),
            })
        );
        expect(mockPrisma.workflowExecutionStep.create).toHaveBeenCalledWith(
            expect.objectContaining({
                data: expect.objectContaining({
                    executionId: 'execution-1',
                    stepKey: 'init',
                    status: 'running',
                }),
            })
        );
        expect(response.json()).toEqual({
            id: 'execution-1',
            workflowId: 'workflow-1',
            status: 'pending',
            startedAt: '2026-04-16T10:00:00.000Z',
            triggeredBy: 'user-demo',
        });
    });

    it('should return 404 when listing triggers for a missing workflow', async () => {
        mockPrisma.workflow.findFirst.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/workflows/missing-workflow/triggers',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({ error: 'Workflow not found' });
    });

    it('should create triggers with schedule defaults when request body omits fields', async () => {
        mockPrisma.workflow.findFirst.mockResolvedValue(buildWorkflow());
        mockPrisma.workflowTrigger.create.mockImplementation(async ({ data }: { data: Record<string, unknown> }) =>
            buildTrigger({
                id: 'trigger-created',
                type: data.type,
                enabled: data.enabled,
                config: data.config,
            })
        );

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/workflows/workflow-1/triggers',
            payload: {},
        });

        expect(response.statusCode).toBe(201);
        expect(mockPrisma.workflowTrigger.create).toHaveBeenCalledWith(
            expect.objectContaining({
                data: {
                    workflowId: 'workflow-1',
                    type: 'schedule',
                    config: {},
                    enabled: true,
                },
            })
        );
        expect(response.json()).toEqual({
            id: 'trigger-created',
            workflowId: 'workflow-1',
            type: 'schedule',
            enabled: true,
        });
    });

    it('should preserve trigger config when patch omits config updates', async () => {
        mockPrisma.workflowTrigger.findUnique.mockResolvedValue(buildTrigger());
        mockPrisma.workflowTrigger.update.mockImplementation(async ({ data }: { data: Record<string, unknown> }) =>
            buildTrigger({
                type: data.type,
                enabled: data.enabled,
                config: data.config,
            })
        );

        const response = await fastify.inject({
            method: 'PATCH',
            url: '/api/v1/triggers/trigger-1',
            payload: {
                type: 'cron',
                enabled: false,
            },
        });

        expect(response.statusCode).toBe(200);
        expect(mockPrisma.workflowTrigger.update).toHaveBeenCalledWith(
            expect.objectContaining({
                data: expect.objectContaining({
                    type: 'cron',
                    enabled: false,
                    config: { branch: 'main' },
                }),
            })
        );
        expect(response.json()).toEqual({
            id: 'trigger-1',
            type: 'cron',
            enabled: false,
        });
    });

    it('should return 404 when execution details are missing', async () => {
        mockPrisma.workflowExecution.findUnique.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/executions/missing-execution',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({ error: 'Execution not found' });
    });

    it('should append a cancellation log when cancelling an execution', async () => {
        mockPrisma.workflowExecution.findUnique.mockResolvedValue({
            id: 'execution-1',
            logs: ['Execution started'],
        });
        mockPrisma.workflowExecution.update.mockImplementation(async ({ data }: { data: Record<string, unknown> }) => ({
            id: 'execution-1',
            status: data.status,
            logs: data.logs,
        }));

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/executions/execution-1/cancel',
        });

        expect(response.statusCode).toBe(200);
        expect(mockPrisma.workflowExecution.update).toHaveBeenCalledWith(
            expect.objectContaining({
                data: expect.objectContaining({
                    status: 'cancelled',
                    logs: ['Execution started', 'Execution cancelled by user'],
                }),
            })
        );
        expect(response.json()).toEqual({ id: 'execution-1', status: 'cancelled' });
    });
});