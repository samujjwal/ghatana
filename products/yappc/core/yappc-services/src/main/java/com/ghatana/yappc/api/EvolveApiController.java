package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.domain.intent.ConstraintSpec;
import com.ghatana.yappc.domain.learn.Insights;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.platform.governance.security.Principal;
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
 * @doc.purpose HTTP API controller for Evolve phase
 * @doc.layer api
 * @doc.pattern Controller
 */
public class EvolveApiController {

    private static final Logger log = LoggerFactory.getLogger(EvolveApiController.class);

    private final EvolutionService evolutionService;

    public EvolveApiController(EvolutionService evolutionService) {
        this.evolutionService = evolutionService;
    }

    public Promise<HttpResponse> propose(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                String json = body.asString(UTF_8);
                try {
                    Insights insights = JsonMapper.fromJson(json, Insights.class);
                    if (insights == null || insights.id() == null || insights.id().isBlank()) {
                        return Promise.of(badRequest400("insights.id is required"));
                    }

                    return evolutionService.propose(insights).map(this::toJsonResponse);
                } catch (JsonProcessingException e) {
                    log.error("Invalid evolve propose request", e);
                    return Promise.of(badRequest400("Invalid JSON format"));
                }
            })
            .whenException(e -> log.error("Evolve propose request failed", e));
    }

    public Promise<HttpResponse> proposeWithConstraints(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                String json = body.asString(UTF_8);
                try {
                    EvolveWithConstraintsRequest payload = JsonMapper.fromJson(json, EvolveWithConstraintsRequest.class);
                    if (payload == null || payload.insights() == null) {
                        return Promise.of(badRequest400("insights is required"));
                    }
                    if (payload.insights().id() == null || payload.insights().id().isBlank()) {
                        return Promise.of(badRequest400("insights.id is required"));
                    }

                    return evolutionService.proposeWithConstraints(payload.insights(), payload.constraints())
                        .map(this::toJsonResponse);
                } catch (JsonProcessingException e) {
                    log.error("Invalid evolve propose-with-constraints request", e);
                    return Promise.of(badRequest400("Invalid JSON format"));
                }
            })
            .whenException(e -> log.error("Evolve propose-with-constraints request failed", e));
    }

    public Promise<HttpResponse> approveProposal(HttpRequest request) {
        String proposalId = request.getPathParameter("proposalId");
        if (proposalId == null || proposalId.isBlank()) {
            return Promise.of(badRequest400("proposalId is required"));
        }

        Principal principal = request.getAttachment(Principal.class);
        if (principal == null) {
            return Promise.of(HttpResponse.ofCode(401)
                    .withJson("{\"error\":\"Unauthenticated\"}")
                    .build());
        }

        return parseDecisionReason(request)
                .then(reason -> evolutionService.approveProposal(proposalId, principal.getName(), reason)
                        .map(this::toJsonResponse))
                .whenException(e -> log.error("Evolve approve request failed: proposalId={}", proposalId, e));
    }

    public Promise<HttpResponse> rejectProposal(HttpRequest request) {
        String proposalId = request.getPathParameter("proposalId");
        if (proposalId == null || proposalId.isBlank()) {
            return Promise.of(badRequest400("proposalId is required"));
        }

        Principal principal = request.getAttachment(Principal.class);
        if (principal == null) {
            return Promise.of(HttpResponse.ofCode(401)
                    .withJson("{\"error\":\"Unauthenticated\"}")
                    .build());
        }

        return parseDecisionReason(request)
                .then(reason -> evolutionService.rejectProposal(proposalId, principal.getName(), reason)
                        .map(this::toJsonResponse))
                .whenException(e -> log.error("Evolve reject request failed: proposalId={}", proposalId, e));
    }

    private Promise<String> parseDecisionReason(HttpRequest request) {
        return request.loadBody()
                .then(body -> {
                    String json = body.asString(UTF_8);
                    if (json == null || json.isBlank()) {
                        return Promise.of((String) null);
                    }
                    try {
                        DecisionRequest decision = JsonMapper.fromJson(json, DecisionRequest.class);
                        return Promise.of(decision != null ? decision.reason() : null);
                    } catch (JsonProcessingException e) {
                        return Promise.of((String) null);
                    }
                });
    }

    private HttpResponse toJsonResponse(EvolutionPlan plan) {
        try {
            return ok200Json(JsonMapper.toJson(plan));
        } catch (JsonProcessingException e) {
            log.error("Error serializing evolution plan response", e);
            return error500("Internal server error");
        }
    }

    private HttpResponse toJsonResponse(EvolutionService.EvolutionDecision decision) {
        try {
            return ok200Json(JsonMapper.toJson(decision));
        } catch (JsonProcessingException e) {
            log.error("Error serializing evolution decision response", e);
            return error500("Internal server error");
        }
    }

    private record EvolveWithConstraintsRequest(Insights insights, ConstraintSpec constraints) {
    }

    private record DecisionRequest(String reason) {
    }
}
