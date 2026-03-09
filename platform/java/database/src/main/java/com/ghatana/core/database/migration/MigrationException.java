package com.ghatana.core.database.migration;

/**
 * Unchecked runtime exception for database schema migration failures.
 *
 * <p><b>Purpose</b><br>
 * Wraps Flyway migration errors, validation failures, and schema inconsistencies
 * as unchecked exceptions. Preserves cause chain for debugging migration failures.
 *
 * <p><b>Architecture Role</b><br>
 * Exception class in core/database/migration for migration error translation.
 * Used by:
 * - FlywayMigration - Wrap Flyway exceptions during migrate/validate/repair
 * - Startup Hooks - Detect and handle migration failures at boot
 * - Migration Scripts - Signal custom migration logic failures
 * - Health Checks - Report migration status (pending, failed)
 *
 * <p><b>Migration Failure Scenarios</b><br>
 * - <b>Validation Failures</b>: Checksum mismatch, missing migration, out-of-order
 * - <b>Execution Failures</b>: SQL syntax error, constraint violation, timeout
 * - <b>Connection Failures</b>: Database unreachable during migration
 * - <b>Rollback Failures</b>: Transaction rollback failed, inconsistent state
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Thrown by FlywayMigration on migration failure
 * try {
 *     MigrationResult result = flywayMigration.migrate();
 * } catch (MigrationException e) {
 *     logger.error("Database migration failed", e);
 *     // Prevent application startup
 *     throw new IllegalStateException("Cannot start with failed migrations", e);
 * }
 * 
 * // Throw from custom migration script
 * public class V001__InitSchema implements JavaMigration {
 *     @Override
 *     public void migrate(Context context) throws Exception {
 *         try {
 *             // Execute migration logic
 *             createTables(context.getConnection());
 *         } catch (SQLException e) {
 *             throw new MigrationException("Failed to create tables", e);
 *         }
 *     }
 * }
 * 
 * // Handle validation failure
 * try {
 *     ValidationResult validation = flywayMigration.validate();
 *     if (!validation.validationSuccessful) {
 *         throw new MigrationException("Migration validation failed: " + validation.errorDetails);
 *     }
 * } catch (MigrationException e) {
 *     alerting.sendCriticalAlert("Database schema inconsistent", e);
 *     throw e;
 * }
 * }</pre>
 *
 * <p><b>Recovery Strategies</b><br>
 * - <b>Validation Failure</b>: Run flyway repair() to fix metadata
 * - <b>Execution Failure</b>: Fix migration script, re-run migrate()
 * - <b>Rollback Failure</b>: Manual intervention, restore from backup
 * - <b>Out-of-Order</b>: Set outOfOrder=true or reorder migrations
 *
 * <p><b>Thread Safety</b><br>
 * Immutable exception - safe to throw across threads.
 *
 * @see FlywayMigration
 * @see org.flywaydb.core.api.exception.FlywayException
 * @since 1.0.0
 * @doc.type exception
 * @doc.purpose Database migration failure exception
 * @doc.layer core
 * @doc.pattern Exception
 */
public class MigrationException extends RuntimeException {
    
    /**
     * Creates a new MigrationException with the specified message.
     * 
     * @param message The exception message
     */
    public MigrationException(String message) {
        super(message);
    }
    
    /**
     * Creates a new MigrationException with the specified message and cause.
     * 
     * @param message The exception message
     * @param cause The underlying cause
     */
    public MigrationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Creates a new MigrationException with the specified cause.
     * 
     * @param cause The underlying cause
     */
    public MigrationException(Throwable cause) {
        super(cause);
    }
}
