import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import growthPlanRoutes from '../growth-plans.js';

const { mockPrisma } = vi.hoisted(() => ({
    mockPrisma: {
        growthPlan: {
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

describe('Growth Plans Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(growthPlanRoutes, { prefix: '/api/v1' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should require userId when listing growth plans', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/growth-plans',
        });

        expect(response.statusCode).toBe(400);
    });

    it('should list growth plans with pagination metadata', async () => {
        mockPrisma.growthPlan.findMany.mockResolvedValue([
            {
                id: 'plan-1',
                userId: 'user-1',
                title: 'Staff Engineer Growth',
                description: 'Focus on platform architecture',
                period: '2026-Q2',
                status: 'active',
                goals: ['ship platform RFC'],
                skills: ['architecture'],
                resources: ['mentoring'],
                progress: 35,
                createdAt: new Date('2026-04-01T00:00:00.000Z'),
                updatedAt: new Date('2026-04-02T00:00:00.000Z'),
                completedAt: null,
            },
        ]);
        mockPrisma.growthPlan.count.mockResolvedValue(1);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/growth-plans?userId=user-1&status=active&period=2026-Q2&limit=5&offset=0',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            data: [
                {
                    id: 'plan-1',
                    userId: 'user-1',
                    title: 'Staff Engineer Growth',
                    description: 'Focus on platform architecture',
                    period: '2026-Q2',
                    status: 'active',
                    goals: ['ship platform RFC'],
                    skills: ['architecture'],
                    resources: ['mentoring'],
                    progress: 35,
                    createdAt: '2026-04-01T00:00:00.000Z',
                    updatedAt: '2026-04-02T00:00:00.000Z',
                    completedAt: null,
                },
            ],
            total: 1,
            limit: 5,
            offset: 0,
        });
    });

    it('should return 404 for unknown growth plans', async () => {
        mockPrisma.growthPlan.findUnique.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/growth-plans/missing-plan',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({ error: 'Growth plan not found' });
    });
});