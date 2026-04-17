import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import devsecopsRoutes from '../devsecops.js';

vi.mock('../../db/client.js', () => ({
    prisma: {},
}));

describe('DevSecOps Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        fastify = Fastify({ logger: false });
        await fastify.register(devsecopsRoutes, { prefix: '/api/v1' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should return an empty item list with the requested filters echoed back', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/devsecops/items?stage=secure&tenantId=tenant-7&status=blocked&priority=critical',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            success: true,
            data: [],
            meta: {
                total: 0,
                filters: {
                    stage: 'secure',
                    tenantId: 'tenant-7',
                    status: 'blocked',
                    priority: 'critical',
                },
            },
        });
    });

    it('should return default stage health payloads', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/devsecops/stage-health/build?tenantId=tenant-7',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            success: true,
            data: expect.objectContaining({
                stage: 'build',
                status: 'on-track',
                itemsTotal: 0,
                itemsCompleted: 0,
                itemsBlocked: 0,
                itemsInProgress: 0,
                criticalIssues: 0,
            }),
        });
    });
});