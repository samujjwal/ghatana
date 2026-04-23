/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.metrics;

import com.ghatana.platform.observability.Metrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for AISuggestionMetricsCollector.
 *
 * @doc.type class
 * @doc.purpose Test AI suggestion metrics collection
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("AISuggestionMetricsCollector")
class AISuggestionMetricsCollectorTest {

    private AISuggestionMetricsCollector collector;
    private Metrics metrics;

    @BeforeEach
    void setUp() { // GH-90000
        metrics = mock(Metrics.class); // GH-90000
        collector = new AISuggestionMetricsCollector(metrics); // GH-90000
    }

    @Nested
    @DisplayName("Suggestion Tracking")
    class SuggestionTrackingTests {

        @Test
        @DisplayName("records suggestion shown")
        void recordsSuggestionShown() { // GH-90000
            collector.recordSuggestionShown("stage", "suggestion-1", Map.of("eventType", "transaction.created")); // GH-90000
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.shown()).isEqualTo(1); // GH-90000
            assertThat(stats.accepted()).isEqualTo(0); // GH-90000
            assertThat(stats.rejected()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("records suggestion accepted")
        void recordsSuggestionAccepted() { // GH-90000
            collector.recordSuggestionAccepted("stage", "suggestion-1"); // GH-90000
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.accepted()).isEqualTo(1); // GH-90000
            assertThat(stats.rejected()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("records suggestion rejected")
        void recordsSuggestionRejected() { // GH-90000
            collector.recordSuggestionRejected("stage", "suggestion-1", "not_relevant"); // GH-90000
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.rejected()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("records suggestion success")
        void recordsSuggestionSuccess() { // GH-90000
            collector.recordSuggestionSuccess("stage", "suggestion-1"); // GH-90000
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.success()).isEqualTo(1); // GH-90000
            assertThat(stats.failure()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("records suggestion failure")
        void recordsSuggestionFailure() { // GH-90000
            collector.recordSuggestionFailure("stage", "suggestion-1"); // GH-90000
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.failure()).isEqualTo(1); // GH-90000
            assertThat(stats.success()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("increments counters correctly")
        void incrementsCountersCorrectly() { // GH-90000
            collector.recordSuggestionShown("stage", "suggestion-1", Map.of()); // GH-90000
            collector.recordSuggestionShown("stage", "suggestion-2", Map.of()); // GH-90000
            collector.recordSuggestionAccepted("stage", "suggestion-1"); // GH-90000
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.shown()).isEqualTo(2); // GH-90000
            assertThat(stats.accepted()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("tracks multiple suggestion types separately")
        void tracksMultipleSuggestionTypesSeparately() { // GH-90000
            collector.recordSuggestionShown("stage", "s1", Map.of()); // GH-90000
            collector.recordSuggestionShown("pipeline", "p1", Map.of()); // GH-90000
            
            assertThat(collector.getStats("stage").shown()).isEqualTo(1);
            assertThat(collector.getStats("pipeline").shown()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Rate Calculations")
    class RateCalculationTests {

        @Test
        @DisplayName("calculates click-through rate")
        void calculatesClickThroughRate() { // GH-90000
            collector.recordSuggestionShown("stage", "s1", Map.of()); // GH-90000
            collector.recordSuggestionShown("stage", "s2", Map.of()); // GH-90000
            collector.recordSuggestionAccepted("stage", "s1"); // GH-90000
            
            double ctr = collector.getClickThroughRate("stage");
            assertThat(ctr).isEqualTo(0.5); // GH-90000
        }

        @Test
        @DisplayName("returns 0 when no suggestions shown")
        void returns0WhenNoSuggestionsShown() { // GH-90000
            double ctr = collector.getClickThroughRate("stage");
            assertThat(ctr).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("calculates adoption rate")
        void calculatesAdoptionRate() { // GH-90000
            collector.recordSuggestionAccepted("stage", "s1"); // GH-90000
            collector.recordSuggestionAccepted("stage", "s2"); // GH-90000
            collector.recordSuggestionSuccess("stage", "s1"); // GH-90000
            
            double adoptionRate = collector.getAdoptionRate("stage");
            assertThat(adoptionRate).isEqualTo(0.5); // GH-90000
        }

        @Test
        @DisplayName("returns 0 adoption when no suggestions accepted")
        void returns0AdoptionWhenNoSuggestionsAccepted() { // GH-90000
            double adoptionRate = collector.getAdoptionRate("stage");
            assertThat(adoptionRate).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("acceptance rate from stats")
        void acceptanceRateFromStats() { // GH-90000
            collector.recordSuggestionShown("stage", "s1", Map.of()); // GH-90000
            collector.recordSuggestionShown("stage", "s2", Map.of()); // GH-90000
            collector.recordSuggestionAccepted("stage", "s1"); // GH-90000
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.acceptanceRate()).isEqualTo(0.5); // GH-90000
        }

        @Test
        @DisplayName("success rate from stats")
        void successRateFromStats() { // GH-90000
            collector.recordSuggestionAccepted("stage", "s1"); // GH-90000
            collector.recordSuggestionAccepted("stage", "s2"); // GH-90000
            collector.recordSuggestionSuccess("stage", "s1"); // GH-90000
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.successRate()).isEqualTo(0.5); // GH-90000
        }
    }

    @Nested
    @DisplayName("SuggestionStats")
    class SuggestionStatsTests {

        @Test
        @DisplayName("stats start at zero")
        void statsStartAtZero() { // GH-90000
            AISuggestionMetricsCollector.SuggestionStats stats = new AISuggestionMetricsCollector.SuggestionStats(); // GH-90000
            
            assertThat(stats.shown()).isEqualTo(0); // GH-90000
            assertThat(stats.accepted()).isEqualTo(0); // GH-90000
            assertThat(stats.rejected()).isEqualTo(0); // GH-90000
            assertThat(stats.success()).isEqualTo(0); // GH-90000
            assertThat(stats.failure()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("increment operations are thread-safe")
        void incrementOperationsAreThreadSafe() throws InterruptedException { // GH-90000
            AISuggestionMetricsCollector.SuggestionStats stats = new AISuggestionMetricsCollector.SuggestionStats(); // GH-90000
            
            Thread t1 = new Thread(() -> { // GH-90000
                for (int i = 0; i < 1000; i++) stats.incrementShown(); // GH-90000
            });
            Thread t2 = new Thread(() -> { // GH-90000
                for (int i = 0; i < 1000; i++) stats.incrementAccepted(); // GH-90000
            });
            
            t1.start(); // GH-90000
            t2.start(); // GH-90000
            t1.join(); // GH-90000
            t2.join(); // GH-90000
            
            assertThat(stats.shown()).isEqualTo(1000); // GH-90000
            assertThat(stats.accepted()).isEqualTo(1000); // GH-90000
        }
    }

    @Nested
    @DisplayName("Get Stats")
    class GetStatsTests {

        @Test
        @DisplayName("returns empty stats for unknown type")
        void returnsEmptyStatsForUnknownType() { // GH-90000
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("unknown");
            
            assertThat(stats.shown()).isEqualTo(0); // GH-90000
            assertThat(stats.accepted()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("returns stats for known type")
        void returnsStatsForKnownType() { // GH-90000
            collector.recordSuggestionShown("stage", "s1", Map.of()); // GH-90000
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.shown()).isEqualTo(1); // GH-90000
        }
    }
}
