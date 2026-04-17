import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import kpiRoutes from '../kpis.js';

const { mockPrisma } = vi.hoisted(() => ({
    mockPrisma: {
        kpi: {
            findMany: vi.fn(),
            findFirst: vi.fn(),
        },
        department: {
            findMany: vi.fn(),
        },
        kpiNarrative: {
            findMany: vi.fn(),
        },
        kpiDataPoint: {
            findMany: vi.fn(),
        },
    },
}));

vi.mock('../../db/client.js', () => ({
    prisma: mockPrisma,
}));

describe('KPI Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(kpiRoutes, { prefix: '/api/v1/kpis' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should list KPIs in API format', async () => {
        mockPrisma.kpi.findMany.mockResolvedValue([
            {
                id: 'db-1',
                key: 'deploy-frequency',
                name: 'Deploy Frequency',
                value: 12,
                unit: 'per-week',
                trend: 8,
                target: 15,
                lastUpdated: new Date('2026-04-16T12:00:00.000Z'),
            },
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/kpis',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual([
            {
                id: 'deploy-frequency',
                name: 'Deploy Frequency',
                value: 12,
                unit: 'per-week',
                trend: 8,
                target: 15,
                lastUpdated: '2026-04-16T12:00:00.000Z',
            },
        ]);
    });

    it('should return 404 for unknown KPIs', async () => {
        mockPrisma.kpi.findFirst.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/kpis/missing-kpi',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({ error: 'KPI not found' });
    });

    it('should return filtered KPI narratives', async () => {
        mockPrisma.kpiNarrative.findMany.mockResolvedValue([
            {
                insight: 'Velocity improved after build queue tuning',
                confidence: 0.92,
            },
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/kpis/narratives?timeRange=30d',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual([
            {
                insight: 'Velocity improved after build queue tuning',
                confidence: 0.92,
            },
        ]);
        expect(mockPrisma.kpiNarrative.findMany).toHaveBeenCalledWith(expect.objectContaining({
            where: { timeRange: '30d' },
        }));
    });
});