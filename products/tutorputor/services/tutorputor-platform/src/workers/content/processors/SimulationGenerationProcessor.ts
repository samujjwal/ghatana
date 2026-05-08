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

/**
 * Normalize domain to full TutorPutor domain set.
 * Aligns with RealContentGenerationClient.normalizeDomain for consistency.
 */
function normalizeDomain(value: string): string {
    const normalized = String(value || '').toUpperCase().replace(/[-\s]/g, '_');
    const mapping: Record<string, string> = {
        // Full TutorPutor domain set
        MATH: 'MATHEMATICS',
        MATHEMATICS: 'MATHEMATICS',
        SCIENCE: 'SCIENCE',
        TECH: 'TECH',
        ENGINEERING: 'ENGINEERING',
        MEDICINE: 'MEDICINE',
        HEALTH: 'HEALTH',
        BUSINESS: 'BUSINESS',
        MANAGEMENT: 'MANAGEMENT',
        ECONOMICS: 'ECONOMICS',
        COMPUTER_SCIENCE: 'COMPUTER_SCIENCE',
        INTERDISCIPLINARY: 'INTERDISCIPLINARY',
        
        // Alternative mappings for backward compatibility
        CS: 'COMPUTER_SCIENCE',
        CS_DISCRETE: 'COMPUTER_SCIENCE',
        'CS-DISCRETE': 'COMPUTER_SCIENCE',
        PHYSICS: 'SCIENCE',
        CHEMISTRY: 'SCIENCE',
        BIOLOGY: 'SCIENCE',
        
        // Legacy fallbacks (prefer explicit domain selection)
        ARTS: 'INTERDISCIPLINARY',
        LANGUAGE: 'INTERDISCIPLINARY',
        GENERAL: 'INTERDISCIPLINARY',
    };
    return mapping[normalized] || 'INTERDISCIPLINARY';
}

function toInputJsonValue(value: unknown): Prisma.InputJsonValue {
    return value as Prisma.InputJsonValue;
}

/**
 * Validate SimulationManifest against proto schema requirements
 */
function validateSimulationManifest(manifest: {
    manifest_id?: string;
    version?: string;
    domain?: string;
    title?: string;
    description?: string;
    entities?: unknown[];
    steps?: unknown[];
    keyframes?: unknown[];
    domain_config?: string;
}): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    if (!manifest.manifest_id) {
        errors.push('manifest_id is required');
    }
    if (!manifest.version) {
        errors.push('version is required');
    }
    if (!manifest.domain) {
        errors.push('domain is required');
    }
    if (!manifest.title) {
        errors.push('title is required');
    }
    if (!manifest.description) {
        errors.push('description is required');
    }
    if (!manifest.entities || !Array.isArray(manifest.entities) || manifest.entities.length === 0) {
        errors.push('entities must be a non-empty array');
    }
    if (!manifest.steps || !Array.isArray(manifest.steps) || manifest.steps.length === 0) {
        errors.push('steps must be a non-empty array');
    }
    if (!manifest.keyframes || !Array.isArray(manifest.keyframes) || manifest.keyframes.length === 0) {
        errors.push('keyframes must be a non-empty array');
    }

    // Validate domain_config is valid JSON if present
    if (manifest.domain_config) {
        try {
            JSON.parse(manifest.domain_config);
        } catch {
            errors.push('domain_config must be valid JSON');
        }
    }

    return { valid: errors.length === 0, errors };
}

/**
 * Extract goal from manifest - prefer explicit goal from domain_config, fall back to title
 */
function extractGoal(manifest: { title?: string; description?: string; domain_config?: string }): string {
    if (manifest.domain_config) {
        try {
            const domainConfig = JSON.parse(manifest.domain_config) as Record<string, unknown>;
            if (typeof domainConfig.goal === 'string' && domainConfig.goal.trim()) {
                return domainConfig.goal.trim();
            }
        } catch {
            // If domain_config is invalid JSON, fall back to title
        }
    }
    // Fall back to title if no explicit goal in domain_config
    return manifest.title || 'Explore simulation';
}

/**
 * Extract success criteria from manifest domain_config
 * Throws error if no valid success criteria found
 */
function extractSuccessCriteria(manifest: { domain_config?: string }): Record<string, unknown> {
    if (!manifest.domain_config) {
        throw new Error('domain_config is required for success criteria');
    }

    try {
        const domainConfig = JSON.parse(manifest.domain_config) as Record<string, unknown>;
        if (domainConfig.successCriteria && typeof domainConfig.successCriteria === 'object') {
            return domainConfig.successCriteria as Record<string, unknown>;
        }
    } catch (e) {
        throw new Error(`Invalid domain_config JSON: ${e instanceof Error ? e.message : String(e)}`);
    }

    throw new Error('successCriteria not found in domain_config');
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

            // Validate manifest against proto schema before persistence
            const validation = validateSimulationManifest(response.manifest);
            if (!validation.valid) {
                throw new Error(`Invalid simulation manifest: ${validation.errors.join(', ')}`);
            }

            this.logger.info(
                { jobId: job.id, manifestId: response.manifest.manifest_id },
                'Simulation manifest validated successfully'
            );

            // Store simulation manifest in database
            const manifestId = response.manifest.manifest_id;
            const persistedDomain = normalizeDomain(domain) as any; // TODO: Regenerate Prisma types after schema update
            const persistedManifest = toInputJsonValue(response.manifest);

            // Extract goal and success criteria from validated manifest
            const goal = extractGoal(response.manifest);
            const successCriteria = extractSuccessCriteria(response.manifest);

            const manifest = await this.prisma.simulationManifest.upsert({
                where: { id: manifestId },
                create: {
                    id: manifestId,
                    tenantId,
                    title: response.manifest.title,
                    description: response.manifest.description || '',
                    version: response.manifest.version || '1.0.0',
                    domain: persistedDomain,
                    manifest: persistedManifest,
                },
                update: {
                    title: response.manifest.title,
                    description: response.manifest.description || '',
                    version: response.manifest.version || '1.0.0',
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
                    goal,
                    successCriteria: toInputJsonValue(successCriteria),
                    estimatedMinutes: 10,
                },
                update: {
                    simulationManifestId: manifest.id,
                    interactionType,
                    goal,
                    successCriteria: toInputJsonValue(successCriteria),
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
