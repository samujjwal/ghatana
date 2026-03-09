package com.ghatana.core.database.health;

import com.ghatana.platform.core.util.Preconditions;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade database health check with validation queries, timeouts, and comprehensive monitoring.
 *
 * <p><b>Purpose</b><br>
 * Provides health checking capabilities for database connections with configurable
 * validation queries, timeout handling, and performance monitoring. Supports both
 * synchronous and asynchronous health checks with detailed diagnostics.
 *
 * <p><b>Architecture Role</b><br>
 * Health monitor in core/database/health for connection validation.
 * Used by:
 * - Health Endpoints - Expose /health/database API status
 * - Monitoring Systems - Track database availability and latency
 * - Alerting - Trigger alerts on unhealthy status
 * - Load Balancers - Route traffic based on health
 * - Service Discovery - Register/deregister based on health
 *
 * <p><b>Health Check Features</b><br>
 * - <b>Validation Query</b>: Configurable SQL query (default: SELECT 1)
 * - <b>Timeout Support</b>: Configurable timeout with async execution
 * - <b>Connection Validation</b>: Optional connection.isValid() check
 * - <b>Response Time Tracking</b>: Measure query execution time
 * - <b>Async Support</b>: Non-blocking health checks via ActiveJ Promise
 * - <b>Diagnostic Details</b>: Database version, connection pool stats
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Basic health check
 * DatabaseHealthCheck healthCheck = DatabaseHealthCheck.builder()
 *     .dataSource(dataSource)
 *     .validationQuery("SELECT 1")
 *     .timeout(Duration.ofSeconds(5))
 *     .build();
 * 
 * HealthStatus status = healthCheck.check();
 * if (status.isHealthy()) {
 *     logger.info("Database healthy: {}", status.getMessage());
 * } else {
 *     logger.error("Database unhealthy: {}", status.getMessage(), status.getException());
 * }
 *
 * // 2. Async health check
 * Promise<HealthStatus> promise = healthCheck.checkAsync();
 * promise.whenResult(status -> {
 *     if (!status.isHealthy()) {
 *         alerting.sendAlert("Database unhealthy", status.getMessage());
 *     }
 * });
 *
 * // 3. Custom validation query
 * DatabaseHealthCheck postgresCheck = DatabaseHealthCheck.builder()
 *     .dataSource(dataSource)
 *     .validationQuery("SELECT version()")
 *     .timeout(Duration.ofSeconds(3))
 *     .validateConnection(true)  // Use Connection.isValid()
 *     .build();
 *
 * // 4. Health endpoint integration
 * @GetMapping("/health/database")
 * public ResponseEntity<HealthResponse> getDatabaseHealth() {
 *     HealthStatus status = healthCheck.check();
 *     
 *     HttpStatus httpStatus = switch (status.getStatus()) {
 *         case HEALTHY -> HttpStatus.OK;
 *         case UNHEALTHY -> HttpStatus.SERVICE_UNAVAILABLE;
 *         case UNKNOWN -> HttpStatus.INTERNAL_SERVER_ERROR;
 *     };
 *     
 *     return ResponseEntity.status(httpStatus)
 *         .body(new HealthResponse(
 *             status.getStatus().name(),
 *             status.getMessage(),
 *             status.getResponseTime().toMillis(),
 *             status.getTimestamp(),
 *             status.getDetails()
 *         ));
 * }
 *
 * // 5. Monitoring loop
 * ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
 * scheduler.scheduleAtFixedRate(() -> {
 *     HealthStatus status = healthCheck.check();
 *     metrics.recordDatabaseHealth(
 *         status.isHealthy(),
 *         status.getResponseTime().toMillis()
 *     );
 * }, 0, 30, TimeUnit.SECONDS);
 * }</pre>
 *
 * <p><b>Validation Query Patterns</b><br>
 * - <b>PostgreSQL</b>: {@code SELECT 1} or {@code SELECT version()}
 * - <b>MySQL</b>: {@code SELECT 1} or {@code SELECT @@version}
 * - <b>Oracle</b>: {@code SELECT 1 FROM DUAL}
 * - <b>SQL Server</b>: {@code SELECT 1}
 * - <b>H2</b>: {@code SELECT 1}
 *
 * <p><b>Connection Validation Modes</b><br>
 * - <b>Query-Based</b>: Execute validation query (default, portable)
 * - <b>Connection.isValid()</b>: JDBC 4.0+ method (faster, driver-specific)
 * - <b>Hybrid</b>: Use both for comprehensive validation
 *
 * <p><b>Timeout Handling</b><br>
 * - Default timeout: 5 seconds
 * - Async execution prevents blocking caller
 * - Timeout triggers unhealthy status
 * - Response time includes timeout duration
 *
 * <p><b>Health Status Interpretation</b><br>
 * - <b>HEALTHY</b>: Query succeeded within timeout
 * - <b>UNHEALTHY</b>: Connection failed, query failed, or timeout
 * - <b>UNKNOWN</b>: Partial failure or indeterminate state
 *
 * <p><b>Performance Metrics</b><br>
 * Track response time for SLO monitoring:
 * - Baseline: 10-50ms for local database
 * - Warning: >100ms sustained
 * - Critical: >1000ms or timeout
 *
 * <p><b>Thread Safety</b><br>
 * Health check is thread-safe. DataSource must be thread-safe (HikariCP is).
 * Can be called concurrently from multiple threads.
 *
 * @see HealthStatus
 * @see HealthDetails
 * @see javax.sql.DataSource
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Database health check with validation queries and timeouts
 * @doc.layer core
 * @doc.pattern Strategy
 */
