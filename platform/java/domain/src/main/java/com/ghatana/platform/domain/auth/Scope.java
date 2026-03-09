/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.util.Objects;

/**
 * OAuth scope representing a permission boundary.
 *
 * @doc.type record
 * @doc.purpose OAuth scope identifier
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record Scope(String value) {
    
    public Scope {
        Objects.requireNonNull(value, "Scope cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Scope cannot be blank");
        }
    }
    
    public static Scope of(String value) {
        return new Scope(value);
    }
    
    @Override
    public String toString() {
        return value;
    }
}
