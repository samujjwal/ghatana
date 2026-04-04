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

import type { PathwaysService } from "@tutorputor/contracts/v1/services";
import type {
  LearningPath,
  LearningPathNode,
  LearningPathRecommendation,
  LearningPathId,
  LearningPathNodeId,
  ModuleId,
  ModuleSummary,
  TenantId,
  UserId,
} from "@tutorputor/contracts/v1/types";
import type { TutorPrismaClient } from "@tutorputor/core/db";

import { aiClient } from "../../clients/ai-client";
import { createStandaloneLogger } from "@tutorputor/core/logger";

const logger = createStandaloneLogger({ component: "PathwaysService" });

// =============================================================================
// Types
// =============================================================================

export type HealthAwarePathwaysService = PathwaysService & {
  checkHealth: () => Promise<boolean>;
};

type LearningPathWithNodes = Record<string, unknown>;

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
): Promise<"beginner" | "intermediate" | "advanced"> {
  // Fetch completed enrollments
  const enrollments = await prisma.enrollment.findMany({
    where: { tenantId, userId, status: "COMPLETED" },
    include: { module: { select: { difficulty: true } } },
  });

  const completedCount = enrollments.length;
  if (completedCount === 0) return "beginner";

  // Fetch graded assessment attempts
  const attempts = await prisma.assessmentAttempt.findMany({
    where: {
      userId,
      status: "GRADED",
      assessment: { module: { tenantId } },
    },
    select: { scorePercent: true },
  });

  const gradedScores = attempts
    .map((a: Record<string, unknown>) => a.scorePercent as number)
    .filter((s: number | null): s is number => s !== null);

  const avgScore =
    gradedScores.length > 0
      ? gradedScores.reduce((sum: number, s: number) => sum + s, 0) /
        gradedScores.length
      : 0;

  // Count advanced-difficulty completions
  const advancedCount = enrollments.filter(
    (e: Record<string, unknown>) =>
      (e.module as Record<string, unknown>)?.difficulty === "ADVANCED",
  ).length;
  const advancedRatio = advancedCount / completedCount;

  // Heuristic thresholds
  if (completedCount >= 8 && avgScore >= 75 && advancedRatio >= 0.3) {
    return "advanced";
  }
  if (completedCount >= 3 && avgScore >= 55) {
    return "intermediate";
  }
  return "beginner";
}

