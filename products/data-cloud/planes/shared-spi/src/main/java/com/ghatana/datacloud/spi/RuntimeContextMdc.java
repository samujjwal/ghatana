package com.ghatana.datacloud.spi;

import org.slf4j.MDC;

/**
 * MDC helper for RuntimeContext to ensure consistent logging across all workflows.
 *
 * <p><b>Purpose</b><br>
 * Provides utility methods to populate and clear MDC with RuntimeContext identifiers,
 * ensuring consistent logging correlation across all Data Cloud workflows.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * try (MdcScope scope = RuntimeContextMdc.withContext(context)) {
 *     // All logs in this scope will have context identifiers
 *     log.info("Processing request");
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose MDC helper for RuntimeContext logging
 * @doc.layer shared-spi
 * @doc.pattern Utility
 */
public final class RuntimeContextMdc {

    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String TENANT_ID_KEY = "tenantId";
    private static final String SURFACE_KEY = "surface";
    private static final String RUN_ID_KEY = "runId";
    private static final String JOB_ID_KEY = "jobId";
    private static final String AGENT_ID_KEY = "agentId";
    private static final String PIPELINE_ID_KEY = "pipelineId";
    private static final String ARTIFACT_ID_KEY = "artifactId";

    private RuntimeContextMdc() {
        // Utility class
    }

    /**
     * Populates MDC with all non-null identifiers from the RuntimeContext.
     */
    public static void populateMdc(RuntimeContext context) {
        if (context == null) {
            return;
        }
        if (context.hasCorrelationId()) {
            MDC.put(CORRELATION_ID_KEY, context.getCorrelationId());
        }
        if (context.hasTenantId()) {
            MDC.put(TENANT_ID_KEY, context.getTenantId());
        }
        if (context.getSurface() != null && !context.getSurface().isBlank()) {
            MDC.put(SURFACE_KEY, context.getSurface());
        }
        if (context.hasRunId()) {
            MDC.put(RUN_ID_KEY, context.getRunId());
        }
        if (context.hasJobId()) {
            MDC.put(JOB_ID_KEY, context.getJobId());
        }
        if (context.hasAgentId()) {
            MDC.put(AGENT_ID_KEY, context.getAgentId());
        }
        if (context.hasPipelineId()) {
            MDC.put(PIPELINE_ID_KEY, context.getPipelineId());
        }
        if (context.hasArtifactId()) {
            MDC.put(ARTIFACT_ID_KEY, context.getArtifactId());
        }
    }

    /**
     * Clears all RuntimeContext-related MDC keys.
     */
    public static void clearMdc() {
        MDC.remove(CORRELATION_ID_KEY);
        MDC.remove(TENANT_ID_KEY);
        MDC.remove(SURFACE_KEY);
        MDC.remove(RUN_ID_KEY);
        MDC.remove(JOB_ID_KEY);
        MDC.remove(AGENT_ID_KEY);
        MDC.remove(PIPELINE_ID_KEY);
        MDC.remove(ARTIFACT_ID_KEY);
    }

    /**
     * Creates a try-with-resources scope that automatically manages MDC.
     */
    public static MdcScope withContext(RuntimeContext context) {
        populateMdc(context);
        return new MdcScope();
    }

    /**
     * Try-with-resources scope for automatic MDC cleanup.
     */
    public static class MdcScope implements AutoCloseable {
        @Override
        public void close() {
            clearMdc();
        }
    }

    /**
     * Gets the current correlationId from MDC.
     */
    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    /**
     * Gets the current tenantId from MDC.
     */
    public static String getTenantId() {
        return MDC.get(TENANT_ID_KEY);
    }

    /**
     * Gets the current runId from MDC.
     */
    public static String getRunId() {
        return MDC.get(RUN_ID_KEY);
    }

    /**
     * Gets the current jobId from MDC.
     */
    public static String getJobId() {
        return MDC.get(JOB_ID_KEY);
    }

    /**
     * Gets the current agentId from MDC.
     */
    public static String getAgentId() {
        return MDC.get(AGENT_ID_KEY);
    }

    /**
     * Gets the current pipelineId from MDC.
     */
    public static String getPipelineId() {
        return MDC.get(PIPELINE_ID_KEY);
    }

    /**
     * Gets the current artifactId from MDC.
     */
    public static String getArtifactId() {
        return MDC.get(ARTIFACT_ID_KEY);
    }
}
