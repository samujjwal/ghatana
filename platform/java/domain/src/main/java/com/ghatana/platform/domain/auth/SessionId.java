/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for sessions.
 *
 * @doc.type record
 * @doc.purpose Session identifier
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record SessionId(String value) {
    
    public SessionId {
        Objects.requireNonNull(value, "SessionId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("SessionId cannot be blank");
        }
    }
    
    public static SessionId of(String value) {
        return new SessionId(value);
    }

    public static SessionId random() {
        return new SessionId(UUID.randomUUID().toString());
    }
}
