/**
 * @doc.type module
 * @doc.purpose Content Needs Analyzer module registration
 * @doc.layer product
 * @doc.pattern Module
 */

import type { FastifyPluginAsync } from 'fastify';
import { ContentNeedsAnalyzer } from './service';
import { createContentStudioService } from '../content/studio/service';
import { registerContentNeedsRoutes } from './routes';
import type { ContentNeedsModuleConfig } from './types';

// Content Needs Analyzer module plugin
export const contentNeedsModule: FastifyPluginAsync = async (fastify) => {
    const { prisma } = fastify;

    // Initialize content studio service dependency
    const contentStudio = createContentStudioService(prisma, {
        openaiApiKey: process.env.OPENAI_API_KEY || '',
        model: 'gpt-4',
    });

    // Initialize content needs analyzer
    const contentNeedsAnalyzer = new ContentNeedsAnalyzer(prisma, contentStudio);

    // Get configuration from environment or use defaults
    const config: ContentNeedsModuleConfig = {
        enabled: process.env.CONTENT_NEEDS_ENABLED !== 'false',
        aiIntegration: {
            enabled: process.env.CONTENT_NEEDS_AI_ENABLED !== 'false',
            model: process.env.CONTENT_NEEDS_AI_MODEL || 'gpt-4',
            maxTokens: parseInt(process.env.CONTENT_NEEDS_AI_MAX_TOKENS || '2000', 10),
            temperature: parseFloat(process.env.CONTENT_NEEDS_AI_TEMPERATURE || '0.7'),
        },
        caching: {
            enabled: process.env.CONTENT_NEEDS_CACHE_ENABLED !== 'false',
            ttlSeconds: parseInt(process.env.CONTENT_NEEDS_CACHE_TTL || '3600', 10),
        },
        analytics: {
            enabled: process.env.CONTENT_NEEDS_ANALYTICS_ENABLED !== 'false',
            trackUsage: process.env.CONTENT_NEEDS_TRACK_USAGE !== 'false',
        },
    };

    // Register routes
    await registerContentNeedsRoutes(fastify, contentNeedsAnalyzer);

    if (config.enabled) {
        // Add health check endpoint
        fastify.get('/health', async (request, reply) => {
            return {
                status: 'healthy',
                config,
                service: 'Content Needs Analyzer',
            };
        });

        // Add metrics endpoint
        fastify.get('/metrics', async (request, reply) => {
            const [
                analysesPerformed,
                exampleCount,
                simulationCount,
                animationCount,
                domainBreakdown,
                bloomBreakdown,
            ] = await Promise.all([
                prisma.learningClaim.count({
                    where: { contentNeeds: { not: null } },
                }),
                prisma.claimExample.count(),
                prisma.claimSimulation.count(),
                prisma.claimAnimation.count(),
                prisma.learningExperience.groupBy({
                    by: ['domain'],
                    _count: { _all: true },
                }),
                prisma.learningClaim.groupBy({
                    by: ['bloomLevel'],
                    where: { contentNeeds: { not: null } },
                    _count: { _all: true },
                }),
            ]);

            const domainDistribution = domainBreakdown.reduce<Record<string, number>>(
                (acc, row) => {
                    acc[row.domain] = row._count._all;
                    return acc;
                },
                {},
            );

            const bloomLevelDistribution = bloomBreakdown.reduce<Record<string, number>>(
                (acc, row) => {
                    acc[row.bloomLevel] = row._count._all;
                    return acc;
                },
                {},
            );

            return {
                analysesPerformed,
                contentGenerated: exampleCount + simulationCount + animationCount,
                averageConfidence: null,
                domainDistribution,
                bloomLevelDistribution,
                processingTimeMs: null,
                cacheHitRate: null,
            };
        });

        fastify.log.info('Content Needs Analyzer module initialized');
    } else {
        fastify.log.info('Content Needs Analyzer module disabled');
    }

    // Decorate the app with the service for use in other modules
    fastify.decorate('contentNeedsAnalyzer', contentNeedsAnalyzer);
};
