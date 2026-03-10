/**
 * Copyright (c) 2025–2026 Ghatana Technologies
 * YAPPC API - Platform Compatibility Layer
 *
 * Provides compatibility methods for working with the ActiveJ framework
 * and platform-specific HTTP response handling.
 */

package com.ghatana.yappc.api.security;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.MediaTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;

/**
 * Compatibility layer for platform-specific HTTP operations.
 *
 * <p>This class bridges the security middleware and the underlying ActiveJ
 * HTTP framework, providing factory methods for common HTTP response patterns
 * and request-context extraction.
 *
 * @doc.type class
 * @doc.purpose ActiveJ HTTP compatibility utilities
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class PlatformCompatibility {

    private PlatformCompatibility() {
        // Utility class – prevent instantiation
    }

    /**
     * Creates an HTTP response with the given status code and plain-text body.
     *
     * @param statusCode HTTP status code
     * @param body       response body text
     * @return a fully-constructed {@link HttpResponse}
     */
    @NotNull
    public static HttpResponse createResponse(int statusCode, @NotNull String body) {
        return HttpResponse.ofCode(statusCode)
                .withBody(body.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    /**
     * Creates an HTTP response with {@code application/json} content type.
     *
     * @param statusCode HTTP status code
     * @param jsonBody   JSON response body
     * @return a fully-constructed {@link HttpResponse} with JSON content type
     */
    @NotNull
    public static HttpResponse createJsonResponse(int statusCode, @NotNull String jsonBody) {
        return HttpResponse.ofCode(statusCode)
                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE,
                        io.activej.http.HttpHeaderValue.ofContentType(
                                io.activej.http.ContentType.of(MediaTypes.JSON)))
                .withBody(jsonBody.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    /**
     * Creates an HTTP response with status code only (empty body).
     *
     * @param statusCode HTTP status code
     * @return a fully-constructed {@link HttpResponse}
     */
    @NotNull
    public static HttpResponse createStatusResponse(int statusCode) {
        return HttpResponse.ofCode(statusCode).build();
    }

    /**
     * Extracts an attached object from an ActiveJ {@link HttpRequest}.
     *
     * <p>Objects are attached to requests via {@link HttpRequest#attach(Object)}
     * during the authentication phase and subsequently retrieved in the
     * authorization phase.
     *
     * @param request the HTTP request (must be an {@link HttpRequest})
     * @param type    the expected type of the attached object
     * @param <T>     the attached object type
     * @return the attached object, or {@code null} if nothing is attached
     *         or the request is not an {@link HttpRequest}
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T getAttached(@NotNull Object request, @NotNull Class<T> type) {
        if (request instanceof HttpRequest httpRequest) {
            Object attached = httpRequest.getAttachment(type);
            if (type.isInstance(attached)) {
                return (T) attached;
            }
        }
        return null;
    }
}
