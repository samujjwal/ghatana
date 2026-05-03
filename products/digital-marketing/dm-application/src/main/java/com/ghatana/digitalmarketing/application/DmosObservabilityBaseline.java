package com.ghatana.digitalmarketing.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Structured logging and observability helpers for DMOS service flows.
 * All important state changes and failures must be logged using these helpers
 * to ensure correlation IDs and tenant context are always present in log output.
 *
 * <p>Usage pattern:
 * <pre>{@code
 *   DmosObservabilityBaseline.logServiceCall("runAudit", ctx.getTenantId().getValue(), ctx.getWorkspaceId().getValue());
 *   // ... perform service work ...
 *   DmosObservabilityBaseline.logServiceSuccess("runAudit", ctx.getTenantId().getValue());
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose DMOS observability baseline — structured log helpers and MDC utilities for X-003
 * @doc.layer product
 * @doc.pattern Observability
 */
public final class DmosObservabilityBaseline {

    private static final Logger LOG = LoggerFactory.getLogger(DmosObservabilityBaseline.class);

    /** MDC key for tenant identifier. */
    public static final String MDC_TENANT_ID = "dmos.tenantId";

    /** MDC key for workspace identifier. */
    public static final String MDC_WORKSPACE_ID = "dmos.workspaceId";

    /** MDC key for correlation identifier. */
    public static final String MDC_CORRELATION_ID = "dmos.correlationId";

    /** MDC key for operation name. */
    public static final String MDC_OPERATION = "dmos.operation";

    private DmosObservabilityBaseline() {
        // utilities only
    }

    /**
     * Sets MDC context for the current async frame.
     * Call at the start of each service operation and clear in finally/completion.
     *
     * @param tenantId     tenant identifier
     * @param workspaceId  workspace identifier
     * @param correlationId correlation / request identifier
     */
    public static void setMdcContext(String tenantId, String workspaceId, String correlationId) {
        if (tenantId != null) MDC.put(MDC_TENANT_ID, tenantId);
        if (workspaceId != null) MDC.put(MDC_WORKSPACE_ID, workspaceId);
        if (correlationId != null) MDC.put(MDC_CORRELATION_ID, correlationId);
    }

    /**
     * Clears all DMOS-specific MDC keys.
     * Must be called on completion of an async operation to prevent MDC leakage.
     */
    public static void clearMdcContext() {
        MDC.remove(MDC_TENANT_ID);
        MDC.remove(MDC_WORKSPACE_ID);
        MDC.remove(MDC_CORRELATION_ID);
        MDC.remove(MDC_OPERATION);
    }

    /**
     * Emits a structured INFO log at the start of a service call.
     *
     * @param operation    operation name (e.g., "runAudit")
     * @param tenantId     tenant identifier
     * @param workspaceId  workspace identifier
     */
    public static void logServiceCall(String operation, String tenantId, String workspaceId) {
        MDC.put(MDC_OPERATION, operation);
        LOG.info("[DMOS] {} started — tenant={} workspace={}", operation, tenantId, workspaceId);
    }

    /**
     * Emits a structured INFO log on successful service completion.
     *
     * @param operation operation name
     * @param tenantId  tenant identifier
     */
    public static void logServiceSuccess(String operation, String tenantId) {
        LOG.info("[DMOS] {} succeeded — tenant={}", operation, tenantId);
    }

    /**
     * Emits a structured WARN log for a recoverable service error.
     *
     * @param operation operation name
     * @param tenantId  tenant identifier
     * @param reason    short description of the error
     */
    public static void logServiceWarning(String operation, String tenantId, String reason) {
        LOG.warn("[DMOS] {} warning — tenant={} reason={}", operation, tenantId, reason);
    }

    /**
     * Emits a structured ERROR log for an unrecoverable or unexpected service failure.
     *
     * @param operation operation name
     * @param tenantId  tenant identifier
     * @param cause     the exception that caused the failure
     */
    public static void logServiceError(String operation, String tenantId, Throwable cause) {
        LOG.error("[DMOS] {} failed — tenant={}", operation, tenantId, cause);
    }

    /**
     * Emits a structured INFO log for a security event (auth, authz, rate-limit).
     *
     * @param event    short event description (e.g., "unauthorized_access")
     * @param tenantId tenant identifier
     * @param actor    principal identifier
     * @param resource resource being accessed
     */
    public static void logSecurityEvent(String event, String tenantId, String actor, String resource) {
        LOG.info("[DMOS][SECURITY] event={} tenant={} actor={} resource={}", event, tenantId, actor, resource);
    }

    /**
     * Emits a structured INFO log for workflow state transitions.
     *
     * @param workflowType  workflow type name
     * @param workflowId    unique workflow instance ID
     * @param fromState     previous state
     * @param toState       new state
     */
    public static void logWorkflowTransition(String workflowType, String workflowId, String fromState, String toState) {
        LOG.info("[DMOS][WORKFLOW] type={} id={} transition={}→{}", workflowType, workflowId, fromState, toState);
    }
}
