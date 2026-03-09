package com.ghatana.flashit.agent.service;

import com.ghatana.flashit.agent.config.AgentConfig;
import com.ghatana.flashit.agent.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EmbeddingService.
 */
@DisplayName("EmbeddingService Tests")
class EmbeddingServiceTest {

    private EmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingService(new TestAgentConfig(""));
    }

    @Test
    @DisplayName("generateEmbedding() handles missing API key gracefully")
    void generateEmbeddingHandlesMissingApiKey() {
        // GIVEN
        EmbeddingRequest request = new EmbeddingRequest(
                "moment-1", "Hello world", "user-123", "TEXT", false);

        // WHEN
        EmbeddingResponse response = service.generateEmbedding(request);

        // THEN — should return empty embedding on failure
        assertThat(response).isNotNull();
        assertThat(response.model()).isNotBlank();
        assertThat(response.processingTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("generateBatchEmbeddings() processes multiple requests")
    void generateBatchEmbeddingsProcessesMultiple() {
        // GIVEN
        List<EmbeddingRequest> requests = List.of(
                new EmbeddingRequest("m1", "Text 1", "user-1", "TEXT", false),
                new EmbeddingRequest("m2", "Text 2", "user-1", "TEXT", false)
        );

        // WHEN
        List<EmbeddingResponse> responses = service.generateBatchEmbeddings(requests);

        // THEN
        assertThat(responses).hasSize(2);
    }

    @Test
    @DisplayName("semanticSearch() returns empty results when no embeddings stored")
    void semanticSearchReturnsEmptyWithNoData() {
        // GIVEN
        SemanticSearchRequest request = new SemanticSearchRequest(
                "find my moment", "user-123", List.of(), 10, 0.5);

        // WHEN
        SemanticSearchResponse response = service.semanticSearch(request);

        // THEN
        assertThat(response).isNotNull();
        assertThat(response.results()).isEmpty();
        assertThat(response.query()).isEqualTo("find my moment");
    }

    private static class TestAgentConfig extends AgentConfig {
        private final String apiKey;

        TestAgentConfig(String apiKey) {
            this.apiKey = apiKey;
        }

        @Override
        public String getOpenAiApiKey() {
            return apiKey;
        }

        @Override
        public boolean isOpenAiConfigured() {
            return false;
        }
    }
}
