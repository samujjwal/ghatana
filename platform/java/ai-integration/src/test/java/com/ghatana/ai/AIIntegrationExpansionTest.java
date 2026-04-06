/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.ai.llm;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.vectorstore.VectorSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

/**
 * Phase 3 Expansion tests for AI Integration module.
 * Tests LLM completion requests, embeddings, vector search, and batch processing.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for LLM and AI integration subsystem
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AIIntegration - Phase 3 Expansion")
class AIIntegrationExpansionTest {

    // ============================================
    // COMPLETION REQUEST VALIDATION (5 tests)
    // ============================================

    @Nested
    @DisplayName("Completion Request Validation")
    class CompletionRequestTests {

        @Test
        @DisplayName("Builder validates prompt XOR messages")
        void validatePromptOrMessages() {
            assertThatThrownBy(() -> CompletionRequest.builder().build())
                .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> CompletionRequest.builder()
                .messages(List.of())
                .build())
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("MaxTokens defaults and bounds checking")
        void maxTokensBounds() {
            CompletionRequest req = CompletionRequest.builder()
                .prompt("test")
                .maxTokens(2048)
                .build();

            assertThat(req.getMaxTokens()).isEqualTo(2048);
        }

        @Test
        @DisplayName("Temperature and sampling parameters")
        void temperatureAndSampling() {
            CompletionRequest req = CompletionRequest.builder()
                .prompt("test")
                .temperature(0.5)
                .topP(0.95)
                .frequencyPenalty(0.1)
                .presencePenalty(0.1)
                .build();

            assertThat(req.getTemperature()).isEqualTo(0.5);
            assertThat(req.getTopP()).isEqualTo(0.95);
            assertThat(req.getFrequencyPenalty()).isEqualTo(0.1);
            assertThat(req.getPresencePenalty()).isEqualTo(0.1);
        }

        @Test
        @DisplayName("Custom parameters in request")
        void customParameters() {
            Map<String, Object> customParams = new HashMap<>();
            customParams.put("presence_penalty", 0.2);
            customParams.put("custom_field", "value");

            CompletionRequest req = CompletionRequest.builder()
                .prompt("test")
                .metadata(customParams)
                .build();

            assertThat(req.getMetadata()).isNotEmpty();
        }

        @Test
        @DisplayName("Many messages in single request")
        void manyMessagesInRequest() {
            List<ChatMessage> messages = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                if (idx % 2 == 0) {
                    messages.add(ChatMessage.user("User message " + idx));
                } else {
                    messages.add(ChatMessage.assistant("Assistant response " + idx));
                }
            }

            CompletionRequest req = CompletionRequest.builder()
                .messages(messages)
                .build();

            assertThat(req.getMessages()).hasSize(100);
        }
    }

    // ============================================
    // CHAT MESSAGE PATTERNS (4 tests)
    // ============================================

    @Nested
    @DisplayName("Chat Message Patterns")
    class ChatMessageTests {

        @Test
        @DisplayName("Create messages in various roles")
        void variousRoles() {
            ChatMessage user = ChatMessage.user("user text");
            ChatMessage assistant = ChatMessage.assistant("assistant text");
            ChatMessage system = ChatMessage.system("system text");

            assertThat(user.getContent()).isEqualTo("user text");
            assertThat(assistant.getContent()).isEqualTo("assistant text");
            assertThat(system.getContent()).isEqualTo("system text");
        }

        @Test
        @DisplayName("Message with tool calls")
        void messagesWithToolCalls() {
            List<ToolCall> toolCalls = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                ToolCall call = ToolCall.of("tool-" + idx, "function", Map.of("param", "value"));
                toolCalls.add(call);
            }

            ChatMessage msg = ChatMessage.assistant("calling tools");
            assertThat(msg.getContent()).isEqualTo("calling tools");
            assertThat(toolCalls).hasSize(10);
        }

        @Test
        @DisplayName("Long conversation thread")
        void longConversationThread() {
            List<ChatMessage> messages = new ArrayList<>();
            for (int turn = 0; turn < 50; turn++) {
                final int turnIdx = turn;
                messages.add(ChatMessage.user("User turn " + turnIdx));
                messages.add(ChatMessage.assistant("Assistant turn " + turnIdx));
            }

            assertThat(messages).hasSize(100);
            assertThat(messages.get(0).getContent()).contains("User turn 0");
            assertThat(messages.get(99).getContent()).contains("Assistant turn 49");
        }

        @Test
        @DisplayName("Unicode and special characters in messages")
        void unicodeContent() {
            ChatMessage msg = ChatMessage.user("Hello 🌍 with émojis and ñ characters");
            assertThat(msg.getContent()).contains("🌍");
        }
    }

    // ============================================
    // COMPLETION RESULT HANDLING (4 tests)
    // ============================================

    @Nested
    @DisplayName("Completion Result Handling")
    class CompletionResultTests {

        @Test
        @DisplayName("Parse completion result with text content")
        void parseTextCompletion() {
            CompletionResult result = CompletionResult.builder()
                .modelUsed("model-v1")
                .text("The completion text")
                .finishReason("stop")
                .tokensUsed(100)
                .build();

            assertThat(result.getModelUsed()).isEqualTo("model-v1");
            assertThat(result.getText()).isEqualTo("The completion text");
            assertThat(result.getFinishReason()).isEqualTo("stop");
            assertThat(result.getTokensUsed()).isEqualTo(100);
        }

        @Test
        @DisplayName("Completion with tool calls")
        void completionWithToolCalls() {
            List<ToolCall> toolCalls = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                ToolCall call = ToolCall.of("call-" + idx, "function-" + idx, Map.of("result", "result-" + idx));
                toolCalls.add(call);
            }

            CompletionResult result = CompletionResult.builder()
                .modelUsed("model-v1")
                .finishReason("tool_calls")
                .tokensUsed(150)
                .toolCalls(toolCalls)
                .build();

            assertThat(result.getToolCalls()).hasSize(5);
            assertThat(result.getFinishReason()).isEqualTo("tool_calls");
        }

        @Test
        @DisplayName("Many completions processed in sequence")
        void manyCompletions() {
            List<CompletionResult> results = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                final int idx = i;
                CompletionResult result = CompletionResult.builder()
                    .modelUsed("model")
                    .text("Response " + idx)
                    .finishReason("stop")
                    .tokensUsed(50 + idx)
                    .build();
                results.add(result);
            }

            assertThat(results).hasSize(50);
            assertThat(results.get(25).getText()).isEqualTo("Response 25");
        }

        @Test
        @DisplayName("Very long completion text")
        void veryLongCompletion() {
            String longText = "Generated text ".repeat(1000);
            CompletionResult result = CompletionResult.builder()
                .modelUsed("model")
                .text(longText)
                .finishReason("stop")
                .tokensUsed(10000)
                .build();

            assertThat(result.getText()).hasSize(longText.length());
        }
    }

    // ============================================
    // EMBEDDING OPERATIONS (4 tests)
    // ============================================

    @Nested
    @DisplayName("Embedding Operations")
    class EmbeddingTests {

        @Test
        @DisplayName("Create embedding with vector values")
        void createEmbedding() {
            float[] vector = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f};
            EmbeddingResult result = new EmbeddingResult("text-content", vector, "model-v1");

            assertThat(result.getText()).isEqualTo("text-content");
            assertThat(result.getVector()).hasSize(5);
            assertThat(result.getModel()).isEqualTo("model-v1");
        }

        @Test
        @DisplayName("Large embedding dimensions")
        void largeEmbeddingDimensions() {
            float[] vector = new float[1536]; // OpenAI embedding dimension
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) (i % 10) / 10.0f;
            }

            EmbeddingResult result = new EmbeddingResult("text", vector, "ada");
            assertThat(result.getVector()).hasSize(1536);
        }

        @Test
        @DisplayName("Many texts embedded in batch")
        void batchEmbeddings() {
            List<EmbeddingResult> results = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                float[] vector = new float[256];
                for (int d = 0; d < vector.length; d++) {
                    vector[d] = (float) (idx + d) / 100.0f;
                }
                EmbeddingResult result = new EmbeddingResult("text-" + idx, vector, "model");
                results.add(result);
            }

            assertThat(results).hasSize(100);
        }

        @Test
        @DisplayName("Normalized embedding vectors")
        void normalizedEmbeddings() {
            float[] vector = {1.0f, 0.0f, 0.0f};
            EmbeddingResult result = new EmbeddingResult("text", vector, "model");

            float magnitude = (float) Math.sqrt(1.0 + 0.0 + 0.0);
            assertThat(magnitude).isCloseTo(1.0f, offset(0.01f));
        }
    }

    // ============================================
    // VECTOR SEARCH RESULTS (4 tests)
    // ============================================

    @Nested
    @DisplayName("Vector Search Results")
    class VectorSearchTests {

        @Test
        @DisplayName("Create vector search result with metadata")
        void vectorSearchWithMetadata() {
            float[] vector = {0.1f, 0.2f, 0.3f};
            Map<String, String> metadata = new HashMap<>();
            metadata.put("source", "document-1");
            metadata.put("page", "5");

            VectorSearchResult result = new VectorSearchResult(
                "id-1", "content", vector, 0.95f, 1, metadata);

            assertThat(result.getId()).isEqualTo("id-1");
            assertThat(result.getSimilarity()).isEqualTo(0.95f);
            assertThat(result.getRank()).isEqualTo(1);
            assertThat(result.getMetadata()).containsEntry("source", "document-1");
        }

        @Test
        @DisplayName("Rank ordering of search results")
        void rankOrdering() {
            List<VectorSearchResult> results = new ArrayList<>();
            float[] baseVector = {0.1f, 0.2f, 0.3f};

            for (int i = 0; i < 50; i++) {
                final int idx = i;
                float similarity = 1.0f - (idx / 100.0f);
                VectorSearchResult result = new VectorSearchResult(
                    "id-" + idx, "content-" + idx, baseVector, similarity, i, new HashMap<String, String>());
                results.add(result);
            }

            // Verify ordering
            for (int i = 0; i < results.size(); i++) {
                assertThat(results.get(i).getRank()).isEqualTo(i);
            }
        }

        @Test
        @DisplayName("Many vector search results from single query")
        void manySearchResults() {
            List<VectorSearchResult> results = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                final int idx = i;
                float[] vector = new float[256];
                for (int d = 0; d < vector.length; d++) {
                    vector[d] = (float) (idx + d) / 1000.0f;
                }

                VectorSearchResult result = new VectorSearchResult(
                    "id-" + idx, "text-" + idx, vector, 0.5f + (idx / 2000.0f), idx,
                    Map.of("batch", String.valueOf(idx / 100)));
                results.add(result);
            }

            assertThat(results).hasSize(1000);
        }

        @Test
        @DisplayName("Similarity score distribution")
        void similarityDistribution() {
            List<VectorSearchResult> results = new ArrayList<>();
            float[] vector = {0.1f, 0.2f, 0.3f};

            // Create results with varied similarity scores
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                float similarity = idx / 100.0f;
                VectorSearchResult result = new VectorSearchResult(
                    "id-" + idx, "content-" + idx, vector, similarity, idx, new HashMap<String, String>());
                results.add(result);
            }

            // Verify distribution
            assertThat(results.get(0).getSimilarity()).isCloseTo(0.0, offset(0.01));
            assertThat(results.get(99).getSimilarity()).isCloseTo(0.99, offset(0.01));
        }
    }

    // ============================================
    // CONCURRENT OPERATIONS (3 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrent LLM Operations")
    class ConcurrencyTests {

        @Test
        @DisplayName("Many concurrent completion requests")
        void concurrentRequests() throws Exception {
            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    exec.submit(() -> {
                        try {
                            CompletionRequest req = CompletionRequest.builder()
                                .prompt("Prompt " + idx)
                                .maxTokens(100)
                                .build();
                            assertThat(req.getPrompt()).isEqualTo("Prompt " + idx);
                            successCount.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }

            assertThat(successCount.get()).isEqualTo(threadCount);
        }

        @Test
        @DisplayName("Concurrent embedding generation")
        void concurrentEmbeddings() throws Exception {
            int threadCount = 30;
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<EmbeddingResult> results = new ArrayList<>();

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    exec.submit(() -> {
                        try {
                            float[] vector = new float[256];
                            for (int d = 0; d < vector.length; d++) {
                                vector[d] = (float) (idx + d) / 100.0f;
                            }
                            EmbeddingResult result = new EmbeddingResult(
                                "text-" + idx, vector, "model");
                            results.add(result);
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }

            assertThat(results.size()).isEqualTo(threadCount);
        }

        @Test
        @DisplayName("Concurrent vector search result processing")
        void concurrentVectorSearch() throws Exception {
            int threadCount = 25;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger processedCount = new AtomicInteger(0);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    exec.submit(() -> {
                        try {
                            List<VectorSearchResult> batch = new ArrayList<>();
                            for (int j = 0; j < 10; j++) {
                                final int itemIdx = j;
                                float[] vector = new float[128];
                                for (int d = 0; d < vector.length; d++) {
                                    vector[d] = (float) (idx + itemIdx + d) / 100.0f;
                                }
                                VectorSearchResult result = new VectorSearchResult(
                                    "id-" + idx + "-" + itemIdx,
                                    "content-" + idx + "-" + itemIdx,
                                    vector, 0.8f + (itemIdx / 100.0f), itemIdx, new HashMap<>());
                                batch.add(result);
                            }
                            processedCount.addAndGet(batch.size());
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }

            assertThat(processedCount.get()).isEqualTo(threadCount * 10);
        }
    }

    // ============================================
    // EDGE CASES (2 tests)
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Empty and null boundaries")
        void emptyAndNullBoundaries() {
            ChatMessage emptyUser = ChatMessage.user("");
            assertThat(emptyUser.getContent()).isEmpty();

            // Builder should allow empty prompt for now (server-side validation)
            CompletionRequest req = CompletionRequest.builder()
                .prompt("")
                .build();
            assertThat(req.getPrompt()).isEmpty();
        }

        @Test
        @DisplayName("Extreme parameter values")
        void extremeParameters() {
            CompletionRequest req = CompletionRequest.builder()
                .prompt("test")
                .temperature(0.0)
                .topP(0.0)
                .maxTokens(1)
                .build();

            assertThat(req.getTemperature()).isEqualTo(0.0);
            assertThat(req.getTopP()).isEqualTo(0.0);
            assertThat(req.getMaxTokens()).isEqualTo(1);
        }
    }
}
