/**
 * Claim Generation Processor - Generates learning claims from topics.
 *
 * This processor:
 * 1. Receives job from queue
 * 2. Calls Java AI agent via gRPC to generate claims
 * 3. If content_needs not populated, calls AnalyzeContentNeeds
 * 4. Stores claims in database
 * 5. Queues follow-up jobs for examples/simulations
 *
 * @doc.type class
 * @doc.purpose Process claim generation jobs with deterministic content needs
 * @doc.layer backend-worker
 * @doc.pattern JobProcessor
 */

import { Job, Queue } from 'bullmq';
import { PrismaClient, type Prisma } from '@tutorputor/core/db';
import { Logger } from 'pino';
import { RealContentGenerationClient, type ContentNeeds } from '../grpc/RealContentGenerationClient';
import * as crypto from 'crypto';
import {
    type CorrelatedGenerationJobData,
    ContentWorkerTelemetryPublisher,
} from '../generation-telemetry';
import { loadFeatureFlags, type ContentGenerationFlags } from '../../../config/feature-flags';

export interface ClaimGenerationJobData extends CorrelatedGenerationJobData {
    experienceId: string;
    tenantId: string;
    topic: string;
    title: string;
    domain: string;
    gradeLevel: string;
    targetGrades: string[];
    maxClaims: number;
}

interface GrpcClaim {
    claim_ref: string;
    text: string;
    bloom_level: string;
    order_index: number;
    content_needs?: ContentNeeds;
}

export class ClaimGenerationProcessor {
    private featureFlags: ContentGenerationFlags;

    constructor(
        private grpcClient: RealContentGenerationClient,
        private prisma: PrismaClient,
        private queue: Queue,
        private logger: Logger,
        private telemetry?: ContentWorkerTelemetryPublisher,
        featureFlags?: ContentGenerationFlags,
    ) {
        this.featureFlags = featureFlags ?? loadFeatureFlags();
    }

    async process(job: Job<ClaimGenerationJobData>): Promise<void> {
        const {
            experienceId,
            tenantId,
            topic,
            domain,
            gradeLevel,
            maxClaims,
            title,
            targetGrades = [],
        } = job.data;

        this.logger.info(
            { jobId: job.id, experienceId, topic },
            'Processing claim generation job'
        );

        try {
            await this.telemetry?.publishForJob(job, {
                stage: 'grpc_request_started',
                message: 'Submitting claim generation request',
                progressPercent: 15,
                status: 'running',
            });

            // Step 1: Call Java agent to generate claims
            const requestId = crypto.randomUUID();
            const response = await this.grpcClient.generateClaims({
                requestId,
                tenantId,
                topic,
                gradeLevel,
                domain,
                maxClaims,
                context: {
                    title,
                    targetGrades: targetGrades.join(','),
                },
            });

            this.logger.info(
                { jobId: job.id, claimsCount: response.claims?.length || 0 },
                'Claims generated successfully'
            );

            const responseCost = ContentWorkerTelemetryPublisher.extractCostFromMetadata(response.metadata);
            await this.telemetry?.publishForJob(job, {
                stage: 'grpc_response_received',
                message: 'Claim generation response received',
                progressPercent: 45,
                status: 'running',
                ...(responseCost ? { cost: responseCost } : {}),
                diagnostics: {
                    claimsCount: response.claims?.length || 0,
                    confidenceScore: response.validation?.confidence_score || 0,
                },
            });

            // Step 2: Update experience with metadata
            await this.prisma.learningExperience.update({
                where: { id: experienceId },
                data: {
                    riskLevel: response.validation?.confidence_score > 0.8 ? 'LOW' : 'MEDIUM',
                    confidenceScore: response.validation?.confidence_score || 0,
                    promptHash: response.metadata?.prompt_hash || '',
                },
            });

            // Step 3: Process and store claims with content needs
            const processedClaims = await this.processClaimsWithContentNeeds(
                response.claims || [],
                { experienceId, tenantId, domain, gradeLevel, job }
            );

            for (const { claim, contentNeeds } of processedClaims) {
                const bloomLevel = this.mapBloomLevel(claim.bloom_level);

                await this.prisma.learningClaim.upsert({
                    where: {
                        experienceId_claimRef: {
                            experienceId,
                            claimRef: claim.claim_ref,
                        },
                    },
                    create: {
                        experienceId,
                        claimRef: claim.claim_ref,
                        text: claim.text,
                        bloomLevel: bloomLevel as 'REMEMBER' | 'UNDERSTAND' | 'APPLY' | 'ANALYZE' | 'EVALUATE' | 'CREATE',
                        orderIndex: claim.order_index,
                        contentNeeds: contentNeeds as unknown as Prisma.InputJsonValue,
                    },
                    update: {
                        text: claim.text,
                        bloomLevel: bloomLevel as 'REMEMBER' | 'UNDERSTAND' | 'APPLY' | 'ANALYZE' | 'EVALUATE' | 'CREATE',
                        orderIndex: claim.order_index,
                        contentNeeds: contentNeeds as unknown as Prisma.InputJsonValue,
                    },
                });

                this.logger.info(
                    { jobId: job.id, claimRef: claim.claim_ref },
                    'Claim stored in database'
                );

                // Step 4: Queue follow-up jobs based on content needs
                await this.queueFollowUpJobs({
                    claim,
                    contentNeeds,
                    experienceId,
                    tenantId,
                    domain,
                    gradeLevel,
                    generationRequestId: job.data.generationRequestId,
                    job,
                });
            }

            await this.telemetry?.publishForJob(job, {
                stage: 'claims_persisted',
                message: 'Claims persisted and follow-up jobs queued',
                progressPercent: 90,
                status: 'running',
                diagnostics: {
                    claimsCount: response.claims?.length || 0,
                    experienceId,
                },
            });
        } catch (error: unknown) {
            this.logger.error({ err: error, jobId: job.id }, 'Failed to process claim generation');
            throw error;
        }
    }

