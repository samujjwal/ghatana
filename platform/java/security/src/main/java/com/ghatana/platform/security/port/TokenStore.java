/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.port;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.Token;
import com.ghatana.platform.domain.auth.TokenId;
import com.ghatana.platform.domain.auth.UserId;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Port for token storage operations.
 *
 * <p>Abstracts token persistence for access tokens, refresh tokens, and ID tokens.
 *
 * @doc.type interface
 * @doc.purpose Port for token storage, retrieval, revocation, and expiry management
 * @doc.layer security
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface TokenStore {

    /**
     * Stores a token.
     *
     * @param token the token to store
     * @return promise completing when stored
     */
    Promise<Void> store(Token token);

    /**
     * Retrieves a token by ID.
     *
     * @param tenantId the tenant ID
     * @param tokenId the token identifier
     * @return promise of optional token
     */
    Promise<Optional<Token>> findById(TenantId tenantId, TokenId tokenId);

    /**
     * Retrieves a token by its value.
     *
     * @param tenantId the tenant ID
     * @param tokenValue the token value/string
     * @return promise of optional token
     */
    Promise<Optional<Token>> findByValue(TenantId tenantId, String tokenValue);

    /**
     * Revokes a token.
     *
     * @param tenantId the tenant ID
     * @param tokenId the token identifier
     * @return promise completing when revoked
     */
    Promise<Void> revoke(TenantId tenantId, TokenId tokenId);

    /**
     * Checks if a token exists and is valid.
     *
     * @param tenantId the tenant ID
     * @param tokenId the token identifier
     * @return promise of true if token exists and is valid
     */
    Promise<Boolean> isValid(TenantId tenantId, TokenId tokenId);

    /**
     * Deletes expired tokens.
     *
     * @param tenantId the tenant ID
     * @return promise of count of tokens deleted
     */
    Promise<Integer> deleteExpired(TenantId tenantId);
}
