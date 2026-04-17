import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import normsRoutes from '../norms.js';

describe('Norms Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        fastify = Fastify({ logger: false });
        await fastify.register(normsRoutes, { prefix: '/api/v1/norms' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should filter norms by category', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/norms?category=security',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toMatchObject({
            success: true,
            count: 2,
        });
        expect(response.json().data).toEqual([
            expect.objectContaining({ id: 'security-mfa' }),
            expect.objectContaining({ id: 'security-data-encryption' }),
        ]);
    });

    it('should return a single norm by id', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/norms/security-mfa',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toMatchObject({
            success: true,
            data: expect.objectContaining({
                id: 'security-mfa',
                name: 'Multi-Factor Authentication',
            }),
        });
    });

    it('should return 404 for unknown norm ids', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/norms/missing-norm',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toMatchObject({
            success: false,
            error: 'Norm not found',
        });
    });
});