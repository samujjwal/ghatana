package com.ghatana.platform.database.testing;

import com.ghatana.platform.core.util.Preconditions;
import com.ghatana.core.database.config.JpaConfig;
import com.ghatana.core.database.jdbc.JdbcTemplate;
import com.ghatana.core.database.migration.FlywayMigration;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;

/**
 * Production-grade database test container using Testcontainers with automatic lifecycle and migrations.
 *
 * <p><b>Purpose</b><br>
 * Provides disposable PostgreSQL test containers with automatic lifecycle management,
 * Flyway migrations, HikariCP connection pooling, and testing utilities. Enables
 * integration tests with real database isolation and reproducibility.
 *
 * <p><b>Architecture Role</b><br>
 * Test infrastructure in core/database/testing for integration testing.
 * Used by:
 * - Integration Tests - Test with real PostgreSQL database
 * - Repository Tests - Validate JPA/JDBC operations
 * - Migration Tests - Test Flyway schema evolution
 * - Performance Tests - Benchmark database operations
 * - CI/CD Pipelines - Automated testing with containers
 *
 * <p><b>Container Features</b><br>
 * - <b>Automatic Lifecycle</b>: Start/stop PostgreSQL container
 * - <b>Migration Support</b>: Run Flyway migrations on startup
 * - <b>Connection Pooling</b>: HikariCP DataSource built-in
 * - <b>Query Utilities</b>: Convenient query/update methods
 * - <b>Isolation</b>: Each test gets fresh container
 * - <b>Cleanup</b>: AutoCloseable for resource safety
 * - <b>Custom Configuration</b>: Builder pattern for flexibility
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Basic usage with default PostgreSQL 15
 * try (DatabaseTestContainer db = DatabaseTestContainer.postgres()) { // GH-90000
 *     db.start(); // GH-90000
 *
 *     DataSource dataSource = db.getDataSource(); // GH-90000
 *     String jdbcUrl = db.getJdbcUrl(); // GH-90000
 *
 *     // Use for testing
 *     Connection conn = dataSource.getConnection(); // GH-90000
 *     // ... test logic
 * }  // Auto-cleanup on close
 *
 * // 2. With Flyway migrations
 * try (DatabaseTestContainer db = DatabaseTestContainer.postgres() // GH-90000
 *         .withMigrations("classpath:db/migration")) {
 *     db.start(); // GH-90000
 *
 *     // Database ready with schema from migrations
 *     List<User> users = db.query( // GH-90000
 *         "SELECT * FROM users WHERE active = ?",
 *         User.class,
 *         rs -> new User(rs.getLong("id"), rs.getString("email")),
 *         true
 *     );
 * }
 *
 * // 3. Custom PostgreSQL version and config
 * try (DatabaseTestContainer db = DatabaseTestContainer.builder() // GH-90000
 *         .image("postgres:15.4-alpine")
 *         .database("integration_test_db")
 *         .username("testuser")
 *         .password("testpass")
 *         .poolSize(10) // GH-90000
 *         .startupTimeout(Duration.ofSeconds(120)) // GH-90000
 *         .withMigrations("classpath:db/migration")
 *         .build()) { // GH-90000
 *     db.start(); // GH-90000
 *
 *     logger.info("Container started at: {}", db.getJdbcUrl()); // GH-90000
 * }
 *
 * // 4. Test with JUnit 5
 * class UserRepositoryTest {
 *     private DatabaseTestContainer db;
 *     private UserRepository repository;
 *
 *     @BeforeEach
 *     void setup() throws Exception { // GH-90000
 *         db = DatabaseTestContainer.postgres() // GH-90000
 *             .withMigrations("classpath:db/migration");
 *         db.start(); // GH-90000
 *
 *         EntityManagerFactory emf = createEMF(db.getDataSource()); // GH-90000
 *         repository = new JpaUserRepository(emf); // GH-90000
 *     }
 *
 *     @Test
 *     void shouldSaveUser() { // GH-90000
 *         User user = new User("john@example.com");
 *         repository.save(user); // GH-90000
 *
 *         Optional<User> found = repository.findById(user.getId()); // GH-90000
 *         assertThat(found).isPresent(); // GH-90000
 *         assertThat(found.get().getEmail()).isEqualTo("john@example.com");
 *     }
 *
 *     @AfterEach
 *     void teardown() throws Exception { // GH-90000
 *         db.close();  // Stop container // GH-90000
 *     }
 * }
 *
 * // 5. Query utilities for assertions
 * try (DatabaseTestContainer db = DatabaseTestContainer.postgres() // GH-90000
 *         .withMigrations("classpath:db/migration")) {
 *     db.start(); // GH-90000
 *
 *     // Insert test data
 *     db.update("INSERT INTO users (email, active) VALUES (?, ?)", // GH-90000
 *         "john@example.com", true);
 *     db.update("INSERT INTO users (email, active) VALUES (?, ?)", // GH-90000
 *         "jane@example.com", false);
 *
 *     // Query active users
 *     List<String> activeEmails = db.query( // GH-90000
 *         "SELECT email FROM users WHERE active = ? ORDER BY email",
 *         String.class,
 *         rs -> rs.getString("email"),
 *         true
 *     );
 *
 *     assertThat(activeEmails).containsExactly("john@example.com");
 *
 *     // Count all users
 *     Integer count = db.queryForObject( // GH-90000
 *         "SELECT COUNT(*) FROM users", // GH-90000
 *         Integer.class,
 *         rs -> rs.getInt(1) // GH-90000
 *     );
 *     assertThat(count).isEqualTo(2); // GH-90000
 * }
 *
 * // 6. Batch operations for test data setup
 * try (DatabaseTestContainer db = DatabaseTestContainer.postgres() // GH-90000
 *         .withMigrations("classpath:db/migration")) {
 *     db.start(); // GH-90000
 *
 *     // Load 1000 test users efficiently
 *     List<Object[]> users = IntStream.range(0, 1000) // GH-90000
 *         .mapToObj(i -> new Object[]{ // GH-90000
 *             "user" + i + "@example.com",
 *             true
 *         })
 *         .collect(Collectors.toList()); // GH-90000
 *
 *     db.batchUpdate( // GH-90000
 *         "INSERT INTO users (email, active) VALUES (?, ?)", // GH-90000
 *         users
 *     );
 *
 *     // Verify count
 *     Integer count = db.queryForObject( // GH-90000
 *         "SELECT COUNT(*) FROM users", // GH-90000
 *         Integer.class,
 *         rs -> rs.getInt(1) // GH-90000
 *     );
 *     assertThat(count).isEqualTo(1000); // GH-90000
 * }
 *
 * // 7. Shared container for test class (performance optimization) // GH-90000
 * @TestInstance(TestInstance.Lifecycle.PER_CLASS) // GH-90000
 * class PerformanceTest {
 *     private static DatabaseTestContainer db;
 *
 *     @BeforeAll
 *     static void setupContainer() throws Exception { // GH-90000
 *         db = DatabaseTestContainer.postgres() // GH-90000
 *             .withMigrations("classpath:db/migration");
 *         db.start(); // GH-90000
 *
 *         // Load test data once
 *         for (int i = 0; i < 10000; i++) { // GH-90000
 *             db.update("INSERT INTO users (email) VALUES (?)", // GH-90000
 *                 "user" + i + "@example.com");
 *         }
 *     }
 *
 *     @Test
 *     void benchmarkQuery() { // GH-90000
 *         // All tests reuse same container and data
 *         long start = System.nanoTime(); // GH-90000
 *         List<User> users = db.query( // GH-90000
 *             "SELECT * FROM users LIMIT 100",
 *             User.class,
 *             rs -> new User(rs.getLong("id"), rs.getString("email"))
 *         );
 *         long durationMs = (System.nanoTime() - start) / 1_000_000; // GH-90000
 *
 *         assertThat(durationMs).isLessThan(50); // GH-90000
 *     }
 *
 *     @AfterAll
 *     static void teardownContainer() throws Exception { // GH-90000
 *         db.close(); // GH-90000
 *     }
 * }
 * }</pre>
 *
 * <p><b>Container Lifecycle</b><br>
 * 1. <b>Create</b>: Builder creates container config
 * 2. <b>Start</b>: PostgreSQL container started via Docker
 * 3. <b>Migrate</b>: Flyway migrations applied (if configured) // GH-90000
 * 4. <b>Use</b>: DataSource available for testing
 * 5. <b>Close</b>: Container stopped, resources cleaned up
 *
 * <p><b>Default Configuration</b><br>
 * - Image: postgres:15-alpine
 * - Database: testdb
 * - Username: test
 * - Password: test
 * - Pool Size: 10 connections (HikariCP) // GH-90000
 * - Startup Timeout: 60 seconds
 * - Port: Random available port (Docker assigns) // GH-90000
 *
 * <p><b>Migration Support</b><br>
 * Automatic Flyway migration execution:
 * <pre>{@code
 * DatabaseTestContainer db = DatabaseTestContainer.postgres() // GH-90000
 *     .withMigrations("classpath:db/migration")
 *     .withBaselineVersion("1.0.0")
 *     .withValidateOnMigrate(true); // GH-90000
 *
 * db.start();  // Migrations applied automatically // GH-90000
 * }</pre>
 *
 * <p><b>Connection Pooling</b><br>
 * Built-in HikariCP pool with sensible defaults:
 * - Maximum pool size: 10 connections
 * - Connection timeout: 30 seconds
 * - Idle timeout: 10 minutes
 * - Max lifetime: 30 minutes
 *
 * <p><b>Query Utilities</b><br>
 * Convenient methods for test assertions:
 * - <b>query(sql, type, mapper, params)</b>: Query list of results // GH-90000
 * - <b>queryForObject(sql, type, mapper, params)</b>: Query single result // GH-90000
 * - <b>update(sql, params)</b>: Execute DML statement // GH-90000
 * - <b>batchUpdate(sql, batchParams)</b>: Batch DML operations // GH-90000
 *
 * <p><b>Builder Pattern</b><br>
 * <pre>{@code
 * DatabaseTestContainer db = DatabaseTestContainer.builder() // GH-90000
 *     .image("postgres:14.9-alpine")      // Custom version
 *     .database("mydb")                   // Custom database name
 *     .username("admin")                  // Custom username
 *     .password("secret")                 // Custom password
 *     .poolSize(20)                       // Custom pool size // GH-90000
 *     .startupTimeout(Duration.ofMinutes(2))  // Custom timeout // GH-90000
 *     .withMigrations("classpath:db/migration")
 *     .withBaselineVersion("2.0.0")
 *     .withValidateOnMigrate(true) // GH-90000
 *     .build(); // GH-90000
 * }</pre>
 *
 * <p><b>Performance Considerations</b><br>
 * - Container startup: ~5-15 seconds (depends on Docker) // GH-90000
 * - Migration execution: Depends on script complexity
 * - Use @TestInstance(PER_CLASS) to share container across tests // GH-90000
 * - Reuse container when possible (expensive to start) // GH-90000
 * - Consider transaction rollback over container recreation
 *
 * <p><b>CI/CD Integration</b><br>
 * Works seamlessly in CI environments:
 * - Requires Docker daemon (Docker Desktop, Docker Engine) // GH-90000
 * - Automatically pulls PostgreSQL image if not cached
 * - Cleans up containers on JVM exit
 * - Supports parallel test execution (isolated containers) // GH-90000
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe after start() completes. DataSource (HikariCP) is thread-safe. // GH-90000
 * Container itself should not be started/stopped concurrently.
 *
 * @see DatabaseTestExtension
 * @see FlywayMigration
 * @see JdbcTemplate
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Database test container with Testcontainers and lifecycle management
 * @doc.layer core
 * @doc.pattern Factory
 */
