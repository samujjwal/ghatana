/**
 * Performance Review API Routes
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for performance review management
 * @doc.layer product
 * @doc.pattern Router
 *
 * Endpoints:
 * - GET /api/v1/performance-reviews - List reviews
 * - POST /api/v1/performance-reviews - Create new review
 * - GET /api/v1/performance-reviews/:id - Get review details
 * - PATCH /api/v1/performance-reviews/:id - Update review
 * - POST /api/v1/performance-reviews/:id/submit - Submit for approval
 * - GET /api/v1/performance-reviews/due - Get upcoming reviews
 * - POST /api/v1/performance-reviews/:id/ai-insights - Generate AI insights
 */

import { FastifyInstance } from 'fastify';
import { prisma } from '../db/client.js';
import type { Prisma } from '../../generated/prisma-client/index.js';

interface ListReviewsQuery {
    employeeId?: string;
    reviewerId?: string;
    cycle?: string; // UI uses this; maps to Prisma `period`
    period?: string; // alias
    status?: string;
}

interface UpsertReviewBody {
    employeeId: string;
    reviewerId?: string;
    cycle?: string;
    period?: string;
    status?: string;
    overallRating?: number;
    metadata?: Record<string, unknown>;
    reviewDate?: string;
}

interface SubmitReviewBody {
    overallRating?: number;
    metadata?: Record<string, unknown>;
}

function getCurrentPeriod(now: Date = new Date()): string {
    const year = now.getFullYear();
    const month = now.getMonth();
    const quarter = Math.floor(month / 3) + 1;
    return `Q${quarter}-${year}`;
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function jsonObject(value: Prisma.JsonValue): Record<string, unknown> {
    if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
        return value as Record<string, unknown>;
    }
    return {};
}

function jsonArray(value: Prisma.JsonValue): unknown[] {
    if (Array.isArray(value)) return value;
    return [];
}

function toApiReview(review: {
    id: string;
    employeeId: string;
    reviewerId: string;
    period: string;
    status: string;
    ratings: Prisma.JsonValue;
    goals: Prisma.JsonValue;
    feedback: string | null;
    strengths: Prisma.JsonValue;
    improvements: Prisma.JsonValue;
    createdAt: Date;
    updatedAt: Date;
    submittedAt: Date | null;
    completedAt: Date | null;
    employee?: { id: string; name: string | null } | null;
}): {
    id: string;
    employeeId: string;
    reviewerId: string;
    period: string;
    status: string;
    overallRating: number;
    metadata: Record<string, unknown>;
    createdAt: string;
    updatedAt: string;
    submittedAt: string | null;
    completedAt: string | null;
    employee?: { id: string; name: string | null } | null;
} {
    const ratings = jsonObject(review.ratings);
    const metadata = (ratings.metadata && typeof ratings.metadata === 'object' && ratings.metadata !== null)
        ? (ratings.metadata as Record<string, unknown>)
        : {};

    // Calculate overall rating from ratings object (either direct 'overall' field or average of all ratings)
    let overallRating = 0;
    if (typeof ratings.overall === 'number') {
        overallRating = ratings.overall;
    } else {
        // Calculate average from all numeric ratings
        const numericRatings = Object.entries(ratings)
            .filter(([key, value]) => key !== 'metadata' && typeof value === 'number')
            .map(([, value]) => value as number);
        if (numericRatings.length > 0) {
            overallRating = numericRatings.reduce((sum, rating) => sum + rating, 0) / numericRatings.length;
        }
    }

    return {
        id: review.id,
        employeeId: review.employeeId,
        reviewerId: review.reviewerId,
        period: review.period,
        status: review.status,
        overallRating,
        metadata: {
            ...metadata,
            goals: jsonArray(review.goals),
            strengths: typeof review.strengths === 'string' ? review.strengths : metadata.strengths ?? review.strengths,
            improvements: typeof review.improvements === 'string' ? review.improvements : metadata.improvements ?? review.improvements,
            careerDevelopment: metadata.careerDevelopment ?? review.feedback ?? '',
        },
        createdAt: review.createdAt.toISOString(),
        updatedAt: review.updatedAt.toISOString(),
        submittedAt: review.submittedAt ? review.submittedAt.toISOString() : null,
        completedAt: review.completedAt ? review.completedAt.toISOString() : null,
        ...(review.employee !== undefined ? { employee: review.employee } : {}),
    };
}

