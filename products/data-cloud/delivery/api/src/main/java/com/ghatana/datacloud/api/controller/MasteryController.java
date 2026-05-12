/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.controller;

import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryTransition;
import com.ghatana.agent.mastery.MasteryTransitionResult;
import com.ghatana.datacloud.governance.approval.ApprovalService;
import com.ghatana.platform.http.server.JsonServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * ActiveJ servlet for mastery operations.
 *
 * <p>Exposes mastery endpoints with governance checks for authorization and approval.
 *
 * @doc.type class
 * @doc.purpose ActiveJ servlet for mastery operations with governance
 * @doc.layer data-cloud
 * @doc.pattern Servlet
 */
public class MasteryController extends JsonServlet {

    private final com.ghatana.agent.mastery.MasteryRegistry masteryRegistry;
    private final ApprovalService approvalService;

    public MasteryController(
            @NotNull com.ghatana.agent.mastery.MasteryRegistry masteryRegistry,
            @NotNull ApprovalService approvalService
    ) {
        this.masteryRegistry = masteryRegistry;
        this.approvalService = approvalService != null ? approvalService : ApprovalService.getInstance();
    }

    /**
     * Query mastery items with governance check.
     */
    public Promise<HttpResponse> queryMastery(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        // Governance check: tenant must have access to mastery data
        return approvalService.checkAccess(tenantId, "mastery:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to mastery data"));
                    }

                    String skillId = request.getQueryParameter("skillId");
                    String agentId = request.getQueryParameter("agentId");
                    String state = request.getQueryParameter("state");
                    String limitStr = request.getQueryParameter("limit");
                    String offsetStr = request.getQueryParameter("offset");

                    Integer limit = limitStr != null ? Integer.parseInt(limitStr) : 100;
                    Integer offset = offsetStr != null ? Integer.parseInt(offsetStr) : 0;

                    MasteryQuery query = MasteryQuery.bySkill(skillId != null ? skillId : "")
                            .withTenantId(tenantId)
                            .withLimit(limit)
                            .withOffset(offset);

                    if (agentId != null) {
                        query = new MasteryQuery(
                                skillId,
                                agentId,
                                null,
                                tenantId,
                                null,
                                state != null ? java.util.Set.of(com.ghatana.agent.mastery.MasteryState.valueOf(state)) : null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                limit,
                                offset
                        );
                    }

                    return masteryRegistry.query(query)
                            .map(this::ok)
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Get a mastery item by ID with governance check.
     */
    public Promise<HttpResponse> getMastery(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String masteryId = request.getPathParameter("masteryId");
        
        // Governance check: tenant must have access to mastery data
        return approvalService.checkAccess(tenantId, "mastery:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to mastery data"));
                    }
                    // TODO: Implement getById in MasteryRegistry
                    return promiseOf(ok(Optional.empty()));
                });
    }

    /**
     * Save a mastery item with governance check.
     */
    public Promise<HttpResponse> saveMastery(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        // Governance check: tenant must have approval to write mastery data
        return approvalService.checkAccess(tenantId, "mastery:write")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to write mastery data"));
                    }

                    return parseBodyAsync(request, MasteryItem.class)
                            .then(item -> {
                                // Ensure tenant matches
                                if (!item.applicability().tenantId().equals(tenantId)) {
                                    return Promise.of(badRequest("Tenant ID mismatch"));
                                }
                                return masteryRegistry.save(item).map(savedItem -> created(savedItem));
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Transition a mastery item to a new state with governance check.
     */
    public Promise<HttpResponse> transition(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        // Governance check: tenant must have approval to transition mastery states
        return approvalService.checkAccess(tenantId, "mastery:transition")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to transition mastery"));
                    }

                    return parseBodyAsync(request, MasteryTransition.class)
                            .then(transition -> {
                                // Note: MasteryTransition doesn't have tenantId, skip tenant check for now
                                // TODO: Add tenant validation when MasteryTransition includes tenant context
                                return masteryRegistry.transition(transition).map(result -> ok(result));
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Find stale mastery items with governance check.
     */
    public Promise<HttpResponse> findStale(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        // Governance check: tenant must have access to mastery data
        return approvalService.checkAccess(tenantId, "mastery:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to mastery data"));
                    }

                    return masteryRegistry.findStale(java.time.Instant.now())
                            .map(this::ok)
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }
}
