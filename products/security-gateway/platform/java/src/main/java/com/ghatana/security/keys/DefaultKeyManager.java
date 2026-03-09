package com.ghatana.security.keys;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Default implementation of the KeyManager interface.
 * Manages cryptographic keys in memory with support for key rotation.
 
 *
 * @doc.type class
 * @doc.purpose Default key manager
 * @doc.layer core
 * @doc.pattern Manager
*/
public class DefaultKeyManager implements KeyManager {
    private static final Logger logger = LoggerFactory.getLogger(DefaultKeyManager.class);

    // Use the map instance passed in by the caller so tests that hold a reference
    // to the original map observe modifications (the unit tests expect this).
    private final Map<String, SecretKey> keys;
    private volatile String currentKeyId;
    private final KeyGenerator keyGenerator;
    private final String algorithm;
    private final ExecutorService executorService;

    /**
     * Creates a new DefaultKeyManager with the specified keys and current key ID.
     *
     * @param keys Map of key IDs to SecretKey instances
     * @param currentKeyId The ID of the current key
     */
    public DefaultKeyManager(Map<String, SecretKey> keys, String currentKeyId, KeyGenerator keyGenerator, String algorithm, ExecutorService executorService) {
        // Keep the original map reference instead of copying so external holders see updates
        this.keys = keys;
        this.currentKeyId = currentKeyId;
        this.keyGenerator = keyGenerator;
        this.algorithm = algorithm;
        this.executorService = executorService;
        logger.info("Initialized DefaultKeyManager with {} keys, current key: {}", 
            keys.size(), currentKeyId);
    }

    @Override
    public Promise<Key> getCurrentKey() {
        try {
            synchronized (keys) {
                SecretKey key = keys.get(currentKeyId);
                if (key == null) {
                    return Promise.ofException(new KeyNotFoundException("Key not found: " + currentKeyId));
                }
                return Promise.of(key);
            }
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Key> getKey(String keyId) {
        try {
            synchronized (keys) {
                SecretKey key = keys.get(keyId);
                if (key == null) {
                    return Promise.ofException(new KeyNotFoundException("Key not found: " + keyId));
                }
                return Promise.of(key);
            }
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    @Override
    public String getCurrentKeyId() {
        return currentKeyId;
    }

    @Override
    public Promise<String> rotateKey() {
        try {
            // Ensure one rotation at a time to keep key generation and map update atomic
            synchronized (keys) {
                // Generate a new key
                SecretKey newKey = keyGenerator.generateKey();

                // Generate a new key ID with UUID to avoid collisions under concurrent rotations
                String newKeyId = "key-" + UUID.randomUUID().toString();

                // Add the new key to the provided map so external holders observe it
                keys.put(newKeyId, newKey);

                // Update the current key ID
                String oldKeyId = this.currentKeyId;
                this.currentKeyId = newKeyId;

                logger.info("Rotated encryption key from {} to {}", oldKeyId, newKeyId);
                return Promise.of(newKeyId);
            }
        } catch (Exception e) {
            return Promise.ofException(new RuntimeException("Failed to generate new key", e));
        }
    }

    /**
     * Gets the number of keys currently managed by this KeyManager.
     *
     * @return The number of keys
     */
    public int getKeyCount() {
        synchronized (keys) {
            return keys.size();
        }
    }

    @Override
    public boolean isKeyHealthy(String keyId) {
        synchronized (keys) {
            return keys.containsKey(keyId);
        }
    }

    @Override
    public long getKeyAge(String keyId) {
        // For this simple implementation, return 0 since we don't track key creation time
        // In a real implementation, you would store the creation timestamp with each key
        return 0;
    }

    @Override
    public long getLastRotationTime(String keyId) {
        // For this simple implementation, return current time
        // In a real implementation, you would store the rotation timestamp with each key
        return System.currentTimeMillis();
    }

    /**
     * Exception thrown when a requested key is not found.
     */
    public static class KeyNotFoundException extends RuntimeException {
        public KeyNotFoundException(String message) {
            super(message);
        }

        public KeyNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
