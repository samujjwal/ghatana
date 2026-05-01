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
@Tag("integration")
@Testcontainers
@DisplayName("User-Profile-Service — PostgreSQL integration tests")
class PostgreSQLIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("profile_integration_test")
                    .withUsername("profile_test")
                    .withPassword("profile_test_pw");

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
            stmt.execute("DROP TABLE IF EXISTS user_profiles CASCADE");
        }
        dataSource.close(); 
    }

    // ── Profile persistence ───────────────────────────────────────────────────

    @Test
    @DisplayName("profile is persisted and readable by user_id")
    void profilePersistAndRead() throws Exception { 
        String userId = uid(); 
        insertProfile(userId, "Alice Smith", "alice@example.com", "tenant-1"); 

        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT user_id, display_name, email FROM user_profiles WHERE user_id = ?")) {
            ps.setString(1, userId); 
            ResultSet rs = ps.executeQuery(); 
            assertThat(rs.next()).isTrue(); 
            assertThat(rs.getString("display_name")).isEqualTo("Alice Smith");
            assertThat(rs.getString("email")).isEqualTo("alice@example.com");
        }
    }

    @Test
    @DisplayName("profile with all optional fields is persisted correctly")
    void profileWithAllFieldsPersisted() throws Exception { 
        String userId = uid(); 
        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO user_profiles
                    (user_id, display_name, email, phone, avatar_url, bio, locale, timezone, tenant_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) { 
            ps.setString(1, userId); 
            ps.setString(2, "Bob Jones"); 
            ps.setString(3, "bob@example.com"); 
            ps.setString(4, "+1-555-0100"); 
            ps.setString(5, "https://example.com/avatar.png"); 
            ps.setString(6, "Software engineer"); 
            ps.setString(7, "en-US"); 
            ps.setString(8, "America/New_York"); 
            ps.setString(9, "tenant-1"); 
            ps.setLong(10, Instant.now().toEpochMilli()); 
            ps.setLong(11, Instant.now().toEpochMilli()); 
            ps.executeUpdate(); 
        }

        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT phone, locale, timezone FROM user_profiles WHERE user_id = ?")) {
            ps.setString(1, userId); 
            ResultSet rs = ps.executeQuery(); 
            assertThat(rs.next()).isTrue(); 
            assertThat(rs.getString("phone")).isEqualTo("+1-555-0100");
            assertThat(rs.getString("locale")).isEqualTo("en-US");
            assertThat(rs.getString("timezone")).isEqualTo("America/New_York");
        }
    }

    // ── Profile query ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("profiles are queryable by tenant_id")
    void queryByTenantId() throws Exception { 
        String tenant = "tenant_" + uid(); 
        insertProfile(uid(), "User A", "a@example.com", tenant); 
        insertProfile(uid(), "User B", "b@example.com", tenant); 
        insertProfile(uid(), "User C", "c@other.com", "other_tenant"); 

        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT count(*) FROM user_profiles WHERE tenant_id = ?")) { 
            ps.setString(1, tenant); 
            ResultSet rs = ps.executeQuery(); 
            rs.next(); 
            assertThat(rs.getInt(1)).isEqualTo(2); 
        }
    }

    @Test
    @DisplayName("profile is queryable by email")
    void queryByEmail() throws Exception { 
        String email = "unique_" + uid() + "@example.com"; 
        insertProfile(uid(), "Email User", email, "tenant-1"); 

        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT user_id FROM user_profiles WHERE email = ?")) {
            ps.setString(1, email); 
            ResultSet rs = ps.executeQuery(); 
            assertThat(rs.next()).isTrue(); 
        }
    }

    @Test
    @DisplayName("profile search by partial display_name returns matches")
    void searchByDisplayName() throws Exception { 
        insertProfile(uid(), "Alice Wonder", "alice_wonder@example.com", "tenant-1"); 
        insertProfile(uid(), "Bob Marley", "bob_marley@example.com", "tenant-1"); 

        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT count(*) FROM user_profiles WHERE display_name ILIKE ?")) { 
            ps.setString(1, "%alice%"); 
            ResultSet rs = ps.executeQuery(); 
            rs.next(); 
            assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1); 
        }
    }

    // ── Profile update ────────────────────────────────────────────────────────

    @Test
    @DisplayName("profile update reflects new display_name")
    void profileUpdateDisplayName() throws Exception { 
        String userId = uid(); 
        insertProfile(userId, "Original Name", "u@example.com", "tenant-1"); 

        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "UPDATE user_profiles SET display_name = ?, updated_at = ? WHERE user_id = ?")) {
            ps.setString(1, "Updated Name"); 
            ps.setLong(2, Instant.now().toEpochMilli()); 
            ps.setString(3, userId); 
            assertThat(ps.executeUpdate()).isEqualTo(1); 
        }

        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT display_name FROM user_profiles WHERE user_id = ?")) {
            ps.setString(1, userId); 
            ResultSet rs = ps.executeQuery(); 
            rs.next(); 
            assertThat(rs.getString("display_name")).isEqualTo("Updated Name");
        }
    }

    @Test
    @DisplayName("updating non-existent user_id affects zero rows")
    void updateNonExistentProfile() throws Exception { 
        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "UPDATE user_profiles SET display_name = 'Ghost' WHERE user_id = ?")) {
            ps.setString(1, "non_existent_" + uid()); 
            assertThat(ps.executeUpdate()).isZero(); 
        }
    }

    // ── Profile deletion ──────────────────────────────────────────────────────

    @Test
    @DisplayName("profile deletion removes the row")
    void profileDelete() throws Exception { 
        String userId = uid(); 
        insertProfile(userId, "Delete Me", "delete@example.com", "tenant-1"); 

        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "DELETE FROM user_profiles WHERE user_id = ?")) {
            ps.setString(1, userId); 
            assertThat(ps.executeUpdate()).isEqualTo(1); 
        }

        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT count(*) FROM user_profiles WHERE user_id = ?")) { 
            ps.setString(1, userId); 
            ResultSet rs = ps.executeQuery(); 
            rs.next(); 
            assertThat(rs.getInt(1)).isZero(); 
        }
    }

    @Test
    @DisplayName("duplicate email violates unique constraint")
    void duplicateEmailViolatesConstraint() throws Exception { 
        String email = "dup_" + uid() + "@example.com"; 
        insertProfile(uid(), "First", email, "tenant-1"); 
        assertThatThrownBy(() -> insertProfile(uid(), "Second", email, "tenant-1")) 
                .isInstanceOf(SQLException.class); 
    }

    // ── Transaction behavior ──────────────────────────────────────────────────

    @Test
    @DisplayName("committed transaction is visible to subsequent reads")
    void committedTransactionVisible() throws Exception { 
        String userId = "txn_" + uid(); 
        try (Connection conn = dataSource.getConnection()) { 
            conn.setAutoCommit(false); 
            try (PreparedStatement ps = conn.prepareStatement( 
                    buildInsertSql())) { 
                fillInsert(ps, userId, "Txn User", "txnuser@example.com", "txn-tenant"); 
                ps.executeUpdate(); 
            }
            conn.commit(); 
        }

        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT count(*) FROM user_profiles WHERE user_id = ?")) { 
            ps.setString(1, userId); 
            ResultSet rs = ps.executeQuery(); 
            rs.next(); 
            assertThat(rs.getInt(1)).isEqualTo(1); 
        }
    }

    @Test
    @DisplayName("rolled-back transaction is not visible to subsequent reads")
    void rolledBackTransactionNotVisible() throws Exception { 
        String userId = "rollback_" + uid(); 
        try (Connection conn = dataSource.getConnection()) { 
            conn.setAutoCommit(false); 
            try (PreparedStatement ps = conn.prepareStatement(buildInsertSql())) { 
                fillInsert(ps, userId, "Rollback User", "rb@example.com", "rb-tenant"); 
                ps.executeUpdate(); 
            }
            conn.rollback(); 
        }

        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement( 
                     "SELECT count(*) FROM user_profiles WHERE user_id = ?")) { 
            ps.setString(1, userId); 
            ResultSet rs = ps.executeQuery(); 
            rs.next(); 
            assertThat(rs.getInt(1)).isZero(); 
        }
    }

    // ── Connection pool behavior ──────────────────────────────────────────────

    @ParameterizedTest(name = "concurrent={0}") 
    @ValueSource(ints = {2, 5, 8}) 
    @DisplayName("concurrent connections to user_profiles do not deadlock")
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void initSchema() throws Exception { 
        try (Connection conn = dataSource.getConnection(); 
             Statement stmt = conn.createStatement()) { 
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS user_profiles (
                        user_id      TEXT PRIMARY KEY,
                        display_name TEXT NOT NULL,
                        email        TEXT NOT NULL UNIQUE,
                        phone        TEXT,
                        avatar_url   TEXT,
                        bio          TEXT,
                        locale       TEXT,
                        timezone     TEXT,
                        tenant_id    TEXT NOT NULL,
                        created_at   BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT,
                        updated_at   BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT
                    )""");
        }
    }

    private void insertProfile(String userId, String name, String email, String tenantId) throws Exception { 
        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(buildInsertSql())) { 
            fillInsert(ps, userId, name, email, tenantId); 
            ps.executeUpdate(); 
        }
    }

    private String buildInsertSql() { 
        return "INSERT INTO user_profiles(user_id, display_name, email, tenant_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)"; 
    }

    private void fillInsert(PreparedStatement ps, String userId, String name, String email, String tenantId) 
            throws SQLException {
        ps.setString(1, userId); 
        ps.setString(2, name); 
        ps.setString(3, email); 
        ps.setString(4, tenantId); 
        ps.setLong(5, Instant.now().toEpochMilli()); 
        ps.setLong(6, Instant.now().toEpochMilli()); 
    }

    private String uid() { 
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12); 
    }
}
