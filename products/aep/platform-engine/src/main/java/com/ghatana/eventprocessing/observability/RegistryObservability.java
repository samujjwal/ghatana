package com.ghatana.eventprocessing.observability;

import com.ghatana.platform.observability.MetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * Observability handler for pattern and pipeline registry operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Centralizes metrics collection, structured logging, and context management
 * for registry operations (pattern registration, pipeline registration,
 * activation, deactivation).
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * RegistryObservability obs = new RegistryObservability(metricsCollector);
 * obs.recordPatternRegistrationStart("tenant-123", "pattern-1");
 * try {
 *   // Registration logic
 *   obs.recordPatternRegistrationSuccess("pattern-1", 150);
 * } catch (Exception e) {
 *   obs.recordPatternRegistrationError("pattern-1", e);
 * } finally {
 *   obs.clearRegistryContext();
 * }
 * }</pre>
 *
 * <p>
 * <b>Metrics Emitted</b><br>
 * - aep.registry.pattern.registration.count (tags: tenant_id, result) - Pattern
 * registration attempts - aep.registry.pattern.registration.errors (tags:
 * tenant_id, error_type) - Pattern registration failures -
 * aep.registry.pattern.registration.latency (tags: tenant_id) - Time to
 * register pattern - aep.registry.pattern.activation.count (tags: tenant_id,
 * result) - Pattern activation attempts -
 * aep.registry.pattern.activation.latency (tags: tenant_id) - Time to activate
 * pattern - aep.registry.pipeline.registration.count (tags: tenant_id, result)
 * - Pipeline registration attempts - aep.registry.pipeline.registration.errors
 * (tags: tenant_id, error_type) - Pipeline registration failures -
 * aep.registry.pipeline.registration.latency (tags: tenant_id) - Time to
 * register pipeline - aep.registry.pipeline.activation.count (tags: tenant_id,
 * result) - Pipeline activation attempts -
 * aep.registry.pipeline.activation.latency (tags: tenant_id) - Time to activate
 * pipeline
 *
 * <p>
 * <b>Logging</b><br>
 * Uses SLF4J with Log4j2 backend. MDC context includes: - tenantId: Tenant
 * identifier (multi-tenancy isolation) - registryType: "pattern" or "pipeline"
 * - operationId: Pattern ID or Pipeline ID - operation: "registration",
 * "activation", "deactivation" - userId: User who initiated operation
 * (optional)
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe via MetricsCollector abstraction. MDC is thread-local.
 *
 * <p>
 * <b>Architecture Role</b><br>
 * This is an observability adapter that abstracts Micrometer metrics and SLF4J
 * logging. It's consumed by PatternRegistryService, PipelineRegistryService,
 * and registry HTTP endpoints.
 *
 * @see MetricsCollector
 * @see PatternRegistryService
 * @see PipelineRegistryService
 *
 * @doc.type class
 * @doc.purpose Centralized observability handler for registry operations
 * @doc.layer product
 * @doc.pattern Observability Adapter
 */
@Slf4j
@RequiredArgsConstructor
public class RegistryObservability {

    private final MetricsCollector metricsCollector;

    private static final String REGISTRY_TYPE_PATTERN = "pattern";
    private static final String REGISTRY_TYPE_PIPELINE = "pipeline";
    private static final String OPERATION_REGISTRATION = "registration";
    private static final String OPERATION_ACTIVATION = "activation";
    private static final String OPERATION_DEACTIVATION = "deactivation";

    // ==================== Pattern Registry Operations ====================
    /**
     * Records the start of a pattern registration operation.
     *
     * <p>
     * Sets MDC context for distributed tracing across async boundaries.
     *
     * @param tenantId the tenant identifier
     * @param patternId the pattern ID being registered
     * @param userId the user initiating the operation (optional, nullable)
     */
    public void recordPatternRegistrationStart(String tenantId, String patternId, String userId) {
        setRegistryContext(tenantId, REGISTRY_TYPE_PATTERN, patternId, OPERATION_REGISTRATION, userId);
        log.info(
                "Starting pattern registration",
                MDC.get("tenantId"),
                MDC.get("operationId"),
                MDC.get("userId"));
    }

