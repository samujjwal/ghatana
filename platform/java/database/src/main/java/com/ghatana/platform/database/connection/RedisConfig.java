/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.database.connection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for Redis connections.
 *
 * @doc.type record
 * @doc.purpose Redis connection pool configuration
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record RedisConfig(
        @NotNull String host,
        int port,
        @Nullable String password,
        int maxTotal,
        int maxIdle,
        @NotNull Duration timeout
) {

    public RedisConfig {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(timeout, "timeout");
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be 1-65535, got: " + port);
        }
        if (maxTotal < 1) {
            throw new IllegalArgumentException("maxTotal must be >= 1, got: " + maxTotal);
        }
        if (maxIdle < 0) {
            throw new IllegalArgumentException("maxIdle must be >= 0, got: " + maxIdle);
        }
    }

    /**
     * Creates a default local-development configuration.
     *
     * @return config pointing to localhost:6379 with 8 max connections
     */
    public static RedisConfig localhost() {
        return new RedisConfig("localhost", 6379, null, 8, 4, Duration.ofSeconds(5));
    }

    /**
     * Creates a builder for custom configuration.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link RedisConfig}.
     */
    public static final class Builder {
        private String host = "localhost";
        private int port = 6379;
        private String password;
        private int maxTotal = 8;
        private int maxIdle = 4;
        private Duration timeout = Duration.ofSeconds(5);

        private Builder() {}

        public Builder host(@NotNull String host) { this.host = host; return this; }
        public Builder port(int port) { this.port = port; return this; }
        public Builder password(@Nullable String password) { this.password = password; return this; }
        public Builder maxTotal(int maxTotal) { this.maxTotal = maxTotal; return this; }
        public Builder maxIdle(int maxIdle) { this.maxIdle = maxIdle; return this; }
        public Builder timeout(@NotNull Duration timeout) { this.timeout = timeout; return this; }

        public RedisConfig build() {
            return new RedisConfig(host, port, password, maxTotal, maxIdle, timeout);
        }
    }
}
