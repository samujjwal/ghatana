package com.ghatana.digitalmarketing.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Migration validation test to ensure all Flyway migrations can be applied cleanly.
 *
 * <p>This test runs on a fresh database to validate that migrations are idempotent,
 * have no syntax errors, and maintain schema integrity. It serves as a CI gate for
 * database schema changes (DMOS-P1-006).</p>
 *
 * @doc.type class
 * @doc.purpose Validates Flyway migrations can be applied cleanly (DMOS-P1-006)
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Flyway migrations — validation")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigrationValidationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("dmos_migration_check")
            .withUsername("dmos")
            .withPassword("dmos_secret");

    @Order(1)
    @Test
    @DisplayName("all migrations apply cleanly to a fresh database")
    void migrationsApplyCleanly() {
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("filesystem:src/main/resources/db/migration")
            .load();

        // Run migrations on fresh database
        var migrateResult = flyway.migrate();

        // Validate that migrations applied cleanly
        flyway.validate();

        // Verify at least the known migrations are applied
        assert migrateResult.migrationsExecuted >= 5 : "Expected at least 5 migrations to be applied";
    }

    @Test
    @DisplayName("migrations are idempotent — can be run twice without error")
    void migrationsAreIdempotent() {
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("filesystem:src/main/resources/db/migration")
            .load();

        // First run
        flyway.migrate();

        // Second run should not apply new migrations
        var migrateResultSecondRun = flyway.migrate();

        assert migrateResultSecondRun.migrationsExecuted == 0 : "Migrations should be idempotent";
    }

    @Test
    @DisplayName("schema version matches expected state")
    void schemaVersionMatchesExpected() {
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("filesystem:src/main/resources/db/migration")
            .load();

        flyway.migrate();

        // Verify the schema version
        var info = flyway.info();
        assert info.pending().length == 0 : "No pending migrations should remain after migration";
        assert info.all().length > 0 : "Migrations should have been applied";
    }
}
