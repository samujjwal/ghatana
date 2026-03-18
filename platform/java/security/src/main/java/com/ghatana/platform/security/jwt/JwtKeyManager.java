package com.ghatana.platform.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.gen.OctetSequenceKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages a rotating ring of HMAC-SHA256 JWT signing keys.
 *
 * <p>Key lifecycle:
 * <ol>
 *   <li>On construction, a single key entry is created from the provided bootstrap secret.</li>
 *   <li>Calling {@link #rotate()} generates a new 256-bit random key, makes it the <em>current</em>
 *       signing key, and schedules the previous key for expiry after {@code retentionSeconds}.</li>
 *   <li>During token validation, the manager tries the key identified by the {@code kid} header
 *       first, then falls back to checking all active keys.</li>
 *   <li>Keys past their retention deadline are pruned automatically during {@link #rotate()} and
 *       {@link #pruneExpiredKeys()}.</li>
 * </ol>
 *
 * <p>Thread safety: all mutations are guarded by the intrinsic lock on {@code this}; reads use
 * a snapshot taken under the same lock.
 *
 * <p>Usage:
 * <pre>{@code
 * // Bootstrap
 * JwtKeyManager manager = new JwtKeyManager(bootstrapSecret, 86_400); // 24 h retention
 * jwtProvider.setKeyManager(manager);
 *
 * // Periodic rotation (e.g. scheduled daily)
 * manager.rotate();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Rotating-key manager for JWT HMAC-SHA256 signing and verification
 * @doc.layer platform
 * @doc.pattern KeyManagement
 */
public final class JwtKeyManager {

    private static final Logger logger = LoggerFactory.getLogger(JwtKeyManager.class);

    private final long retentionSeconds;
    private final CopyOnWriteArrayList<KeyEntry> keys = new CopyOnWriteArrayList<>();
    private final AtomicInteger keyCounter = new AtomicInteger(0);

    /**
     * Constructs a {@code JwtKeyManager} seeded with an existing bootstrap secret.
     *
     * @param bootstrapSecret   the current HMAC secret (must be ≥ 32 bytes when UTF-8 encoded)
     * @param retentionSeconds  how long a retired key remains valid for token verification (e.g. 86_400 for 24 h)
     */
    public JwtKeyManager(String bootstrapSecret, long retentionSeconds) {
        if (bootstrapSecret == null || bootstrapSecret.isBlank()) {
            throw new IllegalArgumentException("bootstrapSecret must not be null or blank");
        }
        if (retentionSeconds <= 0) {
            throw new IllegalArgumentException("retentionSeconds must be > 0");
        }
        this.retentionSeconds = retentionSeconds;
        keys.add(KeyEntry.fromSecret("k" + keyCounter.getAndIncrement(), bootstrapSecret));
    }

    // -----------------------------------------------------------------------
    // Key access
    // -----------------------------------------------------------------------

    /**
     * Returns the ID of the current signing key (the most recently added key).
     *
     * @return current key ID (e.g. {@code "k0"}, {@code "k1"}, …)
     */
    public synchronized String currentKeyId() {
        return keys.get(keys.size() - 1).id;
    }

    /**
     * Returns the {@link JWSSigner} for the current signing key.
     *
     * @return signer
     */
    public synchronized JWSSigner currentSigner() {
        return keys.get(keys.size() - 1).signer;
    }

    /**
     * Constructs the {@link JWSHeader} for a new token, including the {@code kid} parameter.
     *
     * @return JWS header with HS256 algorithm and current key ID
     */
    public synchronized JWSHeader currentHeader() {
        String kid = keys.get(keys.size() - 1).id;
        return new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(kid).build();
    }

    /**
     * Looks up a {@link JWSVerifier} for the given key ID, or returns all active verifiers
     * if the ID is not found (for backward-compatible tokens without a {@code kid} header).
     *
     * @param kid key ID from the JWT header, or {@code null}
     * @return list of candidate verifiers (never empty)
     */
    public synchronized List<JWSVerifier> verifiersFor(String kid) {
        List<KeyEntry> snapshot = List.copyOf(keys);
        if (kid != null) {
            for (KeyEntry entry : snapshot) {
                if (entry.id.equals(kid)) {
                    return List.of(entry.verifier);
                }
            }
            logger.warn("kid '{}' not found in key ring — trying all active keys", kid);
        }
        List<JWSVerifier> all = new ArrayList<>(snapshot.size());
        for (KeyEntry entry : snapshot) {
            all.add(entry.verifier);
        }
        return Collections.unmodifiableList(all);
    }

    // -----------------------------------------------------------------------
    // Rotation
    // -----------------------------------------------------------------------

    /**
     * Rotates to a new randomly-generated signing key.
     *
     * <p>The previous key is marked for expiry at {@code now + retentionSeconds}.
     * Expired keys (those past their expiry deadline) are pruned before the new key is added.
     *
     * @return the ID of the newly generated key
     */
    public synchronized String rotate() {
        pruneExpiredKeysInternal();

        // Mark all current keys with an expiry deadline if they don't already have one
        Instant deadline = Instant.now().plusSeconds(retentionSeconds);
        for (int i = 0; i < keys.size(); i++) {
            KeyEntry existing = keys.get(i);
            if (existing.expiresAt == null) {
                keys.set(i, existing.withExpiry(deadline));
            }
        }

        // Generate new key
        String newKid = "k" + keyCounter.getAndIncrement();
        try {
            OctetSequenceKey jwk = new OctetSequenceKeyGenerator(256)
                .keyID(newKid)
                .generate();
            KeyEntry newEntry = KeyEntry.fromJwk(newKid, jwk);
            keys.add(newEntry);
            logger.info("JWT key rotated — new key id: {}, active keys: {}", newKid, keys.size());
            return newKid;
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to generate new JWT signing key", e);
        }
    }

    /**
     * Explicitly prunes keys whose retention period has expired.
     * At least one key is always retained (the current signing key).
     */
    public synchronized void pruneExpiredKeys() {
        pruneExpiredKeysInternal();
    }

    /** Returns the number of active keys currently in the ring. */
    public synchronized int activeKeyCount() {
        return keys.size();
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private void pruneExpiredKeysInternal() {
        if (keys.size() <= 1) return;
        Instant now = Instant.now();
        // Never prune the last (current signing) key
        keys.removeIf(entry -> entry.expiresAt != null
            && entry.expiresAt.isBefore(now)
            && !entry.id.equals(keys.get(keys.size() - 1).id));
    }

    // -----------------------------------------------------------------------
    // KeyEntry
    // -----------------------------------------------------------------------

    /**
     * Immutable holder for a single key in the ring.
     */
    static final class KeyEntry {
        final String id;
        final JWSSigner signer;
        final JWSVerifier verifier;
        /** Null means this key has no scheduled expiry (the current signing key never does). */
        final Instant expiresAt;

        private KeyEntry(String id, JWSSigner signer, JWSVerifier verifier, Instant expiresAt) {
            this.id = id;
            this.signer = signer;
            this.verifier = verifier;
            this.expiresAt = expiresAt;
        }

        static KeyEntry fromSecret(String id, String secret) {
            try {
                byte[] bytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                return new KeyEntry(id, new MACSigner(bytes), new MACVerifier(bytes), null);
            } catch (JOSEException e) {
                throw new IllegalArgumentException("Invalid JWT secret for key id=" + id, e);
            }
        }

        static KeyEntry fromJwk(String id, OctetSequenceKey jwk) throws JOSEException {
            return new KeyEntry(id,
                new MACSigner(jwk.toSecretKey()),
                new MACVerifier(jwk.toSecretKey()),
                null);
        }

        KeyEntry withExpiry(Instant expiresAt) {
            return new KeyEntry(id, signer, verifier, expiresAt);
        }
    }
}
