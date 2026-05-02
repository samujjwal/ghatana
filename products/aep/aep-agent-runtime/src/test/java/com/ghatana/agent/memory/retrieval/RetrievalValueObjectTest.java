/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("Memory Retrieval – Value Object Tests")
class RetrievalValueObjectTest {

    // =========================================================================
    // RetrievalRequest
    // =========================================================================

    @Nested
    @DisplayName("RetrievalRequest")
    class RetrievalRequestTests {

        @Test
        @DisplayName("builder defaults: k=10, hybridAlpha=0.7, includeSimilarityScores=true")
        void builderDefaults() { 
            RetrievalRequest request = RetrievalRequest.builder() 
                    .query("find my recent notes")
                    .build(); 

            assertThat(request.getQuery()).isEqualTo("find my recent notes");
            assertThat(request.getK()).isEqualTo(10); 
            assertThat(request.getHybridAlpha()).isEqualTo(0.7); 
            assertThat(request.isIncludeSimilarityScores()).isTrue(); 
            assertThat(request.isIncludeExplanation()).isFalse(); 
            assertThat(request.getFilters()).isEmpty(); 
        }

        @Test
        @DisplayName("builder accepts custom k value")
        void customKValue() { 
            RetrievalRequest request = RetrievalRequest.builder() 
                    .query("q")
                    .k(25) 
                    .build(); 

            assertThat(request.getK()).isEqualTo(25); 
        }

        @Test
        @DisplayName("builder accepts tenantId and agentId")
        void tenantAndAgentId() { 
            RetrievalRequest request = RetrievalRequest.builder() 
                    .query("q")
                    .tenantId("acme")
                    .agentId("agent-001")
                    .build(); 

            assertThat(request.getTenantId()).isEqualTo("acme");
            assertThat(request.getAgentId()).isEqualTo("agent-001");
        }

        @Test
        @DisplayName("builder accepts custom filters map")
        void customFilters() { 
            RetrievalRequest request = RetrievalRequest.builder() 
                    .query("q")
                    .filters(Map.of("tier", "working")) 
                    .build(); 

            assertThat(request.getFilters()).containsEntry("tier", "working"); 
        }

        @Test
        @DisplayName("builder accepts time range")
        void timeRange() { 
            Instant start = Instant.parse("2024-01-01T00:00:00Z");
            Instant end = Instant.parse("2024-12-31T23:59:59Z");

            RetrievalRequest request = RetrievalRequest.builder() 
                    .query("q")
                    .startTime(start) 
                    .endTime(end) 
                    .build(); 

            assertThat(request.getStartTime()).isEqualTo(start); 
            assertThat(request.getEndTime()).isEqualTo(end); 
        }

        @Test
        @DisplayName("includeExplanation can be set to true")
        void includeExplanationTrue() { 
            RetrievalRequest request = RetrievalRequest.builder() 
                    .query("q")
                    .includeExplanation(true) 
                    .build(); 

            assertThat(request.isIncludeExplanation()).isTrue(); 
        }

        @Test
        @DisplayName("pure-dense alpha (1.0) can be set")
        void pureDenseAlpha() { 
            RetrievalRequest request = RetrievalRequest.builder() 
                    .query("q")
                    .hybridAlpha(1.0) 
                    .build(); 

            assertThat(request.getHybridAlpha()).isEqualTo(1.0); 
        }

