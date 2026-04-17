/**
 * AI Quality Benchmark Service
 *
 * Human evaluation pipeline for AI-generated content quality.
 * Collects ratings, tracks evaluator agreement, and generates quality reports.
 *
 * @doc.type class
 * @doc.purpose Human evaluation pipeline for AI content quality
 * @doc.layer product
 * @doc.pattern Service
 */

/**
 * Evaluation rubric dimensions
 */
export type EvaluationDimension =
  | "accuracy"
  | "clarity"
  | "completeness"
  | "pedagogical_value"
  | "safety"
  | "hallucination_risk";

/**
 * Rating scale (1-5)
 */
export type Rating = 1 | 2 | 3 | 4 | 5;

/**
 * Content item for evaluation
 */
export interface EvalContent {
  id: string;
  contentType: "tutor_response" | "simulation_explanation" | "learning_unit" | "content_query";
  input: string;
  output: string;
  model: string;
  promptVersion: string;
  metadata: {
    tenantId?: string;
    moduleId?: string;
    timestamp: Date;
  };
}

/**
 * Evaluator rating
 */
export interface EvaluatorRating {
  evaluatorId: string;
  contentId: string;
  dimension: EvaluationDimension;
  rating: Rating;
  confidence: number; // 0-1
  comments?: string;
  flags: ("hallucination" | "unsafe" | "unclear" | "incomplete")[];
  timestamp: Date;
  timeSpentSeconds: number;
}

/**
 * Aggregated quality metrics for a content item
 */
export interface ContentQualityMetrics {
  contentId: string;
  model: string;
  evaluatorCount: number;
  ratingsByDimension: Record<
    EvaluationDimension,
    {
      average: number;
      median: number;
      stdDev: number;
      min: Rating;
      max: Rating;
      count: number;
    }
  >;
  overallScore: number; // 0-100
  interRaterReliability: number; // 0-1, higher is better agreement
  flags: {
    hallucinationCount: number;
    unsafeCount: number;
    unclearCount: number;
    incompleteCount: number;
  };
  consensus: "strong_agreement" | "moderate_agreement" | "disagreement";
  flaggedForReview: boolean;
}

/**
 * Benchmark results for a model/prompt version
 */
export interface BenchmarkReport {
  id: string;
  model: string;
  promptVersion: string;
  startDate: Date;
  endDate: Date;
  totalSamples: number;
  totalEvaluators: number;
  overallQualityScore: number;
  dimensionScores: Record<EvaluationDimension, number>;
  ratingDistribution: Record<Rating, number>;
  flaggedSamples: string[];
  comparisonToBaseline?: {
    improvementPercent: number;
    statisticallySignificant: boolean;
  };
  recommendations: string[];
}

/**
 * Evaluation session
 */
export interface EvaluationSession {
  id: string;
  evaluatorId: string;
  assignedContentIds: string[];
  completedIds: string[];
  startTime: Date;
  endTime?: Date;
  currentIndex: number;
}

/**
 * Quality threshold configuration
 */
export interface QualityThresholds {
  minimumOverallScore: number; // 0-100
  maximumHallucinationRate: number; // 0-1
  minimumInterRaterReliability: number; // 0-1
  minimumEvaluatorsPerItem: number;
  requiredDimensions: EvaluationDimension[];
}

/**
 * AI Quality Benchmark Service
 */
export class AIQualityBenchmarkService {
  private contentItems = new Map<string, EvalContent>();
  private ratings = new Map<string, EvaluatorRating[]>();
  private sessions = new Map<string, EvaluationSession>();
  private qualityCache = new Map<string, ContentQualityMetrics>();
  private thresholds: QualityThresholds;

  constructor(thresholds?: Partial<QualityThresholds>) {
    this.thresholds = {
      minimumOverallScore: 70,
      maximumHallucinationRate: 0.05,
      minimumInterRaterReliability: 0.7,
      minimumEvaluatorsPerItem: 3,
      requiredDimensions: ["accuracy", "clarity", "safety"],
      ...thresholds,
    };
  }

  /**
   * Submit content for evaluation
   */
  submitContent(content: EvalContent): void {
    this.contentItems.set(content.id, content);
    this.ratings.set(content.id, []);
    this.qualityCache.delete(content.id);
  }

