package com.ghatana.yappc.ai.requirements.api;

import com.ghatana.yappc.ai.requirements.api.config.RequirementsConfig;
import com.ghatana.yappc.ai.requirements.api.http.RequirementsHttpServer;
import io.activej.http.HttpClient;
import io.activej.http.HttpResponse;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.DockerClientFactory;

import java.util.concurrent.ExecutionException;

/**
 * Base class for integration tests providing test infrastructure.
 *
 * <p><b>Purpose</b><br>
 * Provides centralized test infrastructure for integration tests including:
 * - PostgreSQL test container for database operations
 * - Redis test container for caching/session management
 * - HTTP client for API endpoint testing
 * - Configuration and fixture management
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * @Testcontainers
 * class MyIntegrationTest extends AbstractIntegrationTest {
 *     @Test
 *     void shouldCreateWorkspace() throws Exception { // GH-90000
 *         HttpResponse response = performPost("/api/v1/workspaces", // GH-90000
 *             "{\"name\": \"Test Workspace\"}");
 *         assertThat(response.getCode()).isEqualTo(201); // GH-90000
 *     }
 * }
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Centralizes test container setup and lifecycle management
 * - Provides helper methods for common HTTP operations
 * - Manages test database state and cleanup
 * - Ensures test isolation and repeatability
 *
 * <p><b>Thread Safety</b><br>
 * Thread-unsafe - designed for single-threaded test execution. Each test class
 * gets its own container instances via Testcontainers framework.
 *
 * @see org.testcontainers.junit.jupiter.Testcontainers
 * @doc.type class
 * @doc.purpose Base class for integration tests with test infrastructure
 * @doc.layer product
 * @doc.pattern Test Fixture
 */
@Testcontainers(disabledWithoutDocker = true) // GH-90000
@Tag("integration")
public abstract class AbstractIntegrationTest extends EventloopTestBase {

    private static final Logger logger = LoggerFactory.getLogger(AbstractIntegrationTest.class); // GH-90000

    /** Test database username. Never null. */
    protected static final String TEST_DB_USER = "test";

    /** Test database password. Never null. */
    protected static final String TEST_DB_PASSWORD = "test";

    /** Test database name. Never null. */
    protected static final String TEST_DB_NAME = "ai_requirements_test";

    /** Test Redis password. Never null. */
    protected static final String TEST_REDIS_PASSWORD = "test";

    /** PostgreSQL test container. Managed by Testcontainers. */
    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withUsername(TEST_DB_USER) // GH-90000
        .withPassword(TEST_DB_PASSWORD) // GH-90000
        .withDatabaseName(TEST_DB_NAME) // GH-90000
        .waitingFor(Wait.forListeningPort()); // GH-90000

