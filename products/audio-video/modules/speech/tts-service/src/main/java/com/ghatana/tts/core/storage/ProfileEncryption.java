package com.ghatana.tts.core.storage;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Profile encryption using AES-GCM for TTS.
 *
 * <p>Provides encryption and decryption of user profile data with
 * authenticated encryption to ensure confidentiality and integrity.
 *
 * @doc.type class
 * @doc.purpose Profile data encryption/decryption
 * @doc.layer core
 * @doc.pattern Utility
 */
public class ProfileEncryption {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits
    private static final int KEY_SIZE = 256; // 256 bits

    private final SecretKey key;
    private final SecureRandom secureRandom;

    /**
     * Create encryption with a generated key.
     */
    public ProfileEncryption() throws Exception {
        this.secureRandom = new SecureRandom();
        this.key = generateKey();
    }

    /**
     * Create encryption with a provided key.
     *
     * @param encodedKey Base64-encoded AES key
     */
    public ProfileEncryption(String encodedKey) {
        this.secureRandom = new SecureRandom();
        byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
        this.key = new SecretKeySpec(decodedKey, 0, decodedKey.length, ALGORITHM);
    }

    /**
     * Generate a new AES-256 key.
     */
    private SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(KEY_SIZE, secureRandom);
        return keyGen.generateKey();
    }

    /**
     * Get the encryption key as a Base64-encoded string.
     */
    public String getEncodedKey() {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * Encrypt data.
     *
     * @param plaintext The data to encrypt
     * @return Encrypted data with IV prepended
     */
    public byte[] encrypt(byte[] plaintext) throws Exception {
        // Generate random IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        // Create cipher
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

        // Encrypt
        byte[] ciphertext = cipher.doFinal(plaintext);

        // Prepend IV to ciphertext
        ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
        buffer.put(iv);
        buffer.put(ciphertext);

        return buffer.array();
    }

    /**
     * Encrypt a string.
     *
     * @param plaintext The string to encrypt
     * @return Base64-encoded encrypted data
     */
    public String encryptString(String plaintext) throws Exception {
        byte[] encrypted = encrypt(plaintext.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * Decrypt data.
     *
     * @param encryptedData The encrypted data with IV prepended
     * @return Decrypted plaintext
     */
    public byte[] decrypt(byte[] encryptedData) throws Exception {
        // Extract IV
        ByteBuffer buffer = ByteBuffer.wrap(encryptedData);
        byte[] iv = new byte[GCM_IV_LENGTH];
        buffer.get(iv);

        // Extract ciphertext
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        // Create cipher
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

        // Decrypt
        return cipher.doFinal(ciphertext);
    }

    /**
     * Decrypt a Base64-encoded string.
     *
     * @param encryptedString Base64-encoded encrypted data
     * @return Decrypted string
     */
    public String decryptString(String encryptedString) throws Exception {
        byte[] encryptedData = Base64.getDecoder().decode(encryptedString);
        byte[] decrypted = decrypt(encryptedData);
        return new String(decrypted, "UTF-8");
    }

    /**
     * Derive an encryption key from a password using PBKDF2.
     *
     * @param password The password
     * @param salt The salt (should be random and stored)
     * @return Encryption instance with derived key
     */
    public static ProfileEncryption fromPassword(String password, byte[] salt) throws Exception {
        javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
            password.toCharArray(),
            salt,
            100000, // 100k iterations
            KEY_SIZE
        );
        SecretKey tmp = factory.generateSecret(spec);
        SecretKey key = new SecretKeySpec(tmp.getEncoded(), ALGORITHM);

        String encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());
        return new ProfileEncryption(encodedKey);
    }
}

