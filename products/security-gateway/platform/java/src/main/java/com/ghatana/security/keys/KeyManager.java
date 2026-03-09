package com.ghatana.security.keys;

import io.activej.promise.Promise;

import javax.crypto.SecretKey;
import java.security.Key;

/**
 * Manages cryptographic keys used for encryption and decryption.
 * Provides methods to retrieve, generate, and manage keys in a secure manner.
 
 *
 * @doc.type interface
 * @doc.purpose Key manager
 * @doc.layer core
 * @doc.pattern Manager
*/
public interface KeyManager {
    /**
     * Gets the current encryption/decryption key.
     *
     * @return A promise that completes with the current key
     */
    Promise<Key> getCurrentKey();
    
    /**
     * Gets a specific version of a key.
     *
     * @param keyId The ID of the key to retrieve
     * @return A promise that completes with the requested key
     */
    Promise<Key> getKey(String keyId);
    
    /**
     * Gets the ID of the current key.
     *
     * @return The ID of the current key
     */
    String getCurrentKeyId();
    
    /**
     * Generates a new key and makes it the current key.
     *
     * @return A promise that completes with the ID of the new key
     */
    Promise<String> rotateKey();

    /**
     * Checks if a key is healthy and not expired.
     *
     * @param keyId The ID of the key to check
     * @return true if the key is healthy, false otherwise
     */
    boolean isKeyHealthy(String keyId);

    /**
     * Gets the age of a key in milliseconds.
     *
     * @param keyId The ID of the key
     * @return The age of the key in milliseconds
     */
    long getKeyAge(String keyId);

    /**
     * Gets the last rotation time for a key.
     *
     * @param keyId The ID of the key
     * @return The last rotation time in milliseconds since epoch
     */
    long getLastRotationTime(String keyId);
    
    /**
     * Gets the current key as a SecretKey.
     *
     * @return A promise that completes with the current key as a SecretKey
     */
    default Promise<SecretKey> getCurrentSecretKey() {
        return getCurrentKey().map(key -> (SecretKey) key);
    }
    
    /**
     * Gets a specific version of a key as a SecretKey.
     *
     * @param keyId The ID of the key to retrieve
     * @return A promise that completes with the requested key as a SecretKey
     */
    default Promise<SecretKey> getSecretKey(String keyId) {
        return getKey(keyId).map(key -> (SecretKey) key);
    }
}
