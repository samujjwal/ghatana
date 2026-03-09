package com.ghatana.core.activej.launcher;

import io.activej.http.HttpServer;
import io.activej.inject.Injector;
import io.activej.inject.module.Module;
import io.activej.inject.module.ModuleBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * Unified application launcher template for standardized service bootstrapping across Ghatana.
 *
 * <p><b>Purpose</b><br>
 * Provides a consistent, opinionated pattern for all Ghatana microservices to follow during startup.
 * Extends {@link ServiceLauncher} to offer a higher-level abstraction specifically for applications
 * (services with HTTP endpoints, business logic, persistence). Reduces boilerplate by centralizing
 * common concerns: module setup, HTTP server lifecycle, logging, and version management.
 *
 * <p><b>Architecture Role</b><br>
 * Part of `core/activej-runtime` application bootstrap layer. Sits between raw ServiceLauncher
 * (low-level DI/lifecycle) and actual service implementations (business logic). Provides a template
 * that all product services follow, ensuring consistency and reducing copy-paste:
 * - Used by all microservices in `products/*`, `multi-agent-system/*`, `yappc/*`
 * - Enforces "service name" + "service version" pattern for observability
 * - Guides services to use core abstractions (metrics, logging, tracing, HTTP)
 * - Enables platform-level service discovery and health checks
 *
 * <p><b>Unified Pattern</b><br>
 * Every UnifiedApplicationLauncher-based service follows this template:
 * <ol>
 *   <li>Extend class, implement 3 abstract methods (service name, setup bindings, create HTTP server)</li>
 *   <li>DI module automatically created with service bindings</li>
 *   <li>Lifecycle hooks called at startup/shutdown</li>
 *   <li>HTTP server (if provided) integrated into service lifecycle</li>
 *   <li>Version accessible for metrics and logs</li>
 * </ol>
 *
 * <p><b>Key Differences from ServiceLauncher</b><br>
 * <table>
 *   <tr><th>Aspect</th><th>ServiceLauncher</th><th>UnifiedApplicationLauncher</th></tr>
 *   <tr><td>Level</td><td>Low-level primitives</td><td>High-level application template</td></tr>
 *   <tr><td>Module Creation</td><td>Manual (implement createModule())</td><td>Automatic (uses setupService())</td></tr>
 *   <tr><td>HTTP Server</td><td>Service defines via DI</td><td>Service provides via createHttpServer()</td></tr>
 *   <tr><td>Service Name</td><td>Not required</td><td>Required (abstract method)</td></tr>
 *   <tr><td>Hooks</td><td>onServiceStarted/Stopping</td><td>onApplicationStarted/Stopping</td></tr>
 *   <tr><td>Version Management</td><td>Not provided</td><td>Built-in getServiceVersion()</td></tr>
 *   <tr><td>Use Case</td><td>Frameworks, low-level services</td><td>Business microservices</td></tr>
 * </table>
 *
 * <p><b>Usage Example</b><br>
 * <pre>{@code
 * // Complete minimal service using UnifiedApplicationLauncher
 * public class OrderServiceApplication extends UnifiedApplicationLauncher {
 *
 *     &#64;Override
 *     protected String getServiceName() {
 *         return "order-service";  // Used in logs, metrics
 *     }
 *
 *     &#64;Override
 *     protected String getServiceVersion() {
 *         return "1.2.3";  // From version catalog ideally
 *     }
 *
 *     &#64;Override
 *     protected void setupService(ModuleBuilder builder) {
 *         // Bind repositories
 *         builder.bind(OrderRepository.class)
 *             .to(PostgresOrderRepository.class);
 *
 *         // Bind services
 *         builder.bind(OrderService.class)
 *             .to(OrderServiceImpl.class);
 *
 *         // Bind infrastructure
 *         builder.bind(MetricsCollector.class)
 *             .to(() -> MetricsCollectorFactory.create(registry));
 *     }
 *
 *     &#64;Override
 *     protected HttpServer createHttpServer(Injector injector) {
 *         OrderService service = injector.getInstance(OrderService.class);
 *
 *         // Use core/http-server abstraction
 *         return HttpServerBuilder.create()
 *             .addRoute(POST, "/orders", ctx ->
 *                 service.createOrder(ctx.getBody())
 *                     .map(order -> ResponseBuilder.ok().json(order))
 *             )
 *             .addRoute(GET, "/orders/:id", ctx ->
 *                 service.getOrder(UUID.fromString(ctx.getPath("id")))
 *                     .map(order -> ResponseBuilder.ok().json(order))
 *             )
 *             .port(8080)
 *             .build();
 *     }
 *
 *     &#64;Override
 *     protected void onApplicationStarted() {
 *         log.info("Order Service ready at http://localhost:8080");
 *         // Publish readiness event
 *         getInstance(EventBus.class)
 *             .publish(new ServiceReadyEvent("order-service", getServiceVersion()));
 *     }
 *
 *     public static void main(String[] args) {
 *         new OrderServiceApplication().launch(args);
 *     }
 * }
 *
 * // Run it
 * $ java OrderServiceApplication
 * [INFO] Starting service launcher: OrderServiceApplication
 * [INFO] Service order-service initialized
 * [INFO] Order Service ready at http://localhost:8080
 * [INFO] Service launcher running (press Ctrl+C to stop)
 * }</pre>
 *
 * <p><b>Extension Points (Abstract Methods - REQUIRED)</b><br>
 * <ul>
 *   <li><b>{@link #getServiceName()}:</b> Return unique service identifier (used in logs, metrics, discovery)</li>
 *   <li><b>{@link #setupService(ModuleBuilder)}:</b> Add all service-specific DI bindings to builder</li>
 *   <li><b>{@link #createHttpServer(Injector)}:</b> Create and configure HTTP server (or return null)</li>
 * </ul>
 *
 * <p><b>Extension Points (Optional Hooks)</b><br>
 * <ul>
 *   <li><b>{@link #getServiceVersion()}:</b> Override to provide semantic version (default: "1.0.0")</li>
 *   <li><b>{@link #onApplicationStarted()}:</b> Called after successful startup (log readiness, publish events)</li>
 *   <li><b>{@link #onApplicationStopping()}:</b> Called before shutdown (cleanup, publish stopping event)</li>
 * </ul>
 *
 * <p><b>Best Practices</b><br>
 * <ul>
 *   <li><b>Keep Bindings Simple:</b> {@link #setupService(ModuleBuilder)} should only add bindings, not instantiate services.</li>
 *   <li><b>Use Service Names Consistently:</b> Match getServiceName() to Kubernetes deployment name, configuration keys, and observability labels.</li>
 *   <li><b>Version from Catalog:</b> Inject version from Gradle version catalog (build system should pass it in), not hardcoded.</li>
 *   <li><b>HTTP Server from createHttpServer():</b> Don't expose HTTP server in DI - use {@link #createHttpServer(Injector)}} instead for clean separation.</li>
 *   <li><b>Override Hooks Sparingly:</b> Most services don't need custom startup/stopping logic - only if doing health checks or event publishing.</li>
 *   <li><b>Use Core Abstractions:</b> In setupService(), always use `core/*` modules for HTTP, metrics, logging, cache, database.</li>
 *   <li><b>Log Service Details in onApplicationStarted():</b> Log listening port, database connection, any important config for operational visibility.</li>
 * </ul>
 *
 * <p><b>Anti-Patterns</b><br>
 * <ul>
 *   <li>❌ <b>Complex setupService():</b> Don't do factory method chains or conditional bindings - use factory beans instead.</li>
 *   <li>❌ <b>No Service Name:</b> Always implement getServiceName() - it's used in logs and metrics for identification.</li>
 *   <li>❌ <b>Multiple HTTP Servers:</b> createHttpServer() should return at most one server. If you need multiple listening ports, use routing.</li>
 *   <li>❌ <b>Synchronous Work in Hooks:</b> Don't do blocking I/O in onApplicationStarted() or onApplicationStopping() - they run on startup thread.</li>
 *   <li>❌ <b>Ignoring getServiceVersion():</b> Don't hardcode version in logs - override getServiceVersion() so metrics and logs stay in sync.</li>
 *   <li>❌ <b>Mixing ServiceLauncher and UnifiedApplicationLauncher:</b> Pick one pattern and stick with it (use Unified for services, ServiceLauncher for frameworks).</li>
 * </ul>
 *
 * <p><b>Lifecycle Sequence</b><br>
 * <pre>
 * 1. new UnifiedApplicationLauncher() → instance created
 * 2. launch(args) → calls parent ServiceLauncher.launch()
 * 3. onStart() → calls createModule() → calls setupService() → builds Module
 * 4. Injector created, ServiceGraph created, services started
 * 5. createHttpServer(injector) called, server starts
 * 6. onServiceStarted() → calls onApplicationStarted() → service publishes readiness
 * 7. run() → service runs until shutdown signal
 * 8. Shutdown signal → onServiceStopping() → calls onApplicationStopping()
 * 9. ServiceGraph stopped → HTTP server stopped → all resources closed
 * 10. Application terminates
 * </pre>
 *
 * <p><b>Version Management Integration</b><br>
 * Recommended approach for version:
 * <pre>{@code
 * // In build.gradle.kts (product service):
 * version = "1.2.3"  // Sourced from version catalog
 *
 * // In application class:
 * &#64;Override
 * protected String getServiceVersion() {
 *     // Can be injected from build config
 *     return "1.2.3";
 *     // Or via environment: System.getenv("SERVICE_VERSION")
 *     // Or via properties: System.getProperty("version")
 * }
 * }</pre>
 *
 * <p><b>Related Components</b><br>
 * @see ServiceLauncher Base class with low-level lifecycle
 * @see EventloopManager Manages eventloop across threads
 * @see io.activej.http.HttpServer HTTP server implementation
 * @see io.activej.inject.Injector Dependency injection container
 * @see ModuleBuilder DI module builder
 *
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Template pattern for standardized microservice bootstrap with DI and HTTP integration
 * @doc.layer core
 * @doc.pattern Template Method + Strategy
 */
@Slf4j
public abstract class UnifiedApplicationLauncher extends ServiceLauncher {

    /**
     * Get the service name for identification and logging.
     *
     * @return service name (e.g., "ingestion-gateway", "tracing-service")
     */
    protected abstract String getServiceName();

    /**
     * Setup service-specific bindings in the module.
     *
     * <p>This is where you bind your service dependencies. The ModuleBuilder is provided
     * and ready for binding. Add your service-specific bindings here.
     *
     * <p>Example:
     * <pre>
     * builder.bind(MyRepository.class).to(PostgresMyRepository.class);
     * builder.bind(MyService.class).to(MyServiceImpl.class);
     * </pre>
     *
     * @param builder the ModuleBuilder for adding service bindings
     */
    protected abstract void setupService(ModuleBuilder builder);

    /**
     * Create the HTTP server for this service.
     *
     * <p>This is where you define your HTTP routes and endpoints.
     * Can return null if service manages its own HTTP servers externally.
     *
     * @param injector the service injector for retrieving dependencies
     * @return configured HTTP server ready to serve requests, or null if not used
     */
    protected abstract HttpServer createHttpServer(Injector injector);

    /**
     * Hook called when the application has started successfully.
     *
     * <p>Override this to perform any post-startup initialization. Default implementation logs service startup.
     */
    protected void onApplicationStarted() {
        log.info("{} started successfully", getServiceName());
    }

    /**
     * Hook called when the application is stopping.
     *
     * <p>Override this to perform any pre-shutdown cleanup. Default implementation logs service shutdown.
     */
    protected void onApplicationStopping() {
        log.info("Stopping service: {}", getServiceName());
    }

    @Override
    protected Module createModule() {
        ModuleBuilder builder = ModuleBuilder.create();
        
        // Setup service-specific bindings
        setupService(builder);
        
        return builder.build();
    }

    @Override
    protected void onServiceStarted() {
        log.info("Service {} initialized", getServiceName());
        onApplicationStarted();
    }

    @Override
    protected void onServiceStopping() {
        onApplicationStopping();
        log.info("Service {} stopped", getServiceName());
    }

    /**
     * Get the service version for logging and metrics.
     * Default returns "1.0.0", can be overridden.
     *
     * @return service version
     */
    protected String getServiceVersion() {
        return "1.0.0";
    }
}
