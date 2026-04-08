/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.supervision;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-process implementation of {@link SupervisionRegistry}.
 *
 * @doc.type class
 * @doc.purpose In-memory supervision registry for platform use and testing
 * @doc.layer platform
 * @doc.pattern Repository / Default Implementation
 */
public final class InMemorySupervisionRegistry implements SupervisionRegistry {

    private final Map<String, SupervisionContract> bySupervisor = new ConcurrentHashMap<>();

    @Override
    public void register(SupervisionContract contract) {
        if (contract == null) {
            throw new IllegalArgumentException("contract must not be null");
        }
        bySupervisor.put(contract.supervisorAgentId(), contract);
    }

    @Override
    public Optional<SupervisionContract> findBySupervisor(String supervisorAgentId) {
        if (supervisorAgentId == null || supervisorAgentId.isBlank()) {
            throw new IllegalArgumentException("supervisorAgentId must not be blank");
        }
        return Optional.ofNullable(bySupervisor.get(supervisorAgentId));
    }

    @Override
    public Optional<SupervisionContract> findBySubordinate(String subordinateAgentId) {
        if (subordinateAgentId == null || subordinateAgentId.isBlank()) {
            throw new IllegalArgumentException("subordinateAgentId must not be blank");
        }
        return bySupervisor.values().stream()
                .filter(c -> c.subordinateAgentIds().contains(subordinateAgentId))
                .findFirst();
    }

    @Override
    public void deregister(String supervisorAgentId) {
        if (supervisorAgentId == null || supervisorAgentId.isBlank()) {
            throw new IllegalArgumentException("supervisorAgentId must not be blank");
        }
        bySupervisor.remove(supervisorAgentId);
    }
}
