/**
 * Bulk Simulation Generation Service
 * 
 * @doc.type module
 * @doc.purpose Automated bulk generation of simulations from domain concepts
 * @doc.layer product
 * @doc.pattern Service
 */

import type { TutorPrismaClient } from "@ghatana/tutorputor-db";
import type { DomainConcept } from "@ghatana/tutorputor-contracts/v1/curriculum/types";
import type { SimulationManifest } from "@ghatana/tutorputor-contracts/v1/simulation/types";
import { generateManifestFromConcept, type ManifestGeneratorOptions } from "./generators/manifest-generator.js";

/**
 * Bulk generation job status.
 */
export type BulkJobStatus = 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';

/**
 * Bulk generation job.
 */
export interface BulkGenerationJob {
    id: string;
    tenantId: string;
    userId: string;
    status: BulkJobStatus;
    totalConcepts: number;
    processedConcepts: number;
    successCount: number;
    failureCount: number;
    startedAt?: Date;
    completedAt?: Date;
    errors: Array<{
        conceptId: string;
        error: string;
    }>;
    createdAt: Date;
    updatedAt: Date;
}

/**
 * Bulk generation request.
 */
export interface BulkGenerationRequest {
    tenantId: string;
    userId: string;
    conceptIds?: string[];
    filters?: {
        domain?: string;
        gradeLevel?: string;
        subject?: string;
    };
    options?: {
        autoPublish?: boolean;
        skipExisting?: boolean;
        batchSize?: number;
    };
}

/**
 * Bulk generation result.
 */
export interface BulkGenerationResult {
    jobId: string;
    status: BulkJobStatus;
    totalConcepts: number;
    processedConcepts: number;
    successCount: number;
    failureCount: number;
    manifests: SimulationManifest[];
    errors: Array<{
        conceptId: string;
        error: string;
    }>;
}

/**
 * Bulk Simulation Generator Service.
 */
export class BulkSimulationGenerator {
    private jobs: Map<string, BulkGenerationJob> = new Map();

    constructor(private prisma: TutorPrismaClient) { }

