/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

/**
 * Base interface for AEP HTTP controllers.
 *
 * <p>Controllers handle specific domain concerns and are wired into
 * the router by {@link com.ghatana.aep.server.http.AepHttpServer}.
 *
 * @doc.type interface
 * @doc.purpose Define contract for HTTP controllers
 * @doc.layer product
 */
public interface AepController {

    /**
     * Returns the base path for this controller's routes.
     * Example: "/api/v1/pipelines"
     */
    String getBasePath();

    /**
     * Handles a request to this controller.
     *
     * @param request the HTTP request
     * @param path the path relative to base path
     * @return promise of HTTP response
     */
    Promise<HttpResponse> handle(HttpRequest request, String path) throws Exception;
}
