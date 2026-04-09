package com.ghatana.platform.testing.internal.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Internal PostgreSQL test container support for platform integration tests.
 *
 * @doc.type class
 * @doc.purpose Internal Postgres test container support for platform integration tests
 * @doc.layer core
 * @doc.pattern Component
 */
public final class PostgresTestContainer {
    private static final Logger log = LoggerFactory.getLogger(PostgresTestContainer.class);
    private static final String POSTGRES_IMAGE = "postgres:15.2";
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static PostgreSQLContainer<?> container;

    private PostgresTestContainer() {
    }

    public static synchronized PostgreSQLContainer<?> getInstance() {
        if (container == null) {
            log.info("Creating new PostgreSQL container with image: {}", POSTGRES_IMAGE);
            container = new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true)
                    .withStartupTimeout(Duration.ofMinutes(2))
                    .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("POSTGRES"));

            container.setWaitStrategy(
                    Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 1)
                            .withStartupTimeout(Duration.ofMinutes(2)));

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (container != null && container.isRunning()) {
                    log.info("Shutting down PostgreSQL container in shutdown hook");
                    container.stop();
                }
            }));
        }
        return container;
    }

    public static void start() {
        if (initialized.compareAndSet(false, true)) {
            final PostgreSQLContainer<?> instance = getInstance();
            try {
                log.info("Starting PostgreSQL container...");
                instance.start();

                if (!instance.isRunning()) {
                    throw new IllegalStateException("PostgreSQL container failed to start");
                }

                log.info("PostgreSQL test container started at: {}", getJdbcUrl());
                log.info("  Username: {}", getUsername());
                log.info("  Database: testdb");

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
                initialized.set(false);
                throw new RuntimeException("Failed to start PostgreSQL container", e);
            }
        } else {
            log.debug("PostgreSQL container already initialized");
        }
    }

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

    public static boolean isDockerAvailable() {
        try {
            org.testcontainers.DockerClientFactory.instance().client();
            return true;
        } catch (Throwable t) {
            log.warn("Docker environment not available for Testcontainers", t);
            return false;
        }
    }

    public static String getJdbcUrl() {
        return getInstance().getJdbcUrl();
    }

    public static String getUsername() {
        return getInstance().getUsername();
    }

    public static String getPassword() {
        return getInstance().getPassword();
    }
}
