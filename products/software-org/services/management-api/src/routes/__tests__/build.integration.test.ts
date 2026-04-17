import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import buildRoutes from '../build.js';

const { mockPrisma } = vi.hoisted(() => ({
    mockPrisma: {
        buildWorkflow: {
            findMany: vi.fn(),
            count: vi.fn(),
            findUnique: vi.fn(),
            create: vi.fn(),
            findFirst: vi.fn(),
            update: vi.fn(),
        },
        auditEvent: {
            create: vi.fn(),
        },
    },
}));

vi.mock('../../db/client.js', () => ({
    prisma: mockPrisma,
}));

describe('Build Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(buildRoutes, { prefix: '/api/v1' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should list workflows with pagination metadata', async () => {
        mockPrisma.buildWorkflow.findMany.mockResolvedValue([
            {
                id: 'wf-1',
                tenantId: 'tenant-a',
                name: 'Deploy Pipeline',
                slug: 'deploy-pipeline',
                description: 'Build and deploy workflow',
                status: 'draft',
                ownerTeamId: 'team-1',
                trigger: { type: 'push' },
                steps: [{ id: 'build' }],
                workflowServices: [{ serviceId: 'svc-1' }],
                workflowPolicies: [{ policyId: 'pol-1' }],
                createdAt: new Date('2026-04-16T10:00:00.000Z'),
                updatedAt: new Date('2026-04-16T11:00:00.000Z'),
            },
        ]);
        mockPrisma.buildWorkflow.count.mockResolvedValue(1);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/build/workflows?tenantId=tenant-a&page=2&limit=5',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            data: [
                {
                    id: 'wf-1',
                    tenantId: 'tenant-a',
                    name: 'Deploy Pipeline',
                    slug: 'deploy-pipeline',
                    description: 'Build and deploy workflow',
                    status: 'draft',
                    ownerTeamId: 'team-1',
                    trigger: { type: 'push' },
                    steps: [{ id: 'build' }],
                    serviceIds: ['svc-1'],
                    policyIds: ['pol-1'],
                    createdAt: '2026-04-16T10:00:00.000Z',
                    updatedAt: '2026-04-16T11:00:00.000Z',
                },
            ],
            pagination: {
                page: '2',
                limit: '5',
                total: 1,
                totalPages: 1,
            },
        });
    });

    it('should reject duplicate workflow slugs', async () => {
        mockPrisma.buildWorkflow.findUnique.mockResolvedValue({ id: 'wf-existing' });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/build/workflows',
            payload: {
                tenantId: 'tenant-a',
                name: 'Deploy Pipeline',
                slug: 'deploy-pipeline',
                trigger: { type: 'push' },
                steps: [{ id: 'build' }],
            },
        });

        expect(response.statusCode).toBe(409);
        expect(response.json()).toEqual({ error: 'Workflow with this slug already exists' });
        expect(mockPrisma.auditEvent.create).not.toHaveBeenCalled();
    });

    it('should reject activation for workflows without valid steps', async () => {
        mockPrisma.buildWorkflow.findFirst.mockResolvedValue({
            id: 'wf-1',
            status: 'draft',
            trigger: { type: 'push' },
            steps: [],
        });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/build/workflows/wf-1/activate?tenantId=tenant-a',
        });

        expect(response.statusCode).toBe(400);
        expect(response.json()).toEqual({
            error: 'Invalid workflow configuration',
            details: ['Trigger and at least one step are required'],
        });
    });
});