export function createPathwaysService(
  prisma: TutorPrismaClient,
): HealthAwarePathwaysService {
  return {
    async generatePathway({ tenantId, userId, goal, constraints }) {
      // 1. Try AI Generation Plan
      try {
        const learnerLevel = await computeLearnerLevel(
          prisma,
          tenantId,
          userId,
        );

        const aiPath = await aiClient.generateLearningPath({
          subject: goal,
          goal: goal,
          learner_level: learnerLevel,
          context_id: userId,
        });

        if (aiPath && aiPath.nodes && aiPath.nodes.length > 0) {
          // Map AI nodes to DB modules via search
          const pathNodes: Array<Record<string, unknown>> = []; // Temporary type as we build for create

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
              if (
                !pathNodes.find(
                  (n: Record<string, unknown>) => n.contentId === match.id,
                )
              ) {
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
            await prisma.learningPath.create({
              data: {
                tenantId,
                userId,
                title: aiPath.title || `Path: ${goal}`,
                goal: goal,
                status: "ACTIVE",
                nodes: {
                  create: pathNodes.map((n: Record<string, unknown>) => ({
                    moduleId: String(n.contentId),
                    orderIndex: Number(n.orderIndex ?? 0),
                  })),
                },
              },
              include: { nodes: true },
            });

            const selected = pathNodes.map((n: Record<string, unknown>) => ({
              id: String(n.contentId) as ModuleId,
              slug: "",
              title: String(n.title ?? ""),
              domain: "TECH" as const,
              difficulty: "INTRO" as const,
              estimatedTimeMinutes: 30,
              tags: [],
              status: "PUBLISHED" as const,
            }));

            return {
              modules: selected,
              reasoning: `Generated pathway for ${goal} using AI-ranked modules.`,
              estimatedDurationMinutes: selected.length * 30,
            } as LearningPathRecommendation;
          }
        }
      } catch (err) {
        logger.warn({
          message: "AI Pathway generation failed, falling back to heuristics",
          error: err instanceof Error ? err.message : String(err),
          goal,
          tenantId,
        });
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
      const scoredModules = modules.map((module: Record<string, unknown>) => {
        let score = 0;
        const goalLower = goal.toLowerCase();

        // Title match
        if ((module.title as string).toLowerCase().includes(goalLower))
          score += 10;

        // Description match
        if ((module.description as string).toLowerCase().includes(goalLower))
          score += 5;

        // Tag match
        const tagMatch = (module.tags as Array<Record<string, unknown>>).some(
          (t: Record<string, unknown>) =>
            (t.label as string).toLowerCase().includes(goalLower),
        );
        if (tagMatch) score += 8;

        // Domain relevance
        if (goalLower.includes((module.domain as string).toLowerCase()))
          score += 7;

        // Prefer easier modules first (lower difficulty = higher score)
        if (module.difficulty === "INTRO") score += 3;
        else if (module.difficulty === "INTERMEDIATE") score += 2;

        return { module, score };
      });

      // Sort by score and limit
      scoredModules.sort(
        (a: Record<string, unknown>, b: Record<string, unknown>) =>
          (b.score as number) - (a.score as number),
      );
      const selectedModules = scoredModules.slice(0, maxModules);

      // Calculate estimated duration
      let totalDuration = 0;
      const filteredModules: ModuleSummary[] = [];

      for (const { module } of selectedModules) {
        const estimatedMinutes = Number(module.estimatedTimeMinutes ?? 0);
        if (
          constraints?.maxDurationMinutes &&
          totalDuration + estimatedMinutes > constraints.maxDurationMinutes
        ) {
          continue;
        }
        totalDuration += estimatedMinutes;
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
      const node = path.nodes.find(
        (n: Record<string, unknown>) => n.moduleId === completedModuleId,
      );
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

      const requiredNodes = updatedPath.nodes.filter(
        (n: Record<string, unknown>) => !n.isOptional,
      );
      const allCompleted = requiredNodes.every(
        (n: Record<string, unknown>) => n.completedAt !== null,
      );

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

function mapToModuleSummary(module: Record<string, unknown>): ModuleSummary {
  return {
    id: module.id as ModuleId,
    slug: module.slug as string,
    title: module.title as string,
    domain: module.domain as "MATH" | "SCIENCE" | "TECH",
    difficulty: module.difficulty as "INTRO" | "INTERMEDIATE" | "ADVANCED",
    estimatedTimeMinutes: module.estimatedTimeMinutes as number,
    tags: (module.tags as Array<Record<string, unknown>>).map(
      (t: Record<string, unknown>) => t.label as string,
    ),
    status: module.status as "DRAFT" | "PUBLISHED" | "ARCHIVED",
    ...((module.publishedAt as Date | undefined)
      ? { publishedAt: (module.publishedAt as Date).toISOString() }
      : {}),
  };
}

function mapToLearningPath(path: LearningPathWithNodes): LearningPath {
  const nodes =
    (path.nodes as Array<Record<string, unknown>> | undefined) ?? [];
  const mappedNodes: LearningPathNode[] = nodes.map(
    (node: Record<string, unknown>) => {
      const mappedNode: LearningPathNode = {
        id: node.id as LearningPathNodeId,
        moduleId: node.moduleId as ModuleId,
        orderIndex: node.orderIndex as number,
        isOptional: node.isOptional as boolean,
      };
      if (node.completedAt instanceof Date) {
        mappedNode.completedAt = node.completedAt.toISOString();
      }
      return mappedNode;
    },
  );

  return {
    id: path.id as LearningPathId,
    userId: path.userId as UserId,
    tenantId: path.tenantId as TenantId,
    title: path.title as string,
    goal: path.goal as string,
    status: path.status as "ACTIVE" | "COMPLETED" | "PAUSED",
    nodes: mappedNodes,
    createdAt: (path.createdAt as Date).toISOString(),
    updatedAt: (path.updatedAt as Date).toISOString(),
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
