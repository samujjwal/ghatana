/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.domain;

/**
 * The token response returned from an OAuth 2.0 grant flow (K01-002).
 *
 * <p>Serializes as:
 * <pre>
 * {
 *   "access_token": "eyJhbGc...",
 *   "token_type": "Bearer",
 *   "expires_in": 3600,
 *   "scope": "ledger:read ledger:post"
 * }
 * </pre>
 *
 * @doc.type record
 * @doc.purpose OAuth 2.0 token response (K01-002)
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,   // seconds
        String scope
) {
    public TokenResponse {
        tokenType = "Bearer";
    }
}
