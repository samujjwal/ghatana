/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

/**
 * Authentication type used during login.
 *
 * <p>Distinguishes how a user authenticated (password, OAuth, API key, etc.)
 * to drive downstream security decisions.
 *
 * @doc.type enum
 * @doc.purpose Authentication method classification
 * @doc.layer core
 * @doc.pattern Value Object
 */
public enum AuthenticationType {
    PASSWORD,
    MFA,
    SSO,
    API_KEY,
    CERTIFICATE,
    BIOMETRIC,
    OAUTH2,
    INTERNAL,
    OAUTH,
    OIDC;

    /**
     * Whether this authentication type requires a password hash.
     *
     * @return true for PASSWORD and MFA
     */
    public boolean requiresPasswordHash() {
        return this == PASSWORD || this == MFA;
    }
}
