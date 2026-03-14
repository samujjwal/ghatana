/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.ownership;

import java.time.Instant;

/**
 * Immutable participant in an ownership graph — a person, legal entity, or account.
 *
 * @doc.type record
 * @doc.purpose Value object representing a beneficial-ownership graph node (K01-019)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record OwnershipEntity(
        String id,
        String name,
        EntityType entityType,
        String tenantId,
        Instant createdAt
) {
    public enum EntityType {
        PERSON,
        LEGAL_ENTITY,
        ACCOUNT
    }
}
