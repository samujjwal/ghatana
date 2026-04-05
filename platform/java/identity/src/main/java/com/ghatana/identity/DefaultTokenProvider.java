/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default {@link TokenProvider} implementation using HMAC-based JWT simulation.
 *
 * <p>Production systems should replace this with a real JWT library (e.g. jose4j, nimbus-jose-jwt)
 * and integrate with the {@link com.ghatana.platform.security} module.
 *
 * <p>Thread-safe; supports key rotation with a grace period for existing tokens.
 *
 * @doc.type class
 * @doc.purpose Default JWT token provider with key rotation support
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class DefaultTokenProvider implements TokenProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultTokenProvider.class);
    private static final Duration MAX_TOKEN_TTL = Duration.ofHours(24);
    private static final Duration DEFAULT_NBF_SKEW = Duration.ofSeconds(5);

    private static class SigningKey {
        final String keyId;
        final String secret;
        final Instant createdAt;

        SigningKey(String keyId, String secret) {
            this.keyId = keyId;
            this.secret = secret;
            this.createdAt = Instant.now();
        }
    }

    /** Current signing key for issuing new tokens. */
    private final AtomicReference<SigningKey> currentKey;
    /** Previous key for validation grace period. */
    private final AtomicReference<SigningKey> previousKey;
    /** When previousKey expires (tokens signed with it no longer accepted). */
    private final AtomicReference<Instant> previousKeyExpiry;

    public DefaultTokenProvider() {
        String secret = UUID.randomUUID().toString();
        this.currentKey = new AtomicReference<>(new SigningKey(UUID.randomUUID().toString(), secret));
        this.previousKey = new AtomicReference<>(null);
        this.previousKeyExpiry = new AtomicReference<>(null);
    }

    @Override
    public Promise<String> createToken(String tenantId, String agentId, Duration ttl) {
        Duration effectiveTtl = ttl.compareTo(MAX_TOKEN_TTL) > 0 ? MAX_TOKEN_TTL : ttl;
        Instant now = Instant.now();
        Instant expiresAt = now.plus(effectiveTtl);
        Instant notBefore = now.minus(DEFAULT_NBF_SKEW);

        String tokenId = UUID.randomUUID().toString();
        String header = Base64.getEncoder().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getEncoder().encodeToString(
            String.format(
                "{\"tokenId\":\"%s\",\"tenantId\":\"%s\",\"agentId\":\"%s\"," +
                "\"iat\":%d,\"exp\":%d,\"nbf\":%d,\"scopes\":[]}",
                tokenId, tenantId, agentId,
                now.getEpochSecond(), expiresAt.getEpochSecond(), notBefore.getEpochSecond()
            ).getBytes()
        );

        String signature = hmacSha256(header + "." + payload, currentKey.get().secret);
        String compactJwt = header + "." + payload + "." + signature;

        log.debug("Created token {} for agent {}/{} ttl={}", tokenId, tenantId, agentId, effectiveTtl);
        return Promise.of(compactJwt);
    }

    @Override
    public Promise<Optional<TokenClaims>> verifyToken(String compactJwt) {
        try {
            String[] parts = compactJwt.split("\\.");
            if (parts.length != 3) {
                log.debug("Invalid JWT format: expected 3 parts, got {}", parts.length);
                return Promise.of(Optional.empty());
            }

            String header = parts[0];
            String payload = parts[1];
            String signature = parts[2];

            // Try to verify with current key
            String expectedSignature = hmacSha256(header + "." + payload, currentKey.get().secret);
            boolean validWithCurrent = constantTimeEquals(signature, expectedSignature);

            // Try with previous key if still in grace period
            boolean validWithPrevious = false;
            SigningKey prev = previousKey.get();
            if (prev != null && Instant.now().isBefore(previousKeyExpiry.get())) {
                String prevExpected = hmacSha256(header + "." + payload, prev.secret);
                validWithPrevious = constantTimeEquals(signature, prevExpected);
            }

            if (!validWithCurrent && !validWithPrevious) {
                log.debug("JWT signature validation failed");
                return Promise.of(Optional.empty());
            }

            // Decode and parse claims
            String payloadDecoded = new String(Base64.getDecoder().decode(payload));
            TokenClaims claims = parseTokenClaims(payloadDecoded);

            if (claims.isExpired()) {
                log.debug("Token {} is expired", claims.tokenId());
                return Promise.of(Optional.empty());
            }

            if (claims.isNotYetValid()) {
                log.debug("Token {} is not yet valid", claims.tokenId());
                return Promise.of(Optional.empty());
            }

            return Promise.of(Optional.of(claims));

        } catch (Exception e) {
            log.debug("Failed to verify token", e);
            return Promise.of(Optional.empty());
        }
    }

    @Override
    public Promise<Optional<TokenClaims>> decodeTokenWithoutVerification(String compactJwt) {
        try {
            String[] parts = compactJwt.split("\\.");
            if (parts.length != 3) {
                return Promise.of(Optional.empty());
            }

            String payloadDecoded = new String(Base64.getDecoder().decode(parts[1]));
            TokenClaims claims = parseTokenClaims(payloadDecoded);
            return Promise.of(Optional.of(claims));

        } catch (Exception e) {
            log.debug("Failed to decode token", e);
            return Promise.of(Optional.empty());
        }
    }

    @Override
    public Promise<Void> rotateSigningKey(Duration gracePeriod) {
        SigningKey newKey = new SigningKey(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        SigningKey old = currentKey.getAndSet(newKey);

        if (old != null) {
            previousKey.set(old);
            previousKeyExpiry.set(Instant.now().plus(gracePeriod));
            log.info("Rotated signing key {} -> {}, grace period: {}", old.keyId, newKey.keyId, gracePeriod);
        }

        return Promise.complete();
    }

    private String hmacSha256(String data, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec =
                new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        int aLen = a.length();
        int bLen = b.length();
        int result = aLen ^ bLen;
        for (int i = 0; i < Math.min(aLen, bLen); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private TokenClaims parseTokenClaims(String jsonPayload) {
        // Simple JSON parsing for testing; production should use a JSON library
        String tokenId = extractJsonString(jsonPayload, "tokenId");
        String tenantId = extractJsonString(jsonPayload, "tenantId");
        String agentId = extractJsonString(jsonPayload, "agentId");
        long iat = extractJsonLong(jsonPayload, "iat");
        long exp = extractJsonLong(jsonPayload, "exp");
        long nbf = extractJsonLong(jsonPayload, "nbf");

        return new TokenClaims(
            tokenId,
            tenantId,
            agentId,
            Set.of(),
            Instant.ofEpochSecond(iat),
            Instant.ofEpochSecond(exp),
            Instant.ofEpochSecond(nbf)
        );
    }

    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return "";
        start += pattern.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private long extractJsonLong(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return 0;
        start += pattern.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        String value = json.substring(start, end).trim();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
