/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

/**
 * Token types for OAuth/OIDC.
 *
 * @doc.type enum
 * @doc.purpose OAuth token type enumeration
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum TokenType {
    ACCESS_TOKEN,
    REFRESH_TOKEN,
    ID_TOKEN,
    AUTHORIZATION_CODE
}
