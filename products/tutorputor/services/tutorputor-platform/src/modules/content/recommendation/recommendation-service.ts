/**
 * Recommendation Service
 *
 * Manages learning-relationship edges between canonical content assets.
 * Supports rule-based bootstrapping (domain, concept, difficulty
 * progression) and serves related/prerequisite/next-step queries.
 *
 * @doc.type class
 * @doc.purpose Recommendation edge management and next-step suggestions
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type {
  ContentAsset,
  ContentAssetType,
  RecommendationEdgeType,
  RecommendationSource,
  RecommendationEdge,
  NextStepSuggestion,
  RelatedAssetsResponse,
} from "../types.js";

type PersistedRecommendationSource =
  | "RULE_BASED"
  | "SEMANTIC"
  | "OUTCOME_AWARE"
  | "MANUAL";

// ---------------------------------------------------------------------------
// Difficulty ordering for progression rules
// ---------------------------------------------------------------------------

const DIFFICULTY_ORDER: Record<string, number> = {
  beginner: 0,
  elementary: 1,
  intermediate: 2,
  advanced: 3,
  expert: 4,
};

const POSITIVE_FEEDBACK = new Set(["positive", "helpful", "relevant"]);
const NEGATIVE_FEEDBACK = new Set(["negative", "not_relevant", "unhelpful"]);

function clamp01(value: number): number {
  return Math.max(0, Math.min(1, value));
}

function normalizeQualityScore(value: number | null | undefined): number {
  if (value == null) return 0.5;
  return value > 1 ? clamp01(value / 100) : clamp01(value);
}

function getDifficultyRank(level: string | null | undefined): number | null {
  if (!level) return null;
  return DIFFICULTY_ORDER[level.toLowerCase()] ?? null;
}

function computePathwayAffinity(
  source: Record<string, unknown>,
  target: Record<string, unknown>,
): number {
  if (
    source.conceptId &&
    target.conceptId &&
    source.conceptId === target.conceptId
  ) {
    return source.assetType === target.assetType ? 1 : 0.85;
  }
  if (source.domain && target.domain && source.domain === target.domain) {
    return 0.65;
  }
  return 0.35;
}

function inferEdgeType(
  source: Record<string, unknown>,
  target: Record<string, unknown>,
): Extract<RecommendationEdgeType, Uppercase<string>> {
  const sourceRank = getDifficultyRank(
    typeof source.difficultyLevel === "string" ? source.difficultyLevel : null,
  );
  const targetRank = getDifficultyRank(
    typeof target.difficultyLevel === "string" ? target.difficultyLevel : null,
  );

  if (
    source.conceptId &&
    target.conceptId &&
    source.conceptId === target.conceptId
  ) {
    return source.assetType === target.assetType ? "ALTERNATIVE" : "RELATED";
  }

  if (sourceRank != null && targetRank != null) {
    if (targetRank < sourceRank) return "PREREQUISITE";
    if (targetRank > sourceRank) return "FOLLOW_UP";
  }

  return "RELATED";
}

function inferBaseWeight(
  edgeType: Extract<RecommendationEdgeType, Uppercase<string>>,
): number {
  switch (edgeType) {
    case "PREREQUISITE":
      return 0.8;
    case "FOLLOW_UP":
      return 0.75;
    case "ALTERNATIVE":
      return 0.7;
    default:
      return 0.55;
  }
}

type RecommendationTelemetryEvent = {
  eventType?: string | null;
  feedbackLabel?: string | null;
};

type RawRecommendationAsset = {
  id: string;
  tenantId: string;
  slug: string;
  title: string;
  assetType: string;
  domain: string;
  status: string;
  currentVersion: number;
  targetGrades?: unknown;
  authorId: string;
  createdAt?: Date | null;
  updatedAt?: Date | null;
  riskLevel?: string | null;
  conceptId?: string | null;
  qualityScore?: number | null;
  semanticIndexStatus?: string | null;
  recommendationStatus?: string | null;
  tags?: unknown;
  difficultyLevel?: string | null;
  lastEditedBy?: string | null;
  publishedAt?: Date | null;
  promptHash?: string | null;
  confidenceScore?: number | null;
  legacyModuleId?: string | null;
  legacyExperienceId?: string | null;
  estimatedTimeMinutes?: number | null;
};

type RawRecommendationEdge = {
  id: string;
  sourceAssetId: string;
  targetAssetId: string;
  edgeType: string;
  source: string;
  weight: number;
  confidence?: number | null;
  reason?: string | null;
  metadata?: unknown;
  createdAt?: Date | null;
  updatedAt?: Date | null;
};

function summarizeTelemetry(events: RecommendationTelemetryEvent[]): {
  impressions: number;
  clicks: number;
  completions: number;
  nextStepSelections: number;
  positiveFeedback: number;
  negativeFeedback: number;
} {
  const summary = {
    impressions: 0,
    clicks: 0,
    completions: 0,
    nextStepSelections: 0,
    positiveFeedback: 0,
    negativeFeedback: 0,
  };

  for (const event of events) {
    const eventType = String(event.eventType ?? "").toUpperCase();
    if (eventType === "IMPRESSION") summary.impressions++;
    if (eventType === "CLICK") summary.clicks++;
    if (eventType === "ASSET_COMPLETE") summary.completions++;
    if (eventType === "NEXT_STEP_SELECT") summary.nextStepSelections++;
    if (eventType === "RANKING_FEEDBACK") {
      const label = String(event.feedbackLabel ?? "").toLowerCase();
      if (POSITIVE_FEEDBACK.has(label)) summary.positiveFeedback++;
      if (NEGATIVE_FEEDBACK.has(label)) summary.negativeFeedback++;
    }
  }

  return summary;
}

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

export class RecommendationService {
  constructor(private readonly prisma: PrismaClient) {}

  async recomputeOutcomeAwareEdges(
    tenantId: string,
    options: { sourceAssetId?: string; limit?: number } = {},
  ): Promise<{
    processedAssets: number;
    updatedEdges: number;
    skippedEdges: number;
  }> {
    const sourceWhere: Record<string, unknown> = {
      tenantId,
      status: "PUBLISHED",
    };

    if (options.sourceAssetId) {
      sourceWhere.id = options.sourceAssetId;
    }

    const sourceAssets = await this.prisma.contentAsset.findMany({
      where: sourceWhere,
      take: options.limit ?? 25,
      orderBy: { updatedAt: "desc" },
    });

    let processedAssets = 0;
    let updatedEdges = 0;
    let skippedEdges = 0;

    for (const sourceAsset of sourceAssets) {
      const candidateWhere: Record<string, unknown> = {
        tenantId,
        status: "PUBLISHED",
        id: { not: sourceAsset.id },
      };

      if (sourceAsset.conceptId) {
        candidateWhere.OR = [
          { domain: sourceAsset.domain },
          { conceptId: sourceAsset.conceptId },
        ];
      } else {
        candidateWhere.domain = sourceAsset.domain;
      }

      const candidates = await this.prisma.contentAsset.findMany({
        where: candidateWhere,
        take: 12,
        orderBy: { qualityScore: "desc" },
      });

      const candidateIds = candidates.map((candidate) => candidate.id);
      const [events, evaluations, existingEdges] = await Promise.all([
        candidateIds.length === 0
          ? []
          : this.prisma.explorerEvent.findMany({
              where: { tenantId, assetId: { in: candidateIds } },
            }),
        candidateIds.length === 0
          ? []
          : this.prisma.evaluationRecord.findMany({
              where: { tenantId, assetId: { in: candidateIds } },
              orderBy: { createdAt: "desc" },
            }),
        this.prisma.recommendationEdge.findMany({
          where: { sourceAssetId: sourceAsset.id },
        }),
      ]);

      const eventsByAsset = new Map<string, any[]>();
      for (const event of events) {
        if (!event.assetId) continue;
        const rows = eventsByAsset.get(event.assetId) ?? [];
        rows.push(event);
        eventsByAsset.set(event.assetId, rows);
      }

      const evaluationByAsset = new Map<string, Record<string, unknown>>();
      for (const evaluation of evaluations) {
        if (!evaluation.assetId || evaluationByAsset.has(evaluation.assetId)) {
          continue;
        }
        evaluationByAsset.set(evaluation.assetId, evaluation);
      }

      for (const candidate of candidates) {
        const edgeType = inferEdgeType(sourceAsset, candidate);
        const existingEdge = existingEdges.find(
          (edge) =>
            edge.targetAssetId === candidate.id && edge.edgeType === edgeType,
        );
        const telemetry = summarizeTelemetry(
          eventsByAsset.get(candidate.id) ?? [],
        );
        const interactionCount =
          telemetry.impressions +
          telemetry.clicks +
          telemetry.completions +
          telemetry.nextStepSelections +
          telemetry.positiveFeedback +
          telemetry.negativeFeedback;

        if (interactionCount < 3) {
          skippedEdges++;
          continue;
        }

        const baseWeight = existingEdge?.weight ?? inferBaseWeight(edgeType);
        const ctr =
          telemetry.impressions > 0
            ? telemetry.clicks / telemetry.impressions
            : 0;
        const completionRate =
          telemetry.clicks > 0 ? telemetry.completions / telemetry.clicks : 0;
        const feedbackScore =
          (telemetry.positiveFeedback + 1) /
          (telemetry.positiveFeedback + telemetry.negativeFeedback + 2);
        const evaluationScore = evaluationByAsset.get(
          candidate.id,
        )?.overallScore;
        const quality = normalizeQualityScore(
          typeof evaluationScore === "number"
            ? evaluationScore
            : candidate.qualityScore,
        );
        const pathwayAffinity = computePathwayAffinity(sourceAsset, candidate);

        const data = {
          sourceAssetId: sourceAsset.id,
          targetAssetId: candidate.id,
          edgeType,
          source: "OUTCOME_AWARE" as PersistedRecommendationSource,
          weight: clamp01(
            baseWeight * 0.3 +
              ctr * 0.2 +
              completionRate * 0.2 +
              feedbackScore * 0.15 +
              quality * 0.1 +
              pathwayAffinity * 0.05,
          ),
          confidence: clamp01(0.45 + interactionCount / 20),
          reason: `Outcome-aware refresh from ${interactionCount} learner signals`,
          metadata: {
            ctr,
            completionRate,
            feedbackScore,
            pathwayAffinity,
            quality,
            interactionCount,
          },
        };

        if (existingEdge) {
          await this.prisma.recommendationEdge.update({
            where: { id: existingEdge.id },
            data,
          });
        } else {
          await this.prisma.recommendationEdge.create({ data });
        }

        updatedEdges++;
      }

      await this.prisma.contentAsset.update({
        where: { id: sourceAsset.id },
        data: { recommendationStatus: "COMPUTED" },
      });
      processedAssets++;
    }

    return { processedAssets, updatedEdges, skippedEdges };
  }

  async recomputeOutcomeAwareEdgesForExperience(
    tenantId: string,
    experienceId: string,
    options: { limitPerAsset?: number } = {},
  ): Promise<{
    processedAssets: number;
    updatedEdges: number;
    skippedEdges: number;
  }> {
    const assets = await this.prisma.contentAsset.findMany({
      where: {
        tenantId,
        legacyExperienceId: experienceId,
        status: "PUBLISHED",
      },
      orderBy: { updatedAt: "desc" },
      select: { id: true },
    });

    let processedAssets = 0;
    let updatedEdges = 0;
    let skippedEdges = 0;

    for (const asset of assets) {
      const result = await this.recomputeOutcomeAwareEdges(tenantId, {
        sourceAssetId: asset.id,
        limit: options.limitPerAsset ?? 12,
      });
      processedAssets += result.processedAssets;
      updatedEdges += result.updatedEdges;
      skippedEdges += result.skippedEdges;
    }

    return { processedAssets, updatedEdges, skippedEdges };
  }

  /**
   * Get all recommendation edges from a source asset, grouped by type.
   */
  async getRelatedAssets(
    tenantId: string,
    assetId: string,
    limit = 10,
  ): Promise<RelatedAssetsResponse> {
    const edges = await this.prisma.recommendationEdge.findMany({
      where: {
        sourceAssetId: assetId,
        sourceAsset: { tenantId },
      },
      include: {
        targetAsset: true,
      },
      orderBy: { weight: "desc" },
    });

    const prerequisites: NextStepSuggestion[] = [];
    const followUps: NextStepSuggestion[] = [];
    const related: NextStepSuggestion[] = [];
    const alternatives: NextStepSuggestion[] = [];

    for (const edge of edges) {
      if (!edge.targetAsset) continue;

      const suggestion: NextStepSuggestion = {
        asset: this.mapAsset(edge.targetAsset),
        edge: this.mapEdge(edge),
        reason:
          edge.reason ??
          `${edge.edgeType.toLowerCase()} via ${edge.source.toLowerCase()}`,
      };

      const type = edge.edgeType as string;

      if (type === "PREREQUISITE" && prerequisites.length < limit) {
        prerequisites.push(suggestion);
      } else if (type === "FOLLOW_UP" && followUps.length < limit) {
        followUps.push(suggestion);
      } else if (type === "RELATED" && related.length < limit) {
        related.push(suggestion);
      } else if (type === "ALTERNATIVE" && alternatives.length < limit) {
        alternatives.push(suggestion);
      }
    }

    return { prerequisites, followUps, related, alternatives };
  }

  /**
   * Get next-step suggestions (follow_up + deeper_dive edges).
   */
  async getNextSteps(
    tenantId: string,
    assetId: string,
    limit = 5,
  ): Promise<NextStepSuggestion[]> {
    const edges = await this.prisma.recommendationEdge.findMany({
      where: {
        sourceAssetId: assetId,
        sourceAsset: { tenantId },
        edgeType: { in: ["FOLLOW_UP", "DEEPER_DIVE"] },
      },
      include: { targetAsset: true },
      orderBy: { weight: "desc" },
      take: limit,
    });

    return edges
      .filter((e) => e.targetAsset)
      .map((edge) => ({
        asset: this.mapAsset(edge.targetAsset),
        edge: this.mapEdge(edge),
        reason:
          edge.reason ??
          `Next step: ${edge.edgeType.toLowerCase().replace("_", " ")}`,
      }));
  }

  /**
   * Get AI-powered personalized recommendations for dashboard.
   * Infers recommendations from user progress and tenant context.
   */
  async getPersonalizedRecommendations(
    tenantId: string,
    userId: string,
    options: { limit?: number; excludeEnrolled?: boolean } = {},
  ): Promise<{
    modules: Array<{
      id: string;
      title: string;
      slug: string;
      description?: string;
      domain?: string;
      difficultyLevel?: string;
      estimatedTimeMinutes?: number;
      tags: string[];
      isAiRecommended: boolean;
      recommendationReason?: string;
      matchScore: number;
    }>;
    reasoning: {
      basedOn: string;
      userLevel: string;
      suggestedDomains: string[];
    };
  }> {
    const limit = options.limit ?? 6;

    // Get user's enrollments and progress
    const enrollments = await this.prisma.enrollment.findMany({
      where: { userId, tenantId },
      include: { module: true },
    });

    const enrolledModuleIds = new Set(enrollments.map((e) => e.moduleId));
    const completedModules = enrollments.filter((e) => e.status === "COMPLETED");
    const inProgressModules = enrollments.filter((e) => e.status === "IN_PROGRESS");

    // Calculate user learning profile
    const userDomains = new Map<string, number>();
    const userProgressSum = enrollments.reduce((sum, e) => sum + (e.progressPercent ?? 0), 0);
    const averageProgress = enrollments.length > 0 ? userProgressSum / enrollments.length : 0;

    for (const enrollment of enrollments) {
      if (enrollment.module?.domain) {
        const current = userDomains.get(enrollment.module.domain) ?? 0;
        userDomains.set(enrollment.module.domain, current + 1);
      }
    }

    // Determine user level based on progress and completed modules
    const userLevel =
      completedModules.length >= 5 || averageProgress > 75
        ? "advanced"
        : completedModules.length >= 2 || averageProgress > 40
          ? "intermediate"
          : "beginner";

    // Get candidate modules based on user's context
    const candidateWhere: Record<string, unknown> = {
      tenantId,
      status: "PUBLISHED",
    };

    if (options.excludeEnrolled !== false) {
      candidateWhere.id = { notIn: Array.from(enrolledModuleIds) };
    }

    // Prioritize modules from user's domains of interest
    const preferredDomains = Array.from(userDomains.entries())
      .sort((a, b) => b[1] - a[1])
      .slice(0, 3)
      .map(([domain]) => domain);

    if (preferredDomains.length > 0) {
      candidateWhere.OR = [
        { domain: { in: preferredDomains } },
        { difficultyLevel: userLevel },
      ];
    } else {
      // For new users, get popular modules from tenant
      candidateWhere.difficultyLevel = { in: ["beginner", "elementary", "intermediate"] };
    }

    const candidates = await this.prisma.contentAsset.findMany({
      where: candidateWhere,
      take: limit * 2,
      orderBy: [{ qualityScore: "desc" }, { updatedAt: "desc" }],
    });

    // Score and rank candidates using AI-inspired algorithm
    const scoredModules = candidates.map((module) => {
      let score = 0;
      const reasons: string[] = [];

      // Base quality score
      score += normalizeQualityScore(module.qualityScore) * 0.25;

      // Domain match boost
      if (preferredDomains.includes(module.domain ?? "")) {
        score += 0.3;
        reasons.push(`Popular in ${module.domain}`);
      }

      // Difficulty level fit
      const difficultyFit = this.scoreDifficultyFit(
        module.difficultyLevel,
        averageProgress / 100,
      );
      score += difficultyFit * 0.25;

      if (difficultyFit > 0.8) {
        reasons.push("Matches your skill level");
      }

      // Trending/popularity boost for new users
      if (preferredDomains.length === 0 && module.qualityScore && module.qualityScore > 70) {
        score += 0.15;
        reasons.push("Trending now");
      }

      // Generate recommendation reason
      let recommendationReason: string | undefined;
      if (reasons.length > 0) {
        recommendationReason = reasons[0];
      } else if (module.difficultyLevel === userLevel) {
        recommendationReason = `Great for ${userLevel} learners`;
      }

      return {
        module: this.mapAsset(module),
        score,
        recommendationReason,
        isAiRecommended: score > 0.6,
      };
    });

    // Sort by score and take top N
    const topModules = scoredModules
      .sort((a, b) => b.score - a.score)
      .slice(0, limit)
      .map(({ module, score, recommendationReason, isAiRecommended }) => ({
        ...module,
        slug: module.legacyModuleId ?? module.id,
        tags: module.tags ?? [],
        ...(typeof module.estimatedTimeMinutes === "number"
          ? { estimatedTimeMinutes: module.estimatedTimeMinutes }
          : {}),
        isAiRecommended,
        ...(recommendationReason ? { recommendationReason } : {}),
        matchScore: score,
      }));

    return {
      modules: topModules,
      reasoning: {
        basedOn:
          preferredDomains.length > 0
            ? "your learning history"
            : "popular modules for new learners",
        userLevel,
        suggestedDomains: preferredDomains.length > 0 ? preferredDomains : ["general"],
      },
    };
  }

  private scoreDifficultyFit(
    difficultyLevel: string | null | undefined,
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

  /**
   * Bootstrap recommendation edges for an asset using rule-based strategies.
   */
  async bootstrapEdges(
    tenantId: string,
    assetId: string,
  ): Promise<{ created: number; skipped: number }> {
    const asset = await this.prisma.contentAsset.findFirst({
      where: { id: assetId, tenantId },
    });

    if (!asset) return { created: 0, skipped: 0 };

    let created = 0;
    let skipped = 0;

    // Strategy 1: Same domain → RELATED edges
    const sameDomain = await this.prisma.contentAsset.findMany({
      where: {
        tenantId,
        domain: asset.domain,
        id: { not: assetId },
        status: "PUBLISHED",
      },
      take: 10,
      orderBy: { qualityScore: "desc" },
    });

    for (const target of sameDomain) {
      const result = await this.upsertEdge(
        assetId,
        target.id,
        "RELATED",
        "RULE_BASED",
        0.5,
        `Same domain: ${asset.domain}`,
      );
      if (result === "created") created++;
      else skipped++;
    }

    // Strategy 2: Same concept → RELATED + ALTERNATIVE edges
    if (asset.conceptId) {
      const sameConcept = await this.prisma.contentAsset.findMany({
        where: {
          tenantId,
          conceptId: asset.conceptId,
          id: { not: assetId },
          status: "PUBLISHED",
        },
        take: 5,
      });

      for (const target of sameConcept) {
        const isSameType = target.assetType === asset.assetType;
        const edgeType = isSameType ? "ALTERNATIVE" : "RELATED";
        const weight = isSameType ? 0.7 : 0.6;

        const result = await this.upsertEdge(
          assetId,
          target.id,
          edgeType,
          "RULE_BASED",
          weight,
          `Same concept: ${asset.conceptId}`,
        );
        if (result === "created") created++;
        else skipped++;
      }
    }

    // Strategy 3: Difficulty progression → PREREQUISITE / FOLLOW_UP
    if (asset.difficultyLevel) {
      const currentOrder =
        DIFFICULTY_ORDER[asset.difficultyLevel.toLowerCase()] ?? -1;

      if (currentOrder >= 0) {
        // Find easier assets in same domain → PREREQUISITE
        const easierLevels = Object.entries(DIFFICULTY_ORDER)
          .filter(([, v]) => v < currentOrder)
          .map(([k]) => k.toUpperCase());

        if (easierLevels.length > 0) {
          const prereqs = await this.prisma.contentAsset.findMany({
            where: {
              tenantId,
              domain: asset.domain,
              difficultyLevel: { in: easierLevels },
              id: { not: assetId },
              status: "PUBLISHED",
            },
            take: 3,
            orderBy: { qualityScore: "desc" },
          });

          for (const target of prereqs) {
            const result = await this.upsertEdge(
              assetId,
              target.id,
              "PREREQUISITE",
              "RULE_BASED",
              0.8,
              `Easier difficulty: ${target.difficultyLevel} → ${asset.difficultyLevel}`,
            );
            if (result === "created") created++;
            else skipped++;
          }
        }

        // Find harder assets in same domain → FOLLOW_UP
        const harderLevels = Object.entries(DIFFICULTY_ORDER)
          .filter(([, v]) => v > currentOrder)
          .map(([k]) => k.toUpperCase());

        if (harderLevels.length > 0) {
          const followUps = await this.prisma.contentAsset.findMany({
            where: {
              tenantId,
              domain: asset.domain,
              difficultyLevel: { in: harderLevels },
              id: { not: assetId },
              status: "PUBLISHED",
            },
            take: 3,
            orderBy: { qualityScore: "desc" },
          });

          for (const target of followUps) {
            const result = await this.upsertEdge(
              assetId,
              target.id,
              "FOLLOW_UP",
              "RULE_BASED",
              0.75,
              `Harder difficulty: ${asset.difficultyLevel} → ${target.difficultyLevel}`,
            );
            if (result === "created") created++;
            else skipped++;
          }
        }
      }
    }

    return { created, skipped };
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private async upsertEdge(
    sourceAssetId: string,
    targetAssetId: string,
    edgeType: Extract<RecommendationEdgeType, Uppercase<string>>,
    source: PersistedRecommendationSource,
    weight: number,
    reason: string,
  ): Promise<"created" | "skipped"> {
    const existing = await this.prisma.recommendationEdge.findFirst({
      where: { sourceAssetId, targetAssetId, edgeType },
    });

    if (existing) return "skipped";

    await this.prisma.recommendationEdge.create({
      data: {
        sourceAssetId,
        targetAssetId,
        edgeType,
        source,
        weight,
        reason,
      },
    });

    return "created";
  }

  private mapAsset(raw: RawRecommendationAsset): ContentAsset {
    const asset: ContentAsset = {
      id: raw.id as string,
      tenantId: raw.tenantId as string,
      slug: raw.slug as string,
      title: raw.title as string,
      assetType: (raw.assetType as string).toLowerCase() as ContentAssetType,
      domain: raw.domain as string,
      status: (raw.status as string).toLowerCase() as ContentAsset["status"],
      currentVersion: raw.currentVersion as number,
      targetGrades: Array.isArray(raw.targetGrades) ? raw.targetGrades : [],
      authorId: raw.authorId as string,
      createdAt: raw.createdAt
        ? (raw.createdAt as Date).toISOString()
        : new Date().toISOString(),
      updatedAt: raw.updatedAt
        ? (raw.updatedAt as Date).toISOString()
        : new Date().toISOString(),
      riskLevel: (
        (raw.riskLevel as string | null | undefined) ?? "LOW"
      ).toUpperCase() as ContentAsset["riskLevel"],
    };

    if (typeof raw.conceptId === "string" && raw.conceptId.length > 0) {
      asset.conceptId = raw.conceptId;
    }
    if (typeof raw.qualityScore === "number") {
      asset.qualityScore = raw.qualityScore;
    }
    if (typeof raw.semanticIndexStatus === "string") {
      const semanticIndexStatus = raw.semanticIndexStatus.toLowerCase();
      if (
        semanticIndexStatus === "pending" ||
        semanticIndexStatus === "indexed" ||
        semanticIndexStatus === "stale"
      ) {
        asset.semanticIndexStatus = semanticIndexStatus;
      }
    }
    if (typeof raw.recommendationStatus === "string") {
      const recommendationStatus = raw.recommendationStatus.toLowerCase();
      if (
        recommendationStatus === "pending" ||
        recommendationStatus === "computed" ||
        recommendationStatus === "stale"
      ) {
        asset.recommendationStatus = recommendationStatus;
      }
    }
    if (Array.isArray(raw.tags)) {
      asset.tags = raw.tags.filter((tag): tag is string => typeof tag === "string");
    }
    if (
      typeof raw.difficultyLevel === "string" &&
      raw.difficultyLevel.length > 0
    ) {
      asset.difficultyLevel = raw.difficultyLevel;
    }
    if (typeof raw.lastEditedBy === "string" && raw.lastEditedBy.length > 0) {
      asset.lastEditedBy = raw.lastEditedBy;
    }
    if (raw.publishedAt instanceof Date) {
      asset.publishedAt = raw.publishedAt.toISOString();
    }
    if (typeof raw.promptHash === "string" && raw.promptHash.length > 0) {
      asset.promptHash = raw.promptHash;
    }
    if (typeof raw.confidenceScore === "number") {
      asset.confidenceScore = raw.confidenceScore;
    }
    if (
      typeof raw.legacyModuleId === "string" &&
      raw.legacyModuleId.length > 0
    ) {
      asset.legacyModuleId = raw.legacyModuleId;
    }
    if (
      typeof raw.legacyExperienceId === "string" &&
      raw.legacyExperienceId.length > 0
    ) {
      asset.legacyExperienceId = raw.legacyExperienceId;
    }
    if (typeof raw.estimatedTimeMinutes === "number") {
      asset.estimatedTimeMinutes = raw.estimatedTimeMinutes;
    }

    return asset;
  }

  private mapEdge(raw: RawRecommendationEdge): RecommendationEdge {
    const edge: RecommendationEdge = {
      id: raw.id,
      sourceAssetId: raw.sourceAssetId,
      targetAssetId: raw.targetAssetId,
      edgeType: (
        raw.edgeType
      ).toLowerCase() as RecommendationEdgeType,
      source: raw.source.toLowerCase() as RecommendationSource,
      weight: raw.weight,
      createdAt: raw.createdAt
        ? raw.createdAt.toISOString()
        : new Date().toISOString(),
      updatedAt: raw.updatedAt
        ? raw.updatedAt.toISOString()
        : new Date().toISOString(),
    };
    if (typeof raw.confidence === "number") {
      edge.confidence = raw.confidence;
    }
    if (typeof raw.reason === "string") {
      edge.reason = raw.reason;
    }
    if (raw.metadata && typeof raw.metadata === "object" && !Array.isArray(raw.metadata)) {
      edge.metadata = raw.metadata as Record<string, unknown>;
    }
    return edge;
  }
}
