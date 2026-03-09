package com.ghatana.platform.database.connection;

import com.ghatana.platform.core.util.Preconditions;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for database connections.
 * 
 * Immutable configuration object that can be built using the fluent builder.
 *
 * @doc.type record
 * @doc.purpose Configuration for database connection pooling
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record DataSourceConfig(
        @NotNull String jdbcUrl,
        @NotNull String username,
        @NotNull String password,
        @NotNull String driverClassName,
        int minimumIdle,
        int maximumPoolSize,
        @NotNull Duration connectionTimeout,
        @NotNull Duration idleTimeout,
        @NotNull Duration maxLifetime,
        @NotNull String poolName
) {
    
    public DataSourceConfig {
        Preconditions.requireNonBlank(jdbcUrl, "jdbcUrl");
        Preconditions.requireNonNull(username, "username");
        Preconditions.requireNonNull(password, "password");
        Preconditions.requireNonBlank(driverClassName, "driverClassName");
        Preconditions.requirePositive(minimumIdle, "minimumIdle");
        Preconditions.requirePositive(maximumPoolSize, "maximumPoolSize");
        Preconditions.require(minimumIdle <= maximumPoolSize, 
                "minimumIdle must be <= maximumPoolSize");
        Preconditions.requireNonNull(connectionTimeout, "connectionTimeout");
        Preconditions.requireNonNull(idleTimeout, "idleTimeout");
        Preconditions.requireNonNull(maxLifetime, "maxLifetime");
        Preconditions.requireNonBlank(poolName, "poolName");
    }
    
    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create a builder pre-populated with this config's values.
     */
    public Builder toBuilder() {
        return new Builder()
                .jdbcUrl(jdbcUrl)
                .username(username)
                .password(password)
                .driverClassName(driverClassName)
                .minimumIdle(minimumIdle)
                .maximumPoolSize(maximumPoolSize)
                .connectionTimeout(connectionTimeout)
                .idleTimeout(idleTimeout)
                .maxLifetime(maxLifetime)
                .poolName(poolName);
    }
    
    /**
     * Builder for DataSourceConfig.
     */
    public static final class Builder {
        private String jdbcUrl;
        private String username = "";
        private String password = "";
        private String driverClassName;
        private int minimumIdle = 5;
        private int maximumPoolSize = 10;
        private Duration connectionTimeout = Duration.ofSeconds(30);
        private Duration idleTimeout = Duration.ofMinutes(10);
        private Duration maxLifetime = Duration.ofMinutes(30);
        private String poolName = "ghatana-pool";
        
        private Builder() {}
        
        public Builder jdbcUrl(@NotNull String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
            return this;
        }
        
        public Builder username(@NotNull String username) {
            this.username = username;
            return this;
        }
        
        public Builder password(@NotNull String password) {
            this.password = password;
            return this;
        }
        
        public Builder driverClassName(@NotNull String driverClassName) {
            this.driverClassName = driverClassName;
            return this;
        }
        
        public Builder minimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
            return this;
        }
        
        public Builder maximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }
        
        public Builder connectionTimeout(@NotNull Duration connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }
        
        public Builder idleTimeout(@NotNull Duration idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }
        
        public Builder maxLifetime(@NotNull Duration maxLifetime) {
            this.maxLifetime = maxLifetime;
            return this;
        }
        
        public Builder poolName(@NotNull String poolName) {
            this.poolName = poolName;
            return this;
        }
        
        /**
         * Configure for PostgreSQL.
         */
        public Builder postgresql(@NotNull String host, int port, @NotNull String database) {
            this.jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
            this.driverClassName = "org.postgresql.Driver";
            return this;
        }
        
        /**
         * Configure for H2 in-memory database.
         */
        public Builder h2InMemory(@NotNull String databaseName) {
            this.jdbcUrl = String.format("jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1", databaseName);
            this.driverClassName = "org.h2.Driver";
            return this;
        }
        
        /**
         * Configure for H2 file-based database.
         */
        public Builder h2File(@NotNull String filePath) {
            this.jdbcUrl = String.format("jdbc:h2:file:%s", filePath);
            this.driverClassName = "org.h2.Driver";
            return this;
        }
        
        public DataSourceConfig build() {
            return new DataSourceConfig(
                    jdbcUrl,
                    username,
                    password,
                    driverClassName,
                    minimumIdle,
                    maximumPoolSize,
                    connectionTimeout,
                    idleTimeout,
                    maxLifetime,
                    poolName
            );
        }
    }
}
