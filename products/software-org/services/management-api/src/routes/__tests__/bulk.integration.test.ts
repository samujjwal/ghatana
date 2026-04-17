import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import bulkRoutes from '../bulk.js';

describe('Bulk Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        fastify = Fastify({ logger: false });
        await fastify.register(bulkRoutes, { prefix: '/api/v1/bulk' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should reject bulk actions without ids', async () => {
        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/bulk/actions/archive',
            payload: { ids: [] },
        });

        expect(response.statusCode).toBe(400);
        expect(response.json()).toEqual({ error: 'ids array is required' });
    });

    it('should execute bulk actions across all provided ids', async () => {
        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/bulk/actions/archive',
            payload: { ids: ['wf-1', 'wf-2'] },
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            success: true,
            action: 'archive',
            itemsProcessed: 2,
            results: [
                { id: 'wf-1', status: 'success' },
                { id: 'wf-2', status: 'success' },
            ],
        });
    });
});