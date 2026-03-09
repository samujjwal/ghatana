/**
 * Budget API Routes
 *
 * RESTful endpoints for budget planning and management.
 *
 * @package @ghatana/software-org-backend
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { prisma } from '../db/client.js';

interface BudgetQueryParams {
    year?: string;
    quarter?: string;
    departmentId?: string;
    status?: string;
}
interface BudgetCreateBody {
    year: number;
    quarter?: string;
    totalBudgetLimit: number;
    budgets: Array<{
        departmentId: string;
        departmentName: string;
        currentAllocated: number;
        currentSpent: number;
        proposedAllocated: number;
        categories: Record<string, number>;
        justification: string;
    }>;
    status?: string;
}

interface BudgetUpdateBody {
    id: string;
    totalBudgetLimit?: number;
    budgets?: Array<{
        departmentId: string;
        proposedAllocated: number;
        categories: Record<string, number>;
        justification: string;
    }>;
    status?: string;
}

/**
 * Register budget routes
 */
export default async function budgetRoutes(fastify: FastifyInstance): Promise<void> {
    /**
     * GET /api/v1/budgets
     * Get budget plans by year/quarter
     */
    fastify.get<{ Querystring: BudgetQueryParams }>(
        '/budgets',
        async (request: FastifyRequest<{ Querystring: BudgetQueryParams }>, reply: FastifyReply) => {
            const { year, quarter, departmentId, status } = request.query;

            fastify.log.debug({ year, quarter, departmentId, status }, 'Fetching budgets');

            try {
                const budgets = await prisma.budget.findMany({
                    where: {
                        ...(year && { year: parseInt(year) }),
                        ...(quarter && { quarter }),
                        ...(departmentId && { departmentId }),
                        ...(status && { status }),
                    },
                    include: {
                        department: true,
                    },
                    orderBy: {
                        createdAt: 'desc',
                    },
                });

                // Group by year/quarter into budget plans
                if (year) {
                    const budgetPlan = {
                        year: parseInt(year),
                        quarter,
                        budgets: budgets.map(b => ({
                            id: b.id,
                            departmentId: b.departmentId,
                            departmentName: b.department.name,
                            currentAllocated: b.allocated,
                            currentSpent: b.spent,
                            proposedAllocated: b.allocated, // Same as current for existing budgets
                            categories: b.categories as Record<string, number>,
                            justification: b.notes || '',
                            status: b.status,
                        })),
                        totalBudgetLimit: budgets.reduce((sum, b) => sum + b.allocated, 0),
                        status: budgets[0]?.status || 'draft',
                    };

                    return reply.send(budgetPlan);
                }

                return reply.send(budgets);
            } catch (error) {
                fastify.log.error(error, 'Error fetching budgets');
                return reply.status(500).send({ error: 'Failed to fetch budgets' });
            }
        }
    );

    /**
     * POST /api/v1/budgets
     * Create new budget plan
     */
    fastify.post<{ Body: BudgetCreateBody }>(
        '/budgets',
        async (request: FastifyRequest<{ Body: BudgetCreateBody }>, reply: FastifyReply) => {
            const { year, quarter, budgets, status } = request.body;

            fastify.log.debug({ year, quarter, budgetCount: budgets.length }, 'Creating budget plan');

            try {
                // Create budgets for each department
                const createdBudgets = await Promise.all(
                    budgets.map(b =>
                        prisma.budget.create({
                            data: {
                                departmentId: b.departmentId,
                                year,
                                quarter: quarter || null,
                                allocated: b.proposedAllocated,
                                spent: b.currentSpent,
                                forecasted: b.proposedAllocated,
                                categories: b.categories,
                                status: status || 'draft',
                                notes: b.justification,
                            },
                        })
                    )
                );

                return reply.status(201).send({
                    id: createdBudgets[0].id,
                    year,
                    quarter,
                    budgets: createdBudgets.map(b => ({
                        id: b.id,
                        departmentId: b.departmentId,
                        allocated: b.allocated,
                        spent: b.spent,
                        categories: b.categories,
                        status: b.status,
                    })),
                    message: 'Budget plan created successfully',
                });
            } catch (error) {
                fastify.log.error(error, 'Error creating budget plan');
                return reply.status(500).send({ error: 'Failed to create budget plan' });
            }
        }
    );

    /**
     * PUT /api/v1/budgets
     * Update existing budget plan
     */
    fastify.put<{ Body: BudgetUpdateBody }>(
        '/budgets',
        async (request: FastifyRequest<{ Body: BudgetUpdateBody }>, reply: FastifyReply) => {
            const { id, budgets, status } = request.body;

            fastify.log.debug({ id, budgetCount: budgets?.length }, 'Updating budget plan');

            try {
                if (!budgets) {
                    return reply.status(400).send({ error: 'Budgets array is required' });
                }

                // Update each department budget
                const updatedBudgets = await Promise.all(
                    budgets.map(b =>
                        prisma.budget.updateMany({
                            where: {
                                departmentId: b.departmentId,
                                // Match the original budget record
                            },
                            data: {
                                allocated: b.proposedAllocated,
                                forecasted: b.proposedAllocated,
                                categories: b.categories,
                                notes: b.justification,
                                ...(status && { status }),
                            },
                        })
                    )
                );

                return reply.send({
                    id,
                    updated: updatedBudgets.reduce((sum, u) => sum + u.count, 0),
                    message: 'Budget plan updated successfully',
                });
            } catch (error) {
                fastify.log.error(error, 'Error updating budget plan');
                return reply.status(500).send({ error: 'Failed to update budget plan' });
            }
        }
    );

    /**
     * DELETE /api/v1/budgets/:id
     * Delete budget plan (soft delete by setting status to archived)
     */
    fastify.delete<{ Params: { id: string } }>(
        '/budgets/:id',
        async (request: FastifyRequest<{ Params: { id: string } }>, reply: FastifyReply) => {
            const { id } = request.params;

            fastify.log.debug({ id }, 'Archiving budget');

            try {
                await prisma.budget.update({
                    where: { id },
                    data: { status: 'archived' },
                });

                return reply.send({ message: 'Budget archived successfully' });
            } catch (error) {
                fastify.log.error(error, 'Error archiving budget');
                return reply.status(500).send({ error: 'Failed to archive budget' });
            }
        }
    );

    /**
     * GET /api/v1/budgets/forecast
     * Generate budget forecast based on historical spending
     */
    fastify.get<{ Querystring: { departmentId?: string; months?: string } }>(
        '/budgets/forecast',
        async (
            request: FastifyRequest<{ Querystring: { departmentId?: string; months?: string } }>,
            reply: FastifyReply
        ) => {
            const { departmentId, months = '12' } = request.query;

            fastify.log.debug({ departmentId, months }, 'Generating forecast');

            try {
                const monthsToForecast = parseInt(months);

                // Fetch historical budgets
                const historical = await prisma.budget.findMany({
                    where: {
                        ...(departmentId && { departmentId }),
                        status: 'active',
                    },
                    orderBy: {
                        year: 'desc',
                    },
                    take: 12,
                });

                if (historical.length === 0) {
                    return reply.send({
                        forecast: [],
                        message: 'No historical data available',
                    });
                }

                // Simple linear trend forecast
                const avgSpend = historical.reduce((sum, b) => sum + b.spent, 0) / historical.length;
                const avgGrowth = historical.length > 1
                    ? (historical[0].spent - historical[historical.length - 1].spent) / historical.length
                    : 0;

                const forecast = Array.from({ length: monthsToForecast }, (_, i) => {
                    const projectedSpend = avgSpend + avgGrowth * (i + 1);
                    return {
                        month: i + 1,
                        projected: Math.max(0, projectedSpend),
                        confidence: Math.max(0.5, 1 - i * 0.05), // Decreasing confidence
                    };
                });

                return reply.send({
                    historical: historical.map(h => ({
                        year: h.year,
                        quarter: h.quarter,
                        allocated: h.allocated,
                        spent: h.spent,
                    })),
                    forecast,
                    avgSpend,
                    avgGrowth,
                });
            } catch (error) {
                fastify.log.error(error, 'Error generating forecast');
                return reply.status(500).send({ error: 'Failed to generate forecast' });
            }
        }
    );
}
