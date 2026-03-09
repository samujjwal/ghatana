/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.probabilistic;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Abstraction for model inference — ML, Bayesian, statistical, or LLM.
 *
 * <p>Implementations may call remote gRPC endpoints, local ONNX runtimes,
 * or in-process statistical calculations.
 *
 * @since 2.0.0
 *
 * @doc.type interface
 * @doc.purpose SPI for probabilistic model inference and prediction
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface ModelInference {

    /**
     * Runs inference on the given input.
     *
     * @param input   the event map
     * @return a Promise of the inference result (confidence + output)
     */
    @NotNull
    Promise<InferenceResult> infer(@NotNull Map<String, Object> input);

    /**
     * Returns the model identifier (name + version).
     */
    @NotNull
    String modelId();

    /**
     * Returns whether the model is currently available.
     */
    @NotNull
    default Promise<Boolean> isAvailable() { return Promise.of(true); }

    /**
     * Result of a model inference call.
     */
    record InferenceResult(
            /** Model output as key-value pairs. */
            @NotNull Map<String, Object> output,
            /** Raw confidence score from the model (before calibration). */
            double rawConfidence,
            /** Model identifier. */
            @NotNull String modelId,
            /** Inference latency in milliseconds. */
            long latencyMs
    ) {}
}
