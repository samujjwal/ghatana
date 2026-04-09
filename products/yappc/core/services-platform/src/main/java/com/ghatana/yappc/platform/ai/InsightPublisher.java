package com.ghatana.yappc.platform.ai;

import com.ghatana.yappc.platform.ai.model.AIInsight;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Persists and broadcasts AI insights to the affected tenant while recording publication metrics.
 * @doc.layer product
 * @doc.pattern Publisher
 */
public final class InsightPublisher {

  private final InsightRepository repository;
  private final TenantInsightBroadcaster broadcaster;
  private final InsightMetrics metrics;
  private final InsightDeduplicator deduplicator;

  public InsightPublisher(
      InsightRepository repository,
      TenantInsightBroadcaster broadcaster,
      InsightMetrics metrics) {
    this(repository, broadcaster, metrics, new InsightDeduplicator());
  }

  public InsightPublisher(
      InsightRepository repository,
      TenantInsightBroadcaster broadcaster,
      InsightMetrics metrics,
      InsightDeduplicator deduplicator) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.deduplicator = Objects.requireNonNull(deduplicator, "deduplicator");
  }

  public Promise<Void> publish(List<AIInsight> insights, String tenantId) {
    Objects.requireNonNull(insights, "insights");
    String resolvedTenantId = Objects.requireNonNullElse(tenantId, "unknown-tenant");

    List<AIInsight> tenantInsights =
        insights.stream().filter(insight -> resolvedTenantId.equals(insight.tenantId())).toList();
    List<AIInsight> uniqueInsights = deduplicator.filter(tenantInsights);
    if (uniqueInsights.isEmpty()) {
      metrics.recordInsightsPublished(0);
      return Promise.complete();
    }

    return repository
      .saveAll(uniqueInsights)
        .then(
            saved ->
                broadcaster.broadcast(resolvedTenantId, saved)
                    .map(
                        ignored -> {
                          metrics.recordInsightsPublished(saved.size());
                          return null;
                        }));
  }

  public interface InsightRepository {
    Promise<List<AIInsight>> saveAll(List<AIInsight> insights);
  }

  public interface TenantInsightBroadcaster {
    Promise<Void> broadcast(String tenantId, List<AIInsight> insights);
  }

  public interface InsightMetrics {
    void recordInsightsPublished(int count);
  }
}
