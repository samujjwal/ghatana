package com.ghatana.appplatform.gateway.transform;

import com.ghatana.platform.http.server.filter.FilterChain;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP filter that applies a configurable chain of header transformations to
 * both the inbound request and the outbound response.
 *
 * <h2>Supported transformations</h2>
 * <ul>
 *   <li><b>Header rename</b> (request): copy a header value to a new name and remove the old one.</li>
 *   <li><b>Header set</b> (request/response): unconditionally set a header to a fixed value.</li>
 *   <li><b>Header remove</b> (request/response): strip a header from the message.</li>
 *   <li><b>Header default</b> (request): set a header only when the header is absent.</li>
 *   <li><b>Field masking</b> (response): redact a named response header value (replaces with {@code ***}).</li>
 * </ul>
 *
 * <p>Build an instance with {@link Builder}:
 * <pre>{@code
 * RequestResponseTransformer transformer = RequestResponseTransformer.builder()
 *     .renameRequestHeader("X-Legacy-User", "X-User-Id")
 *     .removeResponseHeader("X-Internal-Trace")
 *     .maskResponseHeader("X-Api-Key")
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Pluggable request/response header transformer for the finance API gateway (K11-003)
 * @doc.layer product
 * @doc.pattern Filter
 */
public final class RequestResponseTransformer implements FilterChain.Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseTransformer.class);

    /** A single transformation step applied to a mutable header map. */
    @FunctionalInterface
    public interface HeaderTransform {
        /**
         * Applies this transformation to the header map of a message.
         *
         * @param headers mutable map of header name → value (lower-cased names)
         */
        void apply(Map<String, String> headers);
    }

    private final List<HeaderTransform> requestTransforms;
    private final List<HeaderTransform> responseTransforms;

    private RequestResponseTransformer(List<HeaderTransform> requestTransforms,
                                       List<HeaderTransform> responseTransforms) {
        this.requestTransforms = List.copyOf(requestTransforms);
        this.responseTransforms = List.copyOf(responseTransforms);
    }

    @Override
    public Promise<HttpResponse> apply(HttpRequest request, io.activej.http.AsyncServlet next)
            throws Exception {
        // Apply request header transforms (rebuild via mutation of collected headers)
        applyRequestTransforms(request);
        return next.serve(request)
                .map(response -> {
                    applyResponseTransforms(response);
                    return response;
                });
    }

    private void applyRequestTransforms(HttpRequest request) {
        if (requestTransforms.isEmpty()) return;
        // Collect mutable snapshot; ActiveJ HttpRequest headers are live
        var headers = new java.util.LinkedHashMap<String, String>();
        request.getHeaders().forEach(header ->
                headers.put(header.getKey().toLowerCase(), header.getValue()));

        requestTransforms.forEach(t -> t.apply(headers));

        // Re-apply back to request: remove old, set new values
        headers.forEach((name, value) ->
                request.addHeader(HttpHeaders.of(name), value));
    }

    private void applyResponseTransforms(HttpResponse response) {
        if (responseTransforms.isEmpty()) return;
        var headers = new java.util.LinkedHashMap<String, String>();
        response.getHeaders().forEach(header ->
                headers.put(header.getKey().toLowerCase(), header.getValue()));

        responseTransforms.forEach(t -> t.apply(headers));

        headers.forEach((name, value) ->
                response.addHeader(HttpHeaders.of(name), value));
    }

    /** Returns a new {@link Builder}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link RequestResponseTransformer}.
     *
     * @doc.type class
     * @doc.purpose Builder for constructing RequestResponseTransformer instances
     * @doc.layer product
     * @doc.pattern Builder
     */
    public static final class Builder {
        private final List<HeaderTransform> req  = new ArrayList<>();
        private final List<HeaderTransform> resp = new ArrayList<>();

        private Builder() {}

        /**
         * Renames a request header: copies the value to {@code newName} and removes {@code oldName}.
         *
         * @param oldName source header name (case-insensitive)
         * @param newName target header name
         */
        public Builder renameRequestHeader(String oldName, String newName) {
            String from = oldName.toLowerCase();
            String to   = newName.toLowerCase();
            req.add(headers -> {
                String val = headers.remove(from);
                if (val != null) {
                    headers.put(to, val);
                    log.debug("Request header renamed: {} → {}", from, to);
                }
            });
            return this;
        }

        /**
         * Sets a fixed header value on every request, overwriting any existing value.
         *
         * @param name  header name (case-insensitive)
         * @param value header value
         */
        public Builder setRequestHeader(String name, String value) {
            String key = name.toLowerCase();
            req.add(headers -> headers.put(key, value));
            return this;
        }

        /**
         * Sets a header on every request only when the header is absent.
         *
         * @param name         header name (case-insensitive)
         * @param defaultValue value to use when absent
         */
        public Builder defaultRequestHeader(String name, String defaultValue) {
            String key = name.toLowerCase();
            req.add(headers -> headers.putIfAbsent(key, defaultValue));
            return this;
        }

        /**
         * Removes a header from every request.
         *
         * @param name header name (case-insensitive)
         */
        public Builder removeRequestHeader(String name) {
            String key = name.toLowerCase();
            req.add(headers -> headers.remove(key));
            return this;
        }

        /**
         * Sets a fixed header value on every response, overwriting any existing value.
         *
         * @param name  header name (case-insensitive)
         * @param value header value
         */
        public Builder setResponseHeader(String name, String value) {
            String key = name.toLowerCase();
            resp.add(headers -> headers.put(key, value));
            return this;
        }

        /**
         * Removes a header from every response.
         *
         * @param name header name (case-insensitive)
         */
        public Builder removeResponseHeader(String name) {
            String key = name.toLowerCase();
            resp.add(headers -> headers.remove(key));
            return this;
        }

        /**
         * Masks (redacts) a response header value by replacing it with {@code ***}.
         * Does nothing if the header is absent.
         *
         * @param name header name (case-insensitive)
         */
        public Builder maskResponseHeader(String name) {
            String key = name.toLowerCase();
            resp.add(headers -> {
                if (headers.containsKey(key)) {
                    headers.put(key, "***");
                    log.debug("Response header masked: {}", key);
                }
            });
            return this;
        }

        /**
         * Builds the {@link RequestResponseTransformer}.
         *
         * @return immutable transformer instance
         */
        public RequestResponseTransformer build() {
            return new RequestResponseTransformer(req, resp);
        }
    }
}
