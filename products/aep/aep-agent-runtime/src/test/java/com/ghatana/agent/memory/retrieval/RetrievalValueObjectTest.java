/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for retrieval value objects: RetrievalRequest, RetrievalResult,
 * RetrievalExplanation, and InjectionConfig.
 *
 * @doc.type class
 * @doc.purpose Tests for memory retrieval value objects
 * @doc.layer agent-memory
 * @doc.pattern Test
 */
@DisplayName("Memory Retrieval – Value Object Tests [GH-90000]")
class RetrievalValueObjectTest {

    // =========================================================================
    // RetrievalRequest
    // =========================================================================

    @Nested
    @DisplayName("RetrievalRequest [GH-90000]")
    class RetrievalRequestTests {

        @Test
        @DisplayName("builder defaults: k=10, hybridAlpha=0.7, includeSimilarityScores=true [GH-90000]")
        void builderDefaults() { // GH-90000
            RetrievalRequest request = RetrievalRequest.builder() // GH-90000
                    .query("find my recent notes [GH-90000]")
                    .build(); // GH-90000

            assertThat(request.getQuery()).isEqualTo("find my recent notes [GH-90000]");
            assertThat(request.getK()).isEqualTo(10); // GH-90000
            assertThat(request.getHybridAlpha()).isEqualTo(0.7); // GH-90000
            assertThat(request.isIncludeSimilarityScores()).isTrue(); // GH-90000
            assertThat(request.isIncludeExplanation()).isFalse(); // GH-90000
            assertThat(request.getFilters()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("builder accepts custom k value [GH-90000]")
        void customKValue() { // GH-90000
            RetrievalRequest request = RetrievalRequest.builder() // GH-90000
                    .query("q [GH-90000]")
                    .k(25) // GH-90000
                    .build(); // GH-90000

            assertThat(request.getK()).isEqualTo(25); // GH-90000
        }

        @Test
        @DisplayName("builder accepts tenantId and agentId [GH-90000]")
        void tenantAndAgentId() { // GH-90000
            RetrievalRequest request = RetrievalRequest.builder() // GH-90000
                    .query("q [GH-90000]")
                    .tenantId("acme [GH-90000]")
                    .agentId("agent-001 [GH-90000]")
                    .build(); // GH-90000

            assertThat(request.getTenantId()).isEqualTo("acme [GH-90000]");
            assertThat(request.getAgentId()).isEqualTo("agent-001 [GH-90000]");
        }

        @Test
        @DisplayName("builder accepts custom filters map [GH-90000]")
        void customFilters() { // GH-90000
            RetrievalRequest request = RetrievalRequest.builder() // GH-90000
                    .query("q [GH-90000]")
                    .filters(Map.of("tier", "working")) // GH-90000
                    .build(); // GH-90000

            assertThat(request.getFilters()).containsEntry("tier", "working"); // GH-90000
        }

        @Test
        @DisplayName("builder accepts time range [GH-90000]")
        void timeRange() { // GH-90000
            Instant start = Instant.parse("2024-01-01T00:00:00Z [GH-90000]");
            Instant end = Instant.parse("2024-12-31T23:59:59Z [GH-90000]");

            RetrievalRequest request = RetrievalRequest.builder() // GH-90000
                    .query("q [GH-90000]")
                    .startTime(start) // GH-90000
                    .endTime(end) // GH-90000
                    .build(); // GH-90000

            assertThat(request.getStartTime()).isEqualTo(start); // GH-90000
            assertThat(request.getEndTime()).isEqualTo(end); // GH-90000
        }

        @Test
        @DisplayName("includeExplanation can be set to true [GH-90000]")
        void includeExplanationTrue() { // GH-90000
            RetrievalRequest request = RetrievalRequest.builder() // GH-90000
                    .query("q [GH-90000]")
                    .includeExplanation(true) // GH-90000
                    .build(); // GH-90000

            assertThat(request.isIncludeExplanation()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("pure-dense alpha (1.0) can be set [GH-90000]")
        void pureDenseAlpha() { // GH-90000
            RetrievalRequest request = RetrievalRequest.builder() // GH-90000
                    .query("q [GH-90000]")
                    .hybridAlpha(1.0) // GH-90000
                    .build(); // GH-90000

            assertThat(request.getHybridAlpha()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("equals and hashCode work for identical builders [GH-90000]")
        void equalsAndHashCode() { // GH-90000
            RetrievalRequest r1 = RetrievalRequest.builder().query("q [GH-90000]").k(5).build();
            RetrievalRequest r2 = RetrievalRequest.builder().query("q [GH-90000]").k(5).build();

            assertThat(r1).isEqualTo(r2); // GH-90000
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode()); // GH-90000
        }
    }

    // =========================================================================
    // RetrievalResult
    // =========================================================================

    @Nested
    @DisplayName("RetrievalResult [GH-90000]")
    class RetrievalResultTests {

        @Test
        @DisplayName("builder creates result with correct fields [GH-90000]")
        void builderCreatesResult() { // GH-90000
            RetrievalResult result = RetrievalResult.builder() // GH-90000
                    .items(List.of()) // GH-90000
                    .totalCandidates(100) // GH-90000
                    .retrievalTimeMs(42) // GH-90000
                    .strategyUsed("hybrid [GH-90000]")
                    .explanation("Used BM25 + dense retrieval [GH-90000]")
                    .build(); // GH-90000

            assertThat(result.getItems()).isEmpty(); // GH-90000
            assertThat(result.getTotalCandidates()).isEqualTo(100); // GH-90000
            assertThat(result.getRetrievalTimeMs()).isEqualTo(42); // GH-90000
            assertThat(result.getStrategyUsed()).isEqualTo("hybrid [GH-90000]");
            assertThat(result.getExplanation()).isEqualTo("Used BM25 + dense retrieval [GH-90000]");
        }

        @Test
        @DisplayName("explanation can be null [GH-90000]")
        void explanationCanBeNull() { // GH-90000
            RetrievalResult result = RetrievalResult.builder() // GH-90000
                    .items(List.of()) // GH-90000
                    .totalCandidates(0) // GH-90000
                    .retrievalTimeMs(0) // GH-90000
                    .strategyUsed("bm25 [GH-90000]")
                    .build(); // GH-90000

            assertThat(result.getExplanation()).isNull(); // GH-90000
        }
    }

    // =========================================================================
    // RetrievalExplanation
    // =========================================================================

    @Nested
    @DisplayName("RetrievalExplanation [GH-90000]")
    class RetrievalExplanationTests {

        @Test
        @DisplayName("builder creates explanation with defaults [GH-90000]")
        void builderDefaults() { // GH-90000
            RetrievalExplanation explanation = RetrievalExplanation.builder() // GH-90000
                    .retrievalStrategy("hybrid [GH-90000]")
                    .candidatesScanned(50) // GH-90000
                    .scores(Map.of("item-1", Map.of("bm25", 0.8))) // GH-90000
                    .build(); // GH-90000

            assertThat(explanation.getRetrievalStrategy()).isEqualTo("hybrid [GH-90000]");
            assertThat(explanation.getCandidatesScanned()).isEqualTo(50); // GH-90000
            assertThat(explanation.getFiltersApplied()).isEmpty(); // default // GH-90000
            assertThat(explanation.getRerankerInputs()).isEmpty(); // default // GH-90000
        }

        @Test
        @DisplayName("filtersApplied can be set [GH-90000]")
        void filtersApplied() { // GH-90000
            RetrievalExplanation explanation = RetrievalExplanation.builder() // GH-90000
                    .retrievalStrategy("bm25 [GH-90000]")
                    .candidatesScanned(20) // GH-90000
                    .scores(Map.of()) // GH-90000
                    .filtersApplied(List.of("tier=working", "before=2025-01-01")) // GH-90000
                    .build(); // GH-90000

            assertThat(explanation.getFiltersApplied()).containsExactly("tier=working", "before=2025-01-01"); // GH-90000
        }
    }

    // =========================================================================
    // InjectionConfig
    // =========================================================================

    @Nested
    @DisplayName("InjectionConfig [GH-90000]")
    class InjectionConfigTests {

        @Test
        @DisplayName("builder defaults: maxTokens=4000, groupByTier=true, format=MARKDOWN [GH-90000]")
        void builderDefaults() { // GH-90000
            InjectionConfig config = InjectionConfig.builder().build(); // GH-90000

            assertThat(config.getMaxTokens()).isEqualTo(4000); // GH-90000
            assertThat(config.isGroupByTier()).isTrue(); // GH-90000
            assertThat(config.isIncludeProvenance()).isTrue(); // GH-90000
            assertThat(config.isIncludeConfidence()).isTrue(); // GH-90000
            assertThat(config.isIncludeConflictMarkers()).isTrue(); // GH-90000
            assertThat(config.getFormat()).isEqualTo(InjectionConfig.Format.MARKDOWN); // GH-90000
        }

        @Test
        @DisplayName("format can be set to JSON [GH-90000]")
        void formatJsonOverride() { // GH-90000
            InjectionConfig config = InjectionConfig.builder() // GH-90000
                    .format(InjectionConfig.Format.JSON) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getFormat()).isEqualTo(InjectionConfig.Format.JSON); // GH-90000
        }

        @Test
        @DisplayName("format can be set to XML [GH-90000]")
        void formatXmlOverride() { // GH-90000
            InjectionConfig config = InjectionConfig.builder() // GH-90000
                    .format(InjectionConfig.Format.XML) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getFormat()).isEqualTo(InjectionConfig.Format.XML); // GH-90000
        }

        @Test
        @DisplayName("maxTokens can be overridden [GH-90000]")
        void maxTokensOverride() { // GH-90000
            InjectionConfig config = InjectionConfig.builder() // GH-90000
                    .maxTokens(2000) // GH-90000
                    .build(); // GH-90000

            assertThat(config.getMaxTokens()).isEqualTo(2000); // GH-90000
        }

        @Test
        @DisplayName("groupByTier can be disabled [GH-90000]")
        void groupByTierDisabled() { // GH-90000
            InjectionConfig config = InjectionConfig.builder() // GH-90000
                    .groupByTier(false) // GH-90000
                    .build(); // GH-90000

            assertThat(config.isGroupByTier()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Format enum has three values: MARKDOWN, XML, JSON [GH-90000]")
        void formatEnumValues() { // GH-90000
            assertThat(InjectionConfig.Format.values()).containsExactlyInAnyOrder( // GH-90000
                    InjectionConfig.Format.MARKDOWN,
                    InjectionConfig.Format.XML,
                    InjectionConfig.Format.JSON
            );
        }
    }
}
