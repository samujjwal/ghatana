package com.ghatana.platform.security.port;

import io.activej.promise.Promise;

/**
 * Port interface for hashing operations used for PII protection.
 * Provides secure hashing with salt for contact points and sensitive identifiers.
 *
 * @doc.type interface
 * @doc.purpose Secure hashing port for PII protection
 * @doc.layer core
 * @doc.pattern Port
 */
public interface HashingPort {

    /**
     * Hashes a contact point (email, phone) using HMAC with secure salt.
     * The hash is deterministic for the same input and salt.
     *
     * @param contactPoint The raw contact point to hash (e.g., email address)
     * @return A promise that completes with the hashed value (hex-encoded)
     */
    Promise<String> hashContactPoint(String contactPoint);

    /**
     * Hashes arbitrary data using HMAC with secure salt.
     *
     * @param data The data to hash
     * @return A promise that completes with the hashed value (hex-encoded)
     */
    Promise<String> hash(String data);

    /**
     * Verifies that a contact point matches the expected hash.
     *
     * @param contactPoint The raw contact point to verify
     * @param expectedHash The expected hash value
     * @return A promise that completes with true if the hash matches
     */
    Promise<Boolean> verifyContactPoint(String contactPoint, String expectedHash);

    /**
     * Gets the hashing algorithm being used.
     *
     * @return The algorithm name (e.g., "HmacSHA256")
     */
    String getAlgorithm();
}
