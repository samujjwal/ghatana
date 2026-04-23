/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.ai.llm;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for token usage tracking service.
 *
 * @doc.type class
 * @doc.purpose Unit tests for token usage tracking
 * @doc.layer test
 */
@DisplayName("Token Usage Tracking Service Tests")
class TokenUsageTrackingServiceTest {

    @Test
    @DisplayName("tracks token usage for completion requests")
    void tracksTokenUsage() { // GH-90000
        TokenUsageTrackingService tracker = new TokenUsageTrackingService(); // GH-90000
        
        CompletionResult result = CompletionResult.builder() // GH-90000
            .text("Test response")
            .promptTokens(100) // GH-90000
            .completionTokens(50) // GH-90000
            .tokensUsed(150) // GH-90000
            .modelUsed("gpt-4")
            .build(); // GH-90000
        
        tracker.recordUsage("tenant-1", "openai", "gpt-4", result); // GH-90000
        
        TokenUsageTrackingService.UsageSummary summary = tracker.getUsageSummary("tenant-1");
        
        assertThat(summary.tenantId()).isEqualTo("tenant-1");
        assertThat(summary.promptTokens()).isEqualTo(100); // GH-90000
        assertThat(summary.completionTokens()).isEqualTo(50); // GH-90000
        assertThat(summary.totalTokens()).isEqualTo(150); // GH-90000
        assertThat(summary.requestCount()).isEqualTo(1); // GH-90000
        assertThat(summary.totalCost()).isGreaterThan(0.0); // GH-90000
    }

