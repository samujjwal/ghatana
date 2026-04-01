/**
 * Example Generation Processor - Generates concrete examples for claims.
 * 
 * @doc.type class
 * @doc.purpose Process example generation jobs
 * @doc.layer backend-worker
 * @doc.pattern JobProcessor
 */

import { Job } from 'bullmq';
import { PrismaClient } from '@tutorputor/core/db';
import { Logger } from 'pino';
import { RealContentGenerationClient } from '../grpc/RealContentGenerationClient';
import * as crypto from 'crypto';
import {
    type CorrelatedGenerationJobData,
    ContentWorkerTelemetryPublisher,
} from '../generation-telemetry';

export interface ExampleGenerationJobData extends CorrelatedGenerationJobData {
    experienceId: string;
    tenantId: string;
    claimRef: string;
    claimText: string;
    gradeLevel: string;
    domain: string;
    types: string[];
    count: number;
}

export class ExampleGenerationProcessor {
    constructor(
        private grpcClient: RealContentGenerationClient,
        private prisma: PrismaClient,
        private logger: Logger,
        private telemetry?: ContentWorkerTelemetryPublisher,
    ) { }

    async process(job: Job<ExampleGenerationJobData>): Promise<void> {
        const { experienceId, tenantId, claimRef, claimText, gradeLevel, domain, types, count } = job.data;

        this.logger.info(
            { jobId: job.id, experienceId, claimRef },
            'Processing example generation job'
        );

        try {
            await this.telemetry?.publishForJob(job, {
                stage: 'grpc_request_started',
                message: 'Submitting example generation request',
                progressPercent: 20,
                status: 'running',
            });

            // Call Java agent to generate examples
            const requestId = crypto.randomUUID();
            const response = await this.grpcClient.generateExamples({
                requestId,
                tenantId,
                claimText,
                claimRef,
                gradeLevel,
                domain,
                types,
                count,
            });

            this.logger.info(
                { jobId: job.id, examplesCount: response.examples?.length || 0 },
                'Examples generated successfully'
            );

            const responseCost = ContentWorkerTelemetryPublisher.extractCostFromMetadata(response.metadata);
            await this.telemetry?.publishForJob(job, {
                stage: 'grpc_response_received',
                message: 'Example generation response received',
                progressPercent: 55,
                status: 'running',
                ...(responseCost ? { cost: responseCost } : {}),
                diagnostics: {
                    examplesCount: response.examples?.length || 0,
                },
            });

            await this.prisma.claimExample.deleteMany({
                where: { experienceId, claimRef },
            });

            // Store examples in database
            for (const example of response.examples || []) {
                await this.prisma.claimExample.create({
                    data: {
                        experienceId,
                        claimRef,
                        type: String(example.type),
                        title: example.title,
                        description: example.description,
                        content: {
                            problemStatement: example.problem_statement,
                            solution: example.solution_content,
                            keyPoints: example.key_learning_points || [],
                            realWorldConnection: example.real_world_connection,
                        },
                        difficulty: 'INTERMEDIATE', // Default
                        orderIndex: example.order_index,
                    },
                });

                this.logger.info(
                    { jobId: job.id, claimRef, exampleId: example.example_id },
                    'Example stored in database'
                );
            }

            this.logger.info(
                { jobId: job.id, experienceId, claimRef, examplesCount: response.examples?.length || 0 },
                'Example generation job completed'
            );

            await this.telemetry?.publishForJob(job, {
                stage: 'persistence_completed',
                message: 'Example artifacts persisted',
                progressPercent: 90,
                status: 'running',
                diagnostics: {
                    examplesStored: response.examples?.length || 0,
                    experienceId,
                    claimRef,
                },
            });

        } catch (error: unknown) {
            this.logger.error(
                { jobId: job.id, experienceId, claimRef, error: error.message },
                'Example generation job failed'
            );
            throw error;
        }
    }
}
