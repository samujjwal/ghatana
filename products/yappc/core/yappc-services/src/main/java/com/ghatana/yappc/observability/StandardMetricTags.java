/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.observability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.HashMap;

/**
 * Standard metric tags for consistent observability across all YAPPC services.
 * Every metric emission must include these standard tags for proper aggregation and filtering.
 *
 * @doc.type class
 * @doc.purpose Standard metric tags for consistent observability across all services
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class StandardMetricTags {

    private StandardMetricTags() {}

    /**
     * Creates standard metric tags for a metric emission.
     *
     * @param tenantId The tenant ID
     * @param workspaceId The workspace ID
     * @param projectId The project ID
     * @param phase The lifecycle phase (optional)
     * @param operation The operation being performed
     * @param outcome The outcome of the operation
     * @param degraded Whether the system is degraded
     * @return Map of standard metric tags
     */
    public static Map<String, String> create(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @Nullable String phase,
            @NotNull String operation,
            @NotNull String outcome,
            boolean degraded
    ) {
        Map<String, String> tags = new HashMap<>();
        tags.put("tenantId", tenantId);
        tags.put("workspaceId", workspaceId);
        tags.put("projectId", projectId);
        if (phase != null && !phase.isBlank()) {
            tags.put("phase", phase);
        }
        tags.put("operation", operation);
        tags.put("outcome", outcome);
        tags.put("degraded", String.valueOf(degraded));
        return tags;
    }

    /**
     * Creates standard metric tags with additional custom tags.
     *
     * @param tenantId The tenant ID
     * @param workspaceId The workspace ID
     * @param projectId The project ID
     * @param phase The lifecycle phase (optional)
     * @param operation The operation being performed
     * @param outcome The outcome of the operation
     * @param degraded Whether the system is degraded
     * @param additionalTags Additional custom tags
     * @return Map of standard metric tags with custom tags merged
     */
    public static Map<String, String> createWithCustomTags(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @Nullable String phase,
            @NotNull String operation,
            @NotNull String outcome,
            boolean degraded,
            @NotNull Map<String, String> additionalTags
    ) {
        Map<String, String> tags = create(tenantId, workspaceId, projectId, phase, operation, outcome, degraded);
        tags.putAll(additionalTags);
        return tags;
    }

    /**
     * Standard metric tag keys.
     */
    public static class TagKeys {
        public static final String TENANT_ID = "tenantId";
        public static final String WORKSPACE_ID = "workspaceId";
        public static final String PROJECT_ID = "projectId";
        public static final String ARTIFACT_ID = "artifactId";
        public static final String PHASE = "phase";
        public static final String OPERATION = "operation";
        public static final String OUTCOME = "outcome";
        public static final String DEGRADED = "degraded";
    }

    /**
     * Standard outcome values.
     */
    public static class Outcomes {
        public static final String SUCCESS = "success";
        public static final String FAILURE = "failure";
        public static final String ERROR = "error";
        public static final String TIMEOUT = "timeout";
        public static final String DEGRADED = "degraded";
        public static final String REJECTED = "rejected";
        public static final String APPROVED = "approved";
    }

    /**
     * Standard phase values.
     */
    public static class Phases {
        public static final String INTENT = "intent";
        public static final String SHAPE = "shape";
        public static final String VALIDATE = "validate";
        public static final String GENERATE = "generate";
        public static final String RUN = "run";
        public static final String OBSERVE = "observe";
        public static final String LEARN = "learn";
        public static final String EVOLVE = "evolve";
    }

    /**
     * Standard operation values.
     */
    public static class Operations {
        public static final String PHASE_PACKET_QUERY = "phase_packet_query";
        public static final String GENERATION_CREATE = "generation_create";
        public static final String GENERATION_APPLY = "generation_apply";
        public static final String GENERATION_REJECT = "generation_reject";
        public static final String GENERATION_ROLLBACK = "generation_rollback";
        public static final String ARTIFACT_IMPORT = "artifact_import";
        public static final String PREVIEW_CREATE = "preview_create";
        public static final String PREVIEW_VALIDATE = "preview_validate";
        public static final String DASHBOARD_ACTION = "dashboard_action";
    }
}
