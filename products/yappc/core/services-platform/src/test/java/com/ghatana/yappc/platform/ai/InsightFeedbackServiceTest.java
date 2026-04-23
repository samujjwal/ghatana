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
  void recordFeedbackRaisesThresholdWhenDismissalsAreHigh() { // GH-90000
    AtomicReference<InsightFeedback> captured = new AtomicReference<>(); // GH-90000
    InsightFeedbackService service =
        new InsightFeedbackService( // GH-90000
            new InsightFeedbackService.FeedbackRepository() { // GH-90000
              @Override
              public Promise<Void> record(InsightFeedback feedback) { // GH-90000
                captured.set(feedback); // GH-90000
                return Promise.complete(); // GH-90000
              }

              @Override
              public Promise<FeedbackStats> loadStats(String tenantId, AIInsight.InsightType insightType) { // GH-90000
                return Promise.of(new FeedbackStats(tenantId, insightType, 2, 5, 0.7)); // GH-90000
              }
            },
            Clock.fixed(Instant.parse("2026-04-06T12:00:00Z"), ZoneOffset.UTC));

    InsightFeedbackService.ThresholdRecommendation recommendation =
        runPromise( // GH-90000
            () -> // GH-90000
                service.recordFeedback( // GH-90000
                    new InsightFeedback( // GH-90000
                        "tenant-a",
                        "insight-1",
                        AIInsight.InsightType.ARCHITECTURE,
                        FeedbackDecision.DISMISSED,
                        null)));

    assertThat(captured.get().recordedAt()).isEqualTo(Instant.parse("2026-04-06T12:00:00Z"));
    assertThat(recommendation.recommendedThreshold()).isEqualTo(0.75); // GH-90000
  }

  @Test
  @DisplayName("recordFeedback lowers threshold when approvals are strong")
  void recordFeedbackLowersThresholdWhenApprovalsAreStrong() { // GH-90000
    InsightFeedbackService service =
        new InsightFeedbackService( // GH-90000
            new InsightFeedbackService.FeedbackRepository() { // GH-90000
              @Override
              public Promise<Void> record(InsightFeedback feedback) { // GH-90000
                return Promise.complete(); // GH-90000
              }

              @Override
              public Promise<FeedbackStats> loadStats(String tenantId, AIInsight.InsightType insightType) { // GH-90000
                return Promise.of(new FeedbackStats(tenantId, insightType, 8, 2, 0.7)); // GH-90000
              }
            });

    InsightFeedbackService.ThresholdRecommendation recommendation =
        runPromise( // GH-90000
            () -> // GH-90000
                service.recordFeedback( // GH-90000
                    new InsightFeedback( // GH-90000
                        "tenant-a",
                        "insight-2",
                        AIInsight.InsightType.PERFORMANCE,
                        FeedbackDecision.APPROVED,
                        Instant.parse("2026-04-06T12:00:00Z"))));

    assertThat(recommendation.recommendedThreshold()).isEqualTo(0.65); // GH-90000
    assertThat(recommendation.rationale()).contains("Approval rate is strong");
  }
}
