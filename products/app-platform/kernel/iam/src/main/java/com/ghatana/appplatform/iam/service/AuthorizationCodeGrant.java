package com.ghatana.appplatform.iam.service;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * OAuth 2.0 {@code authorization_code} grant with PKCE (S256) (STORY-K01-002).
 *
 * <p>Flow:
 * <ol>
 *   <li>Client sends {@code GET /auth/authorize?code_challenge=...&code_challenge_method=S256&state=...}
 *   <li>On consent, server issues authorization code stored in Redis with short TTL (10 min)
 *   <li>Client exchanges code + {@code code_verifier} via {@code POST /auth/token}
 *   <li>Server verifies PKCE: {@code BASE64URL(SHA-256(code_verifier)) == code_challenge}
 *   <li>Code is single-use — consumed atomically in Redis
 * </ol>
 *
 * <p>PKCE implementation follows RFC 7636 §4.6 exactly. Anti-CSRF {@code state}
 * parameter is required on both legs and compared on exchange.
 *
 * @doc.type class
 * @doc.purpose OAuth 2.0 authorization_code grant with PKCE S256 (K01-002)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AuthorizationCodeGrant {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationCodeGrant.class);
    private static final int CODE_TTL_SECONDS = 600; // 10 minutes per RFC 6749

    private final JedisPool jedisPool;
    private final JwtTokenService tokenService;
    private final Executor blockingExecutor;

    /** Auth code entry stored in Redis as pipe-delimited string. */
    private record CodeEntry(
        String clientId,
        String tenantId,
        String state,
        String codeChallenge // BASE64URL(SHA-256(verifier))
    ) {
        String serialize() { return clientId + "|" + tenantId + "|" + state + "|" + codeChallenge; }

        static CodeEntry deserialize(String raw) {
            String[] parts = raw.split("\\|", 4);
            if (parts.length != 4) throw new IllegalArgumentException("Malformed code entry");
            return new CodeEntry(parts[0], parts[1], parts[2], parts[3]);
        }
    }

    /**
     * @param jedisPool         Redis connection pool for code storage
     * @param tokenService      JWT issuer (reused from K01-001)
     * @param blockingExecutor  executor for Redis I/O off the eventloop
     */
    public AuthorizationCodeGrant(JedisPool jedisPool, JwtTokenService tokenService,
                                   Executor blockingExecutor) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool");
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor");
    }

    /**
     * Issues an authorization code after user consent.
     *
     * @param clientId      authenticated client identifier
     * @param tenantId      tenant scope
     * @param state         anti-CSRF state from the authorization request
     * @param codeChallenge BASE64URL(SHA-256(code_verifier)) — the PKCE challenge
     * @return single-use authorization code (opaque, safe to include in redirect URL)
     */
    public Promise<String> authorize(String clientId, String tenantId,
                                     String state, String codeChallenge) {
        validateNotBlank(clientId, "clientId");
        validateNotBlank(state, "state");
        validateNotBlank(codeChallenge, "codeChallenge");

        String code = generateCode();
        CodeEntry entry = new CodeEntry(clientId, tenantId, state, codeChallenge);

        return Promise.ofBlocking(blockingExecutor, () -> {
            try (var jedis = jedisPool.getResource()) {
                jedis.setex("authcode:" + code, CODE_TTL_SECONDS, entry.serialize());
            }
            log.debug("Issued authorization code for client={} tenant={}", clientId, tenantId);
            return code;
        });
    }

    /**
     * Exchanges an authorization code + PKCE verifier for a JWT.
     *
     * @param code         the authorization code issued by {@link #authorize}
     * @param codeVerifier the raw PKCE verifier (must satisfy S256 challenge)
     * @param state        anti-CSRF state — must match what was provided to {@link #authorize}
     * @return {@link Map} containing {@code access_token} and {@code token_type}
     * @throws InvalidGrantException on code not found, expired, state mismatch, or PKCE failure
     */
    public Promise<Map<String, String>> exchange(String code, String codeVerifier, String state) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            String raw;
            try (var jedis = jedisPool.getResource()) {
                // Single-use: DEL returns the value and removes it atomically
                raw = jedis.getDel("authcode:" + code);
            }
            if (raw == null) {
                throw new InvalidGrantException("Authorization code not found or expired");
            }

            CodeEntry entry = CodeEntry.deserialize(raw);

            if (!entry.state().equals(state)) {
                throw new InvalidGrantException("State mismatch — possible CSRF attack");
            }
            if (!verifyPkce(codeVerifier, entry.codeChallenge())) {
                throw new InvalidGrantException("PKCE verification failed");
            }

            // Issue access token (sub = clientId, no roles on code exchange — caller adds claims)
            com.ghatana.appplatform.iam.domain.TokenClaims claims =
                com.ghatana.appplatform.iam.domain.TokenClaims.forAuthCode(
                    entry.clientId(), entry.tenantId());
            String jwt = tokenService.issue(claims);
            return Map.of("access_token", jwt, "token_type", "Bearer");
        });
    }

    // ─── PKCE S256 ───────────────────────────────────────────────────────────

    /**
     * Verifies the PKCE S256 challenge: BASE64URL(SHA-256(verifier)) == challenge.
     * RFC 7636 §4.6.
     */
    static boolean verifyPkce(String codeVerifier, String expectedChallenge) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return MessageDigest.isEqual(computed.getBytes(), expectedChallenge.getBytes());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String generateCode() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void validateNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    /** Thrown when the authorization code is invalid, expired, or misused. */
    public static final class InvalidGrantException extends RuntimeException {
        public InvalidGrantException(String message) { super(message); }
    }
}
