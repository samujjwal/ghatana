/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-02-04
 * DEPENDS_ON: platform:java:core
 */
package com.ghatana.platform.types.identity;

import java.util.UUID;

/**
 * Unique identifier for idempotency key.
 *
 * @doc.type record
 * @doc.purpose Key ensuring exactly-once processing semantics
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record IdempotencyKey(String value) implements Identifier {

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Idempotency key cannot be null or blank");
        }
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value);
    }

    public static IdempotencyKey random() {
        return new IdempotencyKey(UUID.randomUUID().toString());
    }

    @Override
    public String raw() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
