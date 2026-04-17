import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { observeRoutes } from '../observe.js';

vi.mock('../../db/client.js', () => ({
    prisma: {
        alert: {
            findMany: vi.fn(),
            count: vi.fn(),
            update: vi.fn(),
        },
        logs: {
            findMany: vi.fn(),
        },
    },
}));

describe('Observe Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        fastify = Fastify({ logger: false });
        await observeRoutes(fastify);
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should filter metrics by tenant and category', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/observe/metrics?tenantId=acme-payments-id&category=Velocity',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json().pagination.total).toBe(2);
        expect(response.json().data).toEqual(
            expect.arrayContaining([
                expect.objectContaining({ id: 'deployment-freq', category: 'Velocity' }),
                expect.objectContaining({ id: 'lead-time', category: 'Velocity' }),
            ])
        );
    });

    it('should return 404 for unknown metrics', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/observe/metrics/missing-metric',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({ error: 'Metric not found' });
    });

    it('should filter ML models by degraded status', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/observe/ml/models?status=degraded',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            data: [expect.objectContaining({ id: 'churn-prediction-v1', status: 'degraded' })],
            pagination: {
                page: 1,
                pageSize: 1,
                total: 1,
            },
        });
    });
});