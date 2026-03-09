package com.ghatana.datacloud.infrastructure.encryption;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Simple encryption service for Data Cloud using AES-GCM.
 * 
 * <p>Provides encryption and decryption of data before storage in S3.
 * Uses AES-256 in GCM mode for authenticated encryption.</p>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * // Generate a key
 * String key = SimpleEncryptionService.generateKey();
 * 
 * // Create service
 * SimpleEncryptionService service = new SimpleEncryptionService(key);
 * 
 * // Encrypt data
 * byte[] encrypted = service.encrypt(plaintext);
 * 
 * // Decrypt data
 * byte[] decrypted = service.decrypt(encrypted);
 * }</pre>
 * 
 * @doc.type service
 * @doc.purpose Data encryption/decryption using AES-GCM
 * @doc.layer infrastructure
 * @doc.pattern Strategy
 */
public class SimpleEncryptionService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int AES_KEY_SIZE = 256;
    
    private final SecretKey key;
    private final SecureRandom random;
    
    /**
     * Creates a new encryption service with the specified key.
     * 
     * @param base64Key the base64-encoded AES-256 key
     * @throws IllegalArgumentException if the key is invalid
     */
    public SimpleEncryptionService(String base64Key) {
        Objects.requireNonNull(base64Key, "Encryption key cannot be null");
        
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            if (keyBytes.length != 32) { // 256 bits
                throw new IllegalArgumentException("Invalid key length: expected 32 bytes, got " + keyBytes.length);
            }
            this.key = new SecretKeySpec(keyBytes, "AES");
            this.random = new SecureRandom();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base64 key: " + e.getMessage(), e);
        }
    }
    
    /**
     * Encrypts the given data.
     * 
     * <p>The encrypted output includes the IV prepended to the ciphertext.
     * Format: [12-byte IV][encrypted data with authentication tag]</p>
     * 
     * @param data the data to encrypt
     * @return the encrypted data with IV prepended
     * @throws EncryptionException if encryption fails
     */
    public byte[] encrypt(byte[] data) {
        Objects.requireNonNull(data, "Data to encrypt cannot be null");
        
        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            
            // Encrypt data
            byte[] encrypted = cipher.doFinal(data);
            
            // Prepend IV to encrypted data
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            
            return result;
        } catch (Exception e) {
            throw new EncryptionException("Encryption failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Decrypts the given data.
     * 
     * <p>Expects the input to have the IV prepended to the ciphertext.</p>
     * 
     * @param data the encrypted data with IV prepended
     * @return the decrypted data
     * @throws EncryptionException if decryption fails
     */
    public byte[] decrypt(byte[] data) {
        Objects.requireNonNull(data, "Data to decrypt cannot be null");
        
        if (data.length < GCM_IV_LENGTH) {
            throw new EncryptionException("Invalid encrypted data: too short");
        }
        
        try {
            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(data, 0, iv, 0, iv.length);
            
            // Extract encrypted data
            byte[] encrypted = new byte[data.length - iv.length];
            System.arraycopy(data, iv.length, encrypted, 0, encrypted.length);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            
            // Decrypt data
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            throw new EncryptionException("Decryption failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generates a new random AES-256 key.
     * 
     * @return the base64-encoded key
     */
    public static String generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_SIZE);
            SecretKey key = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }
    
    /**
     * Exception thrown when encryption or decryption fails.
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message) {
            super(message);
        }
        
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Main method for generating encryption keys.
     * 
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        System.out.println("Generated AES-256 Key:");
        System.out.println(generateKey());
        System.out.println("\nStore this key securely in your secrets manager!");
    }
}
