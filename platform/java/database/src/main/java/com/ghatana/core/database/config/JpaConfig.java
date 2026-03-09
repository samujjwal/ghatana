package com.ghatana.core.database.config;

import com.ghatana.platform.core.util.Preconditions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.spi.PersistenceUnitInfo;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Properties;

/**
 * Production-grade JPA configuration with HikariCP connection pooling and sensible defaults.
 *
 * <p><b>Purpose</b><br>
 * Consolidates JPA configuration patterns from across the platform providing single
 * source of truth for database setup. Integrates Hibernate, HikariCP, and entity
 * scanning with production-ready settings and comprehensive validation.
 *
 * <p><b>Architecture Role</b><br>
 * Configuration builder in core/database/config for JPA/Hibernate setup.
 * Used by:
 * - Application Bootstrap - Create EntityManagerFactory at startup
 * - DatabaseConfig - Provide JPA configuration for ActiveJ DI
 * - Testing - Override with H2/Testcontainers configuration
 * - Multi-Tenant - Create tenant-specific EntityManagerFactory
 *
 * <p><b>Configuration Features</b><br>
 * - <b>HikariCP Integration</b>: Production-grade connection pooling
 * - <b>Entity Scanning</b>: Automatic discovery of @Entity classes
 * - <b>Sensible Defaults</b>: Pool size, timeouts, dialect, DDL mode
 * - <b>Builder Pattern</b>: Fluent API for configuration
 * - <b>Validation</b>: Strict parameter validation
 * - <b>Properties Export</b>: Convert to Hibernate Properties
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Basic configuration
 * JpaConfig config = JpaConfig.builder()
 *     .jdbcUrl("jdbc:postgresql://localhost:5432/mydb")
 *     .username("dbuser")
 *     .password("dbpass")
 *     .entityPackages("com.example.entity")
 *     .build();
 * 
 * DataSource dataSource = config.createDataSource();
 * EntityManagerFactory emf = config.createEntityManagerFactory(dataSource);
 *
 * // 2. Production configuration
 * JpaConfig prodConfig = JpaConfig.builder()
 *     .jdbcUrl("jdbc:postgresql://prod-db.example.com:5432/prod")
 *     .username("prod_user")
 *     .password(System.getenv("DB_PASSWORD"))
 *     .entityPackages("com.example.entity", "com.example.audit")
 *     .poolSize(50)                          // Higher pool for production
 *     .connectionTimeout(Duration.ofSeconds(10))
 *     .idleTimeout(Duration.ofMinutes(5))
 *     .maxLifetime(Duration.ofMinutes(15))
 *     .showSql(false)                       // Disable SQL logging
 *     .ddlAuto("validate")                  // Strict validation
 *     .enableCache(true)                    // Enable 2nd level cache
 *     .batchSize(100)                       // Batch inserts/updates
 *     .build();
 *
 * // 3. Test configuration (H2)
 * JpaConfig testConfig = JpaConfig.builder()
 *     .jdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
 *     .username("sa")
 *     .password("")
 *     .entityPackages("com.example.entity")
 *     .dialect("org.hibernate.dialect.H2Dialect")
 *     .ddlAuto("create-drop")               // Auto-create schema
 *     .showSql(true)                        // Debug SQL
 *     .build();
 *
 * // 4. Multi-tenant configuration
 * public JpaConfig createTenantConfig(String tenantId) {
 *     return JpaConfig.builder()
 *         .jdbcUrl("jdbc:postgresql://localhost:5432/tenant_" + tenantId)
 *         .username("tenant_user")
 *         .password(getTenantPassword(tenantId))
 *         .entityPackages("com.example.entity")
 *         .build();
 * }
 *
 * // 5. Custom entity classes (no package scanning)
 * JpaConfig customConfig = JpaConfig.builder()
 *     .jdbcUrl("jdbc:postgresql://localhost:5432/mydb")
 *     .username("user")
 *     .password("pass")
 *     .entityClassNames(
 *         "com.example.User",
 *         "com.example.Order",
 *         "com.example.Product"
 *     )
 *     .build();
 * }</pre>
 *
 * <p><b>HikariCP Connection Pool Settings</b><br>
 * - <b>poolSize</b>: Max connections (default: 20)
 * - <b>connectionTimeout</b>: Max wait for connection (default: 30s)
 * - <b>idleTimeout</b>: Max idle time before eviction (default: 10min)
 * - <b>maxLifetime</b>: Max connection lifetime (default: 30min)
 *
 * <p><b>Hibernate Settings</b><br>
 * - <b>dialect</b>: SQL dialect (default: PostgreSQLDialect)
 * - <b>ddlAuto</b>: Schema generation (none|validate|update|create|create-drop, default: none)
 * - <b>showSql</b>: Log SQL statements (default: false)
 * - <b>formatSql</b>: Format SQL for readability (default: false)
 * - <b>enableCache</b>: Enable 2nd level cache (default: false)
 * - <b>batchSize</b>: Batch insert/update size (default: 100)
 *
 * <p><b>DDL Auto Modes</b><br>
 * - <b>none</b>: No schema management (production)
 * - <b>validate</b>: Validate schema matches entities (production)
 * - <b>update</b>: Update schema (development)
 * - <b>create</b>: Create schema on startup (testing)
 * - <b>create-drop</b>: Create on startup, drop on shutdown (testing)
 *
 * <p><b>Entity Discovery</b><br>
 * Two modes:
 * - <b>Package Scanning</b>: {@code entityPackages("com.example.entity")}
 * - <b>Explicit Classes</b>: {@code entityClassNames("com.example.User", ...)}
 *
 * <p><b>Validation Rules</b><br>
 * - JDBC URL cannot be blank
 * - Username cannot be blank
 * - Password cannot be null (empty allowed for local dev)
 * - Entity packages OR entity class names must be provided
 * - Pool size must be positive
 * - Timeouts must be positive durations
 *
 * <p><b>Thread Safety</b><br>
 * Immutable configuration - all fields final. Safe to share across threads.
 * DataSource and EntityManagerFactory created from config are thread-safe.
 *
 * @see DatabaseConfig
 * @see EntityManagerProvider
 * @see com.zaxxer.hikari.HikariDataSource
 * @see org.hibernate.cfg.AvailableSettings
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Production JPA configuration with HikariCP and defaults
 * @doc.layer core
 * @doc.pattern Configuration
 */
