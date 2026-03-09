package com.ghatana.eventprocessing.observability;

import lombok.extern.slf4j.Slf4j;
import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.MDC;

/**
 * Handles observability (metrics, logs) for specification validation and
 * registration operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Centralizes metric collection and structured logging for all spec validation
 * and registration flows. Emits standard metrics (validation.count,
 * validation.errors, latency, registration.count, registration.errors) with
 * consistent tags (tenant_id, pipeline_id, spec_version, error_code). Uses MDC
 * for distributed tracing context.
 *
 * <p>
 * <b>Metrics Emitted</b><br>
 * <pre>{@code
 * aep.spec.validation.count (counter)         - Total validation attempts
 * aep.spec.validation.errors (counter)        - Total validation failures (by error_code)
 * aep.spec.validation.latency (timer)         - Time to validate spec (ms)
 * aep.spec.registration.count (counter)       - Total registration attempts
 * aep.spec.registration.errors (counter)      - Total registration failures (by error_code)
 * aep.spec.registration.latency (timer)       - Time to register spec (ms)
 * }</pre>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * SpecValidationObservability obs = new SpecValidationObservability(metricsCollector);
 *
 * // Validation flow
 * long startNanos = System.nanoTime();
 * boolean valid = validator.validate(spec);
 * obs.recordValidationMetrics(valid, tenantId, pipelineId, "1.0.0", error);
 * obs.recordValidationLatency(startNanos);
 *
 * // Registration flow
 * startNanos = System.nanoTime();
 * service.register(registration);
 * obs.recordRegistrationMetrics(tenantId, pipelineId, error);
 * obs.recordRegistrationLatency(startNanos);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Observability handler for spec validation and registration
 * metrics/logs
 * @doc.layer product
 * @doc.pattern Observer/Instrumentation
 */
@Slf4j
public class SpecValidationObservability {

    private static final String METRIC_PREFIX = "aep.spec";
    private static final String VALIDATION_COUNT = METRIC_PREFIX + ".validation.count";
    private static final String VALIDATION_ERRORS = METRIC_PREFIX + ".validation.errors";
    private static final String VALIDATION_LATENCY = METRIC_PREFIX + ".validation.latency";
    private static final String REGISTRATION_COUNT = METRIC_PREFIX + ".registration.count";
    private static final String REGISTRATION_ERRORS = METRIC_PREFIX + ".registration.errors";
    private static final String REGISTRATION_LATENCY = METRIC_PREFIX + ".registration.latency";

    private final MetricsCollector metricsCollector;

    /**
     * Constructs SpecValidationObservability with metrics collector.
     *
     * @param metricsCollector metrics collector for emitting metrics
     */
    public SpecValidationObservability(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    /**
     * Records validation outcome metrics.
     *
     * @param isValid whether validation succeeded
     * @param tenantId tenant identifier
     * @param pipelineId pipeline identifier (may be null)
     * @param specVersion spec version string
     * @param errorCode error code if validation failed (may be null)
     */
    public void recordValidationMetrics(
            boolean isValid,
            String tenantId,
            String pipelineId,
            String specVersion,
            String errorCode) {
        String operation = isValid ? "validate" : "validate_error";

        // Increment validation count
        metricsCollector.incrementCounter(
                VALIDATION_COUNT,
                "tenant_id", tenantId,
                "operation", operation,
                "spec_version", specVersion != null ? specVersion : "unknown");

        if (!isValid) {
            // Increment error counter
            metricsCollector.incrementCounter(
                    VALIDATION_ERRORS,
                    "tenant_id", tenantId,
                    "error_code", errorCode != null ? errorCode : "unknown_error",
                    "spec_version", specVersion != null ? specVersion : "unknown");

            // Log validation failure with context
            log.warn(
                    "Spec validation failed: tenantId={}, pipelineId={}, errorCode={}",
                    tenantId,
                    pipelineId,
                    errorCode);
        } else {
            log.debug(
                    "Spec validation succeeded: tenantId={}, pipelineId={}, specVersion={}",
                    tenantId,
                    pipelineId,
                    specVersion);
        }
    }

    /**
     * Records validation latency metric.
     *
     * @param startNanoTime system nanotime when validation started
     */
    public void recordValidationLatency(long startNanoTime) {
        long durationMs = (System.nanoTime() - startNanoTime) / 1_000_000;
        metricsCollector.recordTimer(VALIDATION_LATENCY, durationMs);
    }

    /**
     * Records registration outcome metrics.
     *
     * @param tenantId tenant identifier
     * @param pipelineId pipeline identifier
     * @param errorCode error code if registration failed (may be null)
     */
    public void recordRegistrationMetrics(
            String tenantId,
            String pipelineId,
            String errorCode) {
        if (errorCode == null) {
            // Success case
            metricsCollector.incrementCounter(
                    REGISTRATION_COUNT,
                    "tenant_id", tenantId,
                    "operation", "register",
                    "pipeline_id", pipelineId);

            log.info(
                    "Spec registration succeeded: tenantId={}, pipelineId={}",
                    tenantId,
                    pipelineId);
        } else {
            // Error case
            metricsCollector.incrementCounter(
                    REGISTRATION_ERRORS,
                    "tenant_id", tenantId,
                    "error_code", errorCode,
                    "pipeline_id", pipelineId);

            log.warn(
                    "Spec registration failed: tenantId={}, pipelineId={}, errorCode={}",
                    tenantId,
                    pipelineId,
                    errorCode);
        }
    }

    /**
     * Records registration latency metric.
     *
     * @param startNanoTime system nanotime when registration started
     */
    public void recordRegistrationLatency(long startNanoTime) {
        long durationMs = (System.nanoTime() - startNanoTime) / 1_000_000;
        metricsCollector.recordTimer(REGISTRATION_LATENCY, durationMs);
    }

    /**
     * Sets MDC context for validation operation.
     *
     * @param tenantId tenant identifier
     * @param pipelineId pipeline identifier (may be null)
     */
    public void setValidationContext(String tenantId, String pipelineId) {
        MDC.put("tenantId", tenantId);
        if (pipelineId != null) {
            MDC.put("pipelineId", pipelineId);
        }
        MDC.put("operation", "spec_validation");
    }

    /**
     * Sets MDC context for registration operation.
     *
     * @param tenantId tenant identifier
     * @param pipelineId pipeline identifier
     */
    public void setRegistrationContext(String tenantId, String pipelineId) {
        MDC.put("tenantId", tenantId);
        MDC.put("pipelineId", pipelineId);
        MDC.put("operation", "spec_registration");
    }

    /**
     * Clears MDC context after validation/registration.
     */
    public void clearContext() {
        MDC.remove("tenantId");
        MDC.remove("pipelineId");
        MDC.remove("operation");
    }
}
