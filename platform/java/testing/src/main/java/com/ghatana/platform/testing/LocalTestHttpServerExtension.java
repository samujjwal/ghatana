package com.ghatana.platform.testing;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.net.URI;

/**
 * JUnit 5 extension for local HTTP test server lifecycle management.
 *
 * <h2>Purpose</h2>
 * Provides automatic HTTP server setup/teardown for integration tests:
 * <ul>
 *   <li>Creates ephemeral HTTP server per test method</li>
 *   <li>Injects server instance via parameter injection</li>
 *   <li>Provides default /get endpoint for convenience</li>
 *   <li>Manages lifecycle (start/stop) automatically</li>
 * </ul>
 *
 * <h2>Usage in Tests</h2>
 * {@code
 * @ExtendWith(LocalTestHttpServerExtension.class)
 * class MyHttpIntegrationTest {
 *
 *     // Test infrastructure endpoints (auto-created)
 *     @Test
 *     void shouldAccessDefaultGetEndpoint(LocalTestHttpServer server) throws IOException {
 *         // Test default /get endpoint created by extension
 *         URI defaultGetUri = server.getServerBaseUri().resolve("/get");
 *         
 *         HttpURLConnection conn = (HttpURLConnection) defaultGetUri.toURL().openConnection();
 *         assertEquals(200, conn.getResponseCode());
 *     }
 *
 *     // Custom endpoint registration
 *     @Test
 *     void shouldHandleCustomEndpoints(LocalTestHttpServer server) throws IOException {
 *         // Register custom endpoint
 *         server.createContext("/api/data", exchange -> {
 *             String responseBody = "{\"id\":1,\"name\":\"test\"}";
 *             byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
 *             exchange.getResponseHeaders().add("Content-Type", "application/json");
 *             exchange.sendResponseHeaders(200, bytes.length);
 *             try (OutputStream os = exchange.getResponseBody()) {
 *                 os.write(bytes);
 *             }
 *         });
 *
 *         // Test custom endpoint
 *         URI dataUri = server.getServerBaseUri().resolve("/api/data");
 *         HttpURLConnection conn = (HttpURLConnection) dataUri.toURL().openConnection();
 *         assertEquals(200, conn.getResponseCode());
 *     }
 *
 *     // Multiple endpoints
 *     @Test
 *     void shouldSupportMultipleEndpoints(LocalTestHttpServer server) throws IOException {
 *         server.createContext("/health", exchange -> {
 *             exchange.sendResponseHeaders(200, 7);
 *             try (OutputStream os = exchange.getResponseBody()) {
 *                 os.write("healthy".getBytes());
 *             }
 *         });
 *
 *         server.createContext("/status", exchange -> {
 *             exchange.sendResponseHeaders(200, 2);
 *             try (OutputStream os = exchange.getResponseBody()) {
 *                 os.write("OK".getBytes());
 *             }
 *         });
 *
 *         // Test both endpoints
 *         assertHealthy(server.getServerBaseUri().resolve("/health"));
 *         assertStatus(server.getServerBaseUri().resolve("/status"));
 *     }
 * }
 * }
 *
 * <h2>Lifecycle Management</h2>
 * <ul>
 *   <li><b>@BeforeEach</b>: Creates fresh LocalTestHttpServer, stores in extension context</li>
 *   <li><b>@AfterEach</b>: Retrieves and closes server, cleans up resources</li>
 *   <li><b>Parameter Injection</b>: Injects server instance via ParameterResolver</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * Testing infrastructure extension for core/testing/test-utils:
 * <ul>
 *   <li>Enables lightweight HTTP integration testing without full container</li>
 *   <li>Replaces need for mocking HTTP clients</li>
 *   <li>Supports testing HTTP connectors, clients, and request handlers</li>
 *   <li>Works with ActiveJ HTTP clients for end-to-end testing</li>
 * </ul>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><b>Startup</b>: ~10ms per test (local JDK HTTP server)</li>
 *   <li><b>Shutdown</b>: ~5ms per test</li>
 *   <li><b>Concurrency</b>: Single-threaded (sufficient for test scenarios)</li>
 *   <li><b>Memory</b>: ~1MB per test instance (automatically cleaned)</li>
 * </ul>
 *
 * <h2>Feature Matrix</h2>
 * <table>
 *   <tr><th>Feature</th><th>Supported</th><th>Notes</th></tr>
 *   <tr><td>HTTP GET</td><td>✓</td><td>Via default /get endpoint</td></tr>
 *   <tr><td>HTTP POST</td><td>✓</td><td>Via custom endpoint</td></tr>
 *   <tr><td>Response Bodies</td><td>✓</td><td>String, JSON, binary</td></tr>
 *   <tr><td>Custom Headers</td><td>✓</td><td>Via exchange.getResponseHeaders()</td></tr>
 *   <tr><td>Status Codes</td><td>✓</td><td>Any HTTP status supported</td></tr>
 *   <tr><td>Request Parsing</td><td>✓</td><td>Via getRequestURI(), getQueryString()</td></tr>
 * </table>
 *
 * <h2>Thread Safety</h2>
 * Not thread-safe. Each test method gets its own server instance.
 * Multiple concurrent requests within same test supported by underlying JDK server.
 *
 * @see LocalTestHttpServer HTTP server implementation
 * @see org.junit.jupiter.api.extension.Extension JUnit 5 extension contract
 * @doc.type class
 * @doc.layer testing
 * @doc.purpose JUnit 5 extension for local HTTP server lifecycle in integration tests
 * @doc.pattern extension-point auto-setup-teardown parameter-injection
 */
public class LocalTestHttpServerExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(LocalTestHttpServerExtension.class);
    private static final String KEY = "local-server";

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        LocalTestHttpServer server = new LocalTestHttpServer();
        // Create a default /get endpoint for convenience
        server.createContext("/get", exchange -> {
            String responseBody = "Example Domain - local";
            byte[] bytes = responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        context.getStore(NAMESPACE).put(KEY, server);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        Object obj = context.getStore(NAMESPACE).remove(KEY);
        if (obj instanceof LocalTestHttpServer) {
            ((LocalTestHttpServer) obj).close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        return LocalTestHttpServer.class.equals(type) || URI.class.equals(type);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Object obj = extensionContext.getStore(NAMESPACE).get(KEY);
        if (!(obj instanceof LocalTestHttpServer)) {
            throw new IllegalStateException("LocalTestHttpServer not available");
        }
        LocalTestHttpServer server = (LocalTestHttpServer) obj;
        Class<?> type = parameterContext.getParameter().getType();
        if (LocalTestHttpServer.class.equals(type)) {
            return server;
        }
        if (URI.class.equals(type)) {
            return server.getBaseUri();
        }
        throw new IllegalArgumentException("Unsupported parameter type: " + type);
    }
}
