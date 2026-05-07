/**
 * Simulation Generation Processor - Generates interactive simulations for claims.
 * 
 * @doc.type class
 * @doc.purpose Process simulation generation jobs
 * @doc.layer backend-worker
 * @doc.pattern JobProcessor
 */

import { Job } from 'bullmq';
import { Prisma, PrismaClient } from '@tutorputor/core/db';
import { Logger } from 'pino';
import { RealContentGenerationClient } from '../grpc/RealContentGenerationClient';
import * as crypto from 'crypto';
import {
    type CorrelatedGenerationJobData,
    ContentWorkerTelemetryPublisher,
} from '../generation-telemetry';

type SimulationDomainValue =
    | 'CS_DISCRETE'
    | 'PHYSICS'
    | 'CHEMISTRY'
    | 'BIOLOGY'
    | 'MEDICINE'
    | 'ECONOMICS'
    | 'ENGINEERING'
    | 'MATHEMATICS';

function toSimulationDomain(value: string): SimulationDomainValue {
    const normalized = value.trim().toUpperCase();
    switch (normalized) {
        case 'CS_DISCRETE':
        case 'PHYSICS':
        case 'CHEMISTRY':
        case 'BIOLOGY':
        case 'MEDICINE':
        case 'ECONOMICS':
        case 'ENGINEERING':
        case 'MATHEMATICS':
            return normalized;
        default:
            return 'PHYSICS';
    }
}

function toInputJsonValue(value: unknown): Prisma.InputJsonValue {
    return value as Prisma.InputJsonValue;
}

export interface SimulationGenerationJobData extends CorrelatedGenerationJobData {
    experienceId: string;
    tenantId: string;
    claimRef: string;
    claimText: string;
    gradeLevel: string;
    domain: string;
    interactionType: string;
    complexity: string;
}

export class SimulationGenerationProcessor {
    constructor(
        private grpcClient: RealContentGenerationClient,
        private prisma: PrismaClient,
        private logger: Logger,
        private telemetry?: ContentWorkerTelemetryPublisher,
    ) { }

    async process(job: Job<SimulationGenerationJobData>): Promise<void> {
        const { experienceId, tenantId, claimRef, claimText, gradeLevel, domain, interactionType, complexity } = job.data;

        this.logger.info(
            { jobId: job.id, experienceId, claimRef },
            'Processing simulation generation job'
        );

        try {
            await this.telemetry?.publishForJob(job, {
                stage: 'grpc_request_started',
                message: 'Submitting simulation generation request',
                progressPercent: 20,
                status: 'running',
            });

            // Call Java agent to generate simulation
            const requestId = crypto.randomUUID();
            const response = await this.grpcClient.generateSimulation({
                requestId,
                tenantId,
                claimText,
                claimRef,
                gradeLevel,
                domain,
                interactionType,
                complexity,
            });

            this.logger.info(
                { jobId: job.id, manifestId: response.manifest?.manifest_id },
                'Simulation generated successfully'
            );

            const responseCost = ContentWorkerTelemetryPublisher.extractCostFromMetadata(response.metadata);
            await this.telemetry?.publishForJob(job, {
                stage: 'grpc_response_received',
                message: 'Simulation generation response received',
                progressPercent: 55,
                status: 'running',
                ...(responseCost ? { cost: responseCost } : {}),
                diagnostics: {
                    manifestId: response.manifest?.manifest_id,
                },
            });

            if (!response.manifest) {
                throw new Error('No simulation manifest returned');
            }

            // Store simulation manifest in database
            // Schema requires ID. Using manifest_id if available or generate one?
            // Schema says @id without default(cuid())? No wait:
            // model SimulationManifest {
            //   id          String           @id // Stable manifest ID generated from concept
            const manifestId = response.manifest.manifest_id || crypto.randomUUID();
            const persistedDomain = toSimulationDomain(domain);
            const persistedManifest = toInputJsonValue(response.manifest);

            const manifest = await this.prisma.simulationManifest.upsert({
                where: { id: manifestId },
                create: {
                    id: manifestId,
                    tenantId,
                    title: response.manifest.name, // Mapped name -> title
                    description: response.manifest.description,
                    version: '1.0.0',
                    domain: persistedDomain,
                    // gradeLevel: removed as not in schema
                    manifest: persistedManifest,
                    // status: 'DRAFT', // status not in schema for SimulationManifest?
                    // Schema has `status SimulationTemplateStatus` for Template, but Manifest?
                    // Manifest has `title`, `description`, `domain`, `version`. No `status`.
                },
                update: {
                    title: response.manifest.name,
                    description: response.manifest.description,
                    version: '1.0.0',
                    domain: persistedDomain,
                    manifest: persistedManifest,
                },
            });

            this.logger.info(
                { jobId: job.id, manifestId: manifest.id },
                'Simulation manifest stored in database'
            );

            // Link simulation to claim
            await this.prisma.claimSimulation.upsert({
                where: {
                    experienceId_claimRef: {
                        experienceId,
                        claimRef,
                    },
                },
                create: {
                    experienceId,
                    claimRef,
                    simulationManifestId: manifest.id,
                    interactionType,
                    goal: response.manifest.goals?.[0]?.description || 'Explore the concept',
                    successCriteria: {},
                    estimatedMinutes: 10,
                },
                update: {
                    simulationManifestId: manifest.id,
                    interactionType,
                    goal: response.manifest.goals?.[0]?.description || 'Explore the concept',
                    successCriteria: {},
                    estimatedMinutes: 10,
                },
            });

            this.logger.info(
                { jobId: job.id, claimRef, manifestId: manifest.id },
                'Simulation linked to claim'
            );

            this.logger.info(
                { jobId: job.id, experienceId, claimRef },
                'Simulation generation job completed'
            );

            await this.telemetry?.publishForJob(job, {
                stage: 'persistence_completed',
                message: 'Simulation manifest persisted and linked',
                progressPercent: 90,
                status: 'running',
                diagnostics: {
                    experienceId,
                    claimRef,
                    manifestId: manifest.id,
                },
            });

        } catch (error: unknown) {
            this.logger.error(
                { jobId: job.id, experienceId, claimRef, error: error instanceof Error ? error.message : String(error) },
                'Simulation generation job failed'
            );
            throw error;
        }
    }
}
