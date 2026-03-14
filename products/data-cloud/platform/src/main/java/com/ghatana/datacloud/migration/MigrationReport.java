package com.ghatana.datacloud.migration;

/**
 * Immutable result of a single {@link ZeroDowntimeMigrationStrategy} execution.
 *
 * @doc.type record
 * @doc.purpose Value object capturing outcome (rows, duration, success/failure) of a migration step
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record MigrationReport(
        String strategyName,
        String tableName,
        String operation,
        long rowsAffected,
        long durationMs,
        boolean success,
        String errorMessage) {

    /**
     * Create a successful report.
     *
     * @param strategyName  name of the strategy that ran
     * @param tableName     primary table affected
     * @param operation     e.g. {@code "BACKFILL"}, {@code "CREATE_INDEX"}, {@code "COLUMN_RENAME_BACKFILL"}
     * @param rowsAffected  total DML rows touched across all batches
     * @param durationMs    wall-clock milliseconds elapsed
     * @return successful report
     */
    public static MigrationReport success(
            String strategyName,
            String tableName,
            String operation,
            long rowsAffected,
            long durationMs) {
        return new MigrationReport(strategyName, tableName, operation, rowsAffected, durationMs, true, null);
    }

    /**
     * Create a failure report.
     *
     * @param strategyName human-readable strategy name
     * @param tableName    primary table affected
     * @param operation    operation that was being attempted
     * @param durationMs   wall-clock milliseconds elapsed before failure
     * @param errorMessage root cause message
     * @return failure report
     */
    public static MigrationReport failure(
            String strategyName,
            String tableName,
            String operation,
            long durationMs,
            String errorMessage) {
        return new MigrationReport(strategyName, tableName, operation, 0L, durationMs, false, errorMessage);
    }

    @Override
    public String toString() {
        if (success) {
            return String.format(
                    "MigrationReport{strategy='%s', table='%s', op='%s', rows=%d, ms=%d, OK}",
                    strategyName, tableName, operation, rowsAffected, durationMs);
        }
        return String.format(
                "MigrationReport{strategy='%s', table='%s', op='%s', ms=%d, FAILED: %s}",
                strategyName, tableName, operation, durationMs, errorMessage);
    }
}
