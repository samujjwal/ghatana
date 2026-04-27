package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
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

    private final DataCloudClient client;
    private final HttpHandlerSupport http;

    public AlertingHandler(DataCloudClient client, HttpHandlerSupport http) {
        this.client = client;
        this.http = http;
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

    public Promise<HttpResponse> handleEscalateAlert(HttpRequest request) {
        return escalateAlert(request);
    }

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
                .map(resolvedIds -> http.jsonResponse(Map.of(
                    "groupId", groupId,
                    "tenantId", tenantId,
                    "resolvedAlertIds", resolvedIds,
                    "resolvedAt", Instant.now().toString()
                )));
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
        String alertId = suggestionId.startsWith("suggestion-")
            ? suggestionId.substring("suggestion-".length())
            : suggestionId;

        return resolveAlertIds(tenantId, List.of(alertId), "alert.suggestion.applied")
            .map(resolvedIds -> http.jsonResponse(Map.of(
                "suggestionId", suggestionId,
                "tenantId", tenantId,
                "resolvedAlertIds", resolvedIds,
                "appliedAt", Instant.now().toString()
            )));
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
        return request.loadBody().then(body -> {
            try {
                Map<String, Object> rule = http.objectMapper().readValue(body.getString(StandardCharsets.UTF_8), Map.class);
                Map<String, Object> persisted = normaliseRulePayload(rule, null);
                return client.save(tenantId, ALERT_RULES_COLLECTION, persisted)
                    .then(saved -> appendMutationEvent(tenantId, "alert.rule.created", Map.of("ruleId", saved.id())))
                    .map(ignored -> http.createdResponse(toRuleViewFromData(savedIdMap(persisted), tenantId)));
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
        return request.loadBody().then(body -> {
            try {
                Map<String, Object> rule = http.objectMapper().readValue(body.getString(StandardCharsets.UTF_8), Map.class);
                Map<String, Object> persisted = normaliseRulePayload(rule, ruleId);
                return client.save(tenantId, ALERT_RULES_COLLECTION, persisted)
                    .then(saved -> appendMutationEvent(tenantId, "alert.rule.updated", Map.of("ruleId", saved.id())))
                    .map(ignored -> http.jsonResponse(toRuleViewFromData(savedIdMap(persisted), tenantId)));
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
        return client.delete(tenantId, ALERT_RULES_COLLECTION, ruleId)
            .then(ignored -> appendMutationEvent(tenantId, "alert.rule.deleted", Map.of("ruleId", ruleId)))
            .map(ignored -> http.noContentResponse());
    }

    private Promise<HttpResponse> mutateAlertStatusWithReason(HttpRequest request, String status, String eventType,
                                                               String timestampField, String actorField, String reasonField) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, MISSING_TENANT_MESSAGE));
        }
        String alertId = request.getPathParameter("alertId");
        if (alertId == null || alertId.isBlank()) {
            return Promise.of(http.errorResponse(400, "alertId path parameter is required"));
        }

        return request.loadBody().then(body -> {
            String reason = null;
            try {
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
                        )))
                        .map(ignored -> http.jsonResponse(toAlertViewFromData(alertId, updated, tenantId, Instant.now().toString())));
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
                    )))
                    .map(ignored -> http.jsonResponse(toAlertViewFromData(alertId, updated, tenantId, Instant.now().toString())));
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
        return client.appendEvent(tenantId, DataCloudClient.Event.of(eventType, payload));
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
        return List.of();
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
        return http.requireTenantIdOrFail(request);
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
        return List.of();
    }
}