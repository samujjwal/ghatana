/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.ownership;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates beneficial-ownership queries and registers ownership entities/links (K01-020).
 *
 * <p>Default threshold is 10% (FATF recommendation for beneficial-owner disclosure).
 * Default graph depth limit is 10 hops.
 *
 * @doc.type class
 * @doc.purpose Service for beneficial-ownership registration and UBO discovery (K01-020)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class BeneficialOwnershipService {

    private static final Logger log = LoggerFactory.getLogger(BeneficialOwnershipService.class);

    public static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("10.00");
    public static final int DEFAULT_MAX_DEPTH = 10;

    private final BeneficialOwnershipStore store;
    private final OwnershipThresholdWatcher thresholdWatcher;

    public BeneficialOwnershipService(BeneficialOwnershipStore store,
                                      OwnershipThresholdWatcher thresholdWatcher) {
        this.store            = Objects.requireNonNull(store, "store");
        this.thresholdWatcher = Objects.requireNonNull(thresholdWatcher, "thresholdWatcher");
    }

    // ─── Registration ─────────────────────────────────────────────────────────

    /** Registers or updates an ownership entity. */
    public Promise<Void> registerEntity(OwnershipEntity entity) {
        return store.saveEntity(entity);
    }

    /**
     * Records a new ownership link and notifies the {@link OwnershipThresholdWatcher}
     * so threshold-crossing events are emitted if applicable.
     */
    public Promise<Void> registerLink(OwnershipLink link) {
        return store.saveLink(link)
                .then(() -> thresholdWatcher.onLinkRegistered(link));
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    /**
     * Discovers all ultimate beneficial owners of {@code entityId} at or above the
     * default 10% threshold.
     */
    public Promise<List<BeneficialOwnershipStore.BeneficialOwner>> findBeneficialOwners(
            String entityId, String tenantId) {
        return findBeneficialOwners(entityId, tenantId, DEFAULT_THRESHOLD, DEFAULT_MAX_DEPTH);
    }

    /** Discovers beneficial owners with an explicit threshold. */
    public Promise<List<BeneficialOwnershipStore.BeneficialOwner>> findBeneficialOwners(
            String entityId, String tenantId, BigDecimal threshold, int maxDepth) {
        log.debug("UBO query: entity={} tenant={} threshold={}%", entityId, tenantId, threshold);
        return store.findBeneficialOwners(entityId, tenantId, threshold, maxDepth);
    }

    /** Returns direct ownership links for an entity (immediate parents, no recursion). */
    public Promise<List<OwnershipLink>> findDirectLinks(String entityId, String tenantId) {
        return store.findDirectLinks(entityId, tenantId);
    }
}
