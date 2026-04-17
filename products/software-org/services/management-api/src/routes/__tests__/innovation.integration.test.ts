import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { innovationRoutes } from '../innovation.js';

const { mockInnovationService } = vi.hoisted(() => ({
    mockInnovationService: {
        getIdeas: vi.fn(),
        createIdea: vi.fn(),
        voteIdea: vi.fn(),
        getExperiments: vi.fn(),
        createExperiment: vi.fn(),
        updateExperiment: vi.fn(),
    },
}));

vi.mock('../../services/innovation.service', () => ({
    innovationService: mockInnovationService,
}));

describe('Innovation Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(innovationRoutes, { prefix: '/api/v1' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should forward idea status filters to the service', async () => {
        mockInnovationService.getIdeas.mockResolvedValue([{ id: 'idea-1', status: 'submitted' }]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/ideas?status=submitted',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual([{ id: 'idea-1', status: 'submitted' }]);
        expect(mockInnovationService.getIdeas).toHaveBeenCalledWith('submitted');
    });

    it('should convert experiment date strings before creation', async () => {
        mockInnovationService.createExperiment.mockResolvedValue({ id: 'exp-1', title: 'Chaos Test' });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/experiments',
            payload: {
                ideaId: 'idea-1',
                title: 'Chaos Test',
                startDate: '2026-04-20T00:00:00.000Z',
                endDate: '2026-04-27T00:00:00.000Z',
            },
        });

        expect(response.statusCode).toBe(200);
        expect(mockInnovationService.createExperiment).toHaveBeenCalledWith({
            ideaId: 'idea-1',
            title: 'Chaos Test',
            startDate: new Date('2026-04-20T00:00:00.000Z'),
            endDate: new Date('2026-04-27T00:00:00.000Z'),
        });
        expect(response.json()).toEqual({ id: 'exp-1', title: 'Chaos Test' });
    });

    it('should forward experiment updates with converted dates', async () => {
        mockInnovationService.updateExperiment.mockResolvedValue({ id: 'exp-1', status: 'running' });

        const response = await fastify.inject({
            method: 'PUT',
            url: '/api/v1/experiments/exp-1',
            payload: {
                progress: 70,
                endDate: '2026-04-30T00:00:00.000Z',
            },
        });

        expect(response.statusCode).toBe(200);
        expect(mockInnovationService.updateExperiment).toHaveBeenCalledWith('exp-1', {
            progress: 70,
            endDate: new Date('2026-04-30T00:00:00.000Z'),
        });
        expect(response.json()).toEqual({ id: 'exp-1', status: 'running' });
    });
});