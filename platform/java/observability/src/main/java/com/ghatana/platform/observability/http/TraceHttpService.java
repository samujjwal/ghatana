package com.ghatana.platform.observability.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.core.activej.launcher.UnifiedApplicationLauncher;
import com.ghatana.platform.observability.clickhouse.ClickHouseTraceStorage;
import com.ghatana.platform.observability.http.handlers.*;
import com.ghatana.platform.observability.trace.TraceStorage;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpResponse;
import io.activej.http.HttpServer;
import io.activej.http.RoutingServlet;
import io.activej.inject.Injector;
import io.activej.inject.module.ModuleBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main HTTP service for trace observability REST API.
 * <p>
 * Provides a REST API for:
 * <ul>
 *   <li>Span ingestion (single + batch)</li>
 *   <li>Trace querying (by ID + search)</li>
 * @doc.type class
 * @doc.purpose REST API service for trace ingestion and querying
 * @doc.layer core
 * @doc.pattern HTTP Service, API Gateway
 *   <li>Trace statistics (aggregated metrics)</li>
 *   <li>Health checks (liveness + readiness)</li>
 * </ul>
 * </p>
 * <p>
 * This service uses ActiveJ HTTP server and integrates with Phase 1 core abstractions
 * ({@link TraceStorage}, {@link com.ghatana.platform.observability.trace.SpanData}, etc.).
 * </p>
 * <p>
 * Architecture:
 * <ul>
 *   <li>Extends {@link UnifiedApplicationLauncher} for consistent service bootstrapping</li>
 *   <li>Uses ActiveJ HTTP types (HttpRequest, HttpResponse) - ALLOWED</li>
 *   <li>TODO: Refactor to use {@code core:http-server} ResponseBuilder</li>
 *   <li>All handlers are stateless and thread-safe</li>
 *   <li>JSON serialization via Jackson</li>
 * </ul>
 * </p>
 * <p>
 * API Endpoints:
 * <pre>
 * POST   /api/v1/traces/spans        - Ingest single span
 * POST   /api/v1/traces/spans/batch  - Ingest multiple spans
 * GET    /api/v1/traces/{traceId}    - Get trace by ID
 * GET    /api/v1/traces               - Search traces
 * GET    /api/v1/traces/stats         - Get statistics
 * GET    /health                      - Liveness check
 * GET    /health/ready                - Readiness check
 * </pre>
 * </p>
 * <p>
 * Usage:
 * <pre>{@code
 * // Create with custom TraceStorage
 * TraceStorage storage = ...;
 * TraceHttpService service = new TraceHttpService(storage);
 * service.launch(new String[]{"--port=8080"});
 * }</pre>
 * </p>
 *
 * @doc.author Ghatana Platform Team
 * @doc.created 2025-01-10
 * @doc.updated 2025-01-10
 * @doc.version 1.0.0
 * @doc.purpose Main HTTP service providing REST API for distributed tracing (span ingestion, query, statistics)
 * @doc.responsibility Bootstraps ActiveJ HTTP server, wires trace storage, configures routing, manages service lifecycle
 * @doc.dependencies {@link TraceStorage}, {@link UnifiedApplicationLauncher}, ActiveJ HTTP, Jackson JSON
 * @doc.usage Extend for custom storage backends or run standalone with ClickHouse backend via main() method
 * @doc.examples See main() method for ClickHouse-backed service; constructor for custom storage integration
 * @doc.testing Test with TraceHttpServiceTest (integration tests with TestContainers for ClickHouse)
 * @doc.notes Follows EventCloud Architecture v4 ingress patterns; handlers delegate to TraceStorage abstraction
 * 
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public class TraceHttpService extends UnifiedApplicationLauncher {

    /**
     * Logger for TraceHttpService operations and lifecycle events.
     */
    private static final Logger logger = LoggerFactory.getLogger(TraceHttpService.class);

    /**
     * Trace storage backend for persisting and querying span/trace data.
     * Abstracts ClickHouse, PostgreSQL, or other implementations.
     */
    private final TraceStorage storage;
    
    /**
     * Jackson ObjectMapper for JSON serialization/deserialization.
     * Configured with JavaTimeModule and Jdk8Module for Java 8+ type support.
     */
    private final ObjectMapper objectMapper;

    /**
     * Constructs a TraceHttpService with custom storage backend.
     * <p>
     * Validates storage reference and initializes Jackson ObjectMapper with
     * Java 8+ type support (JavaTimeModule, Jdk8Module).
     * </p>
     *
     * @param storage  TraceStorage backend for span/trace persistence (must not be null)
     * @throws IllegalArgumentException if storage is null
     * @doc.example
     * <pre>{@code
     * TraceStorage clickhouse = ClickHouseTraceStorage.builder()
     *     .withHost("localhost")
     *     .withPort(8123)
     *     .build();
     * TraceHttpService service = new TraceHttpService(clickhouse);
     * service.launch(new String[]{"--port=8080"});
     * }</pre>
     */
    public TraceHttpService(TraceStorage storage) {
        if (storage == null) {
            throw new IllegalArgumentException("TraceStorage cannot be null");
        }

        this.storage = storage;
        this.objectMapper = createObjectMapper();

        logger.info("TraceHttpService initialized");
    }

    /**
     * Creates and configures Jackson ObjectMapper for JSON serialization.
     * <p>
     * Registers modules:
     * <ul>
     *   <li>{@link JavaTimeModule} - Handles Java 8 date/time types (Instant, Duration, etc.)</li>
     *   <li>{@link Jdk8Module} - Handles Optional, Stream, etc.</li>
     * </ul>
     * Calls {@code findAndRegisterModules()} to auto-discover additional modules on classpath.
     * </p>
     *
     * @return Configured ObjectMapper instance with Java 8+ support
     * @doc.thread-safety Safe - creates new instance, no shared state
     */
    private static ObjectMapper createObjectMapper() {
        return JsonUtils.getDefaultMapper();
    }

    /**
     * Returns the service name for logging and monitoring.
     * <p>
     * Used by {@link UnifiedApplicationLauncher} for:
     * <ul>
     *   <li>Log context identification</li>
     *   <li>Metrics tagging (service.name label)</li>
     *   <li>Distributed tracing service attribution</li>
     * </ul>
     * </p>
     *
     * @return Service name identifier: "trace-http-service"
     */
    @Override
    protected String getServiceName() {
        return "trace-http-service";
    }

    /**
     * Configures ActiveJ dependency injection bindings.
     * <p>
     * Binds service dependencies for injection into handlers:
     * <ul>
     *   <li>{@link TraceStorage} - Storage backend instance</li>
     *   <li>{@link ObjectMapper} - JSON serialization instance</li>
     * </ul>
     * </p>
     * <p>
     * Called by {@link UnifiedApplicationLauncher} during service initialization.
     * </p>
     *
     * @param builder ActiveJ ModuleBuilder for DI configuration
     * @doc.lifecycle Called once during service startup before HTTP server creation
     */
    @Override
    protected void setupService(ModuleBuilder builder) {
        // Bind TraceStorage instance
        builder.bind(TraceStorage.class).toInstance(storage);
        
        // Bind ObjectMapper instance
        builder.bind(ObjectMapper.class).toInstance(objectMapper);
        
        logger.info("Service bindings configured");
    }

    /**
     * Creates and configures the ActiveJ HTTP server with routing.
     * <p>
     * Wiring:
     * <ul>
     *   <li>Injects {@link TraceStorage}, {@link ObjectMapper}, Eventloop from DI container</li>
     *   <li>Instantiates handlers: {@link IngestHandler}, {@link QueryHandler}, {@link StatisticsHandler}, {@link HealthHandler}</li>
     *   <li>Configures routing via {@link RoutingServlet} with path-based dispatch</li>
     *   <li>Registers 404 fallback handler for unknown routes</li>
     * </ul>
     * </p>
     * <p>
     * Routing Table:
     * <pre>
     * POST   /api/v1/traces/spans         → IngestHandler.handleSingleSpan
     * POST   /api/v1/traces/spans/batch   → IngestHandler.handleBatchSpans
     * GET    /api/v1/traces/:traceId      → QueryHandler.handleGetTraceById
     * GET    /api/v1/traces                → QueryHandler.handleSearchTraces
     * GET    /api/v1/traces/stats          → StatisticsHandler.handleGetStatistics
     * GET    /health                       → HealthHandler.handleLiveness
     * GET    /health/ready                 → HealthHandler.handleReadiness
     * *      /*                            → 404 Not Found handler
     * </pre>
     * </p>
     * <p>
     * Architecture Notes:
     * <ul>
     *   <li>Uses ActiveJ HTTP types (HttpRequest, HttpResponse) - TYPE REFERENCES ALLOWED</li>
     *   <li>TODO: Migrate to {@code core:http-server} ResponseBuilder for OPERATIONS</li>
     *   <li>All handlers are stateless and thread-safe (safe for concurrent requests)</li>
     *   <li>Eventloop affinity: Single eventloop per server instance (ActiveJ concurrency model)</li>
     * </ul>
     * </p>
     *
     * @param injector ActiveJ DI injector for dependency resolution
     * @return Configured {@link HttpServer} instance ready for binding/listening
     * @doc.lifecycle Called once after setupService(), before server starts listening
     * @doc.concurrency All handlers execute on single eventloop thread (no shared mutable state required)
     */
    @Override
    protected HttpServer createHttpServer(Injector injector) {
        // Get dependencies from injector
        TraceStorage traceStorage = injector.getInstance(TraceStorage.class);
        ObjectMapper mapper = injector.getInstance(ObjectMapper.class);
        io.activej.eventloop.Eventloop eventloop = injector.getInstance(io.activej.eventloop.Eventloop.class);

        // Create handlers
        IngestHandler ingestHandler = new IngestHandler(traceStorage, mapper);
        QueryHandler queryHandler = new QueryHandler(traceStorage, mapper);
        StatisticsHandler statisticsHandler = new StatisticsHandler(traceStorage, mapper);
        HealthHandler healthHandler = new HealthHandler(traceStorage, mapper);

        // Build routing servlet
        AsyncServlet servlet = RoutingServlet.builder(eventloop)
                // Ingestion endpoints
                .with(io.activej.http.HttpMethod.POST, "/api/v1/traces/spans", ingestHandler::handleSingleSpan)
                .with(io.activej.http.HttpMethod.POST, "/api/v1/traces/spans/batch", ingestHandler::handleBatchSpans)
                
                // Query endpoints  
                .with(io.activej.http.HttpMethod.GET, "/api/v1/traces/:traceId", request -> {
                    String traceId = request.getPathParameter("traceId");
                    return queryHandler.handleGetTraceById(request, traceId);
                })
                .with(io.activej.http.HttpMethod.GET, "/api/v1/traces", queryHandler::handleSearchTraces)
                
                // Statistics endpoint
                .with(io.activej.http.HttpMethod.GET, "/api/v1/traces/stats", statisticsHandler::handleGetStatistics)
                
                // Health endpoints
                .with(io.activej.http.HttpMethod.GET, "/health", healthHandler::handleLiveness)
                .with(io.activej.http.HttpMethod.GET, "/health/ready", healthHandler::handleReadiness)
                
                // 404 handler (matches all methods and paths)
                .with("/*", request -> {
                    logger.warn("404 Not Found: {} {}", request.getMethod(), request.getPath());
                    HttpResponse response = HttpResponse.ofCode(404)
                            .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                            .withBody("{\"error\":\"Not Found\",\"path\":\"" + request.getPath() + "\"}".getBytes())
                            .build();
                    return io.activej.promise.Promise.of(response);
                })
                .build();

        // Create HTTP server
        // Note: Port configuration handled by UnifiedApplicationLauncher via --port argument
        HttpServer server = HttpServer.builder(eventloop, servlet)
                .build();

        logger.info("HTTP server created with routing configured");

        return server;
    }

    /**
     * Lifecycle callback invoked after HTTP server starts successfully.
     * <p>
     * Logs service startup confirmation and available API endpoints.
     * Used for operational visibility during deployment.
     * </p>
     *
     * @doc.lifecycle Called once after server binds to port and starts accepting requests
     */
    @Override
    protected void onApplicationStarted() {
        logger.info("TraceHttpService started successfully - REST API operational");
        logger.info("Available endpoints:");
        logger.info("  POST   /api/v1/traces/spans        - Ingest single span");
        logger.info("  POST   /api/v1/traces/spans/batch  - Ingest multiple spans");
        logger.info("  GET    /api/v1/traces/:traceId     - Get trace by ID");
        logger.info("  GET    /api/v1/traces               - Search traces");
        logger.info("  GET    /api/v1/traces/stats         - Get statistics");
        logger.info("  GET    /health                      - Liveness check");
        logger.info("  GET    /health/ready                - Readiness check");
    }

    /**
     * Lifecycle callback invoked during graceful shutdown.
     * <p>
     * Closes {@link TraceStorage} to flush buffered spans and release resources.
     * Logs errors if storage cleanup fails (non-blocking shutdown).
     * </p>
     *
     * @doc.lifecycle Called once during shutdown after HTTP server stops accepting new requests
     * @doc.error-handling Logs storage close errors but does not throw (allows shutdown to complete)
     */
    @Override
    protected void onApplicationStopping() {
        logger.info("Stopping TraceHttpService...");
        
        try {
            // Close storage
            storage.close();
            logger.info("TraceStorage closed successfully");
        } catch (Exception ex) {
            logger.error("Error closing TraceStorage", ex);
        }
    }

    /**
     * Main method for standalone execution with ClickHouse backend.
     * <p>
     * Command-line Arguments:
     * <ul>
     *   <li>{@code --port=N} - HTTP server port (default: 8080, from UnifiedApplicationLauncher)</li>
     *   <li>{@code --ch-host=HOST} - ClickHouse server host (default: localhost)</li>
     *   <li>{@code --ch-port=PORT} - ClickHouse HTTP port (default: 8123)</li>
     *   <li>{@code --ch-database=DB} - ClickHouse database name (default: observability)</li>
     * </ul>
     * </p>
     * <p>
     * Defaults:
     * <ul>
     *   <li>HTTP Port: 8080</li>
     *   <li>ClickHouse Host: localhost</li>
     *   <li>ClickHouse Port: 8123</li>
     *   <li>ClickHouse Database: observability</li>
     *   <li>Batch Size: 5000 spans</li>
     *   <li>Flush Interval: 5 seconds</li>
     * </ul>
     * </p>
     * <p>
     * Usage Examples:
     * <pre>{@code
     * # Start with defaults (localhost:8123, observability database)
     * java -jar trace-http-service.jar --port=8080
     * 
     * # Connect to remote ClickHouse
     * java -jar trace-http-service.jar \
     *   --port=8080 \
     *   --ch-host=clickhouse.prod.internal \
     *   --ch-port=8123 \
     *   --ch-database=traces_prod
     * }</pre>
     * </p>
     *
     * @param args Command-line arguments for HTTP port and ClickHouse configuration
     * @throws Exception if service fails to start (port binding, ClickHouse connection, etc.)
     * @doc.deployment Typically run as Docker container or systemd service in production
     * @doc.testing For local testing, start ClickHouse via docker-compose.observability.yml first
     */
    public static void main(String[] args) throws Exception {
        logger.info("Starting TraceHttpService with ClickHouse backend");
        
        // Parse command-line arguments for ClickHouse configuration
        String chHost = "localhost";
        int chPort = 8123;
        String chDatabase = "observability";
        
        for (String arg : args) {
            if (arg.startsWith("--ch-host=")) {
                chHost = arg.substring("--ch-host=".length());
            } else if (arg.startsWith("--ch-port=")) {
                try {
                    chPort = Integer.parseInt(arg.substring("--ch-port=".length()));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid port number, using default 8123");
                    chPort = 8123;
                }
            } else if (arg.startsWith("--ch-database=")) {
                chDatabase = arg.substring("--ch-database=".length());
            }
        }
        
        logger.info("Configuring ClickHouse storage: host={}, port={}, database={}", chHost, chPort, chDatabase);
        
        // Create ClickHouseTraceStorage with configuration from environment or defaults
        TraceStorage storage = ClickHouseTraceStorage.builder()
                .withHost(chHost)
                .withPort(chPort)
                .withDatabase(chDatabase)
                .withBatchSize(5000)
                .withFlushInterval(java.time.Duration.ofSeconds(5))
                .build();
        
        TraceHttpService service = new TraceHttpService(storage);
        logger.info("TraceHttpService initialized with ClickHouse backend");
        service.launch(args);
    }
}
