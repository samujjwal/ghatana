/*
 * Copyright (c) 2026 Ghatana Technologies
 * AI API Regression Tests
 */
package com.ghatana.ai;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.embedding.EmbeddingRequest;
import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.vectorstore.VectorSearchResult;
import com.ghatana.ai.vectorstore.VectorStore;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for AI API stability.
 * 
 * These tests ensure that the AI API interfaces remain stable
 * and compatible after the split from ai-integration.
 */
@DisplayName("AI API Regression Tests")
class AIApiRegressionTest {

    @Nested
    @DisplayName("LLM Gateway Interface")
    class LLMGatewayTests {

        @Test
        @DisplayName("should maintain LLMGateway interface stability")
        void shouldMaintainLLMGatewayInterfaceStability() {
            // Test that LLMGateway interface methods exist and have correct signatures
            // This is a compile-time test ensuring interface stability
            
            LLMGateway gateway = null; // Interface existence test
            CompletionRequest request = null;
            
            // These method calls should compile if interface is stable
            Promise<CompletionResult> result = gateway.complete(request);
            List<String> models = gateway.getAvailableModels();
            Map<String, Object> metadata = gateway.getMetadata();
            
            // Verify method signatures return expected types
            assertThat(result).isNotNull();
            assertThat(models).isNotNull();
            assertThat(metadata).isNotNull();
        }
    }

    @Nested
    @DisplayName("Embedding Service Interface")
    class EmbeddingServiceTests {

        @Test
        @DisplayName("should maintain EmbeddingService interface stability")
        void shouldMaintainEmbeddingServiceInterfaceStability() {
            // Test that EmbeddingService interface methods exist and have correct signatures
            
            EmbeddingService service = null; // Interface existence test
            EmbeddingRequest request = null;
            
            // These method calls should compile if interface is stable
            Promise<EmbeddingResult> result = service.generateEmbedding(request);
            int dimension = service.getEmbeddingDimension();
            String modelName = service.getModelName();
            
            // Verify method signatures return expected types
            assertThat(result).isNotNull();
            assertThat(dimension).isGreaterThan(0);
            assertThat(modelName).isNotNull();
        }
    }

    @Nested
    @DisplayName("Vector Store Interface")
    class VectorStoreTests {

        @Test
        @DisplayName("should maintain VectorStore interface stability")
        void shouldMaintainVectorStoreInterfaceStability() {
            // Test that VectorStore interface methods exist and have correct signatures
            
            VectorStore store = null; // Interface existence test
            float[] embedding = new float[]{1.0f, 2.0f, 3.0f};
            
            // These method calls should compile if interface is stable
            Promise<List<VectorSearchResult>> results = store.search(embedding, 10);
            Promise<String> storeId = store.store(embedding, "test-id");
            Promise<Boolean> deleteResult = store.delete("test-id");
            
            // Verify method signatures return expected types
            assertThat(results).isNotNull();
            assertThat(storeId).isNotNull();
            assertThat(deleteResult).isNotNull();
        }
    }

    @Nested
    @DisplayName("AI Integration Service Interface")
    class AIIntegrationServiceTests {

        @Test
        @DisplayName("should maintain AIIntegrationService interface stability")
        void shouldMaintainAIIntegrationServiceInterfaceStability() {
            // Test that AIIntegrationService interface methods exist and have correct signatures
            
            AIIntegrationService service = null; // Interface existence test
            String prompt = "test prompt";
            
            // These method calls should compile if interface is stable
            String code = service.generateCode(prompt);
            Promise<String> completion = service.complete(prompt);
            
            // Verify method signatures return expected types
            assertThat(code).isNotNull();
            assertThat(completion).isNotNull();
        }
    }

    @Nested
    @DisplayName("Data Model Compatibility")
    class DataModelTests {

        @Test
        @DisplayName("should maintain CompletionRequest compatibility")
        void shouldMaintainCompletionRequestCompatibility() {
            // Test that CompletionRequest can be constructed with expected fields
            CompletionRequest request = CompletionRequest.builder()
                .prompt("test prompt")
                .maxTokens(100)
                .temperature(0.7)
                .build();
            
            assertThat(request.getPrompt()).isEqualTo("test prompt");
            assertThat(request.getMaxTokens()).isEqualTo(100);
            assertThat(request.getTemperature()).isEqualTo(0.7);
        }

        @Test
        @DisplayName("should maintain CompletionResult compatibility")
        void shouldMaintainCompletionResultCompatibility() {
            // Test that CompletionResult has expected fields
            CompletionResult result = CompletionResult.builder()
                .content("test content")
                .finishReason("stop")
                .usage(Map.of(
                    "promptTokens", 10,
                    "completionTokens", 20,
                    "totalTokens", 30
                ))
                .build();
            
            assertThat(result.getContent()).isEqualTo("test content");
            assertThat(result.getFinishReason()).isEqualTo("stop");
            assertThat(result.getUsage()).isNotNull();
        }

        @Test
        @DisplayName("should maintain EmbeddingResult compatibility")
        void shouldMaintainEmbeddingResultCompatibility() {
            // Test that EmbeddingResult has expected fields
            float[] embedding = new float[]{1.0f, 2.0f, 3.0f};
            EmbeddingResult result = EmbeddingResult.builder()
                .embedding(embedding)
                .model("test-model")
                .usage(Map.of("promptTokens", 5))
                .build();
            
            assertThat(result.getEmbedding()).isEqualTo(embedding);
            assertThat(result.getModel()).isEqualTo("test-model");
            assertThat(result.getUsage()).isNotNull();
        }
    }
}
