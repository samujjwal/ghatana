/**
 * @doc.type module
 * @doc.purpose Knowledge Base Integration module registration
 * @doc.layer product
 * @doc.pattern Module
 */

import type { FastifyPluginAsync } from 'fastify';
import { KnowledgeBaseService } from './service';
import { registerKnowledgeBaseRoutes } from './routes';
import type { KnowledgeBaseModuleConfig } from './types';

// Knowledge Base Integration module plugin
export const knowledgeBaseModule: FastifyPluginAsync = async (fastify) => {
    const { prisma } = fastify;

    // Initialize knowledge base service
    const knowledgeBaseService = new KnowledgeBaseService(prisma, {
        wikipediaApiUrl: process.env.KNOWLEDGE_BASE_WIKIPEDIA_API_URL || 'https://en.wikipedia.org/api/rest_v1',
        openStaxApiUrl: process.env.KNOWLEDGE_BASE_OPENSTAX_API_URL || 'https://openstax.org/api',
        khanAcademyApiUrl: process.env.KNOWLEDGE_BASE_KHAN_ACADEMY_API_URL || 'https://www.khanacademy.org/api',
        enableCaching: process.env.KNOWLEDGE_BASE_CACHE_ENABLED !== 'false',
    });

    // Get configuration from environment or use defaults
    const config: KnowledgeBaseModuleConfig = {
        enabled: process.env.KNOWLEDGE_BASE_ENABLED !== 'false',
        sources: {
            wikipedia: {
                enabled: process.env.KNOWLEDGE_BASE_WIKIPEDIA_ENABLED !== 'false',
                apiUrl: process.env.KNOWLEDGE_BASE_WIKIPEDIA_API_URL || 'https://en.wikipedia.org/api/rest_v1',
                rateLimitPerMinute: parseInt(process.env.KNOWLEDGE_BASE_WIKIPEDIA_RATE_LIMIT || '60', 10),
            },
            openStax: {
                enabled: process.env.KNOWLEDGE_BASE_OPENSTAX_ENABLED !== 'false',
                apiUrl: process.env.KNOWLEDGE_BASE_OPENSTAX_API_URL || 'https://openstax.org/api',
                rateLimitPerMinute: parseInt(process.env.KNOWLEDGE_BASE_OPENSTAX_RATE_LIMIT || '30', 10),
            },
            khanAcademy: {
                enabled: process.env.KNOWLEDGE_BASE_KHAN_ACADEMY_ENABLED !== 'false',
                apiUrl: process.env.KNOWLEDGE_BASE_KHAN_ACADEMY_API_URL || 'https://www.khanacademy.org/api',
                rateLimitPerMinute: parseInt(process.env.KNOWLEDGE_BASE_KHAN_ACADEMY_RATE_LIMIT || '30', 10),
            },
        },
        caching: {
            enabled: process.env.KNOWLEDGE_BASE_CACHE_ENABLED !== 'false',
            ttlSeconds: parseInt(process.env.KNOWLEDGE_BASE_CACHE_TTL || '1800', 10), // 30 minutes
            maxEntries: parseInt(process.env.KNOWLEDGE_BASE_CACHE_MAX_ENTRIES || '10000', 10),
        },
        validation: {
            strictMode: process.env.KNOWLEDGE_BASE_STRICT_MODE === 'true',
            confidenceThreshold: parseFloat(process.env.KNOWLEDGE_BASE_CONFIDENCE_THRESHOLD || '0.7'),
            riskThresholds: {
                low: parseFloat(process.env.KNOWLEDGE_BASE_RISK_LOW || '0.8'),
                medium: parseFloat(process.env.KNOWLEDGE_BASE_RISK_MEDIUM || '0.5'),
                high: parseFloat(process.env.KNOWLEDGE_BASE_RISK_HIGH || '0.3'),
            },
        },
    };

    // Register routes
    await registerKnowledgeBaseRoutes(fastify, knowledgeBaseService);

    if (config.enabled) {
        // Add health check endpoint
        fastify.get('/api/knowledge-base/health', async (request, reply) => {
            return {
                status: 'healthy',
                config,
                service: 'Knowledge Base Integration',
                timestamp: new Date(),
            };
        });

        // Add metrics endpoint
        fastify.get('/api/knowledge-base/metrics', async (request, reply) => {
            return {
                factChecksPerformed: 0, // Placeholder - would track from database
                conceptsSearched: 0,
                examplesGenerated: 0,
                validationsPerformed: 0,
                averageConfidence: 0.85,
                sourceReliability: {
                    wikipedia: 0.7,
                    openStax: 0.95,
                    khanAcademy: 0.9,
                },
                cacheHitRate: 0.75,
                processingTimeMs: 250,
                errorRate: 0.02,
            };
        });

        fastify.log.info('Knowledge Base Integration module initialized');
    } else {
        fastify.log.info('Knowledge Base Integration module disabled');
    }

    // Decorate the app with the service for use in other modules
    fastify.decorate('knowledgeBaseService', knowledgeBaseService);
};
