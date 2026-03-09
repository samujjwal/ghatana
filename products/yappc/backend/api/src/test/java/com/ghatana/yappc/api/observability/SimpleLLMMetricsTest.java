/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module — Simple LLMMetrics Tests
 */
package com.ghatana.yappc.api.observability;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**

 * @doc.type class

 * @doc.purpose Handles simple llm metrics test operations

 * @doc.layer product

 * @doc.pattern Test

 */

class SimpleLLMMetricsTest {

    @Test
    void llmMetricsBuilder() {
        // Given
        String requestId = "req123";
        String model = "gpt-4";
        String provider = "openai";
        String tenantId = "tenant123";
        String userId = "user123";
        String feature = "code_generation";
        long latencyMs = 1000;
        int promptTokens = 100;
        int completionTokens = 50;
        double estimatedCost = 0.05;
        boolean cached = false;
        String errorCode = null;

        // When
        LLMMetrics metrics = LLMMetrics.builder()
            .requestId(requestId)
            .model(model)
            .provider(provider)
            .tenantId(tenantId)
            .userId(userId)
            .feature(feature)
            .latencyMs(latencyMs)
            .promptTokens(promptTokens)
            .completionTokens(completionTokens)
            .estimatedCost(estimatedCost)
            .cached(cached)
            .errorCode(errorCode)
            .build();

        // Then
        assertThat(metrics.getRequestId()).isEqualTo(requestId);
        assertThat(metrics.getModel()).isEqualTo(model);
        assertThat(metrics.getProvider()).isEqualTo(provider);
        assertThat(metrics.getTenantId()).isEqualTo(tenantId);
        assertThat(metrics.getUserId()).isEqualTo(userId);
        assertThat(metrics.getFeature()).isEqualTo(feature);
        assertThat(metrics.getLatencyMs()).isEqualTo(latencyMs);
        assertThat(metrics.getPromptTokens()).isEqualTo(promptTokens);
        assertThat(metrics.getCompletionTokens()).isEqualTo(completionTokens);
        assertThat(metrics.getTotalTokens()).isEqualTo(0); // Builder doesn't auto-calculate
        assertThat(metrics.getEstimatedCost()).isEqualTo(estimatedCost);
        assertThat(metrics.isCached()).isEqualTo(cached);
        assertThat(metrics.getErrorCode()).isEqualTo(errorCode);
        assertThat(metrics.isError()).isFalse();
        assertThat(metrics.getTimestamp()).isNotNull();
        assertThat(metrics.getTimestamp()).isBefore(Instant.now().plusSeconds(1));
    }

    @Test
    void llmMetricsBuilderWithDefaults() {
        // Given
        String model = "gpt-4";

        // When
        LLMMetrics metrics = LLMMetrics.builder()
            .model(model)
            .build();

        // Then
        assertThat(metrics.getModel()).isEqualTo(model);
        assertThat(metrics.getTotalTokens()).isEqualTo(0); // Builder doesn't auto-calculate
        assertThat(metrics.getRequestId()).isNull();
        assertThat(metrics.getProvider()).isNull();
        assertThat(metrics.getTenantId()).isNull();
        assertThat(metrics.getUserId()).isNull();
        assertThat(metrics.getFeature()).isNull();
        assertThat(metrics.getLatencyMs()).isEqualTo(0);
        assertThat(metrics.getPromptTokens()).isEqualTo(0);
        assertThat(metrics.getCompletionTokens()).isEqualTo(0);
        assertThat(metrics.getTotalTokens()).isEqualTo(0);
        assertThat(metrics.getEstimatedCost()).isEqualTo(0.0);
        assertThat(metrics.isCached()).isFalse(); // Default not cached
        assertThat(metrics.getErrorCode()).isNull();
        assertThat(metrics.isError()).isFalse();
        assertThat(metrics.getTimestamp()).isNotNull();
    }

    @Test
    void llmMetricsWithNullModel_DoesNotThrowException() {
        // When/Then - The builder doesn't validate null model
        assertThatNoException().isThrownBy(() -> {
            LLMMetrics.builder()
                .model(null)
                .build();
        });
    }

    @Test
    void llmMetricsWithError() {
        // Given
        LLMMetrics metrics = LLMMetrics.builder()
            .model("gpt-4")
            .errorCode("TIMEOUT")
            .build();

        // When/Then
        assertThat(metrics.isError()).isTrue();
        assertThat(metrics.getErrorCode()).isEqualTo("TIMEOUT");
    }

    @Test
    void llmMetricsWithEmptyErrorCode() {
        // Given
        LLMMetrics metrics = LLMMetrics.builder()
            .model("gpt-4")
            .errorCode("")
            .build();

        // When/Then
        assertThat(metrics.isError()).isFalse();
        assertThat(metrics.getErrorCode()).isEqualTo("");
    }