        @Test
        @DisplayName("equals and hashCode work for identical builders")
        void equalsAndHashCode() { 
            RetrievalRequest r1 = RetrievalRequest.builder().query("q").k(5).build();
            RetrievalRequest r2 = RetrievalRequest.builder().query("q").k(5).build();

            assertThat(r1).isEqualTo(r2); 
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode()); 
        }
    }

    // =========================================================================
    // RetrievalResult
    // =========================================================================

    @Nested
    @DisplayName("RetrievalResult")
    class RetrievalResultTests {

        @Test
        @DisplayName("builder creates result with correct fields")
        void builderCreatesResult() { 
            RetrievalResult result = RetrievalResult.builder() 
                    .items(List.of()) 
                    .totalCandidates(100) 
                    .retrievalTimeMs(42) 
                    .strategyUsed("hybrid")
                    .explanation("Used BM25 + dense retrieval")
                    .build(); 

            assertThat(result.getItems()).isEmpty(); 
            assertThat(result.getTotalCandidates()).isEqualTo(100); 
            assertThat(result.getRetrievalTimeMs()).isEqualTo(42); 
            assertThat(result.getStrategyUsed()).isEqualTo("hybrid");
            assertThat(result.getExplanation()).isEqualTo("Used BM25 + dense retrieval");
        }

        @Test
        @DisplayName("explanation can be null")
        void explanationCanBeNull() { 
            RetrievalResult result = RetrievalResult.builder() 
                    .items(List.of()) 
                    .totalCandidates(0) 
                    .retrievalTimeMs(0) 
                    .strategyUsed("bm25")
                    .build(); 

            assertThat(result.getExplanation()).isNull(); 
        }
    }

    // =========================================================================
    // RetrievalExplanation
    // =========================================================================

    @Nested
    @DisplayName("RetrievalExplanation")
    class RetrievalExplanationTests {

        @Test
        @DisplayName("builder creates explanation with defaults")
        void builderDefaults() { 
            RetrievalExplanation explanation = RetrievalExplanation.builder() 
                    .retrievalStrategy("hybrid")
                    .candidatesScanned(50) 
                    .scores(Map.of("item-1", Map.of("bm25", 0.8))) 
                    .build(); 

            assertThat(explanation.getRetrievalStrategy()).isEqualTo("hybrid");
            assertThat(explanation.getCandidatesScanned()).isEqualTo(50); 
            assertThat(explanation.getFiltersApplied()).isEmpty(); // default 
            assertThat(explanation.getRerankerInputs()).isEmpty(); // default 
        }

        @Test
        @DisplayName("filtersApplied can be set")
        void filtersApplied() { 
            RetrievalExplanation explanation = RetrievalExplanation.builder() 
                    .retrievalStrategy("bm25")
                    .candidatesScanned(20) 
                    .scores(Map.of()) 
                    .filtersApplied(List.of("tier=working", "before=2025-01-01")) 
                    .build(); 

            assertThat(explanation.getFiltersApplied()).containsExactly("tier=working", "before=2025-01-01"); 
        }
    }

    // =========================================================================
    // InjectionConfig
    // =========================================================================

    @Nested
    @DisplayName("InjectionConfig")
    class InjectionConfigTests {

        @Test
        @DisplayName("builder defaults: maxTokens=4000, groupByTier=true, format=MARKDOWN")
        void builderDefaults() { 
            InjectionConfig config = InjectionConfig.builder().build(); 

            assertThat(config.getMaxTokens()).isEqualTo(4000); 
            assertThat(config.isGroupByTier()).isTrue(); 
            assertThat(config.isIncludeProvenance()).isTrue(); 
            assertThat(config.isIncludeConfidence()).isTrue(); 
            assertThat(config.isIncludeConflictMarkers()).isTrue(); 
            assertThat(config.getFormat()).isEqualTo(InjectionConfig.Format.MARKDOWN); 
        }

        @Test
        @DisplayName("format can be set to JSON")
        void formatJsonOverride() { 
            InjectionConfig config = InjectionConfig.builder() 
                    .format(InjectionConfig.Format.JSON) 
                    .build(); 

            assertThat(config.getFormat()).isEqualTo(InjectionConfig.Format.JSON); 
        }

        @Test
        @DisplayName("format can be set to XML")
        void formatXmlOverride() { 
            InjectionConfig config = InjectionConfig.builder() 
                    .format(InjectionConfig.Format.XML) 
                    .build(); 

            assertThat(config.getFormat()).isEqualTo(InjectionConfig.Format.XML); 
        }

        @Test
        @DisplayName("maxTokens can be overridden")
        void maxTokensOverride() { 
            InjectionConfig config = InjectionConfig.builder() 
                    .maxTokens(2000) 
                    .build(); 

            assertThat(config.getMaxTokens()).isEqualTo(2000); 
        }

        @Test
        @DisplayName("groupByTier can be disabled")
        void groupByTierDisabled() { 
            InjectionConfig config = InjectionConfig.builder() 
                    .groupByTier(false) 
                    .build(); 

            assertThat(config.isGroupByTier()).isFalse(); 
        }

        @Test
        @DisplayName("Format enum has three values: MARKDOWN, XML, JSON")
        void formatEnumValues() { 
            assertThat(InjectionConfig.Format.values()).containsExactlyInAnyOrder( 
                    InjectionConfig.Format.MARKDOWN,
                    InjectionConfig.Format.XML,
                    InjectionConfig.Format.JSON
            );
        }
    }
}
