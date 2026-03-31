/**
 * Claim Generation Processor - Generates learning claims from topics.
 * 
 * This processor:
 * 1. Receives job from queue
 * 2. Calls Java AI agent via gRPC
 * 3. Stores claims in database
 * 4. Queues follow-up jobs for examples/simulations
 * 
 * @doc.type class
 * @doc.purpose Process claim generation jobs
 * @doc.layer backend-worker
 * @doc.pattern JobProcessor
 */

import { Job, Queue } from 'bullmq';
import { PrismaClient } from '@tutorputor/core/db';
import { Logger } from 'pino';
import { RealContentGenerationClient } from '../grpc/RealContentGenerationClient';
import * as crypto from 'crypto';
import {
    type CorrelatedGenerationJobData,
    ContentWorkerTelemetryPublisher,
} from '../generation-telemetry';

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

export class ClaimGenerationProcessor {
    constructor(
        private grpcClient: RealContentGenerationClient,
        private prisma: PrismaClient,
        private queue: Queue,
        private logger: Logger,
        private telemetry?: ContentWorkerTelemetryPublisher,
    ) { }

    async process(job: Job<ClaimGenerationJobData>): Promise<void> {
        const { experienceId, tenantId, topic, domain, gradeLevel, maxClaims, title, targetGrades } = job.data;

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

            // Step 3: Store claims in database
            for (const claim of response.claims || []) {
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
                        bloomLevel: bloomLevel as any,
                        orderIndex: claim.order_index,
                        contentNeeds: claim.content_needs, // Storing json content needs
                    },
                    update: {
                        text: claim.text,
                        bloomLevel: bloomLevel as any,
                        orderIndex: claim.order_index,
                        contentNeeds: claim.content_needs,
                    },
                });

                this.logger.info(
                    { jobId: job.id, claimRef: claim.claim_ref },
                    'Claim stored in database'
                );

                // Step 4: Queue follow-up jobs based on content needs
                const contentNeeds = claim.content_needs;

                if (contentNeeds?.examples?.required) {
                    await this.queue.add('generate-examples', {
                        experienceId,
                        tenantId,
                        claimRef: claim.claim_ref,
                        claimText: claim.text,
                        gradeLevel,
                        domain,
                        types: contentNeeds.examples.types || ['REAL_WORLD'],
                        count: contentNeeds.examples.count || 2,
                        generationRequestId: job.data.generationRequestId,
                    }, {
                        jobId: `generate-examples:${experienceId}:${claim.claim_ref}`,
                    });

                    this.logger.info(
                        { jobId: job.id, claimRef: claim.claim_ref },
                        'Queued example generation job'
                    );
                }

                if (contentNeeds?.simulation?.required) {
                    await this.queue.add('generate-simulation', {
                        experienceId,
                        tenantId,
                        claimRef: claim.claim_ref,
                        claimText: claim.text,
                        gradeLevel,
                        domain,
                        interactionType: contentNeeds.simulation.interaction_type || 'PARAMETER_EXPLORATION',
                        complexity: contentNeeds.simulation.complexity || 'MEDIUM',
                        generationRequestId: job.data.generationRequestId,
                    }, {
                        jobId: `generate-simulation:${experienceId}:${claim.claim_ref}`,
                    });

                    this.logger.info(
                        { jobId: job.id, claimRef: claim.claim_ref },
                        'Queued simulation generation job'
                    );
                }

                if (contentNeeds?.animation?.required) {
                    await this.queue.add('generate-animation', {
                        experienceId,
                        tenantId,
                        claimRef: claim.claim_ref,
                        claimText: claim.text,
                        animationType: contentNeeds.animation.animation_type || 'TWO_D',
                        durationSeconds: contentNeeds.animation.duration_seconds || 30,
                        generationRequestId: job.data.generationRequestId,
                    }, {
                        jobId: `generate-animation:${experienceId}:${claim.claim_ref}`,
                    });

                    this.logger.info(
                        { jobId: job.id, claimRef: claim.claim_ref },
                        'Queued animation generation job'
                    );
                }
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
        } catch (error: any) {
            this.logger.error({ err: error, jobId: job.id }, 'Failed to process claim generation');
            throw error;
        }
    }

    private mapBloomLevel(value: any): string {
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
}
