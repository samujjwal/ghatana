/**
 * AI Registry Service PostgreSQL Integration Test Suite
 *
 * Real database integration tests using Testcontainers PostgreSQL.
 * Tests model registry persistence, CRUD operations, queries, and transaction behavior.
 *
 * @doc.type test
 * @doc.purpose PostgreSQL integration for AI model registry persistence
 * @doc.layer shared-services
 * @doc.pattern IntegrationTest
 */

package com.ghatana.ai.registry.database;

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
 * PostgreSQL integration tests for AI Registry Service JDBC persistence layer.
 * Uses a real Testcontainers PostgreSQL instance (no mocks). Validates model
 * storage, metadata persistence, CRUD operations, query correctness, and
 * transaction rollback/commit semantics.
 */
@Tag("integration")
@Testcontainers
@DisplayName("AI Registry Service — PostgreSQL integration tests")
class PostgreSQLIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("ai_registry_integration_test")
                    .withUsername("registry_test")
                    .withPassword("registry_test_pw");

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
            stmt.execute("DROP TABLE IF EXISTS ai_models, model_capabilities, model_pricing CASCADE");
        }
        dataSource.close();
    }

    // ── Model persistence ─────────────────────────────────────────────────────

    @Test
    @DisplayName("model is persisted and readable by model_id")
    void modelPersistAndRead() throws Exception {
        String modelId = "gpt-4-" + uid();
        insertModel(modelId, "GPT-4", "openai", "LLM", "active");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT model_id, name, provider, type, status FROM ai_models WHERE model_id = ?")) {
            ps.setString(1, modelId);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("model_id")).isEqualTo(modelId);
            assertThat(rs.getString("name")).isEqualTo("GPT-4");
            assertThat(rs.getString("provider")).isEqualTo("openai");
            assertThat(rs.getString("type")).isEqualTo("LLM");
            assertThat(rs.getString("status")).isEqualTo("active");
        }
    }

    @Test
    @DisplayName("model with all fields is persisted correctly")
    void modelWithAllFieldsPersisted() throws Exception {
        String modelId = "model-" + uid();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO ai_models
                    (model_id, name, provider, type, version, status, description, max_tokens, supports_streaming, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
            ps.setString(1, modelId);
            ps.setString(2, "Claude 3");
            ps.setString(3, "anthropic");
            ps.setString(4, "LLM");
            ps.setString(5, "3.0");
            ps.setString(6, "active");
            ps.setString(7, "Advanced AI assistant");
            ps.setInt(8, 200000);
            ps.setBoolean(9, true);
            ps.setLong(10, Instant.now().toEpochMilli());
            ps.setLong(11, Instant.now().toEpochMilli());
            ps.executeUpdate();
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT version, max_tokens, supports_streaming FROM ai_models WHERE model_id = ?")) {
            ps.setString(1, modelId);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("version")).isEqualTo("3.0");
            assertThat(rs.getInt("max_tokens")).isEqualTo(200000);
            assertThat(rs.getBoolean("supports_streaming")).isTrue();
        }
    }

    // ── Model query ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("models are queryable by provider")
    void queryByProvider() throws Exception {
        String provider = "openai";
        insertModel("gpt-4-" + uid(), "GPT-4", provider, "LLM", "active");
        insertModel("gpt-3.5-" + uid(), "GPT-3.5", provider, "LLM", "active");
        insertModel("claude-" + uid(), "Claude", "anthropic", "LLM", "active");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM ai_models WHERE provider = ?")) {
            ps.setString(1, provider);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("models are queryable by type")
    void queryByType() throws Exception {
        String type = "EMBEDDING";
        insertModel("ada-002-" + uid(), "Ada 002", "openai", type, "active");
        insertModel("babbage-" + uid(), "Babbage", "openai", type, "active");
        insertModel("gpt-4-" + uid(), "GPT-4", "openai", "LLM", "active");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM ai_models WHERE type = ?")) {
            ps.setString(1, type);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("models are queryable by status")
    void queryByStatus() throws Exception {
        String status = "active";
        insertModel("model-1-" + uid(), "Model 1", "provider1", "LLM", status);
        insertModel("model-2-" + uid(), "Model 2", "provider2", "LLM", status);
        insertModel("model-3-" + uid(), "Model 3", "provider3", "LLM", "deprecated");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM ai_models WHERE status = ?")) {
            ps.setString(1, status);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }

    // ── Model update ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("model update reflects new status")
    void modelUpdateStatus() throws Exception {
        String modelId = "model-" + uid();
        insertModel(modelId, "Test Model", "provider", "LLM", "active");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE ai_models SET status = ?, updated_at = ? WHERE model_id = ?")) {
            ps.setString(1, "deprecated");
            ps.setLong(2, Instant.now().toEpochMilli());
            ps.setString(3, modelId);
            assertThat(ps.executeUpdate()).isEqualTo(1);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT status FROM ai_models WHERE model_id = ?")) {
            ps.setString(1, modelId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getString("status")).isEqualTo("deprecated");
        }
    }

    @Test
    @DisplayName("updating non-existent model_id affects zero rows")
    void updateNonExistentModel() throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE ai_models SET status = 'deprecated' WHERE model_id = ?")) {
            ps.setString(1, "non_existent_" + uid());
            assertThat(ps.executeUpdate()).isZero();
        }
    }

    // ── Model deletion ───────────────────────────────────────────────────────

    @Test
    @DisplayName("model deletion removes the row")
    void modelDelete() throws Exception {
        String modelId = "model-" + uid();
        insertModel(modelId, "Delete Me", "provider", "LLM", "active");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM ai_models WHERE model_id = ?")) {
            ps.setString(1, modelId);
            assertThat(ps.executeUpdate()).isEqualTo(1);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM ai_models WHERE model_id = ?")) {
            ps.setString(1, modelId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    @DisplayName("duplicate model_id violates unique constraint")
    void duplicateModelIdViolatesConstraint() throws Exception {
        String modelId = "model-" + uid();
        insertModel(modelId, "First", "provider", "LLM", "active");
        assertThatThrownBy(() -> insertModel(modelId, "Second", "provider", "LLM", "active"))
                .isInstanceOf(SQLException.class);
    }

    // ── Capability storage ─────────────────────────────────────────────────────

    @Test
    @DisplayName("capability is persisted and readable by model_id")
    void capabilityPersistAndRead() throws Exception {
        String modelId = "model-" + uid();
        insertModel(modelId, "Test Model", "provider", "LLM", "active");
        insertCapability(modelId, "function_calling", true);
        insertCapability(modelId, "vision", true);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM model_capabilities WHERE model_id = ?")) {
            ps.setString(1, modelId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(2);
        }
    }

    // ── Pricing storage ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("pricing is persisted and readable by model_id")
    void pricingPersistAndRead() throws Exception {
        String modelId = "model-" + uid();
        insertModel(modelId, "Test Model", "provider", "LLM", "active");
        insertPricing(modelId, 0.01, 0.03);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT input_tokens_per_1k, output_tokens_per_1k FROM model_pricing WHERE model_id = ?")) {
            ps.setString(1, modelId);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getDouble("input_tokens_per_1k")).isEqualTo(0.01);
            assertThat(rs.getDouble("output_tokens_per_1k")).isEqualTo(0.03);
        }
    }

    // ── Transaction behavior ─────────────────────────────────────────────────────

    @Test
    @DisplayName("committed transaction is visible to subsequent reads")
    void committedTransactionVisible() throws Exception {
        String modelId = "txn_" + uid();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ai_models(model_id, name, provider, type, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, modelId);
                ps.setString(2, "Txn Model");
                ps.setString(3, "provider");
                ps.setString(4, "LLM");
                ps.setString(5, "active");
                ps.setLong(6, Instant.now().toEpochMilli());
                ps.setLong(7, Instant.now().toEpochMilli());
                ps.executeUpdate();
            }
            conn.commit();
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM ai_models WHERE model_id = ?")) {
            ps.setString(1, modelId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("rolled-back transaction is not visible to subsequent reads")
    void rolledBackTransactionNotVisible() throws Exception {
        String modelId = "rollback_" + uid();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ai_models(model_id, name, provider, type, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, modelId);
                ps.setString(2, "Rollback Model");
                ps.setString(3, "provider");
                ps.setString(4, "LLM");
                ps.setString(5, "active");
                ps.setLong(6, Instant.now().toEpochMilli());
                ps.setLong(7, Instant.now().toEpochMilli());
                ps.executeUpdate();
            }
            conn.rollback();
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM ai_models WHERE model_id = ?")) {
            ps.setString(1, modelId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    // ── Connection pool behavior ────────────────────────────────────────────────

    @ParameterizedTest(name = "concurrent={0}")
    @ValueSource(ints = {2, 5, 8})
    @DisplayName("concurrent connections to ai_models do not deadlock")
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

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private void initSchema() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ai_models (
                        model_id              TEXT PRIMARY KEY,
                        name                  TEXT NOT NULL,
                        provider              TEXT NOT NULL,
                        type                  TEXT NOT NULL,
                        version               TEXT,
                        status                TEXT NOT NULL,
                        description           TEXT,
                        max_tokens            INTEGER,
                        supports_streaming    BOOLEAN,
                        created_at            BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT,
                        updated_at            BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT
                    )""");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS model_capabilities (
                        model_id      TEXT NOT NULL REFERENCES ai_models(model_id) ON DELETE CASCADE,
                        capability    TEXT NOT NULL,
                        enabled       BOOLEAN NOT NULL DEFAULT true,
                        PRIMARY KEY (model_id, capability)
                    )""");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS model_pricing (
                        model_id               TEXT PRIMARY KEY REFERENCES ai_models(model_id) ON DELETE CASCADE,
                        input_tokens_per_1k    NUMERIC NOT NULL,
                        output_tokens_per_1k   NUMERIC NOT NULL
                    )""");
        }
    }

    private void insertModel(String modelId, String name, String provider, String type, String status) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO ai_models(model_id, name, provider, type, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, modelId);
            ps.setString(2, name);
            ps.setString(3, provider);
            ps.setString(4, type);
            ps.setString(5, status);
            ps.setLong(6, Instant.now().toEpochMilli());
            ps.setLong(7, Instant.now().toEpochMilli());
            ps.executeUpdate();
        }
    }

    private void insertCapability(String modelId, String capability, boolean enabled) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO model_capabilities(model_id, capability, enabled) VALUES (?, ?, ?)")) {
            ps.setString(1, modelId);
            ps.setString(2, capability);
            ps.setBoolean(3, enabled);
            ps.executeUpdate();
        }
    }

    private void insertPricing(String modelId, double inputPer1k, double outputPer1k) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO model_pricing(model_id, input_tokens_per_1k, output_tokens_per_1k) VALUES (?, ?, ?)")) {
            ps.setString(1, modelId);
            ps.setDouble(2, inputPer1k);
            ps.setDouble(3, outputPer1k);
            ps.executeUpdate();
        }
    }

    private String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
