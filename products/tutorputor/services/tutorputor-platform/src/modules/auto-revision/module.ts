/**
 * @doc.type module
 * @doc.purpose Auto-Revision module registration
 * @doc.layer product
 * @doc.pattern Module
 */

import type { FastifyPluginAsync } from 'fastify';
import { AutoRevisionService } from './service';
import { createContentStudioService } from '../content/studio/service';
import { registerAutoRevisionRoutes } from './routes';
import { AutoRevisionWorkerManager } from './workers';
import type { AutoRevisionModuleConfig } from './types';

// Auto-Revision module plugin
export const autoRevisionModule: FastifyPluginAsync = async (fastify) => {
    const { prisma } = fastify;

    // Initialize content studio service dependency
    const contentStudio = createContentStudioService(prisma, {
        openaiApiKey: process.env.OPENAI_API_KEY || '',
        model: 'gpt-4',
    });

    // Initialize auto-revision service
    const autoRevisionService = new AutoRevisionService(prisma, contentStudio);

    // Get configuration from environment or use defaults
    const config: AutoRevisionModuleConfig = {
        enabled: process.env.AUTO_REVISION_ENABLED === 'true',
        driftMonitoring: {
            enabled: process.env.AUTO_REVISION_DRIFT_MONITORING_ENABLED !== 'false',
            intervalHours: parseInt(process.env.AUTO_REVISION_DRIFT_INTERVAL_HOURS || '6', 10),
        },
        regeneration: {
            enabled: process.env.AUTO_REVISION_REGENERATION_ENABLED !== 'false',
            maxConcurrentJobs: parseInt(process.env.AUTO_REVISION_MAX_CONCURRENT_JOBS || '3', 10),
        },
        abTesting: {
            enabled: process.env.AUTO_REVISION_AB_TESTING_ENABLED !== 'false',
            minSampleSize: parseInt(process.env.AUTO_REVISION_MIN_SAMPLE_SIZE || '100', 10),
            significanceThreshold: parseFloat(process.env.AUTO_REVISION_SIGNIFICANCE_THRESHOLD || '0.05'),
        },
    };

    // Register routes
    await registerAutoRevisionRoutes(fastify, autoRevisionService);

    // Initialize and start background workers if enabled
    if (config.enabled) {
        const workerManager = new AutoRevisionWorkerManager(autoRevisionService, config, fastify.log as any);
        workerManager.start();

        // Store worker manager for graceful shutdown
        fastify.decorate('autoRevisionWorkerManager', workerManager);

        // Add health check endpoint
        fastify.get('/health', async (request, reply) => {
            const status = workerManager.getStatus();
            return {
                status: 'healthy',
                config,
                workers: status,
            };
        });

        // Add metrics endpoint
        fastify.get('/metrics', async (request, reply) => {
            const [
                driftSignalsDetected,
                regenerationsCompleted,
                abExperimentsRunning,
                pendingRegenerations,
            ] = await Promise.all([
                prisma.driftSignal.count(),
                prisma.experienceAutoRefinement.count({
                    where: { status: { in: ['reviewed', 'accepted'] } },
                }),
                prisma.abExperiment.count({
                    where: { status: 'running' },
                }),
                prisma.experienceAutoRefinement.count({
                    where: { status: 'pending' },
                }),
            ]);

            const workerStatus = workerManager.getStatus();
            const workersRunning =
                workerStatus.driftMonitor.running &&
                workerStatus.regenerationWorker.running &&
                workerStatus.abTestEvaluator.running;

            return {
                driftSignalsDetected,
                regenerationsCompleted,
                abExperimentsRunning,
                pendingRegenerations,
                systemHealth: workersRunning ? 'healthy' : 'degraded',
            };
        });

        fastify.log.info('Auto-Revision module initialized with background workers');
    } else {
        fastify.log.info('Auto-Revision module disabled');
    }

    // Decorate the app with the service for use in other modules
    fastify.decorate('autoRevisionService', autoRevisionService);
};
