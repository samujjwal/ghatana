/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.MediaTypes;
import io.activej.http.ContentType;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * OWASP-aligned HTTP security filter for the AEP HTTP server.
 *
 * <p>Wraps the downstream {@link AsyncServlet} (router) and enforces:
 * <ul>
 *   <li><strong>Security Headers</strong>: X-Content-Type-Options, X-Frame-Options,
 *       Content-Security-Policy, Referrer-Policy, Permissions-Policy,
 *       Strict-Transport-Security (HSTS), X-XSS-Protection (disable legacy)</li>
 *   <li><strong>CORS</strong>: Configurable allowed origins; preflight (OPTIONS) handling</li>
 *   <li><strong>Payload Size Limit</strong>: Rejects requests with Content-Length exceeding
 *       {@value #MAX_REQUEST_BODY_BYTES} bytes before body loading</li>
 *   <li><strong>IP-based Rate Limiting</strong>: Fixed-window algorithm, 200 req/60 s per IP,
 *       bounded to {@value #MAX_TRACKED_IPS} entries; returns HTTP 429 + Retry-After on breach</li>
 * </ul>
 *
 * <p>The filter is <em>stateless</em> aside from the rate-limit window map; all methods
 * are safe to call from the ActiveJ event loop thread.
 *
 * @doc.type class
 * @doc.purpose OWASP security filter: headers, CORS, payload size, rate limiting
 * @doc.layer product
 * @doc.pattern Decorator, SecurityGuard
 */
public final class AepSecurityFilter implements AsyncServlet {

    private static final Logger log = LoggerFactory.getLogger(AepSecurityFilter.class);

    // ── Payload size ────────────────────────────────────────────────────────────
    private static final int MAX_REQUEST_BODY_BYTES = AepInputValidator.MAX_REQUEST_BODY_BYTES;

    // ── Rate limiting ───────────────────────────────────────────────────────────
    private static final int RATE_LIMIT_REQUESTS = 200;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    /** Counter incremented when a trusted proxy supplied a valid X-Forwarded-For client IP. */
    public static final String FORWARDED_HEADER_ACCEPTED = "aep.security.proxy.forwarded.accepted";

    /** Counter incremented when X-Forwarded-For is ignored or malformed. */
    public static final String FORWARDED_HEADER_REJECTED = "aep.security.proxy.forwarded.rejected";

    // ── Security header values (constants to avoid allocation per request) ──────
    static final String HSTS_VALUE =
            "max-age=31536000; includeSubDomains; preload";
    static final String CSP_VALUE =
            "default-src 'none'; frame-ancestors 'none'; form-action 'none'";
    private static final String PERMISSIONS_VALUE =
            "camera=(), microphone=(), geolocation=(), payment=()";
    private static final String REFERRER_VALUE =
            "strict-origin-when-cross-origin";

    // ── CORS ────────────────────────────────────────────────────────────────────
    private final String allowedOrigins;
    private final List<IpRange> trustedProxyRanges;
    private final MetricsCollector metricsCollector;

    // ── Downstream ──────────────────────────────────────────────────────────────
    private final AsyncServlet next;
    private final Function<HttpRequest, String> remoteAddressResolver;

    // ── Shared platform limiter ─────────────────────────────────────────────────
    private final RateLimiter rateLimiter;

    /**
     * Constructs a security filter with the given CORS policy.
     *
     * @param next           downstream servlet (router)
     * @param allowedOrigins comma-separated list of allowed CORS origins, or {@code "*"}
     */
    public AepSecurityFilter(AsyncServlet next, String allowedOrigins) {
        this(next, allowedOrigins, "", MetricsCollectorFactory.createNoop());
    }

    /**
     * Constructs a security filter with the given CORS policy and metrics collector.
     *
     * @param next downstream servlet (router)
     * @param allowedOrigins comma-separated list of allowed CORS origins, or {@code "*"}
     * @param metricsCollector metrics collector for security decision counters
     */
    public AepSecurityFilter(AsyncServlet next, String allowedOrigins, MetricsCollector metricsCollector) {
        this(next, allowedOrigins, "", metricsCollector);
    }

    /**
     * Constructs a security filter with the given CORS policy and trusted-proxy list.
     *
     * @param next downstream servlet (router)
     * @param allowedOrigins comma-separated list of allowed CORS origins, or {@code "*"}
     * @param trustedProxyCidrs comma-separated exact IPs or CIDR ranges allowed to supply X-Forwarded-For
     */
    public AepSecurityFilter(AsyncServlet next, String allowedOrigins, String trustedProxyCidrs) {
        this(next, allowedOrigins, trustedProxyCidrs, MetricsCollectorFactory.createNoop());
        }

        /**
         * Constructs a security filter with the given CORS policy, trusted-proxy list, and metrics.
         *
         * @param next downstream servlet (router)
         * @param allowedOrigins comma-separated list of allowed CORS origins, or {@code "*"}
         * @param trustedProxyCidrs comma-separated exact IPs or CIDR ranges allowed to supply X-Forwarded-For
         * @param metricsCollector metrics collector for proxy validation decisions
         */
        public AepSecurityFilter(AsyncServlet next,
                     String allowedOrigins,
                     String trustedProxyCidrs,
                     MetricsCollector metricsCollector) {
        this(next, allowedOrigins, trustedProxyCidrs, AepSecurityFilter::resolveRemoteAddress, metricsCollector);
    }

    AepSecurityFilter(AsyncServlet next,
                      String allowedOrigins,
                      String trustedProxyCidrs,
                      Function<HttpRequest, String> remoteAddressResolver) {
        this(next, allowedOrigins, trustedProxyCidrs, remoteAddressResolver, MetricsCollectorFactory.createNoop());
        }

        AepSecurityFilter(AsyncServlet next,
                  String allowedOrigins,
                  String trustedProxyCidrs,
                  Function<HttpRequest, String> remoteAddressResolver,
                  MetricsCollector metricsCollector) {
        this.next = next;
        this.allowedOrigins = allowedOrigins != null ? allowedOrigins : "*";
        this.trustedProxyRanges = parseTrustedProxyRanges(trustedProxyCidrs);
        this.remoteAddressResolver = remoteAddressResolver != null
                ? remoteAddressResolver
                : AepSecurityFilter::resolveRemoteAddress;
        this.metricsCollector = metricsCollector != null
            ? metricsCollector
            : MetricsCollectorFactory.createNoop();
        this.rateLimiter = DefaultRateLimiter.create(
                RateLimiterConfig.builder()
                        .maxRequestsPerMinute(RATE_LIMIT_REQUESTS)
                        .burstSize(RATE_LIMIT_REQUESTS)
                        .windowDuration(RATE_LIMIT_WINDOW)
                        .build()
        );
    }

    /** Convenience constructor: allows all origins. */
    public AepSecurityFilter(AsyncServlet next) {
        this(next, "*");
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) throws Exception {
        // ── 1. Preflight CORS ────────────────────────────────────────────────
        if (request.getMethod() == HttpMethod.OPTIONS) {
            return Promise.of(corsPreflightResponse(request));
        }

        // ── 2. Payload size gate ─────────────────────────────────────────────
        // Fast path: reject based on the declared Content-Length header before reading the body.
        String contentLengthHeader = request.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLengthHeader != null) {
            try {
                long declaredSize = Long.parseLong(contentLengthHeader.trim());
                if (declaredSize > MAX_REQUEST_BODY_BYTES) {
                    log.warn("Rejected oversized request: Content-Length={} from ip={}",
                            declaredSize, clientIp(request));
                    return Promise.of(errorResponse(413,
                            "Request body exceeds maximum allowed size of " +
                            (MAX_REQUEST_BODY_BYTES / 1024 / 1024) + " MiB"));
                }
            } catch (NumberFormatException ignored) {
                // malformed Content-Length — let downstream decide
            }
        }

        // Slow path: load up to (MAX + 1) bytes from the body asynchronously.
        // If exactly MAX+1 bytes are returned, the actual body is larger than the limit.
        // Note: in unit tests, GET requests have no body stream; loadBody() throws ISE
        // synchronously — catch it and fall through to the routing layer.
        Promise<io.activej.bytebuf.ByteBuf> bodyLoad;
        try {
                bodyLoad = request.loadBody(MAX_REQUEST_BODY_BYTES + 1)
                    .map(buf -> {
                        if (buf != null && buf.readRemaining() > MAX_REQUEST_BODY_BYTES) {
                            // Read (MAX+1) bytes → original body exceeds limit
                            throw new IllegalStateException("Payload Too Large");
                        }
                        return buf;
                    });
        } catch (IllegalStateException noBody) {
            // No body stream present (e.g., GET request) — proceed without body check.
            bodyLoad = Promise.of(null);
        }

        return bodyLoad.then(
                buf -> {
                    // ── 3. Rate limiting ─────────────────────────────────────────────
                    String clientIp = clientIp(request);
                    RateLimiter.AcquireResult rateLimitResult = rateLimiter.tryAcquire(clientIp);
                    if (!rateLimitResult.allowed()) {
                        log.warn("Rate limit exceeded for ip={}", clientIp);
                        return Promise.of(rateLimitResponse(rateLimitResult));
                    }

                    // ── 4. Delegate to router with security headers applied ──
                    return next.serve(request).map(response -> addSecurityHeaders(response, request));
                },
                e -> {
                    // loadBody failed — body exceeded the size limit
                    log.warn("Rejected oversized request body from ip={}: {}",
                            clientIp(request), e.getMessage());
                    return Promise.of(errorResponse(413,
                            "Request body exceeds maximum allowed size of " +
                            (MAX_REQUEST_BODY_BYTES / 1024 / 1024) + " MiB"));
                }
        );
    }

    // =========================================================================
    // Security headers
    // =========================================================================

    /**
     * Adds OWASP-recommended security headers to every response.
     *
     * <p>Because ActiveJ's {@code HttpResponse} is immutable once built, this method
     * reconstructs a new response with the same status code, all original headers, the
     * original body, and the additional security headers appended.
     *
     * <p>The body {@code ByteBuf} reference is reused directly (no copy). ActiveJ's
     * promise pipeline ensures the original response is not recycled before this method
     * completes.
     */
    private HttpResponse addSecurityHeaders(HttpResponse response, HttpRequest request) {
        int code = response.getCode();

        // Retrieve body defensively — ActiveJ throws if no body was set
        io.activej.bytebuf.ByteBuf body = null;
        try {
            body = response.getBody();
        } catch (IllegalStateException ignored) {
            // Response has no body (e.g., 200 with no payload) — safe to skip
        }

        // Start from fresh builder — preserves immutability contract
        HttpResponse.Builder builder = HttpResponse.ofCode(code);

        // Copy all existing headers (Content-Type, etc.)
        for (java.util.Map.Entry<HttpHeader, HttpHeaderValue> entry : response.getHeaders()) {
            builder.withHeader(entry.getKey(), entry.getValue());
        }

        // Determine the actual origin for CORS (use specific origin when credentials are enabled)
        String corsOrigin = allowedOrigins;
        boolean allowCredentials = false;
        
        // When credentials are expected (cookies, auth headers), use the specific origin from request
        // instead of "*" to satisfy CORS spec requirements
        String requestOrigin = request.getHeader(HttpHeaders.ORIGIN);
        if ("*".equals(allowedOrigins) && requestOrigin != null && !requestOrigin.isEmpty()) {
            // For development, use the actual origin from the request when credentials are enabled
            corsOrigin = requestOrigin;
            allowCredentials = true;
        } else if ("*".equals(allowedOrigins)) {
            // No origin in request or wildcard configured - no credentials
            corsOrigin = "*";
            allowCredentials = false;
        } else {
            // When specific origins are configured, allow credentials
            allowCredentials = true;
        }

        // Inject OWASP-recommended security headers
        builder
            .withHeader(HttpHeaders.of("X-Content-Type-Options"),       HttpHeaderValue.of("nosniff"))
            .withHeader(HttpHeaders.of("X-Frame-Options"),              HttpHeaderValue.of("DENY"))
            .withHeader(HttpHeaders.of("X-XSS-Protection"),            HttpHeaderValue.of("0"))
            .withHeader(HttpHeaders.of("Content-Security-Policy"),     HttpHeaderValue.of(CSP_VALUE))
            .withHeader(HttpHeaders.of("Referrer-Policy"),             HttpHeaderValue.of(REFERRER_VALUE))
            .withHeader(HttpHeaders.of("Permissions-Policy"),          HttpHeaderValue.of(PERMISSIONS_VALUE))
            .withHeader(HttpHeaders.of("Strict-Transport-Security"),   HttpHeaderValue.of(HSTS_VALUE))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(corsOrigin))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), HttpHeaderValue.of(allowCredentials ? "true" : "false"))
            .withHeader(HttpHeaders.of("Cache-Control"),               HttpHeaderValue.of("no-store"))
            .withHeader(HttpHeaders.of("X-Request-Id"),
                        HttpHeaderValue.of(java.util.UUID.randomUUID().toString()));

        // Re-apply body (ByteBuf ref shared; not double-freed because original response is dropped)
        if (body != null && body.readRemaining() > 0) {
            builder.withBody(body);
        }

        return builder.build();
    }

    // =========================================================================
    // CORS preflight
    // =========================================================================

    private HttpResponse corsPreflightResponse(HttpRequest request) {
        String requestedHeaders = request.getHeader(
                HttpHeaders.of("Access-Control-Request-Headers"));
        String requestOrigin = request.getHeader(HttpHeaders.ORIGIN);
        
        // When credentials are enabled, use the specific origin from the request
        // instead of "*" to satisfy CORS spec requirements
        String corsOrigin = allowedOrigins;
        if ("*".equals(allowedOrigins) && requestOrigin != null && !requestOrigin.isEmpty()) {
            corsOrigin = requestOrigin;
        }
        
        return HttpResponse.ofCode(204)
            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),
                        HttpHeaderValue.of(corsOrigin))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"),
                        HttpHeaderValue.of("GET, POST, PUT, DELETE, OPTIONS, PATCH"))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"),
                        HttpHeaderValue.of(requestedHeaders != null
                            ? requestedHeaders
                            : "Content-Type, Authorization, X-Tenant-Id, X-User-ID, X-Session-ID, X-API-Key, Idempotency-Key"))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"),
                        HttpHeaderValue.of("true"))
            .withHeader(HttpHeaders.of("Access-Control-Max-Age"),
                        HttpHeaderValue.of("86400"))
            .build();
    }

    // =========================================================================
    // Client IP resolution
    // =========================================================================

    private String clientIp(HttpRequest request) {
        String remoteIp = normalizeAddress(remoteAddressResolver.apply(request));

        // Honour X-Forwarded-For only when the immediate caller is a configured trusted proxy.
        String xff = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (xff != null && !xff.isBlank()) {
            if (isTrustedProxy(remoteIp)) {
                int comma = xff.indexOf(',');
                String forwardedClient = normalizeAddress(comma > 0 ? xff.substring(0, comma) : xff);
                if (forwardedClient != null) {
                    recordForwardedHeaderAccepted();
                    return forwardedClient;
                }
                recordForwardedHeaderRejected("invalid_forwarded_for");
            } else {
                recordForwardedHeaderRejected(trustedProxyRanges.isEmpty()
                        ? "no_trusted_proxy_configured"
                        : "untrusted_proxy");
                log.debug("Ignoring X-Forwarded-For from untrusted remote address='{}'", remoteIp);
            }
        }
        return remoteIp != null ? remoteIp : "unknown";
    }

    private void recordForwardedHeaderAccepted() {
        metricsCollector.incrementCounter(FORWARDED_HEADER_ACCEPTED);
    }

    private void recordForwardedHeaderRejected(String reason) {
        metricsCollector.incrementCounter(FORWARDED_HEADER_REJECTED, "reason", reason);
    }

    private boolean isTrustedProxy(String remoteIp) {
        if (remoteIp == null || trustedProxyRanges.isEmpty()) {
            return false;
        }
        return trustedProxyRanges.stream().anyMatch(range -> range.matches(remoteIp));
    }

    private static List<IpRange> parseTrustedProxyRanges(String trustedProxyCidrs) {
        if (trustedProxyCidrs == null || trustedProxyCidrs.isBlank()) {
            return List.of();
        }

        List<IpRange> ranges = new ArrayList<>();
        for (String rawEntry : trustedProxyCidrs.split(",")) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                continue;
            }
            try {
                ranges.add(IpRange.parse(entry));
            } catch (IllegalArgumentException ex) {
                log.warn("Ignoring invalid trusted proxy entry='{}': {}", entry, ex.getMessage());
            }
        }
        return List.copyOf(ranges);
    }

    private static String resolveRemoteAddress(HttpRequest request) {
        try {
            InetAddress remoteAddress = request.getRemoteAddress();
            return remoteAddress != null ? remoteAddress.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }

        String trimmed = address.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String normalized = trimmed;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("[") && normalized.contains("]")) {
            normalized = normalized.substring(1, normalized.indexOf(']'));
        } else {
            int colonIndex = normalized.lastIndexOf(':');
            if (colonIndex > 0 && normalized.indexOf(':') == colonIndex) {
                normalized = normalized.substring(0, colonIndex);
            }
        }
        return normalized.isBlank() ? null : normalized;
    }

    private record IpRange(byte[] networkBytes, int prefixLength) {
        private static IpRange parse(String raw) {
            String[] parts = raw.split("/");
            try {
                InetAddress address = InetAddress.getByName(parts[0].trim());
                int prefix = parts.length == 2
                        ? Integer.parseInt(parts[1].trim())
                        : address.getAddress().length * Byte.SIZE;
                int maxBits = address.getAddress().length * Byte.SIZE;
                if (prefix < 0 || prefix > maxBits) {
                    throw new IllegalArgumentException("prefix length out of range");
                }
                return new IpRange(address.getAddress(), prefix);
            } catch (UnknownHostException | NumberFormatException ex) {
                throw new IllegalArgumentException("invalid IP or CIDR", ex);
            }
        }

        private boolean matches(String candidate) {
            try {
                byte[] candidateBytes = InetAddress.getByName(candidate).getAddress();
                if (candidateBytes.length != networkBytes.length) {
                    return false;
                }

                int fullBytes = prefixLength / Byte.SIZE;
                int remainingBits = prefixLength % Byte.SIZE;
                for (int index = 0; index < fullBytes; index++) {
                    if (candidateBytes[index] != networkBytes[index]) {
                        return false;
                    }
                }

                if (remainingBits == 0) {
                    return true;
                }

                int mask = 0xFF << (Byte.SIZE - remainingBits);
                return (candidateBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
            } catch (UnknownHostException ex) {
                return false;
            }
        }
    }

    // =========================================================================
    // Error helpers
    // =========================================================================

    private HttpResponse errorResponse(int code, String message) {
        String body;
        try {
            body = String.format(
                "{\"error\":\"%s\",\"code\":%d,\"timestamp\":\"%s\"}",
                message.replace("\"", "\\\""), code, Instant.now());
        } catch (Exception e) {
            body = "{\"error\":\"internal\"}";
        }
        return HttpResponse.ofCode(code)
            .withHeader(HttpHeaders.CONTENT_TYPE,
                        HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
            .withHeader(HttpHeaders.of("X-Content-Type-Options"), HttpHeaderValue.of("nosniff"))
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private HttpResponse rateLimitResponse(RateLimiter.AcquireResult rateLimitResult) {
        return HttpResponse.ofCode(429)
            .withHeader(HttpHeaders.CONTENT_TYPE,
                        HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
            .withHeader(HttpHeaders.of("Retry-After"),
                        HttpHeaderValue.of(String.valueOf(rateLimitResult.retryAfterSeconds())))
            .withHeader(HttpHeaders.of("X-RateLimit-Limit"),
                        HttpHeaderValue.of(String.valueOf(RATE_LIMIT_REQUESTS)))
            .withHeader(HttpHeaders.of("X-RateLimit-Remaining"),
                        HttpHeaderValue.of(String.valueOf(rateLimitResult.remainingTokens())))
            .withHeader(HttpHeaders.of("X-RateLimit-Reset"),
                        HttpHeaderValue.of(String.valueOf(rateLimitResult.resetAtEpochSeconds())))
            .withBody(("{\"error\":\"Too Many Requests\"," +
                       "\"code\":429," +
                       "\"retryAfter\":" + rateLimitResult.retryAfterSeconds() + "," +
                       "\"timestamp\":\"" + Instant.now() + "\"}").getBytes(StandardCharsets.UTF_8))
            .build();
    }
}
