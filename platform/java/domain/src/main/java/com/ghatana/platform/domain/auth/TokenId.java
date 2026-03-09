/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for issued tokens (access, refresh, ID tokens).
 *
 * @doc.type record
 * @doc.purpose Token identifier
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record TokenId(String value) {
    
    public TokenId {
        Objects.requireNonNull(value, "TokenId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TokenId cannot be blank");
        }
    }
    
    public static TokenId of(String value) {
        return new TokenId(value);
    }
    
    public static TokenId random() {
        return new TokenId("token-" + UUID.randomUUID());
    }
}
