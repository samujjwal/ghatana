package com.ghatana.datacloud.migration;

/**
 * Backfills the {@code display_name} column on the {@code entities} table from the
 * embedded {@code data->>'display_name'} JSONB field.
 *
 * <p>This is Phase 2 of the V006 expand/contract sequence. It is registered in
 * {@code DataMigrationService} and runs at startup after Flyway applies V006.
 *
 * <p>The UPDATE is idempotent: rows where {@code display_name IS NOT NULL} are
 * skipped, so re-running on the next startup is safe.
 *
 * @doc.type class
 * @doc.purpose Concrete batched backfill for entities.display_name from JSONB data field
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class BackfillEntitiesDisplayName extends BackfillMigration {

    /** Default batch size of 1 000 rows per transaction. */
    public BackfillEntitiesDisplayName() {
        super("entities");
    }

    /** Allow a custom batch size for performance tuning in environments with large tables. */
    public BackfillEntitiesDisplayName(int batchSize) {
        super("entities", batchSize);
    }

    @Override
    public String name() {
        return "BackfillEntitiesDisplayName";
    }

    /**
     * UPDATE rows where {@code display_name} is null and the JSONB {@code data} field
     * contains a non-null {@code display_name} key. Uses a subquery with {@code FETCH FIRST}
     * to process at most {@code batchSize} rows per transaction.
     *
     * <p>The {@code data->>'display_name'} expression extracts the text value from JSONB.
     * {@code data ? 'display_name'} checks for key existence to avoid NULL from missing keys.
     *
     * <p>Note: {@code data ? 'key'} syntax is PostgreSQL-specific. In H2 tests the
     * {@code data} column is VARCHAR rather than JSONB, so tests use a simpler variant
     * via a subclass — see {@code DataMigrationServiceTest}.
     */
    @Override
    protected String buildUpdateSql() {
        return "UPDATE entities"
                + " SET display_name = data->>'display_name'"
                + " WHERE id IN ("
                + "   SELECT id FROM entities"
                + "   WHERE display_name IS NULL AND data ? 'display_name'"
                + "   ORDER BY id"
                + "   FETCH FIRST " + getBatchSize() + " ROWS ONLY"
                + ")";
    }
}
