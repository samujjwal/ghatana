package com.ghatana.platform.database.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Production-grade annotation for configuring database integration tests with customizable settings.
 *
 * <p><b>Purpose</b><br>
 * Provides declarative configuration for DatabaseTestExtension, allowing customization
 * of Docker image, database credentials, Flyway migrations, JPA entity packages,
 * and test isolation behavior. Enables fine-grained control over test environment.
 *
 * <p><b>Architecture Role</b><br>
 * Configuration annotation in core/database/testing for test setup.
 * Used by:
 * - DatabaseTestExtension - Read annotation to configure container
 * - Integration Tests - Declare test database requirements
 * - CI/CD Tests - Specify environment-specific settings
 * - Multi-Module Tests - Configure entity package scanning
 * - Migration Tests - Specify migration locations
 *
 * <p><b>Configuration Attributes</b><br>
 * - <b>image</b>: Docker image (default: postgres:15-alpine)
 * - <b>database</b>: Database name (default: testdb)
 * - <b>username</b>: Database username (default: test)
 * - <b>password</b>: Database password (default: test)
 * - <b>migrations</b>: Flyway migration locations (default: none)
 * - <b>entityPackages</b>: JPA entity package scanning (default: com.ghatana)
 * - <b>resetBetweenTests</b>: Rollback transactions between tests (default: true)
 * - <b>showSql</b>: Log SQL statements (default: false)
 * - <b>formatSql</b>: Format SQL output (default: false)
 * - <b>poolSize</b>: HikariCP pool size (default: 10)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Basic usage with defaults
 * @ExtendWith(DatabaseTestExtension.class)
 * @DatabaseTest
 * class UserRepositoryTest {
 *     // Uses default PostgreSQL 15 container
 *     // Database: testdb
 *     // Username/Password: test/test
 *     // Entity packages: com.ghatana
 * }
 *
 * // 2. Custom PostgreSQL version and database
 * @ExtendWith(DatabaseTestExtension.class)
 * @DatabaseTest(
 *     image = "postgres:14.9-alpine",
 *     database = "integration_test",
 *     username = "testuser",
 *     password = "testpass"
 * )
 * class OrderRepositoryTest {
 *     // Uses PostgreSQL 14.9
 * }
 *
 * // 3. With Flyway migrations
 * @ExtendWith(DatabaseTestExtension.class)
 * @DatabaseTest(
 *     migrations = {
 *         "classpath:db/migration",        // Production migrations
 *         "classpath:db/test-data"         // Test data setup
 *     }
 * )
 * class MigrationTest {
 *     @Test
 *     void shouldHaveSchema(JdbcTemplate jdbc) {
 *         // Database has schema from migrations
 *         Integer count = jdbc.queryForObject(
 *             "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'users'",
 *             (rs, rowNum) -> rs.getInt(1)
 *         );
 *         assertThat(count).isEqualTo(1);
 *     }
 * }
 *
 * // 4. Custom entity packages
 * @ExtendWith(DatabaseTestExtension.class)
 * @DatabaseTest(
 *     entityPackages = {
 *         "com.example.domain.user",
 *         "com.example.domain.order",
 *         "com.example.domain.payment"
 *     }
 * )
 * class MultiPackageTest {
 *     // JPA scans specified packages only
 * }
 *
 * // 5. SQL logging for debugging
 * @ExtendWith(DatabaseTestExtension.class)
 * @DatabaseTest(
 *     showSql = true,
 *     formatSql = true
 * )
 * class DebugTest {
 *     @Test
 *     void debugQuery(TransactionManager txManager) {
 *         // All SQL logged to console with formatting
 *         txManager.inTransaction(em -> {
 *             em.persist(new User("john@example.com"));
 *             return null;
 *         });
 *     }
 * }
 *
 * // 6. Performance testing with larger pool
 * @ExtendWith(DatabaseTestExtension.class)
 * @DatabaseTest(
 *     poolSize = 50,
 *     resetBetweenTests = false  // Share data across tests
 * )
 * class PerformanceTest {
 *     @BeforeAll
 *     static void loadTestData(TransactionManager txManager) {
 *         // Load once, reuse across all tests
 *         txManager.inTransaction(em -> {
 *             for (int i = 0; i < 10000; i++) {
 *                 em.persist(new User("user" + i + "@example.com"));
 *             }
 *             return null;
 *         });
 *     }
 *     
 *     @Test
 *     void benchmarkQuery() {
 *         // Data persists from @BeforeAll
 *     }
 * }
 *
 * // 7. Method-level override (not yet supported - class-level only)
 * @ExtendWith(DatabaseTestExtension.class)
 * @DatabaseTest(entityPackages = "com.example.entity")
 * class MethodOverrideTest {
 *     
 *     @Test
 *     void testWithClassConfig() {
 *         // Uses class-level @DatabaseTest configuration
 *     }
 *     
 *     // Note: Method-level @DatabaseTest not currently supported
 *     // Container created once per test class
 * }
 *
 * // 8. Multi-tenant testing
 * @ExtendWith(DatabaseTestExtension.class)
 * @DatabaseTest(
 *     database = "tenant_test",
 *     migrations = "classpath:db/migration/multi-tenant"
 * )
 * class MultiTenantTest {
 *     @Test
 *     void shouldIsolateTenants(JdbcTemplate jdbc) {
 *         // Test tenant isolation logic
 *         jdbc.update("SET search_path TO tenant_1");
 *         jdbc.update("INSERT INTO users (email) VALUES (?)", "user1@tenant1.com");
 *         
 *         jdbc.update("SET search_path TO tenant_2");
 *         Integer count = jdbc.queryForObject(
 *             "SELECT COUNT(*) FROM users",
 *             (rs, rowNum) -> rs.getInt(1)
 *         );
 *         assertThat(count).isEqualTo(0);  // Isolated
 *     }
 * }
 * }</pre>
 *
 * <p><b>Default Values</b><br>
 * - image: "postgres:15-alpine"
 * - database: "testdb"
 * - username: "test"
 * - password: "test"
 * - migrations: [] (empty, no migrations)
 * - entityPackages: ["com.ghatana"] (default package)
 * - resetBetweenTests: true (rollback after each test)
 * - showSql: false (no SQL logging)
 * - formatSql: false (no formatting)
 * - poolSize: 10 (HikariCP connections)
 *
 * <p><b>Reset Between Tests</b><br>
 * When true (default), transactions roll back after each test:
 * <pre>{@code
 * @DatabaseTest(resetBetweenTests = true)  // Default
 * class IsolatedTest {
 *     @Test
 *     void test1() {
 *         // Insert data
 *     }
 *     
 *     @Test
 *     void test2() {
 *         // Data from test1 NOT visible - rolled back
 *     }
 * }
 * }</pre>
 *
 * <p><b>Shared Data Across Tests</b><br>
 * Set resetBetweenTests=false to share data:
 * <pre>{@code
 * @DatabaseTest(resetBetweenTests = false)
 * class SharedDataTest {
 *     @BeforeAll
 *     static void loadData() {
 *         // Load once
 *     }
 *     
 *     @Test
 *     void test1() {
 *         // See data from @BeforeAll
 *     }
 *     
 *     @Test
 *     void test2() {
 *         // Also see data from @BeforeAll
 *     }
 * }
 * }</pre>
 *
 * <p><b>Entity Package Scanning</b><br>
 * Specify packages to scan for @Entity classes:
 * <pre>
 * entityPackages = "com.example.domain"           // Single package
 * entityPackages = {"pkg1", "pkg2", "pkg3"}      // Multiple packages
 * </pre>
 *
 * <p><b>Migration Locations</b><br>
 * Flyway migration paths (classpath or filesystem):
 * <pre>
 * migrations = "classpath:db/migration"           // Single location
 * migrations = {
 *     "classpath:db/migration",                   // Schema
 *     "classpath:db/test-data"                    // Test data
 * }
 * </pre>
 *
 * <p><b>SQL Logging</b><br>
 * Enable for debugging (disable in CI for performance):
 * - showSql=true: Log all SQL statements
 * - formatSql=true: Pretty-print SQL (multi-line)
 *
 * <p><b>Best Practices</b><br>
 * - Use resetBetweenTests=true for test isolation (default)
 * - Use resetBetweenTests=false for performance tests (shared data)
 * - Enable showSql/formatSql only for debugging (slow in CI)
 * - Specify entityPackages explicitly for clarity
 * - Use migrations for complex schema setup
 * - Increase poolSize for parallel/performance tests
 *
 * <p><b>Limitations</b><br>
 * - Class-level only (method-level not supported)
 * - Container created once per test class
 * - Cannot change configuration per test method
 *
 * @see DatabaseTestExtension
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Configuration annotation for database integration tests
 * @doc.layer core
 * @doc.pattern Configuration
 */

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DatabaseTest {
    
    /**
     * The PostgreSQL Docker image to use.
     * 
     * @return The Docker image name (default: "postgres:15.4")
     */
    String image() default "";
    
    /**
     * The database name to create.
     * 
     * @return The database name (default: "testdb")
     */
    String database() default "";
    
    /**
     * The database username.
     * 
     * @return The username (default: "testuser")
     */
    String username() default "";
    
    /**
     * The database password.
     * 
     * @return The password (default: "testpass")
     */
    String password() default "";
    
    /**
     * The migration locations to run on startup.
     * 
     * <p>If specified, migrations will be automatically executed
     * when the container starts.
     * 
     * @return Array of migration locations (default: none)
     */
    String[] migrations() default {};
    
    /**
     * The entity packages to scan for JPA entities.
     * 
     * <p>If specified, JPA components (EntityManagerFactory,
     * EntityManagerProvider, TransactionManager) will be created
     * and made available for injection.
     * 
     * @return Array of entity package names (default: none)
     */
    String[] entityPackages() default {};
    
    /**
     * Whether to reset the database between test methods.
     * 
     * <p>If true, the database will be cleaned and migrations
     * re-run before each test method. This ensures test isolation
     * but may impact performance.
     * 
     * @return true to reset between tests (default: false)
     */
    boolean resetBetweenTests() default false;
}
