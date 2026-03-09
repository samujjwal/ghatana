/**
 * Risk Assessment Routes
 *
 * Provides endpoints for retrieving risk scores and insights for children.
 *
 * @doc.type routes
 * @doc.purpose Risk scoring API endpoints
 * @doc.layer backend
 * @doc.pattern REST Routes
 */

import { FastifyPluginAsync, FastifyReply } from 'fastify';
import { z } from 'zod';
import { authenticate, AuthRequest } from '../middleware/auth.middleware';
import { getChildRiskAssessment, getParentRiskOverview } from '../services/risk-scoring.service';
import { logger } from '../utils/logger';

/**
 * Query parameters for risk endpoints
 */
const riskQuerySchema = z.object({
    days: z.coerce.number().int().min(1).max(90).default(7),
});

/**
 * Register risk assessment routes
 */
export const riskRoutes: FastifyPluginAsync = async (fastify) => {
    // All routes require authentication
    fastify.addHook('preHandler', authenticate);

    /**
     * GET /overview
     * Get risk overview for all children of the authenticated parent
     */
    fastify.get('/overview', async (request: AuthRequest, reply: FastifyReply) => {
        try {
            const queryResult = riskQuerySchema.safeParse(request.query);
            const days = queryResult.success ? queryResult.data.days : 7;

            const assessments = await getParentRiskOverview(request.userId!, days);

            return reply.send({
                success: true,
                data: {
                    assessments,
                    summary: {
                        totalChildren: assessments.length,
                        highRiskCount: assessments.filter(a => a.riskBucket === 'high' || a.riskBucket === 'critical').length,
                        avgScore: assessments.length > 0
                            ? Math.round(assessments.reduce((sum, a) => sum + a.overallScore, 0) / assessments.length)
                            : 0,
                    },
                    dataWindow: {
                        days,
                        end: new Date().toISOString(),
                    },
                },
            });
        } catch (error) {
            logger.error('Failed to get risk overview', {
                error: error instanceof Error ? error.message : String(error),
            });
            return reply.status(500).send({
                success: false,
                error: { message: 'Failed to get risk overview', code: 'INTERNAL_ERROR' },
            });
        }
    });

    /**
     * GET /children/:childId
     * Get detailed risk assessment for a specific child
     */
    fastify.get('/children/:childId', async (request: AuthRequest, reply: FastifyReply) => {
        try {
            const { childId } = request.params as { childId: string };
            const queryResult = riskQuerySchema.safeParse(request.query);
            const days = queryResult.success ? queryResult.data.days : 7;

            const assessment = await getChildRiskAssessment(childId, days);

            if (!assessment) {
                return reply.status(404).send({
                    success: false,
                    error: { message: 'Child not found', code: 'NOT_FOUND' },
                });
            }

            return reply.send({
                success: true,
                data: assessment,
            });
        } catch (error) {
            const { childId } = request.params as { childId: string };
            logger.error('Failed to get child risk assessment', {
                childId,
                error: error instanceof Error ? error.message : String(error),
            });
            return reply.status(500).send({
                success: false,
                error: { message: 'Failed to get risk assessment', code: 'INTERNAL_ERROR' },
            });
        }
    });

    /**
     * GET /children/:childId/factors
     * Get risk factors for a specific child (simplified view)
     */
    fastify.get('/children/:childId/factors', async (request: AuthRequest, reply: FastifyReply) => {
        try {
            const { childId } = request.params as { childId: string };
            const queryResult = riskQuerySchema.safeParse(request.query);
            const days = queryResult.success ? queryResult.data.days : 7;
            const severityFilter = (request.query as { severity?: string }).severity;

            const assessment = await getChildRiskAssessment(childId, days);

            if (!assessment) {
                return reply.status(404).send({
                    success: false,
                    error: { message: 'Child not found', code: 'NOT_FOUND' },
                });
            }

            let factors = assessment.factors;
            if (severityFilter && ['low', 'medium', 'high'].includes(severityFilter)) {
                factors = factors.filter(f => f.severity === severityFilter);
            }

            return reply.send({
                success: true,
                data: {
                    childId,
                    childName: assessment.childName,
                    overallScore: assessment.overallScore,
                    riskBucket: assessment.riskBucket,
                    factors,
                    factorCount: factors.length,
                },
            });
        } catch (error) {
            const { childId } = request.params as { childId: string };
            logger.error('Failed to get child risk factors', {
                childId,
                error: error instanceof Error ? error.message : String(error),
            });
            return reply.status(500).send({
                success: false,
                error: { message: 'Failed to get risk factors', code: 'INTERNAL_ERROR' },
            });
        }
    });

    /**
     * GET /children/:childId/recommendations
     * Get recommendations for a specific child
     */
    fastify.get('/children/:childId/recommendations', async (request: AuthRequest, reply: FastifyReply) => {
        try {
            const { childId } = request.params as { childId: string };
            const queryResult = riskQuerySchema.safeParse(request.query);
            const days = queryResult.success ? queryResult.data.days : 7;

            const assessment = await getChildRiskAssessment(childId, days);

            if (!assessment) {
                return reply.status(404).send({
                    success: false,
                    error: { message: 'Child not found', code: 'NOT_FOUND' },
                });
            }

            return reply.send({
                success: true,
                data: {
                    childId,
                    childName: assessment.childName,
                    riskBucket: assessment.riskBucket,
                    insights: assessment.insights,
                    recommendations: assessment.recommendations,
                },
            });
        } catch (error) {
            const { childId } = request.params as { childId: string };
            logger.error('Failed to get child recommendations', {
                childId,
                error: error instanceof Error ? error.message : String(error),
            });
            return reply.status(500).send({
                success: false,
                error: { message: 'Failed to get recommendations', code: 'INTERNAL_ERROR' },
            });
        }
    });
};
