/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Backend - Persistence Module (shared HTTP utilities)
 */
package com.ghatana.yappc.api.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base HTTP response utilities shared across backend sub-modules (auth, deployment, etc.).
 *
 * <p>Provides standard JSON response formatting without depending on api-layer types.
 * The {@code api} module's {@code ApiResponse} extends this with domain-specific exception handling.
 *
 * @doc.type class
 * @doc.purpose Base HTTP response helpers for backend sub-modules
 * @doc.layer product
 * @doc.pattern Utility
 */
public class BaseApiResponse {

    private static final Logger logger = LoggerFactory.getLogger(BaseApiResponse.class);
    private static final String CONTENT_TYPE_JSON = "application/json";

    /** Standard error response structure. */
    public record ErrorResponse(String error, String message, String code, String path) {}

    private BaseApiResponse() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Creates 200 OK response with JSON body. */
    public static HttpResponse ok(Object body) {
        return HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
                .withBody(toJsonBytes(body))
                .build();
    }

    /** Creates 201 Created response with JSON body. */
    public static HttpResponse created(Object body) {
        return HttpResponse.ofCode(201)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
                .withBody(toJsonBytes(body))
                .build();
    }

    /** Creates 204 No Content response. */
    public static HttpResponse noContent() {
        return HttpResponse.ofCode(204).build();
    }

    /** Creates 400 Bad Request response. */
    public static HttpResponse badRequest(String message) {
        return error(400, "BAD_REQUEST", message, null);
    }

    /** Creates 401 Unauthorized response. */
    public static HttpResponse unauthorized(String message) {
        return error(401, "UNAUTHORIZED", message, null);
    }

    /** Creates 403 Forbidden response. */
    public static HttpResponse forbidden(String message) {
        return error(403, "FORBIDDEN", message, null);
    }

    /** Creates 404 Not Found response. */
    public static HttpResponse notFound(String message) {
        return error(404, "NOT_FOUND", message, null);
    }

    /** Creates 409 Conflict response. */
    public static HttpResponse conflict(String message) {
        return error(409, "CONFLICT", message, null);
    }

    /** Creates 500 Internal Server Error response. */
    public static HttpResponse serverError(String message) {
        return error(500, "INTERNAL_ERROR", message, null);
    }

    /** Creates error response with specified code. */
    public static HttpResponse error(int statusCode, String code, String message, String path) {
        ErrorResponse errorResponse = new ErrorResponse(getStatusText(statusCode), message, code, path);
        return HttpResponse.ofCode(statusCode)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
                .withBody(toJsonBytes(errorResponse))
                .build();
    }

    /** Converts exception to appropriate HTTP response. */
    public static HttpResponse fromException(Throwable e) {
        logger.error("API error: {}", e.getMessage(), e);
        if (e instanceof JsonUtils.BadRequestException) {
            return badRequest(e.getMessage());
        }
        if (e instanceof TenantContextExtractor.UnauthorizedException) {
            return unauthorized(e.getMessage());
        }
        if (e instanceof TenantContextExtractor.ForbiddenException) {
            return forbidden(e.getMessage());
        }
        if (e instanceof IllegalArgumentException) {
            return badRequest(e.getMessage());
        }
        return serverError("An unexpected error occurred");
    }

    /**
     * Wraps async operation with error handling.
     */
    public static Promise<HttpResponse> wrap(Promise<HttpResponse> promise) {
        return promise.then(response -> Promise.of(response), e -> Promise.of(fromException(e)));
    }

    /** Serializes an object to JSON bytes using the shared ObjectMapper. */
    public static byte[] toJsonBytes(Object value) {
        try {
            return JsonUtils.getDefaultMapper().writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JSON payload", e);
        }
    }

    private static String getStatusText(int code) {
        return switch (code) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 422 -> "Unprocessable Entity";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> "Error";
        };
    }
}