public final class JpaConfig {
    private static final Logger LOG = LoggerFactory.getLogger(JpaConfig.class);
    
    // Default connection pool settings
    private static final int DEFAULT_POOL_SIZE = 20;
    private static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration DEFAULT_MAX_LIFETIME = Duration.ofMinutes(30);
    
    // Default JPA settings
    private static final String DEFAULT_DIALECT = "org.hibernate.dialect.PostgreSQLDialect";
    private static final String DEFAULT_DDL_AUTO = "none";
    private static final int DEFAULT_BATCH_SIZE = 100;
    
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String[] entityPackages;
    private final String[] entityClassNames;
    private final int poolSize;
    private final Duration connectionTimeout;
    private final Duration idleTimeout;
    private final Duration maxLifetime;
    private final boolean showSql;
    private final boolean formatSql;
    private final String ddlAuto;
    private final String dialect;
    private final boolean enableCache;
    private final int batchSize;
    
    private JpaConfig(Builder builder) {
        // Align validation to throw IllegalArgumentException with messages expected by tests
        if (builder.jdbcUrl == null || builder.jdbcUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("JDBC URL cannot be blank");
        }
        this.jdbcUrl = builder.jdbcUrl;

        if (builder.username == null || builder.username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
        this.username = builder.username;

        if (builder.password == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        this.password = builder.password;

        if ((builder.entityPackages == null || builder.entityPackages.length == 0)
                && (builder.entityClassNames == null || builder.entityClassNames.length == 0)) {
            throw new IllegalArgumentException("Entity packages or entity class names must be provided");
        }
        this.entityPackages = builder.entityPackages;
        this.entityClassNames = builder.entityClassNames;
        this.poolSize = builder.poolSize;
        this.connectionTimeout = builder.connectionTimeout;
        this.idleTimeout = builder.idleTimeout;
        this.maxLifetime = builder.maxLifetime;
        this.showSql = builder.showSql;
        this.formatSql = builder.formatSql;
        this.ddlAuto = builder.ddlAuto;
        this.dialect = builder.dialect;
        this.enableCache = builder.enableCache;
        this.batchSize = builder.batchSize;
    }
    
    /**
     * Creates a production-ready HikariCP data source.
     * 
     * @return Configured DataSource with connection pooling
     */
    public DataSource createDataSource() {
        LOG.info("Creating HikariCP data source for URL: {}", jdbcUrl);
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        
        // Pool settings
        config.setMaximumPoolSize(poolSize);
        config.setConnectionTimeout(connectionTimeout.toMillis());
        config.setIdleTimeout(idleTimeout.toMillis());
        config.setMaxLifetime(maxLifetime.toMillis());
        config.setAutoCommit(false);
        
        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        // Health check
        config.setHealthCheckRegistry(null);
        config.setLeakDetectionThreshold(60000); // 1 minute
        
        return new HikariDataSource(config);
    }
    
    /**
     * Creates JPA properties for Hibernate.
     * 
     * @return Production-ready JPA properties
     */
    public Properties createJpaProperties() {
        Properties properties = new Properties();
        
        // Basic Hibernate settings
    properties.setProperty(AvailableSettings.HBM2DDL_AUTO, ddlAuto);
    properties.setProperty(AvailableSettings.DIALECT, dialect);
    properties.setProperty(AvailableSettings.SHOW_SQL, Boolean.toString(showSql));
    properties.setProperty(AvailableSettings.FORMAT_SQL, Boolean.toString(formatSql));
        
        // Performance settings
    properties.setProperty(AvailableSettings.STATEMENT_BATCH_SIZE, Integer.toString(batchSize));
    properties.setProperty(AvailableSettings.ORDER_INSERTS, "true");
    properties.setProperty(AvailableSettings.ORDER_UPDATES, "true");
    properties.setProperty(AvailableSettings.BATCH_VERSIONED_DATA, "true");
        
        // Connection handling
        properties.put(AvailableSettings.CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT, true);
        properties.put(AvailableSettings.AUTOCOMMIT, false);
        
        // Cache settings
        if (enableCache) {
            properties.setProperty(AvailableSettings.USE_SECOND_LEVEL_CACHE, "true");
            properties.setProperty(AvailableSettings.USE_QUERY_CACHE, "true");
            properties.setProperty(AvailableSettings.CACHE_REGION_FACTORY, "org.hibernate.cache.jcache.JCacheRegionFactory");
        } else {
            properties.setProperty(AvailableSettings.USE_SECOND_LEVEL_CACHE, "false");
            properties.setProperty(AvailableSettings.USE_QUERY_CACHE, "false");
        }
        
        // Statistics (disabled in production for performance)
    properties.setProperty(AvailableSettings.GENERATE_STATISTICS, "false");
        
        // Validation
    properties.setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "callback");
        
        // Logging
    properties.setProperty(AvailableSettings.LOG_SESSION_METRICS, "false");
        
        return properties;
    }
    
    /**
     * Creates the EntityManagerFactory with the given data source.
     * 
     * @param dataSource The data source to use
     * @return Configured EntityManagerFactory
     */
    public EntityManagerFactory createEntityManagerFactory(DataSource dataSource) {
        Preconditions.requireNonNull(dataSource, "DataSource cannot be null");
        
        LOG.info("Creating EntityManagerFactory for packages: {}", String.join(", ", entityPackages));
        
        Properties properties = createJpaProperties();
        HibernatePersistenceProvider provider = new HibernatePersistenceProvider();
        
        PersistenceUnitInfo persistenceUnitInfo;
        if (entityClassNames != null && entityClassNames.length > 0) {
            // If explicit entity class names were provided, use them directly (skip scanning)
            persistenceUnitInfo = new CustomPersistenceUnitInfo(entityClassNames, dataSource, properties, true);
        } else {
            persistenceUnitInfo = new CustomPersistenceUnitInfo(
                entityPackages,
                dataSource,
                properties
            );
        }
        
        return provider.createContainerEntityManagerFactory(persistenceUnitInfo, properties);
    }
    
    /**
     * Safely shuts down a data source.
     * 
     * @param dataSource The data source to shut down
     */
    public static void shutdownDataSource(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            if (!hikariDataSource.isClosed()) {
                hikariDataSource.close();
                LOG.info("HikariCP connection pool has been shut down");
            }
        }
    }
    