public final class DatabaseHealthCheck {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseHealthCheck.class);
    
    private static final String DEFAULT_VALIDATION_QUERY = "SELECT 1";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    
    private static final ExecutorService BLOCKING_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    
    private final DataSource dataSource;
    private final String validationQuery;
    private final Duration timeout;
    private final boolean validateConnection;
    
    private DatabaseHealthCheck(Builder builder) {
        this.dataSource = Preconditions.requireNonNull(builder.dataSource, "DataSource cannot be null");
        this.validationQuery = builder.validationQuery;
        this.timeout = builder.timeout;
        this.validateConnection = builder.validateConnection;
        
        LOG.info("DatabaseHealthCheck initialized with validation query: '{}', timeout: {}ms", 
                validationQuery, timeout.toMillis());
    }
    
    /**
     * Performs a synchronous health check.
     * 
     * @return Health status result
     */
    public HealthStatus check() {
        return checkWithTimeout(timeout);
    }
    
    /**
     * Performs a synchronous health check with custom timeout.
     * 
     * @param customTimeout The timeout for the health check
     * @return Health status result
     */
    public HealthStatus checkWithTimeout(Duration customTimeout) {
        Preconditions.requireNonNull(customTimeout, "Timeout cannot be null");
        
        LOG.debug("Performing database health check with timeout: {}ms", customTimeout.toMillis());
        
        Instant startTime = Instant.now();
        
        try {
            HealthStatus status = BLOCKING_EXECUTOR.submit(this::performCheck)
                .get(customTimeout.toMillis(), TimeUnit.MILLISECONDS);
            
            Duration responseTime = Duration.between(startTime, Instant.now());
            LOG.debug("Health check completed in {}ms with status: {}", 
                    responseTime.toMillis(), status.getStatus());
            
            return status;
            
        } catch (Exception e) {
            Duration responseTime = Duration.between(startTime, Instant.now());
            LOG.warn("Health check failed after {}ms: {}", responseTime.toMillis(), e.getMessage());
            
            return HealthStatus.unhealthy(
                "Health check failed: " + e.getMessage(),
                responseTime,
                e
            );
        }
    }
    
    /**
     * Performs an asynchronous health check.
     * 
     * @return Promise containing the health status result
     */
    public Promise<HealthStatus> checkAsync() {
        LOG.debug("Performing asynchronous database health check");
        
        return Promise.ofBlocking(BLOCKING_EXECUTOR, this::performCheck)
            .map(status -> status)
            .mapException(throwable -> {
                LOG.warn("Async health check failed: {}", throwable.getMessage());
                return throwable;
            });
    }
    
    /**
     * Performs the actual health check logic.
     * 
     * @return Health status result
     */
    private HealthStatus performCheck() {
        Instant startTime = Instant.now();
        
        try (Connection connection = dataSource.getConnection()) {
            
            // Check if connection is valid
            if (validateConnection && !connection.isValid(1)) {
                Duration responseTime = Duration.between(startTime, Instant.now());
                return HealthStatus.unhealthy(
                    "Database connection is not valid",
                    responseTime,
                    null
                );
            }
            
            // Execute validation query
            try (PreparedStatement statement = connection.prepareStatement(validationQuery);
                 ResultSet resultSet = statement.executeQuery()) {
                
                if (resultSet.next()) {
                    Duration responseTime = Duration.between(startTime, Instant.now());
                    
                    return HealthStatus.healthy(
                        "Database connection successful",
                        responseTime,
                        createHealthDetails(connection)
                    );
                } else {
                    Duration responseTime = Duration.between(startTime, Instant.now());
                    return HealthStatus.unhealthy(
                        "Validation query returned no results",
                        responseTime,
                        null
                    );
                }
            }
            
        } catch (SQLException e) {
            Duration responseTime = Duration.between(startTime, Instant.now());
            LOG.debug("Database health check failed with SQL exception", e);
            
            return HealthStatus.unhealthy(
                "Database connection failed: " + e.getMessage(),
                responseTime,
                e
            );
        }
    }
    
    /**
     * Creates health details from the database connection.
     * 
     * @param connection The database connection
     * @return Health details
     */
    private HealthDetails createHealthDetails(Connection connection) {
        try {
            return HealthDetails.builder()
                .detail("database.url", connection.getMetaData().getURL())
                .detail("database.product", connection.getMetaData().getDatabaseProductName())
                .detail("database.version", connection.getMetaData().getDatabaseProductVersion())
                .detail("driver.name", connection.getMetaData().getDriverName())
                .detail("driver.version", connection.getMetaData().getDriverVersion())
                .detail("validation.query", validationQuery)
                .detail("connection.autoCommit", connection.getAutoCommit())
                .detail("connection.readOnly", connection.isReadOnly())
                .build();
        } catch (SQLException e) {
            LOG.debug("Failed to retrieve database metadata", e);
            return HealthDetails.builder()
                .detail("validation.query", validationQuery)
                .detail("metadata.error", e.getMessage())
                .build();
        }
    }
    
    /**
     * Gets the data source used by this health check.
     * 
     * @return The data source
     */
    public DataSource getDataSource() {
        return dataSource;
    }
    
    /**
     * Gets the validation query used by this health check.
     * 
     * @return The validation query
     */
    public String getValidationQuery() {
        return validationQuery;
    }
    
    /**
     * Gets the timeout used by this health check.
     * 
     * @return The timeout duration
     */
    public Duration getTimeout() {
        return timeout;
    }
    
    /**
     * Creates a new builder for DatabaseHealthCheck.
     * 
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for DatabaseHealthCheck with fluent API and sensible defaults.
     */
    public static final class Builder {
        private DataSource dataSource;
        private String validationQuery = DEFAULT_VALIDATION_QUERY;
        private Duration timeout = DEFAULT_TIMEOUT;
        private boolean validateConnection = true;
        
        private Builder() {}
        
        /**
         * Sets the data source.
         * 
         * @param dataSource The data source
         * @return This builder
         */
        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }
        
        /**
         * Sets the validation query.
         * 
         * @param validationQuery The validation query (default: "SELECT 1")
         * @return This builder
         */
        public Builder validationQuery(String validationQuery) {
            this.validationQuery = Preconditions.requireNonBlank(
                validationQuery, "Validation query cannot be blank");
            return this;
        }
        
        /**
         * Sets the timeout for health checks.
         * 
         * @param timeout The timeout duration (default: 5 seconds)
         * @return This builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = Preconditions.requireNonNull(timeout, "Timeout cannot be null");
            return this;
        }
        
        /**
         * Sets whether to validate the connection using Connection.isValid().
         * 
         * @param validateConnection Whether to validate connection (default: true)
         * @return This builder
         */
        public Builder validateConnection(boolean validateConnection) {
            this.validateConnection = validateConnection;
            return this;
        }
        
        /**
         * Builds the DatabaseHealthCheck instance.
         * 
         * @return A new DatabaseHealthCheck instance
         */
        public DatabaseHealthCheck build() {
            return new DatabaseHealthCheck(this);
        }
    }
}
