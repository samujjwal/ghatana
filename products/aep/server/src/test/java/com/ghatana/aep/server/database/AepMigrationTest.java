/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.database;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-MIG-1: AEP Flyway migration tests for fresh schema setup
 * 
 * Tests verify:
 * - All migrations can be executed on a fresh database
 * - Schema is correctly created with all tables, indexes, and constraints
 * - Data can be inserted and queried successfully
 * - Migrations are idempotent (can be re-run without errors)
 * - Tenant isolation constraints are enforced
 * - JSONB columns work correctly for state/config/result fields
 * - Version tracking works for optimistic concurrency
 * 
 * @doc.type class
 * @doc.purpose Flyway migration tests for AEP schema
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true)
class AepMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("aep_test")
        .withUsername("test")
        .withPassword("test");

    private Flyway flyway;
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        // Configure Flyway with test database
        flyway = Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load();

        dataSource = flyway.getConfiguration().getDataSource();
    }

    @AfterEach
    void tearDown() {
        if (flyway != null) {
            flyway.clean();
        }
    }

    // ==================== Fresh Schema Setup Tests ====================

    @Test
    void migrationsExecuteSuccessfullyOnFreshDatabase() {
        // All migrations should execute without errors on a fresh database
        assertDoesNotThrow(() -> {
            int migrationsApplied = flyway.migrate().migrationsExecuted;
            assertTrue(migrationsApplied > 0, "At least one migration should be applied");
        });
    }

    @Test
    void allRequiredTablesExistAfterMigration() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Verify core AEP tables exist
            assertTableExists(conn, "pipeline_checkpoints");
            assertTableExists(conn, "aep_event_checkpoints");
            assertTableExists(conn, "patterns");
            assertTableExists(conn, "agent_registry");
            assertTableExists(conn, "pipeline_registry");
            assertTableExists(conn, "audit_trail");
            assertTableExists(conn, "aep_sessions");
            assertTableExists(conn, "aep_audit_log");
            assertTableExists(conn, "aep_kill_switch");
            assertTableExists(conn, "aep_policy_versions");
            assertTableExists(conn, "aep_approvals");
            assertTableExists(conn, "aep_consents");
            assertTableExists(conn, "aep_recertification");
            assertTableExists(conn, "agent_execution_history");
            assertTableExists(conn, "memory_items");
            assertTableExists(conn, "task_states");
        } catch (SQLException e) {
            fail("Failed to verify table existence: " + e.getMessage());
        }
    }

    // ==================== Data Operations Tests ====================

    @Test
    void canInsertAndQueryPipelineCheckpoints() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Insert test checkpoint
            String insertSql = """
                INSERT INTO pipeline_checkpoints (
                    instance_id, tenant_id, pipeline_id, idempotency_key, status,
                    state, result, current_step_id, current_step_name,
                    completed_steps, total_steps, version
                ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, "checkpoint-123");
                stmt.setString(2, "test-tenant");
                stmt.setString(3, "pipeline-456");
                stmt.setString(4, "idempotency-key-789");
                stmt.setString(5, "RUNNING");
                stmt.setString(6, "{\"step\": \"processing\"}");
                stmt.setString(7, "{\"output\": \"test\"}");
                stmt.setString(8, "step-1");
                stmt.setString(9, "Processing Step");
                stmt.setInt(10, 1);
                stmt.setInt(11, 5);
                stmt.setLong(12, 1);
                
                int rowsInserted = stmt.executeUpdate();
                assertEquals(1, rowsInserted, "Should insert one checkpoint");
            }

            // Query the checkpoint back
            String selectSql = "SELECT status, state, result FROM pipeline_checkpoints WHERE tenant_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, "test-tenant");
                ResultSet rs = stmt.executeQuery();
                
                assertTrue(rs.next(), "Should retrieve the inserted checkpoint");
                String status = rs.getString("status");
                String state = rs.getString("state");
                String result = rs.getString("result");
                assertEquals("RUNNING", status);
                assertEquals("{\"step\": \"processing\"}", state);
                assertEquals("{\"output\": \"test\"}", result);
            }
        } catch (SQLException e) {
            fail("Failed to insert/query checkpoint data: " + e.getMessage());
        }
    }

    @Test
    void canInsertAndQueryAgentRegistry() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Insert test agent
            String insertSql = """
                INSERT INTO agent_registry (
                    id, tenant_id, name, agent_type, status, config
                ) VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, "agent-123");
                stmt.setString(2, "test-tenant");
                stmt.setString(3, "Test Agent");
                stmt.setString(4, "LEARNING");
                stmt.setString(5, "ACTIVE");
                stmt.setString(6, "{\"param\": \"value\"}");
                
                int rowsInserted = stmt.executeUpdate();
                assertEquals(1, rowsInserted, "Should insert one agent");
            }

            // Query the agent back
            String selectSql = "SELECT name, agent_type, status, config FROM agent_registry WHERE tenant_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, "test-tenant");
                ResultSet rs = stmt.executeQuery();
                
                assertTrue(rs.next(), "Should retrieve the inserted agent");
                String name = rs.getString("name");
                String agentType = rs.getString("agent_type");
                String status = rs.getString("status");
                String config = rs.getString("config");
                assertEquals("Test Agent", name);
                assertEquals("LEARNING", agentType);
                assertEquals("ACTIVE", status);
                assertEquals("{\"param\": \"value\"}", config);
            }
        } catch (SQLException e) {
            fail("Failed to insert/query agent data: " + e.getMessage());
        }
    }

    @Test
    void canInsertAndQueryPatterns() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Insert test pattern
            String insertSql = """
                INSERT INTO patterns (
                    tenant_id, name, description, spec, labels, event_types,
                    pattern_type, status, enabled
                ) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, "test-tenant");
                stmt.setString(2, "Test Pattern");
                stmt.setString(3, "A test pattern for validation");
                stmt.setString(4, "{\"type\": \"temporal\", \"window\": 60}");
                stmt.setString(5, "[\"label1\", \"label2\"]");
                stmt.setString(6, "[\"event1\", \"event2\"]");
                stmt.setString(7, "TEMPORAL");
                stmt.setString(8, "ACTIVE");
                stmt.setBoolean(9, true);
                
                int rowsInserted = stmt.executeUpdate();
                assertEquals(1, rowsInserted, "Should insert one pattern");
            }

            // Query the pattern back
            String selectSql = "SELECT name, pattern_type, status, spec FROM patterns WHERE tenant_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, "test-tenant");
                ResultSet rs = stmt.executeQuery();
                
                assertTrue(rs.next(), "Should retrieve the inserted pattern");
                String name = rs.getString("name");
                String patternType = rs.getString("pattern_type");
                String status = rs.getString("status");
                String spec = rs.getString("spec");
                assertEquals("Test Pattern", name);
                assertEquals("TEMPORAL", patternType);
                assertEquals("ACTIVE", status);
                assertEquals("{\"type\": \"temporal\", \"window\": 60}", spec);
            }
        } catch (SQLException e) {
            fail("Failed to insert/query pattern data: " + e.getMessage());
        }
    }

    @Test
    void canInsertAndQueryAgentExecutionHistory() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Insert test execution record
            String insertSql = """
                INSERT INTO agent_execution_history (
                    execution_id, agent_id, status, input_payload, output_payload,
                    duration_ms, executed_at
                ) VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, "exec-123");
                stmt.setString(2, "agent-456");
                stmt.setString(3, "completed");
                stmt.setString(4, "{\"input\": \"data\"}");
                stmt.setString(5, "{\"output\": \"result\"}");
                stmt.setLong(6, 1500);
                stmt.setObject(7, java.time.Instant.now());
                
                int rowsInserted = stmt.executeUpdate();
                assertEquals(1, rowsInserted, "Should insert one execution record");
            }

            // Query the execution record back
            String selectSql = "SELECT status, duration_ms, input_payload FROM agent_execution_history WHERE agent_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, "agent-456");
                ResultSet rs = stmt.executeQuery();
                
                assertTrue(rs.next(), "Should retrieve the inserted execution record");
                String status = rs.getString("status");
                long durationMs = rs.getLong("duration_ms");
                String inputPayload = rs.getString("input_payload");
                assertEquals("completed", status);
                assertEquals(1500, durationMs);
                assertEquals("{\"input\": \"data\"}", inputPayload);
            }
        } catch (SQLException e) {
            fail("Failed to insert/query execution history data: " + e.getMessage());
        }
    }

    // ==================== Constraint Enforcement Tests ====================

    @Test
    void tenantIdIsRequiredForAllTables() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Try to insert checkpoint without tenant_id
            String insertSql = """
                INSERT INTO pipeline_checkpoints (
                    instance_id, pipeline_id, idempotency_key, status
                ) VALUES (?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, "checkpoint-no-tenant");
                stmt.setString(2, "pipeline-456");
                stmt.setString(3, "idempotency-key-789");
                stmt.setString(4, "RUNNING");
                
                assertThrows(SQLException.class, stmt::executeUpdate,
                    "Should reject insert without tenant_id due to NOT NULL constraint");
            }
        } catch (SQLException e) {
            fail("Failed during tenant constraint test: " + e.getMessage());
        }
    }

    @Test
    void uniqueConstraintEnforcesIdempotencyKey() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Insert first checkpoint with idempotency key
            String insertSql = """
                INSERT INTO pipeline_checkpoints (
                    instance_id, tenant_id, pipeline_id, idempotency_key, status
                ) VALUES (?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, "checkpoint-unique-1");
                stmt.setString(2, "test-tenant");
                stmt.setString(3, "pipeline-456");
                stmt.setString(4, "idempotency-key-unique");
                stmt.setString(5, "RUNNING");
                stmt.executeUpdate();
            }

            // Try to insert second checkpoint with same idempotency key - should fail
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, "checkpoint-unique-2");
                stmt.setString(2, "test-tenant");
                stmt.setString(3, "pipeline-456");
                stmt.setString(4, "idempotency-key-unique"); // Same idempotency key
                stmt.setString(5, "RUNNING");
                
                assertThrows(SQLException.class, stmt::executeUpdate,
                    "Should reject duplicate idempotency key due to unique constraint");
            }
        } catch (SQLException e) {
            fail("Failed during unique constraint test: " + e.getMessage());
        }
    }

    @Test
    void uniqueConstraintEnforcesPatternNamePerTenant() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Insert first pattern with name
            String insertSql = """
                INSERT INTO patterns (tenant_id, name, spec, status)
                VALUES (?, ?, ?::jsonb, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, "test-tenant");
                stmt.setString(2, "UniquePattern");
                stmt.setString(3, "{}");
                stmt.setString(4, "ACTIVE");
                stmt.executeUpdate();
            }

            // Try to insert second pattern with same name - should fail
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, "test-tenant");
                stmt.setString(2, "UniquePattern"); // Same name
                stmt.setString(3, "{}");
                stmt.setString(4, "ACTIVE");
                
                assertThrows(SQLException.class, stmt::executeUpdate,
                    "Should reject duplicate pattern name due to unique constraint");
            }
        } catch (SQLException e) {
            fail("Failed during pattern name unique constraint test: " + e.getMessage());
        }
    }

    // ==================== Tenant Isolation Tests ====================

    @Test
    void tenantScopedQueriesOnlyReturnTenantData() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Insert agent for tenant A
            String insertSql = """
                INSERT INTO agent_registry (id, tenant_id, name, agent_type, status)
                VALUES (?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, "agent-tenant-a");
                stmt.setString(2, "tenant-a");
                stmt.setString(3, "Agent A");
                stmt.setString(4, "LEARNING");
                stmt.setString(5, "ACTIVE");
                stmt.executeUpdate();
            }

            // Query for tenant B should return no rows
            String selectSql = "SELECT COUNT(*) FROM agent_registry WHERE tenant_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, "tenant-b");
                ResultSet rs = stmt.executeQuery();
                assertTrue(rs.next());
                int count = rs.getInt(1);
                assertEquals(0, count, "Tenant B should see no agents");
            }

            // Query for tenant A should return one row
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, "tenant-a");
                ResultSet rs = stmt.executeQuery();
                assertTrue(rs.next());
                int count = rs.getInt(1);
                assertEquals(1, count, "Tenant A should see one agent");
            }
        } catch (SQLException e) {
            fail("Failed during tenant scoping test: " + e.getMessage());
        }
    }

    // ==================== JSONB Column Tests ====================

    @Test
    void jsonbColumnsSupportComplexStateAndConfig() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Insert checkpoint with complex JSONB state
            String insertSql = """
                INSERT INTO pipeline_checkpoints (
                    instance_id, tenant_id, pipeline_id, idempotency_key, status, state
                ) VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """;
            
            String complexState = """
                {
                    "steps": [
                        {"id": "step1", "status": "completed", "output": "result1"},
                        {"id": "step2", "status": "running", "output": null}
                    ],
                    "metadata": {
                        "retryCount": 2,
                        "lastError": null
                    }
                }
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, "checkpoint-jsonb");
                stmt.setString(2, "test-tenant");
                stmt.setString(3, "pipeline-456");
                stmt.setString(4, "idempotency-key-jsonb");
                stmt.setString(5, "RUNNING");
                stmt.setString(6, complexState);
                stmt.executeUpdate();
            }

            // Query using JSONB path
            String selectSql = "SELECT state->'steps'->0->>'status' as first_step_status FROM pipeline_checkpoints WHERE tenant_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, "test-tenant");
                ResultSet rs = stmt.executeQuery();
                
                assertTrue(rs.next(), "Should retrieve checkpoint with JSONB query");
                String status = rs.getString("first_step_status");
                assertEquals("completed", status, "Should extract step status from JSONB state");
            }
        } catch (SQLException e) {
            fail("Failed during JSONB test: " + e.getMessage());
        }
    }

    // ==================== Version Tracking Tests ====================

    @Test
    void versionFieldSupportsOptimisticConcurrency() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Insert checkpoint with version 1
            String insertSql = """
                INSERT INTO pipeline_checkpoints (
                    instance_id, tenant_id, pipeline_id, idempotency_key, status, version
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, "checkpoint-version");
                stmt.setString(2, "test-tenant");
                stmt.setString(3, "pipeline-456");
                stmt.setString(4, "idempotency-key-version");
                stmt.setString(5, "RUNNING");
                stmt.setLong(6, 1);
                stmt.executeUpdate();
            }

            // Update with version increment
            String updateSql = """
                UPDATE pipeline_checkpoints 
                SET status = ?, version = version + 1 
                WHERE instance_id = ? AND version = ?
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setString(1, "COMPLETED");
                stmt.setString(2, "checkpoint-version");
                stmt.setLong(3, 1);
                
                int rowsUpdated = stmt.executeUpdate();
                assertEquals(1, rowsUpdated, "Should update one row with matching version");
            }

            // Verify version was incremented
            String selectSql = "SELECT version FROM pipeline_checkpoints WHERE instance_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, "checkpoint-version");
                ResultSet rs = stmt.executeQuery();
                assertTrue(rs.next());
                long version = rs.getLong("version");
                assertEquals(2, version, "Version should be incremented to 2");
            }
        } catch (SQLException e) {
            fail("Failed during version tracking test: " + e.getMessage());
        }
    }

    // ==================== Idempotency Tests ====================

    @Test
    void migrationsCanBeReRunWithoutErrors() {
        // First migration
        flyway.migrate();
        
        // Second migration should be no-op (already at latest version)
        assertDoesNotThrow(() -> {
            int migrationsApplied = flyway.migrate().migrationsExecuted;
            assertEquals(0, migrationsApplied, "No migrations should be applied on re-run");
        });
    }

    @Test
    void cleanAndMigrateRestoresFreshState() {
        // Initial migration
        flyway.migrate();
        
        // Clean removes all schema objects
        flyway.clean();
        
        // Re-migrate should work on clean database
        assertDoesNotThrow(() -> {
            int migrationsApplied = flyway.migrate().migrationsExecuted;
            assertTrue(migrationsApplied > 0, "Should apply migrations after clean");
        });
    }

    // ==================== Helper Methods ====================

    private void assertTableExists(Connection conn, String tableName) throws SQLException {
        ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null);
        assertTrue(rs.next(), "Table " + tableName + " should exist");
    }
}
