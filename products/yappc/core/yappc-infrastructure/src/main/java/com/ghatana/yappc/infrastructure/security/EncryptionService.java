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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * AES-256-GCM encryption service for protecting sensitive YAPPC data at rest.
 *
 * <p>Each encryption call produces a self-contained ciphertext that embeds the
 * random IV, so callers do not need to manage IV storage separately. The format
 * stored is: {@code IV (12 bytes) || GCM ciphertext+tag}.
 *
 * <p><b>Key Management</b></p>
 * <p>The encryption key should be stored in a secret manager and made available
 * through mounted secret files. Legacy environment variable loading is still
 * available as an explicit compatibility mode.
 *
 * <p><b>Usage</b></p>
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
    private static final String ENV_SECRET_NAME = "YAPPC_ENCRYPTION_KEY_SECRET_NAME";
    private static final String ENV_SECRETS_DIR = "YAPPC_SECRETS_DIR";
    private static final String ENV_ALLOW_LEGACY_ENV_KEY = "YAPPC_ALLOW_LEGACY_ENV_KEY";
    private static final String DEFAULT_SECRET_NAME = "yappc/encryption-key";
    private static final String DEFAULT_SECRETS_DIR = "/var/run/secrets/ghatana";

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
     * Secret provider abstraction for key retrieval from external secret stores.
     */
    @FunctionalInterface
    public interface SecretProvider {
        Optional<String> getSecret(String secretName);
    }

    /**
     * Reads secrets from mounted files where each secret is stored as
     * {@code <secretsDir>/<secretName-with-slashes-replaced-by-underscores>}.
     */
    public static final class MountedFileSecretProvider implements SecretProvider {
        private final Path secretsDir;

        public MountedFileSecretProvider(Path secretsDir) {
            this.secretsDir = Objects.requireNonNull(secretsDir, "secretsDir must not be null");
        }

        @Override
        public Optional<String> getSecret(String secretName) {
            String normalizedName = normalizeSecretFileName(secretName);
            Path secretFile = secretsDir.resolve(normalizedName);
            if (!Files.isRegularFile(secretFile)) {
                return Optional.empty();
            }
            try {
                String raw = Files.readString(secretFile, StandardCharsets.UTF_8).trim();
                return raw.isBlank() ? Optional.empty() : Optional.of(raw);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to read secret file: " + secretFile, e);
            }
        }
    }

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
     * Loads encryption key from configured secret sources.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>Mounted secret provider (secret manager integration)</li>
     *   <li>Legacy environment variable {@code YAPPC_ENCRYPTION_KEY} when
     *       {@code YAPPC_ALLOW_LEGACY_ENV_KEY=true}</li>
     * </ol>
     *
     * @throws IllegalStateException when no key source is configured or key is invalid
     */
    public static EncryptionService fromConfiguredSources() {
        String secretName = readOrDefault(System.getenv(ENV_SECRET_NAME), DEFAULT_SECRET_NAME);
        String secretsDir = readOrDefault(System.getenv(ENV_SECRETS_DIR), DEFAULT_SECRETS_DIR);
        boolean allowLegacyEnv = Boolean.parseBoolean(
                readOrDefault(System.getenv(ENV_ALLOW_LEGACY_ENV_KEY), "false"));

        SecretProvider provider = new MountedFileSecretProvider(Path.of(secretsDir));
        return fromConfiguredSources(provider, secretName, System.getenv(ENV_KEY), allowLegacyEnv);
    }

    /**
     * Try to build encryption service from configured sources.
     * Returns empty when no source is configured.
     */
    public static Optional<EncryptionService> tryFromConfiguredSources() {
        String secretName = readOrDefault(System.getenv(ENV_SECRET_NAME), DEFAULT_SECRET_NAME);
        String secretsDir = readOrDefault(System.getenv(ENV_SECRETS_DIR), DEFAULT_SECRETS_DIR);
        boolean allowLegacyEnv = Boolean.parseBoolean(
                readOrDefault(System.getenv(ENV_ALLOW_LEGACY_ENV_KEY), "false"));

        SecretProvider provider = new MountedFileSecretProvider(Path.of(secretsDir));
        return tryFromConfiguredSources(provider, secretName, System.getenv(ENV_KEY), allowLegacyEnv);
    }

    static EncryptionService fromConfiguredSources(
            SecretProvider secretProvider,
            String secretName,
            String legacyEncodedKey,
            boolean allowLegacyEnv) {
        return tryFromConfiguredSources(secretProvider, secretName, legacyEncodedKey, allowLegacyEnv)
                .orElseThrow(() -> new IllegalStateException(
                        "Encryption key not configured via secret manager. "
                                + "Configure mounted secret '" + secretName + "' (" + ENV_SECRET_NAME + ")"
                                + " and optional directory via " + ENV_SECRETS_DIR + "."
                                + (allowLegacyEnv ? "" : " Legacy env fallback is disabled.")));
    }

    static Optional<EncryptionService> tryFromConfiguredSources(
            SecretProvider secretProvider,
            String secretName,
            String legacyEncodedKey,
            boolean allowLegacyEnv) {
        Optional<String> secretValue = secretProvider.getSecret(secretName)
                .filter(value -> !value.isBlank());
        if (secretValue.isPresent()) {
            log.info("Loaded encryption key from secret provider using secret name {}", secretName);
            return Optional.of(new EncryptionService(decodeKey(secretValue.get(), "secret:" + secretName)));
        }

        if (allowLegacyEnv && legacyEncodedKey != null && !legacyEncodedKey.isBlank()) {
            log.warn("Using legacy {} environment variable for encryption key. Migrate to secret manager source.", ENV_KEY);
            return Optional.of(new EncryptionService(decodeKey(legacyEncodedKey, ENV_KEY)));
        }

        return Optional.empty();
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

    private static byte[] decodeKey(String encoded, String sourceName) {
        try {
            return Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid Base64 value in encryption key source: " + sourceName, e);
        }
    }

    private static String normalizeSecretFileName(String secretName) {
        return secretName.replace('/', '_').replace('\\', '_');
    }

    private static String readOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
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
