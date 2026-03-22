/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.authentication.domain;

import java.util.Objects;

/**
 * Represents OAuth 2.0 client credentials.
 *
 * @doc.type record
 * @doc.purpose OAuth client credentials data structure
 * @doc.layer kernel
 * @doc.pattern Domain
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public record ClientCredentials(
    String clientId,
    String clientSecret
) {
    public ClientCredentials {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(clientSecret, "clientSecret");
        
        if (clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("clientId cannot be empty");
        }
        if (clientSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("clientSecret cannot be empty");
        }
    }
}
