package com.ghatana.platform.domain.auth;

import java.util.Objects;
import java.util.UUID;

/**
 * @doc.type record
 * @doc.purpose Self-contained tenant identifier for extracted kernel plugin APIs.
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record TenantId(String value) {

    public TenantId {
        Objects.requireNonNull(value, "TenantId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TenantId cannot be blank");
        }
    }

    public static TenantId of(String value) {
        return new TenantId(value);
    }

    public static TenantId random() {
        return new TenantId("tenant-" + UUID.randomUUID());
    }

    public static TenantId system() {
        return new TenantId("system");
    }

    @Override
    public String toString() {
        return value;
    }
}