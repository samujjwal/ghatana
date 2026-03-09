package com.ghatana.security.storage;

import com.ghatana.platform.security.encryption.EncryptionService;
import com.ghatana.security.keys.KeyManager;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Service that provides secure storage with encryption at rest.
 * Integrates with KeyManager for key management and EncryptionService for data protection.
 
 *
 * @doc.type class
 * @doc.purpose Encrypted storage service
 * @doc.layer core
 * @doc.pattern Service
*/
public class EncryptedStorageService {
    private static final Logger logger = LoggerFactory.getLogger(EncryptedStorageService.class);

    private final KeyManager keyManager;
    private final EncryptionService encryptionService;
    private final Executor executor;
    private final Map<String, byte[]> storage;

    /**
     * Creates a new EncryptedStorageService.
     *
     * @param keyManager The key manager for encryption keys
     * @param encryptionService The encryption service
     * @param executor The executor for async operations
     */
    public EncryptedStorageService(
            KeyManager keyManager,
            EncryptionService encryptionService,
            Executor executor) {

        this.keyManager = keyManager;
        this.encryptionService = encryptionService;
        this.executor = executor;
        this.storage = new ConcurrentHashMap<>();

        logger.info("Initialized EncryptedStorageService with key manager: {}",
            keyManager.getClass().getSimpleName());
    }

    /**
     * Stores data securely with encryption.
     *
     * @param key The key under which to store the data
     * @param data The data to store
     * @return A promise that completes when the operation is done
     */
    public Promise<Void> store(String key, byte[] data) {
        return encryptionService.encryptAsync(data)
            .then(encrypted -> {
                // Perform the map update synchronously on the caller thread. Tests don't run an ActiveJ Reactor
                // in these unit tests, so using Promise.ofBlocking causes "No reactor in current thread".
                storage.put(key, encrypted);
                logger.debug("Stored encrypted data for key: {}", key);
                return Promise.of((Void) null);
            });
    }

    /**
     * Retrieves and decrypts stored data.
     *
     * @param key The key of the data to retrieve
     * @return A promise that completes with the decrypted data, or null if not found
     */
    public Promise<byte[]> retrieve(String key) {
        byte[] encrypted = storage.get(key);
        if (encrypted == null) {
            return Promise.of((byte[]) null);
        }
        return encryptionService.decryptAsync(encrypted);
    }

    /**
     * Removes stored data.
     *
     * @param key The key of the data to remove
     * @return A promise that completes when the operation is done
     */
    public Promise<Void> remove(String key) {
        storage.remove(key);
        logger.debug("Removed data for key: {}", key);
        return Promise.of((Void) null);
    }

    /**
     * Checks if the storage contains data for the given key.
     *
     * @param key The key to check
     * @return A promise that completes with true if the key exists, false otherwise
     */
    public Promise<Boolean> contains(String key) {
        return Promise.of(storage.containsKey(key));
    }

    /**
     * Gets the number of items in the storage.
     *
     * @return A promise that completes with the number of items
     */
    public Promise<Integer> size() {
        return Promise.of(storage.size());
    }

    /**
     * Stores data securely with encryption (synchronous version).
     *
     * @param key The key under which to store the data
     * @param data The data to store
     */
    public void storeSecurely(String key, byte[] data) {
        try {
            byte[] encrypted = encryptionService.encryptAsync(data).getResult();
            storage.put(key, encrypted);
            logger.debug("Stored encrypted data for key: {}", key);
        } catch (Exception e) {
            logger.error("Failed to store data securely for key: {}", key, e);
            throw new RuntimeException("Failed to store data securely", e);
        }
    }

    /**
     * Clears all data from the storage.
     *
     * @return A promise that completes when the operation is done
     */
    public Promise<Void> clear() {
        int count = storage.size();
        storage.clear();
        logger.info("Cleared {} items from encrypted storage", count);
        return Promise.of((Void) null);
    }
}
