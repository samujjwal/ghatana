/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.dlq;

import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.workflow.WorkflowMaterializer;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for the YAPPC Dead-Letter Queue management API.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET  /api/v1/dlq}             — list PENDING DLQ entries for the authenticated tenant</li>
 *   <li>{@code POST /api/v1/dlq/:id/retry}   — retry a failed pipeline event</li>
 * </ul>
 *
 * <h2>List Response (200 OK)</h2>
 * <pre>
 * [
 *   {
 *     "id": "uuid",
 *     "pipelineId": "lifecycle-management-v1",
 *     "nodeId": "phase-transition-validator",
 *     "eventType": "lifecycle.phase.transition.requested",
 *     "failureReason": "INVALID_TRANSITION",
 *     "retryCount": 0,
 *     "status": "PENDING",
 *     "createdAt": "2026-01-01T12:00:00Z"
 *   }
 * ]
 * </pre>
 *
 * <h2>Retry Response (202 Accepted)</h2>
 * <pre>
 * {
 *   "dlqId": "uuid",
 *   "runId": "new-feature-...",
 *   "status": "RUNNING"
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose REST API for YAPPC Dead-Letter Queue management and retry
 * @doc.layer api
 * @doc.pattern Controller
 */
public class DlqController {

    private static final Logger log = LoggerFactory.getLogger(DlqController.class);
    private static final int DEFAULT_LIMIT = 50;
    private static final String STATUS_PENDING = "PENDING";

    private final JdbcDlqRepository dlqRepository;
    private final WorkflowMaterializer workflowMaterializer;

    /**
     * Creates a new {@code DlqController}.
     *
     * @param dlqRepository       JDBC repository for DLQ CRUD operations
     * @param workflowMaterializer used to re-start workflow on retry
     */
    public DlqController(JdbcDlqRepository dlqRepository,
                          WorkflowMaterializer workflowMaterializer) {
        this.dlqRepository       = Objects.requireNonNull(dlqRepository);
        this.workflowMaterializer = Objects.requireNonNull(workflowMaterializer);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/dlq
    // -------------------------------------------------------------------------

    /**
     * Lists PENDING DLQ entries for the authenticated tenant.
     *
     * <p>Accepts optional query param {@code status} (default: {@code PENDING})
     * and {@code limit} (default: 50, max: 200).
     *
     * @param request HTTP request (must be authenticated)
     * @return 200 OK with list of DLQ entries
     */
    public Promise<HttpResponse> listEntries(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
                .map(ctx -> {
                    String status = Optional.ofNullable(request.getQueryParameter("status"))
                            .filter(s -> !s.isBlank())
                            .orElse(STATUS_PENDING);
                    int limit = parseQueryInt(request, "limit", DEFAULT_LIMIT, 1, 200);

                    log.debug("DLQ list: tenant={} status={} limit={}", ctx.tenantId(), status, limit);
                    List<DlqEntry> entries = dlqRepository.list(ctx.tenantId(), status, limit);
                    List<Map<String, Object>> resp = entries.stream()
                            .map(this::serializeEntry)
                            .toList();

                    return ApiResponse.ok(resp);
                })
                .then(Promise::of, e -> {
                    log.error("DLQ list failed: {}", e.getMessage(), e);
                    return Promise.of(ApiResponse.fromException(e));
                });
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/dlq/:id/retry
    // -------------------------------------------------------------------------

    /**
     * Retries a failed DLQ entry by re-starting the associated workflow.
     *
     * <p>Looks up the DLQ entry, extracts {@code workflow_template_id} from the event payload
     * (defaults to {@code "new-feature"}), re-starts the workflow, and marks the entry as
     * {@code RESOLVED} on success or {@code RETRYING} on handoff.
     *
     * @param request HTTP request (must be authenticated)
     * @param id      the DLQ entry UUID from the URL path
     * @return 202 Accepted with retry result
     */
    public Promise<HttpResponse> retryEntry(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    UUID entryId;
                    try {
                        entryId = UUID.fromString(id);
                    } catch (IllegalArgumentException e) {
                        return Promise.of(ApiResponse.badRequest("Invalid DLQ entry ID: " + id));
                    }

                    Optional<DlqEntry> optEntry = dlqRepository.findById(entryId, ctx.tenantId());
                    if (optEntry.isEmpty()) {
                        return Promise.of(ApiResponse.notFound("DLQ entry not found: " + id));
                    }

                    DlqEntry entry = optEntry.get();
                    if ("RESOLVED".equals(entry.status()) || "ABANDONED".equals(entry.status())) {
                        return Promise.of(ApiResponse.badRequest(
                                "Entry " + id + " is already " + entry.status() + " — cannot retry"));
                    }

                    // Mark as RETRYING before starting the workflow
                    dlqRepository.updateStatus(entryId, ctx.tenantId(), "RETRYING", true, null);
                    log.info("DLQ retry: id={} tenant={} pipeline={} node={}",
                            id, ctx.tenantId(), entry.pipelineId(), entry.nodeId());

                    // Determine which workflow template to use
                    String templateId = (String) entry.eventPayload()
                            .getOrDefault("workflow_template_id", "new-feature");

                    // Re-start the workflow with the stored payload as initial variables
                    Map<String, Object> vars = new java.util.HashMap<>(entry.eventPayload());
                    vars.put("dlq.retry", true);
                    vars.put("dlq.entryId", entry.id().toString());

                    try {
                        com.ghatana.platform.workflow.engine.DurableWorkflowEngine.WorkflowExecution exec =
                                workflowMaterializer.startWorkflow(templateId, ctx.tenantId(), vars);

                        // Mark as RESOLVED on successful handoff
                        dlqRepository.updateStatus(entryId, ctx.tenantId(), "RESOLVED", false, Instant.now());

                        Map<String, Object> resp = new LinkedHashMap<>();
                        resp.put("dlqId",  id);
                        resp.put("runId",  exec.run().workflowId());
                        resp.put("status", exec.run().status().name());

                        log.info("DLQ retry succeeded: dlqId={} runId={}", id, exec.run().workflowId());
                        return Promise.of(ApiResponse.accepted(resp));

                    } catch (IllegalArgumentException e) {
                        // Unknown template — mark ABANDONED
                        dlqRepository.updateStatus(entryId, ctx.tenantId(), "ABANDONED", false, null);
                        log.warn("DLQ retry abandoned (unknown template '{}'): dlqId={}", templateId, id);
                        return Promise.of(ApiResponse.badRequest(
                                "Workflow template '" + templateId + "' not registered — entry abandoned"));
                    } catch (Exception e) {
                        // Retry failed — leave as RETRYING for manual intervention
                        log.error("DLQ retry failed: dlqId={} error={}", id, e.getMessage(), e);
                        return Promise.of(ApiResponse.fromException(e));
                    }
                })
                .then(Promise::of, e -> {
                    log.error("DLQ retry endpoint error for id={}: {}", id, e.getMessage(), e);
                    return Promise.of(ApiResponse.fromException(e));
                });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> serializeEntry(DlqEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",            e.id().toString());
        m.put("pipelineId",    e.pipelineId());
        m.put("nodeId",        e.nodeId());
        m.put("eventType",     e.eventType());
        m.put("failureReason", e.failureReason());
        m.put("retryCount",    e.retryCount());
        m.put("status",        e.status());
        m.put("correlationId", e.correlationId());
        m.put("createdAt",     e.createdAt().toString());
        return Collections.unmodifiableMap(m);
    }

    private int parseQueryInt(HttpRequest req, String param, int defaultValue, int min, int max) {
        String raw = req.getQueryParameter(param);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
