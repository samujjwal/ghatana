/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 *   <li>PostgreSQL (with pgvector extension for semantic search)</li> // GH-90000
 *   <li>Kafka (for event streaming)</li> // GH-90000
 *   <li>Redis (for caching)</li> // GH-90000
 *   <li>S3 (for object storage via LocalStack)</li> // GH-90000
 * </ul>
 *
 * <p>Containers are started once per test suite (singleton pattern) and reused // GH-90000
 * across tests to minimize startup overhead.
 *
 * <p><strong>Usage:</strong>
 * <pre>
 * {@code
 * @BeforeAll
 * static void setUp() { // GH-90000
 *     TestContainersConfig.postgres().start(); // GH-90000
 *     TestContainersConfig.kafka().start(); // GH-90000
 * }
 *
 * @AfterAll
 * static void tearDown() { // GH-90000
 *     TestContainersConfig.stopAll(); // GH-90000
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

    private static final Map<String, GenericContainer<?>> CONTAINERS = new ConcurrentHashMap<>(); // GH-90000

    private TestContainersConfig() {} // GH-90000

    /**
     * Get or create PostgreSQL container with pgvector extension.
     *
     * @return PostgreSQL container instance
     */
    public static PostgreSQLContainer<?> postgres() { // GH-90000
        return (PostgreSQLContainer<?>) CONTAINERS.computeIfAbsent("postgres", k -> { // GH-90000
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>( // GH-90000
                DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
            )
                .withDatabaseName("datacloud_test")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("db/init_pgvector.sql");

            container.addExposedPort(5432); // GH-90000
            return container;
        });
    }

    /**
     * Get or create Kafka container.
     *
     * @return Kafka container instance
     */
    public static KafkaContainer kafka() { // GH-90000
        return (KafkaContainer) CONTAINERS.computeIfAbsent("kafka", k -> { // GH-90000
            KafkaContainer container = new KafkaContainer( // GH-90000
                DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
            )
                .withEmbeddedZookeeper(); // GH-90000

            return container;
        });
    }

    /**
     * Get or create Redis container.
     *
     * @return Redis container instance
     */
    public static GenericContainer<?> redis() { // GH-90000
        return CONTAINERS.computeIfAbsent("redis", k -> { // GH-90000
            GenericContainer<?> container = new GenericContainer<>( // GH-90000
                DockerImageName.parse("redis:7-alpine")
            )
                .withExposedPorts(6379) // GH-90000
                .withCommand("redis-server", "--appendonly", "yes"); // GH-90000

            return container;
        });
    }

    /**
     * Get or create S3 (LocalStack) container. // GH-90000
     *
     * @return LocalStack container configured for S3
     */
    public static LocalStackContainer s3() { // GH-90000
        return (LocalStackContainer) CONTAINERS.computeIfAbsent("s3", k -> { // GH-90000
            LocalStackContainer container = new LocalStackContainer( // GH-90000
                DockerImageName.parse("localstack/localstack:2.3")
            )
                .withServices(S3) // GH-90000
                .withEnv("AWS_ACCESS_KEY_ID", "test") // GH-90000
                .withEnv("AWS_SECRET_ACCESS_KEY", "test") // GH-90000
                .withEnv("AWS_DEFAULT_REGION", "us-east-1"); // GH-90000

            return container;
        });
    }

    /**
     * Get JDBC URL for PostgreSQL container.
     *
     * @return JDBC connection URL
     * @throws IllegalStateException if container not started
     */
    public static String getPostgresJdbcUrl() { // GH-90000
        PostgreSQLContainer<?> container = (PostgreSQLContainer<?>) CONTAINERS.get("postgres");
        if (container == null || !container.isRunning()) { // GH-90000
            throw new IllegalStateException("PostgreSQL container not started. Call postgres().start() first.");
        }
        return container.getJdbcUrl(); // GH-90000
    }

    /**
     * Get Kafka bootstrap servers address.
     *
     * @return Kafka bootstrap servers string
     * @throws IllegalStateException if container not started
     */
    public static String getKafkaBootstrapServers() { // GH-90000
        KafkaContainer container = (KafkaContainer) CONTAINERS.get("kafka");
        if (container == null || !container.isRunning()) { // GH-90000
            throw new IllegalStateException("Kafka container not started. Call kafka().start() first.");
        }
        return container.getBootstrapServers(); // GH-90000
    }

    /**
     * Get Redis connection URL.
     *
     * @return Redis connection string (host:port) // GH-90000
     * @throws IllegalStateException if container not started
     */
    public static String getRedisUrl() { // GH-90000
        GenericContainer<?> container = CONTAINERS.get("redis");
        if (container == null || !container.isRunning()) { // GH-90000
            throw new IllegalStateException("Redis container not started. Call redis().start() first.");
        }
        return container.getHost() + ":" + container.getMappedPort(6379); // GH-90000
    }

    /**
     * Get S3 endpoint URL.
     *
     * @return S3 endpoint URL
     * @throws IllegalStateException if container not started
     */
    public static String getS3EndpointUrl() { // GH-90000
        LocalStackContainer container = (LocalStackContainer) CONTAINERS.get("s3");
        if (container == null || !container.isRunning()) { // GH-90000
            throw new IllegalStateException("S3 container not started. Call s3().start() first.");
        }
        return container.getEndpointOverride(S3).toString(); // GH-90000
    }

    /**
     * Get S3 region for LocalStack.
     *
     * @return S3 region
     */
    public static String getS3Region() { // GH-90000
        return "us-east-1";
    }

    /**
     * Get S3 access key for LocalStack.
     *
     * @return access key
     */
    public static String getS3AccessKey() { // GH-90000
        return "test";
    }

    /**
     * Get S3 secret key for LocalStack.
     *
     * @return secret key
     */
    public static String getS3SecretKey() { // GH-90000
        return "test";
    }

    /**
     * Create S3 bucket if it doesn't exist.
     *
     * @param bucketName name of bucket to create
     * @throws IllegalStateException if container not started
     */
    public static void createS3Bucket(String bucketName) { // GH-90000
        LocalStackContainer container = (LocalStackContainer) CONTAINERS.get("s3");
        if (container == null || !container.isRunning()) { // GH-90000
            throw new IllegalStateException("S3 container not started. Call s3().start() first.");
        }

        try {
            container.execInContainer( // GH-90000
                "awslocal", "s3", "mb", "s3://" + bucketName
            );
        } catch (Exception e) { // GH-90000
            throw new RuntimeException("Failed to create S3 bucket: " + bucketName, e); // GH-90000
        }
    }

    /**
     * Stop all containers and clear registry.
     * Call in @AfterAll to clean up resources.
     */
    public static void stopAll() { // GH-90000
        CONTAINERS.values().forEach(container -> { // GH-90000
            if (container.isRunning()) { // GH-90000
                container.stop(); // GH-90000
            }
        });
        CONTAINERS.clear(); // GH-90000
    }

    /**
     * Stop a specific container by name.
     *
     * @param name container name (postgres, kafka, redis, s3) // GH-90000
     */
    public static void stop(String name) { // GH-90000
        GenericContainer<?> container = CONTAINERS.get(name); // GH-90000
        if (container != null && container.isRunning()) { // GH-90000
            container.stop(); // GH-90000
        }
    }

    /**
     * Check if container is running.
     *
     * @param name container name
     * @return true if running
     */
    public static boolean isRunning(String name) { // GH-90000
        GenericContainer<?> container = CONTAINERS.get(name); // GH-90000
        return container != null && container.isRunning(); // GH-90000
    }
}