    /**
     * Records successful pattern registration.
     *
     * @param tenantId the tenant identifier
     * @param patternId the pattern ID registered
     * @param durationMs the time taken to register in milliseconds
     */
    public void recordPatternRegistrationSuccess(
            String tenantId, String patternId, long durationMs) {
        metricsCollector.incrementCounter(
                "aep.registry.pattern.registration.count",
                "tenant_id", tenantId,
                "result", "success");
        metricsCollector.recordTimer(
                "aep.registry.pattern.registration.latency",
                durationMs,
                "tenant_id", tenantId);

        log.info(
                "Pattern registration succeeded: patternId={}, duration={}ms",
                patternId, durationMs);
    }

    /**
     * Records failed pattern registration.
     *
     * @param tenantId the tenant identifier
     * @param patternId the pattern ID that failed registration
     * @param error the exception that occurred
     */
    public void recordPatternRegistrationError(
            String tenantId, String patternId, Exception error) {
        String errorType = error.getClass().getSimpleName();
        metricsCollector.incrementCounter(
                "aep.registry.pattern.registration.errors",
                "tenant_id", tenantId,
                "error_type", errorType);

        log.error(
                "Pattern registration failed: patternId={}, error={}",
                patternId, errorType, error);
    }

    /**
     * Records pattern activation start.
     *
     * @param tenantId the tenant identifier
     * @param patternId the pattern ID being activated
     * @param userId the user initiating the activation
     */
    public void recordPatternActivationStart(String tenantId, String patternId, String userId) {
        setRegistryContext(tenantId, REGISTRY_TYPE_PATTERN, patternId, OPERATION_ACTIVATION, userId);
        log.info(
                "Starting pattern activation",
                MDC.get("tenantId"),
                MDC.get("operationId"));
    }

    /**
     * Records successful pattern activation.
     *
     * @param tenantId the tenant identifier
     * @param patternId the pattern ID activated
     * @param durationMs the time taken to activate in milliseconds
     */
    public void recordPatternActivationSuccess(String tenantId, String patternId, long durationMs) {
        metricsCollector.incrementCounter(
                "aep.registry.pattern.activation.count",
                "tenant_id", tenantId,
                "result", "success");
        metricsCollector.recordTimer(
                "aep.registry.pattern.activation.latency",
                durationMs,
                "tenant_id", tenantId);

        log.info(
                "Pattern activation succeeded: patternId={}, duration={}ms",
                patternId, durationMs);
    }

    /**
     * Records failed pattern activation.
     *
     * @param tenantId the tenant identifier
     * @param patternId the pattern ID that failed activation
     * @param error the exception that occurred
     */
    public void recordPatternActivationError(
            String tenantId, String patternId, Exception error) {
        String errorType = error.getClass().getSimpleName();
        metricsCollector.incrementCounter(
                "aep.registry.pattern.activation.errors",
                "tenant_id", tenantId,
                "error_type", errorType);

        log.error(
                "Pattern activation failed: patternId={}, error={}",
                patternId, errorType, error);
    }

    /**
     * Records pattern deactivation start.
     *
     * @param tenantId the tenant identifier
     * @param patternId the pattern ID being deactivated
     * @param userId the user initiating the deactivation
     */
    public void recordPatternDeactivationStart(String tenantId, String patternId, String userId) {
        setRegistryContext(tenantId, REGISTRY_TYPE_PATTERN, patternId, OPERATION_DEACTIVATION, userId);
        log.info(
                "Starting pattern deactivation",
                MDC.get("tenantId"),
                MDC.get("operationId"));
    }

    /**
     * Records pattern deactivation.
     *
     * @param tenantId the tenant identifier
     * @param patternId the pattern ID being deactivated
     * @param durationMs the time taken to deactivate in milliseconds
     */
    public void recordPatternDeactivationSuccess(
            String tenantId, String patternId, long durationMs) {
        metricsCollector.incrementCounter(
                "aep.registry.pattern.deactivation.count",
                "tenant_id", tenantId,
                "result", "success");

        log.info(
                "Pattern deactivation succeeded: patternId={}, duration={}ms",
                patternId, durationMs);
    }

