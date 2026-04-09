package com.ghatana.platform.testing.internal.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal manager for test container lifecycle orchestration.
 *
 * @doc.type class
 * @doc.purpose Internal test container lifecycle orchestration support
 * @doc.layer core
 * @doc.pattern Manager
 */
public class TestContainerManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TestContainerManager.class);

    private final List<GenericContainer<?>> containers = new ArrayList<>();
    private final Map<String, GenericContainer<?>> namedContainers = new HashMap<>();
    private final Network network;
    private boolean started;

    public TestContainerManager() {
        this(Network.newNetwork());
    }

    public TestContainerManager(Network network) {
        this.network = network;
    }

    public TestContainerManager withContainer(GenericContainer<?> container) {
        return withContainer(null, container);
    }

    public TestContainerManager withContainer(String name, GenericContainer<?> container) {
        if (started) {
            throw new IllegalStateException("Cannot add containers after starting the manager");
        }

        container.withNetwork(network);
        containers.add(container);

        if (name != null && !name.isEmpty()) {
            container.withNetworkAliases(name);
            namedContainers.put(name, container);
        }

        return this;
    }

    public void start() {
        if (started) {
            return;
        }

        log.info("Starting test containers...");
        for (GenericContainer<?> container : containers) {
            container.start();
            log.info("Started container: {}", container.getDockerImageName());
        }
        started = true;
    }

    @Override
    public void close() {
        log.info("Stopping test containers...");
        for (GenericContainer<?> container : containers) {
            try {
                container.stop();
                log.info("Stopped container: {}", container.getDockerImageName());
            } catch (Exception e) {
                log.error("Error stopping container: {}", container.getDockerImageName(), e);
            }
        }
        containers.clear();
        namedContainers.clear();
        started = false;
    }

    @SuppressWarnings("unchecked")
    public <T extends GenericContainer<T>> T getContainer(String name) {
        if (!namedContainers.containsKey(name)) {
            throw new IllegalArgumentException("No container with name: " + name);
        }
        return (T) namedContainers.get(name);
    }

    public Network getNetwork() {
        return network;
    }

    public static GenericContainer<?> createContainerWithLogWait(String image, String logMessage) {
        return new GenericContainer<>(image)
                .waitingFor(Wait.forLogMessage(logMessage, 1));
    }

    public static String executeCommand(GenericContainer<?> container, String... command) throws Exception {
        Container.ExecResult result = container.execInContainer(command);
        if (result.getExitCode() != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", command)
                    + "\nExit code: " + result.getExitCode()
                    + "\nStderr: " + result.getStderr());
        }
        return result.getStdout();
    }
}
