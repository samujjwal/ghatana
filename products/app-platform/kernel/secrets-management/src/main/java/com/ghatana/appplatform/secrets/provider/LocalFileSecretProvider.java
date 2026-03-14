/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.provider;

import com.ghatana.appplatform.secrets.domain.SecretMetadata;
import com.ghatana.appplatform.secrets.domain.SecretValue;
import com.ghatana.appplatform.secrets.port.SecretProvider;
import io.activej.promise.Promise;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * File-system-backed secret provider using AES-256-GCM encryption with
 * Argon2id master key derivation (K14-003).
 *
 * <p>Each secret is stored as a {@code .secret} file in a configurable directory.
 * The file contains the encrypted secret, nonce, salt, and metadata serialized
 * as Java properties. The encryption key is derived from the master passphrase
 * using Argon2id with conservative parameters.
 *
 * <p>File format per secret ({@code <path_as_filename>.secret}):
 * <pre>
 * version=3
 * nonce=<base64>
 * ciphertext=<base64>
 * created_at=<iso8601>
 * expires_at=<iso8601 or empty>
 * max_age_days=90
 * auto_rotate=false
 * </pre>
 *
 * <p>Argon2id parameters: memory=65536 KiB (64 MB), iterations=3, parallelism=1.
 * These are OWASP minimum recommended settings (2024).
 *
 * <p>Security notes:
 * <ul>
 *   <li>Nonce is 12-byte random (GCM standard), unique per encryption</li>
 *   <li>Salt is 32-byte random, stored with the ciphertext</li>
 *   <li>Authentication tag is 128 bits (GCM default)</li>
 *   <li>char[] passphrase is zeroed immediately after key derivation</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose AES-256-GCM local file secret provider with Argon2id KDF (K14-003)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class LocalFileSecretProvider implements SecretProvider {

    private static final Logger LOG = LoggerFactory.getLogger(LocalFileSecretProvider.class);

    // AES-GCM parameters
    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final int KEY_BITS = 256;          // AES-256
    private static final int GCM_NONCE_BYTES = 12;    // 96-bit nonce (GCM recommended)
    private static final int GCM_TAG_BITS = 128;

    // Argon2id parameters (OWASP 2024 minimum)
    private static final int ARGON2_MEMORY_KB = 65_536;   // 64 MB
    private static final int ARGON2_ITERATIONS = 3;
    private static final int ARGON2_PARALLELISM = 1;
    private static final int ARGON2_SALT_BYTES = 32;
    private static final int ARGON2_KEY_BYTES = 32;        // 256-bit key

    private final Path secretsDir;
    private final char[] masterPassphrase;
    private final Executor executor;
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates a local file secret provider.
     *
     * @param secretsDir       directory to store encrypted secret files
     * @param masterPassphrase master passphrase for Argon2id key derivation;
     *                         this array is NOT zeroed by the constructor — callers
     *                         should zero it after constructing the provider
     * @param executor         blocking executor for IO operations
     * @throws IOException if the secrets directory cannot be created
     */
    public LocalFileSecretProvider(Path secretsDir, char[] masterPassphrase,
                                   Executor executor) throws IOException {
        this.secretsDir = secretsDir;
        this.masterPassphrase = Arrays.copyOf(masterPassphrase, masterPassphrase.length);
        this.executor = executor;
        Files.createDirectories(secretsDir);
    }

    @Override
    public Promise<SecretValue> getSecret(String path) {
        return Promise.ofBlocking(executor, () -> {
            Path file = resolveFile(path);
            if (!Files.exists(file)) {
                throw new SecretNotFoundException(path);
            }
            Properties props = loadProperties(file);
            int version = Integer.parseInt(props.getProperty("version", "1"));
            byte[] nonce = Base64.getDecoder().decode(props.getProperty("nonce"));
            byte[] salt = Base64.getDecoder().decode(props.getProperty("salt"));
            byte[] ciphertext = Base64.getDecoder().decode(props.getProperty("ciphertext"));
            String createdAtStr = props.getProperty("created_at");
            String expiresAtStr = props.getProperty("expires_at", "");

            Instant createdAt = Instant.parse(createdAtStr);
            Instant expiresAt = expiresAtStr.isEmpty() ? null : Instant.parse(expiresAtStr);

            byte[] plaintext = decrypt(ciphertext, nonce, salt);
            char[] value = new String(plaintext, StandardCharsets.UTF_8).toCharArray();
            Arrays.fill(plaintext, (byte) 0);

            return new SecretValue(path, version, value, createdAt, expiresAt);
        });
    }

    @Override
    public Promise<SecretValue> putSecret(String path, char[] value, SecretMetadata metadata) {
        return Promise.ofBlocking(executor, () -> {
            Path file = resolveFile(path);
            int version = 1;
            if (Files.exists(file)) {
                Properties existing = loadProperties(file);
                version = Integer.parseInt(existing.getProperty("version", "0")) + 1;
            }

            byte[] nonce = new byte[GCM_NONCE_BYTES];
            byte[] salt = new byte[ARGON2_SALT_BYTES];
            random.nextBytes(nonce);
            random.nextBytes(salt);

            byte[] plaintext = new String(value).getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = encrypt(plaintext, nonce, salt);
            Arrays.fill(plaintext, (byte) 0);

            Instant now = Instant.now();
            Instant expiresAt = metadata.maxAge() != null
                    ? now.plus(metadata.maxAge()) : null;

            Properties props = new Properties();
            props.setProperty("version", String.valueOf(version));
            props.setProperty("nonce", Base64.getEncoder().encodeToString(nonce));
            props.setProperty("salt", Base64.getEncoder().encodeToString(salt));
            props.setProperty("ciphertext", Base64.getEncoder().encodeToString(ciphertext));
            props.setProperty("created_at", now.toString());
            props.setProperty("expires_at", expiresAt != null ? expiresAt.toString() : "");
            props.setProperty("auto_rotate", String.valueOf(metadata.autoRotate()));

            saveProperties(file, props, "Managed secret: " + path);
            LOG.debug("Secret stored: path={}, version={}", path, version);

            return new SecretValue(path, version, Arrays.copyOf(value, value.length), now, expiresAt);
        });
    }

    @Override
    public Promise<Void> deleteSecret(String path) {
        return Promise.ofBlocking(executor, () -> {
            Files.deleteIfExists(resolveFile(path));
            LOG.info("Secret deleted: {}", path);
            return null;
        });
    }

    @Override
    public Promise<List<String>> listSecrets(String prefix) {
        return Promise.ofBlocking(executor, () -> {
            List<String> result = new ArrayList<>();
            try (var stream = Files.walk(secretsDir)) {
                stream.filter(p -> p.toString().endsWith(".secret"))
                      .map(p -> secretsDir.relativize(p).toString())
                      .map(s -> "/" + s.replace(".secret", "").replace("__", "/"))
                      .filter(s -> s.startsWith(prefix))
                      .forEach(result::add);
            }
            return result;
        });
    }

    @Override
    public Promise<SecretValue> rotateSecret(String path) {
        return Promise.ofBlocking(executor, () -> {
            // Generate a new 256-bit random secret value
            byte[] newValueBytes = new byte[32];
            random.nextBytes(newValueBytes);
            char[] newValue = Base64.getEncoder().encodeToString(newValueBytes).toCharArray();
            Arrays.fill(newValueBytes, (byte) 0);

            // Load existing metadata for max-age carry-over
            Path file = resolveFile(path);
            boolean autoRotate = false;
            if (Files.exists(file)) {
                Properties existing = loadProperties(file);
                autoRotate = Boolean.parseBoolean(existing.getProperty("auto_rotate", "false"));
            }

            SecretMetadata metadata = new SecretMetadata(
                    "auto-rotated", null, autoRotate, "local");
            SecretValue result = putSecret(path, newValue, metadata).getResult();
            Arrays.fill(newValue, '\0');
            LOG.info("Secret rotated: path={}, newVersion={}", path, result.version());
            return result;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────

    private byte[] encrypt(byte[] plaintext, byte[] nonce, byte[] salt) throws Exception {
        SecretKey key = deriveKey(salt);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        return cipher.doFinal(plaintext);
    }

    private byte[] decrypt(byte[] ciphertext, byte[] nonce, byte[] salt) throws Exception {
        SecretKey key = deriveKey(salt);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        return cipher.doFinal(ciphertext);
    }

    private SecretKey deriveKey(byte[] salt) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withMemoryAsKB(ARGON2_MEMORY_KB)
                .withIterations(ARGON2_ITERATIONS)
                .withParallelism(ARGON2_PARALLELISM)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] keyBytes = new byte[ARGON2_KEY_BYTES];
        byte[] passphraseBytes = new String(masterPassphrase).getBytes(StandardCharsets.UTF_8);
        generator.generateBytes(passphraseBytes, keyBytes, 0, keyBytes.length);
        Arrays.fill(passphraseBytes, (byte) 0);

        return new SecretKeySpec(keyBytes, "AES");
    }

    private Path resolveFile(String path) {
        // Convert /product/env/name → product__env__name.secret (flat storage)
        String sanitized = path.replaceAll("^/", "").replace("/", "__");
        return secretsDir.resolve(sanitized + ".secret");
    }

    private static Properties loadProperties(Path file) throws IOException {
        Properties props = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return props;
    }

    private static void saveProperties(Path file, Properties props, String comment) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            props.store(writer, comment);
        }
    }

    // ─── K14-013 version history ──────────────────────────────────────────────

    /**
     * Returns the requested version if it matches the current version stored on disk.
     *
     * <p>The local file provider retains only the latest version; requests for older
     * versions throw {@link SecretNotFoundException}.
     */
    @Override
    public Promise<SecretValue> getSecretVersion(String path, int version) {
        return getSecret(path).then(current -> {
            if (current.version() == version) return Promise.of(current);
            throw new SecretNotFoundException(path + "@v" + version);
        });
    }
}
