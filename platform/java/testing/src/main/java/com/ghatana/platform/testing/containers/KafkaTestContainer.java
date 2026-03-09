package com.ghatana.platform.testing.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton Kafka test container for integration tests.
 *
 * @doc.type class
 * @doc.purpose Kafka test container
 * @doc.layer core
 * @doc.pattern Component
 */
public class KafkaTestContainer {
    private static final Logger log = LoggerFactory.getLogger(KafkaTestContainer.class);
    private static final String KAFKA_IMAGE = "confluentinc/cp-kafka:7.4.0";
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static KafkaContainer container;

    private KafkaTestContainer() {
        // Singleton
    }

    /**
     * Get the singleton instance of the Kafka container.
     * Starts the container if it's not already running.
     *
     * @return the Kafka container instance
     */
    public static synchronized KafkaContainer getInstance() {
        if (container == null) {
            log.info("Creating new Kafka container with image: {}", KAFKA_IMAGE);
            
            container = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE))
                .withReuse(true)
                .withStartupTimeout(Duration.ofMinutes(2))
                .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("KAFKA"));
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (container != null && container.isRunning()) {
                    log.info("Shutting down Kafka container in shutdown hook");
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
            final KafkaContainer instance = getInstance();
            try {
                log.info("Starting Kafka container...");
                instance.start();
                
                if (!instance.isRunning()) {
                    throw new IllegalStateException("Kafka container failed to start");
                }
                
                log.info("Kafka test container started at: {}", getBootstrapServers());
                
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (instance.isRunning()) {
                        log.info("Shutting down Kafka container");
                        instance.stop();
                    }
                }));
                
            } catch (Exception e) {
                log.error("Failed to start Kafka container", e);
                if (instance.isRunning()) {
                    instance.stop();
                }
                initialized.set(false);
                throw new RuntimeException("Failed to start Kafka container", e);
            }
        } else {
            log.debug("Kafka container already initialized");
        }
    }

    /**
     * Stop the container if it's running.
     */
    public static void stop() {
        if (container != null && initialized.compareAndSet(true, false)) {
            try {
                container.stop();
                log.info("Kafka test container stopped");
            } catch (Exception e) {
                log.warn("Error stopping Kafka container", e);
            } finally {
                container = null;
            }
        }
    }

    /**
     * Get the Kafka bootstrap servers connection string.
     *
     * @return the bootstrap servers string
     */
    public static String getBootstrapServers() {
        return getInstance().getBootstrapServers();
    }
}