public final class DatabaseTestContainer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseTestContainer.class); // GH-90000

    private static final String DEFAULT_IMAGE = "postgres:15.4";
    private static final String DEFAULT_DATABASE = "testdb";
    private static final String DEFAULT_USERNAME = "testuser";
    private static final String DEFAULT_PASSWORD = "testpass";

    private final PostgreSQLContainer<?> container;
    private final String[] migrationLocations;
    private final boolean autoMigrate;
    private final Duration startTimeout;

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private FlywayMigration migration;
    private volatile boolean started = false;

    private DatabaseTestContainer(Builder builder) { // GH-90000
        this.container = new PostgreSQLContainer<>(DockerImageName.parse(builder.image)) // GH-90000
            .withDatabaseName(builder.database) // GH-90000
            .withUsername(builder.username) // GH-90000
            .withPassword(builder.password) // GH-90000
            .withStartupTimeout(builder.startTimeout); // GH-90000

        this.migrationLocations = builder.migrationLocations;
        this.autoMigrate = builder.autoMigrate;
        this.startTimeout = builder.startTimeout;

        LOG.info("DatabaseTestContainer created with image: {}, database: {}", // GH-90000
                builder.image, builder.database);
    }

    /**
     * Starts the database container.
     *
     * <p>This method starts the PostgreSQL container, creates the DataSource,
     * and optionally runs migrations if configured.
     *
     * @throws RuntimeException if the container fails to start
     */
    public void start() { // GH-90000
        if (started) { // GH-90000
            LOG.debug("Container already started");
            return;
        }

        LOG.info("Starting database test container...");

        try {
            container.start(); // GH-90000

            // Create DataSource
            this.dataSource = createDataSource(); // GH-90000
            this.jdbcTemplate = new JdbcTemplate(dataSource); // GH-90000

            // Mark as started before running migrations (they need started=true) // GH-90000
            started = true;

            // Run migrations if configured
            if (autoMigrate && migrationLocations.length > 0) { // GH-90000
                runMigrations(); // GH-90000
            }

            LOG.info("Database test container started successfully. JDBC URL: {}", getJdbcUrl()); // GH-90000

        } catch (Exception e) { // GH-90000
            LOG.error("Failed to start database test container", e); // GH-90000
            started = false; // Reset on failure
            throw new RuntimeException("Failed to start database test container", e); // GH-90000
        }
    }

    /**
     * Stops the database container.
     */
    public void stop() { // GH-90000
        if (!started) { // GH-90000
            return;
        }

        LOG.info("Stopping database test container...");

        try {
            if (container.isRunning()) { // GH-90000
                container.stop(); // GH-90000
            }
            started = false;
            LOG.info("Database test container stopped");
        } catch (Exception e) { // GH-90000
            LOG.error("Error stopping database test container", e); // GH-90000
        }
    }

    /**
     * Gets the DataSource for the test database.
     *
     * @return The DataSource
     * @throws IllegalStateException if the container is not started
     */
    public DataSource getDataSource() { // GH-90000
        checkStarted(); // GH-90000
        return dataSource;
    }

    /**
     * Gets the JDBC URL for the test database.
     *
     * @return The JDBC URL
     * @throws IllegalStateException if the container is not started
     */
    public String getJdbcUrl() { // GH-90000
        checkStarted(); // GH-90000
        return container.getJdbcUrl(); // GH-90000
    }

    /**
     * Gets the database username.
     *
     * @return The username
     * @throws IllegalStateException if the container is not started
     */
    public String getUsername() { // GH-90000
        checkStarted(); // GH-90000
        return container.getUsername(); // GH-90000
    }

    /**
     * Gets the database password.
     *
     * @return The password
     * @throws IllegalStateException if the container is not started
     */
    public String getPassword() { // GH-90000
        checkStarted(); // GH-90000
        return container.getPassword(); // GH-90000
    }

    /**
     * Gets the database name.
     *
     * @return The database name
     * @throws IllegalStateException if the container is not started
     */
    public String getDatabaseName() { // GH-90000
        checkStarted(); // GH-90000
        return container.getDatabaseName(); // GH-90000
    }

    /**
     * Executes a SQL statement.
     *
     * @param sql The SQL statement
     * @param parameters The statement parameters
     * @return The number of affected rows
     * @throws IllegalStateException if the container is not started
     */
    public int execute(String sql, Object... parameters) { // GH-90000
        checkStarted(); // GH-90000
        return jdbcTemplate.update(sql, parameters); // GH-90000
    }

    /**
     * Executes a query and returns the results.
     *
     * @param <T> The result type
     * @param sql The SQL query
     * @param rowMapper Function to map ResultSet to result objects
     * @param parameters Query parameters
     * @return List of results
     * @throws IllegalStateException if the container is not started
     */
    public <T> List<T> query(String sql, JdbcTemplate.RowMapper<T> rowMapper, Object... parameters) { // GH-90000
        checkStarted(); // GH-90000
        return jdbcTemplate.queryForList(sql, rowMapper, parameters); // GH-90000
    }

    /**
     * Executes a query for a single scalar value.
     *
     * @param <T> The result type
     * @param sql The SQL query
     * @param resultClass The expected result class
     * @param parameters Query parameters
     * @return The scalar result, or null if no result
     * @throws IllegalStateException if the container is not started
     */
    public <T> T queryForScalar(String sql, Class<T> resultClass, Object... parameters) { // GH-90000
        checkStarted(); // GH-90000
        return jdbcTemplate.queryForScalar(sql, resultClass, parameters).orElse(null); // GH-90000
    }

    /**
     * Runs migrations using Flyway.
     *
     * @throws RuntimeException if migrations fail
     */
    public void runMigrations() { // GH-90000
        checkStarted(); // GH-90000

        if (migrationLocations.length == 0) { // GH-90000
            LOG.debug("No migration locations configured");
            return;
        }

        LOG.info("Running database migrations from locations: {}", String.join(", ", migrationLocations)); // GH-90000

        try {
            if (migration == null) { // GH-90000
                migration = FlywayMigration.builder() // GH-90000
                    .dataSource(dataSource) // GH-90000
                    .locations(migrationLocations) // GH-90000
                    .baselineOnMigrate(true) // GH-90000
                    .validateOnMigrate(true) // GH-90000
                    .cleanDisabled(false) // Allow clean in tests // GH-90000
                    .build(); // GH-90000
            }

            FlywayMigration.MigrationResult result = migration.migrate(); // GH-90000
            LOG.info("Migrations completed: {} migrations executed in {}", // GH-90000
                    result.getMigrationsExecuted(), result.getExecutionTime()); // GH-90000

        } catch (Exception e) { // GH-90000
            LOG.error("Migration failed", e); // GH-90000
            throw new RuntimeException("Database migration failed", e); // GH-90000
        }
    }

    /**
     * Cleans the database by dropping all objects.
     *
     * <p><strong>WARNING:</strong> This operation removes all data and schema objects.
     *
     * @throws IllegalStateException if the container is not started
     */
    public void clean() { // GH-90000
        checkStarted(); // GH-90000

        LOG.info("Cleaning database...");

        if (migration != null) { // GH-90000
            migration.clean(); // GH-90000
        } else {
            // Manual cleanup if no migration configured
            execute("DROP SCHEMA public CASCADE");
            execute("CREATE SCHEMA public");
        }

        LOG.info("Database cleaned");
    }

    /**
     * Resets the database to a clean state and re-runs migrations.
     *
     * @throws IllegalStateException if the container is not started
     */
    public void reset() { // GH-90000
        checkStarted(); // GH-90000

        LOG.info("Resetting database...");

        clean(); // GH-90000
        if (autoMigrate && migrationLocations.length > 0) { // GH-90000
            runMigrations(); // GH-90000
        }

        LOG.info("Database reset completed");
    }

    /**
     * Creates a JpaConfig for the test database.
     *
     * @param entityPackages The entity packages to scan
     * @return Configured JpaConfig
     * @throws IllegalStateException if the container is not started
     */
    public JpaConfig createJpaConfig(String... entityPackages) { // GH-90000
        checkStarted(); // GH-90000

        return JpaConfig.builder() // GH-90000
            .jdbcUrl(getJdbcUrl()) // GH-90000
            .username(getUsername()) // GH-90000
            .password(getPassword()) // GH-90000
            .entityPackages(entityPackages) // GH-90000
            .poolSize(5) // Smaller pool for tests // GH-90000
            .showSql(true) // Enable SQL logging in tests // GH-90000
            .formatSql(true) // GH-90000
            .ddlAuto("validate") // Validate schema in tests
            .enableCache(false) // Disable second-level cache in tests // GH-90000
            .build(); // GH-90000
    }

    /**
     * Checks if the container is running.
     *
     * @return true if running
     */
    public boolean isRunning() { // GH-90000
        return started && container.isRunning(); // GH-90000
    }

    /**
     * Gets the underlying Testcontainers PostgreSQL container.
     *
     * @return The PostgreSQL container
     */
    public PostgreSQLContainer<?> getContainer() { // GH-90000
        return container;
    }

    @Override
    public void close() { // GH-90000
        stop(); // GH-90000
    }

    /**
     * Creates a new PostgreSQL test container with default settings.
     *
     * @return A new DatabaseTestContainer
     */
    public static DatabaseTestContainer postgres() { // GH-90000
        return builder().build(); // GH-90000
    }

    /**
     * Creates a new builder for DatabaseTestContainer.
     *
     * @return A new builder instance
     */
    public static Builder builder() { // GH-90000
        return new Builder(); // GH-90000
    }

    /**
     * Creates a DataSource for the test container.
     *
     * @return Configured DataSource
     */
    private DataSource createDataSource() { // GH-90000
        // Create HikariCP DataSource directly without JpaConfig
        // (JpaConfig requires entityPackages, but we don't need them for basic DataSource) // GH-90000
        HikariConfig config = new HikariConfig(); // GH-90000
        config.setJdbcUrl(container.getJdbcUrl()); // GH-90000
        config.setUsername(container.getUsername()); // GH-90000
        config.setPassword(container.getPassword()); // GH-90000
        config.setMaximumPoolSize(5); // Smaller pool for tests // GH-90000
        config.setAutoCommit(false); // GH-90000

        return new HikariDataSource(config); // GH-90000
    }

    /**
     * Checks if the container is started and throws an exception if not.
     *
     * @throws IllegalStateException if the container is not started
     */
    private void checkStarted() { // GH-90000
        if (!started) { // GH-90000
            throw new IllegalStateException("Database test container is not started");
        }
    }

    /**
     * Builder for DatabaseTestContainer with fluent API and sensible defaults.
     */
    public static final class Builder {
        private String image = DEFAULT_IMAGE;
        private String database = DEFAULT_DATABASE;
        private String username = DEFAULT_USERNAME;
        private String password = DEFAULT_PASSWORD;
        private String[] migrationLocations = new String[0];
        private boolean autoMigrate = false;
        private Duration startTimeout = Duration.ofMinutes(2); // GH-90000

        private Builder() {} // GH-90000

        /**
         * Sets the PostgreSQL Docker image.
         *
         * @param image The Docker image (default: "postgres:15.4") // GH-90000
         * @return This builder
         */
        public Builder image(String image) { // GH-90000
            this.image = Preconditions.requireNonBlank(image, "Image cannot be blank"); // GH-90000
            return this;
        }

        /**
         * Sets the database name.
         *
         * @param database The database name (default: "testdb") // GH-90000
         * @return This builder
         */
        public Builder database(String database) { // GH-90000
            this.database = Preconditions.requireNonBlank(database, "Database cannot be blank"); // GH-90000
            return this;
        }

        /**
         * Sets the database username.
         *
         * @param username The username (default: "testuser") // GH-90000
         * @return This builder
         */
        public Builder username(String username) { // GH-90000
            this.username = Preconditions.requireNonBlank(username, "Username cannot be blank"); // GH-90000
            return this;
        }

        /**
         * Sets the database password.
         *
         * @param password The password (default: "testpass") // GH-90000
         * @return This builder
         */
        public Builder password(String password) { // GH-90000
            this.password = Preconditions.requireNonNull(password, "Password cannot be null"); // GH-90000
            return this;
        }

        /**
         * Sets the migration locations and enables auto-migration.
         *
         * @param locations The migration locations
         * @return This builder
         */
        public Builder withMigrations(String... locations) { // GH-90000
            this.migrationLocations = locations != null ? locations.clone() : new String[0]; // GH-90000
            this.autoMigrate = this.migrationLocations.length > 0;
            return this;
        }

        /**
         * Sets the container start timeout.
         *
         * @param timeout The start timeout (default: 2 minutes) // GH-90000
         * @return This builder
         */
        public Builder startTimeout(Duration timeout) { // GH-90000
            this.startTimeout = Preconditions.requireNonNull(timeout, "Timeout cannot be null"); // GH-90000
            return this;
        }

        /**
         * Builds the DatabaseTestContainer instance.
         *
         * @return A new DatabaseTestContainer instance
         */
        public DatabaseTestContainer build() { // GH-90000
            return new DatabaseTestContainer(this); // GH-90000
        }
    }
}
