package com.ghatana.platform.http.security.filter;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * HTTP filter for enforcing maximum request body size limit.
 *
 * <p><b>Purpose</b><br>
 * Protects against DDoS attacks and memory exhaustion by rejecting requests
 * with bodies exceeding a configured size limit. Returns 413 Payload Too Large
 * response for oversized requests.
 *
 * @doc.type class
 * @doc.purpose HTTP request body size limit enforcement filter
 * @doc.layer core
 * @doc.pattern Filter, Security
 */
public final class RequestSizeLimitFilter implements com.ghatana.platform.http.server.filter.FilterChain.Filter {

    private static final Logger logger = LoggerFactory.getLogger(RequestSizeLimitFilter.class);

    private final long maxBodySizeBytes;

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

    public long getMaxBodySize() {
        return maxBodySizeBytes;
    }
}
