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
 * <p>Uses a real Testcontainers PostgreSQL instance (no mocks). Validates credential 
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
    void setUp() throws Exception { 
        HikariConfig config = new HikariConfig(); 
        config.setJdbcUrl(POSTGRES.getJdbcUrl()); 
        config.setUsername(POSTGRES.getUsername()); 
        config.setPassword(POSTGRES.getPassword()); 
        config.setMaximumPoolSize(10); 
        config.setMinimumIdle(2); 
        config.setConnectionTimeout(3_000); 
        dataSource = new HikariDataSource(config); 
        initSchema(); 
    }

    @AfterEach
    void tearDown() throws Exception { 
        try (Connection conn = dataSource.getConnection(); 
             Statement stmt = conn.createStatement()) { 
            stmt.execute("DROP TABLE IF EXISTS auth_sessions, auth_tokens, auth_credentials CASCADE");
        }
        dataSource.close(); 
    }

    // ── Credential storage ────────────────────────────────────────────────────

    @Test
    @DisplayName("credential is persisted and readable by username")
    void credentialPersistAndRead() throws Exception { 
        String username = "user_" + uid(); 
        insertCredential(username, "hashed_pw", "tenant-1"); 
        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT username, tenant_id FROM auth_credentials WHERE username = ?")) {
            ps.setString(1, username); 
            ResultSet rs = ps.executeQuery(); 
            assertThat(rs.next()).isTrue(); 
            assertThat(rs.getString("username")).isEqualTo(username);
            assertThat(rs.getString("tenant_id")).isEqualTo("tenant-1");
        }
    }

    @Test
    @DisplayName("credential update modifies password_hash")
    void credentialUpdate() throws Exception { 
        String username = "user_" + uid(); 
        insertCredential(username, "old_hash", "tenant-1"); 
        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "UPDATE auth_credentials SET password_hash = ? WHERE username = ?")) {
            ps.setString(1, "new_hash"); 
            ps.setString(2, username); 
            int updated = ps.executeUpdate(); 
            assertThat(updated).isEqualTo(1); 
        }
        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT password_hash FROM auth_credentials WHERE username = ?")) {
            ps.setString(1, username); 
            ResultSet rs = ps.executeQuery(); 
            assertThat(rs.next()).isTrue(); 
            assertThat(rs.getString("password_hash")).isEqualTo("new_hash");
        }
    }

    @Test
    @DisplayName("credential deletion removes the row")
    void credentialDelete() throws Exception { 
        String username = "user_" + uid(); 
        insertCredential(username, "hash", "tenant-2"); 
        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "DELETE FROM auth_credentials WHERE username = ?")) {
            ps.setString(1, username); 
            int deleted = ps.executeUpdate(); 
            assertThat(deleted).isEqualTo(1); 
        }
        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT count(*) FROM auth_credentials WHERE username = ?")) { 
            ps.setString(1, username); 
            ResultSet rs = ps.executeQuery(); 
            rs.next(); 
            assertThat(rs.getInt(1)).isZero(); 
        }
    }

    @Test
    @DisplayName("duplicate username violates unique constraint")
    void duplicateUsernameViolatesConstraint() throws Exception { 
        String username = "user_" + uid(); 
        insertCredential(username, "hash", "tenant-1"); 
        assertThatThrownBy(() -> insertCredential(username, "hash2", "tenant-1")) 
                .isInstanceOf(SQLException.class); 
    }

    // ── Token storage ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("token is persisted and retrievable by token_id")
    void tokenPersistAndRead() throws Exception { 
        String tokenId = uid(); 
        String username = "user_" + uid(); 
        insertCredential(username, "hash", "tenant-1"); 
        insertToken(tokenId, username, "ACCESS", Instant.now().plusSeconds(3600).toEpochMilli()); 

        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT token_id, username, token_type FROM auth_tokens WHERE token_id = ?")) {
            ps.setString(1, tokenId); 
            ResultSet rs = ps.executeQuery(); 
            assertThat(rs.next()).isTrue(); 
            assertThat(rs.getString("token_type")).isEqualTo("ACCESS");
        }
    }

    @Test
    @DisplayName("expired tokens can be queried by expiry")
    void expiredTokensQuery() throws Exception { 
        String username = "user_" + uid(); 
        insertCredential(username, "hash", "tenant-1"); 
        String expiredTokenId = uid(); 
        insertToken(expiredTokenId, username, "ACCESS", Instant.now().minusSeconds(10).toEpochMilli()); 

        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT count(*) FROM auth_tokens WHERE expires_at < ?")) { 
            ps.setLong(1, Instant.now().toEpochMilli()); 
            ResultSet rs = ps.executeQuery(); 
            rs.next(); 
            assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1); 
        }
    }

    // ── Session storage ───────────────────────────────────────────────────────

    @Test
    @DisplayName("session is persisted and readable by session_id")
    void sessionPersistAndRead() throws Exception { 
        String sessionId = uid(); 
        String username = "user_" + uid(); 
        insertCredential(username, "hash", "tenant-1"); 
        insertSession(sessionId, username, "tenant-1"); 

        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT session_id, username FROM auth_sessions WHERE session_id = ?")) {
            ps.setString(1, sessionId); 
            ResultSet rs = ps.executeQuery(); 
            assertThat(rs.next()).isTrue(); 
            assertThat(rs.getString("username")).isEqualTo(username);
        }
    }

    @Test
    @DisplayName("session deletion removes the session")
    void sessionDelete() throws Exception { 
        String sessionId = uid(); 
        String username = "user_" + uid(); 
        insertCredential(username, "hash", "tenant-1"); 
        insertSession(sessionId, username, "tenant-1"); 

        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "DELETE FROM auth_sessions WHERE session_id = ?")) {
            ps.setString(1, sessionId); 
            int deleted = ps.executeUpdate(); 
            assertThat(deleted).isEqualTo(1); 
        }
    }

    // ── Connection pool behavior ──────────────────────────────────────────────

    @Test
    @DisplayName("connection pool provides connections within timeout")
    void connectionPoolRespondsWithinTimeout() throws Exception { 
        for (int i = 0; i < 5; i++) { 
            try (Connection conn = dataSource.getConnection()) { 
                assertThat(conn.isValid(1)).isTrue(); 
            }
        }
    }

    @ParameterizedTest(name = "concurrent={0}") 
    @ValueSource(ints = {2, 5, 8}) 
    @DisplayName("concurrent connections do not exceed pool max or deadlock")
    void concurrentConnectionsWithinPool(int threads) throws Exception { 
        CountDownLatch latch = new CountDownLatch(1); 
        AtomicInteger successCount = new AtomicInteger(0); 
        ExecutorService executor = Executors.newFixedThreadPool(threads); 
        for (int i = 0; i < threads; i++) { 
            executor.submit(() -> { 
                try {
                    latch.await(); 
                    try (Connection conn = dataSource.getConnection(); 
                         ResultSet rs = conn.createStatement().executeQuery("SELECT 1")) {
                        if (rs.next()) successCount.incrementAndGet(); 
                    }
                } catch (Exception ignored) {} 
            });
        }
        latch.countDown(); 
        executor.shutdown(); 
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); 
        assertThat(successCount.get()).isEqualTo(threads); 
    }

    // ── Transaction behavior ──────────────────────────────────────────────────

    @Test
    @DisplayName("committed transaction is visible to subsequent reads")
    void committedTransactionIsVisible() throws Exception { 
        String username = "txn_user_" + uid(); 
        try (Connection conn = dataSource.getConnection()) { 
            conn.setAutoCommit(false); 
            try (PreparedStatement ps = conn.prepareStatement( 
                    "INSERT INTO auth_credentials(username, password_hash, tenant_id) VALUES (?, ?, ?)")) { 
                ps.setString(1, username); 
                ps.setString(2, "hash"); 
                ps.setString(3, "txn-tenant"); 
                ps.executeUpdate(); 
            }
            conn.commit(); 
        }
        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT count(*) FROM auth_credentials WHERE username = ?")) { 
            ps.setString(1, username); 
            ResultSet rs = ps.executeQuery(); 
            rs.next(); 
            assertThat(rs.getInt(1)).isEqualTo(1); 
        }
    }

    @Test
    @DisplayName("rolled-back transaction is not visible to subsequent reads")
    void rolledBackTransactionNotVisible() throws Exception { 
        String username = "rollback_user_" + uid(); 
        try (Connection conn = dataSource.getConnection()) { 
            conn.setAutoCommit(false); 
            try (PreparedStatement ps = conn.prepareStatement( 
                    "INSERT INTO auth_credentials(username, password_hash, tenant_id) VALUES (?, ?, ?)")) { 
                ps.setString(1, username); 
                ps.setString(2, "hash"); 
                ps.setString(3, "rollback-tenant"); 
                ps.executeUpdate(); 
            }
            conn.rollback(); 
        }
        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT count(*) FROM auth_credentials WHERE username = ?")) { 
            ps.setString(1, username); 
            ResultSet rs = ps.executeQuery(); 
            rs.next(); 
            assertThat(rs.getInt(1)).isZero(); 
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void initSchema() throws Exception { 
        try (Connection conn = dataSource.getConnection(); 
             Statement stmt = conn.createStatement()) { 
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

    private void insertCredential(String username, String passwordHash, String tenantId) throws Exception { 
        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "INSERT INTO auth_credentials(username, password_hash, tenant_id) VALUES (?, ?, ?)")) { 
            ps.setString(1, username); 
            ps.setString(2, passwordHash); 
            ps.setString(3, tenantId); 
            ps.executeUpdate(); 
        }
    }

    private void insertToken(String tokenId, String username, String type, long expiresAt) throws Exception { 
        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "INSERT INTO auth_tokens(token_id, username, token_type, expires_at) VALUES (?, ?, ?, ?)")) { 
            ps.setString(1, tokenId); 
            ps.setString(2, username); 
            ps.setString(3, type); 
            ps.setLong(4, expiresAt); 
            ps.executeUpdate(); 
        }
    }

    private void insertSession(String sessionId, String username, String tenantId) throws Exception { 
        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "INSERT INTO auth_sessions(session_id, username, tenant_id) VALUES (?, ?, ?)")) { 
            ps.setString(1, sessionId); 
            ps.setString(2, username); 
            ps.setString(3, tenantId); 
            ps.executeUpdate(); 
        }
    }

    private String uid() { 
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12); 
    }
}
