package com.ghatana.yappc.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.platform.ai.InsightFeedbackService.FeedbackDecision;
import com.ghatana.yappc.platform.ai.InsightFeedbackService.FeedbackStats;
import com.ghatana.yappc.platform.ai.InsightFeedbackService.InsightFeedback;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import io.activej.promise.Promise;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InsightFeedbackService Tests")
class InsightFeedbackServiceTest extends EventloopTestBase {

  @Test
  @DisplayName("recordFeedback raises threshold when dismissals are high")
  void recordFeedbackRaisesThresholdWhenDismissalsAreHigh() {
    AtomicReference<InsightFeedback> captured = new AtomicReference<>();
    InsightFeedbackService service =
        new InsightFeedbackService(
            new InsightFeedbackService.FeedbackRepository() {
              @Override
              public Promise<Void> record(InsightFeedback feedback) {
                captured.set(feedback);
                return Promise.complete();
              }

              @Override
              public Promise<FeedbackStats> loadStats(String tenantId, AIInsight.InsightType insightType) {
                return Promise.of(new FeedbackStats(tenantId, insightType, 2, 5, 0.7));
              }
            },
            Clock.fixed(Instant.parse("2026-04-06T12:00:00Z"), ZoneOffset.UTC));

    InsightFeedbackService.ThresholdRecommendation recommendation =
        runPromise(
            () ->
                service.recordFeedback(
                    new InsightFeedback(
                        "tenant-a",
                        "insight-1",
                        AIInsight.InsightType.ARCHITECTURE,
                        FeedbackDecision.DISMISSED,
                        null)));

    assertThat(captured.get().recordedAt()).isEqualTo(Instant.parse("2026-04-06T12:00:00Z"));
    assertThat(recommendation.recommendedThreshold()).isEqualTo(0.75);
  }

  @Test
  @DisplayName("recordFeedback lowers threshold when approvals are strong")
  void recordFeedbackLowersThresholdWhenApprovalsAreStrong() {
    InsightFeedbackService service =
        new InsightFeedbackService(
            new InsightFeedbackService.FeedbackRepository() {
              @Override
              public Promise<Void> record(InsightFeedback feedback) {
                return Promise.complete();
              }

              @Override
              public Promise<FeedbackStats> loadStats(String tenantId, AIInsight.InsightType insightType) {
                return Promise.of(new FeedbackStats(tenantId, insightType, 8, 2, 0.7));
              }
            });

    InsightFeedbackService.ThresholdRecommendation recommendation =
        runPromise(
            () ->
                service.recordFeedback(
                    new InsightFeedback(
                        "tenant-a",
                        "insight-2",
                        AIInsight.InsightType.PERFORMANCE,
                        FeedbackDecision.APPROVED,
                        Instant.parse("2026-04-06T12:00:00Z"))));

    assertThat(recommendation.recommendedThreshold()).isEqualTo(0.65);
    assertThat(recommendation.rationale()).contains("Approval rate is strong");
  }
}
