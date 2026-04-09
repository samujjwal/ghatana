/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.port;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.Token;
import com.ghatana.platform.domain.auth.TokenId;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Port for token storage, retrieval, revocation, and expiry management.
 *
 * <p><b>Contract</b><br>
 * All operations are tenant-scoped.  Implementations MUST guarantee strict
 * tenant isolation.  The store is primarily used to track access tokens and
 * refresh tokens that are issued by {@link JwtTokenProvider} and consumed
 * by the security filter chain.
 *
 * <p><b>Token types managed</b><br>
 * <ul>
 *   <li><b>Access tokens</b> — short-lived JWTs for API authorization.</li>
 *   <li><b>Refresh tokens</b> — long-lived opaque tokens used to obtain new
 *       access tokens without re-authenticating.</li>
 *   <li><b>ID tokens</b> — optional OIDC identity assertions.</li>
 * </ul>
 *
 * <p><b>Implementation guidelines</b><br>
 * <ul>
 *   <li><b>Backend</b>: A Redis/Dragonfly hash-set keyed by
 *       {@code tenantId:tokenId} is the recommended approach.  Use
 *       {@code SET NX EX} semantics so tokens are automatically evicted when
 *       they expire.</li>
 *   <li><b>Revocation</b>: {@link #revoke} MUST atomically mark the token as
 *       revoked and update the backing store in a single operation.  A common
 *       pattern is to keep a small revocation bloom-filter or a deny-list with
 *       a TTL matching the token's remaining lifetime.</li>
 *   <li><b>isValid</b>: A token is considered valid only when it exists
 *       <em>and</em> has not been revoked <em>and</em> has not expired.
 *       Implementations SHOULD perform all three checks atomically.</li>
 *   <li><b>Expiry maintenance</b>: {@link #deleteExpired} ensures the store
 *       does not grow unbounded for backends without native TTL support (e.g.
 *       JDBC).  Redis/Dragonfly implementations may return {@code 0} from a
 *       no-op implementation because native TTL handles cleanup.</li>
 *   <li><b>Async</b>: Use {@link io.activej.promise.Promise#ofBlocking} for
 *       any blocking I/O so the ActiveJ event loop is never blocked.</li>
 * </ul>
 *
 * <p><b>Usage example</b><br>
 * <pre>{@code
 * // Inject the store from the DI container
 * TokenStore tokens = injector.getInstance(TokenStore.class);
 *
 * // 1. Persist a freshly issued access token
 * Token accessToken = Token.createAccess(tenantId, userId, jwtString, expiresAt);
 * tokens.store(accessToken).whenResult(() -> log.debug("Token stored"));
 *
 * // 2. Validate an incoming bearer token
 * tokens.findByValue(tenantId, bearerValue)
 *     .then(opt -> {
 *         if (opt.isEmpty()) return Promise.ofException(new UnauthorizedException());
 *         Token token = opt.get();
 *         return tokens.isValid(tenantId, token.tokenId())
 *             .then(valid -> valid
 *                 ? Promise.of(token)
 *                 : Promise.ofException(new TokenRevokedException()));
 *     });
 *
 * // 3. Revoke a token on explicit logout
 * tokens.revoke(tenantId, tokenId)
 *     .whenResult(() -> log.info("Token {} revoked", tokenId));
 *
 * // 4. Scheduled maintenance job — clean up expired tokens from JDBC store
 * tokens.deleteExpired(tenantId)
 *     .whenResult(count -> log.info("Deleted {} expired token(s)", count));
 * }</pre>
 *
 * @see Token
 * @see TokenId
 * @see JwtTokenProvider for token creation
 * @see SessionStore for session-level persistence
 *
 * @doc.type interface
 * @doc.purpose Port for token storage, retrieval, revocation, and expiry management
 * @doc.layer security
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface TokenStore {

    /**
     * Stores a token, overwriting any existing token with the same ID.
     *
     * <p>Implementations SHOULD set a storage-level TTL equal to the token's
     * expiry time so the entry is automatically removed when the token expires.
     *
     * @param token the token to persist; must not be {@code null}
     * @return a {@link Promise} that completes when the token is durably stored
     */
    Promise<Void> store(Token token);

    /**
     * Retrieves a token by its unique identifier, scoped to a tenant.
     *
     * <p>Returns {@link Optional#empty()} when no token with that ID exists or
     * the token has expired.
     *
     * @param tenantId the tenant scope; must not be {@code null}
     * @param tokenId  the token identifier; must not be {@code null}
     * @return a {@link Promise} of the token if found,
     *         or {@link Optional#empty()} otherwise
     */
    Promise<Optional<Token>> findById(TenantId tenantId, TokenId tokenId);

    /**
     * Retrieves a token by its raw value (e.g. the signed JWT string or opaque
     * token bytes).
     *
     * <p>This is the primary look-up used in the request-filter path when the
     * only available information is the bearer token from the
     * {@code Authorization} header.
     *
     * @param tenantId   the tenant scope; must not be {@code null}
     * @param tokenValue the raw token string; must not be {@code null}
     * @return a {@link Promise} of the token if found,
     *         or {@link Optional#empty()} otherwise
     */
    Promise<Optional<Token>> findByValue(TenantId tenantId, String tokenValue);

    /**
     * Revokes a token, preventing it from being accepted for future requests.
     *
     * <p>If the token does not exist the operation completes successfully
     * without error (idempotent).  Implementations that store revoked token IDs
     * in a deny-list SHOULD set a TTL on the deny-list entry equal to the
     * token's remaining lifetime to avoid unbounded growth.
     *
     * @param tenantId the tenant scope; must not be {@code null}
     * @param tokenId  the token to revoke; must not be {@code null}
     * @return a {@link Promise} that completes when the token is marked as revoked
     */
    Promise<Void> revoke(TenantId tenantId, TokenId tokenId);

    /**
     * Returns {@code true} if the token exists, has not been revoked, and has
     * not expired.
     *
     * <p>Implementations SHOULD perform this check as a single atomic read to
     * avoid race conditions between fetching the token and checking its state.
     *
     * @param tenantId the tenant scope; must not be {@code null}
     * @param tokenId  the token identifier to validate; must not be {@code null}
     * @return a {@link Promise} of {@code true} if the token is valid,
     *         {@code false} otherwise
     */
    Promise<Boolean> isValid(TenantId tenantId, TokenId tokenId);

    /**
     * Deletes all expired tokens for a tenant from the backing store.
     *
     * <p>Intended to be called by a scheduled maintenance task.  Implementations
     * backed by a store with native TTL (Redis / Dragonfly) may return
     * {@code 0} from a no-op body because the backend handles expiry
     * automatically.
     *
     * @param tenantId the tenant scope; must not be {@code null}
     * @return a {@link Promise} of the number of tokens that were deleted
     */
    Promise<Integer> deleteExpired(TenantId tenantId);
}
