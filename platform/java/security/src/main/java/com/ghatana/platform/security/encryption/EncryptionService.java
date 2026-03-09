package com.ghatana.platform.security.encryption;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that provides encryption/decryption operations for different storage types.
 * Integrates with KeyManager for secure key management.
 
 *
 * @doc.type class
 * @doc.purpose Encryption service
 * @doc.layer core
 * @doc.pattern Service
*/
public class EncryptionService {
    private static final Logger logger = LoggerFactory.getLogger(EncryptionService.class);
    
    private final EncryptionProvider encryptionProvider;
    private final Eventloop eventloop;
    
    /**
     * Creates a new EncryptionService with the specified encryption provider and event loop.
     *
     * @param encryptionProvider The encryption provider to use
     * @param eventloop The event loop for async operations
     */
    public EncryptionService(EncryptionProvider encryptionProvider, Eventloop eventloop) {
        this.encryptionProvider = encryptionProvider;
        this.eventloop = eventloop;
        logger.info("Initialized EncryptionService with provider: {}", 
            encryptionProvider.getClass().getSimpleName());
    }
    
    /**
     * Encrypts the given data asynchronously.
     *
     * @param data The data to encrypt
     * @return A promise that completes with the encrypted data
     */
    /**
     * Encrypts the given data asynchronously.
     *
     * @param data The data to encrypt
     * @return A promise that completes with the encrypted data
     */
    public Promise<byte[]> encryptAsync(byte[] data) {
        return encryptionProvider.encrypt(data)
            .mapException(e -> {
                logger.error("Encryption failed", e);
                return new EncryptionException("Failed to encrypt data", e);
            });
    }
    
    /**
     * Decrypts the given data asynchronously.
     *
     * @param encryptedData The encrypted data to decrypt
     * @return A promise that completes with the decrypted data
     */
    /**
     * Decrypts the given data asynchronously.
     *
     * @param encryptedData The encrypted data to decrypt
     * @return A promise that completes with the decrypted data
     */
    public Promise<byte[]> decryptAsync(byte[] encryptedData) {
        return encryptionProvider.decrypt(encryptedData)
            .mapException(e -> {
                logger.error("Decryption failed", e);
                return new EncryptionException("Failed to decrypt data", e);
            });
    }
    
    /**
     * Encrypts the given data with a specific key ID.
     *
     * @param data The data to encrypt
     * @param keyId The ID of the key to use for encryption
     * @return A promise that completes with the encrypted data
     */
    public Promise<byte[]> encryptWithKey(byte[] data, String keyId) {
        return encryptionProvider.encrypt(data)
            .mapException(e -> {
                logger.error("Encryption with key {} failed", keyId, e);
                return new EncryptionException("Failed to encrypt data with key " + keyId, e);
            });
    }

    /**
     * Decrypts the given data with a specific key ID.
     *
     * @param encryptedData The encrypted data to decrypt
     * @param keyId The ID of the key to use for decryption
     * @return A promise that completes with the decrypted data
     */
    public Promise<byte[]> decryptWithKey(byte[] encryptedData, String keyId) {
        return encryptionProvider.decrypt(encryptedData)
            .mapException(e -> {
                logger.error("Decryption with key {} failed", keyId, e);
                return new EncryptionException("Failed to decrypt data with key " + keyId, e);
            });
    }

    /**
     * Gets the encryption provider used by this service.
     *
     * @return The encryption provider
     */
    public EncryptionProvider getEncryptionProvider() {
        return encryptionProvider;
    }
}
