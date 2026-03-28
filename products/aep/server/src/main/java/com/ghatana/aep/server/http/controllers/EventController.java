/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.server.http.HttpHelper;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Controller for event processing endpoints.
 * Handles single event and batch event processing.
 *
 * @doc.type class
 * @doc.purpose HTTP controller for event ingestion and processing endpoints
 * @doc.layer product
 * @doc.pattern Controller
 */
public class EventController {
    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    public Promise<HttpResponse> handleProcessEvent(HttpRequest request) {
        log.debug("Processing single event");
        return Promise.of(HttpHelper.jsonResponse(Map.of("status", "accepted")));
    }

    public Promise<HttpResponse> handleProcessBatch(HttpRequest request) {
        log.debug("Processing batch events");
        return Promise.of(HttpHelper.jsonResponse(Map.of("status", "accepted")));
    }

    /**
     * Route dispatcher for controller methods
     */
    public Promise<HttpResponse> handle(HttpRequest request, String path) {
        String method = request.getMethod().toString();

        if ("POST".equals(method) && path.equals("/api/v1/events")) {
            return handleProcessEvent(request);
        }
        if ("POST".equals(method) && path.equals("/api/v1/events/batch")) {
            return handleProcessBatch(request);
        }

        return Promise.of(HttpHelper.errorResponse(404, "Not found"));
    }
}
