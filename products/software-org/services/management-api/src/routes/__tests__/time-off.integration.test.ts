import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import timeOffRoutes from '../time-off.js';

const { mockPrisma } = vi.hoisted(() => ({
    mockPrisma: {
        timeOffRequest: {
            findMany: vi.fn(),
            count: vi.fn(),
            create: vi.fn(),
            findUnique: vi.fn(),
            update: vi.fn(),
        },
    },
}));

vi.mock('../../db/client.js', () => ({
    prisma: mockPrisma,
}));

describe('Time Off Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(timeOffRoutes, { prefix: '/api/v1' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should list time-off requests with pagination metadata', async () => {
        mockPrisma.timeOffRequest.findMany.mockResolvedValue([
            {
                id: 'req-1',
                userId: 'user-1',
                status: 'PENDING',
                type: 'VACATION',
                startDate: new Date('2026-04-20T00:00:00.000Z'),
                endDate: new Date('2026-04-22T00:00:00.000Z'),
            },
        ]);
        mockPrisma.timeOffRequest.count.mockResolvedValue(1);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/time-off?userId=user-1&limit=10&offset=5',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            data: [
                {
                    id: 'req-1',
                    userId: 'user-1',
                    status: 'PENDING',
                    type: 'VACATION',
                    startDate: '2026-04-20T00:00:00.000Z',
                    endDate: '2026-04-22T00:00:00.000Z',
                },
            ],
            total: 1,
            limit: 10,
            offset: 5,
        });
    });

    it('should reject overlapping time-off requests', async () => {
        mockPrisma.timeOffRequest.findMany.mockResolvedValue([
            {
                id: 'req-existing',
                startDate: new Date('2026-04-20T00:00:00.000Z'),
                endDate: new Date('2026-04-22T00:00:00.000Z'),
                status: 'APPROVED',
            },
        ]);

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/time-off',
            payload: {
                userId: 'user-1',
                type: 'VACATION',
                startDate: '2026-04-21',
                endDate: '2026-04-23',
            },
        });

        expect(response.statusCode).toBe(409);
        expect(response.json()).toEqual({
            error: 'Time-off request conflicts with existing requests',
            conflicts: [
                {
                    id: 'req-existing',
                    startDate: '2026-04-20T00:00:00.000Z',
                    endDate: '2026-04-22T00:00:00.000Z',
                    status: 'APPROVED',
                },
            ],
        });
    });

    it('should reject cancellation when a request is already cancelled', async () => {
        mockPrisma.timeOffRequest.findUnique.mockResolvedValue({
            id: 'req-1',
            status: 'CANCELLED',
        });

        const response = await fastify.inject({
            method: 'DELETE',
            url: '/api/v1/time-off/req-1',
        });

        expect(response.statusCode).toBe(400);
        expect(response.json()).toEqual({ error: 'Request already cancelled' });
        expect(mockPrisma.timeOffRequest.update).not.toHaveBeenCalled();
    });
});