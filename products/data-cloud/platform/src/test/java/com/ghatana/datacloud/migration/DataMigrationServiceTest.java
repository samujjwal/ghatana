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
    void setUp() throws SQLException {
        // Unique DB name per test prevents state leakage between tests in the same JVM run.
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:migration_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;
    }

    // =========================================================================
    // Helper: create a simple table and populate it
    // =========================================================================

    private void createTestTable(String table, String... extraColumns) throws SQLException {
        StringBuilder ddl = new StringBuilder("CREATE TABLE " + table + " (id UUID NOT NULL PRIMARY KEY");
        for (String col : extraColumns) {
            ddl.append(", ").append(col);
        }
        ddl.append(")");
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(ddl.toString());
        }
    }

    private void insertRow(String table, UUID id, String sourceCol, String sourceVal, String targetCol) throws SQLException {
        String sql = "INSERT INTO " + table + " (id, " + sourceCol + ", " + targetCol + ") VALUES (?, ?, NULL)";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, sourceVal);
            ps.executeUpdate();
        }
    }

    private long countNulls(String table, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + column + " IS NULL";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private long countNonNulls(String table, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + column + " IS NOT NULL";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
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
        void successFactory() {
            MigrationReport r = MigrationReport.success("MyStrategy", "my_table", "BACKFILL", 42L, 100L);
            assertThat(r.success()).isTrue();
            assertThat(r.rowsAffected()).isEqualTo(42L);
            assertThat(r.durationMs()).isEqualTo(100L);
            assertThat(r.errorMessage()).isNull();
            assertThat(r.strategyName()).isEqualTo("MyStrategy");
            assertThat(r.tableName()).isEqualTo("my_table");
            assertThat(r.operation()).isEqualTo("BACKFILL");
            assertThat(r.toString()).contains("OK").contains("rows=42");
        }

        @Test
        @DisplayName("failure factory sets correct fields")
        void failureFactory() {
            MigrationReport r = MigrationReport.failure("MyStrategy", "my_table", "BACKFILL", 50L, "timeout");
            assertThat(r.success()).isFalse();
            assertThat(r.rowsAffected()).isEqualTo(0L);
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
        void emptyService() {
            DataMigrationService svc = new DataMigrationService(dataSource);
            assertThat(svc.strategyCount()).isEqualTo(0);
            assertThat(svc.runAll()).isEmpty();
        }

        @Test
        @DisplayName("register returns this for fluent chaining")
        void fluentChaining() {
            DataMigrationService svc = new DataMigrationService(dataSource);
            ZeroDowntimeMigrationStrategy noOp = ds -> MigrationReport.success("noop", "t", "OP", 0L, 1L);
            DataMigrationService returned = svc.register(noOp);
            assertThat(returned).isSameAs(svc);
            assertThat(svc.strategyCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("runAll executes all strategies and accumulates reports")
        void runsAllStrategies() {
            DataMigrationService svc = new DataMigrationService(dataSource);
            svc.register(ds -> MigrationReport.success("s1", "t", "OP", 10L, 5L))
               .register(ds -> MigrationReport.success("s2", "t", "OP", 20L, 6L))
               .register(ds -> MigrationReport.failure("s3", "t", "OP", 7L, "intentional"));

            List<MigrationReport> reports = svc.runAll();
            assertThat(reports).hasSize(3);
            assertThat(reports.get(0).strategyName()).isEqualTo("s1");
            assertThat(reports.get(1).rowsAffected()).isEqualTo(20L);
            assertThat(reports.get(2).success()).isFalse();
        }

        @Test
        @DisplayName("failure in one strategy does not abort subsequent strategies")
        void failureDoesNotAbortChain() {
            AtomicBoolean thirdRan = new AtomicBoolean(false);
            DataMigrationService svc = new DataMigrationService(dataSource);
            svc.register(ds -> { throw new SQLException("simulated"); })
               .register(ds -> { thirdRan.set(true); return MigrationReport.success("s2", "t", "OP", 0L, 1L); });

            List<MigrationReport> reports = svc.runAll();
            assertThat(reports).hasSize(2);
            assertThat(reports.get(0).success()).isFalse();
            assertThat(thirdRan).isTrue();
            assertThat(reports.get(1).success()).isTrue();
        }

        @Test
        @DisplayName("runAll list is unmodifiable")
        void returnsUnmodifiableList() {
            DataMigrationService svc = new DataMigrationService(dataSource);
            svc.register(ds -> MigrationReport.success("s", "t", "OP", 0L, 1L));
            List<MigrationReport> reports = svc.runAll();
            assertThatThrownBy(() -> reports.add(MigrationReport.success("x", "t", "OP", 0L, 1L)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("null DataSource throws NullPointerException")
        void nullDataSourceThrows() {
            assertThatThrownBy(() -> new DataMigrationService(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null strategy throws NullPointerException")
        void nullStrategyThrows() {
            DataMigrationService svc = new DataMigrationService(dataSource);
            assertThatThrownBy(() -> svc.register(null))
                    .isInstanceOf(NullPointerException.class);
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
        void setUpTable() throws SQLException {
            createTestTable(TABLE, "old_name VARCHAR(255)", "new_name VARCHAR(255)");
        }

        private void insertRenameRows(int count) throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                for (int i = 0; i < count; i++) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO " + TABLE + " (id, old_name, new_name) VALUES (?, ?, NULL)")) {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setString(2, "name_" + i);
                        ps.executeUpdate();
                    }
                }
                conn.commit();
            }
        }

        @Test
        @DisplayName("backfills all rows with batchSize > rowCount in a single pass")
        void backfillsAllRowsSinglePass() throws SQLException {
            insertRenameRows(10);
            MigrationReport report = new ColumnRenameMigration(TABLE, "old_name", "new_name", 100)
                    .execute(dataSource);

            assertThat(report.success()).isTrue();
            assertThat(report.rowsAffected()).isEqualTo(10L);
            assertThat(report.operation()).isEqualTo("COLUMN_RENAME_BACKFILL");
            assertThat(countNulls(TABLE, "new_name")).isEqualTo(0L);
        }

        @Test
        @DisplayName("backfills all rows across multiple batches when batchSize < rowCount")
        void backfillsAllRowsMultipleBatches() throws SQLException {
            insertRenameRows(25);
            MigrationReport report = new ColumnRenameMigration(TABLE, "old_name", "new_name", 7)
                    .execute(dataSource);

            assertThat(report.success()).isTrue();
            assertThat(report.rowsAffected()).isEqualTo(25L);
            assertThat(countNulls(TABLE, "new_name")).isEqualTo(0L);
        }

        @Test
        @DisplayName("idempotent: re-running on already-backfilled table updates 0 rows")
        void idempotent() throws SQLException {
            insertRenameRows(5);
            // Run once
            new ColumnRenameMigration(TABLE, "old_name", "new_name").execute(dataSource);
            // Run again
            MigrationReport report = new ColumnRenameMigration(TABLE, "old_name", "new_name")
                    .execute(dataSource);

            assertThat(report.success()).isTrue();
            assertThat(report.rowsAffected()).isEqualTo(0L);
        }

        @Test
        @DisplayName("rows where old_name IS NULL are not backfilled")
        void skipsNullSourceRows() throws SQLException {
            // Insert rows with null source
            try (Connection conn = dataSource.getConnection(); Statement s = conn.createStatement()) {
                s.execute("INSERT INTO " + TABLE + " (id, old_name, new_name) VALUES (RANDOM_UUID(), NULL, NULL)");
            }
            MigrationReport report = new ColumnRenameMigration(TABLE, "old_name", "new_name")
                    .execute(dataSource);

            assertThat(report.success()).isTrue();
            assertThat(report.rowsAffected()).isEqualTo(0L);
            assertThat(countNulls(TABLE, "new_name")).isEqualTo(1L); // still null
        }

        @Test
        @DisplayName("name() includes table and column names")
        void nameIncludesContext() {
            ColumnRenameMigration m = new ColumnRenameMigration("my_table", "src_col", "dst_col");
            assertThat(m.name()).contains("my_table").contains("src_col").contains("dst_col");
        }

        @Test
        @DisplayName("invalid batchSize throws IllegalArgumentException")
        void invalidBatchSizeThrows() {
            assertThatThrownBy(() -> new ColumnRenameMigration("t", "a", "b", 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ColumnRenameMigration("t", "a", "b", 10_001))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // BackfillMigration (via anonymous concrete subclass)
    // =========================================================================

    @Nested
    @DisplayName("BackfillMigration")
    class BackfillMigrationTests {

        private static final String TABLE = "backfill_test";

        @BeforeEach
        void setUpTable() throws SQLException {
            createTestTable(TABLE, "source_val VARCHAR(255)", "target_val VARCHAR(255)");
        }

        /** Simple concrete BackfillMigration for testing: copies source_val -> target_val. */
        private BackfillMigration concreteBackfill(int batchSize) {
            return new BackfillMigration(TABLE, batchSize) {
                @Override
                public String name() { return "TestBackfill"; }

                @Override
                protected String buildUpdateSql() {
                    return "UPDATE " + TABLE
                            + " SET target_val = source_val"
                            + " WHERE id IN ("
                            + "   SELECT id FROM " + TABLE
                            + "   WHERE target_val IS NULL AND source_val IS NOT NULL"
                            + "   ORDER BY id FETCH FIRST " + getBatchSize() + " ROWS ONLY"
                            + ")";
                }
            };
        }

        private void insertBackfillRows(int count) throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                for (int i = 0; i < count; i++) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO " + TABLE + " (id, source_val, target_val) VALUES (?, ?, NULL)")) {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setString(2, "val_" + i);
                        ps.executeUpdate();
                    }
                }
                conn.commit();
            }
        }

        @Test
        @DisplayName("backfills all rows and returns success report")
        void backfillsAllRows() throws SQLException {
            insertBackfillRows(15);
            MigrationReport report = concreteBackfill(1_000).execute(dataSource);

            assertThat(report.success()).isTrue();
            assertThat(report.rowsAffected()).isEqualTo(15L);
            assertThat(report.operation()).isEqualTo("BACKFILL");
            assertThat(countNulls(TABLE, "target_val")).isEqualTo(0L);
        }

        @Test
        @DisplayName("batches work correctly across multiple iterations")
        void batchedExecution() throws SQLException {
            insertBackfillRows(30);
            MigrationReport report = concreteBackfill(4).execute(dataSource);

            assertThat(report.success()).isTrue();
            assertThat(report.rowsAffected()).isEqualTo(30L);
            assertThat(countNulls(TABLE, "target_val")).isEqualTo(0L);
        }

        @Test
        @DisplayName("idempotent: 0 rows updated on second run")
        void idempotent() throws SQLException {
            insertBackfillRows(5);
            concreteBackfill(1_000).execute(dataSource);
            MigrationReport report = concreteBackfill(1_000).execute(dataSource);

            assertThat(report.success()).isTrue();
            assertThat(report.rowsAffected()).isEqualTo(0L);
        }

        @Test
        @DisplayName("empty table returns 0 rows success")
        void emptyTable() throws SQLException {
            MigrationReport report = concreteBackfill(1_000).execute(dataSource);
            assertThat(report.success()).isTrue();
            assertThat(report.rowsAffected()).isEqualTo(0L);
        }

        @Test
        @DisplayName("invalid batchSize throws IllegalArgumentException")
        void invalidBatchSize() {
            assertThatThrownBy(() -> concreteBackfill(-1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> concreteBackfill(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // ConcurrentIndexMigration (via EntitiesDisplayNameIndex)
    // =========================================================================

    @Nested
    @DisplayName("ConcurrentIndexMigration")
    class ConcurrentIndexMigrationTests {

        private static final String TABLE = "entities_idx_test";

        @BeforeEach
        void setUpTable() throws SQLException {
            createTestTable(TABLE, "tenant_id VARCHAR(255)", "collection_name VARCHAR(255)", "display_name VARCHAR(500)");
        }

        /** Anonymous concrete implementation using the test table. */
        private ConcurrentIndexMigration concreteIndex() {
            return new ConcurrentIndexMigration() {
                @Override protected String tableName()    { return TABLE; }
                @Override protected String indexName()    { return "idx_test_display_name"; }
                @Override protected String indexColumns() { return "tenant_id, collection_name, display_name"; }
                @Override protected String whereClause()  { return "display_name IS NOT NULL"; }
                @Override public String name()            { return "TestConcurrentIndex"; }
            };
        }

        @Test
        @DisplayName("creates index successfully on H2 (falls back to standard CREATE INDEX)")
        void createsIndex() throws SQLException {
            MigrationReport report = concreteIndex().execute(dataSource);
            assertThat(report.success()).isTrue();
            assertThat(report.operation()).isEqualTo("CREATE_INDEX");
        }

        @Test
        @DisplayName("idempotent: second run via IF NOT EXISTS does not fail")
        void idempotent() throws SQLException {
            concreteIndex().execute(dataSource);
            MigrationReport report = concreteIndex().execute(dataSource);
            assertThat(report.success()).isTrue();
        }

        @Test
        @DisplayName("canExecuteInTransaction returns false")
        void notTransactional() {
            assertThat(concreteIndex().canExecuteInTransaction()).isFalse();
        }

        @Test
        @DisplayName("name includes index and table names")
        void nameIncludesContext() {
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
        void setUpTable() throws SQLException {
            // Simulate state after V006: entities table with old_name populated, new_name nullable
            createTestTable(TABLE, "old_name VARCHAR(255)", "new_name VARCHAR(255)");
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                for (int i = 0; i < 20; i++) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO " + TABLE + " (id, old_name, new_name) VALUES (?, ?, NULL)")) {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setString(2, "display_" + i);
                        ps.executeUpdate();
                    }
                }
                conn.commit();
            }
        }

        @Test
        @DisplayName("full pipeline: backfill + concurrent index both succeed")
        void fullPipeline() throws SQLException {
            ConcurrentIndexMigration indexMigration = new ConcurrentIndexMigration() {
                @Override protected String tableName()    { return TABLE; }
                @Override protected String indexName()    { return "idx_e2e_new_name"; }
                @Override protected String indexColumns() { return "new_name"; }
                @Override protected String whereClause()  { return "new_name IS NOT NULL"; }
            };

            DataMigrationService svc = new DataMigrationService(dataSource);
            svc.register(new ColumnRenameMigration(TABLE, "old_name", "new_name", 5))
               .register(indexMigration);

            List<MigrationReport> reports = svc.runAll();

            assertThat(reports).hasSize(2);
            assertThat(reports.get(0).success()).isTrue();
            assertThat(reports.get(0).rowsAffected()).isEqualTo(20L);
            assertThat(reports.get(1).success()).isTrue();
            assertThat(countNulls(TABLE, "new_name")).isEqualTo(0L);
            assertThat(countNonNulls(TABLE, "new_name")).isEqualTo(20L);
        }
    }
}
