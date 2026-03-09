/**
 * @doc.type module
 * @doc.purpose Content Needs Analyzer API routes
 * @doc.layer product
 * @doc.pattern Routes
 */

import type { FastifyInstance, FastifyRequest } from 'fastify';
import type { ContentNeedsAnalyzer } from './service';

export function registerContentNeedsRoutes(
    fastify: FastifyInstance,
    contentNeedsAnalyzer: ContentNeedsAnalyzer
): void {

    // Analyze content needs for a specific claim
    fastify.post('/analyze-claim', async (request, reply) => {
        const { claim, context } = request.body as {
            claim: { id: string; text: string; bloomLevel: string };
            context: {
                domain: string;
                gradeRange: string;
                subject: string;
                topic: string;
                prerequisites: string[];
                learningObjectives: string[];
            };
        };

        try {
            const needs = await contentNeedsAnalyzer.analyzeClaimNeeds(claim, context);
            return { success: true, data: needs };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to analyze claim needs' };
        }
    });

    // Analyze content needs for entire experience
    fastify.post('/analyze-experience/:experienceId', async (request, reply) => {
        const { experienceId } = request.params as { experienceId: string };

        try {
            const analyses = await contentNeedsAnalyzer.analyzeExperienceNeeds(experienceId);
            return { success: true, data: analyses };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to analyze experience needs' };
        }
    });

    // Generate content based on analyzed needs
    fastify.post('/generate-content/:claimId', async (request, reply) => {
        const { claimId } = request.params as { claimId: string };
        const { needs } = request.body as { needs: any };

        try {
            const content = await contentNeedsAnalyzer.generateContentForClaim(claimId, needs);
            return { success: true, data: content };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to generate content' };
        }
    });

    // Get content needs analysis history
    fastify.get('/experience/:experienceId/history', async (request, reply) => {
        const { experienceId } = request.params as { experienceId: string };

        try {
            const history = await contentNeedsAnalyzer.getAnalysisHistory(experienceId);
            return { success: true, data: history };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to get analysis history' };
        }
    });

    // Batch analyze multiple claims
    fastify.post('/batch-analyze', async (request, reply) => {
        const { claims, context } = request.body as {
            claims: { id: string; text: string; bloomLevel: string }[];
            context: {
                domain: string;
                gradeRange: string;
                subject: string;
                topic: string;
                prerequisites: string[];
                learningObjectives: string[];
            };
        };

        try {
            const results = [];
            for (const claim of claims) {
                const needs = await contentNeedsAnalyzer.analyzeClaimNeeds(claim, context);
                results.push({ claimId: claim.id, needs });
            }
            return { success: true, data: results };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to batch analyze claims' };
        }
    });
}
