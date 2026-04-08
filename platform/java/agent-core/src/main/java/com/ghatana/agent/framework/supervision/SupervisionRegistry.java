/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.supervision;

import java.util.Optional;

/**
 * Registry that maps supervisor agent IDs to their active {@link SupervisionContract}.
 *
 * <p>Each supervisor may have at most one active contract at a time.
 *
 * @doc.type interface
 * @doc.purpose SPI for storing and querying supervision contracts
 * @doc.layer platform
 * @doc.pattern Registry
 */
public interface SupervisionRegistry {

    /**
     * Registers or replaces the supervision contract for the given supervisor.
     *
     * @param contract the contract to register; never null
     */
    void register(SupervisionContract contract);

    /**
     * Finds the active supervision contract for a given supervisor agent.
     *
     * @param supervisorAgentId the supervisor's agent ID; never blank
     * @return an {@link Optional} containing the contract, or empty if not registered
     */
    Optional<SupervisionContract> findBySupervisor(String supervisorAgentId);

    /**
     * Finds the supervision contract under which the given agent is being supervised.
     *
     * @param subordinateAgentId the supervised agent's ID; never blank
     * @return an {@link Optional} containing the supervising contract, or empty if none
     */
    Optional<SupervisionContract> findBySubordinate(String subordinateAgentId);

    /**
     * Removes the supervision contract for the given supervisor.
     *
     * @param supervisorAgentId the supervisor's agent ID
     */
    void deregister(String supervisorAgentId);
}
