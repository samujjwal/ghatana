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
 * Emits threshold-crossing events when a new ownership link causes any beneficial owner's
 * cumulative stake to cross a regulatory threshold (K01-021).
 *
 * <p>Monitored thresholds (ascending):
 * <ul>
 *   <li>5%  — disclosure-level monitoring</li>
 *   <li>10% — FATF beneficial-owner definition</li>
 *   <li>25% — EU 4AMLD significant control</li>
 *   <li>50% — majority control</li>
 *   <li>75% — dominant control</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Emits regulatory threshold-crossing events for beneficial ownership (K01-021)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class OwnershipThresholdWatcher {

    private static final Logger log = LoggerFactory.getLogger(OwnershipThresholdWatcher.class);

    private static final List<BigDecimal> THRESHOLDS = List.of(
            new BigDecimal("5.00"),
            new BigDecimal("10.00"),
            new BigDecimal("25.00"),
            new BigDecimal("50.00"),
            new BigDecimal("75.00")
    );

    private final BeneficialOwnershipStore store;
    private final ThresholdEventPublisher publisher;

    public OwnershipThresholdWatcher(BeneficialOwnershipStore store,
                                     ThresholdEventPublisher publisher) {
        this.store     = Objects.requireNonNull(store, "store");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    /**
     * Called after a new ownership link is persisted. Re-calculates cumulative stakes
     * for all beneficial owners of the affected child entity and publishes crossing events.
     */
    public Promise<Void> onLinkRegistered(OwnershipLink link) {
        // Re-scan from the lowest threshold so we catch all crossings in one pass.
        return store.findBeneficialOwners(
                link.childId(), link.tenantId(),
                THRESHOLDS.get(0), 10)
            .then(owners -> publishCrossings(owners, link));
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private Promise<Void> publishCrossings(
            List<BeneficialOwnershipStore.BeneficialOwner> owners, OwnershipLink link) {

        for (BeneficialOwnershipStore.BeneficialOwner owner : owners) {
            for (BigDecimal threshold : THRESHOLDS) {
                if (owner.cumulativePercent().compareTo(threshold) >= 0) {
                    log.warn("Ownership threshold crossed: owner={} entity={} pct={}% threshold={}%",
                            owner.ownerId(), owner.entityId(),
                            owner.cumulativePercent(), threshold);
                    publisher.onThresholdCrossed(new ThresholdEvent(
                        owner.ownerId(),
                        owner.entityId(),
                        link.tenantId(),
                        threshold,
                        owner.cumulativePercent()
                    ));
                }
            }
        }
        return Promise.complete();
    }

    // ─── Event types ──────────────────────────────────────────────────────────

    /**
     * Event published when a beneficial owner crosses a regulatory percentage threshold.
     *
     * @param ownerId            beneficial owner entity ID
     * @param entityId           the owned entity
     * @param tenantId           tenant scope
     * @param thresholdCrossed   exact regulatory threshold that was reached
     * @param actualPercent      calculated cumulative ownership percentage
     */
    public record ThresholdEvent(
            String ownerId,
            String entityId,
            String tenantId,
            BigDecimal thresholdCrossed,
            BigDecimal actualPercent
    ) {}

    /**
     * Publisher port — callers inject an implementation appropriate to their
     * infrastructure (e.g., event bus, Kafka, audit log).
     *
     * @doc.type interface
     * @doc.purpose Output port for ownership threshold-crossing events
     * @doc.layer product
     * @doc.pattern Port
     */
    public interface ThresholdEventPublisher {
        void onThresholdCrossed(ThresholdEvent event);
    }
}
