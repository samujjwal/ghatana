package com.ghatana.appplatform.sanctions.domain;

import java.util.List;

/**
 * @doc.type    Record (Immutable Value Object)
 * @doc.purpose A single entry in a sanctions list (D14-001, D14-009).
 *              Aliases include all AKA names for name-matching expansion.
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public record SanctionsEntry(
        String entryId,
        SanctionsListType listType,
        String primaryName,
        List<String> aliases,       // All AKA names for alias expansion
        String entityType,          // INDIVIDUAL or ENTITY
        String dateOfBirth,         // null for entities
        String nationality,         // ISO-3166 alpha-2
        String listVersion          // version tag from the source list update
) {}
