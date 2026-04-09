package com.ghatana.platform.testing.internal.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Internal Redis test container support for platform integration tests.
 *
 * @doc.type class
 * @doc.purpose Internal Redis test container support for platform integration tests
 * @doc.layer core
 * @doc.pattern Component
 */
public final class RedisTestContainer {
    private static final Logger log = LoggerFactory.getLogger(RedisTestContainer.class);
    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final int REDIS_PORT = 6379;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static GenericContainer<?> container;

    private RedisTestContainer() {
    }

    public static synchronized GenericContainer<?> getInstance() {
        if (container == null) {
            log.info("Creating new Redis container with image: {}", REDIS_IMAGE);

            container = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                    .withExposedPorts(REDIS_PORT)
                    .withReuse(true)
                    .withStartupTimeout(Duration.ofMinutes(2))
                    .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("REDIS"));

            container.setWaitStrategy(
                    Wait.forLogMessage(".*Ready to accept connections.*\\n", 1)
                            .withStartupTimeout(Duration.ofMinutes(2)));

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (container != null && container.isRunning()) {
                    log.info("Shutting down Redis container in shutdown hook");
                    container.stop();
                }
            }));
        }
        return container;
    }

    public static void start() {
        if (initialized.compareAndSet(false, true)) {
            final GenericContainer<?> instance = getInstance();
            try {
                log.info("Starting Redis container...");
                instance.start();

                if (!instance.isRunning()) {
                    throw new IllegalStateException("Redis container failed to start");
                }

                log.info("Redis test container started at: {}:{}", getHost(), getPort());

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (instance.isRunning()) {
                        log.info("Shutting down Redis container");
                        instance.stop();
                    }
                }));
            } catch (Exception e) {
                log.error("Failed to start Redis container", e);
                if (instance.isRunning()) {
                    instance.stop();
                }
                initialized.set(false);
                throw new RuntimeException("Failed to start Redis container", e);
            }
        } else {
            log.debug("Redis container already initialized");
        }
    }

    public static void stop() {
        if (container != null && initialized.compareAndSet(true, false)) {
            try {
                container.stop();
                log.info("Redis test container stopped");
            } catch (Exception e) {
                log.warn("Error stopping Redis container", e);
            } finally {
                container = null;
            }
        }
    }

    public static String getHost() {
        return getInstance().getHost();
    }

    public static Integer getPort() {
        return getInstance().getMappedPort(REDIS_PORT);
    }

    public static String getConnectionString() {
        return getHost() + ":" + getPort();
    }
}
