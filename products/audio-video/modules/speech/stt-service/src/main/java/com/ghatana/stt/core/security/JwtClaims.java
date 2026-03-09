package com.ghatana.stt.core.security;

import java.util.Set;

/**
 * JWT token claims extracted from a validated token.
 *
 * @doc.type record
 * @doc.purpose Holds JWT token claims
 * @doc.layer security
 */
public record JwtClaims(String userId, Set<String> roles, long expiresAt) {
    public JwtClaims {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (roles == null) {
            roles = Set.of();
        }
    }
}
