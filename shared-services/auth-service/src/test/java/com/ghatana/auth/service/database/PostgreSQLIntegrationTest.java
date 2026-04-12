/**
 * Auth Service PostgreSQL Integration Test Suite
 *
 * Real database integration tests using Testcontainers PostgreSQL.
 * Tests OIDC session persistence, token storage, and user account management.
 *
 * @doc.type test
 * @doc.purpose PostgreSQL integration for auth-service persistence
 * @doc.layer shared-services
 * @doc.pattern IntegrationTest
 */

package com.ghatana.auth.service.database;

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
 * PostgreSQL integration tests for Auth Service JDBC persistence layer.
 * Uses a real Testcontainers PostgreSQL instance (no mocks). Validates OIDC
 * session storage, token persistence, user account management, CRUD operations,
 * query correctness, and transaction rollback/commit semantics.
 */
@Tag("integration")
@Testcontainers
@DisplayName("Auth Service — PostgreSQL integration tests")
class PostgreSQLIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("auth_service_integration_test")
                    .withUsername("auth_service_test")
                    .withPassword("auth_service_test_pw");

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
            stmt.execute("DROP TABLE IF EXISTS oidc_sessions, platform_tokens, user_accounts CASCADE");
        }
        dataSource.close();
    }

    // ── OIDC session persistence ───────────────────────────────────────────────

    @Test
    @DisplayName("OIDC session is persisted and readable by session_id")
    void oidcSessionPersistAndRead() throws Exception {
        String sessionId = uid();
        insertOidcSession(sessionId, "user@example.com", "tenant-1");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT session_id, email, tenant_id FROM oidc_sessions WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("session_id")).isEqualTo(sessionId);
            assertThat(rs.getString("email")).isEqualTo("user@example.com");
            assertThat(rs.getString("tenant_id")).isEqualTo("tenant-1");
        }
    }

    @Test
    @DisplayName("OIDC session deletion removes the session")
    void oidcSessionDelete() throws Exception {
        String sessionId = uid();
        insertOidcSession(sessionId, "user@example.com", "tenant-1");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM oidc_sessions WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            assertThat(ps.executeUpdate()).isEqualTo(1);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM oidc_sessions WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    @DisplayName("expired OIDC sessions can be queried")
    void expiredOidcSessionsQuery() throws Exception {
        String sessionId = uid();
        insertOidcSessionWithExpiry(sessionId, "user@example.com", "tenant-1", 
                Instant.now().minusSeconds(3600).toEpochMilli());

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM oidc_sessions WHERE expires_at < ?")) {
            ps.setLong(1, Instant.now().toEpochMilli());
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
        }
    }

    // ── Platform token persistence ─────────────────────────────────────────────

    @Test
    @DisplayName("platform token is persisted and readable by token_id")
    void platformTokenPersistAndRead() throws Exception {
        String tokenId = uid();
        String userId = "user-" + uid();
        insertUserAccount(userId, "user@example.com");
        insertPlatformToken(tokenId, userId, "PLATFORM", Instant.now().plusSeconds(900).toEpochMilli());

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT token_id, user_id, token_type FROM platform_tokens WHERE token_id = ?")) {
            ps.setString(1, tokenId);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("token_id")).isEqualTo(tokenId);
            assertThat(rs.getString("user_id")).isEqualTo(userId);
            assertThat(rs.getString("token_type")).isEqualTo("PLATFORM");
        }
    }

    @Test
    @DisplayName("expired platform tokens can be queried")
    void expiredPlatformTokensQuery() throws Exception {
        String userId = "user-" + uid();
        insertUserAccount(userId, "user@example.com");
        String tokenId = uid();
        insertPlatformToken(tokenId, userId, "PLATFORM", Instant.now().minusSeconds(10).toEpochMilli());

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM platform_tokens WHERE expires_at < ?")) {
            ps.setLong(1, Instant.now().toEpochMilli());
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
        }
    }

    // ── User account persistence ─────────────────────────────────────────────

    @Test
    @DisplayName("user account is persisted and readable by user_id")
    void userAccountPersistAndRead() throws Exception {
        String userId = "user-" + uid();
        insertUserAccount(userId, "user@example.com");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT user_id, email FROM user_accounts WHERE user_id = ?")) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("user_id")).isEqualTo(userId);
            assertThat(rs.getString("email")).isEqualTo("user@example.com");
        }
    }

    @Test
    @DisplayName("user account is queryable by email")
    void userAccountQueryByEmail() throws Exception {
        String email = "unique_" + uid() + "@example.com";
        String userId = "user-" + uid();
        insertUserAccount(userId, email);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT user_id FROM user_accounts WHERE email = ?")) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("user_id")).isEqualTo(userId);
        }
    }

    @Test
    @DisplayName("duplicate email violates unique constraint")
    void duplicateEmailViolatesConstraint() throws Exception {
        String email = "dup_" + uid() + "@example.com";
        insertUserAccount("user-1-" + uid(), email);
        assertThatThrownBy(() -> insertUserAccount("user-2-" + uid(), email))
                .isInstanceOf(SQLException.class);
    }

    // ── Transaction behavior ─────────────────────────────────────────────────────

    @Test
    @DisplayName("committed transaction is visible to subsequent reads")
    void committedTransactionVisible() throws Exception {
        String sessionId = "txn_" + uid();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO oidc_sessions(session_id, email, tenant_id, created_at, expires_at) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, sessionId);
                ps.setString(2, "txn@example.com");
                ps.setString(3, "txn-tenant");
                ps.setLong(4, Instant.now().toEpochMilli());
                ps.setLong(5, Instant.now().plusSeconds(3600).toEpochMilli());
                ps.executeUpdate();
            }
            conn.commit();
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM oidc_sessions WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("rolled-back transaction is not visible to subsequent reads")
    void rolledBackTransactionNotVisible() throws Exception {
        String sessionId = "rollback_" + uid();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO oidc_sessions(session_id, email, tenant_id, created_at, expires_at) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, sessionId);
                ps.setString(2, "rollback@example.com");
                ps.setString(3, "rollback-tenant");
                ps.setLong(4, Instant.now().toEpochMilli());
                ps.setLong(5, Instant.now().plusSeconds(3600).toEpochMilli());
                ps.executeUpdate();
            }
            conn.rollback();
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM oidc_sessions WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    // ── Connection pool behavior ────────────────────────────────────────────────

    @ParameterizedTest(name = "concurrent={0}")
    @ValueSource(ints = {2, 5, 8})
    @DisplayName("concurrent connections do not deadlock")
    void concurrentConnectionsNoDeadlock(int threads) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    try (Connection conn = dataSource.getConnection();
                         ResultSet rs = conn.createStatement().executeQuery("SELECT 1")) {
                        if (rs.next()) successes.incrementAndGet();
                    }
                } catch (Exception ignored) {}
            });
        }
        latch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(successes.get()).isEqualTo(threads);
    }

    // ── Foreign key constraints ─────────────────────────────────────────────────

    @Test
    @DisplayName("deleting user account cascades to platform tokens")
    void deleteUserAccountCascadesToTokens() throws Exception {
        String userId = "user-" + uid();
        insertUserAccount(userId, "user@example.com");
        String tokenId = uid();
        insertPlatformToken(tokenId, userId, "PLATFORM", Instant.now().plusSeconds(900).toEpochMilli());

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM user_accounts WHERE user_id = ?")) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM platform_tokens WHERE user_id = ?")) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private void initSchema() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS user_accounts (
                        user_id      TEXT PRIMARY KEY,
                        email        TEXT NOT NULL UNIQUE,
                        name         TEXT,
                        picture_url  TEXT,
                        created_at   BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT,
                        updated_at   BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT
                    )""");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS oidc_sessions (
                        session_id   TEXT PRIMARY KEY,
                        user_id      TEXT REFERENCES user_accounts(user_id) ON DELETE SET NULL,
                        email        TEXT NOT NULL,
                        tenant_id    TEXT NOT NULL,
                        created_at   BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT,
                        expires_at   BIGINT NOT NULL
                    )""");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS platform_tokens (
                        token_id     TEXT PRIMARY KEY,
                        user_id      TEXT NOT NULL REFERENCES user_accounts(user_id) ON DELETE CASCADE,
                        token_type   TEXT NOT NULL,
                        expires_at   BIGINT NOT NULL,
                        created_at   BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT
                    )""");
        }
    }

    private void insertOidcSession(String sessionId, String email, String tenantId) throws Exception {
        insertOidcSessionWithExpiry(sessionId, email, tenantId, Instant.now().plusSeconds(3600).toEpochMilli());
    }

    private void insertOidcSessionWithExpiry(String sessionId, String email, String tenantId, long expiresAt) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO oidc_sessions(session_id, email, tenant_id, created_at, expires_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, sessionId);
            ps.setString(2, email);
            ps.setString(3, tenantId);
            ps.setLong(4, Instant.now().toEpochMilli());
            ps.setLong(5, expiresAt);
            ps.executeUpdate();
        }
    }

    private void insertPlatformToken(String tokenId, String userId, String tokenType, long expiresAt) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO platform_tokens(token_id, user_id, token_type, expires_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, tokenId);
            ps.setString(2, userId);
            ps.setString(3, tokenType);
            ps.setLong(4, expiresAt);
            ps.executeUpdate();
        }
    }

    private void insertUserAccount(String userId, String email) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO user_accounts(user_id, email, created_at, updated_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, userId);
            ps.setString(2, email);
            ps.setLong(3, Instant.now().toEpochMilli());
            ps.setLong(4, Instant.now().toEpochMilli());
            ps.executeUpdate();
        }
    }

    private String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
