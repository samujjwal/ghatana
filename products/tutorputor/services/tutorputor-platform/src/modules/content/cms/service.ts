/**
 * @doc.type module
 * @doc.purpose CMS service for creating and managing content
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@ghatana/tutorputor-db';
import { ModuleStatus } from "@ghatana/tutorputor-contracts/v1";
import type {
    CMSService,
    AIProxyService,
    ContentService,
    ModuleSummary,
    ModuleDetail,
    ModuleDraftInput,
    ModuleDraftPatch,
    ModuleId,
    TenantId,
    UserId,
} from '@ghatana/tutorputor-contracts';

export class CMSServiceImpl implements CMSService {
    constructor(
        private readonly prisma: PrismaClient,
        private readonly contentService: ContentService,
        private readonly aiProxy?: AIProxyService
    ) { }

    async generateDraftFromIntent(args: {
        tenantId: TenantId;
        authorId: UserId;
        intent: string;
    }): Promise<ModuleDetail> {
        if (!this.aiProxy) {
            throw new Error('AI service not available');
        }

        // 1. Generate draft structure using AI
        const draftData = await this.aiProxy.generateLearningUnitDraft({
            topic: args.intent,
            targetAudience: 'General',
        });

        if (!draftData) {
            throw new Error('Failed to generate draft');
        }

        // 2. Create draft in DB
        const slug =
            (draftData.title || 'untitled').toLowerCase().replace(/[^a-z0-9]+/g, '-') +
            '-' +
            Date.now();

        const input: ModuleDraftInput = {
            slug,
            title: draftData.title || 'Untitled Module',
            description: draftData.description || '',
            domain: draftData.domain || 'TECH',
            difficulty: draftData.difficulty || 'INTRO',
            estimatedTimeMinutes: 30,
            tags: [],
            learningObjectives: draftData.learningObjectives || [],
            contentBlocks: draftData.contentBlocks || [],
            prerequisites: [],
        };

        await this.prisma.module.create({
            data: this.buildModuleCreateInput(args.tenantId, args.authorId, input),
        });

        return this.fetchModuleDetailBySlug(args.tenantId, slug);
    }

    async listModules(args: {
        tenantId: TenantId;
        status?: ModuleStatus;
        cursor?: ModuleId | null;
        limit?: number;
    }): Promise<{ items: ModuleSummary[]; nextCursor: ModuleId | null }> {
        const limit = args.limit ?? 20;
        const take = Math.min(limit, 50);
        const where: any = { tenantId: args.tenantId };
        if (args.status) {
            where.status = args.status;
        }

        const modules = await this.prisma.module.findMany({
            where,
            take: take + 1,
            orderBy: { updatedAt: 'desc' },
            ...(args.cursor ? { cursor: { id: args.cursor }, skip: 1 } : {}),
            include: {
                tags: true,
                prerequisites: true,
                learningObjectives: true,
                contentBlocks: true,
                enrollments: false,
            },
        });

        const hasMore = modules.length > take;
        const trimmed = modules.slice(0, take);
        return {
            items: trimmed.map((module: any) => this.mapModuleSummary(module)),
            nextCursor: hasMore ? (modules[modules.length - 1]?.id as ModuleId) ?? null : null,
        };
    }

    async createModuleDraft(args: {
        tenantId: TenantId;
        authorId: UserId;
        input: ModuleDraftInput;
    }): Promise<ModuleDetail> {
        await this.prisma.module.create({
            data: this.buildModuleCreateInput(args.tenantId, args.authorId, args.input),
        });

        return this.fetchModuleDetailBySlug(args.tenantId, args.input.slug);
    }

    async updateModuleDraft(args: {
        tenantId: TenantId;
        moduleId: string;
        userId: UserId;
        patch: ModuleDraftPatch;
    }): Promise<ModuleDetail> {
        const module = await this.prisma.module.findFirst({
            where: { id: args.moduleId, tenantId: args.tenantId },
        });
        if (!module) {
            throw this.notFoundError('Module draft not found');
        }

        await this.prisma.module.update({
            where: { id: args.moduleId },
            data: this.buildModuleUpdateInput(args.patch, args.userId),
        });

        return this.fetchModuleDetailBySlug(args.tenantId, module.slug);
    }

    async publishModule(args: {
        tenantId: TenantId;
        moduleId: string;
        userId: UserId;
    }): Promise<ModuleDetail> {
        const module = await this.prisma.module.findFirst({
            where: { id: args.moduleId, tenantId: args.tenantId },
            include: {
                learningObjectives: true,
                contentBlocks: true,
                tags: true,
                prerequisites: true,
            },
        });
        if (!module) {
            throw this.notFoundError('Module draft not found');
        }

        await this.prisma.moduleRevision.create({
            data: {
                moduleId: args.moduleId,
                version: module.version,
                createdBy: args.userId,
                snapshot: {
                    title: module.title,
                    description: module.description,
                    learningObjectives: module.learningObjectives,
                    contentBlocks: module.contentBlocks,
                    tags: module.tags,
                    prerequisites: module.prerequisites,
                },
            },
        });

        await this.prisma.module.update({
            where: { id: args.moduleId },
            data: {
                status: 'PUBLISHED',
                version: module.version + 1,
                publishedAt: new Date(),
                updatedBy: args.userId,
            },
        });

        return this.fetchModuleDetailBySlug(args.tenantId, module.slug);
    }

    // ===========================================================================
    // Helpers
    // ===========================================================================

    private async fetchModuleDetailBySlug(
        tenantId: TenantId,
        slug: string
    ): Promise<ModuleDetail> {
        const { module } = await this.contentService.getModuleBySlug(tenantId, slug);
        return module;
    }

    private buildModuleCreateInput(
        tenantId: TenantId,
        authorId: string,
        input: ModuleDraftInput
    ): any {
        return {
            id: input.slug,
            tenantId,
            slug: input.slug,
            title: input.title,
            description: input.description,
            domain: input.domain,
            difficulty: input.difficulty,
            estimatedTimeMinutes: input.estimatedTimeMinutes,
            status: 'DRAFT',
            authorId,
            updatedBy: authorId,
            updatedAt: new Date(),
            tags: {
                create: input.tags.map((label: string) => ({ label })),
            },
            learningObjectives: {
                create: input.learningObjectives.map((objective: any) => ({
                    label: objective.label,
                    taxonomyLevel: objective.taxonomyLevel,
                })),
            },
            contentBlocks: {
                create: input.contentBlocks.map((block: any, index: number) => ({
                    id: block.id || `${input.slug}-block-${index}`,
                    orderIndex: block.orderIndex ?? index,
                    blockType: block.blockType,
                    payload: block.payload,
                })),
            },
            prerequisites: {
                create: (input.prerequisites ?? []).map((id: string) => ({
                    prerequisiteModuleId: id,
                })),
            },
        };
    }

    private buildModuleUpdateInput(patch: ModuleDraftPatch, userId: string): any {
        const data: any = {
            updatedBy: userId,
            updatedAt: new Date(),
        };

        if (patch.title) data.title = patch.title;
        if (patch.description) data.description = patch.description;
        if (patch.estimatedTimeMinutes)
            data.estimatedTimeMinutes = patch.estimatedTimeMinutes;
        if (patch.difficulty) data.difficulty = patch.difficulty;

        if (patch.tags) {
            data.tags = {
                deleteMany: {},
                create: patch.tags.map((label: string) => ({ label })),
            };
        }

        if (patch.learningObjectives) {
            data.learningObjectives = {
                deleteMany: {},
                create: patch.learningObjectives.map((objective: any) => ({
                    label: objective.label,
                    taxonomyLevel: objective.taxonomyLevel,
                })),
            };
        }

        if (patch.contentBlocks) {
            data.contentBlocks = {
                deleteMany: {},
                create: patch.contentBlocks.map((block: any, index: number) => ({
                    id: block.id ?? `${Date.now()}-${index}`,
                    orderIndex: block.orderIndex ?? index,
                    blockType: block.blockType,
                    payload: block.payload,
                })),
            };
        }

        return data;
    }

    private mapModuleSummary(module: any): ModuleSummary {
        return {
            id: module.id as ModuleId,
            slug: module.slug,
            title: module.title,
            domain: module.domain as ModuleSummary['domain'],
            difficulty: module.difficulty as ModuleSummary['difficulty'],
            estimatedTimeMinutes: module.estimatedTimeMinutes,
            tags: module.tags.map((tag: any) => tag.label),
            status: module.status as ModuleSummary['status'],
            publishedAt: module.publishedAt?.toISOString(),
        };
    }

    private notFoundError(
        message: string
    ): Error & { code: string; statusCode: number } {
        const error = new Error(message) as Error & {
            code: string;
            statusCode: number;
        };
        error.code = 'CMS_NOT_FOUND';
        error.statusCode = 404;
        return error;
    }
}