  /**
   * Create evaluation session for an evaluator
   */
  createSession(
    evaluatorId: string,
    contentIds: string[],
  ): EvaluationSession {
    const session: EvaluationSession = {
      id: `session-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      evaluatorId,
      assignedContentIds: contentIds,
      completedIds: [],
      startTime: new Date(),
      currentIndex: 0,
    };

    this.sessions.set(session.id, session);
    return session;
  }

  /**
   * Get next item for evaluation in session
   */
  getNextForSession(sessionId: string): EvalContent | null {
    const session = this.sessions.get(sessionId);
    if (!session) return null;

    const remaining = session.assignedContentIds.filter(
      (id) => !session.completedIds.includes(id),
    );

    if (remaining.length === 0) {
      session.endTime = new Date();
      return null;
    }

    const content = this.contentItems.get(remaining[0]);
    if (content) {
      session.currentIndex = session.assignedContentIds.indexOf(remaining[0]);
    }

    return content ?? null;
  }

  /**
   * Submit rating
   */
  submitRating(
    sessionId: string,
    rating: Omit<EvaluatorRating, "evaluatorId" | "timestamp">,
  ): { success: boolean; error?: string } {
    const session = this.sessions.get(sessionId);
    if (!session) {
      return { success: false, error: "Session not found" };
    }

    const content = this.contentItems.get(rating.contentId);
    if (!content) {
      return { success: false, error: "Content not found" };
    }

    const fullRating: EvaluatorRating = {
      ...rating,
      evaluatorId: session.evaluatorId,
      timestamp: new Date(),
    };

    // Add to ratings
    const contentRatings = this.ratings.get(rating.contentId) ?? [];
    contentRatings.push(fullRating);
    this.ratings.set(rating.contentId, contentRatings);

    // Mark as completed in session
    if (!session.completedIds.includes(rating.contentId)) {
      session.completedIds.push(rating.contentId);
    }

    // Invalidate cache
    this.qualityCache.delete(rating.contentId);

    return { success: true };
  }

  /**
   * Calculate quality metrics for content
   */
  calculateQuality(contentId: string): ContentQualityMetrics | null {
    // Check cache
    const cached = this.qualityCache.get(contentId);
    if (cached) return cached;

    const content = this.contentItems.get(contentId);
    if (!content) return null;

    const ratings = this.ratings.get(contentId) ?? [];
    if (ratings.length === 0) return null;

    const dimensions: EvaluationDimension[] = [
      "accuracy",
      "clarity",
      "completeness",
      "pedagogical_value",
      "safety",
      "hallucination_risk",
    ];

    const ratingsByDimension: ContentQualityMetrics["ratingsByDimension"] =
      {} as ContentQualityMetrics["ratingsByDimension"];

    for (const dim of dimensions) {
      const dimRatings = ratings
        .filter((r) => r.dimension === dim)
        .map((r) => r.rating);

      if (dimRatings.length > 0) {
        const sorted = [...dimRatings].sort((a, b) => a - b);
        const sum = sorted.reduce((a, b) => a + b, 0);
        const mean = sum / sorted.length;
        const variance =
          sorted.reduce((sum, val) => sum + Math.pow(val - mean, 2), 0) /
          sorted.length;

        ratingsByDimension[dim] = {
          average: parseFloat(mean.toFixed(2)),
          median: sorted[Math.floor(sorted.length / 2)] ?? sorted[0],
          stdDev: parseFloat(Math.sqrt(variance).toFixed(2)),
          min: sorted[0] as Rating,
          max: sorted[sorted.length - 1] as Rating,
          count: sorted.length,
        };
      }
    }

    // Calculate overall score (weighted average, normalized to 0-100)
    const weights: Record<EvaluationDimension, number> = {
      accuracy: 0.25,
      clarity: 0.2,
      completeness: 0.15,
      pedagogical_value: 0.15,
      safety: 0.2,
      hallucination_risk: 0.05,
    };

    let weightedSum = 0;
    let totalWeight = 0;

    for (const dim of dimensions) {
      const metrics = ratingsByDimension[dim];
      if (metrics) {
        weightedSum += metrics.average * weights[dim];
        totalWeight += weights[dim];
      }
    }

    const overallScore =
      totalWeight > 0 ? Math.round((weightedSum / totalWeight) * 20) : 0;

    // Calculate inter-rater reliability (simplified Krippendorff's alpha approximation)
    const irr = this.calculateInterRaterReliability(ratings);

    // Count flags
    const flags = ratings.reduce(
      (acc, r) => {
        r.flags.forEach((flag) => {
          acc[`${flag}Count` as keyof typeof acc]++;
        });
        return acc;
      },
      { hallucinationCount: 0, unsafeCount: 0, unclearCount: 0, incompleteCount: 0 },
    );

    // Determine consensus level
    const consensus: ContentQualityMetrics["consensus"] =
      irr > 0.8 ? "strong_agreement" : irr > 0.6 ? "moderate_agreement" : "disagreement";

    // Flag for review if metrics are concerning
    const flaggedForReview =
      overallScore < this.thresholds.minimumOverallScore ||
      flags.hallucinationCount / ratings.length >
        this.thresholds.maximumHallucinationRate ||
      irr < this.thresholds.minimumInterRaterReliability ||
      ratings.length < this.thresholds.minimumEvaluatorsPerItem;

    const metrics: ContentQualityMetrics = {
      contentId,
      model: content.model,
      evaluatorCount: new Set(ratings.map((r) => r.evaluatorId)).size,
      ratingsByDimension,
      overallScore,
      interRaterReliability: irr,
      flags,
      consensus,
      flaggedForReview,
    };

    this.qualityCache.set(contentId, metrics);
    return metrics;
  }

  /**
   * Calculate inter-rater reliability (simplified)
   */
  private calculateInterRaterReliability(ratings: EvaluatorRating[]): number {
    const evaluators = [...new Set(ratings.map((r) => r.evaluatorId))];
    if (evaluators.length < 2) return 1; // Single evaluator = perfect reliability by default

    // Group ratings by dimension
    const byDimension = new Map<EvaluationDimension, Map<string, Rating[]>>();

    for (const rating of ratings) {
      if (!byDimension.has(rating.dimension)) {
        byDimension.set(rating.dimension, new Map());
      }
      const dimMap = byDimension.get(rating.dimension)!;
      if (!dimMap.has(rating.evaluatorId)) {
        dimMap.set(rating.evaluatorId, []);
      }
      dimMap.get(rating.evaluatorId)!.push(rating.rating);
    }

    // Calculate average agreement across dimensions
    let totalAgreement = 0;
    let dimensionCount = 0;

    for (const [, evaluatorRatings] of byDimension) {
      if (evaluatorRatings.size < 2) continue;

      // Calculate average pairwise agreement
      const evaluators_list = [...evaluatorRatings.entries()];
      let pairwiseAgreements = 0;
      let pairs = 0;

      for (let i = 0; i < evaluators_list.length; i++) {
        for (let j = i + 1; j < evaluators_list.length; j++) {
          const ratings1 = evaluators_list[i][1];
          const ratings2 = evaluators_list[j][1];
          const minLen = Math.min(ratings1.length, ratings2.length);

          if (minLen > 0) {
            let agreement = 0;
            for (let k = 0; k < minLen; k++) {
              const diff = Math.abs(ratings1[k] - ratings2[k]);
              agreement += 1 - diff / 4; // Normalize to 0-1
            }
            pairwiseAgreements += agreement / minLen;
            pairs++;
          }
        }
      }

      if (pairs > 0) {
        totalAgreement += pairwiseAgreements / pairs;
        dimensionCount++;
      }
    }

    return dimensionCount > 0 ? totalAgreement / dimensionCount : 1;
  }

  /**
   * Generate benchmark report for model/prompt version
   */
  generateReport(
    model: string,
    promptVersion: string,
    startDate?: Date,
    endDate?: Date,
  ): BenchmarkReport | null {
    const allContent = [...this.contentItems.values()].filter(
      (c) =>
        c.model === model &&
        c.promptVersion === promptVersion &&
        (!startDate || c.metadata.timestamp >= startDate) &&
        (!endDate || c.metadata.timestamp <= endDate),
    );

    if (allContent.length === 0) return null;

    const allRatings: EvaluatorRating[] = [];
    const qualityMetrics: ContentQualityMetrics[] = [];
    const flaggedSamples: string[] = [];

    for (const content of allContent) {
      const metrics = this.calculateQuality(content.id);
      if (metrics) {
        qualityMetrics.push(metrics);
        if (metrics.flaggedForReview) {
          flaggedSamples.push(content.id);
        }
      }
      const ratings = this.ratings.get(content.id) ?? [];
      allRatings.push(...ratings);
    }

    if (qualityMetrics.length === 0) return null;

    // Calculate dimension scores
    const dimensionScores: Record<EvaluationDimension, number> = {
      accuracy: 0,
      clarity: 0,
      completeness: 0,
      pedagogical_value: 0,
      safety: 0,
      hallucination_risk: 0,
    };

    const dimensions: EvaluationDimension[] = [
      "accuracy",
      "clarity",
      "completeness",
      "pedagogical_value",
      "safety",
      "hallucination_risk",
    ];

    for (const dim of dimensions) {
      const scores = qualityMetrics
        .map((m) => m.ratingsByDimension[dim]?.average)
        .filter((s): s is number => s !== undefined);
      dimensionScores[dim] =
        scores.length > 0
          ? parseFloat(
              (
                (scores.reduce((a, b) => a + b, 0) / scores.length) *
                20
              ).toFixed(1),
            )
          : 0;
    }

    // Rating distribution
    const ratingDistribution: Record<Rating, number> = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
    for (const rating of allRatings) {
      ratingDistribution[rating.rating]++;
    }

    // Overall score
    const overallQualityScore = Math.round(
      qualityMetrics.reduce((sum, m) => sum + m.overallScore, 0) /
        qualityMetrics.length,
    );

    // Generate recommendations
    const recommendations: string[] = [];

    if (dimensionScores.accuracy < 70) {
      recommendations.push(
        "Accuracy scores are below threshold. Review prompt engineering for factual correctness.",
      );
    }

    if (dimensionScores.safety < 80) {
      recommendations.push(
        "Safety scores need improvement. Strengthen content filtering and safety guardrails.",
      );
    }

    const avgIrr =
      qualityMetrics.reduce((sum, m) => sum + m.interRaterReliability, 0) /
      qualityMetrics.length;
    if (avgIrr < 0.7) {
      recommendations.push(
        "Inter-rater reliability is low. Consider clarifying evaluation rubrics or providing more evaluator training.",
      );
    }

    const hallucinationRate =
      allRatings.filter((r) => r.flags.includes("hallucination")).length /
      allRatings.length;
    if (hallucinationRate > 0.05) {
      recommendations.push(
        `Hallucination rate (${(hallucinationRate * 100).toFixed(1)}%) exceeds threshold. Implement fact-checking or retrieval augmentation.`,
      );
    }

    return {
      id: `benchmark-${Date.now()}`,
      model,
      promptVersion,
      startDate: startDate ?? allContent[0].metadata.timestamp,
      endDate: endDate ?? new Date(),
      totalSamples: allContent.length,
      totalEvaluators: new Set(allRatings.map((r) => r.evaluatorId)).size,
      overallQualityScore,
      dimensionScores,
      ratingDistribution,
      flaggedSamples,
      recommendations,
    };
  }

  /**
   * Get evaluator progress
   */
  getEvaluatorProgress(evaluatorId: string): {
    totalAssigned: number;
    completed: number;
    remaining: number;
    completionRate: number;
  } {
    const sessions = [...this.sessions.values()].filter(
      (s) => s.evaluatorId === evaluatorId,
    );

    const totalAssigned = sessions.reduce(
      (sum, s) => sum + s.assignedContentIds.length,
      0,
    );
    const completed = sessions.reduce(
      (sum, s) => sum + s.completedIds.length,
      0,
    );

    return {
      totalAssigned,
      completed,
      remaining: totalAssigned - completed,
      completionRate: totalAssigned > 0 ? completed / totalAssigned : 0,
    };
  }

  /**
   * Export ratings to CSV format
   */
  exportRatings(): string {
    const headers = [
      "content_id",
      "evaluator_id",
      "dimension",
      "rating",
      "confidence",
      "flags",
      "comments",
      "time_spent_seconds",
      "timestamp",
    ];

    const rows: string[] = [headers.join(",")];

    for (const [contentId, ratings] of this.ratings) {
      for (const rating of ratings) {
        rows.push(
          [
            contentId,
            rating.evaluatorId,
            rating.dimension,
            rating.rating,
            rating.confidence,
            rating.flags.join(";"),
            `"${(rating.comments ?? "").replace(/"/g, """)}"`,
            rating.timeSpentSeconds,
            rating.timestamp.toISOString(),
          ].join(","),
        );
      }
    }

    return rows.join("\n");
  }

  /**
   * Get all flagged content
   */
  getFlaggedContent(): Array<{ content: EvalContent; metrics: ContentQualityMetrics }> {
    const flagged: Array<{ content: EvalContent; metrics: ContentQualityMetrics }> = [];

    for (const content of this.contentItems.values()) {
      const metrics = this.calculateQuality(content.id);
      if (metrics?.flaggedForReview) {
        flagged.push({ content, metrics });
      }
    }

    return flagged.sort((a, b) => a.metrics.overallScore - b.metrics.overallScore);
  }
}

/**
 * Factory function
 */
export function createAIQualityBenchmarkService(
  thresholds?: Partial<QualityThresholds>,
): AIQualityBenchmarkService {
  return new AIQualityBenchmarkService(thresholds);
}
