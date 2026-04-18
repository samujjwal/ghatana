package com.ghatana.yappc.api;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Applies API version validation and version headers for YAPPC API endpoints
 * @doc.layer api
 * @doc.pattern Policy
 */
public final class ApiVersionPolicy {

    private static final String CURRENT_VERSION = "v1";
    private static final Set<String> SUPPORTED = Set.of("1", "v1");

    private static final io.activej.http.HttpHeader X_API_VERSION = HttpHeaders.of("X-API-Version");
    private static final io.activej.http.HttpHeader X_API_COMPATIBILITY = HttpHeaders.of("X-API-Compatibility");
    private static final io.activej.http.HttpHeader DEPRECATION = HttpHeaders.of("Deprecation");

    public AsyncServlet apply(AsyncServlet delegate) {
        return request -> {
            String requestedVersion = firstNonBlank(
                    request.getHeader(HttpHeaders.of("X-API-Version")),
                    request.getHeader(HttpHeaders.of("Accept-Version")));

            if (requestedVersion != null && !SUPPORTED.contains(normalize(requestedVersion))) {
                return Promise.of(HttpResponse.ofCode(406)
                                                .withJson("""
                            {
                              "error": "Unsupported API version",
                              "requestedVersion": "%s",
                              "supportedVersions": ["v1"],
                              "currentVersion": "v1"
                            }
                                                        """.formatted(requestedVersion))
                        .build());
            }

            return delegate.serve(request)
                    .map(this::applyHeaders);
        };
    }

    private HttpResponse applyHeaders(HttpResponse response) {
        return response
                .withHeader(X_API_VERSION, CURRENT_VERSION)
                .withHeader(X_API_COMPATIBILITY, CURRENT_VERSION)
                .withHeader(DEPRECATION, "false");
    }

    private String normalize(String version) {
        return version.trim().toLowerCase();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
