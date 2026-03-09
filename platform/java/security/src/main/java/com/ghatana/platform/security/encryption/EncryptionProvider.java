package com.ghatana.platform.security.encryption;

import io.activej.promise.Promise;

/**
 * Interface for encryption providers that handle encryption and decryption of data.
 
 *
 * @doc.type interface
 * @doc.purpose Encryption provider
 * @doc.layer core
 * @doc.pattern Provider
*/
public interface EncryptionProvider {
    /**
     * Encrypts the given data.
     *
     * @param data The data to encrypt
     * @return A promise that completes with the encrypted data
     */
    Promise<byte[]> encrypt(byte[] data);
    
    /**
     * Decrypts the given encrypted data.
     *
     * @param encryptedData The encrypted data to decrypt
     * @return A promise that completes with the decrypted data
     */
    Promise<byte[]> decrypt(byte[] encryptedData);
    
    /**
     * Gets the algorithm used for encryption.
     *
     * @return The encryption algorithm name
     */
    String getAlgorithm();
    
    /**
     * Gets the key identifier used for encryption/decryption.
     *
     * @return The key identifier
     */
    String getKeyId();
}
