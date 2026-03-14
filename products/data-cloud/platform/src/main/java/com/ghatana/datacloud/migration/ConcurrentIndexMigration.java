package com.ghatana.datacloud.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Abstract base for index migrations that use {@code CREATE INDEX CONCURRENTLY} on PostgreSQL.
 *
 * <h2>Why concurrent?</h2>
 * <p>A regular {@code CREATE INDEX} on a large table acquires a {@code ShareLock} that blocks
 * all DML ({@code INSERT}, {@code UPDATE}, {@code DELETE}) for the duration of the build.
 * {@code CREATE INDEX CONCURRENTLY} builds the index in multiple passes without holding the
 * exclusive lock, so normal traffic continues uninterrupted — at the cost of 2-3× build time.
 *
 * <h2>Transaction constraint</h2>
 * <p>{@code CREATE INDEX CONCURRENTLY} cannot run inside a transaction. When integrating with
 * Flyway via a named subclass (e.g. {@code V008__MyIndex extends ConcurrentIndexMigration}),
 * override {@code canExecuteInTransaction()} from {@code BaseJavaMigration} to return
 * {@code false}. This class does not extend {@code BaseJavaMigration} directly to avoid
 * Flyway's name-validation constraint on anonymous / non-standard class names.
 *
 * <h2>H2 compatibility</h2>
 * <p>H2 does not support {@code CONCURRENTLY}. When the database product name does not contain
 * "postgresql", this class falls back to {@code CREATE INDEX IF NOT EXISTS} so that unit tests
 * using H2 pass without modification.
 *
 * <h2>Usage via DataMigrationService</h2>
 * <pre>
 * DataMigrationService svc = new DataMigrationService(dataSource);
 * svc.register(new EntitiesDisplayNameIndex());
 * svc.runAll();
 * </pre>
 *
 * <h2>Usage as Flyway Java migration (named subclass only)</h2>
 * <pre>
 * public final class V008__EntitiesDisplayNameIndex extends ConcurrentIndexMigration {
 *     {@literal @}Override public boolean canExecuteInTransaction() { return false; }
 *     {@literal @}Override protected String tableName()    { return "entities"; }
 *     {@literal @}Override protected String indexName()   { return "idx_entities_display_name"; }
 *     {@literal @}Override protected String indexColumns(){ return "tenant_id, collection_name, display_name"; }
 *     {@literal @}Override protected String whereClause() { return "display_name IS NOT NULL"; }
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Strategy base for creating indexes without DML-blocking locks
 * @doc.layer product
 * @doc.pattern Strategy
 */
public abstract class ConcurrentIndexMigration implements ZeroDowntimeMigrationStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(ConcurrentIndexMigration.class);

    /** The table on which the index is created. */
    protected abstract String tableName();

    /**
     * The index name. Follow convention: {@code idx_{table}_{columns}},
     * e.g. {@code idx_entities_display_name}.
     */
    protected abstract String indexName();

    /**
     * Comma-separated column expression for the index,
     * e.g. {@code "tenant_id, collection_name, display_name"}.
     */
    protected abstract String indexColumns();

    /**
     * Optional SQL WHERE predicate for a partial index.
     * Return {@code null} (the default) for a full index.
     */
    protected String whereClause() {
        return null;
    }

    /**
     * Documents that this index migration must NOT run inside a transaction.
     * When used as a Flyway Java migration, override this in a named subclass and return
     * {@code false} to instruct Flyway to run outside its default transaction wrapper.
     *
     * @return {@code false} always
     */
    public boolean canExecuteInTransaction() {
        return false;
    }

    /**
     * Execute the index creation via {@link DataMigrationService}.
     */
    @Override
    public MigrationReport execute(DataSource dataSource) {
        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            String sql = buildIndexSql(conn);
            LOG.info("[ConcurrentIndexMigration] Executing: {}", sql);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            long duration = System.currentTimeMillis() - start;
            LOG.error("[ConcurrentIndexMigration] Failed to create index '{}': {}", indexName(), e.getMessage());
            return MigrationReport.failure(name(), tableName(), "CREATE_INDEX", duration, e.getMessage());
        }
        long duration = System.currentTimeMillis() - start;
        LOG.info("[ConcurrentIndexMigration] Index '{}' created in {}ms", indexName(), duration);
        return MigrationReport.success(name(), tableName(), "CREATE_INDEX", 0L, duration);
    }

    @Override
    public String name() {
        return "ConcurrentIndex[" + indexName() + " ON " + tableName() + "]";
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String buildIndexSql(Connection conn) throws SQLException {
        boolean postgresDialect = isPostgres(conn);
        StringBuilder sb = new StringBuilder();
        if (postgresDialect) {
            sb.append("CREATE INDEX CONCURRENTLY IF NOT EXISTS ");
        } else {
            // H2 / other: fall back to plain CREATE INDEX (no CONCURRENTLY keyword)
            sb.append("CREATE INDEX IF NOT EXISTS ");
        }
        sb.append(indexName())
                .append(" ON ")
                .append(tableName())
                .append(" (")
                .append(indexColumns())
                .append(")");
        // Partial indexes (WHERE predicate) are PostgreSQL-specific — H2 does not support them
        if (postgresDialect) {
            String where = whereClause();
            if (where != null && !where.isBlank()) {
                sb.append(" WHERE ").append(where);
            }
        }
        return sb.toString();
    }

    private static boolean isPostgres(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String product = meta.getDatabaseProductName();
        return product != null && product.toLowerCase().contains("postgresql");
    }
}
