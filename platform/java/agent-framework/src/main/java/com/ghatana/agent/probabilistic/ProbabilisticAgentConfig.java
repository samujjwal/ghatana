/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.probabilistic;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentType;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * Configuration for {@link ProbabilisticAgent}.
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Configuration for probabilistic agent inference parameters
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
@Value
@lombok.experimental.NonFinal
@lombok.EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
public class ProbabilisticAgentConfig extends AgentConfig {

    @Builder.Default @NotNull ProbabilisticSubtype subtype = ProbabilisticSubtype.ML_MODEL;

    // ── Model ───────────────────────────────────────────────────────────────

    /** Model name / identifier. */
    @Nullable String modelName;

    /** Model version. */
    @Nullable String modelVersion;

    /** Model serving endpoint (e.g. grpc://model-server:50051). */
    @Nullable String modelEndpoint;

    /** Inference timeout. */
    @Builder.Default @NotNull Duration inferenceTimeout = Duration.ofMillis(100);

    /** Batch size for batch inference. */
    @Builder.Default int batchSize = 1;

    // ── Confidence ──────────────────────────────────────────────────────────

    /** Minimum confidence to consider the result valid. */
    @Builder.Default double confidenceThreshold = 0.85;

    /** Confidence calibration method. */
    @Builder.Default @NotNull ConfidenceCalibrator.Method calibrationMethod =
            ConfidenceCalibrator.Method.IDENTITY;

    // ── Fallback ────────────────────────────────────────────────────────────

    /** Ordered chain of fallback model endpoints. */
    @Singular @NotNull List<String> fallbackEndpoints;

    /** Whether to run in shadow mode (observe without acting). */
    @Builder.Default boolean shadowMode = false;
}
