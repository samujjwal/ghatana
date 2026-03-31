/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Infrastructure — Encryption-at-rest service
 */
package com.ghatana.yappc.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * AES-256-GCM encryption service for protecting sensitive YAPPC data at rest.
 *
 * <p>Each encryption call produces a self-contained ciphertext that embeds the
 * random IV, so callers do not need to manage IV storage separately. The format
 * stored is: {@code IV (12 bytes) || GCM ciphertext+tag}.
 *
 * <h2>Key Management</h2>
 * <p>The encryption key is expected as a Base64-encoded 256-bit value supplied
 * via the {@code YAPPC_ENCRYPTION_KEY} environment variable. In development the
 * service can be constructed with a randomly-generated key (use
 * {@link #generateKey()} and store the result securely before first use).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * EncryptionService enc = EncryptionService.fromEnvironment();
 * String ciphertext = enc.encrypt("sensitive-data");
 * String plaintext  = enc.decrypt(ciphertext);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose AES-256-GCM encryption-at-rest service for YAPPC sensitive data
 * @doc.layer product
 * @doc.pattern Service
 */
public final class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    private static final String ALGORITHM     = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int    GCM_IV_LENGTH = 12;  // bytes — NIST recommended
    private static final int    GCM_TAG_BITS  = 128; // maximum GCM tag length
    private static final int    KEY_BITS      = 256;

    private static final String ENV_KEY = "YAPPC_ENCRYPTION_KEY";

    private final SecretKey secretKey;

    /**
     * Creates an {@code EncryptionService} backed by the provided AES key bytes.
     *
     * @param keyBytes raw 32-byte (256-bit) AES key
     * @throws IllegalArgumentException if keyBytes is not 32 bytes
     */
    public EncryptionService(byte[] keyBytes) {
        Objects.requireNonNull(keyBytes, "keyBytes must not be null");
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "AES-256 key must be exactly 32 bytes, got " + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }

    // ── Factory methods ──────────────────────────────────────────────────────

    /**
     * Loads the encryption key from the {@code YAPPC_ENCRYPTION_KEY} environment variable.
     * The value must be a Base64-encoded 256-bit key.
     *
     * @throws IllegalStateException if the environment variable is not set or the key is invalid
     */
    public static EncryptionService fromEnvironment() {
        String encoded = System.getenv(ENV_KEY);
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    ENV_KEY + " environment variable is not set. "
                    + "Generate a key with EncryptionService.generateKey() and set it before deploying.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid Base64 value in " + ENV_KEY, e);
        }
        return new EncryptionService(keyBytes);
    }

    /**
     * Generates a new random 256-bit AES key and returns it as a Base64 string.
     * Store the result in a secret manager (e.g. Vault, AWS Secrets Manager) and
     * set it as {@code YAPPC_ENCRYPTION_KEY} before the service starts.
     *
     * @return Base64-encoded 256-bit key ready for use with {@link #fromEnvironment()}
     */
    public static String generateKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(KEY_ALGORITHM);
            kg.init(KEY_BITS, new SecureRandom());
            return Base64.getEncoder().encodeToString(kg.generateKey().getEncoded());
        } catch (Exception e) {
            throw new EncryptionException("Failed to generate AES-256 key", e);
        }
    }

    // ── Core operations ──────────────────────────────────────────────────────

    /**
     * Encrypts {@code plaintext} using AES-256-GCM.
     *
     * @param plaintext the data to encrypt; must not be null
     * @return Base64-encoded ciphertext (IV prepended)
     * @throws EncryptionException if encryption fails
     */
    public String encrypt(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        try {
            byte[] iv         = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec spec   = new GCMParameterSpec(GCM_TAG_BITS, iv);
            Cipher           cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Pack: IV (12 bytes) || ciphertext
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ciphertext.length);
            buf.put(iv);
            buf.put(ciphertext);

            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new EncryptionException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a ciphertext previously produced by {@link #encrypt(String)}.
     *
     * @param encoded Base64-encoded ciphertext (IV prepended)
     * @return the original plaintext
     * @throws EncryptionException if decryption fails or the ciphertext is corrupt
     */
    public String decrypt(String encoded) {
        Objects.requireNonNull(encoded, "encoded ciphertext must not be null");
        try {
            byte[]  raw = Base64.getDecoder().decode(encoded);
            if (raw.length < GCM_IV_LENGTH) {
                throw new EncryptionException("Ciphertext too short — possibly corrupt", null);
            }

            ByteBuffer buf = ByteBuffer.wrap(raw);
            byte[] iv         = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[raw.length - GCM_IV_LENGTH];
            buf.get(iv);
            buf.get(ciphertext);

            GCMParameterSpec spec   = new GCMParameterSpec(GCM_TAG_BITS, iv);
            Cipher           cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("Decryption failed — data may be corrupt or key may be wrong", e);
        }
    }

    /**
     * Encrypts sensitive bytes directly, returning a Base64-encoded result.
     */
    public String encryptBytes(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        return encrypt(Base64.getEncoder().encodeToString(data));
    }

    /**
     * Decrypts to raw bytes (inverse of {@link #encryptBytes(byte[])}).
     */
    public byte[] decryptBytes(String encoded) {
        return Base64.getDecoder().decode(decrypt(encoded));
    }

    // ── Exception ────────────────────────────────────────────────────────────

    /**
     * Unchecked exception thrown when encryption or decryption fails.
     *
     * @doc.type class
     * @doc.purpose Unchecked exception for encryption/decryption failures
     * @doc.layer product
     * @doc.pattern Exception
     */
    public static final class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
