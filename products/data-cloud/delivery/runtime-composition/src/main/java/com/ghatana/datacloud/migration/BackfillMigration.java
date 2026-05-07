package com.ghatana.datacloud.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Abstract base for batched DML backfill migrations that avoid table-level lock escalation.
 *
 * <h2>Design</h2>
 * <p>Subclasses implement {@link #buildUpdateSql()} returning an idempotent UPDATE statement
 * that affects at most {@link #getBatchSize()} rows per execution and converges to 0 updated
 * rows when all rows have been processed (i.e. the WHERE clause excludes already-migrated rows).
 *
 * <p>Each batch runs in its own short transaction followed by a configurable pause of
 * {@value #INTER_BATCH_PAUSE_MS} ms to allow vacuuming and reduce replication lag spikes.
 *
 * <h2>Idempotency</h2>
 * <p>The backfill SQL must be written so that re-running it is safe. Typically this means
 * adding a {@code WHERE new_col IS NULL} (or equivalent) guard so that already-processed rows
 * are not re-processed.
 *
 * @doc.type class
 * @doc.purpose Abstract base for chunked DML backfill to avoid long-running transactions and lock escalation
 * @doc.layer product
 * @doc.pattern Strategy
 */
public abstract class BackfillMigration implements ZeroDowntimeMigrationStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(BackfillMigration.class);

    /** Default batch size: 1 000 rows per transaction. */
    protected static final int DEFAULT_BATCH_SIZE = 1_000;

    /** Brief pause between batches to let vacuum/replication catch up. */
    private static final long INTER_BATCH_PAUSE_MS = 10L;

    private final String tableName;
    private final int batchSize;

    /**
     * Construct with {@link #DEFAULT_BATCH_SIZE}.
     *
     * @param tableName primary table being backfilled (used in log messages and reports)
     */
    protected BackfillMigration(String tableName) {
        this(tableName, DEFAULT_BATCH_SIZE);
    }

    /**
     * Construct with a custom batch size.
     *
     * @param tableName primary table being backfilled
     * @param batchSize number of rows to update per transaction; must be between 1 and 10 000
     */
    protected BackfillMigration(String tableName, int batchSize) {
        if (batchSize <= 0 || batchSize > 10_000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 10 000, was: " + batchSize);
        }
        this.tableName = tableName;
        this.batchSize = batchSize;
    }

    /**
     * Return an idempotent UPDATE SQL that processes at most {@link #getBatchSize()} rows per call.
     *
     * <p>The SQL must converge (i.e. return 0 rows updated when there is nothing left to do).
     * Use {@link #getBatchSize()} to embed the limit, typically via a subquery:
     * <pre>
     * UPDATE my_table SET new_col = old_col
     * WHERE id IN (
     *   SELECT id FROM my_table WHERE new_col IS NULL ORDER BY id
     *   FETCH FIRST ? ROWS ONLY
     * )
     * </pre>
     *
     * @return parameterless SQL UPDATE string
     */
    protected abstract String buildUpdateSql();

    /**
     * Execute the batched backfill, retrying until 0 rows are updated.
     *
     * @param dataSource live DataSource
     * @return {@link MigrationReport} with total rows and duration
     */
    @Override
    public final MigrationReport execute(DataSource dataSource) {
        long start = System.currentTimeMillis();
        long totalRows = 0L;
        String sql = buildUpdateSql();

        LOG.info("[BackfillMigration] Starting '{}' on table '{}' (batchSize={})", name(), tableName, batchSize);

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
                        LOG.debug("[BackfillMigration] batch #{}: {} rows updated (total={})", batchCount, updated, totalRows);
                        Thread.sleep(INTER_BATCH_PAUSE_MS);
                    }
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("[BackfillMigration] '{}' interrupted after {} rows in {} batches", name(), totalRows, batchCount);
                    break;
                }
            } while (updated > 0);

            long duration = System.currentTimeMillis() - start;
            LOG.info("[BackfillMigration] '{}' complete: {} rows in {} batches, {}ms", name(), totalRows, batchCount, duration);
            return MigrationReport.success(name(), tableName, "BACKFILL", totalRows, duration);

        } catch (SQLException e) {
            long duration = System.currentTimeMillis() - start;
            LOG.error("[BackfillMigration] '{}' failed after {}ms: {}", name(), duration, e.getMessage());
            return MigrationReport.failure(name(), tableName, "BACKFILL", duration, e.getMessage());
        }
    }

    /** The table name provided at construction time. */
    public final String getTableName() {
        return tableName;
    }

    /** The configured batch size. */
    public final int getBatchSize() {
        return batchSize;
    }
}
