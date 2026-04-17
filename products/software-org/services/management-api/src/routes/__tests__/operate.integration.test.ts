import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { operateRoutes } from '../operate.js';

describe('Operate Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        fastify = Fastify({ logger: false });
        await operateRoutes(fastify);
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should require tenantId for dashboard stats', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/operate/dashboard/stats',
        });

        expect(response.statusCode).toBe(400);
        expect(response.json()).toEqual({ error: 'tenantId is required' });
    });

    it('should return filtered queue items', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/operate/queue?tenantId=tenant-a&type=approval&priority=high',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json().pagination.total).toBe(2);
        expect(response.json().data).toEqual(
            expect.arrayContaining([
                expect.objectContaining({ id: 'queue-001', type: 'approval', priority: 'high' }),
                expect.objectContaining({ id: 'queue-005', type: 'approval', priority: 'high' }),
            ])
        );
    });

    it('should return 404 for unknown incidents within a tenant', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/operate/incidents/INC-999?tenantId=tenant-a',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({ error: 'Incident not found' });
    });
});