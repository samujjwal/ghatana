/**
 * @doc.type module
 * @doc.purpose Knowledge Base Integration API routes
 * @doc.layer product
 * @doc.pattern Routes
 */

import type { FastifyInstance, FastifyRequest } from 'fastify';
import type { KnowledgeBaseService } from './service';

export function registerKnowledgeBaseRoutes(
    fastify: FastifyInstance,
    knowledgeBaseService: KnowledgeBaseService
): void {

    // Verify a factual claim
    fastify.post('/api/knowledge-base/verify-fact', async (request, reply) => {
        const factRequest = request.body as {
            claim: string;
            domain: string;
            context?: {
                gradeRange?: string;
                subject?: string;
                relatedConcepts?: string[];
            };
        };

        try {
            const result = await knowledgeBaseService.verifyFact(factRequest);
            return { success: true, data: result };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to verify fact' };
        }
    });

    // Search for concepts
    fastify.get('/api/knowledge-base/concepts/search', async (request, reply) => {
        const { query, domain } = request.query as { query: string; domain: string };

        try {
            const concepts = await knowledgeBaseService.searchConcept(query, domain);
            return { success: true, data: concepts };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to search concepts' };
        }
    });

    // Find examples for a concept
    fastify.get('/api/knowledge-base/examples/:concept', async (request, reply) => {
        const { concept } = request.params as { concept: string };
        const { domain, gradeRange } = request.query as { domain: string; gradeRange?: string };

        try {
            const examples = await knowledgeBaseService.findExamples(concept, domain, gradeRange);
            return { success: true, data: examples };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to find examples' };
        }
    });

    // Get curriculum alignment
    fastify.get('/api/knowledge-base/curriculum/:concept', async (request, reply) => {
        const { concept } = request.params as { concept: string };
        const { domain } = request.query as { domain: string };

        try {
            const standards = await knowledgeBaseService.getCurriculumAlignment(concept, domain);
            return { success: true, data: standards };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to get curriculum alignment' };
        }
    });

    // Validate content
    fastify.post('/api/knowledge-base/validate', async (request, reply) => {
        const validationRequest = request.body as {
            content: string;
            contentType: 'claim' | 'example' | 'explanation' | 'task';
            domain: string;
            gradeRange: string;
            context?: {
                learningObjectives?: string[];
                prerequisites?: string[];
            };
        };

        try {
            const result = await knowledgeBaseService.validateContent(validationRequest);
            return { success: true, data: result };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to validate content' };
        }
    });

    // Batch fact verification
    fastify.post('/api/knowledge-base/batch-verify', async (request, reply) => {
        const { claims } = request.body as { claims: Array<{ claim: string; domain: string }> };

        try {
            const results = [];
            for (const claimRequest of claims) {
                const result = await knowledgeBaseService.verifyFact(claimRequest);
                results.push({ claim: claimRequest.claim, result });
            }
            return { success: true, data: results };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to batch verify facts' };
        }
    });

    // Get knowledge base statistics
    fastify.get('/api/knowledge-base/stats', async (request, reply) => {
        try {
            // This would return statistics about the knowledge base
            return {
                success: true,
                data: {
                    totalConcepts: 1000, // Placeholder
                    totalSources: 50, // Placeholder
                    averageConfidence: 0.85,
                    lastUpdated: new Date(),
                    domains: ['math', 'science', 'physics', 'chemistry', 'biology'],
                },
            };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to get statistics' };
        }
    });

    // Health check
    fastify.get('/api/knowledge-base/health', async (request, reply) => {
        return {
            success: true,
            status: 'healthy',
            service: 'Knowledge Base Integration',
            timestamp: new Date(),
        };
    });
}
