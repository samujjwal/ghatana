package com.ghatana.datacloud.migration;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Strategy contract for a single step of a zero-downtime database migration.
 *
 * <p>Implementations must be <strong>idempotent</strong>: executing the same strategy twice
 * against the same database state must produce the same terminal result without corrupting data.
 * This is enforced by using conditional SQL (e.g. {@code WHERE new_col IS NULL}) so that
 * already-processed rows are naturally skipped.
 *
 * <p>Strategies are registered into a {@link DataMigrationService} and executed in registration
 * order after Flyway has applied all DDL migrations.
 *
 * @doc.type interface
 * @doc.purpose Strategy for a single idempotent step of a zero-downtime DB migration
 * @doc.layer product
 * @doc.pattern Strategy
 *
 * @see BackfillMigration
 * @see ColumnRenameMigration
 * @see ConcurrentIndexMigration
 * @see DataMigrationService
 */
@FunctionalInterface
public interface ZeroDowntimeMigrationStrategy {

    /**
     * Execute this migration step against the given {@code dataSource}.
     *
     * <p>Implementations are responsible for their own transaction management (commit/rollback).
     * The strategy must not assume the connection is pre-configured.
     *
     * @param dataSource active DataSource to execute against
     * @return {@link MigrationReport} describing the outcome
     * @throws SQLException on an unrecoverable JDBC error that the strategy cannot handle
     */
    MigrationReport execute(DataSource dataSource) throws SQLException;

    /**
     * Human-readable name for this strategy, used in log messages and reports.
     * Defaults to the simple class name.
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
