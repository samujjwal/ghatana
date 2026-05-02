package com.ghatana.digitalmarketing.domain.proposal;

import java.util.Objects;

/**
 * A single deliverable included in a DMOS proposal.
 *
 * <p>Describes one work item: its type, human-readable description, expected
 * timeline in days, unit of measurement, and planned quantity.</p>
 *
 * @param deliverableType short identifier for the deliverable category
 *                        (e.g. {@code "GOOGLE_SEARCH_CAMPAIGN"})
 * @param description     human-readable description of the deliverable
 * @param timelineDays    expected time to complete in calendar days (must be &gt; 0)
 * @param unit            unit of measure (e.g. {@code "campaign"}, {@code "asset"})
 * @param quantity        number of units included (must be &gt; 0)
 *
 * @doc.type class
 * @doc.purpose Proposal deliverable value object for DMOS proposals
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ProposalDeliverable(
        String deliverableType,
        String description,
        int timelineDays,
        String unit,
        int quantity) {

    /**
     * Compact constructor — validates all fields.
     */
    public ProposalDeliverable {
        Objects.requireNonNull(deliverableType, "deliverableType must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        if (deliverableType.isBlank()) {
            throw new IllegalArgumentException("deliverableType must not be blank");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (unit.isBlank()) {
            throw new IllegalArgumentException("unit must not be blank");
        }
        if (timelineDays <= 0) {
            throw new IllegalArgumentException("timelineDays must be positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
