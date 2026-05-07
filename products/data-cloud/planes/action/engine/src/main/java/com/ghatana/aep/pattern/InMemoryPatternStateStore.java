/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link PatternStateStore}.
 *
 * <p>Suitable for:
 * <ul>
 *   <li><b>Testing</b> — fast, no external dependencies</li>
 *   <li><b>Single-node deployments</b> — state survives operator restarts within the JVM</li>
 * </ul>
 *
 * <p><b>Limitations</b><br>
 * State is stored in a {@link ConcurrentHashMap} and is <em>not</em> durable across JVM restarts
 * or multiple instances. For production use, switch to {@link EventCloudPatternStateStore}.
 *
 * <p><b>Key format</b><br>
 * Internal keys use the pattern {@code "<tenantId>:<patternId>"} to provide tenant isolation.
 *
 * @param <S> the serialisable state type
 *
 * @doc.type class
 * @doc.purpose In-memory PatternStateStore for testing and single-node deployments
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class InMemoryPatternStateStore<S> implements PatternStateStore<S> {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPatternStateStore.class);

    private final Map<String, S> store = new ConcurrentHashMap<>();

    @Override
    public @NotNull Promise<Void> save(@NotNull String tenantId,
                                       @NotNull String patternId,
                                       @NotNull S state) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(patternId, "patternId must not be null");
        Objects.requireNonNull(state, "state must not be null");

        String key = compositeKey(tenantId, patternId);
        store.put(key, state);
        log.debug("Saved pattern state for key={}", key);
        return Promise.complete();
    }

    @Override
    public @NotNull Promise<Optional<S>> load(@NotNull String tenantId,
                                               @NotNull String patternId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(patternId, "patternId must not be null");

        String key = compositeKey(tenantId, patternId);
        S state = store.get(key);
        log.debug("Loaded pattern state for key={}: {}", key, state != null ? "found" : "not found");
        return Promise.of(Optional.ofNullable(state));
    }

    @Override
    public @NotNull Promise<Void> delete(@NotNull String tenantId,
                                          @NotNull String patternId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(patternId, "patternId must not be null");

        String key = compositeKey(tenantId, patternId);
        store.remove(key);
        log.debug("Deleted pattern state for key={}", key);
        return Promise.complete();
    }

    @Override
    public @NotNull Promise<Boolean> exists(@NotNull String tenantId,
                                             @NotNull String patternId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(patternId, "patternId must not be null");

        return Promise.of(store.containsKey(compositeKey(tenantId, patternId)));
    }

    /**
     * Returns the number of stored state entries (useful for testing).
     */
    public int size() {
        return store.size();
    }

    /**
     * Clears all stored state (testing utility).
     */
    public void clear() {
        store.clear();
    }

    private static String compositeKey(String tenantId, String patternId) {
        return tenantId + ':' + patternId;
    }
}
