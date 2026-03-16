package com.ghatana.appplatform.sanctions.domain;

import java.util.List;

/**
 * @doc.type    Record (Immutable Value Object)
 * @doc.purpose Input to the screening engine (D14-001, D14-002).
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public record ScreeningRequest(
        String requestId,
        String name,
        ScreeningEntityType entityType,
        String nationality,         // optional — improves precision
        String dateOfBirth,         // optional — YYYY-MM-DD
        List<String> identifiers    // national IDs, passport numbers, etc.
) {}
