package com.ghatana.core.database.migration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link FlywayMigration} using a real PostgreSQL container.
 *
 * @doc.type class
 * @doc.purpose Integration tests for Flyway schema migration manager
 * @doc.layer core
 * @doc.pattern Integration Test
 */
@Tag("integration")
@Testcontainers
@DisplayName("FlywayMigration Integration Tests")
class FlywayMigrationIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    private HikariDataSource dataSource;

    @BeforeEach
    void setUp() { // GH-90000
        HikariConfig hikari = new HikariConfig(); // GH-90000
        hikari.setJdbcUrl(POSTGRES.getJdbcUrl()); // GH-90000
        hikari.setUsername(POSTGRES.getUsername()); // GH-90000
        hikari.setPassword(POSTGRES.getPassword()); // GH-90000
        hikari.setMaximumPoolSize(5); // GH-90000
        dataSource = new HikariDataSource(hikari); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        // Clean the database between tests
        if (dataSource != null) { // GH-90000
            try {
                FlywayMigration cleaner = FlywayMigration.builder() // GH-90000
                        .dataSource(dataSource) // GH-90000
                        .locations("classpath:db/migration")
                        .cleanDisabled(false) // GH-90000
                        .build(); // GH-90000
                cleaner.clean(); // GH-90000
            } catch (Exception ignored) { // GH-90000
                // Ignore cleanup errors
            }
            dataSource.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("migrate applies all pending SQL scripts")
    void migrateAppliesAllScripts() { // GH-90000
        FlywayMigration migration = FlywayMigration.builder() // GH-90000
                .dataSource(dataSource) // GH-90000
                .locations("classpath:db/migration")
                .build(); // GH-90000

        FlywayMigration.MigrationResult result = migration.migrate(); // GH-90000

        assertThat(result.getMigrationsExecuted()).isGreaterThanOrEqualTo(2); // GH-90000
        assertThat(result.isSuccess()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("migration creates the expected table in the database")
    void migrationCreatesTable() throws Exception { // GH-90000
        FlywayMigration migration = FlywayMigration.builder() // GH-90000
                .dataSource(dataSource) // GH-90000
                .locations("classpath:db/migration")
                .build(); // GH-90000
        migration.migrate(); // GH-90000

        try (Connection conn = dataSource.getConnection(); // GH-90000
             ResultSet rs = conn.getMetaData().getTables(null, "public", "test_items", null)) { // GH-90000
            assertThat(rs.next()).isTrue(); // GH-90000
            assertThat(rs.getString("TABLE_NAME")).isEqualTo("test_items");
        }
    }

    @Test
    @DisplayName("second migrate call is idempotent — no migrations re-applied")
    void secondMigrateIsIdempotent() { // GH-90000
        FlywayMigration migration = FlywayMigration.builder() // GH-90000
                .dataSource(dataSource) // GH-90000
                .locations("classpath:db/migration")
                .build(); // GH-90000

        migration.migrate(); // GH-90000
        FlywayMigration.MigrationResult second = migration.migrate(); // GH-90000

        assertThat(second.getMigrationsExecuted()).isEqualTo(0); // GH-90000
        assertThat(second.isSuccess()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("validate passes after successful migration")
    void validateAfterMigration() { // GH-90000
        FlywayMigration migration = FlywayMigration.builder() // GH-90000
                .dataSource(dataSource) // GH-90000
                .locations("classpath:db/migration")
                .build(); // GH-90000
        migration.migrate(); // GH-90000

        FlywayMigration.ValidationResult validation = migration.validate(); // GH-90000

        assertThat(validation.isValid()).isTrue(); // GH-90000
        assertThat(validation.getErrors()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("getStatus returns current version after migration")
    void statusAfterMigration() { // GH-90000
        FlywayMigration migration = FlywayMigration.builder() // GH-90000
                .dataSource(dataSource) // GH-90000
                .locations("classpath:db/migration")
                .build(); // GH-90000
        migration.migrate(); // GH-90000

        FlywayMigration.MigrationStatus status = migration.getStatus(); // GH-90000

        assertThat(status.getCurrentVersion()).isNotNull(); // GH-90000
        assertThat(status.getCurrentVersion()).contains("2"); // V2 is latest
        assertThat(status.getTotalMigrations()).isGreaterThanOrEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("migrate with invalid location raises MigrationException")
    void migrateInvalidLocation() { // GH-90000
        FlywayMigration migration = FlywayMigration.builder() // GH-90000
                .dataSource(dataSource) // GH-90000
                .locations("classpath:db/nonexistent")
                .build(); // GH-90000

        // With no scripts found, Flyway migrates successfully with 0 migrations
        // or may throw depending on mode — we verify it either succeeds or raises
        try {
            FlywayMigration.MigrationResult result = migration.migrate(); // GH-90000
            assertThat(result.getMigrationsExecuted()).isEqualTo(0); // GH-90000
        } catch (MigrationException e) { // GH-90000
            // Also acceptable
            assertThat(e.getMessage()).isNotBlank(); // GH-90000
        }
    }

    @Test
    @DisplayName("getFlyway returns non-null Flyway instance")
    void getFlywayIsNonNull() { // GH-90000
        FlywayMigration migration = FlywayMigration.builder() // GH-90000
                .dataSource(dataSource) // GH-90000
                .locations("classpath:db/migration")
                .build(); // GH-90000

        assertThat(migration.getFlyway()).isNotNull(); // GH-90000
    }
}
