import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import auditRoutes from '../audit.js';

const { mockPrisma } = vi.hoisted(() => ({
    mockPrisma: {
        auditEvent: {
            create: vi.fn(),
            findMany: vi.fn(),
        },
    },
}));

vi.mock('../../db/client.js', () => ({
    prisma: mockPrisma,
}));

describe('Audit Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(auditRoutes, { prefix: '/api/v1/audit' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should reject incomplete decision payloads', async () => {
        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/audit/decisions',
            payload: {
                entityType: 'workflow',
                entityId: 'wf-1',
            },
        });

        expect(response.statusCode).toBe(400);
        expect(response.json()).toEqual({
            error: 'entityType, entityId, and decision are required',
        });
        expect(mockPrisma.auditEvent.create).not.toHaveBeenCalled();
    });

    it('should persist audit decisions', async () => {
        mockPrisma.auditEvent.create.mockResolvedValue({ id: 'audit-1' });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/audit/decisions',
            payload: {
                entityType: 'workflow',
                entityId: 'wf-1',
                decision: 'approve',
                reason: 'Ready for deploy',
                userId: 'user-42',
            },
        });

        expect(response.statusCode).toBe(201);
        expect(response.json()).toEqual({
            id: 'audit-1',
            success: true,
            message: 'Decision recorded successfully',
        });
    });

    it('should return mapped trail records with computed rates', async () => {
        mockPrisma.auditEvent.findMany.mockResolvedValue([
            {
                id: 'audit-3',
                entityType: 'workflow',
                entityId: 'wf-1',
                decision: 'reject',
                reason: 'Missing approvals',
                actorUserId: 'user-3',
                actor: { name: 'Casey' },
                timestamp: new Date('2026-04-16T12:00:00.000Z'),
            },
            {
                id: 'audit-2',
                entityType: 'workflow',
                entityId: 'wf-1',
                decision: 'defer',
                reason: 'Need review',
                actorUserId: 'user-2',
                actor: { name: 'Blair' },
                timestamp: new Date('2026-04-16T11:00:00.000Z'),
            },
            {
                id: 'audit-1',
                entityType: 'workflow',
                entityId: 'wf-1',
                decision: 'approve',
                reason: 'Looks good',
                actorUserId: 'user-1',
                actor: { name: 'Alex' },
                timestamp: new Date('2026-04-16T10:00:00.000Z'),
            },
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/audit/trails/workflow/wf-1',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            total: 3,
            records: [
                {
                    id: 'audit-3',
                    entityType: 'workflow',
                    entityId: 'wf-1',
                    decision: 'reject',
                    reason: 'Missing approvals',
                    userId: 'user-3',
                    userName: 'Casey',
                    timestamp: '2026-04-16T12:00:00.000Z',
                },
                {
                    id: 'audit-2',
                    entityType: 'workflow',
                    entityId: 'wf-1',
                    decision: 'defer',
                    reason: 'Need review',
                    userId: 'user-2',
                    userName: 'Blair',
                    timestamp: '2026-04-16T11:00:00.000Z',
                },
                {
                    id: 'audit-1',
                    entityType: 'workflow',
                    entityId: 'wf-1',
                    decision: 'approve',
                    reason: 'Looks good',
                    userId: 'user-1',
                    userName: 'Alex',
                    timestamp: '2026-04-16T10:00:00.000Z',
                },
            ],
            avgApprovalRate: 33,
            avgDeferRate: 33,
            avgRejectionRate: 33,
        });
    });
});