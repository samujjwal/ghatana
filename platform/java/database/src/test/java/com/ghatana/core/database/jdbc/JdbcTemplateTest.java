package com.ghatana.core.database.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for JdbcTemplate using H2 in-memory database
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("JdbcTemplate — JDBC abstraction with resource management")
class JdbcTemplateTest {

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:jdbc-template-test;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        DataSource dataSource = ds;

        jdbc = new JdbcTemplate(dataSource);

        // Create a simple test table
        jdbc.update("DROP TABLE IF EXISTS test_users");
        jdbc.update("CREATE TABLE test_users (id INT PRIMARY KEY, name VARCHAR(100), active BOOLEAN)");
        jdbc.update("INSERT INTO test_users VALUES (1, 'Alice', TRUE)");
        jdbc.update("INSERT INTO test_users VALUES (2, 'Bob', FALSE)");
        jdbc.update("INSERT INTO test_users VALUES (3, 'Charlie', TRUE)");
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor throws NullPointerException for null DataSource")
    void constructorThrowsForNullDataSource() {
        assertThatThrownBy(() -> new JdbcTemplate(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── queryForObject ────────────────────────────────────────────────────────

    @Test
    @DisplayName("queryForObject returns populated Optional when row exists")
    void queryForObjectReturnsPresentOptional() {
        Optional<String> name = jdbc.queryForObject(
                "SELECT name FROM test_users WHERE id = ?",
                rs -> rs.getString("name"),
                1);

        assertThat(name).contains("Alice");
    }

    @Test
    @DisplayName("queryForObject returns empty Optional when no row matches")
    void queryForObjectReturnsEmptyWhenNotFound() {
        Optional<String> name = jdbc.queryForObject(
                "SELECT name FROM test_users WHERE id = ?",
                rs -> rs.getString("name"),
                999);

        assertThat(name).isEmpty();
    }

    @Test
    @DisplayName("queryForObject throws JdbcException for blank SQL")
    void queryForObjectThrowsForBlankSql() {
        assertThatThrownBy(() -> jdbc.queryForObject(
                "   ",
                rs -> rs.getString("name")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("queryForObject throws NullPointerException for null rowMapper")
    void queryForObjectThrowsForNullRowMapper() {
        assertThatThrownBy(() -> jdbc.queryForObject(
                "SELECT name FROM test_users WHERE id = ?",
                null,
                1))
                .isInstanceOf(NullPointerException.class);
    }

    // ── queryForList ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("queryForList returns all matching rows")
    void queryForListReturnsAllMatchingRows() {
        List<String> names = jdbc.queryForList(
                "SELECT name FROM test_users WHERE active = ?",
                rs -> rs.getString("name"),
                true);

        assertThat(names).containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    @DisplayName("queryForList returns empty list when no rows match")
    void queryForListReturnsEmptyListWhenNoMatch() {
        List<String> names = jdbc.queryForList(
                "SELECT name FROM test_users WHERE id = ?",
                rs -> rs.getString("name"),
                999);

        assertThat(names).isEmpty();
    }

    @Test
    @DisplayName("queryForList returns all rows without parameters")
    void queryForListReturnsAllRowsNoParams() {
        List<Integer> ids = jdbc.queryForList(
                "SELECT id FROM test_users ORDER BY id",
                rs -> rs.getInt("id"));

        assertThat(ids).containsExactly(1, 2, 3);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update returns number of affected rows")
    void updateReturnsAffectedRowCount() {
        int affected = jdbc.update(
                "UPDATE test_users SET active = ? WHERE id = ?",
                false,
                1);

        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("update returns 0 when no rows match")
    void updateReturnsZeroWhenNoMatch() {
        int affected = jdbc.update(
                "UPDATE test_users SET active = ? WHERE id = ?",
                false,
                999);

        assertThat(affected).isEqualTo(0);
    }

    @Test
    @DisplayName("update inserts a new row and returns 1")
    void updateInsertsNewRow() {
        int affected = jdbc.update(
                "INSERT INTO test_users VALUES (?, ?, ?)",
                10, "Dave", true);

        assertThat(affected).isEqualTo(1);

        Optional<String> name = jdbc.queryForObject(
                "SELECT name FROM test_users WHERE id = ?",
                rs -> rs.getString("name"),
                10);
        assertThat(name).contains("Dave");
    }

    @Test
    @DisplayName("update deletes a row and returns 1")
    void updateDeletesRow() {
        int affected = jdbc.update("DELETE FROM test_users WHERE id = ?", 2);

        assertThat(affected).isEqualTo(1);

        Optional<String> found = jdbc.queryForObject(
                "SELECT name FROM test_users WHERE id = ?",
                rs -> rs.getString("name"),
                2);
        assertThat(found).isEmpty();
    }

    // ── JdbcException propagation ─────────────────────────────────────────────

    @Test
    @DisplayName("queryForObject wraps SQL error in JdbcException")
    void queryForObjectWrapsErrorInJdbcException() {
        assertThatThrownBy(() -> jdbc.queryForObject(
                "SELECT this_column_does_not_exist FROM test_users",
                rs -> rs.getString(1)))
                .isInstanceOf(JdbcException.class);
    }

    @Test
    @DisplayName("update wraps SQL error in JdbcException")
    void updateWrapsErrorInJdbcException() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO nonexistent_table VALUES (1)"))
                .isInstanceOf(JdbcException.class);
    }
}
