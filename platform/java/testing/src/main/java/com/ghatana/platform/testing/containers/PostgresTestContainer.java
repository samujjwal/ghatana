package com.ghatana.platform.testing.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton PostgreSQL test container for integration tests.
 
 *
 * @doc.type class
 * @doc.purpose Postgres test container
 * @doc.layer core
 * @doc.pattern Component
*/
public class PostgresTestContainer {
    private static final Logger log = LoggerFactory.getLogger(PostgresTestContainer.class);
    private static final String POSTGRES_IMAGE = "postgres:15.2";
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static PostgreSQLContainer<?> container;

    private PostgresTestContainer() {
        // Singleton
    }

    /**
     * Get the singleton instance of the PostgreSQL container.
     * Starts the container if it's not already running.
     *
     * @return the PostgreSQL container instance
     */
    public static synchronized PostgreSQLContainer<?> getInstance() {
        if (container == null) {
            log.info("Creating new PostgreSQL container with image: {}", POSTGRES_IMAGE);
            
            // Create and configure the container
            container = new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true)
                .withStartupTimeout(Duration.ofMinutes(2))
                .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("POSTGRES"));
            
            // Configure additional container parameters
            container.setWaitStrategy(
                Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 1)
                    .withStartupTimeout(Duration.ofMinutes(2))
            );
            
            // Register shutdown hook to ensure container is stopped when JVM exits
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (container != null && container.isRunning()) {
                    log.info("Shutting down PostgreSQL container in shutdown hook");
                    container.stop();
                }
            }));
        }
        return container;
    }

    /**
     * Start the container if it's not already running.
     */
    public static void start() {
        if (initialized.compareAndSet(false, true)) {
            final PostgreSQLContainer<?> instance = getInstance();
            try {
                log.info("Starting PostgreSQL container...");
                instance.start();
                
                // Verify the container is actually running
                if (!instance.isRunning()) {
                    throw new IllegalStateException("PostgreSQL container failed to start");
                }
                
                log.info("PostgreSQL test container started at: {}", getJdbcUrl());
                log.info("  Username: {}", getUsername());
                log.info("  Database: testdb");
                
                // Register shutdown hook to ensure container is stopped when JVM exits
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (instance.isRunning()) {
                        log.info("Shutting down PostgreSQL container");
                        instance.stop();
                    }
                }));
                
            } catch (Exception e) {
                log.error("Failed to start PostgreSQL container", e);
                if (instance.isRunning()) {
                    instance.stop();
                }
                initialized.set(false); // Reset the flag to allow retry
                throw new RuntimeException("Failed to start PostgreSQL container", e);
            }
        } else {
            log.debug("PostgreSQL container already initialized");
        }
    }

    /**
     * Stop the container if it's running.
     */
    public static void stop() {
        if (container != null && initialized.compareAndSet(true, false)) {
            try {
                container.stop();
                log.info("PostgreSQL test container stopped");
            } catch (Exception e) {
                log.warn("Error stopping PostgreSQL container", e);
            } finally {
                container = null;
            }
        }
    }

    /**
     * Check whether a Docker environment is available for Testcontainers.
     *
     * @return true if Docker is available, false otherwise
     */
    public static boolean isDockerAvailable() {
        try {
            org.testcontainers.DockerClientFactory.instance().client();
            return true;
        } catch (Throwable t) {
            log.warn("Docker environment not available for Testcontainers", t);
            return false;
        }
    }

    /**
     * Get the JDBC URL for the test database.
     *
     * @return the JDBC URL
     */
    public static String getJdbcUrl() {
        return getInstance().getJdbcUrl();
    }

    /**
     * Get the database username.
     *
     * @return the database username
     */
    public static String getUsername() {
        return getInstance().getUsername();
    }

    /**
     * Get the database password.
     *
     * @return the database password
     */
    public static String getPassword() {
        return getInstance().getPassword();
    }
}
