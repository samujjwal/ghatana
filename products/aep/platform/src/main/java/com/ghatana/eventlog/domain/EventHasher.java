package com.ghatana.eventlog.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

/**
 * EventHasher provides simple content hashing to support append-only invariants
 * and lightweight integrity checks for event records.
 *
 * Note: This utility was migrated out of event-core. It intentionally does not
 * depend on concrete event models to keep it reusable for EventRecord or other
 * storage DTOs.
 */
public final class EventHasher {

    private static final String DEFAULT_ALGO = "SHA-256";

    private EventHasher() { }

    public static String hash(String previousHash, String payloadCanonicalJson) {
        Objects.requireNonNull(previousHash, "previousHash");
        Objects.requireNonNull(payloadCanonicalJson, "payloadCanonicalJson");
        try {
            MessageDigest digest = MessageDigest.getInstance(DEFAULT_ALGO);
            digest.update(previousHash.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(payloadCanonicalJson.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = digest.digest();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Hash algorithm not available: " + DEFAULT_ALGO, e);
        }
    }
}
