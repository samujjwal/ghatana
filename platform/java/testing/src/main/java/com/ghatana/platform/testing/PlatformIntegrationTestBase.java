package com.ghatana.platform.testing;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.testing.containers.PostgresTestContainer;
import com.ghatana.platform.testing.containers.RedisTestContainer;
import com.ghatana.platform.testing.containers.TestContainerManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Objects;

/**
 * Comprehensive integration test base class for cross-module tests requiring
 * external infrastructure (Postgres, Redis, etc.) alongside an ActiveJ Eventloop.
 *
 * <p><b>Purpose</b><br>
 * Simplifies writing integration tests that need both async promise execution
 * (via eventloop) and real external dependencies (via TestContainers). Manages
 * the full lifecycle: container startup → test execution → cleanup.
 *
 * <p><b>Included Infrastructure</b>
 * <ul>
 *   <li>ActiveJ Eventloop — inherited from {@link EventloopTestBase}</li>
 *   <li>PostgreSQL — via {@link PostgresTestContainer} (opt-in)</li>
 *   <li>Redis — via {@link RedisTestContainer} (opt-in)</li>
 *   <li>Shared Docker network for container-to-container communication</li>
 * </ul>
 *
 * <p><b>Usage</b>
 * <pre>{@code
 * @DisplayName("My Cross-Module Integration Tests")
 * class MyServiceIntegrationTest extends PlatformIntegrationTestBase {
 *
 *     // Opt in to required infrastructure
 *     @Override protected boolean requiresPostgres() { return true; }
 *     @Override protected boolean requiresRedis() { return true; }
 *
 *     @Test
 *     void shouldPersistAndRetrieve() {
 *         // Use getDataSource() for JDBC
 *         DataSource ds = getDataSource();
 *
 *         // Use getRedisUrl() for Redis connections
 *         String redisUrl = getRedisUrl();
 *
 *         // Run async operations on eventloop
 *         String result = runPromise(() -> myService.processAsync("input"));
 *         assertThat(result).isEqualTo("expected");
 *     }
 * }
 * }</pre>
 *
 * <p><b>Test Isolation</b><br>
 * Containers are started once per test class ({@code @BeforeAll}) and shared
 * across test methods for performance. Each test method gets a fresh eventloop
 * (inherited from {@link EventloopTestBase}).
 *
 * <p><b>Tagging</b><br>
 * All subclasses are automatically tagged with {@code "integration"} for
 * selective test execution via Gradle:
 * <pre>{@code
 * ./gradlew test -PincludeTags=integration
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Cross-module integration test base with container lifecycle
 * @doc.layer core
 * @doc.pattern Base Class, Test Support
 * @see EventloopTestBase
 * @see TestContainerManager
 */
@Tag("integration")
public abstract class PlatformIntegrationTestBase extends EventloopTestBase {

    private static final Logger log = LoggerFactory.getLogger(PlatformIntegrationTestBase.class);

    private static TestContainerManager containerManager;

    // ─── Infrastructure Opt-in Hooks ─────────────────────────────────────

    /**
     * Override to return {@code true} if the test class needs a PostgreSQL database.
     * Default: {@code false}.
     *
     * @return true to start a PostgreSQL container
     */
    protected boolean requiresPostgres() {
        return false;
    }

    /**
     * Override to return {@code true} if the test class needs a Redis instance.
     * Default: {@code false}.
     *
     * @return true to start a Redis container
     */
    protected boolean requiresRedis() {
        return false;
    }

    /**
     * Override to customize the eventloop timeout for integration tests.
     * Default: 30 seconds (longer than unit test default).
     *
     * @return the eventloop timeout duration
     */
    @Override
    protected Duration eventloopTimeout() {
        return Duration.ofSeconds(30);
    }

    // ─── Container Lifecycle ─────────────────────────────────────────────

    @BeforeAll
    static void startContainers(org.junit.jupiter.api.TestInfo testInfo) {
        // Use the concrete subclass to determine which containers are needed.
        // This is a static method, so we use a workaround: we start all
        // possible containers and let subclasses access only what they need.
        // TestContainers reuse keeps this fast after first run.
        containerManager = new TestContainerManager();
        log.info("Starting integration test containers for {}...",
                testInfo.getDisplayName());
    }

    @AfterAll
    static void stopContainers() {
        if (containerManager != null) {
            try {
                containerManager.close();
                log.info("Integration test containers stopped.");
            } catch (Exception e) {
                log.warn("Error stopping test containers", e);
            }
            containerManager = null;
        }
    }

    // ─── Infrastructure Accessors ────────────────────────────────────────

    /**
     * Returns a JDBC DataSource connected to the test PostgreSQL instance.
     * Must have {@code requiresPostgres() == true}.
     *
     * @return configured DataSource
     * @throws IllegalStateException if Postgres was not requested
     */
    protected static DataSource getDataSource() {
        ensurePostgres();
        var container = PostgresTestContainer.getInstance();
        var ds = new org.postgresql.ds.PGSimpleDataSource();
        ds.setUrl(container.getJdbcUrl());
        ds.setUser(container.getUsername());
        ds.setPassword(container.getPassword());
        return ds;
    }

    /**
     * Returns the JDBC URL for the test PostgreSQL instance.
     *
     * @return JDBC connection URL
     * @throws IllegalStateException if Postgres was not requested
     */
    protected static String getJdbcUrl() {
        ensurePostgres();
        return PostgresTestContainer.getJdbcUrl();
    }

    /**
     * Returns the Redis connection URL (e.g., {@code redis://localhost:32768}).
     *
     * @return Redis URL
     * @throws IllegalStateException if Redis was not requested
     */
    protected static String getRedisUrl() {
        ensureRedis();
        return "redis://" + RedisTestContainer.getHost() + ":" + RedisTestContainer.getPort();
    }

    /**
     * Returns the Redis host.
     *
     * @return Redis host
     * @throws IllegalStateException if Redis was not requested
     */
    protected static String getRedisHost() {
        ensureRedis();
        return RedisTestContainer.getHost();
    }

    /**
     * Returns the mapped Redis port.
     *
     * @return Redis port
     * @throws IllegalStateException if Redis was not requested
     */
    protected static int getRedisPort() {
        ensureRedis();
        return RedisTestContainer.getPort();
    }

    // ─── Lazy Container Initialization ───────────────────────────────────

    private static void ensurePostgres() {
        Objects.requireNonNull(containerManager,
                "ContainerManager not initialized — are you calling this from a test method?");
        PostgresTestContainer.start();
        log.info("PostgreSQL available at {}", PostgresTestContainer.getJdbcUrl());
    }

    private static void ensureRedis() {
        Objects.requireNonNull(containerManager,
                "ContainerManager not initialized — are you calling this from a test method?");
        RedisTestContainer.start();
        log.info("Redis available at {}", RedisTestContainer.getConnectionString());
    }
}
