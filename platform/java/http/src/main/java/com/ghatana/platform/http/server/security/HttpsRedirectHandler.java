package com.ghatana.platform.http.server.security;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

/**
 * Production-grade HTTP servlet redirecting all requests to HTTPS with 301 Permanent Redirect and path preservation.
 *
 * <p><b>Purpose</b><br>
 * Enforces HTTPS-only communication by permanently redirecting HTTP requests to HTTPS,
 * preserving request path and query parameters. Typically deployed on port 80 to redirect
 * all HTTP traffic to HTTPS port 443 (or custom port).
 *
 * <p><b>Architecture Role</b><br>
 * HTTPS redirect handler in core/http/security for HTTP→HTTPS redirection.
 * Used by:
 * - HTTP Servers (port 80) - Redirect to HTTPS
 * - Load Balancers - Force HTTPS for all traffic
 * - API Gateways - Ensure secure communication
 * - Compliance - Meet regulatory security requirements
 *
 * <p><b>Redirect Features</b><br>
 * @doc.type class
 * @doc.purpose HTTP servlet for permanent HTTP→HTTPS redirection with path preservation
 * @doc.layer core
 * @doc.pattern Security Handler, HTTP Redirect
 *
 * - <b>301 Permanent Redirect</b>: Browsers cache redirect (SEO-friendly)
 * - <b>Path Preservation</b>: Original request path maintained in redirect
 * - <b>Query Parameters</b>: Query string preserved in redirect
 * - <b>Custom HTTPS Port</b>: Support non-standard ports (e.g., 8443)
 * - <b>Location Header</b>: Proper redirect URI construction
 * - <b>Performance</b>: Minimal overhead (no I/O, just header rewrite)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Redirect to standard HTTPS port (443)
 * Eventloop eventloop = Eventloop.create();
 * HttpServer httpServer = HttpServer.builder(eventloop, HttpsRedirectHandler.create())
 *     .withListenPort(80)
 *     .build();
 * 
 * httpServer.listen();
 *
 * // 2. Redirect to custom HTTPS port (8443)
 * HttpServer httpServer = HttpServer.builder(eventloop, HttpsRedirectHandler.create(8443))
 *     .withListenPort(8080)
 *     .build();
 *
 * // 3. Dual server setup (HTTP redirector + HTTPS server)
 * // HTTPS server on port 443
 * HttpServer httpsServer = HttpServerBuilder.create()
 *     .withPort(443)
 *     .withHttpsConfig(httpsConfig)
 *     .addRoute(HttpMethod.GET, "/api/data", handler)
 *     .build();
 * 
 * // HTTP redirector on port 80
 * HttpServer httpServer = HttpServer.builder(eventloop, HttpsRedirectHandler.create())
 *     .withListenPort(80)
 *     .build();
 * 
 * httpsServer.listen();
 * httpServer.listen();
 *
 * // 4. Load balancer configuration
 * HttpServer redirector = HttpServer.builder(eventloop, HttpsRedirectHandler.create())
 *     .withListenPort(80)
 *     .build();
 *
 * // 5. Docker deployment (non-root ports)
 * HttpServer httpServer = HttpServer.builder(eventloop, HttpsRedirectHandler.create(8443))
 *     .withListenPort(8080)
 *     .build();
 *
 * // 6. Testing with ephemeral ports
 * @Test
 * void testHttpsRedirect() {
 *     HttpServer redirector = HttpServer.builder(testEventloop, 
 *         HttpsRedirectHandler.create(9443))
 *         .withListenPort(9080)
 *         .build();
 *     
 *     redirector.listen();
 *     
 *     // Test redirect behavior
 *     HttpRequest request = HttpRequest.get("http://localhost:9080/api/users?page=1");
 *     HttpResponse response = redirector.serve(request).getResult();
 *     
 *     assertEquals(301, response.getCode());
 *     assertEquals("https://localhost:9443/api/users?page=1", 
 *         response.getHeader("Location"));
 * }
 * }</pre>
 *
 * <p><b>Redirect Examples</b><br>
 * <pre>
 * HTTP Request:  http://api.ghatana.com/v1/events?limit=10
 * HTTP Response: HTTP/1.1 301 Moved Permanently
 *                Location: https://api.ghatana.com/v1/events?limit=10
 *
 * HTTP Request:  http://api.ghatana.com:8080/users/123
 * HTTP Response: HTTP/1.1 301 Moved Permanently
 *                Location: https://api.ghatana.com:8443/users/123
 *
 * HTTP Request:  http://example.com/
 * HTTP Response: HTTP/1.1 301 Moved Permanently
 *                Location: https://example.com/
 * </pre>
 *
 * <p><b>301 vs 302 Redirect</b><br>
 * Uses 301 Permanent Redirect:
 * <pre>
 * Advantages:
 * - Browsers cache redirect (performance improvement)
 * - Search engines update index (SEO benefit)
 * - Signals long-term policy change
 * 
 * Considerations:
 * - Hard to undo (browsers cache redirect)
 * - Use 302 Temporary only if HTTPS is temporary
 * </pre>
 *
 * <p><b>Location Header Construction</b><br>
 * <pre>
 * https://{host}:{port}{path}{?query}
 * 
 * Components:
 * - https://          → Force HTTPS protocol
 * - {host}            → Original request Host header
 * - :{port}           → HTTPS port (omitted if 443)
 * - {path}            → Original request path
 * - {?query}          → Original query string (if present)
 * </pre>
 *
 * <p><b>Port Handling</b><br>
 * <pre>
 * Standard HTTPS (443):
 * http://api.ghatana.com/path → https://api.ghatana.com/path
 * 
 * Custom HTTPS port (8443):
 * http://api.ghatana.com:8080/path → https://api.ghatana.com:8443/path
 * </pre>
 *
 * <p><b>Compliance Standards</b><br>
 * - <b>OWASP ASVS 9.2.1</b>: TLS must be used for all client connections
 * - <b>PCI DSS 4.1 Requirement 4.2</b>: Secure transmission of cardholder data
 * - <b>NIST SP 800-52</b>: HTTP should redirect to HTTPS
 * - <b>GDPR Art. 32(1)(a)</b>: Encryption of personal data in transit
 *
 * <p><b>Deployment Patterns</b><br>
 * <pre>{@code
 * // Pattern 1: Single server with dual ports
 * HttpServer httpsServer = createHttpsServer(443);
 * HttpServer httpRedirector = createHttpRedirector(80, 443);
 * 
 * // Pattern 2: Load balancer handles redirect
 * // (NGINX/HAProxy configured for HTTP→HTTPS redirect)
 * 
 * // Pattern 3: Container deployment (non-root ports)
 * HttpServer httpsServer = createHttpsServer(8443);
 * HttpServer httpRedirector = createHttpRedirector(8080, 8443);
 * 
 * // Pattern 4: Cloud provider termination
 * // (ALB/CloudFront handles redirect, app server HTTPS only)
 * }</pre>
 *
 * <p><b>Best Practices</b><br>
 * - Deploy HTTP redirector on port 80 for standard HTTP traffic
 * - Use 301 Permanent Redirect (default behavior)
 * - Combine with HSTS headers for browser-side enforcement
 * - Ensure HTTPS server is running before starting redirector
 * - Test redirect behavior with curl/browser dev tools
 * - Monitor redirect metrics (redirect count, errors)
 * - Consider load balancer/proxy-level redirect for high traffic
 * - Validate certificate before enabling redirect
 *
 * <p><b>Performance</b><br>
 * - Minimal overhead (header rewrite only, no I/O)
 * - No body content (empty response)
 * - Browser caches 301 redirect (no server round-trip)
 * - Suitable for high-traffic deployments
 *
 * <p><b>Security Considerations</b><br>
 * - Always redirect to same host (prevent open redirect)
 * - Preserve original path and query (no data loss)
 * - Use 301 (not 302) to signal permanent policy
 * - Combine with HSTS for browser-side enforcement
 * - Monitor for redirect loops (misconfiguration)
 *
 * <p><b>Thread Safety</b><br>
 * Handler is immutable and thread-safe.
 * Safe to use with concurrent requests.
 *
 * @see ApiHttpsConfig
 * @see HstsHeaderFilter
 * @see HttpServerBuilder
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose HTTP servlet redirecting all requests to HTTPS
 * @doc.layer core
 * @doc.pattern Adapter
 */
