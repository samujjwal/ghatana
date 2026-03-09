/**
 * Simulation Generation Processor - Generates interactive simulations for claims.
 * 
 * @doc.type class
 * @doc.purpose Process simulation generation jobs
 * @doc.layer backend-worker
 * @doc.pattern JobProcessor
 */

import { Job } from 'bullmq';
import { PrismaClient } from '@ghatana/tutorputor-db';
import { Logger } from 'pino';
import { RealContentGenerationClient } from '../grpc/RealContentGenerationClient';
import * as crypto from 'crypto';

export interface SimulationGenerationJobData {
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
        private logger: Logger
    ) { }

    async process(job: Job<SimulationGenerationJobData>): Promise<void> {
        const { experienceId, tenantId, claimRef, claimText, gradeLevel, domain, interactionType, complexity } = job.data;

        this.logger.info(
            { jobId: job.id, experienceId, claimRef },
            'Processing simulation generation job'
        );

        try {
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

            if (!response.manifest) {
                throw new Error('No simulation manifest returned');
            }

            // Store simulation manifest in database
            // Schema requires ID. Using manifest_id if available or generate one?
            // Schema says @id without default(cuid())? No wait:
            // model SimulationManifest {
            //   id          String           @id // Stable manifest ID generated from concept
            const manifestId = response.manifest.manifest_id || crypto.randomUUID();

            const manifest = await this.prisma.simulationManifest.upsert({
                where: { id: manifestId },
                create: {
                    id: manifestId,
                    tenantId,
                    title: response.manifest.name, // Mapped name -> title
                    description: response.manifest.description,
                    version: '1.0.0',
                    domain: domain as any, // Enum cast
                    // gradeLevel: removed as not in schema
                    manifest: response.manifest as any, // Store full manifest as JSON
                    // status: 'DRAFT', // status not in schema for SimulationManifest?
                    // Schema has `status SimulationTemplateStatus` for Template, but Manifest?
                    // Manifest has `title`, `description`, `domain`, `version`. No `status`.
                },
                update: {
                    title: response.manifest.name,
                    description: response.manifest.description,
                    version: '1.0.0',
                    domain: domain as any,
                    manifest: response.manifest as any,
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

        } catch (error: any) {
            this.logger.error(
                { jobId: job.id, experienceId, claimRef, error: error.message },
                'Simulation generation job failed'
            );
            throw error;
        }
    }
}
