package com.ghatana.platform.observability.http.handlers;

import java.time.Instant;

/**
 * Minimal HTTP error response value object for the observability HTTP layer.
 *
 * <p>Defined locally to avoid a circular dependency with {@code platform.http}:
 * the observability HTTP handlers need to produce error JSON, but the {@code http}
 * module already depends on {@code observability} for {@code MetricsCollector}.
 * Keeping this type inside {@code observability.http.handlers} breaks that cycle.
 *
 * @doc.type record
 * @doc.purpose Encapsulates HTTP error response fields for observability endpoints
 * @doc.layer core
 * @doc.pattern ValueObject
 */
record ObsHttpError(int status, String code, String message, String path, String timestamp) {

    /** Creates a 400 Bad Request error. */
    static ObsHttpError badRequest(String message, String path) {
        return new ObsHttpError(400, "BAD_REQUEST", message, path, Instant.now().toString());
    }

    /** Creates a 404 Not Found error. */
    static ObsHttpError notFound(String message, String path) {
        return new ObsHttpError(404, "NOT_FOUND", message, path, Instant.now().toString());
    }

    /** Creates a 500 Internal Server Error. */
    static ObsHttpError internalError(String message, String path) {
        return new ObsHttpError(500, "INTERNAL_SERVER_ERROR", message, path, Instant.now().toString());
    }
}
