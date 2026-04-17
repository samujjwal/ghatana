import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import tenantRoutes from '../tenants.js';

const { mockPrisma } = vi.hoisted(() => ({
    mockPrisma: {
        tenant: {
            findFirst: vi.fn(),
        },
        alert: {
            findMany: vi.fn(),
        },
        workflow: {
            findMany: vi.fn(),
        },
    },
}));

vi.mock('../../db/client.js', () => ({
    prisma: mockPrisma,
}));

describe('Tenant Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(tenantRoutes, { prefix: '/api/v1' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should return 404 for unknown tenant health requests', async () => {
        mockPrisma.tenant.findFirst.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/tenants/missing/metrics/health',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({ error: 'Tenant not found' });
    });

    it('should derive tenant health from environment records', async () => {
        mockPrisma.tenant.findFirst.mockResolvedValue({
            id: 'tenant-1',
            key: 'acme',
            environments: [
                { healthy: true },
                { healthy: false },
            ],
        });

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/tenants/acme/metrics/health',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            tenantId: 'acme',
            healthy: false,
            lastChecked: expect.any(String),
        });
    });

    it('should return unresolved alerts for a tenant', async () => {
        mockPrisma.tenant.findFirst.mockResolvedValue({ id: 'tenant-1', key: 'acme' });
        mockPrisma.alert.findMany.mockResolvedValue([
            {
                id: 'alert-1',
                severity: 'critical',
                message: 'Deployment blocked',
                timestamp: new Date('2026-04-16T12:00:00.000Z'),
            },
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/tenants/acme/alerts',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual([
            {
                id: 'alert-1',
                severity: 'critical',
                message: 'Deployment blocked',
                timestamp: '2026-04-16T12:00:00.000Z',
            },
        ]);
    });

    it('should map workflow metadata into workflow responses', async () => {
        mockPrisma.workflow.findMany.mockResolvedValue([
            {
                id: 'wf-1',
                name: 'Deploy',
                metadata: { description: 'Production deploy' },
                configuration: { schedule: '0 * * * *' },
                status: 'ACTIVE',
                createdAt: new Date('2026-04-16T10:00:00.000Z'),
                updatedAt: new Date('2026-04-16T11:00:00.000Z'),
            },
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/tenants/acme/workflows',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual([
            {
                id: 'wf-1',
                name: 'Deploy',
                description: 'Production deploy',
                enabled: true,
                schedule: '0 * * * *',
                createdAt: '2026-04-16T10:00:00.000Z',
                updatedAt: '2026-04-16T11:00:00.000Z',
            },
        ]);
    });
});