public final class HttpsRedirectHandler implements AsyncServlet {

    /**
     * Standard HTTPS port (443).
     */
    private static final int DEFAULT_HTTPS_PORT = 443;

    private final int httpsPort;

    private HttpsRedirectHandler(int httpsPort) {
        if (httpsPort < 1 || httpsPort > 65535) {
            throw new IllegalArgumentException(
                "HTTPS port must be between 1 and 65535, got: " + httpsPort
            );
        }
        this.httpsPort = httpsPort;
    }

    /**
     * Creates a redirect handler for standard HTTPS port (443).
     *
     * @return HTTPS redirect handler
     */
    public static HttpsRedirectHandler create() {
        return new HttpsRedirectHandler(DEFAULT_HTTPS_PORT);
    }

    /**
     * Creates a redirect handler for custom HTTPS port.
     *
     * @param httpsPort Custom HTTPS port number
     * @return HTTPS redirect handler
     */
    public static HttpsRedirectHandler create(int httpsPort) {
        return new HttpsRedirectHandler(httpsPort);
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) {
        String httpsUrl = buildHttpsUrl(request);
        HttpResponse response = HttpResponse.ofCode(301)
                .withHeader(HttpHeaders.LOCATION, httpsUrl)
                .build();
        return Promise.of(response);
    }

    /**
     * Builds HTTPS URL from HTTP request.
     *
     * @param request HTTP request
     * @return HTTPS URL (with port if non-standard)
     */
    private String buildHttpsUrl(HttpRequest request) {
        // Get host from Host header or URL
        String host = request.getHeader(HttpHeaders.HOST);
        if (host == null) {
            throw new IllegalStateException("Missing Host header in HTTP request");
        }

        // Remove existing port from Host header
        String hostname = host.split(":")[0];

        // Build HTTPS URL
        StringBuilder url = new StringBuilder("https://");
        url.append(hostname);

        // Add port if non-standard
        if (httpsPort != DEFAULT_HTTPS_PORT) {
            url.append(':').append(httpsPort);
        }

        // Add path
        url.append(request.getPath());

        // Add query string if present
        String query = request.getQuery();
        if (query != null && !query.isEmpty()) {
            url.append('?').append(query);
        }

        return url.toString();
    }

    public int getHttpsPort() {
        return httpsPort;
    }
}
