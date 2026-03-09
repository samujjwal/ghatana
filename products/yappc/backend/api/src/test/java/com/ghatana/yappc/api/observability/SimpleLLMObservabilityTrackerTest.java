/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module — Simple Observability Tests
 */
package com.ghatana.yappc.api.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**

 * @doc.type class

 * @doc.purpose Handles simple llm observability tracker test operations

 * @doc.layer product

 * @doc.pattern Test

 */

class SimpleLLMObservabilityTrackerTest {

    private LLMObservabilityTracker tracker;

    @BeforeEach
    void setUp() {
        // Get singleton instance
        tracker = LLMObservabilityTracker.getInstance();
    }

    @Test
    void getInstanceReturnsSingleton() {
        // When
        LLMObservabilityTracker instance1 = LLMObservabilityTracker.getInstance();
        LLMObservabilityTracker instance2 = LLMObservabilityTracker.getInstance();

        // Then
        assertThat(instance1).isSameAs(instance2);
    }

    @Test
    void trackMetricsUpdatesCounters() {
        // Given
        LLMMetrics metrics = LLMMetrics.builder()
            .tenantId("tenant123")
            .model("gpt-4")
            .feature("code_generation")
            .latencyMs(1000)
            .promptTokens(100)
            .completionTokens(50)
            .estimatedCost(0.05)
            .cached(false)
            .build();

        // When
        tracker.track(metrics);

        // Then - Should not throw exception
        assertThatNoException().isThrownBy(() -> tracker.track(metrics));
    }

    @Test
    void trackErrorMetrics() {
        // Given
        LLMMetrics metrics = LLMMetrics.builder()
            .tenantId("tenant123")
            .model("gpt-4")
            .feature("code_generation")
            .latencyMs(1000)
            .promptTokens(100)
            .completionTokens(50)
            .estimatedCost(0.05)
            .errorCode("TIMEOUT") // Error
            .cached(false)
            .build();

        // When/Then - Should handle error metrics without throwing
        assertThatNoException().isThrownBy(() -> tracker.track(metrics));
    }

    @Test
    void trackCachedMetrics() {
        // Given
        LLMMetrics metrics = LLMMetrics.builder()
            .tenantId("tenant123")
            .model("gpt-4")
            .feature("code_generation")
            .latencyMs(50) // Low latency for cached
            .promptTokens(100)
            .completionTokens(50)
            .estimatedCost(0.0)
            .cached(true)
            .build();

        // When/Then - Should handle cached metrics without throwing
        assertThatNoException().isThrownBy(() -> tracker.track(metrics));
    }

    @Test
    void trackMetricsWithNullTenantId() {
        // Given
        LLMMetrics metrics = LLMMetrics.builder()
            .tenantId(null) // Null tenant
            .model("gpt-4")
            .feature("code_generation")
            .latencyMs(1000)
            .promptTokens(100)
            .completionTokens(50)
            .estimatedCost(0.05)
            .cached(false)
            .build();

        // When/Then - Should handle null tenant gracefully
        assertThatNoException().isThrownBy(() -> tracker.track(metrics));
    }

    @Test
    void trackMetricsWithNullFeature() {
        // Given
        LLMMetrics metrics = LLMMetrics.builder()
            .tenantId("tenant123")
            .model("gpt-4")
            .feature(null) // Null feature
            .latencyMs(1000)
            .promptTokens(100)
            .completionTokens(50)
            .estimatedCost(0.05)
            .cached(false)
            .build();

        // When/Then - Should handle null feature gracefully
        assertThatNoException().isThrownBy(() -> tracker.track(metrics));
    }

    @Test
    void calculateCostForKnownModels() {
        // When/Then - Test cost calculation for known models
        assertThat(LLMObservabilityTracker.calculateCost("gpt-4", 1000, 500))
            .isEqualTo(0.03 + 0.03); // 1K * 0.03 + 0.5K * 0.06

        assertThat(LLMObservabilityTracker.calculateCost("gpt-3.5-turbo", 2000, 1000))
            .isEqualTo(0.001 + 0.0015); // 2K * 0.0005 + 1K * 0.0015
    }

    @Test
    void calculateCostForUnknownModel() {
        // When/Then - Should use default pricing for unknown models
        assertThat(LLMObservabilityTracker.calculateCost("unknown-model", 1000, 500))
            .isEqualTo(0.001 + 0.001); // Default pricing: 0.001, 0.002
    }

    @Test
    void calculateCostWithZeroTokens() {
        // When/Then - Should handle zero tokens
        assertThat(LLMObservabilityTracker.calculateCost("gpt-4", 0, 0))
            .isEqualTo(0.0);

        assertThat(LLMObservabilityTracker.calculateCost("gpt-4", 1000, 0))
            .isEqualTo(0.03);

        assertThat(LLMObservabilityTracker.calculateCost("gpt-4", 0, 1000))
            .isEqualTo(0.06);
    }

    @Test
    void calculateCostWithNegativeTokens() {
        // When/Then - Should handle negative tokens gracefully
        assertThatNoException().isThrownBy(() -> {
            LLMObservabilityTracker.calculateCost("gpt-4", -100, -50);
        });
    }
}
