package com.ghatana.yappc.platform.ai;

import com.ghatana.yappc.platform.ai.model.AIInsight;
import io.activej.promise.Promise;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Stores user feedback on AI insights and recalibrates routing thresholds when signals become noisy or trusted.
 * @doc.layer product
 * @doc.pattern Service
 */
public final class InsightFeedbackService {

  private static final double MIN_THRESHOLD = 0.5;
  private static final double MAX_THRESHOLD = 0.95;

  private final FeedbackRepository repository;
  private final Clock clock;

  public InsightFeedbackService(FeedbackRepository repository) {
    this(repository, Clock.systemUTC());
  }

  InsightFeedbackService(FeedbackRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public Promise<ThresholdRecommendation> recordFeedback(InsightFeedback feedback) {
    Objects.requireNonNull(feedback, "feedback");

    InsightFeedback normalized =
        new InsightFeedback(
            feedback.tenantId(),
            feedback.insightId(),
            feedback.insightType(),
            feedback.decision(),
            feedback.recordedAt() == null ? clock.instant() : feedback.recordedAt());

    return repository
        .record(normalized)
        .then(ignored -> repository.loadStats(normalized.tenantId(), normalized.insightType()))
        .map(this::recommendationFromStats);
  }

  private ThresholdRecommendation recommendationFromStats(FeedbackStats stats) {
    int total = Math.max(1, stats.approvedCount() + stats.dismissedCount());
    double approvalRatio = (double) stats.approvedCount() / total;
    double dismissalRatio = (double) stats.dismissedCount() / total;

    double recommendedThreshold = stats.currentThreshold();
    String rationale = "Feedback remains balanced; keep the current threshold.";

    if (dismissalRatio >= 0.55) {
      recommendedThreshold = clamp(stats.currentThreshold() + 0.05);
      rationale = "Dismissal rate is elevated; raise the confidence threshold to reduce noisy insights.";
    } else if (approvalRatio >= 0.7) {
      recommendedThreshold = clamp(stats.currentThreshold() - 0.05);
      rationale = "Approval rate is strong; lower the confidence threshold slightly to surface more useful insights.";
    }

    return new ThresholdRecommendation(stats.insightType(), recommendedThreshold, rationale);
  }

  private double clamp(double value) {
    double bounded = Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, value));
    return Math.round(bounded * 100.0d) / 100.0d;
  }

  public interface FeedbackRepository {
    Promise<Void> record(InsightFeedback feedback);

    Promise<FeedbackStats> loadStats(String tenantId, AIInsight.InsightType insightType);
  }

  public enum FeedbackDecision {
    APPROVED,
    DISMISSED
  }

  public record InsightFeedback(
      String tenantId,
      String insightId,
      AIInsight.InsightType insightType,
      FeedbackDecision decision,
      Instant recordedAt) {

    public InsightFeedback {
      tenantId = Objects.requireNonNullElse(tenantId, "unknown-tenant");
      insightId = Objects.requireNonNullElse(insightId, "unknown-insight");
      insightType = insightType == null ? AIInsight.InsightType.CODE_QUALITY : insightType;
      decision = decision == null ? FeedbackDecision.DISMISSED : decision;
    }
  }

  public record FeedbackStats(
      String tenantId,
      AIInsight.InsightType insightType,
      int approvedCount,
      int dismissedCount,
      double currentThreshold) {

    public FeedbackStats {
      tenantId = Objects.requireNonNullElse(tenantId, "unknown-tenant");
      insightType = insightType == null ? AIInsight.InsightType.CODE_QUALITY : insightType;
      approvedCount = Math.max(0, approvedCount);
      dismissedCount = Math.max(0, dismissedCount);
      currentThreshold = Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, currentThreshold));
    }
  }

  public record ThresholdRecommendation(
      AIInsight.InsightType insightType, double recommendedThreshold, String rationale) {

    public ThresholdRecommendation {
      insightType = insightType == null ? AIInsight.InsightType.CODE_QUALITY : insightType;
      recommendedThreshold = Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, recommendedThreshold));
      rationale = Objects.requireNonNullElse(rationale, "");
    }
  }
}
