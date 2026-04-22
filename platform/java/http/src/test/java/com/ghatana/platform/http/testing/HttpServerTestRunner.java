package com.ghatana.platform.http.server.testing;

import com.ghatana.core.activej.testing.EventloopTestRunner;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * Production-grade test runner for HTTP servers with automatic lifecycle management and ActiveJ Eventloop integration.
 *
 * <p><b>Purpose</b><br>
 * Provides comprehensive HTTP server testing utilities with automatic start/stop, random port
 * allocation, request/response testing helpers, EventloopTestRunner integration, and timeout
 * protection. Eliminates boilerplate in HTTP server integration tests.
 *
 * <p><b>Architecture Role</b><br>
 * Test runner in core/http/testing for HTTP server integration testing.
 * Used by:
 * - HTTP Server Tests - Test servlets and servers in isolation
 * - Integration Tests - Test full HTTP request/response flow
 * - API Tests - Validate endpoint behavior
 * - Performance Tests - Measure server throughput/latency
 *
 * <p><b>Test Runner Features</b><br>
 * - <b>Automatic Lifecycle</b>: Start/stop server automatically in test scope
 * - <b>Random Ports</b>: Allocate ephemeral port (no conflicts) // GH-90000
 * - <b>Request Helpers</b>: GET, POST, PUT, DELETE methods with JSON support
 * - <b>Eventloop Integration</b>: Uses EventloopTestRunner for Promise execution
 * - <b>Timeout Protection</b>: Configurable timeout (default 10s) // GH-90000
 * - <b>AutoCloseable</b>: Try-with-resources support
 * - <b>Base URL</b>: Get full server URL for external client testing
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Basic servlet testing (try-with-resources) // GH-90000
 * AsyncServlet servlet = new RoutingServlet() // GH-90000
 *     .addRoute(HttpMethod.GET, "/users/:id", this::getUser) // GH-90000
 *     .addRoute(HttpMethod.POST, "/users", this::createUser); // GH-90000
 *
 * try (HttpServerTestRunner runner = HttpServerTestRunner.create(servlet)) { // GH-90000
 *     runner.start(); // GH-90000
 *
 *     HttpResponse response = runner.get("/users/123 [GH-90000]");
 *     assertEquals(200, response.getCode()); // GH-90000
 *
 *     String json = new String(response.getBody().asArray()); // GH-90000
 *     assertTrue(json.contains("\"id\":\"123\"")); // GH-90000
 * }
 *
 * // 2. POST request with JSON body
 * try (HttpServerTestRunner runner = HttpServerTestRunner.create(servlet)) { // GH-90000
 *     runner.start(); // GH-90000
 *
 *     HttpResponse response = runner.post("/users", // GH-90000
 *         "{\"name\":\"John Doe\",\"email\":\"john@example.com\"}");
 *
 *     assertEquals(201, response.getCode()); // GH-90000
 *     assertEquals("application/json", response.getHeader("Content-Type [GH-90000]"));
 * }
 *
 * // 3. Custom timeout (1 minute for slow operations) // GH-90000
 * try (HttpServerTestRunner runner = HttpServerTestRunner.create(servlet, Duration.ofMinutes(1))) { // GH-90000
 *     runner.start(); // GH-90000
 *
 *     HttpResponse response = runner.get("/slow-operation [GH-90000]");
 *     assertEquals(200, response.getCode()); // GH-90000
 * }
 *
 * // 4. External client testing with base URL
 * try (HttpServerTestRunner runner = HttpServerTestRunner.create(servlet)) { // GH-90000
 *     runner.start(); // GH-90000
 *
 *     String baseUrl = runner.getBaseUrl(); // GH-90000
 *     // → "http://localhost:12345"
 *
 *     // Use with OkHttp, Apache HttpClient, etc.
 *     OkHttpClient client = new OkHttpClient(); // GH-90000
 *     Request request = new Request.Builder() // GH-90000
 *         .url(baseUrl + "/users/123") // GH-90000
 *         .build(); // GH-90000
 *
 *     Response externalResponse = client.newCall(request).execute(); // GH-90000
 *     assertEquals(200, externalResponse.code()); // GH-90000
 * }
 *
 * // 5. Reuse EventloopTestRunner (share across tests) // GH-90000
 * EventloopTestRunner eventloopRunner = EventloopTestRunner.builder() // GH-90000
 *     .timeout(Duration.ofSeconds(30)) // GH-90000
 *     .build(); // GH-90000
 *
 * try (HttpServerTestRunner runner = HttpServerTestRunner.create(servlet, eventloopRunner)) { // GH-90000
 *     runner.start(); // GH-90000
 *
 *     HttpResponse response = runner.get("/api/data [GH-90000]");
 *     assertEquals(200, response.getCode()); // GH-90000
 * }
 * // eventloopRunner NOT closed (external ownership) // GH-90000
 *
 * // 6. Multiple request methods
 * try (HttpServerTestRunner runner = HttpServerTestRunner.create(servlet)) { // GH-90000
 *     runner.start(); // GH-90000
 *
 *     // GET
 *     HttpResponse getResponse = runner.get("/users/123 [GH-90000]");
 *
 *     // POST
 *     HttpResponse postResponse = runner.post("/users", // GH-90000
 *         "{\"name\":\"Jane\"}");
 *
 *     // PUT
 *     HttpResponse putResponse = runner.put("/users/123", // GH-90000
 *         "{\"name\":\"Jane Updated\"}");
 *
 *     // DELETE
 *     HttpResponse deleteResponse = runner.delete("/users/123 [GH-90000]");
 *
 *     assertEquals(200, getResponse.getCode()); // GH-90000
 *     assertEquals(201, postResponse.getCode()); // GH-90000
 *     assertEquals(200, putResponse.getCode()); // GH-90000
 *     assertEquals(204, deleteResponse.getCode()); // GH-90000
 * }
 *
 * // 7. JUnit 5 integration (manual lifecycle) // GH-90000
 * @Test
 * void testUserEndpoint() throws Exception { // GH-90000
 *     AsyncServlet servlet = createUserServlet(); // GH-90000
 *
 *     try (HttpServerTestRunner runner = HttpServerTestRunner.create(servlet)) { // GH-90000
 *         runner.start(); // GH-90000
 *
 *         HttpResponse response = runner.get("/users [GH-90000]");
 *         assertEquals(200, response.getCode()); // GH-90000
 *
 *         User[] users = objectMapper.readValue( // GH-90000
 *             response.getBody().asArray(), User[].class); // GH-90000
 *
 *         assertTrue(users.length > 0); // GH-90000
 *     }
 * }
 * }</pre>
 *
 * <p><b>Default Configuration</b><br>
 * - Port: Random ephemeral port (OS-assigned) // GH-90000
 * - Timeout: 10 seconds
 * - Eventloop: Created automatically (or external if provided) // GH-90000
 * - Watchdog: Checks every 2 seconds
 * - Thread Name: "http-test"
 *
 * <p><b>Request Methods</b><br>
 * <pre>{@code
 * runner.get(path)                  → GET request // GH-90000
 * runner.post(path, json)           → POST with JSON body // GH-90000
 * runner.put(path, json)            → PUT with JSON body // GH-90000
 * runner.delete(path)               → DELETE request // GH-90000
 * runner.request(method, path)      → Custom HTTP method // GH-90000
 * }</pre>
 *
 * <p><b>Server Lifecycle</b><br>
 * <pre>
 * create() → start() → run tests → close() // GH-90000
 *    ↓         ↓                      ↓
 * Setup   Listen on               Stop server
 *         random port              Close eventloop
 * </pre>
 *
 * <p><b>Port Allocation</b><br>
 * Uses ephemeral port (0) for automatic allocation: // GH-90000
 * <pre>
 * - OS assigns available port
 * - No port conflicts in parallel tests
 * - Port available via runner.getPort() // GH-90000
 * </pre>
 *
 * <p><b>Timeout Handling</b><br>
 * <pre>
 * Default: 10 seconds
 * Custom: Duration.ofSeconds(30) // GH-90000
 * Applies to: All request operations
 * Prevents: Hung tests from blocking CI/CD
 * </pre>
 *
 * <p><b>Best Practices</b><br>
 * - Use try-with-resources for automatic cleanup
 * - Call .start() explicitly (not auto-started) // GH-90000
 * - Share EventloopTestRunner across related tests (performance) // GH-90000
 * - Use HttpServerTestExtension for JUnit 5 integration
 * - Set appropriate timeout for slow operations
 * - Test with external HTTP clients for full integration
 * - Verify response status, headers, and body
 * - Clean up resources in finally/afterEach
 *
 * <p><b>Performance Considerations</b><br>
 * - Server startup: ~100ms
 * - Request execution: <10ms (in-process) // GH-90000
 * - Port allocation: OS-dependent (~10ms) // GH-90000
 * - Eventloop overhead: Minimal (shared across requests) // GH-90000
 * - External client: Add network latency (~1ms localhost) // GH-90000
 *
 * <p><b>Thread Safety</b><br>
 * NOT thread-safe - designed for single-threaded test execution.
 * Each test should create its own runner instance.
 *
 * @see HttpServerTestExtension
 * @see MockHttpClient
 * @see HttpTestUtils
 * @see com.ghatana.core.activej.testing.EventloopTestRunner
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Test runner for HTTP servers with lifecycle management
 * @doc.layer core
 * @doc.pattern Template Method
 */
