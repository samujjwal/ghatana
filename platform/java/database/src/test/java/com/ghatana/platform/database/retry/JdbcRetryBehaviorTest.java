package com.ghatana.platform.database.retry;

import com.ghatana.core.database.jdbc.JdbcException;
import com.ghatana.core.database.jdbc.JdbcTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for JDBC retry behavior on transient failures.
 *
 * <p>The {@link JdbcTemplate} does not have built-in retry logic — these tests
 * document and validate the contract that:
 * <ul>
 *   <li>Transient connection failures surface as {@link JdbcException}.</li>
 *   <li>A retry wrapper around {@link JdbcTemplate} can absorb transient failures.</li>
 *   <li>Permanent failures propagate after all retry attempts are exhausted.</li>
 *   <li>Successful recovery on a later attempt returns the expected result.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose JDBC transient-retry contract tests for database module
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("JDBC Retry Behavior")
@Tag("unit")
class JdbcRetryBehaviorTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /**
     * DataSource that throws {@link SQLException} for the first {@code failCount}
     * invocations of {@link #getConnection()}, then delegates to an H2 in-memory DB.
     */
    private static DataSource flakyDataSource(int failCount, DataSource delegate) {
        AtomicInteger attempts = new AtomicInteger(0);
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                int attempt = attempts.incrementAndGet();
                if (attempt <= failCount) {
                    throw new SQLException("Transient connection error (attempt " + attempt + ")", "08006");
                }
                return delegate.getConnection();
            }

            @Override
            public Connection getConnection(String u, String p) throws SQLException {
                return getConnection();
            }

            @Override public PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(PrintWriter out) {}
            @Override public void setLoginTimeout(int s) {}
            @Override public int getLoginTimeout() { return 0; }
            @Override public Logger getParentLogger() { return Logger.getAnonymousLogger(); }
            @Override public <T> T unwrap(Class<T> i) throws SQLException { throw new SQLException("not a wrapper"); }
            @Override public boolean isWrapperFor(Class<?> i) { return false; }
        };
    }

    /** Build an H2 in-memory DataSource with a simple table. */
    private static DataSource h2DataSource() throws SQLException {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:retry-test-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE items (id INT PRIMARY KEY, name VARCHAR(100))");
            stmt.execute("INSERT INTO items VALUES (1, 'alpha')");
            stmt.execute("INSERT INTO items VALUES (2, 'beta')");
        }
        return ds;
    }

    /**
     * Minimal retry wrapper: retries the given JDBC operation up to {@code maxAttempts}
     * times, with {@code delayMs} between attempts, rethrows on exhaustion.
     */
    private static <T> T withRetry(int maxAttempts, long delayMs, RetryableOp<T> op) throws Exception {
        Exception last = null;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                return op.run();
            } catch (JdbcException e) {
                last = e;
                if (i < maxAttempts - 1 && delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            }
        }
        throw last;
    }

    @FunctionalInterface
    interface RetryableOp<T> {
        T run() throws Exception;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Single-failure recovery")
    class SingleFailureRecovery {

        @Test
        @DisplayName("recovers from one transient failure and returns correct result")
        void recoversFromOneTransientFailure() throws Exception {
            DataSource stable = h2DataSource();
            DataSource flaky = flakyDataSource(1, stable);

            JdbcTemplate jdbc = new JdbcTemplate(flaky);

            Optional<String> result = withRetry(3, 0, () ->
                    jdbc.queryForObject("SELECT name FROM items WHERE id = ?",
                            rs -> rs.getString("name"), 1));

            assertThat(result).isPresent().hasValue("alpha");
        }

        @Test
        @DisplayName("recovers from two transient failures before succeeding")
        void recoversFromTwoTransientFailures() throws Exception {
            DataSource stable = h2DataSource();
            DataSource flaky = flakyDataSource(2, stable);

            JdbcTemplate jdbc = new JdbcTemplate(flaky);

            List<String> result = withRetry(3, 0, () ->
                    jdbc.queryForList("SELECT name FROM items ORDER BY id",
                            rs -> rs.getString("name")));

            assertThat(result).containsExactly("alpha", "beta");
        }
    }

    @Nested
    @DisplayName("Exhausted retries")
    class ExhaustedRetries {

        @Test
        @DisplayName("propagates JdbcException after all retry attempts fail")
        void propagatesAfterExhaustedRetries() throws Exception {
            DataSource stable = h2DataSource();
            DataSource flaky = flakyDataSource(5, stable); // always fails within 3 retries

            JdbcTemplate jdbc = new JdbcTemplate(flaky);

            assertThatExceptionOfType(JdbcException.class)
                    .isThrownBy(() ->
                            withRetry(3, 0, () ->
                                    jdbc.queryForObject("SELECT name FROM items WHERE id = ?",
                                            rs -> rs.getString("name"), 1)))
                    .withMessageContaining("Failed to execute query")
                    .withCauseInstanceOf(SQLException.class);
        }

        @Test
        @DisplayName("attempt count is respected — no extra calls beyond maxAttempts")
        void exactAttemptCountRespected() throws Exception {
            AtomicInteger calls = new AtomicInteger(0);
            DataSource alwaysFails = new DataSource() {
                @Override
                public Connection getConnection() throws SQLException {
                    calls.incrementAndGet();
                    throw new SQLException("permanent failure", "08006");
                }
                @Override public Connection getConnection(String u, String p) throws SQLException { return getConnection(); }
                @Override public PrintWriter getLogWriter() { return null; }
                @Override public void setLogWriter(PrintWriter o) {}
                @Override public void setLoginTimeout(int s) {}
                @Override public int getLoginTimeout() { return 0; }
                @Override public Logger getParentLogger() { return Logger.getAnonymousLogger(); }
                @Override public <T> T unwrap(Class<T> i) throws SQLException { throw new SQLException(); }
                @Override public boolean isWrapperFor(Class<?> i) { return false; }
            };

            JdbcTemplate jdbc = new JdbcTemplate(alwaysFails);

            assertThatExceptionOfType(JdbcException.class)
                    .isThrownBy(() -> withRetry(3, 0, () ->
                            jdbc.queryForObject("SELECT 1", rs -> rs.getInt(1))));

            assertThat(calls.get()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Immediate success")
    class ImmediateSuccess {

        @Test
        @DisplayName("succeeds on first attempt — zero retries consumed")
        void succeedsFirstAttemptNoRetry() throws Exception {
            AtomicInteger calls = new AtomicInteger(0);
            DataSource stable = h2DataSource();
            DataSource counting = new DataSource() {
                @Override
                public Connection getConnection() throws SQLException {
                    calls.incrementAndGet();
                    return stable.getConnection();
                }
                @Override public Connection getConnection(String u, String p) throws SQLException { return getConnection(); }
                @Override public PrintWriter getLogWriter() { return null; }
                @Override public void setLogWriter(PrintWriter o) {}
                @Override public void setLoginTimeout(int s) {}
                @Override public int getLoginTimeout() { return 0; }
                @Override public Logger getParentLogger() { return Logger.getAnonymousLogger(); }
                @Override public <T> T unwrap(Class<T> i) throws SQLException { throw new SQLException(); }
                @Override public boolean isWrapperFor(Class<?> i) { return false; }
            };

            JdbcTemplate jdbc = new JdbcTemplate(counting);

            Optional<String> result = withRetry(3, 0, () ->
                    jdbc.queryForObject("SELECT name FROM items WHERE id = ?",
                            rs -> rs.getString("name"), 2));

            assertThat(result).hasValue("beta");
            assertThat(calls.get()).isEqualTo(1); // exactly one getConnection call
        }
    }

    @Nested
    @DisplayName("Retry on update operations")
    class RetryOnUpdateOperations {

        @Test
        @DisplayName("update recovers from one transient failure")
        void updateRecoversFromTransientFailure() throws Exception {
            DataSource stable = h2DataSource();
            DataSource flaky = flakyDataSource(1, stable);

            JdbcTemplate jdbc = new JdbcTemplate(flaky);

            int affected = withRetry(3, 0, () ->
                    jdbc.update("UPDATE items SET name = ? WHERE id = ?", "alpha-updated", 1));

            assertThat(affected).isEqualTo(1);

            // Verify the update landed
            JdbcTemplate stable2 = new JdbcTemplate(stable);
            Optional<String> name = stable2.queryForObject(
                    "SELECT name FROM items WHERE id = ?", rs -> rs.getString("name"), 1);
            assertThat(name).hasValue("alpha-updated");
        }
    }
}
