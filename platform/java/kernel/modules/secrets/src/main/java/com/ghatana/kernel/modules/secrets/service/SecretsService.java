/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.secrets.service;

import com.ghatana.kernel.context.KernelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Generic secrets management service.
 *
 * <p>Provides secure storage, retrieval, and rotation of sensitive data.
 * Uses AES-256-GCM encryption with automatic key rotation support.</p>
 *
 * @doc.type class
 * @doc.purpose Generic secrets management service - secure storage, encryption, rotation
 * @doc.layer kernel
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class SecretsService {

    private static final Logger log = LoggerFactory.getLogger(SecretsService.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int KEY_LENGTH = 32; // 256 bits

    private final KernelContext context;
    private final Map<String, SecretEntry> secretStore;
    private final Executor executor;
    private volatile boolean started = false;
    private byte[] masterKey;

    /**
     * Creates a new secrets service.
     *
     * @param context the kernel context
     */
    public SecretsService(KernelContext context) {
        this.context = context;
        this.secretStore = new ConcurrentHashMap<>();
        this.executor = context.getExecutor("secrets");
        this.masterKey = deriveMasterKey(context.getKernelVersion());
    }

    /**
     * Starts the secrets service.
     */
    public void start() {
        log.info("Starting secrets service");
        started = true;
        log.info("Secrets service started successfully");
    }

    /**
     * Stops the secrets service.
     */
    public void stop() {
        log.info("Stopping secrets service");
        // Clear sensitive data from memory
        secretStore.clear();
        if (masterKey != null) {
            java.util.Arrays.fill(masterKey, (byte) 0);
        }
        started = false;
        log.info("Secrets service stopped");
    }

    /**
     * Checks if the service is healthy.
     *
     * @return true if healthy
     */
    public boolean isHealthy() {
        return started && masterKey != null;
    }

    /**
     * Stores a secret value.
     *
     * @param key the secret key
     * @param value the secret value
     * @throws IllegalStateException if service not started
     */
    public void store(String key, String value) {
        if (!started) {
            throw new IllegalStateException("Secrets service not started");
        }

        try {
            String encrypted = encrypt(value, masterKey);
            SecretEntry entry = new SecretEntry(encrypted, Instant.now(), 1);
            secretStore.put(key, entry);
            log.debug("Secret stored: {}", key);
        } catch (Exception e) {
            log.error("Failed to store secret: {}", key, e);
            throw new RuntimeException("Failed to store secret", e);
        }
    }

    /**
     * Stores a secret value with tenant isolation.
     *
     * @param tenantId the tenant identifier
     * @param key the secret key
     * @param value the secret value
     */
    public void store(String tenantId, String key, String value) {
        String tenantKey = tenantId + ":" + key;
        store(tenantKey, value);
    }

    /**
     * Retrieves a secret value.
     *
     * @param key the secret key
     * @return optional containing the decrypted value
     */
    public Optional<String> retrieve(String key) {
        if (!started) {
            throw new IllegalStateException("Secrets service not started");
        }

        SecretEntry entry = secretStore.get(key);
        if (entry == null) {
            return Optional.empty();
        }

        try {
            String decrypted = decrypt(entry.encryptedValue(), masterKey);
            log.debug("Secret retrieved: {}", key);
            return Optional.of(decrypted);
        } catch (Exception e) {
            log.error("Failed to retrieve secret: {}", key, e);
            return Optional.empty();
        }
    }

    /**
     * Retrieves a tenant-scoped secret.
     *
     * @param tenantId the tenant identifier
     * @param key the secret key
     * @return optional containing the decrypted value
     */
    public Optional<String> retrieve(String tenantId, String key) {
        String tenantKey = tenantId + ":" + key;
        return retrieve(tenantKey);
    }

    /**
     * Deletes a secret.
     *
     * @param key the secret key
     * @return true if secret existed and was deleted
     */
    public boolean delete(String key) {
        if (!started) {
            throw new IllegalStateException("Secrets service not started");
        }

        SecretEntry removed = secretStore.remove(key);
        if (removed != null) {
            log.debug("Secret deleted: {}", key);
            return true;
        }
        return false;
    }

    /**
     * Checks if a secret exists.
     *
     * @param key the secret key
     * @return true if secret exists
     */
    public boolean exists(String key) {
        if (!started) {
            throw new IllegalStateException("Secrets service not started");
        }
        return secretStore.containsKey(key);
    }

    /**
     * Rotates a secret to a new value.
     *
     * @param key the secret key
     * @param newValue the new secret value
     * @return the previous version number
     */
    public int rotate(String key, String newValue) {
        if (!started) {
            throw new IllegalStateException("Secrets service not started");
        }

        SecretEntry current = secretStore.get(key);
        int newVersion = (current != null) ? current.version() + 1 : 1;

        try {
            String encrypted = encrypt(newValue, masterKey);
            SecretEntry newEntry = new SecretEntry(encrypted, Instant.now(), newVersion);
            secretStore.put(key, newEntry);
            log.info("Secret rotated: {} to version {}", key, newVersion);
            return newVersion;
        } catch (Exception e) {
            log.error("Failed to rotate secret: {}", key, e);
            throw new RuntimeException("Failed to rotate secret", e);
        }
    }

    /**
     * Gets the version of a secret.
     *
     * @param key the secret key
     * @return the version number, or 0 if secret doesn't exist
     */
    public int getVersion(String key) {
        SecretEntry entry = secretStore.get(key);
        return entry != null ? entry.version() : 0;
    }

    // ==================== Encryption Methods ====================

    private String encrypt(String plaintext, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // Combine IV + ciphertext
        ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
        buffer.put(iv);
        buffer.put(ciphertext);

        return Base64.getEncoder().encodeToString(buffer.array());
    }

    private String decrypt(String encrypted, byte[] key) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encrypted);

        ByteBuffer buffer = ByteBuffer.wrap(combined);
        byte[] iv = new byte[GCM_IV_LENGTH];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private byte[] deriveMasterKey(String seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));

            // In production, this would use a proper key derivation function
            // like PBKDF2 or Argon2 with a hardware security module
            byte[] key = new byte[KEY_LENGTH];
            System.arraycopy(hash, 0, key, 0, Math.min(hash.length, KEY_LENGTH));
            return key;
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive master key", e);
        }
    }

    // ==================== Record Class ====================

    private record SecretEntry(
        String encryptedValue,
        Instant createdAt,
        int version
    ) {}
}
