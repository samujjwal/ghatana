/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.controller;

import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryTransition;
import com.ghatana.agent.mastery.MasteryTransitionResult;
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
 * @doc.type class
 * @doc.purpose ActiveJ servlet for mastery operations
 * @doc.layer data-cloud
 * @doc.pattern Servlet
 */
public class MasteryController extends JsonServlet {

    private final com.ghatana.agent.mastery.MasteryRegistry masteryRegistry;

    public MasteryController(@NotNull com.ghatana.agent.mastery.MasteryRegistry masteryRegistry) {
        this.masteryRegistry = masteryRegistry;
    }

    /**
     * Query mastery items.
     */
    public Promise<HttpResponse> queryMastery(@NotNull HttpRequest request) {
        String skillId = request.getQueryParameter("skillId");
        String agentId = request.getQueryParameter("agentId");
        String state = request.getQueryParameter("state");
        String limitStr = request.getQueryParameter("limit");
        String offsetStr = request.getQueryParameter("offset");

        Integer limit = limitStr != null ? Integer.parseInt(limitStr) : 100;
        Integer offset = offsetStr != null ? Integer.parseInt(offsetStr) : 0;

        MasteryQuery query = MasteryQuery.bySkill(skillId != null ? skillId : "")
                .withLimit(limit)
                .withOffset(offset);

        if (agentId != null) {
            query = new MasteryQuery(
                    skillId,
                    agentId,
                    null,
                    null,
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
                .map(this::ok);
    }

    /**
     * Get a mastery item by ID.
     */
    public Promise<HttpResponse> getMastery(@NotNull HttpRequest request) {
        String masteryId = request.getPathParameter("masteryId");
        // TODO: Implement getById in MasteryRegistry
        return promiseOf(ok(Optional.empty()));
    }

    /**
     * Save a mastery item.
     */
    public Promise<HttpResponse> saveMastery(@NotNull HttpRequest request) {
        return parseBodyAsync(request, MasteryItem.class)
                .then(item -> masteryRegistry.save(item))
                .map(this::created)
                .whenException(e -> Promise.of(internalError(e)));
    }

    /**
     * Transition a mastery item to a new state.
     */
    public Promise<HttpResponse> transition(@NotNull HttpRequest request) {
        return parseBodyAsync(request, MasteryTransition.class)
                .then(transition -> masteryRegistry.transition(transition))
                .map(this::ok)
                .whenException(e -> Promise.of(internalError(e)));
    }

    /**
     * Find stale mastery items.
     */
    public Promise<HttpResponse> findStale(@NotNull HttpRequest request) {
        return masteryRegistry.findStale(java.time.Instant.now())
                .map(this::ok);
    }
}
