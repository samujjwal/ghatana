package com.ghatana.platform.http.server.security;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;

/**
 * Production-grade HTTP filter injecting Strict-Transport-Security (HSTS) headers for browser HTTPS enforcement.
 *
 * <p><b>Purpose</b><br>
 * Instructs browsers to always connect via HTTPS, reject invalid certificates, apply policy
 * to all subdomains, and optionally submit domain to HSTS preload list. Prevents protocol
 * downgrade attacks and ensures secure communication for all future requests.
 *
 * <p><b>Architecture Role</b><br>
 * Security filter in core/http/security for HSTS header injection.
 * Used by:
 * - HttpServerBuilder - Add HSTS to all responses
 * - API Gateways - Enforce HTTPS for external clients
 * - Public Services - Meet security compliance requirements
 * - FilterChain - Compose with other security filters
 *
 * <p><b>HSTS Features</b><br>
 * - <b>HTTPS Enforcement</b>: Browsers upgrade all HTTP requests to HTTPS automatically
 * @doc.type class
 * @doc.purpose HTTP filter for Strict-Transport-Security (HSTS) header injection
 * @doc.layer core
 * @doc.pattern Security Filter, HTTP Handler
 *
 * - <b>Certificate Validation</b>: Browsers reject invalid/self-signed certificates (no warnings)
 * - <b>Subdomain Coverage</b>: includeSubDomains directive applies to all subdomains
 * - <b>Preload Support</b>: preload directive for browser preload list submission
 * - <b>Configurable Max-Age</b>: Custom cache duration (default: 1 year)
 * - <b>Automatic Header Injection</b>: Wraps any AsyncServlet to add HSTS header
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Default HSTS filter (1-year max-age)
 * HstsHeaderFilter hstsFilter = new HstsHeaderFilter();
 * AsyncServlet secureServlet = hstsFilter.wrap(baseServlet);
 *
 * // 2. Custom max-age (6 months = 15768000 seconds)
 * HstsHeaderFilter hstsFilter = new HstsHeaderFilter(15768000);
 * AsyncServlet secureServlet = hstsFilter.wrap(baseServlet);
 *
 * // 3. Integration with HttpServerBuilder
 * HttpServer server = HttpServerBuilder.create()
 *     .withPort(443)
 *     .addFilter(new HstsHeaderFilter())
 *     .addRoute(HttpMethod.GET, "/api/data", handler)
 *     .build();
 *
 * // 4. Short max-age for testing (1 day = 86400 seconds)
 * HstsHeaderFilter testHsts = new HstsHeaderFilter(86400);
 * AsyncServlet testServlet = testHsts.wrap(baseServlet);
 *
 * // 5. Chain with other security filters
 * AsyncServlet secureServlet = new HstsHeaderFilter()
 *     .wrap(new CorsFilter()
 *         .wrap(new AuthenticationFilter()
 *             .wrap(baseServlet)));
 *
 * // 6. Conditional HSTS (production only)
 * if (isProdEnvironment()) {
 *     servlet = new HstsHeaderFilter(31536000).wrap(servlet);
 * }
 *
 * // 7. Integration with FilterChain
 * AsyncServlet servlet = FilterChain.create()
 *     .addFilter((request, next) -> new HstsHeaderFilter().wrap(next).serve(request))
 *     .build(baseServlet);
 * }</pre>
 *
 * <p><b>HSTS Header Format</b><br>
 * <pre>
 * Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
 * 
 * Components:
 * - max-age=31536000         → Cache for 1 year (31536000 seconds)
 * - includeSubDomains        → Apply to all subdomains (e.g., api.example.com, www.example.com)
 * - preload                  → Eligible for browser preload list submission
 * </pre>
 *
 * <p><b>HSTS Behavior</b><br>
 * After receiving HSTS header:
 * <pre>
 * 1. Browser caches HSTS policy for max-age duration
 * 2. All HTTP requests automatically upgraded to HTTPS (no server round-trip)
 * 3. Invalid certificates rejected (no "proceed anyway" option)
 * 4. Policy applies to all subdomains (if includeSubDomains present)
 * 5. Policy persists across browser restarts
 * </pre>
 *
 * <p><b>Max-Age Recommendations</b><br>
 * <pre>
 * Development:   86400 (1 day)       → Short duration for testing
 * Staging:       604800 (1 week)     → Medium duration for validation
 * Production:    31536000 (1 year)   → Long duration for security
 * Preload List:  63072000 (2 years)  → Minimum for HSTS preload submission
 * </pre>
 *
 * <p><b>HSTS Preload List</b><br>
 * Submit domain to https://hstspreload.org/ for browser preload:
 * <pre>
 * Requirements:
 * 1. Valid HTTPS certificate
 * 2. Redirect HTTP to HTTPS
 * 3. Serve HSTS header with max-age >= 31536000
 * 4. Include 'preload' directive
 * 5. Include 'includeSubDomains' directive
 * </pre>
 *
 * <p><b>Removing HSTS Policy</b><br>
 * To remove HSTS policy from browsers:
 * <pre>{@code
 * // Serve HSTS header with max-age=0
 * HstsHeaderFilter removalFilter = new HstsHeaderFilter(0);
 * 
 * // Browsers will:
 * // 1. Delete cached HSTS policy
 * // 2. Allow HTTP connections again
 * // 3. Show certificate warnings if invalid
 * }</pre>
 *
 * <p><b>Compliance Standards</b><br>
 * - <b>OWASP ASVS 9.2.1</b>: TLS must be used for all client connections
 * - <b>PCI DSS 4.1 Requirement 4.2</b>: Strong cryptography enforcement
 * - <b>NIST SP 800-52</b>: HSTS recommended for all public-facing services
 * - <b>GDPR Art. 32(1)(a)</b>: Encryption of personal data in transit
 *
 * <p><b>Best Practices</b><br>
 * - Start with short max-age (1 day) for testing, increase gradually
 * - Use 1-year max-age for production (31536000 seconds)
 * - Enable HSTS only on HTTPS servers (never HTTP)
 * - Include 'includeSubDomains' to cover all subdomains
 * - Add 'preload' and submit to HSTS preload list for maximum security
 * - Monitor certificate expiration (HSTS breaks with expired certificates)
 * - Test thoroughly before enabling (hard to undo)
 * - Coordinate with DNS/CDN if using subdomains
 *
 * <p><b>Security Considerations</b><br>
 * - HSTS prevents protocol downgrade attacks (HTTPS → HTTP)
 * - HSTS prevents man-in-the-middle attacks on first visit (if preloaded)
 * - HSTS breaks HTTP-only services (ensure all content available via HTTPS)
 * - Invalid certificates cannot be bypassed (ensure valid certificate)
 * - Removing HSTS requires serving max-age=0 and waiting for cache expiration
 *
 * <p><b>Thread Safety</b><br>
 * Filter instance is immutable and thread-safe.
 * Safe to use with concurrent requests.
 *
 * @see ApiHttpsConfig
 * @see HttpsRedirectHandler
 * @see FilterChain
 * @see <a href="https://tools.ietf.org/html/rfc6797">RFC 6797 - HTTP Strict Transport Security</a>
 * @see <a href="https://hstspreload.org/">HSTS Preload List Submission</a>
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose HTTP filter injecting HSTS headers for HTTPS enforcement
 * @doc.layer core
 * @doc.pattern Chain of Responsibility
 */
