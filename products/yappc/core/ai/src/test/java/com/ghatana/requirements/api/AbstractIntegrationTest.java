package com.ghatana.requirements.api;

import com.ghatana.requirements.api.config.RequirementsConfig;
import com.ghatana.requirements.api.http.RequirementsHttpServer;
import io.activej.http.HttpClient;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.DockerClientFactory;

import java.net.InetSocketAddress;
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
 *     void shouldCreateWorkspace() throws Exception {
 *         HttpResponse response = performPost("/api/v1/workspaces", 
 *             "{\"name\": \"Test Workspace\"}");
 *         assertThat(response.getCode()).isEqualTo(201);
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
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
public abstract class AbstractIntegrationTest extends EventloopTestBase {
    
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
        .withUsername(TEST_DB_USER)
        .withPassword(TEST_DB_PASSWORD)
        .withDatabaseName(TEST_DB_NAME)
        .waitingFor(Wait.forListeningPort());
    
    /** Redis test container. Managed by Testcontainers. */
    @Container
    protected static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379)
        .waitingFor(Wait.forListeningPort());
    
    /** HTTP client for making requests to the server. Initialized in setUpAll(). */
    protected static HttpClient httpClient;
    
    /** HTTP server instance under test. Initialized in setUpAll(). */
    protected static RequirementsHttpServer httpServer;
    
    /** Configuration for the HTTP server. Initialized in setUpAll(). */
    protected static RequirementsConfig config;
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
    public static void setUpAll() throws ExecutionException, InterruptedException {
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            available = false;
        }
        dockerAvailable = available;
        Assumptions.assumeTrue(dockerAvailable, "Skipping API integration tests because Docker is unavailable");

        // Set environment variables for test containers
        System.setProperty("requirements.DB_URL", "jdbc:postgresql://" + postgres.getHost() + ":" 
            + postgres.getFirstMappedPort() + "/" + TEST_DB_NAME);
        System.setProperty("requirements.DB_USER", TEST_DB_USER);
        System.setProperty("requirements.DB_PASSWORD", TEST_DB_PASSWORD);
        
        // Initialize configuration with test container settings
        config = new RequirementsConfig();
        
        // Initialize HTTP client
        httpClient = HttpClient.builder(io.activej.reactor.Reactor.getCurrentReactor(), 
            io.activej.dns.DnsClient.builder(io.activej.reactor.Reactor.getCurrentReactor(), 
                java.net.InetAddress.getLoopbackAddress()).build()).build();
        
        // Run database migrations
        runDatabaseMigrations();
    }
    
    /**
     * Clean up test data before each test to ensure isolation.
     *
     * @throws Exception if cleanup fails
     */
    @BeforeEach
    public void setUp() throws Exception {
        // Clear test data before each test
        cleanupTestData();
    }
    
    /**
     * Run database migrations to create test schema.
     *
     * <p>Creates tables for users, workspaces, projects, and requirements.
     * This is a simplified migration for testing - production would use Flyway/Liquibase.
     *
     * @throws RuntimeException if migration fails
     */
    protected static void runDatabaseMigrations() {
        // This would typically call a migration framework like Flyway or Liquibase
        // For now, we'll just create the basic schema
        try (var connection = postgres.createConnection("")) {
            var statement = connection.createStatement();
            // Execute DDL statements to create test tables
            statement.execute("CREATE TABLE IF NOT EXISTS users (id UUID PRIMARY KEY, email VARCHAR(255) NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS workspaces (id UUID PRIMARY KEY, name VARCHAR(255) NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS projects (id UUID PRIMARY KEY, workspace_id UUID NOT NULL, name VARCHAR(255) NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS requirements (id UUID PRIMARY KEY, project_id UUID NOT NULL, title VARCHAR(255) NOT NULL)");
        } catch (Exception e) {
            throw new RuntimeException("Failed to run migrations", e);
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
    protected void cleanupTestData() throws Exception {
        if (!dockerAvailable) {
            return;
        }
        try (var connection = postgres.createConnection("")) {
            var statement = connection.createStatement();
            statement.execute("TRUNCATE TABLE requirements CASCADE");
            statement.execute("TRUNCATE TABLE projects CASCADE");
            statement.execute("TRUNCATE TABLE workspaces CASCADE");
            statement.execute("TRUNCATE TABLE users CASCADE");
        }
    }
    
    /**
     * Perform HTTP GET request to the test server.
     *
     * @param path the request path (e.g., "/api/v1/workspaces")
     * @return the HTTP response
     * @throws ExecutionException if request execution fails
     * @throws InterruptedException if request is interrupted
     */
    protected HttpResponse performGet(String path) throws ExecutionException, InterruptedException {
        return httpClient.request(io.activej.http.HttpRequest.get("http://localhost:8080" + path).build())
            .toCompletableFuture().get();
    }
    
    /**
     * Perform HTTP POST request to the test server.
     *
     * @param path the request path (e.g., "/api/v1/workspaces")
     * @param body the request body as JSON string
     * @return the HTTP response
     * @throws ExecutionException if request execution fails
     * @throws InterruptedException if request is interrupted
     */
    protected HttpResponse performPost(String path, String body) throws ExecutionException, InterruptedException {
        return httpClient.request(io.activej.http.HttpRequest.post("http://localhost:8080" + path)
            .withBody(body)
            .build())
            .toCompletableFuture().get();
    }
    
    /**
     * Perform HTTP PUT request to the test server.
     *
     * @param path the request path (e.g., "/api/v1/workspaces/123")
     * @param body the request body as JSON string
     * @return the HTTP response
     * @throws ExecutionException if request execution fails
     * @throws InterruptedException if request is interrupted
     */
    protected HttpResponse performPut(String path, String body) throws ExecutionException, InterruptedException {
        return httpClient.request(io.activej.http.HttpRequest.put("http://localhost:8080" + path)
            .withBody(body)
            .build())
            .toCompletableFuture().get();
    }
    
    /**
     * Perform HTTP DELETE request to the test server.
     *
     * @param path the request path (e.g., "/api/v1/workspaces/123")
     * @return the HTTP response
     * @throws ExecutionException if request execution fails
     * @throws InterruptedException if request is interrupted
     */
    protected HttpResponse performDelete(String path) throws ExecutionException, InterruptedException {
        return httpClient.request(io.activej.http.HttpRequest.builder(io.activej.http.HttpMethod.DELETE, "http://localhost:8080" + path).build())
            .toCompletableFuture().get();
    }
}
