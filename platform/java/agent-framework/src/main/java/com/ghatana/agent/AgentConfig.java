/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.agent;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Base configuration for all agent types.
 *
 * <p>Captures common configuration that every agent needs regardless
 * of its computational type. Type-specific configurations extend this
 * with additional fields using {@code @SuperBuilder}.
 *
 * <h2>Configuration Hierarchy</h2>
 * <pre>
 *   AgentConfig (common: id, type, SLA, resilience, observability)
 *       ├── DeterministicAgentConfig  (rules, thresholds, FSM)
 *       ├── ProbabilisticAgentConfig  (model, confidence, inference)
 *       ├── HybridAgentConfig         (routing strategy, sub-agent refs)
 *       ├── AdaptiveAgentConfig       (learning algo, feedback, exploration)
 *       ├── CompositeAgentConfig      (sub-agents, aggregation, weights)
 *       └── ReactiveAgentConfig       (triggers, cooldowns, windows)
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Base agent configuration
 * @doc.layer core
 * @doc.pattern Configuration Object
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
@Value
@lombok.experimental.NonFinal
@SuperBuilder(toBuilder = true)
public class AgentConfig {

    // ═══════════════════════════════════════════════════════════════════════════
    // Identity
    // ═══════════════════════════════════════════════════════════════════════════

    /** Agent ID (must match descriptor). */
    String agentId;

    /** Agent type (must match descriptor). */
    AgentType type;

    /** Version (semantic versioning). */
    @Builder.Default
    String version = "1.0.0";

    // ═══════════════════════════════════════════════════════════════════════════
    // SLA
    // ═══════════════════════════════════════════════════════════════════════════

    /** Maximum processing time per invocation before timeout. */
    @Builder.Default
    Duration timeout = Duration.ofSeconds(5);

    /** Confidence threshold — results below this are marked LOW_CONFIDENCE. */
    @Builder.Default
    double confidenceThreshold = 0.5;

    // ═══════════════════════════════════════════════════════════════════════════
    // Resilience
    // ═══════════════════════════════════════════════════════════════════════════

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

    /** Circuit-breaker failure threshold (consecutive failures to open circuit). */
    @Builder.Default
    int circuitBreakerThreshold = 5;

    /** Circuit-breaker reset timeout. */
    @Builder.Default
    Duration circuitBreakerReset = Duration.ofSeconds(30);

    // ═══════════════════════════════════════════════════════════════════════════
    // Observability
    // ═══════════════════════════════════════════════════════════════════════════

    /** Whether metrics collection is enabled. */
    @Builder.Default
    boolean metricsEnabled = true;

    /** Whether distributed tracing is enabled. */
    @Builder.Default
    boolean tracingEnabled = true;

    /** Tracing sample rate [0.0, 1.0]. */
    @Builder.Default
    double tracingSampleRate = 0.1;

    // ═══════════════════════════════════════════════════════════════════════════
    // Custom Properties
    // ═══════════════════════════════════════════════════════════════════════════

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
