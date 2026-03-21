/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.pipeline.registry.service.CapabilitiesService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for system capability introspection endpoints.
 *
 * <p>Extracted from {@code AepHttpServer} (Phase 7, P7-2c) to enforce
 * transport-only composition.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /admin/capabilities/schemas - Available schema formats</li>
 *   <li>GET /admin/capabilities/connectors - Available connectors</li>
 *   <li>GET /admin/capabilities/encodings - Available encodings</li>
 *   <li>GET /admin/capabilities/transforms - Available transforms</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose System capability endpoints
 * @doc.layer product
 * @doc.pattern Controller
 */
public class CapabilitiesController {

    private final CapabilitiesService capabilitiesService;
    private final java.util.function.Function<Map<String, Object>, HttpResponse> jsonResponse;

    public CapabilitiesController(CapabilitiesService capabilitiesService,
                                  java.util.function.Function<Map<String, Object>, HttpResponse> jsonResponse) {
        this.capabilitiesService = capabilitiesService;
        this.jsonResponse = jsonResponse;
    }

    public Promise<HttpResponse> handleSchemaCapabilities(HttpRequest request) {
        Map<String, Object> response = new HashMap<>(capabilitiesService.getSchemaFormats());
        response.put("timestamp", Instant.now().toString());
        return Promise.of(jsonResponse.apply(response));
    }

    public Promise<HttpResponse> handleConnectorCapabilities(HttpRequest request) {
        Map<String, Object> response = new HashMap<>(capabilitiesService.getConnectors());
        response.put("timestamp", Instant.now().toString());
        return Promise.of(jsonResponse.apply(response));
    }

    public Promise<HttpResponse> handleEncodingCapabilities(HttpRequest request) {
        Map<String, Object> response = new HashMap<>(capabilitiesService.getEncodings());
        response.put("timestamp", Instant.now().toString());
        return Promise.of(jsonResponse.apply(response));
    }

    public Promise<HttpResponse> handleTransformCapabilities(HttpRequest request) {
        Map<String, Object> response = new HashMap<>(capabilitiesService.getTransforms());
        response.put("timestamp", Instant.now().toString());
        return Promise.of(jsonResponse.apply(response));
    }
}
