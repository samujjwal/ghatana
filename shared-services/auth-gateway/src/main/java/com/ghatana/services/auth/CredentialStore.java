/*
 * Copyright (c) 2025-2026 Ghatana
 */
package com.ghatana.services.auth;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Persistent credential store for user authentication.
 *
 * <p>Implementations must store password hashes using a strong hashing
 * algorithm (BCrypt, Argon2, etc.) — never store plaintext passwords.
 *
 * <p>For production deployments, use {@code JdbcCredentialStore} backed
 * by a real database. For testing only, {@code InMemoryCredentialStore}
 * can be used.
 *
 * @doc.type interface
 * @doc.purpose User credential storage and validation contract
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface CredentialStore {

    /**
     * Looks up a user by username.
     *
     * @param username the login username
     * @return user record if found, empty otherwise
     */
    @NotNull
    Promise<Optional<StoredUser>> findByUsername(@NotNull String username);

    /**
     * Registers a new user with hashed password.
     *
     * @param username  login username (must be unique)
     * @param passwordHash BCrypt-hashed password
     * @param email     email address
     * @param roles     assigned roles
     * @param tenantId  tenant identifier
     * @return the created user record
     */
    @NotNull
    Promise<StoredUser> createUser(
            @NotNull String username,
            @NotNull String passwordHash,
            @NotNull String email,
            @NotNull List<String> roles,
            @NotNull String tenantId);

    /**
     * Immutable user record stored in the credential store.
     *
     * @param username     login name
     * @param passwordHash BCrypt password hash
     * @param email        email address
     * @param roles        assigned role names
     * @param tenantId     owning tenant
     * @param enabled      whether account is active
     *
     * @doc.type record
     * @doc.purpose Stored user credential record
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    record StoredUser(
            @NotNull String username,
            @NotNull String passwordHash,
            @NotNull String email,
            @NotNull List<String> roles,
            @NotNull String tenantId,
            boolean enabled
    ) {}
}
