package com.ghatana.platform.security.encryption.impl;

import com.ghatana.platform.security.encryption.EncryptionProvider;
import io.activej.promise.Promise;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.Executor;

/**
 * AES-GCM implementation of the EncryptionProvider interface.
 * Provides authenticated encryption with associated data (AEAD) using AES in GCM mode.
 
 *
 * @doc.type class
 * @doc.purpose Aes gcm encryption provider
 * @doc.layer core
 * @doc.pattern Provider
*/
public class AesGcmEncryptionProvider implements EncryptionProvider {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits for GCM
    private static final int GCM_TAG_LENGTH = 16; // 128 bits for GCM tag

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;
    private final String keyId;
    private final Executor executor;

    /**
     * Creates a new AesGcmEncryptionProvider with the specified key.
     *
     * @param key The secret key to use for encryption/decryption
     * @param keyId The identifier for the key
     */
    public AesGcmEncryptionProvider(byte[] key, String keyId, Executor executor) {
        this.secretKey = new javax.crypto.spec.SecretKeySpec(key, "AES");
        this.secureRandom = new SecureRandom();
        this.keyId = keyId;
        this.executor = executor != null ? executor : Runnable::run; // Use provided executor or direct execution
    }

    /**
     * Creates a new AesGcmEncryptionProvider with a newly generated key.
     *
     * @param keySize The size of the key in bits (128, 192, or 256)
     * @param keyId The identifier for the key
     * @return A new AesGcmEncryptionProvider instance
     */
    public static AesGcmEncryptionProvider withNewKey(int keySize, String keyId) {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(keySize);
            return new AesGcmEncryptionProvider(keyGen.generateKey().getEncoded(), keyId, null);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate AES key", e);
        }
    }

    public static AesGcmEncryptionProvider withNewKey(int keySize, String keyId, Executor executor) {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(keySize);
            return new AesGcmEncryptionProvider(keyGen.generateKey().getEncoded(), keyId, executor);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate AES key", e);
        }
    }

    @Override
    public Promise<byte[]> encrypt(byte[] data) {
        try {
            // Generate a random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize the cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            // Encrypt the data
            byte[] encrypted = cipher.doFinal(data);

            // Combine IV and encrypted data
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encrypted.length);
            byteBuffer.put(iv);
            byteBuffer.put(encrypted);

            return Promise.of(byteBuffer.array());
        } catch (Exception e) {
            return Promise.ofException(new RuntimeException("Encryption failed", e));
        }
    }

    @Override
    public Promise<byte[]> decrypt(byte[] encryptedData) {
        try {
            // Extract IV and encrypted data
            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedData);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] encrypted = new byte[byteBuffer.remaining()];
            byteBuffer.get(encrypted);

            // Initialize the cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            // Decrypt the data
            return Promise.of(cipher.doFinal(encrypted));
        } catch (Exception e) {
            return Promise.ofException(new RuntimeException("Decryption failed", e));
        }
    }

    @Override
    public String getAlgorithm() {
        return ALGORITHM;
    }

    @Override
    public String getKeyId() {
        return keyId;
    }

    /**
     * Gets the secret key used by this provider.
     *
     * @return The secret key
     */
    public SecretKey getSecretKey() {
        return secretKey;
    }
}
