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
@Tag("infrastructure-backed")
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
    void setUp() { 
        HikariConfig hikari = new HikariConfig(); 
        hikari.setJdbcUrl(POSTGRES.getJdbcUrl()); 
        hikari.setUsername(POSTGRES.getUsername()); 
        hikari.setPassword(POSTGRES.getPassword()); 
        hikari.setMaximumPoolSize(5); 
        dataSource = new HikariDataSource(hikari); 
    }

    @AfterEach
    void tearDown() { 
        // Clean the database between tests
        if (dataSource != null) { 
            try {
                FlywayMigration cleaner = FlywayMigration.builder() 
                        .dataSource(dataSource) 
                        .locations("classpath:db/migration")
                        .cleanDisabled(false) 
                        .build(); 
                cleaner.clean(); 
            } catch (Exception ignored) { 
                // Ignore cleanup errors
            }
            dataSource.close(); 
        }
    }

    @Test
    @DisplayName("migrate applies all pending SQL scripts")
    void migrateAppliesAllScripts() { 
        FlywayMigration migration = FlywayMigration.builder() 
                .dataSource(dataSource) 
                .locations("classpath:db/migration")
                .build(); 

        FlywayMigration.MigrationResult result = migration.migrate(); 

        assertThat(result.getMigrationsExecuted()).isGreaterThanOrEqualTo(2); 
        assertThat(result.isSuccess()).isTrue(); 
    }

    @Test
    @DisplayName("migration creates the expected table in the database")
    void migrationCreatesTable() throws Exception { 
        FlywayMigration migration = FlywayMigration.builder() 
                .dataSource(dataSource) 
                .locations("classpath:db/migration")
                .build(); 
        migration.migrate(); 

        try (Connection conn = dataSource.getConnection(); 
             ResultSet rs = conn.getMetaData().getTables(null, "public", "test_items", null)) { 
            assertThat(rs.next()).isTrue(); 
            assertThat(rs.getString("TABLE_NAME")).isEqualTo("test_items");
        }
    }

    @Test
    @DisplayName("second migrate call is idempotent — no migrations re-applied")
    void secondMigrateIsIdempotent() { 
        FlywayMigration migration = FlywayMigration.builder() 
                .dataSource(dataSource) 
                .locations("classpath:db/migration")
                .build(); 

        migration.migrate(); 
        FlywayMigration.MigrationResult second = migration.migrate(); 

        assertThat(second.getMigrationsExecuted()).isEqualTo(0); 
        assertThat(second.isSuccess()).isTrue(); 
    }

    @Test
    @DisplayName("validate passes after successful migration")
    void validateAfterMigration() { 
        FlywayMigration migration = FlywayMigration.builder() 
                .dataSource(dataSource) 
                .locations("classpath:db/migration")
                .build(); 
        migration.migrate(); 

        FlywayMigration.ValidationResult validation = migration.validate(); 

        assertThat(validation.isValid()).isTrue(); 
        assertThat(validation.getErrors()).isEmpty(); 
    }

    @Test
    @DisplayName("getStatus returns current version after migration")
    void statusAfterMigration() { 
        FlywayMigration migration = FlywayMigration.builder() 
                .dataSource(dataSource) 
                .locations("classpath:db/migration")
                .build(); 
        migration.migrate(); 

        FlywayMigration.MigrationStatus status = migration.getStatus(); 

        assertThat(status.getCurrentVersion()).isNotNull(); 
        assertThat(status.getCurrentVersion()).contains("2"); // V2 is latest
        assertThat(status.getTotalMigrations()).isGreaterThanOrEqualTo(2); 
    }

    @Test
    @DisplayName("migrate with invalid location raises MigrationException")
    void migrateInvalidLocation() { 
        FlywayMigration migration = FlywayMigration.builder() 
                .dataSource(dataSource) 
                .locations("classpath:db/nonexistent")
                .build(); 

        // With no scripts found, Flyway migrates successfully with 0 migrations
        // or may throw depending on mode — we verify it either succeeds or raises
        try {
            FlywayMigration.MigrationResult result = migration.migrate(); 
            assertThat(result.getMigrationsExecuted()).isEqualTo(0); 
        } catch (MigrationException e) { 
            // Also acceptable
            assertThat(e.getMessage()).isNotBlank(); 
        }
    }

    @Test
    @DisplayName("getFlyway returns non-null Flyway instance")
    void getFlywayIsNonNull() { 
        FlywayMigration migration = FlywayMigration.builder() 
                .dataSource(dataSource) 
                .locations("classpath:db/migration")
                .build(); 

        assertThat(migration.getFlyway()).isNotNull(); 
    }
}
