/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.encryption;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Key management service for encryption key lifecycle.
 *
 * <p>Provides secure key lifecycle management:</p>
 * <ul>
 *   <li>Key generation and storage</li>
 *   <li>Key rotation with versioning</li>
 *   <li>Key revocation and disablement</li>
 *   <li>Key metadata management</li>
 * </ul>
 *
 * <p>Migrated from {@code com.ghatana.yappc.framework.encryption.KeyManagementService}.</p>
 *
 * @doc.type interface
 * @doc.purpose Encryption key lifecycle management
 * @doc.layer platform
 * @doc.pattern Repository, Service
 */
public interface KeyManagementService {

    /**
     * Create a new encryption key.
     *
     * @param request key creation request
     * @return created key metadata
     */
    Promise<KeyMetadata> createKey(CreateKeyRequest request);

    /**
     * Get key metadata by ID.
     *
     * @param keyId key identifier
     * @return key metadata if found
     */
    Promise<Optional<KeyMetadata>> getKey(String keyId);

    /**
     * List all keys.
     *
     * @return list of all key metadata
     */
    Promise<List<KeyMetadata>> listKeys();

    /**
     * List active keys only.
     *
     * @return list of active key metadata
     */
    Promise<List<KeyMetadata>> listActiveKeys();

    /**
     * Rotate key to a new version.
     *
     * @param keyId key identifier
     * @return new key metadata
     */
    Promise<KeyMetadata> rotateKey(String keyId);

    /**
     * Revoke key permanently — prevents further use.
     *
     * @param keyId key identifier
     * @return void promise
     */
    Promise<Void> revokeKey(String keyId);

    /**
     * Enable a previously disabled key.
     *
     * @param keyId key identifier
     * @return void promise
     */
    Promise<Void> enableKey(String keyId);

    /**
     * Disable key temporarily.
     *
     * @param keyId key identifier
     * @return void promise
     */
    Promise<Void> disableKey(String keyId);

    /**
     * Delete key permanently. <b>WARNING: This is irreversible.</b>
     *
     * @param keyId key identifier
     * @return void promise
     */
    Promise<Void> deleteKey(String keyId);

    /**
     * Update key metadata.
     *
     * @param keyId    key identifier
     * @param metadata updated metadata
     * @return updated key metadata
     */
    Promise<KeyMetadata> updateKeyMetadata(String keyId, KeyMetadata metadata);

    // ========================================================================
    // Nested Types
    // ========================================================================

    /**
     * Key metadata.
     *
     * @doc.type class
     * @doc.purpose Encryption key metadata
     * @doc.layer platform
     * @doc.pattern Value Object
     */
    class KeyMetadata {
        private final String keyId;
        private final String algorithm;
        private final KeyStatus status;
        private final Instant createdAt;
        private final Instant expiresAt;
        private final String description;
        private final int version;

        public KeyMetadata(String keyId, String algorithm, KeyStatus status,
                           Instant createdAt, Instant expiresAt, String description, int version) {
            this.keyId = keyId;
            this.algorithm = algorithm;
            this.status = status;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.description = description;
            this.version = version;
        }

        public String getKeyId() { return keyId; }
        public String getAlgorithm() { return algorithm; }
        public KeyStatus getStatus() { return status; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getExpiresAt() { return expiresAt; }
        public String getDescription() { return description; }
        public int getVersion() { return version; }
    }

    /**
     * Key status.
     */
    enum KeyStatus {
        ACTIVE,
        DISABLED,
        REVOKED,
        EXPIRED
    }

    /**
     * Request to create a new encryption key.
     *
     * @doc.type class
     * @doc.purpose Key creation request
     * @doc.layer platform
     * @doc.pattern Value Object
     */
    class CreateKeyRequest {
        private final String algorithm;
        private final String description;
        private final Instant expiresAt;

        public CreateKeyRequest(String algorithm, String description, Instant expiresAt) {
            this.algorithm = algorithm;
            this.description = description;
            this.expiresAt = expiresAt;
        }

        public String getAlgorithm() { return algorithm; }
        public String getDescription() { return description; }
        public Instant getExpiresAt() { return expiresAt; }
    }
}
