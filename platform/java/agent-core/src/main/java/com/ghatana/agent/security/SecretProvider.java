/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 4.9 — Security Hardening: Tenant-scoped secret management.
 * Provides secure retrieval of secrets (API keys, model endpoint credentials)
 * with tenant isolation guarantees.
 */
package com.ghatana.agent.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secret provider interface for tenant-scoped credentials. Agents use this to retrieve:
 * <ul>
 *   <li>Model endpoint API keys</li>
 *   <li>Connector credentials (database, message broker)</li>
 *   <li>Third-party service tokens</li>
 *   <li>Encryption keys for data at rest</li>
 * </ul>
 *
 * <h2>Tenant Isolation</h2>
 * All secrets are scoped to a tenant. A tenant cannot access another tenant's secrets.
 * The composite key is (tenantId, secretName).
 *
 * <h2>Secret Lifecycle</h2>
 * <pre>
 * 1. putSecret(tenantId, name, value)          — store or update
 * 2. getSecret(tenantId, name)                 — retrieve
 * 3. listSecretNames(tenantId)                 — enumerate (names only, never values)
 * 4. deleteSecret(tenantId, name)              — remove
 * 5. rotateSecret(tenantId, name, newValue)    — atomic replace (audit-friendly)
 * </pre>
 *
 * <h2>Implementations</h2>
 * <ul>
 *   <li>{@link InMemorySecretProvider} — testing and development</li>
 *   <li>Future: VaultSecretProvider (HashiCorp Vault), AwsSecretProvider (AWS Secrets Manager)</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Provides secure access to agent credentials and secrets
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface SecretProvider {

    /**
     * Retrieves a secret for the given tenant.
     *
     * @param tenantId   tenant identifier
     * @param secretName name of the secret
     * @return the secret value, or empty if not found
     */
    Optional<String> getSecret(@NotNull String tenantId, @NotNull String secretName);

    /**
     * Stores or updates a secret for the given tenant.
     *
     * @param tenantId   tenant identifier
     * @param secretName name of the secret
     * @param value      the secret value
     */
    void putSecret(@NotNull String tenantId, @NotNull String secretName, @NotNull String value);

    /**
     * Deletes a secret for the given tenant.
     *
     * @param tenantId   tenant identifier
     * @param secretName name of the secret
     * @return true if the secret was deleted, false if it didn't exist
     */
    boolean deleteSecret(@NotNull String tenantId, @NotNull String secretName);

    /**
     * Lists all secret names (NOT values) for the given tenant.
     *
     * @param tenantId tenant identifier
     * @return set of secret names
     */
    Set<String> listSecretNames(@NotNull String tenantId);

    /**
     * Rotates a secret: atomically replaces the value. Records the old version
     * timestamp for audit purposes.
     *
     * @param tenantId   tenant identifier
     * @param secretName name of the secret
     * @param newValue   the new secret value
     * @return metadata about the rotation
     * @throws NoSuchElementException if the secret doesn't exist
     */
    RotationResult rotateSecret(@NotNull String tenantId, @NotNull String secretName,
                                @NotNull String newValue);

    /**
     * Checks whether a secret exists for the given tenant.
     *
     * @param tenantId   tenant identifier
     * @param secretName name of the secret
     * @return true if the secret exists
     */
    default boolean hasSecret(@NotNull String tenantId, @NotNull String secretName) {
        return getSecret(tenantId, secretName).isPresent();
    }

    /**
     * Result of a secret rotation.
     */
    record RotationResult(
            String tenantId,
            String secretName,
            Instant previousVersionAt,
            Instant newVersionAt
    ) {}

    // =========================================================================
    // In-Memory Implementation (for testing)
    // =========================================================================

    /**
     * In-memory implementation of {@link SecretProvider} for testing.
     * Thread-safe via ConcurrentHashMap.
     */
    class InMemorySecretProvider implements SecretProvider {

        private final ConcurrentHashMap<String, SecretEntry> secrets = new ConcurrentHashMap<>();

        private record SecretEntry(String value, Instant createdAt, Instant updatedAt) {}

        private static String compositeKey(String tenantId, String secretName) {
            return tenantId + "::" + secretName;
        }

        @Override
        public Optional<String> getSecret(@NotNull String tenantId, @NotNull String secretName) {
            SecretEntry entry = secrets.get(compositeKey(tenantId, secretName));
            return entry != null ? Optional.of(entry.value()) : Optional.empty();
        }

        @Override
        public void putSecret(@NotNull String tenantId, @NotNull String secretName,
                              @NotNull String value) {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(secretName, "secretName");
            Objects.requireNonNull(value, "value");

            Instant now = Instant.now();
            secrets.put(compositeKey(tenantId, secretName),
                    new SecretEntry(value, now, now));
        }

        @Override
        public boolean deleteSecret(@NotNull String tenantId, @NotNull String secretName) {
            return secrets.remove(compositeKey(tenantId, secretName)) != null;
        }

        @Override
        public Set<String> listSecretNames(@NotNull String tenantId) {
            String prefix = tenantId + "::";
            Set<String> names = new TreeSet<>();
            secrets.keySet().forEach(key -> {
                if (key.startsWith(prefix)) {
                    names.add(key.substring(prefix.length()));
                }
            });
            return Collections.unmodifiableSet(names);
        }

        @Override
        public RotationResult rotateSecret(@NotNull String tenantId, @NotNull String secretName,
                                           @NotNull String newValue) {
            String key = compositeKey(tenantId, secretName);
            SecretEntry existing = secrets.get(key);
            if (existing == null) {
                throw new NoSuchElementException("Secret not found: " + secretName +
                        " for tenant: " + tenantId);
            }

            Instant now = Instant.now();
            Instant previousAt = existing.updatedAt();
            secrets.put(key, new SecretEntry(newValue, existing.createdAt(), now));

            return new RotationResult(tenantId, secretName, previousAt, now);
        }

        /**
         * Returns total secret count across all tenants. For testing.
         */
        public int size() {
            return secrets.size();
        }

        /**
         * Clears all secrets. For testing.
         */
        public void clear() {
            secrets.clear();
        }
    }
}
