/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.crypto;

import org.jetbrains.annotations.NotNull;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Password hashing utility using BCrypt algorithm.
 *
 * <p>Provides secure password hashing and verification using industry-standard BCrypt.
 * Never stores plain-text passwords; always uses this utility for hashing.
 *
 * @doc.type class
 * @doc.purpose Password hashing and verification utility using BCrypt
 * @doc.layer security
 * @doc.pattern Utility
 */
public class PasswordHasher {

    private static final Logger log = LoggerFactory.getLogger(PasswordHasher.class);

    /** BCrypt cost factor (higher = slower = more secure) */
    private static final int BCRYPT_COST = 12;

    /**
     * Hash password using BCrypt.
     *
     * @param password plain-text password to hash
     * @return hashed password with embedded salt
     * @throws IllegalArgumentException if password is null or empty
     */
    @NotNull
    public String hash(@NotNull String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        String hashed = BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_COST));
        log.debug("Password hashed successfully");
        return hashed;
    }

    /**
     * Verify plain-text password against hash.
     *
     * @param password plain-text password to verify
     * @param hash previously hashed password
     * @return true if password matches hash, false otherwise
     * @throws IllegalArgumentException if password or hash is null/empty
     */
    public boolean verify(@NotNull String password, @NotNull String hash) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("Hash cannot be null or empty");
        }

        try {
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid hash format during verification: {}", e.getMessage());
            return false;
        }
    }
}
