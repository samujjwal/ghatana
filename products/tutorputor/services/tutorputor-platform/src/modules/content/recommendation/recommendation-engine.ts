/**
 * Recommendation Engine
 *
 * Context-aware read layer over persisted recommendation edges.
 *
 * @doc.type service
 * @doc.purpose Learner-aware ranking of related assets and next steps
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";

interface NextStepSuggestion {
  reason: string;
  asset: {
    id: string;
    title: string;
    domain: string;
    difficultyLevel?: string;
    qualityScore?: number;
  };
  edge: {
    weight?: number;
    confidence?: number;
  };
}

interface RelatedAssetsResponse {
  prerequisites: NextStepSuggestion[];
  followUps: NextStepSuggestion[];
  related: NextStepSuggestion[];
  alternatives: NextStepSuggestion[];
}
import { RecommendationService } from "./recommendation-service.js";

export interface RecommendationOptions {
  assetId: string;
  tenantId: string;
  limit?: number;
}

export interface RecommendationContext {
  userProgress?: Record<string, number>;
  completedAssets?: string[];
  currentPathway?: string;
  learningGoals?: string[];
}

export class RecommendationEngine {
  private readonly recommendationService: RecommendationService;

  constructor(
    prisma: PrismaClient,
    deps: { recommendationService?: RecommendationService } = {},
  ) {
    this.recommendationService =
      deps.recommendationService ?? new RecommendationService(prisma);
  }

  async getRecommendations(
    options: RecommendationOptions,
    context?: RecommendationContext,
  ): Promise<RelatedAssetsResponse> {
    const result = await this.recommendationService.getRelatedAssets(
      options.tenantId,
      options.assetId,
      options.limit ?? 10,
    );

    return {
      prerequisites: this.rankSuggestions(result.prerequisites, context),
      followUps: this.rankSuggestions(result.followUps, context),
      related: this.rankSuggestions(result.related, context),
      alternatives: this.rankSuggestions(result.alternatives, context),
    };
  }

  async getNextSteps(
    options: RecommendationOptions,
    context?: RecommendationContext,
  ): Promise<NextStepSuggestion[]> {
    const result = await this.recommendationService.getNextSteps(
      options.tenantId,
      options.assetId,
      options.limit ?? 5,
    );

    return this.rankSuggestions(result, context);
  }

  private rankSuggestions(
    suggestions: NextStepSuggestion[],
    context?: RecommendationContext,
  ): NextStepSuggestion[] {
    const completedAssets = new Set(context?.completedAssets ?? []);
    const averageProgress = calculateAverageProgress(context?.userProgress);

    return suggestions
      .filter((suggestion) => !completedAssets.has(suggestion.asset.id))
      .map((suggestion) => ({
        suggestion,
        score: this.calculateSuggestionScore(
          suggestion,
          averageProgress,
          context,
        ),
      }))
      .sort((left, right) => right.score - left.score)
      .map(({ suggestion }) => suggestion);
  }

  private calculateSuggestionScore(
    suggestion: NextStepSuggestion,
    averageProgress: number,
    context?: RecommendationContext,
  ): number {
    const edgeWeight = suggestion.edge.weight ?? 0.5;
    const confidence = suggestion.edge.confidence ?? 0.5;
    const qualityScore = normalizeScore(suggestion.asset.qualityScore);
    const difficultyFit = this.scoreDifficultyFit(
      suggestion.asset.difficultyLevel,
      averageProgress,
    );
    const pathwayBoost =
      context?.currentPathway &&
      suggestion.reason
        .toLowerCase()
        .includes(context.currentPathway.toLowerCase())
        ? 0.1
        : 0;
    const goalBoost = context?.learningGoals?.some(
      (goal) =>
        goal.toLowerCase() === suggestion.asset.domain.toLowerCase() ||
        suggestion.asset.title.toLowerCase().includes(goal.toLowerCase()),
    )
      ? 0.12
      : 0;

    return (
      edgeWeight * 0.35 +
      confidence * 0.15 +
      qualityScore * 0.2 +
      difficultyFit * 0.2 +
      pathwayBoost +
      goalBoost
    );
  }

  private scoreDifficultyFit(
    difficultyLevel: string | undefined,
    averageProgress: number,
  ): number {
    const level = difficultyLevel?.toLowerCase() ?? "intermediate";

    if (averageProgress >= 0.75) {
      if (level === "advanced" || level === "expert") return 1;
      if (level === "intermediate") return 0.75;
      return 0.4;
    }

    if (averageProgress <= 0.35) {
      if (level === "beginner" || level === "elementary") return 1;
      if (level === "intermediate") return 0.65;
      return 0.25;
    }

    if (level === "intermediate") return 1;
    if (level === "advanced" || level === "beginner") return 0.7;
    return 0.55;
  }
}

function calculateAverageProgress(
  userProgress: Record<string, number> | undefined,
): number {
  if (!userProgress || Object.keys(userProgress).length === 0) {
    return 0.5;
  }

  const values = Object.values(userProgress);
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function normalizeScore(score: number | undefined): number {
  if (score == null) return 0.5;
  return score > 1 ? Math.max(0, Math.min(1, score / 100)) : score;
}
