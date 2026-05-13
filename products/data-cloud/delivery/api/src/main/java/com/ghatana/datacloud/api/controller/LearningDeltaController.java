/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.controller;

import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaRepository;
import com.ghatana.agent.learning.LearningDeltaService;
import com.ghatana.agent.learning.LearningDeltaState;
import com.ghatana.datacloud.governance.approval.ApprovalService;
import com.ghatana.platform.http.server.JsonServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ActiveJ servlet for learning delta operations.
 *
 * @doc.type class
 * @doc.purpose ActiveJ servlet for learning delta operations
 * @doc.layer data-cloud
 * @doc.pattern Servlet
 */
public class LearningDeltaController extends JsonServlet {

    private final LearningDeltaRepository repository;
    private final LearningDeltaService service;
    private final ApprovalService approvalService;

    public LearningDeltaController(
            @NotNull LearningDeltaRepository repository,
            @NotNull LearningDeltaService service,
            @NotNull ApprovalService approvalService
    ) {
        this.repository = repository;
        this.service = service;
        this.approvalService = approvalService != null ? approvalService : ApprovalService.getInstance();
    }

    /**
     * Save a learning delta with governance check.
     */
    public Promise<HttpResponse> save(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        // Governance check: tenant must have approval to propose learning deltas
        return approvalService.checkAccess(tenantId, "learning:propose")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to propose learning deltas"));
                    }

