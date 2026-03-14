/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.ownership;

import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.util.List;

/**
 * Storage port for beneficial-ownership entities and links (K01-019).
 *
 * <p>Implementations use a recursive-CTE SQL query (PostgreSQL {@code WITH RECURSIVE})
 * to traverse the ownership graph up to a configurable depth limit.
 *
 * @doc.type interface
 * @doc.purpose Port for CRUD and recursive graph traversal of ownership data
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface BeneficialOwnershipStore {

    // ─── Entities ─────────────────────────────────────────────────────────────

    Promise<Void> saveEntity(OwnershipEntity entity);

    Promise<OwnershipEntity> findEntity(String entityId, String tenantId);

    // ─── Links ────────────────────────────────────────────────────────────────

    Promise<Void> saveLink(OwnershipLink link);

    /** All active direct links where {@code childId} is the owned entity. */
    Promise<List<OwnershipLink>> findDirectLinks(String entityId, String tenantId);

    // ─── Traversal ────────────────────────────────────────────────────────────

    /**
     * Traverse the ownership graph upward from {@code entityId} and return all
     * ultimate beneficial owners whose cumulative percentage equals or exceeds
     * {@code thresholdPercent}.
     *
     * @param entityId         root entity to start from
     * @param tenantId         tenant scope
     * @param thresholdPercent minimum cumulative ownership (e.g., 10 = 10%)
     * @param maxDepth         maximum recursive graph depth
     * @return list of discovered beneficial owner results
     */
    Promise<List<BeneficialOwner>> findBeneficialOwners(
            String entityId, String tenantId,
            BigDecimal thresholdPercent, int maxDepth);

    /**
     * Projection returned from the graph traversal — an ultimate owner with
     * cumulative percentage.
     *
     * @param ownerId          beneficial owner entity ID
     * @param entityId         entity they ultimately own
     * @param tenantId         tenant scope
     * @param cumulativePercent compounded ownership across the graph path
     * @param depth            number of hops from root
     */
    record BeneficialOwner(
            String ownerId,
            String entityId,
            String tenantId,
            BigDecimal cumulativePercent,
            int depth
    ) {}
}
