import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import performanceReviewRoutes from '../performance-reviews.js';

const { mockPrisma } = vi.hoisted(() => ({
    mockPrisma: {
        performanceReview: {
            findMany: vi.fn(),
            create: vi.fn(),
            findUnique: vi.fn(),
            update: vi.fn(),
        },
    },
}));

vi.mock('../../db/client.js', () => ({
    prisma: mockPrisma,
}));

function buildReview(overrides: Record<string, unknown> = {}) {
    return {
        id: 'review-1',
        employeeId: 'emp-1',
        reviewerId: 'mgr-1',
        period: 'Q2-2026',
        status: 'IN_PROGRESS',
        ratings: { overall: 4.5, metadata: { strengths: 'Ownership' } },
        goals: ['Ship roadmap'],
        feedback: 'Keep stretching into mentorship.',
        strengths: 'Ownership',
        improvements: 'Communication cadence',
        createdAt: new Date('2026-04-01T00:00:00.000Z'),
        updatedAt: new Date('2026-04-02T00:00:00.000Z'),
        submittedAt: null,
        completedAt: null,
        employee: { id: 'emp-1', name: 'Ada Lovelace' },
        ...overrides,
    };
}

describe('Performance Review Routes Integration Tests', () => {
    let fastify: FastifyInstance;

    beforeEach(async () => {
        vi.clearAllMocks();
        fastify = Fastify({ logger: false });
        await fastify.register(performanceReviewRoutes, { prefix: '/api/v1' });
    });

    afterEach(async () => {
        await fastify.close();
    });

    it('should map cycle query to prisma period filter when listing reviews', async () => {
        mockPrisma.performanceReview.findMany.mockResolvedValue([buildReview()]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/performance-reviews?employeeId=emp-1&cycle=Q2-2026&status=IN_PROGRESS',
        });

        expect(response.statusCode).toBe(200);
        expect(mockPrisma.performanceReview.findMany).toHaveBeenCalledWith(
            expect.objectContaining({
                where: {
                    employeeId: 'emp-1',
                    period: 'Q2-2026',
                    status: 'IN_PROGRESS',
                },
            })
        );
        expect(response.json()).toEqual([
            expect.objectContaining({
                id: 'review-1',
                overallRating: 4.5,
                employee: { id: 'emp-1', name: 'Ada Lovelace' },
            }),
        ]);
    });

    it('should default reviewer period and status on create', async () => {
        mockPrisma.performanceReview.create.mockImplementation(async ({ data }: { data: Record<string, unknown> }) =>
            buildReview({
                reviewerId: data.reviewerId,
                period: data.period,
                status: data.status,
                ratings: data.ratings,
                goals: data.goals,
                strengths: data.strengths,
                improvements: data.improvements,
                feedback: data.feedback,
                submittedAt: data.submittedAt ?? null,
            })
        );

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/performance-reviews',
            payload: {
                employeeId: 'emp-1',
                metadata: {
                    period: 'Q3-2026',
                    goals: ['Lead architecture review'],
                    strengths: 'Systems thinking',
                    improvements: 'Delegation',
                    careerDevelopment: 'Prepare for staff scope',
                },
                overallRating: 5,
            },
        });

        expect(response.statusCode).toBe(201);
        expect(mockPrisma.performanceReview.create).toHaveBeenCalledWith(
            expect.objectContaining({
                data: expect.objectContaining({
                    employeeId: 'emp-1',
                    reviewerId: 'emp-1',
                    period: 'Q3-2026',
                    status: 'IN_PROGRESS',
                    feedback: 'Prepare for staff scope',
                }),
            })
        );
        expect(response.json()).toEqual(
            expect.objectContaining({
                reviewerId: 'emp-1',
                period: 'Q3-2026',
                status: 'IN_PROGRESS',
            })
        );
    });

    it('should return 404 for missing review details', async () => {
        mockPrisma.performanceReview.findUnique.mockResolvedValue(null);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/performance-reviews/missing-review',
        });

        expect(response.statusCode).toBe(404);
        expect(response.json()).toEqual({ error: 'Performance review not found' });
    });

    it('should submit an existing review with updated metadata', async () => {
        mockPrisma.performanceReview.findUnique.mockResolvedValue(buildReview({ ratings: { overallRating: 3 } }));
        mockPrisma.performanceReview.update.mockImplementation(async ({ data }: { data: Record<string, unknown> }) =>
            buildReview({
                status: data.status,
                submittedAt: new Date('2026-04-03T00:00:00.000Z'),
                ratings: data.ratings,
                goals: data.goals,
                strengths: data.strengths,
                improvements: data.improvements,
                feedback: data.feedback,
            })
        );

        const response = await fastify.inject({
            method: 'POST',
            url: '/api/v1/performance-reviews/review-1/submit',
            payload: {
                overallRating: 4,
                metadata: {
                    goals: ['Improve stakeholder updates'],
                    strengths: 'Delivery consistency',
                    improvements: 'Cross-team visibility',
                    careerDevelopment: 'Expand product strategy involvement',
                },
            },
        });

        expect(response.statusCode).toBe(200);
        expect(mockPrisma.performanceReview.update).toHaveBeenCalledWith(
            expect.objectContaining({
                data: expect.objectContaining({
                    status: 'SUBMITTED',
                    feedback: 'Expand product strategy involvement',
                }),
            })
        );
        expect(response.json()).toEqual(
            expect.objectContaining({
                status: 'SUBMITTED',
                metadata: expect.objectContaining({
                    strengths: 'Delivery consistency',
                    improvements: 'Cross-team visibility',
                }),
            })
        );
    });

    it('should return due reviews with requested limit', async () => {
        mockPrisma.performanceReview.findMany.mockResolvedValue([
            buildReview({ id: 'review-due-1', updatedAt: new Date('2026-04-01T00:00:00.000Z') }),
        ]);

        const response = await fastify.inject({
            method: 'GET',
            url: '/api/v1/performance-reviews/due?reviewerId=mgr-1&dueDays=7',
        });

        expect(response.statusCode).toBe(200);
        expect(mockPrisma.performanceReview.findMany).toHaveBeenCalledWith(
            expect.objectContaining({
                where: {
                    reviewerId: 'mgr-1',
                    status: { in: ['IN_PROGRESS', 'DRAFT'] },
                },
                orderBy: { updatedAt: 'asc' },
                take: '7',
            })
        );
        expect(response.json()).toEqual({
            data: [expect.objectContaining({ id: 'review-due-1' })],
            total: 1,
            dueDays: '7',
        });
    });
});