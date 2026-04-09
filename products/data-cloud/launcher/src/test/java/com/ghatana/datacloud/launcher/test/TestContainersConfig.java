/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.test;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

/**
 * Testcontainers configuration for Data Cloud integration tests.
 *
 * <p>Provides shared, reusable container instances for:
 * <ul>
 *   <li>PostgreSQL (with pgvector extension for semantic search)</li>
 *   <li>Kafka (for event streaming)</li>
 *   <li>Redis (for caching)</li>
 *   <li>S3 (for object storage via LocalStack)</li>
 * </ul>
 *
 * <p>Containers are started once per test suite (singleton pattern) and reused
 * across tests to minimize startup overhead.
 *
 * <p><strong>Usage:</strong>
 * <pre>
 * {@code
 * @BeforeAll
 * static void setUp() {
 *     TestContainersConfig.postgres().start();
 *     TestContainersConfig.kafka().start();
 * }
 *
 * @AfterAll
 * static void tearDown() {
 *     TestContainersConfig.stopAll();
 * }
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Testcontainers configuration for integration testing with real databases
 * @doc.layer product
 * @doc.pattern Test Infrastructure, Singleton
 */
public final class TestContainersConfig {

    private static final Map<String, GenericContainer<?>> CONTAINERS = new ConcurrentHashMap<>();

    private TestContainersConfig() {}

    /**
     * Get or create PostgreSQL container with pgvector extension.
     *
     * @return PostgreSQL container instance
     */
    public static PostgreSQLContainer<?> postgres() {
        return (PostgreSQLContainer<?>) CONTAINERS.computeIfAbsent("postgres", k -> {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
            )
                .withDatabaseName("datacloud_test")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("db/init_pgvector.sql");

            container.addExposedPort(5432);
            return container;
        });
    }

    /**
     * Get or create Kafka container.
     *
     * @return Kafka container instance
     */
    public static KafkaContainer kafka() {
        return (KafkaContainer) CONTAINERS.computeIfAbsent("kafka", k -> {
            KafkaContainer container = new KafkaContainer(
                DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
            )
                .withEmbeddedZookeeper();

            return container;
        });
    }

    /**
     * Get or create Redis container.
     *
     * @return Redis container instance
     */
    public static GenericContainer<?> redis() {
        return CONTAINERS.computeIfAbsent("redis", k -> {
            GenericContainer<?> container = new GenericContainer<>(
                DockerImageName.parse("redis:7-alpine")
            )
                .withExposedPorts(6379)
                .withCommand("redis-server", "--appendonly", "yes");

            return container;
        });
    }

    /**
     * Get or create S3 (LocalStack) container.
     *
     * @return LocalStack container configured for S3
     */
    public static LocalStackContainer s3() {
        return (LocalStackContainer) CONTAINERS.computeIfAbsent("s3", k -> {
            LocalStackContainer container = new LocalStackContainer(
                DockerImageName.parse("localstack/localstack:2.3")
            )
                .withServices(S3)
                .withEnv("AWS_ACCESS_KEY_ID", "test")
                .withEnv("AWS_SECRET_ACCESS_KEY", "test")
                .withEnv("AWS_DEFAULT_REGION", "us-east-1");

            return container;
        });
    }

    /**
     * Get JDBC URL for PostgreSQL container.
     *
     * @return JDBC connection URL
     * @throws IllegalStateException if container not started
     */
    public static String getPostgresJdbcUrl() {
        PostgreSQLContainer<?> container = (PostgreSQLContainer<?>) CONTAINERS.get("postgres");
        if (container == null || !container.isRunning()) {
            throw new IllegalStateException("PostgreSQL container not started. Call postgres().start() first.");
        }
        return container.getJdbcUrl();
    }

    /**
     * Get Kafka bootstrap servers address.
     *
     * @return Kafka bootstrap servers string
     * @throws IllegalStateException if container not started
     */
    public static String getKafkaBootstrapServers() {
        KafkaContainer container = (KafkaContainer) CONTAINERS.get("kafka");
        if (container == null || !container.isRunning()) {
            throw new IllegalStateException("Kafka container not started. Call kafka().start() first.");
        }
        return container.getBootstrapServers();
    }

    /**
     * Get Redis connection URL.
     *
     * @return Redis connection string (host:port)
     * @throws IllegalStateException if container not started
     */
    public static String getRedisUrl() {
        GenericContainer<?> container = CONTAINERS.get("redis");
        if (container == null || !container.isRunning()) {
            throw new IllegalStateException("Redis container not started. Call redis().start() first.");
        }
        return container.getHost() + ":" + container.getMappedPort(6379);
    }

    /**
     * Get S3 endpoint URL.
     *
     * @return S3 endpoint URL
     * @throws IllegalStateException if container not started
     */
    public static String getS3EndpointUrl() {
        LocalStackContainer container = (LocalStackContainer) CONTAINERS.get("s3");
        if (container == null || !container.isRunning()) {
            throw new IllegalStateException("S3 container not started. Call s3().start() first.");
        }
        return container.getEndpointOverride(S3).toString();
    }

    /**
     * Get S3 region for LocalStack.
     *
     * @return S3 region
     */
    public static String getS3Region() {
        return "us-east-1";
    }

    /**
     * Get S3 access key for LocalStack.
     *
     * @return access key
     */
    public static String getS3AccessKey() {
        return "test";
    }

    /**
     * Get S3 secret key for LocalStack.
     *
     * @return secret key
     */
    public static String getS3SecretKey() {
        return "test";
    }

    /**
     * Create S3 bucket if it doesn't exist.
     *
     * @param bucketName name of bucket to create
     * @throws IllegalStateException if container not started
     */
    public static void createS3Bucket(String bucketName) {
        LocalStackContainer container = (LocalStackContainer) CONTAINERS.get("s3");
        if (container == null || !container.isRunning()) {
            throw new IllegalStateException("S3 container not started. Call s3().start() first.");
        }

        try {
            container.execInContainer(
                "awslocal", "s3", "mb", "s3://" + bucketName
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create S3 bucket: " + bucketName, e);
        }
    }

    /**
     * Stop all containers and clear registry.
     * Call in @AfterAll to clean up resources.
     */
    public static void stopAll() {
        CONTAINERS.values().forEach(container -> {
            if (container.isRunning()) {
                container.stop();
            }
        });
        CONTAINERS.clear();
    }

    /**
     * Stop a specific container by name.
     *
     * @param name container name (postgres, kafka, redis, s3)
     */
    public static void stop(String name) {
        GenericContainer<?> container = CONTAINERS.get(name);
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    /**
     * Check if container is running.
     *
     * @param name container name
     * @return true if running
     */
    public static boolean isRunning(String name) {
        GenericContainer<?> container = CONTAINERS.get(name);
        return container != null && container.isRunning();
    }
}
