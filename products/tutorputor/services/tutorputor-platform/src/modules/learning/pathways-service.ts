/**
 * Pathways Service (Migrated)
 *
 * AI-driven personalized learning paths.
 * Migrated from tutorputor-pathways/src/service.ts
 *
 * @doc.type service
 * @doc.purpose Personalized learning pathways
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PathwaysService } from "@ghatana/tutorputor-contracts/v1/services";
import type {
  LearningPath,
  LearningPathNode,
  LearningPathRecommendation,
  LearningPathId,
  LearningPathNodeId,
  ModuleId,
  ModuleSummary,
  PathwayConstraints,
  TenantId,
  UserId,
} from "@ghatana/tutorputor-contracts/v1/types";
import type { TutorPrismaClient } from "@ghatana/tutorputor-db";

import { aiClient } from "../../clients/ai-client";

// =============================================================================
// Types
// =============================================================================

export type HealthAwarePathwaysService = PathwaysService & {
  checkHealth: () => Promise<boolean>;
};

type LearningPathWithNodes = any;

// =============================================================================
// Implementation
// =============================================================================

/**
 * Compute learner level from enrollment history and assessment performance.
 * Returns 'beginner' | 'intermediate' | 'advanced' based on:
 *   - Number of completed modules
 *   - Average assessment score across graded attempts
 *   - Proportion of advanced-difficulty modules completed
 */
async function computeLearnerLevel(
  prisma: TutorPrismaClient,
  tenantId: string,
  userId: string,
): Promise<'beginner' | 'intermediate' | 'advanced'> {
  // Fetch completed enrollments
  const enrollments = await prisma.enrollment.findMany({
    where: { tenantId, userId, status: 'COMPLETED' },
    include: { module: { select: { difficulty: true } } },
  });

  const completedCount = enrollments.length;
  if (completedCount === 0) return 'beginner';

  // Fetch graded assessment attempts
  const attempts = await prisma.assessmentAttempt.findMany({
    where: {
      userId,
      status: 'GRADED',
      assessment: { module: { tenantId } },
    },
    select: { scorePercent: true },
  });

  const gradedScores = attempts
    .map((a: any) => a.scorePercent)
    .filter((s: number | null): s is number => s !== null);

  const avgScore =
    gradedScores.length > 0
      ? gradedScores.reduce((sum: number, s: number) => sum + s, 0) / gradedScores.length
      : 0;

  // Count advanced-difficulty completions
  const advancedCount = enrollments.filter(
    (e: any) => e.module?.difficulty === 'ADVANCED',
  ).length;
  const advancedRatio = advancedCount / completedCount;

  // Heuristic thresholds
  if (completedCount >= 8 && avgScore >= 75 && advancedRatio >= 0.3) {
    return 'advanced';
  }
  if (completedCount >= 3 && avgScore >= 55) {
    return 'intermediate';
  }
  return 'beginner';
}

