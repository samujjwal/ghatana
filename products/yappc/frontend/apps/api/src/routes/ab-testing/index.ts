/**
 * A/B Testing API Routes (Node.js/Fastify)
 * 
 * Provides REST endpoints for experiment management, user satisfaction tracking,
 * and real-time metrics dashboards.
 * 
 * @doc.type module
 * @doc.purpose A/B testing API for UI state and CRUD operations
 * @doc.layer product
 * @doc.pattern Controller
 */

import { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { abTestingService, Experiment, ExperimentStatus, AllocationStrategy } from '../../services/ab-testing/ABTestingService';

// ============================================================================
// Request/Response Types
// ============================================================================

interface CreateExperimentRequest {
    name: string;
    description: string;
    variants: Array<{
        name: string;
        model: string;
        trafficPercentage: number;
        config?: Record<string, unknown>;
    }>;
    allocationStrategy: AllocationStrategy;
    targetMetrics: string[];
    minSampleSize?: number;
    statisticalSignificance?: number;
}

interface RecordSatisfactionRequest {
    experimentId: string;
    variantId: string;
    score: number;
    feedbackText?: string;
}

interface TrackInteractionRequest {
    experimentId: string;
    variantId: string;
    sessionId: string;
    eventType: 'request' | 'response' | 'error';
    model: string;
    promptTokens?: number;
    completionTokens?: number;
    latencyMs?: number;
    cost?: number;
    isSuccess: boolean;
    errorCode?: string;
    metadata?: Record<string, unknown>;
}

interface GetAssignmentRequest {
    experimentId: string;
}

// ============================================================================
// Route Registration
// ============================================================================

export async function abTestingRoutes(fastify: FastifyInstance): Promise<void> {
    // ============================================================================
    // Experiment Management
    // ============================================================================

    /**
     * Create a new A/B test experiment
     * POST /api/ab-testing/experiments
     */
    fastify.post<{ Body: CreateExperimentRequest }>(
        '/experiments',
        {
            schema: {
                body: {
                    type: 'object',
                    required: ['name', 'description', 'variants', 'allocationStrategy', 'targetMetrics'],
                    properties: {
                        name: { type: 'string', minLength: 1 },
                        description: { type: 'string' },
                        variants: {
                            type: 'array',
                            minItems: 2,
                            items: {
                                type: 'object',
                                required: ['name', 'model', 'trafficPercentage'],
                                properties: {
                                    name: { type: 'string' },
                                    model: { type: 'string' },
                                    trafficPercentage: { type: 'number', minimum: 0, maximum: 100 },
                                    config: { type: 'object' },
                                },
                            },
                        },
                        allocationStrategy: {
                            type: 'string',
                            enum: ['random', 'weighted', 'sticky', 'multi-armed-bandit']
                        },
                        targetMetrics: { type: 'array', items: { type: 'string' } },
                        minSampleSize: { type: 'number', default: 100 },
                        statisticalSignificance: { type: 'number', default: 0.95 },
                    },
                },
            },
        },
        async (request: FastifyRequest<{ Body: CreateExperimentRequest }>, reply: FastifyReply) => {
            try {
                const { name, description, variants, allocationStrategy, targetMetrics, minSampleSize, statisticalSignificance } = request.body;
                const userId = (request as unknown).user?.id || 'anonymous';

                const experiment = abTestingService.createExperiment({
                    name,
                    description,
                    status: 'draft',
                    variants: variants.map((v, index) => ({
                        id: `var_${Date.now()}_${index}`,
                        name: v.name,
                        model: v.model as unknown,
                        trafficPercentage: v.trafficPercentage,
                        config: v.config,
                    })),
                    allocationStrategy,
                    targetMetrics,
                    minSampleSize: minSampleSize || 100,
                    statisticalSignificance: statisticalSignificance || 0.95,
                    createdBy: userId,
                });

                return reply.status(201).send({
                    success: true,
                    data: experiment,
                });
            } catch (error: unknown) {
                return reply.status(400).send({
                    success: false,
                    error: error.message,
                });
            }
        }
    );

    /**
     * List all experiments
     * GET /api/ab-testing/experiments
     */
    fastify.get<{ Querystring: { status?: ExperimentStatus } }>(
        '/experiments',
        {
            schema: {
                querystring: {
                    type: 'object',
                    properties: {
                        status: {
                            type: 'string',
                            enum: ['draft', 'running', 'paused', 'completed', 'archived']
                        },
                    },
                },
            },
        },
        async (request, reply) => {
            const { status } = request.query;
            const experiments = abTestingService.listExperiments(status);

            return reply.send({
                success: true,
                data: experiments,
                count: experiments.length,
            });
        }
    );

    /**
     * Get experiment by ID
     * GET /api/ab-testing/experiments/:id
     */
    fastify.get<{ Params: { id: string } }>(
        '/experiments/:id',
        async (request, reply) => {
            const experiment = abTestingService.getExperiment(request.params.id);

            if (!experiment) {
                return reply.status(404).send({
                    success: false,
                    error: 'Experiment not found',
                });
            }

            return reply.send({
                success: true,
                data: experiment,
            });
        }
    );

    /**
     * Start an experiment
     * POST /api/ab-testing/experiments/:id/start
     */
    fastify.post<{ Params: { id: string } }>(
        '/experiments/:id/start',
        async (request, reply) => {
            try {
                const experiment = abTestingService.startExperiment(request.params.id);
                return reply.send({
                    success: true,
                    data: experiment,
                    message: 'Experiment started successfully',
                });
            } catch (error: unknown) {
                return reply.status(400).send({
                    success: false,
                    error: error.message,
                });
            }
        }
    );

    /**
     * Pause an experiment
     * POST /api/ab-testing/experiments/:id/pause
     */
    fastify.post<{ Params: { id: string } }>(
        '/experiments/:id/pause',
        async (request, reply) => {
            try {
                const experiment = abTestingService.pauseExperiment(request.params.id);
                return reply.send({
                    success: true,
                    data: experiment,
                    message: 'Experiment paused',
                });
            } catch (error: unknown) {
                return reply.status(400).send({
                    success: false,
                    error: error.message,
                });
            }
        }
    );

    /**
     * Complete an experiment
     * POST /api/ab-testing/experiments/:id/complete
     */
    fastify.post<{ Params: { id: string }; Body: { winnerId?: string } }>(
        '/experiments/:id/complete',
        async (request, reply) => {
            try {
                const results = abTestingService.completeExperiment(
                    request.params.id,
                    request.body?.winnerId
                );
                return reply.send({
                    success: true,
                    data: results,
                    message: 'Experiment completed',
                });
            } catch (error: unknown) {
                return reply.status(400).send({
                    success: false,
                    error: error.message,
                });
            }
        }
    );

    /**
     * Get experiment results/analysis
     * GET /api/ab-testing/experiments/:id/results
     */
    fastify.get<{ Params: { id: string } }>(
        '/experiments/:id/results',
        async (request, reply) => {
            try {
                const results = abTestingService.analyzeExperiment(request.params.id);
                return reply.send({
                    success: true,
                    data: results,
                });
            } catch (error: unknown) {
                return reply.status(400).send({
                    success: false,
                    error: error.message,
                });
            }
        }
    );

    /**
     * Export experiment data
     * GET /api/ab-testing/experiments/:id/export
     */
    fastify.get<{ Params: { id: string } }>(
        '/experiments/:id/export',
        async (request, reply) => {
            try {
                const data = abTestingService.exportExperimentData(request.params.id);
                return reply.send({
                    success: true,
                    data,
                });
            } catch (error: unknown) {
                return reply.status(400).send({
                    success: false,
                    error: error.message,
                });
            }
        }
    );

    // ============================================================================
    // User Assignment
    // ============================================================================

    /**
     * Get user's variant assignment for an experiment
     * GET /api/ab-testing/assignment/:experimentId
     */
    fastify.get<{ Params: GetAssignmentRequest }>(
        '/assignment/:experimentId',
        async (request, reply) => {
            const userId = (request as unknown).user?.id || 'anonymous';
            const variant = abTestingService.assignUser(request.params.experimentId, userId);

            if (!variant) {
                return reply.status(404).send({
                    success: false,
                    error: 'No active experiment or assignment found',
                });
            }

            return reply.send({
                success: true,
                data: {
                    experimentId: request.params.experimentId,
                    variant,
                    assignedAt: new Date(),
                },
            });
        }
    );

    // ============================================================================
    // Interaction Tracking
    // ============================================================================

    /**
     * Track an AI interaction
     * POST /api/ab-testing/track
     */
    fastify.post<{ Body: TrackInteractionRequest }>(
        '/track',
        {
            schema: {
                body: {
                    type: 'object',
                    required: ['experimentId', 'variantId', 'sessionId', 'eventType', 'model', 'isSuccess'],
                    properties: {
                        experimentId: { type: 'string' },
                        variantId: { type: 'string' },
                        sessionId: { type: 'string' },
                        eventType: { type: 'string', enum: ['request', 'response', 'error'] },
                        model: { type: 'string' },
                        promptTokens: { type: 'number' },
                        completionTokens: { type: 'number' },
                        latencyMs: { type: 'number' },
                        cost: { type: 'number' },
                        isSuccess: { type: 'boolean' },
                        errorCode: { type: 'string' },
                        metadata: { type: 'object' },
                    },
                },
            },
        },
        async (request, reply) => {
            const userId = (request as unknown).user?.id || 'anonymous';

            const interaction = abTestingService.trackInteraction({
                ...request.body,
                userId,
                model: request.body.model as unknown,
            });

            return reply.status(201).send({
                success: true,
                data: interaction,
            });
        }
    );

    /**
     * Record user satisfaction
     * POST /api/ab-testing/satisfaction
     */
    fastify.post<{ Body: RecordSatisfactionRequest }>(
        '/satisfaction',
        {
            schema: {
                body: {
                    type: 'object',
                    required: ['experimentId', 'variantId', 'score'],
                    properties: {
                        experimentId: { type: 'string' },
                        variantId: { type: 'string' },
                        score: { type: 'number', minimum: 1, maximum: 5 },
                        feedbackText: { type: 'string' },
                    },
                },
            },
        },
        async (request, reply) => {
            try {
                const userId = (request as unknown).user?.id || 'anonymous';
                const { experimentId, variantId, score, feedbackText } = request.body;

                const interaction = abTestingService.recordSatisfaction(
                    experimentId,
                    variantId,
                    userId,
                    score,
                    feedbackText
                );

                return reply.status(201).send({
                    success: true,
                    data: interaction,
                    message: 'Satisfaction recorded',
                });
            } catch (error: unknown) {
                return reply.status(400).send({
                    success: false,
                    error: error.message,
                });
            }
        }
    );

    // ============================================================================
    // Model Health
    // ============================================================================

    /**
     * Get all model health statuses
     * GET /api/ab-testing/health
     */
    fastify.get('/health', async (request, reply) => {
        const health = abTestingService.getAllModelHealth();

        return reply.send({
            success: true,
            data: health,
            summary: {
                total: health.length,
                healthy: health.filter((h: unknown) => h.isHealthy).length,
                degraded: health.filter((h: unknown) => !h.isHealthy).length,
            },
        });
    });

    /**
     * Get specific model health
     * GET /api/ab-testing/health/:model
     */
    fastify.get<{ Params: { model: string } }>(
        '/health/:model',
        async (request, reply) => {
            const health = abTestingService.getModelHealth(request.params.model as unknown);

            if (!health) {
                return reply.status(404).send({
                    success: false,
                    error: 'Model not found',
                });
            }

            return reply.send({
                success: true,
                data: health,
            });
        }
    );

    /**
     * Get fallback recommendation for a model
     * GET /api/ab-testing/fallback/:model
     */
    fastify.get<{ Params: { model: string } }>(
        '/fallback/:model',
        async (request, reply) => {
            const fallback = abTestingService.getFallbackModel(request.params.model as unknown);

            return reply.send({
                success: true,
                data: {
                    primaryModel: request.params.model,
                    fallbackModel: fallback,
                    shouldUseFallback: fallback !== null,
                },
            });
        }
    );
}

export default abTestingRoutes;
