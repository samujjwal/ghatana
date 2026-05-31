/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.feature.DataCloudFeature;
import com.ghatana.datacloud.feature.DataCloudFeatureFlags;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.DataSourceRegistryHandler;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies fail-closed behavior for connector routes when no connector handler is configured.
 *
 * @doc.type class
 * @doc.purpose Ensure connector and data-fabric routes return explicit 503 fallback responses when unavailable
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudRouterBuilder connector fallback routes")
class DataCloudConnectorRouteFallbackTest extends EventloopTestBase {

        private static final String DEPLOYMENT_PROFILE = "test";

    private static final HttpHandlerSupport HTTP_SUPPORT = new HttpHandlerSupport(
            new ObjectMapper(),
            "*",
            "GET,POST,PUT,DELETE,OPTIONS",
            "Content-Type,Authorization");

        @AfterEach
        void tearDown() {
                DataCloudFeatureFlags.clearOverrides();
        }

    @Test
    @DisplayName("GET /api/v1/connectors returns 503 when connector handler is unavailable")
    void connectorsListReturns503WhenHandlerUnavailable() {
        RoutingServlet router = new DataCloudRouterBuilder(Eventloop.create())
                                .withConnectorRoutes(null, HTTP_SUPPORT)
                .build();

        HttpResponse response = runPromise(() -> router.serve(
                HttpRequest.get("http://localhost/api/v1/connectors").build()));

        String body = runPromise(() -> response.loadBody())
                .asString(StandardCharsets.UTF_8);

        assertThat(response.getCode()).isEqualTo(503);
        assertThat(body).contains("Connector registry not available");
    }

    @Test
    @DisplayName("GET /data-fabric/metrics returns 503 when connector handler is unavailable")
    void dataFabricMetricsReturns503WhenHandlerUnavailable() {
        RoutingServlet router = new DataCloudRouterBuilder(Eventloop.create())
                                .withConnectorRoutes(null, HTTP_SUPPORT)
                .build();

        HttpResponse response = runPromise(() -> router.serve(
                HttpRequest.get("http://localhost/data-fabric/metrics").build()));

        String body = runPromise(() -> response.loadBody())
                .asString(StandardCharsets.UTF_8);

        assertThat(response.getCode()).isEqualTo(503);
        assertThat(body).contains("Connector registry not available");
    }

        @Test
        @DisplayName("GET /api/v1/connectors returns 503 when connector feature is disabled")
        void connectorsListReturns503WhenFeatureDisabled() {
                DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_CONNECTORS, false);
                DataSourceRegistryHandler handler = mock(DataSourceRegistryHandler.class);

                RoutingServlet router = new DataCloudRouterBuilder(Eventloop.create())
                                .withConnectorRoutes(handler, HTTP_SUPPORT)
                                .build();

                HttpResponse response = runPromise(() -> router.serve(
                                HttpRequest.get("http://localhost/api/v1/connectors").build()));

                String body = runPromise(() -> response.loadBody())
                                .asString(StandardCharsets.UTF_8);

                assertThat(response.getCode()).isEqualTo(503);
                assertThat(body).contains("Connector registry not available");
        }
}