export default async function performanceReviewRoutes(fastify: FastifyInstance) {
    /**
     * List performance reviews
     */
    fastify.get<{ Querystring: ListReviewsQuery }>(
        '/performance-reviews',
        async (request, reply) => {
            const { employeeId, reviewerId, cycle, period, status } = request.query;

            const where: Prisma.PerformanceReviewWhereInput = {};
            if (employeeId) where.employeeId = employeeId;
            if (reviewerId) where.reviewerId = reviewerId;
            if (cycle) where.period = cycle;
            if (period) where.period = period;
            if (status) where.status = status;

            const reviews = await prisma.performanceReview.findMany({
                where,
                orderBy: { updatedAt: 'desc' },
                include: {
                    employee: { select: { id: true, name: true } },
                },
            });

            return reviews.map((r) => toApiReview(r));
        }
    );

    /**
     * Create new performance review
     */
    fastify.post<{ Body: UpsertReviewBody }>(
        '/performance-reviews',
        async (request, reply) => {
            const { employeeId, reviewerId, cycle, period, status, overallRating, metadata = {}, reviewDate } =
                request.body;

            const resolvedReviewerId = reviewerId ?? employeeId;
            const resolvedPeriod = period ?? cycle ?? (typeof metadata.period === 'string' ? metadata.period : undefined) ?? getCurrentPeriod();
            const resolvedStatus = status ?? 'IN_PROGRESS';

            const ratingPayload: Record<string, unknown> = {
                overallRating: overallRating ?? (typeof metadata.overallRating === 'number' ? metadata.overallRating : 0),
                metadata,
                reviewDate: reviewDate ?? new Date().toISOString(),
            };

            const review = await prisma.performanceReview.create({
                data: {
                    employeeId,
                    reviewerId: resolvedReviewerId,
                    period: resolvedPeriod,
                    status: resolvedStatus,
                    ratings: ratingPayload as Prisma.InputJsonValue,
                    goals: (Array.isArray(metadata.goals) ? metadata.goals : []) as Prisma.InputJsonValue,
                    strengths: (metadata.strengths ?? '') as Prisma.InputJsonValue,
                    improvements: (metadata.improvements ?? '') as Prisma.InputJsonValue,
                    feedback: typeof metadata.careerDevelopment === 'string' ? metadata.careerDevelopment : undefined,
                    submittedAt: resolvedStatus === 'SUBMITTED' ? new Date() : undefined,
                },
                include: {
                    employee: { select: { id: true, name: true } },
                },
            });

            reply.code(201);
            return toApiReview(review);
        }
    );

    /**
     * Get review details
     */
    fastify.get<{ Params: { id: string } }>(
        '/performance-reviews/:id',
        async (request, reply) => {
            const { id } = request.params;

            const review = await prisma.performanceReview.findUnique({
                where: { id },
                include: {
                    employee: { select: { id: true, name: true } },
                },
            });

            if (!review) {
                reply.code(404);
                return { error: 'Performance review not found' };
            }

            return toApiReview(review);
        }
    );

    /**
     * Update review (web uses PUT)
     */
    fastify.put<{ Params: { id: string }; Body: UpsertReviewBody }>(
        '/performance-reviews/:id',
        async (request, reply) => {
            const { id } = request.params;
            const updates = request.body;

            const existing = await prisma.performanceReview.findUnique({ where: { id } });

            if (!existing) {
                reply.code(404);
                return { error: 'Performance review not found' };
            }

            const resolvedPeriod = updates.period ?? updates.cycle;
            const mergedMetadata = updates.metadata ?? {};
            const ratingPayload: Record<string, unknown> = {
                ...jsonObject(existing.ratings),
                overallRating: updates.overallRating ?? jsonObject(existing.ratings).overallRating ?? 0,
                metadata: mergedMetadata,
                reviewDate: updates.reviewDate ?? new Date().toISOString(),
            };

            const data: Prisma.PerformanceReviewUpdateInput = {
                ...(resolvedPeriod ? { period: resolvedPeriod } : {}),
                ...(updates.status ? { status: updates.status } : {}),
                ratings: ratingPayload as Prisma.InputJsonValue,
                goals: (Array.isArray(mergedMetadata.goals) ? mergedMetadata.goals : jsonArray(existing.goals)) as Prisma.InputJsonValue,
                strengths: (mergedMetadata.strengths ?? existing.strengths) as Prisma.InputJsonValue,
                improvements: (mergedMetadata.improvements ?? existing.improvements) as Prisma.InputJsonValue,
                feedback: typeof mergedMetadata.careerDevelopment === 'string' ? mergedMetadata.careerDevelopment : existing.feedback,
            };

            const updated = await prisma.performanceReview.update({
                where: { id },
                data,
                include: {
                    employee: { select: { id: true, name: true } },
                },
            });

            return toApiReview(updated);
        }
    );

    /**
     * Submit review for approval
     */
    fastify.post<{ Params: { id: string }; Body: SubmitReviewBody }>(
        '/performance-reviews/:id/submit',
        async (request, reply) => {
            const { id } = request.params;
            const { overallRating, metadata = {} } = request.body;

            const existing = await prisma.performanceReview.findUnique({ where: { id } });

            if (!existing) {
                reply.code(404);
                return { error: 'Performance review not found' };
            }

            const currentRatings = jsonObject(existing.ratings);
            const ratingPayload: Record<string, unknown> = {
                ...currentRatings,
                overallRating: overallRating ?? (typeof currentRatings.overallRating === 'number' ? currentRatings.overallRating : 0),
                metadata,
            };

            const updated = await prisma.performanceReview.update({
                where: { id },
                data: {
                    status: 'SUBMITTED',
                    submittedAt: new Date(),
                    ratings: ratingPayload as Prisma.InputJsonValue,
                    goals: (Array.isArray(metadata.goals) ? metadata.goals : jsonArray(existing.goals)) as Prisma.InputJsonValue,
                    strengths: (metadata.strengths ?? existing.strengths) as Prisma.InputJsonValue,
                    improvements: (metadata.improvements ?? existing.improvements) as Prisma.InputJsonValue,
                    feedback: typeof metadata.careerDevelopment === 'string' ? metadata.careerDevelopment : existing.feedback,
                },
                include: {
                    employee: { select: { id: true, name: true } },
                },
            });

            return toApiReview(updated);
        }
    );

    /**
     * Get upcoming reviews
     */
    fastify.get<{ Querystring: { reviewerId?: string; dueDays?: number } }>(
        '/performance-reviews/due',
        async (request, reply) => {
            const { reviewerId, dueDays = 30 } = request.query;

            const where: Prisma.PerformanceReviewWhereInput = {
                status: { in: ['IN_PROGRESS', 'DRAFT'] },
            };
            if (reviewerId) where.reviewerId = reviewerId;

            const reviews = await prisma.performanceReview.findMany({
                where,
                orderBy: { updatedAt: 'asc' },
                take: dueDays,
            });

            return {
                data: reviews.map((r) => toApiReview(r)),
                total: reviews.length,
                dueDays,
            };
        }
    );

    /**
     * Generate AI insights
     */
    fastify.post<{ Params: { id: string } }>(
        '/performance-reviews/:id/ai-insights',
        async (request, reply) => {
            const { id } = request.params;

            const review = await prisma.performanceReview.findUnique({ where: { id } });

            if (!review) {
                reply.code(404);
                return { error: 'Performance review not found' };
            }

            // TODO: Call Java AI integration service
            // const insights = await javaServiceClient.generateReviewInsights(review);

            // Placeholder AI insights
            const insights = {
                reviewId: review.id,
                insights: [
                    {
                        type: 'STRENGTH',
                        category: 'TECHNICAL_SKILLS',
                        description: 'Consistently exceeds technical goals with high-quality deliverables',
                        confidence: 0.92,
                    },
                    {
                        type: 'IMPROVEMENT',
                        category: 'COMMUNICATION',
                        description: 'Could improve cross-team collaboration and stakeholder updates',
                        confidence: 0.78,
                        suggestions: [
                            'Schedule regular check-ins with stakeholders',
                            'Participate in cross-functional team meetings',
                        ],
                    },
                    {
                        type: 'CAREER_PATH',
                        category: 'GROWTH',
                        description: 'Ready for senior-level technical responsibilities',
                        confidence: 0.85,
                        recommendations: [
                            'Lead architectural design discussions',
                            'Mentor junior team members',
                        ],
                    },
                ],
                generatedAt: new Date().toISOString(),
            };

            return insights;
        }
    );

    /**
     * Get review templates
     */
    fastify.get(
        '/performance-reviews/templates',
        {
            schema: {
                response: {
                    200: {
                        type: 'array',
                        items: {
                            type: 'object',
                            properties: {
                                id: { type: 'string' },
                                name: { type: 'string' },
                                description: { type: 'string' },
                                categories: { type: 'array' },
                                questions: { type: 'array' },
                            },
                        },
                    },
                },
            },
        },
        async (request, reply) => {
            // Return predefined templates
            const templates = [
                {
                    id: 'standard-annual',
                    name: 'Standard Annual Review',
                    description: 'Comprehensive annual performance review template',
                    categories: [
                        { id: 'technical', name: 'Technical Skills', weight: 0.3 },
                        { id: 'collaboration', name: 'Collaboration', weight: 0.25 },
                        { id: 'leadership', name: 'Leadership', weight: 0.2 },
                        { id: 'communication', name: 'Communication', weight: 0.15 },
                        { id: 'delivery', name: 'Delivery', weight: 0.1 },
                    ],
                    questions: [
                        {
                            id: 'q1',
                            category: 'technical',
                            text: 'How effectively did the employee apply technical skills?',
                            type: 'rating',
                            scale: 5,
                        },
                        {
                            id: 'q2',
                            category: 'collaboration',
                            text: 'How well did the employee collaborate with team members?',
                            type: 'rating',
                            scale: 5,
                        },
                        {
                            id: 'q3',
                            category: 'leadership',
                            text: 'What leadership qualities did the employee demonstrate?',
                            type: 'text',
                        },
                    ],
                },
                {
                    id: 'quarterly-checkin',
                    name: 'Quarterly Check-in',
                    description: 'Lightweight quarterly performance check-in',
                    categories: [
                        { id: 'progress', name: 'Goal Progress', weight: 0.5 },
                        { id: 'support', name: 'Support Needed', weight: 0.3 },
                        { id: 'satisfaction', name: 'Job Satisfaction', weight: 0.2 },
                    ],
                    questions: [
                        {
                            id: 'q1',
                            category: 'progress',
                            text: 'How much progress has been made on quarterly goals?',
                            type: 'rating',
                            scale: 5,
                        },
                    ],
                },
            ];

            return templates;
        }
    );

    /**
     * Create or update calibration session
     */
    fastify.post<{
        Body: {
            period: string;
            reviewerIds: string[];
            reviewIds: string[];
            status?: 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED';
            calibratedRatings?: Record<string, number>;
            notes?: string;
        };
    }>(
        '/performance-reviews/calibration',
        {
            schema: {
                body: {
                    type: 'object',
                    required: ['period', 'reviewerIds', 'reviewIds'],
                    properties: {
                        period: { type: 'string' },
                        reviewerIds: { type: 'array', items: { type: 'string' } },
                        reviewIds: { type: 'array', items: { type: 'string' } },
                        status: { type: 'string', enum: ['SCHEDULED', 'IN_PROGRESS', 'COMPLETED'] },
                        calibratedRatings: { type: 'object' },
                        notes: { type: 'string' },
                    },
                },
                response: {
                    201: {
                        type: 'object',
                        properties: {
                            id: { type: 'string' },
                            period: { type: 'string' },
                            status: { type: 'string' },
                        },
                    },
                },
            },
        },
        async (request, reply) => {
            const { period, reviewerIds, reviewIds, status = 'SCHEDULED', calibratedRatings, notes } = request.body;

            // Store calibration session data in performance review metadata
            // For simplicity, we'll attach it to the first review as a reference point
            if (reviewIds.length > 0) {
                const firstReviewId = reviewIds[0];
                const review = await prisma.performanceReview.findUnique({
                    where: { id: firstReviewId },
                });

                if (review) {
                    const metadata = jsonObject(review.ratings);
                    metadata.calibration = {
                        period,
                        reviewerIds,
                        reviewIds,
                        status,
                        calibratedRatings: calibratedRatings ?? {},
                        notes: notes ?? '',
                        createdAt: new Date().toISOString(),
                    };

                    await prisma.performanceReview.update({
                        where: { id: firstReviewId },
                        data: {
                            ratings: metadata as Prisma.InputJsonValue,
                        },
                    });
                }
            }

            reply.code(201);
            return {
                id: `calibration-${period}`,
                period,
                status,
            };
        }
    );

    /**
     * Get calibration sessions
     */
    fastify.get<{ Querystring: { period?: string } }>(
        '/performance-reviews/calibration',
        {
            schema: {
                querystring: {
                    type: 'object',
                    properties: {
                        period: { type: 'string' },
                    },
                },
                response: {
                    200: {
                        type: 'array',
                        items: {
                            type: 'object',
                            properties: {
                                id: { type: 'string' },
                                period: { type: 'string' },
                                status: { type: 'string' },
                                reviewerIds: { type: 'array' },
                                reviewIds: { type: 'array' },
                            },
                        },
                    },
                },
            },
        },
        async (request, reply) => {
            const { period } = request.query;

            // Query reviews with calibration metadata
            const where: Prisma.PerformanceReviewWhereInput = {};
            if (period) where.period = period;

            const reviews = await prisma.performanceReview.findMany({
                where,
                select: {
                    id: true,
                    period: true,
                    ratings: true,
                },
            });

            // Extract calibration sessions from metadata
            const calibrations = reviews
                .map((review) => {
                    const metadata = jsonObject(review.ratings);
                    const calibration = metadata.calibration;
                    if (calibration && isRecord(calibration)) {
                        return {
                            id: `calibration-${review.period}`,
                            period: review.period,
                            status: typeof calibration.status === 'string' ? calibration.status : 'SCHEDULED',
                            reviewerIds: Array.isArray(calibration.reviewerIds) ? calibration.reviewerIds : [],
                            reviewIds: Array.isArray(calibration.reviewIds) ? calibration.reviewIds : [],
                        };
                    }
                    return null;
                })
                .filter((c): c is NonNullable<typeof c> => c !== null);

            return calibrations;
        }
    );
}
