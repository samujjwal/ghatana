package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.client.autonomy.AutonomyController;
import com.ghatana.datacloud.client.autonomy.AutonomyLevel;
import com.ghatana.datacloud.spi.WriteIdempotencyStore;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles tenant-scoped operational alerts, derived groupings, and alert rules.
 *
 * <p>Alerts and rules are persisted as Data Cloud entities. Alert groups and
 * resolution suggestions are derived on read from the active alert set so the
 * API stays truthful without introducing another persistence model.
 *
 * @doc.type class
 * @doc.purpose Operational alerts HTTP handler for live alerts triage
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class AlertingHandler {

    static final String ALERTS_COLLECTION = "dc_alerts";
    static final String ALERT_RULES_COLLECTION = "dc_alert_rules";
    private static final Logger log = LoggerFactory.getLogger(AlertingHandler.class);
    private static final String MISSING_TENANT_MESSAGE = "X-Tenant-Id header is required";
    private static final List<Map<String, Object>> NO_ALERT_GROUPS = List.of();
    private static final List<String> NO_STRING_VALUES = List.of();

    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private AutonomyController autonomyController;
    /** DC-BE-002: Generic idempotency store for alerting operations. */
    private WriteIdempotencyStore idempotencyStore;

    public AlertingHandler(DataCloudClient client, HttpHandlerSupport http) {
        this.client = client;
        this.http = http;
    }

    /**
     * Attaches an autonomy controller for gated auto-remediation (P2.4).
     */
    public AlertingHandler withAutonomyController(AutonomyController controller) {
        this.autonomyController = controller;
        return this;
    }
    /**
     * DC-BE-002: Attaches a generic idempotency store for alerting operations.
     *
     * @param idempotencyStore the idempotency store
     * @return {@code this} for method chaining
     */
    public AlertingHandler withIdempotencyStore(WriteIdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
        return this;
    }

    public Promise<HttpResponse> handleListAlerts(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, MISSING_TENANT_MESSAGE));
        }
        int limit = HttpHandlerSupport.parseIntParam(request.getQueryParameter("limit"), 100);
        int offset = HttpHandlerSupport.parseIntParam(request.getQueryParameter("offset"), 0);
        String severity = normalizeOptionalEnum(request.getQueryParameter("severity"));
        String status = normalizeOptionalEnum(request.getQueryParameter("status"));
        String cursor = request.getQueryParameter("cursor");

        // Parse cursor if provided (base64 encoded "offset:timestamp")
        final int effectiveOffset;
        if (cursor != null && !cursor.isBlank()) {
            int parsedOffset = offset;
            try {
                String decoded = new String(java.util.Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] parts = decoded.split(":");
                if (parts.length == 2) {
                    parsedOffset = Integer.parseInt(parts[0]);
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid cursor, fall back to offset param
            }
            effectiveOffset = parsedOffset;
        } else {
            effectiveOffset = offset;
        }

        // Query more than needed to determine hasMore
        int queryLimit = Math.max(limit + Math.max(effectiveOffset, 0) + 1, 100);

        return client.query(tenantId, ALERTS_COLLECTION, DataCloudClient.Query.limit(queryLimit))
            .map(entities -> {
                List<Map<String, Object>> allAlerts = entities.stream()
                    .map(entity -> toAlertView(entity, tenantId))
                    .filter(alert -> severity == null || severity.equals(alert.get("severity")))
                    .filter(alert -> status == null || status.equals(alert.get("status")))
                    .sorted(Comparator.comparing(alert -> String.valueOf(alert.get("createdAt")), Comparator.reverseOrder()))
                    .toList();

                int total = allAlerts.size();
                List<Map<String, Object>> alerts = allAlerts.stream()
                    .skip(Math.max(effectiveOffset, 0))
                    .limit(limit)
                    .toList();

                boolean hasMore = allAlerts.size() > effectiveOffset + limit;
                String nextCursor = null;
                int nextOffset = effectiveOffset + limit;
                if (hasMore && !alerts.isEmpty()) {
                    String lastTimestamp = String.valueOf(alerts.get(alerts.size() - 1).get("createdAt"));
                    String cursorPayload = nextOffset + ":" + lastTimestamp;
                    nextCursor = java.util.Base64.getEncoder()
                        .encodeToString(cursorPayload.getBytes(StandardCharsets.UTF_8));
                }

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("tenantId", tenantId);
                response.put("alerts", alerts);
                response.put("count", alerts.size());
                response.put("total", total);
                response.put("limit", limit);
                response.put("offset", effectiveOffset);
                response.put("hasMore", hasMore);
                if (nextCursor != null) {
                    response.put("nextCursor", nextCursor);
                }
                response.put("slaTracking", true);
                response.put("timestamp", Instant.now().toString());
                return http.jsonResponse(response);
            });
    }

    public Promise<HttpResponse> handleAcknowledgeAlert(HttpRequest request) {
        return mutateAlertStatusWithReason(request, "acknowledged", "alert.acknowledged", "acknowledgedAt", "acknowledgedBy", "acknowledgeReason");
    }

    public Promise<HttpResponse> handleResolveAlert(HttpRequest request) {
        return mutateAlertStatusWithReason(request, "resolved", "alert.resolved", "resolvedAt", "resolvedBy", "resolutionReason");
    }

    private String resolveAlertPathParameter(HttpRequest request) {
        String alertId = request.getPathParameter("alertId");
        if (alertId == null || alertId.isBlank()) {
            return request.getPathParameter("id");
        }
        return alertId;
    }

    public Promise<HttpResponse> handleEscalateAlert(HttpRequest request) {
        return escalateAlert(request);
    }

    /**
     * {@code POST /api/v1/alerts/:alertId/auto-remediate} — gated auto-remediation (P2.4).
     *
     * <p>Attempts to automatically resolve an alert if the autonomy controller
     * permits it for the alerts domain and the alert is low-risk (non-critical).
     */
    public Promise<HttpResponse> handleAutoRemediate(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, MISSING_TENANT_MESSAGE));
        }
        String alertId = resolveAlertPathParameter(request);
        if (alertId == null || alertId.isBlank()) {
            return Promise.of(http.errorResponse(400, "alertId path parameter is required"));
        }
        return client.findById(tenantId, ALERTS_COLLECTION, alertId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(http.errorResponse(404, "Alert not found: " + alertId));
                }
                Map<String, Object> alertData = opt.get().data();
                String severity = String.valueOf(alertData.getOrDefault("severity", "info")).toLowerCase(Locale.ROOT);
                boolean isCritical = "critical".equals(severity);

                if (isCritical) {
                    return Promise.of(http.jsonResponse(Map.of(
                        "tenantId", tenantId,
                        "alertId", alertId,
                        "autoRemediated", false,
                        "reason", "Critical alerts require explicit human resolution",
                        "requiredAction", "manual-resolve",
                        "timestamp", Instant.now().toString()
                    )));
                }

                if (autonomyController != null) {
                    return autonomyController.getCurrentLevel("alerts", tenantId)
                        .then(level -> {
                            if (level == AutonomyLevel.AUTONOMOUS) {
                                return autoResolveAlert(tenantId, alertId, "auto-remediated by autonomy controller");
                            }
                            if (level == AutonomyLevel.SUGGEST) {
                                return Promise.of(http.jsonResponse(Map.of(
                                    "tenantId", tenantId,
                                    "alertId", alertId,
                                    "autoRemediated", false,
                                    "reason", "Autonomy level is SUGGEST — manual action required",
                                    "suggestedAction", "resolve",
                                    "autonomyLevel", level.name(),
                                    "timestamp", Instant.now().toString()
                                )));
                            }
                            // CONFIRM, NOTIFY, DISABLED
                            return Promise.of(http.jsonResponse(Map.of(
                                "tenantId", tenantId,
                                "alertId", alertId,
                                "autoRemediated", false,
                                "reason", "Autonomy level " + level.name() + " does not permit auto-remediation",
                                "autonomyLevel", level.name(),
                                "timestamp", Instant.now().toString()
                            )));
                        })
                        .then(Promise::of, e -> {
                            log.error("[autoRemediate] autonomy check failed tenant={} alert={}: {}", tenantId, alertId, e.getMessage(), e);
                            return autoResolveAlert(tenantId, alertId, "auto-remediated (autonomy check failed, fail-open)");
                        });
                }

                // No autonomy controller: manual only for safety
                return Promise.of(http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "alertId", alertId,
                    "autoRemediated", false,
                    "reason", "Autonomy controller not available — manual resolution required",
                    "timestamp", Instant.now().toString()
                )));
            })
            .then(Promise::of, e -> {
                log.error("[autoRemediate] tenant={} alert={} failed: {}", tenantId, alertId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Auto-remediation failed: " + e.getMessage()));
            });
    }

    private Promise<HttpResponse> autoResolveAlert(String tenantId, String alertId, String reason) {
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("id", alertId);
        updates.put("status", "resolved");
        updates.put("resolvedAt", Instant.now().toString());
        updates.put("resolvedBy", "system.auto-remediate");
        updates.put("resolutionReason", reason);
        return client.save(tenantId, ALERTS_COLLECTION, updates)
            .map(saved -> http.jsonResponse(Map.of(
                "tenantId", tenantId,
                "alertId", alertId,
                "autoRemediated", true,
                "status", "resolved",
                "reason", reason,
                "timestamp", Instant.now().toString()
            )));
    }

    /**
     * {@code POST /api/v1/alerts/:id/remediate} — apply a remediation action to an alert (P2.4).
     *
     * <p>Records the remediation action as an entity for audit and supports rollback.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleRemediateAlert(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, MISSING_TENANT_MESSAGE));
        }
        String alertId = resolveAlertPathParameter(request);
        if (alertId == null || alertId.isBlank()) {
            return Promise.of(http.errorResponse(400, "alertId path parameter is required"));
        }
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> payload = http.objectMapper().readValue(
                    buf.getString(StandardCharsets.UTF_8), Map.class);
                String actionId = (String) payload.getOrDefault("actionId", "remediate-" + UUID.randomUUID().toString());
                String actionType = (String) payload.getOrDefault("actionType", "manual-resolution");
                String actor = (String) payload.getOrDefault("actor", "operator");
                String reason = (String) payload.getOrDefault("reason", "");

                Map<String, Object> record = new LinkedHashMap<>();
                record.put("id", actionId);
                record.put("alertId", alertId);
                record.put("tenantId", tenantId);
                record.put("actionType", actionType);
                record.put("actor", actor);
                record.put("reason", reason);
                record.put("appliedAt", Instant.now().toString());
                record.put("status", "applied");
                record.put("rolledBack", false);

                // Also update the alert with remediation metadata
                Map<String, Object> alertUpdate = new LinkedHashMap<>();
                alertUpdate.put("id", alertId);
                alertUpdate.put("status", "remediated");
                alertUpdate.put("remediatedAt", Instant.now().toString());
                alertUpdate.put("remediationActionId", actionId);
                alertUpdate.put("remediationActionType", actionType);

                return client.save(tenantId, DC_REMEDIATION_ACTIONS_COLLECTION, record)
                    .then(saved -> client.save(tenantId, ALERTS_COLLECTION, alertUpdate))
                    .map(savedAlert -> http.jsonResponse(Map.of(
                        "tenantId", tenantId,
                        "alertId", alertId,
                        "actionId", actionId,
                        "actionType", actionType,
                        "status", "applied",
                        "appliedAt", Instant.now().toString()
                    )))
                    .then(Promise::of, e -> {
                        log.error("[P2.4] Remediation apply failed tenant={} alert={}: {}", tenantId, alertId, e.getMessage(), e);
                        return Promise.of(http.errorResponse(500, "Remediation failed: " + e.getMessage()));
                    });
            } catch (Exception e) {
                return Promise.of(http.errorResponse(400, "Invalid remediation payload: " + e.getMessage()));
            }
        });
    }

    /**
     * {@code POST /api/v1/alerts/:id/remediate/rollback} — rollback a remediation (P2.4).
     *
     * <p>Marks the remediation action as rolled back and optionally reverts
     * the alert status to its previous state.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleRollbackRemediation(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, MISSING_TENANT_MESSAGE));
        }
        String alertId = resolveAlertPathParameter(request);
        if (alertId == null || alertId.isBlank()) {
            return Promise.of(http.errorResponse(400, "alertId path parameter is required"));
        }
        return request.loadBody().then(buf -> {
            try {
                Map<String, Object> payload = http.objectMapper().readValue(
                    buf.getString(StandardCharsets.UTF_8), Map.class);
                String actionId = (String) payload.get("actionId");
                if (actionId == null || actionId.isBlank()) {
                    return Promise.of(http.errorResponse(400, "actionId is required"));
                }
                String reason = (String) payload.getOrDefault("reason", "");

                Map<String, Object> rollbackRecord = new LinkedHashMap<>();
                rollbackRecord.put("id", actionId);
                rollbackRecord.put("status", "rolled-back");
                rollbackRecord.put("rolledBack", true);
                rollbackRecord.put("rolledBackAt", Instant.now().toString());
                rollbackRecord.put("rollbackReason", reason);

                Map<String, Object> alertUpdate = new LinkedHashMap<>();
                alertUpdate.put("id", alertId);
                alertUpdate.put("status", "active");
                alertUpdate.put("rollbackActionId", actionId);
                alertUpdate.put("rollbackAt", Instant.now().toString());

                return client.save(tenantId, DC_REMEDIATION_ACTIONS_COLLECTION, rollbackRecord)
                    .then(saved -> client.save(tenantId, ALERTS_COLLECTION, alertUpdate))
                    .map(savedAlert -> http.jsonResponse(Map.of(
                        "tenantId", tenantId,
                        "alertId", alertId,
                        "actionId", actionId,
                        "status", "rolled-back",
                        "rolledBackAt", Instant.now().toString()
                    )))
                    .then(Promise::of, e -> {
                        log.error("[P2.4] Remediation rollback failed tenant={} alert={}: {}", tenantId, alertId, e.getMessage(), e);
                        return Promise.of(http.errorResponse(500, "Rollback failed: " + e.getMessage()));
                    });
            } catch (Exception e) {
                return Promise.of(http.errorResponse(400, "Invalid rollback payload: " + e.getMessage()));
            }
        });
    }

    /**
     * {@code GET /api/v1/alerts/:id/remediations} — list remediation actions for an alert (P2.4).
     */
    public Promise<HttpResponse> handleListRemediations(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, MISSING_TENANT_MESSAGE));
        }
        String alertId = resolveAlertPathParameter(request);
        if (alertId == null || alertId.isBlank()) {
            return Promise.of(http.errorResponse(400, "alertId path parameter is required"));
        }
        int limit = HttpHandlerSupport.parseIntParam(request.getQueryParameter("limit"), 50);
        return client.query(tenantId, DC_REMEDIATION_ACTIONS_COLLECTION,
                DataCloudClient.Query.limit(limit))
            .map(entities -> {
                List<Map<String, Object>> remediations = entities.stream()
                    .filter(e -> alertId.equals(e.data().get("alertId")))
                    .map(e -> e.data())
                    .toList();
                return http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "alertId", alertId,
                    "remediations", remediations,
                    "total", remediations.size(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[P2.4] List remediations failed tenant={} alert={}: {}", tenantId, alertId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to list remediations: " + e.getMessage()));
            });
    }

    static final String DC_REMEDIATION_ACTIONS_COLLECTION = "dc_remediation_actions";

    public Promise<HttpResponse> handleListAlertGroups(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, MISSING_TENANT_MESSAGE));
        }

        return activeAlerts(tenantId).map(alerts -> {
            List<Map<String, Object>> groups = buildAlertGroups(alerts);
            return http.jsonResponse(Map.of(
                "tenantId", tenantId,
                "groups", groups,
                "count", groups.size(),
                "timestamp", Instant.now().toString()
            ));
        });
    }

    public Promise<HttpResponse> handleResolveGroup(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, MISSING_TENANT_MESSAGE));
        }
        String groupId = request.getPathParameter("groupId");
        if (groupId == null || groupId.isBlank()) {
            return Promise.of(http.errorResponse(400, "groupId path parameter is required"));
        }

        // DC-BE-002: Check idempotency for alert group resolution
        String idempotencyKey = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        String operationScope = "alerts:group:resolve";
        if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            var cached = idempotencyStore.get(tenantId, operationScope, idempotencyKey);
            if (cached.isPresent()) {
                log.info("[DC-BE-002] Returning cached alert group resolution response for key={}", idempotencyKey);
                return Promise.of(http.jsonResponse(cached.get()));
            }
        }

        return activeAlerts(tenantId).then(alerts -> {
            Optional<Map<String, Object>> group = buildAlertGroups(alerts).stream()
                .filter(candidate -> groupId.equals(candidate.get("id")))
                .findFirst();
            if (group.isEmpty()) {
                return Promise.of(http.errorResponse(404, "Alert group not found: " + groupId));
            }

            @SuppressWarnings("unchecked")
            List<String> alertIds = (List<String>) group.get().get("alertIds");
            return resolveAlertIds(tenantId, alertIds, "alert.group.resolved")
                .map(resolvedAlertIds -> {
                    Map<String, Object> responseBody = Map.of(
                        "tenantId", tenantId,
                        "groupId", groupId,
                        "resolvedAlertIds", resolvedAlertIds,
                        "resolvedCount", resolvedAlertIds.size(),
                        "timestamp", Instant.now().toString()
                    );
                    // DC-BE-002: Store response in idempotency store
                    if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
                        try {
                            idempotencyStore.put(tenantId, operationScope, idempotencyKey, responseBody);
                        } catch (Exception e) {
                            log.warn("[DC-BE-002] Failed to cache idempotent response for alert group resolution: {}", e.getMessage());
                        }
                    }
                    return http.jsonResponse(responseBody);
                });
        });
    }

    public Promise<HttpResponse> handleListResolutionSuggestions(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, MISSING_TENANT_MESSAGE));
        }

        return activeAlerts(tenantId).map(alerts -> {
            List<Map<String, Object>> suggestions = alerts.stream()
                .map(this::buildResolutionSuggestion)
                .toList();
            return http.jsonResponse(Map.of(
                "tenantId", tenantId,
                "suggestions", suggestions,
                "count", suggestions.size(),
                "total", suggestions.size(),
                "timestamp", Instant.now().toString()
            ));
        });
    }

    public Promise<HttpResponse> handleApplySuggestion(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, MISSING_TENANT_MESSAGE));
        }
        String suggestionId = request.getPathParameter("suggestionId");
        if (suggestionId == null || suggestionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "suggestionId path parameter is required"));
        }

        // DC-BE-002: Check idempotency for suggestion application
        String idempotencyKey = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        String operationScope = "alerts:suggestion:apply";
        if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            var cached = idempotencyStore.get(tenantId, operationScope, idempotencyKey);
            if (cached.isPresent()) {
                log.info("[DC-BE-002] Returning cached suggestion application response for key={}", idempotencyKey);
                return Promise.of(http.jsonResponse(cached.get()));
            }
        }
        String alertId = suggestionId.startsWith("suggestion-")
            ? suggestionId.substring("suggestion-".length())
            : suggestionId;

        return resolveAlertIds(tenantId, List.of(alertId), "alert.suggestion.applied")
            .map(resolvedAlertIds -> {
                Map<String, Object> responseBody = Map.of(
                    "tenantId", tenantId,
                    "suggestionId", suggestionId,
                    "resolvedAlertIds", resolvedAlertIds,
                    "resolvedCount", resolvedAlertIds.size(),
                    "timestamp", Instant.now().toString()
                );
                // DC-BE-002: Store response in idempotency store
                if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
                    try {
                        idempotencyStore.put(tenantId, operationScope, idempotencyKey, responseBody);
                    } catch (Exception e) {
                        log.warn("[DC-BE-002] Failed to cache idempotent response for suggestion application: {}", e.getMessage());
                    }
                }
                return http.jsonResponse(responseBody);
            });
    }

    public Promise<HttpResponse> handleListAlertRules(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, MISSING_TENANT_MESSAGE));
        }
        return client.query(tenantId, ALERT_RULES_COLLECTION, DataCloudClient.Query.limit(200))
            .map(entities -> http.jsonResponse(Map.of(
                "tenantId", tenantId,
                "rules", entities.stream().map(entity -> toRuleView(entity, tenantId)).toList(),
                "count", entities.size(),
                "timestamp", Instant.now().toString()
            )));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleCreateAlertRule(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, MISSING_TENANT_MESSAGE));
        }

        // DC-BE-002: Check idempotency for alert rule creation
        String idempotencyKey = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        String operationScope = "alerts:rules:create";
        if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            var cached = idempotencyStore.get(tenantId, operationScope, idempotencyKey);
            if (cached.isPresent()) {
                log.info("[DC-BE-002] Returning cached alert rule creation response for key={}", idempotencyKey);
                return Promise.of(http.jsonResponse(cached.get()));
            }
        }
        return request.loadBody().then(body -> {
            try {
                Map<String, Object> rule = http.objectMapper().readValue(body.getString(StandardCharsets.UTF_8), Map.class);
                Map<String, Object> persisted = normaliseRulePayload(rule, null);
                return client.save(tenantId, ALERT_RULES_COLLECTION, persisted)
                    .then(saved -> appendMutationEvent(tenantId, "alert.rule.created", Map.of("ruleId", saved.id()))
                        .map(ignored -> saved))
                    .map(saved -> {
                        Map<String, Object> responseBody = Map.of(
                            "tenantId", tenantId,
                            "rule", toRuleView(saved, tenantId),
                            "timestamp", Instant.now().toString()
                        );
                        // DC-BE-002: Store response in idempotency store
                        if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
                            try {
                                idempotencyStore.put(tenantId, operationScope, idempotencyKey, responseBody);
                            } catch (Exception e) {
                                log.warn("[DC-BE-002] Failed to cache idempotent response for alert rule creation: {}", e.getMessage());
                            }
                        }
                        return http.jsonResponse(201, responseBody);
                    });
            } catch (Exception exception) {
                log.warn("[DC-Alerts] create rule failed tenant={}: {}", tenantId, exception.getMessage());
                return Promise.of(http.errorResponse(400, "Invalid alert rule payload: " + exception.getMessage()));
            }
        });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleUpdateAlertRule(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, MISSING_TENANT_MESSAGE));
        }
        String ruleId = request.getPathParameter("ruleId");
        if (ruleId == null || ruleId.isBlank()) {
            return Promise.of(http.errorResponse(400, "ruleId path parameter is required"));
        }

        // DC-BE-002: Check idempotency for alert rule update
        String idempotencyKey = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        String operationScope = "alerts:rules:update";
        if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            var cached = idempotencyStore.get(tenantId, operationScope, idempotencyKey);
            if (cached.isPresent()) {
                log.info("[DC-BE-002] Returning cached alert rule update response for key={}", idempotencyKey);
                return Promise.of(http.jsonResponse(cached.get()));
            }
        }
        return request.loadBody().then(body -> {
            try {
                Map<String, Object> rule = http.objectMapper().readValue(body.getString(StandardCharsets.UTF_8), Map.class);
                Map<String, Object> persisted = normaliseRulePayload(rule, ruleId);
                return client.save(tenantId, ALERT_RULES_COLLECTION, persisted)
                    .then(saved -> appendMutationEvent(tenantId, "alert.rule.updated", Map.of("ruleId", saved.id()))
                        .map(ignored -> saved))
                    .map(saved -> {
                        Map<String, Object> responseBody = Map.of(
                            "tenantId", tenantId,
                            "rule", toRuleView(saved, tenantId),
                            "timestamp", Instant.now().toString()
                        );
                        // DC-BE-002: Store response in idempotency store
                        if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
                            try {
                                idempotencyStore.put(tenantId, operationScope, idempotencyKey, responseBody);
                            } catch (Exception e) {
                                log.warn("[DC-BE-002] Failed to cache idempotent response for alert rule update: {}", e.getMessage());
                            }
                        }
                        return http.jsonResponse(responseBody);
                    });
            } catch (Exception exception) {
                log.warn("[DC-Alerts] update rule failed tenant={} ruleId={}: {}", tenantId, ruleId, exception.getMessage());
                return Promise.of(http.errorResponse(400, "Invalid alert rule payload: " + exception.getMessage()));
            }
        });
    }

    public Promise<HttpResponse> handleDeleteAlertRule(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, MISSING_TENANT_MESSAGE));
        }
        String ruleId = request.getPathParameter("ruleId");
        if (ruleId == null || ruleId.isBlank()) {
            return Promise.of(http.errorResponse(400, "ruleId path parameter is required"));
        }

        // DC-BE-002: Check idempotency for alert rule deletion
        String idempotencyKey = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        String operationScope = "alerts:rules:delete";
        if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            var cached = idempotencyStore.get(tenantId, operationScope, idempotencyKey);
            if (cached.isPresent()) {
                log.info("[DC-BE-002] Returning cached alert rule deletion response for key={}", idempotencyKey);
                return Promise.of(http.jsonResponse(cached.get()));
            }
        }
        return client.delete(tenantId, ALERT_RULES_COLLECTION, ruleId)
            .then(ignored -> appendMutationEvent(tenantId, "alert.rule.deleted", Map.of("ruleId", ruleId)))
            .map(ignored -> {
                Map<String, Object> responseBody = Map.of(
                    "tenantId", tenantId,
                    "ruleId", ruleId,
                    "deleted", true,
                    "timestamp", Instant.now().toString()
                );
                // DC-BE-002: Store response in idempotency store
                if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
                    try {
                        idempotencyStore.put(tenantId, operationScope, idempotencyKey, responseBody);
                    } catch (Exception e) {
                        log.warn("[DC-BE-002] Failed to cache idempotent response for alert rule deletion: {}", e.getMessage());
                    }
                }
                return http.jsonResponse(responseBody);
            });

    }
    private Promise<HttpResponse> mutateAlertStatusWithReason(HttpRequest request, String status, String eventType,
                                                               String timestampField, String actorField, String reasonField) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, MISSING_TENANT_MESSAGE));
        }
        String alertId = resolveAlertPathParameter(request);
        if (alertId == null || alertId.isBlank()) {
            return Promise.of(http.errorResponse(400, "alertId path parameter is required"));
        }

        // DC-BE-002: Check idempotency for alert status mutation
        String idempotencyKey = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        String operationScope = "alerts:" + status;
        if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            var cached = idempotencyStore.get(tenantId, operationScope, idempotencyKey);
            if (cached.isPresent()) {
                log.info("[DC-BE-002] Returning cached alert status mutation response for key={}", idempotencyKey);
                return Promise.of(http.jsonResponse(cached.get()));
            }
        }

        return request.loadBody().then(body -> {
            String reason = null;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = http.objectMapper().readValue(body.getString(StandardCharsets.UTF_8), Map.class);
                Object reasonValue = payload.get("reason");
                if (reasonValue != null) {
                    reason = String.valueOf(reasonValue);
                }
            } catch (IOException ignored) {
                // No body or invalid JSON, proceed without reason
            }
            final String finalReason = reason;
            return client.findById(tenantId, ALERTS_COLLECTION, alertId)
                .then(optional -> {
                    if (optional.isEmpty()) {
                        return Promise.of(http.errorResponse(404, "Alert not found: " + alertId));
                    }
                    Map<String, Object> updated = new LinkedHashMap<>(optional.get().data());
                    updated.put("id", alertId);
                    updated.put("status", status);
                    updated.put(timestampField, Instant.now().toString());
                    updated.put(actorField, resolvePrincipalName(request));
                    if (finalReason != null && !finalReason.isBlank()) {
                        updated.put(reasonField, finalReason);
                    }
                    updated.put("updatedAt", Instant.now().toString());

                    // Build action history entry
                    Map<String, Object> actionEntry = new LinkedHashMap<>();
                    actionEntry.put("action", status);
                    actionEntry.put("timestamp", Instant.now().toString());
                    actionEntry.put("actor", resolvePrincipalName(request));
                    if (finalReason != null && !finalReason.isBlank()) {
                        actionEntry.put("reason", finalReason);
                    }

                    // Append to action history
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> history = (List<Map<String, Object>>) updated.get("actionHistory");
                    if (history == null) {
                        history = new ArrayList<>();
                    }
                    history.add(actionEntry);
                    updated.put("actionHistory", history);

                    return client.save(tenantId, ALERTS_COLLECTION, updated)
                        .then(saved -> appendMutationEvent(tenantId, eventType, Map.of(
                            "alertId", alertId,
                            "status", status,
                            "reason", finalReason != null ? finalReason : "",
                            "actor", resolvePrincipalName(request)
                        )).map(ignored -> saved))
                        .map(saved -> {
                            Map<String, Object> responseBody = toAlertView(saved, tenantId);
                            // DC-BE-002: Store response in idempotency store
                            if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
                                try {
                                    idempotencyStore.put(tenantId, operationScope, idempotencyKey, responseBody);
                                } catch (Exception e) {
                                    log.warn("[DC-BE-002] Failed to cache idempotent response for alert status mutation: {}", e.getMessage());
                                }
                            }
                            return http.jsonResponse(responseBody);
                        });
                });
        });
    }

    private String resolvePrincipalName(HttpRequest request) {
        // Try to get from Principal attachment (set by security filter)
        com.ghatana.platform.governance.security.Principal principal =
            request.getAttachment(com.ghatana.platform.governance.security.Principal.class);
        if (principal != null && principal.getName() != null) {
            return principal.getName();
        }
        // Fallback to anonymous
        return "anonymous";
    }

    /**
     * Escalates an alert by incrementing escalationLevel and assigning an incident ID.
     */
    private Promise<HttpResponse> escalateAlert(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, MISSING_TENANT_MESSAGE));
        }
        String alertId = request.getPathParameter("alertId");
        if (alertId == null || alertId.isBlank()) {
            return Promise.of(http.errorResponse(400, "alertId path parameter is required"));
        }

        // DC-BE-002: Check idempotency for alert escalation
        String idempotencyKey = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        String operationScope = "alerts:escalate";
        if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            var cached = idempotencyStore.get(tenantId, operationScope, idempotencyKey);
            if (cached.isPresent()) {
                log.info("[DC-BE-002] Returning cached alert escalation response for key={}", idempotencyKey);
                return Promise.of(http.jsonResponse(cached.get()));
            }
        }
        return client.findById(tenantId, ALERTS_COLLECTION, alertId)
            .then(optional -> {
                if (optional.isEmpty()) {
                    return Promise.of(http.errorResponse(404, "Alert not found: " + alertId));
                }
                Map<String, Object> updated = new LinkedHashMap<>(optional.get().data());
                updated.put("id", alertId);
                int currentLevel = Integer.parseInt(String.valueOf(updated.getOrDefault("escalationLevel", "0")));
                updated.put("escalationLevel", String.valueOf(currentLevel + 1));
                updated.put("escalatedAt", Instant.now().toString());
                updated.put("escalatedBy", resolvePrincipalName(request));
                if (!updated.containsKey("incidentId")) {
                    updated.put("incidentId", UUID.randomUUID().toString());
                }
                updated.put("incidentStatus", "open");
                updated.put("updatedAt", Instant.now().toString());

                Map<String, Object> actionEntry = new LinkedHashMap<>();
                actionEntry.put("action", "escalated");
                actionEntry.put("timestamp", Instant.now().toString());
                actionEntry.put("actor", resolvePrincipalName(request));
                actionEntry.put("reason", "Escalated to incident");

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> history = (List<Map<String, Object>>) updated.get("actionHistory");
                if (history == null) {
                    history = new ArrayList<>();
                }
                history.add(actionEntry);
                updated.put("actionHistory", history);

                return client.save(tenantId, ALERTS_COLLECTION, updated)
                    .then(saved -> appendMutationEvent(tenantId, "alert.escalated", Map.of(
                        "alertId", alertId,
                        "escalationLevel", currentLevel + 1,
                        "incidentId", updated.get("incidentId"),
                        "actor", resolvePrincipalName(request)
                    )).map(ignored -> saved))
                    .map(saved -> {
                        Map<String, Object> responseBody = toAlertView(saved, tenantId);
                        // DC-BE-002: Store response in idempotency store
                        if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
                            try {
                                idempotencyStore.put(tenantId, operationScope, idempotencyKey, responseBody);
                            } catch (Exception e) {
                                log.warn("[DC-BE-002] Failed to cache idempotent response for alert escalation: {}", e.getMessage());
                            }
                        }
                        return http.jsonResponse(responseBody);
                    });
            });
    }

    private Promise<List<Map<String, Object>>> activeAlerts(String tenantId) {
        return client.query(tenantId, ALERTS_COLLECTION, DataCloudClient.Query.limit(200))
            .map(entities -> entities.stream()
                .map(entity -> toAlertView(entity, tenantId))
                .filter(alert -> "active".equals(alert.get("status")))
                .sorted(Comparator.comparing(alert -> String.valueOf(alert.get("createdAt")), Comparator.reverseOrder()))
                .toList());
    }

    private Promise<List<String>> resolveAlertIds(String tenantId, List<String> alertIds, String eventType) {
        Promise<List<String>> chain = Promise.of(new ArrayList<>());
        for (String alertId : alertIds) {
            chain = chain.then(resolved -> client.findById(tenantId, ALERTS_COLLECTION, alertId)
                .then(optional -> {
                    if (optional.isEmpty()) {
                        return Promise.of(resolved);
                    }
                    Map<String, Object> updated = new LinkedHashMap<>(optional.get().data());
                    updated.put("id", alertId);
                    updated.put("status", "resolved");
                    updated.put("resolvedAt", Instant.now().toString());
                    updated.put("updatedAt", Instant.now().toString());
                    return client.save(tenantId, ALERTS_COLLECTION, updated)
                        .then(saved -> appendMutationEvent(tenantId, eventType, Map.of("alertId", alertId)))
                        .map(ignored -> {
                            resolved.add(alertId);
                            return resolved;
                        });
                }));
        }
        return chain.map(List::copyOf);
    }

    private Promise<DataCloudClient.Offset> appendMutationEvent(String tenantId, String eventType, Map<String, Object> payload) {
        return client.appendEvent(tenantId, DataCloudClient.Event.builder()
            .type(eventType)
            .payload(payload)
            .source("datacloud.launcher.alerting-handler")
            .build());
    }

    private List<Map<String, Object>> buildAlertGroups(List<Map<String, Object>> alerts) {
        Map<String, List<Map<String, Object>>> bySource = new LinkedHashMap<>();
        for (Map<String, Object> alert : alerts) {
            String source = String.valueOf(alert.getOrDefault("source", "runtime"));
            bySource.computeIfAbsent(source, ignored -> new ArrayList<>()).add(alert);
        }

        List<Map<String, Object>> groups = bySource.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(entry -> buildGroup(entry.getKey(), entry.getValue(), false))
            .toList();
        if (!groups.isEmpty()) {
            return groups;
        }
        if (alerts.size() > 1) {
            return List.of(buildGroup("active-incident", alerts, true));
        }
        return NO_ALERT_GROUPS;
    }

    private Map<String, Object> buildGroup(String key, List<Map<String, Object>> alerts, boolean globalGroup) {
        String severity = alerts.stream()
            .map(alert -> String.valueOf(alert.getOrDefault("severity", "info")))
            .max(Comparator.comparingInt(this::severityRank))
            .orElse("info");
        String label = globalGroup ? "Active operational incidents" : humanizeSource(key) + " degradation";
        return Map.of(
            "id", "group-" + normalizeId(key),
            "title", label,
            "rootCause", globalGroup ? "Multiple active alerts require operator triage" : humanizeSource(key),
            "alertIds", alerts.stream().map(alert -> String.valueOf(alert.get("id"))).toList(),
            "aiConfidence", globalGroup ? 0.67 : 0.84,
            "suggestedAction", suggestedGroupAction(key, severity, alerts.size()),
            "suggestedActionType", severity.equals("critical") ? "manual" : "auto"
        );
    }

    private Map<String, Object> buildResolutionSuggestion(Map<String, Object> alert) {
        String alertId = String.valueOf(alert.get("id"));
        String severity = String.valueOf(alert.getOrDefault("severity", "info"));
        String source = String.valueOf(alert.getOrDefault("source", "runtime"));
        String createdAt = String.valueOf(alert.getOrDefault("createdAt", Instant.now().toString()));
        double confidence = calculateAlertConfidence(alert, severity, source, createdAt);
        return Map.of(
            "id", "suggestion-" + alertId,
            "alertId", alertId,
            "suggestion", suggestedActionForAlert(source, severity),
            "confidence", confidence,
            "confidenceFactors", buildConfidenceFactors(alert, severity, source, createdAt),
            "canAutoResolve", !severity.equals("critical") && confidence > 0.70,
            "steps", List.of(
                "Inspect the affected component for the most recent failure signal.",
                "Apply the remediation and verify the alert no longer reproduces."
            )
        );
    }

    /**
     * Calculates alert resolution confidence using a heuristic model.
     *
     * <p>Factors considered:
     * <ul>
     *   <li>Severity weight (critical alerts have higher confidence in detection but lower auto-resolve)</li>
     *   <li>Alert age (older alerts have more context for better suggestions)</li>
     *   <li>Source reliability (known sources vs custom)</li>
     *   <li>Title/description pattern match</li>
     * </ul>
     */
    private double calculateAlertConfidence(Map<String, Object> alert, String severity, String source, String createdAt) {
        double baseScore = 0.70;

        // Severity factor: info (+0.05), warning (+0.08), critical (+0.10)
        double severityFactor = switch (severity) {
            case "info" -> 0.05;
            case "warning" -> 0.08;
            case "critical" -> 0.10;
            default -> 0.05;
        };

        // Alert age factor: older alerts have more context
        double ageFactor = 0.0;
        try {
            Instant created = Instant.parse(createdAt);
            long hoursOld = Duration.between(created, Instant.now()).toHours();
            ageFactor = Math.min(hoursOld * 0.01, 0.05); // max +0.05 for alerts older than 5 hours
        } catch (DateTimeParseException ignored) {
            // Use default age factor if parsing fails
        }

        // Source reliability factor
        double sourceFactor = switch (source) {
            case "runtime", "system" -> 0.08; // high reliability
            case "monitoring", "health" -> 0.06;
            case "security" -> 0.07;
            default -> 0.03; // custom sources less reliable
        };

        // Pattern match factor: check if title/description contains known patterns
        double patternFactor = calculatePatternMatchFactor(alert);

        double totalScore = baseScore + severityFactor + ageFactor + sourceFactor + patternFactor;
        return Math.round(Math.min(totalScore, 0.98) * 100.0) / 100.0; // cap at 0.98, round to 2 decimals
    }

    private double calculatePatternMatchFactor(Map<String, Object> alert) {
        String title = String.valueOf(alert.getOrDefault("title", "")).toLowerCase();
        String description = String.valueOf(alert.getOrDefault("description", "")).toLowerCase();
        String combined = title + " " + description;

        int patternMatches = 0;
        // High-confidence patterns
        if (combined.contains("out of memory") || combined.contains("oom")) patternMatches += 3;
        if (combined.contains("connection refused") || combined.contains("connection timeout")) patternMatches += 3;
        if (combined.contains("disk full") || combined.contains("no space left")) patternMatches += 3;
        if (combined.contains("cpu usage") && combined.contains("high")) patternMatches += 2;
        if (combined.contains("memory") && combined.contains("leak")) patternMatches += 2;
        if (combined.contains("slow query") || combined.contains("query timeout")) patternMatches += 2;
        // Medium-confidence patterns
        if (combined.contains("error") || combined.contains("exception")) patternMatches += 1;
        if (combined.contains("failed") || combined.contains("failure")) patternMatches += 1;
        if (combined.contains("unavailable") || combined.contains("down")) patternMatches += 1;

        return Math.min(patternMatches * 0.02, 0.08); // max +0.08 for pattern matches
    }

    private Map<String, Object> buildConfidenceFactors(Map<String, Object> alert, String severity, String source, String createdAt) {
        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("severity", severity);
        factors.put("source", source);
        factors.put("modelVersion", "heuristic-v2");
        factors.put("calculationMethod", "multi-factor-heuristic");

        double baseScore = 0.70;
        double severityFactor = switch (severity) {
            case "info" -> 0.05;
            case "warning" -> 0.08;
            case "critical" -> 0.10;
            default -> 0.05;
        };
        factors.put("baseScore", baseScore);
        factors.put("severityFactor", severityFactor);

        double ageFactor = 0.0;
        try {
            Instant created = Instant.parse(createdAt);
            long hoursOld = Duration.between(created, Instant.now()).toHours();
            ageFactor = Math.min(hoursOld * 0.01, 0.05);
            factors.put("alertAgeHours", hoursOld);
        } catch (Exception ignored) {
            factors.put("alertAgeHours", "unknown");
        }
        factors.put("ageFactor", ageFactor);

        double sourceFactor = switch (source) {
            case "runtime", "system" -> 0.08;
            case "monitoring", "health" -> 0.06;
            case "security" -> 0.07;
            default -> 0.03;
        };
        factors.put("sourceFactor", sourceFactor);

        double patternFactor = calculatePatternMatchFactor(alert);
        factors.put("patternFactor", patternFactor);

        return factors;
    }

    private Map<String, Object> toAlertView(DataCloudClient.Entity entity, String tenantId) {
        return toAlertViewFromData(entity.id(), entity.data(), tenantId, entity.createdAt().toString());
    }


    private Map<String, Object> toAlertViewFromData(String id, Map<String, Object> data, String tenantId, String defaultCreatedAt) {
        LinkedHashMap<String, Object> view = new LinkedHashMap<>();
        view.put("id", id);
        view.put("tenantId", tenantId);
        view.put("title", String.valueOf(data.getOrDefault("title", id)));
        view.put("description", String.valueOf(data.getOrDefault("description", "")));
        view.put("severity", normalizeSeverity(data.get("severity")));
        view.put("status", normalizeStatus(data.get("status")));
        view.put("source", String.valueOf(data.getOrDefault("source", "runtime")));
        view.put("createdAt", String.valueOf(data.getOrDefault("createdAt", defaultCreatedAt)));
        putIfPresent(view, "acknowledgedAt", data.get("acknowledgedAt"));
        putIfPresent(view, "acknowledgedBy", data.get("acknowledgedBy"));
        putIfPresent(view, "acknowledgeReason", data.get("acknowledgeReason"));
        putIfPresent(view, "resolvedAt", data.get("resolvedAt"));
        putIfPresent(view, "resolvedBy", data.get("resolvedBy"));
        putIfPresent(view, "resolutionReason", data.get("resolutionReason"));
        putIfPresent(view, "actionHistory", data.get("actionHistory"));
        // Escalation / incident lifecycle fields
        view.put("escalationLevel", String.valueOf(data.getOrDefault("escalationLevel", "0")));
        putIfPresent(view, "escalatedAt", data.get("escalatedAt"));
        putIfPresent(view, "escalatedBy", data.get("escalatedBy"));
        putIfPresent(view, "incidentId", data.get("incidentId"));
        view.put("incidentStatus", String.valueOf(data.getOrDefault("incidentStatus", "open")));
        putIfPresent(view, "incidentClosedAt", data.get("incidentClosedAt"));

        // SLA tracking: compute SLA minutes and breach status based on severity and age
        long slaMinutes = computeSlaMinutes(view.get("severity"));
        boolean slaBreached = computeSlaBreached(String.valueOf(view.get("createdAt")), slaMinutes);
        view.put("slaMinutes", slaMinutes);
        view.put("slaBreached", slaBreached);

        return Map.copyOf(view);
    }

    private Map<String, Object> toRuleView(DataCloudClient.Entity entity, String tenantId) {
        return toRuleViewFromData(savedIdMap(entity.data(), entity.id()), tenantId);
    }

    private Map<String, Object> toRuleViewFromData(Map<String, Object> data, String tenantId) {
        LinkedHashMap<String, Object> rule = new LinkedHashMap<>();
        rule.put("id", String.valueOf(data.getOrDefault("id", "")));
        rule.put("tenantId", tenantId);
        rule.put("name", String.valueOf(data.getOrDefault("name", "Untitled alert rule")));
        rule.put("description", String.valueOf(data.getOrDefault("description", "")));
        rule.put("enabled", readBoolean(data.get("enabled"), true));
        rule.put("severity", normalizeSeverity(data.get("severity")));
        rule.put("conditionType", String.valueOf(data.getOrDefault("conditionType", "threshold")));
        rule.put("metric", String.valueOf(data.getOrDefault("metric", "")));
        rule.put("operator", String.valueOf(data.getOrDefault("operator", "gt")));
        rule.put("threshold", readDouble(data.get("threshold"), 0));
        rule.put("duration", readInt(data.get("duration"), 5));
        rule.put("channels", readStringList(data.get("channels")));
        rule.put("recipients", readStringList(data.get("recipients")));
        putIfPresent(rule, "webhookUrl", data.get("webhookUrl"));
        return Map.copyOf(rule);
    }

    private Map<String, Object> normaliseRulePayload(Map<String, Object> payload, String explicitRuleId) {
        LinkedHashMap<String, Object> rule = new LinkedHashMap<>();
        if (explicitRuleId != null && !explicitRuleId.isBlank()) {
            rule.put("id", explicitRuleId);
        } else if (payload.containsKey("id")) {
            rule.put("id", String.valueOf(payload.get("id")));
        }
        rule.put("name", String.valueOf(payload.getOrDefault("name", "Untitled alert rule")));
        rule.put("description", String.valueOf(payload.getOrDefault("description", "")));
        rule.put("enabled", readBoolean(payload.get("enabled"), true));
        rule.put("severity", normalizeSeverity(payload.get("severity")));
        rule.put("conditionType", String.valueOf(payload.getOrDefault("conditionType", "threshold")));
        rule.put("metric", String.valueOf(payload.getOrDefault("metric", "")));
        rule.put("operator", String.valueOf(payload.getOrDefault("operator", "gt")));
        rule.put("threshold", readDouble(payload.get("threshold"), 0));
        rule.put("duration", readInt(payload.get("duration"), 5));
        rule.put("channels", readStringList(payload.get("channels")));
        rule.put("recipients", readStringList(payload.get("recipients")));
        if (payload.containsKey("webhookUrl")) {
            rule.put("webhookUrl", String.valueOf(payload.get("webhookUrl")));
        }
        rule.put("updatedAt", Instant.now().toString());
        return Map.copyOf(rule);
    }

    private Map<String, Object> savedIdMap(Map<String, Object> data) {
        return savedIdMap(data, String.valueOf(data.getOrDefault("id", "")));
    }

    private Map<String, Object> savedIdMap(Map<String, Object> data, String id) {
        LinkedHashMap<String, Object> withId = new LinkedHashMap<>(data);
        withId.put("id", id);
        return Map.copyOf(withId);
    }

    private String resolveTenantId(HttpRequest request) {
        String queryTenant = request.getQueryParameter("tenantId");
        if (queryTenant != null && !queryTenant.isBlank()) {
            return queryTenant;
        }
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return null;
        }
        return resolutionResult.tenantId();
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            target.put(key, value);
        }
    }

    private String normalizeSeverity(Object raw) {
        String severity = normalizeOptionalEnum(raw == null ? null : String.valueOf(raw));
        if (severity == null) {
            return "warning";
        }
        return switch (severity) {
            case "critical", "warning", "info" -> severity;
            default -> "warning";
        };
    }

    private String normalizeStatus(Object raw) {
        String status = normalizeOptionalEnum(raw == null ? null : String.valueOf(raw));
        if (status == null) {
            return "active";
        }
        return switch (status) {
            case "active", "acknowledged", "resolved" -> status;
            default -> "active";
        };
    }

    private String normalizeOptionalEnum(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "critical" -> 3;
            case "warning" -> 2;
            default -> 1;
        };
    }

    private String humanizeSource(String source) {
        String normalized = source.replace('-', ' ').replace('_', ' ').trim();
        if (normalized.isEmpty()) {
            return "Runtime";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String normalizeId(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String suggestedGroupAction(String source, String severity, int alertCount) {
        if (severity.equals("critical")) {
            return "Review " + humanizeSource(source) + " immediately and resolve the " + alertCount + " related active alerts in sequence.";
        }
        return "Apply the recommended remediation for " + humanizeSource(source) + " and verify all " + alertCount + " related alerts stabilize.";
    }

    private String suggestedActionForAlert(String source, String severity) {
        String normalizedSource = source.toLowerCase(Locale.ROOT);
        if (normalizedSource.contains("kafka")) {
            return "Restart the affected Kafka consumer group and verify backlog recovery.";
        }
        if (normalizedSource.contains("schema")) {
            return "Inspect the schema registry response times and clear unhealthy nodes from rotation.";
        }
        if (severity.equals("critical")) {
            return "Escalate to the operator on call and validate service recovery before resolving the alert.";
        }
        return "Apply the standard remediation playbook for " + humanizeSource(source) + " and verify the signal normalizes.";
    }

    private boolean readBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return defaultValue;
    }

    private double readDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private int readInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private List<String> readStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return NO_STRING_VALUES;
    }

    /**
     * Computes SLA threshold in minutes based on alert severity.
     * critical = 60min, warning = 240min (4h), info = 1440min (24h).
     */
    private static long computeSlaMinutes(Object severityObj) {
        String severity = String.valueOf(severityObj).toLowerCase(Locale.ROOT);
        return switch (severity) {
            case "critical" -> 60L;
            case "warning" -> 240L;
            case "info" -> 1440L;
            default -> 240L;
        };
    }

    /**
     * Determines whether the alert has breached its SLA based on creation time and severity.
     */
    private static boolean computeSlaBreached(String createdAtIso, long slaMinutes) {
        if (createdAtIso == null || createdAtIso.isBlank() || "null".equals(createdAtIso)) {
            return false;
        }
        try {
            Instant created = Instant.parse(createdAtIso);
            long ageMinutes = Duration.between(created, Instant.now()).toMinutes();
            return ageMinutes > slaMinutes;
        } catch (Exception e) {
            return false;
        }
    }
}
