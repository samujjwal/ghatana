/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.ingress.api;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for {@link HealthController}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>{@code GET /health/live} returns HTTP 200 with {@code {"status":"UP"}}</li>
 *   <li>{@code GET /health/ready} returns HTTP 200 with {@code {"status":"READY"}}</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Smoke tests for AEP health endpoints
 * @doc.layer product-aep
 * @doc.pattern Smoke Test
 */
@DisplayName("HealthController smoke tests")
class HealthControllerTest extends EventloopTestBase {

    private io.activej.http.RoutingServlet servlet;

    @BeforeEach
    void setUp() {
        HealthController controller = new HealthController(eventloop());
        servlet = controller.servlet();
    }

    @Test
    @DisplayName("GET /health/live returns 200 UP")
    void liveness_returns200Up() {
        HttpRequest request = HttpRequest.get("http://localhost/health/live").build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        String body = runPromise(() -> response.loadBody()
                .map($ -> new String(response.getBody().asArray())));
        assertThat(body).contains("UP");
    }

    @Test
    @DisplayName("GET /health/ready returns 200 READY")
    void readiness_returns200Ready() {
        HttpRequest request = HttpRequest.get("http://localhost/health/ready").build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        String body = runPromise(() -> response.loadBody()
                .map($ -> new String(response.getBody().asArray())));
        assertThat(body).contains("READY");
    }
}
