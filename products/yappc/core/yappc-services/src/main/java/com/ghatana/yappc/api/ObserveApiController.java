package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.observe.Observation;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.services.observe.ObserveService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.ghatana.yappc.api.HttpResponses.badRequest400;
import static com.ghatana.yappc.api.HttpResponses.error500;
import static com.ghatana.yappc.api.HttpResponses.ok200Json;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @doc.type class
 * @doc.purpose HTTP API controller for Observe phase
 * @doc.layer api
 * @doc.pattern Controller
 */
public class ObserveApiController {

    private static final Logger log = LoggerFactory.getLogger(ObserveApiController.class);

    private final ObserveService observeService;

    public ObserveApiController(ObserveService observeService) {
        this.observeService = observeService;
    }

    public Promise<HttpResponse> collectObservation(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                String json = body.asString(UTF_8);
                try {
                    RunResult runResult = JsonMapper.fromJson(json, RunResult.class);
                    if (runResult == null || runResult.id() == null || runResult.id().isBlank()) {
                        return Promise.of(badRequest400("runResult.id is required"));
                    }

                    return observeService.collect(runResult).map(this::toJsonResponse);
                } catch (JsonProcessingException e) {
                    log.error("Invalid observe collect request", e);
                    return Promise.of(badRequest400("Invalid JSON format"));
                }
            })
            .whenException(e -> log.error("Observe collect request failed", e));
    }

    private HttpResponse toJsonResponse(Observation observation) {
        try {
            return ok200Json(JsonMapper.toJson(observation));
        } catch (JsonProcessingException e) {
            log.error("Error serializing observation response", e);
            return error500("Internal server error");
        }
    }
}
