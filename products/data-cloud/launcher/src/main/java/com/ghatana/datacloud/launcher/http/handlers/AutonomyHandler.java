package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.client.autonomy.AutonomyController;
import com.ghatana.datacloud.client.autonomy.AutonomyLevel;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * HTTP handler for autonomy management routes.
 *
 * <p>Exposes the emergency global shutoff endpoint and domain-level policy management
 * required by B9 of the Data Cloud gap plan. The emergency shutoff sets every action
 * type to {@link AutonomyLevel#SUGGEST} (full human-in-the-loop) and logs the
 * operator override via the {@link AutonomyController}.
 *
 * <p>Routes served:
 * <ul>
 *   <li>{@code PUT  /api/v1/autonomy/level}           — set global level (SUGGEST to halt all autonomous action)</li>
 *   <li>{@code GET  /api/v1/autonomy/level}           — get current global override level</li>
 *   <li>{@code GET  /api/v1/autonomy/domains}         — list all domain states</li>
 *   <li>{@code GET  /api/v1/autonomy/domains/:domain} — get single domain state</li>
 *   <li>{@code PUT  /api/v1/autonomy/domains/:domain} — update domain policy</li>
 *   <li>{@code GET  /api/v1/autonomy/logs}            — get autonomy audit log</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Emergency autonomy shutoff and domain-level policy HTTP handler
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class AutonomyHandler {

    private static final Logger log = LoggerFactory.getLogger(AutonomyHandler.class);

    private static final String GLOBAL_ACTION_TYPE = "*";
    private static final String REASON_OPERATOR_OVERRIDE = "Operator emergency shutoff";

    private final AutonomyController autonomyController;
    private final HttpHandlerSupport http;

    /**
     * Global override level. When set, reflects the latest operator-forced level for
     * display in {@code GET /api/v1/autonomy/level}. Null means no override in effect.
     */
    private volatile AutonomyLevel globalOverride = null;

    /**
     * @param autonomyController the autonomy controller; may be {@code null} to return 503
     * @param http               shared HTTP helper
     */
    public AutonomyHandler(AutonomyController autonomyController, HttpHandlerSupport http) {
        this.autonomyController = autonomyController;
        this.http = http;
    }

    /**
     * {@code PUT /api/v1/autonomy/level} — set the global autonomy level.
     *
     * <p>Body: {@code {"level": "SUGGEST"}} to halt all autonomous action.
     * The operator must supply a {@code reason} field to create an auditable log entry.
     *
     * <pre>
     * PUT /api/v1/autonomy/level
     * Content-Type: application/json
     *
     * { "level": "SUGGEST", "reason": "Emergency operator shutoff" }
     * </pre>
     */
    public Promise<HttpResponse> handleSetGlobalLevel(HttpRequest request) {
        if (autonomyController == null) {
            return Promise.of(http.errorResponse(503, "Autonomy controller not available"));
        }
        return request.loadBody()
            .then(body -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = http.objectMapper().readValue(
                        body.getString(StandardCharsets.UTF_8), Map.class);
                    String levelStr = (String) payload.get("level");
                    if (levelStr == null || levelStr.isBlank()) {
                        return Promise.of(http.errorResponse(400, "'level' field is required"));
                    }
                    AutonomyLevel level;
                    try {
                        level = AutonomyLevel.valueOf(levelStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return Promise.of(http.errorResponse(400,
                            "Unknown level '" + levelStr + "'. Valid values: SUGGEST, CONFIRM, NOTIFY, AUTONOMOUS"));
                    }
                    String reason = payload.containsKey("reason")
                        ? (String) payload.get("reason")
                        : REASON_OPERATOR_OVERRIDE;

                    String tenantId = http.resolveTenantId(request);
                    final AutonomyLevel targetLevel = level;

                    // forceDowngrade all known domains to target level
                    return autonomyController.listAllStates(tenantId)
                        .then(states -> {
                            if (states.isEmpty()) {
                                globalOverride = targetLevel;
                                log.warn("[B9] Global autonomy override set to {} by operator (no active domains), reason: {}",
                                    targetLevel, reason);
                                return Promise.of(http.jsonResponse(Map.of(
                                    "globalLevel", targetLevel.name(),
                                    "affectedDomains", 0,
                                    "timestamp", Instant.now().toString(),
                                    "reason", reason
                                )));
                            }
                            // Apply the level to every known action type
                            Promise<Void> chain = Promise.complete();
                            for (String actionType : states.keySet()) {
                                chain = chain.then(() ->
                                    autonomyController.setLevel(actionType, tenantId, targetLevel, reason)
                                        .map(r -> null));
                            }
                            final int domainCount = states.size();
                            return chain.map(ignored -> {
                                globalOverride = targetLevel;
                                log.warn("[B9] Global autonomy override set to {} across {} domains by operator, reason: {}",
                                    targetLevel, domainCount, reason);
                                return http.jsonResponse(Map.of(
                                    "globalLevel", targetLevel.name(),
                                    "affectedDomains", domainCount,
                                    "timestamp", Instant.now().toString(),
                                    "reason", reason
                                ));
                            });
                        })
                        .then(Promise::of, e -> {
                            log.error("[B9] Failed to set global autonomy level: {}", e.getMessage(), e);
                            return Promise.of(http.errorResponse(500,
                                "Failed to set global autonomy level: " + e.getMessage()));
                        });
                } catch (Exception e) {
                    log.warn("[B9] Invalid request body for set-global-level: {}", e.getMessage());
                    return Promise.of(http.errorResponse(400, "Invalid JSON body: " + e.getMessage()));
                }
            });
    }

    /**
     * {@code GET /api/v1/autonomy/level} — get the current global override level.
     *
     * <p>Returns the last operator-set level, or {@code "NONE"} if no override is active.
     */
    public Promise<HttpResponse> handleGetGlobalLevel(HttpRequest request) {
        if (autonomyController == null) {
            return Promise.of(http.errorResponse(503, "Autonomy controller not available"));
        }
        String tenantId = http.resolveTenantId(request);
        return autonomyController.listAllStates(tenantId)
            .map(states -> http.jsonResponse(Map.of(
                "globalLevel", globalOverride != null ? globalOverride.name() : "NONE",
                "shutoffActive", globalOverride == AutonomyLevel.SUGGEST,
                "domainCount", states.size(),
                "timestamp", Instant.now().toString()
            )))
            .then(Promise::of, e -> {
                log.error("[B9] Failed to get global autonomy level: {}", e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to get global level: " + e.getMessage()));
            });
    }

    /**
     * {@code GET /api/v1/autonomy/domains} — list all domain autonomy states.
     */
    public Promise<HttpResponse> handleListDomains(HttpRequest request) {
        if (autonomyController == null) {
            return Promise.of(http.errorResponse(503, "Autonomy controller not available"));
        }
        String tenantId = http.resolveTenantId(request);
        return autonomyController.listAllStates(tenantId)
            .map(states -> http.jsonResponse(Map.of(
                "domains", states,
                "count", states.size(),
                "timestamp", Instant.now().toString()
            )))
            .then(Promise::of, e -> {
                log.error("[B9] Failed to list autonomy domains: {}", e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to list domains: " + e.getMessage()));
            });
    }

    /**
     * {@code GET /api/v1/autonomy/domains/:domain} — get domain state.
     */
    public Promise<HttpResponse> handleGetDomain(HttpRequest request) {
        if (autonomyController == null) {
            return Promise.of(http.errorResponse(503, "Autonomy controller not available"));
        }
        String domain = request.getPathParameter("domain");
        return autonomyController.getStateForDomain(domain)
            .map(state -> http.jsonResponse(Map.of(
                "domain", domain,
                "state", state,
                "timestamp", Instant.now().toString()
            )))
            .then(Promise::of, e -> {
                log.error("[B9] Failed to get domain state for {}: {}", domain, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to get domain state: " + e.getMessage()));
            });
    }

    /**
     * {@code GET /api/v1/autonomy/logs} — get autonomy audit log for observability.
     */
    public Promise<HttpResponse> handleGetLogs(HttpRequest request) {
        if (autonomyController == null) {
            return Promise.of(http.errorResponse(503, "Autonomy controller not available"));
        }
        return autonomyController.getActionLog()
            .map(logs -> http.jsonResponse(Map.of(
                "logs", logs,
                "count", logs.size(),
                "globalOverride", globalOverride != null ? globalOverride.name() : "NONE",
                "timestamp", Instant.now().toString()
            )))
            .then(Promise::of, e -> {
                log.error("[B9] Failed to retrieve autonomy logs: {}", e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to retrieve logs: " + e.getMessage()));
            });
    }
}
