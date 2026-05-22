package com.ghatana.digitalmarketing.domain.preference;

import java.time.Instant;
import java.util.Objects;

/**
 * Customer notification preference domain model.
 *
 * <p>Represents a customer's opt-in/opt-out preferences for SMS and email
 * notifications within a tenant context.</p>
 *
 * @doc.type record
 * @doc.purpose Domain model for customer notification preferences
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CustomerPreference(
        String subjectId,
        String tenantId,
        boolean smsEnabled,
        boolean emailEnabled,
        Instant updatedAt,
        String updatedBy
) {
    public CustomerPreference {
        Objects.requireNonNull(subjectId, "subjectId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        Objects.requireNonNull(updatedBy, "updatedBy must not be null");
    }
}
