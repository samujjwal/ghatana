/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link WorkflowDefinitionRegistry} for testing and development.
 *
 * <p>Stores definitions in a {@link ConcurrentHashMap} keyed by workflow ID.
 * Versioning is supported: registering a definition with the same workflow ID
 * but higher version replaces the "latest" reference while retaining older versions
 * for lookup.
 *
 * @doc.type class
 * @doc.purpose In-memory workflow definition store for tests
 * @doc.layer platform
 * @doc.pattern Repository
 */
public final class InMemoryWorkflowDefinitionRegistry implements WorkflowDefinitionRegistry {

    // workflowId → (version → definition)
    private final ConcurrentHashMap<String, NavigableMap<Integer, WorkflowDefinition>> store =
        new ConcurrentHashMap<>();

    @Override
    public Promise<Void> register(@NotNull WorkflowDefinition definition) {
        store.computeIfAbsent(definition.workflowId(), k -> new TreeMap<>())
             .put(definition.version(), definition);
        return Promise.complete();
    }

    @Override
    public Promise<Optional<WorkflowDefinition>> findLatest(@NotNull String workflowId) {
        NavigableMap<Integer, WorkflowDefinition> versions = store.get(workflowId);
        if (versions == null || versions.isEmpty()) {
            return Promise.of(Optional.empty());
        }
        return Promise.of(Optional.of(versions.lastEntry().getValue()));
    }

    @Override
    public Promise<Optional<WorkflowDefinition>> findByVersion(@NotNull String workflowId, int version) {
        NavigableMap<Integer, WorkflowDefinition> versions = store.get(workflowId);
        if (versions == null) {
            return Promise.of(Optional.empty());
        }
        return Promise.of(Optional.ofNullable(versions.get(version)));
    }

    @Override
    public Promise<List<WorkflowDefinition>> listAll() {
        List<WorkflowDefinition> result = new ArrayList<>();
        for (NavigableMap<Integer, WorkflowDefinition> versions : store.values()) {
            if (!versions.isEmpty()) {
                result.add(versions.lastEntry().getValue());
            }
        }
        return Promise.of(List.copyOf(result));
    }

    @Override
    public Promise<Void> remove(@NotNull String workflowId) {
        store.remove(workflowId);
        return Promise.complete();
    }

    /** Returns the number of distinct workflow IDs registered. */
    public int size() {
        return store.size();
    }

    /** Removes all definitions (for test teardown). */
    public void clear() {
        store.clear();
    }
}
