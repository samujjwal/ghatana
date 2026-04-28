package com.ghatana.yappc.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.yappc.platform.ai.model.AIInsight;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InsightDeduplicator Tests")
class InsightDeduplicatorTest {

  @Test
  @DisplayName("filter suppresses duplicate insights inside the duplicate window")
  void filterSuppressesDuplicateInsightsInsideDuplicateWindow() { // GH-90000
    InsightDeduplicator deduplicator =
        new InsightDeduplicator( // GH-90000
            Duration.ofMinutes(30), // GH-90000
            Clock.fixed(Instant.parse("2026-04-06T12:00:00Z"), ZoneOffset.UTC));

    List<AIInsight> firstPass = deduplicator.filter(List.of(insight("tenant-a"), insight("tenant-a")));
    List<AIInsight> secondPass = deduplicator.filter(List.of(insight("tenant-a")));

    assertThat(firstPass).hasSize(1); // GH-90000
    assertThat(secondPass).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("filter keeps distinct insight fingerprints")
  void filterKeepsDistinctInsightFingerprints() { // GH-90000
    InsightDeduplicator deduplicator = new InsightDeduplicator(); // GH-90000

    List<AIInsight> filtered =
        deduplicator.filter( // GH-90000
            List.of( // GH-90000
                insight("tenant-a"),
                new AIInsight( // GH-90000
                    "insight-2",
                    "tenant-a",
                    "project-a",
                    AIInsight.InsightType.SECURITY,
                    AIInsight.InsightSeverity.WARNING,
                    "Different type",
                    "Description",
                    "Suggestion",
                    0.9,
                    "src/App.ts",
                    12,
                    List.of("security"),
                    Instant.parse("2026-04-06T12:00:00Z"),
                    false)));

    assertThat(filtered).hasSize(2); // GH-90000
  }

  private AIInsight insight(String tenantId) { // GH-90000
    return new AIInsight( // GH-90000
        "insight-1",
        tenantId,
        "project-a",
        AIInsight.InsightType.PERFORMANCE,
        AIInsight.InsightSeverity.WARNING,
        "Nested loop hotspot",
        "Description",
        "Suggestion",
        0.9,
        "src/App.ts",
        12,
        List.of("performance"),
        Instant.parse("2026-04-06T12:00:00Z"),
        false);
  }
}
