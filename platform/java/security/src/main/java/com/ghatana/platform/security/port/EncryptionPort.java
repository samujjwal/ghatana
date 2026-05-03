package com.ghatana.platform.security.port;

import io.activej.promise.Promise;

/**
 * Port interface for encryption operations used for PII protection.
 * Provides AES encryption for storing raw PII values in encrypted form.
 *
 * @doc.type interface
 * @doc.purpose Secure encryption port for PII protection
 * @doc.layer core
 * @doc.pattern Port
 */
public interface EncryptionPort {

    /**
     * Encrypts sensitive data for storage.
     *
     * @param plaintext The plaintext data to encrypt
     * @return A promise that completes with the encrypted data (base64-encoded)
     */
    Promise<String> encrypt(String plaintext);

    /**
     * Decrypts sensitive data for use.
     *
     * @param ciphertext The encrypted data (base64-encoded)
     * @return A promise that completes with the decrypted plaintext
     */
    Promise<String> decrypt(String ciphertext);

    /**
     * Encrypts binary data for storage.
     *
     * @param data The binary data to encrypt
     * @return A promise that completes with the encrypted data
     */
    Promise<byte[]> encryptBytes(byte[] data);

    /**
     * Decrypts binary data for use.
     *
     * @param encryptedData The encrypted data
     * @return A promise that completes with the decrypted data
     */
    Promise<byte[]> decryptBytes(byte[] encryptedData);

    /**
     * Gets the encryption algorithm being used.
     *
     * @return The algorithm name (e.g., "AES/GCM/NoPadding")
     */
    String getAlgorithm();

    /**
     * Gets the key identifier for the encryption key.
     *
     * @return The key identifier
     */
    String getKeyId();
}
