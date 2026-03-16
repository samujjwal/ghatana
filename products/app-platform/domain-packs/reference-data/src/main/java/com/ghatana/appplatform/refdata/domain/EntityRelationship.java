package com.ghatana.appplatform.refdata.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * @doc.type       Domain Record
 * @doc.purpose    Directed edge in the entity relationship graph.  Records which
 *                 entities stand in a structural relationship (e.g. SUBSIDIARY,
 *                 CUSTODIAN_FOR) and the period during which that relationship
 *                 was in effect.
 * @doc.layer      Domain
 * @doc.pattern    Immutable Value Object / SCD Type-2
 */
public record EntityRelationship(
        UUID id,
        UUID parentEntityId,
        UUID childEntityId,
        RelationshipType relationshipType,
        LocalDate effectiveFrom,
        LocalDate effectiveTo           // null = still active
) {
    public boolean isActive() {
        return effectiveTo == null;
    }
}
