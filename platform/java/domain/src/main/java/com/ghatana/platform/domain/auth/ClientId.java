/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for OAuth clients.
 *
 * @doc.type record
 * @doc.purpose OAuth client identifier
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ClientId(String value) {
    
    public ClientId {
        Objects.requireNonNull(value, "ClientId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ClientId cannot be blank");
        }
    }
    
    public static ClientId of(String value) {
        return new ClientId(value);
    }
    
    public static ClientId random() {
        return new ClientId("client-" + UUID.randomUUID());
    }
}