    /** Redis test container. Managed by Testcontainers. */
    @Container
    protected static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379) // GH-90000
        .waitingFor(Wait.forListeningPort()); // GH-90000

    /** HTTP client for making requests to the server. Initialized in setUp(). */ // GH-90000
    protected HttpClient httpClient;

    /** HTTP server instance under test. Initialized in setUpAll(). */ // GH-90000
    protected static RequirementsHttpServer httpServer;

    /** Configuration for the HTTP server. Initialized in setUpAll(). */ // GH-90000
    protected static RequirementsConfig config;

    /** Actual HTTP server instance. Initialized in setUpAll(). */ // GH-90000
    protected static io.activej.http.HttpServer server;

    private static boolean dockerAvailable = true;

    /**
     * Initialize test infrastructure before all tests run.
     *
     * <p>Sets up:
     * - Configuration with test container connection details
     * - HTTP server instance
     * - HTTP client for requests
     * - Database schema via migrations
     *
     * @throws ExecutionException if async setup fails
     * @throws InterruptedException if setup is interrupted
     */
    @BeforeAll
    public static void setUpAll() throws ExecutionException, InterruptedException { // GH-90000
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable(); // GH-90000
        } catch (Throwable ex) { // GH-90000
            available = false;
        }
        dockerAvailable = available;
        Assumptions.assumeTrue(dockerAvailable, "Skipping API integration tests because Docker is unavailable"); // GH-90000

        // Set environment variables for test containers
        System.setProperty("requirements.DB_URL", "jdbc:postgresql://" + postgres.getHost() + ":" // GH-90000
            + postgres.getFirstMappedPort() + "/" + TEST_DB_NAME); // GH-90000
        System.setProperty("requirements.DB_USER", TEST_DB_USER); // GH-90000
        System.setProperty("requirements.DB_PASSWORD", TEST_DB_PASSWORD); // GH-90000

        // Initialize configuration with test container settings
        config = new RequirementsConfig(); // GH-90000

        // Skip HTTP server startup for now - tests will fail until properly configured
        // Note: HTTP server setup with proper dependency injection not yet implemented
        logger.info("HTTP server startup skipped - tests will be implemented with proper mocks");

        // Run database migrations
        runDatabaseMigrations(); // GH-90000
    }

    /**
     * Clean up test infrastructure after all tests complete.
     *
     * <p>Stops the HTTP server to free up the port.
     */
    @AfterAll
    public static void tearDownAll() throws Exception { // GH-90000
        logger.info("Test cleanup completed - HTTP server was not started");
    }

    /**
     * Set up test infrastructure before each test.
     *
     * @throws Exception if setup fails
     */
    @BeforeEach
    public void setUp() throws Exception { // GH-90000
        // Initialize HTTP client in the test thread context
        httpClient = HttpClient.builder(io.activej.reactor.Reactor.getCurrentReactor(), // GH-90000
            io.activej.dns.DnsClient.builder(io.activej.reactor.Reactor.getCurrentReactor(), // GH-90000
                java.net.InetAddress.getLoopbackAddress()).build()).build(); // GH-90000

        // Clear test data before each test
        cleanupTestData(); // GH-90000
    }

    /**
     * Run database migrations to create test schema.
     *
     * <p>Creates tables for users, workspaces, projects, and requirements.
     * This is a simplified migration for testing - production would use Flyway/Liquibase.
     *
     * @throws RuntimeException if migration fails
     */
    protected static void runDatabaseMigrations() { // GH-90000
        // This would typically call a migration framework like Flyway or Liquibase
        // For now, we'll just create the basic schema
        try (var connection = postgres.createConnection("")) {
            var statement = connection.createStatement(); // GH-90000
            // Execute DDL statements to create test tables
            statement.execute("CREATE TABLE IF NOT EXISTS users (id UUID PRIMARY KEY, email VARCHAR(255) NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS workspaces (id UUID PRIMARY KEY, name VARCHAR(255) NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS projects (id UUID PRIMARY KEY, workspace_id UUID NOT NULL, name VARCHAR(255) NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS requirements (id UUID PRIMARY KEY, project_id UUID NOT NULL, title VARCHAR(255) NOT NULL)");
        } catch (Exception e) { // GH-90000
            throw new RuntimeException("Failed to run migrations", e); // GH-90000
        }
    }

    /**
     * Clean up test data to ensure test isolation.
     *
     * <p>Truncates all test tables in cascade order to remove test data
     * created during test execution.
     *
     * @throws Exception if cleanup fails
     */
    protected void cleanupTestData() throws Exception { // GH-90000
        if (!dockerAvailable) { // GH-90000
            return;
        }
        try (var connection = postgres.createConnection("")) {
            var statement = connection.createStatement(); // GH-90000
            statement.execute("TRUNCATE TABLE requirements CASCADE");
            statement.execute("TRUNCATE TABLE projects CASCADE");
            statement.execute("TRUNCATE TABLE workspaces CASCADE");
            statement.execute("TRUNCATE TABLE users CASCADE");
        }
    }

    /**
     * Perform HTTP GET request to the test server.
     *
     * @param path the request path (e.g., "/api/v1/workspaces") // GH-90000
     * @return the HTTP response
     * @throws ExecutionException if request execution fails
     * @throws InterruptedException if request is interrupted
     */
    protected HttpResponse performGet(String path) throws ExecutionException, InterruptedException { // GH-90000
        return runPromise(() -> httpClient.request(io.activej.http.HttpRequest.get("http://localhost:8082" + path).build())); // GH-90000
    }

    /**
     * Perform HTTP POST request to the test server.
     *
     * @param path the request path (e.g., "/api/v1/workspaces") // GH-90000
     * @param body the request body as JSON string
     * @return the HTTP response
     * @throws ExecutionException if request execution fails
     * @throws InterruptedException if request is interrupted
     */
    protected HttpResponse performPost(String path, String body) throws ExecutionException, InterruptedException { // GH-90000
        return runPromise(() -> httpClient.request(io.activej.http.HttpRequest.post("http://localhost:8082" + path) // GH-90000
            .withBody(body) // GH-90000
            .build())); // GH-90000
    }

    /**
     * Perform HTTP PUT request to the test server.
     *
     * @param path the request path (e.g., "/api/v1/workspaces/123") // GH-90000
     * @param body the request body as JSON string
     * @return the HTTP response
     * @throws ExecutionException if request execution fails
     * @throws InterruptedException if request is interrupted
     */
    protected HttpResponse performPut(String path, String body) throws ExecutionException, InterruptedException { // GH-90000
        return runPromise(() -> httpClient.request(io.activej.http.HttpRequest.put("http://localhost:8082" + path) // GH-90000
            .withBody(body) // GH-90000
            .build())); // GH-90000
    }

    /**
     * Perform HTTP DELETE request to the test server.
     *
     * @param path the request path (e.g., "/api/v1/workspaces/123") // GH-90000
     * @return the HTTP response
     * @throws ExecutionException if request execution fails
     * @throws InterruptedException if request is interrupted
     */
    protected HttpResponse performDelete(String path) throws ExecutionException, InterruptedException { // GH-90000
        return runPromise(() -> httpClient.request(io.activej.http.HttpRequest.builder(io.activej.http.HttpMethod.DELETE, "http://localhost:8082" + path).build())); // GH-90000
    }
}
