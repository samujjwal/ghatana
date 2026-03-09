package com.ghatana.platform.testing.containers;

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
import java.util.function.Consumer;

/**
 * Manages the lifecycle of test containers.
 
 *
 * @doc.type class
 * @doc.purpose Test container manager
 * @doc.layer core
 * @doc.pattern Manager
*/
public class TestContainerManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TestContainerManager.class);
    
    private final List<GenericContainer<?>> containers = new ArrayList<>();
    private final Map<String, GenericContainer<?>> namedContainers = new HashMap<>();
    private Network network;
    private boolean started = false;
    
    /**
     * Creates a new TestContainerManager with a default network.
     */
    public TestContainerManager() {
        this.network = Network.newNetwork();
    }
    
    /**
     * Creates a new TestContainerManager with a custom network.
     *
     * @param network the network to use for the containers
     */
    public TestContainerManager(Network network) {
        this.network = network;
    }
    
    /**
     * Adds a container to be managed.
     *
     * @param container the container to add
     * @return this TestContainerManager for method chaining
     */
    public TestContainerManager withContainer(GenericContainer<?> container) {
        return withContainer(null, container);
    }
    
    /**
     * Adds a named container to be managed.
     *
     * @param name the name of the container
     * @param container the container to add
     * @return this TestContainerManager for method chaining
     */
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
    
    /**
     * Starts all managed containers.
     */
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
    
    /**
     * Stops all managed containers.
     */
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
    
    /**
     * Gets a container by its name.
     *
     * @param name the name of the container
     * @return the container
     * @throws IllegalArgumentException if no container with the given name exists
     */
    @SuppressWarnings("unchecked")
    public <T extends GenericContainer<T>> T getContainer(String name) {
        if (!namedContainers.containsKey(name)) {
            throw new IllegalArgumentException("No container with name: " + name);
        }
        return (T) namedContainers.get(name);
    }
    
    /**
     * Gets the network used by the containers.
     *
     * @return the network
     */
    public Network getNetwork() {
        return network;
    }
    
    /**
     * Creates a new container that waits for a specific log message.
     *
     * @param image the Docker image
     * @param logMessage the log message to wait for
     * @return a new GenericContainer
     */
    public static GenericContainer<?> createContainerWithLogWait(String image, String logMessage) {
        return new GenericContainer<>(image)
                .waitingFor(Wait.forLogMessage(logMessage, 1));
    }
    
    /**
     * Executes a command in a container and returns the output.
     *
     * @param container the container
     * @param command the command to execute
     * @return the command output
     * @throws Exception if the command fails
     */
    public static String executeCommand(GenericContainer<?> container, String... command) throws Exception {
        Container.ExecResult result = container.execInContainer(command);
        if (result.getExitCode() != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", command) + 
                                     "\nExit code: " + result.getExitCode() + 
                                     "\nStderr: " + result.getStderr());
        }
        return result.getStdout();
    }
}