    private mapBloomLevel(value: unknown): string {
        if (typeof value === 'number') {
            const byIndex = [
                'REMEMBER',
                'REMEMBER',
                'UNDERSTAND',
                'APPLY',
                'ANALYZE',
                'EVALUATE',
                'CREATE',
            ];
            return byIndex[value] || 'UNDERSTAND';
        }

        const asString = String(value || '').toUpperCase();
        const allowed = ['REMEMBER', 'UNDERSTAND', 'APPLY', 'ANALYZE', 'EVALUATE', 'CREATE'];
        return allowed.includes(asString) ? asString : 'UNDERSTAND';
    }

    /**
     * Process claims and ensure content needs are populated.
     * If GenerateClaims doesn't return content_needs, calls AnalyzeContentNeeds.
     */
    private async processClaimsWithContentNeeds(
        claims: GrpcClaim[],
        context: {
            experienceId: string;
            tenantId: string;
            domain: string;
            gradeLevel: string;
            job: Job<ClaimGenerationJobData>;
        }
    ): Promise<Array<{ claim: GrpcClaim; contentNeeds: ContentNeeds }>> {
        const results: Array<{ claim: GrpcClaim; contentNeeds: ContentNeeds }> = [];

        for (const claim of claims) {
            let contentNeeds = claim.content_needs;

            // If content_needs is missing or incomplete, call AnalyzeContentNeeds (if enabled)
            if (!contentNeeds || !this.isValidContentNeeds(contentNeeds)) {
                if (this.featureFlags.enableAutoContentNeedsAnalysis) {
                    this.logger.info(
                        { claimRef: claim.claim_ref },
                        'Content needs not populated, calling AnalyzeContentNeeds'
                    );

                    try {
                        const analysisRequestId = crypto.randomUUID();
                        const analysisResponse = await this.grpcClient.analyzeContentNeeds({
                            requestId: analysisRequestId,
                            tenantId: context.tenantId,
                            claimText: claim.text,
                            bloomLevel: claim.bloom_level,
                            domain: context.domain,
                            gradeLevel: context.gradeLevel,
                            context: {},
                        });

                        contentNeeds = analysisResponse.contentNeeds;

                        this.logger.info(
                            { claimRef: claim.claim_ref },
                            'Content needs analysis completed'
                        );
                    } catch (error) {
                        this.logger.warn(
                            { err: error, claimRef: claim.claim_ref },
                            'Failed to analyze content needs, using defaults'
                        );

                        // Provide default content needs as fallback
                        contentNeeds = this.getDefaultContentNeeds(context.domain);
                    }
                } else {
                    this.logger.info(
                        { claimRef: claim.claim_ref },
                        'Auto content needs analysis disabled, using defaults'
                    );
                    contentNeeds = this.getDefaultContentNeeds(context.domain);
                }
            }

            results.push({ claim, contentNeeds: contentNeeds! });
        }

        return results;
    }

