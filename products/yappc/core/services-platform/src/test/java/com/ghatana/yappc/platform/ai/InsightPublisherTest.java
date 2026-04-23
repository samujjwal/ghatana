package com.ghatana.yappc.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.platform.ai.model.AIInsight;
import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InsightPublisher Tests")
class InsightPublisherTest extends EventloopTestBase {

  @Test
  @DisplayName("publish saves and broadcasts tenant insights")
  void publishSavesAndBroadcastsTenantInsights() { // GH-90000
    List<AIInsight> saved = new ArrayList<>(); // GH-90000
    List<AIInsight> broadcast = new ArrayList<>(); // GH-90000
    AtomicInteger metricCount = new AtomicInteger(-1); // GH-90000

    InsightPublisher publisher =
        new InsightPublisher( // GH-90000
            insights -> {
              saved.addAll(insights); // GH-90000
              return Promise.of(insights); // GH-90000
            },
            (tenantId, insights) -> { // GH-90000
              assertThat(tenantId).isEqualTo("tenant-a");
              broadcast.addAll(insights); // GH-90000
              return Promise.complete(); // GH-90000
            },
            metricCount::set);

    runPromise(() -> publisher.publish(List.of(insight("tenant-a"), insight("tenant-b")), "tenant-a"));

    assertThat(saved).hasSize(1); // GH-90000
    assertThat(broadcast).hasSize(1); // GH-90000
    assertThat(metricCount.get()).isEqualTo(1); // GH-90000
  }

  @Test
  @DisplayName("publish records zero when no tenant insights are present")
  void publishRecordsZeroWhenNoTenantInsightsPresent() { // GH-90000
    AtomicInteger metricCount = new AtomicInteger(-1); // GH-90000

    InsightPublisher publisher =
        new InsightPublisher( // GH-90000
            insights -> Promise.of(insights), // GH-90000
            (tenantId, insights) -> Promise.complete(), // GH-90000
            metricCount::set);

    runPromise(() -> publisher.publish(List.of(insight("tenant-b")), "tenant-a"));

    assertThat(metricCount.get()).isZero(); // GH-90000
  }

  @Test
  @DisplayName("publish suppresses duplicate insights before save and broadcast")
  void publishSuppressesDuplicateInsightsBeforeSaveAndBroadcast() { // GH-90000
    List<AIInsight> saved = new ArrayList<>(); // GH-90000
    AtomicInteger metricCount = new AtomicInteger(-1); // GH-90000

    InsightPublisher publisher =
        new InsightPublisher( // GH-90000
            insights -> {
              saved.addAll(insights); // GH-90000
              return Promise.of(insights); // GH-90000
            },
            (tenantId, insights) -> Promise.complete(), // GH-90000
            metricCount::set,
            new InsightDeduplicator()); // GH-90000

    runPromise(() -> publisher.publish(List.of(insight("tenant-a"), insight("tenant-a")), "tenant-a"));

    assertThat(saved).hasSize(1); // GH-90000
    assertThat(metricCount.get()).isEqualTo(1); // GH-90000
  }

  private AIInsight insight(String tenantId) { // GH-90000
    return new AIInsight( // GH-90000
        null,
        tenantId,
        null,
        null,
        null,
        null,
        null,
        null,
        5.0,
        null,
        -3,
        null,
        null,
        false);
  }
}
