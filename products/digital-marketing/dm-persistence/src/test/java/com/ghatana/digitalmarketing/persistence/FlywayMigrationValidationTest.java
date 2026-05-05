package com.ghatana.digitalmarketing.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1-006: Flyway migration validation test.
 *
 * <p>Validates that all DMOS migrations can be successfully run from an empty database
 * and that upgrade migrations work correctly from realistic previous versions.</p>
 *
 * <p>Test strategy:</p>
 * <ul>
 *   <li>Run all migrations from empty DB (fresh install)</li>
 *   <li>Run migrations from specific previous versions (upgrade path)</li>
 *   <li>Verify migration count matches expected</li>
 * </ul>
 */
@DisplayName("P1-006: Flyway Migration Validation Tests")
@Testcontainers
class FlywayMigrationValidationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("dmos_test")
        .withUsername("test")
        .withPassword("test");

    private static final int EXPECTED_MIGRATION_COUNT = 29; // V1 through V29

    @Test
    @DisplayName("P1-006: Fresh migration from empty database succeeds")
    void freshMigrationFromEmptyDatabaseSucceeds() {
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load();

        // When: Run all migrations from empty database
        var migrationResult = flyway.migrate();

        // Then: All migrations should succeed
        assertThat(migrationResult.success).as("P1-006: Fresh migration should succeed").isTrue();
        assertThat(migrationResult.migrationsExecuted)
            .as("P1-006: All migrations should be executed")
            .hasSize(EXPECTED_MIGRATION_COUNT);
    }

    @Test
    @DisplayName("P1-006: Migration from V10 to current succeeds")
    void migrationFromV10ToCurrentSucceeds() {
        // Given: Database at V10 (seed_demo_data)
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target("10")
            .load();
        flyway.migrate();

        // When: Migrate from V10 to current
        Flyway flywayUpgrade = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load();
        var upgradeResult = flywayUpgrade.migrate();

        // Then: Upgrade should succeed
        assertThat(upgradeResult.success).as("P1-006: Upgrade migration should succeed").isTrue();
        assertThat(upgradeResult.migrationsExecuted)
            .as("P1-006: Expected number of upgrade migrations should be executed")
            .hasSize(EXPECTED_MIGRATION_COUNT - 10);
    }

    @Test
    @DisplayName("P1-006: Migration from V20 to current succeeds")
    void migrationFromV20ToCurrentSucceeds() {
        // Given: Database at V20 (website_audit_reports)
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target("20")
            .load();
        flyway.migrate();

        // When: Migrate from V20 to current
        Flyway flywayUpgrade = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load();
        var upgradeResult = flywayUpgrade.migrate();

        // Then: Upgrade should succeed
        assertThat(upgradeResult.success).as("P1-006: Upgrade migration should succeed").isTrue();
        assertThat(upgradeResult.migrationsExecuted)
            .as("P1-006: Expected number of upgrade migrations should be executed")
            .hasSize(EXPECTED_MIGRATION_COUNT - 20);
    }

    @Test
    @DisplayName("P1-006: Migration info shows correct version history")
    void migrationInfoShowsCorrectVersionHistory() {
        // Given: All migrations run
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load();
        flyway.migrate();

        // When: Query migration info
        var info = flyway.info();

        // Then: Migration history should be complete
        assertThat(info.success)
            .as("P1-006: All migrations should be in success state")
            .isTrue();
        assertThat(info.pending())
            .as("P1-006: No pending migrations should remain")
            .isEmpty();
        assertThat(info.applied())
            .as("P1-006: All migrations should be applied")
            .hasSize(EXPECTED_MIGRATION_COUNT);
    }

    @Test
    @DisplayName("P1-006: Repeated migration is idempotent")
    void repeatedMigrationIsIdempotent() {
        // Given: All migrations run once
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load();
        flyway.migrate();

        // When: Run migrations again (should be no-op)
        var repeatResult = flyway.migrate();

        // Then: No migrations should be executed
        assertThat(repeatResult.success).as("P1-006: Repeated migration should succeed").isTrue();
        assertThat(repeatResult.migrationsExecuted)
            .as("P1-006: No migrations should be executed on repeat")
            .isEmpty();
    }
}
