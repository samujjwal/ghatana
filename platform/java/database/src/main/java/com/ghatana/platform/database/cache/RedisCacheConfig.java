package com.ghatana.platform.database.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * Redis cache configuration value object.
 * 
 * Immutable configuration for Redis-backed caches consolidating connection
 * parameters, TTL settings, and namespace configuration.
 *
 * @doc.type class
 * @doc.purpose Immutable configuration value object for Redis-backed caches
 * @doc.layer platform
 * @doc.pattern Config
 */
public final class RedisCacheConfig {
    
    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final Duration timeout;
    private final long ttlSeconds;
    private final String keyPrefix;
    
    private RedisCacheConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.password = builder.password;
        this.database = builder.database;
        this.timeout = builder.timeout;
        this.ttlSeconds = builder.ttlSeconds;
        this.keyPrefix = builder.keyPrefix;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public Builder toBuilder() {
        return new Builder()
                .host(host)
                .port(port)
                .password(password)
                .database(database)
                .timeout(timeout)
                .ttlSeconds(ttlSeconds)
                .keyPrefix(keyPrefix);
    }
    
    @NotNull
    public String getHost() {
        return host;
    }
    
    public int getPort() {
        return port;
    }
    
    @Nullable
    public String getPassword() {
        return password;
    }
    
    public int getDatabase() {
        return database;
    }
    
    @NotNull
    public Duration getTimeout() {
        return timeout;
    }
    
    public long getTtlSeconds() {
        return ttlSeconds;
    }
    
    @NotNull
    public String getKeyPrefix() {
        return keyPrefix;
    }
    
    public static final class Builder {
        private String host = "localhost";
        private int port = 6379;
        private String password = null;
        private int database = 0;
        private Duration timeout = Duration.ofSeconds(5);
        private long ttlSeconds = 86400; // 24 hours
        private String keyPrefix = "";
        
        private Builder() {}
        
        public Builder host(@NotNull String host) {
            this.host = Objects.requireNonNull(host, "host must not be null");
            return this;
        }
        
        public Builder port(int port) {
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("port must be between 1 and 65535");
            }
            this.port = port;
            return this;
        }
        
        public Builder password(@Nullable String password) {
            this.password = password;
            return this;
        }
        
        public Builder database(int database) {
            if (database < 0 || database > 15) {
                throw new IllegalArgumentException("database must be between 0 and 15");
            }
            this.database = database;
            return this;
        }
        
        public Builder timeout(@NotNull Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
            return this;
        }
        
        public Builder ttlSeconds(long ttlSeconds) {
            if (ttlSeconds < 0) {
                throw new IllegalArgumentException("ttlSeconds must be non-negative");
            }
            this.ttlSeconds = ttlSeconds;
            return this;
        }
        
        public Builder keyPrefix(@NotNull String keyPrefix) {
            this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix must not be null");
            return this;
        }
        
        public RedisCacheConfig build() {
            return new RedisCacheConfig(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RedisCacheConfig that = (RedisCacheConfig) o;
        return port == that.port &&
                database == that.database &&
                ttlSeconds == that.ttlSeconds &&
                host.equals(that.host) &&
                Objects.equals(password, that.password) &&
                timeout.equals(that.timeout) &&
                keyPrefix.equals(that.keyPrefix);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(host, port, password, database, timeout, ttlSeconds, keyPrefix);
    }
    
    @Override
    public String toString() {
        return "RedisCacheConfig{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", database=" + database +
                ", timeout=" + timeout +
                ", ttlSeconds=" + ttlSeconds +
                ", keyPrefix='" + keyPrefix + '\'' +
                '}';
    }
}
