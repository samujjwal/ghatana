/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.hybrid;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;

/**
 * Hybrid agent — combines deterministic and probabilistic sub-agents with
 * configurable routing strategies and fallback behaviour.
 *
 * <p>Routing strategies:
 * <ul>
 *   <li><b>DETERMINISTIC_FIRST</b> — fast-path if rules match; escalate to ML otherwise</li>
 *   <li><b>PROBABILISTIC_FIRST</b> — ML first; fallback to rules on error/timeout</li>
 *   <li><b>PARALLEL</b> — run both; merge results (deterministic takes precedence on conflict)</li>
 * </ul>
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Agent combining deterministic rules with probabilistic reasoning
 * @doc.layer platform
 * @doc.pattern Service
 * @doc.gaa.lifecycle reason
 */
public class HybridAgent
        extends AbstractTypedAgent<Map<String, Object>, Map<String, Object>> {

    private final AgentDescriptor descriptor;
    private volatile HybridAgentConfig hybridConfig;

    /**
     * Pluggable sub-agents. Set via constructor or setter.
     */
    private volatile TypedAgent<Map<String, Object>, Map<String, Object>> deterministicAgent;
    private volatile TypedAgent<Map<String, Object>, Map<String, Object>> probabilisticAgent;

    // ═══════════════════════════════════════════════════════════════════════════
    // Construction
    // ═══════════════════════════════════════════════════════════════════════════

    public HybridAgent(@NotNull String agentId) {
        this.descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name(agentId)
                .description("Hybrid agent — deterministic + probabilistic routing")
                .version("1.0.0")
                .type(AgentType.HYBRID)
                .determinism(DeterminismGuarantee.CONFIG_SCOPED)
                .stateMutability(StateMutability.STATELESS)
                .failureMode(FailureMode.FALLBACK)
                .latencySla(Duration.ofMillis(50))
                .throughputTarget(50_000)
                .build();
    }

    public HybridAgent(@NotNull AgentDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TypedAgent Contract
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public AgentDescriptor descriptor() { return descriptor; }

    @Override
    @NotNull
    protected Promise<Void> doInitialize(@NotNull AgentConfig config) {
        if (!(config instanceof HybridAgentConfig hc)) {
            return Promise.ofException(new IllegalArgumentException(
                    "Expected HybridAgentConfig but got " + config.getClass().getSimpleName()));
        }
        this.hybridConfig = hc;

        if (deterministicAgent == null && probabilisticAgent == null) {
            return Promise.ofException(new IllegalStateException(
                    "At least one sub-agent (deterministic or probabilistic) must be set"));
        }

        log.info("Initialized hybrid agent: {} strategy={}", descriptor.getAgentId(), hc.getStrategy());
        return Promise.complete();
    }

    @Override
    @NotNull
    protected Promise<AgentResult<Map<String, Object>>> doProcess(
            @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {

        if (hybridConfig == null) {
            return Promise.of(AgentResult.failure(
                    new IllegalStateException("Not configured"), descriptor.getAgentId(), Duration.ZERO));
        }

        return switch (hybridConfig.getStrategy()) {
            case DETERMINISTIC_FIRST -> deterministicFirst(ctx, input);
            case PROBABILISTIC_FIRST -> probabilisticFirst(ctx, input);
            case PARALLEL -> parallel(ctx, input);
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Routing Strategies
    // ═══════════════════════════════════════════════════════════════════════════

    private Promise<AgentResult<Map<String, Object>>> deterministicFirst(
            AgentContext ctx, Map<String, Object> input) {

        if (deterministicAgent == null) {
            return escalateToProbabilistic(ctx, input, "No deterministic agent available");
        }

        return deterministicAgent.process(ctx, input)
                .then(detResult -> {
                    ctx.recordMetric("agent.hybrid.fastPath", 1);

                    // If deterministic returned a meaningful result, use it
                    if (detResult.isSuccess() && detResult.getConfidence() >=
                            hybridConfig.getEscalationConfidenceThreshold()) {
                        Map<String, Object> output = enrichOutput(detResult, "deterministic");
                        return Promise.of(AgentResult.<Map<String, Object>>builder()
                                .output(output)
                                .confidence(detResult.getConfidence())
                                .status(detResult.getStatus())
                                .explanation("Fast-path: deterministic match")
                                .build());
                    }

                    // Escalate to probabilistic
                    ctx.recordMetric("agent.hybrid.escalation", 1);
                    return escalateToProbabilistic(ctx, input, "Deterministic: " +
                            (detResult.isSuccess() ? "low confidence" : detResult.getStatus()));
                }, ex -> {
                    log.warn("Deterministic agent error, escalating: {}", ex.getMessage());
                    return escalateToProbabilistic(ctx, input, "Deterministic error: " + ex.getMessage());
                });
    }

    private Promise<AgentResult<Map<String, Object>>> probabilisticFirst(
            AgentContext ctx, Map<String, Object> input) {

        if (probabilisticAgent == null) {
            return fallbackToDeterministic(ctx, input, "No probabilistic agent available");
        }

        return probabilisticAgent.process(ctx, input)
                .then(probResult -> {
                    if (probResult.isSuccess()) {
                        Map<String, Object> output = enrichOutput(probResult, "probabilistic");
                        return Promise.of(AgentResult.<Map<String, Object>>builder()
                                .output(output)
                                .confidence(probResult.getConfidence())
                                .status(probResult.getStatus())
                                .explanation("Primary: probabilistic match")
                                .build());
                    }
                    return fallbackToDeterministic(ctx, input,
                            "Probabilistic: " + probResult.getStatus());
                }, ex -> {
                    log.warn("Probabilistic agent error, falling back: {}", ex.getMessage());
                    return fallbackToDeterministic(ctx, input,
                            "Probabilistic error: " + ex.getMessage());
                });
    }

    private Promise<AgentResult<Map<String, Object>>> parallel(
            AgentContext ctx, Map<String, Object> input) {

        Promise<AgentResult<Map<String, Object>>> detPromise =
                deterministicAgent != null
                        ? deterministicAgent.process(ctx, input)
                        : Promise.of(AgentResult.<Map<String, Object>>builder()
                                .output(Map.of()).confidence(0).status(AgentResultStatus.SKIPPED).build());

        Promise<AgentResult<Map<String, Object>>> probPromise =
                probabilisticAgent != null
                        ? probabilisticAgent.process(ctx, input)
                        : Promise.of(AgentResult.<Map<String, Object>>builder()
                                .output(Map.of()).confidence(0).status(AgentResultStatus.SKIPPED).build());

        return Promises.toList(List.of(detPromise, probPromise))
                .map(results -> {
                    AgentResult<Map<String, Object>> detResult = results.get(0);
                    AgentResult<Map<String, Object>> probResult = results.get(1);

                    // Merge: start with probabilistic, overlay deterministic (higher certainty wins)
                    Map<String, Object> merged = new LinkedHashMap<>();
                    if (probResult.getOutput() != null) merged.putAll(probResult.getOutput());
                    if (detResult.isSuccess() && detResult.getOutput() != null) {
                        merged.putAll(detResult.getOutput());
                    }
                    merged.put("_hybrid.detStatus", detResult.getStatus().name());
                    merged.put("_hybrid.probStatus", probResult.getStatus().name());
                    merged.put("_hybrid.strategy", "PARALLEL");

                    double confidence = detResult.isSuccess()
                            ? Math.max(detResult.getConfidence(), probResult.getConfidence())
                            : probResult.getConfidence();

                    ctx.recordMetric("agent.hybrid.parallel", 1);

                    return AgentResult.<Map<String, Object>>builder()
                            .output(merged)
                            .confidence(confidence)
                            .status(detResult.isSuccess() || probResult.isSuccess()
                                    ? AgentResultStatus.SUCCESS : AgentResultStatus.DEGRADED)
                            .explanation("Parallel: merged deterministic + probabilistic")
                            .build();
                });
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Promise<AgentResult<Map<String, Object>>> escalateToProbabilistic(
            AgentContext ctx, Map<String, Object> input, String reason) {
        if (probabilisticAgent == null) {
            return Promise.of(AgentResult.<Map<String, Object>>builder()
                    .output(Map.of())
                    .confidence(0)
                    .status(AgentResultStatus.DEGRADED)
                    .explanation("Cannot escalate — no probabilistic agent. " + reason)
                    .build());
        }
        return probabilisticAgent.process(ctx, input)
                .map(r -> {
                    Map<String, Object> output = enrichOutput(r, "probabilistic");
                    output.put("_hybrid.escalationReason", reason);
                    return r.toBuilder().output(output)
                            .explanation("Escalated to probabilistic: " + reason).build();
                });
    }

    private Promise<AgentResult<Map<String, Object>>> fallbackToDeterministic(
            AgentContext ctx, Map<String, Object> input, String reason) {
        if (deterministicAgent == null) {
            return Promise.of(AgentResult.<Map<String, Object>>builder()
                    .output(Map.of())
                    .confidence(0)
                    .status(AgentResultStatus.DEGRADED)
                    .explanation("Cannot fallback — no deterministic agent. " + reason)
                    .build());
        }
        return deterministicAgent.process(ctx, input)
                .map(r -> {
                    Map<String, Object> output = enrichOutput(r, "deterministic");
                    output.put("_hybrid.fallbackReason", reason);
                    return r.toBuilder().output(output)
                            .explanation("Fallback to deterministic: " + reason).build();
                });
    }

    private Map<String, Object> enrichOutput(AgentResult<Map<String, Object>> result, String source) {
        Map<String, Object> output = result.getOutput() != null
                ? new LinkedHashMap<>(result.getOutput()) : new LinkedHashMap<>();
        output.put("_hybrid.source", source);
        output.put("_hybrid.strategy", hybridConfig.getStrategy().name());
        return output;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Sub-agent Injection
    // ═══════════════════════════════════════════════════════════════════════════

    public void setDeterministicAgent(
            @Nullable TypedAgent<Map<String, Object>, Map<String, Object>> agent) {
        this.deterministicAgent = agent;
    }

    public void setProbabilisticAgent(
            @Nullable TypedAgent<Map<String, Object>, Map<String, Object>> agent) {
        this.probabilisticAgent = agent;
    }
}