public final class HstsHeaderFilter {

    /**
     * HSTS header name.
     */
    private static final HttpHeader HSTS_HEADER = HttpHeaders.of("Strict-Transport-Security");

    /**
     * Default max-age: 1 year (31536000 seconds).
     */
    private static final long DEFAULT_MAX_AGE = 31536000L;

    private final String hstsValue;

    /**
     * Creates an HSTS filter with default 1-year max-age.
     */
    public HstsHeaderFilter() {
        this(DEFAULT_MAX_AGE);
    }

    /**
     * Creates an HSTS filter with custom max-age.
     *
     * @param maxAgeSeconds HSTS max-age in seconds
     */
    public HstsHeaderFilter(long maxAgeSeconds) {
        if (maxAgeSeconds < 0) {
            throw new IllegalArgumentException(
                "HSTS max-age must be non-negative, got: " + maxAgeSeconds
            );
        }
        this.hstsValue = String.format(
            "max-age=%d; includeSubDomains; preload",
            maxAgeSeconds
        );
    }

    /**
     * Wraps a servlet with HSTS header injection.
     *
     * @param servlet Base servlet to wrap
     * @return Wrapped servlet with HSTS headers
     */
    public AsyncServlet wrap(AsyncServlet servlet) {
        return request -> servlet.serve(request)
                .map(response -> addHstsHeader(response));
    }

    /**
     * Adds HSTS header to HTTP response.
     * <p>
     * Creates a new response with the HSTS header while preserving
     * the original response's status code and body (if present).
     * Redirect responses (301, 302) don't have bodies, so we handle
     * them separately to preserve the Location header.
     *
     * @param originalResponse Original HTTP response
     * @return New response with HSTS header
     */
    private HttpResponse addHstsHeader(HttpResponse originalResponse) {
        HttpResponse.Builder builder = HttpResponse.ofCode(originalResponse.getCode())
                .withHeader(HSTS_HEADER, hstsValue);
        
        // Copy Location header if present (for redirects)
        String location = originalResponse.getHeader(HttpHeaders.LOCATION);
        if (location != null) {
            builder.withHeader(HttpHeaders.LOCATION, location);
        }
        
        // Only add body if it exists - redirect responses don't have bodies
        // We need to catch the exception since getBody() throws if body is missing
        try {
            if (originalResponse.getBody() != null) {
                builder.withBody(originalResponse.getBody());
            }
        } catch (IllegalStateException e) {
            // Body is missing or already consumed - this is OK for redirects
            // Just return the response without a body
        }
        
        return builder.build();
    }

    public String getHstsValue() {
        return hstsValue;
    }
}
