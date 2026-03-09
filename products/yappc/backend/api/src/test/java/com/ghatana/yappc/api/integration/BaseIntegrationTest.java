/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module - Integration Tests
 */
package com.ghatana.yappc.api.integration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base class for integration tests.
 *
 * <p>Provides:
 * <ul>
 *   <li>PostgreSQL test container</li>
 *   <li>Database connection pool</li>
 *   <li>Database cleanup between tests</li>
 *   <li>Test data helpers</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Integration test base
 * @doc.layer test
 * @doc.pattern Test Base Class
 */
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("yappc_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    protected static DataSource dataSource;

    @BeforeAll
    static void setupDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(5);

        dataSource = new HikariDataSource(config);

        // Run migrations
        runMigrations();
    }

    @AfterAll
    static void teardownDatabase() {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }

    @BeforeEach
    void cleanDatabase() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Disable foreign key checks
            stmt.execute("SET session_replication_role = 'replica'");
            
            // Truncate all tables
            stmt.execute("TRUNCATE TABLE approval_records CASCADE");
            stmt.execute("TRUNCATE TABLE approval_stages CASCADE");
            stmt.execute("TRUNCATE TABLE approval_workflows CASCADE");
            stmt.execute("TRUNCATE TABLE audit_events CASCADE");
            stmt.execute("TRUNCATE TABLE stories CASCADE");
            stmt.execute("TRUNCATE TABLE sprints CASCADE");
            stmt.execute("TRUNCATE TABLE requirements CASCADE");
            stmt.execute("TRUNCATE TABLE projects CASCADE");
            stmt.execute("TRUNCATE TABLE workspaces CASCADE");
            stmt.execute("TRUNCATE TABLE users CASCADE");
            stmt.execute("TRUNCATE TABLE tenants CASCADE");
            
            // Re-enable foreign key checks
            stmt.execute("SET session_replication_role = 'origin'");
        }
    }

    private static void runMigrations() {
        // TODO: Run Flyway migrations
        // For now, assume schema is already created by Flyway
    }

    // ========================================================================
    // Test Data Helpers
    // ========================================================================

    protected String createTestTenant(String tenantId, String name) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(String.format(
                "INSERT INTO tenants (id, name, status) VALUES ('%s', '%s', 'ACTIVE')",
                tenantId, name
            ));
        }
        return tenantId;
    }

    protected String createTestUser(String userId, String tenantId, String email, String name) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(String.format(
                "INSERT INTO users (id, tenant_id, email, name, status) VALUES ('%s', '%s', '%s', '%s', 'ACTIVE')",
                userId, tenantId, email, name
            ));
        }
        return userId;
    }

    protected String createTestWorkspace(String workspaceId, String tenantId, String ownerId, String name) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(String.format(
                "INSERT INTO workspaces (id, tenant_id, owner_id, name) VALUES ('%s', '%s', '%s', '%s')",
                workspaceId, tenantId, ownerId, name
            ));
        }
        return workspaceId;
    }

    protected String createTestProject(String projectId, String tenantId, String workspaceId, String name) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(String.format(
                "INSERT INTO projects (id, tenant_id, workspace_id, name, status) VALUES ('%s', '%s', '%s', '%s', 'ACTIVE')",
                projectId, tenantId, workspaceId, name
            ));
        }
        return projectId;
    }

    protected void assertTenantIsolation(String tenantId, String tableName, String idColumn, String expectedId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery(String.format(
                "SELECT COUNT(*) FROM %s WHERE tenant_id = '%s' AND %s = '%s'",
                tableName, tenantId, idColumn, expectedId
            ));
            rs.next();
            int count = rs.getInt(1);
            if (count != 1) {
                throw new AssertionError(String.format(
                    "Expected 1 row in %s for tenant %s with %s = %s, but found %d",
                    tableName, tenantId, idColumn, expectedId, count
                ));
            }
        }
    }
}
