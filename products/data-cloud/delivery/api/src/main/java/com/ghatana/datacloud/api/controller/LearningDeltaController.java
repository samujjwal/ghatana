/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.controller;

import com.ghatana.agent.learning.delta.LearningDelta;
import com.ghatana.agent.learning.delta.LearningDeltaState;
import com.ghatana.platform.http.server.JsonServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * ActiveJ servlet for learning delta operations.
 *
 * @doc.type class
 * @doc.purpose ActiveJ servlet for learning delta operations
 * @doc.layer data-cloud
 * @doc.pattern Servlet
 */
public class LearningDeltaController extends JsonServlet {

    private final com.ghatana.agent.learning.delta.LearningDeltaRepository repository;
    private final com.ghatana.agent.learning.delta.LearningDeltaService service;

    public LearningDeltaController(
            @NotNull com.ghatana.agent.learning.delta.LearningDeltaRepository repository,
            @NotNull com.ghatana.agent.learning.delta.LearningDeltaService service
    ) {
        this.repository = repository;
        this.service = service;
    }

    /**
     * Save a learning delta.
     */
    public Promise<HttpResponse> save(@NotNull HttpRequest request) {
        return parseBodyAsync(request, LearningDelta.class)
                .then(delta -> {
                    // TODO: Get contract from context
                    com.ghatana.agent.learning.LearningContract contract = new com.ghatana.agent.learning.LearningContract(
                            com.ghatana.agent.learning.LearningLevel.L2,
                            java.util.Set.of(),
                            true,
                            false
                    );
                    return service.propose(delta, contract);
                })
                .map(this::created)
                .whenException(e -> Promise.of(internalError(e)));
    }

    /**
     * Get a learning delta by ID.
     */
    public Promise<HttpResponse> getById(@NotNull HttpRequest request) {
        String deltaId = request.getPathParameter("deltaId");
        return repository.findById(deltaId)
                .map(optional -> optional
                        .map(this::ok)
                        .orElseGet(() -> notFound("Learning delta not found")));
    }

    /**
     * Find pending learning deltas for an agent.
     */
    public Promise<HttpResponse> findPending(@NotNull HttpRequest request) {
        String agentId = request.getPathParameter("agentId");
        return repository.findPending(agentId)
                .map(this::ok);
    }

    /**
     * Evaluate a learning delta.
     */
    public Promise<HttpResponse> evaluate(@NotNull HttpRequest request) {
        String deltaId = request.getPathParameter("deltaId");
        return service.evaluate(deltaId)
                .map(result -> ok(java.util.Map.of("success", result)));
    }

    /**
     * Transition a learning delta to a new state.
     */
    public Promise<HttpResponse> transition(@NotNull HttpRequest request) {
        String deltaId = request.getPathParameter("deltaId");
        String state = request.getQueryParameter("state");
        return repository.transition(deltaId, LearningDeltaState.valueOf(state))
                .map(this::ok)
                .whenException(e -> Promise.of(badRequest("Invalid state: " + state)));
    }
}
