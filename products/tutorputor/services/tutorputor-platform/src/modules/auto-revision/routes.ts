/**
 * @doc.type module
 * @doc.purpose Auto-Revision API routes
 * @doc.layer product
 * @doc.pattern Routes
 */

import type { FastifyInstance, FastifyRequest } from 'fastify';
import type { AutoRevisionService } from './service';

export interface AutoRevisionRoutes {
    // Drift detection endpoints
    getDriftSignals: (request: FastifyRequest<{ Params: { experienceId: string } }>) => Promise<any>;
    monitorAllDrift: (request: FastifyRequest) => Promise<any>;

    // Regeneration endpoints
    queueRegeneration: (request: FastifyRequest<{ Params: { experienceId: string } }>) => Promise<any>;
    processRegenerationQueue: (request: FastifyRequest) => Promise<any>;

    // A/B testing endpoints
    createABExperiment: (request: FastifyRequest<{ Params: { experienceId: string } }>) => Promise<any>;
    evaluateABExperiments: (request: FastifyRequest) => Promise<any>;

    // Analytics endpoints
    getRegenerationHistory: (request: FastifyRequest<{ Params: { experienceId: string } }>) => Promise<any>;
    getABExperimentResults: (request: FastifyRequest<{ Params: { experimentId: string } }>) => Promise<any>;
}

export function registerAutoRevisionRoutes(
    fastify: FastifyInstance,
    autoRevisionService: AutoRevisionService
): void {

    // Get drift signals for a specific experience
    fastify.get('/experiences/:experienceId/drift', async (request, reply) => {
        const { experienceId } = request.params as { experienceId: string };

        try {
            const signals = await autoRevisionService.detectDrift(experienceId);
            return { success: true, data: signals };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to detect drift signals' };
        }
    });

    // Monitor drift for all active experiences
    fastify.post('/monitor-drift', async (request, reply) => {
        try {
            const candidates = await autoRevisionService.monitorDrift();
            return { success: true, data: candidates };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to monitor drift' };
        }
    });

    // Queue experience for regeneration
    fastify.post('/experiences/:experienceId/regenerate', async (request, reply) => {
        const { experienceId } = request.params as { experienceId: string };

        try {
            const signals = await autoRevisionService.detectDrift(experienceId);
            await autoRevisionService.queueExperienceRegeneration(experienceId, signals);
            return { success: true, message: 'Regeneration queued' };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to queue regeneration' };
        }
    });

    // Process regeneration queue
    fastify.post('/process-queue', async (request, reply) => {
        try {
            await autoRevisionService.processRegenerationQueue();
            return { success: true, message: 'Regeneration queue processed' };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to process regeneration queue' };
        }
    });

    // Create A/B experiment
    fastify.post('/experiences/:experienceId/ab-experiment', async (request, reply) => {
        const { experienceId } = request.params as { experienceId: string };
        const { treatmentVersion } = request.body as { treatmentVersion: number };

        try {
            const experiment = await autoRevisionService.createABExperiment(
                experienceId,
                Number.isFinite(treatmentVersion) ? treatmentVersion : 2,
            );
            return { success: true, data: experiment };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to create A/B experiment' };
        }
    });

    // Evaluate A/B experiments
    fastify.post('/evaluate-ab-experiments', async (request, reply) => {
        try {
            await autoRevisionService.evaluateABExperiments();
            return { success: true, message: 'A/B experiments evaluated' };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to evaluate A/B experiments' };
        }
    });

    // Get regeneration history for an experience
    fastify.get('/experiences/:experienceId/history', async (request, reply) => {
        const { experienceId } = request.params as { experienceId: string };

        try {
            const history = await autoRevisionService.getRegenerationHistory(experienceId);
            return { success: true, data: history };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to get regeneration history' };
        }
    });

    // Get A/B experiment results
    fastify.get('/ab-experiments/:experimentId/results', async (request, reply) => {
        const { experimentId } = request.params as { experimentId: string };

        try {
            const result = await autoRevisionService.getABExperimentResults(experimentId);
            return { success: true, data: result };
        } catch (error) {
            fastify.log.error(error);
            reply.code(500);
            return { success: false, error: 'Failed to get experiment results' };
        }
    });
}
