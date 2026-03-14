/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.provider;

import com.ghatana.appplatform.iam.port.SigningKeyProvider;
import com.nimbusds.jose.jwk.RSAKey;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Rotating RSA signing key provider with grace-period support (STORY-K14-006).
 *
 * <p>Maintains a single active signing key in memory. On {@link #rotate()}, a new
 * RSA-2048 key pair is generated, becomes the active key, and the previous public key
 * is stored in Redis under {@code kid:grace:{kid}} with a 24-hour TTL so that tokens
 * signed with the old key remain verifiable during the grace period.
 *
 * <h3>Redis key layout</h3>
 * <pre>
 * kid:grace:{kid}  — JSON public key blob (RSAKey.toPublicJWK().toJSONString()), TTL 86400 s
 * kid:active       — current active kid, for discovery across instances
 * </pre>
 *
 * <h3>Multi-instance consistency</h3>
 * <p>The active private key is not replicated to Redis (it must never leave the JVM
 * for security reasons). In a multi-instance deployment, key rotation should be
 * triggered on a single designated instance (e.g., via a scheduled job). Other
 * instances serve the same public keys via the JWKS endpoint which reads from Redis.
 *
 * @doc.type class
 * @doc.purpose Rotating RS256 signing key provider with Redis-backed grace period (K14-006)
 * @doc.layer product
 * @doc.pattern Provider
 */
public final class SigningKeyRotator implements SigningKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyRotator.class);

    /** TTL in seconds for a retired key's public key in Redis (24 hours). */
    static final long GRACE_TTL_SECONDS = 24L * 3600L;

    private static final String GRACE_KEY_PREFIX = "kid:grace:";
    private static final String ACTIVE_KID_KEY   = "kid:active";

    private final JedisPool jedis;
    private final Executor  executor;
    private final AtomicReference<RSAKey> activeKey = new AtomicReference<>();

    /**
     * @param jedis    Redis connection pool — stores grace-period public keys
     * @param executor blocking executor for Redis I/O
     */
    public SigningKeyRotator(JedisPool jedis, Executor executor) {
        this.jedis    = jedis;
        this.executor = executor;
        this.activeKey.set(generateKey());
        log.info("[K14-006] Initial signing key generated: kid={}", activeKey.get().getKeyID());
    }

    // ──────────────────────────────────────────────────────────────────────
    // SigningKeyProvider implementation
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public RSAKey getSigningKey() {
        return activeKey.get();
    }

    @Override
    public String getKeyId() {
        return activeKey.get().getKeyID();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Rotation
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Rotates the active signing key.
     *
     * <ol>
     *   <li>Generates a new RSA-2048 key pair.</li>
     *   <li>Stores the <em>old</em> public key in Redis with a 24-hour TTL.</li>
     *   <li>Atomically swaps the active key reference.</li>
     * </ol>
     *
     * @return async completion; resolves with the new kid
     */
    public Promise<String> rotate() {
        RSAKey oldKey = activeKey.get();
        RSAKey newKey = generateKey();

        return Promise.ofBlocking(executor, () -> {
            // Persist old public key to Redis for the grace window
            String graceRedisKey = GRACE_KEY_PREFIX + oldKey.getKeyID();
            String publicKeyJson = oldKey.toPublicJWK().toJSONString();
            try (var j = jedis.getResource()) {
                j.setex(graceRedisKey, GRACE_TTL_SECONDS, publicKeyJson);
                j.setex(ACTIVE_KID_KEY, GRACE_TTL_SECONDS, newKey.getKeyID());
            }

            // Atomically activate new key
            activeKey.set(newKey);
            log.info("[K14-006] Key rotated: oldKid={} newKid={} graceTtl={}s",
                     oldKey.getKeyID(), newKey.getKeyID(), GRACE_TTL_SECONDS);
            return newKey.getKeyID();
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Grace-period public key listing (used by JwksEndpointHandler)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns all valid public keys: the active key plus any grace-period keys still in Redis.
     *
     * @return async list of public-only {@link RSAKey} objects (no private material)
     */
    public Promise<List<RSAKey>> listValidPublicKeys() {
        return Promise.ofBlocking(executor, () -> {
            List<RSAKey> result = new ArrayList<>();
            result.add(activeKey.get().toPublicJWK());

            // SCAN for grace keys — prefix search
            try (var j = jedis.getResource()) {
                var graceKeys = j.keys(GRACE_KEY_PREFIX + "*");
                for (String redisKey : graceKeys) {
                    String json = j.get(redisKey);
                    if (json != null) {
                        try {
                            result.add(RSAKey.parse(json));
                        } catch (Exception e) {
                            log.warn("[K14-006] Failed to parse grace key {}: {}", redisKey, e.getMessage());
                        }
                    }
                }
            }
            return result;
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal
    // ──────────────────────────────────────────────────────────────────────

    private static RSAKey generateKey() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) kp.getPublic())
                .privateKey((RSAPrivateKey) kp.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm unavailable", e);
        }
    }
}
