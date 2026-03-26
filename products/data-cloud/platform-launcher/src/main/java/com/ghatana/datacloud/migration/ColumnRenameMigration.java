package com.ghatana.datacloud.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Zero-downtime column rename using the <em>expand/contract</em> (aka dual-write) pattern.
 *
 * <h2>Expand/Contract Lifecycle</h2>
 * <pre>
 * Deploy N   (Flyway SQL):  ADD COLUMN {newCol} {type} NULL
 *                           — safe: adding a nullable column never locks on Postgres 11+
 * Deploy N   (this class):  BACKFILL {newCol} = {oldCol} in batches of {batchSize}
 *                           — no table lock; short per-batch transactions
 * Deploy N+1 (App code):    Read from {newCol} (fallback to {oldCol}); write to both
 *                           — dual-write transitional period
 * Deploy N+1 (Flyway SQL):  ALTER COLUMN {newCol} SET NOT NULL
 *                           — safe only after 100% backfill is verified
 * Deploy N+2 (App code):    Read and write only {newCol}
 * Deploy N+2 (Flyway SQL):  DROP COLUMN {oldCol}
 *                           — safe: no remaining code references it
 * </pre>
 *
 * <h2>Idempotency</h2>
 * <p>The backfill SQL uses {@code WHERE newCol IS NULL AND oldCol IS NOT NULL} so that
 * rows already migrated are naturally skipped. Running this strategy twice is safe.
 *
 * <h2>Batch SQL</h2>
 * <p>Uses a subquery with {@code FETCH FIRST n ROWS ONLY} (SQL standard, supported by
 * PostgreSQL 8.4+ and H2 2.x) to keep individual transactions short and avoid lock escalation.
 *
 * @doc.type class
 * @doc.purpose Zero-downtime batched backfill for the expand/contract column rename pattern
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class ColumnRenameMigration implements ZeroDowntimeMigrationStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(ColumnRenameMigration.class);
    private static final int DEFAULT_BATCH_SIZE = 1_000;
    private static final long INTER_BATCH_PAUSE_MS = 10L;

    private final String tableName;
    private final String sourceColumn;
    private final String targetColumn;
    private final int batchSize;

    /**
     * Construct with {@value #DEFAULT_BATCH_SIZE} rows per batch.
     *
     * @param tableName    table being migrated
     * @param sourceColumn existing column (populated, will be dropped in a future deploy)
     * @param targetColumn new column (nullable now; NOT NULL added after full backfill)
     */
    public ColumnRenameMigration(String tableName, String sourceColumn, String targetColumn) {
        this(tableName, sourceColumn, targetColumn, DEFAULT_BATCH_SIZE);
    }

    /**
     * @param tableName    table being migrated
     * @param sourceColumn existing column
     * @param targetColumn new column (must already exist as nullable)
     * @param batchSize    rows per transaction; must be between 1 and 10 000
     */
    public ColumnRenameMigration(String tableName, String sourceColumn, String targetColumn, int batchSize) {
        if (batchSize <= 0 || batchSize > 10_000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 10 000, was: " + batchSize);
        }
        this.tableName = tableName;
        this.sourceColumn = sourceColumn;
        this.targetColumn = targetColumn;
        this.batchSize = batchSize;
    }

    @Override
    public String name() {
        return "ColumnRename[" + tableName + "." + sourceColumn + " -> " + targetColumn + "]";
    }

    /**
     * Execute the batched backfill until all rows where {@code targetColumn IS NULL} are
     * filled from {@code sourceColumn}.
     */
    @Override
    public MigrationReport execute(DataSource dataSource) throws SQLException {
        long start = System.currentTimeMillis();
        long totalRows = 0L;

        // Subquery limits rows per batch and is safe on both PostgreSQL and H2.
        // ORDER BY id ensures deterministic page scanning to avoid repeated work.
        String sql = "UPDATE " + tableName
                + " SET " + targetColumn + " = " + sourceColumn
                + " WHERE id IN ("
                + "   SELECT id FROM " + tableName
                + "   WHERE " + targetColumn + " IS NULL AND " + sourceColumn + " IS NOT NULL"
                + "   ORDER BY id FETCH FIRST " + batchSize + " ROWS ONLY"
                + ")";

        LOG.info("[ColumnRenameMigration] Starting backfill {}.{} -> {} (batchSize={})",
                tableName, sourceColumn, targetColumn, batchSize);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            int batchCount = 0;
            int updated;
            do {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    updated = ps.executeUpdate();
                    conn.commit();
                    totalRows += updated;
                    batchCount++;
                    if (updated > 0) {
                        LOG.debug("[ColumnRenameMigration] batch #{}: {} rows (total={})", batchCount, updated, totalRows);
                        Thread.sleep(INTER_BATCH_PAUSE_MS);
                    }
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("[ColumnRenameMigration] '{}' interrupted after {} rows", name(), totalRows);
                    break;
                }
            } while (updated > 0);
        }

        long duration = System.currentTimeMillis() - start;
        LOG.info("[ColumnRenameMigration] Complete: {} rows in {}ms", totalRows, duration);
        return MigrationReport.success(name(), tableName, "COLUMN_RENAME_BACKFILL", totalRows, duration);
    }
}
