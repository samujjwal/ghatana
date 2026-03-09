/**
 * Growth Plans API Routes
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for employee personal growth plans
 * @doc.layer product
 * @doc.pattern Router
 *
 * Endpoints:
 * - GET /api/v1/growth-plans - List growth plans for a user
 * - POST /api/v1/growth-plans - Create a growth plan
 * - GET /api/v1/growth-plans/:id - Get a growth plan
 * - PUT /api/v1/growth-plans/:id - Update a growth plan
 * - POST /api/v1/growth-plans/:id/complete - Complete a growth plan
 */

import { FastifyInstance } from 'fastify';
import { prisma } from '../db/client.js';
import type { GrowthPlan, Prisma } from '../../generated/prisma-client/index.js';

interface ListGrowthPlansQuery {
    userId: string;
    status?: 'active' | 'completed' | 'archived';
    period?: string;
    limit?: number;
    offset?: number;
}

interface CreateGrowthPlanBody {
    userId: string;
    title: string;
    description?: string;
    period: string;
    goals?: unknown;
    skills?: unknown;
    resources?: unknown;
    progress?: number;
}

interface UpdateGrowthPlanBody {
    title?: string;
    description?: string;
    period?: string;
    status?: 'active' | 'completed' | 'archived';
    goals?: unknown;
    skills?: unknown;
    resources?: unknown;
    progress?: number;
}

interface GrowthPlanResponse {
    id: string;
    userId: string;
    title: string;
    description: string | null;
    period: string;
    status: string;
    goals: unknown;
    skills: unknown;
    resources: unknown;
    progress: number;
    createdAt: string;
    updatedAt: string;
    completedAt: string | null;
}

const toGrowthPlanResponse = (plan: GrowthPlan): GrowthPlanResponse => ({
    id: plan.id,
    userId: plan.userId,
    title: plan.title,
    description: plan.description ?? null,
    period: plan.period,
    status: plan.status,
    goals: plan.goals as unknown,
    skills: plan.skills as unknown,
    resources: plan.resources as unknown,
    progress: plan.progress,
    createdAt: plan.createdAt.toISOString(),
    updatedAt: plan.updatedAt.toISOString(),
    completedAt: plan.completedAt?.toISOString() ?? null,
});

