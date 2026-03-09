/*
 * Copyright (c) 2025-2026 Ghatana
 */
package com.ghatana.services.auth;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory credential store for development and testing only.
 *
 * <p><b>WARNING</b>: This store loses all data on restart.
 * Use {@code JdbcCredentialStore} for production deployments.
 *
 * <p>Passwords are stored as BCrypt hashes — never plaintext.
 *
 * @doc.type class
 * @doc.purpose In-memory credential store for dev/test
 * @doc.layer product
 * @doc.pattern Repository
 */
public class InMemoryCredentialStore implements CredentialStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryCredentialStore.class);
    private final Map<String, StoredUser> users = new ConcurrentHashMap<>();

    @Override
    @NotNull
    public Promise<Optional<StoredUser>> findByUsername(@NotNull String username) {
        Objects.requireNonNull(username, "username must not be null");
        return Promise.of(Optional.ofNullable(users.get(username.toLowerCase(Locale.ROOT))));
    }

    @Override
    @NotNull
    public Promise<StoredUser> createUser(
            @NotNull String username,
            @NotNull String passwordHash,
            @NotNull String email,
            @NotNull List<String> roles,
            @NotNull String tenantId) {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");

        String key = username.toLowerCase(Locale.ROOT);
        if (users.containsKey(key)) {
            return Promise.ofException(
                    new IllegalStateException("User already exists: " + username));
        }

        StoredUser user = new StoredUser(
                username, passwordHash, email,
                List.copyOf(roles), tenantId, true);
        users.put(key, user);
        log.info("Created user: {} (tenant: {})", username, tenantId);
        return Promise.of(user);
    }

    /**
     * Seeds an initial admin user (for dev/test bootstrap).
     *
     * @param username     admin username
     * @param passwordHash BCrypt hash of the admin password
     * @param tenantId     admin tenant
     */
    public void seedAdmin(@NotNull String username, @NotNull String passwordHash, @NotNull String tenantId) {
        String key = username.toLowerCase(Locale.ROOT);
        users.putIfAbsent(key, new StoredUser(
                username, passwordHash, "",
                List.of("ADMIN", "USER"), tenantId, true));
    }
}
