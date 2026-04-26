/**
 * @doc.type test
 * @doc.purpose Testcontainers-backed integration tests for JdbcTemplate against a real PostgreSQL instance
 * @doc.layer platform
 * @doc.pattern IntegrationTest
 */
package com.ghatana.platform.integration;

import com.ghatana.core.database.jdbc.JdbcTemplate;
import com.ghatana.platform.testing.PlatformIntegrationTestBase;
import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link JdbcTemplate} using a real PostgreSQL container.
 *
 * <p>Verifies CRUD operations, batch inserts, transaction isolation, and
 * empty-result handling against an actual PostgreSQL database rather than
 * an in-memory simulation.
 */
@DisplayName("JdbcTemplate – PostgreSQL container integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JdbcTemplateContainerTest extends PlatformIntegrationTestBase {

    @Override
    protected boolean requiresPostgres() {
        return true;
    }

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DataSource ds = getDataSource();
        jdbc = new JdbcTemplate(ds);

        // Create a local test table scoped to this test run
        jdbc.update("""
            CREATE TABLE IF NOT EXISTS it_kv_store (
                id      BIGSERIAL   PRIMARY KEY,
                k       TEXT        NOT NULL UNIQUE,
                v       TEXT        NOT NULL,
                created TIMESTAMPTZ DEFAULT now()
            )
            """);
        jdbc.update("TRUNCATE TABLE it_kv_store");
    }

    @AfterEach
    void tearDown() {
        try {
            jdbc.update("DROP TABLE IF EXISTS it_kv_store");
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }

    // ── INSERT + SELECT ───────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("update inserts a row and returns affected-row count of 1")
    void insertRowReturnsOne() {
        int affected = jdbc.update(
            "INSERT INTO it_kv_store (k, v) VALUES (?, ?)", "key1", "val1");

        assertThat(affected).isEqualTo(1);
    }

    @Test
    @Order(2)
    @DisplayName("queryForObject retrieves an inserted row by key")
    void queryForObjectRetrievesInsertedRow() {
        jdbc.update("INSERT INTO it_kv_store (k, v) VALUES (?, ?)", "hello", "world");

        Optional<String> result = jdbc.queryForObject(
            "SELECT v FROM it_kv_store WHERE k = ?",
            rs -> rs.getString("v"),
            "hello"
        );

        assertThat(result).contains("world");
    }

    @Test
    @Order(3)
    @DisplayName("queryForObject returns empty Optional for a missing key")
    void queryForObjectReturnsEmptyForMissingKey() {
        Optional<String> result = jdbc.queryForObject(
            "SELECT v FROM it_kv_store WHERE k = ?",
            rs -> rs.getString("v"),
            "nonexistent"
        );

        assertThat(result).isEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("queryForList returns all inserted rows")
    void queryForListReturnsAllRows() {
        jdbc.update("INSERT INTO it_kv_store (k, v) VALUES (?, ?)", "k1", "v1");
        jdbc.update("INSERT INTO it_kv_store (k, v) VALUES (?, ?)", "k2", "v2");
        jdbc.update("INSERT INTO it_kv_store (k, v) VALUES (?, ?)", "k3", "v3");

        List<String> keys = jdbc.queryForList(
            "SELECT k FROM it_kv_store ORDER BY k",
            rs -> rs.getString("k")
        );

        assertThat(keys).containsExactly("k1", "k2", "k3");
    }

    @Test
    @Order(5)
    @DisplayName("queryForList returns an empty list when no rows match")
    void queryForListReturnsEmptyListWhenNoRows() {
        List<String> result = jdbc.queryForList(
            "SELECT k FROM it_kv_store",
            rs -> rs.getString("k")
        );

        assertThat(result).isEmpty();
    }

    // ── UPDATE / DELETE ───────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("update modifies an existing row and returns affected-row count")
    void updateModifiesExistingRow() {
        jdbc.update("INSERT INTO it_kv_store (k, v) VALUES (?, ?)", "mutable", "original");

        int affected = jdbc.update(
            "UPDATE it_kv_store SET v = ? WHERE k = ?", "updated", "mutable");

        assertThat(affected).isEqualTo(1);

        Optional<String> value = jdbc.queryForObject(
            "SELECT v FROM it_kv_store WHERE k = ?", rs -> rs.getString("v"), "mutable");
        assertThat(value).contains("updated");
    }

    @Test
    @Order(7)
    @DisplayName("update returns 0 when no rows match the predicate")
    void updateReturnsZeroWhenNoMatch() {
        int affected = jdbc.update(
            "UPDATE it_kv_store SET v = ? WHERE k = ?", "x", "ghost");

        assertThat(affected).isZero();
    }

    @Test
    @Order(8)
    @DisplayName("DELETE removes the correct row")
    void deleteRemovesRow() {
        jdbc.update("INSERT INTO it_kv_store (k, v) VALUES (?, ?)", "to-delete", "bye");

        jdbc.update("DELETE FROM it_kv_store WHERE k = ?", "to-delete");

        Optional<String> result = jdbc.queryForObject(
            "SELECT v FROM it_kv_store WHERE k = ?", rs -> rs.getString("v"), "to-delete");
        assertThat(result).isEmpty();
    }

    // ── Batch inserts ─────────────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("batchUpdate inserts multiple rows in a single round-trip")
    void batchUpdateInsertsMultipleRows() {
        record KV(String key, String value) {}
        List<KV> entries = List.of(
            new KV("b1", "val-b1"),
            new KV("b2", "val-b2"),
            new KV("b3", "val-b3"),
            new KV("b4", "val-b4"),
            new KV("b5", "val-b5")
        );

        jdbc.batchUpdate(
            "INSERT INTO it_kv_store (k, v) VALUES (?, ?)",
            entries,
            (ps, kv) -> {
                ps.setString(1, kv.key());
                ps.setString(2, kv.value());
            }
        );

        List<String> keys = jdbc.queryForList(
            "SELECT k FROM it_kv_store ORDER BY k",
            rs -> rs.getString("k")
        );
        assertThat(keys).containsExactly("b1", "b2", "b3", "b4", "b5");
    }

    // ── Transaction isolation ─────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("inTransaction commits both statements atomically")
    void transactionCommitsBothStatementsAtomically() {
        jdbc.inTransaction(txJdbc -> {
            txJdbc.update("INSERT INTO it_kv_store (k, v) VALUES (?, ?)", "tx-a", "alpha");
            txJdbc.update("INSERT INTO it_kv_store (k, v) VALUES (?, ?)", "tx-b", "beta");
        });

        List<String> keys = jdbc.queryForList(
            "SELECT k FROM it_kv_store ORDER BY k",
            rs -> rs.getString("k")
        );
        assertThat(keys).containsExactlyInAnyOrder("tx-a", "tx-b");
    }

    @Test
    @Order(11)
    @DisplayName("inTransaction rolls back on exception — no rows persisted")
    void transactionRollsBackOnException() {
        assertThatThrownBy(() ->
            jdbc.inTransaction(txJdbc -> {
                txJdbc.update("INSERT INTO it_kv_store (k, v) VALUES (?, ?)", "rollback-key", "value");
                // Force a constraint violation to trigger rollback
                txJdbc.update("INSERT INTO it_kv_store (k, v) VALUES (?, ?)", "rollback-key", "duplicate");
            })
        ).isInstanceOf(Exception.class);

        Optional<String> result = jdbc.queryForObject(
            "SELECT v FROM it_kv_store WHERE k = ?",
            rs -> rs.getString("v"),
            "rollback-key"
        );
        assertThat(result).isEmpty(); // rollback ensures nothing was persisted
    }

    // ── Scalar queries ────────────────────────────────────────────────────

    @Test
    @Order(12)
    @DisplayName("queryForObject COUNT returns the correct row count")
    void countQueryIsAccurate() {
        jdbc.update("INSERT INTO it_kv_store (k, v) VALUES (?, ?)", "c1", "x");
        jdbc.update("INSERT INTO it_kv_store (k, v) VALUES (?, ?)", "c2", "x");

        Optional<Long> count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM it_kv_store",
            rs -> rs.getLong(1)
        );

        assertThat(count).contains(2L);
    }
}
