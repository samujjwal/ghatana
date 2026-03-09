package com.ghatana.ingress.api;

import com.ghatana.platform.http.server.response.ResponseBuilder;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.inject.annotation.Inject;

import static io.activej.http.HttpMethod.GET;

/**
 * Health endpoints as per Day 10 plan.
 * /health/live  -> always UP when process is live
 * /health/ready -> basic readiness (can be extended with DB/Redis checks)
 */
public final class HealthController {

    @Inject
    public HealthController(Eventloop eventloop) {
        this.eventloop = eventloop;
    }

    private final Eventloop eventloop;

    public RoutingServlet servlet() {
        return RoutingServlet.builder(eventloop)
                .with(GET, "/health/live", req -> 
                    ResponseBuilder.ok()
                        .rawJson("{\"status\":\"UP\"}")
                        .build()
                        .toPromise())
                .with(GET, "/health/ready", req -> 
                    ResponseBuilder.ok()
                        .rawJson("{\"status\":\"READY\"}")
                        .build()
                        .toPromise())
                .build();
    }
}
