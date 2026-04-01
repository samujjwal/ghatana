/**
 * Asset Outcome Service
 *
 * Aggregates learner telemetry, evaluation signals, and review history into a
 * governed asset-health summary. Optionally persists the derived confidence and
 * candidate state back onto the canonical asset.
 *
 * @doc.type class
 * @doc.purpose Analyze content-asset health from learner and reviewer outcomes
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import { RegenerationCandidateService } from "../candidates/candidate-service.js";
import { RecommendationService } from "./recommendation-service.js";

type PublishRecommendation = "auto_publish" | "manual_review" | "block";
type GenerationReviewDecisionStatus = string;

interface AssetOutcomeSummary {
  assetId: string;
  assetStatus: string;
  evaluationScore?: number;
  evaluationRecommendation?: PublishRecommendation;
  latestReviewStatus?: GenerationReviewDecisionStatus;
  telemetry: {
    impressions: number;
    clicks: number;
    completions: number;
    nextStepSelections: number;
    positiveFeedback: number;
    negativeFeedback: number;
    ctr: number;
    completionRate: number;
    feedbackRatio: number;
  };
  engagementScore: number;
  confidenceScore: number;
  healthStatus: "healthy" | "watch" | "intervene";
  openCandidateCount: number;
  recommendedActions: string[];
  experimentSummary?: {
    observationCount: number;
    controlMean?: number;
    treatmentMean?: number;
    relativeLift?: number;
    dominantVariant: "control" | "treatment" | "balanced";
  };
  recommendationRefresh?: unknown;
}

const POSITIVE_FEEDBACK = new Set(["positive", "helpful", "relevant"]);
const NEGATIVE_FEEDBACK = new Set(["negative", "not_relevant", "unhelpful"]);

export class AssetOutcomeService {
  private readonly candidateService: RegenerationCandidateService;
  private readonly recommendationService: RecommendationService;

  constructor(private readonly prisma: PrismaClient) {
    this.candidateService = new RegenerationCandidateService(prisma);
    this.recommendationService = new RecommendationService(prisma);
  }

  async analyzeAsset(
    tenantId: string,
    assetId: string,
    options?: {
      apply?: boolean;
      recomputeRecommendations?: boolean;
    },
  ): Promise<AssetOutcomeSummary> {
    const asset = await this.prisma.contentAsset.findFirst({
      where: { id: assetId, tenantId },
    });

    if (!asset) {
      throw new Error(`Content asset ${assetId} not found`);
    }

    const [events, latestEvaluation, openCandidates] = await Promise.all([
      this.prisma.explorerEvent.findMany({
        where: { tenantId, assetId },
        orderBy: { occurredAt: "desc" },
        take: 500,
      }),
      this.prisma.evaluationRecord.findFirst({
        where: { tenantId, assetId },
        orderBy: { createdAt: "desc" },
      }),
      this.candidateService.listOpenCandidates(tenantId, { assetId }),
    ]);
    const experimentObservations = await this.prisma.aBExperimentObservation.findMany({
      where: { tenantId, assetId },
      orderBy: { observedAt: "desc" },
      take: 500,
    });

    const latestReview = latestEvaluation?.generationRequestId
      ? await this.prisma.generationReviewDecision.findFirst({
          where: {
            tenantId,
            requestId: latestEvaluation.generationRequestId,
          },
          orderBy: { createdAt: "desc" },
        })
      : null;

    const telemetry = summarizeTelemetry(events);
    const experimentSummary = summarizeExperimentObservations(experimentObservations);
    const evaluationScore =
      typeof latestEvaluation?.overallScore === "number"
        ? latestEvaluation.overallScore
        : typeof asset.qualityScore === "number"
          ? normalizeScore(asset.qualityScore)
          : undefined;
    const evaluationRecommendation = latestEvaluation
      ? (String(latestEvaluation.recommendation).toLowerCase() as PublishRecommendation)
      : undefined;
    const latestReviewStatus = latestReview
      ? (String(latestReview.status).toLowerCase() as GenerationReviewDecisionStatus)
      : undefined;

    const engagementScore = clamp01(
      telemetry.ctr * 0.35 +
        telemetry.completionRate * 0.35 +
        telemetry.feedbackRatio * 0.2 +
        completionSignal(telemetry) * 0.1,
    );
    const confidenceScore = clamp01(
      (evaluationScore ?? normalizeScore(asset.qualityScore)) * 0.55 +
        engagementScore * 0.3 +
        reviewFactor(latestReviewStatus) * 0.1 +
        experimentConfidence(experimentSummary) * 0.05,
    );
    const healthStatus = deriveHealthStatus({
      engagementScore,
      telemetry,
      openCandidateCount: openCandidates.length,
      ...(experimentSummary ? { experimentSummary } : {}),
      ...(evaluationScore !== undefined ? { evaluationScore } : {}),
      ...(evaluationRecommendation ? { evaluationRecommendation } : {}),
      ...(latestReviewStatus ? { latestReviewStatus } : {}),
    });
    const recommendedActions = deriveRecommendedActions({
      assetStatus: String(asset.status).toLowerCase(),
      telemetry,
      healthStatus,
      ...(experimentSummary ? { experimentSummary } : {}),
      recommendationStatus: String(asset.recommendationStatus ?? "").toLowerCase(),
      ...(evaluationRecommendation ? { evaluationRecommendation } : {}),
      ...(latestReviewStatus ? { latestReviewStatus } : {}),
    });

    let recommendationRefresh:
      | AssetOutcomeSummary["recommendationRefresh"]
      | undefined;

    if (options?.apply) {
      await this.persistOutcomeSignals(
        tenantId,
        assetId,
        confidenceScore,
        healthStatus,
        telemetry,
        experimentSummary,
        evaluationScore,
        evaluationRecommendation,
        latestReviewStatus,
      );

      await this.ensureRegenerationCandidateIfNeeded(
        tenantId,
        asset,
        openCandidates.length,
        healthStatus,
        telemetry,
        experimentSummary,
        evaluationRecommendation,
      );
    }

    if (options?.recomputeRecommendations && String(asset.status) === "PUBLISHED") {
      recommendationRefresh =
        await this.recommendationService.recomputeOutcomeAwareEdges(tenantId, {
          sourceAssetId: assetId,
          limit: 1,
        });
    }

    return {
      assetId,
      assetStatus: String(asset.status).toLowerCase() as AssetOutcomeSummary["assetStatus"],
      ...(evaluationScore !== undefined ? { evaluationScore } : {}),
      ...(evaluationRecommendation ? { evaluationRecommendation } : {}),
      ...(latestReviewStatus ? { latestReviewStatus } : {}),
      telemetry,
      engagementScore,
      confidenceScore,
      healthStatus,
      openCandidateCount: openCandidates.length,
      recommendedActions,
      ...(experimentSummary ? { experimentSummary } : {}),
      ...(recommendationRefresh ? { recommendationRefresh } : {}),
    };
  }

  private async persistOutcomeSignals(
    tenantId: string,
    assetId: string,
    confidenceScore: number,
    healthStatus: AssetOutcomeSummary["healthStatus"],
    telemetry: AssetOutcomeSummary["telemetry"],
    experimentSummary?: NonNullable<AssetOutcomeSummary["experimentSummary"]>,
    evaluationScore?: number,
    evaluationRecommendation?: PublishRecommendation,
    latestReviewStatus?: GenerationReviewDecisionStatus,
  ): Promise<void> {
    await this.prisma.contentAsset.update({
      where: { id: assetId },
      data: {
        confidenceScore,
        recommendationStatus:
          telemetry.negativeFeedback > 0 || telemetry.nextStepSelections > 0
            ? "STALE"
            : "COMPUTED",
        reviewState: JSON.stringify({
          source: "asset_outcome_analysis",
          tenantId,
          healthStatus,
          confidenceScore,
          telemetry,
          ...(experimentSummary ? { experimentSummary } : {}),
          ...(evaluationScore !== undefined ? { evaluationScore } : {}),
          ...(evaluationRecommendation ? { evaluationRecommendation } : {}),
          ...(latestReviewStatus ? { latestReviewStatus } : {}),
          analyzedAt: new Date().toISOString(),
        }),
      },
    });
  }

  private async ensureRegenerationCandidateIfNeeded(
    tenantId: string,
    asset: Record<string, unknown>,
    openCandidateCount: number,
    healthStatus: AssetOutcomeSummary["healthStatus"],
    telemetry: AssetOutcomeSummary["telemetry"],
    experimentSummary?: NonNullable<AssetOutcomeSummary["experimentSummary"]>,
    evaluationRecommendation?: PublishRecommendation,
  ): Promise<void> {
    if (openCandidateCount > 0 || healthStatus !== "intervene") {
      return;
    }

    const trigger =
      evaluationRecommendation === "block"
        ? "low_evaluation_score"
        : "poor_discovery_performance";
    const assetType = String(asset.assetType ?? "").toLowerCase();

    await this.candidateService.createCandidate(tenantId, {
      assetId: asset.id as string,
      ...(assetType ? { assetType } : {}),
      trigger,
      severity: evaluationRecommendation === "block" ? "high" : "medium",
      reason:
        evaluationRecommendation === "block"
          ? "Asset outcome analysis confirmed blocked evaluation quality"
          : "Asset outcome analysis detected poor learner engagement or negative feedback",
      evidence: {
        telemetry,
        ...(experimentSummary ? { experimentSummary } : {}),
        evaluationRecommendation: evaluationRecommendation ?? null,
      },
      priority: evaluationRecommendation === "block" ? 85 : 70,
    });
  }

  async analyzeExperienceAssets(
    tenantId: string,
    experienceId: string,
    options?: {
      apply?: boolean;
      recomputeRecommendations?: boolean;
    },
  ) {
    const assets = await this.prisma.contentAsset.findMany({
      where: {
        tenantId,
        legacyExperienceId: experienceId,
      },
      orderBy: { updatedAt: "desc" },
      select: { id: true },
    });

    const results = [];
    for (const asset of assets) {
      results.push(
        await this.analyzeAsset(tenantId, asset.id, options),
      );
    }

    return {
      experienceId,
      totalAssets: results.length,
      healthyAssets: results.filter((result) => result.healthStatus === "healthy")
        .length,
      watchAssets: results.filter((result) => result.healthStatus === "watch")
        .length,
      interveneAssets: results.filter((result) => result.healthStatus === "intervene")
        .length,
      assets: results,
    };
  }
}

function summarizeTelemetry(events: unknown[]): AssetOutcomeSummary["telemetry"] {
  const summary = {
    impressions: 0,
    clicks: 0,
    completions: 0,
    nextStepSelections: 0,
    positiveFeedback: 0,
    negativeFeedback: 0,
    ctr: 0,
    completionRate: 0,
    feedbackRatio: 0.5,
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

  summary.ctr =
    summary.impressions > 0 ? clamp01(summary.clicks / summary.impressions) : 0;
  summary.completionRate =
    summary.clicks > 0 ? clamp01(summary.completions / summary.clicks) : 0;
  summary.feedbackRatio = clamp01(
    (summary.positiveFeedback + 1) /
      (summary.positiveFeedback + summary.negativeFeedback + 2),
  );

  return summary;
}

function normalizeScore(value: number | null | undefined): number {
  if (value == null) return 0.5;
  return value > 1 ? clamp01(value / 100) : clamp01(value);
}

function summarizeExperimentObservations(
  observations: Array<{
    variant: string;
    metricValue: number;
  }>,
): AssetOutcomeSummary["experimentSummary"] | undefined {
  if (observations.length === 0) {
    return undefined;
  }
  const control = observations.filter((observation) => observation.variant === "control");
  const treatment = observations.filter((observation) => observation.variant === "treatment");
  const controlMean =
    control.length === 0
      ? undefined
      : control.reduce((sum, observation) => sum + observation.metricValue, 0) /
        control.length;
  const treatmentMean =
    treatment.length === 0
      ? undefined
      : treatment.reduce((sum, observation) => sum + observation.metricValue, 0) /
        treatment.length;
  const relativeLift =
    controlMean !== undefined && treatmentMean !== undefined
      ? (treatmentMean - controlMean) / Math.abs(controlMean || 1)
      : undefined;
  const dominantVariant =
    control.length === treatment.length
      ? "balanced"
      : control.length > treatment.length
        ? "control"
        : "treatment";

  return {
    observationCount: observations.length,
    ...(controlMean !== undefined ? { controlMean } : {}),
    ...(treatmentMean !== undefined ? { treatmentMean } : {}),
    ...(relativeLift !== undefined ? { relativeLift } : {}),
    dominantVariant,
  };
}

function experimentConfidence(
  summary?: NonNullable<AssetOutcomeSummary["experimentSummary"]>,
): number {
  if (!summary || summary.observationCount === 0) {
    return 0.5;
  }
  if (summary.relativeLift == null) {
    return 0.55;
  }
  return clamp01(0.5 + summary.relativeLift * 0.5);
}

function clamp01(value: number): number {
  return Math.max(0, Math.min(1, value));
}

function completionSignal(telemetry: AssetOutcomeSummary["telemetry"]): number {
  return clamp01((telemetry.completions + telemetry.nextStepSelections) / 5);
}

function reviewFactor(
  status?: GenerationReviewDecisionStatus,
): number {
  switch (status) {
    case "approved":
      return 1;
    case "pending":
      return 0.5;
    case "rejected":
    case "regeneration_requested":
      return 0.1;
    default:
      return 0.6;
  }
}

function deriveHealthStatus(input: {
  evaluationScore?: number;
  evaluationRecommendation?: PublishRecommendation;
  engagementScore: number;
  telemetry: AssetOutcomeSummary["telemetry"];
  openCandidateCount: number;
  experimentSummary?: NonNullable<AssetOutcomeSummary["experimentSummary"]>;
  latestReviewStatus?: GenerationReviewDecisionStatus;
}): AssetOutcomeSummary["healthStatus"] {
  if (
    input.evaluationRecommendation === "block" ||
    input.latestReviewStatus === "rejected" ||
    input.latestReviewStatus === "regeneration_requested" ||
    input.openCandidateCount > 0 ||
    input.telemetry.negativeFeedback >= input.telemetry.positiveFeedback + 2 ||
    (input.experimentSummary?.relativeLift ?? 0) < -0.1
  ) {
    return "intervene";
  }

  if (
    (input.evaluationScore ?? 0.7) < 0.7 ||
    input.engagementScore < 0.45 ||
    (input.experimentSummary?.relativeLift ?? 0) < 0 ||
    input.latestReviewStatus === "pending"
  ) {
    return "watch";
  }

  return "healthy";
}

function deriveRecommendedActions(input: {
  assetStatus: string;
  evaluationRecommendation?: PublishRecommendation;
  latestReviewStatus?: GenerationReviewDecisionStatus;
  telemetry: AssetOutcomeSummary["telemetry"];
  healthStatus: AssetOutcomeSummary["healthStatus"];
  experimentSummary?: NonNullable<AssetOutcomeSummary["experimentSummary"]>;
  recommendationStatus: string;
}): string[] {
  const actions: string[] = [];

  if (input.healthStatus === "intervene") {
    actions.push("open_or_queue_regeneration");
  }
  if (
    input.evaluationRecommendation === "manual_review" ||
    input.latestReviewStatus === "pending"
  ) {
    actions.push("manual_review_required");
  }
  if (
    input.recommendationStatus === "stale" ||
    (input.experimentSummary?.relativeLift ?? 0) < 0 ||
    input.telemetry.negativeFeedback > 0 ||
    input.telemetry.nextStepSelections > 0
  ) {
    actions.push("recompute_recommendations");
  }
  if ((input.experimentSummary?.relativeLift ?? 0) > 0.05) {
    actions.push("promote_successful_variant_signals");
  }
  if (
    input.assetStatus === "published" &&
    input.healthStatus === "healthy" &&
    input.evaluationRecommendation === "auto_publish"
  ) {
    actions.push("keep_published");
  }

  return actions.length > 0 ? actions : ["monitor"];
}
