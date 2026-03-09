/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for users.
 *
 * <p>This is the canonical UserId for the platform.
 *
 * @doc.type record
 * @doc.purpose User identifier
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record UserId(String value) {
    
    public UserId {
        Objects.requireNonNull(value, "UserId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("UserId cannot be blank");
        }
    }
    
    public static UserId of(String value) {
        return new UserId(value);
    }
    
    public static UserId random() {
        return new UserId("user-" + UUID.randomUUID());
    }
}
