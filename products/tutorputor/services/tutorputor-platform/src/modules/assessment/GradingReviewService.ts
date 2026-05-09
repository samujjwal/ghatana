/**
 * Grading Review Service
 *
 * Handles AI grading failures and routes them to human review.
 * Manages the pending_human_review status for assessment attempts.
 *
 * @doc.type class
 * @doc.purpose AI grading failure review path with human review workflow
 * @doc.layer platform
 * @doc.pattern Service
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";

const logger = createStandaloneLogger({ component: "GradingReviewService" });

// ============================================================================
// Review Types
// ============================================================================

export enum GradingFailureReason {
  LOW_CONFIDENCE = "low_confidence",
  AMBIGUOUS_RESPONSE = "ambiguous_response",
  SCHEMA_MISMATCH = "schema_mismatch",
  TIMEOUT = "timeout",
  ERROR = "error",
  CONTENT_MISMATCH = "content_mismatch",
}

export interface GradingReviewRequest {
  attemptId: string;
  tenantId: TenantId;
  userId: UserId;
  failureReason: GradingFailureReason;
  metadata: Record<string, unknown>;
}

export interface GradingReviewDecision {
  attemptId: string;
  reviewerId: UserId;
  tenantId: TenantId;
  approved: boolean;
  manualScore?: number;
  feedback?: string;
  reviewedAt: Date;
}

export interface PendingReview {
  attemptId: string;
  tenantId: TenantId;
  userId: UserId;
  assessmentId: string;
  failureReason: GradingFailureReason;
  submittedAt: Date;
  priority: "low" | "medium" | "high" | "urgent";
}

// ============================================================================
// Grading Review Service
// ============================================================================

export class GradingReviewService {
  private static instance: GradingReviewService;

  private constructor() {}

  static getInstance(): GradingReviewService {
    if (!GradingReviewService.instance) {
      GradingReviewService.instance = new GradingReviewService();
    }
    return GradingReviewService.instance;
  }

  /**
   * Route failed AI grading to human review
   */
  async routeToHumanReview(
    prisma: TutorPrismaClient,
    request: GradingReviewRequest,
  ): Promise<void> {
    try {
      // Update assessment attempt status to PENDING_HUMAN_REVIEW
      await prisma.assessmentAttempt.update({
        where: { id: request.attemptId },
        data: {
          status: "PENDING_HUMAN_REVIEW",
        },
      });

      logger.info({
        message: "Assessment attempt routed to human review",
        attemptId: request.attemptId,
        tenantId: request.tenantId,
        userId: request.userId,
        failureReason: request.failureReason,
      }, "GradingReviewService");

      // Create review record (implementation depends on schema)
      // This would typically create a GradingReview table entry
    } catch (error) {
      logger.error({
        message: "Failed to route to human review",
        attemptId: request.attemptId,
        tenantId: request.tenantId,
        error,
      }, "GradingReviewService");
      throw error;
    }
  }

  /**
   * Get pending reviews for a tenant
   */
  async getPendingReviews(
    prisma: TutorPrismaClient,
    tenantId: TenantId,
    limit: number = 50,
  ): Promise<PendingReview[]> {
    try {
      // Fetch pending reviews from database (implementation depends on schema)
      const pendingReviews: PendingReview[] = [];

      logger.info({
        message: "Pending reviews retrieved",
        tenantId,
        count: pendingReviews.length,
      }, "GradingReviewService");

      return pendingReviews;
    } catch (error) {
      logger.error({
        message: "Failed to get pending reviews",
        tenantId,
        error,
      }, "GradingReviewService");
      throw error;
    }
  }

  /**
   * Submit human review decision
   */
  async submitReviewDecision(
    prisma: TutorPrismaClient,
    decision: GradingReviewDecision,
  ): Promise<void> {
    try {
      // Update assessment attempt based on review decision
      const status = decision.approved ? ("GRADED" as const) : ("PENDING_HUMAN_REVIEW" as const);

      await prisma.assessmentAttempt.update({
        where: { id: decision.attemptId },
        data: {
          status,
          scorePercent: decision.manualScore,
          gradedAt: decision.reviewedAt,
          feedback: decision.feedback ? { humanReviewer: decision.reviewerId, feedback: decision.feedback } : undefined,
        },
      });

      logger.info({
        message: "Human review decision submitted",
        attemptId: decision.attemptId,
        reviewerId: decision.reviewerId,
        tenantId: decision.tenantId,
        approved: decision.approved,
        manualScore: decision.manualScore,
      }, "GradingReviewService");

      // Update review record (implementation depends on schema)
    } catch (error) {
      logger.error({
        message: "Failed to submit review decision",
        attemptId: decision.attemptId,
        reviewerId: decision.reviewerId,
        error,
      }, "GradingReviewService");
      throw error;
    }
  }

  /**
   * Check if AI grading should be routed to human review
   */
  shouldRouteToHumanReview(
    confidence: number,
    error: unknown | null,
    metadata: Record<string, unknown>,
  ): { shouldRoute: boolean; reason?: GradingFailureReason } {
    // Low confidence threshold
    if (confidence < 0.5) {
      return { shouldRoute: true, reason: GradingFailureReason.LOW_CONFIDENCE };
    }

    // Error occurred during grading
    if (error) {
      return { shouldRoute: true, reason: GradingFailureReason.ERROR };
    }

    // Check for ambiguous responses
    if (metadata.ambiguous === true) {
      return { shouldRoute: true, reason: GradingFailureReason.AMBIGUOUS_RESPONSE };
    }

    // Check for schema mismatch
    if (metadata.schemaMismatch === true) {
      return { shouldRoute: true, reason: GradingFailureReason.SCHEMA_MISMATCH };
    }

    // Check for content mismatch
    if (metadata.contentMismatch === true) {
      return { shouldRoute: true, reason: GradingFailureReason.CONTENT_MISMATCH };
    }

    return { shouldRoute: false };
  }

  /**
   * Calculate review priority based on failure reason and context
   */
  calculatePriority(
    failureReason: GradingFailureReason,
    submittedAt: Date,
  ): "low" | "medium" | "high" | "urgent" {
    const ageInHours = (Date.now() - submittedAt.getTime()) / (1000 * 60 * 60);

    // Urgent: Errors or content mismatches
    if (failureReason === GradingFailureReason.ERROR || failureReason === GradingFailureReason.CONTENT_MISMATCH) {
      return "urgent";
    }

    // High: Schema mismatches or old pending reviews
    if (failureReason === GradingFailureReason.SCHEMA_MISMATCH || ageInHours > 48) {
      return "high";
    }

    // Medium: Low confidence or ambiguous responses
    if (failureReason === GradingFailureReason.LOW_CONFIDENCE || failureReason === GradingFailureReason.AMBIGUOUS_RESPONSE) {
      return "medium";
    }

    // Low: Timeout
    return "low";
  }

  /**
   * Get review statistics for a tenant
   */
  async getReviewStatistics(
    prisma: TutorPrismaClient,
    tenantId: TenantId,
  ): Promise<{
    pending: number;
    completedToday: number;
    averageReviewTimeMinutes: number;
    failureReasonBreakdown: Record<GradingFailureReason, number>;
  }> {
    try {
      // Calculate statistics from database (implementation depends on schema)
      const stats = {
        pending: 0,
        completedToday: 0,
        averageReviewTimeMinutes: 0,
        failureReasonBreakdown: {
          [GradingFailureReason.LOW_CONFIDENCE]: 0,
          [GradingFailureReason.AMBIGUOUS_RESPONSE]: 0,
          [GradingFailureReason.SCHEMA_MISMATCH]: 0,
          [GradingFailureReason.TIMEOUT]: 0,
          [GradingFailureReason.ERROR]: 0,
          [GradingFailureReason.CONTENT_MISMATCH]: 0,
        },
      };

      logger.info({
        message: "Review statistics retrieved",
        tenantId,
        stats,
      }, "GradingReviewService");

      return stats;
    } catch (error) {
      logger.error({
        message: "Failed to get review statistics",
        tenantId,
        error,
      }, "GradingReviewService");
      throw error;
    }
  }

  /**
   * Escalate review to higher priority
   */
  async escalateReview(
    prisma: TutorPrismaClient,
    attemptId: string,
    tenantId: TenantId,
    reason: string,
  ): Promise<void> {
    try {
      logger.info({
        message: "Review escalated",
        attemptId,
        tenantId,
        reason,
      }, "GradingReviewService");

      // Update review priority (implementation depends on schema)
    } catch (error) {
      logger.error({
        message: "Failed to escalate review",
        attemptId,
        tenantId,
        error,
      }, "GradingReviewService");
      throw error;
    }
  }
}

// Singleton instance
export function getGradingReviewService(): GradingReviewService {
  return GradingReviewService.getInstance();
}