@Slf4j
public class HttpServerTestRunner implements AutoCloseable {

    private final AsyncServlet servlet;
    private final EventloopTestRunner eventloopRunner;
    private final boolean ownsEventloopRunner;
    private HttpServer server;
    private int port;
    private boolean started = false;

    private HttpServerTestRunner(AsyncServlet servlet, EventloopTestRunner eventloopRunner) { // GH-90000
        this(servlet, eventloopRunner, true); // GH-90000
    }

    private HttpServerTestRunner(AsyncServlet servlet, EventloopTestRunner eventloopRunner, boolean ownsEventloopRunner) { // GH-90000
        this.servlet = servlet;
        this.eventloopRunner = eventloopRunner;
        this.ownsEventloopRunner = ownsEventloopRunner;
    }

    /**
     * Creates a test runner for the given servlet.
     * Uses default timeout of 10 seconds.
     *
     * @param servlet The servlet to test
     * @return A new test runner
     */
    public static HttpServerTestRunner create(AsyncServlet servlet) { // GH-90000
        return create(servlet, Duration.ofSeconds(10)); // GH-90000
    }

    /**
     * Creates a test runner with custom timeout.
     *
     * @param servlet The servlet to test
     * @param timeout The timeout for operations
     * @return A new test runner
     */
    public static HttpServerTestRunner create(AsyncServlet servlet, Duration timeout) { // GH-90000
        EventloopTestRunner eventloopRunner = EventloopTestRunner.builder() // GH-90000
            .timeout(timeout) // GH-90000
            .watchdogEvery(Duration.ofSeconds(2)) // GH-90000
            .threadName("http-test [GH-90000]")
            .build(); // GH-90000

        return new HttpServerTestRunner(servlet, eventloopRunner); // GH-90000
    }