    /**
     * Start a bulk generation job.
     */
    async startBulkGeneration(request: BulkGenerationRequest): Promise<string> {
        const jobId = `job-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

        // Fetch concepts based on request
        const concepts = await this.fetchConcepts(request);

        const job: BulkGenerationJob = {
            id: jobId,
            tenantId: request.tenantId,
            userId: request.userId,
            status: 'pending',
            totalConcepts: concepts.length,
            processedConcepts: 0,
            successCount: 0,
            failureCount: 0,
            errors: [],
            createdAt: new Date(),
            updatedAt: new Date()
        };

        this.jobs.set(jobId, job);

        // Start processing asynchronously
        this.processBulkJob(jobId, concepts, request).catch(error => {
            console.error(`Bulk job ${jobId} failed:`, error);
            job.status = 'failed';
            job.errors.push({ conceptId: 'system', error: error.message });
            job.updatedAt = new Date();
        });

        return jobId;
    }

    /**
     * Get job status.
     */
    async getJobStatus(jobId: string): Promise<BulkGenerationJob | null> {
        return this.jobs.get(jobId) || null;
    }

    /**
     * Cancel a running job.
     */
    async cancelJob(jobId: string): Promise<boolean> {
        const job = this.jobs.get(jobId);
        if (!job) return false;

        if (job.status === 'running' || job.status === 'pending') {
            job.status = 'cancelled';
            job.completedAt = new Date();
            job.updatedAt = new Date();
            return true;
        }

        return false;
    }

    /**
     * Get job results.
     */
    async getJobResults(jobId: string): Promise<BulkGenerationResult | null> {
        const job = this.jobs.get(jobId);
        if (!job) return null;

        // Fetch generated manifests
        const manifests = await this.prisma.simulationManifest.findMany({
            where: {
                tenantId: job.tenantId,
                createdAt: {
                    gte: job.createdAt
                }
            }
        });

        return {
            jobId: job.id,
            status: job.status,
            totalConcepts: job.totalConcepts,
            processedConcepts: job.processedConcepts,
            successCount: job.successCount,
            failureCount: job.failureCount,
            manifests: manifests.map(m => m.manifest as unknown as SimulationManifest),
            errors: job.errors
        };
    }

    /**
     * Process bulk generation job.
     */
    private async processBulkJob(
        jobId: string,
        concepts: DomainConcept[],
        request: BulkGenerationRequest
    ): Promise<void> {
        const job = this.jobs.get(jobId);
        if (!job) return;

        job.status = 'running';
        job.startedAt = new Date();
        job.updatedAt = new Date();

        const batchSize = request.options?.batchSize || 10;
        const skipExisting = request.options?.skipExisting ?? true;
        const autoPublish = request.options?.autoPublish ?? false;

        // Process in batches
        for (let i = 0; i < concepts.length; i += batchSize) {
            // Check if job was cancelled
            if ((job.status as BulkJobStatus) === 'cancelled') break;

            const batch = concepts.slice(i, i + batchSize);

            await Promise.all(
                batch.map(async (concept) => {
                    try {
                        // Check if simulation already exists
                        if (skipExisting) {
                            const existing = await this.prisma.simulationManifest.findFirst({
                                where: {
                                    tenantId: request.tenantId,
                                    title: concept.name,
                                    domain: concept.domain
                                }
                            });

                            if (existing) {
                                job.processedConcepts++;
                                job.updatedAt = new Date();
                                return;
                            }
                        }

                        // Generate manifest
                        const options: ManifestGeneratorOptions = {
                            tenantId: request.tenantId,
                            authorId: request.userId,
                            version: '1.0.0',
                            placeholderSteps: false
                        };

                        const result = generateManifestFromConcept(concept, options);
                        const manifest = result.manifest;

                        // Add lifecycle metadata
                        const enrichedManifest = {
                            ...manifest,
                            lifecycle: {
                                status: autoPublish ? 'published' : 'draft',
                                createdBy: 'template' as const,
                                validatedAt: Date.now(),
                                publishedAt: autoPublish ? Date.now() : undefined
                            },
                            safety: {
                                parameterBounds: { enforced: true },
                                executionLimits: {
                                    maxSteps: 1000,
                                    maxRuntimeMs: 60000
                                }
                            },
                            ecd: {
                                claims: concept.pedagogicalMetadata?.learningObjectives?.map((obj, idx) => ({
                                    id: `claim-${idx}`,
                                    description: obj,
                                    evidenceIds: [`evidence-${idx}`]
                                })) || [],
                                evidence: concept.pedagogicalMetadata?.learningObjectives?.map((_, idx) => ({
                                    id: `evidence-${idx}`,
                                    source: 'telemetry.parameterChange' as const,
                                    requiredForClaim: [`claim-${idx}`]
                                })) || [],
                                tasks: [{
                                    id: 'task-1',
                                    type: 'manipulation' as const,
                                    claimIds: concept.pedagogicalMetadata?.learningObjectives?.map((_, idx) => `claim-${idx}`) || []
                                }]
                            }
                        };

                        // Persist to database
                        await this.prisma.simulationManifest.create({
                            data: {
                                id: `sim_${manifest.domain}_${concept.id}_${Date.now()}`,
                                tenantId: request.tenantId,
                                manifest: JSON.stringify(enrichedManifest),
                                domain: manifest.domain,
                                title: manifest.title,
                                version: manifest.version,
                                createdAt: new Date(),
                                updatedAt: new Date()
                            }
                        });

                        job.successCount++;
                        job.processedConcepts++;
                        job.updatedAt = new Date();

                    } catch (error) {
                        console.error(`Failed to generate simulation for concept ${concept.id}:`, error);
                        job.failureCount++;
                        job.processedConcepts++;
                        job.errors.push({
                            conceptId: concept.id,
                            error: error instanceof Error ? error.message : String(error)
                        });
                        job.updatedAt = new Date();
                    }
                })
            );
        }

        // Mark job as completed
        if ((job.status as BulkJobStatus) !== 'cancelled') {
            job.status = 'completed';
        }
        job.completedAt = new Date();
        job.updatedAt = new Date();
    }

    /**
     * Fetch concepts based on request criteria.
     */
    private async fetchConcepts(request: BulkGenerationRequest): Promise<DomainConcept[]> {
        // If specific concept IDs provided, fetch those
        if (request.conceptIds && request.conceptIds.length > 0) {
            const concepts = await this.prisma.domainConcept.findMany({
                where: {
                    id: { in: request.conceptIds },
                    tenantId: request.tenantId
                }
            });

            return concepts.map(c => ({
                id: c.externalId,
                name: c.name,
                description: c.description,
                domain: c.domain,
                level: c.level,
                prerequisites: [],
                audienceTags: JSON.parse(c.audienceTags) as DomainConcept['audienceTags'],
                keywords: JSON.parse(c.keywords) as string[],
                simulationMetadata: c.simulationMetadata as unknown as DomainConcept['simulationMetadata'],
                crossDomainLinks: c.crossDomainLinks as unknown as DomainConcept['crossDomainLinks'],
                learningObjectMetadata: c.learningObjectMetadata as unknown as DomainConcept['learningObjectMetadata'],
                pedagogicalMetadata: c.pedagogicalMetadata as unknown as DomainConcept['pedagogicalMetadata'],
            }) as unknown as DomainConcept);
        }

        // Otherwise, fetch based on filters
        // gradeLevel maps to `level` in the schema; `subject` has no schema equivalent and is ignored
        const where = {
            tenantId: request.tenantId,
            ...(request.filters?.domain && { domain: request.filters.domain }),
            ...(request.filters?.gradeLevel && { level: request.filters.gradeLevel }),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        } as any;

        const concepts = await this.prisma.domainConcept.findMany({
            where,
            take: 1000 // Limit to 1000 concepts per job
        });

        return concepts.map(c => ({
            id: c.externalId,
            name: c.name,
            description: c.description,
            domain: c.domain,
            level: c.level,
            prerequisites: [],
            audienceTags: JSON.parse(c.audienceTags) as DomainConcept['audienceTags'],
            keywords: JSON.parse(c.keywords) as string[],
            simulationMetadata: c.simulationMetadata as unknown as DomainConcept['simulationMetadata'],
            crossDomainLinks: c.crossDomainLinks as unknown as DomainConcept['crossDomainLinks'],
            learningObjectMetadata: c.learningObjectMetadata as unknown as DomainConcept['learningObjectMetadata'],
            pedagogicalMetadata: c.pedagogicalMetadata as unknown as DomainConcept['pedagogicalMetadata'],
        }) as unknown as DomainConcept);
    }

    /**
     * Clean up old completed jobs.
     */
    async cleanupOldJobs(olderThanDays: number = 7): Promise<number> {
        const cutoffDate = new Date();
        cutoffDate.setDate(cutoffDate.getDate() - olderThanDays);

        let cleaned = 0;
        for (const [jobId, job] of this.jobs.entries()) {
            if (
                (job.status === 'completed' || job.status === 'failed' || job.status === 'cancelled') &&
                job.completedAt &&
                job.completedAt < cutoffDate
            ) {
                this.jobs.delete(jobId);
                cleaned++;
            }
        }

        return cleaned;
    }
}

/**
 * Create bulk simulation generator service.
 */
export function createBulkSimulationGenerator(prisma: TutorPrismaClient): BulkSimulationGenerator {
    return new BulkSimulationGenerator(prisma);
}
