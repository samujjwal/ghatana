package com.ghatana.platform.http.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Base class for JSON-based HTTP servlets using ActiveJ.
 * 
 * Provides common functionality for parsing JSON requests and returning JSON responses.
 *
 * @doc.type class
 * @doc.purpose Base class for JSON-based HTTP servlets using ActiveJ
 * @doc.layer platform
 * @doc.pattern Service
 */
public abstract class JsonServlet {
    
    private static final Logger log = LoggerFactory.getLogger(JsonServlet.class);
    
    protected static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();
    
    protected static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";
    
    /**
     * Parse the request body as JSON, returning a Promise for non-blocking usage.
     * <p>Preferred over {@link #parseBody} — chain with {@code .then()} / {@code .map()}.
     *
     * @param request the HTTP request
     * @param type    the target type
     * @return a Promise that resolves to the parsed body
     */
    protected <T> Promise<T> parseBodyAsync(@NotNull HttpRequest request, @NotNull Class<T> type) {
        return request.loadBody()
                .map(body -> {
                    try {
                        return MAPPER.readValue(body.getString(StandardCharsets.UTF_8), type);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse request body", e);
                    }
                });
    }

    /**
     * Parse the request body as JSON (synchronous).
     * <p>Uses the already-loaded body buffer when available (e.g. from ActiveJ's
     * body-loading middleware). For fully non-blocking parsing, prefer
     * {@link #parseBodyAsync}.
     *
     * @param request the HTTP request
     * @param type    the target type
     * @return the deserialized body
     * @throws Exception if parsing fails or the body is not yet loaded
     */
    protected <T> T parseBody(@NotNull HttpRequest request, @NotNull Class<T> type) throws Exception {
        byte[] bodyBytes = request.getBody() != null ? request.getBody().asArray() : new byte[0];
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        return MAPPER.readValue(body, type);
    }
    
    /**
     * Create a JSON response with the given status code and body.
     */
    protected HttpResponse jsonResponse(int statusCode, @NotNull Object body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            return HttpResponse.ofCode(statusCode)
                    .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
                    .withBody(json.getBytes(StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            log.error("Failed to serialize response", e);
            return HttpResponse.ofCode(500)
                    .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
                    .withBody("{\"success\":false,\"error\":{\"code\":\"SERIALIZATION_ERROR\"}}".getBytes(StandardCharsets.UTF_8))
                    .build();
        }
    }
    
    /**
     * Create a successful JSON response (200 OK).
     */
    protected HttpResponse ok(@NotNull Object body) {
        return jsonResponse(200, com.ghatana.platform.http.server.HttpResponse.ok(body));
    }
    
    /**
     * Create a created JSON response (201 Created).
     */
    protected HttpResponse created(@NotNull Object body) {
        return jsonResponse(201, com.ghatana.platform.http.server.HttpResponse.ok(body));
    }
    
    /**
     * Create a no content response (204 No Content).
     */
    protected HttpResponse noContent() {
        return HttpResponse.ofCode(204).build();
    }
    
    /**
     * Create a bad request error response (400).
     */
    protected HttpResponse badRequest(@NotNull String message) {
        return jsonResponse(400, com.ghatana.platform.http.server.HttpResponse.badRequest(message));
    }
    
    /**
     * Create an unauthorized error response (401).
     */
    protected HttpResponse unauthorized(@NotNull String message) {
        return jsonResponse(401, com.ghatana.platform.http.server.HttpResponse.unauthorized(message));
    }
    
    /**
     * Create a forbidden error response (403).
     */
    protected HttpResponse forbidden(@NotNull String message) {
        return jsonResponse(403, com.ghatana.platform.http.server.HttpResponse.forbidden(message));
    }
    
    /**
     * Create a not found error response (404).
     */
    protected HttpResponse notFound(@NotNull String message) {
        return jsonResponse(404, com.ghatana.platform.http.server.HttpResponse.notFound(message));
    }
    
    /**
     * Create an internal server error response (500).
     */
    protected HttpResponse internalError(@NotNull String message) {
        return jsonResponse(500, com.ghatana.platform.http.server.HttpResponse.internalError(message));
    }
    
    /**
     * Create an internal server error response from an exception.
     */
    protected HttpResponse internalError(@NotNull Throwable throwable) {
        log.error("Internal server error", throwable);
        return jsonResponse(500, com.ghatana.platform.http.server.HttpResponse.error(throwable));
    }
    
    /**
     * Wrap a response in a Promise for async handling.
     */
    protected Promise<HttpResponse> promiseOf(@NotNull HttpResponse response) {
        return Promise.of(response);
    }
}
