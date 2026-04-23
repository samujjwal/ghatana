package com.ghatana.datacloud.migration;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the zero-downtime migration framework using H2 in-memory database.
 *
 * <p>H2 is used instead of a real PostgreSQL instance so these tests are fast and require
 * no external infrastructure. H2 2.x supports SQL-standard {@code FETCH FIRST n ROWS ONLY}
 * and UUID literals, making it compatible with all framework SQL.
 *
 * <p>Note: {@code CREATE INDEX CONCURRENTLY} is PostgreSQL-specific. {@link ConcurrentIndexMigration}
 * detects the database product name and falls back to plain {@code CREATE INDEX IF NOT EXISTS}
 * on H2, so the tests validate the build path without requiring a real Postgres connection.
 */
@DisplayName("Zero-downtime DB migration framework")
class DataMigrationServiceTest {

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException { // GH-90000
        // Unique DB name per test prevents state leakage between tests in the same JVM run.
        JdbcDataSource ds = new JdbcDataSource(); // GH-90000
        ds.setURL("jdbc:h2:mem:migration_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1"); // GH-90000
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;
    }

    // =========================================================================
    // Helper: create a simple table and populate it
    // =========================================================================

    private void createTestTable(String table, String... extraColumns) throws SQLException { // GH-90000
        StringBuilder ddl = new StringBuilder("CREATE TABLE " + table + " (id UUID NOT NULL PRIMARY KEY"); // GH-90000
        for (String col : extraColumns) { // GH-90000
            ddl.append(", ").append(col);
        }
        ddl.append(")");
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) { // GH-90000
            stmt.execute(ddl.toString()); // GH-90000
        }
    }

    private void insertRow(String table, UUID id, String sourceCol, String sourceVal, String targetCol) throws SQLException { // GH-90000
        String sql = "INSERT INTO " + table + " (id, " + sourceCol + ", " + targetCol + ") VALUES (?, ?, NULL)"; // GH-90000
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) { // GH-90000
            ps.setObject(1, id); // GH-90000
            ps.setString(2, sourceVal); // GH-90000
            ps.executeUpdate(); // GH-90000
        }
    }

    private long countNulls(String table, String column) throws SQLException { // GH-90000
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + column + " IS NULL"; // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement(); // GH-90000
             ResultSet rs = stmt.executeQuery(sql)) { // GH-90000
            rs.next(); // GH-90000
            return rs.getLong(1); // GH-90000
        }
    }

    private long countNonNulls(String table, String column) throws SQLException { // GH-90000
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + column + " IS NOT NULL"; // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement(); // GH-90000
             ResultSet rs = stmt.executeQuery(sql)) { // GH-90000
            rs.next(); // GH-90000
            return rs.getLong(1); // GH-90000
        }
    }

    // =========================================================================
    // MigrationReport
    // =========================================================================

    @Nested
    @DisplayName("MigrationReport")
    class MigrationReportTests {

        @Test
        @DisplayName("success factory sets correct fields")
        void successFactory() { // GH-90000
            MigrationReport r = MigrationReport.success("MyStrategy", "my_table", "BACKFILL", 42L, 100L); // GH-90000
            assertThat(r.success()).isTrue(); // GH-90000
            assertThat(r.rowsAffected()).isEqualTo(42L); // GH-90000
            assertThat(r.durationMs()).isEqualTo(100L); // GH-90000
            assertThat(r.errorMessage()).isNull(); // GH-90000
            assertThat(r.strategyName()).isEqualTo("MyStrategy");
            assertThat(r.tableName()).isEqualTo("my_table");
            assertThat(r.operation()).isEqualTo("BACKFILL");
            assertThat(r.toString()).contains("OK").contains("rows=42");
        }

        @Test
        @DisplayName("failure factory sets correct fields")
        void failureFactory() { // GH-90000
            MigrationReport r = MigrationReport.failure("MyStrategy", "my_table", "BACKFILL", 50L, "timeout"); // GH-90000
            assertThat(r.success()).isFalse(); // GH-90000
            assertThat(r.rowsAffected()).isEqualTo(0L); // GH-90000
            assertThat(r.errorMessage()).isEqualTo("timeout");
            assertThat(r.toString()).contains("FAILED").contains("timeout");
        }
    }

    // =========================================================================
    // DataMigrationService — registration and orchestration
    // =========================================================================

    @Nested
    @DisplayName("DataMigrationService")
    class DataMigrationServiceTests {

        @Test
        @DisplayName("runAll on empty service returns empty list")
        void emptyService() { // GH-90000
            DataMigrationService svc = new DataMigrationService(dataSource); // GH-90000
            assertThat(svc.strategyCount()).isEqualTo(0); // GH-90000
            assertThat(svc.runAll()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("register returns this for fluent chaining")
        void fluentChaining() { // GH-90000
            DataMigrationService svc = new DataMigrationService(dataSource); // GH-90000
            ZeroDowntimeMigrationStrategy noOp = ds -> MigrationReport.success("noop", "t", "OP", 0L, 1L); // GH-90000
            DataMigrationService returned = svc.register(noOp); // GH-90000
            assertThat(returned).isSameAs(svc); // GH-90000
            assertThat(svc.strategyCount()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("runAll executes all strategies and accumulates reports")
        void runsAllStrategies() { // GH-90000
            DataMigrationService svc = new DataMigrationService(dataSource); // GH-90000
            svc.register(ds -> MigrationReport.success("s1", "t", "OP", 10L, 5L)) // GH-90000
               .register(ds -> MigrationReport.success("s2", "t", "OP", 20L, 6L)) // GH-90000
               .register(ds -> MigrationReport.failure("s3", "t", "OP", 7L, "intentional")); // GH-90000

            List<MigrationReport> reports = svc.runAll(); // GH-90000
            assertThat(reports).hasSize(3); // GH-90000
            assertThat(reports.get(0).strategyName()).isEqualTo("s1");
            assertThat(reports.get(1).rowsAffected()).isEqualTo(20L); // GH-90000
            assertThat(reports.get(2).success()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("failure in one strategy does not abort subsequent strategies")
        void failureDoesNotAbortChain() { // GH-90000
            AtomicBoolean thirdRan = new AtomicBoolean(false); // GH-90000
            DataMigrationService svc = new DataMigrationService(dataSource); // GH-90000
            svc.register(ds -> { throw new SQLException("simulated"); })
               .register(ds -> { thirdRan.set(true); return MigrationReport.success("s2", "t", "OP", 0L, 1L); }); // GH-90000

            List<MigrationReport> reports = svc.runAll(); // GH-90000
            assertThat(reports).hasSize(2); // GH-90000
            assertThat(reports.get(0).success()).isFalse(); // GH-90000
            assertThat(thirdRan).isTrue(); // GH-90000
            assertThat(reports.get(1).success()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("runAll list is unmodifiable")
        void returnsUnmodifiableList() { // GH-90000
            DataMigrationService svc = new DataMigrationService(dataSource); // GH-90000
            svc.register(ds -> MigrationReport.success("s", "t", "OP", 0L, 1L)); // GH-90000
            List<MigrationReport> reports = svc.runAll(); // GH-90000
            assertThatThrownBy(() -> reports.add(MigrationReport.success("x", "t", "OP", 0L, 1L))) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("null DataSource throws NullPointerException")
        void nullDataSourceThrows() { // GH-90000
            assertThatThrownBy(() -> new DataMigrationService(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null strategy throws NullPointerException")
        void nullStrategyThrows() { // GH-90000
            DataMigrationService svc = new DataMigrationService(dataSource); // GH-90000
            assertThatThrownBy(() -> svc.register(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // =========================================================================
    // ColumnRenameMigration
    // =========================================================================

    @Nested
    @DisplayName("ColumnRenameMigration")
    class ColumnRenameMigrationTests {

        private static final String TABLE = "rename_test";

        @BeforeEach
        void setUpTable() throws SQLException { // GH-90000
            createTestTable(TABLE, "old_name VARCHAR(255)", "new_name VARCHAR(255)"); // GH-90000
        }

        private void insertRenameRows(int count) throws SQLException { // GH-90000
            try (Connection conn = dataSource.getConnection()) { // GH-90000
                conn.setAutoCommit(false); // GH-90000
                for (int i = 0; i < count; i++) { // GH-90000
                    try (PreparedStatement ps = conn.prepareStatement( // GH-90000
                            "INSERT INTO " + TABLE + " (id, old_name, new_name) VALUES (?, ?, NULL)")) { // GH-90000
                        ps.setObject(1, UUID.randomUUID()); // GH-90000
                        ps.setString(2, "name_" + i); // GH-90000
                        ps.executeUpdate(); // GH-90000
                    }
                }
                conn.commit(); // GH-90000
            }
        }

        @Test
        @DisplayName("backfills all rows with batchSize > rowCount in a single pass")
        void backfillsAllRowsSinglePass() throws SQLException { // GH-90000
            insertRenameRows(10); // GH-90000
            MigrationReport report = new ColumnRenameMigration(TABLE, "old_name", "new_name", 100) // GH-90000
                    .execute(dataSource); // GH-90000

            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.rowsAffected()).isEqualTo(10L); // GH-90000
            assertThat(report.operation()).isEqualTo("COLUMN_RENAME_BACKFILL");
            assertThat(countNulls(TABLE, "new_name")).isEqualTo(0L); // GH-90000
        }

        @Test
        @DisplayName("backfills all rows across multiple batches when batchSize < rowCount")
        void backfillsAllRowsMultipleBatches() throws SQLException { // GH-90000
            insertRenameRows(25); // GH-90000
            MigrationReport report = new ColumnRenameMigration(TABLE, "old_name", "new_name", 7) // GH-90000
                    .execute(dataSource); // GH-90000

            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.rowsAffected()).isEqualTo(25L); // GH-90000
            assertThat(countNulls(TABLE, "new_name")).isEqualTo(0L); // GH-90000
        }

        @Test
        @DisplayName("idempotent: re-running on already-backfilled table updates 0 rows")
        void idempotent() throws SQLException { // GH-90000
            insertRenameRows(5); // GH-90000
            // Run once
            new ColumnRenameMigration(TABLE, "old_name", "new_name").execute(dataSource); // GH-90000
            // Run again
            MigrationReport report = new ColumnRenameMigration(TABLE, "old_name", "new_name") // GH-90000
                    .execute(dataSource); // GH-90000

            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.rowsAffected()).isEqualTo(0L); // GH-90000
        }

        @Test
        @DisplayName("rows where old_name IS NULL are not backfilled")
        void skipsNullSourceRows() throws SQLException { // GH-90000
            // Insert rows with null source
            try (Connection conn = dataSource.getConnection(); Statement s = conn.createStatement()) { // GH-90000
                s.execute("INSERT INTO " + TABLE + " (id, old_name, new_name) VALUES (RANDOM_UUID(), NULL, NULL)"); // GH-90000
            }
            MigrationReport report = new ColumnRenameMigration(TABLE, "old_name", "new_name") // GH-90000
                    .execute(dataSource); // GH-90000

            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.rowsAffected()).isEqualTo(0L); // GH-90000
            assertThat(countNulls(TABLE, "new_name")).isEqualTo(1L); // still null // GH-90000
        }

        @Test
        @DisplayName("name() includes table and column names")
        void nameIncludesContext() { // GH-90000
            ColumnRenameMigration m = new ColumnRenameMigration("my_table", "src_col", "dst_col"); // GH-90000
            assertThat(m.name()).contains("my_table").contains("src_col").contains("dst_col");
        }

        @Test
        @DisplayName("invalid batchSize throws IllegalArgumentException")
        void invalidBatchSizeThrows() { // GH-90000
            assertThatThrownBy(() -> new ColumnRenameMigration("t", "a", "b", 0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
            assertThatThrownBy(() -> new ColumnRenameMigration("t", "a", "b", 10_001)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // =========================================================================
    // BackfillMigration (via anonymous concrete subclass) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("BackfillMigration")
    class BackfillMigrationTests {

        private static final String TABLE = "backfill_test";

        @BeforeEach
        void setUpTable() throws SQLException { // GH-90000
            createTestTable(TABLE, "source_val VARCHAR(255)", "target_val VARCHAR(255)"); // GH-90000
        }

        /** Simple concrete BackfillMigration for testing: copies source_val -> target_val. */
        private BackfillMigration concreteBackfill(int batchSize) { // GH-90000
            return new BackfillMigration(TABLE, batchSize) { // GH-90000
                @Override
                public String name() { return "TestBackfill"; } // GH-90000

                @Override
                protected String buildUpdateSql() { // GH-90000
                    return "UPDATE " + TABLE
                            + " SET target_val = source_val"
                            + " WHERE id IN (" // GH-90000
                            + "   SELECT id FROM " + TABLE
                            + "   WHERE target_val IS NULL AND source_val IS NOT NULL"
                            + "   ORDER BY id FETCH FIRST " + getBatchSize() + " ROWS ONLY" // GH-90000
                            + ")";
                }
            };
        }

        private void insertBackfillRows(int count) throws SQLException { // GH-90000
            try (Connection conn = dataSource.getConnection()) { // GH-90000
                conn.setAutoCommit(false); // GH-90000
                for (int i = 0; i < count; i++) { // GH-90000
                    try (PreparedStatement ps = conn.prepareStatement( // GH-90000
                            "INSERT INTO " + TABLE + " (id, source_val, target_val) VALUES (?, ?, NULL)")) { // GH-90000
                        ps.setObject(1, UUID.randomUUID()); // GH-90000
                        ps.setString(2, "val_" + i); // GH-90000
                        ps.executeUpdate(); // GH-90000
                    }
                }
                conn.commit(); // GH-90000
            }
        }

        @Test
        @DisplayName("backfills all rows and returns success report")
        void backfillsAllRows() throws SQLException { // GH-90000
            insertBackfillRows(15); // GH-90000
            MigrationReport report = concreteBackfill(1_000).execute(dataSource); // GH-90000

            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.rowsAffected()).isEqualTo(15L); // GH-90000
            assertThat(report.operation()).isEqualTo("BACKFILL");
            assertThat(countNulls(TABLE, "target_val")).isEqualTo(0L); // GH-90000
        }

        @Test
        @DisplayName("batches work correctly across multiple iterations")
        void batchedExecution() throws SQLException { // GH-90000
            insertBackfillRows(30); // GH-90000
            MigrationReport report = concreteBackfill(4).execute(dataSource); // GH-90000

            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.rowsAffected()).isEqualTo(30L); // GH-90000
            assertThat(countNulls(TABLE, "target_val")).isEqualTo(0L); // GH-90000
        }

        @Test
        @DisplayName("idempotent: 0 rows updated on second run")
        void idempotent() throws SQLException { // GH-90000
            insertBackfillRows(5); // GH-90000
            concreteBackfill(1_000).execute(dataSource); // GH-90000
            MigrationReport report = concreteBackfill(1_000).execute(dataSource); // GH-90000

            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.rowsAffected()).isEqualTo(0L); // GH-90000
        }

        @Test
        @DisplayName("empty table returns 0 rows success")
        void emptyTable() throws SQLException { // GH-90000
            MigrationReport report = concreteBackfill(1_000).execute(dataSource); // GH-90000
            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.rowsAffected()).isEqualTo(0L); // GH-90000
        }

        @Test
        @DisplayName("invalid batchSize throws IllegalArgumentException")
        void invalidBatchSize() { // GH-90000
            assertThatThrownBy(() -> concreteBackfill(-1)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
            assertThatThrownBy(() -> concreteBackfill(0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // =========================================================================
    // ConcurrentIndexMigration (via EntitiesDisplayNameIndex) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("ConcurrentIndexMigration")
    class ConcurrentIndexMigrationTests {

        private static final String TABLE = "entities_idx_test";

        @BeforeEach
        void setUpTable() throws SQLException { // GH-90000
            createTestTable(TABLE, "tenant_id VARCHAR(255)", "collection_name VARCHAR(255)", "display_name VARCHAR(500)"); // GH-90000
        }

        /** Anonymous concrete implementation using the test table. */
        private ConcurrentIndexMigration concreteIndex() { // GH-90000
            return new ConcurrentIndexMigration() { // GH-90000
                @Override protected String tableName()    { return TABLE; } // GH-90000
                @Override protected String indexName()    { return "idx_test_display_name"; } // GH-90000
                @Override protected String indexColumns() { return "tenant_id, collection_name, display_name"; } // GH-90000
                @Override protected String whereClause()  { return "display_name IS NOT NULL"; } // GH-90000
                @Override public String name()            { return "TestConcurrentIndex"; } // GH-90000
            };
        }

        @Test
        @DisplayName("creates index successfully on H2 (falls back to standard CREATE INDEX)")
        void createsIndex() throws SQLException { // GH-90000
            MigrationReport report = concreteIndex().execute(dataSource); // GH-90000
            assertThat(report.success()).isTrue(); // GH-90000
            assertThat(report.operation()).isEqualTo("CREATE_INDEX");
        }

        @Test
        @DisplayName("idempotent: second run via IF NOT EXISTS does not fail")
        void idempotent() throws SQLException { // GH-90000
            concreteIndex().execute(dataSource); // GH-90000
            MigrationReport report = concreteIndex().execute(dataSource); // GH-90000
            assertThat(report.success()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("canExecuteInTransaction returns false")
        void notTransactional() { // GH-90000
            assertThat(concreteIndex().canExecuteInTransaction()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("name includes index and table names")
        void nameIncludesContext() { // GH-90000
            assertThat(concreteIndex().name()).isEqualTo("TestConcurrentIndex");
        }
    }

    // =========================================================================
    // End-to-end: full expand/contract sequence
    // =========================================================================

    @Nested
    @DisplayName("Expand/contract end-to-end")
    class EndToEndTests {

        private static final String TABLE = "e2e_entities";

        @BeforeEach
        void setUpTable() throws SQLException { // GH-90000
            // Simulate state after V006: entities table with old_name populated, new_name nullable
            createTestTable(TABLE, "old_name VARCHAR(255)", "new_name VARCHAR(255)"); // GH-90000
            try (Connection conn = dataSource.getConnection()) { // GH-90000
                conn.setAutoCommit(false); // GH-90000
                for (int i = 0; i < 20; i++) { // GH-90000
                    try (PreparedStatement ps = conn.prepareStatement( // GH-90000
                            "INSERT INTO " + TABLE + " (id, old_name, new_name) VALUES (?, ?, NULL)")) { // GH-90000
                        ps.setObject(1, UUID.randomUUID()); // GH-90000
                        ps.setString(2, "display_" + i); // GH-90000
                        ps.executeUpdate(); // GH-90000
                    }
                }
                conn.commit(); // GH-90000
            }
        }

        @Test
        @DisplayName("full pipeline: backfill + concurrent index both succeed")
        void fullPipeline() throws SQLException { // GH-90000
            ConcurrentIndexMigration indexMigration = new ConcurrentIndexMigration() { // GH-90000
                @Override protected String tableName()    { return TABLE; } // GH-90000
                @Override protected String indexName()    { return "idx_e2e_new_name"; } // GH-90000
                @Override protected String indexColumns() { return "new_name"; } // GH-90000
                @Override protected String whereClause()  { return "new_name IS NOT NULL"; } // GH-90000
            };

            DataMigrationService svc = new DataMigrationService(dataSource); // GH-90000
            svc.register(new ColumnRenameMigration(TABLE, "old_name", "new_name", 5)) // GH-90000
               .register(indexMigration); // GH-90000

            List<MigrationReport> reports = svc.runAll(); // GH-90000

            assertThat(reports).hasSize(2); // GH-90000
            assertThat(reports.get(0).success()).isTrue(); // GH-90000
            assertThat(reports.get(0).rowsAffected()).isEqualTo(20L); // GH-90000
            assertThat(reports.get(1).success()).isTrue(); // GH-90000
            assertThat(countNulls(TABLE, "new_name")).isEqualTo(0L); // GH-90000
            assertThat(countNonNulls(TABLE, "new_name")).isEqualTo(20L); // GH-90000
        }
    }
}
