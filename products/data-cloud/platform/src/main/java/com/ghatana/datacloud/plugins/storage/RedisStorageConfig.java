package com.ghatana.datacloud.plugins.redis;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for Redis HOT tier storage plugin.
 *
 * <p><b>Purpose</b><br>
 * Provides configuration options for:
 * <ul>
 *   <li>Redis connection settings (host, port, auth)</li>
 *   <li>Connection pooling parameters</li>
 *   <li>Ring buffer sizing for off-heap storage</li>
 *   <li>Flush policies (batch size, time interval)</li>
 *   <li>TTL for HOT tier events</li>
 * </ul>
 *
 * <p><b>Builder Pattern</b><br>
 * <pre>{@code
 * RedisStorageConfig config = RedisStorageConfig.builder()
 *     .host("localhost")
 *     .port(6379)
 *     .password("secret")
 *     .maxPoolSize(20)
 *     .ringBufferSize(65536)
 *     .flushBatchSize(1000)
 *     .flushIntervalMs(100)
 *     .hotTierTtl(Duration.ofHours(1))
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Configuration for Redis M0 HOT tier
 * @doc.layer plugin
 * @doc.pattern Builder, ValueObject
 */
public final class RedisStorageConfig {

    // Connection settings
    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final Duration connectionTimeout;
    private final Duration socketTimeout;

    // Connection pooling
    private final int maxPoolSize;
    private final int minIdleConnections;
    private final Duration maxWaitTime;
    private final boolean testOnBorrow;

    // Ring buffer settings (for off-heap batching)
    private final int ringBufferSize;
    private final int flushBatchSize;
    private final long flushIntervalMs;

    // TTL settings
    private final Duration hotTierTtl;
    private final Duration streamMaxLen;

    // Stream settings
    private final String streamKeyPrefix;
    private final boolean useStreams;

    // Cluster settings
    private final boolean clusterMode;
    private final String[] clusterNodes;

    private RedisStorageConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.password = builder.password;
        this.database = builder.database;
        this.connectionTimeout = builder.connectionTimeout;
        this.socketTimeout = builder.socketTimeout;
        this.maxPoolSize = builder.maxPoolSize;
        this.minIdleConnections = builder.minIdleConnections;
        this.maxWaitTime = builder.maxWaitTime;
        this.testOnBorrow = builder.testOnBorrow;
        this.ringBufferSize = builder.ringBufferSize;
        this.flushBatchSize = builder.flushBatchSize;
        this.flushIntervalMs = builder.flushIntervalMs;
        this.hotTierTtl = builder.hotTierTtl;
        this.streamMaxLen = builder.streamMaxLen;
        this.streamKeyPrefix = builder.streamKeyPrefix;
        this.useStreams = builder.useStreams;
        this.clusterMode = builder.clusterMode;
        this.clusterNodes = builder.clusterNodes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RedisStorageConfig defaults() {
        return builder().build();
    }

    // Getters

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPassword() {
        return password;
    }

    public int getDatabase() {
        return database;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public Duration getSocketTimeout() {
        return socketTimeout;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public int getMinIdleConnections() {
        return minIdleConnections;
    }

    public Duration getMaxWaitTime() {
        return maxWaitTime;
    }

    public boolean isTestOnBorrow() {
        return testOnBorrow;
    }

    public int getRingBufferSize() {
        return ringBufferSize;
    }

    public int getFlushBatchSize() {
        return flushBatchSize;
    }

    public long getFlushIntervalMs() {
        return flushIntervalMs;
    }

    public Duration getHotTierTtl() {
        return hotTierTtl;
    }

    public Duration getStreamMaxLen() {
        return streamMaxLen;
    }

    public String getStreamKeyPrefix() {
        return streamKeyPrefix;
    }

    public boolean isUseStreams() {
        return useStreams;
    }

    public boolean isClusterMode() {
        return clusterMode;
    }

    public String[] getClusterNodes() {
        return clusterNodes != null ? clusterNodes.clone() : new String[0];
    }

    /**
     * Builder for RedisStorageConfig.
     */
    public static final class Builder {
        private String host = "localhost";
        private int port = 6379;
        private String password = null;
        private int database = 0;
        private Duration connectionTimeout = Duration.ofSeconds(2);
        private Duration socketTimeout = Duration.ofSeconds(2);

        // Pool defaults
        private int maxPoolSize = 16;
        private int minIdleConnections = 4;
        private Duration maxWaitTime = Duration.ofSeconds(5);
        private boolean testOnBorrow = true;

        // Ring buffer defaults (power of 2 for Disruptor)
        private int ringBufferSize = 65536; // 64K events in ring buffer
        private int flushBatchSize = 1000;
        private long flushIntervalMs = 100;

        // TTL defaults
        private Duration hotTierTtl = Duration.ofHours(1);
        private Duration streamMaxLen = Duration.ofMinutes(30); // Max stream age

        // Stream defaults
        private String streamKeyPrefix = "ec:stream:";
        private boolean useStreams = true;

        // Cluster defaults
        private boolean clusterMode = false;
        private String[] clusterNodes = null;

        private Builder() {
        }

        public Builder host(String host) {
            this.host = Objects.requireNonNull(host, "host");
            return this;
        }

        public Builder port(int port) {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            this.port = port;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder database(int database) {
            if (database < 0 || database > 15) {
                throw new IllegalArgumentException("Database must be between 0 and 15");
            }
            this.database = database;
            return this;
        }

        public Builder connectionTimeout(Duration timeout) {
            this.connectionTimeout = Objects.requireNonNull(timeout, "connectionTimeout");
            return this;
        }

        public Builder socketTimeout(Duration timeout) {
            this.socketTimeout = Objects.requireNonNull(timeout, "socketTimeout");
            return this;
        }

        public Builder maxPoolSize(int size) {
            if (size < 1) {
                throw new IllegalArgumentException("maxPoolSize must be at least 1");
            }
            this.maxPoolSize = size;
            return this;
        }

        public Builder minIdleConnections(int size) {
            if (size < 0) {
                throw new IllegalArgumentException("minIdleConnections cannot be negative");
            }
            this.minIdleConnections = size;
            return this;
        }

        public Builder maxWaitTime(Duration wait) {
            this.maxWaitTime = Objects.requireNonNull(wait, "maxWaitTime");
            return this;
        }

        public Builder testOnBorrow(boolean test) {
            this.testOnBorrow = test;
            return this;
        }

        public Builder ringBufferSize(int size) {
            // Must be power of 2 for Disruptor
            if (size < 1 || (size & (size - 1)) != 0) {
                throw new IllegalArgumentException("ringBufferSize must be a power of 2");
            }
            this.ringBufferSize = size;
            return this;
        }

        public Builder flushBatchSize(int size) {
            if (size < 1) {
                throw new IllegalArgumentException("flushBatchSize must be at least 1");
            }
            this.flushBatchSize = size;
            return this;
        }

        public Builder flushIntervalMs(long interval) {
            if (interval < 1) {
                throw new IllegalArgumentException("flushIntervalMs must be at least 1");
            }
            this.flushIntervalMs = interval;
            return this;
        }

        public Builder hotTierTtl(Duration ttl) {
            this.hotTierTtl = Objects.requireNonNull(ttl, "hotTierTtl");
            return this;
        }

        public Builder streamMaxLen(Duration len) {
            this.streamMaxLen = Objects.requireNonNull(len, "streamMaxLen");
            return this;
        }

        public Builder streamKeyPrefix(String prefix) {
            this.streamKeyPrefix = Objects.requireNonNull(prefix, "streamKeyPrefix");
            return this;
        }

        public Builder useStreams(boolean use) {
            this.useStreams = use;
            return this;
        }

        public Builder clusterMode(boolean cluster) {
            this.clusterMode = cluster;
            return this;
        }

        public Builder clusterNodes(String... nodes) {
            this.clusterNodes = nodes != null ? nodes.clone() : null;
            if (nodes != null && nodes.length > 0) {
                this.clusterMode = true;
            }
            return this;
        }

        public RedisStorageConfig build() {
            return new RedisStorageConfig(this);
        }
    }

    @Override
    public String toString() {
        return "RedisStorageConfig{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", database=" + database +
                ", maxPoolSize=" + maxPoolSize +
                ", ringBufferSize=" + ringBufferSize +
                ", flushBatchSize=" + flushBatchSize +
                ", hotTierTtl=" + hotTierTtl +
                ", useStreams=" + useStreams +
                ", clusterMode=" + clusterMode +
                '}';
    }
}
