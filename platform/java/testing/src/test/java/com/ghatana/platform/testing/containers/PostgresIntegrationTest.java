package com.ghatana.platform.testing.containers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;

import com.ghatana.platform.testing.IntegrationTest;

@IntegrationTest
class PostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresIntegrationTest.class);
    private static boolean dockerAvailable;

    @BeforeAll
    static void setup() {
        // Check Docker availability first
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            log.warn("Docker availability check failed: {}", ex.getMessage());
            available = false;
        }
        dockerAvailable = available;

        // Skip tests gracefully if Docker is not running
        Assumptions.assumeTrue(dockerAvailable,
                () -> "Skipping PostgresIntegrationTest because Docker is unavailable. Start Docker Desktop to run these tests.");

        try {
            log.info("Starting PostgreSQL container for testing...");
            // Start the container before all tests
            PostgresTestContainer.start();
            log.info("PostgreSQL container started at: {}", PostgresTestContainer.getJdbcUrl());

            // Initialize test data
            log.info("Initializing test data...");
            initializeTestData();
            log.info("Test data initialization complete");
        } catch (Exception e) {
            log.error("Failed to initialize test environment", e);
            throw new RuntimeException("Test initialization failed", e);
        }
    }

    @AfterAll
    static void tearDown() {
        // Container will be stopped by the JVM shutdown hook
        log.info("PostgreSQL integration tests completed");
    }

    @Test
    void shouldConnectToDatabase() throws SQLException {
        // Given
        String url = PostgresTestContainer.getJdbcUrl();
        String username = PostgresTestContainer.getUsername();
        String password = PostgresTestContainer.getPassword();

        // When
        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            // Then
            assertThat(conn).isNotNull();
            assertThat(conn.isValid(5)).isTrue();
            log.info("Successfully connected to test database: {}", url);
        }
    }

    @Test
    void shouldExecuteQuery() throws SQLException {
        // Given
        String url = PostgresTestContainer.getJdbcUrl();
        String username = PostgresTestContainer.getUsername();
        String password = PostgresTestContainer.getPassword();

        // When
        try (Connection conn = DriverManager.getConnection(url, username, password); var stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {

            // Then
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    private static void initializeTestData() {
        // This would typically create test tables and insert test data
        try (Connection conn = DriverManager.getConnection(
                PostgresTestContainer.getJdbcUrl(),
                PostgresTestContainer.getUsername(),
                PostgresTestContainer.getPassword()); var stmt = conn.createStatement()) {

            // Create a test table if it doesn't exist
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS test_table (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                )
                """);

            // Insert test data
            stmt.executeUpdate("""
                INSERT INTO test_table (name) VALUES 
                ('Test 1'),
                ('Test 2'),
                ('Test 3')
                """);

            log.info("Test data initialized");
        } catch (SQLException e) {
            log.error("Failed to initialize test data", e);
            throw new RuntimeException("Failed to initialize test data", e);
        }
    }
}