    /**
     * Records failed pattern deactivation.
     *
     * @param tenantId the tenant identifier
     * @param patternId the pattern ID that failed deactivation
     * @param error the exception that occurred
     */
    public void recordPatternDeactivationError(
            String tenantId, String patternId, Exception error) {
        String errorType = error.getClass().getSimpleName();
        metricsCollector.incrementCounter(
                "aep.registry.pattern.deactivation.errors",
                "tenant_id", tenantId,
                "error_type", errorType);

        log.error(
                "Pattern deactivation failed: patternId={}, error={}",
                patternId, errorType, error);
    }

    // ==================== Pipeline Registry Operations ====================
    /**
     * Records the start of a pipeline registration operation.
     *
     * @param tenantId the tenant identifier
     * @param pipelineId the pipeline ID being registered
     * @param userId the user initiating the operation
     */
    public void recordPipelineRegistrationStart(String tenantId, String pipelineId, String userId) {
        setRegistryContext(tenantId, REGISTRY_TYPE_PIPELINE, pipelineId, OPERATION_REGISTRATION, userId);
        log.info(
                "Starting pipeline registration",
                MDC.get("tenantId"),
                MDC.get("operationId"));
    }

    /**
     * Records successful pipeline registration.
     *
     * @param tenantId the tenant identifier
     * @param pipelineId the pipeline ID registered
     * @param durationMs the time taken to register in milliseconds
     */
    public void recordPipelineRegistrationSuccess(
            String tenantId, String pipelineId, long durationMs) {
        metricsCollector.incrementCounter(
                "aep.registry.pipeline.registration.count",
                "tenant_id", tenantId,
                "result", "success");
        metricsCollector.recordTimer(
                "aep.registry.pipeline.registration.latency",
                durationMs,
                "tenant_id", tenantId);

        log.info(
                "Pipeline registration succeeded: pipelineId={}, duration={}ms",
                pipelineId, durationMs);
    }

    /**
     * Records failed pipeline registration.
     *
     * @param tenantId the tenant identifier
     * @param pipelineId the pipeline ID that failed registration
     * @param error the exception that occurred
     */
    public void recordPipelineRegistrationError(
            String tenantId, String pipelineId, Exception error) {
        String errorType = error.getClass().getSimpleName();
        metricsCollector.incrementCounter(
                "aep.registry.pipeline.registration.errors",
                "tenant_id", tenantId,
                "error_type", errorType);

        log.error(
                "Pipeline registration failed: pipelineId={}, error={}",
                pipelineId, errorType, error);
    }

    /**
     * Records successful pipeline activation.
     *
     * @param tenantId the tenant identifier
     * @param pipelineId the pipeline ID being activated
     * @param userId the user initiating the activation
     */
    public void recordPipelineActivationStart(String tenantId, String pipelineId, String userId) {
        setRegistryContext(tenantId, REGISTRY_TYPE_PIPELINE, pipelineId, OPERATION_ACTIVATION, userId);
        log.info(
                "Starting pipeline activation",
                MDC.get("tenantId"),
                MDC.get("operationId"));
    }

    /**
     * Records successful pipeline activation.
     *
     * @param tenantId the tenant identifier
     * @param pipelineId the pipeline ID activated
     * @param durationMs the time taken to activate in milliseconds
     */
    public void recordPipelineActivationSuccess(String tenantId, String pipelineId, long durationMs) {
        metricsCollector.incrementCounter(
                "aep.registry.pipeline.activation.count",
                "tenant_id", tenantId,
                "result", "success");
        metricsCollector.recordTimer(
                "aep.registry.pipeline.activation.latency",
                durationMs,
                "tenant_id", tenantId);

        log.info(
                "Pipeline activation succeeded: pipelineId={}, duration={}ms",
                pipelineId, durationMs);
    }

    /**
     * Records failed pipeline activation.
     *
     * @param tenantId the tenant identifier
     * @param pipelineId the pipeline ID that failed activation
     * @param error the exception that occurred
     */
    public void recordPipelineActivationError(
            String tenantId, String pipelineId, Exception error) {
        String errorType = error.getClass().getSimpleName();
        metricsCollector.incrementCounter(
                "aep.registry.pipeline.activation.errors",
                "tenant_id", tenantId,
                "error_type", errorType);

        log.error(
                "Pipeline activation failed: pipelineId={}, error={}",
                pipelineId, errorType, error);
    }

