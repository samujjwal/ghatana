/**
 * Content Generation Orchestrator
 *
 * Coordinates parallel generation of examples, simulations, and animations
 * for a claim based on its ContentNeeds analysis. Provides fallback chains,
 * cost tracking, and timeout guards.
 *
 * @doc.type class
 * @doc.purpose Orchestrate parallel content generation for claims
 * @doc.layer backend-worker
 * @doc.pattern Orchestrator
 */

import { Queue, QueueEvents, Job } from 'bullmq';
import { PrismaClient } from '@ghatana/tutorputor-db';
import { Logger } from 'pino';
import type { ContentNeeds } from '@ghatana/tutorputor-contracts/v1/learning-unit';
import { ModalitySelector, type ModalityType } from '../../utils/modality-selector';

// ============================================================================
// Types
// ============================================================================

export interface OrchestrationRequest {
    experienceId: string;
    tenantId: string;
    claimRef: string;
    claimText: string;
    gradeLevel: string;
    domain: string;
    needs: ContentNeeds;
}

export interface OrchestrationResult {
    claimRef: string;
    examples: GenerationOutcome;
    simulation: GenerationOutcome;
    animation: GenerationOutcome;
    totalCost: CostSummary;
    durationMs: number;
}

export interface GenerationOutcome {
    status: 'success' | 'failed' | 'skipped' | 'timeout';
    jobId?: string;
    error?: string;
    durationMs: number;
}

export interface CostSummary {
    totalTokens: number;
    estimatedCostUsd: number;
    breakdown: {
        examples: number;
        simulation: number;
        animation: number;
    };
}

export interface OrchestratorConfig {
    redis: {
        host: string;
        port: number;
        password?: string;
        db?: number;
    };
    /** Max time to wait for all generation jobs (ms) */
    timeoutMs: number;
    /** Max cost per orchestration in USD */
    maxCostUsd: number;
    /** Cost per 1K tokens in USD */
    costPer1kTokens: number;
}

const DEFAULT_CONFIG: Partial<OrchestratorConfig> = {
    timeoutMs: 120_000,
    maxCostUsd: 2.0,
    costPer1kTokens: 0.03,
};

// ============================================================================
// Orchestrator
// ============================================================================

export class ContentGenerationOrchestrator {
    private queue: Queue;
    private queueEvents: QueueEvents;
    private logger: Logger;
    private prisma: PrismaClient;
    private config: OrchestratorConfig;
    private modalitySelector: ModalitySelector;

    constructor(
        prisma: PrismaClient,
        logger: Logger,
        config: OrchestratorConfig,
    ) {
        this.prisma = prisma;
        this.logger = logger;
        this.config = { ...DEFAULT_CONFIG, ...config };
        this.modalitySelector = new ModalitySelector(prisma);

        const redisOpts = {
            host: config.redis.host,
            port: config.redis.port,
            password: config.redis.password,
            db: config.redis.db || 0,
        };

        this.queue = new Queue('content-generation', { connection: redisOpts });
        this.queueEvents = new QueueEvents('content-generation', { connection: redisOpts });
    }

    /**
     * Orchestrate content generation for a single claim.
     * Dispatches example, simulation, and animation jobs in parallel,
     * waits for completion (or timeout), and aggregates results.
     */
    async orchestrateForClaim(request: OrchestrationRequest): Promise<OrchestrationResult> {
        const startTime = Date.now();
        const { needs, claimRef } = request;

        this.logger.info({ claimRef, experienceId: request.experienceId }, 'Starting content orchestration');

        // Dispatch jobs in parallel based on needs
        const jobs = await this.dispatchJobs(request);

        // Wait for all jobs with timeout
        const outcomes = await this.awaitJobs(jobs);

        // Compute cost summary
        const totalCost = this.computeCost(outcomes);

        const result: OrchestrationResult = {
            claimRef,
            examples: outcomes.examples,
            simulation: outcomes.simulation,
            animation: outcomes.animation,
            totalCost,
            durationMs: Date.now() - startTime,
        };

        this.logger.info(
            {
                claimRef,
                durationMs: result.durationMs,
                examplesStatus: result.examples.status,
                simulationStatus: result.simulation.status,
                animationStatus: result.animation.status,
                totalTokens: totalCost.totalTokens,
            },
            'Content orchestration completed',
        );

        return result;
    }

