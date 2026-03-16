package com.ghatana.appplatform.refdata.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * @doc.type       Domain Record
 * @doc.purpose    Entity master record for legal and market participants: issuers,
 *                 brokers, custodians, exchanges, regulators, and banks.
 *                 Temporal versioning via effectiveFrom/effectiveTo (SCD Type-2).
 * @doc.layer      Domain
 * @doc.pattern    Immutable Value Object / SCD Type-2
 */
public record MarketEntity(
        UUID id,
        EntityType entityType,
        String name,
        String shortName,
        String registrationNumber,
        String country,
        String status,              // ACTIVE / INACTIVE
        LocalDate effectiveFrom,
        LocalDate effectiveTo,      // null = current version
        Instant createdAtUtc,
        Map<String, Object> metadata
) {
    public boolean isCurrent() {
        return effectiveTo == null;
    }
}
