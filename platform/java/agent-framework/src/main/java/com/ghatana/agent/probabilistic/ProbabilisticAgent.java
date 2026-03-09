/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.probabilistic;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.*;

/**
 * Probabilistic agent — produces non-deterministic output with a confidence score.
 *
 * <p>Wraps a {@link ModelInference} implementation and applies
 * {@link ConfidenceCalibrator} to produce calibrated confidence values.
 *
 * <p>Features:
 * <ul>
 *   <li>Model version management</li>
 *   <li>Confidence calibration (isotonic, Platt, temperature)</li>
 *   <li>Fallback chain on model timeout/error</li>
 *   <li>Shadow mode (observe without acting)</li>
 *   <li>Batch inference for throughput</li>
 * </ul>
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Agent using probabilistic models for uncertain decision making
 * @doc.layer platform
 * @doc.pattern Service
 * @doc.gaa.lifecycle reason
 */
public class ProbabilisticAgent
        extends AbstractTypedAgent<Map<String, Object>, Map<String, Object>> {

    private final AgentDescriptor descriptor;
    private volatile ProbabilisticAgentConfig probConfig;
    private volatile ConfidenceCalibrator calibrator;
    private volatile ModelInference primaryModel;
    private volatile List<ModelInference> fallbackModels = List.of();

    // ═══════════════════════════════════════════════════════════════════════════
    // Construction
    // ═══════════════════════════════════════════════════════════════════════════

    public ProbabilisticAgent(@NotNull String agentId) {
        this(agentId, null);
    }

    public ProbabilisticAgent(@NotNull String agentId, ModelInference primaryModel) {
        this.descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name(agentId)
                .description("Probabilistic agent — model-based inference")
                .version("1.0.0")
                .type(AgentType.PROBABILISTIC)
                .determinism(DeterminismGuarantee.NONE)
                .stateMutability(StateMutability.STATELESS)
                .failureMode(FailureMode.FALLBACK)
                .latencySla(Duration.ofMillis(100))
                .throughputTarget(10_000)
                .build();
        this.primaryModel = primaryModel;
    }

    public ProbabilisticAgent(@NotNull AgentDescriptor descriptor, ModelInference primaryModel) {
        this.descriptor = Objects.requireNonNull(descriptor);
        this.primaryModel = primaryModel;
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
        if (!(config instanceof ProbabilisticAgentConfig pc)) {
            return Promise.ofException(new IllegalArgumentException(
                    "Expected ProbabilisticAgentConfig but got " + config.getClass().getSimpleName()));
        }

        this.probConfig = pc;
        this.calibrator = ConfidenceCalibrator.builder()
                .method(pc.getCalibrationMethod())
                .build();

        log.info("Initialized probabilistic agent: {} model={} v{} threshold={}",
                descriptor.getAgentId(), pc.getModelName(), pc.getModelVersion(),
                pc.getConfidenceThreshold());

        return Promise.complete();
    }

    @Override
    @NotNull
    protected Promise<AgentResult<Map<String, Object>>> doProcess(
            @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {

        if (probConfig == null || primaryModel == null) {
            return Promise.of(AgentResult.failure(
                    new IllegalStateException("Agent not configured or no model set"),
                    descriptor.getAgentId(), Duration.ZERO));
        }

        return inferWithFallback(ctx, input, primaryModel, 0);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Inference with Fallback Chain
    // ═══════════════════════════════════════════════════════════════════════════

    private Promise<AgentResult<Map<String, Object>>> inferWithFallback(
            AgentContext ctx, Map<String, Object> input,
            ModelInference model, int fallbackIndex) {

        return model.infer(input)
                .map(result -> {
                    double calibrated = calibrator.calibrate(result.rawConfidence());

                    ctx.recordMetric("agent.model.latency.ms", result.latencyMs());
                    ctx.recordMetric("agent.model.rawConfidence", result.rawConfidence());
                    ctx.recordMetric("agent.model.calibratedConfidence", calibrated);

                    Map<String, Object> output = new LinkedHashMap<>(result.output());
                    output.put("_model.id", result.modelId());
                    output.put("_model.rawConfidence", result.rawConfidence());
                    output.put("_model.calibratedConfidence", calibrated);

                    // Shadow mode: observe but mark as degraded/skipped
                    if (probConfig.isShadowMode()) {
                        return AgentResult.<Map<String, Object>>builder()
                                .output(output)
                                .confidence(calibrated)
                                .status(AgentResultStatus.SKIPPED)
                                .explanation("Shadow mode — observation only")
                                .build();
                    }

                    // Check confidence threshold
                    AgentResultStatus status = calibrated >= probConfig.getConfidenceThreshold()
                            ? AgentResultStatus.SUCCESS
                            : AgentResultStatus.LOW_CONFIDENCE;

                    return AgentResult.<Map<String, Object>>builder()
                            .output(output)
                            .confidence(calibrated)
                            .status(status)
                            .explanation(String.format("Model %s: confidence=%.3f (%s)",
                                    result.modelId(), calibrated,
                                    status == AgentResultStatus.SUCCESS ? "above threshold" : "below threshold"))
                            .build();
                })
                .then(
                        result -> Promise.of(result),
                        ex -> {
                            // Try fallback
                            if (fallbackIndex < fallbackModels.size()) {
                                log.warn("Model {} failed, trying fallback #{}: {}",
                                        model.modelId(), fallbackIndex + 1, ex.getMessage());
                                return inferWithFallback(ctx, input,
                                        fallbackModels.get(fallbackIndex), fallbackIndex + 1);
                            }
                            // All models failed
                            log.error("All models exhausted for agent {}", descriptor.getAgentId(), ex);
                            return Promise.of(AgentResult.failure(ex,
                                    descriptor.getAgentId(), Duration.ZERO));
                        }
                );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Setters for Models (post-construction injection)
    // ═══════════════════════════════════════════════════════════════════════════

    public void setPrimaryModel(@NotNull ModelInference model) {
        this.primaryModel = Objects.requireNonNull(model);
    }

    public void setFallbackModels(@NotNull List<ModelInference> models) {
        this.fallbackModels = List.copyOf(models);
    }

    public ProbabilisticAgentConfig getProbabilisticConfig() { return probConfig; }
}