    /**
     * Create a test runner backed by an existing EventloopTestRunner. The provided
     * runner is considered external and will not be closed when this HttpServerTestRunner
     * is closed.
     */
    public static HttpServerTestRunner create(AsyncServlet servlet, EventloopTestRunner eventloopRunner) { // GH-90000
        return new HttpServerTestRunner(servlet, eventloopRunner, false); // GH-90000
    }

    /**
     * Starts the HTTP server on a random available port.
     */
    public void start() { // GH-90000
        if (started) { // GH-90000
            return;
        }

        eventloopRunner.start(); // GH-90000

        // Find available port
        this.port = findAvailablePort(); // GH-90000

        // Create and start server
        eventloopRunner.runBlocking(() -> { // GH-90000
            server = HttpServer.builder(eventloopRunner.eventloop(), servlet) // GH-90000
                .withListenAddress(new InetSocketAddress("127.0.0.1", port)) // GH-90000
                .withAcceptOnce() // GH-90000
                .build(); // GH-90000

            server.listen(); // GH-90000
            log.info("Test HTTP server started on port {}", port); // GH-90000
            return null;
        });

        started = true;
    }

    /**
     * Executes a GET request.
     *
     * @param path The request path
     * @return The HTTP response
     */
    public HttpResponse get(String path) { // GH-90000
        return request(HttpRequest.get("http://127.0.0.1:" + port + path).build()); // GH-90000
    }

