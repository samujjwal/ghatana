import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { skillsRoutes } from '../skills.js';

const { mockSkillsService } = vi.hoisted(() => ({
    mockSkillsService: {
        getSkills: vi.fn(),
        getUserSkills: vi.fn(),
        updateUserSkill: vi.fn(),
        getSkillGaps: vi.fn(),
        getDevelopmentPlans: vi.fn(),
        createDevelopmentPlan: vi.fn(),
        updateDevelopmentPlan: vi.fn(),
    },
}));

vi.mock('../../services/skills.service', () => ({
    skillsService: mockSkillsService,
}));

describe('Skills Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(skillsRoutes, { prefix: '/api/v1' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should list skills from the service layer', async () => {
        mockSkillsService.getSkills.mockResolvedValue([
            { id: 'skill-1', name: 'Architecture' },
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/skills',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual([{ id: 'skill-1', name: 'Architecture' }]);
    });

    it('should reject invalid proficiency updates at the schema boundary', async () => {
        const response = await fastify.inject({
            method: 'PUT',
            url: '/api/v1/users/user-1/skills/skill-1',
            payload: {
                proficiency: 9,
                interest: 'high',
            },
        });

        expect(response.statusCode).toBe(400);
        expect(mockSkillsService.updateUserSkill).not.toHaveBeenCalled();
    });

    it('should convert plan due dates before calling the service', async () => {
        mockSkillsService.createDevelopmentPlan.mockResolvedValue({
            id: 'plan-1',
            status: 'planned',
        });

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/plans',
            payload: {
                userId: 'user-1',
                skillId: 'skill-1',
                type: 'course',
                dueDate: '2026-05-01T00:00:00.000Z',
            },
        });

        expect(response.statusCode).toBe(200);
        expect(mockSkillsService.createDevelopmentPlan).toHaveBeenCalledWith({
            userId: 'user-1',
            skillId: 'skill-1',
            type: 'course',
            dueDate: new Date('2026-05-01T00:00:00.000Z'),
        });
        expect(response.json()).toEqual({ id: 'plan-1', status: 'planned' });
    });
});