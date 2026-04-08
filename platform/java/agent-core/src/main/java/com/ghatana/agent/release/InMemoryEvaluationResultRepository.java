/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release;

import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Fast, thread-safe in-memory implementation of {@link EvaluationResultRepository}.
 *
 * <p>Intended for contract tests and unit tests. Not suitable for production use.
 *
 * @doc.type class
 * @doc.purpose In-memory EvaluationResultRepository for testing
 * @doc.layer platform
 * @doc.pattern Repository
 */
public final class InMemoryEvaluationResultRepository implements EvaluationResultRepository {

    private final Map<String, EvaluationResult> store = new ConcurrentHashMap<>();

    @Override
    public Promise<EvaluationResult> save(EvaluationResult result) {
        store.put(result.evaluationId(), result);
        return Promise.of(result);
    }

    @Override
    public Promise<Optional<EvaluationResult>> findById(String evaluationId) {
        return Promise.of(Optional.ofNullable(store.get(evaluationId)));
    }

    @Override
    public Promise<List<EvaluationResult>> findByRelease(String agentReleaseId, String tenantId) {
        List<EvaluationResult> results = store.values().stream()
                .filter(r -> r.agentReleaseId().equals(agentReleaseId) && r.tenantId().equals(tenantId))
                .collect(Collectors.toCollection(ArrayList::new));
        return Promise.of(results);
    }

    @Override
    public Promise<List<EvaluationResult>> findPassingByRelease(String agentReleaseId, String tenantId) {
        List<EvaluationResult> results = store.values().stream()
                .filter(r -> r.agentReleaseId().equals(agentReleaseId)
                        && r.tenantId().equals(tenantId)
                        && r.passed())
                .collect(Collectors.toCollection(ArrayList::new));
        return Promise.of(results);
    }

    @Override
    public Promise<Long> countPassing(String agentReleaseId, String tenantId) {
        long count = store.values().stream()
                .filter(r -> r.agentReleaseId().equals(agentReleaseId)
                        && r.tenantId().equals(tenantId)
                        && r.passed())
                .count();
        return Promise.of(count);
    }

    @Override
    public Promise<Long> deleteByRelease(String agentReleaseId, String tenantId) {
        List<String> toDelete = store.entrySet().stream()
                .filter(e -> e.getValue().agentReleaseId().equals(agentReleaseId)
                        && e.getValue().tenantId().equals(tenantId))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayList::new));
        toDelete.forEach(store::remove);
        return Promise.of((long) toDelete.size());
    }
}