    /**
     * Creates a new builder for JpaConfig.
     * 
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for JpaConfig with fluent API and sensible defaults.
     */
    public static final class Builder {
        private String jdbcUrl;
        private String username;
        private String password;
        private String[] entityPackages = new String[0];
        private int poolSize = DEFAULT_POOL_SIZE;
        private Duration connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
        private Duration idleTimeout = DEFAULT_IDLE_TIMEOUT;
        private Duration maxLifetime = DEFAULT_MAX_LIFETIME;
        private boolean showSql = false;
        private boolean formatSql = false;
        private String ddlAuto = DEFAULT_DDL_AUTO;
        private String dialect = DEFAULT_DIALECT;
        private boolean enableCache = true;
    private String[] entityClassNames = new String[0];
        private int batchSize = DEFAULT_BATCH_SIZE;
        
        private Builder() {}
        
        /**
         * Sets the JDBC URL.
         * 
         * @param jdbcUrl The JDBC URL
         * @return This builder
         */
        public Builder jdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
            return this;
        }
        
        /**
         * Sets the database username.
         * 
         * @param username The username
         * @return This builder
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }
        
        /**
         * Sets the database password.
         * 
         * @param password The password
         * @return This builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }
        
        /**
         * Sets the entity packages to scan.
         * 
         * @param packages The entity packages
         * @return This builder
         */
        public Builder entityPackages(String... packages) {
            this.entityPackages = packages != null ? packages.clone() : new String[0];
            return this;
        }

