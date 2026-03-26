package com.ghatana.datacloud.infrastructure.config;

import java.util.Objects;

/**
 * Configuration for JPA/Hibernate thread pool.
 *
 * <p><b>Purpose</b><br>
 * Configures the thread pool used for non-blocking JPA operations via Promise.ofBlocking().
 * Allows tuning of pool size and behavior for different deployment environments.
 *
 * <p><b>Architecture Role</b><br>
 * - Thread pool configuration for infrastructure layer  
 * - Used by JpaEntityRepositoryImpl and JpaCollectionRepositoryImpl
 * - Supports virtual threads with configurable pool sizing strategy
 * - Enables performance tuning for different load profiles
 *
 * <p><b>Environment Variables</b><br>
 * <ul>
 *   <li>{@code JPA_THREAD_POOL_TYPE}: "virtual" (default) or "platform"</li>
 *   <li>{@code JPA_THREAD_POOL_PREFIX}: thread name prefix (default: "jpa-worker")</li>
 *   <li>{@code JPA_THREAD_POOL_QUEUE_SIZE}: queue size for bounded pools (default: 1000)</li>
 *   <li>{@code JPA_THREAD_POOL_CORE_SIZE}: core pool size (default: 10, only for platform threads)</li>
 *   <li>{@code JPA_THREAD_POOL_MAX_SIZE}: max pool size (default: 100, only for platform threads)</li>
 *   <li>{@code JPA_THREAD_POOL_KEEP_ALIVE_SECS}: keep-alive time in seconds (default: 60)</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Load from environment
 * JpaThreadPoolConfig config = JpaThreadPoolConfig.fromEnvironment();
 *
 * // Programmatic configuration
 * JpaThreadPoolConfig config = JpaThreadPoolConfig.builder()
 *     .type(ThreadPoolType.VIRTUAL)  // Use virtual threads (Java 21+)
 *     .prefix("jpa-db")
 *     .build();
 *
 * // Apply to repository
 * ExecutorService executor = config.createExecutorService();
 * JpaEntityRepositoryImpl repo = new JpaEntityRepositoryImpl(executor);
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - all fields final. Safe to share across threads.
 *
 * @see JpaEntityRepositoryImpl
 * @doc.type class
 * @doc.purpose Configuration for JPA thread pool sizing and behavior
 * @doc.layer product
 * @doc.pattern Value Object, Configuration
 */
public final class JpaThreadPoolConfig {

    /**
     * Thread pool type: VIRTUAL (Java 21+ virtual threads) or PLATFORM (OS threads).
     */
    public enum ThreadPoolType {
        /** Virtual threads (Java 21+) - lightweight, efficient for I/O-bound workloads */
        VIRTUAL,
        /** Platform threads (OS threads) - traditional threads, better for CPU-bound work */
        PLATFORM
    }

    private final ThreadPoolType type;
    private final String threadNamePrefix;
    private final int queueSize;
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveSeconds;

    private JpaThreadPoolConfig(Builder builder) {
        this.type = builder.type;
        this.threadNamePrefix = builder.threadNamePrefix;
        this.queueSize = builder.queueSize;
        this.corePoolSize = builder.corePoolSize;
        this.maxPoolSize = builder.maxPoolSize;
        this.keepAliveSeconds = builder.keepAliveSeconds;
    }

    /**
     * Gets the thread pool type.
     *
     * @return thread pool type (VIRTUAL or PLATFORM)
     */
    public ThreadPoolType getType() {
        return type;
    }

    /**
     * Gets the thread name prefix.
     *
     * @return prefix for thread names
     */
    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    /**
     * Gets the queue size for bounded pools.
     *
     * @return queue size
     */
    public int getQueueSize() {
        return queueSize;
    }

