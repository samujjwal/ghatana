/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void setUp() {
        metrics = mock(Metrics.class);
        collector = new AISuggestionMetricsCollector(metrics);
    }

    @Nested
    @DisplayName("Suggestion Tracking")
    class SuggestionTrackingTests {

        @Test
        @DisplayName("records suggestion shown")
        void recordsSuggestionShown() {
            collector.recordSuggestionShown("stage", "suggestion-1", Map.of("eventType", "transaction.created"));
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.shown()).isEqualTo(1);
            assertThat(stats.accepted()).isEqualTo(0);
            assertThat(stats.rejected()).isEqualTo(0);
        }

        @Test
        @DisplayName("records suggestion accepted")
        void recordsSuggestionAccepted() {
            collector.recordSuggestionAccepted("stage", "suggestion-1");
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.accepted()).isEqualTo(1);
            assertThat(stats.rejected()).isEqualTo(0);
        }

        @Test
        @DisplayName("records suggestion rejected")
        void recordsSuggestionRejected() {
            collector.recordSuggestionRejected("stage", "suggestion-1", "not_relevant");
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.rejected()).isEqualTo(1);
        }

        @Test
        @DisplayName("records suggestion success")
        void recordsSuggestionSuccess() {
            collector.recordSuggestionSuccess("stage", "suggestion-1");
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.success()).isEqualTo(1);
            assertThat(stats.failure()).isEqualTo(0);
        }

        @Test
        @DisplayName("records suggestion failure")
        void recordsSuggestionFailure() {
            collector.recordSuggestionFailure("stage", "suggestion-1");
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.failure()).isEqualTo(1);
            assertThat(stats.success()).isEqualTo(0);
        }

        @Test
        @DisplayName("increments counters correctly")
        void incrementsCountersCorrectly() {
            collector.recordSuggestionShown("stage", "suggestion-1", Map.of());
            collector.recordSuggestionShown("stage", "suggestion-2", Map.of());
            collector.recordSuggestionAccepted("stage", "suggestion-1");
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.shown()).isEqualTo(2);
            assertThat(stats.accepted()).isEqualTo(1);
        }

        @Test
        @DisplayName("tracks multiple suggestion types separately")
        void tracksMultipleSuggestionTypesSeparately() {
            collector.recordSuggestionShown("stage", "s1", Map.of());
            collector.recordSuggestionShown("pipeline", "p1", Map.of());
            
            assertThat(collector.getStats("stage").shown()).isEqualTo(1);
            assertThat(collector.getStats("pipeline").shown()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Rate Calculations")
    class RateCalculationTests {

        @Test
        @DisplayName("calculates click-through rate")
        void calculatesClickThroughRate() {
            collector.recordSuggestionShown("stage", "s1", Map.of());
            collector.recordSuggestionShown("stage", "s2", Map.of());
            collector.recordSuggestionAccepted("stage", "s1");
            
            double ctr = collector.getClickThroughRate("stage");
            assertThat(ctr).isEqualTo(0.5);
        }

        @Test
        @DisplayName("returns 0 when no suggestions shown")
        void returns0WhenNoSuggestionsShown() {
            double ctr = collector.getClickThroughRate("stage");
            assertThat(ctr).isEqualTo(0.0);
        }

        @Test
        @DisplayName("calculates adoption rate")
        void calculatesAdoptionRate() {
            collector.recordSuggestionAccepted("stage", "s1");
            collector.recordSuggestionAccepted("stage", "s2");
            collector.recordSuggestionSuccess("stage", "s1");
            
            double adoptionRate = collector.getAdoptionRate("stage");
            assertThat(adoptionRate).isEqualTo(0.5);
        }

        @Test
        @DisplayName("returns 0 adoption when no suggestions accepted")
        void returns0AdoptionWhenNoSuggestionsAccepted() {
            double adoptionRate = collector.getAdoptionRate("stage");
            assertThat(adoptionRate).isEqualTo(0.0);
        }

        @Test
        @DisplayName("acceptance rate from stats")
        void acceptanceRateFromStats() {
            collector.recordSuggestionShown("stage", "s1", Map.of());
            collector.recordSuggestionShown("stage", "s2", Map.of());
            collector.recordSuggestionAccepted("stage", "s1");
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.acceptanceRate()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("success rate from stats")
        void successRateFromStats() {
            collector.recordSuggestionAccepted("stage", "s1");
            collector.recordSuggestionAccepted("stage", "s2");
            collector.recordSuggestionSuccess("stage", "s1");
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.successRate()).isEqualTo(0.5);
        }
    }

    @Nested
    @DisplayName("SuggestionStats")
    class SuggestionStatsTests {

        @Test
        @DisplayName("stats start at zero")
        void statsStartAtZero() {
            AISuggestionMetricsCollector.SuggestionStats stats = new AISuggestionMetricsCollector.SuggestionStats();
            
            assertThat(stats.shown()).isEqualTo(0);
            assertThat(stats.accepted()).isEqualTo(0);
            assertThat(stats.rejected()).isEqualTo(0);
            assertThat(stats.success()).isEqualTo(0);
            assertThat(stats.failure()).isEqualTo(0);
        }

        @Test
        @DisplayName("increment operations are thread-safe")
        void incrementOperationsAreThreadSafe() throws InterruptedException {
            AISuggestionMetricsCollector.SuggestionStats stats = new AISuggestionMetricsCollector.SuggestionStats();
            
            Thread t1 = new Thread(() -> {
                for (int i = 0; i < 1000; i++) stats.incrementShown();
            });
            Thread t2 = new Thread(() -> {
                for (int i = 0; i < 1000; i++) stats.incrementAccepted();
            });
            
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            
            assertThat(stats.shown()).isEqualTo(1000);
            assertThat(stats.accepted()).isEqualTo(1000);
        }
    }

    @Nested
    @DisplayName("Get Stats")
    class GetStatsTests {

        @Test
        @DisplayName("returns empty stats for unknown type")
        void returnsEmptyStatsForUnknownType() {
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("unknown");
            
            assertThat(stats.shown()).isEqualTo(0);
            assertThat(stats.accepted()).isEqualTo(0);
        }

        @Test
        @DisplayName("returns stats for known type")
        void returnsStatsForKnownType() {
            collector.recordSuggestionShown("stage", "s1", Map.of());
            
            AISuggestionMetricsCollector.SuggestionStats stats = collector.getStats("stage");
            assertThat(stats.shown()).isEqualTo(1);
        }
    }
}