export default async function growthPlanRoutes(fastify: FastifyInstance): Promise<void> {
    /**
     * GET /api/v1/growth-plans
     */
    fastify.get<{ Querystring: ListGrowthPlansQuery }>(
        '/growth-plans',
        {
            schema: {
                querystring: {
                    type: 'object',
                    required: ['userId'],
                    properties: {
                        userId: { type: 'string' },
                        status: { type: 'string', enum: ['active', 'completed', 'archived'] },
                        period: { type: 'string' },
                        limit: { type: 'number', default: 50 },
                        offset: { type: 'number', default: 0 },
                    },
                },
            },
        },
        async (request) => {
            const { userId, status, period, limit = 50, offset = 0 } = request.query;

            const where: Prisma.GrowthPlanWhereInput = { userId };
            if (status) where.status = status;
            if (period) where.period = period;

            const [plans, total] = await Promise.all([
                prisma.growthPlan.findMany({
                    where,
                    take: limit,
                    skip: offset,
                    orderBy: { updatedAt: 'desc' },
                }),
                prisma.growthPlan.count({ where }),
            ]);

            return {
                data: plans.map(toGrowthPlanResponse),
                total,
                limit,
                offset,
            };
        }
    );

    /**
     * POST /api/v1/growth-plans
     */
    fastify.post<{ Body: CreateGrowthPlanBody }>(
        '/growth-plans',
        {
            schema: {
                body: {
                    type: 'object',
                    required: ['userId', 'title', 'period'],
                    properties: {
                        userId: { type: 'string' },
                        title: { type: 'string' },
                        description: { type: 'string' },
                        period: { type: 'string' },
                        goals: { type: 'array' },
                        skills: { type: 'array' },
                        resources: { type: 'array' },
                        progress: { type: 'number', minimum: 0, maximum: 100 },
                    },
                },
            },
        },
        async (request, reply) => {
            const { userId, title, description, period, goals, skills, resources, progress } = request.body;

            const plan = await prisma.growthPlan.create({
                data: {
                    userId,
                    title,
                    description,
                    period,
                    goals: (goals ?? []) as Prisma.InputJsonValue,
                    skills: (skills ?? []) as Prisma.InputJsonValue,
                    resources: (resources ?? []) as Prisma.InputJsonValue,
                    progress: progress ?? 0,
                    status: 'active',
                },
            });

            return reply.status(201).send(toGrowthPlanResponse(plan));
        }
    );

    /**
     * GET /api/v1/growth-plans/:id
     */
    fastify.get<{ Params: { id: string } }>(
        '/growth-plans/:id',
        {
            schema: {
                params: {
                    type: 'object',
                    required: ['id'],
                    properties: { id: { type: 'string' } },
                },
            },
        },
        async (request, reply) => {
            const plan = await prisma.growthPlan.findUnique({ where: { id: request.params.id } });
            if (!plan) return reply.status(404).send({ error: 'Growth plan not found' });
            return toGrowthPlanResponse(plan);
        }
    );

    /**
     * PUT /api/v1/growth-plans/:id
     */
    fastify.put<{ Params: { id: string }; Body: UpdateGrowthPlanBody }>(
        '/growth-plans/:id',
        {
            schema: {
                params: {
                    type: 'object',
                    required: ['id'],
                    properties: { id: { type: 'string' } },
                },
                body: {
                    type: 'object',
                    properties: {
                        title: { type: 'string' },
                        description: { type: 'string' },
                        period: { type: 'string' },
                        status: { type: 'string', enum: ['active', 'completed', 'archived'] },
                        goals: { type: 'array' },
                        skills: { type: 'array' },
                        resources: { type: 'array' },
                        progress: { type: 'number', minimum: 0, maximum: 100 },
                    },
                },
            },
        },
        async (request, reply) => {
            const { id } = request.params;
            const updates = request.body;

            const existing = await prisma.growthPlan.findUnique({ where: { id } });
            if (!existing) return reply.status(404).send({ error: 'Growth plan not found' });

            const data: Prisma.GrowthPlanUpdateInput = {};
            if (updates.title !== undefined) data.title = updates.title;
            if (updates.description !== undefined) data.description = updates.description;
            if (updates.period !== undefined) data.period = updates.period;
            if (updates.status !== undefined) data.status = updates.status;
            if (updates.progress !== undefined) data.progress = updates.progress;
            if (updates.goals !== undefined) data.goals = updates.goals as Prisma.InputJsonValue;
            if (updates.skills !== undefined) data.skills = updates.skills as Prisma.InputJsonValue;
            if (updates.resources !== undefined) data.resources = updates.resources as Prisma.InputJsonValue;

            const plan = await prisma.growthPlan.update({ where: { id }, data });
            return toGrowthPlanResponse(plan);
        }
    );

    /**
     * POST /api/v1/growth-plans/:id/complete
     */
    fastify.post<{ Params: { id: string } }>(
        '/growth-plans/:id/complete',
        {
            schema: {
                params: {
                    type: 'object',
                    required: ['id'],
                    properties: { id: { type: 'string' } },
                },
            },
        },
        async (request, reply) => {
            const { id } = request.params;

            const existing = await prisma.growthPlan.findUnique({ where: { id } });
            if (!existing) return reply.status(404).send({ error: 'Growth plan not found' });

            const plan = await prisma.growthPlan.update({
                where: { id },
                data: {
                    status: 'completed',
                    progress: 100,
                    completedAt: new Date(),
                },
            });

            return toGrowthPlanResponse(plan);
        }
    );
}
