/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.memory;

import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Fast, thread-safe in-memory implementation of {@link MemoryNamespaceRepository}.
 *
 * <p>Intended for contract tests and unit tests. Not suitable for production use.
 *
 * @doc.type class
 * @doc.purpose In-memory MemoryNamespaceRepository for testing
 * @doc.layer framework
 * @doc.pattern Repository
 */
public final class InMemoryMemoryNamespaceRepository implements MemoryNamespaceRepository {

    private final Map<String, MemoryNamespace> store = new ConcurrentHashMap<>();

    @Override
    public Promise<MemoryNamespace> save(MemoryNamespace namespace) {
        store.put(namespace.namespaceId(), namespace);
        return Promise.of(namespace);
    }

    @Override
    public Promise<Optional<MemoryNamespace>> findById(String namespaceId) {
        return Promise.of(Optional.ofNullable(store.get(namespaceId)));
    }

    @Override
    public Promise<List<MemoryNamespace>> findByAgent(String agentId, String tenantId) {
        List<MemoryNamespace> results = store.values().stream()
                .filter(ns -> ns.agentId().equals(agentId) && ns.tenantId().equals(tenantId))
                .collect(Collectors.toCollection(ArrayList::new));
        return Promise.of(results);
    }

    @Override
    public Promise<List<MemoryNamespace>> findPromotionEnabledByAgent(String agentId, String tenantId) {
        List<MemoryNamespace> results = store.values().stream()
                .filter(ns -> ns.agentId().equals(agentId)
                        && ns.tenantId().equals(tenantId)
                        && ns.promotionEnabled())
                .collect(Collectors.toCollection(ArrayList::new));
        return Promise.of(results);
    }

    @Override
    public Promise<Optional<MemoryNamespace>> findByAgentAndScope(
            String agentId, MemoryScope scope, String tenantId) {
        Optional<MemoryNamespace> result = store.values().stream()
                .filter(ns -> ns.agentId().equals(agentId)
                        && ns.scope() == scope
                        && ns.tenantId().equals(tenantId))
                .findFirst();
        return Promise.of(result);
    }

    @Override
    public Promise<Boolean> delete(String namespaceId, String tenantId) {
        MemoryNamespace existing = store.get(namespaceId);
        if (existing == null || !existing.tenantId().equals(tenantId)) {
            return Promise.of(false);
        }
        store.remove(namespaceId);
        return Promise.of(true);
    }
}