    /**
     * Executes a POST request with body.
     *
     * @param path The request path
     * @param body The request body
     * @return The HTTP response
     */
    public HttpResponse post(String path, String body) { // GH-90000
        return request(HttpRequest.post("http://127.0.0.1:" + port + path) // GH-90000
            .withBody(body.getBytes()) // GH-90000
            .build()); // GH-90000
    }

    /**
     * Executes a PUT request with body.
     *
     * @param path The request path
     * @param body The request body
     * @return The HTTP response
     */
    public HttpResponse put(String path, String body) { // GH-90000
        return request(HttpRequest.put("http://127.0.0.1:" + port + path) // GH-90000
            .withBody(body.getBytes()) // GH-90000
            .build()); // GH-90000
    }

    /**
     * Executes a DELETE request.
     *
     * @param path The request path
     * @return The HTTP response
     */
    public HttpResponse delete(String path) { // GH-90000
        HttpRequest.Builder builder = HttpRequest.builder(io.activej.http.HttpMethod.DELETE, "http://127.0.0.1:" + port + path); // GH-90000
        return request(builder.build()); // GH-90000
    }

    /**
     * Executes a custom HTTP request.
     *
     * @param request The HTTP request
     * @return The HTTP response
     */
    public HttpResponse request(HttpRequest request) { // GH-90000
        if (!started) { // GH-90000
            start(); // GH-90000
        }

        return eventloopRunner.runPromise(() -> servlet.serve(request)); // GH-90000
    }

    /**
     * Gets the base URL of the test server.
     *
     * @return The base URL (e.g., http://127.0.0.1:12345) // GH-90000
     */
    public String getBaseUrl() { // GH-90000
        return "http://127.0.0.1:" + port;
    }

    /**
     * Gets the port the server is listening on.
     *
     * @return The port number
     */
    public int getPort() { // GH-90000
        return port;
    }

    /**
     * Gets the underlying eventloop test runner.
     *
     * @return The eventloop runner
     */
    public EventloopTestRunner getEventloopRunner() { // GH-90000
        return eventloopRunner;
    }

    @Override
    public void close() { // GH-90000
        if (server != null) { // GH-90000
            eventloopRunner.runBlocking(() -> { // GH-90000
                server.close(); // GH-90000
                log.info("Test HTTP server stopped [GH-90000]");
                return null;
            });
        }
        if (ownsEventloopRunner) { // GH-90000
            eventloopRunner.close(); // GH-90000
        }
        started = false;
    }

    private int findAvailablePort() { // GH-90000
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) { // GH-90000
            return socket.getLocalPort(); // GH-90000
        } catch (Exception e) { // GH-90000
            throw new RuntimeException("Failed to find available port", e); // GH-90000
        }
    }
}
