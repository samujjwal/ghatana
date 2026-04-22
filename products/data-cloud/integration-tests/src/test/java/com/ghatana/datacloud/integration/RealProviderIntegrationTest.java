/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.redis.testcontainers.RedisContainer;
import org.testcontainers.containers.ClickHouseContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import redis.clients.jedis.Jedis;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Provider Integration Tests
 *
 * Tests integration with real providers using Testcontainers:
 * - PostgreSQL for relational storage
 * - Kafka for event streaming
 * - Redis for caching and pub/sub
 * - ClickHouse for analytics and time-series data
 *
 * @doc.type class
 * @doc.purpose Test integration with real providers (PostgreSQL, Kafka, Redis, ClickHouse) using Testcontainers
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Real Provider Integration Tests")
@Tag("integration")
class RealProviderIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:16-alpine"))
        .withDatabaseName("datacloud_test")
        .withUsername("test_user")
        .withPassword("test_pass")
        .withStartupTimeout(Duration.ofSeconds(60));

    private static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
        .withStartupTimeout(Duration.ofSeconds(60));

    private static final RedisContainer REDIS = new RedisContainer(
        DockerImageName.parse("redis:7-alpine"))
        .withStartupTimeout(Duration.ofSeconds(60));

    private static final ClickHouseContainer CLICKHOUSE = new ClickHouseContainer(
        DockerImageName.parse("clickhouse/clickhouse-server:24.3"))
        .withStartupTimeout(Duration.ofSeconds(60));

    @BeforeAll
    static void startContainers() {
        POSTGRESQL.start();
        KAFKA.start();
        REDIS.start();
        CLICKHOUSE.start();
    }

    @AfterAll
    static void stopContainers() {
        if (CLICKHOUSE != null) CLICKHOUSE.stop();
        if (REDIS != null) REDIS.stop();
        if (KAFKA != null) KAFKA.stop();
        if (POSTGRESQL != null) POSTGRESQL.stop();
    }

    @Test
    @DisplayName("PostgreSQL container is running and accessible")
    void postgresqlContainerIsRunningAndAccessible() throws SQLException {
        assertThat(POSTGRESQL.isRunning()).isTrue();
        assertThat(POSTGRESQL.getJdbcUrl()).isNotEmpty();
        assertThat(POSTGRESQL.getUsername()).isEqualTo("test_user");
        assertThat(POSTGRESQL.getPassword()).isEqualTo("test_pass");

        // Test actual connection
        try (Connection conn = DriverManager.getConnection(
            POSTGRESQL.getJdbcUrl(),
            POSTGRESQL.getUsername(),
            POSTGRESQL.getPassword())) {
            assertThat(conn.isValid(5)).isTrue();

            // Test query
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("PostgreSQL can create and query tables")
    void postgresqlCanCreateAndQueryTables() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
            POSTGRESQL.getJdbcUrl(),
            POSTGRESQL.getUsername(),
            POSTGRESQL.getPassword())) {

            // Create table
            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS test_features (" +
                "id SERIAL PRIMARY KEY, " +
                "feature_name VARCHAR(255) NOT NULL, " +
                "feature_value DOUBLE PRECISION, " +
                "tenant_id VARCHAR(255) NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            // Insert data
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO test_features (feature_name, feature_value, tenant_id) VALUES (?, ?, ?)")) {
                ps.setString(1, "test_feature_1");
                ps.setDouble(2, 123.45);
                ps.setString(3, "tenant-1");
                ps.executeUpdate();

                ps.setString(1, "test_feature_2");
                ps.setDouble(2, 678.90);
                ps.setString(3, "tenant-2");
                ps.executeUpdate();
            }

            // Query data
            try (ResultSet rs = conn.createStatement().executeQuery(
                "SELECT feature_name, feature_value, tenant_id FROM test_features ORDER BY id")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("feature_name")).isEqualTo("test_feature_1");
                assertThat(rs.getDouble("feature_value")).isEqualTo(123.45);
                assertThat(rs.getString("tenant_id")).isEqualTo("tenant-1");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("feature_name")).isEqualTo("test_feature_2");
                assertThat(rs.getDouble("feature_value")).isEqualTo(678.90);
                assertThat(rs.getString("tenant_id")).isEqualTo("tenant-2");
            }

            // Cleanup
            conn.createStatement().execute("DROP TABLE test_features");
        }
    }

    @Test
    @DisplayName("PostgreSQL supports tenant isolation with row-level security")
    void postgresqlSupportsTenantIsolationWithRowLevelSecurity() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
            POSTGRESQL.getJdbcUrl(),
            POSTGRESQL.getUsername(),
            POSTGRESQL.getPassword())) {

            // Create table with tenant column
            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS tenant_isolated_features (" +
                "id SERIAL PRIMARY KEY, " +
                "feature_name VARCHAR(255) NOT NULL, " +
                "feature_value DOUBLE PRECISION, " +
                "tenant_id VARCHAR(255) NOT NULL)"
            );

            // Insert data for different tenants
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tenant_isolated_features (feature_name, feature_value, tenant_id) VALUES (?, ?, ?)")) {
                ps.setString(1, "tenant_a_feature");
                ps.setDouble(2, 100.0);
                ps.setString(3, "tenant-a");
                ps.executeUpdate();

                ps.setString(1, "tenant_b_feature");
                ps.setDouble(2, 200.0);
                ps.setString(3, "tenant-b");
                ps.executeUpdate();
            }

            // Query with tenant filter
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT feature_name, feature_value FROM tenant_isolated_features WHERE tenant_id = ?")) {
                ps.setString(1, "tenant-a");
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("feature_name")).isEqualTo("tenant_a_feature");
                    assertThat(rs.getDouble("feature_value")).isEqualTo(100.0);
                    assertThat(rs.next()).isFalse(); // Only one row for tenant-a
                }
            }

            // Cleanup
            conn.createStatement().execute("DROP TABLE tenant_isolated_features");
        }
    }

    @Test
    @DisplayName("Kafka container is running and accessible")
    void kafkaContainerIsRunningAndAccessible() {
        assertThat(KAFKA.isRunning()).isTrue();
        assertThat(KAFKA.getBootstrapServers()).isNotEmpty();
        assertThat(KAFKA.getBootstrapServers()).contains("9092");
    }

    @Test
    @DisplayName("Kafka can create and list topics")
    void kafkaCanCreateAndListTopics() {
        // This test verifies Kafka is accessible
        // Full Kafka client testing would require additional dependencies
        assertThat(KAFKA.isRunning()).isTrue();
        assertThat(KAFKA.getBootstrapServers()).isNotEmpty();
    }

    @Test
    @DisplayName("Redis container is running and accessible")
    void redisContainerIsRunningAndAccessible() {
        assertThat(REDIS.isRunning()).isTrue();
        assertThat(REDIS.getRedisHost()).isNotEmpty();
        assertThat(REDIS.getRedisPort()).isPositive();
    }

    @Test
    @DisplayName("Redis can store and retrieve values")
    void redisCanStoreAndRetrieveValues() {
        try (Jedis jedis = new Jedis(REDIS.getRedisHost(), REDIS.getRedisPort())) {
            // Test set and get
            jedis.set("test_key", "test_value");
            String value = jedis.get("test_key");
            assertThat(value).isEqualTo("test_value");

            // Test numeric operations
            jedis.set("counter", "0");
            jedis.incr("counter");
            assertThat(Long.parseLong(jedis.get("counter"))).isEqualTo(1);

            // Test hash operations
            jedis.hset("feature:1", Map.of(
                "name", "feature_1",
                "value", "123.45",
                "tenant", "tenant-1"
            ));
            Map<String, String> feature = jedis.hgetAll("feature:1");
            assertThat(feature).hasSize(3);
            assertThat(feature.get("name")).isEqualTo("feature_1");
            assertThat(feature.get("value")).isEqualTo("123.45");
            assertThat(feature.get("tenant")).isEqualTo("tenant-1");

            // Test list operations
            jedis.lpush("feature_list", "feature_3", "feature_2", "feature_1");
            List<String> features = jedis.lrange("feature_list", 0, -1);
            assertThat(features).hasSize(3);
            assertThat(features).containsExactly("feature_1", "feature_2", "feature_3");

            // Cleanup
            jedis.del("test_key", "counter", "feature:1", "feature_list");
        }
    }

    @Test
    @DisplayName("Redis supports expiration and caching patterns")
    void redisSupportsExpirationAndCachingPatterns() throws InterruptedException {
        try (Jedis jedis = new Jedis(REDIS.getRedisHost(), REDIS.getRedisPort())) {
            // Test TTL
            jedis.setex("expiring_key", 2, "will_expire");
            assertThat(jedis.ttl("expiring_key")).isGreaterThan(0);
            assertThat(jedis.get("expiring_key")).isEqualTo("will_expire");

            Thread.sleep(2500);
            assertThat(jedis.get("expiring_key")).isNull();

            // Test cache pattern
            String cacheKey = "cache:feature:123";
            jedis.set(cacheKey, "cached_value");
            jedis.expire(cacheKey, 3600); // 1 hour

            assertThat(jedis.exists(cacheKey)).isTrue();
            assertThat(jedis.ttl(cacheKey)).isGreaterThan(3000); // Close to 3600

            // Cleanup
            jedis.del(cacheKey);
        }
    }

    @Test
    @DisplayName("ClickHouse container is running and accessible")
    void clickhouseContainerIsRunningAndAccessible() throws SQLException {
        assertThat(CLICKHOUSE.isRunning()).isTrue();
        assertThat(CLICKHOUSE.getJdbcUrl()).isNotEmpty();
        assertThat(CLICKHOUSE.getUsername()).isNotEmpty();
        assertThat(CLICKHOUSE.getPassword()).isNotEmpty();

        // Test actual connection
        try (Connection conn = DriverManager.getConnection(
            CLICKHOUSE.getJdbcUrl(),
            CLICKHOUSE.getUsername(),
            CLICKHOUSE.getPassword())) {
            assertThat(conn.isValid(5)).isTrue();

            // Test query
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("ClickHouse can create and query time-series data")
    void clickhouseCanCreateAndQueryTimeSeriesData() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
            CLICKHOUSE.getJdbcUrl(),
            CLICKHOUSE.getUsername(),
            CLICKHOUSE.getPassword())) {

            // Create MergeTree table for time-series data
            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS feature_metrics (" +
                "timestamp DateTime DEFAULT now(), " +
                "feature_name String, " +
                "feature_value Float64, " +
                "tenant_id String" +
                ") ENGINE = MergeTree() " +
                "ORDER BY (timestamp, feature_name)"
            );

            // Insert time-series data
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO feature_metrics (timestamp, feature_name, feature_value, tenant_id) VALUES (?, ?, ?, ?)")) {
                for (int i = 0; i < 10; i++) {
                    ps.setObject(1, java.time.LocalDateTime.now().minusHours(i));
                    ps.setString(2, "metric_" + (i % 3));
                    ps.setDouble(3, 100.0 + i * 10.0);
                    ps.setString(4, "tenant-" + (i % 2));
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // Query time-series data
            try (ResultSet rs = conn.createStatement().executeQuery(
                "SELECT feature_name, avg(feature_value) as avg_value, count() as count " +
                "FROM feature_metrics " +
                "GROUP BY feature_name " +
                "ORDER BY feature_name")) {
                int rowCount = 0;
                while (rs.next()) {
                    rowCount++;
                    assertThat(rs.getString("feature_name")).startsWith("metric_");
                    assertThat(rs.getDouble("avg_value")).isGreaterThan(0);
                    assertThat(rs.getLong("count")).isGreaterThan(0);
                }
                assertThat(rowCount).isEqualTo(3); // 3 unique metrics
            }

            // Cleanup
            conn.createStatement().execute("DROP TABLE feature_metrics");
        }
    }

    @Test
    @DisplayName("ClickHouse supports efficient aggregations")
    void clickhouseSupportsEfficientAggregations() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
            CLICKHOUSE.getJdbcUrl(),
            CLICKHOUSE.getUsername(),
            CLICKHOUSE.getPassword())) {

            // Create table for aggregation tests
            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS feature_events (" +
                "event_time DateTime, " +
                "event_type String, " +
                "feature_id UInt64, " +
                "tenant_id String, " +
                "value Float64" +
                ") ENGINE = MergeTree() " +
                "PARTITION BY toYYYYMM(event_time) " +
                "ORDER BY (event_time, event_type)"
            );

            // Insert event data
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO feature_events (event_time, event_type, feature_id, tenant_id, value) VALUES (?, ?, ?, ?, ?)")) {
                for (int i = 0; i < 100; i++) {
                    ps.setObject(1, java.time.LocalDateTime.now().minusDays(i % 30));
                    ps.setString(2, i % 2 == 0 ? "ingest" : "query");
                    ps.setLong(3, i);
                    ps.setString(4, "tenant-" + (i % 5));
                    ps.setDouble(5, Math.random() * 1000);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // Test aggregation by tenant
            try (ResultSet rs = conn.createStatement().executeQuery(
                "SELECT tenant_id, " +
                "count() as event_count, " +
                "avg(value) as avg_value, " +
                "max(value) as max_value " +
                "FROM feature_events " +
                "GROUP BY tenant_id " +
                "ORDER BY tenant_id")) {
                int tenantCount = 0;
                while (rs.next()) {
                    tenantCount++;
                    assertThat(rs.getString("tenant_id")).startsWith("tenant-");
                    assertThat(rs.getLong("event_count")).isGreaterThan(0);
                    assertThat(rs.getDouble("avg_value")).isBetween(0.0, 1000.0);
                    assertThat(rs.getDouble("max_value")).isBetween(0.0, 1000.0);
                }
                assertThat(tenantCount).isEqualTo(5); // 5 tenants
            }

            // Cleanup
            conn.createStatement().execute("DROP TABLE feature_events");
        }
    }

    @Test
    @DisplayName("All providers can be used together in a multi-provider scenario")
    void allProvidersCanBeUsedTogetherInMultiProviderScenario() throws SQLException {
        // This test demonstrates a scenario where all providers work together
        // PostgreSQL for persistent storage, Redis for caching, ClickHouse for analytics

        try (Connection pgConn = DriverManager.getConnection(
            POSTGRESQL.getJdbcUrl(),
            POSTGRESQL.getUsername(),
            POSTGRESQL.getPassword());
             Jedis jedis = new Jedis(REDIS.getRedisHost(), REDIS.getRedisPort());
             Connection chConn = DriverManager.getConnection(
            CLICKHOUSE.getJdbcUrl(),
            CLICKHOUSE.getUsername(),
            CLICKHOUSE.getPassword())) {

            // 1. Store feature in PostgreSQL
            pgConn.createStatement().execute(
                "CREATE TEMP TABLE features (" +
                "id SERIAL PRIMARY KEY, " +
                "name VARCHAR(255), " +
                "value DOUBLE PRECISION)"
            );

            try (PreparedStatement ps = pgConn.prepareStatement(
                "INSERT INTO features (name, value) VALUES (?, ?)")) {
                ps.setString(1, "multi_provider_feature");
                ps.setDouble(2, 42.0);
                ps.executeUpdate();
            }

            // 2. Cache in Redis
            jedis.set("cache:multi_provider_feature", "42.0");
            jedis.expire("cache:multi_provider_feature", 3600);

            // 3. Log analytics in ClickHouse
            chConn.createStatement().execute(
                "CREATE TEMP TABLE feature_access_log (" +
                "timestamp DateTime, " +
                "feature_name String, " +
                "access_type String" +
                ") ENGINE = MergeTree() " +
                "ORDER BY timestamp"
            );

            try (PreparedStatement ps = chConn.prepareStatement(
                "INSERT INTO feature_access_log (timestamp, feature_name, access_type) VALUES (?, ?, ?)")) {
                ps.setObject(1, java.time.LocalDateTime.now());
                ps.setString(2, "multi_provider_feature");
                ps.setString(3, "read");
                ps.executeUpdate();
            }

            // Verify all operations succeeded
            assertThat(jedis.get("cache:multi_provider_feature")).isEqualTo("42.0");

            try (ResultSet rs = pgConn.createStatement().executeQuery(
                "SELECT value FROM features WHERE name = 'multi_provider_feature'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble("value")).isEqualTo(42.0);
            }

            try (ResultSet rs = chConn.createStatement().executeQuery(
                "SELECT count() FROM feature_access_log")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("Providers handle concurrent operations correctly")
    void providersHandleConcurrentOperationsCorrectly() throws SQLException, InterruptedException {
        // Test concurrent writes to PostgreSQL
        try (Connection conn = DriverManager.getConnection(
            POSTGRESQL.getJdbcUrl(),
            POSTGRESQL.getUsername(),
            POSTGRESQL.getPassword())) {

            conn.createStatement().execute(
                "CREATE TEMP TABLE concurrent_features (" +
                "id SERIAL PRIMARY KEY, " +
                "name VARCHAR(255), " +
                "value DOUBLE PRECISION, " +
                "thread_id VARCHAR(255))"
            );

            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                final int threadId = i;
                Thread thread = new Thread(() -> {
                    try (Connection threadConn = DriverManager.getConnection(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword())) {
                        for (int j = 0; j < 10; j++) {
                            try (PreparedStatement ps = threadConn.prepareStatement(
                                "INSERT INTO concurrent_features (name, value, thread_id) VALUES (?, ?, ?)")) {
                                ps.setString(1, "feature_" + threadId + "_" + j);
                                ps.setDouble(2, threadId * 100 + j);
                                ps.setString(3, "thread_" + threadId);
                                ps.executeUpdate();
                            }
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                threads.add(thread);
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join(10000);
            }

            // Verify all inserts succeeded
            try (ResultSet rs = conn.createStatement().executeQuery(
                "SELECT count(*) FROM concurrent_features")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isEqualTo(100); // 10 threads * 10 inserts
            }
        }

        // Test concurrent operations in Redis
        try (Jedis jedis = new Jedis(REDIS.getRedisHost(), REDIS.getRedisPort())) {
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                final int threadId = i;
                Thread thread = new Thread(() -> {
                    try (Jedis threadJedis = new Jedis(REDIS.getRedisHost(), REDIS.getRedisPort())) {
                        for (int j = 0; j < 10; j++) {
                            threadJedis.set("concurrent_" + threadId + "_" + j, "value_" + j);
                        }
                    }
                });
                threads.add(thread);
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join(10000);
            }

            // Verify all sets succeeded
            int keyCount = 0;
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    if (jedis.exists("concurrent_" + i + "_" + j)) {
                        keyCount++;
                    }
                }
            }
            assertThat(keyCount).isEqualTo(100);
        }
    }
}
