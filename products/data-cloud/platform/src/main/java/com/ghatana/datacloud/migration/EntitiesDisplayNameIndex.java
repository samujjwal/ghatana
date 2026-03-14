package com.ghatana.datacloud.migration;

/**
 * Creates a partial index on {@code entities.display_name} using
 * {@code CREATE INDEX CONCURRENTLY} (PostgreSQL) to avoid locking DML
 * during the index build on large tables.
 *
 * <p>Registered with {@link DataMigrationService} at startup after Flyway V006
 * has added the column and {@link BackfillEntitiesDisplayName} has populated it.
 *
 * <p>The partial {@code WHERE display_name IS NOT NULL} keeps the index compact
 * and efficient — rows without a display name are excluded entirely.
 *
 * @doc.type class
 * @doc.purpose Concurrent partial index on entities.display_name for fast tenant-scoped lookups
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class EntitiesDisplayNameIndex extends ConcurrentIndexMigration {

    @Override
    protected String tableName() {
        return "entities";
    }

    @Override
    protected String indexName() {
        return "idx_entities_display_name";
    }

    @Override
    protected String indexColumns() {
        return "tenant_id, collection_name, display_name";
    }

    @Override
    protected String whereClause() {
        // Partial index: only index rows that have a display_name value.
        // Reduces index size and keeps write amplification low for rows without display_name.
        return "display_name IS NOT NULL";
    }

    @Override
    public String name() {
        return "EntitiesDisplayNameIndex";
    }
}
