package com.ghatana.platform.testing.containers;

import com.ghatana.platform.testing.internal.containers.PostgresTestContainer;

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

    private static final Logger log = LoggerFactory.getLogger(PostgresIntegrationTest.class); // GH-90000
    private static boolean dockerAvailable;

    @BeforeAll
    static void setup() { // GH-90000
        // Check Docker availability first
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable(); // GH-90000
        } catch (Throwable ex) { // GH-90000
            log.warn("Docker availability check failed: {}", ex.getMessage()); // GH-90000
            available = false;
        }
        dockerAvailable = available;

        // Skip tests gracefully if Docker is not running
        Assumptions.assumeTrue(dockerAvailable, // GH-90000
                () -> "Skipping PostgresIntegrationTest because Docker is unavailable. Start Docker Desktop to run these tests."); // GH-90000

        try {
            log.info("Starting PostgreSQL container for testing... [GH-90000]");
            // Start the container before all tests
            PostgresTestContainer.start(); // GH-90000
            log.info("PostgreSQL container started at: {}", PostgresTestContainer.getJdbcUrl()); // GH-90000

            // Initialize test data
            log.info("Initializing test data... [GH-90000]");
            initializeTestData(); // GH-90000
            log.info("Test data initialization complete [GH-90000]");
        } catch (Exception e) { // GH-90000
            log.error("Failed to initialize test environment", e); // GH-90000
            Assumptions.assumeTrue(false, // GH-90000
                    "Skipping PostgresIntegrationTest because PostgreSQL container setup failed: " + e.getMessage()); // GH-90000
        }
    }

    @AfterAll
    static void tearDown() { // GH-90000
        // Container will be stopped by the JVM shutdown hook
        log.info("PostgreSQL integration tests completed [GH-90000]");
    }

    @Test
    void shouldConnectToDatabase() throws SQLException { // GH-90000
        // Given
        String url = PostgresTestContainer.getJdbcUrl(); // GH-90000
        String username = PostgresTestContainer.getUsername(); // GH-90000
        String password = PostgresTestContainer.getPassword(); // GH-90000

        // When
        try (Connection conn = DriverManager.getConnection(url, username, password)) { // GH-90000
            // Then
            assertThat(conn).isNotNull(); // GH-90000
            assertThat(conn.isValid(5)).isTrue(); // GH-90000
            log.info("Successfully connected to test database: {}", url); // GH-90000
        }
    }

    @Test
    void shouldExecuteQuery() throws SQLException { // GH-90000
        // Given
        String url = PostgresTestContainer.getJdbcUrl(); // GH-90000
        String username = PostgresTestContainer.getUsername(); // GH-90000
        String password = PostgresTestContainer.getPassword(); // GH-90000

        // When
        try (Connection conn = DriverManager.getConnection(url, username, password); var stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1")) {

            // Then
            assertThat(rs.next()).isTrue(); // GH-90000
            assertThat(rs.getInt(1)).isEqualTo(1); // GH-90000
        }
    }

    private static void initializeTestData() { // GH-90000
        // This would typically create test tables and insert test data
        try (Connection conn = DriverManager.getConnection( // GH-90000
                PostgresTestContainer.getJdbcUrl(), // GH-90000
                PostgresTestContainer.getUsername(), // GH-90000
                PostgresTestContainer.getPassword()); var stmt = conn.createStatement()) { // GH-90000

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

            log.info("Test data initialized [GH-90000]");
        } catch (SQLException e) { // GH-90000
            log.error("Failed to initialize test data", e); // GH-90000
            throw new RuntimeException("Failed to initialize test data", e); // GH-90000
        }
    }
}
