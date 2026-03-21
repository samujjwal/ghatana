/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
    private static final long RATE_LIMIT_WINDOW_MS = 60_000L;
    private static final int MAX_TRACKED_IPS = 10_000;

    // ── Security header values (constants to avoid allocation per request) ──────
    private static final String HSTS_VALUE =
            "max-age=31536000; includeSubDomains; preload";
    private static final String CSP_VALUE =
            "default-src 'none'; frame-ancestors 'none'; form-action 'none'";
    private static final String PERMISSIONS_VALUE =
            "camera=(), microphone=(), geolocation=(), payment=()";
    private static final String REFERRER_VALUE =
            "strict-origin-when-cross-origin";

    // ── CORS ────────────────────────────────────────────────────────────────────
    private final String allowedOrigins;

    // ── Downstream ──────────────────────────────────────────────────────────────
    private final AsyncServlet next;

    // ── Rate-limit state (event-loop thread) ────────────────────────────────────
    private final ConcurrentHashMap<String, long[]> rateLimitMap = new ConcurrentHashMap<>();

    /**
     * Constructs a security filter with the given CORS policy.
     *
     * @param next           downstream servlet (router)
     * @param allowedOrigins comma-separated list of allowed CORS origins, or {@code "*"}
     */
    public AepSecurityFilter(AsyncServlet next, String allowedOrigins) {
        this.next = next;
        this.allowedOrigins = allowedOrigins != null ? allowedOrigins : "*";
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
            bodyLoad = request.loadBody((int) MAX_REQUEST_BODY_BYTES + 1)
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
                    if (!isRateLimitAllowed(clientIp)) {
                        log.warn("Rate limit exceeded for ip={}", clientIp);
                        return Promise.of(rateLimitResponse());
                    }

                    // ── 4. Delegate to router with security headers applied ──
                    return next.serve(request).map(this::addSecurityHeaders);
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
    private HttpResponse addSecurityHeaders(HttpResponse response) {
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

        // Inject OWASP-recommended security headers
        builder
            .withHeader(HttpHeaders.of("X-Content-Type-Options"),       HttpHeaderValue.of("nosniff"))
            .withHeader(HttpHeaders.of("X-Frame-Options"),              HttpHeaderValue.of("DENY"))
            .withHeader(HttpHeaders.of("X-XSS-Protection"),            HttpHeaderValue.of("0"))
            .withHeader(HttpHeaders.of("Content-Security-Policy"),     HttpHeaderValue.of(CSP_VALUE))
            .withHeader(HttpHeaders.of("Referrer-Policy"),             HttpHeaderValue.of(REFERRER_VALUE))
            .withHeader(HttpHeaders.of("Permissions-Policy"),          HttpHeaderValue.of(PERMISSIONS_VALUE))
            .withHeader(HttpHeaders.of("Strict-Transport-Security"),   HttpHeaderValue.of(HSTS_VALUE))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(allowedOrigins))
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
        return HttpResponse.ofCode(204)
            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"),
                        HttpHeaderValue.of(allowedOrigins))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"),
                        HttpHeaderValue.of("GET, POST, PUT, DELETE, OPTIONS"))
            .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"),
                        HttpHeaderValue.of(requestedHeaders != null
                            ? requestedHeaders
                            : "Content-Type, Authorization, X-Tenant-Id"))
            .withHeader(HttpHeaders.of("Access-Control-Max-Age"),
                        HttpHeaderValue.of("86400"))
            .build();
    }

    // =========================================================================
    // Rate limiting (fixed-window, per-IP)
    // =========================================================================

    private boolean isRateLimitAllowed(String ip) {
        long now = System.currentTimeMillis();

        // Evict oldest entries if we're at capacity to prevent unbounded memory growth
        if (rateLimitMap.size() > MAX_TRACKED_IPS) {
            rateLimitMap.entrySet().stream()
                .filter(e -> now - e.getValue()[1] > RATE_LIMIT_WINDOW_MS)
                .map(Map.Entry::getKey)
                .limit(MAX_TRACKED_IPS / 10L)
                .forEach(rateLimitMap::remove);
        }

        long[] window = rateLimitMap.computeIfAbsent(ip, k -> new long[]{0L, now});
        // window[0] = request count in current window
        // window[1] = window start timestamp

        if (now - window[1] >= RATE_LIMIT_WINDOW_MS) {
            // New window — reset
            window[0] = 1;
            window[1] = now;
            return true;
        }

        window[0]++;
        return window[0] <= RATE_LIMIT_REQUESTS;
    }

    // =========================================================================
    // Client IP resolution
    // =========================================================================

    private String clientIp(HttpRequest request) {
        // Honour X-Forwarded-For when running behind a proxy / ingress
        String xff = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (xff != null) {
            // Take the first (leftmost) address — that is the real client IP
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return "unknown";
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

    private HttpResponse rateLimitResponse() {
        return HttpResponse.ofCode(429)
            .withHeader(HttpHeaders.CONTENT_TYPE,
                        HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
            .withHeader(HttpHeaders.of("Retry-After"), HttpHeaderValue.of("60"))
            .withHeader(HttpHeaders.of("X-RateLimit-Limit"),
                        HttpHeaderValue.of(String.valueOf(RATE_LIMIT_REQUESTS)))
            .withBody(("{\"error\":\"Too Many Requests\"," +
                       "\"code\":429," +
                       "\"retryAfter\":60," +
                       "\"timestamp\":\"" + Instant.now() + "\"}").getBytes(StandardCharsets.UTF_8))
            .build();
    }
}
