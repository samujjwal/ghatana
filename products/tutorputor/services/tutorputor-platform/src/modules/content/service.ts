/**
 * @doc.type module
 * @doc.purpose Content service for modules and lessons
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type {
  ContentService,
  AIProxyService,
  ModuleDetail,
  ModuleSummary,
  Enrollment,
  ModuleId,
  TenantId,
  UserId,
} from "@tutorputor/contracts";
import { ModalityValidator } from "../../utils/modality-validator";
import { createStandaloneLogger } from "@tutorputor/core/logger";
import { NotFoundError, ConflictError } from "../../core/errors";

const logger = createStandaloneLogger({ component: "ContentService" });

type TagRecord = {
  label: string;
};

type EnrollmentRecord = {
  id: string;
  moduleId: string;
  userId: string;
  status: string;
  progressPercent: number;
  startedAt?: Date | null;
  completedAt?: Date | null;
  timeSpentSeconds: number;
};

type ModuleSummaryRecord = {
  id: string;
  slug: string;
  title: string;
  domain: string;
  difficulty: string;
  estimatedTimeMinutes: number;
  tags?: TagRecord[];
  enrollments?: EnrollmentRecord[];
};

type ModuleDetailRecord = ModuleSummaryRecord & {
  description: string;
  learningObjectives?: Array<{
    id: string;
    label: string;
    taxonomyLevel: string;
  }>;
  contentBlocks?: Array<{
    id: string;
    orderIndex: number;
    blockType: string;
    payload: unknown;
  }>;
  prerequisites?: Array<{
    prerequisiteModuleId: string;
  }>;
  version: number;
};

export class ContentServiceImpl implements ContentService {
  private readonly modalityValidator: ModalityValidator;

  constructor(
    private readonly prisma: PrismaClient,
    private readonly aiProxy?: AIProxyService,
  ) {
    this.modalityValidator = new ModalityValidator(prisma);
  }

  async getModuleBySlug(
    tenantId: TenantId,
    slug: string,
    userId?: UserId,
  ): Promise<{ module: ModuleDetail; enrollment?: Enrollment }> {
    const module = await this.prisma.module.findFirst({
      where: { tenantId, slug },
      include: this.buildDetailInclude(tenantId, userId),
    });

    if (!module) {
      throw new NotFoundError("Module", slug);
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
      ...(enrollmentRecord
        ? { enrollment: this.mapEnrollment(enrollmentRecord) }
        : {}),
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
    const where: Record<string, unknown> = { tenantId: args.tenantId };

    if (args.status) {
      where.status = { equals: args.status };
    } else {
      where.status = "PUBLISHED";
    }

    if (args.domain) {
      where.domain = { equals: args.domain };
    }

    if (args.query && this.aiProxy) {
      // If AI Proxy is available, we could do semantic search or refined parsing
      try {
        const filters = await this.aiProxy.parseContentQuery(args.query);

        if (filters.domain) where.domain = { equals: filters.domain };
        if (filters.difficulty)
          where.difficulty = { equals: filters.difficulty };

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
      orderBy: { title: "asc" },
      ...(args.cursor ? { cursor: { id: args.cursor } } : {}),
      skip: args.cursor ? 1 : 0,
      include: this.buildSummaryInclude(args.tenantId, args.userId),
    });

    const hasMore = modules.length > take;
    const trimmed = modules.slice(0, take);
    const items = trimmed.map((module) => this.mapModuleSummary(module));

    return {
      items,
      nextCursor: hasMore ? (modules[modules.length - 1]?.id ?? null) : null,
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
  async publishExperience(
    experienceId: string,
    tenantId: TenantId,
  ): Promise<void> {
    // Verify experience exists and belongs to tenant
    const experience = await this.prisma.learningExperience.findFirst({
      where: { id: experienceId, tenantId },
      select: { id: true, status: true },
    });

    if (!experience) {
      throw new NotFoundError("Experience", experienceId);
    }

    if (experience.status === "PUBLISHED") {
      throw new ConflictError(
        `Experience ${experienceId} is already published`,
        "ALREADY_PUBLISHED",
      );
    }

    // Validate and publish atomically
    await this.prisma.$transaction(async (tx) => {
      // Validate all claims have modalities before publishing
      await this.modalityValidator.validateExperienceForPublishing(
        experienceId,
      );

      // Proceed with publishing
      await tx.learningExperience.update({
        where: { id: experienceId },
        data: {
          status: "PUBLISHED",
          publishedAt: new Date(),
          updatedAt: new Date(),
        },
      });
    });

    // Log successful validation and publish
    logger.info({
      message: "Published experience after modality validation",
      experienceId,
    });
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
      select: { claimRef: true },
    });

    const claimStatuses = await Promise.all(
      claims.map(async (claim: { claimRef: string }) => {
        const validation = await this.modalityValidator.validateClaimModality(
          experienceId,
          claim.claimRef,
        );
        return {
          claimRef: claim.claimRef,
          valid: validation.valid,
          modalities: validation.modalities,
          preferredModality: validation.preferredModality ?? null,
        };
      }),
    );

    const errors = claimStatuses
      .filter((c: { valid: boolean; claimRef: string }) => !c.valid)
      .map(
        (c: { valid: boolean; claimRef: string }) =>
          `Claim ${c.claimRef} has no supporting modalities`,
      );

    return {
      experienceId,
      readyToPublish: errors.length === 0,
      claims: claimStatuses,
      errors,
    };
  }

  // ===========================================================================
  // Helpers
  // ===========================================================================

  private buildSummaryInclude(
    tenantId: TenantId,
    userId?: UserId,
  ): Record<string, unknown> {
    const include: Record<string, unknown> = {
      tags: true,
    };
    if (userId) {
      include.enrollments = {
        where: { tenantId, userId },
      };
    }
    return include;
  }

  private buildDetailInclude(
    tenantId: TenantId,
    userId?: UserId,
  ): Record<string, unknown> {
    const include: Record<string, unknown> = {
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

  private mapModuleSummary(module: ModuleSummaryRecord): ModuleSummary {
    const enrollment = module.enrollments?.[0];
    const tags = module.tags ?? [];
    const summary: ModuleSummary = {
      id: module.id as ModuleId,
      slug: module.slug,
      title: module.title,
      domain: module.domain as ModuleSummary["domain"],
      difficulty: module.difficulty as ModuleSummary["difficulty"],
      estimatedTimeMinutes: module.estimatedTimeMinutes,
      tags: tags.map((tag) => tag.label),
      status: "PUBLISHED",
    };
    if (enrollment?.progressPercent !== undefined) {
      summary.progressPercent = enrollment.progressPercent;
    }
    return summary;
  }

  private mapModuleDetail(module: ModuleDetailRecord): ModuleDetail {
    const contentBlocks = module.contentBlocks ?? [];
    const learningObjectives = module.learningObjectives ?? [];
    const prerequisites = module.prerequisites ?? [];
    return {
      ...this.mapModuleSummary(module),
      description: module.description,
      learningObjectives: learningObjectives.map((objective) => ({
        id: `${module.id}-${objective.id}`,
        label: objective.label,
        taxonomyLevel:
          objective.taxonomyLevel as ModuleDetail["learningObjectives"][number]["taxonomyLevel"],
      })),
      contentBlocks: contentBlocks
        .slice()
        .sort((a, b) => a.orderIndex - b.orderIndex)
        .map((block) => ({
          id: block.id,
          orderIndex: block.orderIndex,
          blockType:
            block.blockType as ModuleDetail["contentBlocks"][number]["blockType"],
          payload: block.payload,
        })),
      prerequisites: prerequisites.map(
        (prereq) => prereq.prerequisiteModuleId as ModuleId,
      ),
      version: module.version,
    };
  }

  private mapEnrollment(record: EnrollmentRecord): Enrollment {
    return {
      id: record.id as Enrollment["id"],
      moduleId: record.moduleId as ModuleId,
      userId: record.userId as UserId,
      status: record.status as Enrollment["status"],
      progressPercent: record.progressPercent,
      ...(record.startedAt
        ? { startedAt: record.startedAt.toISOString() }
        : {}),
      ...(record.completedAt
        ? { completedAt: record.completedAt.toISOString() }
        : {}),
      timeSpentSeconds: record.timeSpentSeconds,
    };
  }
}
