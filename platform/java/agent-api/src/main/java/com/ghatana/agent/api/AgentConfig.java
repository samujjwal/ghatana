/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.api;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Base configuration for all agent types.
 *
 * <p>Captures common configuration every agent needs regardless of its
 * computational type. Type-specific configurations extend this class
 * using {@code @SuperBuilder}.
 *
 * @doc.type class
 * @doc.purpose Base agent configuration
 * @doc.layer core
 * @doc.pattern Configuration Object
 */
@Value
@NonFinal
@SuperBuilder(toBuilder = true)
public class AgentConfig {

    // Identity
    /** Agent ID (must match descriptor). */
    String agentId;

    /** Agent type (must match descriptor). */
    AgentType type;

    /** Version (semantic versioning). */
    @Builder.Default
    String version = "1.0.0";

    /**
     * Reference used by {@code AgentLogicProvider} to resolve the concrete
     * implementation for this agent.
     *
     * <p>Format: {@code <provider-id>:<qualified-agent-id>}
     * (e.g., {@code yappc-java:agent.yappc.java-expert}).
     */
    String implementationRef;

    // SLA
    /** Maximum processing time per invocation before timeout. */
    @Builder.Default
    Duration timeout = Duration.ofSeconds(5);

    /** Confidence threshold — results below this are marked LOW_CONFIDENCE. */
    @Builder.Default
    double confidenceThreshold = 0.5;

    // Resilience
    /** Maximum retries on failure. */
    @Builder.Default
    int maxRetries = 0;

    /** Initial backoff delay between retries. */
    @Builder.Default
    Duration retryBackoff = Duration.ofMillis(100);

    /** Maximum backoff delay. */
    @Builder.Default
    Duration maxRetryBackoff = Duration.ofSeconds(5);

    /** Failure mode (fail-fast, retry, fallback, etc.). */
    @Builder.Default
    FailureMode failureMode = FailureMode.FAIL_FAST;

    /** Circuit-breaker failure threshold. */
    @Builder.Default
    int circuitBreakerThreshold = 5;

    /** Circuit-breaker reset timeout. */
    @Builder.Default
    Duration circuitBreakerReset = Duration.ofSeconds(30);

    // Observability
    /** Whether metrics collection is enabled. */
    @Builder.Default
    boolean metricsEnabled = true;

    /** Whether distributed tracing is enabled. */
    @Builder.Default
    boolean tracingEnabled = true;

    /** Tracing sample rate [0.0, 1.0]. */
    @Builder.Default
    double tracingSampleRate = 0.1;

    // Custom properties
    /** Arbitrary key-value properties for type-specific or extension use. */
    @Builder.Default
    Map<String, Object> properties = Map.of();

    /** Labels for filtering and discovery. */
    @Builder.Default
    Map<String, String> labels = Map.of();

    /** Required capabilities from the runtime environment. */
    @Builder.Default
    Set<String> requiredCapabilities = Set.of();
}
