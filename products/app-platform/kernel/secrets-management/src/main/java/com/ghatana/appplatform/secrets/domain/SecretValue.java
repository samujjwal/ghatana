/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable value representing a decrypted secret (K14-001).
 *
 * <p>Values are stored as {@code char[]} (not {@code String}) so they can be
 * zeroed out after use, preventing sensitive data from remaining in the JVM
 * heap or string pool.
 *
 * <p>Usage:
 * <pre>{@code
 * SecretValue secret = provider.getSecret("/db/password").getResult();
 * try {
 *     connectWithPassword(secret.value());
 * } finally {
 *     secret.destroy(); // zero the char[]
 * }
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Immutable, zeroable secret value wrapper (K14-001)
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public final class SecretValue {

    private char[] value;
    private final String path;
    private final int version;
    private final Instant createdAt;
    private final Instant expiresAt;
    private boolean destroyed;

    public SecretValue(String path, int version, char[] value,
                       Instant createdAt, Instant expiresAt) {
        this.path = Objects.requireNonNull(path, "path");
        this.version = version;
        this.value = Arrays.copyOf(value, value.length);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = expiresAt;
        this.destroyed = false;
    }

    /** The secret path (e.g., "/db/primary/password"). */
    public String path() { return path; }

    /** Monotonic version number — increments on every rotation. */
    public int version() { return version; }

    /** Timestamp when this secret version was created. */
    public Instant createdAt() { return createdAt; }

    /** Expiry timestamp, or null if this secret never expires. */
    public Instant expiresAt() { return expiresAt; }

    /** Returns true if this secret version has passed its expiry. */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Returns a copy of the secret value as a char array.
     *
     * <p>Callers MUST zero the returned array after use.
     *
     * @throws IllegalStateException if this secret has already been destroyed
     */
    public char[] value() {
        if (destroyed) throw new IllegalStateException("SecretValue has been destroyed");
        return Arrays.copyOf(value, value.length);
    }

    /**
     * Zeros the internal value array, preventing the secret from being read again.
     *
     * <p>Call this in a {@code finally} block after using the secret.
     */
    public void destroy() {
        Arrays.fill(value, '\0');
        this.destroyed = true;
    }

    @Override
    public String toString() {
        return "SecretValue{path='" + path + "', version=" + version
                + ", destroyed=" + destroyed + "}";
    }
}
