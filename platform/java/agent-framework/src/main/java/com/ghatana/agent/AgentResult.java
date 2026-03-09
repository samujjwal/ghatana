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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Typed outcome of an agent's {@code process()} invocation.
 *
 * <p>Every agent invocation produces an {@code AgentResult<O>} that carries:
 * <ul>
 *   <li>The typed output payload</li>
 *   <li>A confidence score [0.0, 1.0]</li>
 *   <li>A machine-/human-readable status</li>
 *   <li>An explanation for audit and debugging</li>
 *   <li>Execution metrics (latency, model version, etc.)</li>
 *   <li>The wall-clock processing time</li>
 * </ul>
 *
 * <h2>Confidence Semantics</h2>
 * <ul>
 *   <li>{@code 1.0} — deterministic / certainty</li>
 *   <li>{@code 0.85+} — high-confidence model inference</li>
 *   <li>{@code 0.5–0.85} — moderate, may benefit from review</li>
 *   <li>{@code 0.0–0.5} — low-confidence, likely needs fallback</li>
 * </ul>
 *
 * @param <O> the output type
 *
 * @doc.type record
 * @doc.purpose Typed agent processing result
 * @doc.layer core
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
@Value
@Builder(toBuilder = true)
public class AgentResult<O> {

    /** The computed output — may be {@code null} for SKIPPED or FAILED results. */
    O output;

    /** Confidence score in [0.0, 1.0]. For deterministic agents this is always 1.0. */
    @Builder.Default
    double confidence = 1.0;

    /** Processing outcome status. */
    @Builder.Default
    AgentResultStatus status = AgentResultStatus.SUCCESS;

    /** Human- and machine-readable explanation of the outcome. */
    String explanation;

    /** Agent ID that produced this result. */
    String agentId;

    /** Execution metrics (latency breakdown, model version, cache hit, etc.). */
    @Builder.Default
    Map<String, Object> metrics = Map.of();

    /** Wall-clock processing time. */
    Duration processingTime;

    /** Timestamp when processing started. */
    @Builder.Default
    Instant startedAt = Instant.now();

    /** Optional trace ID for distributed tracing correlation. */
    String traceId;

    // ═══════════════════════════════════════════════════════════════════════════
    // Predicates
    // ═══════════════════════════════════════════════════════════════════════════

    /** Whether the result indicates successful processing. */
    public boolean isSuccess() {
        return status == AgentResultStatus.SUCCESS;
    }

    /** Whether the result indicates a failure. */
    public boolean isFailed() {
        return status == AgentResultStatus.FAILED || status == AgentResultStatus.TIMEOUT;
    }

    /** Whether the confidence meets or exceeds a given threshold. */
    public boolean meetsConfidence(double threshold) {
        return confidence >= threshold;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Factory Methods
    // ═══════════════════════════════════════════════════════════════════════════

    /** Creates a successful result with maximum confidence (deterministic). */
    public static <O> AgentResult<O> success(O output, String agentId, Duration time) {
        return AgentResult.<O>builder()
                .output(output)
                .confidence(1.0)
                .status(AgentResultStatus.SUCCESS)
                .agentId(agentId)
                .processingTime(time)
                .build();
    }

    /** Creates a successful result with the given confidence. */
    public static <O> AgentResult<O> successWithConfidence(
            O output, double confidence, String agentId, Duration time, String explanation) {
        AgentResultStatus status = confidence >= 0.5
                ? AgentResultStatus.SUCCESS
                : AgentResultStatus.LOW_CONFIDENCE;
        return AgentResult.<O>builder()
                .output(output)
                .confidence(confidence)
                .status(status)
                .agentId(agentId)
                .processingTime(time)
                .explanation(explanation)
                .build();
    }

    /** Creates a failed result. */
    public static <O> AgentResult<O> failure(Throwable error, String agentId, Duration time) {
        Objects.requireNonNull(error, "error must not be null");
        return AgentResult.<O>builder()
                .confidence(0.0)
                .status(AgentResultStatus.FAILED)
                .agentId(agentId)
                .explanation(error.getClass().getSimpleName() + ": " + error.getMessage())
                .processingTime(time)
                .build();
    }

    /** Creates a timeout result. */
    public static <O> AgentResult<O> timeout(String agentId, Duration elapsed) {
        return AgentResult.<O>builder()
                .confidence(0.0)
                .status(AgentResultStatus.TIMEOUT)
                .agentId(agentId)
                .explanation("Processing timed out after " + elapsed.toMillis() + "ms")
                .processingTime(elapsed)
                .build();
    }

    /** Creates a skipped result. */
    public static <O> AgentResult<O> skipped(String reason, String agentId) {
        return AgentResult.<O>builder()
                .confidence(0.0)
                .status(AgentResultStatus.SKIPPED)
                .agentId(agentId)
                .explanation(reason)
                .processingTime(Duration.ZERO)
                .build();
    }

    /** Creates a delegated result. */
    public static <O> AgentResult<O> delegated(String delegateAgentId, String agentId) {
        return AgentResult.<O>builder()
                .confidence(0.0)
                .status(AgentResultStatus.DELEGATED)
                .agentId(agentId)
                .explanation("Delegated to agent: " + delegateAgentId)
                .processingTime(Duration.ZERO)
                .metrics(Map.of("delegateAgentId", delegateAgentId))
                .build();
    }
}
