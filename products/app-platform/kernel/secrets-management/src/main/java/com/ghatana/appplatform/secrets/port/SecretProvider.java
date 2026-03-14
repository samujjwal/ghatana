/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.port;

import com.ghatana.appplatform.secrets.domain.SecretMetadata;
import com.ghatana.appplatform.secrets.domain.SecretValue;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Port for pluggable secret storage providers (K14-001).
 *
 * <p>Implementations include:
 * <ul>
 *   <li>{@code LocalFileSecretProvider} — AES-256-GCM encrypted local storage (K14-003)</li>
 *   <li>{@code VaultSecretProvider} — HashiCorp Vault KV v2 (K14-002)</li>
 * </ul>
 *
 * <p>All methods operate on secret paths of the form {@code /product/env/name},
 * e.g., {@code /data-cloud/prod/db-password}.
 *
 * @doc.type interface
 * @doc.purpose Pluggable secret storage port (K14-001)
 * @doc.layer core
 * @doc.pattern Repository
 */
public interface SecretProvider {

    /**
     * Retrieves the current version of a secret by path.
     *
     * @param path secret path (e.g., "/db/primary/password")
     * @return current secret value
     * @throws SecretNotFoundException if no secret exists at the path
     */
    Promise<SecretValue> getSecret(String path);

    /**
     * Stores or updates a secret at the given path, incrementing the version.
     *
     * @param path     secret path
     * @param value    raw secret value (will be encrypted before storage)
     * @param metadata lifecycle metadata
     * @return the stored secret value (with assigned version)
     */
    Promise<SecretValue> putSecret(String path, char[] value, SecretMetadata metadata);

    /**
     * Deletes a secret and all its versions.
     *
     * @param path secret path
     * @return completion signal
     */
    Promise<Void> deleteSecret(String path);

    /**
     * Lists all secret paths under a given prefix.
     *
     * @param prefix path prefix (e.g., "/data-cloud/prod/")
     * @return list of matching secret paths
     */
    Promise<List<String>> listSecrets(String prefix);

    /**
     * Rotates a secret by generating a new random value and storing it at the same path.
     *
     * <p>The old version is retained for audit purposes; only the latest version is
     * returned by {@link #getSecret}.
     *
     * @param path secret path
     * @return new secret value
     */
    Promise<SecretValue> rotateSecret(String path);

    /**
     * Retrieves a specific historical version of a secret (K14-013).
     *
     * <p>Version numbers are monotonically increasing integers assigned by the provider.
     * Version 1 is the first ever stored value.
     *
     * @param path    secret path
     * @param version exact version number to retrieve
     * @return the specified version's secret value
     * @throws SecretNotFoundException if the path or version does not exist
     */
    Promise<SecretValue> getSecretVersion(String path, int version);

    /**
     * Exception thrown when {@link #getSecret} finds no secret at the given path.
     */
    class SecretNotFoundException extends RuntimeException {
        public SecretNotFoundException(String path) {
            super("Secret not found: " + path);
        }
    }
}