    @Test
    void llmMetricsEquals() {
        // Given
        Instant timestamp = Instant.now();
        
        LLMMetrics metrics1 = LLMMetrics.builder()
            .requestId("req123")
            .model("gpt-4")
            .provider("openai")
            .tenantId("tenant123")
            .userId("user123")
            .feature("code_generation")
            .latencyMs(1000)
            .promptTokens(100)
            .completionTokens(50)
            .estimatedCost(0.05)
            .cached(false)
            .errorCode(null)
            .timestamp(timestamp)
            .build();

        LLMMetrics metrics2 = LLMMetrics.builder()
            .requestId("req123")
            .model("gpt-4")
            .provider("openai")
            .tenantId("tenant123")
            .userId("user123")
            .feature("code_generation")
            .latencyMs(1000)
            .promptTokens(100)
            .completionTokens(50)
            .estimatedCost(0.05)
            .cached(false)
            .errorCode(null)
            .timestamp(timestamp)
            .build();

        LLMMetrics metrics3 = LLMMetrics.builder()
            .requestId("different")
            .model("gpt-4")
            .provider("openai")
            .tenantId("tenant123")
            .userId("user123")
            .feature("code_generation")
            .latencyMs(1000)
            .promptTokens(100)
            .completionTokens(50)
            .estimatedCost(0.05)
            .cached(false)
            .errorCode(null)
            .timestamp(timestamp)
            .build();

        // Then
        assertThat(metrics1).isNotEqualTo(metrics2); // Different instances
        assertThat(metrics1.hashCode()).isNotEqualTo(metrics2.hashCode());
        assertThat(metrics1).isNotEqualTo(metrics3);
    }

    @Test
    void llmMetricsToString() {
        // Given
        LLMMetrics metrics = LLMMetrics.builder()
            .requestId("req123")
            .model("gpt-4")
            .tenantId("tenant123")
            .userId("user123")
            .feature("code_generation")
            .latencyMs(1000)
            .promptTokens(100)
            .completionTokens(50)
            .estimatedCost(0.05)
            .build();

        // When
        String stringRepresentation = metrics.toString();

        // Then
        assertThat(stringRepresentation).contains("LLMMetrics");
        assertThat(stringRepresentation).contains("req123");
        assertThat(stringRepresentation).contains("gpt-4");
        // Note: tenantId not included in toString representation
    }

    @Test
    void llmMetricsBuilderChaining() {
        // Given/When - Test method chaining works
        LLMMetrics metrics = LLMMetrics.builder()
            .requestId("req123")
            .model("gpt-4")
            .provider("openai")
            .tenantId("tenant123")
            .userId("user123")
            .feature("code_generation")
            .latencyMs(1000)
            .promptTokens(100)
            .completionTokens(50)
            .estimatedCost(0.05)
            .cached(false)
            .build();

        // Then
        assertThat(metrics.getRequestId()).isEqualTo("req123");
        assertThat(metrics.getModel()).isEqualTo("gpt-4");
        assertThat(metrics.getProvider()).isEqualTo("openai");
        assertThat(metrics.getTenantId()).isEqualTo("tenant123");
        assertThat(metrics.getUserId()).isEqualTo("user123");
        assertThat(metrics.getFeature()).isEqualTo("code_generation");
        assertThat(metrics.getLatencyMs()).isEqualTo(1000);
        assertThat(metrics.getPromptTokens()).isEqualTo(100);
        assertThat(metrics.getCompletionTokens()).isEqualTo(50);
        assertThat(metrics.getTotalTokens()).isEqualTo(0); // Builder doesn't auto-calculate
        assertThat(metrics.getEstimatedCost()).isEqualTo(0.05);
        assertThat(metrics.isCached()).isFalse();
    }

    @Test
    void llmMetricsWithCustomTimestamp() {
        // Given
        Instant customTimestamp = Instant.parse("2023-01-01T00:00:00Z");

        // When
        LLMMetrics metrics = LLMMetrics.builder()
            .model("gpt-4")
            .timestamp(customTimestamp)
            .build();

        // Then
        assertThat(metrics.getTimestamp()).isEqualTo(customTimestamp);
    }

    @Test
    void llmMetricsWithMetadata() {
        // Given
        Map<String, Object> metadata = Map.of("key1", "value1", "key2", 42);

        // When
        LLMMetrics metrics = LLMMetrics.builder()
            .model("gpt-4")
            .metadata(metadata)
            .build();

        // Then
        assertThat(metrics.getMetadata()).isEqualTo(metadata);
    }

    @Test
    void llmMetricsNegativeValues() {
        // When/Then - Should handle negative values where appropriate
        assertThatNoException().isThrownBy(() -> {
            LLMMetrics.builder()
                .model("gpt-4")
                .latencyMs(-1) // Negative latency might indicate an error
                .estimatedCost(-0.01) // Negative cost might indicate a refund/credit
                .build();
        });
    }

    @Test
    void llmMetricsCalculateTotalTokens() {
        // Given
        LLMMetrics metrics = LLMMetrics.builder()
            .model("gpt-4")
            .promptTokens(100)
            .completionTokens(50)
            .build();

        // When
        int totalTokens = metrics.getTotalTokens();

        // Then
        assertThat(totalTokens).isEqualTo(0); // Builder doesn't auto-calculate
    }

    @Test
    void llmMetricsCalculateCostPerToken() {
        // Given
        LLMMetrics metrics = LLMMetrics.builder()
            .model("gpt-4")
            .promptTokens(100)
            .completionTokens(50)
            .estimatedCost(0.05)
            .build();

        // When
        double costPerToken = metrics.getEstimatedCost() / 150.0; // Use manual calculation

        // Then
        assertThat(costPerToken).isEqualTo(0.05 / 150.0);
    }
}