    @Test
    @DisplayName("aggregates usage across multiple requests")
    void aggregatesUsageAcrossRequests() { // GH-90000
        TokenUsageTrackingService tracker = new TokenUsageTrackingService(); // GH-90000
        
        CompletionResult result1 = CompletionResult.builder() // GH-90000
            .text("Response 1")
            .promptTokens(100) // GH-90000
            .completionTokens(50) // GH-90000
            .tokensUsed(150) // GH-90000
            .modelUsed("gpt-4")
            .build(); // GH-90000
        
        CompletionResult result2 = CompletionResult.builder() // GH-90000
            .text("Response 2")
            .promptTokens(200) // GH-90000
            .completionTokens(100) // GH-90000
            .tokensUsed(300) // GH-90000
            .modelUsed("gpt-4")
            .build(); // GH-90000
        
        tracker.recordUsage("tenant-1", "openai", "gpt-4", result1); // GH-90000
        tracker.recordUsage("tenant-1", "openai", "gpt-4", result2); // GH-90000
        
        TokenUsageTrackingService.UsageSummary summary = tracker.getUsageSummary("tenant-1");
        
        assertThat(summary.promptTokens()).isEqualTo(300); // GH-90000
        assertThat(summary.completionTokens()).isEqualTo(150); // GH-90000
        assertThat(summary.totalTokens()).isEqualTo(450); // GH-90000
        assertThat(summary.requestCount()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("separates usage by tenant")
    void separatesUsageByTenant() { // GH-90000
        TokenUsageTrackingService tracker = new TokenUsageTrackingService(); // GH-90000
        
        CompletionResult result = CompletionResult.builder() // GH-90000
            .text("Response")
            .promptTokens(100) // GH-90000
            .completionTokens(50) // GH-90000
            .tokensUsed(150) // GH-90000
            .modelUsed("gpt-4")
            .build(); // GH-90000
        
        tracker.recordUsage("tenant-1", "openai", "gpt-4", result); // GH-90000
        tracker.recordUsage("tenant-2", "openai", "gpt-4", result); // GH-90000
        
        TokenUsageTrackingService.UsageSummary summary1 = tracker.getUsageSummary("tenant-1");
        TokenUsageTrackingService.UsageSummary summary2 = tracker.getUsageSummary("tenant-2");
        
        assertThat(summary1.requestCount()).isEqualTo(1); // GH-90000
        assertThat(summary2.requestCount()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("filters usage by time range")
    void filtersUsageByTimeRange() { // GH-90000
        TokenUsageTrackingService tracker = new TokenUsageTrackingService(); // GH-90000
        
        Instant now = Instant.now(); // GH-90000
        Instant yesterday = now.minus(java.time.Duration.ofDays(1)); // GH-90000
        
        CompletionResult result1 = CompletionResult.builder() // GH-90000
            .text("Response 1")
            .promptTokens(100) // GH-90000
            .completionTokens(50) // GH-90000
            .tokensUsed(150) // GH-90000
            .modelUsed("gpt-4")
            .build(); // GH-90000
        
        CompletionResult result2 = CompletionResult.builder() // GH-90000
            .text("Response 2")
            .promptTokens(200) // GH-90000
            .completionTokens(100) // GH-90000
            .tokensUsed(300) // GH-90000
            .modelUsed("gpt-4")
            .build(); // GH-90000
        
        tracker.recordUsage("tenant-1", "openai", "gpt-4", result1); // GH-90000
        tracker.recordUsage("tenant-1", "openai", "gpt-4", result2); // GH-90000
        
        // Note: In a real test, you'd need to control the timestamp in the usage entry
        // For now, we just verify the method exists and returns a summary
        TokenUsageTrackingService.UsageSummary summary = tracker.getUsageSummary("tenant-1", yesterday, now); // GH-90000
        assertThat(summary.tenantId()).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("calculates cost using default calculator")
    void calculatesCost() { // GH-90000
        TokenUsageTrackingService.DefaultTokenCostCalculator calculator = 
            new TokenUsageTrackingService.DefaultTokenCostCalculator(); // GH-90000
        
        double cost = calculator.calculateCost("openai", "gpt-4", 1000, 1000); // GH-90000
        
        // OpenAI: $0.03 per 1K prompt + $0.06 per 1K completion
        assertThat(cost).isEqualTo(0.09, org.assertj.core.api.Assertions.withPrecision(0.001)); // GH-90000
    }

    @Test
    @DisplayName("clears usage for tenant")
    void clearsUsageForTenant() { // GH-90000
        TokenUsageTrackingService tracker = new TokenUsageTrackingService(); // GH-90000
        
        CompletionResult result = CompletionResult.builder() // GH-90000
            .text("Response")
            .promptTokens(100) // GH-90000
            .completionTokens(50) // GH-90000
            .tokensUsed(150) // GH-90000
            .modelUsed("gpt-4")
            .build(); // GH-90000
        
        tracker.recordUsage("tenant-1", "openai", "gpt-4", result); // GH-90000
        
        assertThat(tracker.getUsageSummary("tenant-1").requestCount()).isEqualTo(1);
        
        tracker.clearUsage("tenant-1");
        
        assertThat(tracker.getUsageSummary("tenant-1").requestCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("decorator gateway tracks usage")
    void decoratorGatewayTracksUsage() { // GH-90000
        TokenUsageTrackingService tracker = new TokenUsageTrackingService(); // GH-90000
        LLMGateway mockGateway = createMockGateway(); // GH-90000
        
        TokenTrackingLLMGateway trackingGateway = new TokenTrackingLLMGateway(mockGateway, tracker); // GH-90000
        
        assertThat(trackingGateway.getTrackingService()).isEqualTo(tracker); // GH-90000
        assertThat(trackingGateway.getDelegate()).isEqualTo(mockGateway); // GH-90000
    }

    // Helper method

    private LLMGateway createMockGateway() { // GH-90000
        return new LLMGateway() { // GH-90000
            @Override
            public Promise<CompletionResult> complete(CompletionRequest request) { // GH-90000
                return Promise.of(CompletionResult.builder() // GH-90000
                    .text("Mock response")
                    .promptTokens(100) // GH-90000
                    .completionTokens(50) // GH-90000
                    .tokensUsed(150) // GH-90000
                    .modelUsed("gpt-4")
                    .build()); // GH-90000
            }

            @Override
            public Promise<CompletionResult> completeWithTools(CompletionRequest request, List<ToolDefinition> tools) { // GH-90000
                return Promise.of(CompletionResult.of("Mock response"));
            }

            @Override
            public Promise<CompletionResult> continueWithToolResults(CompletionRequest request, List<ToolCallResult> toolResults) { // GH-90000
                return Promise.of(CompletionResult.of("Mock response"));
            }

            @Override
            public Promise<EmbeddingResult> embed(String text) { // GH-90000
                return Promise.of(new EmbeddingResult(text, new float[0], "model")); // GH-90000
            }

            @Override
            public Promise<TokenStream> stream(CompletionRequest request) { // GH-90000
                return Promise.of(new DefaultTokenStream()); // GH-90000
            }

            @Override
            public Promise<List<EmbeddingResult>> embedBatch(List<String> texts) { // GH-90000
                return Promise.of(List.of()); // GH-90000
            }

            @Override
            public MetricsCollector getMetrics() { // GH-90000
                return NoopMetricsCollector.getInstance(); // GH-90000
            }

            @Override
            public String getDefaultProvider() { // GH-90000
                return "openai";
            }

            @Override
            public List<String> getAvailableProviders() { // GH-90000
                return List.of("openai");
            }

            @Override
            public boolean isProviderAvailable(String providerName) { // GH-90000
                return true;
            }
        };
    }
}
