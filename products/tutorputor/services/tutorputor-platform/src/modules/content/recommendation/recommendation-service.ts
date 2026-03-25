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
} from "@tutorputor/contracts/v1/content-studio";

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

function computePathwayAffinity(source: any, target: any): number {
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

function inferEdgeType(source: any, target: any): string {
  const sourceRank = getDifficultyRank(source.difficultyLevel);
  const targetRank = getDifficultyRank(target.difficultyLevel);

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

function inferBaseWeight(edgeType: string): number {
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

function summarizeTelemetry(events: any[]): {
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

    const sourceAssets = await (this.prisma as any).contentAsset.findMany({
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

      const candidates = await (this.prisma as any).contentAsset.findMany({
        where: candidateWhere,
        take: 12,
        orderBy: { qualityScore: "desc" },
      });

      const candidateIds = candidates.map((candidate: any) => candidate.id);
      const [events, evaluations, existingEdges] = await Promise.all([
        candidateIds.length === 0
          ? []
          : (this.prisma as any).explorerEvent.findMany({
              where: { tenantId, assetId: { in: candidateIds } },
            }),
        candidateIds.length === 0
          ? []
          : (this.prisma as any).evaluationRecord.findMany({
              where: { tenantId, assetId: { in: candidateIds } },
              orderBy: { createdAt: "desc" },
            }),
        (this.prisma as any).recommendationEdge.findMany({
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

      const evaluationByAsset = new Map<string, any>();
      for (const evaluation of evaluations) {
        if (!evaluation.assetId || evaluationByAsset.has(evaluation.assetId)) {
          continue;
        }
        evaluationByAsset.set(evaluation.assetId, evaluation);
      }

      for (const candidate of candidates) {
        const edgeType = inferEdgeType(sourceAsset, candidate);
        const existingEdge = existingEdges.find(
          (edge: any) =>
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
        const quality = normalizeQualityScore(
          evaluationByAsset.get(candidate.id)?.overallScore ??
            candidate.qualityScore,
        );
        const pathwayAffinity = computePathwayAffinity(sourceAsset, candidate);

        const data = {
          sourceAssetId: sourceAsset.id,
          targetAssetId: candidate.id,
          edgeType,
          source: "OUTCOME_AWARE",
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
          await (this.prisma as any).recommendationEdge.update({
            where: { id: existingEdge.id },
            data,
          });
        } else {
          await (this.prisma as any).recommendationEdge.create({ data });
        }

        updatedEdges++;
      }

      await (this.prisma as any).contentAsset.update({
        where: { id: sourceAsset.id },
        data: { recommendationStatus: "COMPUTED" },
      });
      processedAssets++;
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
    const edges = await (this.prisma as any).recommendationEdge.findMany({
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
    const edges = await (this.prisma as any).recommendationEdge.findMany({
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
      .filter((e: any) => e.targetAsset)
      .map((edge: any) => ({
        asset: this.mapAsset(edge.targetAsset),
        edge: this.mapEdge(edge),
        reason:
          edge.reason ??
          `Next step: ${edge.edgeType.toLowerCase().replace("_", " ")}`,
      }));
  }

  /**
   * Bootstrap recommendation edges for an asset using rule-based strategies.
   */
  async bootstrapEdges(
    tenantId: string,
    assetId: string,
  ): Promise<{ created: number; skipped: number }> {
    const asset = await (this.prisma as any).contentAsset.findFirst({
      where: { id: assetId, tenantId },
    });

    if (!asset) return { created: 0, skipped: 0 };

    let created = 0;
    let skipped = 0;

    // Strategy 1: Same domain → RELATED edges
    const sameDomain = await (this.prisma as any).contentAsset.findMany({
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
      const sameConcept = await (this.prisma as any).contentAsset.findMany({
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
          const prereqs = await (this.prisma as any).contentAsset.findMany({
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
          const followUps = await (this.prisma as any).contentAsset.findMany({
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
    edgeType: string,
    source: string,
    weight: number,
    reason: string,
  ): Promise<"created" | "skipped"> {
    const existing = await (this.prisma as any).recommendationEdge.findFirst({
      where: { sourceAssetId, targetAssetId, edgeType },
    });

    if (existing) return "skipped";

    await (this.prisma as any).recommendationEdge.create({
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

  private mapAsset(raw: Record<string, unknown>): ContentAsset {
    return {
      id: raw.id as string,
      tenantId: raw.tenantId as string,
      slug: raw.slug as string,
      title: raw.title as string,
      assetType: (raw.assetType as string).toLowerCase() as ContentAssetType,
      domain: raw.domain as string,
      conceptId: (raw.conceptId as string) ?? undefined,
      status: (raw.status as string).toLowerCase() as ContentAsset["status"],
      currentVersion: raw.currentVersion as number,
      qualityScore: (raw.qualityScore as number) ?? undefined,
      semanticIndexStatus:
        ((
          raw.semanticIndexStatus as string | null | undefined
        )?.toLowerCase() as ContentAsset["semanticIndexStatus"]) ?? undefined,
      recommendationStatus:
        ((
          raw.recommendationStatus as string | null | undefined
        )?.toLowerCase() as ContentAsset["recommendationStatus"]) ?? undefined,
      tags: (raw.tags as string[]) ?? undefined,
      targetGrades: Array.isArray(raw.targetGrades)
        ? (raw.targetGrades as string[])
        : [],
      difficultyLevel: (raw.difficultyLevel as string) ?? undefined,
      authorId: raw.authorId as string,
      lastEditedBy: (raw.lastEditedBy as string) ?? undefined,
      publishedAt: raw.publishedAt
        ? (raw.publishedAt as Date).toISOString()
        : undefined,
      createdAt: raw.createdAt
        ? (raw.createdAt as Date).toISOString()
        : new Date().toISOString(),
      updatedAt: raw.updatedAt
        ? (raw.updatedAt as Date).toISOString()
        : new Date().toISOString(),
      promptHash: (raw.promptHash as string) ?? undefined,
      riskLevel: (
        (raw.riskLevel as string | null | undefined) ?? "LOW"
      ).toUpperCase() as ContentAsset["riskLevel"],
      confidenceScore: (raw.confidenceScore as number) ?? undefined,
      legacyModuleId: (raw.legacyModuleId as string) ?? undefined,
      legacyExperienceId: (raw.legacyExperienceId as string) ?? undefined,
    };
  }

  private mapEdge(raw: Record<string, unknown>): RecommendationEdge {
    return {
      id: raw.id as string,
      sourceAssetId: raw.sourceAssetId as string,
      targetAssetId: raw.targetAssetId as string,
      edgeType: (
        raw.edgeType as string
      ).toLowerCase() as RecommendationEdgeType,
      source: (raw.source as string).toLowerCase() as RecommendationSource,
      weight: raw.weight as number,
      confidence: (raw.confidence as number) ?? undefined,
      reason: (raw.reason as string) ?? undefined,
      metadata: (raw.metadata as Record<string, unknown>) ?? undefined,
      createdAt: raw.createdAt
        ? (raw.createdAt as Date).toISOString()
        : new Date().toISOString(),
      updatedAt: raw.updatedAt
        ? (raw.updatedAt as Date).toISOString()
        : new Date().toISOString(),
    };
  }
}
