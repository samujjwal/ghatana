package com.ghatana.pipeline.registry.web;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * HTTP filter for Cross-Origin Resource Sharing (CORS) support in ActiveJ HTTP servers.
 * <p>
 * This filter adds CORS headers to HTTP responses to allow cross-origin requests from
 * frontend applications running on different origins (e.g., Vite dev server on localhost:5173).
 * </p>
 *
 * @doc.type class
 * @doc.purpose Provides CORS headers for cross-origin HTTP requests
 * @doc.layer platform
 * @doc.pattern Filter
 */
public class CorsFilter {

    private static final Logger LOG = LoggerFactory.getLogger(CorsFilter.class);

    private final boolean enabled;
    private final String allowedOrigin;
    private final String allowedMethods;
    private final String allowedHeaders;
    private final String maxAge;

    private CorsFilter(Builder builder) {
        this.enabled = builder.enabled;
        this.allowedOrigin = builder.allowedOrigin;
        this.allowedMethods = builder.allowedMethods;
        this.allowedHeaders = builder.allowedHeaders;
        this.maxAge = builder.maxAge;
    }

    /**
     * Apply CORS headers to the response from the given handler.
     *
     * @param request the HTTP request
     * @param handler the next handler in the chain
     * @return a Promise of HttpResponse with CORS headers added
     */
    public Promise<HttpResponse> filter(HttpRequest request, Function<HttpRequest, Promise<HttpResponse>> handler) {
        if (!enabled) {
            return handler.apply(request);
        }

        // Special handling for preflight OPTIONS requests if they aren't handled by the routing servlet
        if (request.getMethod() == HttpMethod.OPTIONS) {
            return Promise.of(addCorsHeaders(HttpResponse.ok200().build(), request));
        }

        return handler.apply(request)
                .map(response -> addCorsHeaders(response, request));
    }

    /**
     * Add CORS headers to the given response.
     *
     * @param response the original response
     * @param request the original request to extract Origin from
     * @return a new response with CORS headers added
     */
    private HttpResponse addCorsHeaders(HttpResponse response, HttpRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        String activeOrigin = allowedOrigin;
        
        // If allowedOrigin is "*" but we have a specific Origin in request,
        // use that instead to support credentials: true
        if ("*".equals(allowedOrigin) && origin != null && !origin.isEmpty()) {
            activeOrigin = origin;
        }

        HttpResponse.Builder builder = HttpResponse.ofCode(response.getCode())
                .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, activeOrigin)
                .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, allowedMethods)
                .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, allowedHeaders)
                .withHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, maxAge)
                .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

        // Copy critical headers from the original response
        String location = response.getHeader(HttpHeaders.LOCATION);
        if (location != null) {
            builder.withHeader(HttpHeaders.LOCATION, location);
        }
        
        String contentType = response.getHeader(HttpHeaders.CONTENT_TYPE);
        if (contentType != null) {
            builder.withHeader(HttpHeaders.CONTENT_TYPE, contentType);
        }

        // Copy body if possible (without exhausting it if it's already used)
        try {
            if (response.getBody() != null) {
                builder.withBody(response.getBody());
            }
        } catch (Exception e) {
            // Body missing or already consumed - this is expected in some async flows
        }

        return builder.build();
    }

    /**
     * Create a new builder for CorsFilter.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for CorsFilter.
     */
    public static class Builder {
        private boolean enabled = true;
        private String allowedOrigin = "*";
        private String allowedMethods = "GET, POST, PUT, DELETE, OPTIONS, PATCH";
        private String allowedHeaders = "Content-Type, Authorization, X-API-Key, Idempotency-Key, X-Tenant-ID, X-User-ID, X-Session-ID";
        private String maxAge = "86400";

        /**
         * Enable or disable CORS filtering.
         *
         * @param enabled true to enable CORS, false to disable
         * @return this builder
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Set the allowed origin for CORS requests.
         *
         * @param allowedOrigin the allowed origin (e.g., "*" or "http://localhost:5173")
         * @return this builder
         */
        public Builder allowedOrigin(String allowedOrigin) {
            this.allowedOrigin = allowedOrigin;
            return this;
        }

        /**
         * Set the allowed HTTP methods for CORS requests.
         *
         * @param allowedMethods comma-separated list of HTTP methods
         * @return this builder
         */
        public Builder allowedMethods(String allowedMethods) {
            this.allowedMethods = allowedMethods;
            return this;
        }

        /**
         * Set the allowed headers for CORS requests.
         *
         * @param allowedHeaders comma-separated list of headers
         * @return this builder
         */
        public Builder allowedHeaders(String allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
            return this;
        }

        /**
         * Set the max age for CORS preflight cache.
         *
         * @param maxAge max age in seconds
         * @return this builder
         */
        public Builder maxAge(String maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        /**
         * Build the CorsFilter instance.
         *
         * @return a new CorsFilter instance
         */
        public CorsFilter build() {
            LOG.info("Building CorsFilter with enabled={}, allowedOrigin={}", enabled, allowedOrigin);
            return new CorsFilter(this);
        }
    }
}

