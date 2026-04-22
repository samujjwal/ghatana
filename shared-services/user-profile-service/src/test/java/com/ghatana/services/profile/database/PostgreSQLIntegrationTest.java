package com.ghatana.services.profile.database;

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
 * PostgreSQL integration tests for user-profile-service JDBC persistence layer.
 *
 * <p>Uses a real Testcontainers PostgreSQL instance. Validates profile persistence,
 * CRUD query correctness, update semantics, delete semantics, and transaction
 * rollback/commit behaviour.
 *
 * @doc.type    class
 * @doc.purpose PostgreSQL integration: profile persistence, queries, updates, deletes, transactions
 * @doc.layer   product
 * @doc.pattern IntegrationTest
 */
@Tag("integration [GH-90000]")
@Testcontainers
@DisplayName("User-Profile-Service — PostgreSQL integration tests [GH-90000]")
class PostgreSQLIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine [GH-90000]")
                    .withDatabaseName("profile_integration_test [GH-90000]")
                    .withUsername("profile_test [GH-90000]")
                    .withPassword("profile_test_pw [GH-90000]");

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
            stmt.execute("DROP TABLE IF EXISTS user_profiles CASCADE [GH-90000]");
        }
        dataSource.close(); // GH-90000
    }

    // ── Profile persistence ───────────────────────────────────────────────────

    @Test
    @DisplayName("profile is persisted and readable by user_id [GH-90000]")
    void profilePersistAndRead() throws Exception { // GH-90000
        String userId = uid(); // GH-90000
        insertProfile(userId, "Alice Smith", "alice@example.com", "tenant-1"); // GH-90000

        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT user_id, display_name, email FROM user_profiles WHERE user_id = ?")) {
            ps.setString(1, userId); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            assertThat(rs.next()).isTrue(); // GH-90000
            assertThat(rs.getString("display_name [GH-90000]")).isEqualTo("Alice Smith [GH-90000]");
            assertThat(rs.getString("email [GH-90000]")).isEqualTo("alice@example.com [GH-90000]");
        }
    }

    @Test
    @DisplayName("profile with all optional fields is persisted correctly [GH-90000]")
    void profileWithAllFieldsPersisted() throws Exception { // GH-90000
        String userId = uid(); // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO user_profiles
                    (user_id, display_name, email, phone, avatar_url, bio, locale, timezone, tenant_id, created_at, updated_at) // GH-90000
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) { // GH-90000
            ps.setString(1, userId); // GH-90000
            ps.setString(2, "Bob Jones"); // GH-90000
            ps.setString(3, "bob@example.com"); // GH-90000
            ps.setString(4, "+1-555-0100"); // GH-90000
            ps.setString(5, "https://example.com/avatar.png"); // GH-90000
            ps.setString(6, "Software engineer"); // GH-90000
            ps.setString(7, "en-US"); // GH-90000
            ps.setString(8, "America/New_York"); // GH-90000
            ps.setString(9, "tenant-1"); // GH-90000
            ps.setLong(10, Instant.now().toEpochMilli()); // GH-90000
            ps.setLong(11, Instant.now().toEpochMilli()); // GH-90000
            ps.executeUpdate(); // GH-90000
        }

        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT phone, locale, timezone FROM user_profiles WHERE user_id = ?")) {
            ps.setString(1, userId); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            assertThat(rs.next()).isTrue(); // GH-90000
            assertThat(rs.getString("phone [GH-90000]")).isEqualTo("+1-555-0100 [GH-90000]");
            assertThat(rs.getString("locale [GH-90000]")).isEqualTo("en-US [GH-90000]");
            assertThat(rs.getString("timezone [GH-90000]")).isEqualTo("America/New_York [GH-90000]");
        }
    }

    // ── Profile query ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("profiles are queryable by tenant_id [GH-90000]")
    void queryByTenantId() throws Exception { // GH-90000
        String tenant = "tenant_" + uid(); // GH-90000
        insertProfile(uid(), "User A", "a@example.com", tenant); // GH-90000
        insertProfile(uid(), "User B", "b@example.com", tenant); // GH-90000
        insertProfile(uid(), "User C", "c@other.com", "other_tenant"); // GH-90000

        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT count(*) FROM user_profiles WHERE tenant_id = ?")) { // GH-90000
            ps.setString(1, tenant); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            rs.next(); // GH-90000
            assertThat(rs.getInt(1)).isEqualTo(2); // GH-90000
        }
    }

    @Test
    @DisplayName("profile is queryable by email [GH-90000]")
    void queryByEmail() throws Exception { // GH-90000
        String email = "unique_" + uid() + "@example.com"; // GH-90000
        insertProfile(uid(), "Email User", email, "tenant-1"); // GH-90000

        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT user_id FROM user_profiles WHERE email = ?")) {
            ps.setString(1, email); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            assertThat(rs.next()).isTrue(); // GH-90000
        }
    }

    @Test
    @DisplayName("profile search by partial display_name returns matches [GH-90000]")
    void searchByDisplayName() throws Exception { // GH-90000
        insertProfile(uid(), "Alice Wonder", "alice_wonder@example.com", "tenant-1"); // GH-90000
        insertProfile(uid(), "Bob Marley", "bob_marley@example.com", "tenant-1"); // GH-90000

        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT count(*) FROM user_profiles WHERE display_name ILIKE ?")) { // GH-90000
            ps.setString(1, "%alice%"); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            rs.next(); // GH-90000
            assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1); // GH-90000
        }
    }

    // ── Profile update ────────────────────────────────────────────────────────

    @Test
    @DisplayName("profile update reflects new display_name [GH-90000]")
    void profileUpdateDisplayName() throws Exception { // GH-90000
        String userId = uid(); // GH-90000
        insertProfile(userId, "Original Name", "u@example.com", "tenant-1"); // GH-90000

        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "UPDATE user_profiles SET display_name = ?, updated_at = ? WHERE user_id = ?")) {
            ps.setString(1, "Updated Name"); // GH-90000
            ps.setLong(2, Instant.now().toEpochMilli()); // GH-90000
            ps.setString(3, userId); // GH-90000
            assertThat(ps.executeUpdate()).isEqualTo(1); // GH-90000
        }

        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT display_name FROM user_profiles WHERE user_id = ?")) {
            ps.setString(1, userId); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            rs.next(); // GH-90000
            assertThat(rs.getString("display_name [GH-90000]")).isEqualTo("Updated Name [GH-90000]");
        }
    }

    @Test
    @DisplayName("updating non-existent user_id affects zero rows [GH-90000]")
    void updateNonExistentProfile() throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "UPDATE user_profiles SET display_name = 'Ghost' WHERE user_id = ?")) {
            ps.setString(1, "non_existent_" + uid()); // GH-90000
            assertThat(ps.executeUpdate()).isZero(); // GH-90000
        }
    }

    // ── Profile deletion ──────────────────────────────────────────────────────

    @Test
    @DisplayName("profile deletion removes the row [GH-90000]")
    void profileDelete() throws Exception { // GH-90000
        String userId = uid(); // GH-90000
        insertProfile(userId, "Delete Me", "delete@example.com", "tenant-1"); // GH-90000

        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "DELETE FROM user_profiles WHERE user_id = ?")) {
            ps.setString(1, userId); // GH-90000
            assertThat(ps.executeUpdate()).isEqualTo(1); // GH-90000
        }

        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT count(*) FROM user_profiles WHERE user_id = ?")) { // GH-90000
            ps.setString(1, userId); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            rs.next(); // GH-90000
            assertThat(rs.getInt(1)).isZero(); // GH-90000
        }
    }

    @Test
    @DisplayName("duplicate email violates unique constraint [GH-90000]")
    void duplicateEmailViolatesConstraint() throws Exception { // GH-90000
        String email = "dup_" + uid() + "@example.com"; // GH-90000
        insertProfile(uid(), "First", email, "tenant-1"); // GH-90000
        assertThatThrownBy(() -> insertProfile(uid(), "Second", email, "tenant-1")) // GH-90000
                .isInstanceOf(SQLException.class); // GH-90000
    }

    // ── Transaction behavior ──────────────────────────────────────────────────

    @Test
    @DisplayName("committed transaction is visible to subsequent reads [GH-90000]")
    void committedTransactionVisible() throws Exception { // GH-90000
        String userId = "txn_" + uid(); // GH-90000
        try (Connection conn = dataSource.getConnection()) { // GH-90000
            conn.setAutoCommit(false); // GH-90000
            try (PreparedStatement ps = conn.prepareStatement( // GH-90000
                    buildInsertSql())) { // GH-90000
                fillInsert(ps, userId, "Txn User", "txnuser@example.com", "txn-tenant"); // GH-90000
                ps.executeUpdate(); // GH-90000
            }
            conn.commit(); // GH-90000
        }

        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT count(*) FROM user_profiles WHERE user_id = ?")) { // GH-90000
            ps.setString(1, userId); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            rs.next(); // GH-90000
            assertThat(rs.getInt(1)).isEqualTo(1); // GH-90000
        }
    }

    @Test
    @DisplayName("rolled-back transaction is not visible to subsequent reads [GH-90000]")
    void rolledBackTransactionNotVisible() throws Exception { // GH-90000
        String userId = "rollback_" + uid(); // GH-90000
        try (Connection conn = dataSource.getConnection()) { // GH-90000
            conn.setAutoCommit(false); // GH-90000
            try (PreparedStatement ps = conn.prepareStatement(buildInsertSql())) { // GH-90000
                fillInsert(ps, userId, "Rollback User", "rb@example.com", "rb-tenant"); // GH-90000
                ps.executeUpdate(); // GH-90000
            }
            conn.rollback(); // GH-90000
        }

        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement( // GH-90000
                     "SELECT count(*) FROM user_profiles WHERE user_id = ?")) { // GH-90000
            ps.setString(1, userId); // GH-90000
            ResultSet rs = ps.executeQuery(); // GH-90000
            rs.next(); // GH-90000
            assertThat(rs.getInt(1)).isZero(); // GH-90000
        }
    }

    // ── Connection pool behavior ──────────────────────────────────────────────

    @ParameterizedTest(name = "concurrent={0}") // GH-90000
    @ValueSource(ints = {2, 5, 8}) // GH-90000
    @DisplayName("concurrent connections to user_profiles do not deadlock [GH-90000]")
    void concurrentConnectionsNoDeadlock(int threads) throws Exception { // GH-90000
        CountDownLatch latch = new CountDownLatch(1); // GH-90000
        AtomicInteger successes = new AtomicInteger(0); // GH-90000
        ExecutorService executor = Executors.newFixedThreadPool(threads); // GH-90000
        for (int i = 0; i < threads; i++) { // GH-90000
            executor.submit(() -> { // GH-90000
                try {
                    latch.await(); // GH-90000
                    try (Connection conn = dataSource.getConnection(); // GH-90000
                         ResultSet rs = conn.createStatement().executeQuery("SELECT 1 [GH-90000]")) {
                        if (rs.next()) successes.incrementAndGet(); // GH-90000
                    }
                } catch (Exception ignored) {} // GH-90000
            });
        }
        latch.countDown(); // GH-90000
        executor.shutdown(); // GH-90000
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); // GH-90000
        assertThat(successes.get()).isEqualTo(threads); // GH-90000
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void initSchema() throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement()) { // GH-90000
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS user_profiles ( // GH-90000
                        user_id      TEXT PRIMARY KEY,
                        display_name TEXT NOT NULL,
                        email        TEXT NOT NULL UNIQUE,
                        phone        TEXT,
                        avatar_url   TEXT,
                        bio          TEXT,
                        locale       TEXT,
                        timezone     TEXT,
                        tenant_id    TEXT NOT NULL,
                        created_at   BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT, // GH-90000
                        updated_at   BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT // GH-90000
                    )""");
        }
    }

    private void insertProfile(String userId, String name, String email, String tenantId) throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             PreparedStatement ps = conn.prepareStatement(buildInsertSql())) { // GH-90000
            fillInsert(ps, userId, name, email, tenantId); // GH-90000
            ps.executeUpdate(); // GH-90000
        }
    }

    private String buildInsertSql() { // GH-90000
        return "INSERT INTO user_profiles(user_id, display_name, email, tenant_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)"; // GH-90000
    }

    private void fillInsert(PreparedStatement ps, String userId, String name, String email, String tenantId) // GH-90000
            throws SQLException {
        ps.setString(1, userId); // GH-90000
        ps.setString(2, name); // GH-90000
        ps.setString(3, email); // GH-90000
        ps.setString(4, tenantId); // GH-90000
        ps.setLong(5, Instant.now().toEpochMilli()); // GH-90000
        ps.setLong(6, Instant.now().toEpochMilli()); // GH-90000
    }

    private String uid() { // GH-90000
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12); // GH-90000
    }
}
