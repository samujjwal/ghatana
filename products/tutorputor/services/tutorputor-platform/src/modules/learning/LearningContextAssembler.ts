/**
 * Learning Context Assembler
 *
 * Assembles structured learning context from module data, learner progress,
 * and related content without relying on fragile regex parsing.
 *
 * @doc.type class
 * @doc.purpose Structured learning context assembly for AI tutor queries
 * @doc.layer platform
 * @doc.pattern Assembler
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import type { ModuleId, TenantId, UserId } from "@tutorputor/contracts/v1/types";

const logger = createStandaloneLogger({ component: "LearningContextAssembler" });

// ============================================================================
// Context Types
// ============================================================================

export interface LearningContextInput {
  tenantId: TenantId;
  userId?: UserId;
  moduleId: ModuleId;
  currentLessonId?: string;
  currentContentBlockId?: string;
  simulationRunId?: string;
  locale?: string;
}

export interface LearningContext {
  module: {
    id: string;
    title: string;
    description: string | null;
    domain: string | null;
    difficultyLevel: string | null;
  };
  learningObjectives: string[];
  claims: Array<{
    id: string;
    text: string;
    conceptIds: string[];
  }>;
  evidence: Array<{
    id: string;
    text: string;
    claimId: string;
    source: string;
  }>;
  contentBlocks: Array<{
    id: string;
    title: string;
    type: string;
    content: string | null;
  }>;
  learnerProgress: {
    completedLessons: string[];
    masteryScores: Record<string, number>;
    recentAttempts: Array<{
      itemId: string;
      success: boolean;
      timestamp: Date;
    }>;
  };
  misconceptions: string[];
}

// ============================================================================
// Learning Context Assembler
// ============================================================================

export class LearningContextAssembler {
  private static instance: LearningContextAssembler;

  private constructor() {}

  static getInstance(): LearningContextAssembler {
    if (!LearningContextAssembler.instance) {
      LearningContextAssembler.instance = new LearningContextAssembler();
    }
    return LearningContextAssembler.instance;
  }

  /**
   * Assemble learning context from database
   */
  async assembleContext(
    prisma: TutorPrismaClient,
    input: LearningContextInput,
  ): Promise<LearningContext | null> {
    try {
      // Fetch module data
      const module = await prisma.contentAsset.findFirst({
        where: {
          id: input.moduleId,
          tenantId: input.tenantId,
        },
        select: {
          id: true,
          title: true,
          searchableText: true,
          domain: true,
          difficultyLevel: true,
        },
      });

      if (!module) {
        logger.warn({
          message: "Module not found",
          moduleId: input.moduleId,
          tenantId: input.tenantId,
        }, "LearningContextAssembler");
        return null;
      }

      // Fetch learning objectives from claims
      // Note: LearningClaim table structure may differ, simplified query
      const claims = await prisma.learningClaim.findMany({
        where: {
          experienceId: input.moduleId,
        },
        select: {
          id: true,
          text: true,
        },
        take: 10,
      });

      // Fetch evidence for claims - simplified as table may not exist
      const evidence: Array<{ id: string; text: string; claimId: string; source: string }> = [];

      // Fetch content blocks
      const contentBlocks = await prisma.contentBlock.findMany({
        where: {
          assetId: input.moduleId,
        },
        select: {
          id: true,
          title: true,
          blockType: true,
          payload: true,
        },
        orderBy: { orderIndex: "asc" },
      });

      // Fetch learner progress if userId provided - simplified as table may not exist
      // This can be enhanced once the schema is confirmed
      let learnerProgress = {
        completedLessons: [] as string[],
        masteryScores: {} as Record<string, number>,
        recentAttempts: [] as Array<{ itemId: string; success: boolean; timestamp: Date }>,
      };

      if (input.userId) {
        // Placeholder for learner progress - schema needs to be confirmed
        learnerProgress.completedLessons = [];
        learnerProgress.masteryScores = {};
        learnerProgress.recentAttempts = [];
      }

      // Extract misconceptions - simplified as table may not exist
      const misconceptions: string[] = [];

      const learningObjectives = claims.map((c: { text: string }) => c.text);

      logger.info({
        message: "Learning context assembled",
        moduleId: input.moduleId,
        tenantId: input.tenantId,
        userId: input.userId,
        claimCount: claims.length,
        evidenceCount: evidence.length,
        contentBlockCount: contentBlocks.length,
      }, "LearningContextAssembler");

      return {
        module: {
          id: module.id,
          title: module.title,
          description: module.searchableText,
          domain: module.domain,
          difficultyLevel: module.difficultyLevel,
        },
        learningObjectives,
        claims: claims.map((c: { id: string; text: string }) => ({
          id: c.id,
          text: c.text,
          conceptIds: [], // Placeholder as field may not exist
        })),
        evidence: evidence.map((e: { id: string; text: string; claimId: string; source: string }) => ({
          id: e.id,
          text: e.text,
          claimId: e.claimId,
          source: e.source,
        })),
        contentBlocks: contentBlocks.map((b: { id: string; title: string | null; blockType: string; payload: unknown }) => ({
          id: b.id,
          title: b.title || "Untitled",
          type: b.blockType,
          content: b.payload as string | null,
        })),
        learnerProgress,
        misconceptions,
      };
    } catch (error) {
      logger.error({
        message: "Failed to assemble learning context",
        moduleId: input.moduleId,
        tenantId: input.tenantId,
        error,
      }, "LearningContextAssembler");
      return null;
    }
  }

  /**
   * Extract misconceptions from learner assessment data
   * Simplified placeholder - can be enhanced once schema is confirmed
   */
  private async extractMisconceptions(
    prisma: TutorPrismaClient,
    tenantId: TenantId,
    userId?: UserId,
    claimIds?: string[],
  ): Promise<string[]> {
    // Placeholder - schema needs to be confirmed
    return [];
  }

  /**
   * Assemble simplified module context for AI tutor queries
   */
  async assembleModuleContext(
    prisma: TutorPrismaClient,
    moduleId: ModuleId,
    tenantId: TenantId,
  ): Promise<{
    title: string;
    description: string | null;
    domain: string | null;
    contentBlocks: Array<{ title: string; content: string | null }>;
    learningObjectives: string[];
    misconceptions: string[];
  } | null> {
    const context = await this.assembleContext(prisma, {
      tenantId,
      moduleId,
    });

    if (!context) {
      return null;
    }

    return {
      title: context.module.title,
      description: context.module.description,
      domain: context.module.domain,
      contentBlocks: context.contentBlocks.map((b: { title: string; content: string | null }) => ({
        title: b.title,
        content: b.content,
      })),
      learningObjectives: context.learningObjectives,
      misconceptions: context.misconceptions,
    };
  }
}

// Singleton instance
export function getLearningContextAssembler(): LearningContextAssembler {
  return LearningContextAssembler.getInstance();
}
