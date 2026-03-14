/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.ownership;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A directed ownership edge: {@code parent} owns {@code percentage} of {@code child}.
 *
 * <p>Percentage is stored as a decimal 0–100 (e.g., 51.5 = 51.5%).
 * A {@code null} validTo means the link is currently active.
 *
 * @doc.type record
 * @doc.purpose Immutable ownership link (edge) in the beneficial-ownership graph (K01-019)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record OwnershipLink(
        String parentId,
        String childId,
        RelationshipType relationshipType,
        BigDecimal percentage,   // 0 – 100
        String tenantId,
        Instant validFrom,
        Instant validTo          // null = currently active
) {
    public enum RelationshipType {
        OWNS_PERCENTAGE,
        CONTROLS,
        BENEFICIARY_OF
    }

    /** Returns {@code true} when this link is active at the given point in time. */
    public boolean activeAt(Instant instant) {
        return !instant.isBefore(validFrom)
                && (validTo == null || instant.isBefore(validTo));
    }
}
