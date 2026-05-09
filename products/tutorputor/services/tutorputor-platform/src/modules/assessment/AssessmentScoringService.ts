/**
 * Assessment Scoring Service
 *
 * Canonical scoring service for assessments with Confidence-Based Marking (CBM).
 * Provides standardized scoring logic across the platform.
 * Hardened to bind scoring to attempts, validate telemetry schemas, and enforce CBM linkage.
 *
 * @doc.type class
 * @doc.purpose Canonical assessment scoring with CBM support and evidence binding
 * @doc.layer platform
 * @doc.pattern Service
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";

const logger = createStandaloneLogger({ component: "AssessmentScoringService" });

// ============================================================================
// Scoring Types
// ============================================================================

export enum ConfidenceLevel {
  LOW = "low",
  MEDIUM = "medium",
  HIGH = "high",
}

export interface AssessmentResponse {
  itemId: string;
  answer: unknown;
  confidence: ConfidenceLevel;
  timeSpentSeconds: number;
}

export interface ScoringResult {
  score: number; // 0-100
  maxScore: number;
  correctCount: number;
  totalCount: number;
  confidenceAdjustment: number;
  confidenceBreakdown: {
    low: { correct: number; total: number; adjustment: number };
    medium: { correct: number; total: number; adjustment: number };
    high: { correct: number; total: number; adjustment: number };
  };
  // Evidence binding fields
  attemptId: string;
  scoredAt: string; // ISO timestamp
  telemetryValidated: boolean;
  cbmLinked: boolean;
}

export interface AttemptBinding {
  attemptId: string;
  userId: string;
  tenantId: string;
  assessmentId: string;
  startedAt: string;
}

export interface TelemetryEvent {
  eventType: string;
  timestamp: string;
  attemptId: string;
  itemId?: string;
  data: Record<string, unknown>;
}

export interface CBMConfig {
  enabled: boolean;
  lowConfidencePenalty: number; // Penalty multiplier for low confidence correct answers
  mediumConfidencePenalty: number; // Penalty multiplier for medium confidence correct answers
  highConfidenceBonus: number; // Bonus multiplier for high confidence correct answers
  incorrectPenalty: number; // Penalty for incorrect answers
}

// ============================================================================
// Assessment Scoring Service
// ============================================================================

export class AssessmentScoringService {
  private static instance: AssessmentScoringService;
  private defaultCBMConfig: CBMConfig = {
    enabled: true,
    lowConfidencePenalty: 0.5,
    mediumConfidencePenalty: 0.75,
    highConfidenceBonus: 1.1,
    incorrectPenalty: 0,
  };

  private constructor() {}

  static getInstance(): AssessmentScoringService {
    if (!AssessmentScoringService.instance) {
      AssessmentScoringService.instance = new AssessmentScoringService();
    }
    return AssessmentScoringService.instance;
  }

  /**
   * Score an assessment with optional CBM
   * Hardened to bind scoring to attempts and validate telemetry
   */
  scoreAssessment(
    responses: AssessmentResponse[],
    isCorrect: (response: AssessmentResponse) => boolean,
    attemptBinding: AttemptBinding,
    telemetryEvents?: TelemetryEvent[],
    pointsPerItem: number = 10,
    config?: Partial<CBMConfig>,
  ): ScoringResult {
    const cbmConfig = { ...this.defaultCBMConfig, ...config };
    const totalCount = responses.length;
    let rawScore = 0;
    let confidenceAdjustment = 0;

    const confidenceBreakdown = {
      low: { correct: 0, total: 0, adjustment: 0 },
      medium: { correct: 0, total: 0, adjustment: 0 },
      high: { correct: 0, total: 0, adjustment: 0 },
    };

    for (const response of responses) {
      const correct = isCorrect(response);
      const points = correct ? pointsPerItem : 0;

      if (cbmConfig.enabled) {
        // Apply CBM logic
        if (correct) {
          let adjustedPoints = points;
          let baseAdjustment = 0;

          switch (response.confidence) {
            case ConfidenceLevel.LOW:
              adjustedPoints = points * cbmConfig.lowConfidencePenalty;
              baseAdjustment = points * (cbmConfig.lowConfidencePenalty - 1);
              confidenceBreakdown.low.total++;
              confidenceBreakdown.low.correct++;
              confidenceBreakdown.low.adjustment += baseAdjustment;
              break;
            case ConfidenceLevel.MEDIUM:
              adjustedPoints = points * cbmConfig.mediumConfidencePenalty;
              baseAdjustment = points * (cbmConfig.mediumConfidencePenalty - 1);
              confidenceBreakdown.medium.total++;
              confidenceBreakdown.medium.correct++;
              confidenceBreakdown.medium.adjustment += baseAdjustment;
              break;
            case ConfidenceLevel.HIGH:
              adjustedPoints = points * cbmConfig.highConfidenceBonus;
              baseAdjustment = points * (cbmConfig.highConfidenceBonus - 1);
              confidenceBreakdown.high.total++;
              confidenceBreakdown.high.correct++;
              confidenceBreakdown.high.adjustment += baseAdjustment;
              break;
          }

          rawScore += adjustedPoints;
          confidenceAdjustment += baseAdjustment;
        } else {
          // Incorrect answers get no points, but track confidence for analytics
          switch (response.confidence) {
            case ConfidenceLevel.LOW:
              confidenceBreakdown.low.total++;
              break;
            case ConfidenceLevel.MEDIUM:
              confidenceBreakdown.medium.total++;
              break;
            case ConfidenceLevel.HIGH:
              confidenceBreakdown.high.total++;
              break;
          }

          // Apply incorrect penalty if configured
          if (cbmConfig.incorrectPenalty > 0) {
            const penalty = points * cbmConfig.incorrectPenalty;
            rawScore -= penalty;
            confidenceAdjustment -= penalty;
          }
        }
      } else {
        // Traditional scoring without CBM
        if (correct) {
          rawScore += points;
        }
      }
    }

    const maxScore = totalCount * pointsPerItem;
    const score = Math.max(0, Math.min(100, (rawScore / maxScore) * 100));
    const correctCount = responses.filter((r) => isCorrect(r)).length;

    // Validate telemetry events if provided
    const telemetryValidated = this.validateTelemetryEvents(attemptBinding.attemptId, telemetryEvents);

    logger.info({
      message: "Assessment scored",
      cbmEnabled: cbmConfig.enabled,
      score,
      maxScore,
      correctCount,
      totalCount,
      confidenceAdjustment,
      attemptId: attemptBinding.attemptId,
      telemetryValidated,
    }, "AssessmentScoringService");

    return {
      score,
      maxScore,
      correctCount,
      totalCount,
      confidenceAdjustment,
      confidenceBreakdown,
      attemptId: attemptBinding.attemptId,
      scoredAt: new Date().toISOString(),
      telemetryValidated,
      cbmLinked: cbmConfig.enabled,
    };
  }

  /**
   * Validate telemetry events for an attempt
   */
  private validateTelemetryEvents(attemptId: string, events?: TelemetryEvent[]): boolean {
    if (!events || events.length === 0) {
      return false;
    }

    // All events must belong to the same attempt
    const allMatchAttempt = events.every((event) => event.attemptId === attemptId);
    if (!allMatchAttempt) {
      logger.warn({
        message: "Telemetry events attempt ID mismatch",
        attemptId,
        eventAttemptIds: events.map((e) => e.attemptId),
      }, "AssessmentScoringService");
      return false;
    }

    // Validate required fields
    const allValid = events.every((event) => {
      return (
        event.eventType &&
        typeof event.eventType === "string" &&
        event.timestamp &&
        typeof event.timestamp === "string" &&
        event.data &&
        typeof event.data === "object"
      );
    });

    return allValid;
  }

  /**
   * Calculate passing status based on score and passing threshold
   */
  isPassing(score: number, passingThreshold: number): boolean {
    return score >= passingThreshold;
  }

  /**
   * Calculate grade based on score
   */
  calculateGrade(score: number): string {
    if (score >= 90) return "A";
    if (score >= 80) return "B";
    if (score >= 70) return "C";
    if (score >= 60) return "D";
    return "F";
  }

  /**
   * Calculate mastery level based on score
   */
  calculateMasteryLevel(score: number): "novice" | "developing" | "proficient" | "advanced" | "expert" {
    if (score >= 95) return "expert";
    if (score >= 85) return "advanced";
    if (score >= 70) return "proficient";
    if (score >= 50) return "developing";
    return "novice";
  }

  /**
   * Get CBM configuration
   */
  getCBMConfig(): CBMConfig {
    return { ...this.defaultCBMConfig };
  }

  /**
   * Update CBM configuration
   */
  updateCBMConfig(config: Partial<CBMConfig>): void {
    this.defaultCBMConfig = { ...this.defaultCBMConfig, ...config };
    logger.info({
      message: "CBM configuration updated",
      config: this.defaultCBMConfig,
    }, "AssessmentScoringService");
  }

  /**
   * Calculate item-level scoring with CBM
   * Returns item-level scoring without attempt binding (for internal use)
   */
  scoreItem(
    response: AssessmentResponse,
    isCorrect: boolean,
    pointsPerItem: number = 10,
    config?: Partial<CBMConfig>,
  ): { points: number; adjustment: number } {
    const cbmConfig = { ...this.defaultCBMConfig, ...config };
    let points = isCorrect ? pointsPerItem : 0;
    let adjustment = 0;

    if (cbmConfig.enabled && isCorrect) {
      switch (response.confidence) {
        case ConfidenceLevel.LOW:
          points = points * cbmConfig.lowConfidencePenalty;
          adjustment = pointsPerItem * (cbmConfig.lowConfidencePenalty - 1);
          break;
        case ConfidenceLevel.MEDIUM:
          points = points * cbmConfig.mediumConfidencePenalty;
          adjustment = pointsPerItem * (cbmConfig.mediumConfidencePenalty - 1);
          break;
        case ConfidenceLevel.HIGH:
          points = points * cbmConfig.highConfidenceBonus;
          adjustment = pointsPerItem * (cbmConfig.highConfidenceBonus - 1);
          break;
      }
    } else if (cbmConfig.incorrectPenalty > 0 && !isCorrect) {
      const penalty = pointsPerItem * cbmConfig.incorrectPenalty;
      points -= penalty;
      adjustment = -penalty;
    }

    return { points, adjustment };
  }

  /**
   * Validate attempt binding consistency
   */
  validateAttemptBinding(attemptBinding: AttemptBinding): boolean {
    return !!(
      attemptBinding.attemptId &&
      attemptBinding.userId &&
      attemptBinding.tenantId &&
      attemptBinding.assessmentId &&
      attemptBinding.startedAt
    );
  }
}

// Singleton instance
export function getAssessmentScoringService(): AssessmentScoringService {
  return AssessmentScoringService.getInstance();
}
