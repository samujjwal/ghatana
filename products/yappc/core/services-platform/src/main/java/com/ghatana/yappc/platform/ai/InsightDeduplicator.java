package com.ghatana.yappc.platform.ai;

import com.ghatana.yappc.platform.ai.model.AIInsight;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Suppresses repeated AI insights within a short window so users see distinct recommendations instead of noise.
 * @doc.layer product
 * @doc.pattern Policy
 */
public final class InsightDeduplicator {

  private static final Duration DEFAULT_DUPLICATE_WINDOW = Duration.ofMinutes(30);

  private final Duration duplicateWindow;
  private final Clock clock;
  private final Map<String, Instant> publishedFingerprints = new LinkedHashMap<>();

  public InsightDeduplicator() {
    this(DEFAULT_DUPLICATE_WINDOW, Clock.systemUTC());
  }

  InsightDeduplicator(Duration duplicateWindow, Clock clock) {
    this.duplicateWindow = Objects.requireNonNull(duplicateWindow, "duplicateWindow");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public synchronized List<AIInsight> filter(List<AIInsight> insights) {
    Objects.requireNonNull(insights, "insights");
    Instant now = clock.instant();
    publishedFingerprints.entrySet().removeIf(entry -> entry.getValue().plus(duplicateWindow).isBefore(now));

    List<AIInsight> uniqueInsights = new ArrayList<>();
    for (AIInsight insight : insights) {
      String fingerprint = fingerprint(insight);
      Instant publishedAt = publishedFingerprints.get(fingerprint);
      if (publishedAt != null && publishedAt.plus(duplicateWindow).isAfter(now)) {
        continue;
      }
      publishedFingerprints.put(fingerprint, now);
      uniqueInsights.add(insight);
    }

    return List.copyOf(uniqueInsights);
  }

  private String fingerprint(AIInsight insight) {
    return insight.tenantId()
        + '|'
        + insight.projectId()
        + '|'
        + insight.type().name()
        + '|'
        + insight.sourceRef()
        + '|'
        + insight.lineNumber()
        + '|'
        + insight.title()
        + '|'
        + insight.suggestion();
  }
}
