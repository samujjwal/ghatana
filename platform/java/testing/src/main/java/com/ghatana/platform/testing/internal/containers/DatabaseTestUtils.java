package com.ghatana.platform.testing.internal.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Internal database test utilities backed by Testcontainers.
 *
 * @doc.type class
 * @doc.purpose Internal Testcontainers-based database test utilities
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class DatabaseTestUtils {
    private static final Logger log = LoggerFactory.getLogger(DatabaseTestUtils.class);
    private static final String DEFAULT_POSTGRES_IMAGE = "postgres:15-alpine";

    private DatabaseTestUtils() {
    }

    public static PostgreSQLContainer<?> createPostgresContainer() {
        return createPostgresContainer(DEFAULT_POSTGRES_IMAGE);
    }

    public static PostgreSQLContainer<?> createPostgresContainer(String imageName) {
        return new PostgreSQLContainer<>(DockerImageName.parse(imageName))
                .withDatabaseName("testdb")
                .withUsername("testuser")
                .withPassword("testpass")
                .withInitScript("db/init.sql");
    }

    public static Connection getConnection(JdbcDatabaseContainer<?> container) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", container.getUsername());
        props.setProperty("password", container.getPassword());
        return DriverManager.getConnection(container.getJdbcUrl(), props);
    }

    public static void executeSql(Connection connection, String sql) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            String[] statements = sql.split(";\\s*\n");
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    log.debug("Executing SQL: {}", trimmed);
                    stmt.execute(trimmed);
                }
            }
            connection.commit();
        }
    }

    public static void createDatabase(Connection connection, String dbName) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            connection.setAutoCommit(false);
            String terminateSql = String.format(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '%s' AND pid <> pg_backend_pid()",
                    dbName);

            try {
                stmt.execute(terminateSql);
            } catch (SQLException e) {
                log.debug("No existing connections to terminate for database: {}", dbName);
            }

            stmt.execute(String.format("DROP DATABASE IF EXISTS \"%s\"", dbName));
            stmt.execute(String.format("CREATE DATABASE \"%s\"", dbName));
            connection.commit();
            log.info("Created test database: {}", dbName);
        }
    }

    public static void dropDatabase(Connection connection, String dbName) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            String terminateSql = String.format(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '%s' AND pid <> pg_backend_pid()",
                    dbName);

            try {
                stmt.execute(terminateSql);
            } catch (SQLException e) {
                log.warn("Failed to terminate connections to database: {}", dbName, e);
            }

            stmt.execute(String.format("DROP DATABASE IF EXISTS \"%s\"", dbName));
            connection.commit();
            log.info("Dropped database: {}", dbName);
        }
    }
}