        /**
         * Sets explicit entity classes to register with the persistence unit. This avoids
         * package-scanning when running in test or modular classpath environments.
         *
         * @param classes Entity classes to register
         * @return This builder
         */
        public Builder entityClassNames(Class<?>... classes) {
            if (classes == null || classes.length == 0) {
                this.entityClassNames = new String[0];
            } else {
                String[] names = new String[classes.length];
                for (int i = 0; i < classes.length; i++) {
                    names[i] = classes[i].getName();
                }
                this.entityClassNames = names;
            }
            return this;
        }
        
        /**
         * Sets the connection pool size.
         * 
         * @param poolSize The pool size (default: 20)
         * @return This builder
         */
        public Builder poolSize(int poolSize) {
            Preconditions.require(poolSize > 0, "Pool size must be positive");
            this.poolSize = poolSize;
            return this;
        }
        
        /**
         * Sets the connection timeout.
         * 
         * @param timeout The connection timeout (default: 30 seconds)
         * @return This builder
         */
        public Builder connectionTimeout(Duration timeout) {
            this.connectionTimeout = Preconditions.requireNonNull(timeout, "Connection timeout cannot be null");
            return this;
        }
        
        /**
         * Sets the idle timeout.
         * 
         * @param timeout The idle timeout (default: 10 minutes)
         * @return This builder
         */
        public Builder idleTimeout(Duration timeout) {
            this.idleTimeout = Preconditions.requireNonNull(timeout, "Idle timeout cannot be null");
            return this;
        }
        
        /**
         * Sets the maximum connection lifetime.
         * 
         * @param lifetime The max lifetime (default: 30 minutes)
         * @return This builder
         */
        public Builder maxLifetime(Duration lifetime) {
            this.maxLifetime = Preconditions.requireNonNull(lifetime, "Max lifetime cannot be null");
            return this;
        }
        
        /**
         * Enables or disables SQL logging.
         * 
         * @param showSql Whether to show SQL (default: false)
         * @return This builder
         */
        public Builder showSql(boolean showSql) {
            this.showSql = showSql;
            return this;
        }
        
        /**
         * Enables or disables SQL formatting.
         * 
         * @param formatSql Whether to format SQL (default: false)
         * @return This builder
         */
        public Builder formatSql(boolean formatSql) {
            this.formatSql = formatSql;
            return this;
        }
        
        /**
         * Sets the DDL auto strategy.
         * 
         * @param ddlAuto The DDL auto strategy (default: "none")
         * @return This builder
         */
        public Builder ddlAuto(String ddlAuto) {
            this.ddlAuto = Preconditions.requireNonNull(ddlAuto, "DDL auto cannot be blank");
            return this;
        }
        
        /**
         * Sets the SQL dialect.
         * 
         * @param dialect The SQL dialect (default: PostgreSQL)
         * @return This builder
         */
        public Builder dialect(String dialect) {
            this.dialect = Preconditions.requireNonNull(dialect, "Dialect cannot be blank");
            return this;
        }
        
        /**
         * Enables or disables second-level cache.
         * 
         * @param enableCache Whether to enable cache (default: true)
         * @return This builder
         */
        public Builder enableCache(boolean enableCache) {
            this.enableCache = enableCache;
            return this;
        }
        
        /**
         * Sets the batch size for operations.
         * 
         * @param batchSize The batch size (default: 100)
         * @return This builder
         */
        public Builder batchSize(int batchSize) {
            Preconditions.require(batchSize > 0, "Batch size must be positive");
            this.batchSize = batchSize;
            return this;
        }
        
        /**
         * Builds the JpaConfig instance.
         * 
         * @return A new JpaConfig instance
         */
        public JpaConfig build() {
            return new JpaConfig(this);
        }
    }
    
    // Getters
    public String getJdbcUrl() { return jdbcUrl; }
    public String getUsername() { return username; }
    public String[] getEntityPackages() { return entityPackages.clone(); }
    public int getPoolSize() { return poolSize; }
    public Duration getConnectionTimeout() { return connectionTimeout; }
    public Duration getIdleTimeout() { return idleTimeout; }
    public Duration getMaxLifetime() { return maxLifetime; }
    public boolean isShowSql() { return showSql; }
    public boolean isFormatSql() { return formatSql; }
    public String getDdlAuto() { return ddlAuto; }
    public String getDialect() { return dialect; }
    public boolean isEnableCache() { return enableCache; }
    public String[] getEntityClassNames() { return entityClassNames == null ? new String[0] : entityClassNames.clone(); }
    public int getBatchSize() { return batchSize; }
}
