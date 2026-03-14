/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents persisted OAuth 2.0 client credentials (K01-002).
 *
 * <p>The {@code clientSecretHash} is a bcrypt hash — never the raw secret.
 * Use {@link #hasSecret(String, String)} for constant-time comparison.
 *
 * @doc.type record
 * @doc.purpose OAuth 2.0 client credentials domain model (K01-002)
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record ClientCredentials(
        UUID clientId,
        String clientIdStr,           // human-readable, used in requests
        String clientSecretHash,      // bcrypt hash
        UUID tenantId,
        List<String> grantedScopes,
        ClientStatus status
) {
    public ClientCredentials {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(clientIdStr, "clientIdStr");
        Objects.requireNonNull(clientSecretHash, "clientSecretHash");
        Objects.requireNonNull(tenantId, "tenantId");
        grantedScopes = grantedScopes != null ? List.copyOf(grantedScopes) : List.of();
        status = status != null ? status : ClientStatus.ACTIVE;
    }

    public enum ClientStatus { ACTIVE, SUSPENDED, REVOKED }

    public boolean isActive() {
        return status == ClientStatus.ACTIVE;
    }
}
