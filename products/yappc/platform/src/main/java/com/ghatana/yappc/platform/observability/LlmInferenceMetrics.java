package com.ghatana.yappc.platform.observability;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.inject.annotation.Inject;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * AI quality telemetry for LLM inference calls within YAPPC AI workflows.
 *
 * <p>Records per-invocation counters and timers tagged by {@code workflow} and
 * {@code model}, enabling Grafana dashboards and Alertmanager rules to surface:
 * <ul>
 *   <li>Inference error rate per workflow (alerts on elevated failure rate)</li>
 *   <li>Inference latency p50/p95/p99 (alerting on tail-latency regression)</li>
 *   <li>Token consumption trends (cost observability)</li>
 * </ul>
 *
 * <p>Metric naming follows the OBS-001 convention:
 * {@code yappc.<domain>.<operation>.<outcome>}, lowercase, dot-separated.
 *
 * <p>Usage example inside an AI workflow service:
 * <pre>{@code
 * Timer.Sample sample = llmMetrics.startInference("scaffolding", "gpt-4o");
 * try {
 *     CompletionResult result = completionService.complete(request).getResult();
 *     llmMetrics.recordInferenceSuccess(sample, "scaffolding", "gpt-4o", result.getTokensUsed());
 * } catch (Exception e) {
 *     llmMetrics.recordInferenceFailure(sample, "scaffolding", "gpt-4o", "completion_error", e);
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Publish AI/LLM quality telemetry — latency, error rate, and token usage
 *              for every inference call made within YAPPC workflows.
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class LlmInferenceMetrics {

    private static final Logger log = LoggerFactory.getLogger(LlmInferenceMetrics.class);

    private static final String PREFIX = "yappc.ai.inference";

    // Counter metric names
    static final String METRIC_INVOKED    = PREFIX + ".invoked";
    static final String METRIC_SUCCEEDED  = PREFIX + ".succeeded";
    static final String METRIC_FAILED     = PREFIX + ".failed";

    // Histogram / timer metric names
    static final String METRIC_DURATION   = PREFIX + ".duration";
    static final String METRIC_TOKENS     = PREFIX + ".tokens.used";

    private final MetricsCollector metricsCollector;
    private final MeterRegistry meterRegistry;

    /**
     * @param metricsCollector platform-standard metrics abstraction
     * @param meterRegistry    Micrometer registry (for Timer and DistributionSummary operations)
     */
    @Inject
    public LlmInferenceMetrics(MetricsCollector metricsCollector, MeterRegistry meterRegistry) {
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector");
        this.meterRegistry    = Objects.requireNonNull(meterRegistry,    "meterRegistry");
        log.info("LlmInferenceMetrics initialized");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inference lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call immediately before issuing an LLM inference request.
     *
     * @param workflow slug identifying the calling AI workflow (e.g., "scaffolding",
     *                  "requirements-nlp", "canvas-layout")
     * @param model    model identifier as reported by the {@code CompletionService}
     *                  (e.g., "gpt-4o", "claude-3-opus"); use {@code "unknown"} when
     *                  the model is resolved lazily
     * @return an opaque {@link Timer.Sample} to be passed to
     *         {@link #recordInferenceSuccess} or {@link #recordInferenceFailure}
     */
    public Timer.Sample startInference(String workflow, String model) {
        metricsCollector.incrementCounter(
                METRIC_INVOKED,
                "workflow", workflow,
                "model",    model);
        return Timer.start(meterRegistry);
    }

    /**
     * Call after a successful LLM inference call.
     *
     * @param sample     the sample returned by {@link #startInference}
     * @param workflow   workflow that issued the call
     * @param model      model that served the request
     * @param tokensUsed total tokens consumed (prompt + completion); use {@code 0} when
     *                   the value is unavailable
     */
    public void recordInferenceSuccess(
            Timer.Sample sample,
            String workflow,
            String model,
            int tokensUsed) {
        metricsCollector.incrementCounter(
                METRIC_SUCCEEDED,
                "workflow", workflow,
                "model",    model);
        stopTimer(sample, workflow, model, "success");
        if (tokensUsed > 0) {
            recordTokens(workflow, model, tokensUsed);
        }
    }

    /**
     * Call after a failed LLM inference call.
     *
     * @param sample    the sample returned by {@link #startInference}
     * @param workflow  workflow that issued the call
     * @param model     model that was targeted
     * @param errorType short classification of the failure (e.g., "timeout",
     *                  "rate_limit", "completion_error", "parse_error")
     * @param cause     the exception; may be {@code null} for non-exception failures
     */
    public void recordInferenceFailure(
            Timer.Sample sample,
            String workflow,
            String model,
            String errorType,
            Exception cause) {
        metricsCollector.incrementCounter(
                METRIC_FAILED,
                "workflow",   workflow,
                "model",      model,
                "error_type", errorType);
        if (cause != null) {
            metricsCollector.recordError(METRIC_FAILED, cause,
                    java.util.Map.of("workflow", workflow, "model", model));
        }
        stopTimer(sample, workflow, model, "failure");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────────

    private void stopTimer(Timer.Sample sample, String workflow, String model, String status) {
        if (sample == null) {
            log.warn("stopTimer called with null sample for workflow={} model={}", workflow, model);
            return;
        }
        try {
            sample.stop(Timer.builder(METRIC_DURATION)
                    .tags(Tags.of(
                            "workflow", workflow,
                            "model",    model,
                            "status",   status))
                    .register(meterRegistry));
        } catch (Exception e) {
            log.warn("Failed to record inference duration for workflow={} model={}", workflow, model, e);
        }
    }

    private void recordTokens(String workflow, String model, int tokensUsed) {
        try {
            DistributionSummary.builder(METRIC_TOKENS)
                    .tags(Tags.of("workflow", workflow, "model", model))
                    .register(meterRegistry)
                    .record(tokensUsed);
        } catch (Exception e) {
            log.warn("Failed to record token usage for workflow={} model={}", workflow, model, e);
        }
    }
}
