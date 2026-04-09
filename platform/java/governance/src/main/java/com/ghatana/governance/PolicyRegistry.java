/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.governance;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe registry for {@link CompiledPolicy} instances.
 *
 * <p>Policies are registered at startup or via hot-reload and queried
 * during action governance evaluation. The registry supports concurrent
 * reads and writes.
 *
 * @doc.type class
 * @doc.purpose Thread-safe registry for compiled governance policies
 * @doc.layer platform
 * @doc.pattern Registry
 */
public final class PolicyRegistry {

    private static final Logger log = LoggerFactory.getLogger(PolicyRegistry.class);

    private final CopyOnWriteArrayList<CompiledPolicy> policies = new CopyOnWriteArrayList<>();

    /**
     * Registers a compiled policy.
     *
     * @param policy the policy to register
     */
    public void register(@NotNull CompiledPolicy policy) {
        policies.add(policy);
        log.info("Registered governance policy: {} (priority={})", policy.id(), policy.priority());
    }

    /**
     * Replaces all policies with the given list (for hot-reload).
     *
     * @param newPolicies the replacement policy set
     */
    public void replaceAll(@NotNull List<CompiledPolicy> newPolicies) {
        policies.clear();
        policies.addAll(newPolicies);
        log.info("Replaced all governance policies ({} loaded)", newPolicies.size());
    }

    /**
     * Returns all policies that match the given evaluation context, ordered by priority.
     *
     * @param ctx the evaluation context
     * @return matching policies sorted by priority (ascending)
     */
    @NotNull
    public List<CompiledPolicy> findMatching(@NotNull PolicyEvaluationContext ctx) {
        return policies.stream()
                .filter(p -> p.matches(ctx))
                .sorted((a, b) -> Integer.compare(a.priority(), b.priority()))
                .toList();
    }

    /**
     * Returns all registered policies.
     */
    @NotNull
    public List<CompiledPolicy> getAll() {
        return List.copyOf(policies);
    }

    /**
     * Returns the number of registered policies.
     */
    public int size() {
        return policies.size();
    }
}
