import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import adminRoutes from '../admin.js';

const { mockPrisma } = vi.hoisted(() => ({
    mockPrisma: {
        tenant: {
            findMany: vi.fn(),
            count: vi.fn(),
            findUnique: vi.fn(),
            create: vi.fn(),
        },
        auditEvent: {
            create: vi.fn(),
        },
    },
}));

vi.mock('../../db/client.js', () => ({
    prisma: mockPrisma,
}));

describe('Admin Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(adminRoutes, { prefix: '/api/v1' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should paginate tenant listings', async () => {
        mockPrisma.tenant.findMany.mockResolvedValue([
            {
                id: 'tenant-1',
                key: 'acme',
                name: 'Acme',
                displayName: 'Acme Corp',
                description: 'Primary tenant',
                status: 'active',
                plan: 'enterprise',
                createdAt: new Date('2026-04-16T10:00:00.000Z'),
                updatedAt: new Date('2026-04-16T11:00:00.000Z'),
                _count: { environments: 2 },
            },
        ]);
        mockPrisma.tenant.count.mockResolvedValue(1);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/admin/tenants?page=2&limit=5&status=active',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            data: [
                {
                    id: 'tenant-1',
                    key: 'acme',
                    name: 'Acme',
                    displayName: 'Acme Corp',
                    description: 'Primary tenant',
                    status: 'active',
                    plan: 'enterprise',
                    createdAt: '2026-04-16T10:00:00.000Z',
                    updatedAt: '2026-04-16T11:00:00.000Z',
                    environmentCount: 2,
                },
            ],
            total: 1,
            page: '2',
            limit: '5',
        });
    });

    it('should reject duplicate tenant keys', async () => {
        mockPrisma.tenant.findUnique.mockResolvedValue({ id: 'tenant-1' });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/admin/tenants',
            payload: {
                key: 'acme',
                name: 'Acme',
                displayName: 'Acme Corp',
            },
        });

        expect(response.statusCode).toBe(409);
        expect(response.json()).toEqual({ error: 'Tenant with this key already exists' });
        expect(mockPrisma.auditEvent.create).not.toHaveBeenCalled();
    });

    it('should create tenants and emit an audit event', async () => {
        mockPrisma.tenant.findUnique.mockResolvedValue(null);
        mockPrisma.tenant.create.mockResolvedValue({
            id: 'tenant-2',
            key: 'globex',
            name: 'Globex',
            displayName: 'Globex Corp',
            description: 'Secondary tenant',
            status: 'active',
            plan: 'standard',
            createdAt: new Date('2026-04-16T12:00:00.000Z'),
            updatedAt: new Date('2026-04-16T12:00:00.000Z'),
        });
        mockPrisma.auditEvent.create.mockResolvedValue({ id: 'audit-1' });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/admin/tenants',
            payload: {
                key: 'globex',
                name: 'Globex',
                displayName: 'Globex Corp',
                description: 'Secondary tenant',
            },
        });

        expect(response.statusCode).toBe(201);
        expect(response.json()).toEqual({
            id: 'tenant-2',
            key: 'globex',
            name: 'Globex',
            displayName: 'Globex Corp',
            description: 'Secondary tenant',
            status: 'active',
            plan: 'standard',
            createdAt: '2026-04-16T12:00:00.000Z',
            updatedAt: '2026-04-16T12:00:00.000Z',
        });
        expect(mockPrisma.auditEvent.create).toHaveBeenCalledTimes(1);
    });
});