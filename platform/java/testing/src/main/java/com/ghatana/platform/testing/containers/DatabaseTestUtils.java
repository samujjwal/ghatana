package com.ghatana.platform.testing.containers;

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
 * Utilities for database testing with Testcontainers.
 *
 * @doc.type class
 * @doc.purpose Testcontainers-based database testing utilities for PostgreSQL
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class DatabaseTestUtils {
    private static final Logger log = LoggerFactory.getLogger(DatabaseTestUtils.class);
    
    // Default PostgreSQL image
    private static final String DEFAULT_POSTGRES_IMAGE = "postgres:15-alpine";
    
    private DatabaseTestUtils() {
        // Utility class
    }
    
    /**
     * Creates a new PostgreSQL container with default settings.
     *
     * @return a configured PostgreSQL container
     */
    public static PostgreSQLContainer<?> createPostgresContainer() {
        return createPostgresContainer(DEFAULT_POSTGRES_IMAGE);
    }
    
    /**
     * Creates a new PostgreSQL container with a specific image.
     *
     * @param imageName the Docker image name and tag
     * @return a configured PostgreSQL container
     */
    public static PostgreSQLContainer<?> createPostgresContainer(String imageName) {
        return new PostgreSQLContainer<>(DockerImageName.parse(imageName))
                .withDatabaseName("testdb")
                .withUsername("testuser")
                .withPassword("testpass")
                .withInitScript("db/init.sql");
    }
    
    /**
     * Gets a connection to the database in the container.
     *
     * @param container the database container
     * @return a new database connection
     * @throws SQLException if a database access error occurs
     */
    public static Connection getConnection(JdbcDatabaseContainer<?> container) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", container.getUsername());
        props.setProperty("password", container.getPassword());
        return DriverManager.getConnection(container.getJdbcUrl(), props);
    }
    
    /**
     * Executes SQL statements from a string.
     *
     * @param connection the database connection
     * @param sql the SQL statements to execute (semicolon-separated)
     * @throws SQLException if a database access error occurs
     */
    public static void executeSql(Connection connection, String sql) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Split by semicolon to handle multiple statements
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
    
    /**
     * Creates a test database with the given name.
     *
     * @param connection the database connection
     * @param dbName the name of the database to create
     * @throws SQLException if a database access error occurs
     */
    public static void createDatabase(Connection connection, String dbName) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Disable auto-commit to allow DDL in transactions
            connection.setAutoCommit(false);
            
            // End any existing connections to the database
            String terminateSql = String.format(
                "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '%s' AND pid <> pg_backend_pid()", 
                dbName);
            
            try {
                stmt.execute(terminateSql);
            } catch (SQLException e) {
                // Ignore if the database doesn't exist yet
                log.debug("No existing connections to terminate for database: {}", dbName);
            }
            
            // Drop the database if it exists
            String dropDbSql = String.format("DROP DATABASE IF EXISTS \"%s\"", dbName);
            stmt.execute(dropDbSql);
            
            // Create the database
            String createDbSql = String.format("CREATE DATABASE \"%s\"", dbName);
            stmt.execute(createDbSql);
            
            connection.commit();
            log.info("Created test database: {}", dbName);
        }
    }
    
    /**
     * Drops a database.
     *
     * @param connection the database connection
     * @param dbName the name of the database to drop
     * @throws SQLException if a database access error occurs
     */
    public static void dropDatabase(Connection connection, String dbName) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // End any existing connections to the database
            String terminateSql = String.format(
                "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '%s' AND pid <> pg_backend_pid()", 
                dbName);
            
            try {
                stmt.execute(terminateSql);
            } catch (SQLException e) {
                log.warn("Failed to terminate connections to database: {}", dbName, e);
            }
            
            // Drop the database
            String dropDbSql = String.format("DROP DATABASE IF EXISTS \"%s\"", dbName);
            stmt.execute(dropDbSql);
            connection.commit();
            log.info("Dropped database: {}", dbName);
        }
    }
}
