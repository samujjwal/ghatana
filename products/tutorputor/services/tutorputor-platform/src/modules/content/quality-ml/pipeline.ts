/**
 * Content Quality ML Pipeline
 *
 * This is a production-safe heuristic baseline, not a heavyweight
 * model-training runtime. It extracts stable features from canonical
 * assets, telemetry, and evaluations, then produces repeatable
 * quality predictions and improvement suggestions.
 *
 * @doc.type class
 * @doc.purpose Predict content quality from governed asset data
 * @doc.layer product
 * @doc.pattern ML Pipeline
 */

import type { PrismaClient } from "@tutorputor/core/db";

export interface QualityFeatures {
  contentLength: number;
  contentLengthScore: number;
  blockCount: number;
  blockCountScore: number;
  lexicalDiversity: number;
  estimatedReadability: number;
  qualityScore: number;
  confidenceScore: number;
  evaluationScore: number;
  completionRate: number;
  feedbackRatio: number;
  replayRate: number;
}

export interface QualityPrediction {
  assetId: string;
  predictedQuality: "high" | "medium" | "low";
  confidence: number;
  features: QualityFeatures;
  featureImportance: Record<keyof QualityFeatures, number>;
  suggestions: string[];
}

export interface AppliedQualityPrediction extends QualityPrediction {
  applied: true;
  recommendationStatus: "computed" | "stale";
}

export class ContentQualityMLPipeline {
  constructor(private readonly prisma: PrismaClient) {}

  async predictAssetQuality(
    tenantId: string,
    assetId: string,
  ): Promise<QualityPrediction> {
    const asset = await this.prisma.contentAsset.findFirst({
      where: { tenantId, id: assetId },
      include: {
        blocks: {
          orderBy: { orderIndex: "asc" },
        },
      },
    });
    if (!asset) {
      throw new Error(`Asset ${assetId} not found`);
    }

    const [events, latestEvaluation] = await Promise.all([
      this.prisma.explorerEvent.findMany({
        where: { tenantId, assetId },
      }),
      this.prisma.evaluationRecord.findFirst({
        where: { tenantId, assetId },
        orderBy: { createdAt: "desc" },
      }),
    ]);

    const features = this.extractFeatures(asset, events, latestEvaluation);
    const score = this.predictScore(features);
    return {
      assetId,
      predictedQuality:
        score >= 0.75 ? "high" : score >= 0.45 ? "medium" : "low",
      confidence: score,
      features,
      featureImportance: FEATURE_IMPORTANCE,
      suggestions: this.generateSuggestions(features),
    };
  }

  async backfillPredictions(
    tenantId: string,
    options: { limit?: number; assetIds?: string[] } = {},
  ): Promise<QualityPrediction[]> {
    const where: Record<string, unknown> = {
      tenantId,
      status: "PUBLISHED",
    };
    if (options.assetIds?.length) {
      where.id = { in: options.assetIds };
    }

    const assets = await this.prisma.contentAsset.findMany({
      where,
      orderBy: { updatedAt: "desc" },
      take: options.limit ?? 25,
      select: { id: true },
    });

    const results: QualityPrediction[] = [];
    for (const asset of assets) {
      results.push(await this.predictAssetQuality(tenantId, asset.id));
    }
    return results;
  }

  async applyPrediction(
    tenantId: string,
    assetId: string,
  ): Promise<AppliedQualityPrediction> {
    const prediction = await this.predictAssetQuality(tenantId, assetId);
    const recommendationStatus =
      prediction.predictedQuality === "high" ? "computed" : "stale";

    await this.prisma.contentAsset.update({
      where: { id: assetId },
      data: {
        qualityScore: prediction.confidence,
        confidenceScore: prediction.confidence,
        recommendationStatus: recommendationStatus.toUpperCase(),
        reviewState: JSON.stringify({
          source: "quality_ml_prediction",
          predictedQuality: prediction.predictedQuality,
          confidence: prediction.confidence,
          suggestions: prediction.suggestions,
          features: prediction.features,
          appliedAt: new Date().toISOString(),
        }),
      },
    });

    return {
      ...prediction,
      applied: true,
      recommendationStatus,
    };
  }

  async applyPredictionsBatch(
    tenantId: string,
    options: { limit?: number; assetIds?: string[] } = {},
  ): Promise<AppliedQualityPrediction[]> {
    const predictions = await this.backfillPredictions(tenantId, options);
    const applied: AppliedQualityPrediction[] = [];
    for (const prediction of predictions) {
      applied.push(await this.applyPrediction(tenantId, prediction.assetId));
    }
    return applied;
  }

  async applyPredictionsForExperience(
    tenantId: string,
    experienceId: string,
    options: { limit?: number } = {},
  ): Promise<AppliedQualityPrediction[]> {
    const assets = await this.prisma.contentAsset.findMany({
      where: {
        tenantId,
        legacyExperienceId: experienceId,
      },
      orderBy: { updatedAt: "desc" },
      take: options.limit ?? 25,
      select: { id: true },
    });

    const predictions: AppliedQualityPrediction[] = [];
    for (const asset of assets) {
      predictions.push(await this.applyPrediction(tenantId, asset.id));
    }
    return predictions;
  }

