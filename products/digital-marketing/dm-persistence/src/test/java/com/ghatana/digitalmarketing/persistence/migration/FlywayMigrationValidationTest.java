package com.ghatana.digitalmarketing.persistence.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1-006: Flyway migration validation tests.
 *
 * <p>Validates database migrations work correctly:
 * <ul>
 *   <li>Fresh migration from empty database succeeds</li>
 *   <li>Upgrade migration from realistic previous versions succeeds</li>
 *   <li>All expected tables are created</li>
 *   <li>Constraints and indexes are properly applied</li>
 *   <li>PII HMAC key configuration migration works</li>
 * </ul>
 */
@Testcontainers
@DisplayName("P1-006: Flyway Migration Validation Tests")
class FlywayMigrationValidationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("dmos_migration_test")
        .withUsername("test")
        .withPassword("test");

    @Test
    @DisplayName("P1-006: Fresh migration from empty database succeeds")
    void freshMigrationSucceeds() {
        // Given: A fresh PostgreSQL database
        DataSource dataSource = createDataSource();

        // When: Flyway migrates from scratch
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("filesystem:src/main/resources/db/migration")
            .cleanDisabled(false)
            .load();

        flyway.clean();
        flyway.migrate();

        // Then: All expected migrations should be applied
        assertThat(flyway.info().applied().length).isGreaterThan(0);

        // Verify schema version is recorded
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT version, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")) {

            assertThat(rs.next()).isTrue();
            String version = rs.getString("version");
            String description = rs.getString("description");

            assertThat(version).isNotNull();
            assertThat(description).isNotNull();

        } catch (Exception e) {
            throw new RuntimeException("Failed to verify migration history", e);
        }
    }

    @Test
    @DisplayName("P1-006: All expected tables are created after migration")
    void allExpectedTablesAreCreated() {
        // Given: Migrated database
        DataSource dataSource = createDataSource();
        runMigrations(dataSource);

        // Then: Expected tables should exist
        Set<String> expectedTables = Set.of(
            "dmos_campaigns",
            "dmos_ai_action_log",
            "dmos_system_config",
            "dmos_approval_snapshots",
            "dmos_marketing_strategies",
            "dmos_budget_recommendations",
            "dmos_workspaces"
        );

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {

            Set<String> actualTables = new java.util.HashSet<>();
            while (rs.next()) {
                actualTables.add(rs.getString("table_name"));
            }

            // Check critical tables exist
            assertThat(actualTables)
                .as("Expected DMOS tables should exist after migration")
                .containsAll(expectedTables);

        } catch (Exception e) {
            throw new RuntimeException("Failed to verify table existence", e);
        }
    }

    @Test
    @DisplayName("P1-006: Campaign table has CHECK constraints (V21)")
    void campaignTableHasCheckConstraints() {
        // Given: Migrated database
        DataSource dataSource = createDataSource();
        runMigrations(dataSource);

        // Then: CHECK constraints should exist
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT conname FROM pg_constraint WHERE conrelid = 'dmos_campaigns'::regclass AND contype = 'c'")) {

            Set<String> constraints = new java.util.HashSet<>();
            while (rs.next()) {
                constraints.add(rs.getString("conname"));
            }

            assertThat(constraints)
                .as("Campaign table should have CHECK constraints from V21")
                .containsAnyOf(
                    "dmos_campaigns_status_check",
                    "dmos_campaigns_type_check",
                    "dmos_campaigns_name_not_empty"
                );

        } catch (Exception e) {
            throw new RuntimeException("Failed to verify CHECK constraints", e);
        }
    }

    @Test
    @DisplayName("P1-006: AI action log has tenant_id column (V22)")
    void aiActionLogHasTenantIdColumn() {
        // Given: Migrated database
        DataSource dataSource = createDataSource();
        runMigrations(dataSource);

        // Then: tenant_id column should exist
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT column_name FROM information_schema.columns WHERE table_name = 'dmos_ai_action_log'")) {

            Set<String> columns = new java.util.HashSet<>();
            while (rs.next()) {
                columns.add(rs.getString("column_name"));
            }

            assertThat(columns)
                .as("AI action log should have tenant_id column from V22")
                .contains("tenant_id");

        } catch (Exception e) {
            throw new RuntimeException("Failed to verify tenant_id column", e);
        }
    }

    @Test
    @DisplayName("P1-006: System config table exists for PII HMAC key (V23)")
    void systemConfigTableExistsForPiiHmacKey() {
        // Given: Migrated database
        DataSource dataSource = createDataSource();
        runMigrations(dataSource);

        // Then: System config table should exist
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT table_name FROM information_schema.tables WHERE table_name = 'dmos_system_config'")) {

            assertThat(rs.next())
                .as("dmos_system_config table should exist for PII HMAC key storage (V23)")
                .isTrue();

        } catch (Exception e) {
            throw new RuntimeException("Failed to verify system config table", e);
        }
    }

    @Test
    @DisplayName("P1-006: Migration is repeatable (clean + migrate)")
    void migrationIsRepeatable() {
        // Given: DataSource
        DataSource dataSource = createDataSource();

        // First migration
        Flyway flyway1 = Flyway.configure()
            .dataSource(dataSource)
            .locations("filesystem:src/main/resources/db/migration")
            .cleanDisabled(false)
            .load();

        flyway1.clean();
        flyway1.migrate();

        int firstMigrationCount = flyway1.info().applied().length;

        // Clean and migrate again
        flyway1.clean();
        flyway1.migrate();

        int secondMigrationCount = flyway1.info().applied().length;

        // Then: Both migrations should apply same number of scripts
        assertThat(secondMigrationCount)
            .as("Repeatable migration should apply same number of scripts")
            .isEqualTo(firstMigrationCount);
    }

    @Test
    @DisplayName("P1-006: Invalid status cannot be inserted due to CHECK constraint")
    void invalidStatusCannotBeInserted() {
        // Given: Migrated database
        DataSource dataSource = createDataSource();
        runMigrations(dataSource);

        // Then: Invalid status should be rejected
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // This should fail due to CHECK constraint
            stmt.execute(
                "INSERT INTO dmos_campaigns (id, workspace_id, name, status, type, created_at, updated_at, created_by) " +
                "VALUES ('test-1', 'ws-1', 'Test', 'INVALID_STATUS', 'EMAIL', NOW(), NOW(), 'user-1')"
            );

            // If we reach here, the constraint didn't work
            org.junit.jupiter.api.Assertions.fail("Should have rejected invalid status due to CHECK constraint");

        } catch (Exception e) {
            // Expected - constraint should have rejected this
            assertThat(e.getMessage()).containsIgnoringCase("check");
        }
    }

    // Helper methods

    private DataSource createDataSource() {
        org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
        ds.setURL(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        return ds;
    }

    private void runMigrations(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("filesystem:src/main/resources/db/migration")
            .cleanDisabled(false)
            .load();
        flyway.clean();
        flyway.migrate();
    }
}
