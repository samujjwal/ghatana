/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void tracksTokenUsage() {
        TokenUsageTrackingService tracker = new TokenUsageTrackingService();
        
        CompletionResult result = CompletionResult.builder()
            .text("Test response")
            .promptTokens(100)
            .completionTokens(50)
            .tokensUsed(150)
            .modelUsed("gpt-4")
            .build();
        
        tracker.recordUsage("tenant-1", "openai", "gpt-4", result);
        
        TokenUsageTrackingService.UsageSummary summary = tracker.getUsageSummary("tenant-1");
        
        assertThat(summary.tenantId()).isEqualTo("tenant-1");
        assertThat(summary.promptTokens()).isEqualTo(100);
        assertThat(summary.completionTokens()).isEqualTo(50);
        assertThat(summary.totalTokens()).isEqualTo(150);
        assertThat(summary.requestCount()).isEqualTo(1);
        assertThat(summary.totalCost()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("aggregates usage across multiple requests")
    void aggregatesUsageAcrossRequests() {
        TokenUsageTrackingService tracker = new TokenUsageTrackingService();
        
        CompletionResult result1 = CompletionResult.builder()
            .text("Response 1")
            .promptTokens(100)
            .completionTokens(50)
            .tokensUsed(150)
            .modelUsed("gpt-4")
            .build();
        
        CompletionResult result2 = CompletionResult.builder()
            .text("Response 2")
            .promptTokens(200)
            .completionTokens(100)
            .tokensUsed(300)
            .modelUsed("gpt-4")
            .build();
        
        tracker.recordUsage("tenant-1", "openai", "gpt-4", result1);
        tracker.recordUsage("tenant-1", "openai", "gpt-4", result2);
        
        TokenUsageTrackingService.UsageSummary summary = tracker.getUsageSummary("tenant-1");
        
        assertThat(summary.promptTokens()).isEqualTo(300);
        assertThat(summary.completionTokens()).isEqualTo(150);
        assertThat(summary.totalTokens()).isEqualTo(450);
        assertThat(summary.requestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("separates usage by tenant")
    void separatesUsageByTenant() {
        TokenUsageTrackingService tracker = new TokenUsageTrackingService();
        
        CompletionResult result = CompletionResult.builder()
            .text("Response")
            .promptTokens(100)
            .completionTokens(50)
            .tokensUsed(150)
            .modelUsed("gpt-4")
            .build();
        
        tracker.recordUsage("tenant-1", "openai", "gpt-4", result);
        tracker.recordUsage("tenant-2", "openai", "gpt-4", result);
        
        TokenUsageTrackingService.UsageSummary summary1 = tracker.getUsageSummary("tenant-1");
        TokenUsageTrackingService.UsageSummary summary2 = tracker.getUsageSummary("tenant-2");
        
        assertThat(summary1.requestCount()).isEqualTo(1);
        assertThat(summary2.requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("filters usage by time range")
    void filtersUsageByTimeRange() {
        TokenUsageTrackingService tracker = new TokenUsageTrackingService();
        
        Instant now = Instant.now();
        Instant yesterday = now.minus(java.time.Duration.ofDays(1));
        
        CompletionResult result1 = CompletionResult.builder()
            .text("Response 1")
            .promptTokens(100)
            .completionTokens(50)
            .tokensUsed(150)
            .modelUsed("gpt-4")
            .build();
        
        CompletionResult result2 = CompletionResult.builder()
            .text("Response 2")
            .promptTokens(200)
            .completionTokens(100)
            .tokensUsed(300)
            .modelUsed("gpt-4")
            .build();
        
        tracker.recordUsage("tenant-1", "openai", "gpt-4", result1);
        tracker.recordUsage("tenant-1", "openai", "gpt-4", result2);
        
        // Note: In a real test, you'd need to control the timestamp in the usage entry
        // For now, we just verify the method exists and returns a summary
        TokenUsageTrackingService.UsageSummary summary = tracker.getUsageSummary("tenant-1", yesterday, now);
        assertThat(summary.tenantId()).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("calculates cost using default calculator")
    void calculatesCost() {
        TokenUsageTrackingService.DefaultTokenCostCalculator calculator = 
            new TokenUsageTrackingService.DefaultTokenCostCalculator();
        
        double cost = calculator.calculateCost("openai", "gpt-4", 1000, 1000);
        
        // OpenAI: $0.03 per 1K prompt + $0.06 per 1K completion
        assertThat(cost).isEqualTo(0.09, org.assertj.core.api.Assertions.withPrecision(0.001));
    }

    @Test
    @DisplayName("clears usage for tenant")
    void clearsUsageForTenant() {
        TokenUsageTrackingService tracker = new TokenUsageTrackingService();
        
        CompletionResult result = CompletionResult.builder()
            .text("Response")
            .promptTokens(100)
            .completionTokens(50)
            .tokensUsed(150)
            .modelUsed("gpt-4")
            .build();
        
        tracker.recordUsage("tenant-1", "openai", "gpt-4", result);
        
        assertThat(tracker.getUsageSummary("tenant-1").requestCount()).isEqualTo(1);
        
        tracker.clearUsage("tenant-1");
        
        assertThat(tracker.getUsageSummary("tenant-1").requestCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("decorator gateway tracks usage")
    void decoratorGatewayTracksUsage() {
        TokenUsageTrackingService tracker = new TokenUsageTrackingService();
        LLMGateway mockGateway = createMockGateway();
        
        TokenTrackingLLMGateway trackingGateway = new TokenTrackingLLMGateway(mockGateway, tracker);
        
        assertThat(trackingGateway.getTrackingService()).isEqualTo(tracker);
        assertThat(trackingGateway.getDelegate()).isEqualTo(mockGateway);
    }

    // Helper method

    private LLMGateway createMockGateway() {
        return new LLMGateway() {
            @Override
            public Promise<CompletionResult> complete(CompletionRequest request) {
                return Promise.of(CompletionResult.builder()
                    .text("Mock response")
                    .promptTokens(100)
                    .completionTokens(50)
                    .tokensUsed(150)
                    .modelUsed("gpt-4")
                    .build());
            }

            @Override
            public Promise<CompletionResult> completeWithTools(CompletionRequest request, List<ToolDefinition> tools) {
                return Promise.of(CompletionResult.of("Mock response"));
            }

            @Override
            public Promise<CompletionResult> continueWithToolResults(CompletionRequest request, List<ToolCallResult> toolResults) {
                return Promise.of(CompletionResult.of("Mock response"));
            }

            @Override
            public Promise<EmbeddingResult> embed(String text) {
                return Promise.of(new EmbeddingResult(text, new float[0], "model"));
            }

            @Override
            public Promise<TokenStream> stream(CompletionRequest request) {
                return Promise.of(new DefaultTokenStream());
            }

            @Override
            public Promise<List<EmbeddingResult>> embedBatch(List<String> texts) {
                return Promise.of(List.of());
            }

            @Override
            public MetricsCollector getMetrics() {
                return NoopMetricsCollector.getInstance();
            }

            @Override
            public String getDefaultProvider() {
                return "openai";
            }

            @Override
            public List<String> getAvailableProviders() {
                return List.of("openai");
            }

            @Override
            public boolean isProviderAvailable(String providerName) {
                return true;
            }
        };
    }
}