    /**
     * Orchestrate content generation for all claims in an experience.
     * Processes claims sequentially to respect cost limits.
     */
    async orchestrateForExperience(
        experienceId: string,
        tenantId: string,
        claimNeeds: Array<{
            claimRef: string;
            claimText: string;
            gradeLevel: string;
            domain: string;
            needs: ContentNeeds;
        }>,
    ): Promise<OrchestrationResult[]> {
        const results: OrchestrationResult[] = [];
        let accumulatedCost = 0;

        for (const claim of claimNeeds) {
            // Cost guard: stop if approaching limit
            if (accumulatedCost >= this.config.maxCostUsd) {
                this.logger.warn(
                    { experienceId, claimRef: claim.claimRef, accumulatedCost },
                    'Cost limit reached, skipping remaining claims',
                );
                break;
            }

            const result = await this.orchestrateForClaim({
                experienceId,
                tenantId,
                ...claim,
            });

            accumulatedCost += result.totalCost.estimatedCostUsd;
            results.push(result);
        }

        return results;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private async dispatchJobs(
        request: OrchestrationRequest,
    ): Promise<{
        examples: Job | null;
        simulation: Job | null;
        animation: Job | null;
    }> {
        const { needs, experienceId, tenantId, claimRef, claimText, gradeLevel, domain } = request;

        const promises: Promise<void>[] = [];
        const jobs: { examples: Job | null; simulation: Job | null; animation: Job | null } = {
            examples: null,
            simulation: null,
            animation: null,
        };

        // Examples
        if (needs.examples.required && needs.examples.count > 0) {
            promises.push(
                this.queue
                    .add('generate-examples', {
                        experienceId,
                        tenantId,
                        claimRef,
                        claimText,
                        gradeLevel,
                        domain,
                        types: needs.examples.types,
                        count: needs.examples.count,
                    })
                    .then((job: Job) => { jobs.examples = job; }),
            );
        }

        // Simulation
        if (needs.simulation.required) {
            promises.push(
                this.queue
                    .add('generate-simulation', {
                        experienceId,
                        tenantId,
                        claimRef,
                        claimText,
                        gradeLevel,
                        domain,
                        interactionType: needs.simulation.interactionType,
                        complexity: needs.simulation.complexity,
                    })
                    .then((job: Job) => { jobs.simulation = job; }),
            );
        }

        // Animation
        if (needs.animation.required) {
            promises.push(
                this.queue
                    .add('generate-animation', {
                        experienceId,
                        tenantId,
                        claimRef,
                        claimText,
                        gradeLevel,
                        domain,
                        animationType: needs.animation.type,
                        durationSeconds: needs.animation.durationSeconds,
                        complexity: needs.animation.complexity,
                    })
                    .then((job: Job) => { jobs.animation = job; }),
            );
        }

        await Promise.all(promises);
        return jobs;
    }

    /**
     * Dispatch jobs in priority order: simulation > animation > example
     * This method can be used to prioritize certain modalities over others.
     * 
     * @param request - The orchestration request
     * @param priorityModality - The priority modality to generate first
     * @returns Jobs for the priority modality
     */
    private async dispatchPriorityJobs(
        request: OrchestrationRequest,
        priorityModality: ModalityType,
    ): Promise<{ examples: Job | null; simulation: Job | null; animation: Job | null }> {
        const { needs, experienceId, tenantId, claimRef, claimText, gradeLevel, domain } = request;

        const jobs: { examples: Job | null; simulation: Job | null; animation: Job | null } = {
            examples: null,
            simulation: null,
            animation: null,
        };

        // Generate priority modality first
        if (priorityModality === 'simulation' && needs.simulation.required) {
            jobs.simulation = await this.queue.add('generate-simulation', {
                experienceId,
                tenantId,
                claimRef,
                claimText,
                gradeLevel,
                domain,
                interactionType: needs.simulation.interactionType,
                complexity: needs.simulation.complexity,
            });
        } else if (priorityModality === 'animation' && needs.animation.required) {
            jobs.animation = await this.queue.add('generate-animation', {
                experienceId,
                tenantId,
                claimRef,
                claimText,
                gradeLevel,
                domain,
                animationType: needs.animation.type,
                durationSeconds: needs.animation.durationSeconds,
                complexity: needs.animation.complexity,
            });
        } else if (priorityModality === 'example' && needs.examples.required) {
            jobs.examples = await this.queue.add('generate-examples', {
                experienceId,
                tenantId,
                claimRef,
                claimText,
                gradeLevel,
                domain,
                types: needs.examples.types,
                count: needs.examples.count,
            });
        }

        this.logger.info(
            { experienceId, claimRef, priorityModality },
            'Dispatched priority job'
        );

        return jobs;
    }

    /**
     * Get the priority modality based on needs (simulation > animation > example)
     */
    private getPriorityModality(needs: ContentNeeds): ModalityType | null {
        if (needs.simulation.required) return 'simulation';
        if (needs.animation.required) return 'animation';
        if (needs.examples.required && needs.examples.count > 0) return 'example';
        return null;
    }

    private async awaitJobs(jobs: {
        examples: Job | null;
        simulation: Job | null;
        animation: Job | null;
    }): Promise<{
        examples: GenerationOutcome;
        simulation: GenerationOutcome;
        animation: GenerationOutcome;
    }> {
        const timeout = this.config.timeoutMs;

        const awaitOne = async (
            job: Job | null,
            label: string,
        ): Promise<GenerationOutcome> => {
            if (!job) {
                return { status: 'skipped', durationMs: 0 };
            }

            const start = Date.now();

            try {
                const result = await Promise.race([
                    job.waitUntilFinished(this.queueEvents, timeout),
                    new Promise<never>((_, reject) =>
                        setTimeout(() => reject(new Error('timeout')), timeout),
                    ),
                ]);

                return {
                    status: 'success',
                    jobId: job.id,
                    durationMs: Date.now() - start,
                };
            } catch (error: any) {
                const isTimeout = error?.message === 'timeout';
                this.logger.warn(
                    { jobId: job.id, label, error: error?.message },
                    isTimeout ? 'Job timed out' : 'Job failed',
                );

                return {
                    status: isTimeout ? 'timeout' : 'failed',
                    jobId: job.id,
                    error: error?.message,
                    durationMs: Date.now() - start,
                };
            }
        };

        // Await all three in parallel
        const [examples, simulation, animation] = await Promise.all([
            awaitOne(jobs.examples, 'examples'),
            awaitOne(jobs.simulation, 'simulation'),
            awaitOne(jobs.animation, 'animation'),
        ]);

        return { examples, simulation, animation };
    }

    private computeCost(outcomes: {
        examples: GenerationOutcome;
        simulation: GenerationOutcome;
        animation: GenerationOutcome;
    }): CostSummary {
        // Estimated token usage per generation type (conservative estimates)
        const TOKEN_ESTIMATES = {
            examples: 2000,
            simulation: 3000,
            animation: 1500,
        };

        const costPerToken = this.config.costPer1kTokens / 1000;
        let totalTokens = 0;
        const breakdown = { examples: 0, simulation: 0, animation: 0 };

        for (const key of ['examples', 'simulation', 'animation'] as const) {
            if (outcomes[key].status === 'success') {
                const tokens = TOKEN_ESTIMATES[key];
                totalTokens += tokens;
                breakdown[key] = tokens;
            }
        }

        return {
            totalTokens,
            estimatedCostUsd: Math.round(totalTokens * costPerToken * 10000) / 10000,
            breakdown,
        };
    }

    async close(): Promise<void> {
        await this.queue.close();
        await this.queueEvents.close();
    }
}

/**
 * Factory function.
 */
export function createContentGenerationOrchestrator(
    prisma: PrismaClient,
    logger: Logger,
    config: OrchestratorConfig,
): ContentGenerationOrchestrator {
    return new ContentGenerationOrchestrator(prisma, logger, config);
}