    /**
     * Check if content needs is valid (has at least one modality requirement).
     */
    private isValidContentNeeds(contentNeeds: ContentNeeds): boolean {
        return Boolean(
            contentNeeds.examples?.required ||
            contentNeeds.simulation?.required ||
            contentNeeds.animation?.required
        );
    }

    /**
     * Get default content needs based on domain.
     */
    private getDefaultContentNeeds(domain: string): ContentNeeds {
        const upperDomain = domain.toUpperCase();

        // STEM domains benefit from simulations
        if (['MATH', 'SCIENCE', 'PHYSICS', 'CHEMISTRY', 'BIOLOGY'].includes(upperDomain)) {
            return {
                examples: { required: true, types: ['REAL_WORLD', 'PROBLEM_SOLVING'], count: 2, necessity: 0.8, rationale: 'Default: STEM domain requires worked examples' },
                simulation: { required: true, interactionType: 'PARAMETER_EXPLORATION', complexity: 'MEDIUM', necessity: 0.7, rationale: 'Default: STEM domain benefits from simulations' },
                animation: { required: false, animationType: 'TWO_D', durationSeconds: 30, necessity: 0.3, rationale: 'Default: Optional animation support' },
            };
        }

        // Default for other domains
        return {
            examples: { required: true, types: ['REAL_WORLD'], count: 2, necessity: 0.8, rationale: 'Default: Examples support learning' },
            simulation: { required: false, interactionType: 'PARAMETER_EXPLORATION', complexity: 'MEDIUM', necessity: 0.3, rationale: 'Default: Simulation optional' },
            animation: { required: false, animationType: 'TWO_D', durationSeconds: 30, necessity: 0.2, rationale: 'Default: Animation optional' },
        };
    }

    /**
     * Queue follow-up generation jobs based on content needs.
     */
    private async queueFollowUpJobs(params: {
        claim: GrpcClaim;
        contentNeeds: ContentNeeds;
        experienceId: string;
        tenantId: string;
        domain: string;
        gradeLevel: string;
        generationRequestId: string | undefined;
        job: Job<ClaimGenerationJobData>;
    }): Promise<void> {
        const { claim, contentNeeds, experienceId, tenantId, domain, gradeLevel, generationRequestId, job } = params;

        // Queue example generation if needed
        if (contentNeeds.examples?.required) {
            await this.queue.add('generate-examples', {
                experienceId,
                tenantId,
                claimRef: claim.claim_ref,
                claimText: claim.text,
                gradeLevel,
                domain,
                types: contentNeeds.examples.types || ['REAL_WORLD'],
                count: contentNeeds.examples.count || 2,
                generationRequestId,
            }, {
                jobId: `generate-examples:${experienceId}:${claim.claim_ref}`,
            });

            this.logger.info(
                { jobId: job.id, claimRef: claim.claim_ref },
                'Queued example generation job'
            );
        }

        // Queue simulation generation if needed
        if (contentNeeds.simulation?.required) {
            await this.queue.add('generate-simulation', {
                experienceId,
                tenantId,
                claimRef: claim.claim_ref,
                claimText: claim.text,
                gradeLevel,
                domain,
                interactionType: contentNeeds.simulation.interactionType || 'PARAMETER_EXPLORATION',
                complexity: contentNeeds.simulation.complexity || 'MEDIUM',
                generationRequestId,
            }, {
                jobId: `generate-simulation:${experienceId}:${claim.claim_ref}`,
            });

            this.logger.info(
                { jobId: job.id, claimRef: claim.claim_ref },
                'Queued simulation generation job'
            );
        }

        // Queue animation generation if needed AND feature flag is enabled
        if (contentNeeds.animation?.required && this.featureFlags.enableAnimationGeneration) {
            await this.queue.add('generate-animation', {
                experienceId,
                tenantId,
                claimRef: claim.claim_ref,
                claimText: claim.text,
                animationType: contentNeeds.animation.animationType || 'TWO_D',
                durationSeconds: contentNeeds.animation.durationSeconds || 30,
                domain,
                gradeLevel,
                generationRequestId,
            }, {
                jobId: `generate-animation:${experienceId}:${claim.claim_ref}`,
            });

            this.logger.info(
                { jobId: job.id, claimRef: claim.claim_ref },
                'Queued animation generation job'
            );
        } else if (contentNeeds.animation?.required && !this.featureFlags.enableAnimationGeneration) {
            this.logger.info(
                { jobId: job.id, claimRef: claim.claim_ref },
                'Animation generation skipped - feature disabled in Phase 0'
            );
        }
    }
}
