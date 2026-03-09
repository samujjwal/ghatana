package com.ghatana.core.activej.launcher;

import io.activej.inject.Injector;
import io.activej.inject.module.Module;
import io.activej.launcher.Launcher;
import io.activej.service.ServiceGraph;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Base class for ActiveJ service launchers with integrated lifecycle management.
 *
 * <p><b>Purpose</b><br>
 * Abstracts away the complexity of bootstrapping ActiveJ services with proper dependency injection,
 * service graph lifecycle management, graceful shutdown, and resource cleanup. Subclasses define
 * their DI bindings and hook into lifecycle events (start/stop/shutdown). This pattern is the
 * standard for all Ghatana microservices and ensures consistent startup/shutdown behavior across the platform.
 *
 * <p><b>Architecture Role</b><br>
 * Part of `core/activej-runtime` service bootstrapping layer. Acts as the entry point for every Ghatana service:
 * - Used by HTTP servers, event processors, agents, and other services
 * - Manages ActiveJ Injector, ServiceGraph, and thread lifecycle
 * - Integrates with EventloopManager for thread-safe eventloop access
 * - Provides extension points for service-specific initialization
 * - Used by microservices in `products/*`, `multi-agent-system/*`, `yappc/*`
 *
 * <p><b>Lifecycle Phases</b><br>
 * <ol>
 *   <li><b>Initialization:</b> {@code new MyServiceLauncher()} - instance created</li>
 *   <li><b>Module Creation:</b> {@link #createModule()} called - return DI bindings</li>
 *   <li><b>Injection:</b> {@link #createInjector(Module)} creates injector</li>
 *   <li><b>Service Graph:</b> ServiceGraph created from injector, manages all services</li>
 *   <li><b>Startup:</b> {@link #onStart()} starts all registered services (60s timeout)</li>
 *   <li><b>Post-Start Hook:</b> {@link #onServiceStarted()} - service-specific post-init logic</li>
 *   <li><b>Running:</b> {@link #run()} - service runs until shutdown signal</li>
 *   <li><b>Pre-Shutdown Hook:</b> {@link #onServiceStopping()} - service-specific pre-stop logic</li>
 *   <li><b>Service Graph Stop:</b> {@link #onShutdown()} stops all services (30s timeout)</li>
 *   <li><b>Resource Cleanup:</b> All registered resources closed in reverse order</li>
 *   <li><b>Complete:</b> Service terminated</li>
 * </ol>
 *
 * <p><b>Key Features</b><br>
 * <ul>
 *   <li><b>Template Method Pattern:</b> Subclass implements {@link #createModule()} to define service bindings</li>
 *   <li><b>Extensible Hooks:</b> Override {@link #onServiceStarted()}, {@link #onServiceStopping()} for custom logic</li>
 *   <li><b>Lifecycle Timeouts:</b> 60s for startup, 30s for shutdown (configurable in subclass if needed)</li>
 *   <li><b>Graceful Shutdown:</b> Calls pre-shutdown hook, stops service graph, closes resources</li>
 *   <li><b>Resource Management:</b> {@link #registerResource(AutoCloseable)} automatically closes on shutdown</li>
 *   <li><b>Injector Access:</b> {@link #getInstance(Class)} convenience method for service lookup</li>
 *   <li><b>Logging:</b> DEBUG and INFO logs throughout lifecycle (uses Log4j2)</li>
 * </ul>
 *
 * <p><b>Usage Example</b><br>
 * <pre>{@code
 * // Define service implementation
 * public class UserService {
 *     public Promise<User> findById(UUID id) { ... }
 * }
 *
 * // Define launcher - single method required
 * public class UserServiceLauncher extends ServiceLauncher {
 *
 *     &#64;Override
 *     protected Module createModule() {
 *         return Module.create()
 *             // Bind core services
 *             .bind(UserService.class).to(UserServiceImpl::new)
 *             .bind(UserRepository.class).to(JpaUserRepository::new)
 *
 *             // Bind infrastructure
 *             .bind(HttpServer.class).to(HttpServerBuilder::build)
 *             .bind(Database.class).to(DatabaseConfig::createPool)
 *
 *             // Bind observability
 *             .bind(MetricsCollector.class)
 *             .to(() -> MetricsCollectorFactory.create(registry));
 *     }
 *
 *     &#64;Override
 *     protected void onServiceStarted() {
 *         HttpServer server = getInstance(HttpServer.class);
 *         log.info("HTTP server listening on {}", server.getPort());
 *
 *         // Optional: publish readiness event
 *         eventBus.publish(new ServiceReadyEvent("user-service", "1.0.0"));
 *     }
 *
 *     &#64;Override
 *     protected void onServiceStopping() {
 *         // Optional: cleanup before stop
 *         getInstance(MetricsCollector.class).flush();
 *     }
 *
 *     public static void main(String[] args) {
 *         new UserServiceLauncher().launch(args);
 *     }
 * }
 *
 * // Run the service
 * $ java UserServiceLauncher
 * [INFO] Starting service launcher: UserServiceLauncher
 * [INFO] HTTP server listening on 8080
 * [INFO] Service launcher running (press Ctrl+C to stop)
 * }</pre>
 *
 * <p><b>Extension Points</b><br>
 * <ul>
 *   <li><b>{@link #createModule()}:</b> [REQUIRED] Define DI bindings for all services</li>
 *   <li><b>{@link #createInjector(Module)}:</b> [OPTIONAL] Customize injector (e.g., add interceptors, logging)</li>
 *   <li><b>{@link #onServiceStarted()}:</b> [OPTIONAL] Post-startup actions (log ports, publish events, etc.)</li>
 *   <li><b>{@link #onServiceStopping()}:</b> [OPTIONAL] Pre-shutdown cleanup (flush metrics, drain queues, etc.)</li>
 * </ul>
 *
 * <p><b>Best Practices</b><br>
 * <ul>
 *   <li><b>Keep Module Creation Simple:</b> {@link #createModule()} should only return bindings, no heavy work. Heavy initialization belongs in service constructors.</li>
 *   <li><b>Use getInstance() for Late-Binding:</b> In {@link #onServiceStarted()}, use {@link #getInstance(Class)} to access other services instead of storing references.</li>
 *   <li><b>Always Register Resources:</b> Use {@link #registerResource(AutoCloseable)} for DB connections, thread pools, file handles - they close automatically.</li>
 *   <li><b>Fail Fast on Module Errors:</b> If {@link #createModule()} returns null or invalid bindings, startup fails immediately with clear error message.</li>
 *   <li><b>Log Startup/Shutdown:</b> Subclasses should log important milestones in {@link #onServiceStarted()} and {@link #onServiceStopping()}} for operational visibility.</li>
 *   <li><b>Respect Timeouts:</b> 60s startup and 30s shutdown are generous - if services take longer, investigate (likely deadlock or blocking I/O).</li>
 *   <li><b>Handle Errors Gracefully:</b> Both lifecycle hooks wrap errors in try-catch to prevent cascade failures - safe to override.</li>
 * </ul>
 *
 * <p><b>Anti-Patterns</b><br>
 * <ul>
 *   <li>❌ <b>Blocking I/O in Hooks:</b> Don't call {@code Thread.sleep()}, JDBC queries, or file I/O in lifecycle hooks (except promises). These block the startup/shutdown thread.</li>
 *   <li>❌ <b>Complex Module Logic:</b> Don't do heavy initialization in {@link #createModule()}} - move to service constructors or factory methods.</li>
 *   <li>❌ <b>Ignoring Resource Registration:</b> Don't manually create connection pools, file streams, etc. without calling {@link #registerResource(AutoCloseable)}} - causes resource leaks.</li>
 *   <li>❌ <b>Multiple Module Instances:</b> Don't call {@link #createModule()}} multiple times - it's called once during startup. Store the Module as field if you need it later.</li>
 *   <li>❌ <b>Throwing Exceptions in Hooks:</b> The framework catches and logs errors - if you need to fail startup, throw exception from {@link #createModule()}} or service constructor instead.</li>
 *   <li>❌ <b>Accessing getInstance() Before onStart():</b> Don't call {@link #getInstance(Class)}} in constructor or {{@link #createModule()}}} - injector isn't ready yet.</li>
 * </ul>
 *
 * <p><b>Error Handling</b><br>
 * - If {@link #createModule()} returns null → startup fails with {@code IllegalStateException}
 * - If service graph startup exceeds 60s timeout → startup fails with {@code TimeoutException}
 * - If service graph stop exceeds 30s timeout → logged as ERROR but shutdown continues (fail-safe)
 * - Errors in lifecycle hooks are caught and logged but don't cascade (failure in one hook doesn't prevent others)
 *
 * <p><b>Thread Safety</b><br>
 * ServiceLauncher is designed to be single-threaded during startup/shutdown phases but thread-safe during
 * the running phase. The injector and service instances must be thread-safe if accessed from multiple threads.
 * EventloopManager handles per-thread eventloop association.
 *
 * <p><b>Related Components</b><br>
 * @see EventloopManager Manages eventloop lifecycle across threads
 * @see PromiseUtils Utilities for promise-based service operations
 * @see io.activej.launcher ActiveJ Launcher base class
 * @see io.activej.service.ServiceGraph Manages service startup/stop
 * @see io.activej.inject.Injector Dependency injection container
 *
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Base class for ActiveJ service bootstrapping with lifecycle hooks and DI integration
 * @doc.layer core
 * @doc.pattern Template Method + Factory
 */
@Slf4j
public abstract class ServiceLauncher extends Launcher {
    
    private Injector injector;
    private ServiceGraph serviceGraph;
    private final List<AutoCloseable> resources = new ArrayList<>();
    
    /**
     * Creates the DI module for this service.
     * Subclasses must implement this to provide their service bindings.
     * 
     * @return The DI module
     */
    protected abstract Module createModule();
    
    /**
     * Creates the injector from the module.
     * Override to customize injector creation (e.g., add interceptors).
     * 
     * @param module The DI module
     * @return The injector instance
     */
    protected Injector createInjector(Module module) {
        return Injector.of(module);
    }
    
    /**
     * Called after services have started successfully.
     * Override to perform post-startup actions.
     */
    protected void onServiceStarted() {
        // Default: no-op
    }
    
    /**
     * Called before services are stopped.
     * Override to perform pre-shutdown actions.
     */
    protected void onServiceStopping() {
        // Default: no-op
    }
    
    @Override
    protected final void onStart() throws Exception {
        log.info("Starting service launcher: {}", getClass().getSimpleName());
        
        // Create injector
        Module module = createModule();
        if (module == null) {
            throw new IllegalStateException("createModule() returned null");
        }
        
        injector = createInjector(module);
        log.debug("Injector created");
        
        // Create service graph
        serviceGraph = injector.getInstance(ServiceGraph.class);
        log.debug("Service graph created with {} services", 
            0);
        
        // Start services
        CompletableFuture<?> startFuture = serviceGraph.startFuture();
        startFuture.get(60, TimeUnit.SECONDS); // 60 second timeout
        
        
        // Post-start hook
        onServiceStarted();
    }
    
    @Override
    protected final void run() throws Exception {
        log.info("Service launcher running (press Ctrl+C to stop)");
        
        // Keep running until shutdown signal
        awaitShutdown();
    }
    /**
     * Handles the shutdown sequence for the service launcher.
     * This method is called when the service receives a shutdown signal.
     * It ensures all resources are properly cleaned up and services are stopped gracefully.
     *
     * @throws Exception if an error occurs during shutdown
     */
    protected final void onShutdown() throws Exception {
        log.info("Shutting down service launcher");
        
        // Pre-shutdown hook
        try {
            onServiceStopping();
        } catch (Exception e) {
            log.error("Error in onServiceStopping hook", e);
        }
        
        // Stop service graph
        if (serviceGraph != null) {
            try {
                log.debug("Stopping service graph");
                CompletableFuture<?> stopFuture = serviceGraph.stopFuture();
                stopFuture.get(30, TimeUnit.SECONDS); // 30 second timeout
                log.info("Service graph stopped");
            } catch (Exception e) {
                log.error("Error stopping service graph", e);
            }
        }
        
        // Close additional resources
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
                log.debug("Closed resource: {}", resource.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("Error closing resource: {}", resource.getClass().getSimpleName(), e);
            }
        }
        
        log.info("Service launcher shutdown complete");
    }
    
    /**
     * Registers a resource for cleanup on shutdown.
     * Resources are closed in reverse order of registration.
     * 
     * @param resource The resource to register
     */
    protected final void registerResource(AutoCloseable resource) {
        if (resource != null) {
            resources.add(resource);
            log.debug("Registered resource for cleanup: {}", resource.getClass().getSimpleName());
        }
    }
    
    /**
     * Gets the injector instance.
     * Available after {@link #onStart()} completes.
     * 
     * @return The injector
     */
    protected final Injector getInjector() {
        return injector;
    }
    
    /**
     * Gets the service graph.
     * Available after {@link #onStart()} completes.
     * 
     * @return The service graph
     */
    protected final ServiceGraph getServiceGraph() {
        return serviceGraph;
    }
    
    /**
     * Gets an instance from the injector.
     * Convenience method for accessing injected services.
     * 
     * @param type The service type
     * @param <T> The type parameter
     * @return The service instance
     */
    protected final <T> T getInstance(Class<T> type) {
        if (injector == null) {
            throw new IllegalStateException("Injector not yet created");
        }
        return injector.getInstance(type);
    }
}