  extractFeatures(
    asset: {
      id: string;
      searchableText: string | null;
      qualityScore: number | null;
      confidenceScore: number | null;
      blocks: Array<{ payload: unknown }>;
    },
    events: Array<{
      eventType: string;
      feedbackLabel: string | null;
      sessionId: string | null;
    }>,
    latestEvaluation: { overallScore: number | null } | null,
  ): QualityFeatures {
    const text = [
      asset.searchableText ?? "",
      ...asset.blocks.map((block) => stringifyBlock(block.payload)),
    ]
      .join(" ")
      .trim();

    const words = text
      .toLowerCase()
      .split(/\s+/)
      .map((word) => word.replace(/[^a-z0-9]/g, ""))
      .filter(Boolean);
    const uniqueWords = new Set(words);
    const impressions = events.filter((event) => normalizeEventType(event.eventType) === "impression").length;
    const clicks = events.filter((event) => normalizeEventType(event.eventType) === "click").length;
    const completions = events.filter((event) => normalizeEventType(event.eventType) === "asset_complete").length;
    const feedbackEvents = events.filter((event) => normalizeEventType(event.eventType) === "ranking_feedback");
    const positiveFeedback = feedbackEvents.filter((event) =>
      ["positive", "helpful", "relevant"].includes(String(event.feedbackLabel ?? "").toLowerCase()),
    ).length;
    const sessions = new Map<string, number>();
    for (const event of events) {
      if (!event.sessionId) continue;
      sessions.set(event.sessionId, (sessions.get(event.sessionId) ?? 0) + 1);
    }
    const repeatSessions = Array.from(sessions.values()).filter((count) => count > 1).length;

    return {
      contentLength: text.length,
      contentLengthScore: clamp01(text.length / 2500),
      blockCount: asset.blocks.length,
      blockCountScore: clamp01(asset.blocks.length / 6),
      lexicalDiversity: words.length === 0 ? 0 : uniqueWords.size / words.length,
      estimatedReadability: estimateReadability(words),
      qualityScore: normalizeScore(asset.qualityScore),
      confidenceScore: normalizeScore(asset.confidenceScore),
      evaluationScore: normalizeScore(latestEvaluation?.overallScore),
      completionRate: clicks === 0 ? 0 : completions / clicks,
      feedbackRatio:
        feedbackEvents.length === 0 ? 0.5 : positiveFeedback / feedbackEvents.length,
      replayRate: impressions === 0 ? 0 : repeatSessions / Math.max(1, sessions.size),
    };
  }

  private predictScore(features: QualityFeatures): number {
    return clamp01(
      features.contentLengthScore * FEATURE_IMPORTANCE.contentLength +
        features.blockCountScore * FEATURE_IMPORTANCE.blockCount +
        features.lexicalDiversity * FEATURE_IMPORTANCE.lexicalDiversity +
        features.estimatedReadability * FEATURE_IMPORTANCE.estimatedReadability +
        features.qualityScore * FEATURE_IMPORTANCE.qualityScore +
        features.confidenceScore * FEATURE_IMPORTANCE.confidenceScore +
        features.evaluationScore * FEATURE_IMPORTANCE.evaluationScore +
        features.completionRate * FEATURE_IMPORTANCE.completionRate +
        features.feedbackRatio * FEATURE_IMPORTANCE.feedbackRatio +
        features.replayRate * FEATURE_IMPORTANCE.replayRate,
    );
  }

  private generateSuggestions(features: QualityFeatures): string[] {
    const suggestions: string[] = [];
    if (features.contentLength < 400) {
      suggestions.push("Expand the asset with more explanatory detail or worked context.");
    }
    if (features.blockCount < 2) {
      suggestions.push("Split the asset into more structured blocks to improve scanability.");
    }
    if (features.lexicalDiversity < 0.35) {
      suggestions.push("Reduce repetition and introduce more concept-specific vocabulary.");
    }
    if (features.estimatedReadability < 0.45) {
      suggestions.push("Simplify sentence structure and reduce dense technical phrasing.");
    }
    if (features.completionRate < 0.45) {
      suggestions.push("Review pacing and front-load clearer learner value to improve completion.");
    }
    if (features.feedbackRatio < 0.55) {
      suggestions.push("Audit learner feedback themes and revise examples or explanations.");
    }
    if (features.evaluationScore < 0.65) {
      suggestions.push("Address evaluation issues before republishing this asset.");
    }
    return suggestions;
  }
}

const FEATURE_IMPORTANCE: Record<keyof QualityFeatures, number> = {
  contentLength: 0.08,
  contentLengthScore: 0.08,
  blockCount: 0.06,
  blockCountScore: 0.06,
  lexicalDiversity: 0.1,
  estimatedReadability: 0.1,
  qualityScore: 0.14,
  confidenceScore: 0.08,
  evaluationScore: 0.18,
  completionRate: 0.12,
  feedbackRatio: 0.08,
  replayRate: 0.06,
};

function stringifyBlock(payload: unknown): string {
  if (!payload) return "";
  if (typeof payload === "string") return payload;
  if (typeof payload === "object") return JSON.stringify(payload);
  return String(payload);
}

function estimateReadability(words: string[]): number {
  if (words.length === 0) return 0.5;
  const averageWordLength =
    words.reduce((sum, word) => sum + word.length, 0) / words.length;
  return clamp01(1 - (averageWordLength - 4.5) / 8);
}

function normalizeEventType(value: string): string {
  return value.toLowerCase();
}

function normalizeScore(value: number | null | undefined): number {
  if (value == null) return 0.5;
  return value > 1 ? clamp01(value / 100) : clamp01(value);
}

function clamp01(value: number): number {
  return Math.max(0, Math.min(1, value));
}