    /**
     * Records pipeline deactivation start.
     *
     * @param tenantId the tenant identifier
     * @param pipelineId the pipeline ID being deactivated
     * @param userId the user initiating the deactivation
     */
    public void recordPipelineDeactivationStart(String tenantId, String pipelineId, String userId) {
        setRegistryContext(tenantId, REGISTRY_TYPE_PIPELINE, pipelineId, OPERATION_DEACTIVATION, userId);
        log.info(
                "Starting pipeline deactivation",
                MDC.get("tenantId"),
                MDC.get("operationId"));
    }

    /**
     * Records pipeline deactivation.
     *
     * @param tenantId the tenant identifier
     * @param pipelineId the pipeline ID being deactivated
     * @param durationMs the time taken to deactivate in milliseconds
     */
    public void recordPipelineDeactivationSuccess(
            String tenantId, String pipelineId, long durationMs) {
        metricsCollector.incrementCounter(
                "aep.registry.pipeline.deactivation.count",
                "tenant_id", tenantId,
                "result", "success");

        log.info(
                "Pipeline deactivation succeeded: pipelineId={}, duration={}ms",
                pipelineId, durationMs);
    }

    /**
     * Records failed pipeline deactivation.
     *
     * @param tenantId the tenant identifier
     * @param pipelineId the pipeline ID that failed deactivation
     * @param error the exception that occurred
     */
    public void recordPipelineDeactivationError(
            String tenantId, String pipelineId, Exception error) {
        String errorType = error.getClass().getSimpleName();
        metricsCollector.incrementCounter(
                "aep.registry.pipeline.deactivation.errors",
                "tenant_id", tenantId,
                "error_type", errorType);

        log.error(
                "Pipeline deactivation failed: pipelineId={}, error={}",
                pipelineId, errorType, error);
    }

    // ==================== Deletion Operations ====================
    /**
     * Records the start of a deletion operation.
     *
     * @param tenantId the tenant identifier
     * @param entityId the entity ID being deleted (pattern or pipeline)
     */
    public void recordDeletionStart(String tenantId, String entityId) {
        setRegistryContext(tenantId, "entity", entityId, "deletion", null);
        log.info(
                "Starting deletion",
                MDC.get("tenantId"),
                MDC.get("operationId"));
    }

    /**
     * Records successful deletion.
     *
     * @param entityId the entity ID that was deleted
     * @param durationMs the time taken to delete in milliseconds
     */
    public void recordDeletionSuccess(String entityId, long durationMs) {
        metricsCollector.incrementCounter(
                "aep.registry.deletion.count",
                "result", "success");
        metricsCollector.recordTimer(
                "aep.registry.deletion.latency",
                durationMs);

        log.info(
                "Deletion succeeded: entityId={}, duration={}ms",
                entityId, durationMs);
    }

    /**
     * Records failed deletion.
     *
     * @param entityId the entity ID that failed deletion
     * @param error the exception that occurred
     */
    public void recordDeletionError(String entityId, Exception error) {
        String errorType = error.getClass().getSimpleName();
        metricsCollector.incrementCounter(
                "aep.registry.deletion.errors",
                "error_type", errorType);

        log.error(
                "Deletion failed: entityId={}, error={}",
                entityId, errorType, error);
    }

    // ==================== MDC Context Management ====================
    /**
     * Sets MDC context for distributed tracing.
     *
     * @param tenantId the tenant identifier
     * @param registryType "pattern" or "pipeline"
     * @param operationId the pattern ID or pipeline ID
     * @param operation the operation type ("registration", "activation",
     * "deactivation")
     * @param userId the user ID (may be null)
     */
    private void setRegistryContext(
            String tenantId, String registryType, String operationId, String operation, String userId) {
        MDC.put("tenantId", tenantId);
        MDC.put("registryType", registryType);
        MDC.put("operationId", operationId);
        MDC.put("operation", operation);
        if (userId != null) {
            MDC.put("userId", userId);
        }
    }

    /**
     * Clears MDC context after operation completion.
     *
     * <p>
     * Should be called in a finally block to ensure cleanup even on exceptions.
     */
    public void clearRegistryContext() {
        MDC.remove("tenantId");
        MDC.remove("registryType");
        MDC.remove("operationId");
        MDC.remove("operation");
        MDC.remove("userId");
    }
}
