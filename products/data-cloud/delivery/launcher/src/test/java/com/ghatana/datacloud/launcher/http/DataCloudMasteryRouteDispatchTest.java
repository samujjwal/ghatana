/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.api.controller.MasteryController;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpError;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies mastery endpoints are reachable through the canonical launcher router.
 *
 * @doc.type class
 * @doc.purpose Route-dispatch verification for mastery endpoints in DataCloudRouterBuilder
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudRouterBuilder mastery route dispatch")
class DataCloudMasteryRouteDispatchTest extends EventloopTestBase {

    @Test
    @DisplayName("GET /api/v1/mastery/preview/decision dispatches to MasteryController")
    void previewDecisionRouteDispatchesToMasteryController() {
        MasteryController masteryController = masteryControllerWithAcceptedResponse();
        RoutingServlet router = new DataCloudRouterBuilder(Eventloop.create())
                .withMasteryRoutes(masteryController)
                .build();

        HttpRequest request = HttpRequest.get(
                        "http://localhost/api/v1/mastery/preview/decision"
                                + "?tenantId=t-1&agentId=a-1&skillId=s-1")
                .build();

        int status = runPromise(() -> router.serve(request).map(HttpResponse::getCode));

        assertThat(status).isEqualTo(202);
        verify(masteryController, times(1)).serve(any());
    }

    @Test
    @DisplayName("GET /api/v1/mastery/preview/retrieval dispatches to MasteryController")
    void previewRetrievalRouteDispatchesToMasteryController() {
        MasteryController masteryController = masteryControllerWithAcceptedResponse();
        RoutingServlet router = new DataCloudRouterBuilder(Eventloop.create())
                .withMasteryRoutes(masteryController)
                .build();

        HttpRequest request = HttpRequest.get(
                        "http://localhost/api/v1/mastery/preview/retrieval"
                                + "?tenantId=t-1&agentId=a-1&skillId=s-1&limit=5")
                .build();

        int status = runPromise(() -> router.serve(request).map(HttpResponse::getCode));

        assertThat(status).isEqualTo(202);
        verify(masteryController, times(1)).serve(any());
    }

    @Test
    @DisplayName("POST /api/v1/mastery/learning-deltas/:deltaId/dry-run-promotion dispatches to MasteryController")
    void dryRunPromotionRouteDispatchesToMasteryController() {
        MasteryController masteryController = masteryControllerWithAcceptedResponse();
        RoutingServlet router = new DataCloudRouterBuilder(Eventloop.create())
                .withMasteryRoutes(masteryController)
                .build();

        HttpRequest request = HttpRequest.post(
                        "http://localhost/api/v1/mastery/learning-deltas/delta-1/dry-run-promotion?tenantId=t-1")
                .build();

        int status = runPromise(() -> router.serve(request).map(HttpResponse::getCode));

        assertThat(status).isEqualTo(202);
        verify(masteryController, times(1)).serve(any());
    }

    @Test
    @DisplayName("POST /api/v1/mastery/obsolescence-events/process dispatches to MasteryController")
    void obsolescenceProcessRouteDispatchesToMasteryController() {
        MasteryController masteryController = masteryControllerWithAcceptedResponse();
        RoutingServlet router = new DataCloudRouterBuilder(Eventloop.create())
                .withMasteryRoutes(masteryController)
                .build();

        HttpRequest request = HttpRequest.post(
                        "http://localhost/api/v1/mastery/obsolescence-events/process?tenantId=t-1")
                .build();

        int status = runPromise(() -> router.serve(request).map(HttpResponse::getCode));

        assertThat(status).isEqualTo(202);
        verify(masteryController, times(1)).serve(any());
    }

    @Test
    @DisplayName("Mastery routes are not reachable when MasteryController is omitted")
    void masteryRoutesNotReachableWhenControllerOmitted() {
        RoutingServlet router = new DataCloudRouterBuilder(Eventloop.create())
                .withMasteryRoutes(null)
                .build();

        HttpRequest request = HttpRequest.get(
                        "http://localhost/api/v1/mastery/preview/decision"
                                + "?tenantId=t-1&agentId=a-1&skillId=s-1")
                .build();

        assertThatThrownBy(() -> runPromise(() -> router.serve(request).map(HttpResponse::getCode)))
                .hasRootCauseInstanceOf(HttpError.class)
                .hasMessageContaining("HTTP code 404");
    }

    private static MasteryController masteryControllerWithAcceptedResponse() {
        MasteryController masteryController = mock(MasteryController.class);
        when(masteryController.serve(any()))
                .thenReturn(Promise.of(HttpResponse.ofCode(202).build()));
        return masteryController;
    }
}
