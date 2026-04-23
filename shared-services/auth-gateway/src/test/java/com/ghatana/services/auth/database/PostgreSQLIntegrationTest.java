package com.ghatana.services.auth.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * PostgreSQL integration tests for auth-gateway JDBC persistence layer.
 *
 * <p>Uses a real Testcontainers PostgreSQL instance (no mocks). Validates credential // GH-90000
 * storage, token storage, session persistence, connection pool behaviour, and
 * transaction rollback/commit semantics.
 *
 * @doc.type    class
 * @doc.purpose PostgreSQL integration: credentials, tokens, sessions, pooling, transactions
 * @doc.layer   product
 * @doc.pattern IntegrationTest
 */
@Tag("integration")
@Testcontainers
@DisplayName("Auth-Gateway — PostgreSQL integration tests")
class PostgreSQLIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("auth_integration_test")
                    .withUsername("auth_test")
                    .withPassword("auth_test_pw");

    private HikariDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        HikariConfig config = new HikariConfig(); // GH-90000
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); // GH-90000
        config.setUsername(POSTGRES.getUsername()); // GH-90000
        config.setPassword(POSTGRES.getPassword()); // GH-90000
        config.setMaximumPoolSize(10); // GH-90000
        config.setMinimumIdle(2); // GH-90000
        config.setConnectionTimeout(3_000); // GH-90000
        dataSource = new HikariDataSource(config); // GH-90000
        initSchema(); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement()) { // GH-90000
            stmt.execute("DROP TABLE IF EXISTS auth_sessions, auth_tokens, auth_credentials CASCADE");
        }
        dataSource.close(); // GH-90000
    }

    // ── Credential storage ────────────────────────────────────────────────────

    @Test
    @DisplayName("credential is persisted and readable by username")
    void credentialPersistAndRead() throws Exception { // GH-90000
        String username = "user_" + uid(); // GH-90000
        insertCredential(username, "hashed_pw", "tenant-1"); // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT username, tenant_id FROM auth_credentials WHERE username = ?")) {
            ps.setString(1, username); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            assertThat(rs.next()).isTrue(); // GH-90000
            assertThat(rs.getString("username")).isEqualTo(username);
            assertThat(rs.getString("tenant_id")).isEqualTo("tenant-1");
        }
    }

    @Test
    @DisplayName("credential update modifies password_hash")
    void credentialUpdate() throws Exception { // GH-90000
        String username = "user_" + uid(); // GH-90000
        insertCredential(username, "old_hash", "tenant-1"); // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "UPDATE auth_credentials SET password_hash = ? WHERE username = ?")) {
            ps.setString(1, "new_hash"); // GH-90000
            ps.setString(2, username); // GH-90000
            int updated = ps.executeUpdate(); // GH-90000
            assertThat(updated).isEqualTo(1); // GH-90000
        }
        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT password_hash FROM auth_credentials WHERE username = ?")) {
            ps.setString(1, username); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            assertThat(rs.next()).isTrue(); // GH-90000
            assertThat(rs.getString("password_hash")).isEqualTo("new_hash");
        }
    }

    @Test
    @DisplayName("credential deletion removes the row")
    void credentialDelete() throws Exception { // GH-90000
        String username = "user_" + uid(); // GH-90000
        insertCredential(username, "hash", "tenant-2"); // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "DELETE FROM auth_credentials WHERE username = ?")) {
            ps.setString(1, username); // GH-90000
            int deleted = ps.executeUpdate(); // GH-90000
            assertThat(deleted).isEqualTo(1); // GH-90000
        }
        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT count(*) FROM auth_credentials WHERE username = ?")) { // GH-90000
            ps.setString(1, username); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            rs.next(); // GH-90000
            assertThat(rs.getInt(1)).isZero(); // GH-90000
        }
    }

    @Test
    @DisplayName("duplicate username violates unique constraint")
    void duplicateUsernameViolatesConstraint() throws Exception { // GH-90000
        String username = "user_" + uid(); // GH-90000
        insertCredential(username, "hash", "tenant-1"); // GH-90000
        assertThatThrownBy(() -> insertCredential(username, "hash2", "tenant-1")) // GH-90000
                .isInstanceOf(SQLException.class); // GH-90000
    }

    // ── Token storage ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("token is persisted and retrievable by token_id")
    void tokenPersistAndRead() throws Exception { // GH-90000
        String tokenId = uid(); // GH-90000
        String username = "user_" + uid(); // GH-90000
        insertCredential(username, "hash", "tenant-1"); // GH-90000
        insertToken(tokenId, username, "ACCESS", Instant.now().plusSeconds(3600).toEpochMilli()); // GH-90000

        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT token_id, username, token_type FROM auth_tokens WHERE token_id = ?")) {
            ps.setString(1, tokenId); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            assertThat(rs.next()).isTrue(); // GH-90000
            assertThat(rs.getString("token_type")).isEqualTo("ACCESS");
        }
    }

    @Test
    @DisplayName("expired tokens can be queried by expiry")
    void expiredTokensQuery() throws Exception { // GH-90000
        String username = "user_" + uid(); // GH-90000
        insertCredential(username, "hash", "tenant-1"); // GH-90000
        String expiredTokenId = uid(); // GH-90000
        insertToken(expiredTokenId, username, "ACCESS", Instant.now().minusSeconds(10).toEpochMilli()); // GH-90000

        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT count(*) FROM auth_tokens WHERE expires_at < ?")) { // GH-90000
            ps.setLong(1, Instant.now().toEpochMilli()); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            rs.next(); // GH-90000
            assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1); // GH-90000
        }
    }

    // ── Session storage ───────────────────────────────────────────────────────

    @Test
    @DisplayName("session is persisted and readable by session_id")
    void sessionPersistAndRead() throws Exception { // GH-90000
        String sessionId = uid(); // GH-90000
        String username = "user_" + uid(); // GH-90000
        insertCredential(username, "hash", "tenant-1"); // GH-90000
        insertSession(sessionId, username, "tenant-1"); // GH-90000

        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT session_id, username FROM auth_sessions WHERE session_id = ?")) {
            ps.setString(1, sessionId); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            assertThat(rs.next()).isTrue(); // GH-90000
            assertThat(rs.getString("username")).isEqualTo(username);
        }
    }

    @Test
    @DisplayName("session deletion removes the session")
    void sessionDelete() throws Exception { // GH-90000
        String sessionId = uid(); // GH-90000
        String username = "user_" + uid(); // GH-90000
        insertCredential(username, "hash", "tenant-1"); // GH-90000
        insertSession(sessionId, username, "tenant-1"); // GH-90000

        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "DELETE FROM auth_sessions WHERE session_id = ?")) {
            ps.setString(1, sessionId); // GH-90000
            int deleted = ps.executeUpdate(); // GH-90000
            assertThat(deleted).isEqualTo(1); // GH-90000
        }
    }

    // ── Connection pool behavior ──────────────────────────────────────────────

    @Test
    @DisplayName("connection pool provides connections within timeout")
    void connectionPoolRespondsWithinTimeout() throws Exception { // GH-90000
        for (int i = 0; i < 5; i++) { // GH-90000
            try (Connection conn = dataSource.getConnection()) { // GH-90000
                assertThat(conn.isValid(1)).isTrue(); // GH-90000
            }
        }
    }

    @ParameterizedTest(name = "concurrent={0}") // GH-90000
    @ValueSource(ints = {2, 5, 8}) // GH-90000
    @DisplayName("concurrent connections do not exceed pool max or deadlock")
    void concurrentConnectionsWithinPool(int threads) throws Exception { // GH-90000
        CountDownLatch latch = new CountDownLatch(1); // GH-90000
        AtomicInteger successCount = new AtomicInteger(0); // GH-90000
        ExecutorService executor = Executors.newFixedThreadPool(threads); // GH-90000
        for (int i = 0; i < threads; i++) { // GH-90000
            executor.submit(() -> { // GH-90000
                try {
                    latch.await(); // GH-90000
                    try (Connection conn = dataSource.getConnection(); // GH-90000
                         ResultSet rs = conn.createStatement().executeQuery("SELECT 1")) {
                        if (rs.next()) successCount.incrementAndGet(); // GH-90000
                    }
                } catch (Exception ignored) {} // GH-90000
            });
        }
        latch.countDown(); // GH-90000
        executor.shutdown(); // GH-90000
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); // GH-90000
        assertThat(successCount.get()).isEqualTo(threads); // GH-90000
    }

    // ── Transaction behavior ──────────────────────────────────────────────────

    @Test
    @DisplayName("committed transaction is visible to subsequent reads")
    void committedTransactionIsVisible() throws Exception { // GH-90000
        String username = "txn_user_" + uid(); // GH-90000
        try (Connection conn = dataSource.getConnection()) { // GH-90000
            conn.setAutoCommit(false); // GH-90000
            try (PreparedStatement ps = conn.prepareStatement( // GH-90000
                    "INSERT INTO auth_credentials(username, password_hash, tenant_id) VALUES (?, ?, ?)")) { // GH-90000
                ps.setString(1, username); // GH-90000
                ps.setString(2, "hash"); // GH-90000
                ps.setString(3, "txn-tenant"); // GH-90000
                ps.executeUpdate(); // GH-90000
            }
            conn.commit(); // GH-90000
        }
        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT count(*) FROM auth_credentials WHERE username = ?")) { // GH-90000
            ps.setString(1, username); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            rs.next(); // GH-90000
            assertThat(rs.getInt(1)).isEqualTo(1); // GH-90000
        }
    }

    @Test
    @DisplayName("rolled-back transaction is not visible to subsequent reads")
    void rolledBackTransactionNotVisible() throws Exception { // GH-90000
        String username = "rollback_user_" + uid(); // GH-90000
        try (Connection conn = dataSource.getConnection()) { // GH-90000
            conn.setAutoCommit(false); // GH-90000
            try (PreparedStatement ps = conn.prepareStatement( // GH-90000
                    "INSERT INTO auth_credentials(username, password_hash, tenant_id) VALUES (?, ?, ?)")) { // GH-90000
                ps.setString(1, username); // GH-90000
                ps.setString(2, "hash"); // GH-90000
                ps.setString(3, "rollback-tenant"); // GH-90000
                ps.executeUpdate(); // GH-90000
            }
            conn.rollback(); // GH-90000
        }
        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT count(*) FROM auth_credentials WHERE username = ?")) { // GH-90000
            ps.setString(1, username); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            rs.next(); // GH-90000
            assertThat(rs.getInt(1)).isZero(); // GH-90000
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void initSchema() throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement()) { // GH-90000
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS auth_credentials (
                        id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        username       TEXT NOT NULL UNIQUE,
                        password_hash  TEXT NOT NULL,
                        tenant_id      TEXT NOT NULL,
                        created_at     BIGINT NOT NULL DEFAULT EXTRACT(EPOCH FROM now()) * 1000
                    )""");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS auth_tokens (
                        token_id    TEXT PRIMARY KEY,
                        username    TEXT NOT NULL REFERENCES auth_credentials(username) ON DELETE CASCADE,
                        token_type  TEXT NOT NULL,
                        expires_at  BIGINT NOT NULL
                    )""");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS auth_sessions (
                        session_id  TEXT PRIMARY KEY,
                        username    TEXT NOT NULL REFERENCES auth_credentials(username) ON DELETE CASCADE,
                        tenant_id   TEXT NOT NULL,
                        created_at  BIGINT NOT NULL DEFAULT EXTRACT(EPOCH FROM now()) * 1000
                    )""");
        }
    }

    private void insertCredential(String username, String passwordHash, String tenantId) throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "INSERT INTO auth_credentials(username, password_hash, tenant_id) VALUES (?, ?, ?)")) { // GH-90000
            ps.setString(1, username); // GH-90000
            ps.setString(2, passwordHash); // GH-90000
            ps.setString(3, tenantId); // GH-90000
            ps.executeUpdate(); // GH-90000
        }
    }

    private void insertToken(String tokenId, String username, String type, long expiresAt) throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "INSERT INTO auth_tokens(token_id, username, token_type, expires_at) VALUES (?, ?, ?, ?)")) { // GH-90000
            ps.setString(1, tokenId); // GH-90000
            ps.setString(2, username); // GH-90000
            ps.setString(3, type); // GH-90000
            ps.setLong(4, expiresAt); // GH-90000
            ps.executeUpdate(); // GH-90000
        }
    }

    private void insertSession(String sessionId, String username, String tenantId) throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "INSERT INTO auth_sessions(session_id, username, tenant_id) VALUES (?, ?, ?)")) { // GH-90000
            ps.setString(1, sessionId); // GH-90000
            ps.setString(2, username); // GH-90000
            ps.setString(3, tenantId); // GH-90000
            ps.executeUpdate(); // GH-90000
        }
    }

    private String uid() { // GH-90000
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12); // GH-90000
    }
}
