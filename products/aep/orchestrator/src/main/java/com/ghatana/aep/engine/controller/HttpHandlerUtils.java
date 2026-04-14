package com.ghatana.aep.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP utility functions for AEP controllers.
 *
 * @doc.type class
 * @doc.purpose HTTP response and parsing utilities
 * @doc.layer product
 * @doc.pattern Utility
 */
public class HttpHandlerUtils {

    private static final Logger log = LoggerFactory.getLogger(HttpHandlerUtils.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpHeaderValue CONTENT_TYPE_JSON =
        HttpHeaderValue.of("application/json; charset=utf-8");

    public HttpResponse jsonResponse(int status, Object data) {
        try {
            byte[] body = MAPPER.writeValueAsBytes(data);
            return HttpResponse.ofCode(status)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
                .withBody(body)
                .build();
        } catch (Exception e) {
            log.error("Failed to serialize JSON response for status={}", status, e);
            return HttpResponse.ofCode(500)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
                .withBody("{\"error\":\"internal_serialization_error\"}".getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }

    public HttpResponse errorResponse(int status, String message) {
        return jsonResponse(status, Map.of("error", message));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> toJson(Object obj) {
        try {
            return MAPPER.convertValue(obj, Map.class);
        } catch (Exception e) {
            log.warn("toJson conversion failed for {}", obj != null ? obj.getClass().getSimpleName() : "null", e);
            return Map.of();
        }
    }
}

