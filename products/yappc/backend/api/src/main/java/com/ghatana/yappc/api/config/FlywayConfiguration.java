/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Flyway database migration configuration for YAPPC.
 *
 * <p>Manages schema versioning and migrations using Flyway. Migrations are located
 * in {@code src/main/resources/db/migration/} and follow the naming convention:
 * <ul>
 *   <li>{@code V1__Initial_schema.sql} — versioned migrations</li>
 *   <li>{@code R__Repeatable_migration.sql} — repeatable migrations</li>
 * </ul>
 *
 * <p>Migration execution happens automatically on application startup via
 * {@link #runMigrations(DataSource)}.
 *
 * @doc.type class
 * @doc.purpose Database migration management
 * @doc.layer infrastructure
 * @doc.pattern Configuration
 */
public class FlywayConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(FlywayConfiguration.class);

    private static final String MIGRATION_LOCATION = "classpath:db/migration";
    private static final String MIGRATION_TABLE = "flyway_schema_history";

    /**
     * Creates and configures a Flyway instance for the given datasource.
     *
     * @param dataSource the datasource to run migrations against
     * @return configured Flyway instance
     */
    public static Flyway createFlyway(DataSource dataSource) {
        logger.info("Configuring Flyway with migration location: {}", MIGRATION_LOCATION);

        return Flyway.configure()
                .dataSource(dataSource)
                .locations(MIGRATION_LOCATION)
                .table(MIGRATION_TABLE)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .outOfOrder(false)
                .cleanDisabled(true) // Safety: prevent accidental data loss
                .load();
    }

    /**
     * Runs database migrations on application startup.
     *
     * <p>This method should be called during application initialization,
     * before any database operations are performed.
     *
     * @param dataSource the datasource to migrate
     * @throws RuntimeException if migration fails
     */
    public static void runMigrations(DataSource dataSource) {
        logger.info("Starting database migrations...");

        try {
            Flyway flyway = createFlyway(dataSource);

            // Log current migration status
            var info = flyway.info();
            var current = info.current();
            if (current != null) {
                logger.info("Current database version: {}", current.getVersion());
            } else {
                logger.info("Database is empty, will apply baseline + migrations");
            }

            // Run migrations
            var result = flyway.migrate();

            if (result.migrationsExecuted > 0) {
                logger.info("Successfully applied {} migration(s)", result.migrationsExecuted);
                logger.info("Database is now at version: {}", result.targetSchemaVersion);
            } else {
                logger.info("Database is up to date at version: {}",
                        current != null ? current.getVersion() : "baseline");
            }

        } catch (Exception e) {
            logger.error("Database migration failed", e);
            throw new RuntimeException("Failed to run database migrations", e);
        }
    }

    /**
     * Validates that all migrations have been applied.
     *
     * <p>Useful for health checks and startup validation.
     *
     * @param dataSource the datasource to validate
     * @return true if all migrations are applied, false otherwise
     */
    public static boolean validateMigrations(DataSource dataSource) {
        try {
            Flyway flyway = createFlyway(dataSource);
            var validation = flyway.validateWithResult();
            
            if (!validation.validationSuccessful) {
                logger.warn("Migration validation failed: {}", validation.errorDetails);
                return false;
            }
            
            logger.debug("Migration validation successful");
            return true;
            
        } catch (Exception e) {
            logger.error("Migration validation error", e);
            return false;
        }
    }

    /**
     * Gets migration info for monitoring/debugging.
     *
     * @param dataSource the datasource to inspect
     * @return migration info summary
     */
    public static String getMigrationInfo(DataSource dataSource) {
        try {
            Flyway flyway = createFlyway(dataSource);
            var info = flyway.info();
            var current = info.current();
            var pending = info.pending();

            return String.format(
                    "Current: %s, Pending: %d, Applied: %d",
                    current != null ? current.getVersion() : "none",
                    pending.length,
                    info.applied().length
            );
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
