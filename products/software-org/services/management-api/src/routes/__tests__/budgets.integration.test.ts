import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import budgetRoutes from '../budgets.js';

const { mockPrisma } = vi.hoisted(() => ({
    mockPrisma: {
        budget: {
            findMany: vi.fn(),
            updateMany: vi.fn(),
            update: vi.fn(),
        },
    },
}));

vi.mock('../../db/client.js', () => ({
    prisma: mockPrisma,
}));

describe('Budget Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(budgetRoutes, { prefix: '/api/v1' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should group department budgets into a yearly budget plan', async () => {
        mockPrisma.budget.findMany.mockResolvedValue([
            {
                id: 'budget-1',
                departmentId: 'dept-1',
                allocated: 120000,
                spent: 45000,
                categories: { headcount: 80000 },
                notes: 'Scale platform team',
                status: 'draft',
                department: { name: 'Engineering' },
            },
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/budgets?year=2026&quarter=Q2',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({
            year: 2026,
            quarter: 'Q2',
            budgets: [
                {
                    id: 'budget-1',
                    departmentId: 'dept-1',
                    departmentName: 'Engineering',
                    currentAllocated: 120000,
                    currentSpent: 45000,
                    proposedAllocated: 120000,
                    categories: { headcount: 80000 },
                    justification: 'Scale platform team',
                    status: 'draft',
                },
            ],
            totalBudgetLimit: 120000,
            status: 'draft',
        });
    });

    it('should reject budget-plan updates without a budgets array', async () => {
        const response = await fastify.inject({
            method: 'PUT',
            url: '/api/v1/budgets',
            payload: {
                id: 'plan-1',
            },
        });

        expect(response.statusCode).toBe(400);
        expect(response.json()).toEqual({ error: 'Budgets array is required' });
    });

    it('should archive budgets by updating their status', async () => {
        mockPrisma.budget.update.mockResolvedValue({ id: 'budget-1', status: 'archived' });

        const response = await fastify.inject({
            method: 'DELETE',
            url: '/api/v1/budgets/budget-1',
        });

        expect(response.statusCode).toBe(200);
        expect(response.json()).toEqual({ message: 'Budget archived successfully' });
        expect(mockPrisma.budget.update).toHaveBeenCalledWith({
            where: { id: 'budget-1' },
            data: { status: 'archived' },
        });
    });
});