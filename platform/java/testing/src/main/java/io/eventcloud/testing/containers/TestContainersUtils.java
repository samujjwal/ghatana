package com.ghatana.platform.testing.containers;

import org.testcontainers.containers.*;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for managing TestContainers in tests.
 *
 * @doc.type class
 * @doc.purpose Shared TestContainers lifecycle management for PostgreSQL, Kafka, MongoDB, and Redis
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class TestContainersUtils {

    private static final Map<String, ContainerState> containers = new ConcurrentHashMap<>();
    private static final Object lock = new Object();
    private static volatile boolean initialized = false;

    private TestContainersUtils() {
        // Utility class
    }

    /**
     * Get or create and start a PostgreSQL container.
     *
     * @param name the name of the container
     * @return the PostgreSQL container
     */
    public static PostgreSQLContainer<?> getOrCreatePostgres(String name) {
        return (PostgreSQLContainer<?>) containers.computeIfAbsent("postgres-" + name, k -> {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:13-alpine")
                .withDatabaseName("testdb")
                .withUsername("testuser")
                .withPassword("testpass")
                .withReuse(true);
            container.start();
            return container;
        });
    }

    /**
     * Get or create a Kafka container.
     *
     * @param name the name of the container
     * @return the Kafka container
     */
    public static KafkaContainer getOrCreateKafka(String name) {
        return (KafkaContainer) containers.computeIfAbsent("kafka-" + name, k -> 
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.1"))
                .withReuse(true)
        );
    }

    /**
     * Get or create a MongoDB container.
     *
     * @param name the name of the container
     * @return the MongoDB container
     */
    public static MongoDBContainer getOrCreateMongo(String name) {
        return (MongoDBContainer) containers.computeIfAbsent("mongo-" + name, k -> 
            new MongoDBContainer("mongo:4.4")
                .withReuse(true)
        );
    }

    /**
     * Get or create a Redis container.
     *
     * @param name the name of the container
     * @return the Redis container
     */
    public static GenericContainer<?> getOrCreateRedis(String name) {
        return (GenericContainer<?>) containers.computeIfAbsent("redis-" + name, k -> 
            new GenericContainer<>("redis:6-alpine")
                .withExposedPorts(6379)
                .withReuse(true)
        );
    }

    /**
     * Start all registered containers.
     */
    public static void startAll() {
        if (initialized) {
            return;
        }
        
        synchronized (lock) {
            if (!initialized) {
                containers.values().forEach(container -> {
                    if (container instanceof Startable) {
                        try {
                            // Start the container if it's not already started
                            ((Startable) container).start();
                        } catch (Exception e) {
                            System.err.println("Error starting container: " + e.getMessage());
                            throw new RuntimeException("Failed to start container", e);
                        }
                    }
                });
                initialized = true;
            }
        }
    }

    /**
     * Stop all registered containers.
     */
    public static void stopAll() {
        if (!initialized) {
            return;
        }
        
        synchronized (lock) {
            if (initialized) {
                containers.values().forEach(container -> {
                    if (container instanceof AutoCloseable) {
                        try {
                            ((AutoCloseable) container).close();
                        } catch (Exception e) {
                            // Log error but continue stopping other containers
                            System.err.println("Error stopping container: " + e.getMessage());
                        }
                    }
                });
                containers.clear();
                initialized = false;
            }
        }
    }

    /**
     * Get the connection URL for a container.
     *
     * @param container the container
     * @return the connection URL
     */
    public static String getConnectionUrl(ContainerState container) {
        return String.format("%s://%s:%d",
            container instanceof PostgreSQLContainer ? "postgresql" :
            container instanceof KafkaContainer ? "kafka" :
            container instanceof MongoDBContainer ? "mongodb" : "tcp",
            container.getHost(),
            container.getFirstMappedPort()
        );
    }
}
