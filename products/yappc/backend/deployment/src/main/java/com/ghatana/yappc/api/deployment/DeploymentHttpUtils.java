/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * HTTP utilities for the deployment module — minimal copy of api.common helpers to avoid circular dependency.
 *
 * @doc.type class
 * @doc.purpose Deployment-local HTTP response and JSON parsing helpers
 * @doc.layer product
 * @doc.pattern Utility
 */
final class DeploymentHttpUtils {

    private static final Logger log = LoggerFactory.getLogger(DeploymentHttpUtils.class);
    private static final String CONTENT_TYPE_JSON = "application/json";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private DeploymentHttpUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Parse JSON request body into the given type. */
    static <T> Promise<T> parseBody(HttpRequest request, Class<T> type) {
        return request.loadBody().then(body -> {
            String json = body != null ? body.getString(StandardCharsets.UTF_8) : null;
            if (json == null || json.isBlank()) {
                return Promise.ofException(new BadRequestException("Request body is required"));
            }
            try {
                return Promise.of(MAPPER.readValue(json, type));
            } catch (JsonProcessingException e) {
                return Promise.ofException(new BadRequestException("Invalid JSON: " + e.getMessage()));
            }
        });
    }

    /** Serialize an object to JSON bytes. */
    static byte[] toJsonBytes(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize response", e);
        }
    }

    /** Create a 200 OK JSON response. */
    static HttpResponse ok(Object body) {
        return HttpResponse.ok200()
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
                .withBody(toJsonBytes(body))
                .build();
    }

    /** Create a 500 error JSON response from an exception. */
    static HttpResponse serverError(Throwable e) {
        log.error("Deployment API error", e);
        record ErrorBody(String error, String message) {}
        return HttpResponse.ofCode(500)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
                .withBody(toJsonBytes(new ErrorBody("INTERNAL_ERROR", e.getMessage())))
                .build();
    }

    /** Simple bad request exception. */
    static final class BadRequestException extends RuntimeException {
        BadRequestException(String message) {
            super(message);
        }
    }
}