    /**
     * Gets the core pool size (platform threads only).
     *
     * @return core pool size
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Gets the maximum pool size (platform threads only).
     *
     * @return maximum pool size
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    /**
     * Gets the keep-alive time in seconds (platform threads only).
     *
     * @return keep-alive time in seconds
     */
    public long getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    /**
     * Creates a new builder with default values.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates configuration from environment variables.
     * Falls back to defaults if environment variables are not set.
     *
     * @return configuration from environment
     */
    public static JpaThreadPoolConfig fromEnvironment() {
        Builder builder = builder();

        String type = System.getenv("JPA_THREAD_POOL_TYPE");
        if (type != null && !type.isEmpty()) {
            try {
                builder.type(ThreadPoolType.valueOf(type.toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Invalid type, use default
            }
        }

        String prefix = System.getenv("JPA_THREAD_POOL_PREFIX");
        if (prefix != null && !prefix.isEmpty()) {
            builder.threadNamePrefix(prefix);
        }

        String queueSize = System.getenv("JPA_THREAD_POOL_QUEUE_SIZE");
        if (queueSize != null && !queueSize.isEmpty()) {
            try {
                builder.queueSize(Integer.parseInt(queueSize));
            } catch (NumberFormatException e) {
                // Invalid number, use default
            }
        }

        String coreSize = System.getenv("JPA_THREAD_POOL_CORE_SIZE");
        if (coreSize != null && !coreSize.isEmpty()) {
            try {
                builder.corePoolSize(Integer.parseInt(coreSize));
            } catch (NumberFormatException e) {
                // Invalid number, use default
            }
        }

        String maxSize = System.getenv("JPA_THREAD_POOL_MAX_SIZE");
        if (maxSize != null && !maxSize.isEmpty()) {
            try {
                builder.maxPoolSize(Integer.parseInt(maxSize));
            } catch (NumberFormatException e) {
                // Invalid number, use default
            }
        }

        String keepAlive = System.getenv("JPA_THREAD_POOL_KEEP_ALIVE_SECS");
        if (keepAlive != null && !keepAlive.isEmpty()) {
            try {
                builder.keepAliveSeconds(Long.parseLong(keepAlive));
            } catch (NumberFormatException e) {
                // Invalid number, use default
            }
        }

        return builder.build();
    }

    /**
     * Creates a builder initialized with this config's values.
     *
     * @return builder with current values
     */
    public Builder toBuilder() {
        return new Builder()
                .type(type)
                .threadNamePrefix(threadNamePrefix)
                .queueSize(queueSize)
                .corePoolSize(corePoolSize)
                .maxPoolSize(maxPoolSize)
                .keepAliveSeconds(keepAliveSeconds);
    }

    @Override
    public String toString() {
        return "JpaThreadPoolConfig{" +
                "type=" + type +
                ", threadNamePrefix='" + threadNamePrefix + '\'' +
                ", queueSize=" + queueSize +
                ", corePoolSize=" + corePoolSize +
                ", maxPoolSize=" + maxPoolSize +
                ", keepAliveSeconds=" + keepAliveSeconds +
                '}';
    }

    /**
     * Builder for JpaThreadPoolConfig.
     */
    public static final class Builder {
        private ThreadPoolType type = ThreadPoolType.VIRTUAL;
        private String threadNamePrefix = "jpa-worker";
        private int queueSize = 1000;
        private int corePoolSize = 10;
        private int maxPoolSize = 100;
        private long keepAliveSeconds = 60L;

        private Builder() {
        }

        /**
         * Sets the thread pool type.
         *
         * @param type thread pool type
         * @return this builder
         */
        public Builder type(ThreadPoolType type) {
            this.type = Objects.requireNonNull(type, "Thread pool type must not be null");
            return this;
        }

        /**
         * Sets the thread name prefix.
         *
         * @param prefix thread name prefix
         * @return this builder
         */
        public Builder threadNamePrefix(String prefix) {
            this.threadNamePrefix = Objects.requireNonNull(prefix, "Thread name prefix must not be null");
            return this;
        }

        /**
         * Sets the queue size for bounded pools.
         *
         * @param size queue size
         * @return this builder
         */
        public Builder queueSize(int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("Queue size must be positive");
            }
            this.queueSize = size;
            return this;
        }

        /**
         * Sets the core pool size (platform threads only).
         *
         * @param size core pool size
         * @return this builder
         */
        public Builder corePoolSize(int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("Core pool size must be positive");
            }
            this.corePoolSize = size;
            return this;
        }

        /**
         * Sets the maximum pool size (platform threads only).
         *
         * @param size maximum pool size
         * @return this builder
         */
        public Builder maxPoolSize(int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("Max pool size must be positive");
            }
            this.maxPoolSize = size;
            return this;
        }

        /**
         * Sets the keep-alive time in seconds (platform threads only).
         *
         * @param seconds keep-alive time
         * @return this builder
         */
        public Builder keepAliveSeconds(long seconds) {
            if (seconds < 0) {
                throw new IllegalArgumentException("Keep-alive seconds must be non-negative");
            }
            this.keepAliveSeconds = seconds;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return immutable configuration
         */
        public JpaThreadPoolConfig build() {
            return new JpaThreadPoolConfig(this);
        }
    }
}
