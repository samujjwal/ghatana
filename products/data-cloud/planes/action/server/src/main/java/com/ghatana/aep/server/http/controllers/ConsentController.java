/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.server.consent.ConsentDecisionStore;
import com.ghatana.aep.server.http.HttpHelper;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * T-23: HTTP controller for server-side consent decision management.
 *
 * <p>The server is the authoritative source of truth for consent state.
 * Clients must not rely on localStorage consent flags — this API provides
 * the canonical record of per-user consent decisions.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/consent/record}       — record a consent decision</li>
 *   <li>{@code GET  /api/v1/consent/{userId}}      — get latest decision for user</li>
 *   <li>{@code GET  /api/v1/consent}               — list all decisions for tenant</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Server-side consent decision recording and retrieval
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class ConsentController implements AepController {

    private static final Logger log = LoggerFactory.getLogger(ConsentController.class);
    private static final Set<String> VALID_STATUSES = Set.of("granted", "denied", "withdrawn");

    private final ConsentDecisionStore store;

    public ConsentController(ConsentDecisionStore store) {
        this.store = store;
    }

    @Override
    public String getBasePath() {
        return "/api/v1/consent";
    }

    @Override
    public Promise<HttpResponse> handle(HttpRequest request, String path) {
        if ("record".equals(path)) {
            return handleRecordConsent(request);
        }
        if (path == null || path.isBlank()) {
            return handleListConsent(request);
        }
        return handleGetConsent(request, path);
    }

    /**
     * POST /api/v1/consent/record
     * Body: { "userId": "...", "status": "granted|denied|withdrawn", "purposes": [...] }
     */
    public Promise<HttpResponse> handleRecordConsent(HttpRequest request) {
        String tenantId = HttpHelper.resolveTenantId(request);
        return request.loadBody()
                .then(body -> {
                    Map<String, Object> payload;
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> temp = HttpHelper.mapper().readValue(
                                body.asString(StandardCharsets.UTF_8), Map.class);
                        payload = temp;
                    } catch (Exception e) {
                        return Promise.of(HttpHelper.errorResponse(400, "Invalid JSON body"));
                    }
                    String userId = (String) payload.get("userId");
                    String status = (String) payload.get("status");
                    if (userId == null || userId.isBlank()) {
                        return Promise.of(HttpHelper.errorResponse(400, "userId is required"));
                    }
                    if (status == null || !VALID_STATUSES.contains(status)) {
                        return Promise.of(HttpHelper.errorResponse(400, "status must be one of: granted, denied, withdrawn"));
                    }
                    List<String> purposes = payload.get("purposes") instanceof List<?> list
                            ? list.stream().map(Object::toString).toList()
                            : List.of();
                    return store.recordDecision(tenantId, userId, status, purposes, Instant.now())
                            .map(record -> {
                                log.info("[consent] decision recorded tenantId={} userId={} status={}", tenantId, userId, status);
                                return HttpHelper.jsonResponse(201, Map.of(
                                        "consentId", record.consentId(),
                                        "tenantId", record.tenantId(),
                                        "userId", record.userId(),
                                        "status", record.status(),
                                        "purposes", record.purposes(),
                                        "decidedAt", record.decidedAt().toString()));
                            })
                            .then(Promise::of, e -> {
                                log.error("[consent] recordDecision error tenantId={} userId={}: {}", tenantId, userId, e.getMessage(), e);
                                return Promise.of(HttpHelper.errorResponse(500, "Failed to record consent decision"));
                            });
                });
    }

    /**
     * GET /api/v1/consent/:userId
     */
    public Promise<HttpResponse> handleGetConsent(HttpRequest request, String userId) {
        String tenantId = HttpHelper.resolveTenantId(request);
        return store.getDecision(tenantId, userId)
                .map(opt -> opt.map(record -> HttpHelper.jsonResponse(200, Map.of(
                        "consentId", record.consentId(),
                        "tenantId", record.tenantId(),
                        "userId", record.userId(),
                        "status", record.status(),
                        "purposes", record.purposes(),
                        "decidedAt", record.decidedAt().toString())))
                        .orElseGet(() -> HttpHelper.errorResponse(404, "No consent decision found for user")))
                .then(Promise::of, e -> {
                    log.warn("[consent] getDecision error tenantId={} userId={}: {}", tenantId, userId, e.getMessage());
                    return Promise.of(HttpHelper.errorResponse(500, "Failed to retrieve consent decision"));
                });
    }

    /**
     * GET /api/v1/consent
     * Query params: limit (default 50), offset (default 0)
     */
    public Promise<HttpResponse> handleListConsent(HttpRequest request) {
        String tenantId = HttpHelper.resolveTenantId(request);
        int limit  = parseIntParam(request.getQueryParameter("limit"),  50,  500);
        int offset = parseIntParam(request.getQueryParameter("offset"), 0, Integer.MAX_VALUE);
        return store.listDecisions(tenantId, limit, offset)
                .map(records -> {
                    List<Map<String, Object>> items = records.stream()
                            .map(r -> Map.<String, Object>of(
                                    "consentId", r.consentId(),
                                    "userId", r.userId(),
                                    "status", r.status(),
                                    "purposes", r.purposes(),
                                    "decidedAt", r.decidedAt().toString()))
                            .toList();
                    return HttpHelper.jsonResponse(200, Map.of(
                            "items", items,
                            "count", items.size(),
                            "tenantId", tenantId));
                })
                .then(Promise::of, e -> {
                    log.warn("[consent] listDecisions error tenantId={}: {}", tenantId, e.getMessage());
                    return Promise.of(HttpHelper.errorResponse(500, "Failed to list consent decisions"));
                });
    }

    private static int parseIntParam(String raw, int defaultValue, int maxValue) {
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            int parsed = Integer.parseInt(raw.trim());
            return Math.min(Math.max(0, parsed), maxValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
