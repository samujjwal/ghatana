package com.ghatana.appplatform.gateway.transform;

import com.ghatana.platform.http.server.filter.FilterChain;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP filter that rejects requests whose body exceeds a configurable size limit.
 *
 * <h2>Enforcement strategy</h2>
 * <ol>
 *   <li>Inspect the {@code Content-Length} header (fast path). If the declared size
 *       exceeds the limit, return {@code 413 Content Too Large} immediately without
 *       reading the body.</li>
 *   <li>When {@code Content-Length} is absent (chunked or no body), the body is not
 *       buffered eagerly by this filter; the limit is enforced on the declared header
 *       only. Deeper body streaming limits are expected to be configured at the
 *       ActiveJ server or platform layer.</li>
 * </ol>
 *
 * <p>Default limit: {@value #DEFAULT_MAX_BYTES} bytes (1 MiB).
 *
 * @doc.type class
 * @doc.purpose Enforces maximum request body size, returning 413 when exceeded (K11-011)
 * @doc.layer product
 * @doc.pattern Filter
 */
public final class RequestBodySizeFilter implements FilterChain.Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestBodySizeFilter.class);

    /** Default maximum body size: 1 MiB. */
    public static final long DEFAULT_MAX_BYTES = 1_048_576L;

    private static final HttpHeaders.Value CONTENT_LENGTH = HttpHeaders.of("Content-Length");
    private static final String BODY_TOO_LARGE = "Request body too large. Maximum allowed size is %d bytes.";

    private final long maxBytes;

    /**
     * Creates a filter with the default limit of {@value #DEFAULT_MAX_BYTES} bytes (1 MiB).
     */
    public RequestBodySizeFilter() {
        this(DEFAULT_MAX_BYTES);
    }

    /**
     * Creates a filter with a custom maximum body size.
     *
     * @param maxBytes maximum allowed body size in bytes; must be &gt; 0
     * @throws IllegalArgumentException if {@code maxBytes} is not positive
     */
    public RequestBodySizeFilter(long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive, got: " + maxBytes);
        }
        this.maxBytes = maxBytes;
    }

    @Override
    public Promise<HttpResponse> apply(HttpRequest request, io.activej.http.AsyncServlet next)
            throws Exception {

        String contentLengthHeader = request.getHeader(CONTENT_LENGTH);
        if (contentLengthHeader != null && !contentLengthHeader.isBlank()) {
            long declaredSize;
            try {
                declaredSize = Long.parseLong(contentLengthHeader.strip());
            } catch (NumberFormatException e) {
                log.debug("Malformed Content-Length header: '{}' — rejecting with 400", contentLengthHeader);
                return Promise.of(HttpResponse.ofCode(400)
                        .withPlainText("Malformed Content-Length header."));
            }

            if (declaredSize > maxBytes) {
                log.info("Request body too large: declared={}B limit={}B path={}",
                        declaredSize, maxBytes, request.getPath());
                return Promise.of(HttpResponse.ofCode(413)
                        .withHeader(HttpHeaders.of("Content-Type"), "text/plain; charset=utf-8")
                        .withPlainText(String.format(BODY_TOO_LARGE, maxBytes)));
            }
        }

        return next.serve(request);
    }

    /**
     * Returns the configured maximum body size in bytes.
     *
     * @return maximum allowed body size
     */
    public long getMaxBytes() {
        return maxBytes;
    }
}
