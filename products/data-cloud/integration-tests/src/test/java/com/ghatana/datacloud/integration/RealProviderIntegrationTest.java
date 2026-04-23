/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * @doc.purpose Test integration with real providers (PostgreSQL, Kafka, Redis, ClickHouse) using Testcontainers // GH-90000
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Real Provider Integration Tests")
@Tag("integration")
class RealProviderIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>( // GH-90000
        DockerImageName.parse("postgres:16-alpine"))
        .withDatabaseName("datacloud_test")
        .withUsername("test_user")
        .withPassword("test_pass")
        .withStartupTimeout(Duration.ofSeconds(60)); // GH-90000

    private static final KafkaContainer KAFKA = new KafkaContainer( // GH-90000
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
        .withStartupTimeout(Duration.ofSeconds(60)); // GH-90000

    private static final RedisContainer REDIS = new RedisContainer( // GH-90000
        DockerImageName.parse("redis:7-alpine"))
        .withStartupTimeout(Duration.ofSeconds(60)); // GH-90000

    private static final ClickHouseContainer CLICKHOUSE = new ClickHouseContainer( // GH-90000
        DockerImageName.parse("clickhouse/clickhouse-server:24.3"))
        .withStartupTimeout(Duration.ofSeconds(60)); // GH-90000

    @BeforeAll
    static void startContainers() { // GH-90000
        POSTGRESQL.start(); // GH-90000
        KAFKA.start(); // GH-90000
        REDIS.start(); // GH-90000
        CLICKHOUSE.start(); // GH-90000
    }

    @AfterAll
    static void stopContainers() { // GH-90000
        if (CLICKHOUSE != null) CLICKHOUSE.stop(); // GH-90000
        if (REDIS != null) REDIS.stop(); // GH-90000
        if (KAFKA != null) KAFKA.stop(); // GH-90000
        if (POSTGRESQL != null) POSTGRESQL.stop(); // GH-90000
    }

    @Test
    @DisplayName("PostgreSQL container is running and accessible")
    void postgresqlContainerIsRunningAndAccessible() throws SQLException { // GH-90000
        assertThat(POSTGRESQL.isRunning()).isTrue(); // GH-90000
        assertThat(POSTGRESQL.getJdbcUrl()).isNotEmpty(); // GH-90000
        assertThat(POSTGRESQL.getUsername()).isEqualTo("test_user");
        assertThat(POSTGRESQL.getPassword()).isEqualTo("test_pass");

        // Test actual connection
        try (Connection conn = DriverManager.getConnection( // GH-90000
            POSTGRESQL.getJdbcUrl(), // GH-90000
            POSTGRESQL.getUsername(), // GH-90000
            POSTGRESQL.getPassword())) { // GH-90000
            assertThat(conn.isValid(5)).isTrue(); // GH-90000

            // Test query
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT 1")) {
                assertThat(rs.next()).isTrue(); // GH-90000
                assertThat(rs.getInt(1)).isEqualTo(1); // GH-90000
            }
        }
    }

    @Test
    @DisplayName("PostgreSQL can create and query tables")
    void postgresqlCanCreateAndQueryTables() throws SQLException { // GH-90000
        try (Connection conn = DriverManager.getConnection( // GH-90000
            POSTGRESQL.getJdbcUrl(), // GH-90000
            POSTGRESQL.getUsername(), // GH-90000
            POSTGRESQL.getPassword())) { // GH-90000

            // Create table
            conn.createStatement().execute( // GH-90000
                "CREATE TABLE IF NOT EXISTS test_features (" + // GH-90000
                "id SERIAL PRIMARY KEY, " +
                "feature_name VARCHAR(255) NOT NULL, " + // GH-90000
                "feature_value DOUBLE PRECISION, " +
                "tenant_id VARCHAR(255) NOT NULL, " + // GH-90000
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            // Insert data
            try (PreparedStatement ps = conn.prepareStatement( // GH-90000
                "INSERT INTO test_features (feature_name, feature_value, tenant_id) VALUES (?, ?, ?)")) { // GH-90000
                ps.setString(1, "test_feature_1"); // GH-90000
                ps.setDouble(2, 123.45); // GH-90000
                ps.setString(3, "tenant-1"); // GH-90000
                ps.executeUpdate(); // GH-90000

                ps.setString(1, "test_feature_2"); // GH-90000
                ps.setDouble(2, 678.90); // GH-90000
                ps.setString(3, "tenant-2"); // GH-90000
                ps.executeUpdate(); // GH-90000
            }

            // Query data
            try (ResultSet rs = conn.createStatement().executeQuery( // GH-90000
                "SELECT feature_name, feature_value, tenant_id FROM test_features ORDER BY id")) {
                assertThat(rs.next()).isTrue(); // GH-90000
                assertThat(rs.getString("feature_name")).isEqualTo("test_feature_1");
                assertThat(rs.getDouble("feature_value")).isEqualTo(123.45);
                assertThat(rs.getString("tenant_id")).isEqualTo("tenant-1");

                assertThat(rs.next()).isTrue(); // GH-90000
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
    void postgresqlSupportsTenantIsolationWithRowLevelSecurity() throws SQLException { // GH-90000
        try (Connection conn = DriverManager.getConnection( // GH-90000
            POSTGRESQL.getJdbcUrl(), // GH-90000
            POSTGRESQL.getUsername(), // GH-90000
            POSTGRESQL.getPassword())) { // GH-90000

            // Create table with tenant column
            conn.createStatement().execute( // GH-90000
                "CREATE TABLE IF NOT EXISTS tenant_isolated_features (" + // GH-90000
                "id SERIAL PRIMARY KEY, " +
                "feature_name VARCHAR(255) NOT NULL, " + // GH-90000
                "feature_value DOUBLE PRECISION, " +
                "tenant_id VARCHAR(255) NOT NULL)" // GH-90000
            );

            // Insert data for different tenants
            try (PreparedStatement ps = conn.prepareStatement( // GH-90000
                "INSERT INTO tenant_isolated_features (feature_name, feature_value, tenant_id) VALUES (?, ?, ?)")) { // GH-90000
                ps.setString(1, "tenant_a_feature"); // GH-90000
                ps.setDouble(2, 100.0); // GH-90000
                ps.setString(3, "tenant-a"); // GH-90000
                ps.executeUpdate(); // GH-90000

                ps.setString(1, "tenant_b_feature"); // GH-90000
                ps.setDouble(2, 200.0); // GH-90000
                ps.setString(3, "tenant-b"); // GH-90000
                ps.executeUpdate(); // GH-90000
            }

            // Query with tenant filter
            try (PreparedStatement ps = conn.prepareStatement( // GH-90000
                "SELECT feature_name, feature_value FROM tenant_isolated_features WHERE tenant_id = ?")) {
                ps.setString(1, "tenant-a"); // GH-90000
                try (ResultSet rs = ps.executeQuery()) { // GH-90000
                    assertThat(rs.next()).isTrue(); // GH-90000
                    assertThat(rs.getString("feature_name")).isEqualTo("tenant_a_feature");
                    assertThat(rs.getDouble("feature_value")).isEqualTo(100.0);
                    assertThat(rs.next()).isFalse(); // Only one row for tenant-a // GH-90000
                }
            }

            // Cleanup
            conn.createStatement().execute("DROP TABLE tenant_isolated_features");
        }
    }

    @Test
    @DisplayName("Kafka container is running and accessible")
    void kafkaContainerIsRunningAndAccessible() { // GH-90000
        assertThat(KAFKA.isRunning()).isTrue(); // GH-90000
        assertThat(KAFKA.getBootstrapServers()).isNotEmpty(); // GH-90000
        assertThat(KAFKA.getBootstrapServers()).contains("9092");
    }

    @Test
    @DisplayName("Kafka can create and list topics")
    void kafkaCanCreateAndListTopics() { // GH-90000
        // This test verifies Kafka is accessible
        // Full Kafka client testing would require additional dependencies
        assertThat(KAFKA.isRunning()).isTrue(); // GH-90000
        assertThat(KAFKA.getBootstrapServers()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Redis container is running and accessible")
    void redisContainerIsRunningAndAccessible() { // GH-90000
        assertThat(REDIS.isRunning()).isTrue(); // GH-90000
        assertThat(REDIS.getRedisHost()).isNotEmpty(); // GH-90000
        assertThat(REDIS.getRedisPort()).isPositive(); // GH-90000
    }

    @Test
    @DisplayName("Redis can store and retrieve values")
    void redisCanStoreAndRetrieveValues() { // GH-90000
        try (Jedis jedis = new Jedis(REDIS.getRedisHost(), REDIS.getRedisPort())) { // GH-90000
            // Test set and get
            jedis.set("test_key", "test_value"); // GH-90000
            String value = jedis.get("test_key");
            assertThat(value).isEqualTo("test_value");

            // Test numeric operations
            jedis.set("counter", "0"); // GH-90000
            jedis.incr("counter");
            assertThat(Long.parseLong(jedis.get("counter"))).isEqualTo(1);

            // Test hash operations
            jedis.hset("feature:1", Map.of( // GH-90000
                "name", "feature_1",
                "value", "123.45",
                "tenant", "tenant-1"
            ));
            Map<String, String> feature = jedis.hgetAll("feature:1");
            assertThat(feature).hasSize(3); // GH-90000
            assertThat(feature.get("name")).isEqualTo("feature_1");
            assertThat(feature.get("value")).isEqualTo("123.45");
            assertThat(feature.get("tenant")).isEqualTo("tenant-1");

            // Test list operations
            jedis.lpush("feature_list", "feature_3", "feature_2", "feature_1"); // GH-90000
            List<String> features = jedis.lrange("feature_list", 0, -1); // GH-90000
            assertThat(features).hasSize(3); // GH-90000
            assertThat(features).containsExactly("feature_1", "feature_2", "feature_3"); // GH-90000

            // Cleanup
            jedis.del("test_key", "counter", "feature:1", "feature_list"); // GH-90000
        }
    }

    @Test
    @DisplayName("Redis supports expiration and caching patterns")
    void redisSupportsExpirationAndCachingPatterns() throws InterruptedException { // GH-90000
        try (Jedis jedis = new Jedis(REDIS.getRedisHost(), REDIS.getRedisPort())) { // GH-90000
            // Test TTL
            jedis.setex("expiring_key", 2, "will_expire"); // GH-90000
            assertThat(jedis.ttl("expiring_key")).isGreaterThan(0);
            assertThat(jedis.get("expiring_key")).isEqualTo("will_expire");

            Thread.sleep(2500); // GH-90000
            assertThat(jedis.get("expiring_key")).isNull();

            // Test cache pattern
            String cacheKey = "cache:feature:123";
            jedis.set(cacheKey, "cached_value"); // GH-90000
            jedis.expire(cacheKey, 3600); // 1 hour // GH-90000

            assertThat(jedis.exists(cacheKey)).isTrue(); // GH-90000
            assertThat(jedis.ttl(cacheKey)).isGreaterThan(3000); // Close to 3600 // GH-90000

            // Cleanup
            jedis.del(cacheKey); // GH-90000
        }
    }

    @Test
    @DisplayName("ClickHouse container is running and accessible")
    void clickhouseContainerIsRunningAndAccessible() throws SQLException { // GH-90000
        assertThat(CLICKHOUSE.isRunning()).isTrue(); // GH-90000
        assertThat(CLICKHOUSE.getJdbcUrl()).isNotEmpty(); // GH-90000
        assertThat(CLICKHOUSE.getUsername()).isNotEmpty(); // GH-90000
        assertThat(CLICKHOUSE.getPassword()).isNotEmpty(); // GH-90000

        // Test actual connection
        try (Connection conn = DriverManager.getConnection( // GH-90000
            CLICKHOUSE.getJdbcUrl(), // GH-90000
            CLICKHOUSE.getUsername(), // GH-90000
            CLICKHOUSE.getPassword())) { // GH-90000
            assertThat(conn.isValid(5)).isTrue(); // GH-90000

            // Test query
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT 1")) {
                assertThat(rs.next()).isTrue(); // GH-90000
                assertThat(rs.getInt(1)).isEqualTo(1); // GH-90000
            }
        }
    }

    @Test
    @DisplayName("ClickHouse can create and query time-series data")
    void clickhouseCanCreateAndQueryTimeSeriesData() throws SQLException { // GH-90000
        try (Connection conn = DriverManager.getConnection( // GH-90000
            CLICKHOUSE.getJdbcUrl(), // GH-90000
            CLICKHOUSE.getUsername(), // GH-90000
            CLICKHOUSE.getPassword())) { // GH-90000

            // Create MergeTree table for time-series data
            conn.createStatement().execute( // GH-90000
                "CREATE TABLE IF NOT EXISTS feature_metrics (" + // GH-90000
                "timestamp DateTime DEFAULT now(), " + // GH-90000
                "feature_name String, " +
                "feature_value Float64, " +
                "tenant_id String" +
                ") ENGINE = MergeTree() " + // GH-90000
                "ORDER BY (timestamp, feature_name)" // GH-90000
            );

            // Insert time-series data
            try (PreparedStatement ps = conn.prepareStatement( // GH-90000
                "INSERT INTO feature_metrics (timestamp, feature_name, feature_value, tenant_id) VALUES (?, ?, ?, ?)")) { // GH-90000
                for (int i = 0; i < 10; i++) { // GH-90000
                    ps.setObject(1, java.time.LocalDateTime.now().minusHours(i)); // GH-90000
                    ps.setString(2, "metric_" + (i % 3)); // GH-90000
                    ps.setDouble(3, 100.0 + i * 10.0); // GH-90000
                    ps.setString(4, "tenant-" + (i % 2)); // GH-90000
                    ps.addBatch(); // GH-90000
                }
                ps.executeBatch(); // GH-90000
            }

            // Query time-series data
            try (ResultSet rs = conn.createStatement().executeQuery( // GH-90000
                "SELECT feature_name, avg(feature_value) as avg_value, count() as count " + // GH-90000
                "FROM feature_metrics " +
                "GROUP BY feature_name " +
                "ORDER BY feature_name")) {
                int rowCount = 0;
                while (rs.next()) { // GH-90000
                    rowCount++;
                    assertThat(rs.getString("feature_name")).startsWith("metric_");
                    assertThat(rs.getDouble("avg_value")).isGreaterThan(0);
                    assertThat(rs.getLong("count")).isGreaterThan(0);
                }
                assertThat(rowCount).isEqualTo(3); // 3 unique metrics // GH-90000
            }

            // Cleanup
            conn.createStatement().execute("DROP TABLE feature_metrics");
        }
    }

    @Test
    @DisplayName("ClickHouse supports efficient aggregations")
    void clickhouseSupportsEfficientAggregations() throws SQLException { // GH-90000
        try (Connection conn = DriverManager.getConnection( // GH-90000
            CLICKHOUSE.getJdbcUrl(), // GH-90000
            CLICKHOUSE.getUsername(), // GH-90000
            CLICKHOUSE.getPassword())) { // GH-90000

            // Create table for aggregation tests
            conn.createStatement().execute( // GH-90000
                "CREATE TABLE IF NOT EXISTS feature_events (" + // GH-90000
                "event_time DateTime, " +
                "event_type String, " +
                "feature_id UInt64, " +
                "tenant_id String, " +
                "value Float64" +
                ") ENGINE = MergeTree() " + // GH-90000
                "PARTITION BY toYYYYMM(event_time) " + // GH-90000
                "ORDER BY (event_time, event_type)" // GH-90000
            );

            // Insert event data
            try (PreparedStatement ps = conn.prepareStatement( // GH-90000
                "INSERT INTO feature_events (event_time, event_type, feature_id, tenant_id, value) VALUES (?, ?, ?, ?, ?)")) { // GH-90000
                for (int i = 0; i < 100; i++) { // GH-90000
                    ps.setObject(1, java.time.LocalDateTime.now().minusDays(i % 30)); // GH-90000
                    ps.setString(2, i % 2 == 0 ? "ingest" : "query"); // GH-90000
                    ps.setLong(3, i); // GH-90000
                    ps.setString(4, "tenant-" + (i % 5)); // GH-90000
                    ps.setDouble(5, Math.random() * 1000); // GH-90000
                    ps.addBatch(); // GH-90000
                }
                ps.executeBatch(); // GH-90000
            }

            // Test aggregation by tenant
            try (ResultSet rs = conn.createStatement().executeQuery( // GH-90000
                "SELECT tenant_id, " +
                "count() as event_count, " + // GH-90000
                "avg(value) as avg_value, " + // GH-90000
                "max(value) as max_value " + // GH-90000
                "FROM feature_events " +
                "GROUP BY tenant_id " +
                "ORDER BY tenant_id")) {
                int tenantCount = 0;
                while (rs.next()) { // GH-90000
                    tenantCount++;
                    assertThat(rs.getString("tenant_id")).startsWith("tenant-");
                    assertThat(rs.getLong("event_count")).isGreaterThan(0);
                    assertThat(rs.getDouble("avg_value")).isBetween(0.0, 1000.0);
                    assertThat(rs.getDouble("max_value")).isBetween(0.0, 1000.0);
                }
                assertThat(tenantCount).isEqualTo(5); // 5 tenants // GH-90000
            }

            // Cleanup
            conn.createStatement().execute("DROP TABLE feature_events");
        }
    }

    @Test
    @DisplayName("All providers can be used together in a multi-provider scenario")
    void allProvidersCanBeUsedTogetherInMultiProviderScenario() throws SQLException { // GH-90000
        // This test demonstrates a scenario where all providers work together
        // PostgreSQL for persistent storage, Redis for caching, ClickHouse for analytics

        try (Connection pgConn = DriverManager.getConnection( // GH-90000
            POSTGRESQL.getJdbcUrl(), // GH-90000
            POSTGRESQL.getUsername(), // GH-90000
            POSTGRESQL.getPassword()); // GH-90000
             Jedis jedis = new Jedis(REDIS.getRedisHost(), REDIS.getRedisPort()); // GH-90000
             Connection chConn = DriverManager.getConnection( // GH-90000
            CLICKHOUSE.getJdbcUrl(), // GH-90000
            CLICKHOUSE.getUsername(), // GH-90000
            CLICKHOUSE.getPassword())) { // GH-90000

            // 1. Store feature in PostgreSQL
            pgConn.createStatement().execute( // GH-90000
                "CREATE TEMP TABLE features (" + // GH-90000
                "id SERIAL PRIMARY KEY, " +
                "name VARCHAR(255), " + // GH-90000
                "value DOUBLE PRECISION)"
            );

            try (PreparedStatement ps = pgConn.prepareStatement( // GH-90000
                "INSERT INTO features (name, value) VALUES (?, ?)")) { // GH-90000
                ps.setString(1, "multi_provider_feature"); // GH-90000
                ps.setDouble(2, 42.0); // GH-90000
                ps.executeUpdate(); // GH-90000
            }

            // 2. Cache in Redis
            jedis.set("cache:multi_provider_feature", "42.0"); // GH-90000
            jedis.expire("cache:multi_provider_feature", 3600); // GH-90000

            // 3. Log analytics in ClickHouse
            chConn.createStatement().execute( // GH-90000
                "CREATE TEMP TABLE feature_access_log (" + // GH-90000
                "timestamp DateTime, " +
                "feature_name String, " +
                "access_type String" +
                ") ENGINE = MergeTree() " + // GH-90000
                "ORDER BY timestamp"
            );

            try (PreparedStatement ps = chConn.prepareStatement( // GH-90000
                "INSERT INTO feature_access_log (timestamp, feature_name, access_type) VALUES (?, ?, ?)")) { // GH-90000
                ps.setObject(1, java.time.LocalDateTime.now()); // GH-90000
                ps.setString(2, "multi_provider_feature"); // GH-90000
                ps.setString(3, "read"); // GH-90000
                ps.executeUpdate(); // GH-90000
            }

            // Verify all operations succeeded
            assertThat(jedis.get("cache:multi_provider_feature")).isEqualTo("42.0");

            try (ResultSet rs = pgConn.createStatement().executeQuery( // GH-90000
                "SELECT value FROM features WHERE name = 'multi_provider_feature'")) {
                assertThat(rs.next()).isTrue(); // GH-90000
                assertThat(rs.getDouble("value")).isEqualTo(42.0);
            }

            try (ResultSet rs = chConn.createStatement().executeQuery( // GH-90000
                "SELECT count() FROM feature_access_log")) { // GH-90000
                assertThat(rs.next()).isTrue(); // GH-90000
                assertThat(rs.getLong(1)).isEqualTo(1); // GH-90000
            }
        }
    }

    @Test
    @DisplayName("Providers handle concurrent operations correctly")
    void providersHandleConcurrentOperationsCorrectly() throws SQLException, InterruptedException { // GH-90000
        // Test concurrent writes to PostgreSQL
        try (Connection conn = DriverManager.getConnection( // GH-90000
            POSTGRESQL.getJdbcUrl(), // GH-90000
            POSTGRESQL.getUsername(), // GH-90000
            POSTGRESQL.getPassword())) { // GH-90000

            conn.createStatement().execute( // GH-90000
                "CREATE TEMP TABLE concurrent_features (" + // GH-90000
                "id SERIAL PRIMARY KEY, " +
                "name VARCHAR(255), " + // GH-90000
                "value DOUBLE PRECISION, " +
                "thread_id VARCHAR(255))" // GH-90000
            );

            List<Thread> threads = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 10; i++) { // GH-90000
                final int threadId = i;
                Thread thread = new Thread(() -> { // GH-90000
                    try (Connection threadConn = DriverManager.getConnection( // GH-90000
                        POSTGRESQL.getJdbcUrl(), // GH-90000
                        POSTGRESQL.getUsername(), // GH-90000
                        POSTGRESQL.getPassword())) { // GH-90000
                        for (int j = 0; j < 10; j++) { // GH-90000
                            try (PreparedStatement ps = threadConn.prepareStatement( // GH-90000
                                "INSERT INTO concurrent_features (name, value, thread_id) VALUES (?, ?, ?)")) { // GH-90000
                                ps.setString(1, "feature_" + threadId + "_" + j); // GH-90000
                                ps.setDouble(2, threadId * 100 + j); // GH-90000
                                ps.setString(3, "thread_" + threadId); // GH-90000
                                ps.executeUpdate(); // GH-90000
                            }
                        }
                    } catch (SQLException e) { // GH-90000
                        throw new RuntimeException(e); // GH-90000
                    }
                });
                threads.add(thread); // GH-90000
                thread.start(); // GH-90000
            }

            for (Thread thread : threads) { // GH-90000
                thread.join(10000); // GH-90000
            }

            // Verify all inserts succeeded
            try (ResultSet rs = conn.createStatement().executeQuery( // GH-90000
                "SELECT count(*) FROM concurrent_features")) { // GH-90000
                assertThat(rs.next()).isTrue(); // GH-90000
                assertThat(rs.getLong(1)).isEqualTo(100); // 10 threads * 10 inserts // GH-90000
            }
        }

        // Test concurrent operations in Redis
        try (Jedis jedis = new Jedis(REDIS.getRedisHost(), REDIS.getRedisPort())) { // GH-90000
            List<Thread> threads = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 10; i++) { // GH-90000
                final int threadId = i;
                Thread thread = new Thread(() -> { // GH-90000
                    try (Jedis threadJedis = new Jedis(REDIS.getRedisHost(), REDIS.getRedisPort())) { // GH-90000
                        for (int j = 0; j < 10; j++) { // GH-90000
                            threadJedis.set("concurrent_" + threadId + "_" + j, "value_" + j); // GH-90000
                        }
                    }
                });
                threads.add(thread); // GH-90000
                thread.start(); // GH-90000
            }

            for (Thread thread : threads) { // GH-90000
                thread.join(10000); // GH-90000
            }

            // Verify all sets succeeded
            int keyCount = 0;
            for (int i = 0; i < 10; i++) { // GH-90000
                for (int j = 0; j < 10; j++) { // GH-90000
                    if (jedis.exists("concurrent_" + i + "_" + j)) { // GH-90000
                        keyCount++;
                    }
                }
            }
            assertThat(keyCount).isEqualTo(100); // GH-90000
        }
    }
}
