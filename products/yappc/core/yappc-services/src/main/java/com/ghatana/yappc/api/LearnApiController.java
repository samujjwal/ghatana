package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.learn.HistoricalContext;
import com.ghatana.yappc.domain.learn.Insights;
import com.ghatana.yappc.domain.observe.Observation;
import com.ghatana.yappc.services.learn.LearningService;
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
 * @doc.purpose HTTP API controller for Learn phase
 * @doc.layer api
 * @doc.pattern Controller
 */
public class LearnApiController {

    private static final Logger log = LoggerFactory.getLogger(LearnApiController.class);

    private final LearningService learningService;

    public LearnApiController(LearningService learningService) {
        this.learningService = learningService;
    }

    public Promise<HttpResponse> analyze(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                String json = body.asString(UTF_8);
                try {
                    Observation observation = JsonMapper.fromJson(json, Observation.class);
                    if (observation == null || observation.id() == null || observation.id().isBlank()) {
                        return Promise.of(badRequest400("observation.id is required"));
                    }

                    return learningService.analyze(observation).map(this::toJsonResponse);
                } catch (JsonProcessingException e) {
                    log.error("Invalid learn analyze request", e);
                    return Promise.of(badRequest400("Invalid JSON format"));
                }
            })
            .whenException(e -> log.error("Learn analyze request failed", e));
    }

    public Promise<HttpResponse> analyzeWithContext(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                String json = body.asString(UTF_8);
                try {
                    LearnWithContextRequest payload = JsonMapper.fromJson(json, LearnWithContextRequest.class);
                    if (payload == null || payload.observation() == null) {
                        return Promise.of(badRequest400("observation is required"));
                    }
                    if (payload.observation().id() == null || payload.observation().id().isBlank()) {
                        return Promise.of(badRequest400("observation.id is required"));
                    }

                    return learningService.analyzeWithContext(payload.observation(), payload.context())
                        .map(this::toJsonResponse);
                } catch (JsonProcessingException e) {
                    log.error("Invalid learn analyze-with-context request", e);
                    return Promise.of(badRequest400("Invalid JSON format"));
                }
            })
            .whenException(e -> log.error("Learn analyze-with-context request failed", e));
    }

    private HttpResponse toJsonResponse(Insights insights) {
        try {
            return ok200Json(JsonMapper.toJson(insights));
        } catch (JsonProcessingException e) {
            log.error("Error serializing insights response", e);
            return error500("Internal server error");
        }
    }

    private record LearnWithContextRequest(Observation observation, HistoricalContext context) {
    }
}