export function createPathwaysService(
  prisma: TutorPrismaClient,
): HealthAwarePathwaysService {
  return {
    async generatePathway({ tenantId, userId, goal, constraints }) {
      // 1. Try AI Generation Plan
      try {
        const learnerLevel = await computeLearnerLevel(prisma, tenantId, userId);

        const aiPath = await aiClient.generateLearningPath({
          subject: goal,
          goal: goal,
          learner_level: learnerLevel,
          context_id: userId,
        });

        if (aiPath && aiPath.nodes && aiPath.nodes.length > 0) {
          // Map AI nodes to DB modules via search
          const pathNodes: any[] = []; // Temporary type as we build for create

          for (const aiNode of aiPath.nodes) {
            // Find best matching module
            const match = await prisma.module.findFirst({
              where: {
                tenantId,
                status: "PUBLISHED",
                OR: [
                  { title: { contains: aiNode.title, mode: "insensitive" } },
                  {
                    description: {
                      contains: aiNode.title,
                      mode: "insensitive",
                    },
                  },
                  {
                    tags: {
                      some: {
                        label: { contains: aiNode.title, mode: "insensitive" },
                      },
                    },
                  },
                ],
              },
              include: { tags: true },
            });

            if (match) {
              // Prevent duplicates
              if (!pathNodes.find((n: any) => n.contentId === match.id)) {
                pathNodes.push({
                  title: match.title,
                  description: match.description ?? "",
                  type: "MODULE",
                  contentId: match.id,
                  status: "PENDING",
                  orderIndex: pathNodes.length,
                  metadata: { ai_node_id: aiNode.id },
                });
              }
            }
          }

          if (pathNodes.length > 0) {
            // Create the paths
            const path = await prisma.learningPath.create({
              data: {
                tenantId,
                userId,
                title: aiPath.title || `Path: ${goal}`,
                goal: goal,
                status: "ACTIVE",
                nodes: {
                  create: pathNodes.map((n: any) => ({
                    moduleId: n.contentId,
                    orderIndex: n.orderIndex,
                  })),
                },
              },
              include: { nodes: true },
            });

            return {
              id: path.id as LearningPathId,
              title: path.title,
              description: path.goal,
              progress: 0,
              nodes: pathNodes.map((n: any) => ({
                id: `temp-${n.orderIndex}` as LearningPathNodeId,
                title: n.title,
                description: n.description,
                type: "MODULE",
                contentId: n.contentId,
                status: "PENDING",
                orderIndex: n.orderIndex,
                estimatedTimeMinutes: 30,
              })),
            } as any;
          }
        }
      } catch (err) {
        console.warn(
          "AI Pathway generation failed, falling back to heuristics.",
          err,
        );
      }

      // Fetch available modules considering constraints
      const excludeIds = constraints?.excludeModuleIds ?? [];
      const maxModules = constraints?.maxModules ?? 10;

      const modules = await prisma.module.findMany({
        where: {
          tenantId,
          status: "PUBLISHED",
          ...(excludeIds.length > 0 ? { id: { notIn: excludeIds } } : {}),
        },
        orderBy: [{ difficulty: "asc" }, { estimatedTimeMinutes: "asc" }],
        take: maxModules * 2, // Fetch more to allow filtering
        include: {
          tags: true,
          prerequisites: true,
          learningObjectives: true,
        },
      });

      // Score and rank modules based on goal relevance
      const scoredModules = modules.map((module: any) => {
        let score = 0;
        const goalLower = goal.toLowerCase();

        // Title match
        if (module.title.toLowerCase().includes(goalLower)) score += 10;

        // Description match
        if (module.description.toLowerCase().includes(goalLower)) score += 5;

        // Tag match
        const tagMatch = module.tags.some((t: any) =>
          t.label.toLowerCase().includes(goalLower),
        );
        if (tagMatch) score += 8;

        // Domain relevance
        if (goalLower.includes(module.domain.toLowerCase())) score += 7;

        // Prefer easier modules first (lower difficulty = higher score)
        if (module.difficulty === "INTRO") score += 3;
        else if (module.difficulty === "INTERMEDIATE") score += 2;

        return { module, score };
      });

      // Sort by score and limit
      scoredModules.sort((a: any, b: any) => b.score - a.score);
      const selectedModules = scoredModules.slice(0, maxModules);

      // Calculate estimated duration
      let totalDuration = 0;
      const filteredModules: ModuleSummary[] = [];

      for (const { module } of selectedModules) {
        if (
          constraints?.maxDurationMinutes &&
          totalDuration + module.estimatedTimeMinutes >
          constraints.maxDurationMinutes
        ) {
          continue;
        }
        totalDuration += module.estimatedTimeMinutes;
        filteredModules.push(mapToModuleSummary(module));
      }

      // Generate reasoning
      const reasoning = generateReasoning(goal, filteredModules);

      return {
        modules: filteredModules,
        reasoning,
        estimatedDurationMinutes: totalDuration,
      };
    },

    async getPathwayForUser({ tenantId, userId }) {
      const path = await prisma.learningPath.findFirst({
        where: {
          tenantId,
          userId,
          status: "ACTIVE",
        },
        include: {
          nodes: {
            orderBy: { orderIndex: "asc" },
          },
        },
      });

      if (!path) return null;

      return mapToLearningPath(path);
    },

    async createPathway({ tenantId, userId, title, goal, moduleIds }) {
      // Deactivate any existing active pathways
      await prisma.learningPath.updateMany({
        where: { tenantId, userId, status: "ACTIVE" },
        data: { status: "PAUSED" },
      });

      // Create new pathway
      const path = await prisma.learningPath.create({
        data: {
          tenantId,
          userId,
          title,
          goal,
          status: "ACTIVE",
          nodes: {
            create: moduleIds.map((moduleId, index) => ({
              moduleId,
              orderIndex: index,
              isOptional: false,
            })),
          },
        },
        include: {
          nodes: {
            orderBy: { orderIndex: "asc" },
          },
        },
      });

      return mapToLearningPath(path);
    },

    async advancePathway({ tenantId, userId, completedModuleId }) {
      // Find active pathway
      const path = await prisma.learningPath.findFirst({
        where: {
          tenantId,
          userId,
          status: "ACTIVE",
        },
        include: {
          nodes: {
            orderBy: { orderIndex: "asc" },
          },
        },
      });

      if (!path) {
        throw new Error("No active learning path found");
      }

      // Find and mark node as completed
      const node = path.nodes.find((n: any) => n.moduleId === completedModuleId);
      if (node) {
        await prisma.learningPathNode.update({
          where: { id: node.id },
          data: { completedAt: new Date() },
        });
      }

      // Check if all required nodes are completed
      const updatedPath = await prisma.learningPath.findFirst({
        where: { id: path.id },
        include: {
          nodes: {
            orderBy: { orderIndex: "asc" },
          },
        },
      });

      if (!updatedPath) {
        throw new Error("Path not found after update");
      }

      const requiredNodes = updatedPath.nodes.filter((n: any) => !n.isOptional);
      const allCompleted = requiredNodes.every((n: any) => n.completedAt !== null);

      if (allCompleted) {
        await prisma.learningPath.update({
          where: { id: path.id },
          data: { status: "COMPLETED" },
        });
        updatedPath.status = "COMPLETED";
      }

      return mapToLearningPath(updatedPath);
    },

    async checkHealth() {
      await prisma.$queryRaw`SELECT 1`;
      return true;
    },
  };
}

