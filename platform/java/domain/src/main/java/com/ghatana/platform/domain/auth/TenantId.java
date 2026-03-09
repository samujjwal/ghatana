/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.util.Objects;
import java.util.UUID;

/**
 * Canonical tenant identifier for multi-tenant isolation.
 *
 * <p>Provides type-safe tenant identification across all platform modules
 * and products. All authentication, authorization, and data isolation
 * operations are scoped to a tenant.</p>
 *
 * <p><b>Usage</b></p>
 * <pre>{@code
 * TenantId tenantId = TenantId.of("tenant-123");
 * TenantId newId = TenantId.random();
 * TenantId sys = TenantId.system();
 * String raw = tenantId.value();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Unique identifier for tenant isolation
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record TenantId(String value) {
    
    public TenantId {
        Objects.requireNonNull(value, "TenantId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TenantId cannot be blank");
        }
    }
    
    /** Create a TenantId from a string value. */
    public static TenantId of(String value) {
        return new TenantId(value);
    }
    
    /** Generate a random tenant ID (prefixed with "tenant-"). */
    public static TenantId random() {
        return new TenantId("tenant-" + UUID.randomUUID());
    }
    
    /** Alias for {@link #random()} for backward compatibility. */
    public static TenantId generate() {
        return random();
    }
    
    /** The system tenant, used for platform-level operations. */
    public static TenantId system() {
        return new TenantId("system");
    }
    
    /** Get the string value. Alias for the record accessor {@link #value()}. */
    public String getValue() {
        return value;
    }
}
