package com.ghatana.platform.testing.fixtures;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * E2E Test Fixtures — Pre-configured test containers for common scenarios.
 * 
 * Provides:
 * - PostgreSQL database container
 * - Redis cache container
 * - Kafka message broker container
 * - Common fixture configurations
 * 
 * Usage:
 * ```java
 * class AgentExecutionE2ETest {
 *   static PostgreSQLContainer<?> db = E2ETestFixtures.postgresDatabase();
 *   static GenericContainer<?> redis = E2ETestFixtures.redisCache();
 *   
 *   @BeforeEach
 *   void setUp() {
 *     db.start();
 *     redis.start();
 *   }
 * }
 * ```
 *
 * @doc.type class
 * @doc.purpose Preconfigured testcontainers fixtures for end-to-end platform tests
 * @doc.layer core
 * @doc.pattern Test Support
 */
public class E2ETestFixtures {

    /**
     * Configure PostgreSQL database for testing
     * - Version: 14.x (stable, well-tested)
     * - Port: 5432 (internal)
     * - Database: testdb
     * - User: postgres
     * - Password: testpass
     */
    public static PostgreSQLContainer<?> postgresDatabase() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:14"))
            .withDatabaseName("testdb")
            .withUsername("postgres")
            .withPassword("testpass")
            .withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
            .withStartupTimeout(Duration.ofSeconds(60));
    }

    /**
     * Configure Redis cache for testing
     * - Version: 7.x (stable)
     * - Port: 6379 (internal)
     * - No authentication (test environment)
     */
    public static GenericContainer<?> redisCache() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
            .withStartupTimeout(Duration.ofSeconds(30));
    }

    /**
     * Configure Kafka message broker for testing
     * - Version: 7.x (consistent with production)
     * - Port: 9092 (broker)
     * - Uses Zookeeper internally
     */
    public static GenericContainer<?> kafkaBroker() {
        return new GenericContainer<>(DockerImageName.parse("confluentinc/cp-kafka:7.0.0"))
            .withExposedPorts(9092)
            .withEnv("KAFKA_BROKER_ID", "1")
            .withEnv("KAFKA_ZOOKEEPER_CONNECT", "localhost:2181")
            .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://localhost:9092")
            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
            .waitingFor(Wait.forLogMessage(".*started.*", 1))
            .withStartupTimeout(Duration.ofSeconds(60));
    }

    /**
     * Shared fixture wrapper for multi-container scenarios
     * 
     * Usage:
     * ```java
     * E2ETestFixtures.Shared fixtures = E2ETestFixtures.createSharedFixtures();
     * fixtures.start();
     * 
     * // Use fixtures.database(), fixtures.cache(), etc.
     * 
     * fixtures.stop();
     * ```
     */
    public static class Shared {
        private PostgreSQLContainer<?> database;
        private GenericContainer<?> cache;
        private GenericContainer<?> kafka;

        public Shared(boolean withDb, boolean withCache, boolean withKafka) {
            if (withDb) this.database = postgresDatabase();
            if (withCache) this.cache = redisCache();
            if (withKafka) this.kafka = kafkaBroker();
        }

        public void start() {
            if (database != null) database.start();
            if (cache != null) cache.start();
            if (kafka != null) kafka.start();
        }

        public void stop() {
            if (database != null) database.stop();
            if (cache != null) cache.stop();
            if (kafka != null) kafka.stop();
        }

        public PostgreSQLContainer<?> database() { return database; }
        public GenericContainer<?> cache() { return cache; }
        public GenericContainer<?> kafka() { return kafka; }

        public String getDatabaseUrl() {
            return database != null ? database.getJdbcUrl() : null;
        }

        public String getCacheHost() {
            return cache != null ? cache.getHost() : null;
        }

        public int getCachePort() {
            return cache != null ? cache.getFirstMappedPort() : 0;
        }
    }

    /**
     * Create shared fixtures builder
     */
    public static SharedBuilder shared() {
        return new SharedBuilder();
    }

    public static class SharedBuilder {
        private boolean withDb = false;
        private boolean withCache = false;
        private boolean withKafka = false;

        public SharedBuilder withDatabase() {
            this.withDb = true;
            return this;
        }

        public SharedBuilder withCache() {
            this.withCache = true;
            return this;
        }

        public SharedBuilder withKafka() {
            this.withKafka = true;
            return this;
        }

        public Shared build() {
            return new Shared(withDb, withCache, withKafka);
        }
    }

    /**
     * Test data builders for common scenarios
     */
    public static class TestData {
        private final Map<String, Object> data;

        public TestData() {
            this.data = new HashMap<>();
        }

        public TestData withField(String key, Object value) {
            data.put(key, value);
            return this;
        }

        public Map<String, Object> build() {
            return new HashMap<>(data);
        }

        /**
         * Sample agent input for E2E testing
         */
        public static Map<String, Object> sampleAgentInput(String type) {
            return switch (type) {
                case "deterministic" -> Map.of(
                    "agentType", "DETERMINISTIC",
                    "input", "test input",
                    "timeout", 5000L
                );
                case "probabilistic" -> Map.of(
                    "agentType", "PROBABILISTIC",
                    "input", "test input",
                    "timeout", 10000L
                );
                case "composite" -> Map.of(
                    "agentType", "COMPOSITE",
                    "subAgents", new String[]{"agent1", "agent2"},
                    "timeout", 15000L
                );
                default -> Map.of(
                    "agentType", "UNKNOWN",
                    "input", "test input",
                    "timeout", 5000L
                );
            };
        }

        /**
         * Sample workflow definition for E2E testing
         */
        public static Map<String, Object> sampleWorkflowDefinition() {
            return Map.of(
                "name", "E2E Test Workflow",
                "steps", new Object[]{
                    Map.of("name", "step1", "action", "execute"),
                    Map.of("name", "step2", "action", "execute"),
                    Map.of("name", "step3", "action", "execute")
                },
                "compensation", new Object[]{
                    Map.of("name", "step3", "action", "compensate"),
                    Map.of("name", "step2", "action", "compensate"),
                    Map.of("name", "step1", "action", "compensate")
                }
            );
        }
    }
}