                    return parseBodyAsync(request, LearningDelta.class)
                            .then((LearningDelta delta) -> {
                                // Ensure tenant matches
                                if (!delta.tenantId().equals(tenantId)) {
                                    return Promise.of(badRequest("Tenant ID mismatch"));
                                }
                                // TODO: Get contract from context based on tenant and target
                                com.ghatana.agent.learning.LearningContract contract = new com.ghatana.agent.learning.LearningContract(
                                        com.ghatana.agent.learning.LearningLevel.L2,
                                        java.util.Set.of(),
                                        true,
                                        false
                                );
                                return service.propose(delta, contract).map(this::created);
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Get a learning delta by ID with governance check.
     */
    public Promise<HttpResponse> getById(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String deltaId = request.getPathParameter("deltaId");

        // Governance check: tenant must have access to learning delta data
        return approvalService.checkAccess(tenantId, "learning:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to learning delta data"));
                    }

                    return repository.findById(deltaId)
                            .map(optional -> optional
                                    .map(delta -> {
                                        // Tenant isolation: only return delta if tenant matches
                                        if (!delta.tenantId().equals(tenantId)) {
                                            return notFound("Learning delta not found");
                                        }
                                        return ok(delta);
                                    })
                                    .orElseGet(() -> notFound("Learning delta not found")));
                });
    }

    /**
     * Find learning deltas by state with governance check.
     */
    public Promise<HttpResponse> findByState(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String state = request.getPathParameter("state");
        if (state == null || state.isBlank()) {
            return Promise.of(badRequest("state is required"));
        }

        // Governance check: tenant must have access to learning delta data
        return approvalService.checkAccess(tenantId, "learning:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to learning delta data"));
                    }

                    try {
                        LearningDeltaState deltaState = LearningDeltaState.valueOf(state);
                        return repository.findByState(deltaState)
                                .map(deltas -> {
                                    // Tenant isolation: filter deltas by tenant
                                    List<LearningDelta> filteredDeltas = deltas.stream()
                                            .filter(delta -> delta.tenantId().equals(tenantId))
                                            .toList();
                                    return ok(filteredDeltas);
                                })
                                .whenException(e -> Promise.of(internalError(e)));
                    } catch (IllegalArgumentException e) {
                        return Promise.of(badRequest("Invalid state: " + state));
                    }
                });
    }

    /**
     * Find pending learning deltas for an agent with governance check.
     */
    public Promise<HttpResponse> findPending(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String agentId = request.getPathParameter("agentId");

        // Governance check: tenant must have access to learning delta data
        return approvalService.checkAccess(tenantId, "learning:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to learning delta data"));
                    }

                    return repository.findPending(agentId)
                            .map(deltas -> {
                                // Tenant isolation: filter deltas by tenant
                                List<LearningDelta> filteredDeltas = deltas.stream()
                                        .filter(delta -> delta.tenantId().equals(tenantId))
                                        .toList();
                                return ok(filteredDeltas);
                            });
                });
    }

    /**
     * Evaluate a learning delta with governance check.
     */
    public Promise<HttpResponse> evaluate(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String deltaId = request.getPathParameter("deltaId");

        // Governance check: tenant must have approval to evaluate learning deltas
        return approvalService.checkAccess(tenantId, "learning:evaluate")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to evaluate learning deltas"));
                    }

                    // Validate tenant ownership
                    return repository.findById(deltaId)
                            .then((Optional<LearningDelta> optional) -> {
                                if (optional.isEmpty()) {
                                    return Promise.of(notFound("Learning delta not found"));
                                }
                                LearningDelta delta = optional.get();
                                if (!delta.tenantId().equals(tenantId)) {
                                    return Promise.of(forbidden("Cannot evaluate learning delta from another tenant"));
                                }
                                return service.evaluate(deltaId)
                                        .map(result -> ok(Map.of("success", result)));
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Transition a learning delta to a new state with governance check.
     */
    public Promise<HttpResponse> transition(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String deltaId = request.getPathParameter("deltaId");
        String state = request.getQueryParameter("state");

        // Governance check: tenant must have approval to transition learning deltas
        return approvalService.checkAccess(tenantId, "learning:transition")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to transition learning deltas"));
                    }

                    // Validate tenant ownership
                    return repository.findById(deltaId)
                            .then((Optional<LearningDelta> optional) -> {
                                if (optional.isEmpty()) {
                                    return Promise.of(notFound("Learning delta not found"));
                                }
                                LearningDelta delta = optional.get();
                                if (!delta.tenantId().equals(tenantId)) {
                                    return Promise.of(forbidden("Cannot transition learning delta from another tenant"));
                                }

                                try {
                                    LearningDeltaState newState = LearningDeltaState.valueOf(state);
                                    return repository.transition(deltaId, newState)
                                            .map(this::ok)
                                            .whenException(e -> Promise.of(badRequest("Invalid state: " + state)));
                                } catch (IllegalArgumentException e) {
                                    return Promise.of(badRequest("Invalid state: " + state));
                                }
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * List all learning deltas for a tenant with governance check.
     */
    public Promise<HttpResponse> listAll(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        String agentId = request.getQueryParameter("agentId");
        String limitStr = request.getQueryParameter("limit");
        String offsetStr = request.getQueryParameter("offset");

        Integer limit = limitStr != null ? Integer.parseInt(limitStr) : 100;
        Integer offset = offsetStr != null ? Integer.parseInt(offsetStr) : 0;

        // Governance check: tenant must have access to learning delta data
        return approvalService.checkAccess(tenantId, "learning:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to learning delta data"));
                    }

                    return repository.findByTenant(tenantId, agentId, limit, offset)
                            .map(this::ok)
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }

    /**
     * Get state distribution for learning deltas with governance check.
     */
    public Promise<HttpResponse> getStateDistribution(@NotNull HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("tenantId is required"));
        }

        // Governance check: tenant must have access to learning delta data
        return approvalService.checkAccess(tenantId, "learning:read")
                .then(hasAccess -> {
                    if (!hasAccess) {
                        return Promise.of(forbidden("Access denied to learning delta data"));
                    }

                    // Get all deltas for tenant and count by state
                    return repository.findByTenant(tenantId, null, 10000, 0)
                            .map(deltas -> {
                                Map<LearningDeltaState, Long> distribution = deltas.stream()
                                        .collect(java.util.stream.Collectors.groupingBy(
                                                LearningDelta::state,
                                                java.util.stream.Collectors.counting()
                                        ));
                                return ok(distribution);
                            })
                            .whenException(e -> Promise.of(internalError(e)));
                });
    }
}
