package com.ghatana.platform.security.encryption;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Service that provides secure hashing operations for PII protection.
 * Uses HMAC-SHA256 with a secure salt for deterministic hashing.
 *
 * @doc.type class
 * @doc.purpose Secure hashing service for PII protection
 * @doc.layer core
 * @doc.pattern Service
 */
public class HashingService {
    private static final Logger logger = LoggerFactory.getLogger(HashingService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final String salt;
    private final Eventloop eventloop;

    /**
     * Creates a new HashingService with the specified salt and event loop.
     *
     * @param salt The secure salt for HMAC operations (must be non-null and non-empty)
     * @param eventloop The event loop for async operations
     */
    public HashingService(String salt, Eventloop eventloop) {
        if (salt == null || salt.isBlank()) {
            throw new IllegalArgumentException("Salt must not be null or blank");
        }
        this.salt = salt;
        this.eventloop = eventloop;
        logger.info("Initialized HashingService with HMAC algorithm: {}", HMAC_ALGORITHM);
    }

    /**
     * Hashes a contact point using HMAC-SHA256 with the configured salt.
     *
     * @param contactPoint The contact point to hash (e.g., email address)
     * @return A promise that completes with the hex-encoded hash
     */
    public Promise<String> hashContactPoint(String contactPoint) {
        return hash(contactPoint)
            .mapException(e -> {
                logger.error("Failed to hash contact point", e);
                return new EncryptionException("Failed to hash contact point", e);
            });
    }

    /**
     * Hashes arbitrary data using HMAC-SHA256.
     *
     * @param data The data to hash
     * @return A promise that completes with the hex-encoded hash
     */
    public Promise<String> hash(String data) {
        return Promise.ofBlocking(eventloop, () -> {
            try {
                Mac mac = Mac.getInstance(HMAC_ALGORITHM);
                SecretKeySpec secretKey = new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
                mac.init(secretKey);
                byte[] hashBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
                return HEX_FORMAT.formatHex(hashBytes);
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                throw new EncryptionException("Failed to compute HMAC-SHA256 hash", e);
            }
        });
    }

    /**
     * Verifies that a contact point matches the expected hash.
     *
     * @param contactPoint The raw contact point to verify
     * @param expectedHash The expected hash value
     * @return A promise that completes with true if the hash matches
     */
    public Promise<Boolean> verifyContactPoint(String contactPoint, String expectedHash) {
        return hashContactPoint(contactPoint)
            .map(actualHash -> actualHash.equals(expectedHash))
            .mapException(e -> {
                logger.error("Failed to verify contact point hash", e);
                return new EncryptionException("Failed to verify contact point hash", e);
            });
    }

    /**
     * Gets the hashing algorithm being used.
     *
     * @return The algorithm name
     */
    public String getAlgorithm() {
        return HMAC_ALGORITHM;
    }

    /**
     * Gets the salt being used for hashing.
     *
     * @return The salt value
     */
    public String getSalt() {
        return salt;
    }
}
