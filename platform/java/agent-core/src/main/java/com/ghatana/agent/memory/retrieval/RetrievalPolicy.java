/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

/**
 * Policy for determining memory retrieval behavior.
 *
 * @doc.type interface
 * @doc.purpose Policy for memory retrieval
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public interface RetrievalPolicy {

    /**
     * Determines if memory should be retrieved for a given context.
     *
     * @param skillId skill identifier
     * @param agentId agent identifier
     * @param tenantId tenant identifier
     * @return true if retrieval is allowed
     */
    boolean shouldRetrieve(String skillId, String agentId, String tenantId);

    /**
     * Default retrieval policy that allows all retrieval.
     */
    RetrievalPolicy ALLOW_ALL = (skillId, agentId, tenantId) -> true;

    /**
     * Conservative retrieval policy that requires mastery.
     */
    RetrievalPolicy REQUIRE_MASTERY = (skillId, agentId, tenantId) -> false;
}
