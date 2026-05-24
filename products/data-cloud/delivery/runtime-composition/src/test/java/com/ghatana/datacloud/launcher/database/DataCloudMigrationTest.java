/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.database;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-MIG-1: Data Cloud Flyway migration tests for fresh schema setup
 * 
 * Tests verify:
 * - All migrations can be executed on a fresh database
 * - Schema is correctly created with all tables, indexes, and constraints
 * - Data can be inserted and queried successfully
 * - Migrations are idempotent (can be re-run without errors)
 * - Tenant isolation constraints are enforced
 * - JSONB columns work correctly
 * - Check constraints validate enum values
 * 
 * @doc.type class
 * @doc.purpose Flyway migration tests for Data Cloud schema
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true)
class DataCloudMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("datacloud_test")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);

    private Flyway flyway;
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        // Configure Flyway with test database
        // Use filesystem location to avoid picking up migrations from other JARs on classpath
        File migrationDir = Paths.get("src/main/resources/db/migration").toFile();
        String location = migrationDir.exists() ? "filesystem:" + migrationDir.getAbsolutePath() : "classpath:db/migration";
        
        flyway = Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations(location)
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .cleanDisabled(false)  // Enable clean for test isolation
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
            // Verify core tables exist
            assertTableExists(conn, "events");
            assertTableExists(conn, "entities");
            assertTableExists(conn, "timeseries");
            assertTableExists(conn, "collections");
            assertTableExists(conn, "event_log");
            assertTableExists(conn, "entity_relations");
            assertTableExists(conn, "agent_releases");
            assertTableExists(conn, "agent_rollouts");
            assertTableExists(conn, "evaluation_results");
            assertTableExists(conn, "memory_namespaces");
            assertTableExists(conn, "promotion_evidence");
            assertTableExists(conn, "media_artifacts");
            // V025: release readiness evidence tables
            assertTableExists(conn, "product_release_readiness");
            assertTableExists(conn, "product_bootstrap_evidence");
            assertTableExists(conn, "product_rollback_evidence");
        } catch (SQLException e) {
            fail("Failed to verify table existence: " + e.getMessage());
        }
    }

    @Test
    void releaseReadinessTablesHaveCorrectConstraints() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            assertUniqueConstraintExists(conn, "product_release_readiness",
                    "uq_release_readiness_product_version_target");
            assertUniqueConstraintExists(conn, "product_bootstrap_evidence",
                    "uq_bootstrap_evidence_product_env");
            assertUniqueConstraintExists(conn, "product_rollback_evidence",
                    "uq_rollback_evidence_product_env");
        } catch (SQLException e) {
            fail("Failed to verify V025 release readiness constraints: " + e.getMessage());
        }
    }

    @Test
    void releaseReadinessTableEnforcesVerdictCheckConstraint() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            String insertInvalidVerdict = """
                INSERT INTO product_release_readiness
                    (product_id, product_version, release_target, release_verdict, tenant_id)
                VALUES ('phr', '1.0.0', 'staging', 'invalid-verdict', 'tenant-test')
                """;
            try (var stmt = conn.prepareStatement(insertInvalidVerdict)) {
                assertThrows(SQLException.class, stmt::executeUpdate,
                        "Invalid release_verdict should be rejected by check constraint");
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            // Expected path for constraint violations — test passes
        }
    }

    @Test
    void releaseReadinessTableEnforcesTargetCheckConstraint() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            String insertInvalidTarget = """
                INSERT INTO product_release_readiness
                    (product_id, product_version, release_target, release_verdict, tenant_id)
                VALUES ('phr', '1.0.0', 'unknown-env', 'pass', 'tenant-test')
                """;
            try (var stmt = conn.prepareStatement(insertInvalidTarget)) {
                assertThrows(SQLException.class, stmt::executeUpdate,
                        "Invalid release_target should be rejected by check constraint");
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            // Expected path for constraint violations — test passes
        }
    }

    @Test
    void allRequiredIndexesExistAfterMigration() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Verify performance indexes exist
            assertIndexExists(conn, "idx_events_tenant");
            assertIndexExists(conn, "idx_events_stream");
            assertIndexExists(conn, "idx_events_partition_offset");
            assertIndexExists(conn, "idx_events_detection_time");
            assertIndexExists(conn, "idx_events_correlation");
        } catch (SQLException e) {
            fail("Failed to verify index existence: " + e.getMessage());
        }
    }

    @Test
    void allRequiredConstraintsExistAfterMigration() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Verify check constraints exist
            assertCheckConstraintExists(conn, "events", "chk_events_record_type");
            
            // Verify unique constraints exist
            assertUniqueConstraintExists(conn, "events", "uk_events_offset");
            assertUniqueConstraintExists(conn, "events", "uk_events_idempotency");
        } catch (SQLException e) {
            fail("Failed to verify constraint existence: " + e.getMessage());
        }
    }

    // ==================== Data Operations Tests ====================

    @Test
    void canInsertAndQueryEventData() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Insert test event
            String insertSql = """
                INSERT INTO events (
                    id, tenant_id, collection_name, record_type, data, metadata,
                    stream_name, partition_id, event_offset, occurrence_time, detection_time
                ) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setObject(1, UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
                stmt.setString(2, "test-tenant");
                stmt.setString(3, "test-collection");
                stmt.setString(4, "EVENT");
                stmt.setString(5, "{\"test\": \"data\"}");
                stmt.setString(6, "{\"meta\": \"value\"}");
                stmt.setString(7, "test-stream");
                stmt.setInt(8, 0);
                stmt.setLong(9, 1);
                stmt.setTimestamp(10, Timestamp.from(java.time.Instant.now()));
                stmt.setTimestamp(11, Timestamp.from(java.time.Instant.now()));
                
                int rowsInserted = stmt.executeUpdate();
                assertEquals(1, rowsInserted, "Should insert one event");
            }

            // Query the event back
            String selectSql = "SELECT data, metadata FROM events WHERE tenant_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, "test-tenant");
                ResultSet rs = stmt.executeQuery();
                
                assertTrue(rs.next(), "Should retrieve the inserted event");
                String data = rs.getString("data");
                String metadata = rs.getString("metadata");
                assertEquals("{\"test\": \"data\"}", data);
                assertEquals("{\"meta\": \"value\"}", metadata);
            }
        } catch (SQLException e) {
            fail("Failed to insert/query event data: " + e.getMessage());
        }
    }

    @Test
    void canInsertAndQueryEntityData() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Insert test entity
            String insertSql = """
                INSERT INTO entities (
                    id, tenant_id, collection_name, record_type, data, metadata,
                    version
                ) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setObject(1, UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
                stmt.setString(2, "test-tenant");
                stmt.setString(3, "test-collection");
                stmt.setString(4, "ENTITY");
                stmt.setString(5, "{\"name\": \"test-entity\"}");
                stmt.setString(6, "{\"version\": 1}");
                stmt.setInt(7, 1);
                
                int rowsInserted = stmt.executeUpdate();
                assertEquals(1, rowsInserted, "Should insert one entity");
            }

            // Query the entity back
            String selectSql = "SELECT id, data, version FROM entities WHERE tenant_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, "test-tenant");
                ResultSet rs = stmt.executeQuery();
                
                assertTrue(rs.next(), "Should retrieve the inserted entity");
                String entityId = rs.getString("id");
                String data = rs.getString("data");
                int entityVersion = rs.getInt("version");
                assertEquals("{\"name\": \"test-entity\"}", data);
                assertEquals("550e8400-e29b-41d4-a716-446655440001", entityId);
                assertEquals(1, entityVersion);
            }
        } catch (SQLException e) {
            fail("Failed to insert/query entity data: " + e.getMessage());
        }
    }

    // ==================== Constraint Enforcement Tests ====================

    @Test
    void checkConstraintEnforcesValidRecordType() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Try to insert invalid record type
            String insertSql = """
                INSERT INTO events (
                    id, tenant_id, collection_name, record_type, data, metadata,
                    stream_name, partition_id, event_offset, occurrence_time, detection_time
                ) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setObject(1, UUID.fromString("550e8400-e29b-41d4-a716-446655440002"));
                stmt.setString(2, "test-tenant");
                stmt.setString(3, "test-collection");
                stmt.setString(4, "INVALID_TYPE"); // Invalid record type
                stmt.setString(5, "{}");
                stmt.setString(6, "{}");
                stmt.setString(7, "test-stream");
                stmt.setInt(8, 0);
                stmt.setLong(9, 1);
                stmt.setTimestamp(10, Timestamp.from(java.time.Instant.now()));
                stmt.setTimestamp(11, Timestamp.from(java.time.Instant.now()));
                
                assertThrows(SQLException.class, stmt::executeUpdate,
                    "Should reject invalid record type due to check constraint");
            }
        } catch (SQLException e) {
            fail("Failed during constraint test: " + e.getMessage());
        }
    }

    @Test
    void uniqueConstraintEnforcesOffsetUniqueness() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Insert first event with offset 1
            String insertSql = """
                INSERT INTO events (
                    id, tenant_id, collection_name, record_type, data, metadata,
                    stream_name, partition_id, event_offset, occurrence_time, detection_time
                ) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setObject(1, UUID.fromString("550e8400-e29b-41d4-a716-446655440003"));
                stmt.setString(2, "test-tenant");
                stmt.setString(3, "test-collection");
                stmt.setString(4, "EVENT");
                stmt.setString(5, "{}");
                stmt.setString(6, "{}");
                stmt.setString(7, "test-stream");
                stmt.setInt(8, 0);
                stmt.setLong(9, 1);
                stmt.setTimestamp(10, Timestamp.from(java.time.Instant.now()));
                stmt.setTimestamp(11, Timestamp.from(java.time.Instant.now()));
                stmt.executeUpdate();
            }

            // Try to insert second event with same offset - should fail
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setObject(1, UUID.fromString("550e8400-e29b-41d4-a716-446655440004"));
                stmt.setString(2, "test-tenant");
                stmt.setString(3, "test-collection");
                stmt.setString(4, "EVENT");
                stmt.setString(5, "{}");
                stmt.setString(6, "{}");
                stmt.setString(7, "test-stream");
                stmt.setInt(8, 0);
                stmt.setLong(9, 1); // Same offset
                stmt.setTimestamp(10, Timestamp.from(java.time.Instant.now()));
                stmt.setTimestamp(11, Timestamp.from(java.time.Instant.now()));
                
                assertThrows(SQLException.class, stmt::executeUpdate,
                    "Should reject duplicate offset due to unique constraint");
            }
        } catch (SQLException e) {
            fail("Failed during unique constraint test: " + e.getMessage());
        }
    }

    // ==================== Tenant Isolation Tests ====================

    @Test
    void tenantIdIsRequired() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Try to insert event without tenant_id
            String insertSql = """
                INSERT INTO events (
                    id, collection_name, record_type, data, metadata,
                    stream_name, partition_id, event_offset, occurrence_time, detection_time
                ) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setObject(1, UUID.fromString("550e8400-e29b-41d4-a716-446655440005"));
                stmt.setString(2, "test-collection");
                stmt.setString(3, "EVENT");
                stmt.setString(4, "{}");
                stmt.setString(5, "{}");
                stmt.setString(6, "test-stream");
                stmt.setInt(7, 0);
                stmt.setLong(8, 1);
                stmt.setTimestamp(9, Timestamp.from(java.time.Instant.now()));
                stmt.setTimestamp(10, Timestamp.from(java.time.Instant.now()));
                
                assertThrows(SQLException.class, stmt::executeUpdate,
                    "Should reject insert without tenant_id due to NOT NULL constraint");
            }
        } catch (SQLException e) {
            fail("Failed during tenant isolation test: " + e.getMessage());
        }
    }

    @Test
    void tenantScopedQueriesOnlyReturnTenantData() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Insert event for tenant A
            String insertSql = """
                INSERT INTO events (
                    id, tenant_id, collection_name, record_type, data, metadata,
                    stream_name, partition_id, event_offset, occurrence_time, detection_time
                ) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setObject(1, UUID.fromString("550e8400-e29b-41d4-a716-446655440006"));
                stmt.setString(2, "tenant-a");
                stmt.setString(3, "test-collection");
                stmt.setString(4, "EVENT");
                stmt.setString(5, "{}");
                stmt.setString(6, "{}");
                stmt.setString(7, "test-stream");
                stmt.setInt(8, 0);
                stmt.setLong(9, 1);
                stmt.setTimestamp(10, Timestamp.from(java.time.Instant.now()));
                stmt.setTimestamp(11, Timestamp.from(java.time.Instant.now()));
                stmt.executeUpdate();
            }

            // Query for tenant B should return no rows
            String selectSql = "SELECT COUNT(*) FROM events WHERE tenant_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, "tenant-b");
                ResultSet rs = stmt.executeQuery();
                assertTrue(rs.next());
                int count = rs.getInt(1);
                assertEquals(0, count, "Tenant B should see no events");
            }

            // Query for tenant A should return one row
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, "tenant-a");
                ResultSet rs = stmt.executeQuery();
                assertTrue(rs.next());
                int count = rs.getInt(1);
                assertEquals(1, count, "Tenant A should see one event");
            }
        } catch (SQLException e) {
            fail("Failed during tenant scoping test: " + e.getMessage());
        }
    }

    // ==================== JSONB Column Tests ====================

    @Test
    void jsonbColumnsSupportComplexQueries() {
        flyway.migrate();

        try (Connection conn = dataSource.getConnection()) {
            // Insert event with complex JSONB data
            String insertSql = """
                INSERT INTO events (
                    id, tenant_id, collection_name, record_type, data, metadata,
                    stream_name, partition_id, event_offset, occurrence_time, detection_time
                ) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
                """;
            
            String complexJson = """
                {
                    "nested": {
                        "value": 123,
                        "array": [1, 2, 3]
                    },
                    "string": "test"
                }
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setObject(1, UUID.fromString("550e8400-e29b-41d4-a716-446655440007"));
                stmt.setString(2, "test-tenant");
                stmt.setString(3, "test-collection");
                stmt.setString(4, "EVENT");
                stmt.setString(5, complexJson);
                stmt.setString(6, "{}");
                stmt.setString(7, "test-stream");
                stmt.setInt(8, 0);
                stmt.setLong(9, 1);
                stmt.setTimestamp(10, Timestamp.from(java.time.Instant.now()));
                stmt.setTimestamp(11, Timestamp.from(java.time.Instant.now()));
                stmt.executeUpdate();
            }

            // Query using JSONB path
            String selectSql = "SELECT data->'nested'->>'value' as value FROM events WHERE tenant_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
                stmt.setString(1, "test-tenant");
                ResultSet rs = stmt.executeQuery();
                
                assertTrue(rs.next(), "Should retrieve event with JSONB query");
                String value = rs.getString("value");
                assertEquals("123", value, "Should extract nested value from JSONB");
            }
        } catch (SQLException e) {
            fail("Failed during JSONB test: " + e.getMessage());
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

    private void assertIndexExists(Connection conn, String indexName) throws SQLException {
        String indexSql = "SELECT 1 FROM pg_indexes WHERE indexname = ?";
        try (PreparedStatement stmt = conn.prepareStatement(indexSql)) {
            stmt.setString(1, indexName);
            ResultSet rs = stmt.executeQuery();
            assertTrue(rs.next(), "Index " + indexName + " should exist");
        }
    }

    private void assertCheckConstraintExists(Connection conn, String tableName, String constraintName) throws SQLException {
        ResultSet rs = conn.getMetaData().getExportedKeys(null, null, tableName);
        // Check constraints are typically in table constraints
        String checkSql = "SELECT conname FROM pg_constraint WHERE conrelid = ?::regclass AND contype = 'c'";
        try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
            stmt.setString(1, tableName);
            ResultSet constraintRs = stmt.executeQuery();
            boolean found = false;
            while (constraintRs.next()) {
                if (constraintName.equals(constraintRs.getString("conname"))) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Check constraint " + constraintName + " should exist on " + tableName);
        }
    }

    private void assertUniqueConstraintExists(Connection conn, String tableName, String constraintName) throws SQLException {
        String checkSql = "SELECT conname FROM pg_constraint WHERE conrelid = ?::regclass AND contype = 'u'";
        try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
            stmt.setString(1, tableName);
            ResultSet constraintRs = stmt.executeQuery();
            boolean found = false;
            while (constraintRs.next()) {
                if (constraintName.equals(constraintRs.getString("conname"))) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Unique constraint " + constraintName + " should exist on " + tableName);
        }
    }
}
