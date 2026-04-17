import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import modelRoutes from '../models.js';

const { mockPrisma } = vi.hoisted(() => ({
    mockPrisma: {
        mlModel: {
            findMany: vi.fn(),
            findFirst: vi.fn(),
        },
        mlModelVersion: {
            findMany: vi.fn(),
        },
        mlModelMetric: {
            findMany: vi.fn(),
        },
        modelFeatureImportance: {
            findMany: vi.fn(),
        },
    },
}));

vi.mock('../../db/client.js', () => ({
    prisma: mockPrisma,
}));

describe('Models Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(modelRoutes, { prefix: '/api/v1/models' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should require both model ids for comparisons', async () => {
        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/models/compare?modelId1=model-a',
        });

        expect(response.statusCode).toBe(400);
        expect(response.json()).toEqual({ error: 'modelId1 and modelId2 are required' });
    });

    it('should return 404 when metrics are requested without a current version', async () => {
        mockPrisma.mlModel.findFirst.mockResolvedValue({
            id: 'model-1',
            key: 'fraud-model',
            versions: [],
        });

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/models/fraud-model/metrics',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({ error: 'Model or current version not found' });
    });

    it('should map feature importance entries for the current version', async () => {
        mockPrisma.mlModel.findFirst.mockResolvedValue({
            id: 'model-1',
            key: 'fraud-model',
            versions: [{ id: 'version-1', status: 'current' }],
        });
        mockPrisma.modelFeatureImportance.findMany.mockResolvedValue([
            { featureName: 'amount', importance: 0.72 },
            { featureName: 'country', importance: 0.18 },
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/models/fraud-model/feature-importance',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual([
            { name: 'amount', importance: 0.72 },
            { name: 'country', importance: 0.18 },
        ]);
    });
});