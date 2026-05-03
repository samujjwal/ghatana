package com.ghatana.digitalmarketing.application.privacy;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Service for encrypting and decrypting sensitive contact data (DMOS-P1-014).
 *
 * <p>Uses AES-GCM for authenticated encryption. The encryption key is derived from
 * an environment variable or a default key (must be changed in production).</p>
 *
 * @doc.type class
 * @doc.purpose Encrypts/decrypts sensitive contact data (DMOS-P1-014)
 * @doc.layer application
 * @doc.pattern Service
 */
public final class ContactEncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final SecretKey encryptionKey;
    private final SecureRandom secureRandom;

    public ContactEncryptionService() {
        this.encryptionKey = deriveKey();
        this.secureRandom = new SecureRandom();
    }

    /**
     * Encrypts a contact value using AES-GCM.
     *
     * @param plainText the plain text contact value
     * @return base64-encoded ciphertext (IV + ciphertext + tag)
     */
    public String encrypt(String plainText) {
        Objects.requireNonNull(plainText, "plainText must not be null");

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);
            byte[] cipherBytes = cipher.doFinal(plainBytes);

            // Combine IV + ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
            buffer.put(iv);
            buffer.put(cipherBytes);
            byte[] combined = buffer.array();

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a contact value using AES-GCM.
     *
     * @param cipherText base64-encoded ciphertext (IV + ciphertext + tag)
     * @return the plain text contact value
     */
    public String decrypt(String cipherText) {
        Objects.requireNonNull(cipherText, "cipherText must not be null");

        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);

            if (combined.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Ciphertext too short");
            }

            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] cipherBytes = new byte[combined.length - GCM_IV_LENGTH];
            buffer.get(cipherBytes);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    /**
     * Derives the encryption key from an environment variable or default.
     */
    private SecretKey deriveKey() {
        String keySource = System.getenv("DMOS_CONTACT_ENCRYPTION_KEY");
        if (keySource == null || keySource.isBlank()) {
            keySource = "default-encryption-key-change-in-production-32-bytes";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(keySource.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
