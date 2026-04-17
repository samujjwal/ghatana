import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import agentActionsRoutes from '../agent-actions.js';

describe('Agent Actions Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        fastify = Fastify({ logger: false });
        await fastify.register(agentActionsRoutes, { prefix: '/api/v1/agents' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should list pending agent actions', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/agents/actions',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toHaveLength(3);
        expect(response.json()[0]).toMatchObject({
            id: 'action-1',
            status: 'pending',
        });
    });

    it('should return 404 for unknown actions', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/agents/actions/missing-action',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({ error: 'Action not found' });
    });

    it('should defer actions with a replacement deadline', async () => {
        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/agents/actions/action-1/defer',
            payload: {
                deferredBy: 'user-42',
                reason: 'Waiting on CAB approval',
                newDeadline: '2026-04-17T12:00:00.000Z',
            },
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toMatchObject({
            status: 'deferred',
            actionId: 'action-1',
            deferredBy: 'user-42',
            reason: 'Waiting on CAB approval',
            newDeadline: '2026-04-17T12:00:00.000Z',
        });
    });
});