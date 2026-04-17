import Fastify, { FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import metricsRoutes from '../metrics.js';

const { mockFindMany } = vi.hoisted(() => ({
    mockFindMany: vi.fn(),
}));

vi.mock('../../db/client.js', () => ({
    prisma: {
        kpi: {
            findMany: mockFindMany,
        },
    },
}));

describe('Metrics Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        fastify = Fastify({ logger: false });
        await fastify.register(metricsRoutes, { prefix: '/api/v1/metrics' });
        vi.clearAllMocks();
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should return aggregated metrics with the default time range', async () => {
        mockFindMany.mockResolvedValue([
            { value: 10.4 },
            { value: 4.2 },
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/metrics',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toMatchObject({
            value: 15,
            timeRange: '7d',
        });
        expect(mockFindMany).toHaveBeenCalledTimes(1);
    });

    it('should preserve the requested time range', async () => {
        mockFindMany.mockResolvedValue([{ value: 2.6 }]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/metrics?timeRange=30d',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toMatchObject({
            value: 3,
            timeRange: '30d',
        });
    });
});