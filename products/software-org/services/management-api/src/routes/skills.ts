import { FastifyInstance } from 'fastify';
import { skillsService } from '../services/skills.service';

export async function skillsRoutes(fastify: FastifyInstance) {
    fastify.get('/skills', async (request, reply) => {
        const skills = await skillsService.getSkills();
        return skills;
    });

    fastify.get<{ Params: { userId: string } }>(
        '/users/:userId/skills',
        {
            schema: {
                params: {
                    type: 'object',
                    required: ['userId'],
                    properties: {
                        userId: { type: 'string' },
                    },
                },
            },
        },
        async (request) => {
            return skillsService.getUserSkills(request.params.userId);
        }
    );

    fastify.put<{
        Params: { userId: string; skillId: string };
        Body: { proficiency: number; interest: string };
    }>(
        '/users/:userId/skills/:skillId',
        {
            schema: {
                params: {
                    type: 'object',
                    required: ['userId', 'skillId'],
                    properties: {
                        userId: { type: 'string' },
                        skillId: { type: 'string' },
                    },
                },
                body: {
                    type: 'object',
                    required: ['proficiency', 'interest'],
                    properties: {
                        proficiency: { type: 'number', minimum: 0, maximum: 5 },
                        interest: { type: 'string' },
                    },
                },
            },
        },
        async (request) => {
            const { userId, skillId } = request.params;
            return skillsService.updateUserSkill(userId, skillId, request.body);
        }
    );

    fastify.get<{ Querystring: { teamId?: string } }>(
        '/gaps',
        {
            schema: {
                querystring: {
                    type: 'object',
                    properties: {
                        teamId: { type: 'string' },
                    },
                },
            },
        },
        async (request) => {
            return skillsService.getSkillGaps(request.query.teamId);
        }
    );

    fastify.get<{ Params: { userId: string } }>(
        '/users/:userId/plans',
        {
            schema: {
                params: {
                    type: 'object',
                    required: ['userId'],
                    properties: {
                        userId: { type: 'string' },
                    },
                },
            },
        },
        async (request) => {
            return skillsService.getDevelopmentPlans(request.params.userId);
        }
    );

    fastify.post<{ Body: { userId: string; skillId: string; type: string; dueDate?: string } }>(
        '/plans',
        {
            schema: {
                body: {
                    type: 'object',
                    required: ['userId', 'skillId', 'type'],
                    properties: {
                        userId: { type: 'string' },
                        skillId: { type: 'string' },
                        type: { type: 'string' },
                        dueDate: { type: 'string' },
                    },
                },
            },
        },
        async (request) => {
            const { dueDate, ...rest } = request.body;
            return skillsService.createDevelopmentPlan({
                ...rest,
                dueDate: dueDate ? new Date(dueDate) : undefined,
            });
        }
    );

    fastify.put<{ Params: { id: string }; Body: { status?: string; progress?: number; dueDate?: string } }>(
        '/plans/:id',
        {
            schema: {
                params: {
                    type: 'object',
                    required: ['id'],
                    properties: {
                        id: { type: 'string' },
                    },
                },
                body: {
                    type: 'object',
                    properties: {
                        status: { type: 'string' },
                        progress: { type: 'number', minimum: 0, maximum: 100 },
                        dueDate: { type: 'string' },
                    },
                },
            },
        },
        async (request) => {
            const { id } = request.params;
            const { dueDate, ...rest } = request.body;
            return skillsService.updateDevelopmentPlan(id, {
                ...rest,
                ...(dueDate ? { dueDate: new Date(dueDate) } : {}),
            });
        }
    );
}
