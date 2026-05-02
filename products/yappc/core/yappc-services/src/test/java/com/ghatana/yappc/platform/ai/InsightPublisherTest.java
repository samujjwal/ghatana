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
  void publishSavesAndBroadcastsTenantInsights() { 
    List<AIInsight> saved = new ArrayList<>(); 
    List<AIInsight> broadcast = new ArrayList<>(); 
    AtomicInteger metricCount = new AtomicInteger(-1); 

    InsightPublisher publisher =
        new InsightPublisher( 
            insights -> {
              saved.addAll(insights); 
              return Promise.of(insights); 
            },
            (tenantId, insights) -> { 
              assertThat(tenantId).isEqualTo("tenant-a");
              broadcast.addAll(insights); 
              return Promise.complete(); 
            },
            metricCount::set);

    runPromise(() -> publisher.publish(List.of(insight("tenant-a"), insight("tenant-b")), "tenant-a"));

    assertThat(saved).hasSize(1); 
    assertThat(broadcast).hasSize(1); 
    assertThat(metricCount.get()).isEqualTo(1); 
  }

  @Test
  @DisplayName("publish records zero when no tenant insights are present")
  void publishRecordsZeroWhenNoTenantInsightsPresent() { 
    AtomicInteger metricCount = new AtomicInteger(-1); 

    InsightPublisher publisher =
        new InsightPublisher( 
            insights -> Promise.of(insights), 
            (tenantId, insights) -> Promise.complete(), 
            metricCount::set);

    runPromise(() -> publisher.publish(List.of(insight("tenant-b")), "tenant-a"));

    assertThat(metricCount.get()).isZero(); 
  }

  @Test
  @DisplayName("publish suppresses duplicate insights before save and broadcast")
  void publishSuppressesDuplicateInsightsBeforeSaveAndBroadcast() { 
    List<AIInsight> saved = new ArrayList<>(); 
    AtomicInteger metricCount = new AtomicInteger(-1); 

    InsightPublisher publisher =
        new InsightPublisher( 
            insights -> {
              saved.addAll(insights); 
              return Promise.of(insights); 
            },
            (tenantId, insights) -> Promise.complete(), 
            metricCount::set,
            new InsightDeduplicator()); 

    runPromise(() -> publisher.publish(List.of(insight("tenant-a"), insight("tenant-a")), "tenant-a"));

    assertThat(saved).hasSize(1); 
    assertThat(metricCount.get()).isEqualTo(1); 
  }

  private AIInsight insight(String tenantId) { 
    return new AIInsight( 
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
