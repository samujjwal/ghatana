package com.ghatana.aep.engine.controller;

import io.activej.http.HttpResponse;
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

    public HttpResponse jsonResponse(int status, Object data) {
        // Stub implementation
        return HttpResponse.ok200().build();
    }

    public HttpResponse errorResponse(int status, String message) {
        // Stub implementation
        return HttpResponse.ofCode(status).build();
    }

    public Map<String, Object> toJson(Object obj) {
        return Map.of();
    }
}
