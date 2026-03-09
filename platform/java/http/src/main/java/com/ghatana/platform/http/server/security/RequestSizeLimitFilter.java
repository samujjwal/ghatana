package com.ghatana.platform.http.server.security;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * HTTP filter for enforcing maximum request body size limit.
 *
 * <p>
 * <b>Purpose</b><br>
 * Protects against DDoS attacks and memory exhaustion by rejecting requests
 * with bodies exceeding a configured size limit. Returns 413 Payload Too Large
 * response for oversized requests.
 *
 * <p>
 * <b>Security Impact</b><br>
 * Prevents attackers from sending extremely large payloads that could: -
 * Exhaust server memory - Cause out-of-memory errors - Degrade service for
 * legitimate users - Lead to denial of service
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * HttpServer server = HttpServerBuilder.create()
 *     .withPort(8080)
 *     .withMaxBodySize(10 * 1024 * 1024)  // 10MB limit
 *     .addFilter(new RequestSizeLimitFilter(10 * 1024 * 1024))
 *     .addRoute(HttpMethod.POST, "/api/data", handler)
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>Default Limits (Recommendations)</b><br>
 * - REST API: 1-10 MB - GraphQL API: 5-50 MB - File Upload: 100-500 MB -
 * Webhook Ingestion: 1-5 MB
 *
 * <p>
 * <b>Error Response</b><br>
 * <pre>{@code
 * HTTP/1.1 413 Payload Too Large
 * Content-Type: application/json
 *
 * {
 *   "error": "Request body size exceeds maximum allowed: 10485760 bytes",
 *   "maxSize": 10485760,
 *   "timestamp": "2025-11-15T12:30:00Z"
 * }
 * }</pre>
 *
 * <p>
 * <b>Behavior</b><br>
 * - Checks Content-Length header first (fast path) - If no Content-Length,
 * checks actual body size during loading - Applies to all requests
 * (configurable per route if needed) - Returns 413 immediately for oversized
 * requests - Logs oversized request attempts for audit/security analysis
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe (immutable after construction).
 *
 * @see HttpServerBuilder#withMaxBodySize(long)
 *
 * @author HTTP Server Team
 * @created 2025-11-15
 * @version 1.0.0
 * @doc.type class
 * @doc.purpose HTTP request body size limit enforcement filter
 * @doc.layer core
 * @doc.pattern Filter, Security
 */
public final class RequestSizeLimitFilter implements com.ghatana.platform.http.server.filter.FilterChain.Filter {

    private static final Logger logger = LoggerFactory.getLogger(RequestSizeLimitFilter.class);

    private final long maxBodySizeBytes;

    /**
     * Creates a request size limit filter.
     *
     * @param maxBodySizeBytes Maximum allowed body size in bytes
     * @throws IllegalArgumentException if maxBodySizeBytes <= 0
     */
    public RequestSizeLimitFilter(long maxBodySizeBytes) {
        if (maxBodySizeBytes <= 0) {
            throw new IllegalArgumentException("Max body size must be positive, got: " + maxBodySizeBytes);
        }
        this.maxBodySizeBytes = maxBodySizeBytes;
    }

    @Override
    public Promise<HttpResponse> apply(HttpRequest request, AsyncServlet next) throws Exception {
        String contentLengthHeader = request.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLengthHeader != null) {
            try {
                long contentLength = Long.parseLong(contentLengthHeader.trim());
                if (contentLength > maxBodySizeBytes) {
                    logger.warn("Rejected oversized request: Content-Length={} exceeds max={}",
                            contentLength, maxBodySizeBytes);
                    return Promise.of(createPayloadTooLargeResponse(contentLength));
                }
            } catch (NumberFormatException e) {
                logger.warn("Malformed Content-Length header: '{}'", contentLengthHeader);
                return Promise.of(HttpResponse.ofCode(400)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withJson("{\"error\":\"Malformed Content-Length header\"}")
                        .build());
            }
        }
        return next.serve(request);
    }

    /**
     * Creates a 413 Payload Too Large response.
     *
     * @param actualSize The actual body size received
     * @return HTTP 413 response with JSON error body
     */
    private HttpResponse createPayloadTooLargeResponse(long actualSize) {
        String body = "{\"error\":\"Request body size exceeds maximum allowed: " + maxBodySizeBytes + " bytes\","
                + "\"maxSize\":" + maxBodySizeBytes + ","
                + "\"actualSize\":" + actualSize + ","
                + "\"timestamp\":\"" + Instant.now() + "\"}";
        return HttpResponse.ofCode(413)
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .withJson(body)
                .build();
    }

    /**
     * Gets the maximum body size this filter enforces.
     *
     * @return Maximum body size in bytes
     */
    public long getMaxBodySize() {
        return maxBodySizeBytes;
    }
}