// =============================================================================
// Helper Functions
// =============================================================================

function mapToModuleSummary(module: any): ModuleSummary {
  return {
    id: module.id as ModuleId,
    slug: module.slug,
    title: module.title,
    domain: module.domain as "MATH" | "SCIENCE" | "TECH",
    difficulty: module.difficulty as "INTRO" | "INTERMEDIATE" | "ADVANCED",
    estimatedTimeMinutes: module.estimatedTimeMinutes,
    tags: module.tags.map((t: any) => t.label),
    status: module.status as "DRAFT" | "PUBLISHED" | "ARCHIVED",
    publishedAt: module.publishedAt?.toISOString(),
  };
}

function mapToLearningPath(path: LearningPathWithNodes): LearningPath {
  return {
    id: path.id as LearningPathId,
    userId: path.userId as UserId,
    tenantId: path.tenantId as TenantId,
    title: path.title,
    goal: path.goal,
    status: path.status as "ACTIVE" | "COMPLETED" | "PAUSED",
    nodes: path.nodes.map((node: any) => ({
      id: node.id as LearningPathNodeId,
      moduleId: node.moduleId as ModuleId,
      orderIndex: node.orderIndex,
      isOptional: node.isOptional,
      completedAt: node.completedAt?.toISOString(),
    })),
    createdAt: path.createdAt.toISOString(),
    updatedAt: path.updatedAt.toISOString(),
  };
}

function generateReasoning(goal: string, modules: ModuleSummary[]): string {
  if (modules.length === 0) {
    return `No modules found matching goal: "${goal}". Try adjusting your search criteria.`;
  }

  const domains = [...new Set(modules.map((m) => m.domain))];
  const difficulties = [...new Set(modules.map((m) => m.difficulty))];
  const totalTime = modules.reduce((sum, m) => sum + m.estimatedTimeMinutes, 0);
  const hours = Math.floor(totalTime / 60);
  const minutes = totalTime % 60;

  let reasoning = `Based on your goal "${goal}", I recommend ${modules.length} module(s) `;
  reasoning += `covering ${domains.join(", ")} `;
  reasoning += `at ${difficulties.join(" and ")} difficulty levels. `;
  reasoning += `Estimated total time: ${hours > 0 ? `${hours}h ` : ""}${minutes}m. `;
  reasoning += `Modules are ordered from foundational to advanced concepts.`;

  return reasoning;
}
