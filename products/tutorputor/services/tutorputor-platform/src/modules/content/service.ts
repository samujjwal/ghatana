/**
 * @doc.type module
 * @doc.purpose Content service for modules and lessons
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@ghatana/tutorputor-db';
import type {
    ContentService,
    AIProxyService,
    ModuleDetail,
    ModuleSummary,
    Enrollment,
    ModuleId,
    TenantId,
    UserId,
} from '@ghatana/tutorputor-contracts';
import { ModalityValidator, PublishingError } from '../../utils/modality-validator';

// Using looser typing for Prisma includes/payloads.
// This module should be tightened to generated Prisma payload types.
type ModuleSummaryPayload = any;
type ModuleDetailPayload = any;

export class ContentServiceImpl implements ContentService {
    private readonly modalityValidator: ModalityValidator;

    constructor(
        private readonly prisma: PrismaClient,
        private readonly aiProxy?: AIProxyService
    ) {
        this.modalityValidator = new ModalityValidator(prisma);
    }

    async getModuleBySlug(
        tenantId: TenantId,
        slug: string,
        userId?: UserId
    ): Promise<{ module: ModuleDetail; enrollment?: Enrollment }> {
        const module = await this.prisma.module.findFirst({
            where: { tenantId, slug },
            include: this.buildDetailInclude(tenantId, userId),
        });

        if (!module) {
            throw new Error(
                `Module with slug "${slug}" not found for tenant ${tenantId}`
            );
        }

        const enrollmentRecord = userId
            ? await this.prisma.enrollment.findUnique({
                where: {
                    tenantId_userId_moduleId: {
                        tenantId,
                        userId,
                        moduleId: module.id,
                    },
                },
            })
            : null;

        return {
            module: this.mapModuleDetail(module),
            enrollment: enrollmentRecord
                ? this.mapEnrollment(enrollmentRecord)
                : undefined,
        };
    }

    async listModules(args: {
        tenantId: TenantId;
        domain?: string;
        status?: string;
        cursor?: string;
        limit?: number;
        userId?: UserId;
        query?: string;
    }): Promise<{ items: ModuleSummary[]; nextCursor: string | null }> {
        const limit = args.limit ?? 20;
        const take = Math.min(limit, 50);
        const where: any = { tenantId: args.tenantId };

        if (args.status) {
            where.status = { equals: args.status as any };
        } else {
            where.status = 'PUBLISHED';
        }

        if (args.domain) {
            where.domain = { equals: args.domain as any };
        }

        if (args.query && this.aiProxy) {
            // If AI Proxy is available, we could do semantic search or refined parsing
            try {
                const filters = await this.aiProxy.parseContentQuery(args.query);

                if (filters.domain) where.domain = { equals: filters.domain as any };
                if (filters.difficulty)
                    where.difficulty = { equals: filters.difficulty as any };

                if (filters.textSearch) {
                    where.OR = [
                        { title: { contains: filters.textSearch } },
                        { description: { contains: filters.textSearch } },
                    ];
                }
            } catch (e) {
                // Fallback to basic search if AI parsing fails
                where.OR = [
                    { title: { contains: args.query } },
                    { description: { contains: args.query } },
                ];
            }
        } else if (args.query) {
            where.OR = [
                { title: { contains: args.query } },
                { description: { contains: args.query } },
            ];
        }

        const modules = await this.prisma.module.findMany({
            where,
            take: take + 1,
            orderBy: { title: 'asc' },
            cursor: args.cursor ? { id: args.cursor } : undefined,
            skip: args.cursor ? 1 : 0,
            include: this.buildSummaryInclude(args.tenantId, args.userId),
        });

        const hasMore = modules.length > take;
        const trimmed = modules.slice(0, take);
        const items = trimmed.map((module: any) =>
            this.mapModuleSummary(module as ModuleSummaryPayload)
        );

        return {
            items,
            nextCursor: hasMore ? modules[modules.length - 1]?.id ?? null : null,
        };
    }

    /**
     * Publish a learning experience after validating that all claims
     * have at least one supporting modality (example, simulation, or animation).
     * 
     * @param experienceId - The experience ID to publish
     * @param tenantId - The tenant ID for authorization
     * @throws PublishingError if any claim lacks modalities
     */
    async publishExperience(experienceId: string, tenantId: TenantId): Promise<void> {
        // Verify experience exists and belongs to tenant
        const experience = await this.prisma.learningExperience.findFirst({
            where: { id: experienceId, tenantId },
            select: { id: true, status: true }
        });

        if (!experience) {
            throw new Error(`Experience ${experienceId} not found for tenant ${tenantId}`);
        }

        if (experience.status === 'PUBLISHED') {
            throw new Error(`Experience ${experienceId} is already published`);
        }

        // Validate all claims have modalities before publishing
        await this.modalityValidator.validateExperienceForPublishing(experienceId);

        // Proceed with publishing
        await this.prisma.learningExperience.update({
            where: { id: experienceId },
            data: {
                status: 'PUBLISHED',
                publishedAt: new Date(),
                updatedAt: new Date()
            }
        });

        // Log successful validation and publish
        console.log(`[ContentService] Published experience ${experienceId} after modality validation`);
    }

    /**
     * Get modality validation status for an experience's claims.
     * Useful for pre-publish checks in the UI.
     * 
     * @param experienceId - The experience ID
     * @returns Validation status for each claim
     */
    async getExperienceModalityStatus(experienceId: string): Promise<{
        experienceId: string;
        readyToPublish: boolean;
        claims: Array<{
            claimRef: string;
            valid: boolean;
            modalities: { examples: number; simulations: number; animations: number };
            preferredModality: string | null;
        }>;
        errors: string[];
    }> {
        const claims = await this.prisma.learningClaim.findMany({
            where: { experienceId },
            select: { claimRef: true }
        });

        const claimStatuses = await Promise.all(
            claims.map(async (claim: { claimRef: string }) => {
                const validation = await this.modalityValidator.validateClaimModality(
                    experienceId,
                    claim.claimRef
                );
                return {
                    claimRef: claim.claimRef,
                    valid: validation.valid,
                    modalities: validation.modalities,
                    preferredModality: validation.preferredModality ?? null
                };
            })
        );

        const errors = claimStatuses
            .filter((c: { valid: boolean; claimRef: string }) => !c.valid)
            .map((c: { valid: boolean; claimRef: string }) => `Claim ${c.claimRef} has no supporting modalities`);

        return {
            experienceId,
            readyToPublish: errors.length === 0,
            claims: claimStatuses,
            errors
        };
    }

    // ===========================================================================
    // Helpers
    // ===========================================================================

    private buildSummaryInclude(tenantId: TenantId, userId?: UserId): any {
        const include: any = {
            tags: true,
        };
        if (userId) {
            include.enrollments = {
                where: { tenantId, userId },
            };
        }
        return include;
    }

    private buildDetailInclude(tenantId: TenantId, userId?: UserId): any {
        const include: any = {
            tags: true,
            learningObjectives: true,
            contentBlocks: true,
            prerequisites: true,
        };
        if (userId) {
            include.enrollments = {
                where: { tenantId, userId },
            };
        }
        return include;
    }

    private mapModuleSummary(module: ModuleSummaryPayload): ModuleSummary {
        const enrollment = module.enrollments?.[0];
        return {
            id: module.id as ModuleId,
            slug: module.slug,
            title: module.title,
            domain: module.domain as ModuleSummary['domain'],
            difficulty: module.difficulty as ModuleSummary['difficulty'],
            estimatedTimeMinutes: module.estimatedTimeMinutes,
            tags: module.tags.map((tag: any) => tag.label),
            status: 'PUBLISHED',
            progressPercent: enrollment?.progressPercent ?? undefined,
        };
    }

    private mapModuleDetail(module: ModuleDetailPayload): ModuleDetail {
        return {
            ...this.mapModuleSummary(module as ModuleSummaryPayload),
            description: module.description,
            learningObjectives: module.learningObjectives.map((objective: any) => ({
                id: `${module.id}-${objective.id}`,
                label: objective.label,
                taxonomyLevel: objective.taxonomyLevel as any,
            })),
            contentBlocks: module.contentBlocks
                .slice()
                .sort((a: any, b: any) => a.orderIndex - b.orderIndex)
                .map((block: any) => ({
                    id: block.id,
                    orderIndex: block.orderIndex,
                    blockType: block.blockType as any,
                    payload: block.payload as unknown,
                })),
            prerequisites: module.prerequisites.map(
                (prereq: any) => prereq.prerequisiteModuleId as ModuleId
            ),
            version: module.version,
        };
    }

    private mapEnrollment(record: any): Enrollment {
        return {
            id: record.id as Enrollment['id'],
            moduleId: record.moduleId as ModuleId,
            userId: record.userId as UserId,
            status: record.status,
            progressPercent: record.progressPercent,
            startedAt: record.startedAt?.toISOString(),
            completedAt: record.completedAt?.toISOString(),
            timeSpentSeconds: record.timeSpentSeconds,
        };
    }
}
