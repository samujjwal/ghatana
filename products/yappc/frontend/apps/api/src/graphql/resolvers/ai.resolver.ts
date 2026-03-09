/**
 * AI GraphQL Resolver for AI-powered features.
 *
 * Provides GraphQL queries and mutations for AI insights, predictions,
 * anomalies, and copilot interactions.
 *
 * @module graphql/resolvers/ai
 * @doc.type class
 * @doc.purpose AI GraphQL resolver
 * @doc.layer product
 * @doc.pattern Resolver
 */

import { AIService, createAIService } from '../../services/ai/ai.service';
import type {
    InsightFilter,
    PredictionFilter,
    AnomalyFilter,
    CopilotMessageInput,
} from '../../services/ai/ai.service';
import { getPrismaClient, type PrismaClient } from '../../database/client';

// Lazy-load services to avoid blocking initialization
let aiService: AIService | null = null;

function getPrisma(): PrismaClient {
    return getPrismaClient();
}

function getAIService(): AIService {
    if (!aiService) {
        aiService = createAIService(getPrisma());
    }
    return aiService;
}

/**
 * GraphQL Context with user info
 */
interface GraphQLContext {
    userId: string;
    workspaceId?: string;
    permissions?: string[];
}

/**
 * AI Resolver
 */
export const aiResolvers = {
    Query: {
        /**
         * Get AI insights
         */
        async aiInsights(
            _parent: unknown,
            args: { filter?: InsightFilter },
            context: GraphQLContext
        ) {
            if (!context.userId) {
                throw new Error('Unauthorized');
            }

            return getAIService().getInsights(args.filter || {});
        },

        /**
         * Get predictions
         */
        async predictions(
            _parent: unknown,
            args: { filter?: PredictionFilter },
            context: GraphQLContext
        ) {
            if (!context.userId) {
                throw new Error('Unauthorized');
            }

            return getAIService().getPredictions(args.filter || {});
        },

        /**
         * Get anomaly alerts
         */
        async anomalies(
            _parent: unknown,
            args: { filter?: AnomalyFilter },
            context: GraphQLContext
        ) {
            if (!context.userId) {
                throw new Error('Unauthorized');
            }

            return getAIService().getAnomalies(args.filter || {});
        },

        /**
         * Get copilot session
         */
        async copilotSession(
            _parent: unknown,
            args: { sessionId: string },
            context: GraphQLContext
        ) {
            if (!context.userId) {
                throw new Error('Unauthorized');
            }

            const session = await getAIService().getCopilotSession(args.sessionId);

            // Verify session belongs to user
            if (session && session.userId !== context.userId) {
                throw new Error('Forbidden');
            }

            return session;
        },

        /**
         * Get AI metrics summary
         */
        async aiMetricsSummary(
            _parent: unknown,
            args: { timeRangeHours?: number },
            context: GraphQLContext
        ) {
            if (!context.userId) {
                throw new Error('Unauthorized');
            }

            // Check if user has admin permissions
            if (!context.permissions?.includes('admin')) {
                throw new Error('Forbidden: Admin access required');
            }

            const hoursAgo = args.timeRangeHours || 24;
            const start = new Date(Date.now() - hoursAgo * 60 * 60 * 1000);
            const end = new Date();

            return getAIService().getMetricsSummary({ start, end });
        },
    },

    Mutation: {
        /**
         * Acknowledge anomaly
         */
        async acknowledgeAnomaly(
            _parent: unknown,
            args: { anomalyId: string },
            context: GraphQLContext
        ) {
            if (!context.userId) {
                throw new Error('Unauthorized');
            }

            return getAIService().acknowledgeAnomaly(args.anomalyId, context.userId);
        },

        /**
         * Send message to AI Copilot
         */
        async sendCopilotMessage(
            _parent: unknown,
            args: {
                input: {
                    sessionId: string;
                    message: string;
                    context?: Record<string, unknown>;
                };
            },
            context: GraphQLContext
        ) {
            if (!context.userId) {
                throw new Error('Unauthorized');
            }

            const input: CopilotMessageInput = {
                ...args.input,
                userId: context.userId,
            };

            const result = await getAIService().sendCopilotMessage(input);

            // Track metric
            await getAIService().trackMetric({
                agentName: 'CopilotAgent',
                model: 'gpt-4-turbo',
                operation: 'chat',
                tokensUsed: result.tokensUsed,
                latencyMs: 0, // NOTE: Track actual latency
                costUSD: result.tokensUsed * 0.00003, // Estimate
                success: true,
                userId: context.userId,
                sessionId: result.sessionId,
            });

            return {
                sessionId: result.sessionId,
                message: result.response,
                tokensUsed: result.tokensUsed,
            };
        },

        /**
         * Mark anomaly as false positive
         */
        async markAnomalyFalsePositive(
            _parent: unknown,
            args: { anomalyId: string },
            context: GraphQLContext
        ) {
            if (!context.userId) {
                throw new Error('Unauthorized');
            }

            return getPrisma().anomalyAlert.update({
                where: { id: args.anomalyId },
                data: {
                    falsePositive: true,
                    resolvedAt: new Date(),
                    acknowledged: true,
                    acknowledgedBy: context.userId,
                },
            });
        },

        /**
         * Rate copilot session
         */
        async rateCopilotSession(
            _parent: unknown,
            args: { sessionId: string; rating: number },
            context: GraphQLContext
        ) {
            if (!context.userId) {
                throw new Error('Unauthorized');
            }

            if (args.rating < 1 || args.rating > 5) {
                throw new Error('Rating must be between 1 and 5');
            }

            const session = await getPrisma().copilotSession.findUnique({
                where: { id: args.sessionId },
            });

            if (!session || session.userId !== context.userId) {
                throw new Error('Session not found or unauthorized');
            }

            return getPrisma().copilotSession.update({
                where: { id: args.sessionId },
                data: {
                    satisfactionRating: args.rating,
                    endedAt: new Date(),
                },
            });
        },
    },
};

export default aiResolvers;
