/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.evaluation;

import com.ghatana.agent.evaluation.EvaluationResult;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data Cloud-backed repository for evaluation results.
 *
 * <p>TODO: Replace in-memory storage with actual Data Cloud persistence.
 *
 * @doc.type class
 * @doc.purpose Data Cloud repository for evaluation results
 * @doc.layer data-cloud
 * @doc.pattern Repository
 */
public final class DataCloudEvaluationResultRepository {

    private final ConcurrentHashMap<String, EvaluationResult> results = new ConcurrentHashMap<>();

    /**
     * Saves an evaluation result.
     *
     * @param result evaluation result to save
     * @return promise of saved result
     */
    @NotNull
    public Promise<EvaluationResult> save(@NotNull EvaluationResult result) {
        results.put(result.resultId(), result);
        return Promise.of(result);
    }

    /**
     * Finds an evaluation result by ID.
     *
     * @param resultId result identifier
     * @return promise of optional result
     */
    @NotNull
    public Promise<Optional<EvaluationResult>> findById(@NotNull String resultId) {
        return Promise.of(Optional.ofNullable(results.get(resultId)));
    }

    /**
     * Finds evaluation results for a specific delta.
     *
     * @param deltaId delta identifier
     * @return promise of list of results
     */
    @NotNull
    public Promise<List<EvaluationResult>> findByDeltaId(@NotNull String deltaId) {
        return Promise.of(results.values().stream()
                .filter(r -> r.deltaId().equals(deltaId))
                .toList());
    }

    /**
     * Finds evaluation results for a specific pack.
     *
     * @param packId pack identifier
     * @return promise of list of results
     */
    @NotNull
    public Promise<List<EvaluationResult>> findByPackId(@NotNull String packId) {
        return Promise.of(results.values().stream()
                .filter(r -> r.packId().equals(packId))
                .toList());
    }
}
